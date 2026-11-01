import { describe, it, expect } from 'vitest';
import { withRackLoad, withoutRack } from './coiRackLoad';

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
