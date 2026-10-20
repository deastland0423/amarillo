package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.Weapon;

/**
 * G15.4: a weapon that fills two adjacent option mounts.
 *
 * <h2>The rule</h2>
 * Annex #8B marks eight options as needing two adjacent mounts — both ESGs, all four plasma
 * entries, the PPD and the stasis field generator. A Plasma-G "requires two adjacent centerline
 * optional mounts or two adjacent WYN hull side mounts" (owner, 2026-10-20), and the Plasma-S
 * the same plus size class 3 or larger.
 *
 * <p>Until now {@code OptionMountLoadout.validate} refused every one of them outright, so no
 * Orion could carry any of the heavy option weapons its own annex sells it.
 *
 * <h2>Occupied, not empty</h2>
 * The weapon sits on BOTH mounts — one primary, one continuation — and is added to the ship's
 * weapon list ONCE. The continuation is not empty, which is what stops a second weapon being
 * seated in the half that looks free, and carries no BPV, because one weapon is one purchase.
 */
public class MultiMountOptionTest {

    private OptionMountCatalog catalog;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        catalog = OptionMountCatalog.loadDefault();
    }

    private static Ship hull(String type) {
        ShipSpec spec = ShipLibrary.get("Orion", type);
        assertNotNull("fixture: Orion " + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    private static OptionMount mount(Ship ship, String designator) {
        for (OptionMount m : ship.getOptionMounts())
            if (m.getDesignator().equalsIgnoreCase(designator))
                return m;
        throw new AssertionError("fixture: no mount " + designator);
    }

    private static long weaponsNamed(Ship ship, Weapon weapon) {
        return ship.getWeapons().fetchAllWeapons().stream().filter(w -> w == weapon).count();
    }

    // ---------------------------------------------------------------- it fits

    /** The CA's two adjacent centerline mounts are exactly what a Plasma-G asks for. */
    @Test
    public void aPlasmaGFillsTheCasAdjacentCenterlinePair() {
        Ship ca = hull("CA");

        Weapon plasma = OptionMountLoadout.equip(ca, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        assertNotNull(plasma);
        assertSame("both mounts hold the same weapon", plasma, mount(ca, "A").getWeapon());
        assertSame(plasma, mount(ca, "B").getWeapon());
        assertTrue("B is the far half", mount(ca, "B").isContinuation());
        assertFalse("and A is not", mount(ca, "A").isContinuation());
    }

    /** One weapon, one entry in the ship's weapons — a list holding it twice would fire twice. */
    @Test
    public void theWeaponIsRegisteredOnlyOnce() {
        Ship ca = hull("CA");

        Weapon plasma = OptionMountLoadout.equip(ca, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        assertEquals(1, weaponsNamed(ca, plasma));
        assertEquals("named for both boxes it fills", "OPT-A+B", plasma.getDesignator());
    }

    /** One weapon is one purchase, however many boxes it fills. */
    @Test
    public void onlyThePrimaryCarriesTheCost() {
        Ship ca = hull("CA");

        OptionMountLoadout.equip(ca, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        assertTrue("the primary is charged", mount(ca, "A").getBpvCost() > 0);
        assertEquals("the far half is not", 0.0, mount(ca, "B").getBpvCost(), 0.0001);
    }

    /** Neither half is free afterwards, which is the point of marking the continuation. */
    @Test
    public void neitherHalfCanTakeASecondWeapon() {
        Ship ca = hull("CA");
        OptionMountLoadout.equip(ca, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        assertNotNull(OptionMountLoadout.validate(ca, mount(ca, "A"), catalog.get("Phaser-1"), 180));
        assertNotNull("and the far half is occupied, not empty",
                OptionMountLoadout.validate(ca, mount(ca, "B"), catalog.get("Phaser-1"), 180));
    }

    // ---------------------------------------------------------------- the row of three

    /**
     * THE CASE THE RUN MODEL WAS BUILT FOR. The Battle Raider's A-B-C is one run, so a two-mount
     * weapon seats on A+B or on B+C and never on A+C — B lies between them. Picking B leaves
     * the player with whichever side is still open.
     */
    @Test
    public void theBrsRowOfThreeSeatsTwoMountWeaponsOnNeighboursOnly() {
        Ship br = hull("BR");

        Weapon first = OptionMountLoadout.equip(br, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        assertSame(first, mount(br, "A").getWeapon());
        assertSame("A+B taken", first, mount(br, "B").getWeapon());
        assertTrue("C is untouched", mount(br, "C").isEmpty());
    }

    /** With A+B gone, C has no free neighbour left, so a second two-mount weapon is refused. */
    @Test
    public void theLeftoverEndOfARowCannotSeatATwoMountWeapon() {
        Ship br = hull("BR");
        OptionMountLoadout.equip(br, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        String refusal = OptionMountLoadout.validate(
                br, mount(br, "C"), catalog.get("Plasma-G Torp (No Swivel)"), 180);

        assertNotNull("C is alone now", refusal);
        assertTrue(refusal, refusal.contains("G15.4"));
    }

    /** Picking the middle of a free row works, taking the mount beside it. */
    @Test
    public void pickingTheMiddleOfTheRowSeatsOnANeighbour() {
        Ship br = hull("BR");

        Weapon plasma = OptionMountLoadout.equip(br, catalog, "B", "Plasma-G Torp (No Swivel)", 180);

        List<String> filled = new java.util.ArrayList<>();
        for (OptionMount m : br.getOptionMounts())
            if (m.getWeapon() == plasma)
                filled.add(m.getDesignator());
        assertEquals("two mounts, and B is one of them", 2, filled.size());
        assertTrue(filled.toString(), filled.contains("B"));
    }

    // ---------------------------------------------------------------- the row of four

    /**
     * The Heavy Battlecruiser's four centerline mounts are ONE run, so it is the only hull that
     * can carry two separate two-mount weapons — A+B and C+D — which no other Orion can do.
     */
    @Test
    public void theBchSeatsTwoSeparateTwoMountWeapons() {
        Ship bch = hull("BCH");

        Weapon first = OptionMountLoadout.equip(bch, catalog, "A", "Plasma-G Torp (No Swivel)", 180);
        Weapon second = OptionMountLoadout.equip(bch, catalog, "C", "Plasma-G Torp (No Swivel)", 180);

        assertNotSame("two weapons, not one", first, second);
        assertSame(first, mount(bch, "A").getWeapon());
        assertSame(first, mount(bch, "B").getWeapon());
        assertSame(second, mount(bch, "C").getWeapon());
        assertSame(second, mount(bch, "D").getWeapon());
    }

    /**
     * And a run of four still means CONSECUTIVE: once A+B is taken, a weapon asked for at D
     * seats on C+D, because B is no longer free. A+D was never a pair at all.
     */
    @Test
    public void aRunOfFourIsStillConsecutive() {
        Ship bch = hull("BCH");
        OptionMountLoadout.equip(bch, catalog, "A", "Plasma-G Torp (No Swivel)", 180);

        Weapon second = OptionMountLoadout.equip(bch, catalog, "D", "Plasma-S Torp (No Swivel)", 180);

        assertSame("D took C, the only neighbour left", second, mount(bch, "C").getWeapon());
        assertFalse(bch.mountsAreAdjacent("A", "D"));
    }

    // ---------------------------------------------------------------- it does not fit

    /**
     * The SLV shows two centerline mounts and declares NO adjacency run, which is the whole
     * reason adjacency is data rather than a count of mounts.
     */
    @Test
    public void theSlvsTwoCenterlineMountsAreNotAdjacentSoNothingSpansThem() {
        Ship slv = hull("SLV");

        String refusal = OptionMountLoadout.validate(
                slv, mount(slv, "A"), catalog.get("Plasma-G Torp (No Swivel)"), 180);

        assertNotNull("two mounts, but not adjacent ones", refusal);
        assertTrue(refusal, refusal.contains("G15.4"));
    }

    /** A wing mount is not a seat for a centerline-only weapon, however many neighbours it has. */
    @Test
    public void aCenterlineOnlyWeaponIsStillRefusedOnAWing() {
        Ship br = hull("BR");

        String refusal = OptionMountLoadout.validate(
                br, mount(br, "D"), catalog.get("Plasma-G Torp (No Swivel)"), 180);

        assertNotNull(refusal);
        assertTrue(refusal, refusal.contains("G15.43"));
    }

    /** And a single-mount weapon still seats in one mount, untouched by any of this. */
    @Test
    public void aSingleMountWeaponStillTakesExactlyOneMount() {
        Ship ca = hull("CA");

        Weapon phaser = OptionMountLoadout.equip(ca, catalog, "A", "Phaser-1", 180);

        assertSame(phaser, mount(ca, "A").getWeapon());
        assertTrue("B is left alone", mount(ca, "B").isEmpty());
        assertEquals("OPT-A", phaser.getDesignator());
    }
}
