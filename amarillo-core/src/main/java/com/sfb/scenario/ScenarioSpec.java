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
/**
 * JSON format for a scenario file. Fields not present in older files are
 * ignored or defaulted so existing scenario JSONs keep loading without changes.
 *
 * startSpeed convention: use the actual prior-turn speed (e.g. 15).
 *   Use 16 to represent "Speed Max" (the conventional maximum for most ships).
 *   Acceleration limits on turn 1 are enforced elsewhere using this value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScenarioSpec {

    public String       id;
    public String       name;
    public int          year;
    public int          numPlayers   = 2;
    public String       description;
    public String       mapType      = "STANDARD";   // "STANDARD" | "FLOATING"
    public String       lengthCondition;              // "STANDARD" = last side standing
    public List<String> specialRules;                 // free-text rules from the X.4 section
    public List<SideSpec>        sides;
    public VictoryConditions     victoryConditions;
    public ShuttleRules          shuttleRules    = new ShuttleRules();
    public CommanderOptions      commanderOptions = new CommanderOptions();

    // -------------------------------------------------------------------------
    // Side
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SideSpec {
        public String            faction;          // e.g. "Federation"
        public String            name;             // display name, e.g. "Orion Pirates"
        public List<ShipSetup>   ships;
        public List<Reinforcement> reinforcements; // ships arriving later (optional)
    }

    // -------------------------------------------------------------------------
    // Ship setup
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShipSetup {
        public String       hull;           // e.g. "CC", "DD+"
        public String       shipName;       // scenario-specific name, e.g. "Kongo"
        public String       startHex;       // SFB CCRR notation, e.g. "0515"
        public String       startHeading;   // "A"–"F"
        public int          startSpeed;     // prior-turn speed; 16 = Speed Max
        public int          weaponStatus;   // 0–3 per S4.1
        public List<String> refits;         // refit codes applied to this ship, e.g. "plus", "awr"
    }

    // -------------------------------------------------------------------------
    // Reinforcements
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reinforcement {
        public int              arrivalTurnMin;     // earliest turn the ships can arrive
        public int              arrivalTurnMax;     // latest turn
        public boolean          secretSelection;    // true if player secretly picks the turn
        public String           entryHex;           // starting hex (CCRR or edge label)
        public String           entryHeading;       // "A"–"F"
        public int              entrySpeed;         // speed on arrival; 16 = Speed Max
        public int              weaponStatus;       // 0–3
        public List<ShipSetup>  ships;
        public String           notes;              // any additional rules text
    }

    // -------------------------------------------------------------------------
    // Victory conditions
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VictoryConditions {
        public String type  = "STANDARD";   // "STANDARD" | "MODIFIED" | "SPECIAL"
        public String notes;                // full text description of the conditions
    }

    // -------------------------------------------------------------------------
    // Shuttle / PF rules
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShuttleRules {
        public boolean warpBoosterPacks = true;
        public boolean megapacks        = true;
        public boolean mrsShuttles      = true;
        public boolean pfs              = true;
    }

    // -------------------------------------------------------------------------
    // Commander's Option Items
    // -------------------------------------------------------------------------

    /**
     * budgetPercent: each ship may spend this % of its BPV on options (default 20).
     * maxDroneSpeed: caps drone speed regardless of year (null = year-based).
     * allowTBombs:   whether T-bombs may be purchased (default true).
     * allowCommandos: whether commando units may be purchased (default true).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommanderOptions {
        public int     budgetPercent  = 20;
        public Integer maxDroneSpeed  = null;
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
