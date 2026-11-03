import type {
  CoiDroneAddition, CoiDroneModule, CoiDroneRack, CoiDroneType, CoiShipData, CoiSubmunitionSpeed,
} from '../api/gameApi';
import { parseDroneName, rackSpacesOf, type DroneComposition } from './droneComposition';

/**
 * What a drone loadout costs against the ship's Commander's Option budget (FD2.22).
 *
 * <h2>Why the view adds this up at all</h2>
 * It was not adding it up, and that was a bug a player could walk into: `coiCost` summed
 * boarding parties, commandos, T-bombs and deck crews, so the "x / y BPV" counter and the
 * over-budget guard both ignored the drones. The server counts them — `CoiBudget.refusalFor`
 * has since the year went in — so a loadout the dialog showed as affordable could be refused on
 * submission with nothing on screen to explain it.
 *
 * <h2>Why it cannot be one number from the server</h2>
 * A player ASSEMBLES drones here (FD10.2): three armor modules on a type-IV, extended range on
 * a type-III, a chosen engine for a multi-warhead drone's submunitions. No catalogue holds the
 * result, so no catalogue figure can be sent for it. What is sent instead is the four terms
 * `DroneSpec.pointCost` is defined as — engine, payload, frame, submunitions — and this adds
 * them to the modules' own costs, which the dialog already had for other reasons.
 *
 * <p>So nothing here decides a rules question. Every number is one core computed, including the
 * year's: `speedCredit` is `DroneSpec.speedCostIn(year)` already resolved, so this never sees a
 * date and cannot get FD10.65's ladder wrong. The one piece of judgement — which figures a grown
 * frame uses — follows `rackSpacesOf`, which has made the same call for rack spaces since
 * payload modules arrived.
 *
 * <h2>An under-filled drone is billed as though it were full (FD10.48)</h2>
 * A player may leave payload space empty — "remove and discard the explosive warhead and launch
 * the drone with no payload at all" — and that discard happens to a drone already bought. So the
 * payload term floors at what the frame shipped with, which is what `dt.payloadCost` carries.
 *
 * <p>This used to price the hole as a discount, so a half-filled type-IV came out 0.75 BELOW the
 * two type-I-slows it displaces, and a clamp on the rack total hid it. Inside the rack that
 * negative still paid for a dearer drone in the next space — one rack space buying another,
 * which is the refund FD2.22 does not offer, wearing a hat. With the floor at source there is
 * nothing to clamp, and `rackSurcharge` is a plain sum like core's `rackUpgradeCost`.
 *
 * <h2>Except in a magazine launcher, where it is not a plain sum</h2>
 * FD2.45 pays "only for a single loading of each launch rack", and for every ordinary rack that
 * costs nothing to obey: the reloads are kept somewhere this never looks. A type-D keeps them
 * INSIDE itself as the spare magazines (FD3.43), so FD3.44 charges one magazine of its three and
 * FD3.84 charges a type-H two of its five. The rack sends `paidLoadingSpaces` and
 * `dearestLoading` below picks which drones fill it — mirroring core's `PaidLoading`, which is
 * where the rule and the reasoning live. Without this the dialog quoted three times the bill
 * `CoiBudget.refusalFor` would check and refused loadouts the server would have taken.
 */

/** One drone's surcharge over the type-I-slows it displaces. Never negative — see the header. */
export function droneSurcharge(
  c:         DroneComposition,
  types:     CoiDroneType[],
  modules:   CoiDroneModule[],
  additions: CoiDroneAddition[],
  subSpeeds: CoiSubmunitionSpeed[],
): number {
  const dt = types.find(t => t.name === c.basis);
  if (!dt || dt.engineCost == null) return 0;   // an older server sent no costs; say nothing

  // FD10.24: a type-III given extended range is priced from the two-space column. The server
  // sends both figures; picking between them is the same choice rackSpacesOf makes.
  const grown = c.additions.includes('EXTENDED_RANGE')
             && dt.engineCostWithExtendedRange != null;
  const engine = grown ? dt.engineCostWithExtendedRange! : dt.engineCost;
  const credit = grown
    ? (dt.speedCreditWithExtendedRange ?? 0)
    : (dt.speedCredit ?? 0);

  // Naming a module REPLACES the payload (FD10.4), which is the same rule the editor already
  // shows: ticking one module on a type-IV leaves the other space empty rather than keeping the
  // explosive that was there. And FD10.48 bills that empty space anyway, so the catalogue's
  // payload is a floor rather than a fallback — which is also what makes naming no module at all
  // come out as an untouched drone, with no branch needed to say so.
  const chosen = c.modules.reduce(
    (sum, n) => sum + (modules.find(m => m.name === n)?.cost ?? 0), 0);
  const payload = Math.max(chosen, dt.payloadCost ?? 0);

  // FD10.53's additions are bought on top. The catalogue's own — a type-III's guidance — are in
  // frameCost and never appear here, because the only non-player-choice addition in the game is
  // that one and the picker cannot tick it.
  const added = c.additions.reduce(
    (sum, n) => sum + (additions.find(a => a.name === n)?.cost ?? 0), 0);

  // FD8.44: every submunition aboard pays for its own engine.
  const subs = c.submunitionSpeed == null ? 0
    : c.modules.reduce((n, name) => n + (modules.find(m => m.name === name)?.submunitions ?? 0), 0)
      * (subSpeeds.find(s => s.name === c.submunitionSpeed)?.surchargePerSubmunition ?? 0);

  const loaded = engine + payload + (dt.frameCost ?? 0) + added + subs;
  return loaded - rackSpacesOf(c, types) * TYPE_I_SLOW_COST_PER_SPACE - credit;
}

/**
 * What the drones a player already owns are worth, per rack space: a type-I-slow costs one point
 * (FD2.11) and occupies one space (FD2.1), so the two are the same number. Core's
 * `CoiLoadout.TYPE_I_SLOW_COST_PER_SPACE`.
 */
export const TYPE_I_SLOW_COST_PER_SPACE = 1.0;

/** Half a space is the smallest a drone comes in (FD3.5's type-VI), so it is the unit. */
const HALF_SPACE = 0.5;

/**
 * The dearest `paidSpaces` worth of these drones — core's `PaidLoading.dearestLoading`.
 *
 * FD2.452 settles which magazine FD3.44 charges for: "the reload drones are presumed to be of
 * the same cost (or lower) as the drones in the rack", so a free reload may be no dearer than
 * what was paid for and the paid magazine has to be the dearest one. Taking the dearest DRONE
 * first is a different answer and a smaller one — one two-space type-IV beats any single
 * dogfight drone, but eight dogfight drones fit the same four spaces — so it is a knapsack, and
 * a small exact one: every size and the capacity itself are multiples of half a space.
 */
function dearestLoading(
  items: { spaces: number; worth: number }[], paidSpaces: number,
): number {
  const capacity = Math.round(paidSpaces / HALF_SPACE);
  if (capacity <= 0) return 0;
  // best[w] = the dearest loading that fits in w half-spaces. Zero-filled, because a loading is
  // never obliged to use the room it has, and a drone worth nothing is simply left out.
  const best = new Array<number>(capacity + 1).fill(0);
  for (const item of items) {
    if (item.worth <= 0) continue;
    const size = Math.round(item.spaces / HALF_SPACE);
    if (size <= 0 || size > capacity) continue;
    for (let w = capacity; w >= size; w--)
      best[w] = Math.max(best[w], best[w - size] + item.worth);
  }
  return best[capacity];
}

/**
 * One rack's cost: the drones' surcharges, plainly summed — unless the rack is one of FD2.45's
 * two magazine launchers, which pay for a single magazine (see the header).
 *
 * @param rack the rack this load is going into, when the caller knows which; without it the
 *             whole load is billed, which is right for every rack but a type-D or type-H
 */
export function rackSurcharge(
  drones: string[], ship: CoiShipData, rack?: CoiDroneRack,
): number {
  if (drones.length === 0) return 0;
  const modules   = ship.availableDroneModules ?? [];
  const additions = ship.availableDroneAdditions ?? [];
  const subSpeeds = ship.availableSubmunitionSpeeds ?? [];
  const priced = drones.map(name => {
    const c = parseDroneName(name, modules, additions);
    return {
      spaces: rackSpacesOf(c, ship.availableDroneTypes),
      worth:  droneSurcharge(c, ship.availableDroneTypes, modules, additions, subSpeeds),
    };
  });
  const whole = priced.reduce((sum, d) => sum + d.worth, 0);

  const paid = rack?.paidLoadingSpaces;
  if (paid == null || paid >= rack!.spaces) return whole;
  return dearestLoading(priced, paid);
}

/**
 * Every rack the player has BUILT, summed.
 *
 * <p>A rack absent from the loadouts keeps what the ship arrived with (S2.11), and the ship was
 * charged for that under S2.11 rather than out of this budget — so it is not counted here, which
 * is also exactly what `applyCoi` does on the other side: it visits only the racks the loadout
 * names.
 */
export function droneLoadoutCost(
  loadouts: Record<number, string[]>,
  ship:     CoiShipData,
): number {
  const racks = ship.droneRacks ?? [];
  return Object.entries(loadouts).reduce((sum, [index, drones]) => sum + rackSurcharge(
    drones, ship, racks.find(r => r.index === Number(index))), 0);
}
