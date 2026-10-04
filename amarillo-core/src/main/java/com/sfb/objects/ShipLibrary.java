package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Static registry of all ship specs loaded from JSON files on disk.
 * Keys are "FACTION_HULL" in upper case, e.g. "FEDERATION_CA", "KLINGON_D7".
 */
public class ShipLibrary {

    private static final Map<String, ShipSpec> registry = new HashMap<>();

    /**
     * Faction+type keys that more than one file claimed, newest file winning (J-none; this is a
     * data-integrity concern, not a rule).
     * <p>
     * {@code registry.put} silently replaced the earlier spec, so a second file declaring an
     * existing type deleted a ship from the game with no symptom anywhere: the hull simply was
     * not there, and every guard passed because every guard walks the REGISTRY. Found 2026-10-03
     * when a Hydran file named ah.json still declared {@code "type": "EH"} and the Y175
     * aegis-FULL Escort Hunter did not exist in play.
     * <p>
     * Recorded rather than thrown: a half-loaded library is worse than a shadowed hull, and the
     * build-time guard is what should fail. {@link #duplicateKeys()} is what it reads.
     */
    private static final Map<String, String> duplicates = new java.util.LinkedHashMap<>();

    /** Which file first claimed each key, so a collision can name both sides. */
    private static final Map<String, String> sourceFile = new HashMap<>();

    /**
     * Recursively scan a root directory (e.g. "data/factions") for *.json files
     * and load each as a ShipSpec into the registry.
     */
    public static void loadAllSpecs(String rootPath) {
        // Per-LOAD, not cumulative. The registry is deliberately not cleared — re-loading the
        // same tree just re-puts identical specs, and several test classes load it per JVM — but
        // collision tracking must start empty or the second load reports every ship as a
        // duplicate of itself.
        sourceFile.clear();
        duplicates.clear();
        scanDirectory(new File(rootPath));
    }

    private static void scanDirectory(File dir) {
        if (!dir.isDirectory()) return;
        File[] entries = dir.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            if (entry.isDirectory()) {
                scanDirectory(entry);
            } else if (entry.getName().endsWith(".json")) {
                try {
                    ShipSpec spec = ShipSpec.fromJson(entry);
                    // Not every JSON file under data/factions is a ship — fighter catalogues and
                    // any other per-faction table live here too. A ship spec without a hull is
                    // not one, and registering it would put a junk entry under a null key.
                    if (spec.type == null || spec.type.isBlank())
                        continue;
                    String key = key(spec.faction, spec.type);
                    String prior = sourceFile.put(key, entry.getName());
                    if (prior != null) {
                        duplicates.put(key, prior + " overwritten by " + entry.getName());
                        System.err.println("ShipLibrary: " + spec.faction + "/" + spec.type
                                + " declared by both " + prior + " and " + entry.getName()
                                + " — the first is being discarded");
                    }
                    registry.put(key, spec);
                } catch (IOException e) {
                    System.err.println("ShipLibrary: failed to load " + entry.getPath() + " — " + e.getMessage());
                }
            }
        }
    }

    /**
     * Faction+type keys claimed by more than one file in the last load, mapped to a description
     * of which file lost. Empty when the data is clean.
     */
    public static Map<String, String> duplicateKeys() {
        return Collections.unmodifiableMap(duplicates);
    }

    /** Look up a spec by faction and hull type. Returns null if not found. */
    public static ShipSpec get(String faction, String hull) {
        return registry.get(key(faction, hull));
    }

    /** All loaded specs. */
    public static Collection<ShipSpec> all() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /** True if any specs have been loaded. */
    public static boolean isLoaded() {
        return !registry.isEmpty();
    }

    /** Instantiate a Ship from a spec. Caller sets location, facing, speed. */
    public static Ship createShip(ShipSpec spec) {
        Ship ship = new Ship();
        ship.init(spec.toInitMap());
        return ship;
    }

    private static String key(String faction, String hull) {
        return (faction + "_" + hull).toUpperCase();
    }
}
