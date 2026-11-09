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
 *           "type": "CC",
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
    public int          mapCols      = 42;            // map width in hexes (default 42)
    public int          mapRows      = 32;            // map height in hexes (default 32)
    public int          maxTurns     = 0;             // 0 = no turn limit
    public String       lengthCondition;              // "STANDARD" = last side standing
    public List<String> specialRules;                 // free-text rules from the X.4 section
    public List<SideSpec>        sides;
    public List<TerrainSetup>    terrain;          // optional — asteroid/planet hexes
    /**
     * Terrain described rather than listed: "a field in this region, at about this density".
     * Expanded into {@link #terrain} when the scenario is loaded, so nine hand-written asteroid
     * hexes can become one plan. Seeds are written back on first use, so a file lays out the
     * same map every time it is read.
     */
    public List<TerrainGenerator.Plan> terrainPlan;
    public List<ObjectiveSetup>  objectives;       // optional — capturable objects (stasis boxes, canisters, cargo)
    public VictoryConditions     victoryConditions;
    public ShuttleRules          shuttleRules    = new ShuttleRules();
    public CommanderOptions      commanderOptions = new CommanderOptions();

    // -------------------------------------------------------------------------
    // Terrain
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TerrainSetup {
        public String type;     // "ASTEROID" | "PLANET" | "GAS_GIANT"
        public String hex;      // CCRR notation, e.g. "0515" — the center hex
        public String name;     // optional display name (mainly for planets)
        public int    radius = 0; // GAS_GIANT footprint radius (diameter 2r+1); ignored for other types
        public String tokenArt; // optional counter art path under /tokens/ (overrides the per-type default)
        public List<RingBand> rings; // GAS_GIANT planetary rings (P2.223); ignored for other types
    }

    /** A capturable scenario objective (stasis box, probe canister, cargo pod). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ObjectiveSetup {
        public String       name;                 // display name
        public String       hex;                  // CCRR starting hex
        public List<String> retrieval;            // ["TRACTOR"|"TRANSPORTER"|"SHUTTLE_PICKUP"]; default TRANSPORTER
        public boolean      survivesDestruction = true; // survives its carrier's destruction (SH47.475)
        public int          side;                 // planet hex side 1..6 (A..F) it sits on; 0 = none (SH50.46)
        public int          points;               // victory points to the side controlling it at scenario end (0 = none)
        // Size in cargo spaces (G25.1; Annex #7K lists them: probe 2, large mine 4, stored
        // shuttle 25, PF 125). Boxed: unset means the scenario never said, and the size
        // gates are skipped rather than reading an unstated 0 as weightless.
        public Double       cargoSpaces;
        // (G25.214) Cargo cannot normally be left in space and still exist; a scenario has
        // to grant it for a specific item. Default false is the rule, not a placeholder.
        public boolean      canBeLeftInSpace;
        // (D7.85/D7.824) What an enemy hit-and-run raid may TRY to do to this: seize it,
        // wreck it, either, or neither. Independent flags because D7.85 keeps the distinction
        // on the target — "hit and run raids can destroy, but not capture, a cloaking device"
        // — while which permitted attempt is made stays the raider's choice (D7.824/D7.827).
        // Both default false, which is D7.825's own default: "non-SSD items ... cannot be
        // attacked by hit and run raids unless the rules specifically allow it." SH47.476
        // allows the seizure and not the wrecking; SH35's canister allows neither.
        public boolean      raidCapture;
        public boolean      raidDestroy;
        // (SH47.474) What a ship must do in the hex before it may take this thing.
        // studyMode is "NONE" (the default), "TURNS" — complete turns in the hex, which is
        // SH47.474 — or "RESEARCH_POINTS", G4.11 points so that lab boxes set the pace, for a
        // survey scenario where that is the point. studyRequired is the threshold.
        // See Objective.StudyMode for why SH47 uses TURNS and not points.
        public String       studyMode;
        public int          studyRequired;
        // (SH47.46) Starts face down: nobody knows it is there until a ship enters its hex.
        // Default false — every scenario written before this put its objectives in plain
        // sight, and a default of hidden would have made all of them vanish.
        public boolean      hidden;
    }

    /**
     * (SH47.46) How many of the field's counters hide something, and how many are withdrawn
     * before any are placed.
     *
     * <h2>This block is PUBLIC, and that is correct</h2>
     * It is printed in the rulebook: both players know there are two Stasis Boxes among
     * twenty-two counters and that four are set aside unseen. What they do not know is WHICH,
     * and that assignment is never written here — it is drawn when the Game is built and lives
     * only on the server. The spec is broadcast to the lobby so the deployment screen can show
     * players the map, so anything placed in it is public by construction.
     *
     * <p>Withdrawing before dealing is what makes the COUNT a secret as well as the position:
     * remove four of twenty-two and it may be that two boxes are out there, or one, or none.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchBag {
        /** How many counters hide a capturable object. */
        public int          objectives;
        /** How many hide a nuclear space mine (SH47.48). */
        public int          mines;
        /** Counters set aside face down before any are placed, never looked at. */
        public int          withdrawUnseen;
        /** What each hidden object is; its hex is whichever counter draws it. */
        public ObjectiveSetup objectiveTemplate;
        /**
         * (M2.14) Size classes a dealt mine will accept, or empty/absent for all of them
         * (M2.141). SH47.48 sets "size class 6 or larger", which is 1-6: in SFB a LARGER size
         * class is a SMALLER unit, so this keeps the mine from being swept by drones — which
         * M2.141 names as the whole point of the setting.
         */
        public List<Integer> mineSizeClasses;
    }

    /**
     * (SH47.46) Face-down counters, as HEXES ONLY. Filled in by {@code expandTerrainPlans} from
     * a field that asked to keep its counter positions, and public for the same reason the bag
     * is: a ship searches a counter by entering its hex, which it could not choose to do if the
     * counters were invisible. What each one hides is not here and never will be.
     */
    public List<String> searchMarkers;

    public SearchBag searchBag;

    /** A planetary ring band: enterable asteroid-like hexes at [inner, outer] hex-distance from center. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RingBand {
        public int inner;
        public int outer;
    }

    // -------------------------------------------------------------------------
    // Side
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SideSpec {
        public String            faction;          // e.g. "Federation"
        /**
         * Where this side may set up, when the players place their own ships rather than the
         * scenario fixing every hex. Null means the ships' own startHex values stand.
         * <p>
         * On the spec rather than in the session because the lobby broadcasts the spec, so the
         * deployment screen needs nothing new to learn where a fleet may stand — and a written
         * scenario can say "within six hexes of your own edge" instead of pinning each ship.
         */
        public MapRegion         deploymentZone;
        public String            name;             // display name, e.g. "Orion Pirates"
        public String            cartel;           // Orion cartel (G15.44); null = player picks it at COI
        public List<ShipSetup>   ships;
        /**
         * True when this side's ships are brought rather than listed — somebody's saved fleet
         * stands here. Everything else about the side still applies: its ground, its faction if
         * the scenario insists on one, whatever the victory conditions say about it.
         * <p>
         * This is what lets one scenario format cover both an authored battle and a pick-up
         * game. A pick-up game is a scenario where every side says this and little else.
         */
        public boolean           bringYourOwn;
        public List<Reinforcement> reinforcements; // ships arriving later (optional)
        /** Map edges that are destruction zones for this side: "TOP"|"BOTTOM"|"LEFT"|"RIGHT". */
        public java.util.Set<String> destructionEdges = new java.util.HashSet<>();
        /** SFB directions (A–F) that are destruction zones for accel disengagement (C7.1). */
        public java.util.Set<String> destructionDirections = new java.util.HashSet<>();

        /**
         * Which of C7.0's means of disengagement this side may use. EMPTY OR ABSENT MEANS ALL
         * OF THEM, which is what every scenario written before this field said by omission.
         *
         * <p>A scenario can now say "this side cannot simply outrun the battle" without having
         * to declare the whole map lethal. The permissive default is the same choice R1.7's
         * acceleration flags made: a new restriction must be opted into, or adding the field
         * would silently re-rule every existing scenario.
         *
         * <p>This is a SCENARIO restriction and stacks with the HULL's own — a plain freighter
         * still cannot disengage by acceleration (R1.7) however permissive the scenario is.
         */
        public java.util.Set<com.sfb.properties.DisengagementMethod> disengagement =
                new java.util.HashSet<>();

        /**
         * The SFB directions (A–F) this side may disengage by acceleration ALONG. Empty or
         * absent means any direction.
         *
         * <p>Distinct from {@link #destructionDirections}, and the difference is the point:
         * a direction missing from THIS list is simply not a way out, and the attempt is
         * refused. A direction listed in THAT one kills the ship that takes it. SH47.41's
         * "can only disengage from the 01xx map edge" is the first kind — a refusal, not a
         * death sentence — and until this field existed the only way to express it was to
         * declare every other direction lethal, which is a different rule.
         *
         * <p>Both may be set at once. A direction that is permitted here and lethal there is a
         * trap the scenario meant to lay, so the destruction check runs first.
         */
        public java.util.Set<String> accelDisengageDirections = new java.util.HashSet<>();

        /**
         * The map edges this side may LEAVE by. Empty or absent falls back to "any edge that is
         * not in {@link #destructionEdges}", which is how every scenario behaved before this
         * field and is still the right reading for most of them.
         *
         * <p>The same refusal-versus-death distinction as {@link #accelDisengageDirections},
         * and SH47 is why it exists. SH47.41 says only "the Federation can only disengage from
         * 42xx map edge" — it never says an illegal exit destroys the ship, and SH80.41 DOES
         * say that where it means it. Stating the restriction as three lethal edges, the only
         * way available before, enforced a harsher rule than the scenario prints.
         *
         * <p>Both may be set. A permitted edge that is also a destruction edge kills, because
         * the destruction check runs first — a scenario setting both meant the trap.
         *
         * <p>This list is STATED where the briefing otherwise DERIVES one by complementing the
         * destruction edges. The stated list wins; see {@code GameController.getBriefing}, and
         * the standing rule that where a rule derives a number the data may also state it and
         * core must not refuse the stated one.
         */
        public java.util.Set<String> exitEdges = new java.util.HashSet<>();
    }

    // -------------------------------------------------------------------------
    // Ship setup
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShipSetup {
        public String       faction;        // overrides side faction for this ship (e.g. "Lyran" on a Klingon side)
        public String       type;           // the SSD Type line, e.g. "CC", "DD+"
        public String       shipName;       // scenario-specific name, e.g. "Kongo"
        public String       startHex;       // SFB CCRR notation, e.g. "0515"
        public String       startHeading;   // "A"–"F"
        public int          startSpeed;     // prior-turn speed; 16 = Speed Max
        public int          weaponStatus;   // 0–3 per S4.1
        public List<String> refits;         // refit codes applied to this ship, e.g. "plus", "awr"
        /**
         * Orion option-mount choices the SCENARIO fixes (G15.4), by mount designator → Annex #8B
         * option name. Same shape as {@code CoiLoadout.optionMounts}, deliberately: one record
         * of one fact, differing only in who wrote it.
         *
         * <h2>Who is the shipyard</h2>
         * G15.415 — "Optional weapons cannot be changed without a shipyard overhaul taking
         * several weeks or months" — makes a loadout part of what the ship IS, not a choice made
         * on the morning of the battle. So it is filled by whoever bought the ship:
         * <ul>
         *   <li>the FLEET, when the side brought its own ({@code SideSpec.bringYourOwn});</li>
         *   <li>the SCENARIO, here, when the scenario listed the ship;</li>
         *   <li>the PLAYER at COI, when neither said — a scenario that leaves this empty has
         *       delegated the shipyard to its players, which is a legitimate thing to write.</li>
         * </ul>
         * The precedence needs no arbiter: whatever is filled here is equipped at build, and
         * {@code OptionMountLoadout.validate} refuses a mount that is already filled, so a COI
         * submission cannot overwrite it. A scenario may fill SOME mounts and leave the rest
         * open, and the COI form will offer exactly the ones it left.
         */
        public java.util.Map<String, String> optionMounts = new java.util.LinkedHashMap<>();
        /**
         * R8.R3's wing arc choice per mount: designator -> "FORWARD" or "OUTBOARD".
         *
         * <p>A disruptor or fusion beam in a WING option mount may bear FA or outboard
         * (L + LF on the left, RF + R on the right) "depending on the taste of the Orion
         * player" — one ship may carry one of each. Nothing derives it, so it is bought with
         * the weapon like the weapon itself (G15.415). Absent or unrecognised means FORWARD,
         * and it is ignored for any mount where R8.R3 leaves no choice.
         */
        public java.util.Map<String, String> optionMountArcs = new java.util.LinkedHashMap<>();
        /**
         * R1.14: the NAME of the planet this unit is built on, matching a {@link TerrainSetup}'s
         * name. Set only for a ground base.
         * <p>
         * The planet is named rather than the hex restated, and that is the whole point: a
         * {@code startHex} would state the base's position a second time, and the two can
         * silently disagree the first time somebody nudges the planet while tuning a scenario.
         * The bases would stay behind in empty space with every ground-base rule quietly not
         * applying, because all of them key off the face. One source of truth instead — and it
         * mirrors the engine, whose siting call takes a planet and a face and never a hex.
         * <p>
         * {@code startHex}, {@code startHeading} and {@code startSpeed} are all IGNORED for a
         * ground base: the hex is the planet's, the heading is the face (P2.62), and the speed
         * is zero because a ground base does not move.
         */
        public String       planet;
        /** Which face of that planet it stands on: "A"-"F", or 1-6. See P2.62. */
        public String       planetFace;
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
