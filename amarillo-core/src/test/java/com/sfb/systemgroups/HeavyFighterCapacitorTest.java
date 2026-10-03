package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.weapons.FighterPhoton;
import com.sfb.weapons.FighterPlasmaF;
import com.sfb.weapons.Weapon;

/**
 * J4.85 and J4.86: the box capacitor and the reload for the two heavy fighter weapons that had
 * neither — the Federation A-10's photon and the Romulan Gladiator's type-F plasma torpedo.
 * <p>
 * Both weapons already worked in flight. What was missing was everything a CARRIER does between
 * sorties: {@code ShuttleSpace.kindFor} fell through to {@code NONE} for both, so their boxes
 * had no capacitor, a deck crew found no work, and neither weapon could ever be reloaded. A
 * Gladiator fired its torpedo once per scenario and an A-10 its photon once, which looked like
 * working software.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li><b>J4.852</b> "Each fighter box assigned to a photon-armed fighter (marked +) includes a
 *       capacitor for a single photon torpedo. This capacitor can be reloaded (same procedure as
 *       a photon torpedo tube, two points of warp power for two consecutive turns, but overloads
 *       are not allowed) by the ship's power."</li>
 *   <li><b>J4.853</b> "Reloading a shuttle with a photon torpedo is a single deck crew
 *       action."</li>
 *   <li><b>J4.862</b> "Each shuttle box (marked +) that carries a fighter capable of firing a
 *       type-F plasma torpedo includes a storage facility (not shown on the SSD) for a single
 *       type-F plasma torpedo. Boxes holding fighters that do not carry this weapon do not have
 *       the storage facility."</li>
 *   <li><b>J4.863</b> "Reloading a type-F plasma torpedo on a fighter is a single deck crew
 *       action."</li>
 *   <li><b>J4.881</b>, which is what prices the plasma: "there is no difference whatsoever in
 *       arming the type-F plasma torpedo on a ship than in arming one to be held for use by a
 *       fighter."</li>
 *   <li><b>J4.882</b> "The ship cannot reload the fighter directly, but must reload the storage
 *       capacitor." Two steps, always.</li>
 * </ul>
 */
public class HeavyFighterCapacitorTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    private static Fighter fighter(String type, String name) {
        Fighter f = CataloguedFighter.of(type);
        assertNotNull("fixture: " + type + " should be in the catalogue", f);
        f.setName(name);
        return f;
    }

    private static FighterPhoton photonOf(Fighter f) {
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterPhoton fp)
                return fp;
        throw new AssertionError(f.getName() + " has no photon");
    }

    private static FighterPlasmaF plasmaOf(Fighter f) {
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterPlasmaF fp)
                return fp;
        throw new AssertionError(f.getName() + " has no plasma launcher");
    }

    // ---------------------------------------------------------------- the box has one at all

    /** J4.852: a photon fighter's box carries a capacitor for a single torpedo. */
    @Test
    public void anA10sBoxHasAPhotonCapacitorForOne() {
        ShuttleSpace box = new ShuttleSpace(fighter("a10", "A10-1"));

        assertEquals(ShuttleSpace.CapacitorKind.PHOTON, box.getCapacitorKind());
        assertEquals(ShuttleSpace.PHOTON_CAPACITOR, box.capacitorCapacity());
        assertEquals("one torpedo and no more", 1, box.capacitorCapacity());
    }

    /** J4.862: a plasma-F fighter's box carries a storage facility for a single torpedo. */
    @Test
    public void aGladiatorsBoxHasAPlasmaStorageFacilityForOne() {
        ShuttleSpace box = new ShuttleSpace(fighter("g1", "G1-1"));

        assertEquals(ShuttleSpace.CapacitorKind.PLASMA_F, box.getCapacitorKind());
        assertEquals(1, box.capacitorCapacity());
    }

    /**
     * J4.862's second sentence: "Boxes holding fighters that do not carry this weapon do not
     * have the storage facility." The G-1E is the case worth testing, because it sits in the
     * same bay as the G-1 and carries no weapons at all.
     */
    @Test
    public void aBoxHoldingAnUnarmedEwFighterGetsNoStorageFacility() {
        ShuttleSpace box = new ShuttleSpace(fighter("g1_e", "G1E-1"));

        assertEquals(ShuttleSpace.CapacitorKind.NONE, box.getCapacitorKind());
        assertEquals(0, box.capacitorCapacity());
    }

    // ---------------------------------------------------------------- the ship fills it

    /**
     * J4.852: four points, two a turn, two consecutive turns.
     * <p>
     * The RATE is what makes it take two turns rather than one. It is enforced as a ceiling on
     * what ONE allocation may buy rather than by a per-turn counter, because energy allocation
     * happens once a turn - the same way the hellbore's two-points-a-turn has always worked.
     */
    @Test
    public void thePhotonCapacitorTakesTwoPointsATurnForTwoTurns() {
        Fighter a10 = fighter("a10", "A10-1");
        // No need to spend anything: a fighter is built EMPTY (J4.8223), which is the state
        // a deck crew exists to remedy.
        ShuttleSpace box = new ShuttleSpace(a10);
        box.setCapacitorCharges(0);

        assertEquals("two a turn is all it will take", 2, box.capacitorPowerWanted());
        box.addCapacitorEnergy(2);
        assertEquals("still empty after one turn", 0, box.getCapacitorCharges());

        assertEquals("two already banked, two still owed", 2, box.capacitorPowerWanted());
        box.addCapacitorEnergy(2);              // the next turn's allocation

        assertEquals("full after the second payment (J4.852)", 1, box.getCapacitorCharges());
    }

    /** The part-payment has to be held, or a four-point charge could never be completed. */
    @Test
    public void thePartPaidPhotonChargeBanksAcrossTheTurn() {
        Fighter a10 = fighter("a10", "A10-1");
        ShuttleSpace box = new ShuttleSpace(a10);
        box.setCapacitorCharges(0);
        box.addCapacitorEnergy(2);

        assertEquals("the part payment is held, not lost", 2, box.getCapacitorEnergyBanked());
        assertEquals("two to go", 2, box.capacitorPowerWanted());
    }

    /** J4.881 prices the plasma the same as a ship's: four points over two turns. */
    @Test
    public void thePlasmaStorageFacilityIsPricedLikeAShipsTypeF() {
        Fighter g1 = fighter("g1", "G1-1");
        ShuttleSpace box = new ShuttleSpace(g1);
        box.setCapacitorCharges(0);

        assertEquals(ShuttleSpace.POWER_PER_PLASMA_F_CHARGE, 4);
        assertEquals("rate-limited, so it cannot fill in one turn", 2,
                box.capacitorPowerWanted());
        box.addCapacitorEnergy(2);
        box.addCapacitorEnergy(2);

        assertEquals(1, box.getCapacitorCharges());
    }

    // ---------------------------------------------------------------- the crew moves it across

    /**
     * J4.853: one deck crew action. And J4.882's two-step shape — the charge comes out of the
     * box, never from the ship directly.
     */
    @Test
    public void aDeckCrewMovesThePhotonFromBoxToFighter() {
        Fighter a10 = fighter("a10", "A10-1");
        FighterPhoton gun = photonOf(a10);
        assertEquals("fixture: built empty (J4.8223)", 0, gun.getChargesRemaining());
        ShuttleSpace box = new ShuttleSpace(a10);
        box.setCapacitorCharges(1);

        FighterArming.Load load = FighterArming.load(box, a10, FighterArming.HALF_ACTIONS_PER_ACTION);

        assertEquals("reloaded", 1, gun.getChargesRemaining());
        assertEquals("the box gave up its torpedo", 0, box.getCapacitorCharges());
        assertEquals("a whole action", FighterArming.HALF_ACTIONS_PER_ACTION, load.halfActionsUsed());
        assertTrue(load.note(), load.note().contains("J4.853"));
    }

    /** J4.863: the same, for the Gladiator's torpedo. */
    @Test
    public void aDeckCrewMovesThePlasmaFromBoxToFighter() {
        Fighter g1 = fighter("g1", "G1-1");
        FighterPlasmaF launcher = plasmaOf(g1);
        assertFalse("fixture: a fighter is built empty (J4.8223)", launcher.isLoaded());
        ShuttleSpace box = new ShuttleSpace(g1);
        box.setCapacitorCharges(1);

        FighterArming.Load load = FighterArming.load(box, g1, FighterArming.HALF_ACTIONS_PER_ACTION);

        assertTrue("the torpedo is aboard", launcher.isLoaded());
        assertEquals(0, box.getCapacitorCharges());
        assertTrue(load.note(), load.note().contains("J4.863"));
    }

    /** Half an action buys nothing: J4.8174's all-or-nothing, as for the hellbore. */
    @Test
    public void halfAnActionReloadsNeitherWeapon() {
        Fighter a10 = fighter("a10", "A10-1");
        ShuttleSpace photonBox = new ShuttleSpace(a10);
        photonBox.setCapacitorCharges(1);

        Fighter g1 = fighter("g1", "G1-1");
        ShuttleSpace plasmaBox = new ShuttleSpace(g1);
        plasmaBox.setCapacitorCharges(1);

        assertEquals(0, FighterArming.load(photonBox, a10, 1).halfActionsUsed());
        assertEquals(0, FighterArming.load(plasmaBox, g1, 1).halfActionsUsed());
        assertEquals("the box keeps its charge", 1, photonBox.getCapacitorCharges());
        assertEquals(1, plasmaBox.getCapacitorCharges());
    }

    /** An empty box says so rather than conjuring a torpedo (J4.882). */
    @Test
    public void anEmptyBoxReloadsNothing() {
        Fighter g1 = fighter("g1", "G1-1");
        ShuttleSpace box = new ShuttleSpace(g1);
        box.setCapacitorCharges(0);

        FighterArming.Load load = FighterArming.load(box, g1, FighterArming.HALF_ACTIONS_PER_ACTION);

        assertFalse(plasmaOf(g1).isLoaded());
        assertTrue(load.note(), load.note().contains("J4.862"));
    }

    // ---------------------------------------------------------------- the crew has work at all

    /**
     * The symptom that made this a bug rather than a missing feature: both fighters reported
     * NOTHING for a deck crew to do, so they never appeared in the hangar's work list.
     */
    @Test
    public void bothFightersNowGiveADeckCrewWork() {
        Fighter a10 = fighter("a10", "A10-1");
        assertTrue("an A-10 with an empty photon wants a crew (J4.853)",
                FighterArming.needsArming(a10));

        Fighter g1 = fighter("g1", "G1-1");
        assertTrue("a Gladiator with no torpedo wants a crew (J4.863)",
                FighterArming.needsArming(g1));
    }

    /** And the G-1E still wants none, because there is nothing aboard it to arm. */
    @Test
    public void theUnarmedEwFighterStillWantsNoCrew() {
        assertFalse(FighterArming.needsArming(fighter("g1_e", "G1E-1")));
    }
}
