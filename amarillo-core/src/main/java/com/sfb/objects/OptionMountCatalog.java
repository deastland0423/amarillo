package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Orion option-mount cost chart (Annex #8B, G15.4), loaded from JSON. This
 * is rules reference data — the legal-weapon / cost / constraint table a loadout
 * validator consults when a player fills an {@link OptionMount}. It is not ship
 * data; a ship's chosen options live on the ship.
 *
 * <p>Load once and look up by name via {@link #get(String)}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionMountCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Provenance of the chart (module + revision). */
    public String source;

    /** Notes on scope / field defaults. */
    public String note;

    /** The chart rows. */
    public List<OptionCatalogEntry> options = new ArrayList<>();

    @JsonIgnore
    private Map<String, OptionCatalogEntry> index;

    public static OptionMountCatalog fromJson(File file) throws IOException {
        OptionMountCatalog catalog = MAPPER.readValue(file, OptionMountCatalog.class);
        catalog.buildIndex();
        return catalog;
    }

    private void buildIndex() {
        index = new HashMap<>();
        for (OptionCatalogEntry e : options) {
            index.put(key(e.name), e);
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
    }

    /** The catalog row for the option of this name (case-insensitive), or null if absent. */
    public OptionCatalogEntry get(String name) {
        if (index == null) {
            buildIndex();
        }
        return index.get(key(name));
    }

    public List<OptionCatalogEntry> all() {
        return options;
    }
}
