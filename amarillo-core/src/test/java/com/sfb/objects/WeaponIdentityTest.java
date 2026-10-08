package com.sfb.objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * No two weapons on one hull may share a type and a designator.
 *
 * <h2>Why that is worse than untidy</h2>
 * A weapon's identity on the wire is {@code type + "-" + designator} — that string is what a client
 * sends back to choose which gun fires, which rack to reload, and which system a hit-and-run party
 * raids. Two weapons sharing it means the second one is <b>unaddressable</b>: it exists on the ship,
 * it absorbs a damage allocation, and no order can ever name it.
 *
 * <h2>How it was found</h2>
 * The refit migration's equivalence check compared the synthesised Federation TUG+ against
 * {@code tug+.json} and reported eight weapons in the file against seven built. The file listed
 * {@code Phaser1 "4"} twice. Nothing else in the build could see it: the hull loads, the count is
 * plausible, and the arcs were identical so even a careful reader scanning the list would not
 * notice the repeat.
 *
 * <p>A sweep for the same shape then found three more, which is why this exists as a guard rather
 * than as a one-off fix.
 */
public class WeaponIdentityTest {

    private static final File FACTIONS = new File("../data/factions");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void noTwoWeaponsOnAHullShareAnIdentity() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        List<String> wrong = new ArrayList<>();
        int checked = 0;

        File[] factions = FACTIONS.listFiles(File::isDirectory);
        for (File faction : factions == null ? new File[0] : factions)
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                JsonNode root = MAPPER.readTree(f);
                JsonNode weapons = root.path("weapons");
                if (!weapons.isArray() || weapons.isEmpty())
                    continue;

                Map<String, Integer> seen = new LinkedHashMap<>();
                for (JsonNode w : weapons) {
                    String id = w.path("type").asText("") + "-" + w.path("designator").asText("");
                    seen.merge(id, 1, Integer::sum);
                    checked++;
                }
                for (Map.Entry<String, Integer> e : seen.entrySet())
                    if (e.getValue() > 1)
                        wrong.add(faction.getName() + "/" + f.getName() + ": '" + e.getKey()
                                + "' appears " + e.getValue() + " times — the second one cannot be"
                                + " named by any order, so it can never fire, reload or be raided");
            }

        assertTrue("there should be weapons to check", checked > 100);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a weapon identity is claimed twice on one hull:" + indent
                + String.join(indent, wrong), wrong.isEmpty());
    }

    /**
     * And a refit must not introduce one either — it adds weapons to a hull that already has some.
     * <p>
     * Checked through the built variant rather than the file, because the collision would only
     * appear once the refit was applied: a refit adding a {@code Phaser1 "7"} to a hull that already
     * carries one reads perfectly well in both halves.
     */
    @Test
    public void noRefitCreatesADuplicateIdentity() {
        ShipLibrary.loadAllSpecs(FACTIONS.getPath());

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.refitOf == null || spec.weapons == null)
                continue;
            checked++;
            Map<String, Integer> seen = new LinkedHashMap<>();
            for (ShipSpec.WeaponSpec w : spec.weapons)
                seen.merge(w.type + "-" + w.designator, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : seen.entrySet())
                if (e.getValue() > 1)
                    wrong.add(spec.faction + "/" + spec.type + " (refit of " + spec.refitOf
                            + "): '" + e.getKey() + "' appears " + e.getValue() + " times");
        }

        assertTrue("there should be refitted hulls to check", checked > 0);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a refit gives a hull two weapons with one identity:" + indent
                + String.join(indent, wrong), wrong.isEmpty());
    }
}
