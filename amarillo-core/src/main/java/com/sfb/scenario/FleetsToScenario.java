package com.sfb.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns saved fleets into the scenario a game is actually built from.
 * <p>
 * Everything downstream — ship loading, Commander's Options, victory scoring, the lobby's own
 * display — already works from a {@link ScenarioSpec}, so a battle between bought fleets is the
 * same battle as a hand-authored one once this has run. Nothing else needs to know where the
 * ships came from.
 * <p>
 * Placement here is deliberately naive: teams spread across the map facing the middle, ships
 * stacked in a column at Speed Max. It is a starting line, not a deployment — that is a phase
 * of its own, where each player places their own ships in secret and the map is revealed when
 * everyone is done. Until then this is enough to play.
 */
public final class FleetsToScenario {

    /**
     * One fleet, the team flying it, and the ground it sets up on. Several fleets may share a
     * team (allies). A null zone takes the default: a band around the column this fleet would
     * have lined up on anyway.
     */
    public record Entry(FleetSpec fleet, String team, MapRegion zone) {
        public Entry(FleetSpec fleet, String team) {
            this(fleet, team, null);
        }
    }

    /**
     * The conditions the host agreed before anyone chose a fleet (S8.13, S8.135), including
     * what the map has on it — terrain is settled before forces are bought (S8.15).
     */
    public record Conditions(int year, int budget, int mapCols, int mapRows, int weaponStatus,
                             List<TerrainGenerator.Plan> terrain) {
        public Conditions(int year, int budget, int mapCols, int mapRows, int weaponStatus) {
            this(year, budget, mapCols, mapRows, weaponStatus, List.of());
        }

        public static Conditions defaults(int year, int budget) {
            return new Conditions(year, budget, 42, 32, 2, List.of());
        }
    }

    /** Columns either side of a fleet's own that it may spread into by default. */
    private static final int DEFAULT_ZONE_WIDTH = 3;

    /** Hexes kept clear of the map edge, so a starting line is not against the wall. */
    private static final int EDGE_MARGIN = 5;

    /** Rows between ships of one fleet, so a column of them is legible. */
    private static final int SHIP_SPACING = 2;

    private FleetsToScenario() {
    }

    public static ScenarioSpec build(List<Entry> entries, Conditions conditions) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.id = "fleet-battle";
        spec.name = "Fleet battle";
        spec.description = entries.stream()
                .map(e -> e.fleet().name != null && !e.fleet().name.isBlank()
                        ? e.fleet().name : e.team())
                .reduce((a, b) -> a + " vs " + b).orElse("");
        spec.year = conditions.year();
        spec.numPlayers = entries.size();
        spec.mapCols = conditions.mapCols();
        spec.mapRows = conditions.mapRows();
        spec.sides = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++)
            spec.sides.add(sideFor(entries.get(i), i, entries.size(), conditions));

        // Terrain last, and it does not dodge the deployment zones. A standard asteroid field
        // covers a quarter of the map and setting up in one is ordinary (P3.11); a planet or a
        // giant is placed where it lands, and a zone is wide enough to stand clear of it.
        // Deployment refuses the body's own hexes when a ship is put down, which is the check
        // that actually matters.
        if (conditions.terrain() != null && !conditions.terrain().isEmpty()) {
            spec.terrainPlan = new ArrayList<>(conditions.terrain());
            ScenarioLoader.expandTerrainPlans(spec);
        }

        return spec;
    }

    private static ScenarioSpec.SideSpec sideFor(Entry entry, int index, int total,
                                                 Conditions conditions) {
        FleetSpec fleet = entry.fleet();
        ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
        side.faction = fleet.factions.isEmpty() ? null : fleet.factions.get(0);
        side.name = entry.team();
        side.ships = new ArrayList<>();

        int column = columnFor(index, total, conditions.mapCols());
        // Face the middle of the map, so opposing lines start pointed at each other.
        String heading = column <= conditions.mapCols() / 2 ? "C" : "F";
        int firstRow = firstRowFor(fleet.ships.size(), conditions.mapRows());

        side.deploymentZone = entry.zone() != null ? entry.zone()
                : defaultZone(column, conditions.mapCols(), conditions.mapRows());

        for (int s = 0; s < fleet.ships.size(); s++) {
            FleetSpec.ShipEntry ship = fleet.ships.get(s);
            ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
            setup.faction = fleet.factionOf(ship);
            setup.type = ship.type;
            setup.shipName = ship.name;
            setup.startHex = hex(column, clampRow(firstRow + s * SHIP_SPACING, conditions.mapRows()));
            setup.startHeading = heading;
            setup.startSpeed = 16;   // Speed Max, the convention ScenarioSpec already uses
            setup.weaponStatus = conditions.weaponStatus();
            setup.refits = new ArrayList<>();
            side.ships.add(setup);
        }
        return side;
    }

    /**
     * The ground a fleet gets when the host does not draw one: a band of columns around the one
     * it would have lined up on, the full height of the map. Wide enough that a planet landing
     * in it still leaves somewhere to stand, and it generalises to three and four fleets
     * without special-casing any of them.
     */
    private static MapRegion defaultZone(int column, int mapCols, int mapRows) {
        int from = Math.max(1, column - DEFAULT_ZONE_WIDTH);
        int to = Math.min(mapCols, column + DEFAULT_ZONE_WIDTH);
        return MapRegion.box(String.format("%02d%02d", from, 1),
                             String.format("%02d%02d", to, mapRows));
    }

    /** Fleets spread evenly across the map's width, clear of both edges. */
    private static int columnFor(int index, int total, int mapCols) {
        int left = EDGE_MARGIN;
        int right = Math.max(left, mapCols - EDGE_MARGIN);
        if (total <= 1)
            return (left + right) / 2;
        return left + Math.round((float) index * (right - left) / (total - 1));
    }

    /** A column of ships centred on the map, so nobody starts crowded against a corner. */
    private static int firstRowFor(int shipCount, int mapRows) {
        int span = Math.max(0, (shipCount - 1) * SHIP_SPACING);
        return clampRow(mapRows / 2 - span / 2, mapRows);
    }

    private static int clampRow(int row, int mapRows) {
        return Math.max(1, Math.min(mapRows, row));
    }

    /** SFB's CCRR notation: column and row, each zero-padded to two digits. */
    private static String hex(int col, int row) {
        return String.format("%02d%02d", col, row);
    }
}
