import { describe, it, expect } from 'vitest';
import { budgetBasisLabel, defaultAmmoLabel, droneChipLabel } from './coiLabels';
import type { CoiDroneType, CoiShipData } from '../api/gameApi';

/**
 * The sentences a player reads beside two numbers that must not be added together.
 *
 * S3.211 takes the Commander's Option percentage of a basis that excludes "the cost of mandatory
 * drone speed upgrades", while S2.14 counts that same charge in the victory total. Both figures
 * reach this screen, so the tooltip has to say which is which — and a sentence that quietly
 * stops matching the data is exactly what a test is for.
 */

const TYPES: CoiDroneType[] = [
  { name: 'TypeI',  speed: 8,  damage: 12, rack: 1, family: 'STANDARD', dogfight: false },
  { name: 'TypeIF', speed: 32, damage: 12, rack: 1, family: 'STANDARD', dogfight: false },
  { name: 'TypeIV', speed: 8,  damage: 24, rack: 2, family: 'HEAVY',    dogfight: false },
] as CoiDroneType[];

const SHIP: CoiShipData = {
  shipName: 'KHS Quasar',
  bpv: 116,
  weaponStatus: 0,
  coiBudget: 23,
  coiBudgetPercent: 20,
  coiBudgetBasis: 116,
  allowTBombs: true,
  allowCommandos: true,
  maxTBombs: 4,
  maxDroneSpeed: null,
} as CoiShipData;

describe('budgetBasisLabel', () => {
  it('states the percentage and the basis it is taken of', () => {
    expect(budgetBasisLabel(SHIP)).toContain('20% of 116 BPV');
    expect(budgetBasisLabel(SHIP)).toContain('S3.211');
  });

  /** A carrier's budget is bigger than a share of its hull, and the fighters are why. */
  it('breaks a carrier basis into hull and fighters', () => {
    const cv = { ...SHIP, coiBudgetBasis: 200, coiFighterBpv: 84 };

    expect(budgetBasisLabel(cv)).toContain('200 BPV (hull 116 + fighters 84)');
  });

  /**
   * S3.211 excludes "the cost of mandatory drone speed upgrades" from the basis, so the two
   * numbers on screen genuinely do not add up and the label has to say so.
   */
  it('names the drone speed charge as the thing NOT in the basis', () => {
    const fast = { ...SHIP, droneSpeedBpv: 16 };

    expect(budgetBasisLabel(fast)).toContain('not counting 16 BPV of drone speed');
    expect(budgetBasisLabel(fast)).toContain('20% of 116 BPV');
  });

  /** A ship with no drones has nothing to exclude, and says nothing about it. */
  it('stays quiet when there is no drone charge', () => {
    expect(budgetBasisLabel({ ...SHIP, droneSpeedBpv: 0 })).not.toContain('drone speed');
    expect(budgetBasisLabel(SHIP)).not.toContain('drone speed');
  });

  /** An older server sends no basis at all, and gets no tooltip rather than a wrong one. */
  it('says nothing without a basis to explain', () => {
    expect(budgetBasisLabel({ ...SHIP, coiBudgetBasis: undefined })).toBe('');
    expect(budgetBasisLabel({ ...SHIP, coiBudgetPercent: undefined })).toBe('');
  });
});

describe('defaultAmmoLabel', () => {
  /** Four identical chips tell a player less than one grouped line does. */
  it('groups a rack of identical drones', () => {
    expect(defaultAmmoLabel(['TypeI', 'TypeI', 'TypeI', 'TypeI'], TYPES))
      .toBe('4 × TypeI (12dmg, spd 8)');
  });

  /** S2.11: a Y180 rack holds something different from a Y166 one, which is the whole point. */
  it('shows the speed the scenario year fitted', () => {
    expect(defaultAmmoLabel(['TypeIF', 'TypeIF'], TYPES)).toContain('spd 32');
  });

  it('keeps a mixed rack in the order it is held', () => {
    expect(defaultAmmoLabel(['TypeIV', 'TypeI', 'TypeI'], TYPES))
      .toBe('1 × TypeIV (24dmg, spd 8), 2 × TypeI (12dmg, spd 8)');
  });

  /**
   * A drone built in FD10.4's workshop is not in the catalogue, so there is no damage or speed
   * to quote. Its name is the whole truth about it, and a fabricated "(0dmg, spd 0)" would be
   * worse than nothing.
   */
  it('names a composed drone without inventing numbers for it', () => {
    expect(defaultAmmoLabel(['TypeIV+ECM'], TYPES)).toBe('1 × TypeIV+ECM');
  });

  it('has nothing to say about an empty rack', () => {
    expect(defaultAmmoLabel([], TYPES)).toBe('');
  });
});

/**
 * The word "undefined" reached a player's screen once: a Y180 scenario capping drone speed at 20
 * had its racks filled with speed-32 drones the picker had no row for, and every chip read
 * "TypeIF (undefineddmg, spd undefined)". The fitting bug is fixed in core; this is the other
 * half, because a name the catalogue does not list is still a name.
 */
describe('droneChipLabel', () => {
  it('shows the catalogue numbers when it has them', () => {
    expect(droneChipLabel('TypeIF', TYPES)).toBe('TypeIF (12dmg, spd 32)');
  });

  it('never prints the word undefined', () => {
    expect(droneChipLabel('TypeIF', [])).toBe('TypeIF');
    expect(droneChipLabel('TypeIF', [])).not.toContain('undefined');
  });

  /** A drone from FD10.4's workshop has no catalogue row either, and wants the same answer. */
  it('names a composed drone plainly', () => {
    expect(droneChipLabel('TypeIV+ECM', TYPES)).toBe('TypeIV+ECM');
  });
});
