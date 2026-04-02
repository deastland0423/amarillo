package com.sfb.samples;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Central registry mapping hull-type keys to ship factory methods.
 * Keys use the format FACTION_HULL (e.g. "FED_CA", "KLN_D7", "ROM_KR").
 *
 * Usage:
 *   ship.init(ShipRegistry.build("FED_CA"));
 */
public class ShipRegistry {

    private static final Map<String, Supplier<Map<String, Object>>> REGISTRY;

    static {
        Map<String, Supplier<Map<String, Object>>> m = new LinkedHashMap<>();

        // Federation
        m.put("FED_CA",  FederationShips::getFedCa);
        m.put("FED_OCL", FederationShips::getFedOcl);
        m.put("FED_FFG", FederationShips::getFedFfg);

        // Klingon
        m.put("KLN_D7",  KlingonShips::getD7);
        m.put("KLN_F5",  KlingonShips::getF5);

        // Romulan
        m.put("ROM_KR",  RomulanShips::getRomKr);

        REGISTRY = Collections.unmodifiableMap(m);
    }

    private ShipRegistry() {}

    /**
     * Build ship data for the given key.
     * @throws IllegalArgumentException if the key is not registered.
     */
    public static Map<String, Object> build(String key) {
        Supplier<Map<String, Object>> supplier = REGISTRY.get(key);
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown ship type: " + key
                    + "  Available: " + REGISTRY.keySet());
        }
        return supplier.get();
    }

    /** Returns all registered hull-type keys in insertion order. */
    public static java.util.Set<String> availableTypes() {
        return REGISTRY.keySet();
    }
}
