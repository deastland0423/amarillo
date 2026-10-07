import { describe, expect, it } from 'vitest';
import type { DroneRackState, ReloadPoolEntry } from '../types/gameState';
import { ANTI_DRONE_KEY, claimedByOtherRacks, reloadEntriesFor } from './EnergyAllocationDialog';

const entry = (droneType: string, count: number, rackSize = 1): ReloadPoolEntry =>
  ({ droneType, count, rackSize });

/** Only the field these helpers read. */
const rack = (pool: ReloadPoolEntry[]) => ({ reloadPool: pool }) as Pick<DroneRackState, 'reloadPool'>;

describe('reloadEntriesFor', () => {
  /**
   * The ship's drones and the rack's anti-drones are owned at different levels and spent against
   * the same two spaces (FD2.42), so the picker needs them in one list.
   */
  it('puts the ship stockpile and the rack reserve together', () => {
    const ship = { reloadPool: [entry('TYPE_I', 4), entry('TYPE_IV', 2)] };
    const got = reloadEntriesFor(ship, rack([entry(ANTI_DRONE_KEY, 8, 0.5)]));
    expect(got.map(e => e.droneType)).toEqual(['TYPE_I', 'TYPE_IV', 'ANTI_DRONE']);
  });

  it('copes with either side being absent', () => {
    expect(reloadEntriesFor({}, undefined)).toEqual([]);
    expect(reloadEntriesFor({ reloadPool: [entry('TYPE_I', 1)] }, undefined)).toHaveLength(1);
    expect(reloadEntriesFor({}, rack([entry(ANTI_DRONE_KEY, 2, 0.5)]))).toHaveLength(1);
  });

  /**
   * A server that sends `null` rather than omitting the key: Jackson serialises null as
   * `"reloadPool": null`, so `?? []` has to carry it, and `!== undefined` would not.
   */
  it('treats a null pool as empty', () => {
    const ship = { reloadPool: null as unknown as ReloadPoolEntry[] };
    expect(reloadEntriesFor(ship, rack(null as unknown as ReloadPoolEntry[]))).toEqual([]);
  });
});

describe('claimedByOtherRacks', () => {
  /**
   * The bug the ship-level pool introduced and this closes. One stockpile, several racks: without
   * subtracting what the other racks asked for, the dialog would offer the same four drones to each
   * rack, and the server — whose take() is authoritative — would give them to whichever it handled
   * first and leave the rest short of what the screen promised.
   */
  it('counts what the other racks have asked for', () => {
    const selections = {
      'Drone-Rack 1': { TYPE_I: 2 },
      'Drone-Rack 2': { TYPE_I: 1, TYPE_IV: 1 },
      'Drone-Rack 3': { TYPE_I: 1 },
    };
    expect(claimedByOtherRacks(selections, 'Drone-Rack 1', 'TYPE_I')).toBe(2);
    expect(claimedByOtherRacks(selections, 'Drone-Rack 2', 'TYPE_I')).toBe(3);
    expect(claimedByOtherRacks(selections, 'Drone-Rack 9', 'TYPE_I')).toBe(4);
  });

  it('ignores a rack own claim and types it is not asked about', () => {
    const selections = { 'Drone-Rack 1': { TYPE_I: 3 } };
    expect(claimedByOtherRacks(selections, 'Drone-Rack 1', 'TYPE_I')).toBe(0);
    expect(claimedByOtherRacks(selections, 'Drone-Rack 2', 'TYPE_VI')).toBe(0);
    expect(claimedByOtherRacks({}, 'Drone-Rack 1', 'TYPE_I')).toBe(0);
  });

  /**
   * Anti-drones are the exception, and the reason the exception is needed is that they share a key.
   * FD3.72 gives EACH type-G its own entirely-anti-drone reload set, and it can only be loaded into
   * the rack that holds it — so two type-Gs each asking for four are not competing, and subtracting
   * one from the other would halve a legal loading.
   */
  it('leaves each rack its own anti-drone reserve', () => {
    const selections = {
      'Drone-Rack 1': { [ANTI_DRONE_KEY]: 4 },
      'Drone-Rack 2': { [ANTI_DRONE_KEY]: 4 },
    };
    expect(claimedByOtherRacks(selections, 'Drone-Rack 1', ANTI_DRONE_KEY)).toBe(0);
    expect(claimedByOtherRacks(selections, 'Drone-Rack 2', ANTI_DRONE_KEY)).toBe(0);
  });

  /**
   * What the picker actually computes, written out once: a four-drone pile with three already
   * spoken for leaves one, and a pile nobody has touched leaves all of it.
   */
  it('leaves the right number available', () => {
    const pool = entry('TYPE_I', 4);
    const selections = { 'Drone-Rack 2': { TYPE_I: 3 } };
    const available = (rackName: string) =>
      Math.max(0, pool.count - claimedByOtherRacks(selections, rackName, pool.droneType));
    expect(available('Drone-Rack 1')).toBe(1);
    expect(available('Drone-Rack 2')).toBe(4);
  });
});
