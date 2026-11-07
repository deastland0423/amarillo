/**
 * Whether an energy allocation is over budget, and the figure to show for it.
 *
 * <h2>The bug this exists to stop</h2>
 * A player allocated two points of warp energy for speed 3, watched the total read "23.0 / 23",
 * and was told they were OVER BUDGET. Both statements came from the same number: the display
 * rounded it with `toFixed(1)` and the comparison did not.
 *
 * The number was 23.0000001. Movement cost is stored per hull as a decimal, and the thirds are
 * truncated — `0.6666667` stands in for 2/3 across 77 hulls, `0.3333333` for 1/3 across 72 — so
 * `speed * moveCost` at speed 3 gives 2.0000001 rather than 2. Core already knew: `hexesBought`
 * reads warp energy with an explicit `+ 1e-6` to undo exactly this.
 *
 * <h2>Why rounding and not a tolerance on the comparison</h2>
 * A tolerance would have left the display and the test reading different values, which is the
 * actual fault — one number, rounded once, cannot disagree with itself. It also sets the
 * tolerance where it belongs: energy in SFB is accounted in units no finer than a quarter or a
 * third, so a ten-thousandth of a point is float noise and never a real overspend.
 *
 * <p>It is not only movement. The allocation total is a sum of twenty-six terms, several of them
 * fractional — transporters at 0.2 a use (not exact in binary either), fractional battery draw,
 * erratic maneuvers — so the error accrues from more than one place and the fix has to sit at
 * the sum rather than on any one term.
 */

/** Finer than any real energy amount, coarser than any accumulated float error. */
const PRECISION = 1e4;

export interface BudgetState {
  /** The spend to display, with float noise removed. */
  shown: number;
  /**
   * The budget it was judged against, carried along so a consumer can render "shown / total"
   * without being handed the raw pair as well — which is how the Hangar drawer came to re-do
   * the comparison itself and then disagree with the bar above it.
   */
  total: number;
  /** Whether it genuinely exceeds the budget. */
  overBudget: boolean;
}

export function budgetState(spent: number, total: number): BudgetState {
  const shown = Math.round(spent * PRECISION) / PRECISION;
  return { shown, total, overBudget: shown > total };
}
