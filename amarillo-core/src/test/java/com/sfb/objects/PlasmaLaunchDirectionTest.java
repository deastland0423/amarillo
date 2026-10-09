package com.sfb.objects;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A plasma launcher's firing arc fixes the directions it may launch in.
 *
 * <h2>The rule, from the owner, 2026-10-09</h2>
 * It is a property of the ARC, not of the ship, so there is exactly one right answer per arc and
 * a hull cannot have an opinion about it:
 *
 * <pre>
 *   FA    -> 1
 *   RA    -> 13
 *   FP    -> 21, 1, 5
 *   LP    -> 17, 21, 1
 *   RP    -> 1, 5, 9
 *   LF,L  -> 21
 *   RF,R  -> 5
 * </pre>
 *
 * <h2>Why a guard and not a fix</h2>
 * Nothing derives these today — every launcher in every ship file states its own directions by
 * hand, 141 of them, so a single mistyped list is invisible. Two were found the day this was
 * written and neither looked wrong on the page: the Gorn L-Q's rear launchers had been given the
 * left/right split that LP and RP use, and the Gorn BDD and BDL had the RP directions under an
 * arc of RA — where the ARC was the typo, not the directions.
 *
 * <p>Cross-checking each launcher against its arc is what made both visible, because the fleet is
 * overwhelmingly consistent: the outliers stood out at two against thirty-five. This test is that
 * cross-check, kept.
 *
 * <p>The better fix is to DERIVE the directions from the arc and stop storing them. That is a
 * data-model change touching 141 launchers and the refit blocks that add more, so it is not done
 * here — but if it is ever done, this test is what proves the derivation agrees with the data it
 * replaces.
 */
public class PlasmaLaunchDirectionTest {

    /** Arc (as the ship file spells it) to the only directions a plasma on it may launch in. */
    private static final Map<String, List<String>> BY_ARC = new LinkedHashMap<>();
    static {
        BY_ARC.put("FA",   List.of("1"));
        BY_ARC.put("RA",   List.of("13"));
        BY_ARC.put("FP",   List.of("21", "1", "5"));
        BY_ARC.put("LP",   List.of("17", "21", "1"));
        BY_ARC.put("RP",   List.of("1", "5", "9"));
        BY_ARC.put("L,LF", List.of("21"));
        BY_ARC.put("LF,L", List.of("21"));
        BY_ARC.put("R,RF", List.of("5"));
        BY_ARC.put("RF,R", List.of("5"));
    }

    /**
     * Arcs the owner's list does not cover, held as a roster so a NEW uncovered arc fails here.
     *
     * <p>LS and RS are the two side arcs in use. The fleet says 13,17,21,1 and 1,5,9,13 by eight
     * launchers to one in each case, the dissenter being the Gorn DN+ in both — which came from
     * dnf.json and is the open question, not the rule. Until the owner states the side arcs, this
     * test checks only that nothing ELSE has appeared.
     */
    private static final Set<String> NOT_YET_STATED = new LinkedHashSet<>(List.of("LS", "RS"));

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static String key(List<String> arcs) {
        return arcs == null ? "" : String.join(",", arcs);
    }

    @Test
    public void everyPlasmaLauncherLaunchesWhereItsArcAllows() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;

        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.weapons == null) continue;
            for (ShipSpec.WeaponSpec w : spec.weapons) {
                if (!"PlasmaLauncher".equals(w.type)) continue;
                String arc = key(w.arcs);
                List<String> want = BY_ARC.get(arc);
                if (want == null) continue;        // covered by the roster test below
                checked++;
                List<String> got = w.launchDirections == null ? List.of() : w.launchDirections;
                if (!want.equals(got))
                    wrong.add(spec.faction + "/" + spec.type + " launcher " + w.designator
                            + " on arc " + arc + " launches " + got + ", should be " + want);
            }
        }

        assertTrue("there should be plasma launchers to check", checked > 100);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a plasma launcher disagrees with its arc:" + indent + String.join(indent, wrong),
                wrong.isEmpty());
    }

    /**
     * And no arc appears that the rule has nothing to say about.
     * <p>
     * A roster rather than an invariant, deliberately: a plasma turning up on an arc nobody has
     * ruled on should cost a decision here, not be silently skipped by the test above.
     */
    @Test
    public void onlyTheKnownUnstatedArcsAreUncovered() {
        Set<String> uncovered = new TreeSet<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.weapons == null) continue;
            for (ShipSpec.WeaponSpec w : spec.weapons) {
                if (!"PlasmaLauncher".equals(w.type)) continue;
                String arc = key(w.arcs);
                if (!BY_ARC.containsKey(arc)) uncovered.add(arc);
            }
        }
        assertEquals("a plasma launcher sits on an arc the launch-direction rule does not cover;"
                + " ask the owner what it launches in, then add it to BY_ARC",
                new TreeSet<>(NOT_YET_STATED), uncovered);
    }
}
