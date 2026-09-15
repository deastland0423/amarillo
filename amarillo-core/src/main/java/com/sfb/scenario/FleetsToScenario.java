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

    /** One fleet and the team flying it. Several fleets may share a team (allies). */
    public record Entry(FleetSpec fleet, String team) {
    }

    /** The conditions the host agreed before anyone chose a fleet (S8.13, S8.135). */
    public record Conditions(int year, int budget, int mapCols, int mapRows, int weaponStatus) {
        public static Conditions defaults(int year, int budget) {
            return new Conditions(year, budget, 42, 32, 2);
        }
    }

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
