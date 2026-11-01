import { describe, expect, it } from 'vitest';
import type { CoiDroneType, CoiDroneModule, CoiDroneAddition } from '../api/gameApi';
import {
  additionRefusal, describeDrone, formatDroneName, isModified, moduleRefusal, parseDroneName,
  payloadUsed, plainDrone, rackSpacesOf,
} from './droneComposition';

/**
 * The client half of core's `DroneLoad` grammar.
 *
 * <p>Every one of these strings is one core will have to read back, so the cases that matter are
 * the ones where the two could disagree: which bucket a token lands in, what an unmodified drone
 * spells itself as, and FD10.24's frame change — the one modification that alters how much of a
 * rack a drone fills, and the one number this file is not allowed to work out for itself.
 */

const MODULES: CoiDroneModule[] = [
  { name: 'EXPLOSIVE_1', label: 'Explosive', spaces: 1, cost: 0.5,
    availability: 'GENERAL', strength: 12, hullBonus: 0, requiresForward: false,
    armor: false, submunitions: 0 },
  { name: 'ECM', label: 'ECM', spaces: 1, cost: 0.5,
    availability: 'RESTRICTED', strength: 0, hullBonus: 0, requiresForward: false,
    armor: false, submunitions: 0 },
  { name: 'ARMOR_HALF', label: 'Armor (1/2 space)', spaces: 0.5, cost: 0.25,
    availability: 'RESTRICTED', strength: 0, hullBonus: 2, requiresForward: false,
    armor: true, submunitions: 0 },
  { name: 'MW_LARGE', label: 'Multi-warhead (5)', spaces: 2, cost: 3.5,
    availability: 'LIMITED', strength: 0, hullBonus: 0, requiresForward: true,
    armor: false, submunitions: 5 },
];

const ADDITIONS: CoiDroneAddition[] = [
  { name: 'EXTENDED_RANGE', label: 'Extended range', cost: 0.5,
    availability: 'GENERAL', hullBonus: 0, externalArmor: false },
  { name: 'EXTERNAL_ARMOR_HALF', label: 'External armor (1/2 space)', cost: 0.25,
    availability: 'LIMITED', hullBonus: 2, externalArmor: true },
];

function droneType(over: Partial<CoiDroneType> & { name: string }): CoiDroneType {
  return {
    speed: 8, damage: 12, rack: 1, family: 'STANDARD', familyLabel: 'Standard',
    endurance: 96, selfGuiding: false, dogfight: false, damageVsShip: 12,
    ...over,
  };
}

const TYPES: CoiDroneType[] = [
  droneType({ name: 'TypeI', payloadSpaces: 1, modular: true, rackWithExtendedRange: 1 }),
  // FD10.24: a type-III given extended range "become[s] type-III-XX, which are two-space
  // drones with one payload space".
  droneType({ name: 'TypeIII', speed: 12, endurance: 800, selfGuiding: true,
    family: 'SELF_GUIDING', familyLabel: 'Self-guiding',
    payloadSpaces: 1, modular: true, rackWithExtendedRange: 2 }),
  droneType({ name: 'TypeIV', damage: 24, damageVsShip: 24, rack: 2, family: 'HEAVY',
    familyLabel: 'Heavy', payloadSpaces: 2, modular: true, rackWithExtendedRange: 2 }),
  // A dogfight drone cannot be given extended range at all (FD2.222), so the server sends no
  // figure for it.
  droneType({ name: 'TypeVI', speed: 12, damage: 8, damageVsShip: 2, rack: 0.5, endurance: 32,
    family: 'DOGFIGHT', familyLabel: 'Dogfight', dogfight: true,
    payloadSpaces: 0, modular: false }),
];

describe('the loadout name', () => {
  /**
   * The compatibility promise, and the reason the wire's shape did not have to change: a drone
   * with nothing done to it is spelled exactly as it always was.
   */
  it('is just the drone name when nothing was done to it', () => {
    const plain = plainDrone('TypeIM');

    expect(isModified(plain)).toBe(false);
    expect(formatDroneName(plain)).toBe('TypeIM');
    expect(parseDroneName('TypeIM', MODULES, ADDITIONS)).toEqual(plain);
  });

  it('round-trips a composed drone', () => {
    const name = 'TypeIV+ARMOR_HALF+ARMOR_HALF+EXTENDED_RANGE';
    const comp = parseDroneName(name, MODULES, ADDITIONS);

    expect(comp.basis).toBe('TypeIV');
    expect(comp.modules).toEqual(['ARMOR_HALF', 'ARMOR_HALF']);
    expect(comp.additions).toEqual(['EXTENDED_RANGE']);
    expect(formatDroneName(comp)).toBe(name);
  });

  /**
   * FD8.11 buys submunitions an engine of their own. A `SPEED_` token can only be theirs — the
   * drone's own propulsion comes from its basis — which is the same rule core's parser follows.
   */
  it('reads a SPEED_ token as the submunitions engine', () => {
    const comp = parseDroneName('TypeIV+MW_LARGE+SPEED_32', MODULES, ADDITIONS);

    expect(comp.modules).toEqual(['MW_LARGE']);
    expect(comp.submunitionSpeed).toBe('SPEED_32');
    expect(formatDroneName(comp)).toBe('TypeIV+MW_LARGE+SPEED_32');
  });

  /**
   * A name can carry something this client is not offering as a choice: core's formatter spells
   * out a type-III's own frame surcharge, which is what MAKES it a type-III rather than an
   * option on it. Editing such a drone must not quietly throw that away.
   */
  it('keeps a token it does not recognise', () => {
    const comp = parseDroneName('TypeIII+EXPLOSIVE_1+TYPE_III_GUIDANCE', MODULES, ADDITIONS);

    expect(comp.modules).toEqual(['EXPLOSIVE_1']);
    expect(comp.passthrough).toEqual(['TYPE_III_GUIDANCE']);
    expect(formatDroneName(comp)).toBe('TypeIII+EXPLOSIVE_1+TYPE_III_GUIDANCE');
  });
});

describe('payload space', () => {
  it('adds up the modules, half-spaces included', () => {
    const comp = parseDroneName('TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF', MODULES, ADDITIONS);

    expect(payloadUsed(comp, MODULES)).toBe(1.5);
  });

  /**
   * FD12.13: external armor is "in excess of the normal payload space limit" and FD10.47 says
   * it takes "up no payload spaces but slow[s] down the drone". So a one-space drone can carry a
   * full warhead AND armor, which internal armor would make impossible.
   */
  it('does not charge a frame addition any payload space', () => {
    const comp = parseDroneName('TypeI+EXPLOSIVE_1+EXTERNAL_ARMOR_HALF', MODULES, ADDITIONS);

    expect(payloadUsed(comp, MODULES)).toBe(1);
  });
});

describe('rack space', () => {
  it('is the frame footprint for an ordinary drone', () => {
    expect(rackSpacesOf(plainDrone('TypeI'), TYPES)).toBe(1);
    expect(rackSpacesOf(plainDrone('TypeIV'), TYPES)).toBe(2);
    expect(rackSpacesOf(plainDrone('TypeVI'), TYPES)).toBe(0.5);
  });

  /**
   * FD10.24, the one modification that changes a drone's footprint. The figure comes from the
   * server; this only has to use it when extended range is ticked, because a view deciding that
   * a frame had changed would be a view deciding a rules question.
   */
  it('grows a type-III given extended range to two spaces', () => {
    const comp = parseDroneName('TypeIII+EXTENDED_RANGE', MODULES, ADDITIONS);

    expect(rackSpacesOf(comp, TYPES)).toBe(2);
  });

  it('leaves every other drone its own footprint under extended range', () => {
    const comp = parseDroneName('TypeI+EXTENDED_RANGE', MODULES, ADDITIONS);

    expect(rackSpacesOf(comp, TYPES)).toBe(1);
  });

  /** A loadout naming a drone this ship was not offered contributes nothing rather than NaN. */
  it('ignores a drone it has never heard of', () => {
    expect(rackSpacesOf(plainDrone('TypeIX'), TYPES)).toBe(0);
  });
});

describe('the chip label', () => {
  it('is the bare name and stats for an unmodified drone', () => {
    expect(describeDrone(plainDrone('TypeIM'), MODULES, ADDITIONS)).toBe('TypeIM');
  });

  /** FD12.12's armored type-IV, as a player would want to read it back off the rack. */
  it('names what was done to a modified one', () => {
    const comp = parseDroneName('TypeIV+ARMOR_HALF+ARMOR_HALF+EXTENDED_RANGE',
      MODULES, ADDITIONS);

    expect(describeDrone(comp, MODULES, ADDITIONS))
      .toBe('TypeIV · Armor (1/2 space), Armor (1/2 space), Extended range');
  });
});

/**
 * What the editor greys out, and why.
 *
 * These are ADVICE: core refuses an illegal composition whatever the client believed, and
 * `DroneLoadTest` pins every clause at that end. What they buy is a button that stops a player
 * building a drone they will be told off for — and they are tested here because when this logic
 * sat in the editor's JSX, FD12.131 was simply missing from it, and no test on this side could
 * have noticed.
 */
const mod = (name: string) => MODULES.find(m => m.name === name)!;
const addn = (name: string) => ADDITIONS.find(a => a.name === name)!;

describe('moduleRefusal', () => {
  it('allows a module there is room for', () => {
    expect(moduleRefusal(plainDrone('TypeIV'), mod('ECM'), MODULES, ADDITIONS, 2)).toBeNull();
  });

  /** FD10.4: the payload has to fit. */
  it('refuses a module the payload has no room for', () => {
    const full = parseDroneName('TypeI+EXPLOSIVE_1', MODULES, ADDITIONS);

    expect(moduleRefusal(full, mod('ECM'), MODULES, ADDITIONS, 1)).toContain('FD10.4');
  });

  /**
   * FD10.443: "The ECM module can... be combined with any other module in the game except an
   * (internal or external) armor module. ECM drones will not work with armor modules."
   */
  it('refuses an ECM module beside internal armor, and armor beside an ECM module', () => {
    const armored = parseDroneName('TypeIV+ARMOR_HALF', MODULES, ADDITIONS);
    const jammer  = parseDroneName('TypeIV+ECM', MODULES, ADDITIONS);

    expect(moduleRefusal(armored, mod('ECM'), MODULES, ADDITIONS, 2)).toContain('FD10.443');
    expect(moduleRefusal(jammer, mod('ARMOR_HALF'), MODULES, ADDITIONS, 2))
      .toContain('FD10.443');
  });

  /** And beside EXTERNAL armor, which the same clause covers in the same breath. */
  it('refuses an ECM module beside external armor', () => {
    const plated = parseDroneName('TypeIV+EXTERNAL_ARMOR_HALF', MODULES, ADDITIONS);

    expect(moduleRefusal(plated, mod('ECM'), MODULES, ADDITIONS, 2)).toContain('FD10.443');
  });

  /**
   * FD12.131: "External armor cannot be placed on a drone equipped with internal armor." The
   * clause the contract test against core caught missing, asked of the MODULE so that the order
   * of the clicks cannot decide whether the drone is legal.
   */
  it('refuses internal armor on a drone that already has external armor', () => {
    const plated = parseDroneName('TypeIV+EXTERNAL_ARMOR_HALF', MODULES, ADDITIONS);

    expect(moduleRefusal(plated, mod('ARMOR_HALF'), MODULES, ADDITIONS, 2))
      .toContain('FD12.131');
  });

  /**
   * FD10.402: a forward-only module claims the one forward space. A two-space module on a
   * two-space frame is forward by filling everything, which is why FD10.432 writes "forward
   * payload space(s)" with the plural — so it is not refused for being forward-only.
   */
  it('lets a module that spans the whole payload be forward', () => {
    expect(moduleRefusal(plainDrone('TypeIV'), mod('MW_LARGE'), MODULES, ADDITIONS, 2))
      .toBeNull();
  });
});

describe('additionRefusal', () => {
  it('allows an ordinary addition', () => {
    expect(additionRefusal(plainDrone('TypeI'), addn('EXTENDED_RANGE'), MODULES, 1, 4, 1))
      .toBeNull();
  });

  /** FD10.443 and FD12.131, from the other side. */
  it('refuses external armor on an ECM or internally armored drone', () => {
    const jammer  = parseDroneName('TypeIV+ECM', MODULES, ADDITIONS);
    const armored = parseDroneName('TypeIV+ARMOR_HALF', MODULES, ADDITIONS);

    expect(additionRefusal(jammer, addn('EXTERNAL_ARMOR_HALF'), MODULES, 2, 4, 2))
      .toContain('FD10.443');
    expect(additionRefusal(armored, addn('EXTERNAL_ARMOR_HALF'), MODULES, 2, 4, 2))
      .toContain('FD12.131');
  });

  /**
   * FD10.24: a type-III given extended range becomes a two-space drone, so it has to fit. The
   * grown figure comes from the server; this only has to compare it with the room left.
   */
  it('refuses extended range on a type-III with one rack space left', () => {
    expect(additionRefusal(plainDrone('TypeIII'), addn('EXTENDED_RANGE'), MODULES, 1, 1, 2))
      .toContain('FD10.24');
    expect(additionRefusal(plainDrone('TypeIII'), addn('EXTENDED_RANGE'), MODULES, 1, 2, 2))
      .toBeNull();
  });

  /** Unticking is always allowed, whatever else is aboard. */
  it('never refuses an addition that is already on', () => {
    const both = parseDroneName('TypeIV+ARMOR_HALF+EXTERNAL_ARMOR_HALF', MODULES, ADDITIONS);

    expect(additionRefusal(both, addn('EXTERNAL_ARMOR_HALF'), MODULES, 2, 2, 2)).toBeNull();
  });
});
