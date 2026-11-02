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
/** The two halves of one magazine's load (FD3.70), as the COI state holds them. */
export interface RackMagazine {
  droneRackLoadouts: RackLoadouts;
  antiDroneLoadouts: Record<number, number>;
}

/**
 * Apply a ready-made loadout to one rack, drones and anti-drone rounds together.
 *
 * <h2>Why both maps move at once</h2>
 * FD3.70 has drones and ADDs sharing one magazine, so a preset describes the whole of it. Setting
 * only the drones would leave whatever ADD count was there beside them, which is how you get a
 * magazine holding more than it has room for and a refusal the player cannot see the cause of.
 *
 * <p>An ADD-only preset sets the drones to `[]` rather than deleting the key, and that is
 * deliberate: see the note at the top of this file. The rack is being LOADED with anti-drones and
 * no drones, which is not the same as being left alone. Zero ADDs, on the other hand, removes the
 * key, because there is no such thing as deliberately loading none of them.
 */
export function withPreset(
  state:     RackMagazine,
  rackIndex: number,
  preset:    { drones: string[]; antiDrones: number },
): RackMagazine {
  const antiDroneLoadouts = { ...state.antiDroneLoadouts };
  if (preset.antiDrones > 0) antiDroneLoadouts[rackIndex] = preset.antiDrones;
  else delete antiDroneLoadouts[rackIndex];
  return {
    droneRackLoadouts: withRackLoad(state.droneRackLoadouts, rackIndex, preset.drones),
    antiDroneLoadouts,
  };
}

export function withoutRack(loadouts: RackLoadouts, rackIndex: number): RackLoadouts {
  const next = { ...loadouts };
  delete next[rackIndex];
  return next;
}
