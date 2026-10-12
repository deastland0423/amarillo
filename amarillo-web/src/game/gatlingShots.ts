/**
 * How many shots a weapon has left while drafting a volley, and which weapons are therefore
 * used up.
 *
 * <h2>Why this is its own file</h2>
 * Almost every weapon fires once and the pad could treat "named in an order" as "gone". A
 * GATLING cannot be treated that way. E2.151: "A gatling phaser can be fired up to four times
 * during a single turn. Each firing may be during the same or a different impulse and at the same
 * or a different target. <b>Gatling phasers can fire at different targets in the same
 * impulse.</b>"
 *
 * <p>The pad used to map a weapon to the first target it was promised to and grey it out for
 * every other one, which made the second half of a gatling's allowance unreachable. Reported in
 * play 2026-10-11: a Stinger-2 engaging drones at range 2 put one shot on the first drone and
 * could not touch the second. The engine was never the obstacle — {@code PhaserG} sets
 * {@code minImpulseGap(0)} and {@code maxShotsPerTurn(4)}, and the server replays each sealed
 * order as its own fire action — so the rule was implemented everywhere except the one layer a
 * player can reach. Hence a pure function with tests rather than arithmetic inside a component.
 */

/** Just the weapon fields this arithmetic needs, so it can be tested without a whole DTO. */
export interface ShotAllowance {
  name:            string;
  /** Shots a turn, for a weapon that repeats; 1 for everything else. */
  maxShotsPerTurn: number;
  /** Impulses that must pass between firings. Non-zero means it cannot fire twice in one. */
  minImpulseGap:   number;
  /**
   * Shots the weapon may still fire right now, with every rate cap already applied by core.
   *
   * This replaced a `maxShotsPerTurn - shotsThisTurn` subtraction done here, which silently
   * stopped being the answer when E2.151's quarter-turn window arrived: four shots fired on
   * impulses 29-32 leave none for impulse 1 of the next turn, though the per-turn count has
   * just been zeroed. The view cannot see a rolling window, and should not be the place that
   * tries to.
   */
  shotsAvailableNow: number;
}

/** Just the order fields this arithmetic needs. */
export interface DraftedOrder {
  shipName:    string;
  targetName:  string | null;
  /** One entry PER SHOT: the wire format repeats a name rather than carrying a count. */
  weaponNames: string[];
  hexCol?:     number;
  hexRow?:     number;
}

/**
 * Whether a weapon may be spread across more than one target in a single impulse.
 *
 * <p>A gap of zero and an allowance above one is exactly a gatling: anything with a gap cannot
 * fire twice in one impulse however large its per-turn allowance, so it is spent the moment it is
 * promised. The ADD sets a gap of 1 for precisely this reason.
 */
export function canSplitShots(w: ShotAllowance): boolean {
  return w.minImpulseGap === 0 && w.maxShotsPerTurn > 1;
}

/**
 * Shots this weapon can still be given, counting both what it has fired and what the drafted
 * orders have already promised.
 *
 * <p>Drafted shots must count, or the pad would offer a gatling a fifth shot and the server would
 * refuse the volley at the reveal — after the orders were sealed, which is the worst moment to
 * find out.
 */
export function shotsRemaining(w: ShotAllowance, draftedShots: number): number {
  // A weapon that cannot split still offers one order's worth, and only if it can fire at all.
  const allowance = canSplitShots(w) ? w.shotsAvailableNow : Math.min(1, w.shotsAvailableNow);
  return allowance - draftedShots;
}

/**
 * Per weapon, how many shots this attacker's drafted orders have already spent, and the first
 * target each was promised to.
 */
export function tallyDraftedShots(
  orders: DraftedOrder[],
  attackerName: string | null,
): { spent: Map<string, number>; promisedTo: Map<string, string> } {
  const spent      = new Map<string, number>();
  const promisedTo = new Map<string, string>();
  for (const o of orders) {
    if (o.shipName !== attackerName) continue;
    for (const name of o.weaponNames) {
      spent.set(name, (spent.get(name) ?? 0) + 1);
      if (!promisedTo.has(name))
        promisedTo.set(name, o.targetName ?? `hex ${o.hexCol}|${o.hexRow}`);
    }
  }
  return { spent, promisedTo };
}

/**
 * Weapons that are promised elsewhere AND have nothing left, mapped to where they went.
 *
 * <p>A weapon the attacker does not have is treated as spent: an order naming something not in
 * this list is not a weapon this pad can offer again.
 */
export function weaponsSpokenFor(
  orders: DraftedOrder[],
  attackerName: string | null,
  weapons: ShotAllowance[],
): Map<string, string> {
  const { spent, promisedTo } = tallyDraftedShots(orders, attackerName);
  const byName = new Map(weapons.map(w => [w.name, w]));

  const map = new Map<string, string>();
  for (const [name, where] of promisedTo) {
    const w = byName.get(name);
    if (!w || shotsRemaining(w, spent.get(name) ?? 0) <= 0)
      map.set(name, where);
  }
  return map;
}
