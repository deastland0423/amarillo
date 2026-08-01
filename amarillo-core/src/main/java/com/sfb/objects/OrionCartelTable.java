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
 * The Orion cartel territory table (G15.44), loaded from JSON. Reference data:
 * which cartels exist and, for each, the home + operating-zone empires that
 * decide an option weapon's access tier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrionCartelTable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] DEFAULT_PATHS = {
        "data/reference/orion_cartels.json",     // server (cwd = repo root)
        "../data/reference/orion_cartels.json",  // core tests (cwd = module dir)
    };

    private static OrionCartelTable defaultTable;

    public String source;
    public String note;
    public List<OrionCartel> cartels = new ArrayList<>();

    @JsonIgnore
    private Map<String, OrionCartel> index;

    public static OrionCartelTable fromJson(File file) throws IOException {
        OrionCartelTable table = MAPPER.readValue(file, OrionCartelTable.class);
        table.buildIndex();
        return table;
    }

    /** The table from its standard location, loaded once and cached (empty if missing). */
    public static synchronized OrionCartelTable loadDefault() {
        if (defaultTable == null) {
            for (String path : DEFAULT_PATHS) {
                File f = new File(path);
                if (f.exists()) {
                    try {
                        defaultTable = fromJson(f);
                        break;
                    } catch (IOException e) {
                        System.err.println("Failed to read cartel table at " + path + ": " + e.getMessage());
                    }
                }
            }
            if (defaultTable == null) {
                System.err.println("Cartel table not found on any default path; using empty table");
                defaultTable = new OrionCartelTable();
                defaultTable.buildIndex();
            }
        }
        return defaultTable;
    }

    private void buildIndex() {
        index = new HashMap<>();
        for (OrionCartel c : cartels) {
            index.put(key(c.name), c);
        }
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
    }

    /** The cartel of this name (case-insensitive), or null if unknown. */
    public OrionCartel get(String name) {
        if (index == null) {
            buildIndex();
        }
        return index.get(key(name));
    }

    public List<OrionCartel> all() {
        return cartels;
    }
}
