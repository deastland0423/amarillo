package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.Terrain;
import com.sfb.properties.TerrainType;
import com.sfb.samples.FederationShips;

/**
 * A small ground base allocates its own energy, and pays nothing for fire control (D6.631).
 *
 * <h2>The two rules</h2>
 * <b>D6.631</b>: "PFs, shuttles (including fighters), captor mines, small and medium ground bases,
 * self-guiding seeking weapons, and certain other units noted in the rules do not allocate energy
 * for active fire control." Of those categories only ground bases are Ships in this model.
 *
 * <p><b>D17.123</b> draws the boundary: "Small and medium ground bases [size 5 units, see (P2.76)
 * and (R1.28)] count as PFs for this purpose. Size class 4 and larger bases on a planetary surface
 * are treated as ships." The nine orbital Base Stations are size class 3, so they pay as usual.
 *
 * <h2>Two fixes, and which one did what</h2>
 * Six phaser batteries on Nivram came up on PASSIVE fire control, close to ornamental, and under
 * P2.713 could not even be bombarded. The cause was that NOTHING allocated energy for them —
 * {@code allocateEnergy} is what raises fire control, and no caller ever reached a base. The
 * automatic allocation below is what fixes that.
 *
 * <p>D6.631 is the separate, smaller correction: a base should not spend the point in the first
 * place. A GBD2 has eight APR against a three-point capacitor, so it never noticed the charge, but
 * a tighter sheet would. Removing the exemption fails exactly one test here, and that is the
 * honest measure of it — the rule is right, the consequence is modest.
 *
 * <h2>What the player does not lose to the automatic allocation</h2>
 * Only EW looks forfeited, and D6.312 answers it: "RESERVE POWER (H7.2) may be used to increase
 * ECM or ECCM strength during the Fire Decision Step of any impulse." Unspent power is therefore
 * still EW later, decided with better information than allocation time offers.
 */
public class GroundBaseAllocationTest {

    private Game game;
    private Terrain planet;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();
        planet = new Terrain(TerrainType.PLANET, 20, 20, 1);
        game.addTerrain(planet);
    }

    /**
     * Site a base on the planet. {@code placeGroundBase} ENROLS the base in the game itself, so
     * nothing here may add it again — doing both is what once put every battery on Nivram twice,
     * and it hid in this fixture until a mutation run printed a queue of twelve for six bases.
     */
    private Ship groundBase(String type, int face) {
        ShipSpec spec = ShipLibrary.get("Neutral", type);
        assertNotNull("fixture: " + type + " should be in the library", spec);
        Ship base = ShipLibrary.createShip(spec);
        base.setName(type + "-" + face);
        assertTrue(game.placeGroundBase(base, planet, face).isSuccess());
        assertTrue("fixture: placeGroundBase should have enrolled it",
                game.getShips().contains(base));
        return base;
    }

    // ---------------------------------------------------------------- D6.631

    @Test
    public void aSmallGroundBasePaysNothingForFireControl() {
        assertEquals(0, groundBase("GBD2", 1).getFireControlCost());
    }

    /** D17.123: a size-class-3 orbital base is treated as a ship and pays the point. */
    @Test
    public void anOrbitalBaseStationStillPaysForFireControl() {
        ShipSpec spec = ShipLibrary.get("Federation", "BS");
        assertNotNull("fixture: the Federation Base Station should be in the library", spec);
        Ship bs = ShipLibrary.createShip(spec);

        assertEquals("size class 3, so not a small ground base", 3, bs.getSizeClass());
        assertFalse(bs.isSmallGroundBase());
        assertEquals(1, bs.getFireControlCost());
    }

    /** A CA built from the sample spec, the way every other test in this package does it. */
    private static Ship fedCa() {
        Ship ca = new Ship();
        ca.init(FederationShips.getFedCa());
        ca.setName("USS Probe");
        return ca;
    }

    @Test
    public void anOrdinaryShipStillPaysForFireControl() {
        assertEquals(1, fedCa().getFireControlCost());
    }

    /**
     * The payoff, and the symptom that exposed the bug: the base is on ACTIVE fire control once
     * the turn starts, with nobody having allocated anything for it.
     */
    @Test
    public void aGroundBaseComesUpOnActiveFireControl() {
        Ship base = groundBase("GBD2", 1);

        game.startTurn();

        assertTrue("a passive battery cannot shoot, and cannot be bombarded under P2.713",
                base.isActiveFireControl());
    }

    // ---------------------------------------------------------------- the automatic allocation

    @Test
    public void aPhaserGroundBaseIsNotAskedToAllocate() {
        Ship base = groundBase("GBD2", 1);

        game.startTurn();

        assertFalse("a form with one answer should not stop the turn",
                game.getAllocationQueue().contains(base));
    }

    /** Six batteries meant six identical forms a turn. Now none. */
    @Test
    public void sixBatteriesStopTheTurnNotAtAll() {
        for (int face = 1; face <= 6; face++)
            groundBase("GBD2", face);

        game.startTurn();

        assertTrue("queue should be empty: " + game.getAllocationQueue(),
                game.getAllocationQueue().isEmpty());
        assertFalse("and the turn should be under way", game.isAwaitingAllocation());
    }

    /** The point of allocating at all: the phasers come up charged. */
    @Test
    public void theAutomaticAllocationChargesThePhaserCapacitor() {
        Ship base = groundBase("GBD2", 1);

        game.startTurn();

        assertTrue("capacitor should be charged, was " + base.getWeapons().getPhaserCapacitorEnergy(),
                base.getWeapons().getPhaserCapacitorEnergy() > 0);
    }

    // ---------------------------------------------------------------- what is NOT automatic

    /**
     * Arming a heavy weapon is a real decision — standard or overload, and how long to hold it —
     * so these bases still ask. The test is the WEAPONS, not the hull class.
     */
    @Test
    public void aGroundBaseWithAHeavyWeaponStillAllocates() {
        for (String type : new String[] { "GBPT", "GBDF", "GBDH", "GBFB", "GBDD", "GBDT" }) {
            Game g = new Game();
            Terrain p = new Terrain(TerrainType.PLANET, 20, 20, 1);
            g.addTerrain(p);
            ShipSpec spec = ShipLibrary.get("Neutral", type);
            assertNotNull("fixture: " + type, spec);
            Ship base = ShipLibrary.createShip(spec);
            base.setName(type);
            assertTrue(g.placeGroundBase(base, p, 1).isSuccess());

            g.startTurn();

            assertTrue(type + " arms a heavy weapon, so its allocation is the player's",
                    g.getAllocationQueue().contains(base));
        }
    }

    /** ...while the phaser-only fits do not. Pinned together so the split stays deliberate. */
    @Test
    public void thePhaserOnlyFitsAllocateThemselves() {
        for (String type : new String[] { "GBD1", "GBD2", "GBDP" }) {
            Game g = new Game();
            Terrain p = new Terrain(TerrainType.PLANET, 20, 20, 1);
            g.addTerrain(p);
            Ship base = ShipLibrary.createShip(ShipLibrary.get("Neutral", type));
            base.setName(type);
            assertTrue(g.placeGroundBase(base, p, 1).isSuccess());

            g.startTurn();

            assertFalse(type + " has nothing to decide", g.getAllocationQueue().contains(base));
            assertTrue(type + " should still be shooting", base.isActiveFireControl());
        }
    }

    @Test
    public void anOrdinaryShipIsStillAsked() {
        Ship ca = fedCa();
        game.getShips().add(ca);

        game.startTurn();

        assertTrue(game.getAllocationQueue().contains(ca));
        assertTrue(game.isAwaitingAllocation());
    }

    /**
     * A battery alongside a real ship must not start the impulses early — the ship has not
     * allocated yet, and beginImpulses() is what ends the allocation window.
     */
    @Test
    public void aBatteryDoesNotStartTheTurnOutFromUnderAShip() {
        Ship base = groundBase("GBD2", 1);
        Ship ca = fedCa();
        game.getShips().add(ca);

        game.startTurn();

        assertTrue("the ship still owes an allocation", game.isAwaitingAllocation());
        assertEquals("and is the only one left to ask", 1, game.getAllocationQueue().size());
        assertSame(ca, game.nextShipNeedingAllocation());
        assertEquals("impulse 1 must wait for it", 0, game.getClock().getImpulse());
    }

    /**
     * The turn still runs once the ship submits. Guards the double-beginImpulses hazard: the
     * automatic pass can empty the queue itself, and the tail of startTurn() would otherwise
     * call beginImpulses() a second time.
     */
    @Test
    public void theTurnRunsNormallyOnceTheShipHasAllocated() {
        Ship base = groundBase("GBD2", 1);
        Ship ca = fedCa();
        game.getShips().add(ca);
        game.startTurn();

        game.submitAllocation(ca, ca.buildAutoAllocation());

        assertFalse(game.isAwaitingAllocation());
        assertEquals(1, game.getClock().getImpulse());
        assertTrue(base.isActiveFireControl());
    }
}
