package com.sfb.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Data class mirroring a scenario JSON file.
 *
 * JSON format:
 * {
 *   "id": "SH67",
 *   "name": "Diplomatic Immunity",
 *   "year": 163,
 *   "description": "...",
 *   "lengthCondition": "STANDARD",
 *   "sides": [
 *     {
 *       "name": "Federation",
 *       "faction": "Federation",
 *       "ships": [
 *         {
 *           "hull": "CC",
 *           "shipName": "Kongo",
 *           "startHex": "0515",
 *           "startHeading": "C",
 *           "startSpeed": 5,
 *           "weaponStatus": 0
 *         }
 *       ]
 *     }
 *   ],
 *   "victoryConditions": { "type": "STANDARD" }
 * }
 *
 * startHex uses SFB CCRR notation (column/row, zero-padded to 2 digits each).
 * startHeading is "A"–"F" mapping to internal facings 1, 5, 9, 13, 17, 21.
 * weaponStatus is 0–3 per S4.1 rules.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioSpec {

    public String id;
    public String name;
    public int    year;
    public String description;
    public String lengthCondition;      // "STANDARD" = last side standing
    public List<SideSpec> sides;
    public VictoryConditions victoryConditions;
    public CommanderOptions commanderOptions = new CommanderOptions(); // defaults if omitted

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SideSpec {
        public String faction;          // matches Faction enum name, e.g. "Federation"
        public String name;             // display name for this side, e.g. "Orion Pirates"
        public List<ShipSetup> ships;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShipSetup {
        public String hull;             // matches hull field in ship JSON, e.g. "CC", "DD+"
        public String shipName;         // scenario-specific ship name, e.g. "Kongo"
        public String startHex;         // SFB CCRR notation, e.g. "0515"
        public String startHeading;     // "A"–"F"
        public int    startSpeed;
        public int    weaponStatus;     // 0, 1, 2, or 3
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VictoryConditions {
        public String type;             // "STANDARD" for now
    }

    /**
     * Commander's Option Items rules for this scenario.
     * budgetPercent: each ship may spend this % of its BPV on options (default 20).
     * maxDroneSpeed: caps drone speed regardless of year (null = use year-based availability).
     * allowTBombs: whether T-bombs may be purchased (default true).
     * allowCommandos: whether commando units may be purchased (default true).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommanderOptions {
        public int     budgetPercent  = 20;
        public Integer maxDroneSpeed  = null;   // null = year determines availability
        public boolean allowTBombs    = true;
        public boolean allowCommandos = true;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ScenarioSpec fromJson(File file) throws IOException {
        return MAPPER.readValue(file, ScenarioSpec.class);
    }

    public static ScenarioSpec fromJson(String path) throws IOException {
        return fromJson(new File(path));
    }
}
