package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Haas;
import com.sfb.properties.Location;

/**
 * A fighter's per-turn state actually resetting at the turn boundary.
 * <p>
 * {@code Fighter.startTurn()} has always existed and nothing ever called it. Ships get
 * {@code ship.startTurn()} from {@code Game.startTurn()}; shuttles got only
 * {@code attachClock}, so every counter that method clears was cleared exactly never:
 * <ul>
 *   <li>{@code dronesFiredThisTurn} — J4.241 allows two drones A TURN. Unreset, a fighter
 *       that spent its pair on turn one could never launch another for the rest of the
 *       game.</li>
 *   <li>{@code firstDroneTarget} — J4.241's "both at the same target" test, comparing
 *       against a target chosen turns ago and quite possibly already dead.</li>
 *   <li>{@code firedDogfightDroneThisTurn} — the other half of the same rule.</li>
 *   <li>{@code tacticalManeuverUsed} — J4.12 gives one a TURN; a fighter got one a game.</li>
 *   <li>{@code getWeapons().cleanUp()} — the per-turn weapon housekeeping every ship has
 *       always had.</li>
 * </ul>
 * Silent in every case: nothing throws, nothing logs, the fighter just quietly stops being
 * allowed to do things.
 */
public class FighterTurnResetTest {

    private Game game;
    private Ship carrier;
    private Haas fighter;

    @Before
    public void setUp() {
        game = new Game();
        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kzinti);
        game.getShips().add(carrier);

        fighter = new Haas();
        fighter.setName("HAAS-1");
        fighter.setOwner(kzinti);
        fighter.setLocation(new Location(10, 12));
        fighter.setFacing(13);
        game.getActiveShuttles().add(fighter);

        game.startTurn();
    }

    /** J4.12: "change facing freely, once per turn" — a turn, not a career. */
    @Test
    public void theTacticalManeuverComesBackEachTurn() {
        assertTrue(fighter.performTacticalManeuver(5));
        assertFalse("used up for this turn", fighter.performTacticalManeuver(9));

        game.startTurn();

        assertFalse("the flag should have cleared", fighter.isTacticalManeuverUsed());
        assertTrue("and the maneuver be available again", fighter.performTacticalManeuver(13));
    }

    /** J4.241: two drones a turn. The count has to go back to nothing. */
    @Test
    public void theDroneAllowanceComesBackEachTurn() {
        fighter.recordDroneFired(null, null, game.getAbsoluteImpulse());
        fighter.recordDroneFired(null, null, game.getAbsoluteImpulse());
        assertEquals(2, fighter.getDronesFiredThisTurn());

        game.startTurn();

        assertEquals("J4.241's pair is per TURN", 0, fighter.getDronesFiredThisTurn());
    }

    /** A fighter in a bay is swept too, so it launches into a clean turn. */
    @Test
    public void aFighterInABayIsResetAsWell() throws Exception {
        // A battlecruiser keeps admin shuttles; for a fighter in a bay we need a carrier.
        Ship cvs = com.sfb.objects.ShipLibrary.createShip(
                com.sfb.objects.ShipSpec.fromJson(
                        new java.io.File("../data/factions/kzinti/cvs.json")));
        cvs.setName("KHS Watchful");
        cvs.setLocation(new Location(12, 10));
        cvs.setFacing(1);
        cvs.setOwner(carrier.getOwner());
        game.getShips().add(cvs);

        com.sfb.objects.shuttles.Fighter inBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : cvs.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getInventory())
                if (inBay == null && craft instanceof com.sfb.objects.shuttles.Fighter f)
                    inBay = f;
        assertNotNull("fixture needs a fighter in a bay", inBay);

        assertTrue(inBay.performTacticalManeuver(5));

        game.startTurn();

        assertFalse("swept in the bay, not only on the map",
                inBay.isTacticalManeuverUsed());
    }
}
