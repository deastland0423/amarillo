package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * Bolting a plasma rack through the ORDINARY fire path, which is where it leaked.
 *
 * <h2>The bug this exists for</h2>
 * Giving {@code PlasmaRack} the {@code DirectFire} interface made
 * {@code canBeFiredAtTarget()} answer true, and {@code DamageResolver} tests exactly that
 * capability before dispatching to {@code fire()}. So a rack became firable from the normal fire
 * orders with none of its rules applied, and the first run of this fixture produced:
 *
 * <pre>
 *   PlasmaDRack-1  (die 3)  HIT  5
 *   after: torps=3 mode=DEFENSIVE bolts=1   target sizeClass=3
 * </pre>
 *
 * A size-class-3 cruiser, bolted in DEFENSIVE mode, which FP10.212 forbids outright. Three things
 * were wrong at once: the mode was chosen silently by a default, FP10.212's target restrictions
 * were never consulted because {@code bolt()} cannot see the target, and FP10.241's per-ship limit
 * was implemented on {@code Ship} with nothing calling it.
 *
 * <p>The seeking path enforced all three; the bolt path inherited none. That asymmetry is the
 * thing to remember — enforcement attached to one entry point is not enforcement.
 */
public class PlasmaRackBoltFirePathTest {

    private Game game;
    private Ship k5d;
    private Ship cruiser;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player romulan = new Player();
        romulan.setTeamName("Romulan");
        Player federation = new Player();
        federation.setTeamName("Federation");

        k5d = ShipLibrary.createShip(ShipLibrary.get("Romulan", "K5D"));
        k5d.setName("RIS Nemesis");
        k5d.setLocation(new Location(10, 10));
        k5d.setFacing(1);
        k5d.setOwner(romulan);
        k5d.setActiveFireControl(true);
        game.getShips().add(k5d);

        cruiser = new Ship();
        cruiser.init(FederationShips.getFedCa());
        cruiser.setName("USS Enterprise");
        cruiser.setLocation(new Location(7, 11));    // three hexes off the port side
        cruiser.setFacing(13);
        cruiser.setOwner(federation);
        cruiser.setActiveFireControl(true);
        game.getShips().add(cruiser);

        game.startTurn();
        for (Ship s : new Ship[]{k5d, cruiser}) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        toDirectFire();

        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(3);              // FP10.25 WS-III
        k5d.addLockOn(cruiser);
    }

    private void toDirectFire() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE)
                return;
            game.advancePhase();
        }
        fail("never reached a Direct Fire phase");
    }

    private List<PlasmaRack> racks() {
        List<PlasmaRack> list = new ArrayList<>();
        for (Weapon w : k5d.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                list.add(rack);
        return list;
    }

    private List<PlasmaRack> portRacks() {
        List<PlasmaRack> ls = new ArrayList<>();
        for (PlasmaRack rack : racks())
            if ("LS".equals(rack.getArcLabel()))
                ls.add(rack);
        return ls;
    }

    /**
     * Move on to the next Direct-Fire segment.
     * <p>
     * Needed because an attacker may fire at a given target only ONCE per Direct-Fire segment, so
     * a test about a second volley has to leave the first one behind. Nothing to do with the rack;
     * it is the rule that pairs an attacker with a target.
     */
    private void nextSegment() {
        game.advancePhase();
        toDirectFire();
    }

    /** Fire these racks at the cruiser, as the fire orders pad would. */
    private String fire(PlasmaRack... which) {
        List<Weapon> selected = new ArrayList<>();
        for (PlasmaRack rack : which)
            selected.add(rack);
        return game.fireWeapons(k5d, cruiser, selected, 3, 3, 4, false, true);
    }

    // ---------------------------------------------------------------- the leak, closed

    /**
     * The exact shot that used to land. FP10.212: defensive mode engages "size-5 and smaller
     * targets", and a cruiser is size class 3.
     */
    @Test
    public void aRackCannotBoltACruiserInDefensiveMode() {
        PlasmaRack rack = portRacks().get(0);
        rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);

        String log = fire(rack);

        assertTrue(log, log.contains("FP10.212"));
        assertFalse("nothing hit", log.contains("HIT"));
        assertEquals("and no torpedo was spent", 4, rack.getTorpedoes());
        assertEquals("nor was the mode committed",
                PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn());
    }

    /**
     * And the mode is no longer chosen for the player. With nothing declared the rack is refused
     * by name rather than quietly committed — which is what the old DEFENSIVE default did.
     */
    @Test
    public void aRackWithNoDeclaredModeIsRefused() {
        PlasmaRack rack = portRacks().get(0);

        String log = fire(rack);

        assertTrue(log, log.contains("FP10.21"));
        assertEquals(4, rack.getTorpedoes());
        assertEquals(PlasmaRack.RackMode.UNDECIDED, rack.getModeThisTurn());
    }

    // ---------------------------------------------------------------- what is still legal

    /** Offensive mode has no target restriction of its own, so the cruiser is fair game. */
    @Test
    public void aRackMayBoltACruiserInOffensiveMode() {
        PlasmaRack rack = portRacks().get(0);
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);

        String log = fire(rack);

        assertTrue(log, log.contains("HIT") || log.contains("MISS"));
        assertEquals("a torpedo was spent", 3, rack.getTorpedoes());
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, rack.getModeThisTurn());
        assertEquals(1, rack.getBoltsThisTurn());
    }

    /**
     * FP10.241: "A ship armed with plasma racks may not fire more than one type-D plasma bolt at a
     * size-4 or larger target during any given turn. This restriction is per firing ship, not per
     * rack." So the SECOND rack is refused even though it is a different rack with its own
     * ammunition and its own untouched bolt allowance.
     * <p>
     * This is the clause that was implemented on {@code Ship} and never called.
     */
    @Test
    public void aShipBoltsOneTypeDAtACruiserPerTurnAcrossAllItsRacks() {
        PlasmaRack first = portRacks().get(0);
        PlasmaRack second = portRacks().get(1);
        first.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        second.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);

        String firstLog = fire(first);
        assertTrue(firstLog, firstLog.contains("HIT") || firstLog.contains("MISS"));

        nextSegment();
        second.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        String secondLog = fire(second);

        assertTrue(secondLog, secondLog.contains("FP10.241"));
        assertEquals("the second rack kept its torpedo", 4, second.getTorpedoes());
    }

    /**
     * FP10.221, reached through the fire path: a rack bolts once a turn whatever its mode, so the
     * same rack cannot bolt again even after the impulse moves on.
     */
    @Test
    public void aRackBoltsOnlyOncePerTurnThroughTheFirePath() {
        PlasmaRack rack = portRacks().get(0);
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        fire(rack);

        nextSegment();
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        String log = fire(rack);

        assertTrue(log, log.contains("FP10.211") || log.contains("FP10.221"));
        assertEquals(3, rack.getTorpedoes());
    }

    /**
     * A declaration is consumed by the shot it was made for. Otherwise a mode chosen once would
     * keep arming later volleys the player never declared anything for — the same class of mistake
     * as the default it replaced.
     */
    @Test
    public void aDeclarationDoesNotOutliveItsVolley() {
        PlasmaRack rack = portRacks().get(0);
        PlasmaRack other = portRacks().get(1);
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        fire(rack);

        assertNull("consumed", rack.getDeclaredBoltMode());
        assertNull("and never leaked to another rack", other.getDeclaredBoltMode());

        nextSegment();
        String log = fire(other);
        assertTrue("so the other rack is refused for want of a mode", log.contains("FP10.21"));
    }

    /** A rack already committed this turn keeps its mode: FP10.21 does not let it change. */
    @Test
    public void aCommittedRackIgnoresALaterDeclaration() {
        PlasmaRack rack = portRacks().get(0);
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);
        fire(rack);
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, rack.getModeThisTurn());

        rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);

        assertEquals("still offensive for the rest of the turn",
                PlasmaRack.RackMode.OFFENSIVE, rack.modeForDirectFire());
    }

    /**
     * FP9.22 reaches the fire path too: an unactivated rack bolts nothing. Asserted because the
     * bolt path reaches {@code boltRefusal} by way of the new block rather than on its own.
     */
    @Test
    public void anUnactivatedRackBoltsNothing() {
        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(0);
        PlasmaRack rack = portRacks().get(0);
        rack.declareBoltMode(PlasmaRack.RackMode.OFFENSIVE);

        String log = fire(rack);

        assertTrue(log, log.contains("FP9.22"));
        assertEquals(4, rack.getTorpedoes());
    }
}
