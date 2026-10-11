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

    /*
     * What myShips was last known to be, so a broadcast that omits it does not erase it.
     *
     * A plain variable rather than `prev` inside a setState updater. The updater used to read
     * `prev` AND fire a REST re-fetch from inside itself, and an updater must be pure: React
     * double-invokes updaters in development, so that fetch went out twice every time. The same
     * mistake in GameBoard's combat-log flush printed a whole fire declaration twice, identical
     * die rolls and all (2026-10-11).
     */
    let knownShips: string[] | null = null;

    // Fetch initial snapshot immediately
    gameApi
      .getGameState(gameId, playerToken)
      .then(s => {
        if (!active) return;
        knownShips = (s as GameState).myShips ?? knownShips;
        setState(s as GameState);
      })
      .catch(console.error);

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 3000,
      onConnect: () => {
        if (!active) return;  // already cleaned up before this connection completed
        const onState = (message: { body: string }) => {
          if (!active) return;  // discard if this subscription was already cleaned up
          const incoming = JSON.parse(message.body) as GameState;
          knownShips = incoming.myShips ?? knownShips;
          setState({ ...incoming, myShips: knownShips });
          if (knownShips === null) {
            // Ships not assigned yet — re-fetch REST to pick up any new assignment. Out
            // here, not inside the updater, so it goes out once.
            gameApi.getGameState(gameId, playerToken)
              .then(s => {
                if (!active) return;
                knownShips = (s as GameState).myShips ?? knownShips;
                setState(s as GameState);
              })
              .catch(console.error);
          }
        };
        // Shared topic: omniscient broadcasts while ships are unassigned
        // (solo/dev). Per-token topic: this player's REDACTED view once
        // assignments exist — hidden info never reaches this client at all.
        client.subscribe(`/topic/games/${gameId}/state`, onState);
        client.subscribe(`/topic/games/${gameId}/state/${playerToken}`, onState);
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
