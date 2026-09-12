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
    public String hull;
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
    /** Carrier escort: cannot be fielded except as part of a carrier group (S8.311). */
    public boolean isEscort;
    /**
     * A true carrier rather than a hybrid. Its fighters count against the battle force's
     * fighter limit (S8.321); hybrids' do not (S8.322). Not inferable from bay contents —
     * plenty of ships carry a few fighters without being carriers.
     */
    public boolean trueCarrier;

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShuttleBaySpec {
        public List<String> shuttles;  // e.g. ["admin", "admin", "gas"]
        public int          launchTubes; // J1.54 — 0 means standard hatch only
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
        /** For drone racks: number of spaces */
        public int spaces;
        /** For ADD: type string */
        public String addType;
        /** For ADD: number of shots */
        public int shots;
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
        m.put("hull", hull);
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
        if (isEscort)
            m.put("isescort", true);
        if (trueCarrier)
            m.put("truecarrier", true);
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
                if (bay.launchTubes > 0) {
                    Map<String, Object> bayMap = new HashMap<>();
                    bayMap.put("shuttles", shuttles);
                    bayMap.put("launchTubes", bay.launchTubes);
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
