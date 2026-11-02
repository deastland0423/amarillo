import type { CoiShipChoices, CoiSubmission, FleetShipEntry } from '../api/gameApi';

/**
 * Matching a fleet's ships to the Commander's Options dialog, which knows them by NAME.
 *
 * <h2>Why position and not name</h2>
 * The dialog is keyed by ship name, and the names it is handed are the ones the fleet RESOLVER
 * produced — which are not always the names in the spec. An entry may carry no name at all,
 * meaning the ship file's own; and two ships of one type get " #2" appended, because a violation
 * has to be able to point at one of them. Keying off `entry.name` would therefore drop the
 * purchases of an unnamed ship and give both copies of a pair the same ones.
 *
 * <p>The resolver reports ships in the order of `spec.ships`, so position is the reliable join.
 * `names[i]` is the name standing for `ships[i]`, and the caller checks the two lengths agree
 * before trusting that.
 */

/** What each ship has already bought, keyed the way the dialog expects. */
export function coiInitialFor(
  ships: FleetShipEntry[],
  names: string[],
): CoiSubmission {
  const out: CoiSubmission = {};
  ships.forEach((entry, i) => {
    if (entry.coi && names[i]) out[names[i]] = entry.coi;
  });
  return out;
}

/**
 * Record the dialog's answer on the fleet (S8.12).
 *
 * <p>A ship that bought nothing gets no `coi` block rather than an empty one, and that is not
 * tidiness: an ABSENT block is what tells the loader to leave the ship with the drones its year
 * fits (S2.11), exactly as an absent rack index does inside the block. An empty object would be
 * read as a purchase of nothing.
 *
 * <p>Nothing derived is written. The cost is computed from these choices at the year of whatever
 * battle they are used in, because S8.132 makes a later year's refits mandatory and S8.133 its
 * drone speeds standard — so one purchase legitimately costs two different amounts.
 */
export function withCoiApplied(
  ships: FleetShipEntry[],
  names: string[],
  submission: CoiSubmission,
): FleetShipEntry[] {
  return ships.map((entry, i) => {
    const chosen: CoiShipChoices | undefined = names[i] ? submission[names[i]] : undefined;
    if (!chosen || Object.keys(chosen).length === 0) {
      const rest = { ...entry };
      delete rest.coi;
      return rest;
    }
    return { ...entry, coi: chosen };
  });
}
