package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * FP9.22's activation energy as ONE allocation line covering every place a type-D torpedo can
 * sit aboard a ship.
 *
 * <h2>Why one line and not two</h2>
 * FP9.22 is a single rule with several homes: "When placed on a fighter ready rack, plasma rack,
 * or fighter, they can be activated, which requires 1/2 of an energy point (reserve or allocated)
 * per torpedo." A player allocating that energy is not choosing between a rack and a squadron,
 * and the rule gives no reason to make them, so {@code Ship.plasmaActivationWanted} sums the
 * ship's plasma racks and its fighters' rails together.
 *
 * <p>The READY RACK is deliberately left out, and FP10.33 is the reason: "an activated torpedo
 * automatically switches itself off when unloaded from a rack/fighter and requires new activation
 * energy after being installed on another rack/fighter." A torpedo paid for in the ready rack
 * loses it the instant a deck crew moves it to the fighter, so paying there buys nothing.
 *
 * <h2>Why it is the only fractional line</h2>
 * Half a point per torpedo, where every other allocation line is whole points. That is real
 * rather than an artefact: a torpedo is activated or it is not, so the halves have to be tracked
 * as halves and a quarter point left over buys nothing.
 */
public class PlasmaActivationAllocationTest {

    private Ship krv;

    @Before
    public void loadTheCarrier() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        krv = ShipLibrary.createShip(ShipLibrary.get("Romulan", "KRV"));
    }

    /**
     * Arm the fighters from their own boxes, which is what a deck crew does in play.
     * <p>
     * Needed because a carrier is built with its fighters EMPTY and the torpedoes in the ready
     * racks behind them (J4.8223, J4.886: "the stores forward and the fighters empty"). So a
     * freshly-loaded KRV wants activation energy for its RACKS and none at all for its squadron -
     * correct, and not what a test about covering both homes can use as a fixture.
     */
    private void armTheSquadron() {
        for (com.sfb.systemgroups.ShuttleBay bay : krv.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() != null)
                    box.armOccupantFully();
    }

    private java.util.List<PlasmaRack> racksOf(Ship ship) {
        java.util.List<PlasmaRack> racks = new java.util.ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaRack rack)
                racks.add(rack);
        return racks;
    }

    /**
     * The KRV is the fixture because it has both homes at once: two plasma racks of four
     * torpedoes, and a bay of Gladiator-Fs with two plasma-D rails apiece. Exactly the ship the
     * single line exists for.
     */
    @Test
    public void theKrvHasBothKindsOfMountAboard() {
        assertEquals("two plasma racks (FP10.12: usually LS and RS)", 2, racksOf(krv).size());

        // Empty on arrival, so nothing to activate out there yet - J4.8223/J4.886 put the
        // torpedoes in the ready racks and leave the fighters bare.
        assertEquals("the squadron starts with nothing loaded",
                0.0, krv.getShuttles().plasmaDActivationWanted(), 0.001);

        armTheSquadron();
        assertTrue("and wants activation once a deck crew has loaded it",
                krv.getShuttles().plasmaDActivationWanted() > 0);
    }

    /**
     * The ceiling is the sum of both, in half points. Asserted as the sum of its two parts rather
     * than as a literal, so adding a rack or re-seating the squadron cannot make the test wrong
     * while the rule stays right.
     */
    @Test
    public void theCeilingCoversTheRacksAndTheFightersTogether() {
        armTheSquadron();
        double racks = 0;
        for (PlasmaRack rack : racksOf(krv))
            racks += rack.activationEnergyWanted();
        double fighters = krv.getShuttles().plasmaDActivationWanted();

        assertEquals(racks + fighters, krv.plasmaActivationWanted(), 0.001);

        // Both parts are real, so neither half of the sum is zero and passing cannot be an
        // accident of one of them being absent.
        assertEquals("two racks of four, half a point each", 4.0, racks, 0.001);
        assertTrue("and the squadron wants some too", fighters > 0);
    }

    /** Half a point buys one torpedo, in the rack as on a rail. */
    @Test
    public void halfAPointActivatesOneTorpedo() {
        PlasmaRack rack = racksOf(krv).get(0);
        assertEquals(0, rack.getActiveTorpedoes());

        double spent = krv.activatePlasmaTorpedoes(0.5);

        assertEquals(0.5, spent, 0.001);
        assertEquals(1, rack.getActiveTorpedoes());
    }

    /**
     * The ship's own racks are served before the fighters' rails.
     * <p>
     * A rack's torpedo can be fired this turn by the ship itself; a fighter's cannot be used
     * until the fighter launches, and J1.341 holds it back half a turn after that. So when the
     * energy runs short, the torpedoes that could matter this turn are the ones that get it.
     */
    @Test
    public void theShipsOwnRacksArePaidForFirst() {
        armTheSquadron();
        double racksWant = 0;
        for (PlasmaRack rack : racksOf(krv))
            racksWant += rack.activationEnergyWanted();

        double spent = krv.activatePlasmaTorpedoes(racksWant);

        assertEquals(racksWant, spent, 0.001);
        for (PlasmaRack rack : racksOf(krv))
            assertEquals("every rack full", PlasmaRack.CAPACITY, rack.getActiveTorpedoes());
        assertEquals("and nothing went to the squadron yet",
                krv.getShuttles().plasmaDActivationWanted(),
                krv.plasmaActivationWanted() , 0.001);
    }

    /** Pay for everything and nothing is left wanting. */
    @Test
    public void payingTheWholeCeilingActivatesEverythingAboard() {
        armTheSquadron();
        double wanted = krv.plasmaActivationWanted();

        double spent = krv.activatePlasmaTorpedoes(wanted);

        assertEquals(wanted, spent, 0.001);
        assertEquals("nothing left to buy", 0.0, krv.plasmaActivationWanted(), 0.001);
    }

    /**
     * A quarter point buys nothing and is not consumed. There is no state between inactive and
     * active for a part payment to sit in, so the energy has to come back rather than vanish.
     */
    @Test
    public void aPartChargeBuysNothingAndIsNotSpent() {
        double spent = krv.activatePlasmaTorpedoes(0.25);

        assertEquals("nothing bought", 0.0, spent, 0.001);
        assertEquals("and nothing activated anywhere",
                0, racksOf(krv).get(0).getActiveTorpedoes());
    }

    /** Offering more than the ship can use spends only what there was to buy. */
    @Test
    public void surplusEnergyIsNotConsumed() {
        armTheSquadron();
        double wanted = krv.plasmaActivationWanted();

        double spent = krv.activatePlasmaTorpedoes(wanted + 10);

        assertEquals(wanted, spent, 0.001);
    }

    /**
     * A ship with no type-Ds anywhere wants nothing — which is what keeps the line off every
     * other allocation dialog in the game.
     */
    @Test
    public void aShipWithNoTypeDsWantsNothing() {
        Ship fedCa = new Ship();
        fedCa.init(com.sfb.samples.FederationShips.getFedCa());

        assertEquals(0.0, fedCa.plasmaActivationWanted(), 0.001);
        assertEquals(0.0, fedCa.activatePlasmaTorpedoes(5), 0.001);
    }
}
