import { describe, expect, it } from 'vitest';
import { edgeLabel, yourSide, type BriefingData, type BriefingSide } from './briefing';

/**
 * The briefing panel's two helpers. Everything else it shows is sent by the server, which is
 * the point: an edge a ship may leave by is a rule, and a rule the client works out for itself
 * is a rule that can disagree with the one being enforced.
 */

function side(over: Partial<BriefingSide> = {}): BriefingSide {
  return {
    name: 'United Federation of Planets',
    faction: 'Federation',
    yours: false,
    exitEdges: ['RIGHT'],
    destructionEdges: ['TOP', 'BOTTOM', 'LEFT'],
    ships: ['USS Xerxes'],
    ...over,
  };
}

function briefing(sides: BriefingSide[]): BriefingData {
  return { name: 'The Stasis Box', description: '', specialRules: [], sides };
}

describe('naming an edge', () => {
  /**
   * SH47.41 prints "the 42xx map edge" and the scenario file says RIGHT. Neither is much use
   * while looking at a map, so the label says both.
   */
  it('gives the side of the screen and the column or row it is', () => {
    expect(edgeLabel('RIGHT', 42, 30)).toBe('right (column 42)');
    expect(edgeLabel('LEFT', 42, 30)).toBe('left (column 1)');
    expect(edgeLabel('TOP', 42, 30)).toBe('top (row 1)');
    expect(edgeLabel('BOTTOM', 42, 30)).toBe('bottom (row 30)');
  });

  /** The numbers come from the map, since not every scenario is 42 × 30. */
  it('reads the numbers off the map rather than assuming the usual size', () => {
    expect(edgeLabel('RIGHT', 20, 15)).toBe('right (column 20)');
    expect(edgeLabel('BOTTOM', 20, 15)).toBe('bottom (row 15)');
  });
});

describe('finding your own side', () => {
  it('picks the side the server marked', () => {
    const mine = side({ name: 'Kzinti Hegemony', yours: true, exitEdges: ['LEFT'] });
    expect(yourSide(briefing([side(), mine]))?.name).toBe('Kzinti Hegemony');
  });

  /** A spectator, or a game whose seats are not assigned: no badge, and no crash. */
  it('returns null when no side is yours', () => {
    expect(yourSide(briefing([side(), side({ name: 'Kzinti Hegemony' })]))).toBeNull();
  });

  it('returns null before the briefing has loaded', () => {
    expect(yourSide(null)).toBeNull();
  });
});
