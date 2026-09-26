/**
 * Who the movement phase is waiting for.
 *
 * `movableNow` is the whole queue of units still to move this impulse, and the server sorts
 * it globally — slowest first, ties broken by worst turn mode — because a faster ship is
 * entitled to see where the slower ones went before committing. Only its HEAD may move, and
 * whose ship that is decides what each player should be told.
 *
 * The banner used to filter the queue down to the player's own ships and prompt on that, so
 * on an impulse where both fleets had something to move, both players were told to move and
 * the server then refused whichever was not at the head ("Move USS Enterprise first (speed
 * 22)"). Found in a playtest, 2026-09-26.
 */

export type MovementPrompt =
  /** The head of the queue is mine: I am the one holding everyone up. */
  | { kind: 'move'; ship: string }
  /**
   * The head is someone else's. `mineNext` is my own first ship in the queue, if I have
   * one — worth saying, because "waiting" and "the game has forgotten me" look identical
   * otherwise.
   */
  | { kind: 'wait'; ship: string; mineNext: string | null }
  /** Nothing left to move this impulse. */
  | { kind: 'none' };

export function movementPrompt(
  movableNow: readonly string[],
  isMine: (name: string) => boolean,
): MovementPrompt {
  const next = movableNow[0];
  if (next === undefined) return { kind: 'none' };
  if (isMine(next)) return { kind: 'move', ship: next };
  return { kind: 'wait', ship: next, mineNext: movableNow.find(isMine) ?? null };
}
