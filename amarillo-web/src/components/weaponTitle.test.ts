import { describe, expect, it } from 'vitest';
import type { WeaponState } from '../types/gameState';
import { weaponTitle } from './weaponTitle';

/** Only the fields the title reads; the rest of WeaponState is irrelevant here. */
function weapon(type: string | undefined, designator: string | undefined,
                name = 'IGNORED', rackType?: string): WeaponState {
  return { name, type, designator, rackType } as WeaponState;
}

/** A drone rack as the server sends one: type "Drone", designator "Rack n", plus its rack type. */
function rack(rackType: string, designator = 'Rack 1'): WeaponState {
  return weapon('Drone', designator, `Drone-${designator}`, rackType);
}

/** A plasma launcher: type is always "Plasma", with the letter in launcherType. */
function plasma(launcherType: string | null, designator = 'A'): WeaponState {
  return { name: `Plasma-${designator}`, type: 'Plasma', designator, launcherType } as WeaponState;
}

describe('weaponTitle', () => {
  it('says the kind and the designator once each', () => {
    expect(weaponTitle(weapon('Phaser1', '1'))).toBe('Phaser1 1');
    expect(weaponTitle(weapon('Photon', 'A'))).toBe('Photon A');
    expect(weaponTitle(weapon('Fusion', 'C'))).toBe('Fusion C');
    expect(weaponTitle(weapon('Disruptor30', 'B'))).toBe('Disruptor30 B');
  });

  /**
   * The case that prompted the whole thing. 43 hulls designate their ADDs "ADD 1", so the type
   * must drop out or it reads "ADD ADD 1" — and before this it read "ADD-ADD 1 ADD 1".
   */
  it('drops a type the designator already states', () => {
    expect(weaponTitle(weapon('ADD', 'ADD 1'))).toBe('ADD 1');
    expect(weaponTitle(weapon('ADD', 'ADD 2'))).toBe('ADD 2');
  });

  /**
   * And the near-miss beside it: a rack's designator does NOT start with "Drone", so the type
   * stays and the result is the rack's actual name. This is the pair that makes the rule earn its
   * place — one blanket treatment cannot produce both "ADD 1" and "Drone Rack 1".
   */
  it('keeps a type the designator does not state', () => {
    expect(weaponTitle(weapon('Drone', 'Rack 1'))).toBe('Drone Rack 1');
    expect(weaponTitle(weapon('Drone', 'Rack 12'))).toBe('Drone Rack 12');
  });

  it('matches regardless of case', () => {
    expect(weaponTitle(weapon('add', 'ADD 1'))).toBe('ADD 1');
    expect(weaponTitle(weapon('ADD', 'add 3'))).toBe('add 3');
  });

  /**
   * The regression guard, stated as the shape of the original bug rather than as "no repetition".
   * The panel rendered `name` and then appended `designator`, and since `name` is
   * `type + "-" + designator` the result was always `type-designator designator`. Asserting that
   * exact shape is gone is precise; asserting "the designator never appears twice" is not, because
   * a type ending in a digit makes "Phaser1 1" contain "1" twice quite legitimately.
   */
  it('never produces the old name-plus-designator shape', () => {
    const cases: Array<[string, string]> = [
      ['Phaser1', '1'], ['PhaserG', '7'], ['Photon', 'A'], ['Disruptor30', 'D'],
      ['ADD', 'ADD 1'], ['Drone', 'Rack 1'], ['PlasmaLauncher', 'A'], ['Hellbore', 'B'],
    ];
    for (const [type, designator] of cases) {
      const identity = `${type}-${designator}`;
      const title = weaponTitle(weapon(type, designator, identity));
      expect(title, 'the old doubled shape is back').not.toBe(`${identity} ${designator}`);
      // Nor should the identity string leak in on its own — the hyphen is a wire-format artefact.
      expect(title, `'${title}' shows the identity string`).not.toContain(identity);
    }
  });

  /**
   * A rack says which of the eight it is. The whole point: a type-C fires twice a turn, a type-G
   * throws anti-drones, a type-D has magazines and no reloads, and "Drone Rack 1" said none of it.
   */
  it('names which drone rack it is', () => {
    expect(weaponTitle(rack('TYPE_A'))).toBe('Type-A Drone Rack 1');
    expect(weaponTitle(rack('TYPE_G', 'Rack 2'))).toBe('Type-G Drone Rack 2');
    expect(weaponTitle(rack('TYPE_D'))).toBe('Type-D Drone Rack 1');
    expect(weaponTitle(rack('TYPE_H', 'Rack 11'))).toBe('Type-H Drone Rack 11');
  });

  /** Transliterated from the enum, so a rack type nobody has written about still reads sensibly. */
  it('handles a rack type it has never been told about', () => {
    expect(weaponTitle(rack('TYPE_I'))).toBe('Type-I Drone Rack 1');
  });

  /** An unexpected shape says nothing rather than guessing — better plain than wrong. */
  it('falls back when the rack type is not TYPE_x', () => {
    expect(weaponTitle(rack('WHATEVER'))).toBe('Drone Rack 1');
  });

  /** And a weapon that is not a rack is untouched by any of it. */
  it('leaves non-racks alone', () => {
    expect(weaponTitle(weapon('Phaser1', '1'))).toBe('Phaser1 1');
    expect(weaponTitle(weapon('ADD', 'ADD 1'))).toBe('ADD 1');
  });

  /**
   * A plasma launcher says which torpedo it throws. `type` is only ever "Plasma", so without this
   * a Gorn cruiser's armament read "Plasma A, Plasma B, Plasma C" — and an R, an S and an F are not
   * variants of one gun but different warheads, arming costs and ranges.
   */
  it('names which plasma a launcher throws', () => {
    expect(weaponTitle(plasma('R'))).toBe('Plasma-R A');
    expect(weaponTitle(plasma('S', 'B'))).toBe('Plasma-S B');
    expect(weaponTitle(plasma('F', 'D'))).toBe('Plasma-F D');
    expect(weaponTitle(plasma('G', 'C'))).toBe('Plasma-G C');
  });

  /** A launcher with no fixed type says "Plasma" rather than inventing a letter. */
  it('falls back for a launcher with no type', () => {
    expect(weaponTitle(plasma(null))).toBe('Plasma A');
  });

  /** And the plasma RACK is a different weapon entirely — it must not be relabelled. */
  it('leaves a plasma rack alone', () => {
    expect(weaponTitle(weapon('PlasmaRack', 'A'))).toBe('PlasmaRack A');
  });

  /** A weapon with no designator is just its kind, not a trailing space. */
  it('handles a missing designator', () => {
    expect(weaponTitle(weapon('ESG', undefined))).toBe('ESG');
    expect(weaponTitle(weapon('ESG', ''))).toBe('ESG');
  });

  /**
   * `name` is the fallback only when the server is too old to send `type`, and it is the identity
   * string — so this is the degraded case, not the intended one. It must still not double-print.
   */
  it('falls back to the identity name when type is absent', () => {
    expect(weaponTitle(weapon(undefined, 'A', 'Photon-A'))).toBe('Photon-A A');
    expect(weaponTitle(weapon(undefined, undefined, 'Photon-A'))).toBe('Photon-A');
  });
});
