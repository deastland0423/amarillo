import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  bearsOn,
  hexGetBearing,
  hexGetRelativeBearing,
  hexRange,
  hexesInArc,
} from './geometry';

/**
 * The drift guard.
 *
 * These functions are a hand port of com.sfb.utilities.MapUtils so the client can preview
 * which weapons bear without a round trip. The fixture below is generated FROM MapUtils
 * and asserted by both tiers — HexGeometryFixtureTest on the Java side, this file here —
 * so neither copy can move without one of the two suites going red.
 *
 * It exists because a mirror that has drifted is worse than no mirror: it looks
 * authoritative. This one drifted once already, to a geometric atan2 that rounded to the
 * wrong direction near the spine lines.
 *
 * Regenerate (only for an intended change to the algorithm) with:
 *   mvn test -pl amarillo-core -Dtest=HexGeometryFixtureTest -Dhex.fixture.regenerate=true
 */
interface RelativeCase {
  trueBearing: number;
  facing: number;
  relative: number;
}

interface FixtureCase {
  fromCol: number;
  fromRow: number;
  toCol: number;
  toRow: number;
  bearing: number;
  range: number;
}

const fixture: { note: string; cases: FixtureCase[]; relativeBearings: RelativeCase[] } = JSON.parse(
  readFileSync(new URL('../../../data/fixtures/hex-geometry.json', import.meta.url), 'utf-8'),
);

describe('hex geometry mirrors MapUtils', () => {
  it('has a fixture worth trusting', () => {
    expect(fixture.cases.length).toBeGreaterThan(500);
    const directions = new Set(fixture.cases.map(c => c.bearing));
    for (let d = 1; d <= 24; d++) expect(directions.has(d)).toBe(true);
  });

  it('agrees with the server on every bearing', () => {
    const wrong: string[] = [];
    for (const c of fixture.cases) {
      const got = hexGetBearing(c.fromCol, c.fromRow, c.toCol, c.toRow);
      if (got !== c.bearing)
        wrong.push(`(${c.fromCol}|${c.fromRow}) -> (${c.toCol}|${c.toRow}): server ${c.bearing}, client ${got}`);
    }
    expect(wrong.slice(0, 10)).toEqual([]);
    expect(wrong).toHaveLength(0);
  });

  it('agrees with the server on every relative bearing', () => {
    // The conversion that decides which way an arc POINTS. The client could get every true
    // bearing right and still draw every arc rotated by a facing.
    expect(fixture.relativeBearings).toHaveLength(24 * 6);
    const wrong: string[] = [];
    for (const r of fixture.relativeBearings) {
      const got = hexGetRelativeBearing(r.trueBearing, r.facing);
      if (got !== r.relative)
        wrong.push(`bearing ${r.trueBearing} from facing ${r.facing}: server ${r.relative}, client ${got}`);
    }
    expect(wrong.slice(0, 10)).toEqual([]);
    expect(wrong).toHaveLength(0);
  });

  it('agrees with the server on every range', () => {
    const wrong: string[] = [];
    for (const c of fixture.cases) {
      const got = hexRange(c.fromCol, c.fromRow, c.toCol, c.toRow);
      if (got !== c.range)
        wrong.push(`(${c.fromCol}|${c.fromRow}) -> (${c.toCol}|${c.toRow}): server ${c.range}, client ${got}`);
    }
    expect(wrong.slice(0, 10)).toEqual([]);
    expect(wrong).toHaveLength(0);
  });
});

/**
 * The arc helpers are new, so they get their own tests rather than riding on the fixture.
 * Arcs are expressed RELATIVE to facing, which is the part that is easy to get backwards.
 */
describe('arcs', () => {
  /** Build a mask from relative directions. */
  const mask = (...dirs: number[]) => dirs.reduce((m, d) => m | (1 << (d - 1)), 0);

  /** FA, the forward arc: relative directions 21-24 and 1-5. */
  const FA = mask(21, 22, 23, 24, 1, 2, 3, 4, 5);
  const ALL = mask(...Array.from({ length: 24 }, (_, i) => i + 1));

  it('reads an arc relative to facing, not absolutely', () => {
    const me = { col: 10, row: 10 };
    const dueNorth = { col: 10, row: 5 };

    // Facing 1 is north, so a contact due north is dead ahead and inside FA.
    expect(bearsOn(me, 1, FA, dueNorth)).toBe(true);
    // Facing 13 is south: the same contact is now astern, and FA cannot reach it.
    expect(bearsOn(me, 13, FA, dueNorth)).toBe(false);
  });

  it('never bears on its own hex', () => {
    const me = { col: 10, row: 10 };
    expect(bearsOn(me, 1, ALL, me)).toBe(false);
  });

  it('a 360 arc covers every hex around it', () => {
    const me = { col: 10, row: 10 };
    const hexes = hexesInArc(me, 1, ALL, 3);
    // Every hex within 3 except the centre: 1 + 3*3*(3+1) = 37 hexes in the disc.
    expect(hexes).toHaveLength(36);
  });

  it('an arc and its facing rotate together', () => {
    const me = { col: 10, row: 10 };
    const ahead = hexesInArc(me, 1, FA, 4).length;
    // The same arc from a different facing covers the same NUMBER of hexes; only their
    // direction changes. A port that dropped the relative-bearing conversion would show
    // the same hexes instead.
    for (const facing of [5, 9, 13, 17, 21])
      expect(hexesInArc(me, facing, FA, 4).length).toBe(ahead);

    const north = hexesInArc(me, 1, FA, 4).map(h => `${h.col}|${h.row}`).sort();
    const south = hexesInArc(me, 13, FA, 4).map(h => `${h.col}|${h.row}`).sort();
    expect(south).not.toEqual(north);
  });

  it('is bounded by the radius it was asked for', () => {
    const me = { col: 10, row: 10 };
    for (const h of hexesInArc(me, 1, ALL, 5))
      expect(hexRange(me.col, me.row, h.col, h.row)).toBeLessThanOrEqual(5);
  });
});
