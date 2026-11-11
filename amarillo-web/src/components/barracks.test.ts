import { describe, it, expect } from 'vitest';
import { partiesLostWithNextBarracksBox, barracksLossLabel } from './barracks';
import type { ShipObject } from '../types/gameState';

/**
 * (G28.32, G28.33) The DAC dialog's price for destroying a barracks box.
 *
 * A drift guard, not a calculation check. This is a MIRROR of
 * `Ship.partiesInTheEmptiestBarracksBox`, and the whole hazard of a mirror is that it fails
 * quietly: the server kills the right marines whatever the dialog says, so a stale mirror only
 * ever shows up as a button quoting a price nobody was charged. This one really did spend a day
 * computing an even spread after the rule had become packing.
 *
 * The cases below are the three branches and the two boundaries between them.
 */

function ship(boxes: number, parties: number): ShipObject {
  return { availableBarracks: boxes, boardingParties: parties } as ShipObject;
}

describe('partiesLostWithNextBarracksBox', () => {
  it('charges nothing while any box stands empty', () => {
    // 15 in three boxes pack 10/5/0 — the owner loses the empty one first.
    expect(partiesLostWithNextBarracksBox(ship(3, 15))).toBe(0);
    // One party in three boxes is 1/0/0: two empties.
    expect(partiesLostWithNextBarracksBox(ship(3, 1))).toBe(0);
  });

  it('charges the part-filled box once the empties are gone', () => {
    // 15 in TWO boxes is 10/5 — no empty left, so the five go.
    expect(partiesLostWithNextBarracksBox(ship(2, 15))).toBe(5);
    // 25 in three boxes is 10/10/5.
    expect(partiesLostWithNextBarracksBox(ship(3, 25))).toBe(5);
    // A single party in a single box is the smallest part-filled box there is.
    expect(partiesLostWithNextBarracksBox(ship(1, 1))).toBe(1);
  });

  it('charges a full ten when every surviving box is full', () => {
    expect(partiesLostWithNextBarracksBox(ship(1, 10))).toBe(10);
    expect(partiesLostWithNextBarracksBox(ship(2, 20))).toBe(10);
    expect(partiesLostWithNextBarracksBox(ship(3, 30))).toBe(10);
  });

  it('ignores the surplus over capacity, which is in no box at all', () => {
    // (G28.32) "Any surplus marine squads are considered to be distributed around the ship."
    // The real Federation CMC: thirty-two parties, three boxes, thirty berths.
    expect(partiesLostWithNextBarracksBox(ship(3, 32))).toBe(10);
    expect(partiesLostWithNextBarracksBox(ship(1, 999))).toBe(10);
  });

  it('charges nothing when there is nothing to charge', () => {
    expect(partiesLostWithNextBarracksBox(ship(0, 20))).toBe(0);
    expect(partiesLostWithNextBarracksBox(ship(3, 0))).toBe(0);
    expect(partiesLostWithNextBarracksBox(null)).toBe(0);
    expect(partiesLostWithNextBarracksBox(undefined)).toBe(0);
  });

  it('reads a missing field as absent rather than as zero parties in a real box', () => {
    // Jackson writes an inapplicable field as `null`, never by omitting the key, so the guard
    // has to be `?? 0` and not `!== undefined`. See CLAUDE.md.
    expect(partiesLostWithNextBarracksBox({} as ShipObject)).toBe(0);
  });
});

describe('barracksLossLabel', () => {
  it('says "empty" when the box costs nobody', () => {
    expect(barracksLossLabel(ship(3, 15))).toBe('empty');
  });

  it('counts and cites the rule', () => {
    expect(barracksLossLabel(ship(3, 30))).toBe('10 boarding parties lost with it (G28.33)');
    expect(barracksLossLabel(ship(2, 15))).toBe('5 boarding parties lost with it (G28.33)');
  });

  it('is singular for one party', () => {
    expect(barracksLossLabel(ship(1, 1))).toBe('1 boarding party lost with it (G28.33)');
  });
});
