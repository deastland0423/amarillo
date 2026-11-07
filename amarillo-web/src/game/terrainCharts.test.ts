import { describe, it, expect } from 'vitest';
import { readChart, riskSummary, type TerrainChart, type ChartPosition } from './terrainCharts';

/**
 * The asteroid chart as core serves it (P3.2). Written out here ONLY as a fixture — the real
 * one arrives from the endpoint, which is the whole point of the design.
 */
const ASTEROID: TerrainChart = {
  name: 'Asteroid Collision',
  rule: 'P3.2',
  rows: [
    [0, 0, 0, 0],   // die 1
    [0, 0, 0, 5],   // die 2
    [0, 0, 3, 10],  // die 3
    [0, 2, 6, 15],  // die 4
    [0, 6, 10, 20], // die 5
    [0, 10, 15, 30],// die 6
  ],
};

const at = (speed: number, column: number, dieShift = 0): ChartPosition => ({
  shipName: 'USS Hood', effectiveSpeed: speed, speedColumn: column, dieShift, modifiers: [],
});

describe('readChart', () => {
  it('marks only the column the ship’s effective speed selects', () => {
    const cells = readChart(ASTEROID, at(8, 1));
    expect(cells[5].map(c => c.inYourColumn)).toEqual([false, true, false, false]);
  });

  it('marks nothing when no ship is selected', () => {
    const cells = readChart(ASTEROID, null);
    expect(cells.flat().every(c => !c.inYourColumn)).toBe(true);
  });

  it('shows the chart unshifted for a ship with no modifiers', () => {
    const cells = readChart(ASTEROID, at(8, 1));
    expect(cells.map(r => r[1].damage)).toEqual([0, 0, 0, 2, 6, 10]);
  });

  /**
   * (C11.21) A nimble ship subtracts one from the die, so rolling a 6 reads row 5. The row axis
   * stays "what I rolled" and the damage shown is what that roll costs — otherwise the aid is
   * just the Annex reprinted, and the player still has to do the shift in their head.
   */
  it('folds a nimble ship’s −1 into the damage each roll costs', () => {
    const cells = readChart(ASTEROID, at(8, 1, -1));
    // raw die 1→row 1 (clamped), 2→1, 3→2, 4→3, 5→4, 6→5
    expect(cells.map(r => r[1].damage)).toEqual([0, 0, 0, 0, 2, 6]);
  });

  /** (D6.628) Passive fire control adds one, which is strictly worse at every roll. */
  it('folds a passive ship’s +1 the other way', () => {
    const cells = readChart(ASTEROID, at(8, 1, +1));
    // raw die 1→row 2, 2→3, 3→4, 4→5, 5→6, 6→6 (clamped)
    expect(cells.map(r => r[1].damage)).toEqual([0, 0, 2, 6, 10, 10]);
  });

  /**
   * A shift must not read off the end of the chart. Core clamps to 1–6 before indexing; if this
   * did not, a nimble ship rolling a 1 would index row 0 and a passive ship rolling a 6 would
   * index row 6 — an exception thrown at a player in the middle of their turn.
   */
  it('clamps at both ends rather than running off the chart', () => {
    expect(() => readChart(ASTEROID, at(8, 1, -3))).not.toThrow();
    expect(() => readChart(ASTEROID, at(8, 1, +3))).not.toThrow();
    expect(readChart(ASTEROID, at(8, 1, -3))[0][1].damage).toBe(0);
    expect(readChart(ASTEROID, at(8, 1, +3))[5][1].damage).toBe(10);
  });
});

describe('riskSummary', () => {
  /**
   * The 1–6 column is all zeros, and saying so plainly is the most useful thing on the chart:
   * creeping through an asteroid field is FREE. It is also where the cliff is — one more point
   * of speed moves you to a column where a six costs ten.
   */
  it('says outright that no roll hurts in the slowest column', () => {
    expect(riskSummary(ASTEROID, at(6, 0)))
      .toBe('USS Hood at effective speed 6: no die roll does any damage.');
  });

  it('counts the harmful rolls and the worst case', () => {
    expect(riskSummary(ASTEROID, at(8, 1)))
      .toBe('USS Hood at effective speed 8: 3 of 6 die rolls do damage, up to 10 points.');
  });

  /** And the summary reads the same shifted cells the table does, so the two cannot disagree. */
  it('reflects the die shift', () => {
    expect(riskSummary(ASTEROID, at(8, 1, -1)))
      .toBe('USS Hood at effective speed 8: 2 of 6 die rolls do damage, up to 6 points.');
  });

  it('asks for a ship when none is selected', () => {
    expect(riskSummary(ASTEROID, null)).toMatch(/Select one of your ships/);
  });
});
