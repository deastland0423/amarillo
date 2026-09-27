package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Two ships of one faction may not share a "type".
 * <p>
 * The type is the SSD designation — "CA+", "D7K", "TGC+p" — and it is how a player says which
 * ship they mean. Two files claiming the same one is always a slip, and a quiet one: both
 * ships load, both work, and the only symptom is that a fleet list or a scenario naming that
 * type has two candidates and no way to choose.
 * <p>
 * Found by hand in the Lyran ships (2026-09-26), where three refit pairs had drifted: ca+
 * carried its sibling's "CA+p", and cw+p and tgc+p each carried their sibling's plain "+".
 * Every other pair in the faction — cl, dd, dn, ff — was right, which is what made the three
 * invisible.
 * <p>
 * Across factions is a different matter and deliberately allowed: a Federation CA and a Lyran
 * CA are both "CA", and both are correct.
 */
public class ShipTypeUniquenessTest {

    private static final File SHIP_DIR = new File("../data/factions");

    @Test
    public void noTwoShipsInAFactionShareAType() throws Exception {
        List<String> clashes = new ArrayList<>();
        int checked = 0;

        for (File faction : dirs(SHIP_DIR)) {
            Map<String, List<String>> byType = new LinkedHashMap<>();
            for (File f : jsonFiles(faction)) {
                ShipSpec spec = ShipSpec.fromJson(f);
                if (spec == null || spec.type == null)
                    continue;
                checked++;
                byType.computeIfAbsent(spec.type, t -> new ArrayList<>()).add(f.getName());
            }
            for (Map.Entry<String, List<String>> e : byType.entrySet())
                if (e.getValue().size() > 1)
                    clashes.add(faction.getName() + " type \"" + e.getKey() + "\": "
                            + String.join(", ", e.getValue()));
        }

        assertTrue("the ship library should not be empty", checked > 100);
        assertTrue("two ships of one faction claim the same SSD type, so nothing naming that"
                + " type can say which it means:\n  " + String.join("\n  ", clashes),
                clashes.isEmpty());
    }

    /**
     * The same designation in two different navies is not a clash. Stated as a test so that
     * a future attempt to make types globally unique fails here rather than in the data.
     */
    @Test
    public void thesameTypeInTwoFactionsIsFine() throws Exception {
        String federationCa = typeOf("federation/ca.json");
        String lyranCa = typeOf("lyran/ca.json");

        assertEquals("CA", federationCa);
        assertEquals("a Lyran CA is also a CA", federationCa, lyranCa);
    }

    private String typeOf(String path) throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File(SHIP_DIR, path));
        assertNotNull(path + " should parse", spec);
        return spec.type;
    }

    private static File[] dirs(File root) {
        File[] d = root.listFiles(File::isDirectory);
        return d == null ? new File[0] : d;
    }

    private static File[] jsonFiles(File dir) {
        File[] f = dir.listFiles(n -> n.getName().endsWith(".json"));
        return f == null ? new File[0] : f;
    }
}
