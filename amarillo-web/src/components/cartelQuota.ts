import type { Cartel } from '../api/gameApi';

/**
 * G15.44's access tiers and the quota they impose, shared by the two places an Orion's option
 * mounts are chosen.
 *
 * <h2>Why both places need it</h2>
 * G15.415 — "Optional weapons cannot be changed without a shipyard overhaul taking several weeks
 * or months" — makes a loadout part of what a ship IS. So it is chosen wherever the ship is
 * bought: in the FLEET BUILDER for a pick-up battle, and in the COI form when a scenario has
 * delegated the shipyard to its players. Both must tier and total the same way, and a second
 * copy of this arithmetic would be a second answer waiting to disagree with the server's.
 *
 * <h2>Why the choices are never filtered by cartel</h2>
 * G15.441 splits the FORCE's mounts 70% to the cartel's home empire, 20% to its operating zone
 * and 10% to anyone. No single option is legal or illegal on its own — a Klingon disruptor is
 * fine until it is the eleventh one — so a picker can only ever show the tier and the running
 * total, and let the whole selection be judged. `CartelQuota` on the server does the judging.
 */
export type Tier = 'HOME' | 'OPERATING' | 'OUTSIDE' | 'UNIVERSAL';

/** Access tier of an option's producing empires under a cartel (mirrors OrionCartel.accessForAny). */
export function tierFor(empires: string[] | null | undefined, cartel: Cartel | undefined): Tier {
  if (!empires || empires.length === 0 || !cartel) return 'UNIVERSAL';
  let best: Tier = 'OUTSIDE';
  for (const e of empires) {
    if (e === cartel.home) return 'HOME';
    if (cartel.operatingZone.includes(e)) best = 'OPERATING';
  }
  return best;
}

export interface Quota {
  hasMounts: boolean;
  total:     number;
  op:        number;  outUsed:  number;
  opCap:     number;  outCap:   number;
  within:    boolean;
}

/**
 * The quota for a force, from its total mount count and the tiers of what has been chosen.
 *
 * <p>Caps are rounded to nearest and the two pools are independent, which is how
 * `CartelQuota.evaluate` reads G15.441 on the server. A force with no declared cartel is always
 * within quota: an Orion who has not said which cartel they fly for is not second-guessed.
 */
export function quotaOf(totalMounts: number, tiers: Tier[], cartel: Cartel | undefined): Quota {
  let op = 0, outUsed = 0;
  for (const tier of tiers) {
    if (tier === 'OPERATING') op++;
    else if (tier === 'OUTSIDE') outUsed++;
  }
  const opCap  = Math.round(totalMounts * 0.20);
  const outCap = Math.round(totalMounts * 0.10);
  return {
    hasMounts: totalMounts > 0,
    total: totalMounts, op, outUsed, opCap, outCap,
    within: !cartel || (op <= opCap && outUsed <= outCap),
  };
}

/** How a quota reads to a player, e.g. "2/1 operating-zone, 0/1 outside — over quota". */
export function describeQuota(q: Quota, cartel: Cartel | undefined): string {
  if (!cartel || !q.hasMounts) return '';
  return `${q.op}/${q.opCap} operating-zone, ${q.outUsed}/${q.outCap} outside`
       + (q.within ? '' : ' — over quota (G15.44)');
}
