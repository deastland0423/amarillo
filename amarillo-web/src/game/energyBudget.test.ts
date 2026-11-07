import { describe, it, expect } from 'vitest';
import { budgetState } from './energyBudget';

describe('budgetState', () => {
  /**
   * The playtest case, reproduced with the real numbers.
   *
   * Movement cost is stored as a truncated decimal: `0.6666667` for 2/3 across 77 hulls. So a
   * ship buying speed 3 pays `3 * 0.6666667 = 2.0000001`, the turn's total comes to 23.0000001,
   * and a strict `spent > total` called that over budget — while `toFixed(1)` printed "23.0".
   * Both statements came from the same number, read two different ways.
   */
  it('does not call a float artefact an overspend', () => {
    const mv = 3 * 0.6666667;          // the real data value, not a rounded stand-in
    const state = budgetState(21 + mv, 23);

    expect(mv).toBeGreaterThan(2);      // the artefact is real...
    expect(state.overBudget).toBe(false); // ...and must not cost the player a point
    expect(state.shown).toBe(23);
  });

  /**
   * The worst case in the data, and the reason this rounds rather than adding a tolerance of
   * 1e-6: at speed 30 the same hull's error reaches exactly 1e-6, which a 1e-6 tolerance sits
   * right on the boundary of.
   */
  it('holds at the largest error the data can produce', () => {
    const mv = 30 * 0.6666667;          // 20.000001
    expect(mv - 20).toBeCloseTo(1e-6, 12);
    expect(budgetState(mv, 20).overBudget).toBe(false);
    expect(budgetState(mv, 20).shown).toBe(20);
  });

  /** A third the other way is slightly UNDER, which must not become an overspend either. */
  it('handles the thirds that round down', () => {
    expect(budgetState(3 * 0.3333333, 1).overBudget).toBe(false);
    expect(budgetState(3 * 0.3333333, 1).shown).toBe(1);
  });

  /** A real overspend is still a real overspend — the point is not to be permissive. */
  it('still catches going over', () => {
    expect(budgetState(23.5, 23).overBudget).toBe(true);
    expect(budgetState(24, 23).overBudget).toBe(true);
  });

  /**
   * Including by the smallest amount the game can actually spend. A transporter use is 0.2 of a
   * point, and 0.2 is not exact in binary either — so the tolerance has to be far below a real
   * fraction while still above the noise.
   */
  it('catches an overspend of one transporter use', () => {
    expect(budgetState(23 + 0.2, 23).overBudget).toBe(true);
  });

  /** Exactly on budget is not over it. */
  it('treats an exact spend as within budget', () => {
    expect(budgetState(23, 23).overBudget).toBe(false);
    expect(budgetState(0, 0).overBudget).toBe(false);
  });

  /** Under budget, and the displayed figure is the rounded one. */
  it('reports the rounded figure for display', () => {
    expect(budgetState(22.0000000003, 23).shown).toBe(22);
    expect(budgetState(22.25, 23).shown).toBe(22.25);
  });

  /**
   * The display and the verdict must come from ONE value. That is the whole design: the bug was
   * two readings of the same number disagreeing, so the figure shown is the figure tested.
   */
  it('shows the number it judged', () => {
    const state = budgetState(23.0000001, 23);
    expect(state.shown).toBe(23);
    expect(state.shown > 23).toBe(state.overBudget);
  });
});
