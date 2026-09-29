package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger_E;

/**
 * The Hydran RN+ as its ship file describes it: a fully capable carrier whose standard
 * complement includes one Stinger-E (R1.F7).
 * <p>
 * Nine fighters is close to a J4.463 boundary in both halves of that rule — a carrier
 * designed for fewer than eight may field NO EW fighter, and a squadron of fewer than eight
 * may hold none either. Nine clears both by one, so the ship is legal by a single fighter and
 * a drift in either direction would make its standard loadout illegal. Hence a test.
 */
public class HydranRnPlusTest {

    @BeforeClass
    public static void loadShipFiles() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private Ship rnPlus() {
        ShipSpec spec = ShipLibrary.get("hydran", "RN+");
        assertNotNull("the Hydran RN+ should load from data/factions", spec);
        return ShipLibrary.createShip(spec);
    }

    @Test
    public void theRnPlusCarriesNineFightersAndOneIsAStingerE() {
        Ship ship = rnPlus();
        int fighters = 0, ewf = 0, admin = 0;
        for (Shuttle craft : ship.getShuttles().getAllShuttles()) {
            if (craft instanceof Stinger_E)
                ewf++;
            if (craft instanceof Fighter)
                fighters++;
            else
                admin++;
        }
        assertEquals("1 Stinger-E + 2 Stinger-H + 6 Stinger-2", 9, fighters);
        assertEquals(1, ewf);
        assertEquals("three admin shuttles as well", 3, admin);
    }

    /** J4.463: nine designed fighters entitles it to exactly one EW fighter. */
    @Test
    public void nineFightersEntitleItToTheOneEwFighterItCarries() {
        com.sfb.systemgroups.Shuttles shuttles = rnPlus().getShuttles();

        assertEquals(9, shuttles.getDesignedFighterComplement());
        assertEquals("under sixteen, so one (J4.463)", 1, shuttles.allowedEwFighters());
        assertEquals("and it fields exactly that", 1, shuttles.ewFightersAboard());
    }

    /**
     * J4.461 organises into the minimum number of squadrons, so nine fighters make one of
     * nine — which is the other thing the Stinger-E needs, since J4.463 bars an EW fighter
     * from a squadron of fewer than eight.
     */
    @Test
    public void theStingerEsSquadronIsLargeEnoughToHoldIt() {
        com.sfb.systemgroups.Shuttles shuttles = rnPlus().getShuttles();
        List<Squadron> squadrons = shuttles.getSquadrons();

        assertEquals("twelve is the cap, so nine fit in one (J4.462)", 1, squadrons.size());
        Squadron only = squadrons.get(0);
        assertEquals(9, only.size());
        assertTrue("eight or more (J4.463)", only.largeEnoughForEwFighter());
        assertEquals(1, only.ewFighterCount());
    }

    /** J4.931/J4.6: lending EW to a squadron is a CAPABLE carrier's privilege. */
    @Test
    public void itIsAFullyCapableCarrier() {
        assertTrue(rnPlus().getCarrierClass().isCarrier());
    }

    /**
     * Hydran fighters carry no drones at all, so nothing behind them needs stocking — no
     * ready racks (J4.822) and no J4.7 drone stores.
     */
    @Test
    public void aHydranCarrierNeedsNoDroneStores() {
        Ship ship = rnPlus();
        for (Shuttle craft : ship.getShuttles().getAllShuttles())
            if (craft instanceof Fighter fighter)
                assertNull(fighter.getName() + " should need no reload",
                        com.sfb.systemgroups.ReadyRack.forFighter(fighter));
    }
}
