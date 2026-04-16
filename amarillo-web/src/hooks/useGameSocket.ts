import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { GameState } from '../types/gameState';
import { gameApi } from '../api/gameApi';

/**
 * Subscribes to the game-state WebSocket topic and returns live game state.
 * Also fetches an initial snapshot via REST on mount.
 * Returns null until the first state arrives.
 */
export function useGameSocket(gameId: string, playerToken: string): GameState | null {
  const [state, setState] = useState<GameState | null>(null);

  useEffect(() => {
    // Fetch initial snapshot immediately
    gameApi
      .getGameState(gameId, playerToken)
      .then(s => setState(s as GameState))
      .catch(console.error);

    const client = new Client({
      webSocketFactory: () => new SockJS(`${import.meta.env.VITE_WS_URL ?? ''}/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/games/${gameId}/state`, (message) => {
          const incoming = JSON.parse(message.body) as GameState;
          // Broadcasts never contain myShips (server can't know who is receiving).
          // Preserve the value from the initial REST fetch.
          setState(prev => ({ ...incoming, myShips: incoming.myShips ?? prev?.myShips ?? null }));
        });
      },
    });

    client.activate();
    return () => { client.deactivate(); };
  }, [gameId, playerToken]);

  return state;
}
