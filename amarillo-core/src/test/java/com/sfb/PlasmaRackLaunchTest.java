package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.Location;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * Launching a type-D from a plasma rack as a seeking weapon, through the real game path.
 *
 * <h2>Why this is driven through Game and not the rack</h2>
 * {@code PlasmaRackTest} covers the rack's own rules. This covers the ones only the GAME knows:
 * the arc to the target, the range FP10.212 measures from the firing ship, the target's size class,
 * and FP10.242's limit on how many of the SHIP's racks may be offensive in a turn. A test that
 * called {@code rack.launch} directly would enter below every one of those — the mistake
 * [[feedback_test_at_the_gating_layer]] records twice over.
 *
 * <p>The Romulan K5D is the fixture: four racks, at LS/LS/RS/RS, and no fighters to confuse the
 * reading. It is also the only hull where FP10.242 can bite.
 */
public class PlasmaRackLaunchTest {

    private Game game;
    private Ship k5d;
    private Ship enemy;
    private AdminShuttle smallTarget;

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

        enemy = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        enemy.setName("USS Constitution");
        // Three hexes off the port side, so it sits in an LS rack's arc and inside FP10.212's six.
        enemy.setLocation(new Location(7, 11));
        enemy.setFacing(13);
        enemy.setOwner(federation);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        game.startTurn();
        for (Ship s : new Ship[] { k5d, enemy }) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        toActivity();

        // A size-6 unit to stand in for the fighters and drones defensive mode exists to kill.
        smallTarget = new AdminShuttle();
        smallTarget.setName("Target Shuttle");
        smallTarget.setOwner(federation);
        smallTarget.setLocation(new Location(8, 11));
        smallTarget.setFacing(13);
        game.getActiveShuttles().add(smallTarget);

        // FP10.25 WS-III: every torpedo active, so nothing here is refused for want of FP9.22's
        // half point. Activation has its own tests.
        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(3);
    }

    private void toActivity() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    private List<PlasmaRack> racks() {
        List<PlasmaRack> list = new ArrayList<>();
        for (Weapon w : k5d.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                list.add(rack);
        return list;
    }

    /** A rack whose arc bears on the port-side targets. */
    private PlasmaRack portRack(int which) {
        List<PlasmaRack> ls = new ArrayList<>();
        for (PlasmaRack rack : racks())
            if ("LS".equals(rack.getArcLabel()))
                ls.add(rack);
        return ls.get(which);
    }

    private int torpedoesInSpace() {
        int n = 0;
        for (com.sfb.objects.Seeker s : game.getSeekers())
            if (s instanceof PlasmaTorpedo)
                n++;
        return n;
    }

    // ---------------------------------------------------------------- the fixture itself

    @Test
    public void theK5dHasTwoRacksOnEachSide() {
        assertEquals(4, racks().size());
        int ls = 0, rs = 0;
        for (PlasmaRack rack : racks()) {
            if ("LS".equals(rack.getArcLabel())) ls++;
            if ("RS".equals(rack.getArcLabel())) rs++;
        }
        assertEquals("FP10.12: usually LS or RS", 2, ls);
        assertEquals(2, rs);
    }

    // ---------------------------------------------------------------- launching

    /** Defensive mode at a size-6 target three hexes out: exactly what the weapon is for. */
    @Test
    public void aDefensiveLaunchAtASmallTargetFlies() {
        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(1, torpedoesInSpace());
        assertEquals("one torpedo gone", 3, portRack(0).getTorpedoes());
        assertEquals(PlasmaRack.RackMode.DEFENSIVE, portRack(0).getModeThisTurn());
    }

    /** Offensive mode has no size or range restriction of its own (FP10.211). */
    @Test
    public void anOffensiveLaunchAtACruiserFlies() {
        Game.ActionResult r = game.launchPlasmaRack(
                k5d, enemy, portRack(0), PlasmaRack.RackMode.OFFENSIVE, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(1, torpedoesInSpace());
        assertEquals(PlasmaRack.RackMode.OFFENSIVE, portRack(0).getModeThisTurn());
    }

    /** The torpedo that comes out is a type-D, guided by the firing ship. */
    @Test
    public void theTorpedoIsATypeDControlledByTheShip() {
        game.launchPlasmaRack(k5d, smallTarget, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);

        PlasmaTorpedo torp = null;
        for (com.sfb.objects.Seeker s : game.getSeekers())
            if (s instanceof PlasmaTorpedo pt)
                torp = pt;
        assertNotNull(torp);
        assertEquals(com.sfb.properties.PlasmaType.D, torp.getPlasmaType());
        assertEquals(k5d, torp.getController());
        assertEquals(smallTarget, torp.getTarget());
        assertEquals("launched from the ship's hex", k5d.getLocation(), torp.getLocation());
    }

    // ---------------------------------------------------------------- what only the game knows

    /**
     * FP10.212's size restriction, refused at the LAUNCH rather than by the rack alone — the rack
     * cannot see the target, so this is the gate that matters.
     */
    @Test
    public void defensiveModeIsRefusedAgainstACruiser() {
        Game.ActionResult r = game.launchPlasmaRack(
                k5d, enemy, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.212"));
        assertEquals("and nothing left the rack", 4, portRack(0).getTorpedoes());
        assertEquals("nor was the mode settled",
                PlasmaRack.RackMode.UNDECIDED, portRack(0).getModeThisTurn());
    }

    /**
     * FP10.212's six hexes, measured "from the firing ship". Also a size-6 target, so only the
     * range can be the reason.
     */
    @Test
    public void defensiveModeIsRefusedBeyondSixHexes() {
        smallTarget.setLocation(new Location(1, 10));   // well off the port bow

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.212"));
    }

    /** FP10.12: the target has to be in the rack's own 180 degrees. */
    @Test
    public void aRackWillNotFireOutsideItsArc() {
        // A starboard rack cannot reach a target off the port side.
        PlasmaRack starboard = null;
        for (PlasmaRack rack : racks())
            if ("RS".equals(rack.getArcLabel()))
                starboard = rack;

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, starboard, PlasmaRack.RackMode.DEFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("arc"));
    }

    /**
     * FP10.242: "A ship armed with plasma racks may not use more than two of those racks in
     * offensive mode during a given turn." The reason the K5D's FOUR racks matter — two of them
     * are forced into defensive mode whatever the player wants.
     * <p>
     * All four bear on the cruiser only if it is in their arcs, so this uses the two port racks for
     * the first two shots and then asks a starboard one, moving the target to suit. What is being
     * tested is the ship's count, not the geometry.
     */
    @Test
    public void onlyTwoRacksMayGoOffensiveInATurn() {
        assertTrue(game.launchPlasmaRack(k5d, enemy, portRack(0),
                PlasmaRack.RackMode.OFFENSIVE, 0).isSuccess());
        assertTrue(game.launchPlasmaRack(k5d, enemy, portRack(1),
                PlasmaRack.RackMode.OFFENSIVE, 0).isSuccess());
        assertEquals(2, k5d.plasmaRacksInOffensiveMode(game.getClock().getTurn()));

        // A third rack, with the target moved into its arc so only FP10.242 can refuse it.
        PlasmaRack starboard = null;
        for (PlasmaRack rack : racks())
            if ("RS".equals(rack.getArcLabel()))
                starboard = rack;
        enemy.setLocation(new Location(13, 11));

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, enemy, starboard, PlasmaRack.RackMode.OFFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.242"));
        assertEquals("the third rack kept its torpedo", 4, starboard.getTorpedoes());
        assertEquals("and was not left stuck in a mode it could not enter",
                PlasmaRack.RackMode.UNDECIDED, starboard.getModeThisTurn());
    }

    /**
     * And the third rack may still fire DEFENSIVELY — FP10.242 limits offensive mode only, which
     * is what makes it a trade rather than a cap on the weapon.
     */
    @Test
    public void aThirdRackMayStillFireDefensively() {
        game.launchPlasmaRack(k5d, enemy, portRack(0), PlasmaRack.RackMode.OFFENSIVE, 0);
        game.launchPlasmaRack(k5d, enemy, portRack(1), PlasmaRack.RackMode.OFFENSIVE, 0);

        PlasmaRack starboard = null;
        for (PlasmaRack rack : racks())
            if ("RS".equals(rack.getArcLabel()))
                starboard = rack;
        smallTarget.setLocation(new Location(12, 11));

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, starboard, PlasmaRack.RackMode.DEFENSIVE, 0);

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /** A mode has to be named; UNDECIDED is not a choice the rules recognise. */
    @Test
    public void aLaunchWithoutAModeIsRefused() {
        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, portRack(0), PlasmaRack.RackMode.UNDECIDED, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.21"));
    }

    /** FP9.22's gate, reached through the launch: an unactivated rack sends nothing. */
    @Test
    public void anUnactivatedRackLaunchesNothing() {
        for (PlasmaRack rack : racks())
            rack.applyWeaponStatus(0);

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, smallTarget, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP9.22"));
        assertEquals(0, torpedoesInSpace());
    }

    /**
     * FP10.212 again, from the other side: in defensive mode a rack fires every impulse, so a
     * second torpedo needs only a new impulse rather than a new turn.
     */
    @Test
    public void aDefensiveRackFiresAgainNextImpulse() {
        assertTrue(game.launchPlasmaRack(k5d, smallTarget, portRack(0),
                PlasmaRack.RackMode.DEFENSIVE, 0).isSuccess());

        Game.ActionResult sameImpulse = game.launchPlasmaRack(
                k5d, smallTarget, portRack(0), PlasmaRack.RackMode.DEFENSIVE, 0);
        assertFalse("one shot per impulse", sameImpulse.isSuccess());

        game.advancePhase();
        toActivity();

        assertTrue("but free again next impulse",
                game.launchPlasmaRack(k5d, smallTarget, portRack(0),
                        PlasmaRack.RackMode.DEFENSIVE, 0).isSuccess());
        assertEquals(2, torpedoesInSpace());
    }

    /** An offensive rack has spent its turn, however many impulses pass. */
    @Test
    public void anOffensiveRackIsDoneForTheTurn() {
        assertTrue(game.launchPlasmaRack(k5d, enemy, portRack(0),
                PlasmaRack.RackMode.OFFENSIVE, 0).isSuccess());

        for (int i = 0; i < 10; i++) {
            game.advancePhase();
            toActivity();
        }

        Game.ActionResult r = game.launchPlasmaRack(
                k5d, enemy, portRack(0), PlasmaRack.RackMode.OFFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.211"));
    }
}
