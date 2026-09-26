package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every key in a ship file must be one the spec declares, in the place the spec declares it.
 * <p>
 * {@link ShipSpec} and all its nested classes carry {@code @JsonIgnoreProperties(ignoreUnknown
 * = true)}, and that is right for production: a ship file written against a later version of
 * the game should load what it can rather than refuse to start. The cost is that a key the
 * spec does not know is indistinguishable from one it does — both load in silence.
 * <p>
 * Three kinds of silence turned up the day this was written (2026-09-25). "typeName" was in
 * all 162 files, wanted, and read by nothing. "oakdisc" was a deliberate note-to-self in one
 * Orion file. And six Tholian ships carried "nimble" inside their auxiliary block instead of
 * at the top level, so six ships the data calls nimble were not nimble in play — a correctly
 * spelled field in the wrong place, which is the hardest of the three to see by eye.
 * <p>
 * So this test does what the loader deliberately will not. It follows the spec by reflection
 * rather than by a hand-written list of paths, so a field added to {@link ShipSpec} tomorrow
 * is accepted tomorrow with nothing to change here.
 */
public class ShipJsonKeyGuardTest {

    /**
     * Paths that are in the data on purpose and not yet in the spec, each with the reason.
     * <p>
     * Keyed by PATH, not by bare name, and the distinction earns its keep: "nimble" and
     * "type" are both real {@link ShipSpec} fields at the top level, so parking either by
     * name would have blessed exactly the misplaced copies that prompted this test.
     * <p>
     * This is the list of "known, not yet built" — it exists so that state is visible rather
     * than silent. Wiring a path into the spec means deleting its line here, and
     * {@link #theAllowListDoesNotOutliveItsKeys()} fails if a line stays behind.
     */
    private static final Map<String, String> PARKED = new LinkedHashMap<>();
    static {
        PARKED.put("control.oakdisc",
                "Orion, 1 file. A special Orion system the owner flagged 2026-09-25 for "
                + "later; purpose and rule number not yet established.");
        PARKED.put("auxiliary.web",
                "Tholian, 7 files. Web generators — the Sequence of Play cites web "
                + "deceleration at (G10.59), so the system is G10 and wholly unbuilt.");
        PARKED.put("auxiliary.maxAccel",
                "4 files, Q-ships and a freighter. A cap on acceleration these hulls cannot "
                + "exceed. Our movement applies no such cap.");
        PARKED.put("auxiliary.slow",
                "2 files, the large Q-ships. Companion to maxAccel; unbuilt.");
        PARKED.put("shuttleBays[].type",
                "6 Kzinti carriers, always \"tunnel\". A kind of bay, distinct from the "
                + "ordinary hangar ShuttleBaySpec assumes. Parked by PATH: a stray \"type\" "
                + "anywhere else in a ship file still fails, which is the point.");
    }

    private static final File SHIP_DIR = new File("../data/factions");

    // -------------------------------------------------------------------------

    @Test
    public void everyKeyInEveryShipFileIsDeclaredOrParked() throws Exception {
        Map<String, List<String>> undeclared = undeclaredPaths();

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : undeclared.entrySet())
            if (!PARKED.containsKey(e.getKey()))
                problems.add(e.getKey() + "  in " + e.getValue());

        assertTrue("ship files carry keys the spec does not declare. Either add the field to"
                + " ShipSpec, correct the spelling — or correct the PLACE, since a real field"
                + " in the wrong block loads into nothing — or park it in PARKED with a"
                + " reason:\n  " + String.join("\n  ", problems), problems.isEmpty());
    }

    /**
     * A parked path that has since been wired into the spec, or removed from the data, leaves
     * a line here claiming something untrue. The list is only useful while every line is live.
     */
    @Test
    public void theAllowListDoesNotOutliveItsKeys() throws Exception {
        Map<String, List<String>> undeclared = undeclaredPaths();

        List<String> stale = new ArrayList<>();
        for (String path : PARKED.keySet())
            if (!undeclared.containsKey(path))
                stale.add(path + " — either no ship file carries it any more, or the spec "
                        + "declares it now; either way it is no longer parked");

        assertTrue("PARKED has entries that are no longer true:\n  "
                + String.join("\n  ", stale), stale.isEmpty());
    }

    // -------------------------------------------------------------------------

    /** Every undeclared path in the ship library, and which files carry it. */
    private Map<String, List<String>> undeclaredPaths() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<String>> found = new LinkedHashMap<>();
        int files = 0;
        for (File faction : dirs(SHIP_DIR))
            for (File f : jsonFiles(faction)) {
                files++;
                check(mapper.readTree(f), ShipSpec.class,
                        faction.getName() + "/" + f.getName(), "", found);
            }
        assertTrue("no ship files found — is the data directory where this test thinks?",
                files > 100);
        return found;
    }

    /**
     * Walk one JSON object against the spec class it describes, collecting undeclared paths.
     * Array indices are dropped — shuttleBays[0] and shuttleBays[3] pose the same question —
     * so one entry covers every element.
     */
    private void check(JsonNode node, Class<?> type, String file, String path,
            Map<String, List<String>> found) {
        if (node == null || !node.isObject())
            return;
        for (Iterator<String> it = node.fieldNames(); it.hasNext();) {
            String key = it.next();
            String where = path.isEmpty() ? key : path + "." + key;

            Field field = declares(type, key);
            if (field == null) {
                found.computeIfAbsent(where, k -> new ArrayList<>()).add(file);
                continue;
            }

            JsonNode value = node.get(key);
            if (value.isObject()) {
                check(value, field.getType(), file, where, found);
            } else if (value.isArray()) {
                Class<?> element = elementType(field);
                if (element != null)
                    for (JsonNode child : value)
                        check(child, element, file, where + "[]", found);
            }
        }
    }

    /** The field of that exact name, on the class or anything it extends; null if none. */
    private static Field declares(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass())
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        return null;
    }

    /**
     * The element class of a {@code List<X>} field, but only when X is one of our own spec
     * classes. A {@code List<String>} of arcs has no keys to check, and neither has an array
     * of shield values, so both answer null and the walk stops there.
     */
    private static Class<?> elementType(Field field) {
        Type generic = field.getGenericType();
        if (!(generic instanceof ParameterizedType))
            return null;
        Type[] args = ((ParameterizedType) generic).getActualTypeArguments();
        if (args.length != 1 || !(args[0] instanceof Class))
            return null;
        Class<?> element = (Class<?>) args[0];
        return element.getName().startsWith(ShipSpec.class.getName()) ? element : null;
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
