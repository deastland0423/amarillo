/**
 * Which screen a game belongs on.
 *
 * Lives in its own module rather than in `App.tsx` for two reasons: a component file that also
 * exports functions breaks Fast Refresh, and this is pure logic with no React in it — which is
 * also what makes it testable without rendering anything.
 */

export type Screen = 'lobby' | 'pregame' | 'game' | 'fleet';

/** What a client needs to know about a game to place it. */
export interface GamePlacement {
  started: boolean;
  /** True for a battle restored from a save rather than newly set up. */
  restored?: boolean;
  /** How many ships nobody has claimed. */
  unassignedShips?: number;
}

/**
 * "Started" is not enough on its own, and that is the whole reason this exists.
 *
 * A battle RESTORED from a save is already started, but its ships belong to nobody — a save
 * records which team held each ship, never which person. Assignment lives on the pre-game screen,
 * so routing a restored battle to the map by its `started` flag alone would strand the host with
 * a fleet they cannot give to anybody, and no way back, because the map has no assignment
 * controls.
 *
 * Once every ship is claimed it is an ordinary game and goes to the map, including on a later
 * reload — which is why this reads the live counts rather than remembering a decision.
 */
export function screenFor(status: GamePlacement): Screen {
  if (status.restored && (status.unassignedShips ?? 0) > 0)
    return 'pregame';
  return status.started ? 'game' : 'pregame';
}
