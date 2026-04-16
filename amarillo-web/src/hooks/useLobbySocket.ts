import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface LobbyPlayer {
  name: string;
  isHost: boolean;
  assignedShips: string[];
}

export interface LobbyState {
  gameId: string;
  started: boolean;
  players: LobbyPlayer[];
  unassignedShips: string[];
}

/**
 * Subscribes to the lobby WebSocket topic and returns live lobby state.
 * Starts with null until the first message arrives.
 */
export function useLobbySocket(gameId: string): LobbyState | null {
  const [state, setState] = useState<LobbyState | null>(null);

  useEffect(() => {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${import.meta.env.VITE_WS_URL ?? ''}/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
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
