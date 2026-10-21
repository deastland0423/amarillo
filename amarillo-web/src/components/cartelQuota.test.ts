import { describe, expect, it } from 'vitest';
import { describeCartel, describeQuota, quotaOf, tierFor } from './cartelQuota';

describe('describeCartel', () => {
  /**
   * The reason the operating zone is on the label at all: Lion's Heart and Penzance are both
   * Federation cartels, so a home-only label makes them look identical — while one reaches
   * Kzinti space for drones and the other Romulan space for plasma (G15.441).
   */
  it('tells two cartels of the same home empire apart', () => {
    const lionsHeart = { name: "Lion's Heart", home: ['Federation'], operatingZone: ['Kzinti'] };
    const penzance   = { name: 'Penzance',     home: ['Federation'], operatingZone: ['Romulan'] };

    expect(describeCartel(lionsHeart)).toBe("Lion's Heart — Federation + (Kzinti)");
    expect(describeCartel(penzance)).toBe('Penzance — Federation + (Romulan)');
  });

  it('lists a wide operating zone in the order the cartel table gives it', () => {
    expect(describeCartel({
      name: 'Daven', home: ['Klingon'],
      operatingZone: ['Hydran', 'Lyran', 'Kzinti', 'Federation', 'LDR'],
    })).toBe('Daven — Klingon + (Hydran, Lyran, Kzinti, Federation, LDR)');
  });

  /**
   * Dragon's operating zone in the cartel table is its own home. Home access is already
   * unlimited, so repeating it would advertise reach the cartel has not got.
   */
  it('drops an operating zone that only repeats the home empire', () => {
    expect(describeCartel({ name: 'Dragon', home: ['Federation'], operatingZone: ['Federation'] }))
      .toBe('Dragon — Federation');
  });

  it('says nothing extra for a cartel with no operating zone', () => {
    expect(describeCartel({ name: 'Nowhere', home: ['Gorn'], operatingZone: [] }))
      .toBe('Nowhere — Gorn');
  });
});

/**
 * G15.442, the Cluster Cartel's protected shipyard: Kzinti, Lyran and Klingon space count as
 * HOME (90% of weapons), with the other 10% from anywhere. Expressed as four home empires and
 * an EMPTY operating zone, so the ordinary caps — operating <= 20%, outside <= 10% — yield 90/10
 * without a rule of their own. Getting this wrong made a legal Cluster fleet read illegal.
 */
describe('the Cluster Cartel (G15.442)', () => {
  const cluster = { name: 'Cluster', home: ['WYN', 'Kzinti', 'Lyran', 'Klingon'],
                    operatingZone: [] as string[] };
  const pharoh = { name: 'Pharoh', home: ['Federation'], operatingZone: ['Klingon', 'Romulan'] };

  it('treats every one of its home empires as unlimited', () => {
    for (const empire of ['WYN', 'Kzinti', 'Lyran', 'Klingon'])
      expect(tierFor([empire], cluster)).toBe('HOME');
  });

  /** And is not simply permissive: what the rule does not name is still outside. */
  it('still puts an empire it does not name outside', () => {
    expect(tierFor(['Hydran'], cluster)).toBe('OUTSIDE');
  });

  /** The case that used to fail: ten Klingon disruptors across a Cluster force is legal. */
  it('lets a whole force arm from Klingon space', () => {
    const tiers = Array(10).fill(tierFor(['Klingon'], cluster));
    expect(quotaOf(10, tiers, cluster).within).toBe(true);
  });

  /** A multi-empire home does not weaken anyone else's tiering. */
  it('leaves other cartels alone', () => {
    expect(tierFor(['Klingon'], pharoh)).toBe('OPERATING');
    expect(tierFor(['Federation'], pharoh)).toBe('HOME');
  });

  /** It has no operating pool, so the readout must not report one. */
  it('reports no operating-zone figure', () => {
    const q = quotaOf(10, [tierFor(['Hydran'], cluster)], cluster);
    expect(describeQuota(q, cluster)).not.toContain('operating-zone');
    expect(describeQuota(q, pharoh)).toContain('operating-zone');
  });

  /**
    * The picker names the FIRST home empire only. The Cluster's other three are real access
    * (the tier badge says "home" beside a Klingon weapon) but would swamp a list of ten
    * cartels, nine of which have exactly one.
    */
  it('names only its first home empire in the picker', () => {
    expect(describeCartel(cluster)).toBe('Cluster — WYN');
  });
});
