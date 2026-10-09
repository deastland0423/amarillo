package com.sfb.objects;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Refits as data (S3.24): one base hull, a declared set of refits, and the variants history named.
 *
 * <h2>What this replaced, and why it is not just tidying</h2>
 * Every refitted ship used to be its own file — 128 of 427 hulls. That was lossy as well as long.
 * The Lyran CWB is a CW with the +, the phaser AND the power-pack refits and names none of them, so
 * "power pack without the phaser refit" could not be written down at all. The Klingon D6 needed five
 * files for three independent switches. Refits as switches can express every combination; the
 * variant list only says what to CALL the ones that happened.
 *
 * <h2>The expectations below are the deleted files</h2>
 * These numbers were read off {@code d6b.json}, {@code d6k.json}, {@code d6bu.json},
 * {@code d6ku.json}, {@code d6db.json} and {@code d6sb.json} before they were removed. A temporary
 * test compared the synthesised spec against each of those files field by field and the six agreed;
 * this is what carries that agreement forward now the files are gone.
 */
public class RefitResolverTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private ShipSpec klingon(String type) {
        ShipSpec spec = ShipLibrary.get("Klingon", type);
        assertNotNull("the library should hold Klingon/" + type, spec);
        return spec;
    }

    private static long count(ShipSpec spec, String weaponType) {
        return spec.weapons.stream().filter(w -> weaponType.equals(w.type)).count();
    }

    // ----------------------------------------------------------- the variants history named

    /**
     * The B refit on the D6, every part of it. Four separate things travel together in this hull's
     * data — and two of them, the disruptor range and the rack type, were missed on the first reading
     * because a diff by weapon TYPE and DESIGNATOR cannot see a property change.
     */
    @Test
    public void theBRefitBuildsTheD6B() {
        ShipSpec d6b = klingon("D6B");

        assertEquals(123, d6b.bpv);
        assertEquals("the refit cannot be fielded before it exists (S3.24)", 165, d6b.serviceYear);
        assertArrayEquals(new int[] { 30, 22, 22, 22, 22, 22 }, d6b.shields);
        assertTrue("the B refit brings DERFACS", d6b.auxiliary.derfacs);
        assertEquals("and an ADD", 1, count(d6b, "ADD"));
        assertTrue("disruptors lengthen to 30",
                d6b.weapons.stream().filter(w -> "Disruptor".equals(w.type))
                        .allMatch(w -> w.range == 30));
        assertTrue("and the racks become type-A",
                d6b.weapons.stream().filter(w -> "DroneRack".equals(w.type))
                        .allMatch(w -> "TYPE_A".equals(w.rackType)));
        assertNotNull("the Y175 block travels with it", d6b.y175Upgrades);
        assertEquals("D6", d6b.refitOf);
    }

    /**
     * D13.142's cousin in the data: the K refit arrives ON TOP of B, so asking for K alone gets both.
     * A D6K has B's shields as well as its own phaser-1s, which is what the file said and what the
     * {@code requires} list now says.
     */
    @Test
    public void theKRefitBringsTheBRefitWithIt() {
        ShipSpec d6k = klingon("D6K");

        assertEquals(126, d6k.bpv);
        assertEquals(List.of("B", "K"), d6k.appliedRefits);
        assertArrayEquals("B's shields came along", new int[] { 30, 22, 22, 22, 22, 22 }, d6k.shields);
        assertEquals("three phaser-2s became phaser-1s", 3, count(d6k, "Phaser1"));
        assertEquals("and four are left", 4, count(d6k, "Phaser2"));
    }

    /** The UIM refit, and the one that proves the costs are additive: 123 + 5 and 126 + 5. */
    @Test
    public void theUimRefitAddsFiveToWhateverItIsFittedTo() {
        assertEquals(128, klingon("D6Bu").bpv);
        assertEquals(131, klingon("D6Ku").bpv);
        assertEquals(1, klingon("D6Bu").auxiliary.uim);
        assertEquals(1, klingon("D6Ku").auxiliary.uim);
        assertEquals("the D6Ku is all three", List.of("B", "K", "u"), klingon("D6Ku").appliedRefits);
    }

    /**
     * The same refit code does something DIFFERENT on each hull, which is why refits are declared per
     * hull and never faction-wide. The B refit is +10 on the D6, +4 on the D6D and +6 on the D6S;
     * only the D6 gains DERFACS and an ADD, and the D6D's six type-B racks have nothing to upgrade.
     */
    @Test
    public void theSameRefitIsNotTheSameOnEveryHull() {
        ShipSpec d6db = klingon("D6DB");
        ShipSpec d6sb = klingon("D6SB");

        assertEquals(117, d6db.bpv);
        assertEquals(106, d6sb.bpv);
        assertEquals("the scout's refit raises its EPV", 136, d6sb.epv);
        assertFalse("the drone cruiser gains no DERFACS", d6db.auxiliary.derfacs);
        assertEquals("nor an ADD", 0, count(d6db, "ADD"));
        assertTrue("its own racks are untouched",
                d6db.weapons.stream().filter(w -> "DroneRack".equals(w.type))
                        .allMatch(w -> "TYPE_B".equals(w.rackType)));
    }

    /**
     * The refitted drone cruiser keeps its D% marking.
     * <p>
     * The deleted {@code d6db.json} did not carry it, and that was the one place this migration
     * deliberately changed behaviour. The flag carries FD10.622's doubled special-drone caps and
     * S3.223's 30% Commander's Option budget, and a power-and-shield refit has no business taking
     * either away. Annex #3 has no D6DB row to appeal to — it rosters the D6D — so
     * {@code DPercentShipTest} accepts the marking by inheritance from the base.
     */
    @Test
    public void aRefitInheritsTheDPercentMarking() {
        assertTrue("the D6D is marked", klingon("D6D").dPercent);
        assertTrue("so the D6DB is too", klingon("D6DB").dPercent);
    }

    // ------------------------------------------------------- the combinations history did not

    /**
     * The lossiness fix, and the point of the whole exercise: a combination nobody fielded is still
     * expressible, because the refits are independent switches rather than a list of finished ships.
     * <p>
     * No D6 ever carried a UIM without the B refit, so no file could describe one and S3.24 would
     * have had nothing to sell. It gets a derived code, since there is no historical one to use.
     */
    @Test
    public void anUnnamedCombinationIsStillBuildable() {
        ShipSpec d6 = klingon("D6");
        ShipSpec uimOnly = RefitResolver.apply(d6, List.of("u"));

        assertEquals("113 plus the UIM's five", 118, uimOnly.bpv);
        assertEquals(1, uimOnly.auxiliary.uim);
        assertFalse("and none of the B refit came with it", uimOnly.auxiliary.derfacs);
        assertEquals("D6u", uimOnly.type);
        assertTrue("its name says what it is: " + uimOnly.typeName,
                uimOnly.typeName.contains("UIM"));
    }

    /**
     * A derived code is written in the house order — capitals, then lower case, then plus signs —
     * not in whatever order the hull happens to declare its refits.
     *
     * <p>The owner's rule, 2026-10-08. Without it the same combination comes out "DN+pB" on one
     * hull and "DNpB+" on its neighbour, depending only on how each file was typed, and
     * {@code ShipFileNamingTest} would reject half of them.
     */
    @Test
    public void aDerivedCodeIsWrittenInTheHouseOrder() {
        // The Lyran CW, because it names only three of its seven combinations — so these go
        // through the DERIVATION. The DN would not do: the owner enumerated all seven of its
        // variants by hand, so every lookup there finds a declared code and canonicalCode is
        // never reached, which would make this test agree with itself.
        ShipSpec cw = ShipLibrary.get("Lyran", "CW");
        assertNotNull("fixture: the Lyran CW should be in the library", cw);
        assertNull("fixture: {B,p} must be UNNAMED for this to test the derivation",
                RefitResolver.variantFor(cw, List.of("B", "p")));

        // Asked for in the least tidy order imaginable.
        assertEquals("CWBp", RefitResolver.apply(cw, List.of("p", "B")).type);
        assertEquals("CWB+", RefitResolver.apply(cw, List.of("B", "+")).type);
        assertEquals("CWB", RefitResolver.apply(cw, List.of("B")).type);
        assertEquals("CWp", RefitResolver.apply(cw, List.of("p")).type);

        // All three refits at once. Its historical code was "CWB", which named the bundle after
        // its most notable refit and said nothing of the + and phaser refits inside it — the same
        // code the canonical rule derives for the power pack ALONE. The owner's resolution, taken
        // first on the DN: the plain code means the single refit and the bundle spells itself out.
        assertEquals("CWBp+", RefitResolver.apply(cw, List.of("+", "p", "B")).type);
    }

    /**
     * Applying refits must not refit the BASE.
     * <p>
     * The library holds one spec per hull and builds every variant from it, so a resolver that
     * mutated its input would turn the D6 itself into a D6B — and then the D6K, built next, would be
     * a D6B with phasers, at 123 + 3 + 10. The copy is a Jackson round trip rather than a
     * hand-written clone for the same reason: ShipSpec gains fields most weeks, and a clone that
     * fell behind would drop whatever was added last from every refitted hull in the game.
     */
    @Test
    public void applyingRefitsLeavesTheBaseHullAlone() {
        ShipSpec d6 = klingon("D6");
        int bpv = d6.bpv;
        int[] shields = d6.shields.clone();
        long phaser2s = count(d6, "Phaser2");

        RefitResolver.apply(d6, List.of("B", "K", "u"));

        assertEquals("the base hull's BPV", bpv, d6.bpv);
        assertArrayEquals("its shields", shields, d6.shields);
        assertEquals("and its phasers", phaser2s, count(d6, "Phaser2"));
        assertNull("the base is nobody's refit", d6.refitOf);
    }

    /** Asking for nothing gives the hull back, not a nameless variant of it. */
    @Test
    public void noRefitsMeansTheHullItself() {
        ShipSpec plain = RefitResolver.apply(klingon("D6"), List.of());

        assertEquals("D6", plain.type);
        assertEquals(113, plain.bpv);
        assertNull(plain.refitOf);
    }

    /**
     * A refit can swap a weapon into a different FAMILY, not just a better model of the same one.
     *
     * <p>The Orion Base Station is the first hull in the data to do it. Its sheet says the two
     * drone racks become plasma-D racks for four points if the base sits in a plasma empire's
     * space, which is a change of weapon class rather than of rack type — and it is a refit
     * rather than a year upgrade because what decides it is WHOSE space the base is in, which
     * only the scenario knows.
     *
     * <p>Worth its own test because {@code swap} sets {@code w.type} and leaves the rest of the
     * {@link ShipSpec.WeaponSpec} alone: the converted entry still carries {@code rackType
     * TYPE_D}, which means nothing to a plasma rack. That is harmless only as long as the arcs
     * survive — FP10.0 racks fire in an arc where a drone rack does not care — so the arcs are
     * what this asserts, alongside the class.
     */
    @Test
    public void aRefitCanSwapAWeaponIntoAnotherFamily() {
        ShipSpec orionBase = ShipLibrary.get("Orion", "BS");
        assertNotNull("the library should hold Orion/BS", orionBase);

        ShipSpec converted = RefitResolver.apply(orionBase, List.of("pd"));

        assertEquals("204 — the sheet's four points to change the racks", 204, converted.bpv);
        assertEquals("no drone rack survives the swap", 0, count(converted, "DroneRack"));
        assertEquals("both become plasma racks", 2, count(converted, "PlasmaRack"));
        assertEquals("rack 1 keeps its RS arc",
                List.of("RS"),
                converted.weapons.stream()
                        .filter(w -> "Rack 1".equals(w.designator)).findFirst().orElseThrow().arcs);
        assertEquals("and rack 2 its LS arc",
                List.of("LS"),
                converted.weapons.stream()
                        .filter(w -> "Rack 2".equals(w.designator)).findFirst().orElseThrow().arcs);
    }

    /**
     * A refit that names a field wrongly fails loudly at load.
     * <p>
     * This is what lets {@code ShipJsonKeyGuardTest} stop at a map without weakening anything: the
     * guard cannot check a map's keys against a spec, but the resolver checks them against the real
     * block and by name. A refit writing {@code "APR"} would otherwise do nothing at all, and nothing
     * downstream would look wrong.
     */
    @Test
    public void aRefitThatMisnamesAFieldIsAnError() {
        ShipSpec base = RefitResolver.deepCopy(klingon("D6"));
        ShipSpec.RefitSpec bad = new ShipSpec.RefitSpec();
        bad.code = "X";
        bad.power = java.util.Map.of("APR", 5);          // the field is "apr"
        base.refits = List.of(bad);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> RefitResolver.apply(base, List.of("X")));
        assertTrue("the message should name the field: " + e.getMessage(),
                e.getMessage().contains("power.APR"));
    }
}
