package com.sfb.scenario;

import com.sfb.exceptions.CapacitorException;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Faction;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Converts a ScenarioSpec into configured Ship objects ready to be added to a Game.
 *
 * Heading → facing mapping (SFB standard, 24-step internal system):
 *   A=1, B=5, C=9, D=13, E=17, F=21
 *
 * Weapon status effects on initial ship state (S4.10–S4.13):
 *   WS-0: capacitors uncharged (cannot hold energy until 1 pt spent to energize)
 *   WS-1: capacitors charged, phaser cap empty
 *   WS-2: capacitors charged, phaser cap full
 *   WS-3: capacitors charged, phaser cap full (multi-turn weapon pre-arming deferred)
 */
public class ScenarioLoader {

    /**
     * Build all ships for every side in the scenario.
     * Returns a list of ship lists, one per side, in the same order as spec.sides.
     * Ships that cannot be resolved (missing spec) are skipped with a warning.
     */
    public static List<List<Ship>> loadShips(ScenarioSpec spec) {
        List<List<Ship>> result = new ArrayList<>();
        if (spec.sides == null)
            return result;
        for (ScenarioSpec.SideSpec side : spec.sides) {
            List<Ship> ships = new ArrayList<>();
            // A side may list no ships at all: one marked bringYourOwn is waiting for a fleet,
            // and has none until somebody hands it one.
            if (side.ships == null) {
                result.add(ships);
                continue;
            }
            for (ScenarioSpec.ShipSetup setup : side.ships) {
                String faction = (setup.faction != null && !setup.faction.isBlank()) ? setup.faction : side.faction;
                Ship ship = buildShip(faction, setup, spec.year);
                if (ship != null) ships.add(ship);
            }
            result.add(ships);
        }
        return result;
    }

    private static Ship buildShip(String faction, ScenarioSpec.ShipSetup setup, int year) {
        ShipSpec shipSpec = ShipLibrary.get(faction, setup.type);
        if (shipSpec == null) {
            System.err.println("ScenarioLoader: no spec found for "
                    + faction + "/" + setup.type + " — ship skipped");
            return null;
        }
        Ship ship = ShipLibrary.createShip(shipSpec);
        ship.setName(setup.shipName);
        ship.getShuttles().prefixShuttleNames(setup.shipName);
        ship.setLocation(parseHex(setup.startHex));
        ship.setFacing(parseHeading(setup.startHeading));
        ship.setSpeed(setup.startSpeed);
        // Seed C2.2 speed history — assume ship has been at startSpeed for at least 2 turns
        ship.setSpeedPreviousTurn(setup.startSpeed);
        ship.setSpeedTwoTurnsAgo(setup.startSpeed);
        applyFighterComplement(ship, year);
        applyYearUpgrades(ship, faction, year, shipSpec);
        applyEsgCapacitors(ship, year);
        applyFusionHolding(ship, year);
        applyUimAvailability(ship, year);
        applyWeaponStatus(ship, setup.weaponStatus);
        return ship;
    }

    /**
     * J4.4: re-seat each bay's declared fighter complement for the SCENARIO's year.
     * <p>
     * A ship is built from its own file, which knows only its service year, so a Kzinti CVS
     * arrives flying the AAS it entered service with in Y170. This is where it re-equips: HAAS
     * from Y173, TAAS from Y177, TADS from Y180, TADSC from Y183. A bay whose contents were
     * listed literally has no complement and is left exactly as the file wrote it.
     * <p>
     * Before {@code applyWeaponStatus}, which arms whatever is aboard — arming the outgoing
     * fighters and then swapping them would throw the work away.
     */
    private static void applyFighterComplement(Ship ship, int year) {
        // The work moved to FighterComplement.reseat: the fleet resolver and the ship-catalogue
        // endpoint need the same re-seating, and while this was private to the scenario loader
        // they both quietly sold carriers at their hull's service year.
        for (String n : com.sfb.objects.FighterComplement.reseat(ship, year))
            note(ship, n);
    }

    /**
     * Fit ESG capacitors by scenario year (G23.24). The Lyrans fielded them Y167–169;
     * a ship in a Y167+ battle carries them regardless of when its hull was introduced
     * (pre-capacitor ships cost 1 BPV less, G23.245). The rare G17.5 "repaired without a
     * capacitor" case is not modelled.
     */
    static void applyEsgCapacitors(Ship ship, int year) {
        boolean hasCapacitors = year >= 167;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.ESG) {
                ((com.sfb.weapons.ESG) w).setHasCapacitor(hasCapacitors);
            }
        }
    }

    /**
     * E7.5: fit the fusion holding system by scenario year. The Hydrans developed it in Y168 and
     * "had installed it on virtually all fusion-armed ships by the time the Hydrans entered the
     * General War in Y169", and "there is no cost for this refit".
     * <p>
     * Universal rather than faction-keyed, like the ADD and plasma-rack upgrades in
     * {@code applyYearUpgrades} and for the same reason: the rule is about the WEAPON, so anyone
     * who ever mounts a fusion gets it on the same date. Its own method rather than a branch
     * inside {@code applyYearUpgrades}, because that one returns early below Y175.
     * <p>
     * Set in BOTH directions, exactly as {@code applyEsgCapacitors} does, so the loader is the
     * single authority and a Y134 hull cannot inherit the modern default. Before this existed
     * the four Y134 Hydran hulls - Hunter, Lancer, Ranger and Small Q-Ship - could hold their
     * fusions in a pre-refit scenario, which E7.23 forbids outright.
     */
    static void applyFusionHolding(Ship ship, int year) {
        boolean hasHolding = year >= 168;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.Fusion fusion) {
                fusion.setHoldingSystem(hasHolding);
            }
        }
    }

    /** BPV a single UIM is worth (D6.5), read off the "u" refit variants. */
    private static final int UIM_BPV = 5;

    /**
     * D6.56: the year the UIM became available, <b>which is per empire</b>.
     *
     * <p>The prose reads "The UIM became available (to the Klingons) about Y165", and the
     * INSTALLATION list dates each adopter separately: Klingon "See (R3.R3); available Y165 and
     * later", Lyran "See (R11.R4); available <b>Y166</b> and later. Also see (R14.R2) for
     * availability to the <b>LDR in Y170</b>."
     *
     * <p>One constant of 165 handed the Lyran CC a UIM in a Y165 battle, where its own SSD says "No
     * UIM prior to Y166. Reduce BPV -5" — the owner's reading of the SSD is what turned this up. It
     * was exactly one hull in exactly one year, because the CC is the only Lyran UIM hull that can
     * be fielded that early (the CC+ enters service in Y166 and the BCH in Y180), but it was five
     * BPV and a free shift on the range 16-22 band.
     *
     * <p>D6.5 also names the WYN as an early adopter without dating them, and we have no WYN hulls;
     * a faction not listed here falls back to the Klingon year, and if one ever declares a UIM its
     * R-section should be read rather than this default trusted.
     */
    private static final java.util.Map<Faction, Integer> UIM_YEAR_BY_EMPIRE = java.util.Map.of(
            Faction.Klingon, 165,
            Faction.Lyran, 166);

    /** D6.5's general case, and the fallback for an empire D6.56 does not date. */
    private static final int UIM_YEAR = 165;

    /** The year {@code ship}'s empire could first fit a UIM (D6.56). */
    private static int uimYearFor(Ship ship) {
        Faction faction = ship.getFaction();
        return faction == null ? UIM_YEAR : UIM_YEAR_BY_EMPIRE.getOrDefault(faction, UIM_YEAR);
    }

    /**
     * D6.5: strip the UIM from a ship in a scenario earlier than Y165, and refund its BPV.
     * <p>
     * "The UIM became available (to the Klingons) about Y165"; the rule restates it as "available
     * Y165 and later", and YD6.5 adds that it does not exist in the Early Years (Y80-Y120) at all.
     * <p>
     * Two Klingon hulls predate it and still carry one, which is correct data rather than an error:
     * the <b>D7N (Y137)</b> and <b>D7C (Y143)</b> are long-lived hulls whose SSDs print the UIM,
     * because they have one in any scenario from Y165 on. The owner read the D7N's sheet
     * (2026-10-04): it "was shown with 1x UIM in the SSD, but there's a note stating that prior to
     * Y165 it had no UIM and the BPV is 5 less".
     * <p>
     * So the module is a property of the YEAR, not of the hull, exactly like E7.5 fusion holding
     * in {@code applyFusionHolding} above - and like that one, the ship file declares the modern
     * state and the loader takes it away. The five points come straight from the refit variants,
     * where fitting a UIM costs exactly that: D6K 126 / D6Ku 131, D7B 128 / D7Bu 133, D7K 131 /
     * D7Ku 136, D7D 148 / D7Du 153. Removing it has to refund the same, or an early D7N is priced
     * for equipment it is not carrying.
     * <p>
     * Charged per module. Only one-UIM hulls are exercised today - the two-UIM C8 and C9 are Y167
     * and later, so they never reach this - and if a multi-UIM pre-Y165 hull ever appears the SSD
     * should be checked rather than this assumption trusted.
     * <p>
     * DERFACS needs no equivalent: YE3.6 bars it from the Early Years too, but the earliest hull
     * declaring it is the Kzinti TGT at Y130 and the Early Years end at Y120 (Y0.0).
     */
    static void applyUimAvailability(Ship ship, int year) {
        if (year <= 0 || year >= uimYearFor(ship))
            return;
        int removed = ship.removeUims();
        if (removed > 0)
            ship.setBattlePointValue(ship.getBattlePointValue() - removed * UIM_BPV);
    }

    /**
     * Build all terrain objects defined in the scenario.
     * Each entry in spec.terrain becomes one Terrain hex on the map.
     */
    public static List<Terrain> loadTerrain(ScenarioSpec spec) {
        expandTerrainPlans(spec);
        List<Terrain> result = new ArrayList<>();
        if (spec.terrain == null) return result;
        for (ScenarioSpec.TerrainSetup setup : spec.terrain) {
            TerrainType type;
            try {
                type = TerrainType.valueOf(setup.type.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("ScenarioLoader: unknown terrain type '" + setup.type + "' — skipped");
                continue;
            }
            Location loc = parseHex(setup.hex);
            int radius = setup.radius;
            if (radius > 0 && type != TerrainType.GAS_GIANT) {
                System.err.println("ScenarioLoader: radius only applies to GAS_GIANT — ignored for "
                        + setup.type + " at " + setup.hex + " (class-M fills exactly one hex, P2.211)");
                radius = 0;
            }
            Terrain t = new Terrain(type, loc.getX(), loc.getY(), radius);
            t.setName(setup.name != null ? setup.name : setup.type + "-" + setup.hex);
            t.setTokenArt(setup.tokenArt);
            if (setup.rings != null && !setup.rings.isEmpty()) {
                if (type != TerrainType.GAS_GIANT) {
                    System.err.println("ScenarioLoader: rings only apply to GAS_GIANT — ignored for "
                            + setup.type + " at " + setup.hex);
                } else {
                    List<int[]> bands = new ArrayList<>();
                    for (ScenarioSpec.RingBand rb : setup.rings) {
                        int inner = Math.min(rb.inner, rb.outer);
                        int outer = Math.max(rb.inner, rb.outer);
                        if (inner <= radius)
                            System.err.println("ScenarioLoader: ring band inner=" + rb.inner
                                    + " overlaps the giant body (radius " + radius + ") at " + setup.hex
                                    + " — body hexes stay no-entry, ring hexes only where inner > radius");
                        bands.add(new int[] { inner, outer });
                    }
                    t.setRingBands(bands);
                }
            }
            result.add(t);
        }
        return result;
    }

    /**
     * Turn any terrain plans into real hexes, once, writing them into the spec itself.
     * <p>
     * Into the spec rather than straight into Terrain objects, because the lobby broadcasts the
     * spec and the deployment screen has to show a player what they are setting up around — a
     * field that only existed inside a loaded Game would be invisible until the battle began.
     * <p>
     * Ships that already have a starting hex keep it clear: a scenario that places a cruiser at
     * 1216 should not drop an asteroid on top of it.
     */
    public static void expandTerrainPlans(ScenarioSpec spec) {
        if (spec.terrainPlan == null || spec.terrainPlan.isEmpty())
            return;

        List<MapRegion> keepClear = new ArrayList<>();
        if (spec.sides != null)
            for (ScenarioSpec.SideSpec side : spec.sides)
                if (side.ships != null)
                    for (ScenarioSpec.ShipSetup ship : side.ships)
                        if (ship.startHex != null && !ship.startHex.isBlank())
                            keepClear.add(MapRegion.circle(ship.startHex, 0));

        List<ScenarioSpec.TerrainSetup> generated = TerrainGenerator.generate(
                spec.terrainPlan, keepClear, spec.mapCols, spec.mapRows);

        if (spec.terrain == null)
            spec.terrain = new ArrayList<>();
        spec.terrain.addAll(generated);
        // Spent: the hexes are in spec.terrain now, and running again would double them.
        spec.terrainPlan = null;
    }

    public static List<com.sfb.objects.Objective> loadObjectives(ScenarioSpec spec) {
        List<com.sfb.objects.Objective> result = new ArrayList<>();
        if (spec.objectives == null)
            return result;
        for (ScenarioSpec.ObjectiveSetup setup : spec.objectives) {
            Location loc = parseHex(setup.hex);
            com.sfb.objects.Objective obj = new com.sfb.objects.Objective(
                    setup.name != null ? setup.name : "Objective-" + setup.hex, loc.getX(), loc.getY());
            obj.setSurvivesCarrierDestruction(setup.survivesDestruction);
            obj.setSide(setup.side);
            obj.setPoints(setup.points);
            List<String> methods = setup.retrieval != null && !setup.retrieval.isEmpty()
                    ? setup.retrieval
                    : List.of("TRANSPORTER"); // sensible default
            for (String m : methods) {
                try {
                    obj.getAllowedRetrieval().add(
                            com.sfb.properties.RetrievalMethod.valueOf(m.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    System.err.println("ScenarioLoader: unknown retrieval method '" + m
                            + "' on objective " + setup.name + " — skipped");
                }
            }
            result.add(obj);
        }
        return result;
    }

    /**
     * Parse SFB CCRR hex notation to a Location(column, row).
     * "0515" → Location(5, 15).
     */
    static Location parseHex(String hex) {
        int col = Integer.parseInt(hex.substring(0, 2));
        int row = Integer.parseInt(hex.substring(2, 4));
        return new Location(col, row);
    }

    /**
     * Map SFB heading letter to internal 24-step facing.
     * A=1, B=5, C=9, D=13, E=17, F=21.
     */
    static int parseHeading(String heading) {
        char ch = heading.toUpperCase().charAt(0);
        return (ch - 'A') * 4 + 1;
    }

    /**
     * Apply Commander's Option Items to a ship.
     *
     * Call this after buildShip() and before the game starts. Each item is
     * validated (within budget, within limits); violations are logged and
     * that item is skipped rather than throwing.
     *
     * @param ship    the fully constructed ship
     * @param loadout the COI selections for this ship
     * @param spec    the scenario spec (used to read commanderOptions and year)
     */
    public static void applyCoi(Ship ship, CoiLoadout loadout, ScenarioSpec spec) {
        if (loadout == null) return;

        // J4.621: which fighter model a casual carrier's facilities serve. Done FIRST and
        // separately from the budget below, because it costs nothing - the drones and chaff come
        // with the ship ("enough... to re-arm those fighters three times") and only special
        // drones and speed upgrades are bought.
        //
        // A re-fit rather than a first fitting: the facilities were already built with the ship,
        // from the line's standard fighter, because nothing at construction time knows what the
        // player will choose. An unavailable or absent choice falls back to that same standard
        // fighter, which refitFacilities validates.
        if (loadout.fighterFacilityType != null && !loadout.fighterFacilityType.isBlank()) {
            ship.setFighterFacilityType(loadout.fighterFacilityType);
            com.sfb.objects.FighterComplement.refitFacilities(ship, spec.year);
        }

        int budgetPercent = spec.commanderOptions != null
                ? spec.commanderOptions.budgetPercent
                : CoiBudget.DEFAULT_PERCENT;
        // S3.211's basis, which includes the fighters the hull BPV leaves out. The endpoint now
        // refuses an over-budget loadout outright, so the per-item checks below are the last line
        // of defence for a loadout written into a scenario file, which never passes through it.
        double budget = CoiBudget.allowanceFor(ship, budgetPercent);
        double spent  = 0;

        // --- Extra boarding parties ---
        int extraBPs = Math.min(loadout.extraBoardingParties, CoiLoadout.MAX_EXTRA_BP);
        double bpCost = extraBPs * CoiLoadout.COST_EXTRA_BP;
        if (spent + bpCost <= budget) {
            ship.getCrew().getFriendlyTroops().normal += extraBPs;
            spent += bpCost;
        } else {
            note(ship, "COI: skipping " + extraBPs + " extra BPs — over budget");
        }

        // --- Convert normal BPs to commandos ---
        int conversions = Math.min(loadout.convertBpToCommando, CoiLoadout.MAX_CONVERT_TO_COMMANDO);
        conversions = Math.min(conversions, ship.getCrew().getFriendlyTroops().normal);
        double convCost = conversions * CoiLoadout.COST_CONVERT_TO_COMMANDO;
        if (spent + convCost <= budget) {
            ship.getCrew().getFriendlyTroops().normal    -= conversions;
            ship.getCrew().getFriendlyTroops().commandos += conversions;
            spent += convCost;
        } else {
            note(ship, "COI: skipping " + conversions + " BP→commando conversions — over budget");
        }

        // --- Which fighters are ready (S4.10-S4.12) ---
        // Before the crews are hired, because it costs nothing and cannot fail: it only
        // re-arranges what the weapon status already granted.
        applyFighterReadiness(ship, loadout.armedFighters);

        // --- Extra deck crews (S3.2/J4.816) ---
        // Only a fully capable carrier may hire them. A casual carrier, or a ship with no
        // fighters at all, has nowhere to put them — so the request is refused rather than
        // charged for, and the player is told while there is still time to spend it elsewhere.
        int extraCrews = Math.min(loadout.extraDeckCrews, CoiLoadout.MAX_EXTRA_DECK_CREWS);
        if (extraCrews > 0 && !ship.getCarrierClass().isCarrier()) {
            note(ship, "COI: only a fully capable carrier may hire deck crews (J4.816)");
        } else if (extraCrews > 0) {
            double crewCost = extraCrews * CoiLoadout.COST_EXTRA_DECK_CREW;
            if (spent + crewCost <= budget) {
                ship.getCrew().addDeckCrews(extraCrews);
                spent += crewCost;
            } else {
                note(ship, "COI: skipping " + extraCrews + " extra deck crews — over budget");
            }
        }

        // --- Extra commando squads ---
        int extraCommandos = Math.min(loadout.extraCommandoSquads, CoiLoadout.MAX_EXTRA_COMMANDOS);
        double cmdCost = extraCommandos * CoiLoadout.COST_EXTRA_COMMANDO;
        if (spent + cmdCost <= budget) {
            ship.getCrew().getFriendlyTroops().commandos += extraCommandos;
            spent += cmdCost;
        } else {
            note(ship, "COI: skipping " + extraCommandos + " extra commando squads — over budget");
        }

        // --- T-bombs (4 BPV each; each purchased T-bomb includes 1 free dummy) ---
        boolean allowTBombs = spec.commanderOptions == null || spec.commanderOptions.allowTBombs;
        if (allowTBombs && loadout.extraTBombs > 0) {
            int maxTBombs = com.sfb.constants.Constants.MAX_TBOMBS[ship.getSizeClass()];
            int requested = Math.min(loadout.extraTBombs, maxTBombs);
            if (requested < loadout.extraTBombs) {
                note(ship, "COI: capping T-bombs at " + maxTBombs
                        + " for size class " + ship.getSizeClass());
            }
            double tbCost = requested * CoiLoadout.COST_TBOMB;
            if (spent + tbCost <= budget) {
                ship.setTBombs(ship.getTBombs() + requested);
                ship.setDummyTBombs(ship.getDummyTBombs() + requested); // 1 free dummy per purchased
                spent += tbCost;
            } else {
                note(ship, "COI: skipping " + requested + " T-bombs — over budget");
            }
        }

        // --- Drone rack loadouts (free; year/speed limits enforced) ---
        if (!loadout.droneRackLoadouts.isEmpty() || !loadout.antiDroneLoadouts.isEmpty()) {
            Integer maxSpeed = spec.commanderOptions != null ? spec.commanderOptions.maxDroneSpeed : null;
            int year = spec.year;

            List<DroneRack> racks = new ArrayList<>();
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (w instanceof DroneRack) racks.add((DroneRack) w);
            }

            // Every rack either side of the two maps mentions, so a rack given only
            // anti-drones is still visited.
            java.util.Set<Integer> rackIndexes = new java.util.LinkedHashSet<>();
            rackIndexes.addAll(loadout.droneRackLoadouts.keySet());
            rackIndexes.addAll(loadout.antiDroneLoadouts.keySet());

            for (Integer rackIndexBoxed : rackIndexes) {
                int rackIndex = rackIndexBoxed;
                if (rackIndex < 0 || rackIndex >= racks.size()) {
                    note(ship, "COI: drone rack index " + rackIndex + " out of range — skipped");
                    continue;
                }
                DroneRack rack = racks.get(rackIndex);
                List<DroneType> requestedTypes =
                        loadout.droneRackLoadouts.getOrDefault(rackIndex, java.util.List.of());
                int antiDrones = loadout.antiDroneLoadouts.getOrDefault(rackIndex, 0);

                if (antiDrones > 0 && !rack.acceptsAntiDrones()) {
                    note(ship, "COI: rack " + rackIndex + " is a " + rack.getRackType()
                            + " and carries no anti-drones (FD3.70) — anti-drones dropped");
                    antiDrones = 0;
                }
                if (antiDrones > 0 && year > 0 && year < DroneRack.ANTI_DRONE_FIRST_YEAR) {
                    note(ship, "COI: anti-drones are not available before Y"
                            + DroneRack.ANTI_DRONE_FIRST_YEAR + " (FD3.72) — rack "
                            + rackIndex + " anti-drones dropped");
                    antiDrones = 0;
                }

                // Validate each type against year, speed cap, and rack capability
                List<Drone> drones = new ArrayList<>();
                double totalRackSize = 0;
                boolean valid = true;
                for (DroneType dt : requestedTypes) {
                    if (!dt.availableIn(year)) {
                        note(ship, "COI: " + dt + " not available in year " + year
                                + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    if (maxSpeed != null && dt.speed > maxSpeed) {
                        note(ship, "COI: " + dt + " speed " + dt.speed
                                + " exceeds cap " + maxSpeed + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    if (!rack.accepts(dt)) {
                        note(ship, "COI: " + dt + " cannot be loaded in rack type "
                                + rack.getRackType() + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    totalRackSize += dt.rack;
                }
                if (!valid) continue;

                // Every rack reached here was named in one map or the other, which means
                // the player built a loadout for it — so what they built is what it
                // carries, drones and anti-drones alike. A rack they never touched is not
                // in either map and keeps the ammunition it arrived with.
                //
                // FD3.70: the two share one magazine, so they are budgeted together rather
                // than each against the whole rack.
                double totalWithAntiDrones =
                        totalRackSize + antiDrones * DroneRack.ANTI_DRONE_SPACE;
                if (totalWithAntiDrones > rack.getSpaces()) {
                    note(ship, "COI: loadout for rack " + rackIndex + " exceeds rack size ("
                            + totalWithAntiDrones + " > " + rack.getSpaces() + ") — skipped");
                    continue;
                }
                for (DroneType dt : requestedTypes) drones.add(new Drone(dt));
                rack.setAmmo(drones);
                rack.setAddAmmo(0);
                if (antiDrones > 0)
                    rack.loadAntiDrones(antiDrones, year);
            }
        }

        // --- Special shuttle conversions (WS-2: max 1, WS-3: max 2) ---
        // Admin shuttles are converted in-bay to the requested special type.
        if (!loadout.specialShuttlePrep.isEmpty()) {
            int ws = spec.sides.stream()
                    .flatMap(side -> side.ships.stream())
                    .filter(ss -> ship.getName().equals(ss.shipName))
                    .mapToInt(ss -> ss.weaponStatus)
                    .findFirst().orElse(0);
            // S4.12 (Weapon Status II): one shuttle may be prepared for a special role
            // (scatter pack, suicide shuttle, wild weasel). S4.13 (Weapon Status III): two.
            // Below WS-II, none — there has been no time to prepare anything.
            int maxPrep = ws >= 3 ? 2 : ws == 2 ? 1 : 0;

            int applied = 0;
            for (CoiLoadout.SpecialShuttlePrep prep : loadout.specialShuttlePrep) {
                if (applied >= maxPrep) {
                    note(ship, "COI: special shuttle limit (" + maxPrep + ") reached — skipping "
                            + prep.shuttleName);
                    continue;
                }
                // Find the admin shuttle in the ship's bays
                ShuttleBay foundBay = null;
                Shuttle foundShuttle = null;
                for (ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(prep.shuttleName)) {
                            foundBay    = bay;
                            foundShuttle = s;
                            break;
                        }
                    }
                    if (foundBay != null) break;
                }
                if (foundBay == null || foundShuttle == null) {
                    note(ship, "COI: shuttle not found: " + prep.shuttleName + " — skipped");
                    continue;
                }
                if ("suicide".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeSuicide()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a suicide shuttle — skipped");
                        continue;
                    }
                    SuicideShuttle ss = new SuicideShuttle(foundShuttle);
                    int energy = Math.max(1, Math.min(3, prep.energyPerTurn));
                    for (int t = 0; t < 3; t++) ss.arm(energy);
                    foundBay.replaceShuttle(foundShuttle, ss);
                    applied++;

                } else if ("scatterpack".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeScatterPack()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a scatterpack — skipped");
                        continue;
                    }
                    ScatterPack sp = new ScatterPack(foundShuttle);
                    for (DroneType dt : prep.drones) {
                        // Pull one drone of this type from any rack's ammo, then reloads
                        if (!pullDroneFromRacks(ship, dt)) {
                            note(ship, "COI: no " + dt + " available in racks for scatterpack "
                                    + prep.shuttleName + " — drone skipped");
                            continue;
                        }
                        if (!sp.addDrone(new Drone(dt))) {
                            note(ship, "COI: scatterpack " + prep.shuttleName
                                    + " payload full — remaining drones skipped");
                            break;
                        }
                    }
                    if (sp.getPayload().isEmpty()) {
                        // Leave it a plain shuttle. An empty pack can never launch (the
                        // launch action wants a payload) and, being prepared, can no longer
                        // launch as an ordinary shuttle either — dead weight all battle.
                        note(ship, "COI: scatterpack " + prep.shuttleName
                                + " got no drones from the racks — left as a plain shuttle");
                        continue;
                    }
                    foundBay.replaceShuttle(foundShuttle, sp);
                    applied++;

                } else if ("wildweasel".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeWildWeasel()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a Wild Weasel — skipped");
                        continue;
                    }
                    // No AdminShuttle check: the canBecomeWildWeasel() test just above is
                    // the rule (J3.18), and this used to refuse a GAS or HTS that had
                    // already passed it.
                    // Charge to full (2 turns) so it's ready to launch on turn 1
                    foundShuttle.incrementWwCharge();
                    foundShuttle.incrementWwCharge();
                    applied++;

                } else {
                    note(ship, "COI: unknown conversion type '" + prep.type
                            + "' for shuttle " + prep.shuttleName + " — skipped");
                }
            }
        }

        // --- Heavy weapon arming mode overrides (WS-3 only; weapon must already be armed) ---
        if (!loadout.weaponArmingModes.isEmpty()) {
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof HeavyWeapon)) continue;
                if (w instanceof com.sfb.weapons.Fusion) continue;
                if (w instanceof com.sfb.weapons.PlasmaLauncher
                        && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;

                com.sfb.properties.WeaponArmingType mode =
                        loadout.weaponArmingModes.get(w.getDesignator());
                if (mode == null || mode == com.sfb.properties.WeaponArmingType.STANDARD) continue;

                HeavyWeapon hw = (HeavyWeapon) w;
                if (!hw.isArmed()) {
                    // S4.32: the arming turns a ship gets for free before the scenario cannot
                    // include overload energy. A partly armed photon may still be fused for
                    // proximity, which costs nothing (E4.31).
                    if (w instanceof com.sfb.weapons.Photon && hw.getArmingTurn() > 0
                            && mode == com.sfb.properties.WeaponArmingType.SPECIAL) {
                        ((com.sfb.weapons.Photon) w).setSpecial();
                    } else if (mode == com.sfb.properties.WeaponArmingType.OVERLOAD) {
                        note(ship, "COI: weapon " + w.getLabel()
                                + " cannot start overloaded — prior-turn arming carries no"
                                + " overload energy (S4.32)");
                    } else {
                        note(ship, "COI: weapon " + w.getLabel()
                                + " is not armed — arming mode override skipped");
                    }
                    continue;
                }
                // Photons take their overload from the S4.32 pool below, not from a mode flag.
                if (w instanceof com.sfb.weapons.Photon
                        && mode == com.sfb.properties.WeaponArmingType.OVERLOAD) {
                    note(ship, "COI: photon " + w.getLabel()
                            + " — set its free overload energy in photonOverload (S4.32)");
                    continue;
                }

                // Reset and re-arm in the requested mode
                hw.reset();
                switch (mode) {
                    case OVERLOAD: hw.setOverload(); break;
                    case SPECIAL:  hw.setSpecial();  break;
                    case ROLLING:
                        if (hw instanceof com.sfb.weapons.PlasmaLauncher)
                            ((com.sfb.weapons.PlasmaLauncher) hw).setRollingMode();
                        break;
                    default: break;
                }
                hw.setArmed(true);
                hw.setArmingTurn(hw.totalArmingTurns());
                if (w instanceof com.sfb.weapons.Photon) {
                    ((com.sfb.weapons.Photon) w).setArmingEnergy(
                        (double) hw.energyToArm() * hw.totalArmingTurns());
                }
            }
        }

        // --- Free photon overload energy at WS-III (S4.32) ---
        // Two points per tube, poolable across the ship's tubes: a Federation CA with four
        // photons has eight points, enough to take two tubes to a full 100% overload and
        // leave two standard, or to give every tube half an overload. The energy can only
        // overload, never arm, and taking any commits that tube (E4.414) — which caps it at
        // range 8 and doubles its holding cost (E4.413).
        if (!loadout.photonOverload.isEmpty()) {
            int ws = spec.sides.stream()
                    .flatMap(side -> side.ships.stream())
                    .filter(ss -> ship.getName().equals(ss.shipName))
                    .mapToInt(ss -> ss.weaponStatus)
                    .findFirst().orElse(0);
            List<com.sfb.weapons.Photon> tubes = new ArrayList<>();
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
                if (w instanceof com.sfb.weapons.Photon)
                    tubes.add((com.sfb.weapons.Photon) w);

            if (ws < 3) {
                note(ship, "COI: free photon overload energy is a WS-3 allowance only"
                        + " — ignored for " + ship.getName() + " at WS-" + ws + " (S4.32)");
            } else if (tubes.isEmpty()) {
                note(ship, "COI: " + ship.getName() + " has no photon tubes — free"
                        + " overload energy ignored (S4.32)");
            } else {
                double pool = CoiLoadout.FREE_OVERLOAD_PER_TUBE * tubes.size();
                double spentPool = 0;
                for (com.sfb.weapons.Photon p : tubes) {
                    Double want = loadout.photonOverload.get(p.getDesignator());
                    if (want == null || want <= 0)
                        continue;
                    double amount = Math.floor(want * 2) / 2.0;          // half points (E4.414)
                    if (amount > com.sfb.weapons.Photon.MAX_OVERLOAD) {  // 100% and no more (E4.41)
                        note(ship, "COI: photon " + p.getDesignator() + " capped at "
                                + com.sfb.weapons.Photon.MAX_OVERLOAD + " overload points (E4.41)");
                        amount = com.sfb.weapons.Photon.MAX_OVERLOAD;
                    }
                    if (spentPool + amount > pool) {
                        amount = pool - spentPool;
                        note(ship, "COI: " + ship.getName() + " has only " + pool
                                + " free overload points — photon " + p.getDesignator()
                                + " reduced to " + amount + " (S4.32)");
                    }
                    if (amount <= 0)
                        continue;
                    p.setOverload();
                    p.setArmingEnergy(p.getArmingEnergy() + amount);
                    spentPool += amount;
                }
            }
        }

        // --- Orion option-mount weapons (G15.4) ---
        // The ship's inherent loadout, not a commander's-option budget item: the
        // BPV delta flows into effective BPV, not the COI budget. Illegal picks
        // (wrong position/size/year, unavailable) are skipped with a reason.
        if (!loadout.optionMounts.isEmpty()) {
            com.sfb.objects.OptionMountCatalog catalog = com.sfb.objects.OptionMountCatalog.loadDefault();
            for (Map.Entry<String, String> entry : loadout.optionMounts.entrySet()) {
                try {
                    com.sfb.objects.OptionMountLoadout.equip(ship, catalog, entry.getKey(), entry.getValue(), spec.year);
                } catch (IllegalArgumentException e) {
                    note(ship, "COI: option mount " + entry.getKey() + " ("
                            + entry.getValue() + ") — " + e.getMessage());
                }
            }
        }

        // Record the COI spend so the enemy is awarded these points at scenario end
        // (S2.20 step B). Option-mount weapon costs are excluded — they ride the ship's
        // GABPV and are already scored under step C.
        ship.setCoiSpend(spent);
    }

    /**
     * Remove one drone of the given type from the ship's rack ammo or reloads.
     * Searches ammo lists first, then reload sets across all racks.
     * Returns true if a drone was found and removed, false if none available.
     */
    /**
     * Tell the player, not just the console. A COI selection that cannot be applied used to
     * print to System.err and stop there, so a setup silently came out different from what
     * was chosen and the first sign of it was a missing option mid-battle.
     */
    private static void note(Ship ship, String message) {
        System.err.println(message);
        ship.addSetupNote(message);
    }

    private static boolean pullDroneFromRacks(Ship ship, DroneType type) {
        List<DroneRack> racks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack) racks.add((DroneRack) w);
        }
        // Check the ship's reload stockpile first — preserve ammo so the racks are ready to fire on
        // Turn 1. One pile for the ship, per FD2.422, so this no longer has to walk the racks: it
        // used to, and a drone sitting in rack 2's set was only findable after rack 1's was empty,
        // which is the same confusion the stockpile exists to end.
        if (!ship.reloadStockpile().take(type, 1).isEmpty())
            return true;
        // Fall back to ammo only if no matching drone exists in the stockpile
        for (DroneRack rack : racks) {
            Iterator<Drone> it = rack.getAmmo().iterator();
            while (it.hasNext()) {
                if (it.next().getDroneType() == type) { it.remove(); return true; }
            }
        }
        return false;
    }

    /**
     * Apply Y175 universal and faction-specific ship upgrades.
     * Universal: all ADD_6 → ADD_12.
     * Faction defaults apply unless shipSpec.y175Upgrades is non-null (explicit override).
     */
    static void applyYearUpgrades(Ship ship, String faction, int year, ShipSpec shipSpec) {
        if (year < 175) return;

        // Universal: ADD_6 → ADD_12
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof ADD) {
                ADD add = (ADD) w;
                if (add.getAddType() == ADD.AddType.ADD_6) {
                    add.upgradeTo(ADD.AddType.ADD_12);
                }
            }
        }

        // FP10.312: "Along with the Y175 drone rack refits, each plasma rack has two sets of
        // reloads; there is no extra cost for this." Universal like the ADD upgrade above and
        // for the same reason - the rule is about the weapon, not the navy that mounts it - so
        // it sits before the per-ship override and the faction defaults rather than inside
        // them. A Gorn, ISC, Romulan or Orion rack (FP10.15) all get it.
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.PlasmaRack pr)
                pr.applyY175Refit();
        }

        // Explicit per-ship override list takes precedence over faction defaults
        if (shipSpec.y175Upgrades != null) {
            if (shipSpec.y175Upgrades.refitCost != 0) {
                ship.setBattlePointValue(ship.getBattlePointValue() + shipSpec.y175Upgrades.refitCost);
            }
            for (ShipSpec.Y175RackUpgrade ru : shipSpec.y175Upgrades.racks) {
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof DroneRack)) continue;
                    if (!ru.designator.equals(w.getDesignator())) continue;
                    DroneRack rack = (DroneRack) w;
                    rack.upgradeRackType(DroneRack.DroneRackType.valueOf(ru.upgradeTo));
                    if (ru.extraReloads > 0) rack.addReloadSets(ru.extraReloads);
                }
            }
            for (ShipSpec.Y175AddUpgrade au : shipSpec.y175Upgrades.adds) {
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof ADD)) continue;
                    if (!au.designator.equals(w.getDesignator())) continue;
                    ((ADD) w).upgradeTo(ADD.AddType.valueOf(au.upgradeTo));
                }
            }
            return;
        }

        // Faction defaults
        List<DroneRack> typeARacks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack) {
                DroneRack rack = (DroneRack) w;
                if (rack.getRackType() == DroneRack.DroneRackType.TYPE_A) {
                    typeARacks.add(rack);
                }
            }
        }

        switch (faction.toUpperCase()) {
            case "FEDERATION":
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (w instanceof DroneRack) {
                        DroneRack rack = (DroneRack) w;
                        if (rack.getRackType() == DroneRack.DroneRackType.TYPE_G) {
                            rack.addReloadSets(1);
                        }
                    }
                }
                break;

            case "KLINGON":
                for (DroneRack rack : typeARacks) {
                    rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                }
                break;

            case "KZINTI":
                if (typeARacks.size() >= 4) {
                    // First two → TYPE_C, remainder → TYPE_B
                    for (int i = 0; i < typeARacks.size(); i++) {
                        if (i < 2) typeARacks.get(i).upgradeRackType(DroneRack.DroneRackType.TYPE_C);
                        else       typeARacks.get(i).upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                    }
                } else {
                    for (DroneRack rack : typeARacks) {
                        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                    }
                }
                break;

            default:
                break;
        }
    }

    /**
     * Apply weapon status initial conditions to a ship (S4.10–S4.13).
     */
    /**
     * FP10.25: how many of each plasma rack's torpedoes are active at this weapon status.
     * <p>
     * "Status 0: torpedoes inactive. Status 1: torpedoes inactive. Status II: one torpedo per
     * rack is active. Status III: all torpedoes on racks are active."
     * <p>
     * Called on every branch including 0 and 1, where it happens to set nothing: a rack arrives
     * full of torpedoes (J4.886) but with none ACTIVATED, so the constructor already agrees with
     * the rule at those statuses. The call is there so the ladder is stated in one place and is
     * total, rather than relying on a default matching a rule it does not cite - but it is not
     * load-bearing today, and a mutation test removing it correctly fails nothing.
     */
    private static void applyPlasmaRackStatus(Ship ship, int weaponStatus) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.PlasmaRack rack)
                rack.applyWeaponStatus(weaponStatus);
    }

    static void applyWeaponStatus(Ship ship, int weaponStatus) {
        ship.setWeaponStatus(weaponStatus);
        switch (weaponStatus) {
            case 0:
                // WS-0: phasers not energized — caps cannot hold energy yet
                ship.setCapacitorsCharged(false);
                applyPlasmaRackStatus(ship, weaponStatus);
                break;
            case 1:
                // WS-1: phasers energized, caps empty, fire control active
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
                applyPlasmaRackStatus(ship, weaponStatus);
                break;
            case 2:
            case 3:
                // WS-2/3: caps fully charged, fire control active
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
                double capSize = ship.getWeapons().getAvailablePhaserCapacitor();
                if (capSize > 0) {
                    try {
                        ship.chargeCapacitor(capSize);
                    } catch (CapacitorException e) {
                        // Already full — safe to ignore
                    }
                }
                // ESG generators start charged by weapon status (G23.23): WS-2 = 2, WS-3 = 5.
                int esgInitial = weaponStatus == 3 ? 5 : 2;
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (w instanceof com.sfb.weapons.ESG) {
                        ((com.sfb.weapons.ESG) w).setStoredEnergy(esgInitial);
                    }
                }
                // FP10.25: a plasma rack's torpedoes are ACTIVATED by weapon status, not armed -
                // "Status II: one torpedo per rack is active. Status III: all torpedoes on racks
                // are active." So this is not the all-but-final-arming-turn treatment the heavy
                // weapons get below; the rack has no arming schedule to be partway through.
                applyPlasmaRackStatus(ship, weaponStatus);
                // WS-2: all-but-final arming turn completed (S4.12).
                // armingTurn = totalArmingTurns - 1 for all eligible heavy weapons.
                // Excludes Disruptors (always ready) and Fusion beams (not multi-turn).
                if (weaponStatus == 2) {
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (!(w instanceof HeavyWeapon)) continue;
                        if (w instanceof com.sfb.weapons.Fusion) continue;
                        if (w instanceof com.sfb.weapons.Disruptor) continue;
                        if (w instanceof com.sfb.weapons.PlasmaLauncher
                                && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;
                        HeavyWeapon hw = (HeavyWeapon) w;
                        hw.setArmingTurn(hw.totalArmingTurns() - 1);
                        // A completed arming turn means its energy is in the tube (E4.21), and
                        // for photons that stored energy is what the warhead is made of. The
                        // pre-game turns are plain standard charges: nothing in S4.12 lets a
                        // ship start with overload energy already committed (E4.411).
                        if (w instanceof com.sfb.weapons.Photon) {
                            ((com.sfb.weapons.Photon) w).setArmingEnergy(
                                com.sfb.weapons.Photon.STANDARD_PER_TURN * (hw.totalArmingTurns() - 1));
                        }
                    }
                }
                // WS-3: all heavy weapons start fully armed (S4.13).
                // Excludes Plasma-R (cannot hold/launch as seeker from armed state).
                // Excludes Disruptors: single-turn arming, reset every turn — pre-arming is
                // meaningless since they re-arm in turn 1 EA anyway.
                // Fusion beams are single-turn arming but still start armed at WS-3.
                // Hellbores start armed but cannot hold — rolling delay applies in turn 1 EA.
                if (weaponStatus == 3) {
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (!(w instanceof HeavyWeapon)) continue;
                        if (w instanceof com.sfb.weapons.Disruptor) continue;
                        if (w instanceof com.sfb.weapons.PlasmaLauncher
                                && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;
                        HeavyWeapon hw = (HeavyWeapon) w;
                        hw.setArmed(true);
                        hw.setArmingTurn(hw.totalArmingTurns());
                        if (w instanceof com.sfb.weapons.Photon) {
                            ((com.sfb.weapons.Photon) w).setArmingEnergy(
                                (double) hw.energyToArm() * hw.totalArmingTurns());
                        }
                        if (hw instanceof com.sfb.weapons.PlasmaLauncher)
                            ((com.sfb.weapons.PlasmaLauncher) hw).setArmedState();
                    }
                }
                break;
            default:
                System.err.println("ScenarioLoader: unknown weaponStatus " + weaponStatus + " — treating as WS-2");
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
        }

        applyFighterWeaponStatus(ship, weaponStatus);
    }

    /**
     * Weapon status for the fighters in the bays (S4.10-S4.13, J4.8224).
     *
     * Fighters are built empty and their boxes full, which is J4.8223's resting state for a
     * carrier: racks loaded, fighters not. This decides how much of that has been moved onto
     * the fighters before the scenario starts, and every charge or drone that moves comes out
     * of that fighter's own box — so a carrier's total ammunition is the same at every weapon
     * status, only its readiness differs.
     *
     * <ul>
     *   <li>WS-0 and WS-1: two fighters armed (identical for fighter operations).</li>
     *   <li>WS-2: two turns of work by every deck crew (S4.12), which is 2 actions each, and
     *       J4.8172 caps any one fighter at 2 crews — so 4 actions is the most one can
     *       receive. A Stinger-1 wants 2 and an AAS 2, so today's fighters are never short;
     *       an advanced fighter with a heavier load would be only partly armed, which is the
     *       point of spending a budget rather than setting a flag.</li>
     *   <li>WS-3: everything loaded, racks and capacitors drawn down to pay for it
     *       (J4.8224).</li>
     * </ul>
     *
     * Which fighters get the work is first-come unless the player said otherwise in the
     * Commander's Options — see {@link #applyFighterReadiness}. On a squadron of one type it
     * makes no difference; on a mixed one it is a real decision, and the Hydran Ranger refit
     * carries Stinger-2s and Stinger-Hs in the same bay.
     */
    static void applyFighterWeaponStatus(Ship ship, int weaponStatus) {
        // S4.1 extends these provisions to "fully capable carriers (J4.61) and most Hydran
        // ships (J4.623)" — which is what CAPABLE means — and pointedly not to a casual
        // carrier with a fighter or two aboard (J4.62).
        if (!ship.getCarrierClass().isCarrier())
            return;

        java.util.List<com.sfb.systemgroups.ShuttleSpace> boxes = new java.util.ArrayList<>();
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces())
                if (!box.isDestroyed()
                        && box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter)
                    boxes.add(box);
        if (boxes.isEmpty())
            return;

        if (weaponStatus >= 3) {
            for (com.sfb.systemgroups.ShuttleSpace box : boxes)
                box.armOccupantFully();
            return;
        }

        if (weaponStatus == 2) {
            // S4.12: two turns of deck crew work before the scenario opens.
            int halfActions = ship.getCrew().getDeckCrews() * WS2_TURNS
                    * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;
            int perFighterCap = WS2_TURNS * MAX_CREWS_PER_FIGHTER
                    * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;
            for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
                if (halfActions <= 0)
                    break;
                com.sfb.systemgroups.FighterArming.Load load =
                        com.sfb.systemgroups.FighterArming.load(box, box.getShuttle(),
                                Math.min(halfActions, perFighterCap));
                halfActions -= load.halfActionsUsed();
            }
            return;
        }

        // WS-0 and WS-1: a couple of fighters ready on the deck, the rest cold.
        //
        // A fighter with nothing to arm does not use up one of the two. S4.10 grants two
        // fighters READY, and a Stinger-E with one Ph-G and two permanent EW pods (R1.F7) is
        // already ready — arming it is a no-op. Spending a slot on it left a Hydran Ranger
        // refit with ONE hot fighter instead of two, because the Stinger-E is the first
        // fighter its bay lists.
        int armed = 0;
        for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
            if (armed >= WS01_ARMED_FIGHTERS)
                break;
            if (!com.sfb.systemgroups.FighterArming.needsArming(box.getShuttle()))
                continue;
            box.armOccupantFully();
            armed++;
        }
    }

    /** S4.12: the carrier has had two turns of deck crew activity before the scenario. */
    private static final int WS2_TURNS = 2;

    /** J4.8172: two deck crews on one fighter, and no more. */
    private static final int MAX_CREWS_PER_FIGHTER = 2;

    /** S4.10/S4.11: two fighters may be armed and ready at the lowest weapon statuses. */
    private static final int WS01_ARMED_FIGHTERS = 2;

    /**
     * Re-do the fighter readiness around the player's choice (Commander's Options).
     * <p>
     * Runs after the weapon status has already armed whichever fighters came first, because
     * the options are chosen later — so it strips the squadron back and arms the named ones
     * instead. Stripping is not a loss: J4.886's charges go back into the box they came from,
     * which is the same accounting every other route uses.
     * <p>
     * Named fighters beyond what the status allows are ignored rather than refused; the
     * server holds the list to the allowance before it ever gets here.
     */
    static void applyFighterReadiness(Ship ship, java.util.List<String> armedFighters) {
        if (armedFighters == null || armedFighters.isEmpty())
            return;
        int ws = ship.getWeaponStatus();
        if (ws >= 3)
            return;   // S4.13 arms everything; there is nothing to choose between

        java.util.List<com.sfb.systemgroups.ShuttleSpace> boxes = new java.util.ArrayList<>();
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces())
                if (!box.isDestroyed()
                        && box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter)
                    boxes.add(box);
        if (boxes.isEmpty())
            return;

        for (com.sfb.systemgroups.ShuttleSpace box : boxes)
            com.sfb.systemgroups.FighterArming.disarm(box, box.getShuttle());

        int allowed = ws == 2
                ? boxes.size()   // WS-2 is bounded by the crews' budget, not by a count
                : WS01_ARMED_FIGHTERS;
        int halfActions = ws == 2
                ? ship.getCrew().getDeckCrews() * WS2_TURNS
                        * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION
                : Integer.MAX_VALUE;
        int perFighterCap = WS2_TURNS * MAX_CREWS_PER_FIGHTER
                * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;

        int armed = 0;
        for (String name : armedFighters) {
            if (armed >= allowed)
                break;
            for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
                if (!box.getShuttle().getName().equalsIgnoreCase(name))
                    continue;
                if (ws == 2) {
                    if (halfActions <= 0)
                        break;
                    com.sfb.systemgroups.FighterArming.Load load =
                            com.sfb.systemgroups.FighterArming.load(box, box.getShuttle(),
                                    Math.min(halfActions, perFighterCap));
                    halfActions -= load.halfActionsUsed();
                } else {
                    // Naming a fighter that has nothing to arm spends none of the allowance
                    // (S4.10) — the same reasoning as the automatic pass above.
                    if (!com.sfb.systemgroups.FighterArming.needsArming(box.getShuttle()))
                        break;
                    box.armOccupantFully();
                }
                armed++;
                break;
            }
        }
    }
}
