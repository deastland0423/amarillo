import type { DroneRackState, ReloadPoolEntry } from '../types/gameState';

/**
 * The reload-pool vocabulary: what a rack can be reloaded with, and who has already claimed it.
 *
 * Its own module for the same reason `weaponCondition.ts` and `armingStatus.ts` are — one place
 * per shared vocabulary, so a term cannot come to mean two things in two panels. The immediate
 * push was smaller: these are pure functions living in a component file, which costs the whole
 * file its fast refresh, and `reloadPool.test.ts` was already named after the module that did not
 * exist yet.
 */

/**
 * Everything a rack can be reloaded WITH, in one list.
 *
 * Two pools meet here, and they are owned at different levels. The DRONES belong to the ship
 * (FD2.422: the stockpile "is not directly associated with any particular rack and can be loaded
 * onto any rack on the ship"), while a type-G's ANTI-DRONE reserve is the rack's own — FD3.72 gives
 * that rack a reload set "entirely anti-drones", and it cannot be loaded anywhere else. FD2.42
 * spends both against the same two spaces a turn, which is why the caller wants them together.
 */
export function reloadEntriesFor(ship: { reloadPool?: ReloadPoolEntry[] },
                                 rack?: Pick<DroneRackState, 'reloadPool'>): ReloadPoolEntry[] {
  return [...(ship.reloadPool ?? []), ...(rack?.reloadPool ?? [])];
}

/** An anti-drone round rides the reload pool under a key that is not a drone type (FD2.42). */
export const ANTI_DRONE_KEY = 'ANTI_DRONE';

/**
 * How many of a stockpile drone the OTHER racks have already asked for this turn.
 *
 * One pile, several claims. Before the stockpile became the ship's, each rack offered its own
 * drones and no two selections could collide; now two racks reaching for the same drone is the
 * ordinary case, and the server's take() would hand them to whichever it processed first and leave
 * the second rack quietly short. Subtracting this is what keeps the dialog from offering the same
 * drone twice.
 *
 * Anti-drones are exempt: each type-G holds its own reserve, and both appear under the same key.
 */
export function claimedByOtherRacks(selections: Record<string, Record<string, number>>,
                                    thisRack: string, droneType: string): number {
  if (droneType === ANTI_DRONE_KEY) return 0;
  return Object.entries(selections)
    .filter(([rackName]) => rackName !== thisRack)
    .reduce((n, [, sel]) => n + (sel[droneType] ?? 0), 0);
}
