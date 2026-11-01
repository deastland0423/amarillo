/**
 * The two things a player can do to a drone rack's loadout, as changes to the COI state.
 *
 * <h2>Absent is not empty, and the difference is a whole rack of drones</h2>
 * Core's {@code CoiLoadout} says it plainly: "If a rack index is absent, the rack keeps its
 * default ammo." A rack set to `[]` is a rack the player has deliberately emptied, and
 * {@code ScenarioLoader.applyCoi} loads exactly that — nothing. So "put it back the way it was"
 * must DELETE the key, and a `[]` would send the ship into battle with four empty racks.
 *
 * That is the kind of mistake that reads correctly and plays catastrophically, which is why
 * these two lines are a module with tests rather than two closures in the dialog's render.
 */
export type RackLoadouts = Record<number, string[]>;

/** Put a whole load in the builder for one rack, replacing whatever was there. */
export function withRackLoad(
  loadouts: RackLoadouts,
  rackIndex: number,
  drones: string[],
): RackLoadouts {
  return { ...loadouts, [rackIndex]: [...drones] };
}

/**
 * Forget this rack entirely, so it keeps the drones the ship arrived with (S2.11).
 *
 * NOT the same as loading it with nothing: see the note above.
 */
export function withoutRack(loadouts: RackLoadouts, rackIndex: number): RackLoadouts {
  const next = { ...loadouts };
  delete next[rackIndex];
  return next;
}
