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
  const kind = w.type ?? w.name;
  const designator = w.designator ?? '';
  if (!designator) return kind;
  if (designator.toLowerCase().startsWith(kind.toLowerCase())) return designator;
  return `${kind} ${designator}`;
}
