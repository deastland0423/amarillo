import { describe, it, expect } from 'vitest';
import { defaultShipCoi, seedShipCoi } from './coiShipState';
import type { CoiShipChoices } from '../api/gameApi';

/**
 * A purchase that goes through the editor has to come back the same purchase.
 *
 * This matters most in the fleet builder, where a saved fleet's options are reopened for editing:
 * a field the seed forgets is a purchase the player silently loses the next time they touch the
 * dialog. Two of the fields change shape on the way through — shuttle preparations are a list on
 * the wire and a map in here, rack indexes are strings in JSON and numbers in here — so this is
 * not a copy and cannot be checked by eye.
 */

describe('seedShipCoi', () => {
  it('starts empty when nothing was bought', () => {
    expect(seedShipCoi()).toEqual(defaultShipCoi());
    expect(seedShipCoi(undefined)).toEqual(defaultShipCoi());
  });

  it('carries the simple counts across', () => {
    const saved: CoiShipChoices = {
      extraBoardingParties: 3,
      convertBpToCommando:  1,
      extraCommandoSquads:  2,
      extraTBombs:          4,
      extraDeckCrews:       1,
    };
    const seeded = seedShipCoi(saved);

    expect(seeded.extraBoardingParties).toBe(3);
    expect(seeded.convertBpToCommando).toBe(1);
    expect(seeded.extraCommandoSquads).toBe(2);
    expect(seeded.extraTBombs).toBe(4);
    expect(seeded.extraDeckCrews).toBe(1);
  });

  /** JSON object keys are strings; the editor indexes racks by number. */
  it('turns the rack keys back into numbers', () => {
    const seeded = seedShipCoi({
      droneRackLoadouts: { '0': ['TypeIF', 'TypeIF'], '2': ['TypeIVF'] },
      antiDroneLoadouts: { '1': 8 },
    });

    expect(seeded.droneRackLoadouts[0]).toEqual(['TypeIF', 'TypeIF']);
    expect(seeded.droneRackLoadouts[2]).toEqual(['TypeIVF']);
    expect(seeded.antiDroneLoadouts[1]).toBe(8);
  });

  /** FD3.70's rounds and the drones share one magazine, so both halves must survive. */
  it('keeps a magazine of anti-drones with no drones in it', () => {
    const seeded = seedShipCoi({
      droneRackLoadouts: { '0': [] },
      antiDroneLoadouts: { '0': 8 },
    });

    expect(seeded.droneRackLoadouts[0]).toEqual([]);
    expect(seeded.antiDroneLoadouts[0]).toBe(8);
  });

  /** A list on the wire, each entry naming its shuttle; a map keyed by that name in here. */
  it('turns the shuttle preparations back into a map', () => {
    const seeded = seedShipCoi({
      specialShuttlePrep: [
        { shuttleName: 'Shuttle 1', type: 'suicide', energyPerTurn: 2, drones: [] },
        { shuttleName: 'Shuttle 2', type: 'scatterpack', energyPerTurn: 3,
          drones: ['TypeIF', 'TypeIF'] },
      ],
    });

    expect(seeded.shuttlePrep['Shuttle 1'])
      .toEqual({ type: 'suicide', energyPerTurn: 2, drones: [] });
    expect(seeded.shuttlePrep['Shuttle 2'])
      .toEqual({ type: 'scatterpack', energyPerTurn: 3, drones: ['TypeIF', 'TypeIF'] });
  });

  it('keeps the arming modes, the overloads and the option mounts', () => {
    const seeded = seedShipCoi({
      weaponArmingModes: { '1': 'OVERLOAD' },
      photonOverload:    { '1': 2 },
      optionMounts:      { A: 'ESG' },
      optionMountArcs:   { A: 'OUTBOARD' },
      fighterFacilityType: 'Stinger-1',
      armedFighters:     ['Stinger2-1'],
    });

    expect(seeded.weaponArmingModes).toEqual({ '1': 'OVERLOAD' });
    expect(seeded.photonOverload).toEqual({ '1': 2 });
    expect(seeded.optionMounts).toEqual({ A: 'ESG' });
    expect(seeded.optionMountArcs).toEqual({ A: 'OUTBOARD' });
    expect(seeded.fighterFacilityType).toBe('Stinger-1');
    expect(seeded.armedFighters).toEqual(['Stinger2-1']);
  });

  it('does not alias the saved arrays and maps it was handed', () => {
    const saved: CoiShipChoices = {
      armedFighters:     ['Stinger2-1'],
      optionMounts:      { A: 'ESG' },
      droneRackLoadouts: { '0': ['TypeIF'] },
    };
    const seeded = seedShipCoi(saved);

    seeded.armedFighters.push('Stinger2-2');
    seeded.optionMounts.B = 'Drone rack';
    seeded.droneRackLoadouts[0].push('TypeIF');

    expect(saved.armedFighters).toEqual(['Stinger2-1']);
    expect(saved.optionMounts).toEqual({ A: 'ESG' });
    expect(saved.droneRackLoadouts).toEqual({ '0': ['TypeIF'] });
  });
});
