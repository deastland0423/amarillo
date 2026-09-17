package com.sfb.scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Fills the blanks in a scenario with the fleets people brought.
 * <p>
 * A scenario file already says everything a battle needs — terrain, victory conditions, special
 * rules, the ground each side sets up on. What it could not say until now is "the ships are
 * brought, not listed". A side marked {@code bringYourOwn} is a place for somebody's fleet to
 * stand, and this puts one there.
 * <p>
 * That makes the pick-up battle the least-specified scenario rather than a separate road: no
 * terrain, no victory conditions beyond the standard, two sides on opposite edges. It goes
 * through the same filling as an authored situation does, because it is one.
 * <p>
 * The point of doing it this way is the zones. "Set up within three hexes of the planet you are
 * defending" cannot come from a lobby dropdown — it means something only in a scenario that has
 * a planet and a defender. Written down beside them, it means exactly what it says.
 */
public final class FleetsToScenario {

    /**
     * One fleet and the side it is flying for. Several fleets may name the same side (allies).
     * A zone here overrides whatever the scenario gave that side, which the plain pick-up
     * battle uses and an authored situation generally should not.
     */
    public record Entry(FleetSpec fleet, String team, MapRegion zone) {
        public Entry(FleetSpec fleet, String team) {
            this(fleet, team, null);
        }
    }

    /**
     * What the host settles for this particular battle (S8.13, S8.135). A scenario that cares
     * about its own terrain keeps it; one that names none leaves the choice here.
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

    /** Columns either side of its own that a fleet may spread into, when nothing says otherwise. */
    private static final int DEFAULT_ZONE_WIDTH = 3;

    /** Hexes kept clear of the map edge, so a starting line is not against the wall. */
    private static final int EDGE_MARGIN = 5;

    /** Rows between ships of one fleet, when they are lined up rather than deployed. */
    private static final int SHIP_SPACING = 2;

    private FleetsToScenario() {
    }

    /** The plain pick-up battle: a scenario shaped to fit, then filled. */
    public static ScenarioSpec build(List<Entry> entries, Conditions conditions) {
        return fill(pickupTemplate(entries, conditions), entries, conditions);
    }

    /**
     * A scenario with nothing settled but the shape: one side per fleet, spread across the map,
     * each with ground around the line it would have formed on. What an authored situation
     * looks like when it has nothing to say.
     */
    public static ScenarioSpec pickupTemplate(List<Entry> entries, Conditions conditions) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.id = "fleet-battle";
        spec.name = "Fleet battle";
        spec.year = conditions.year();
        spec.numPlayers = entries.size();
        spec.mapCols = conditions.mapCols();
        spec.mapRows = conditions.mapRows();
        spec.sides = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            ScenarioSpec.SideSpec side = new ScenarioSpec.SideSpec();
            side.name = entries.get(i).team();
            side.bringYourOwn = true;
            side.ships = new ArrayList<>();
            int column = columnFor(i, entries.size(), conditions.mapCols());
            side.deploymentZone = defaultZone(column, conditions.mapCols(), conditions.mapRows());
            spec.sides.add(side);
        }
        return spec;
    }

    /**
     * Put the fleets into the scenario's waiting sides.
     *
     * @param template the scenario, its own terrain and rules intact
     * @param entries  the fleets, each naming the side it flies for
     */
    public static ScenarioSpec fill(ScenarioSpec template, List<Entry> entries,
                                    Conditions conditions) {
        ScenarioSpec spec = template;
        if (conditions.year() > 0)
            spec.year = conditions.year();
        if (spec.mapCols <= 0) spec.mapCols = conditions.mapCols();
        if (spec.mapRows <= 0) spec.mapRows = conditions.mapRows();

        List<Entry> unplaced = new ArrayList<>(entries);
        for (int i = 0; i < spec.sides.size(); i++) {
            ScenarioSpec.SideSpec side = spec.sides.get(i);
            if (!side.bringYourOwn)
                continue;   // an authored side brought its own ships already

            // By name where the host said one, otherwise in the order they were offered.
            Entry entry = takeFor(unplaced, side.name);
            if (entry == null) {
                // Nobody brought a fleet for this side. Empty rather than null, so everything
                // downstream can iterate it without asking.
                if (side.ships == null)
                    side.ships = new ArrayList<>();
                continue;
            }

            if (entry.zone() != null)
                side.deploymentZone = entry.zone();
            if (side.faction == null && !entry.fleet().factions.isEmpty())
                side.faction = entry.fleet().factions.get(0);
            if (side.name == null || side.name.isBlank())
                side.name = entry.team();

            side.ships = shipsFor(entry.fleet(), side, i, spec, conditions);
        }

        spec.numPlayers = Math.max(spec.numPlayers, entries.size());
        if (spec.description == null || spec.description.isBlank())
            spec.description = entries.stream()
                    .map(e -> e.fleet().name != null && !e.fleet().name.isBlank()
                            ? e.fleet().name : e.team())
                    .reduce((a, b) -> a + " vs " + b).orElse("");

        // Terrain the scenario did not name, chosen for this battle instead. A scenario that
        // says where its planet is keeps it — the zones may be measured from it.
        boolean scenarioHasTerrain = spec.terrain != null && !spec.terrain.isEmpty();
        if (!scenarioHasTerrain && conditions.terrain() != null && !conditions.terrain().isEmpty()) {
            spec.terrainPlan = new ArrayList<>(conditions.terrain());
        }
        ScenarioLoader.expandTerrainPlans(spec);

        return spec;
    }

    /** The fleet for this side: the one that named it, else the next one going spare. */
    private static Entry takeFor(List<Entry> unplaced, String sideName) {
        for (int i = 0; i < unplaced.size(); i++)
            if (sideName != null && sideName.equalsIgnoreCase(unplaced.get(i).team()))
                return unplaced.remove(i);
        return unplaced.isEmpty() ? null : unplaced.remove(0);
    }

    /**
     * The ships, standing somewhere sensible to begin with.
     * <p>
     * Inside the side's own ground where it has any, using the same layout a player is offered
     * when they set up by hand — so what they see first is what Auto-arrange would give them.
     * A side with no ground gets a column, which is all the pick-up battle ever had.
     */
    private static List<ScenarioSpec.ShipSetup> shipsFor(
            FleetSpec fleet, ScenarioSpec.SideSpec side, int index,
            ScenarioSpec spec, Conditions conditions) {

        List<String> names = new ArrayList<>();
        for (FleetSpec.ShipEntry ship : fleet.ships)
            names.add(ship.name != null && !ship.name.isBlank() ? ship.name : ship.type);

        List<Deployment.Placement> laidOut = side.deploymentZone != null
                ? Deployment.autoArrange(names, side.deploymentZone,
                                         Deployment.noEntryHexes(spec), spec.mapCols, spec.mapRows)
                : List.of();

        int column = columnFor(index, Math.max(1, spec.sides.size()), spec.mapCols);
        String fallbackHeading = column <= spec.mapCols / 2 ? "C" : "F";
        int firstRow = firstRowFor(fleet.ships.size(), spec.mapRows);

        List<ScenarioSpec.ShipSetup> out = new ArrayList<>();
        for (int s = 0; s < fleet.ships.size(); s++) {
            FleetSpec.ShipEntry ship = fleet.ships.get(s);
            Deployment.Placement spot = s < laidOut.size() ? laidOut.get(s) : null;

            ScenarioSpec.ShipSetup setup = new ScenarioSpec.ShipSetup();
            setup.faction = fleet.factionOf(ship);
            setup.type = ship.type;
            setup.shipName = names.get(s);
            setup.startHex = spot != null ? spot.hex()
                    : hex(column, clampRow(firstRow + s * SHIP_SPACING, spec.mapRows));
            setup.startHeading = spot != null ? spot.heading() : fallbackHeading;
            setup.startSpeed = 16;   // Speed Max, the convention ScenarioSpec already uses
            setup.weaponStatus = conditions.weaponStatus();
            setup.refits = new ArrayList<>();
            out.add(setup);
        }
        return out;
    }

    /**
     * The ground a fleet gets when nothing says otherwise: a band of columns around the one it
     * would have lined up on, the full height of the map. Wide enough that a planet landing in
     * it still leaves somewhere to stand, and it generalises to three and four fleets.
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
