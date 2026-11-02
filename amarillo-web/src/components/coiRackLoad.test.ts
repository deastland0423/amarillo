import { describe, it, expect } from 'vitest';
import { withRackLoad, withoutRack, withPreset } from './coiRackLoad';

/**
 * Core's rule, from the other end: "If a rack index is absent, the rack keeps its default
 * ammo." So the difference between an absent key and an empty array is the difference between a
 * full rack and an empty one, and the dialog's "Back to standard" button depends on getting it
 * right.
 */
describe('withRackLoad', () => {
  it('sets one rack and leaves the others alone', () => {
    const before = { 0: ['TypeI'], 2: ['TypeIV'] };

    expect(withRackLoad(before, 0, ['TypeIF', 'TypeIF']))
      .toEqual({ 0: ['TypeIF', 'TypeIF'], 2: ['TypeIV'] });
  });

  it('copies the list rather than aliasing the caller\'s', () => {
    const source = ['TypeIF'];
    const after = withRackLoad({}, 1, source);
    source.push('TypeI');

    expect(after[1]).toEqual(['TypeIF']);
  });

  it('does not mutate what it was given', () => {
    const before = { 0: ['TypeI'] };
    withRackLoad(before, 0, ['TypeIF']);

    expect(before).toEqual({ 0: ['TypeI'] });
  });
});

describe('withoutRack', () => {
  /** The one that matters: the key goes away, so the ship keeps the drones it came with. */
  it('removes the key rather than emptying it', () => {
    const after = withoutRack({ 0: ['TypeI'], 1: ['TypeIV'] }, 0);

    expect(0 in after).toBe(false);
    expect(after).toEqual({ 1: ['TypeIV'] });
  });

  it('is not the same as loading the rack with nothing', () => {
    const emptied = withRackLoad({ 0: ['TypeI'] }, 0, []);
    const forgotten = withoutRack({ 0: ['TypeI'] }, 0);

    expect(emptied).toEqual({ 0: [] });
    expect(forgotten).not.toEqual(emptied);
  });

  it('tolerates a rack that was never touched', () => {
    expect(withoutRack({ 1: ['TypeI'] }, 0)).toEqual({ 1: ['TypeI'] });
  });

  it('does not mutate what it was given', () => {
    const before = { 0: ['TypeI'] };
    withoutRack(before, 0);

    expect(before).toEqual({ 0: ['TypeI'] });
  });
});

/**
 * FD3.70 has drones and anti-drone rounds sharing one magazine, so a preset describes the whole
 * of it and both maps have to move together. Leaving an old ADD count beside new drones is how a
 * magazine ends up holding more than it has room for, and the refusal that follows says nothing
 * about which half was at fault.
 */
describe('withPreset', () => {
  const state = {
    droneRackLoadouts: { 0: ['TypeI'], 1: ['TypeIV'] },
    antiDroneLoadouts: { 0: 4, 1: 2 },
  };

  it('replaces the drones and the anti-drones of one rack together', () => {
    expect(withPreset(state, 0, { drones: ['TypeIF', 'TypeIF'], antiDrones: 0 })).toEqual({
      droneRackLoadouts: { 0: ['TypeIF', 'TypeIF'], 1: ['TypeIV'] },
      antiDroneLoadouts: { 1: 2 },
    });
  });

  /** Zero rounds is the absence of a choice, so the key goes rather than holding a 0. */
  it('removes the anti-drone entry rather than storing a zero', () => {
    const after = withPreset(state, 1, { drones: ['TypeI'], antiDrones: 0 });
    expect(Object.prototype.hasOwnProperty.call(after.antiDroneLoadouts, 1)).toBe(false);
  });

  /**
   * An anti-drone-only load sets the drones to [], which is NOT the same as deleting the key:
   * absent means "keep what the ship came with" (S2.11) and [] means "sail with none". A rack
   * full of ADDs has deliberately no drones in it.
   */
  it('empties the drones rather than forgetting the rack, for an ADD-only load', () => {
    const after = withPreset(state, 0, { drones: [], antiDrones: 8 });
    expect(after.droneRackLoadouts[0]).toEqual([]);
    expect(after.antiDroneLoadouts[0]).toBe(8);
  });

  it('leaves the other racks alone', () => {
    const after = withPreset(state, 0, { drones: ['TypeIF'], antiDrones: 0 });
    expect(after.droneRackLoadouts[1]).toEqual(['TypeIV']);
    expect(after.antiDroneLoadouts[1]).toBe(2);
  });

  it('does not mutate what it was given', () => {
    withPreset(state, 0, { drones: ['TypeIF'], antiDrones: 8 });
    expect(state.droneRackLoadouts[0]).toEqual(['TypeI']);
    expect(state.antiDroneLoadouts[0]).toBe(4);
  });
});
