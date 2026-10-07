import type { WeaponState } from '../types/gameState';

/**
 * What to call a weapon in a list: its kind and its designator, each said once.
 *
 * ## The bug this exists for
 *
 * `WeaponState.name` is the server's IDENTITY for a weapon and is built as `type + "-" +
 * designator` — so it already contains the designator. The SSD panel printed `name` and then
 * appended `designator`, which double-printed it on every row of every ship in the game:
 *
 * ```
 *   Phaser1-1 1        Photon-A A        Disruptor30-A A
 *   ADD-ADD 1 ADD 1    Drone-Rack 1 Rack 1
 * ```
 *
 * The ADDs and drone racks read worst, which is how it was spotted, but nothing was exempt.
 *
 * ## The one rule
 *
 * A designator that already begins with its own weapon's type needs no type in front of it.
 * That single clause is what separates the two awkward cases:
 *
 * - ADD: type `ADD`, designator `ADD 1` → **"ADD 1"**, not "ADD ADD 1".
 * - Drone rack: type `Drone`, designator `Rack 1` → **"Drone Rack 1"**, which is its real name.
 *
 * Both come from the ship data, where 43 hulls designate ADDs "ADD 1" and 91 designate racks
 * "Rack 1". Those designators are NOT free to change: `name` is the wire key for reload
 * selections, hex fire and boarding targets, so renaming them would be a protocol change across
 * 134 files to fix a caption. Hence a display rule rather than a data edit.
 *
 * ## What this deliberately is not
 *
 * Not a prettifier. `GameBoard` and `EnergyAllocationDialog` each carry their own `weaponLabel`
 * that abbreviates for a narrow sidebar ("Ph1-A", "Dis-A"), and a panel with a whole line to spend
 * does not need a third, divergent scheme. Both of those have also drifted: they rewrite
 * `/^Disruptor-/` and `/^DroneRack-/` while the real names are `Disruptor30-A` and `Drone-Rack 1`,
 * so neither rule has matched anything for some time. If a shared display NAME is ever wanted
 * ("Phaser-1" for `Phaser1`, a range for `Disruptor30`), it belongs in core beside the type, where
 * all three call sites can read it — not copied a fourth time into a view.
 */
export function weaponTitle(w: WeaponState): string {
  const kind = rackKind(w) ?? plasmaKind(w) ?? w.type ?? w.name;
  const designator = w.designator ?? '';
  if (!designator) return kind;
  if (designator.toLowerCase().startsWith(kind.toLowerCase())) return designator;
  return `${kind} ${designator}`;
}

/**
 * A drone rack says WHICH of the eight it is: "Type-A Drone" with designator "Rack 1" gives
 * **"Type-A Drone Rack 1"**.
 *
 * Worth the words. The eight racks are not variations on a theme — FD3.3's type-C fires twice a
 * turn, FD3.5's type-E carries eight dogfight drones and launches four times, FD3.7's type-G throws
 * anti-drones, and FD3.4's type-D is a magazine launcher with no reloads at all — and "Drone Rack 1"
 * conveyed none of that. In the fleet builder it is a buying decision; in battle it is a targeting
 * one.
 *
 * Returns null for any weapon that is not a rack, so the ordinary path is untouched. Formatting
 * TYPE_A as "Type-A" is a plain transliteration of the enum, not a lookup table that could fall
 * behind it: a ninth rack type would read "Type-I" the day it was added.
 */
/**
 * A plasma launcher says WHICH torpedo it throws: "Plasma-R A", not "Plasma A".
 *
 * `type` is only ever "Plasma" — the letter lives in `launcherType`, and it is the single most
 * important fact about the weapon. An R and an F are not variants of one gun: FP1.x gives them
 * different warheads, different arming costs and different ranges, so a Gorn heavy cruiser with
 * plasma-Rs forward is a different ship to fight from one with plasma-Fs, and the shelf was showing
 * both as "Plasma A, Plasma B".
 *
 * Returns null for anything that is not a launcher, so the ordinary path is untouched. A launcher
 * whose type is absent falls back to plain "Plasma" rather than inventing a letter: {@code
 * launcherType} is null on a launcher that has none, and saying nothing beats guessing.
 *
 * Note this is the LAUNCHER's fixed type, not what is loaded in it. Which torpedo is in the tube —
 * and whether it is a pseudo — is secret until identified (G4.232), and the DTO withholds
 * {@code plasmaType} from an enemy for that reason. The launcher itself is on the SSD.
 */
function plasmaKind(w: WeaponState): string | null {
  if (w.type !== 'Plasma' || !w.launcherType) return null;
  return 'Plasma-' + w.launcherType;
}

function rackKind(w: WeaponState): string | null {
  if (!w.rackType) return null;
  const letter = w.rackType.replace(/^TYPE_/, '');
  if (letter === w.rackType) return null;   // an unexpected shape: say nothing rather than guess
  return `Type-${letter} ${w.type ?? 'Drone'}`;
}
