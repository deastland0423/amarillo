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
 * A leader ship is one that leads a squadron of its own kind, and S8.36 restricts the flag much
 * further than "it has a big command rating":
 *
 * <p><b>S8.363 — dreadnoughts are not leaders.</b> "Dreadnoughts are not 'leader' ships." Flatly,
 * with no exception. A DN is already limited to one per fleet by its own clause and brings its
 * command rating to the fleet without needing followers; making it a leader would let it demand a
 * squadron it is not supposed to have. The owner's reasonable-sounding guess on 2026-10-05 — "all
 * dreadnoughts should be leaders" — is the opposite of what the rule says, which is exactly why this
 * is a guard and not a convention.
 *
 * <p><b>S8.333 — a heavy battlecruiser needs no followers.</b> The BCH gets its own one-per-fleet
 * allowance, in addition to the one size class 2 ship rather than instead of it, and takes no
 * squadron. So the hulls that exist to lead — the Romulan SuperHawk, RoyalHawk and NovaHawk, named
 * in S8.333 — are BCHs, not leaders, despite "Command Cruiser" in their type names.
 *
 * <p><b>S8.36's named exception</b> is the one that caught me out two days earlier: "King Eagles are
 * considered to be heavy cruisers, not command cruisers." I had advised flagging the Romulan KE as a
 * leader; the rule names it specifically as not one.
 *
 * <h2>Why {@code isBCH} is not redundant with the BCH line</h2>
 * The line is the hull family (and the move-cost class {@code ShipLineCatalogTest} checks against);
 * {@code isBCH} is S8.333 fleet standing, and the data separates them in <i>both</i> directions.
 * Three Romulan BCHs sit on the <b>CA</b> line, being hawk-series cruisers, while the BCH line also
 * carries the Kzinti CV and CVS (carriers on a BCH hull, size class 3) and the Tholian D (a
 * dreadnought whose 1.0 move cost the DN line does not allow). Keying the fleet rule off the line
 * would both miss the three Romulans and wrongly limit four hulls that are not BCHs.
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

    /** S8.333: a BCH takes no squadron of followers, so it is never a leader either. */
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
     * S8.36's named exception. Pinned by name because the rule names it: nothing in the King Eagle's
     * own numbers distinguishes it from a command cruiser, which is why the rule had to say so.
     */
    @Test
    public void theRomulanKingEagleIsNotALeader() {
        ShipSpec ke = ShipLibrary.get("Romulan", "KE");
        assertNotNull("fixture: the Romulan KE should be in the library", ke);
        assertFalse("S8.36: 'King Eagles are considered to be heavy cruisers, not command cruisers'",
                ke.isLeader);
    }

    /** The three hulls S8.333 names are flagged as BCHs, wherever their line puts them. */
    @Test
    public void theRomulanHawkBchsAreFlaggedAsBchs() {
        for (String type : List.of("SUP-A", "RHK", "NHK")) {
            ShipSpec spec = ShipLibrary.get("Romulan", type);
            assertNotNull("fixture: Romulan " + type + " should be in the library", spec);
            assertTrue("S8.333 names the " + type + " as a heavy battlecruiser", spec.isBCH);
        }
    }

    /**
     * And the flag still reaches something — a guard that only ever found nothing would pass just as
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
     * A dreadnought is one by hull family, not only by line: the Tholian D is a dreadnought parked on
     * the BCH line because the DN line's 1.5 move cost would fail {@code ShipLineCatalogTest}. Asking
     * only about the line would let that one through.
     *
     * <p>Both spellings count. The data holds twenty "Dreadnought" and two Gorn "Dreadnaught", and a
     * rule must not turn on which one a file happened to use.
     */
    private static boolean isDreadnought(ShipSpec spec) {
        if ("DN".equals(spec.line))
            return true;
        if (spec.typeName == null)
            return false;
        String name = spec.typeName.toLowerCase();
        return name.contains("dreadnought") || name.contains("dreadnaught");
    }

    private static String describe(ShipSpec spec) {
        return spec.faction + "/" + spec.type + " (line " + spec.line + ", " + spec.typeName + ")";
    }
}
