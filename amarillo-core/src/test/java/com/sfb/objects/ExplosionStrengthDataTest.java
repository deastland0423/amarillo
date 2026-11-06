package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * D5.2 explosion strengths in the ship data: every hull has one, it is sane, and it survives into
 * the variants.
 *
 * <h2>Where the numbers came from</h2>
 * Annex #1's "D5.2" column, in {@code data/rules/Master_Annex_File.pdf} — which IS Module G3's
 * annexes, so no other book is involved. All 355 were read in one pass on 2026-11-05, and each
 * was checked against the chart's own BPV column: a hull whose chart BPV equals the one in its
 * ship file is a hull paired with the right row. That check earned its place by catching a real
 * error while it ran — the annex reads split values "economic/combat", which is the opposite
 * way round from the obvious reading.
 *
 * <h2>Zero is a value; null is a gap</h2>
 * The chart lists ground bases at ZERO, agreeing with (D5.13): small ground bases do not explode.
 * So the floor here is 0 and not 1. {@link ShipSpec#explosionStrength} stays an {@code Integer}
 * because those two states must remain distinguishable — a hull nobody has read is not a hull
 * that explodes for nothing.
 */
public class ExplosionStrengthDataTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /**
     * Hulls the chart does not list separately, each with the reason — the same shape as the
     * parked art in {@code TokenArtClaimedTest}. A list like this is worth reading only while
     * every line is still true, which {@link #noParkedHullOutlivesItsReason()} enforces.
     */
    private static final java.util.Map<String, String> NOT_ON_THE_CHART =
            new java.util.LinkedHashMap<>();
    static {
        NOT_ON_THE_CHART.put("Hydran/GEN-F",
                "the Gendarme with fighters. Annex #1 prints one GEN row (strength 7) and no"
                + " variant rows, and (D5.12) says the strength is not modified by what a ship"
                + " carries — so 7 is very likely right, and 'very likely' is not a source.");
    }

    /**
     * Every hull in the data has a number, or a line above saying why it has not.
     *
     * <p>This is the half that stops the pass rotting. Entering a hull is exactly the moment to
     * forget a field nothing yet reads, and without this the next ship file would simply be
     * silent about it.
     */
    @Test
    public void everyHullDeclaresAnExplosionStrength() {
        List<String> missing = new ArrayList<>();
        int seen = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.refitOf != null)
                continue;                       // a variant inherits its hull's; see below
            seen++;
            String key = spec.faction + "/" + spec.type;
            if (spec.explosionStrength == null && !NOT_ON_THE_CHART.containsKey(key))
                missing.add(key + " — read it off Annex #1's D5.2 column");
        }
        assertTrue("fixture: the library should be loaded", seen > 300);
        assertTrue("hulls with no explosion strength:\n  " + String.join("\n  ", missing),
                missing.isEmpty());
    }

    /** A parked hull that has since been given a number is a line claiming something untrue. */
    @Test
    public void noParkedHullOutlivesItsReason() {
        List<String> stale = new ArrayList<>();
        for (String key : NOT_ON_THE_CHART.keySet()) {
            String[] parts = key.split("/", 2);
            ShipSpec spec = ShipLibrary.get(parts[0], parts[1]);
            if (spec == null)
                stale.add(key + " — no such hull any more; remove this line");
            else if (spec.explosionStrength != null)
                stale.add(key + " — it has a number now; remove this line");
        }
        assertTrue("NOT_ON_THE_CHART has entries that are no longer true:\n  "
                + String.join("\n  ", stale), stale.isEmpty());
    }

    /**
     * Every number is in the range a basic explosion strength occupies.
     *
     * <p>The upper bound is doing the real work. A BPV typed into this field by mistake is the
     * error this field invites — the two are adjacent in the chart and adjacent in the JSON —
     * and every warship BPV above a frigate's is three digits, while the largest strength in our
     * data is a base's. If a legitimate hull ever exceeds this, raise the bound; do not widen it
     * pre-emptively, because a bound that admits 104 admits the mistake.
     *
     * <p>The floor is 0, not 1: the chart gives ground bases zero, which is (D5.13) in the data.
     */
    @Test
    public void everyDeclaredStrengthIsPlausible() {
        List<String> wrong = new ArrayList<>();
        int declared = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.explosionStrength == null)
                continue;
            declared++;
            int bes = spec.explosionStrength;
            if (bes < 0 || bes > 99)
                wrong.add(spec.faction + "/" + spec.type + " = " + bes
                        + " — outside 0..99; a BPV in the explosionStrength field looks like this");
        }
        assertTrue("fixture: the hulls should carry numbers by now", declared > 300);
        assertTrue("implausible explosion strengths:\n  " + String.join("\n  ", wrong),
                wrong.isEmpty());
    }

    /**
     * The hulls read so far.
     *
     * <p>Double-sourced, which is why they are worth pinning: Annex #1's Klingon chart (PDF page
     * 39) and the corner box on each R6 sheet, which prints the same figure as "EX".
     */
    @Test
    public void theHullsReadSoFarCarryTheirChartNumber() {
        assertEquals("F5W", Integer.valueOf(13), klingon("F5W").explosionStrength);
        assertEquals("FWC", Integer.valueOf(13), klingon("FWC").explosionStrength);
        assertEquals("FWV", Integer.valueOf(12), klingon("FWV").explosionStrength);
    }

    /**
     * A refitted variant gets its hull's number with nothing declared.
     *
     * <p>Its own method rather than three more lines above, because a mutation to the base hull's
     * number would otherwise fail the first assertion and never reach these — the masking that
     * makes a test look like it covers something it has not run in months. Removing the key from
     * {@code fwc.json} fails this with null, which is the proof that the FWL's 13 comes from the
     * hull and from nowhere else.
     */
    @Test
    public void aVariantInheritsItsHullsExplosionStrength() {
        assertEquals("the F5WK inherits the F5W's",
                Integer.valueOf(13), klingon("F5WK").explosionStrength);
        assertEquals("the FWL inherits the FWC's",
                Integer.valueOf(13), klingon("FWL").explosionStrength);
        assertEquals("the FWVK inherits the FWV's",
                Integer.valueOf(12), klingon("FWVK").explosionStrength);
    }

    /**
     * A refit can change it, which is the whole argument for where the field lives.
     *
     * <p>{@code RefitResolver} reaches top-level spec fields through {@code set}, and the
     * {@code power}, {@code auxiliary} and {@code control} blocks through their own calls. It has
     * no path into {@code tables} at all — so had this gone in beside {@code excess}, where the
     * excess-damage trigger might have argued for it, no refit or variant could ever have changed
     * it. Nothing in the data needs this today; the chart is entitled to say otherwise tomorrow.
     */
    @Test
    public void aRefitCanChangeTheExplosionStrength() {
        ShipSpec base = RefitResolver.deepCopy(klingon("F5W"));
        ShipSpec.RefitSpec bigger = new ShipSpec.RefitSpec();
        bigger.code = "X";
        bigger.set = java.util.Map.of("explosionStrength", 17);
        base.refits = List.of(bigger);

        assertEquals(Integer.valueOf(17),
                RefitResolver.apply(base, List.of("X")).explosionStrength);
        assertEquals("and the hull it was built from is untouched",
                Integer.valueOf(13), klingon("F5W").explosionStrength);
    }

    private ShipSpec klingon(String type) {
        ShipSpec spec = ShipLibrary.get("Klingon", type);
        assertNotNull("the library should hold Klingon/" + type, spec);
        return spec;
    }
}
