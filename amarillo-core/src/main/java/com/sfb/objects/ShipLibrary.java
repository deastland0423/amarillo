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
     * Recursively scan a root directory (e.g. "data/factions") for *.json files
     * and load each as a ShipSpec into the registry.
     */
    public static void loadAllSpecs(String rootPath) {
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
                    String key = key(spec.faction, spec.hull);
                    registry.put(key, spec);
                } catch (IOException e) {
                    System.err.println("ShipLibrary: failed to load " + entry.getPath() + " — " + e.getMessage());
                }
            }
        }
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
