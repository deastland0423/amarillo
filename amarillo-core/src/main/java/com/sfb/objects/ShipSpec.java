package com.sfb.objects;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.weapons.Weapon;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ShipSpec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- Top-level fields ---
    public String faction;
    /** The SSD's "Type" line: this exact variant, e.g. "CA+", "D7C". Identity, not family. */
    public String type;
    /**
     * The family this variant serves in, e.g. "CA" for a D7C. Catalogued in
     * data/shiplines/shiplines.json; what the leader rules (S8.36) compare.
     */
    public String line;
    public String name;
    public String tokenArt;  // optional path to a PNG token image, e.g. "federation/constitution.png"
    public int serviceYear;
    public int bpv;
    public int epv;
    public int commandRating;
    public String turnMode;
    public int sizeClass;
    public double moveCost;
    public int breakdown;
    public int bonusHets;
    public boolean nimble;
    public int stealthBonus; // Orion Stealth Bonus in ECM points (G15.8), 0 if none
    public Boolean canDoubleEngines; // G15.28: null/absent = capable (default); set false for freighters & the one non-doubling warship

    // --- Fleet-building classifications (S8.0 patrol scenarios). All default false. ---
    /** Leader variant (CWL, DWL, DDL, CC…): restricted by S8.36/S8.361 when fleet building. */
    public boolean isLeader;
    /**
     * A remark for whoever reads the file. Never rendered, never a rule — it exists so a
     * ship's oddities can be written down beside the ship. Declared rather than parked
     * because it is permanent by design, not a system waiting to be built.
     */
    public String note;

    /**
     * The class written out, e.g. "Commando Cruiser" for type "CMC".
     *
     * {@link #type} is the SSD designation and is what the counter and the tooltip have
     * always shown; this is the name a player would say out loud. Every ship file has
     * carried it since the data was written — nothing read it until now.
     */
    public String typeName;

    /** Carrier escort: cannot be fielded except as part of a carrier group (S8.311). */
    public boolean isEscort;

    /**
     * Aegis fire control fitted to this hull (D13.0): "NONE", "LIMITED" or "FULL".
     * <p>
     * Deliberately NOT derived from {@link #isEscort}. Most aegis ships are carrier escorts,
     * but D13.0 names the Klingon D5 as a non-escort that has it, and plenty of escorts predate
     * the system. The two answer different questions and one field cannot do both.
     * <p>
     * Nor is it derived from {@link #serviceYear}: the pre- and post-Y175 versions are separate
     * hulls with their own years and BPVs — Kzinti EFF (Y168, limited) against AFF (Y175, full).
     * See {@link com.sfb.properties.AegisLevel}.
     */
    public String aegis;

    /**
     * Weapon TYPES this ship's aegis may control, when it cannot control everything.
     * <p>
     * D13.22's default is that "the aegis system can control all direct-fire weapons", and
     * absent or empty means exactly that. The clause continues "unless noted otherwise, for
     * example the D5" — so a hull may name a shorter list, and the Klingon D5's is its ADDs
     * and its four phaser-3s.
     * <p>
     * Type names, matching {@code Weapon.getType()} and the "type" in this file's own weapon
     * entries: "ADD", "Phaser3". Not designators — a restriction that happened to name every
     * weapon of a type would be the same list written less robustly, and would rot the moment
     * a variant renumbered its mounts.
     */
    public java.util.List<String> aegisWeapons;
    /**
     * Cannot be fielded without an escort group (S8.315): a size class 2 ship of this kind
     * needs three escorts, class 3 two, class 4 one, at least one of them size class 4.
     * <p>
     * Also decides S8.321's fighter limit — a true carrier's fighters count against the battle
     * force's allowance, a hybrid's do not (S8.322) — which is unimplemented but will read
     * this flag, not carrierClass: a Hydran Ranger is a CAPABLE carrier whose Stingers do NOT
     * count. Named for the escort constraint because that is what it enforces today and
     * nothing says only a carrier can need escorts.
     */
    public boolean requiresEscort;
    /**
     * What kind of carrier this is (J4.61/J4.62) — which decides capabilities, not fleet
     * legality: extra deck crews, EW lent to fighters, and S4.1's weapon status provisions.
     * Separate from requiresEscort because a Hydran fighter ship is a capable carrier that is
     * fielded without escorts. Not inferable from bay contents: plenty of ships carry a few
     * fighters without being carriers.
     */
    public String carrierClass;
    /**
     * Spaces of spare drones this carrier holds for its fighters (J4.7).
     * <p>
     * Per ship, out of Annex #7G, and declared rather than derived: the rulebook prints only
     * the Kzinti CV's 150 and there is no formula behind the rest. J4.72 makes this the TOTAL
     * — the ready racks and the fighters' own loads come out of it, not on top of it.
     * <p>
     * Zero means no supply, which is the honest answer for every hull whose Annex #7G line we
     * have not read: its racks hold what they hold and cannot be refilled.
     */
    public double droneStorageSpaces;
    /**
     * Heavy battlecruiser. No more than one may be in a battle force, though it needs no
     * squadron of followers and may be there alongside the one allowed size class 2 ship
     * (S8.333).
     */
    public boolean isBCH;

    public int[] shields;

    // --- Nested specs ---
    public HullSpec hullBoxes;
    public PowerSpec power;
    public ControlSpec control;
    public TableSpec tables;
    public AuxiliarySpec auxiliary;
    public CrewSpec crewData;

    public List<WeaponSpec> weapons;
    public List<OptionMountSpec> optionMounts; // Orion "OPT" boxes (G15.4); empty until filled at setup
    public List<ShuttleBaySpec> shuttleBays;
    /** If present, applied instead of faction default Y175 upgrades. Empty lists = fully exempt. */
    public Y175Upgrades y175Upgrades;

    // -------------------------------------------------------------------------
    // Inner spec classes
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HullSpec {
        public int fhull;
        public int ahull;
        public int chull;
        public int armor;
        public int cargo;
        public int barracks; // Not a real hull box type, but used for boarding party calculations
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionMountSpec {
        public String position;   // "CENTERLINE" | "WING" (G15.43)
        public String designator; // e.g. "A"
        public List<String> arcs; // e.g. ["FA"]
        public WeaponSpec weapon; // pinned weapon (G15.4); null = empty mount, player-chosen at setup
        public double bpvCost;    // BPV delta of the pinned option (Annex #8B); may be negative/fractional
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PowerSpec {
        public int leftWarp;
        public int rightWarp;
        public int centerWarp;
        public int impulse;
        public int apr;
        public int awr;
        public int battery;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ControlSpec {
        public int bridge;
        public int emergency;
        public int auxCon;
        public int flag;
        public int security;
        public double controlMod;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableSpec {
        public int[] damageControl;
        public int[] sensor;
        public int[] scanner;
        public int excess;
    }

    /**
     * A bay's fighter complement by ROLE, resolved to types by the year (J4.4).
     * <p>
     * The alternative was to list concrete fighters in {@code shuttles}, which can only ever be
     * right for one era: a Kzinti CVS entered service in Y170 flying AAS, re-equipped with HAAS
     * in Y173, TAAS in Y177, TADS in Y180 and TADSC in Y183, and none of that changes its SSD.
     * Authoring five files per carrier would multiply every hull correction by five and leave
     * the fighters' BPV — which feeds the carrier's S3.211 option budget — to be maintained by
     * hand in each.
     * <p>
     * So the ship declares the shape of its complement and {@code fighterLines} in
     * shuttles.json decides what fills it. Counts are per role; a role the era has no type for
     * falls back to the standard fighter.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FighterComplementSpec {
        /** Line name as keyed in shuttles.json, e.g. "kzinti-attack". */
        public String line;
        /** General-purpose fighters. */
        public int standard;
        /**
         * Attack fighters, where the line has them (the Hydran Stinger-H).
         * <p>
         * NOT "heavy": a heavy fighter is a separate SFB category that J4.463 counts in
         * its own right ("five or more heavy fighters"), so the name is reserved for it.
         */
        public int attack;
        /** EW fighters (J4.463 caps how many a carrier may field). */
        public int ew;

        public int total() {
            return standard + attack + ew;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShuttleBaySpec {
        public List<String> shuttles;  // e.g. ["admin", "admin", "gas"]
        /**
         * Fighters by role, resolved by year (J4.4). Sits alongside {@code shuttles} rather
         * than replacing it: the admin shuttles sharing the bay stay listed literally, because
         * nothing about them changes with the year.
         */
        public FighterComplementSpec fighters;
        public int          launchTubes; // J1.54 — 0 means standard hatch only
        /**
         * J1.58: "tunnel" for a bay with doors at both ends, each hatch working
         * independently at the full J1.50 rate. The Kzinti CV, CVS, CVL, MCV and CVE are
         * built this way, as is the Federation CVS. Absent means an ordinary one-hatch bay.
         * <p>
         * A hatch is not a launch tube: a tube cannot recover a shuttle (J1.541) and will
         * not pass an administrative shuttle or a heavy fighter (J1.542). A second hatch
         * has no such limits, which is why a tunnel deck is counted here and not as tubes.
         */
        public String       type;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuxiliarySpec {
        public int transporters;
        public int tractors;
        public int labs;
        public int probes;
        public int shuttles;
        public int tBombs;
        public int dummyTBombs;
        public int nuclearSpaceMines;
        public int cloakCost;
        public boolean derfacs;
        public int uim;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrewSpec {
        public int totalCrew;
        public int boardingParties;
        public int deckCrews;
        public int minCrew;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175RackUpgrade {
        public String designator;   // matches weapon designator in the ship JSON
        public String upgradeTo;    // DroneRackType name, e.g. "TYPE_B", "TYPE_C"
        public int    extraReloads; // additional reload sets (e.g. Federation TYPE_G +1)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175AddUpgrade {
        public String designator;  // matches weapon designator
        public String upgradeTo;   // AddType name, e.g. "ADD_12"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Y175Upgrades {
        public List<Y175RackUpgrade> racks = new ArrayList<>();
        public List<Y175AddUpgrade>  adds  = new ArrayList<>();
        public int refitCost = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeaponSpec {
        public String type;
        public String designator;
        public List<String> arcs;
        /** For disruptors: range (e.g. 30 or 15) */
        public int range;
        /** For plasma launchers: "R", "S", "G", "F" etc. */
        public String plasmaType;
        /** For plasma launchers: legal facing directions on launch */
        public List<String> launchDirections;
        /** For drone racks: "TYPE_F", "TYPE_G" */
        public String rackType;
        /**
         * For drone RAILS: "LIGHT", "STANDARD", "SPECIAL", "HEAVY" (J4.231).
         * <p>
         * Required, not defaulted. The rail decides what fits on it and what that costs, so a
         * rail of the wrong type quietly accepts the wrong drones — a worse failure than the
         * factory refusing to build it.
         */
        public String railType;
        /** For drone racks: number of spaces */
        public int spaces;
        /** For ADD: type string */
        public String addType;
        /** For ScoutChannel: DAC hit location of the weapon it replaced (G24.17), e.g. "torp", "phaser". */
        public String dacHitLocation;
    }

    // -------------------------------------------------------------------------
    // JSON loading
    // -------------------------------------------------------------------------

    public static ShipSpec fromJson(File file) throws IOException {
        return MAPPER.readValue(file, ShipSpec.class);
    }

    // -------------------------------------------------------------------------
    // Conversion to Map for Ship.init()
    // -------------------------------------------------------------------------

    /**
     * Convert this spec into the Map<String, Object> format expected by
     * Ship.init().
     */
    public Map<String, Object> toInitMap() {
        Map<String, Object> m = new HashMap<>();

        m.put("faction", Faction.valueOf(faction));
        m.put("type", type);
        if (line != null && !line.isBlank())
            m.put("line", line);
        m.put("name", name);
        if (tokenArt != null) m.put("tokenart", tokenArt);
        m.put("serviceyear", serviceYear);
        m.put("bpv", bpv);
        m.put("epv", epv);
        if (commandRating > 0)
            m.put("commandrating", commandRating);
        m.put("turnmode", TurnMode.valueOf(turnMode));
        m.put("sizeclass", sizeClass);
        m.put("movecost", moveCost);
        m.put("breakdown", breakdown);
        m.put("bonushets", bonusHets);
        if (nimble)
            m.put("nimble", true);
        if (isLeader)
            m.put("isleader", true);
        if (typeName != null)
            m.put("typename", typeName);
        if (isEscort)
            m.put("isescort", true);
        if (aegis != null)
            m.put("aegis", aegis);
        if (aegisWeapons != null && !aegisWeapons.isEmpty())
            m.put("aegisweapons", aegisWeapons);
        if (requiresEscort)
            m.put("requiresescort", true);
        if (carrierClass != null)
            m.put("carrierclass", carrierClass);
        if (droneStorageSpaces > 0)
            m.put("dronestoragespaces", droneStorageSpaces);
        if (isBCH)
            m.put("isbch", true);
        if (stealthBonus > 0)
            m.put("stealthbonus", stealthBonus);
        if (canDoubleEngines != null)
            m.put("candoubleengines", canDoubleEngines);
        if (optionMounts != null && !optionMounts.isEmpty())
            m.put("optionmounts", buildOptionMounts());

        // Shields
        if (shields != null && shields.length >= 6) {
            m.put("shield1", shields[0]);
            m.put("shield2", shields[1]);
            m.put("shield3", shields[2]);
            m.put("shield4", shields[3]);
            m.put("shield5", shields[4]);
            m.put("shield6", shields[5]);
        }

        // Hull boxes
        if (hullBoxes != null) {
            if (hullBoxes.fhull > 0)
                m.put("fhull", hullBoxes.fhull);
            if (hullBoxes.ahull > 0)
                m.put("ahull", hullBoxes.ahull);
            if (hullBoxes.chull > 0)
                m.put("chull", hullBoxes.chull);
            if (hullBoxes.cargo > 0)
                m.put("cargo", hullBoxes.cargo);
            if (hullBoxes.armor > 0)
                m.put("armor", hullBoxes.armor);
        }

        // Power
        if (power != null) {
            if (power.leftWarp > 0)
                m.put("lwarp", power.leftWarp);
            if (power.rightWarp > 0)
                m.put("rwarp", power.rightWarp);
            if (power.centerWarp > 0)
                m.put("cwarp", power.centerWarp);
            if (power.impulse > 0)
                m.put("impulse", power.impulse);
            if (power.apr > 0)
                m.put("apr", power.apr);
            if (power.awr > 0)
                m.put("awr", power.awr);
            if (power.battery > 0)
                m.put("battery", power.battery);
        }

        // Control
        if (control != null) {
            m.put("bridge", control.bridge);
            m.put("emer", control.emergency);
            m.put("auxcon", control.auxCon);
            if (control.security > 0)
                m.put("security", control.security);
            m.put("controlmod", control.controlMod);
            if (control.flag > 0)
                m.put("flag", control.flag);
        }

        // Tables
        if (tables != null) {
            m.put("damcon", tables.damageControl);
            m.put("sensor", tables.sensor);
            m.put("scanner", tables.scanner);
            m.put("excess", tables.excess);
        }

        // Auxiliary
        if (auxiliary != null) {
            m.put("trans", auxiliary.transporters);
            m.put("tractor", auxiliary.tractors);
            m.put("lab", auxiliary.labs);
            m.put("probe", auxiliary.probes);
            m.put("shuttle", auxiliary.shuttles);
            if (auxiliary.tBombs > 0)
                m.put("tbombs", auxiliary.tBombs);
            if (auxiliary.dummyTBombs > 0)
                m.put("dummytbombs", auxiliary.dummyTBombs);
            if (auxiliary.nuclearSpaceMines > 0)
                m.put("nuclearspacemines", auxiliary.nuclearSpaceMines);
            if (auxiliary.cloakCost > 0)
                m.put("cloakcost", auxiliary.cloakCost);
            if (auxiliary.derfacs)
                m.put("derfacs", true);
            if (auxiliary.uim > 0)
                m.put("uim", auxiliary.uim);
        }

        // Shuttle bays — use object format when launch tubes are specified (J1.54)
        if (shuttleBays != null && !shuttleBays.isEmpty()) {
            List<Object> bayList = new ArrayList<>();
            for (ShuttleBaySpec bay : shuttleBays) {
                List<String> shuttles = bay.shuttles != null ? bay.shuttles : new ArrayList<>();
                // A role-based complement forces the object form, since the plain list has
                // nowhere to carry it.
                if (bay.launchTubes > 0 || bay.type != null || bay.fighters != null) {
                    Map<String, Object> bayMap = new HashMap<>();
                    bayMap.put("shuttles", shuttles);
                    if (bay.launchTubes > 0)
                        bayMap.put("launchTubes", bay.launchTubes);
                    if (bay.type != null)
                        bayMap.put("type", bay.type);
                    if (bay.fighters != null) {
                        Map<String, Object> f = new HashMap<>();
                        f.put("line", bay.fighters.line);
                        f.put("standard", bay.fighters.standard);
                        f.put("attack", bay.fighters.attack);
                        f.put("ew", bay.fighters.ew);
                        bayMap.put("fighters", f);
                    }
                    bayList.add(bayMap);
                } else {
                    bayList.add(shuttles);
                }
            }
            m.put("shuttlebays", bayList);
        }

        // Crew
        if (crewData != null) {
            m.put("crew", crewData.totalCrew);
            m.put("boardingparties", crewData.boardingParties);
            m.put("minimumcrew", crewData.minCrew);
            if (crewData.deckCrews > 0)
                m.put("deckcrews", crewData.deckCrews);
        }

        // Weapons
        if (weapons != null) {
            m.put("weapons", buildWeapons());
        }

        return m;
    }

    // -------------------------------------------------------------------------
    // Weapon building
    // -------------------------------------------------------------------------

    private List<OptionMount> buildOptionMounts() {
        List<OptionMount> list = new ArrayList<>();
        for (OptionMountSpec ms : optionMounts) {
            OptionMount.Position pos = "WING".equalsIgnoreCase(ms.position)
                    ? OptionMount.Position.WING
                    : OptionMount.Position.CENTERLINE;
            List<String> arcs = (ms.arcs == null || ms.arcs.isEmpty()) ? List.of("FULL") : ms.arcs;
            OptionMount mount = new OptionMount(pos, ms.designator, arcs);
            if (ms.weapon != null) {
                // Pinned weapon (G15.4): build it with the MOUNT's arc — the mount
                // defines where the option fires, not the weapon's native arc.
                Weapon w = WeaponFactory.build(ms.weapon, arcs);
                if (w != null) {
                    mount.setWeapon(w);
                }
                mount.setBpvCost(ms.bpvCost);
            }
            list.add(mount);
        }
        return list;
    }

    private List<Weapon> buildWeapons() {
        List<Weapon> list = new ArrayList<>();
        for (WeaponSpec ws : weapons) {
            Weapon w = WeaponFactory.build(ws, ws.arcs);
            if (w != null) {
                // ESG capacitor presence (G23.24) is decided by the SCENARIO year in
                // ScenarioLoader (a Y120 hull in a Y167+ battle still has capacitors),
                // not here — weapons are built before the battle year is known.
                list.add(w);
            }
        }
        return list;
    }

}
