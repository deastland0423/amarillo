import type { CoiShipChoices } from '../api/gameApi';

/**
 * One ship's Commander's Option choices while they are being EDITED, and the translation to and
 * from the form they are recorded in.
 *
 * <h2>Why this is not in the dialog</h2>
 * It is the same reason `coiRackLoad.ts` is its own module: the translation is a pure function
 * that was living in a component file, which costs that file its fast refresh and leaves the
 * function untestable. And this one has real work to do rather than copying fields across.
 *
 * <h2>Two shapes, and the differences are deliberate</h2>
 * A recorded purchase ({@link CoiShipChoices}) leaves out what was not chosen, because a fleet
 * file records a purchase rather than a form. An editor needs every field present, or its
 * controls are uncontrolled. Then `specialShuttlePrep` is a LIST on the wire, each entry naming
 * its shuttle, while the editor holds a map of shuttle to preparation; and rack indexes are
 * strings in JSON, as every object key is, and numbers in here.
 *
 * <p>So a purchase that round-trips through the editor has to come back the same purchase. That
 * is what {@code coiShipState.test.ts} holds still, and it matters most in the fleet builder,
 * where reopening the dialog on a saved fleet must show what the fleet carries rather than
 * quietly offering to replace it with nothing.
 */
export type ArmMode = 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING';

export interface ShuttlePrep {
  type:          string;       // 'suicide' | 'scatterpack' | 'wildweasel'
  energyPerTurn: number;       // suicide only
  drones:        string[];     // scatterpack only
}

export interface ShipCoi {
  extraBoardingParties: number;
  convertBpToCommando:  number;
  extraCommandoSquads:  number;
  extraTBombs:          number;
  extraDeckCrews:       number;
  armedFighters:        string[];
  weaponArmingModes:    Record<string, ArmMode>;
  photonOverload:       Record<string, number>;   // free WS-III overload energy per tube (S4.32)
  droneRackLoadouts:    Record<number, string[]>;   // rackIndex → drone type names
  antiDroneLoadouts:    Record<number, number>;     // rackIndex → anti-drone rounds (FD3.70)
  shuttlePrep:          Record<string, ShuttlePrep | null>; // shuttleName → prep or null (not selected)
  optionMounts:         Record<string, string>;     // mount designator → option name (G15.4)
  optionMountArcs:      Record<string, string>;     // designator → "FORWARD"|"OUTBOARD" (R8.R3)
  /**
   * J4.621: which fighter model this ship's fighter facilities serve. '' means "not chosen",
   * which leaves the line's standard fighter — the server treats absent and unavailable the
   * same way, so there is nothing to represent for a default beyond the empty string.
   */
  fighterFacilityType:  string;
}

/**
 * The editor's state for a ship, from choices already recorded (S8.12) or empty.
 *
 * <h2>The two shapes are not quite the same, and the differences are deliberate</h2>
 * What is saved omits what was not chosen, since a fleet file records a purchase rather than a
 * form; the editor needs every field present so the controls are not uncontrolled. And
 * `specialShuttlePrep` is a LIST on the wire, keyed by shuttle name, while the editor holds a
 * map of shuttle to preparation — so it is turned back here rather than stored twice.
 */
export function seedShipCoi(saved?: CoiShipChoices): ShipCoi {
  const base = defaultShipCoi();
  if (!saved) return base;

  const shuttlePrep: Record<string, ShuttlePrep | null> = {};
  for (const prep of saved.specialShuttlePrep ?? []) {
    if (!prep.shuttleName) continue;
    shuttlePrep[prep.shuttleName] = {
      type:          prep.type ?? 'suicide',
      energyPerTurn: prep.energyPerTurn ?? 3,
      drones:        prep.drones ?? [],
    };
  }

  // Rack indexes travel as strings (JSON object keys always do) and are numbers in here.
  //
  // The conversion is for the TYPE and not the runtime: a JavaScript object key is always a
  // string, so racks[0] and racks["0"] are one property and dropping the Number() changes
  // nothing a test could see. Kept because Record<number, ...> is the honest declaration of what
  // a rack index is, and because the server speaks strings either way.
  const racks: Record<number, string[]> = {};
  for (const [key, drones] of Object.entries(saved.droneRackLoadouts ?? {}))
    racks[Number(key)] = [...drones];
  const addRounds: Record<number, number> = {};
  for (const [key, rounds] of Object.entries(saved.antiDroneLoadouts ?? {}))
    addRounds[Number(key)] = rounds;

  return {
    ...base,
    extraBoardingParties: saved.extraBoardingParties ?? 0,
    convertBpToCommando:  saved.convertBpToCommando  ?? 0,
    extraCommandoSquads:  saved.extraCommandoSquads  ?? 0,
    extraTBombs:          saved.extraTBombs          ?? 0,
    extraDeckCrews:       saved.extraDeckCrews       ?? 0,
    fighterFacilityType:  saved.fighterFacilityType  ?? '',
    armedFighters:        [...(saved.armedFighters ?? [])],
    weaponArmingModes:    { ...(saved.weaponArmingModes ?? {}) },
    photonOverload:       { ...(saved.photonOverload ?? {}) },
    droneRackLoadouts:    racks,
    antiDroneLoadouts:    addRounds,
    shuttlePrep,
    optionMounts:         { ...(saved.optionMounts ?? {}) },
    optionMountArcs:      { ...(saved.optionMountArcs ?? {}) },
  };
}

export function defaultShipCoi(): ShipCoi {
  return {
    extraBoardingParties: 0,
    convertBpToCommando:  0,
    extraCommandoSquads:  0,
    extraTBombs:          0,
    extraDeckCrews:       0,
    armedFighters:        [],
    weaponArmingModes:    {},
    photonOverload:       {},
    droneRackLoadouts:    {},
    antiDroneLoadouts:    {},
    shuttlePrep:          {},
    optionMounts:         {},
    optionMountArcs:      {},
    fighterFacilityType:  '',
  };
}
