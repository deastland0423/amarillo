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
    // Guard against React Strict Mode double-invocation: ignore callbacks from
    // the first (discarded) effect run once cleanup has fired.
    let active = true;

    // Fetch initial snapshot immediately
    gameApi
      .getGameState(gameId, playerToken)
      .then(s => { if (active) setState(s as GameState); })
      .catch(console.error);

    const client = new Client({
      webSocketFactory: () => new SockJS(`http://${window.location.hostname}:8080/ws`),
      reconnectDelay: 3000,
      onConnect: () => {
        if (!active) return;  // already cleaned up before this connection completed
        client.subscribe(`/topic/games/${gameId}/state`, (message) => {
          if (!active) return;  // discard if this subscription was already cleaned up
          const incoming = JSON.parse(message.body) as GameState;
          setState(prev => {
            const knownShips = incoming.myShips ?? prev?.myShips ?? null;
            if (knownShips === null) {
              // Ships not assigned yet — re-fetch REST to pick up any new assignment.
              gameApi.getGameState(gameId, playerToken)
                .then(s => { if (active) setState(s as GameState); })
                .catch(console.error);
            }
            return { ...incoming, myShips: knownShips };
          });
        });
      },
    });

    client.activate();
    return () => {
      active = false;
      client.deactivate();
    };
  }, [gameId, playerToken]);

  return state;
}
