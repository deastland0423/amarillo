package com.sfb.scenario;

import com.sfb.exceptions.CapacitorException;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Location;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;

import java.util.ArrayList;
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
        for (ScenarioSpec.SideSpec side : spec.sides) {
            List<Ship> ships = new ArrayList<>();
            for (ScenarioSpec.ShipSetup setup : side.ships) {
                Ship ship = buildShip(side.faction, setup, spec.year);
                if (ship != null) ships.add(ship);
            }
            result.add(ships);
        }
        return result;
    }

    private static Ship buildShip(String faction, ScenarioSpec.ShipSetup setup, int year) {
        ShipSpec shipSpec = ShipLibrary.get(faction, setup.hull);
        if (shipSpec == null) {
            System.err.println("ScenarioLoader: no spec found for "
                    + faction + "/" + setup.hull + " — ship skipped");
            return null;
        }
        Ship ship = ShipLibrary.createShip(shipSpec);
        ship.setName(setup.shipName);
        ship.setLocation(parseHex(setup.startHex));
        ship.setFacing(parseHeading(setup.startHeading));
        ship.setSpeed(setup.startSpeed);
        // Seed C2.2 speed history — assume ship has been at startSpeed for at least 2 turns
        ship.setSpeedPreviousTurn(setup.startSpeed);
        ship.setSpeedTwoTurnsAgo(setup.startSpeed);
        applyYearUpgrades(ship, faction, year, shipSpec);
        applyWeaponStatus(ship, setup.weaponStatus);
        return ship;
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

        int budgetPercent = spec.commanderOptions != null ? spec.commanderOptions.budgetPercent : 20;
        double budget = CoiLoadout.budget(ship.getBattlePointValue(), budgetPercent);
        double spent  = 0;

        // --- Extra boarding parties ---
        int extraBPs = Math.min(loadout.extraBoardingParties, CoiLoadout.MAX_EXTRA_BP);
        double bpCost = extraBPs * CoiLoadout.COST_EXTRA_BP;
        if (spent + bpCost <= budget) {
            ship.getCrew().getFriendlyTroops().normal += extraBPs;
            spent += bpCost;
        } else {
            System.err.println("COI: skipping " + extraBPs + " extra BPs — over budget");
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
            System.err.println("COI: skipping " + conversions + " BP→commando conversions — over budget");
        }

        // --- Extra commando squads ---
        int extraCommandos = Math.min(loadout.extraCommandoSquads, CoiLoadout.MAX_EXTRA_COMMANDOS);
        double cmdCost = extraCommandos * CoiLoadout.COST_EXTRA_COMMANDO;
        if (spent + cmdCost <= budget) {
            ship.getCrew().getFriendlyTroops().commandos += extraCommandos;
            spent += cmdCost;
        } else {
            System.err.println("COI: skipping " + extraCommandos + " extra commando squads — over budget");
        }

        // --- T-bombs (4 BPV each; each purchased T-bomb includes 1 free dummy) ---
        boolean allowTBombs = spec.commanderOptions == null || spec.commanderOptions.allowTBombs;
        if (allowTBombs && loadout.extraTBombs > 0) {
            int maxTBombs = com.sfb.constants.Constants.MAX_TBOMBS[ship.getSizeClass()];
            int requested = Math.min(loadout.extraTBombs, maxTBombs);
            if (requested < loadout.extraTBombs) {
                System.err.println("COI: capping T-bombs at " + maxTBombs
                        + " for size class " + ship.getSizeClass());
            }
            double tbCost = requested * CoiLoadout.COST_TBOMB;
            if (spent + tbCost <= budget) {
                ship.setTBombs(ship.getTBombs() + requested);
                ship.setDummyTBombs(ship.getDummyTBombs() + requested); // 1 free dummy per purchased
                spent += tbCost;
            } else {
                System.err.println("COI: skipping " + requested + " T-bombs — over budget");
            }
        }

        // --- Drone rack loadouts (free; year/speed limits enforced) ---
        if (!loadout.droneRackLoadouts.isEmpty()) {
            Integer maxSpeed = spec.commanderOptions != null ? spec.commanderOptions.maxDroneSpeed : null;
            int year = spec.year;

            List<DroneRack> racks = new ArrayList<>();
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (w instanceof DroneRack) racks.add((DroneRack) w);
            }

            for (Map.Entry<Integer, List<DroneType>> entry : loadout.droneRackLoadouts.entrySet()) {
                int rackIndex = entry.getKey();
                if (rackIndex < 0 || rackIndex >= racks.size()) {
                    System.err.println("COI: drone rack index " + rackIndex + " out of range — skipped");
                    continue;
                }
                DroneRack rack = racks.get(rackIndex);
                List<DroneType> requestedTypes = entry.getValue();

                // Validate each type against year and speed cap; check total rack size
                List<Drone> drones = new ArrayList<>();
                double totalRackSize = 0;
                boolean valid = true;
                for (DroneType dt : requestedTypes) {
                    if (!dt.availableIn(year)) {
                        System.err.println("COI: " + dt + " not available in year " + year
                                + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    if (maxSpeed != null && dt.speed > maxSpeed) {
                        System.err.println("COI: " + dt + " speed " + dt.speed
                                + " exceeds cap " + maxSpeed + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    totalRackSize += dt.rack;
                }
                if (!valid) continue;
                if (totalRackSize > rack.getSpaces()) {
                    System.err.println("COI: loadout for rack " + rackIndex + " exceeds rack size ("
                            + totalRackSize + " > " + rack.getSpaces() + ") — skipped");
                    continue;
                }
                for (DroneType dt : requestedTypes) drones.add(new Drone(dt));
                rack.setAmmo(drones);
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
                    System.err.println("COI: weapon " + w.getName()
                            + " is not armed — arming mode override skipped");
                    continue;
                }

                // Reset and re-arm in the requested mode
                hw.reset();
                switch (mode) {
                    case OVERLOAD: hw.setOverload(); break;
                    case SPECIAL:  hw.setSpecial();  break;
                    default: break;
                }
                hw.setArmed(true);
                hw.setArmingTurn(hw.totalArmingTurns());
            }
        }
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
                    if (ru.extraReloads > 0) rack.addReloads(ru.extraReloads);
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
                            rack.addReloads(1);
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
    static void applyWeaponStatus(Ship ship, int weaponStatus) {
        switch (weaponStatus) {
            case 0:
                // WS-0: phasers not energized — caps cannot hold energy yet
                ship.setCapacitorsCharged(false);
                break;
            case 1:
                // WS-1: phasers energized, caps empty, fire control active
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
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
                    }
                }
                // WS-3: multi-turn arming weapons start fully armed and held (S4.13).
                // Excludes Plasma-R (cannot hold) and Fusion beams (not multi-turn arming).
                if (weaponStatus == 3) {
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (!(w instanceof HeavyWeapon)) continue;
                        if (w instanceof com.sfb.weapons.Fusion) continue;
                        if (w instanceof com.sfb.weapons.PlasmaLauncher
                                && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;
                        HeavyWeapon hw = (HeavyWeapon) w;
                        hw.setArmed(true);
                        hw.setArmingTurn(hw.totalArmingTurns());
                    }
                }
                break;
            default:
                System.err.println("ScenarioLoader: unknown weaponStatus " + weaponStatus + " — treating as WS-2");
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
        }
    }
}
