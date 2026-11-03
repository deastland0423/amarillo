import { describe, it, expect } from 'vitest';
import { activeCoiView, coiInitialFor, fleetCoiKey, withCoiApplied } from './fleetCoiMapping';
import type { CoiView } from './fleetCoiMapping';
import type { CoiSideData, CoiSubmission, FleetShipEntry, FleetSpec } from '../api/gameApi';

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


/**
 * Which fleet an options payload belongs to.
 *
 * Reported from live play, 2026-11-02: adjust the Kzinti fleet's options, save it, load Task
 * Force Optimus, and its Options view is the Kzinti fleet. Nothing on the way back through the
 * fleet list cleared the payload or the resolved names, so the dialog reopened on the force it
 * had finished with — and since the join is POSITIONAL, submitting it would have written one
 * fleet's purchases onto another fleet's ships.
 */
describe('fleetCoiKey', () => {
  const kzinti: FleetSpec = {
    id: 'kzinti-musketeers', name: 'Musketeers', factions: ['Kzinti'],
    year: 180, budget: 300,
    ships: [{ faction: 'Kzinti', type: 'CS' }, { faction: 'Kzinti', type: 'FF' }],
  };

  it('tells two saved fleets apart', () => {
    const fed: FleetSpec = { ...kzinti, id: 'task-force-optimus', name: 'Optimus' };
    expect(fleetCoiKey(fed)).not.toBe(fleetCoiKey(kzinti));
  });

  /** The year decides availability and price (S8.131, S8.133), so the payload moves with it. */
  it('changes with the year', () => {
    expect(fleetCoiKey({ ...kzinti, year: 175 })).not.toBe(fleetCoiKey(kzinti));
  });

  it('changes when a ship is bought or sold', () => {
    expect(fleetCoiKey({ ...kzinti, ships: [kzinti.ships[0]] })).not.toBe(fleetCoiKey(kzinti));
  });

  /** Positional join, so the ORDER is part of the identity even with the same two hulls. */
  it('changes when the ships are reordered', () => {
    const swapped = { ...kzinti, ships: [kzinti.ships[1], kzinti.ships[0]] };
    expect(fleetCoiKey(swapped)).not.toBe(fleetCoiKey(kzinti));
  });

  /** A refit can change the racks the dialog offers — a type-A becomes a type-B in Y175. */
  it('changes when a refit is ticked', () => {
    const refitted = { ...kzinti, ships: [{ ...kzinti.ships[0], refits: ['+'] }, kzinti.ships[1]] };
    expect(fleetCoiKey(refitted)).not.toBe(fleetCoiKey(kzinti));
  });

  /** Renaming a ship moves the name the dialog answers with, which is the join's other half. */
  it('changes when a ship is renamed', () => {
    const renamed = { ...kzinti, ships: [{ ...kzinti.ships[0], name: 'KHS Claw' }, kzinti.ships[1]] };
    expect(fleetCoiKey(renamed)).not.toBe(fleetCoiKey(kzinti));
  });

  /**
   * The purchases themselves are NOT in the key. Keying on them would have the act of
   * submitting the dialog invalidate the payload that submitted it.
   */
  it('does not change when a ship records what it bought', () => {
    const bought = { ...kzinti, ships: [{ ...kzinti.ships[0], coi: CHOICES }, kzinti.ships[1]] };
    expect(fleetCoiKey(bought)).toBe(fleetCoiKey(kzinti));
  });

  it('is stable for the same fleet', () => {
    expect(fleetCoiKey({ ...kzinti })).toBe(fleetCoiKey(kzinti));
  });
});

describe('activeCoiView', () => {
  const SIDES = [{ side: 'Kzinti', ships: [] }] as unknown as CoiSideData[];

  const kzinti: FleetSpec = {
    id: 'kzinti-musketeers', name: 'Musketeers', factions: ['Kzinti'],
    year: 180, budget: 300, ships: [{ faction: 'Kzinti', type: 'CS' }],
  };
  const view: CoiView = {
    forFleet: fleetCoiKey(kzinti), sides: SIDES, names: ['KHS Claw'],
  };

  it('keeps the dialog open on the fleet it was fetched for', () => {
    expect(activeCoiView(view, kzinti)).toBe(view);
  });

  /** The reported bug, as reported. */
  it('shuts the dialog when another fleet is loaded', () => {
    const fed: FleetSpec = {
      id: 'task-force-optimus', name: 'Optimus', factions: ['Federation'],
      year: 180, budget: 300, ships: [{ faction: 'Federation', type: 'CA' }],
    };
    expect(activeCoiView(view, fed)).toBeNull();
  });

  /** And when the same fleet is edited underneath it. */
  it('shuts the dialog when the fleet changes underneath it', () => {
    expect(activeCoiView(view, { ...kzinti, year: 175 })).toBeNull();
  });

  it('survives the fleet recording what the dialog bought', () => {
    const bought = { ...kzinti, ships: [{ ...kzinti.ships[0], coi: CHOICES }] };
    expect(activeCoiView(view, bought)).toBe(view);
  });

  it('is null when nothing is open', () => {
    expect(activeCoiView(null, kzinti)).toBeNull();
  });
});
