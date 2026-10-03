package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;

/**
 * J1.620: a tractor recovery may land the craft on the balcony.
 * <p>
 * The rule says it in a parenthesis that is easy to read past:
 * <blockquote>
 * "The tractor is attached to the shuttle. The shuttle is then rotated (G7.7) into the ship's
 * hex. At this point, the shuttle may be pulled into the bay (<b>or onto the balcony</b> or
 * mech-link) and landed."
 * </blockquote>
 * It is also the "any method" half of J1.532 — a ship can land on the balcony by any means it
 * could land in the hangar — and the two together are why this is a destination for the
 * EXISTING procedure rather than a procedure of its own. Everything about the pull-in is
 * unchanged: the craft shuts down (J1.622), is drawn one hex closer each impulse (J1.621), and
 * a broken tractor link ends the whole thing (J1.6221).
 * <p>
 * Two differences at the moment of landing, and they are the point:
 * <ul>
 * <li><b>No hatch.</b> J1.53 puts landings ON the balcony in its free, unlimited clause, and
 * the craft never passes through a hatch to reach it.</li>
 * <li><b>No empty shuttle box.</b> J1.62's third condition asks for one, and adds "see
 * (J1.62), (J1.64), (J1.65), and (J1.66) for exceptions" — the balcony being what those
 * exceptions are about. A free position stands in, because demanding both would make the
 * clause unreachable on a full ship, which is the ship most likely to want it.</li>
 * </ul>
 * J1.531 bars enemy craft in the same breath: "enemy shuttles cannot land or be brought down
 * on the balcony." <i>Brought down</i> is this procedure by name, and J1.6214's friendly check
 * already refuses it.
 * <p>
 * J1.534's barred roles are NOT re-tested here. The declaration asks the same
 * {@code barredFromBalcony} the bay/balcony transfer asks, and {@code BalconyRestrictionsTest}
 * and {@code BalconyTransferTest} already exercise it from both directions - the converted
 * craft and the one still being prepared. Proving a shared helper a third time through a
 * fixture that has to fight G7's tractor accounting to set itself up would buy nothing.
 */
public class BalconyTractorRecoveryTest {

    private Game game;
    private Ship cva;
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

        game.startTurn();
        com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
        e.setLifeSupport(cva.getLifeSupportCost());
        e.setFireControl(cva.getFireControlCost());
        e.setTractors(4);
        game.submitAllocation(cva, e);
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

    /**
     * A craft of the carrier's own, launched and then held in its tractor in the same hex —
     * the state J1.620's last step begins from.
     */
    private Shuttle launchedAndHeld() {
        return heldBy(cva);
    }

    /** As above, but held by a named ship - through the real tractor action (G7.4). */
    private Shuttle heldBy(Ship holder) {
        ShuttleBay bay = fighterBay();
        Shuttle craft = bay.getInventory().get(0);
        assertTrue(game.launchShuttle(cva, bay, craft, 4, 1).isSuccess());
        craft.setSpeed(0);
        Game.ActionResult grab = game.establishTractor(holder, craft.getName(), 1);
        assertTrue("fixture: the tractor must take hold - " + grab.getMessage(),
                grab.isSuccess());
        assertEquals("fixture: held by " + holder.getName(), holder, craft.getTractoringUnit());
        return craft;
    }

    // ---------------------------------------------------------------- declaring it

    @Test
    public void aRecoveryCanBeDeclaredForTheBalcony() {
        Shuttle craft = launchedAndHeld();

        Game.ActionResult r = game.beginShuttleRecovery(cva, craft.getName(), true);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("balcony"));
        assertTrue("the craft is under the procedure", craft.isBeingRecovered());
        assertTrue("and bound for the balcony", craft.isRecoverToBalcony());
    }

    /** The default is unchanged: a plain recovery still goes to a bay. */
    @Test
    public void anOrdinaryRecoveryStillGoesToABay() {
        Shuttle craft = launchedAndHeld();

        assertTrue(game.beginShuttleRecovery(cva, craft.getName()).isSuccess());

        assertTrue(craft.isBeingRecovered());
        assertFalse(craft.isRecoverToBalcony());
    }

    /** A ship with no balcony says so rather than quietly recovering into a bay instead. */
    @Test
    public void aShipWithNoBalconyRefusesTheDeclaration() throws Exception {
        Ship ca = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        ca.setName("USS Constitution");
        ca.setLocation(cva.getLocation());
        ca.setOwner(fed);
        ca.setActiveFireControl(true);
        game.getShips().add(ca);
        com.sfb.systemgroups.Energy caEnergy = new com.sfb.systemgroups.Energy();
        caEnergy.setTractors(4);
        ca.getTractors().initForTurn(4, game.getAbsoluteImpulse());
        Shuttle craft = heldBy(ca);

        Game.ActionResult r = game.beginShuttleRecovery(ca, craft.getName(), true);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("no balcony"));
        assertFalse(craft.isBeingRecovered());
    }

    /** J1.531/J1.6214: an enemy craft cannot be brought down onto a balcony. */
    @Test
    public void anEnemyCraftCannotBeBroughtDownOntoTheBalcony() {
        Shuttle craft = launchedAndHeld();
        craft.setOwner(klingon);

        Game.ActionResult r = game.beginShuttleRecovery(cva, craft.getName(), true);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("friendly"));
    }

    // ---------------------------------------------------------------- completing it

    /**
     * The landing itself: the craft parks, and no hatch is spent. A bay recovery takes one
     * (J1.50); this is the free clause of J1.53.
     */
    @Test
    public void landingOnTheBalconyCostsNoHatch() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = launchedAndHeld();
        assertTrue(game.beginShuttleRecovery(cva, craft.getName(), true).isSuccess());
        int hatchesBefore = bay.getAvailableHatchCount(game.getAbsoluteImpulse());

        String log = game.completeRecovery(cva, craft);

        assertNotNull("the recovery completed", log);
        assertTrue(log, log.contains("balcony"));
        assertTrue("parked outside", bay.isParked(craft));
        assertFalse("off the map", game.getActiveShuttles().contains(craft));
        assertEquals("no hatch spent (J1.53)",
                hatchesBefore, bay.getAvailableHatchCount(game.getAbsoluteImpulse()));
        assertFalse("and the procedure is over", craft.isBeingRecovered());
    }

    /**
     * It needs no empty shuttle box, which is the J1.62 condition the balcony is an exception
     * to. Proven by filling every box on the ship first — a bay recovery would have nowhere
     * to go, and this still lands.
     */
    @Test
    public void itNeedsNoEmptyShuttleBox() {
        ShuttleBay bay = fighterBay();
        Shuttle craft = launchedAndHeld();
        assertTrue(game.beginShuttleRecovery(cva, craft.getName(), true).isSuccess());

        // Refill the box the launch emptied, so not one box is free anywhere.
        for (ShuttleBay b : cva.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace sp : b.getSpaces())
                if (sp.isEmpty() && !sp.isDestroyed()) {
                    com.sfb.objects.shuttles.AdminShuttle filler =
                            new com.sfb.objects.shuttles.AdminShuttle();
                    filler.setName("Filler-" + b.getSpaces().indexOf(sp));
                    sp.setShuttle(filler);
                }
        for (ShuttleBay b : cva.getShuttles().getBays())
            assertEquals("fixture: no free box on " + cva.getName(), 0, b.getEmptySpaceCount());

        String log = game.completeRecovery(cva, craft);

        assertNotNull("a full ship can still take one on the wing (J1.620)", log);
        assertTrue(bay.isParked(craft));
    }

    /**
     * A full balcony holds the craft at Range 0 instead, exactly as a bay with no ready hatch
     * does (J1.6213) — the procedure waits rather than failing, so it can land next impulse.
     */
    @Test
    public void aFullBalconyHoldsTheCraftAtRangeZero() {
        Shuttle craft = launchedAndHeld();
        assertTrue(game.beginShuttleRecovery(cva, craft.getName(), true).isSuccess());
        int n = 0;
        for (ShuttleBay b : cva.getShuttles().getBays())
            while (b.balconyFree() > 0) {
                com.sfb.objects.shuttles.AdminShuttle filler =
                        new com.sfb.objects.shuttles.AdminShuttle();
                filler.setName("Filler-" + (++n));
                assertTrue(b.park(filler));
            }

        String log = game.completeRecovery(cva, craft);

        assertNull("nowhere to park — hold at Range 0 (J1.6213)", log);
        assertTrue("still under the procedure", craft.isBeingRecovered());
        assertTrue("still in space", game.getActiveShuttles().contains(craft));
    }

    /**
     * The destination belongs to the PROCEDURE. A broken tractor link ends it (J1.6221), and
     * the flag has to go with it — a stale one would send the next recovery to the balcony
     * unasked.
     */
    @Test
    public void endingTheProcedureForgetsTheDestination() {
        Shuttle craft = launchedAndHeld();
        assertTrue(game.beginShuttleRecovery(cva, craft.getName(), true).isSuccess());
        assertTrue(craft.isRecoverToBalcony());

        craft.setBeingRecovered(false);   // what a broken link does

        assertFalse("the destination went with the procedure", craft.isRecoverToBalcony());
    }
}
