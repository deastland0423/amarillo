import type { ShipObject, ShuttleBayState, ShuttleInBayState } from '../types/gameState';

/**
 * What the client needs to know about a balcony (J1.53), in one place.
 *
 * Extracted because the same two questions were being asked in two components — the launch
 * pad, which must offer parked craft a launch, and the map panel, which must offer a craft in
 * space a place to land — and because the first of them was already wrong once.
 *
 * The trap these functions exist to close: a parked craft is deliberately NOT in a bay's
 * `shuttles`. That field lists the occupied BOXES, which is right for damage, arming and deck
 * crews, and wrong for anything about launching. Code reading only `shuttles` loses every
 * parked craft silently — no error, just a craft that cannot be given an order. The server had
 * the same bug by the same route, in the lookup behind LAUNCH_SHUTTLE.
 */

/**
 * Everything a ship could launch: the craft in its boxes PLUS the craft parked outside.
 *
 * Ask this, never `bay.shuttles`, when the question is "what can be launched".
 */
export function launchableCraft(bays: ShuttleBayState[] | undefined): ShuttleInBayState[] {
  return (bays ?? []).flatMap(b => [...(b.shuttles ?? []), ...(b.balcony ?? [])]);
}

/** The names of every parked craft, for marking the rows whose launch is the free one. */
export function parkedCraftNames(bays: ShuttleBayState[] | undefined): Set<string> {
  return new Set((bays ?? []).flatMap(b => (b.balcony ?? []).map(c => c.name)));
}

/** True if any bay has a balcony at all. Almost no bay does, so this gates the whole section. */
export function hasBalcony(bays: ShuttleBayState[] | undefined): boolean {
  return (bays ?? []).some(b => b.balconyPositions > 0);
}

/** Positions still free on one bay's balcony. */
export function balconyFreeOn(bay: ShuttleBayState): number {
  return Math.max(0, bay.balconyPositions - (bay.balcony?.length ?? 0));
}

/**
 * True if this ship has a free balcony position somewhere — what a craft in space needs
 * before it can be offered a landing on one (J1.532).
 */
export function shipHasFreeBalcony(ship: ShipObject | null | undefined): boolean {
  return (ship?.shuttleBays ?? []).some(b => b.balconyPositions > 0 && balconyFreeOn(b) > 0);
}
