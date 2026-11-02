import { describe, it, expect } from 'vitest';
import { coiInitialFor, withCoiApplied } from './fleetCoiMapping';
import type { CoiSubmission, FleetShipEntry } from '../api/gameApi';

/**
 * The join between a fleet's ships and a dialog that knows them by name.
 *
 * The resolver renames: an entry with no name takes the ship file's own, and the second ship of a
 * type gets " #2" so a violation can point at one of them. So the names the dialog answers with
 * are not the names in the spec, and matching on them loses purchases — which is why these two
 * functions match on position, and why that is worth a test rather than a comment.
 */

const CHOICES = { extraTBombs: 2 };
const OTHER   = { extraBoardingParties: 4 };

describe('coiInitialFor', () => {
  it('keys each ship by the name the dialog will use', () => {
    const ships: FleetShipEntry[] = [
      { type: 'CS', name: 'KHS One', coi: CHOICES },
      { type: 'FF', name: 'KHS Two', coi: OTHER },
    ];

    expect(coiInitialFor(ships, ['KHS One', 'KHS Two']))
      .toEqual({ 'KHS One': CHOICES, 'KHS Two': OTHER });
  });

  /** The resolver's name, not the entry's: an unnamed ship still has its purchases shown. */
  it('uses the resolved name even when the entry has none', () => {
    const ships: FleetShipEntry[] = [{ type: 'CS', coi: CHOICES }];

    expect(coiInitialFor(ships, ['KZN CS'])).toEqual({ 'KZN CS': CHOICES });
  });

  /** Two ships of one type are told apart by the suffix the resolver added. */
  it('keeps a pair of identical hulls apart', () => {
    const ships: FleetShipEntry[] = [
      { type: 'CS', name: 'KHS Pair', coi: CHOICES },
      { type: 'CS', name: 'KHS Pair', coi: OTHER },
    ];

    expect(coiInitialFor(ships, ['KHS Pair', 'KHS Pair #2']))
      .toEqual({ 'KHS Pair': CHOICES, 'KHS Pair #2': OTHER });
  });

  it('says nothing about a ship that has bought nothing', () => {
    expect(coiInitialFor([{ type: 'CS', name: 'KHS Bare' }], ['KHS Bare'])).toEqual({});
  });
});

describe('withCoiApplied', () => {
  const ships: FleetShipEntry[] = [
    { type: 'CS', name: 'KHS Pair' },
    { type: 'CS', name: 'KHS Pair' },
  ];
  const names = ['KHS Pair', 'KHS Pair #2'];

  it('records each answer against the ship that gave it', () => {
    const submission: CoiSubmission = { 'KHS Pair': CHOICES, 'KHS Pair #2': OTHER };

    expect(withCoiApplied(ships, names, submission)).toEqual([
      { type: 'CS', name: 'KHS Pair', coi: CHOICES },
      { type: 'CS', name: 'KHS Pair', coi: OTHER },
    ]);
  });

  /**
   * An ABSENT block means "leave the ship with the drones its year fits" (S2.11). An empty one
   * would be read as a purchase of nothing, so a ship that bought nothing must have no block.
   */
  it('removes the block rather than storing an empty one', () => {
    const before: FleetShipEntry[] = [{ type: 'CS', name: 'KHS Pair', coi: CHOICES }];
    const after = withCoiApplied(before, ['KHS Pair'], { 'KHS Pair': {} });

    expect(after[0]).toEqual({ type: 'CS', name: 'KHS Pair' });
    expect('coi' in after[0]).toBe(false);
  });

  it('removes the block when the dialog says nothing about the ship at all', () => {
    const before: FleetShipEntry[] = [{ type: 'CS', name: 'KHS Pair', coi: CHOICES }];

    expect('coi' in withCoiApplied(before, ['KHS Pair'], {})[0]).toBe(false);
  });

  it('leaves everything else on the entry alone', () => {
    const before: FleetShipEntry[] = [{
      type: 'CR', name: 'USS Keep', faction: 'Federation',
      refits: ['+'], optionMounts: { A: 'ESG' },
    }];
    const after = withCoiApplied(before, ['USS Keep'], { 'USS Keep': CHOICES });

    expect(after[0].refits).toEqual(['+']);
    expect(after[0].optionMounts).toEqual({ A: 'ESG' });
    expect(after[0].faction).toBe('Federation');
  });

  it('does not mutate what it was given', () => {
    const before: FleetShipEntry[] = [{ type: 'CS', name: 'KHS Pair', coi: CHOICES }];
    withCoiApplied(before, ['KHS Pair'], { 'KHS Pair': OTHER });

    expect(before[0].coi).toBe(CHOICES);
  });
});
