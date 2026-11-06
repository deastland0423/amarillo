package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * D5.2 explosion strengths in the ship data: what is declared must be sane, and what is read off
 * the chart must survive into the variants.
 *
 * <h2>Why this does not yet demand a number from every hull</h2>
 * The figures live in Annex #1's "D5.2" column ({@code data/rules/Master_Annex_File.pdf}, which is
 * Module G3's annexes), one chart page per faction, and reading all of them is a data pass of its
 * own — see the ship-explosion section of BACKLOG.md. About 430 hulls carry no number today.
 *
 * <p>That is what {@link ShipSpec#explosionStrength} is an {@code Integer} for: a primitive would
 * have said every unread hull explodes for zero, which is a claim rather than a silence. When the
 * pass is finished, {@link #everyDeclaredStrengthIsPlausible()} becomes "every hull declares one"
 * and this paragraph goes with it.
 */
public class ExplosionStrengthDataTest {

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /**
     * Every number that IS declared is in the range a basic explosion strength occupies.
     *
     * <p>The upper bound is doing the real work. A BPV typed into this field by mistake is the
     * error this field invites — they are adjacent in the chart and adjacent in the JSON — and
     * every warship BPV above a frigate's is three digits while the largest explosion strengths on
     * the Romulan chart are in the forties. If a legitimate base ever exceeds this, raise the
     * bound; do not widen it pre-emptively, because a bound that admits 104 admits the mistake.
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
            if (bes < 1 || bes > 99)
                wrong.add(spec.faction + "/" + spec.type + " = " + bes
                        + " — outside 1..99; a BPV in the explosionStrength field looks like this");
        }
        assertTrue("fixture: some hull should declare one by now", declared > 0);
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
