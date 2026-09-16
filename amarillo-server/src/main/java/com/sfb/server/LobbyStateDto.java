package com.sfb.server;


import java.util.List;
import java.util.stream.Collectors;

/**
 * Snapshot of lobby state broadcast to all clients when players join,
 * the game starts, or ships are assigned.
 */
public class LobbyStateDto {

    public static class PlayerDto {
        public final String       name;
        public final String       teamName;
        public final boolean      isHost;
        public final List<String> assignedShips;
        public final boolean      coiDone;
        /**
         * How far along their setup is — a count, never the hexes. Placements stay secret
         * until everyone is finished, and this goes to the whole room.
         */
        public final int          shipsPlaced;
        public final boolean      deploymentDone;

        PlayerDto(String name, String teamName, boolean isHost, List<String> ships, boolean coiDone,
                  int shipsPlaced, boolean deploymentDone) {
            this.name           = name;
            this.teamName       = teamName;
            this.isHost         = isHost;
            this.assignedShips  = ships;
            this.coiDone        = coiDone;
            this.shipsPlaced    = shipsPlaced;
            this.deploymentDone = deploymentDone;
        }
    }

    /** A side's forces, so every client can see what is being sat down to. */
    public static class SideDto {
        public final String        name;
        public final String        faction;
        public final List<ShipDto> ships;
        public final ZoneDto       deploymentZone;

        SideDto(String name, String faction, List<ShipDto> ships, ZoneDto zone) {
            this.name           = name;
            this.faction        = faction;
            this.ships          = ships;
            this.deploymentZone = zone;
        }
    }

    /**
     * The ground a side may set up on, expanded to hexes.
     * <p>
     * Expanded here rather than described and re-derived in the browser: the map already has a
     * hexRange of its own, and two implementations of "which hexes are in this zone" is how the
     * tint comes to disagree with the refusal. The whole map is sent as no hexes at all — there
     * is nothing to mark when the answer is "anywhere".
     */
    public static class ZoneDto {
        public final String       describe;
        public final List<String> hexes;

        ZoneDto(String describe, List<String> hexes) {
            this.describe = describe;
            this.hexes    = hexes;
        }

        static ZoneDto of(com.sfb.scenario.MapRegion zone, int mapCols, int mapRows) {
            if (zone == null)
                return null;
            List<String> hexes = new java.util.ArrayList<>();
            if (zone.shape != com.sfb.scenario.MapRegion.Shape.ANYWHERE) {
                for (int c = 1; c <= mapCols; c++)
                    for (int r = 1; r <= mapRows; r++)
                        if (zone.contains(c, r, mapCols, mapRows))
                            hexes.add(String.format("%02d%02d", c, r));
            }
            return new ZoneDto(zone.describe(), hexes);
        }
    }

    /** A piece of terrain, so a player can see what they are setting up around. */
    public static class TerrainDto {
        public final String  terrainType;
        public final String  hex;
        public final String  name;
        public final int     radius;
        public final int[][] rings;

        TerrainDto(com.sfb.scenario.ScenarioSpec.TerrainSetup t) {
            this.terrainType = t.type != null ? t.type : "";
            this.hex         = t.hex != null ? t.hex : "";
            this.name        = t.name;
            this.radius      = t.radius;
            this.rings = t.rings == null ? new int[0][] : t.rings.stream()
                    .map(b -> new int[] { b.inner, b.outer })
                    .toArray(int[][]::new);
        }
    }

    public static class ShipDto {
        public final String       shipName;
        public final String       type;
        public final String       startHex;
        public final String       startHeading;
        public final int          startSpeed;
        public final int          weaponStatus;
        public final List<String> refits;

        ShipDto(com.sfb.scenario.ScenarioSpec.ShipSetup setup) {
            this.shipName     = setup.shipName != null ? setup.shipName : "";
            this.type         = setup.type != null ? setup.type : "";
            this.startHex     = setup.startHex != null ? setup.startHex : "";
            this.startHeading = setup.startHeading != null ? setup.startHeading : "";
            this.startSpeed   = setup.startSpeed;
            this.weaponStatus = setup.weaponStatus;
            this.refits       = setup.refits != null ? setup.refits : List.of();
        }
    }

    public final String          gameId;
    public final boolean         scenarioLoaded;
    public final String          scenarioId;
    // Scenario identity for EVERY player — joiners have no local scenario list,
    // so the broadcast is their only source for what they are sitting down to
    public final String          scenarioName;
    public final String          scenarioDescription;
    public final int             scenarioYear;
    public final List<String>    scenarioSpecialRules;
    public final boolean         started;
    public final boolean         allCoiReady;
    /** Whether this battle expects players to set their own ships down, and whether they have. */
    public final boolean         deploymentRequired;
    public final boolean         allDeploymentReady;
    public final List<PlayerDto> players;
    public final List<String>    unassignedShips;
    /**
     * The forces, from the loaded spec itself rather than from the scenario listing. A battle
     * assembled from saved fleets is not a file on disk, so matching an id against
     * data/scenarios would show a joiner nothing at all.
     */
    public final List<SideDto>   sides;
    /**
     * What is on the map. Broadcast for the same reason the forces are: a player choosing where
     * to set up has to see the asteroids and the gas giant before they place anything, and the
     * battle has not started yet so there is no game state to read it from.
     */
    public final List<TerrainDto> terrain;
    public final int             mapCols;
    public final int             mapRows;

    public LobbyStateDto(GameSession session) {
        this.gameId          = session.getId();
        this.scenarioLoaded  = session.isScenarioLoaded();
        this.scenarioId      = session.getLoadedScenarioId();
        com.sfb.scenario.ScenarioSpec spec = session.getLoadedSpec();
        this.scenarioName         = spec != null && spec.name != null ? spec.name : null;
        this.scenarioDescription  = spec != null && spec.description != null ? spec.description : null;
        this.scenarioYear         = spec != null ? spec.year : 0;
        this.scenarioSpecialRules = spec != null && spec.specialRules != null
                ? spec.specialRules : List.of();
        this.started         = session.isStarted();
        this.allCoiReady     = session.allCoiDone();
        this.deploymentRequired  = session.isDeploymentRequired();
        this.allDeploymentReady  = session.allDeploymentDone();

        this.players = session.getPlayers().entrySet().stream()
                .map(e -> new PlayerDto(
                        e.getValue().getName(),
                        session.getTeamNameFor(e.getKey()),
                        session.isHost(e.getKey()),
                        session.getAssignedShipsFor(e.getKey()),
                        session.isCoiDone(e.getKey()),
                        session.deploymentFor(e.getKey()).size(),
                        session.isDeploymentDone(e.getKey())
                ))
                .collect(Collectors.toList());

        this.unassignedShips = session.getUnassignedShipNames();

        this.sides = spec == null || spec.sides == null ? List.of() : spec.sides.stream()
                .map(side -> new SideDto(
                        side.name != null ? side.name : side.faction,
                        side.faction,
                        side.ships == null ? List.<ShipDto>of()
                                : side.ships.stream().map(ShipDto::new).collect(Collectors.toList()),
                        ZoneDto.of(side.deploymentZone, spec.mapCols, spec.mapRows)))
                .collect(Collectors.toList());

        this.terrain = spec == null || spec.terrain == null ? List.of() : spec.terrain.stream()
                .map(TerrainDto::new)
                .collect(Collectors.toList());
        this.mapCols = spec != null ? spec.mapCols : 42;
        this.mapRows = spec != null ? spec.mapRows : 32;
    }
}
