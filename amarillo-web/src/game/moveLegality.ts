/**
 * Why a movement button is unavailable this impulse, mirrored for the move grid.
 *
 * Core owns these rules and refuses an illegal move authoritatively; this exists so the button
 * can be GREYED with the reason attached, the way the HET button already is. The move grid used
 * to offer every direction whenever it was your impulse, so a player could press sideslip on an
 * impulse where it was illegal and watch nothing happen — no refusal on screen, no explanation.
 *
 * Same relationship to the rules as `groundBombardment.ts` and `weaponDamageTables.ts`: a mirror
 * for the player's benefit, never the decision.
 */

export interface MovingShip {
  /** C2.44: one hex of movement since the last sideslip. Absent on an older server. */
  canSideslip?: boolean;
  /** C2.2: hexes still required before this ship may turn. 0 means it may turn now. */
  hexesUntilTurn?: number;
  /** Null for a unit with no turn mode — a base rotates (C3.7) rather than turning. */
  turnMode?: string | null;
}

/**
 * The reason this move is unavailable, or null if it is allowed.
 *
 * Unknown fields mean "no reason to refuse". An older server sends neither flag, and Jackson
 * serialises an absent value as null rather than omitting it, so every check here has to treat
 * missing as permissive — greying a legal button is worse than offering an illegal one, because
 * the engine still refuses the illegal move while a wrongly greyed button leaves the player with
 * no way forward at all.
 */
export function moveRefusal(action: string, ship: MovingShip | null | undefined): string | null {
  if (!ship) return null;

  if (action === 'SIDESLIP_LEFT' || action === 'SIDESLIP_RIGHT') {
    if (ship.canSideslip === false)
      return 'needs one hex of movement since the last sideslip (C2.44)';
    return null;
  }

  if (action === 'TURN_LEFT' || action === 'TURN_RIGHT') {
    // A base has no turn mode and does not turn at all; it rotates on its own rules (C3.7).
    if (ship.turnMode === null)
      return 'this unit rotates rather than turns (C3.7)';
    const needed = ship.hexesUntilTurn ?? 0;
    if (needed > 0)
      return `${needed} more hex${needed === 1 ? '' : 'es'} before it can turn (C2.2)`;
    return null;
  }

  return null;
}
