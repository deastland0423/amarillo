import type {
  CoiShipChoices, CoiSideData, CoiSubmission, FleetShipEntry, FleetSpec,
} from '../api/gameApi';

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

/**
 * The options payload the dialog is open on, together with the fleet it was fetched FOR.
 *
 * <h2>Why the fleet is carried alongside</h2>
 * Reported from live play, 2026-11-02: adjust the Kzinti fleet's options, save it, load Task
 * Force Optimus — and its Options view is the Kzinti fleet. The payload and the ship names
 * were held in state of their own, and nothing on the way back through the fleet list cleared
 * them, so the dialog reopened on the force it had finished with. Worse than looking wrong: the
 * join to `spec.ships` is POSITIONAL, so submitting it would have written one fleet's purchases
 * onto another fleet's ships.
 *
 * <p>Resetting at each load point was the obvious fix and the wrong one — it holds only until
 * someone adds a fourth way to change the fleet. So the data states which fleet it belongs to
 * and {@link activeCoiView} refuses it against any other. A load path cannot forget to
 * invalidate a cache that invalidates itself.
 */
export interface CoiView {
  /** The {@link fleetCoiKey} of the fleet this was fetched for. */
  forFleet: string;
  sides: CoiSideData[];
  /** `names[i]` is the name the resolver gave `spec.ships[i]`. */
  names: string[];
}

/**
 * What a fleet must still be for an options payload fetched against it to mean anything.
 *
 * Everything the payload depends on, and nothing else:
 * <ul>
 *   <li>the saved fleet's id, which is what changes when another fleet is loaded;</li>
 *   <li>the year, because availability and price both move with it (S8.131, S8.133);</li>
 *   <li>each ship in ORDER, since the join is positional — its faction, type, name and the
 *       refits ticked on it, because a refit can change the racks the dialog offers to load
 *       (a type-A becomes a type-B in Y175).</li>
 * </ul>
 *
 * <p>Deliberately NOT included: the `coi` blocks themselves. Those are the dialog's own answer,
 * and keying on them would have the act of submitting invalidate the thing that submitted it.
 */
export function fleetCoiKey(spec: FleetSpec): string {
  const ships = spec.ships.map(e =>
    [e.faction ?? '', e.type, e.name ?? '', (e.refits ?? []).join('+')].join(':'));
  return [spec.id ?? 'unsaved', spec.year, ships.join('|')].join('#');
}

/**
 * The view if it still belongs to this fleet, otherwise null — which closes the dialog.
 *
 * <p>An unsaved fleet has no id, so two different new fleets can share the 'unsaved' slot. The
 * ship list is in the key for that case: two blank fleets with no ships are genuinely
 * interchangeable as far as this payload goes, and the Options button is disabled until one of
 * them has a ship.
 */
export function activeCoiView(view: CoiView | null, spec: FleetSpec): CoiView | null {
  if (!view) return null;
  return view.forFleet === fleetCoiKey(spec) ? view : null;
}
