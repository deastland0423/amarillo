import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { gameApi } from '../api/gameApi';

export interface LobbyPlayer {
  name: string;
  teamName: string | null;
  isHost: boolean;
  assignedShips: string[];
  coiDone: boolean;
}

export interface LobbyState {
  gameId: string;
  scenarioLoaded: boolean;
  scenarioId: string | null;
  started: boolean;
  allCoiReady: boolean;
  players: LobbyPlayer[];
  unassignedShips: string[];
}

/**
 * Subscribes to the lobby WebSocket topic and returns live lobby state.
 * Also fetches an initial snapshot via REST on mount so state is never null.
 */
export function useLobbySocket(gameId: string): LobbyState | null {
  const [state, setState] = useState<LobbyState | null>(null);

  useEffect(() => {
    // Fetch initial snapshot so we don't depend on catching the join broadcast.
    gameApi.getLobbyState(gameId)
      .then(s => setState(s as LobbyState))
      .catch(console.error);

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        // Re-fetch on connect in case broadcasts were missed during reconnection.
        gameApi.getLobbyState(gameId)
          .then(s => setState(s as LobbyState))
          .catch(console.error);

        client.subscribe(`/topic/games/${gameId}/lobby`, (message) => {
          setState(JSON.parse(message.body) as LobbyState);
        });
      },
    });

    client.activate();
    return () => { client.deactivate(); };
  }, [gameId]);

  return state;
}
