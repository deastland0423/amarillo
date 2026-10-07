package com.sfb.objects;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Which hulls may declare {@code isLeader} — S8.363 and S8.333.
 *
 * <h2>The two rules, both of which read against intuition</h2>
 * A leader ship is one that leads a squadron of its own kind, and S8.36
 * restricts the flag much
 * further than "it has a big command rating":
 *
 * <p>
 * <b>S8.363 — dreadnoughts are not leaders.</b> "Dreadnoughts are not 'leader'
 * ships." Flatly,
 * with no exception. A DN is already limited to one per fleet by its own clause
 * and brings its
 * command rating to the fleet without needing followers; making it a leader
 * would let it demand a
 * squadron it is not supposed to have. The owner's reasonable-sounding guess on
 * 2026-10-05 — "all
 * dreadnoughts should be leaders" — is the opposite of what the rule says,
 * which is exactly why this
 * is a guard and not a convention.
 *
 * <p>
 * <b>S8.333 — a heavy battlecruiser needs no followers.</b> The BCH gets its
 * own one-per-fleet
 * allowance, in addition to the one size class 2 ship rather than instead of
 * it, and takes no
 * squadron. So the hulls that exist to lead — the Romulan SuperHawk, RoyalHawk
 * and NovaHawk, named
 * in S8.333 — are BCHs, not leaders, despite "Command Cruiser" in their type
 * names.
 *
 * <p>
 * <b>S8.36's named exception</b> is the one that caught me out two days
 * earlier: "King Eagles are
 * considered to be heavy cruisers, not command cruisers." I had advised
 * flagging the Romulan KE as a
 * leader; the rule names it specifically as not one.
 *
 * <h2>Why {@code isBCH} is not redundant with the BCH line</h2>
 * The line is the hull family — and the move-cost class
 * {@code ShipLineCatalogTest} checks against —
 * while {@code isBCH} is S8.333 fleet standing. <b>The line is the broader
 * set:</b> it also carries
 * the Kzinti CV and CVS (carriers built on a BCH hull) and the Tholian D (a
 * dreadnought, parked
 * there because the DN line's 1.5 move cost will not take its 1.0). Reading the
 * one-per-fleet limit
 * off the line would wrongly cap three hulls that are not heavy battlecruisers.
 *
 * <p>
 * S8.333 settles that itself rather than leaving it to inference — <b>"The Kzinti CVS is not a BCH
 * variant"</b>, in those words. It also decides the Lyran BC in advance of its entry: <b>"The Lyran
 * BC is not a BCH variant, but is considered to be a BCH under this rule for all purposes"</b> — so
 * when that hull arrives it takes {@code isBCH} despite not being one, since this flag exists to
 * answer precisely "under this rule". The one-per-fleet list is KillerHawk, SuperHawk, RoyalHawk,
 * NovaHawk, plus each empire's actual BCHs and the Lyran BC.
 *
 * <p>
 * The three Romulan hawks used to make the point in the other direction too,
 * sitting on the CA
 * line until the owner moved them on 2026-10-05 — which is the slip
 * {@link #everyBchIsOnTheBchLine()} now watches for.
 */
public class LeaderFlagTest {

    @BeforeClass
    public static void loadData() {
        ShipLibrary.loadAllSpecs("../data/factions");
        assertTrue("fixture: the library should have loaded the hulls",
                ShipLibrary.all().size() > 100);
    }

    /** S8.363: no dreadnought is a leader, however large its command rating. */
    @Test
    public void noDreadnoughtIsALeader() {
        List<String> offenders = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (isDreadnought(spec) && spec.isLeader)
                offenders.add(describe(spec));

        assertEquals("S8.363 'Dreadnoughts are not leader ships', but these declare isLeader: "
                + offenders, List.of(), offenders);
    }

    /**
     * S8.333: a BCH takes no squadron of followers, so it is never a leader either.
     */
    @Test
    public void noHeavyBattlecruiserIsALeader() {
        List<String> offenders = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.isBCH && spec.isLeader)
                offenders.add(describe(spec));

        assertEquals("S8.333: a BCH needs no followers and is not a leader, but these declare both: "
                + offenders, List.of(), offenders);
    }

    /**
     * S8.36's named exception. Pinned by name because the rule names it: nothing in
     * the King Eagle's
     * own numbers distinguishes it from a command cruiser, which is why the rule
     * had to say so.
     */
    @Test
    public void theRomulanKingEagleIsNotALeader() {
        ShipSpec ke = ShipLibrary.get("Romulan", "KE");
        assertNotNull("fixture: the Romulan KE should be in the library", ke);
        assertFalse("S8.36: 'King Eagles are considered to be heavy cruisers, not command cruisers'",
                ke.isLeader);
    }

    /** The three hulls S8.333 names are flagged as BCHs. */
    @Test
    public void theRomulanHawkBchsAreFlaggedAsBchs() {
        for (String type : List.of("SUP-A", "RHK", "NHK")) {
            ShipSpec spec = ShipLibrary.get("Romulan", type);
            assertNotNull("fixture: Romulan " + type + " should be in the library", spec);
            assertTrue("S8.333 names the " + type + " as a heavy battlecruiser", spec.isBCH);
        }
    }

    /**
     * A hull flagged {@code isBCH} belongs on the BCH line.
     *
     * <p>
     * Not a rule — a data-consistency check, and deliberately the weaker half of
     * the pair, since
     * the line also holds hulls that are not BCHs. It exists because this is the
     * slip that actually
     * happened: the three Romulan hawks were flagged as BCHs while still on the CA
     * line, caught by
     * eye rather than by anything failing. A hull entered by copying its nearest
     * neighbour inherits
     * that neighbour's line, so the flag and the line drift apart silently.
     *
     * <p>
     * If a faction ever fields a BCH whose move cost the BCH line does not allow —
     * the way the
     * Tholian dreadnought cannot sit on the DN line — this is the guard to relax,
     * not the data to
     * bend.
     */
    @Test
    public void everyBchIsOnTheBchLine() {
        List<String> misfiled = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.isBCH && !"BCH".equals(spec.line))
                misfiled.add(describe(spec));

        assertEquals("these declare isBCH but are not on the BCH line: " + misfiled,
                List.of(), misfiled);
    }

    /**
     * "Dreadnought", never "Dreadnaught" — and this is a correctness guard, not a style one.
     *
     * <p>{@link #isDreadnought} asks the type name for the word, so a hull that spells it the other
     * way is not a dreadnought as far as S8.363 is concerned. The misspelling is also the kind that
     * survives review: it is a real historical English spelling, it reads correctly, and nothing
     * else in the system cares. Both Gorn offenders happened to be on the DN line, so the line
     * branch covered them and the bug stayed invisible — a DN-hulled <i>carrier</i> like the Fed CVA
     * or Klingon C8V, whose type name is "Heavy Carrier", has no such second chance if its line is
     * ever what strays.
     */
    @Test
    public void dreadnoughtIsSpelledTheOneWay() {
        List<String> misspelled = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            for (String field : new String[] { spec.typeName, spec.name })
                if (field != null && field.toLowerCase().contains("dreadnaught"))
                    misspelled.add(spec.faction + "/" + spec.type + " = '" + field + "'");

        assertEquals("'Dreadnaught' must be spelled 'Dreadnought' — isDreadnought() matches only the"
                + " canonical spelling, so these hulls would escape S8.363: " + misspelled,
                List.of(), misspelled);
    }

    /**
     * And the flag still reaches something — a guard that only ever found nothing
     * would pass just as
     * well if {@code isLeader} had quietly stopped being read at all.
     */
    @Test
    public void someHullsAreStillLeaders() {
        List<String> leaders = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.isLeader)
                leaders.add(describe(spec));
        assertFalse("fixture: some hull should still be a leader (S8.361)", leaders.isEmpty());
    }

    /**
     * A dreadnought is one by hull family, not only by line: the Tholian D is a
     * dreadnought parked on
     * the BCH line because the DN line's 1.5 move cost would fail
     * {@code ShipLineCatalogTest}. Asking
     * only about the line would let that one through.
     *
     * <p>
     * It reads the one canonical spelling, which is safe only because
     * {@link #dreadnoughtIsSpelledTheOneWay()} enforces it. Two Gorn files said "Dreadnaught" until
     * 2026-10-05, and the predicate briefly tolerated both — the wrong fix, since a rule silently
     * stops applying to any hull that strays by a letter. Enforce the spelling, then trust it.
     *
     * <p><b>Battleships count too</b>, added when the Klingon B10 arrived 2026-10-06 — the first
     * BB-line hull in the library, and one this predicate would have waved straight past. S8.331
     * names the two in one breath: "no more than one size class 2 ship <b>(dreadnoughts,
     * battleships, most CVAs and SCSs)</b>", and S8.332 makes the substitution explicit —
     * "Battleships can be substituted for DNs if mutually agreed." A hull standing in a
     * dreadnought's slot does not get to be a leader when the dreadnought cannot.
     *
     * <p>Left alone deliberately: the CVAs and SCSs that S8.331 also names. Those are size class 2
     * by hull but they are carriers, and their fleet constraints come from S8.31's carrier groups
     * rather than from the DN clause — the Federation CVA already sits on the DN line and so is
     * covered anyway, while a size-class-2 SCS is not a thing this library has yet.
     */
    private static boolean isDreadnought(ShipSpec spec) {
        if ("DN".equals(spec.line) || "BB".equals(spec.line))
            return true;
        if (spec.typeName == null)
            return false;
        String name = spec.typeName.toLowerCase();
        return name.contains("dreadnought") || name.contains("battleship");
    }

    private static String describe(ShipSpec spec) {
        return spec.faction + "/" + spec.type + " (line " + spec.line + ", " + spec.typeName + ")";
    }
}
