package com.sfb.weapons;

import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.PlasmaType;
import com.sfb.systemgroups.DroneStore;
import com.sfb.systemgroups.ReadyRack;
import com.sfb.systemgroups.ShuttleBay;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * FP13.31 K-RAILS: a rail built for the type-K plasma torpedo.
 *
 * <p>"A few fighters (see racial sections) were designed to carry plasma-Ks on light rails,
 * including the Gorn copy of the Shenyang F-7 (R6.F11)." The Gorn <b>G-18E</b> is the first such
 * fighter in the data.
 *
 * <h2>Why PLASMA_K is its own rail type</h2>
 * FP13.31 calls them light rails, but a plain {@code LIGHT} rail means "one type-VI drone"
 * (J4.232), and J4.28 keeps drones and plasma off each other's rails entirely — "type-D plasmas
 * use a unique type of launch rail which cannot carry drones". Overloading LIGHT would make it mean
 * two incompatible things depending on the fighter.
 *
 * <p>Distinct from a type-D rail carrying a K, which is also legal: FP13.3 lets "a plasma-K replace
 * a plasma-D on a launch rail (one for one) of a fighter... that carries plasma-Ds". So a D-rail
 * takes either; a K-rail only ever takes a K.
 *
 * <h2>The size, which is the whole point</h2>
 * FP13.32: "a plasma-K capsule is half of the size of a plasma-D", so it takes half a space in the
 * hold and half a deck crew action to load. The owner's framing: a type-K is to a type-D what a
 * type-VI is to a type-I — the same arithmetic the drone side already does.
 */
public class PlasmaKRailTest {

    @BeforeClass
    public static void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    // ------------------------------------------------------------------ the rail

    @Test
    public void aKRailIsHalfASpaceAndADRailIsAWhole() {
        assertEquals(0.5, DroneRail.DroneRailType.PLASMA_K.capacity, 0.0001);
        assertEquals(1.0, DroneRail.DroneRailType.PLASMA_D.capacity, 0.0001);
    }

    @Test
    public void bothPlasmaRailsCarryPlasmaAndNeitherCarriesDrones() {
        DroneRail k = new DroneRail(DroneRail.DroneRailType.PLASMA_K);
        DroneRail d = new DroneRail(DroneRail.DroneRailType.PLASMA_D);

        assertTrue(k.carriesPlasma());
        assertTrue(d.carriesPlasma());
        assertEquals(PlasmaType.K, k.plasmaType());
        assertEquals(PlasmaType.D, d.plasmaType());

        // J4.28 / J4.825: no drones on a plasma-armed fighter, either way round.
        assertFalse(k.accepts(new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeVI)));
        assertFalse(d.accepts(new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI)));
        assertNull("no design drone to reach for", k.getDesignDrone());
    }

    /** isPlasmaD stays type-D-specific; carriesPlasma is the question almost every caller means. */
    @Test
    public void isPlasmaDIsStillOnlyTheDRail() {
        assertFalse(new DroneRail(DroneRail.DroneRailType.PLASMA_K).isPlasmaD());
        assertTrue(new DroneRail(DroneRail.DroneRailType.PLASMA_D).isPlasmaD());
    }

    @Test
    public void aDroneRailCarriesNoPlasma() {
        DroneRail std = new DroneRail(DroneRail.DroneRailType.STANDARD);
        assertFalse(std.carriesPlasma());
        assertNull(std.plasmaType());
    }

    // ------------------------------------------------------------------ the hold, in spaces

    @Test
    public void theHoldCountsSpacesSoItHoldsTwiceAsManyKs() {
        DroneStore store = new DroneStore(10);
        assertEquals(10, store.stockPlasmaDs(10));

        assertEquals("ten type-Ds at a space each", 10, store.plasmaCount(PlasmaType.D));
        assertEquals("or twenty type-Ks at half a space (FP13.32)",
                20, store.plasmaCount(PlasmaType.K));
        assertEquals(10.0, store.spacesHeld(), 0.0001);
    }

    @Test
    public void drawingAKCostsHalfASpaceAndADCostsAWhole() {
        DroneStore store = new DroneStore(4);
        store.stockPlasmaDs(4);

        assertTrue(store.takePlasma(PlasmaType.K));
        assertEquals(3.5, store.spacesHeld(), 0.0001);

        assertTrue(store.takePlasma(PlasmaType.D));
        assertEquals(2.5, store.spacesHeld(), 0.0001);

        store.putPlasma(PlasmaType.K);
        assertEquals("and back it goes at the same price", 3.0, store.spacesHeld(), 0.0001);
    }

    /** Half a space left will still yield a K and will not yield a D. */
    @Test
    public void halfASpaceIsAKButNotAD() {
        DroneStore store = new DroneStore(1);
        store.stockPlasmaDs(1);
        assertTrue(store.takePlasma(PlasmaType.K));
        assertEquals(0.5, store.spacesHeld(), 0.0001);

        assertFalse("not room for a whole type-D", store.takePlasma(PlasmaType.D));
        assertTrue("but room for one more type-K", store.takePlasma(PlasmaType.K));
        assertTrue(store.isEmpty());
    }

    /** The type-D wrappers every existing caller uses still mean exactly what they did. */
    @Test
    public void theTypeDWrappersAreUnchanged() {
        DroneStore store = new DroneStore(3);
        store.stockPlasmaDs(3);
        assertEquals(3, store.plasmaDCount());
        assertTrue(store.takePlasmaD());
        assertEquals(2, store.plasmaDCount());
        store.putPlasmaD();
        assertEquals(3, store.plasmaDCount());
    }

    // ------------------------------------------------------------------ the G-18E on the hull

    @Test
    public void theGornG18eBuildsWithTwoKRails() {
        Shuttle fighter = ShuttleBay.buildShuttle("g18_e", "Test G-18E");
        assertNotNull("the catalogue should build it", fighter);

        int kRails = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.plasmaType() == PlasmaType.K)
                kRails++;
        assertEquals("FP13.31: two dedicated K-rails", 2, kRails);
    }

    /** Its box stocks type-Ks, not type-Ds — J4.8222 makes a rack serve one kind of fighter. */
    @Test
    public void itsReadyRackStocksTypeKs() {
        ReadyRack rack = ReadyRack.forType("g18_e");
        assertNotNull("a K-rail fighter's box gets a rack (J4.822)", rack);
        assertTrue(rack.carriesPlasma());
        assertEquals(PlasmaType.K, rack.plasmaType());
        assertFalse("and it is not a type-D rack", rack.isPlasmaD());
        assertEquals("one torpedo a rail", 2, rack.capacity());
        assertEquals("full on arrival (J4.886)", 2, rack.count());
        assertEquals("two type-Ks are one space (FP13.32)", 1.0, rack.spaces(), 0.0001);
    }

    /** And the plain G-18 beside it is a type-D fighter, so the two do not blur together. */
    @Test
    public void thePlainG18IsStillATypeDFighter() {
        ReadyRack rack = ReadyRack.forType("g18");
        assertNotNull(rack);
        assertEquals(PlasmaType.D, rack.plasmaType());
        assertTrue(rack.isPlasmaD());
        assertEquals("two type-Ds are two spaces (FP9.21)", 2.0, rack.spaces(), 0.0001);
    }
}
