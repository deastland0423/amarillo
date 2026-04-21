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
        public final boolean      isHost;
        public final List<String> assignedShips;
        public final boolean      coiDone;

        PlayerDto(String name, boolean isHost, List<String> ships, boolean coiDone) {
            this.name          = name;
            this.isHost        = isHost;
            this.assignedShips = ships;
            this.coiDone       = coiDone;
        }
    }

    public final String          gameId;
    public final boolean         scenarioLoaded;
    public final String          scenarioId;
    public final boolean         started;
    public final boolean         allCoiReady;
    public final List<PlayerDto> players;
    public final List<String>    unassignedShips;

    public LobbyStateDto(GameSession session) {
        this.gameId          = session.getId();
        this.scenarioLoaded  = session.isScenarioLoaded();
        this.scenarioId      = session.getLoadedScenarioId();
        this.started         = session.isStarted();
        this.allCoiReady     = session.allCoiDone();

        this.players = session.getPlayers().entrySet().stream()
                .map(e -> new PlayerDto(
                        e.getValue().getName(),
                        session.isHost(e.getKey()),
                        session.getAssignedShipsFor(e.getKey()),
                        session.isCoiDone(e.getKey())
                ))
                .collect(Collectors.toList());

        this.unassignedShips = session.getUnassignedShipNames();
    }
}
