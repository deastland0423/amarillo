package com.sfb.weapons;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.systemgroups.ShuttleBay;

/**
 * FP9.22 and FP10.32: a type-D torpedo does nothing until it is activated, and activation
 * costs half an energy point.
 * <blockquote>
 * "When placed on a fighter ready rack, plasma rack, or fighter, they can be activated, which
 * requires 1/2 of an energy point (reserve or allocated) per torpedo. <b>The weapon cannot be
 * launched until it has been activated.</b>" (FP9.22)
 * <br>
 * "This can be supplied during energy allocation or by reserve power at any point after
 * loading and before firing. Torpedoes activated by reserve power can be fired immediately
 * (within the Sequence of Play)." (FP10.32)
 * </blockquote>
 * Two consequences shape the design.
 * <p>
 * <b>Half a point, not one.</b> Every other figure in the fighter energy accounting is a whole
 * point, so a torpedo priced at 1 would silently double what a carrier pays to make its
 * squadron dangerous. It is a {@code double} constant for that reason alone.
 * <p>
 * <b>The activation belongs to the mount, not the torpedo.</b> FP10.33: "an activated torpedo
 * automatically switches itself off when unloaded from a rack/fighter and requires new
 * activation energy after being installed on another rack/fighter." Holding the flag on the
 * rail gets that for free - a torpedo carries no state to leak into its next mount - and
 * {@link #unloadingSwitchesTheTorpedoOff} is what pins it.
 * <p>
 * NOT in this slice: launching the thing. The gate ({@link DroneRail#canLaunchTorpedo}) is
 * here for the launch to consult, but a fighter cannot yet fire a plasma-D from a rail - that
 * is the next piece, along with loading torpedoes out of the carrier's drone stores (J4.825
 * puts them under the drone storage rules, so the pool already exists).
 */
public class PlasmaDActivationTest {

    @Before
    public void loadData() throws Exception {
        ShuttleCatalog.loadDefault("../data");
    }

    private static DroneRail loadedRail() {
        DroneRail rail = new DroneRail(DroneRail.DroneRailType.PLASMA_D);
        rail.setDesignator("A");
        rail.loadTorpedo(torpedo());
        return rail;
    }

    private static PlasmaTorpedo torpedo() {
        return new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
    }

    // ---------------------------------------------------------------- the gate

    /** Loaded is not armed: FP9.22's "cannot be launched until it has been activated". */
    @Test
    public void aLoadedTorpedoCannotLaunchUntilActivated() {
        DroneRail rail = loadedRail();

        assertTrue("it is on the rail", rail.isLoaded());
        assertFalse("but inert", rail.isTorpedoActivated());
        assertFalse("and may not be launched (FP9.22)", rail.canLaunchTorpedo());
    }

    @Test
    public void halfAPointActivatesIt() {
        DroneRail rail = loadedRail();

        assertEquals("half a point, not one", 0.5, DroneRail.ACTIVATION_ENERGY, 0.001);
        assertEquals(0.5, rail.activationEnergyWanted(), 0.001);
        assertTrue(rail.activateTorpedo(DroneRail.ACTIVATION_ENERGY));

        assertTrue(rail.isTorpedoActivated());
        assertTrue(rail.canLaunchTorpedo());
        assertEquals("and wants nothing more", 0, rail.activationEnergyWanted(), 0.001);
    }

    /** Short of half a point buys nothing at all, rather than part-arming it. */
    @Test
    public void aQuarterPointBuysNothing() {
        DroneRail rail = loadedRail();

        assertFalse(rail.activateTorpedo(0.25));

        assertFalse(rail.isTorpedoActivated());
        assertEquals("still owed in full", 0.5, rail.activationEnergyWanted(), 0.001);
    }

    /** An empty rail has nothing to activate and asks for nothing. */
    @Test
    public void anEmptyRailWantsNoActivationEnergy() {
        DroneRail rail = new DroneRail(DroneRail.DroneRailType.PLASMA_D);

        assertEquals(0, rail.activationEnergyWanted(), 0.001);
        assertFalse(rail.activateTorpedo(1.0));
        assertFalse(rail.canLaunchTorpedo());
    }

    /** Paying twice is not an error, it is simply nothing to buy. */
    @Test
    public void anAlreadyActiveTorpedoTakesNoMoreEnergy() {
        DroneRail rail = loadedRail();
        rail.activateTorpedo(0.5);

        assertFalse("nothing to do", rail.activateTorpedo(0.5));
        assertEquals(0, rail.activationEnergyWanted(), 0.001);
        assertTrue(rail.canLaunchTorpedo());
    }

    /** An ordinary drone rail has no such notion: a drone needs no activation. */
    @Test
    public void aDroneRailNeedsNoActivation() {
        DroneRail standard = new DroneRail(DroneRail.DroneRailType.STANDARD);

        assertEquals(0, standard.activationEnergyWanted(), 0.001);
        assertFalse("and the plasma gate never opens for it", standard.canLaunchTorpedo());
    }

    // ---------------------------------------------------------------- FP9.22's last clause

    /**
     * "Torpedoes on fighters assumed to be loaded before a scenario (due to weapon status) are
     * assumed to be active." A deck crew's mid-game reload is NOT - it needs its half point.
     */
    @Test
    public void aTorpedoLoadedBeforeTheScenarioIsAlreadyActive() {
        DroneRail rail = new DroneRail(DroneRail.DroneRailType.PLASMA_D);

        rail.loadTorpedo(torpedo(), true);

        assertTrue(rail.isTorpedoActivated());
        assertTrue(rail.canLaunchTorpedo());
        assertEquals("nothing owed for it", 0, rail.activationEnergyWanted(), 0.001);
    }

    // ---------------------------------------------------------------- FP10.33

    /**
     * "An activated torpedo automatically switches itself off when unloaded from a
     * rack/fighter and requires new activation energy after being installed on another
     * rack/fighter." The same torpedo, a second mount, and the carrier pays again.
     */
    @Test
    public void unloadingSwitchesTheTorpedoOff() {
        DroneRail first = loadedRail();
        first.activateTorpedo(0.5);
        assertTrue(first.canLaunchTorpedo());

        PlasmaTorpedo moved = first.removeTorpedo();

        assertFalse("the rail it left is inert", first.isTorpedoActivated());

        DroneRail second = new DroneRail(DroneRail.DroneRailType.PLASMA_D);
        second.loadTorpedo(moved);

        assertFalse("and it arrives switched off (FP10.33)", second.isTorpedoActivated());
        assertEquals("needing its half point again", 0.5,
                second.activationEnergyWanted(), 0.001);
    }

    // ---------------------------------------------------------------- what the ship pays

    /** A G-F with two loaded rails wants a whole point: half a torpedo apiece. */
    @Test
    public void aGladiatorFWithBothRailsLoadedWantsOnePoint() {
        Fighter gf = CataloguedFighter.of("gf");
        ShuttleBay bay = new ShuttleBay(null);
        bay.addSpace(new com.sfb.systemgroups.ShuttleSpace(gf));
        com.sfb.systemgroups.Shuttles shuttles = new com.sfb.systemgroups.Shuttles(null);
        shuttles.getBays().add(bay);
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                rail.loadTorpedo(torpedo());

        assertEquals("two torpedoes, half a point each", 1.0,
                shuttles.plasmaDActivationWanted(), 0.001);

        double spent = shuttles.activatePlasmaDs(1.0);

        assertEquals(1.0, spent, 0.001);
        assertEquals(0, shuttles.plasmaDActivationWanted(), 0.001);
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                assertTrue("both ready to fire", rail.canLaunchTorpedo());
    }

    /** A short offer activates what it covers and leaves the rest owed. */
    @Test
    public void aShortOfferActivatesOnlyWhatItCovers() {
        Fighter gf = CataloguedFighter.of("gf");
        ShuttleBay bay = new ShuttleBay(null);
        bay.addSpace(new com.sfb.systemgroups.ShuttleSpace(gf));
        com.sfb.systemgroups.Shuttles shuttles = new com.sfb.systemgroups.Shuttles(null);
        shuttles.getBays().add(bay);
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                rail.loadTorpedo(torpedo());

        double spent = shuttles.activatePlasmaDs(0.5);

        assertEquals("one torpedo's worth", 0.5, spent, 0.001);
        assertEquals("the other still owed", 0.5,
                shuttles.plasmaDActivationWanted(), 0.001);
    }

    /**
     * A craft on the balcony counts too. J1.53 lets a carrier launch a parked squadron at
     * once, and FP10.32 allows activation "at any point after loading and before firing" - so
     * a torpedo out on the track is exactly one worth paying for. J1.531 bars deck crew work
     * out there, not the ship's own energy.
     */
    @Test
    public void aTorpedoOnAParkedFighterIsStillWorthActivating() {
        Fighter gf = CataloguedFighter.of("gf");
        gf.setName("GF-1");
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                rail.loadTorpedo(torpedo());

        ShuttleBay bay = new ShuttleBay(null);
        bay.setBalconyPositions(2);
        assertTrue(bay.park(gf));
        com.sfb.systemgroups.Shuttles shuttles = new com.sfb.systemgroups.Shuttles(null);
        shuttles.getBays().add(bay);

        assertEquals("parked, and still asking", 1.0,
                shuttles.plasmaDActivationWanted(), 0.001);
        assertEquals(1.0, shuttles.activatePlasmaDs(1.0), 0.001);
    }

    /** A carrier with no plasma-D fighters asks for nothing: the Warhawk flies plasma-Fs. */
    @Test
    public void aCarrierWithNoPlasmaDFightersWantsNothing() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        Ship wh = ShipLibrary.createShip(ShipLibrary.get("Romulan", "WH"));

        assertEquals(0, wh.getShuttles().plasmaDActivationWanted(), 0.001);
    }
}
