import { describe, it, expect } from 'vitest';
import { coiCost, type CoiCostInputs } from './coiTotal';
import type { CoiDroneModule, CoiDroneType, CoiShipData } from '../api/gameApi';

/**
 * The term that was missing.
 *
 * `coiCost` counted boarding parties, commandos, T-bombs and deck crews and stopped there, so a
 * drone loadout cost nothing on screen while the server priced it and could refuse the whole
 * submission. These are mostly one test said four ways: the drones are in the total.
 */

const TYPES: CoiDroneType[] = [
  { name: 'TypeI', rack: 1, engineCost: 0.50, payloadCost: 0.50, frameCost: 0, speedCredit: 0 },
] as CoiDroneType[];

const MODULES: CoiDroneModule[] = [
  { name: 'SWORDFISH_PHASER3', cost: 1.00, spaces: 1.0, submunitions: 0 },
] as CoiDroneModule[];

const SHIP: CoiShipData = {
  availableDroneTypes:   TYPES,
  availableDroneModules: MODULES,
} as CoiShipData;

function loadout(over: Partial<CoiCostInputs> = {}): CoiCostInputs {
  return {
    extraBoardingParties: 0,
    convertBpToCommando:  0,
    extraCommandoSquads:  0,
    extraTBombs:          0,
    extraDeckCrews:       0,
    droneRackLoadouts:    {},
    ...over,
  };
}

describe('coiCost', () => {
  it('charges nothing for an empty loadout', () => {
    expect(coiCost(loadout(), SHIP)).toBe(0);
  });

  /** S3.2's rates, which were the whole of this function before. */
  it('still counts the items it always counted', () => {
    expect(coiCost(loadout({
      extraBoardingParties: 2,   // 1.0
      convertBpToCommando:  1,   // 0.5
      extraCommandoSquads:  1,   // 1.0
      extraTBombs:          1,   // 4.0
      extraDeckCrews:       2,   // 1.0
    }), SHIP)).toBe(7.5);
  });

  /** The regression: a swordfish drone is half a point (FD10.51) and it has to show up here. */
  it('counts the drone loadout', () => {
    expect(coiCost(loadout({
      droneRackLoadouts: { 0: ['TypeI+SWORDFISH_PHASER3', 'TypeI', 'TypeI', 'TypeI'] },
    }), SHIP)).toBe(0.50);
  });

  it('adds the drones to the other items rather than replacing them', () => {
    expect(coiCost(loadout({
      extraTBombs:       1,
      droneRackLoadouts: { 0: ['TypeI+SWORDFISH_PHASER3'] },
    }), SHIP)).toBe(4.50);
  });

  /** A rack left at its scenario default is not in the loadouts, and the ship paid for it (S2.11). */
  it('charges nothing for a rack the player never built', () => {
    expect(coiCost(loadout({ droneRackLoadouts: { 0: ['TypeI', 'TypeI'] } }), SHIP)).toBe(0);
  });
});
