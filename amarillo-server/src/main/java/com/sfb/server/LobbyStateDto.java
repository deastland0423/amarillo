package com.sfb.server;

import com.sfb.objects.Ship;

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

        PlayerDto(String name, boolean isHost, List<String> ships) {
            this.name          = name;
            this.isHost        = isHost;
            this.assignedShips = ships;
        }
    }

    public final String          gameId;
    public final boolean         started;
    public final List<PlayerDto> players;
    public final List<String>    unassignedShips;

    public LobbyStateDto(GameSession session) {
        this.gameId  = session.getId();
        this.started = session.isStarted();

        this.players = session.getPlayers().entrySet().stream()
                .map(e -> new PlayerDto(
                        e.getValue().getName(),
                        session.isHost(e.getKey()),
                        e.getValue().getShipNames()
                ))
                .collect(Collectors.toList());

        if (session.isStarted()) {
            this.unassignedShips = session.getGame().getShips().stream()
                    .filter(s -> s.getOwner() == null)
                    .map(Ship::getName)
                    .collect(Collectors.toList());
        } else {
            this.unassignedShips = List.of();
        }
    }
}
