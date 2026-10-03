import { describe, it, expect } from 'vitest';
import {
  launchableCraft, parkedCraftNames, hasBalcony, balconyFreeOn, shipHasFreeBalcony,
} from './balcony';
import type { ShipObject, ShuttleBayState, ShuttleInBayState } from '../types/gameState';

/**
 * The balcony's client-side questions (J1.53).
 *
 * The one worth a test is {@link launchableCraft}. A parked craft is deliberately absent from
 * a bay's `shuttles` — that field is the occupied BOXES — so code that reads only `shuttles`
 * loses every parked craft with no error at all: the launch the rules make free and unlimited
 * becomes the one launch the player cannot give. That is a drift guard, not a calculation
 * check, which is why it is pinned here rather than left to the component.
 */

function craft(name: string): ShuttleInBayState {
  return {
    name, type: 'admin', maxSpeed: 6, effectiveMaxSpeed: 6, canLaunch: true,
  } as ShuttleInBayState;
}

function bay(opts: Partial<ShuttleBayState>): ShuttleBayState {
  return {
    bayIndex: 0, canLaunch: true, launchTubeCount: 0, availableTubes: 0,
    totalSpaces: 6, destroyedSpaces: 0, emptySpaces: 0,
    shuttles: [], spaces: [], balconyPositions: 0, balcony: [],
    ...opts,
  } as ShuttleBayState;
}

describe('launchableCraft', () => {
  it('includes the craft parked on a balcony, not only those in boxes', () => {
    const b = bay({
      balconyPositions: 6,
      shuttles: [craft('Inside-1')],
      balcony: [craft('Outside-1'), craft('Outside-2')],
    });

    expect(launchableCraft([b]).map(c => c.name))
      .toEqual(['Inside-1', 'Outside-1', 'Outside-2']);
  });

  it('spans every bay', () => {
    const one = bay({ bayIndex: 0, shuttles: [craft('A')] });
    const two = bay({ bayIndex: 1, balconyPositions: 2, balcony: [craft('B')] });

    expect(launchableCraft([one, two]).map(c => c.name)).toEqual(['A', 'B']);
  });

  it('survives a snapshot with no balcony field at all', () => {
    // An older server build, or a payload in flight across a deploy: balcony is optional on
    // the wire for exactly this reason, and a missing one must read as "none parked".
    const legacy = { ...bay({ shuttles: [craft('A')] }), balcony: undefined };

    expect(launchableCraft([legacy]).map(c => c.name)).toEqual(['A']);
    expect(parkedCraftNames([legacy]).size).toBe(0);
  });

  it('is empty rather than throwing when a ship has no bays', () => {
    expect(launchableCraft(undefined)).toEqual([]);
    expect(launchableCraft([])).toEqual([]);
  });
});

describe('parkedCraftNames', () => {
  it('names only the craft outside', () => {
    const b = bay({
      balconyPositions: 3, shuttles: [craft('Inside-1')], balcony: [craft('Outside-1')],
    });

    const parked = parkedCraftNames([b]);

    expect(parked.has('Outside-1')).toBe(true);
    expect(parked.has('Inside-1')).toBe(false);
  });
});

describe('hasBalcony', () => {
  it('is false for the ordinary ship, which is almost every ship', () => {
    expect(hasBalcony([bay({}), bay({ bayIndex: 1 })])).toBe(false);
  });

  it('is true if any single bay has positions', () => {
    expect(hasBalcony([bay({}), bay({ bayIndex: 1, balconyPositions: 6 })])).toBe(true);
  });
});

describe('balconyFreeOn', () => {
  it('counts the positions still free', () => {
    expect(balconyFreeOn(bay({ balconyPositions: 6, balcony: [craft('A')] }))).toBe(5);
  });

  it('is zero on a full balcony, and never negative', () => {
    const full = bay({ balconyPositions: 1, balcony: [craft('A'), craft('B')] });
    expect(balconyFreeOn(full)).toBe(0);
  });

  it('is zero on a bay with no balcony', () => {
    expect(balconyFreeOn(bay({}))).toBe(0);
  });
});

describe('shipHasFreeBalcony', () => {
  const shipWith = (bays: ShuttleBayState[]) => ({ shuttleBays: bays } as ShipObject);

  it('is true when a position is open somewhere', () => {
    expect(shipHasFreeBalcony(shipWith([
      bay({ balconyPositions: 2, balcony: [craft('A'), craft('B')] }),   // full
      bay({ bayIndex: 1, balconyPositions: 6, balcony: [craft('C')] }),  // room here
    ]))).toBe(true);
  });

  it('is false when every position is taken', () => {
    expect(shipHasFreeBalcony(shipWith([
      bay({ balconyPositions: 1, balcony: [craft('A')] }),
    ]))).toBe(false);
  });

  it('is false for a ship with no balcony, and for no ship', () => {
    expect(shipHasFreeBalcony(shipWith([bay({})]))).toBe(false);
    expect(shipHasFreeBalcony(null)).toBe(false);
  });
});
