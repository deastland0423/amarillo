import type { CoiDroneType, CoiShipData } from '../api/gameApi';

/**
 * The sentences that explain a Commander's Options figure to the player.
 *
 * Its own module for the reason `reloadPool.ts` and `weaponCondition.ts` are: these are pure
 * functions that were living in a component file, which costs that file its fast refresh and
 * leaves them untestable. Both of these state a rule — one of them a rule about a number that is
 * deliberately ABSENT from the figure beside it — and a sentence that quietly stops matching the
 * data is exactly the kind of thing a test should hold still.
 *
 * Neither computes a rules outcome. Every number here was decided on the server; these only
 * spell out the sum it sent.
 */

/**
 * Where a ship's option budget came from, for the tooltip on the figure (S3.211).
 *
 * Worth saying because the basis is not the number beside it on the SSD. The budget is a share
 * of the "Effective Adjusted Combat BPV", which adds the ship's fighters to its hull — so a
 * carrier's budget is well above a share of its printed BPV, and without this the difference
 * looks like a bug.
 *
 * And it names the one figure that is NOT in the basis. S3.211 takes the percentage of "the
 * ship, its refits, and its fighters, but not including the cost of mandatory drone speed
 * upgrades or crew quality adjustments", so a drone ship's speed charge is excluded by rule. A
 * player who can see both numbers on one screen will otherwise try to add them.
 */
export function budgetBasisLabel(ship: CoiShipData): string {
  if (ship.coiBudgetBasis == null || ship.coiBudgetPercent == null) return '';
  const fighters = ship.coiFighterBpv ?? 0;
  const basis = fighters > 0
    ? `${ship.coiBudgetBasis} BPV (hull ${ship.bpv} + fighters ${fighters})`
    : `${ship.coiBudgetBasis} BPV`;
  const drones = ship.droneSpeedBpv ?? 0;
  const excluded = drones > 0
    ? ` (not counting ${drones} BPV of drone speed, S3.211)`
    : '';
  return `Commander's Options: ${ship.coiBudgetPercent}% of ${basis}${excluded} — S3.211`;
}

/**
 * One loaded drone, as a chip reads.
 *
 * <h2>Why the catalogue can come up empty</h2>
 * It did, in a browser: a Y180 scenario capping drone speed at 20 had its racks filled with
 * speed-32 drones, and the picker — which honours the cap — had no row for them. Every chip read
 * "TypeIF (undefineddmg, spd undefined)". The fitting bug is fixed in `ScenarioLoader`, but the
 * rendering should not have invented the word "undefined" either: a name the catalogue does not
 * list is still a name, and showing it bare is both honest and the thing that made the real bug
 * findable at a glance.
 */
export function droneChipLabel(name: string, types: CoiDroneType[]): string {
  const dt = types.find(t => t.name === name);
  return dt ? `${name} (${dt.damage}dmg, spd ${dt.speed})` : name;
}

/**
 * What a rack is carrying before anybody touches it (S2.11).
 *
 * <h2>Why this is worth a line of its own</h2>
 * The drone builder starts EMPTY, and empty means "keep what it came with". That is the right
 * default — the alternative made the common case begin by deleting four chips — but it was an
 * invisible one: the rack said "using scenario defaults" without ever saying what those were.
 *
 * A player who does not want to choose drones still wants to know what they have. And since the
 * loader fits the scenario's year (S2.11), that is now a real answer rather than a constant: a
 * Y180 ship carries type-IFs at speed 32 where a Y166 one carries type-Is at speed 8.
 *
 * <p>Grouped by name, because four identical chips tell a player less than "4 × TypeIF" does.
 * Insertion order is kept, so the list reads in the order the rack holds them.
 */
export function defaultAmmoLabel(ammo: string[], types: CoiDroneType[]): string {
  const counts = new Map<string, number>();
  for (const name of ammo) counts.set(name, (counts.get(name) ?? 0) + 1);
  return [...counts.entries()].map(([name, n]) => {
    // A composed drone (FD10.2) is not in the catalogue, so it has no damage or speed to show.
    // Its name is still the whole truth about it, which is why there is no fallback detail.
    return `${n} × ${droneChipLabel(name, types)}`;
  }).join(', ');
}
