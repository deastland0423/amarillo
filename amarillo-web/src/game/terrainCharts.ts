/**
 * The terrain collision charts, as served by the player-aid endpoint.
 *
 * NO NUMBERS LIVE HERE. The charts come from core, because `weaponDamageTables.ts` next door is
 * 485 lines of hand-mirrored Java tables with a standing note to update them whenever a weapon
 * changes — and two mine numbers that disagreed with the rulebook turned up in a single playtest
 * on 2026-11-07. This file holds only the reading of the chart, which is what the view needs and
 * what can be tested without a server.
 */

export interface TerrainChart {
  name: string;   // "Asteroid Collision"
  rule: string;   // "P3.2"
  rows: number[][];  // [die - 1][speed column] = damage points
}

/** Where the viewer's own ship sits, when the request named one. */
export interface ChartPosition {
  shipName: string;
  effectiveSpeed: number;
  speedColumn: number;
  dieShift: number;
  modifiers: string[];
}

export interface TerrainChartData {
  speedColumns: string[];
  charts: TerrainChart[];
  you?: ChartPosition | null;
}

/**
 * What one cell should look like: the damage, and whether it is the one this ship would read.
 *
 * <p>The highlight is the whole point of showing a chart in the app rather than leaving the
 * player to the Annex. Paper cannot know that you are at effective speed 8 on passive fire
 * control; this can.
 */
export interface Cell {
  damage: number;
  /**
   * In the column this ship's effective speed selects.
   *
   * There is no separate "this is your outcome" flag, and there was one in the first draft:
   * because the die shift is folded into `damage`, every cell in your column already IS your
   * outcome for that roll. Two fields that are always equal is an API that invites a reader to
   * look for a difference.
   */
  inYourColumn: boolean;
}

/**
 * Read a chart for display, marking the viewer's column and the row each raw die maps to.
 *
 * <p>The row marking is the subtle half. A nimble ship subtracts one from the die (C11.21), so
 * when it rolls a 4 it reads row 3 — the player wants to see the outcome OF THEIR ROLL, which
 * means the marked cell for raw die 4 sits on row 3. Rows are returned in raw-die order so the
 * left-hand axis is still "what I rolled", with the shift folded into which cell lights up.
 *
 * <p>Shifts cannot push the reading off either end: core clamps to 1-6 before indexing, and so
 * does this, or a nimble ship rolling a 1 would read row 0 and a passive ship rolling a 6 would
 * read row 6. Both would be an exception thrown at a player mid-turn.
 */
export function readChart(chart: TerrainChart, you?: ChartPosition | null): Cell[][] {
  const rowCount = chart.rows.length;
  return chart.rows.map((_row, dieIndex) => {
    const rawDie = dieIndex + 1;
    const shifted = you
      ? Math.max(1, Math.min(rowCount, rawDie + you.dieShift))
      : rawDie;
    return chart.rows[dieIndex].map((_damage, column) => ({
      // The DAMAGE shown for a raw die is the shifted row's damage: that is what this ship
      // suffers when it rolls this number. Showing the unshifted figure would be printing the
      // Annex and calling it an aid.
      damage: chart.rows[shifted - 1][column],
      inYourColumn: !!you && you.speedColumn === column,
    }));
  });
}

/**
 * The plain-language summary above a chart: what this ship is risking right now.
 *
 * <p>Separate from the chart so the number and the table cannot disagree — it reads the same
 * cells {@link readChart} marks.
 */
export function riskSummary(chart: TerrainChart, you?: ChartPosition | null): string {
  if (!you)
    return 'Select one of your ships to see where it sits on the chart.';
  const cells = readChart(chart, you);
  const outcomes = cells.map(row => row[you.speedColumn]?.damage ?? 0);
  const worst = Math.max(...outcomes);
  const harmful = outcomes.filter(d => d > 0).length;

  if (worst === 0)
    return `${you.shipName} at effective speed ${you.effectiveSpeed}: no die roll does any damage.`;
  return `${you.shipName} at effective speed ${you.effectiveSpeed}: `
    + `${harmful} of ${outcomes.length} die rolls do damage, up to ${worst} points.`;
}
