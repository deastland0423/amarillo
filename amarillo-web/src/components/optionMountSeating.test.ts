import { describe, expect, it } from 'vitest';
import { seatAll, windowFor } from './optionMountSeating';

/**
 * The cases are the ones MultiMountOptionTest pins on the server, deliberately: this module
 * mirrors OptionMountLoadout.seatFor, and a mirror is only worth having if it is held to the
 * same examples as the thing it reflects.
 */

/** An Orion CA: two adjacent centerline mounts, two wing mounts that stand alone. */
const CA_RUNS = [['A', 'B'], ['C'], ['D']];
/** The Battle Raider's row of three — the shape that makes this more than a pair test. */
const BR_RUNS = [['A', 'B', 'C']];

const ESG = { name: 'ESG', mountsRequired: 2 };
const PH1 = { name: 'Phaser-1' };

/** Centerline-only options are not offered in a wing mount, as the server's listing reflects. */
const centerlineOnly = (d: string, o: string) => o !== 'ESG' || d === 'A' || d === 'B';
const anywhere = () => true;

describe('windowFor', () => {
  it('seats a two-mount weapon on the pair that contains the chosen mount', () => {
    expect(windowFor('A', ESG, CA_RUNS, new Set(), centerlineOnly)).toEqual(['A', 'B']);
  });

  /** Picking the far half is the same weapon in the same pair, not a refusal. */
  it('seats from the second mount of a pair too', () => {
    expect(windowFor('B', ESG, CA_RUNS, new Set(), centerlineOnly)).toEqual(['A', 'B']);
  });

  /** A lone mount has no neighbour, so there is nowhere to put the second half. */
  it('refuses a two-mount weapon in a mount that stands alone', () => {
    expect(windowFor('C', ESG, CA_RUNS, new Set(), centerlineOnly)).toBeNull();
  });

  /**
   * THE RUN-OF-THREE CASE. A+B and B+C are adjacent; A+C is not, because B lies between them.
   * A test that only showed A+B and C+D working would pass for two separate pairs just as well.
   */
  it('never straddles a gap in a run of three', () => {
    expect(windowFor('A', ESG, BR_RUNS, new Set(), anywhere)).toEqual(['A', 'B']);
    // B is taken, so the only window left for C is B+C — which is blocked, not A+C.
    expect(windowFor('C', ESG, BR_RUNS, new Set(['A', 'B']), anywhere)).toBeNull();
  });

  /** Picking the middle of a free run takes the lower neighbour; core seats lowest start first. */
  it('takes the nearer side when the chosen mount has two free neighbours', () => {
    expect(windowFor('B', ESG, BR_RUNS, new Set(), anywhere)).toEqual(['A', 'B']);
  });

  /** ...and the other side when that one is gone. */
  it('takes the far side when the nearer one is occupied', () => {
    expect(windowFor('B', ESG, BR_RUNS, new Set(['A']), anywhere)).toEqual(['B', 'C']);
  });
});

describe('seatAll', () => {
  const optionFor = (_d: string, name: string) => (name === 'ESG' ? ESG : PH1);

  /** The reason the module exists: B is filled, and the picker has to stop offering it. */
  it('marks the second half of a two-mount weapon as occupied', () => {
    const held = seatAll(CA_RUNS, { A: 'ESG' }, optionFor, centerlineOnly);

    expect(held.get('A')).toEqual({ option: 'ESG', primary: true, chosenAt: 'A' });
    expect(held.get('B')).toEqual({ option: 'ESG', primary: false, chosenAt: 'A' });
    expect(held.has('C')).toBe(false);
  });

  /** A one-mount weapon takes one mount, which is the ordinary path and must stay boring. */
  it('leaves a single-mount weapon in its own mount', () => {
    const held = seatAll(CA_RUNS, { C: 'Phaser-1' }, optionFor, anywhere);

    expect(held.get('C')).toEqual({ option: 'Phaser-1', primary: true, chosenAt: 'C' });
    expect(held.size).toBe(1);
  });

  /**
   * A choice the server would refuse is left out rather than drawn in. Both halves of the CA's
   * only pair are gone, so the second ESG has nowhere to sit.
   */
  it('omits a choice that cannot be seated', () => {
    const held = seatAll(CA_RUNS, { A: 'ESG', B: 'ESG' }, optionFor, centerlineOnly);

    expect(held.get('B')).toEqual({ option: 'ESG', primary: false, chosenAt: 'A' });
    expect([...held.keys()].sort()).toEqual(['A', 'B']);
  });

  /** Seating follows the order the choices were made, as equipDeclaredMounts does. */
  it('seats in choice order, so the first claim wins the shared mount', () => {
    const esgFirst = seatAll(BR_RUNS, { B: 'ESG', A: 'Phaser-1' }, optionFor, anywhere);
    expect(esgFirst.get('A')?.option).toBe('ESG');

    const phaserFirst = seatAll(BR_RUNS, { A: 'Phaser-1', B: 'ESG' }, optionFor, anywhere);
    expect(phaserFirst.get('A')?.option).toBe('Phaser-1');
    expect(phaserFirst.get('C')?.option).toBe('ESG');
  });
});
