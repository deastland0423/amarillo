package com.sfb.scenario;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a fleet's ships actually stand when the battle opens.
 * <p>
 * Each player places their own, in secret, within the zone their fleet was given; the map is
 * revealed when everyone is done. This is the part that can be checked — a placement is legal
 * if it is on the map and inside the zone. Ships may share a hex, because the movement rules
 * let them, and deployment has no business being stricter than play.
 */
public final class Deployment {

    /** One ship, set down. Speed 16 is Speed Max, the convention ScenarioSpec already uses. */
    public record Placement(String shipName, String hex, String heading, int speed) {
        public Placement(String shipName, String hex, String heading) {
            this(shipName, hex, heading, 16);
        }
    }

    /** Rows between ships when they are arranged automatically. */
    private static final int SPACING = 2;

    private Deployment() {
    }

    /** As below, with nothing on the map to run into. */
    public static List<String> check(List<Placement> placements, MapRegion zone,
                                     int mapCols, int mapRows) {
        return check(placements, zone, Set.of(), mapCols, mapRows);
    }

    /**
     * What is wrong with these placements, empty if nothing. Reports every fault rather than
     * the first, so a player fixing a setup sees the whole picture at once.
     * <p>
     * Asteroid hexes are not a fault — setting up in a field is ordinary, and a standard field
     * covers a quarter of the map. What is refused is a solid body: a planet or a gas giant is
     * no-entry (P2.224), so a ship cannot begin somewhere it could never have moved to.
     *
     * @param noEntry hexes no ship may occupy, as CCRR strings — see {@link #noEntryHexes}
     */
    public static List<String> check(List<Placement> placements, MapRegion zone,
                                     Set<String> noEntry, int mapCols, int mapRows) {
        List<String> problems = new ArrayList<>();
        MapRegion z = zone != null ? zone : MapRegion.anywhere();
        Set<String> blocked = noEntry != null ? noEntry : Set.of();

        for (Placement p : placements) {
            com.sfb.properties.Location loc = MapRegion.parse(p.hex());
            if (loc == null) {
                problems.add(p.shipName() + " has no hex to stand on");
                continue;
            }
            if (!z.contains(loc, mapCols, mapRows))
                problems.add(p.shipName() + " is at " + p.hex() + ", outside its deployment area ("
                        + z.describe() + ")");
            else if (blocked.contains(p.hex()))
                problems.add(p.shipName() + " cannot stand at " + p.hex() + " — there is a planet there");
            if (p.heading() == null || p.heading().isBlank()
                    || "ABCDEF".indexOf(p.heading().toUpperCase().charAt(0)) < 0)
                problems.add(p.shipName() + " is not facing anywhere");
        }
        return problems;
    }

    /**
     * The hexes a ship may not be placed on: the footprint of every planet and gas giant in the
     * scenario. Mirrors what Game does with the same terrain when the battle starts, so setup
     * and play agree about where the solid ground is.
     * <p>
     * Asteroids and ring hexes are deliberately absent — both are enterable, at a price.
     */
    public static Set<String> noEntryHexes(ScenarioSpec spec) {
        Set<String> out = new LinkedHashSet<>();
        if (spec == null || spec.terrain == null)
            return out;

        for (ScenarioSpec.TerrainSetup t : spec.terrain) {
            if (t.type == null)
                continue;
            String type = t.type.toUpperCase();
            if (!type.equals("PLANET") && !type.equals("GAS_GIANT"))
                continue;
            com.sfb.properties.Location centre = MapRegion.parse(t.hex);
            if (centre == null)
                continue;
            int radius = Math.max(0, t.radius);
            for (int c = 1; c <= spec.mapCols; c++)
                for (int r = 1; r <= spec.mapRows; r++) {
                    com.sfb.properties.Location hex = new com.sfb.properties.Location(c, r);
                    if (com.sfb.utilities.MapUtils.getRange(centre, hex) <= radius)
                        out.add(String.format("%02d%02d", c, r));
                }
        }
        return out;
    }

    /** True if every ship has been set down somewhere legal. */
    public static boolean isComplete(List<String> shipNames, List<Placement> placements,
                                     MapRegion zone, int mapCols, int mapRows) {
        return isComplete(shipNames, placements, zone, Set.of(), mapCols, mapRows);
    }

    public static boolean isComplete(List<String> shipNames, List<Placement> placements,
                                     MapRegion zone, Set<String> noEntry,
                                     int mapCols, int mapRows) {
        Set<String> placed = new LinkedHashSet<>();
        for (Placement p : placements)
            placed.add(p.shipName());
        return placed.containsAll(shipNames)
                && check(placements, zone, noEntry, mapCols, mapRows).isEmpty();
    }

    /**
     * A tidy starting layout inside a zone: a column of ships down the middle of it, facing the
     * centre of the map. Somewhere to begin rather than somewhere to end — a player nudges it.
     * Wraps to the next column when a fleet is taller than its zone.
     */
    public static List<Placement> autoArrange(List<String> shipNames, MapRegion zone,
                                              int mapCols, int mapRows) {
        return autoArrange(shipNames, zone, Set.of(), mapCols, mapRows);
    }

    /**
     * As above, keeping clear of ground no ship may occupy. A zone drawn around a planet
     * contains the planet, so a tidy column down the middle of one will march a ship straight
     * into it — which is exactly what happened to the third ship of a defending patrol.
     */
    public static List<Placement> autoArrange(List<String> shipNames, MapRegion zone,
                                              Set<String> noEntry, int mapCols, int mapRows) {
        List<Placement> out = new ArrayList<>();
        if (shipNames.isEmpty())
            return out;

        MapRegion z = zone != null ? zone : MapRegion.anywhere();
        Set<String> blocked = noEntry != null ? noEntry : Set.of();
        List<int[]> legal = hexesIn(z, mapCols, mapRows);
        legal.removeIf(h -> blocked.contains(hex(h[0], h[1])));
        if (legal.isEmpty())
            return out;

        // Work outward from the middle of the zone so a small fleet sits in the middle of it.
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE, maxRow = Integer.MIN_VALUE;
        for (int[] h : legal) {
            minCol = Math.min(minCol, h[0]); maxCol = Math.max(maxCol, h[0]);
            minRow = Math.min(minRow, h[1]); maxRow = Math.max(maxRow, h[1]);
        }
        int midCol = (minCol + maxCol) / 2;
        int span = (shipNames.size() - 1) * SPACING;
        int startRow = Math.max(minRow, (minRow + maxRow) / 2 - span / 2);

        // Face the middle of the map, so opposing setups start pointed at each other.
        String heading = midCol <= mapCols / 2 ? "C" : "F";

        int col = midCol, row = startRow;
        for (String name : shipNames) {
            int[] spot = nextLegal(z, col, row, mapCols, mapRows, out, blocked);
            if (spot == null) {
                // Zone too small to keep the pattern; fall back to any free legal hex.
                spot = anyUnused(legal, out);
                if (spot == null)
                    break;
            }
            out.add(new Placement(name, hex(spot[0], spot[1]), heading));
            col = spot[0];
            row = spot[1] + SPACING;
        }
        return out;
    }

    /** Walk down the column for a legal, unused hex; step to the next column at the bottom. */
    private static int[] nextLegal(MapRegion z, int col, int row, int mapCols, int mapRows,
                                   List<Placement> taken, Set<String> blocked) {
        for (int c = col; c <= mapCols; c++) {
            for (int r = (c == col ? row : 1); r <= mapRows; r++)
                if (z.contains(c, r, mapCols, mapRows)
                        && !isTaken(taken, c, r)
                        && !blocked.contains(hex(c, r)))
                    return new int[] { c, r };
        }
        return null;
    }

    private static int[] anyUnused(List<int[]> legal, List<Placement> taken) {
        for (int[] h : legal)
            if (!isTaken(taken, h[0], h[1]))
                return h;
        return null;
    }

    private static boolean isTaken(List<Placement> taken, int col, int row) {
        String hex = hex(col, row);
        for (Placement p : taken)
            if (hex.equals(p.hex()))
                return true;
        return false;
    }

    private static List<int[]> hexesIn(MapRegion z, int mapCols, int mapRows) {
        List<int[]> out = new ArrayList<>();
        for (int c = 1; c <= mapCols; c++)
            for (int r = 1; r <= mapRows; r++)
                if (z.contains(c, r, mapCols, mapRows))
                    out.add(new int[] { c, r });
        return out;
    }

    private static String hex(int col, int row) {
        return String.format("%02d%02d", col, row);
    }
}
