package com.sfb.scenario;

import com.sfb.exceptions.CapacitorException;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Location;

import java.util.ArrayList;
import java.util.List;

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
                Ship ship = buildShip(side.faction, setup);
                if (ship != null) ships.add(ship);
            }
            result.add(ships);
        }
        return result;
    }

    private static Ship buildShip(String faction, ScenarioSpec.ShipSetup setup) {
        ShipSpec spec = ShipLibrary.get(faction, setup.hull);
        if (spec == null) {
            System.err.println("ScenarioLoader: no spec found for "
                    + faction + "/" + setup.hull + " — ship skipped");
            return null;
        }
        Ship ship = ShipLibrary.createShip(spec);
        ship.setName(setup.shipName);
        ship.setLocation(parseHex(setup.startHex));
        ship.setFacing(parseHeading(setup.startHeading));
        ship.setSpeed(setup.startSpeed);
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
     * Apply weapon status initial conditions to a ship (S4.10–S4.13).
     */
    static void applyWeaponStatus(Ship ship, int weaponStatus) {
        switch (weaponStatus) {
            case 0:
                // WS-0: phasers not energized — caps cannot hold energy yet
                ship.setCapacitorsCharged(false);
                break;
            case 1:
                // WS-1: phasers energized, caps empty
                ship.setCapacitorsCharged(true);
                break;
            case 2:
            case 3:
                // WS-2/3: caps fully charged
                ship.setCapacitorsCharged(true);
                double capSize = ship.getWeapons().getAvailablePhaserCapacitor();
                if (capSize > 0) {
                    try {
                        ship.chargeCapacitor(capSize);
                    } catch (CapacitorException e) {
                        // Already full — safe to ignore
                    }
                }
                break;
            default:
                System.err.println("ScenarioLoader: unknown weaponStatus " + weaponStatus + " — treating as WS-2");
                ship.setCapacitorsCharged(true);
        }
    }
}
