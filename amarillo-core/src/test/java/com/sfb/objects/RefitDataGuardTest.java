package com.sfb.objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Refit blocks (S3.24) are hand-authored from the SSDs, and these are the mistakes that otherwise
 * cost nothing until they cost a battle.
 *
 * <p>Every one of these fired on real data the day it was written. The resolver is deliberately
 * tolerant at runtime — a data typo should not stop the server starting — so the loudness lives
 * here, which is also where the person entering hulls will meet it.
 */
public class RefitDataGuardTest {

    private static final File FACTIONS = new File("../data/factions");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<ShipSpec> hullsWithRefits() throws Exception {
        List<ShipSpec> out = new ArrayList<>();
        File[] factions = FACTIONS.listFiles(File::isDirectory);
        if (factions == null)
            return out;
        for (File faction : factions)
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                ShipSpec spec = MAPPER.readValue(f, ShipSpec.class);
                if (spec.refits != null && !spec.refits.isEmpty())
                    out.add(spec);
            }
        return out;
    }

    private static Set<String> codesOf(ShipSpec hull) {
        Set<String> out = new LinkedHashSet<>();
        for (ShipSpec.RefitSpec r : hull.refits)
            if (r.code != null)
                out.add(r.code);
        return out;
    }

    private void report(List<String> wrong, String what) {
        String indent = System.lineSeparator() + "  ";
        assertTrue(what + ":" + indent + String.join(indent, wrong), wrong.isEmpty());
    }

    /**
     * A {@code requires} naming a refit the hull does not declare does NOTHING, silently.
     * <p>
     * {@link RefitResolver#withRequirements} looks the code up, finds nothing and moves on — so a
     * D6V whose K refit requires "B" when the D6V has no B refit builds a ship with no B refit and
     * no complaint. Found on the owner's first hand-authored hull.
     */
    @Test
    public void everyRequirementNamesARefitTheHullHas() throws Exception {
        assumeTrue(FACTIONS.isDirectory());
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ShipSpec hull : hullsWithRefits()) {
            Set<String> codes = codesOf(hull);
            for (ShipSpec.RefitSpec r : hull.refits) {
                if (r.requires == null)
                    continue;
                for (String need : r.requires) {
                    checked++;
                    if (!codes.contains(need))
                        wrong.add(hull.faction + "/" + hull.type + ": refit " + r.code
                                + " requires '" + need + "', which this hull does not declare "
                                + codes);
                }
            }
        }
        assertTrue("some refits should declare requirements by now", checked > 0);
        report(wrong, "a refit requires something the hull has not got, which is silently ignored");
    }

    /** And a variant cannot ask for a refit the hull has not got, for the same reason. */
    @Test
    public void everyVariantNamesRefitsTheHullHas() throws Exception {
        assumeTrue(FACTIONS.isDirectory());
        List<String> wrong = new ArrayList<>();
        for (ShipSpec hull : hullsWithRefits()) {
            if (hull.variants == null)
                continue;
            Set<String> codes = codesOf(hull);
            for (ShipSpec.VariantSpec v : hull.variants)
                for (String code : v.refits == null ? List.<String>of() : v.refits)
                    if (!codes.contains(code))
                        wrong.add(hull.faction + "/" + hull.type + ": variant " + v.type
                                + " asks for refit '" + code + "', not among " + codes);
        }
        report(wrong, "a variant names a refit its hull does not declare");
    }

    /**
     * A refitted hull cannot enter service before the hull it is a refit OF.
     * <p>
     * The D6V enters service in Y167 and its UIM variant declared Y165, so a D6Vu could be fielded
     * two years before the carrier existed. The override exists for the opposite case — a
     * combination that arrived LATER than its refits would suggest — so it needs a floor.
     */
    @Test
    public void noVariantEntersServiceBeforeItsBaseHull() throws Exception {
        assumeTrue(FACTIONS.isDirectory());
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ShipSpec hull : hullsWithRefits()) {
            if (hull.variants == null)
                continue;
            for (ShipSpec.VariantSpec v : hull.variants) {
                checked++;
                ShipSpec built = RefitResolver.apply(hull, v.refits);
                if (built.serviceYear < hull.serviceYear)
                    wrong.add(hull.faction + "/" + v.type + " enters service in Y"
                            + built.serviceYear + ", before its own hull " + hull.type
                            + " (Y" + hull.serviceYear + ")");
            }
        }
        assertTrue("there should be variants to check", checked > 0);
        report(wrong, "a refitted hull predates the ship it is a refit of");
    }

    /**
     * A refit CODE is the short marker history wrote on the hull — B, K, u, +, p, a — never a type
     * code.
     *
     * <p>This catches a specific defect in the migration generator. Where a refit RENAMES the ship
     * the generator could not work out the marker: a D7C with the K refit is a D7L, so "D7L" does
     * not start with "D7C" and the fallback used the whole type as the code. The result reads
     * "D7L refit" in the data and would put "D7L" on a chip in the fleet builder where it should
     * say "Phaser-1". Two characters covers every real marker, including the Lyran "+p".
     */
    @Test
    public void everyRefitCodeIsAMarkerAndNotATypeCode() throws Exception {
        assumeTrue(FACTIONS.isDirectory());
        List<String> wrong = new ArrayList<>();
        for (ShipSpec hull : hullsWithRefits())
            for (ShipSpec.RefitSpec r : hull.refits)
                if (r.code != null && r.code.length() > 2)
                    wrong.add(hull.faction + "/" + hull.type + ": refit code '" + r.code
                            + "' looks like a type code rather than a marker — the generator could"
                            + " not derive it, so it needs the real one (B, K, u, +, p, a)");
        report(wrong, "a refit code is not a marker");
    }

    /** A refit that changes nothing at all is either a mistake or a cost with no effect. */
    @Test
    public void everyRefitDoesSomething() throws Exception {
        assumeTrue(FACTIONS.isDirectory());
        List<String> wrong = new ArrayList<>();
        for (ShipSpec hull : hullsWithRefits())
            for (ShipSpec.RefitSpec r : hull.refits) {
                boolean touches = r.shields != null || r.epv != null || r.y175Upgrades != null
                        || notEmpty(r.power) || notEmpty(r.auxiliary) || notEmpty(r.control)
                        || (r.addWeapons != null && !r.addWeapons.isEmpty())
                        || (r.replaceWeapons != null && !r.replaceWeapons.isEmpty());
                if (!touches)
                    wrong.add(hull.faction + "/" + hull.type + ": refit " + r.code
                            + " costs " + r.bpv + " BPV and changes nothing");
            }
        report(wrong, "a refit with no effect");
    }

    private static boolean notEmpty(java.util.Map<String, Object> m) {
        return m != null && !m.isEmpty();
    }

    // ------------------------------------------------- y175Upgrades is an OPT-OUT, not an addition

    /**
     * A hull declaring {@code y175Upgrades} must account for every rack its faction default would
     * have upgraded, because declaring the block SKIPS those defaults.
     *
     * <p>{@code ScenarioLoader.applyYearUpgrades} applies a per-ship block and then {@code return}s,
     * so the faction switch below it never runs. The Federation default gives every type-G rack a
     * third reload set (FD3.72), the Klingon and Kzinti defaults upgrade type-A racks — and a block
     * that names only some of a ship's racks silently loses the rest.
     *
     * <p>Found on the Federation NCD and NCD+ (2026-10-08) while answering the owner's question about
     * whether the ADD_6-to-ADD_12 entry was necessary. It is not — that upgrade is universal and runs
     * before any block — but the question is what led here: both hulls named their four type-A racks
     * and not their two type-G ones, so in every Y175 battle they fought two reload sets short.
     *
     * <p>Restating what the default would have done is the price of the opt-out. The block cannot
     * simply be deleted where it carries a {@code refitCost} the default does not charge.
     */
    @Test
    public void aY175BlockAccountsForEveryRackItsFactionDefaultWouldHaveTouched() throws Exception {
        assumeTrue(FACTIONS.isDirectory());

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        File[] factions = FACTIONS.listFiles(File::isDirectory);
        for (File faction : factions == null ? new File[0] : factions)
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                ShipSpec hull = MAPPER.readValue(f, ShipSpec.class);
                if (hull.y175Upgrades == null || hull.weapons == null || hull.faction == null)
                    continue;

                // What this faction's default would have reached for.
                String wanted = switch (hull.faction.toUpperCase()) {
                    case "FEDERATION" -> "TYPE_G";
                    case "KLINGON", "KZINTI" -> "TYPE_A";
                    default -> null;
                };
                if (wanted == null)
                    continue;

                Set<String> named = new LinkedHashSet<>();
                for (ShipSpec.Y175RackUpgrade ru : hull.y175Upgrades.racks)
                    named.add(ru.designator);
                for (ShipSpec.WeaponSpec w : hull.weapons) {
                    if (!"DroneRack".equals(w.type) || !wanted.equals(w.rackType))
                        continue;
                    checked++;
                    if (!named.contains(w.designator))
                        wrong.add(hull.faction + "/" + hull.type + ": " + w.designator + " is "
                                + wanted + ", which the faction default would have upgraded, but the"
                                + " hull's y175Upgrades block does not name it — and declaring the"
                                + " block skips the default");
                }
            }

        assertTrue("some hull should declare a block over racks the default would reach", checked > 0);
        report(wrong, "a y175Upgrades block silently skips its faction default");
    }
}
