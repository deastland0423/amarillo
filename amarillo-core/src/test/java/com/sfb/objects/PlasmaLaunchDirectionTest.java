package com.sfb.objects;

import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A plasma launcher's firing arc decides where it may launch, and nothing states it any more.
 *
 * <h2>The rule (D2.34, D2.36)</h2>
 * A swivel mount "is able to track targets in a 180 degree firing arc and to fire its weapons in
 * any of three specified directions". The ARC is what the launcher aims at; the DIRECTION is the
 * facing the torpedo leaves on; and the arc fixes the directions completely, so a hull has no say.
 * {@link ArcUtils#plasmaLaunchDirections} is the single statement of it.
 *
 * <h2>What this replaced</h2>
 * 159 hand-typed direction lists in the ship files, one per launcher, where only one answer was
 * ever possible. A mistyped list was invisible — a wrong set of directions is still a perfectly
 * ordinary set of directions — and three errors were found in one day:
 * <ul>
 *   <li>the Gorn L-Q's two rear launchers, given the left/right split that LP and RP use;</li>
 *   <li>the Gorn BDD and BDL, carrying the RP directions under an arc of RA — where the ARC was
 *       the typo and the directions were right;</li>
 *   <li>sixteen side-arc launchers across eight hulls, each with a spurious 13 that D2.34's
 *       "three specified directions" rules out.</li>
 * </ul>
 *
 * <p>The last is the cautionary one: those sixteen were the MAJORITY, eight to one against the
 * single hull that had it right. In this data a majority is only the number of times something was
 * copied. Deriving makes the whole class of error unrepresentable rather than merely detectable.
 *
 * <p>A file may still state the field — {@code WeaponFactory} prefers a stated list — so a genuine
 * exception stays expressible if one is ever found. {@link #noShipFileStatesLaunchDirections()}
 * fails if one appears, so adding it back is a decision rather than a drift.
 */
public class PlasmaLaunchDirectionTest {

    private static final File FACTIONS = new File("../data/factions");

    @BeforeClass
    public static void loadLibrary() {
        ShipLibrary.loadAllSpecs(FACTIONS.getPath());
    }

    /** Every launcher in the game now gets its directions from its arc, and gets some. */
    @Test
    public void everyBuiltLauncherDerivesItsDirectionsFromItsArc() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;

        for (ShipSpec spec : ShipLibrary.all()) {
            Ship ship = ShipLibrary.createShip(spec);
            for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof PlasmaLauncher pl)) continue;
                checked++;
                if (pl.getLaunchDirections() == 0)
                    wrong.add(spec.faction + "/" + spec.type + " launcher " + pl.getDesignator()
                            + " launches in NO direction — its arc has no rule in"
                            + " ArcUtils.plasmaLaunchDirections");
            }
        }

        assertTrue("there should be plasma launchers to check", checked > 200);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a plasma launcher has no launch direction:" + indent
                + String.join(indent, wrong), wrong.isEmpty());
    }

    /** The exact rule, pinned. These are the numbers, not a restatement of the implementation. */
    @Test
    public void theArcToDirectionRuleIsWhatTheOwnerStated() {
        assertEquals("a 360-degree mount fires in any cardinal direction, not D2.34's three",
                ArcUtils.of(1, 5, 9, 13, 17, 21), ArcUtils.plasmaLaunchDirections(List.of("FULL")));
        assertEquals(ArcUtils.of(1),          ArcUtils.plasmaLaunchDirections(List.of("FA")));
        assertEquals(ArcUtils.of(13),         ArcUtils.plasmaLaunchDirections(List.of("RA")));
        assertEquals(ArcUtils.of(21, 1, 5),   ArcUtils.plasmaLaunchDirections(List.of("FP")));
        assertEquals(ArcUtils.of(17, 21, 1),  ArcUtils.plasmaLaunchDirections(List.of("LP")));
        assertEquals(ArcUtils.of(1, 5, 9),    ArcUtils.plasmaLaunchDirections(List.of("RP")));
        assertEquals(ArcUtils.of(17, 21, 1),  ArcUtils.plasmaLaunchDirections(List.of("LS")));
        assertEquals(ArcUtils.of(1, 5, 9),    ArcUtils.plasmaLaunchDirections(List.of("RS")));
        assertEquals(ArcUtils.of(21),         ArcUtils.plasmaLaunchDirections(List.of("L", "LF")));
        assertEquals(ArcUtils.of(5),          ArcUtils.plasmaLaunchDirections(List.of("R", "RF")));
        assertEquals(ArcUtils.of(17),         ArcUtils.plasmaLaunchDirections(List.of("L", "LR")));
        assertEquals(ArcUtils.of(9),          ArcUtils.plasmaLaunchDirections(List.of("R", "RR")));
        // D2.36, the Gorn battle pod's reverse swivel mounts. No hull carries one yet.
        assertEquals(ArcUtils.of(13, 17, 21), ArcUtils.plasmaLaunchDirections(List.of("LPR")));
        assertEquals(ArcUtils.of(5, 9, 13),   ArcUtils.plasmaLaunchDirections(List.of("RPR")));
        assertEquals(ArcUtils.of(9, 13, 17),  ArcUtils.plasmaLaunchDirections(List.of("AP")));
    }

    /**
     * A multi-token arc means the same thing whichever order it is written in.
     * <p>
     * The owner's point, and the data proves it was needed: both {@code ["L","LF"]} and
     * {@code ["LF","L"]} appear in the ship files for the same arc. The derivation sorts before
     * matching so neither spelling is privileged.
     */
    @Test
    public void theOrderOfTheArcTokensDoesNotMatter() {
        assertEquals(ArcUtils.plasmaLaunchDirections(List.of("L", "LF")),
                     ArcUtils.plasmaLaunchDirections(List.of("LF", "L")));
        assertEquals(ArcUtils.plasmaLaunchDirections(List.of("R", "RF")),
                     ArcUtils.plasmaLaunchDirections(List.of("RF", "R")));
        assertEquals(ArcUtils.plasmaLaunchDirections(List.of("L", "LR")),
                     ArcUtils.plasmaLaunchDirections(List.of("LR", "L")));
        assertEquals("and case is not significant either", ArcUtils.of(1),
                     ArcUtils.plasmaLaunchDirections(List.of("fa")));
    }

    /** An arc with no rule derives nothing, rather than guessing. */
    @Test
    public void anUnknownArcDerivesNothing() {
        assertEquals(0, ArcUtils.plasmaLaunchDirections(List.of("FX")));
        assertEquals(0, ArcUtils.plasmaLaunchDirections(List.of()));
        assertEquals(0, ArcUtils.plasmaLaunchDirections(null));
    }

    /**
     * And no ship file states the field again.
     * <p>
     * WeaponFactory still honours a stated list, so an exception remains possible — but it should
     * cost a deliberate decision and a line here, not slip back in because someone copied an old
     * weapon entry. Checked against the FILES rather than the specs, because that is where a
     * hand-edit lands.
     */
    @Test
    public void noShipFileStatesLaunchDirections() throws Exception {
        java.util.Set<String> stating = new TreeSet<>();
        int files = 0;
        File[] factions = FACTIONS.listFiles(File::isDirectory);
        for (File faction : factions == null ? new File[0] : factions)
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                files++;
                if (java.nio.file.Files.readString(f.toPath()).contains("\"launchDirections\""))
                    stating.add(faction.getName() + "/" + f.getName());
            }

        assertTrue("there should be ship files to read", files > 200);
        assertEquals("a ship file states launchDirections, which the arc now derives — remove it,"
                + " or if this really is an exception to D2.34, say so here", new TreeSet<>(),
                stating);
    }
}
