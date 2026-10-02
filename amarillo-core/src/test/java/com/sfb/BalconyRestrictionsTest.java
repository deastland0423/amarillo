package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.Weapon;

/**
 * J1.53 balcony and track, slice five: what a craft may NOT do while parked.
 * <p>
 * J1.531, after the damage clauses:
 * <blockquote>
 * "Shuttles on the balcony cannot fire, and enemy shuttles cannot land or be brought down on
 * the balcony. Shuttles on the balcony cannot be prepared for special missions (WW, suicide,
 * SP), or rearmed or repaired by deck crews (J4.8)."
 * </blockquote>
 * And J1.534: "Scatter-packs can be held on the balcony; suicide shuttles and wild weasels
 * cannot."
 * <p>
 * Most of this slice turned out to be already true, and that is the point worth recording.
 * Slice one's decision that a parked craft is NOT in {@link ShuttleBay#getInventory()} and
 * holds no {@link com.sfb.systemgroups.ShuttleSpace} means every path that does work on a
 * craft aboard — the end-of-turn rearm pass over the spaces, the deck crews posted to a box,
 * the Energy Allocation sweep that charges weasels — cannot see a parked one without being
 * told balconies exist. So these tests are mostly guards: they pin behaviour that falls out of
 * the structure, which is exactly the behaviour most likely to be undone by a well-meaning
 * change to a lookup.
 * <p>
 * Two things were NOT already true and are fixed in this slice: a craft being CHARGED as a
 * weasel could be parked (it is still an admin shuttle, so an {@code instanceof} check missed
 * it — {@link #aChargingWeaselCannotBeParked}), and nothing stopped a parked craft from
 * firing beyond the fact that no lookup could name it.
 */
public class BalconyRestrictionsTest {

    private Game game;
    private Ship cva;
    private Ship enemy;
    private Player fed;
    private Player klingon;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        game = new Game();

        fed = new Player();
        fed.setTeamName("Federation");
        klingon = new Player();
        klingon.setTeamName("Klingon");

        cva = ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
        cva.setName("USS Enterprise");
        cva.setLocation(new Location(10, 10));
        cva.setFacing(1);
        cva.setOwner(fed);
        cva.setActiveFireControl(true);
        game.getShips().add(cva);

        enemy = ShipLibrary.createShip(ShipLibrary.get("Klingon", "D7"));
        enemy.setName("IKS Fury");
        enemy.setLocation(new Location(10, 11));
        enemy.setFacing(13);
        enemy.setOwner(klingon);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        game.startTurn();
        for (Ship ship : new Ship[] { cva, enemy }) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setFireControl(ship.getFireControlCost());
            game.submitAllocation(ship, e);
        }
        toActivity();
    }

    private void toActivity() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    private ShuttleBay fighterBay() {
        for (ShuttleBay b : cva.getShuttles().getBays())
            if (b.hasBalcony())
                return b;
        throw new AssertionError("fixture: the CVA should have a bay with a balcony");
    }

    /** Park the first craft in the bay through the real action, and hand it back. */
    private Shuttle park(ShuttleBay bay) {
        String name = bay.getInventory().get(0).getName();
        Game.ActionResult r = game.moveToBalcony(cva, name);
        assertTrue("fixture: " + r.getMessage(), r.isSuccess());
        return bay.getBalcony().get(bay.getBalcony().size() - 1);
    }

    // ---------------------------------------------------------------- cannot fire

    @Test
    public void aParkedCraftCannotFire() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay);
        List<Weapon> guns = new ArrayList<>();
        for (Weapon w : craft.getWeapons().fetchAllWeapons())
            if (w.isFunctional())
                guns.add(w);
        assertFalse("fixture: the craft has weapons", guns.isEmpty());

        String log = game.fireWeapons(craft, enemy, guns, 1, 1, 1);

        assertTrue(log, log.contains("balcony"));
        assertTrue(log, log.contains("J1.531"));
    }

    /** The same craft, launched, fires perfectly well — it is the balcony that stops it. */
    @Test
    public void theSameCraftCanFireOnceItHasLaunched() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay);
        assertTrue(game.launchShuttle(cva, bay, craft, 6, 1).isSuccess());
        List<Weapon> guns = new ArrayList<>();
        for (Weapon w : craft.getWeapons().fetchAllWeapons())
            if (w.isFunctional())
                guns.add(w);

        String log = game.fireWeapons(craft, enemy, guns, 1, 1, 1);

        assertFalse(log, log.contains("J1.531"));
    }

    @Test
    public void theLookupKnowsWhoIsParkedAndWhoIsNot() {
        ShuttleBay bay = fighterBay();
        Shuttle inside = bay.getInventory().get(1);
        Shuttle parked = park(bay);

        assertTrue(game.isParkedOnBalcony(parked));
        assertFalse("a craft in a box is not parked", game.isParkedOnBalcony(inside));
        assertFalse("nor is a ship", game.isParkedOnBalcony(cva));
        assertFalse("nor is null", game.isParkedOnBalcony(null));
    }

    // ------------------------------------------------- no special-mission preparation

    /**
     * The gap this slice closed. A shuttle being charged as a weasel is still an
     * {@code AdminShuttle} of the same type (J3.18) — only {@code specialRole()} knows
     * otherwise — so the {@code instanceof} check that guarded J1.534 waved it straight out
     * onto the balcony, where J1.531 says no preparation may happen at all.
     */
    @Test
    public void aChargingWeaselCannotBeParked() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = null;
        for (Shuttle s : bay.getInventory())
            if (s.canBecomeWildWeasel()) {
                craft = s;
                break;
            }
        if (craft == null) {
            // The CVA's fighter bays carry fighters, which J3.18 does not let serve. Charge
            // one from the admin bay instead — the rule is about the craft, not the bay.
            for (ShuttleBay b : cva.getShuttles().getBays())
                for (Shuttle s : b.getInventory())
                    if (s.canBecomeWildWeasel()) {
                        craft = s;
                        break;
                    }
            assertNotNull("fixture: the CVA should carry something that can weasel", craft);
            // Give that bay a balcony so the refusal has to come from the ROLE, not from the
            // bay lacking one — otherwise this test would pass for the wrong reason.
            for (ShuttleBay b : cva.getShuttles().getBays())
                if (b.getInventory().contains(craft))
                    b.setBalconyPositions(2);
        }
        craft.incrementWwCharge();
        assertEquals("Wild Weasel", craft.specialRole());

        Game.ActionResult r = game.moveToBalcony(cva, craft.getName());

        assertFalse("J1.534 bars a weasel, charged or converted", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J1.534"));
        assertTrue(r.getMessage(), r.getMessage().contains("Wild Weasel"));
    }

    /** J1.534 names the scatter pack as the role that IS allowed out there. */
    @Test
    public void aScatterPackMayBeParked() {
        ShuttleBay bay = fighterBay();
        Shuttle base = bay.getInventory().get(0);
        com.sfb.objects.shuttles.ScatterPack pack =
                new com.sfb.objects.shuttles.ScatterPack(base);
        pack.setName(base.getName());
        assertTrue(bay.replaceShuttle(base, pack));
        assertEquals("scatterpack", pack.specialRole());

        Game.ActionResult r = game.moveToBalcony(cva, pack.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(bay.isParked(pack));
    }

    /**
     * The Energy Allocation sweep that charges weasels walks the bay INVENTORY, so a parked
     * craft is out of its reach and cannot be prepared (J1.531). Pinned because that sweep is
     * server-side and a future "also check the balcony" would look like a bug fix.
     */
    @Test
    public void thePreparationSweepCannotSeeAParkedCraft() {
        ShuttleBay bay = fighterBay();
        Shuttle parked = park(bay);

        for (ShuttleBay b : cva.getShuttles().getBays())
            assertFalse("a parked craft must not appear in any bay's inventory",
                    b.getInventory().contains(parked));
        assertFalse(cva.getShuttles().getAllShuttles().contains(parked));
    }

    // ------------------------------------------------- no rearming or repair

    /**
     * J1.531: no rearming or repair by deck crews. The end-of-turn pass iterates the bay's
     * SPACES and reads each occupant, and a parked craft occupies none — so it is skipped
     * without the pass knowing balconies exist. Proven by running the real pass and checking
     * the parked craft is never named in its log.
     */
    @Test
    public void theRearmPassSkipsParkedCraft() {
        ShuttleBay bay = fighterBay();
        Shuttle parked = park(bay);
        for (com.sfb.systemgroups.ShuttleSpace space : bay.getSpaces())
            space.setDeckCrews(1);          // crews posted everywhere, so only the rule excludes it

        ShuttleBay.RearmResult result = bay.rearmFighters(game.getCurrentTurn() + 1, null);

        for (String line : result.log())
            assertFalse("the parked craft was worked on: " + line,
                    line.contains(parked.getName()));
        assertNull("and it holds no space to be worked in", bay.findSpace(parked));
    }

    // ------------------------------------------------- no enemy craft

    /** J1.531: "enemy shuttles cannot land or be brought down on the balcony." */
    @Test
    public void anEnemyCraftCannotLandOnTheBalcony() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = park(bay);
        assertTrue(game.launchShuttle(cva, bay, craft, 6, 1).isSuccess());
        craft.setSpeed(0);
        enemy.setLocation(craft.getLocation());
        enemy.setSpeed(0);
        // Give the Klingon a balcony, so the refusal comes from ownership and not from the
        // hull having nowhere to put it.
        enemy.getShuttles().getBays().get(0).setBalconyPositions(2);

        Game.ActionResult r = game.landOnBalcony(enemy, craft.getName());

        assertFalse("a Federation fighter may not park on a Klingon hull", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

}
