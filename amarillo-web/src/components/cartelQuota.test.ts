import { describe, expect, it } from 'vitest';
import { describeCartel } from './cartelQuota';

describe('describeCartel', () => {
  /**
   * The reason the operating zone is on the label at all: Lion's Heart and Penzance are both
   * Federation cartels, so a home-only label makes them look identical — while one reaches
   * Kzinti space for drones and the other Romulan space for plasma (G15.441).
   */
  it('tells two cartels of the same home empire apart', () => {
    const lionsHeart = { name: "Lion's Heart", home: 'Federation', operatingZone: ['Kzinti'] };
    const penzance   = { name: 'Penzance',     home: 'Federation', operatingZone: ['Romulan'] };

    expect(describeCartel(lionsHeart)).toBe("Lion's Heart — Federation + (Kzinti)");
    expect(describeCartel(penzance)).toBe('Penzance — Federation + (Romulan)');
  });

  it('lists a wide operating zone in the order the cartel table gives it', () => {
    expect(describeCartel({
      name: 'Daven', home: 'Klingon',
      operatingZone: ['Hydran', 'Lyran', 'Kzinti', 'Federation', 'LDR'],
    })).toBe('Daven — Klingon + (Hydran, Lyran, Kzinti, Federation, LDR)');
  });

  /**
   * Dragon's operating zone in the cartel table is its own home. Home access is already
   * unlimited, so repeating it would advertise reach the cartel has not got.
   */
  it('drops an operating zone that only repeats the home empire', () => {
    expect(describeCartel({ name: 'Dragon', home: 'Federation', operatingZone: ['Federation'] }))
      .toBe('Dragon — Federation');
  });

  it('says nothing extra for a cartel with no operating zone', () => {
    expect(describeCartel({ name: 'Nowhere', home: 'Gorn', operatingZone: [] }))
      .toBe('Nowhere — Gorn');
  });
});
