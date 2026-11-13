import { describe, expect, it } from 'vitest';
import { shieldStrengthColor } from '../types/gameState';
import { gaugeColour, gauges } from './systemGauge';

/**
 * These gauges replaced a text row that said "fore 4/4 · aft 6/8". Nothing here crashes if it
 * is wrong; it misreports how badly a ship is hurt, which is worse, because the player acts on
 * it and never finds out the panel was the thing that lied.
 */

describe('gauges', () => {
  it('drops a system this hull never had', () => {
    // The AWR case: most hulls have none, and a flat bar captioned "AWR 0/0" would read as a
    // system that has been destroyed rather than one that was never printed.
    expect(gauges(['AWR', 0, 0])).toEqual([]);
    expect(gauges(['AWR', undefined, undefined])).toEqual([]);
    expect(gauges(['AWR', 0, undefined])).toEqual([]);
  });

  it('keeps a printed system that has been shot to nothing', () => {
    // The mirror of the case above, and the reason the max is what decides rather than the
    // count: a destroyed system is the single most important thing the row can report.
    expect(gauges(['impulse', 0, 6])).toEqual([{ caption: 'impulse', now: 0, max: 6 }]);
  });

  it('treats a missing count as zero rather than dropping the row', () => {
    // A field absent from the DTO means "none left", not "no such system" — the max already
    // said the system exists. Reading it as absent would hide exactly the ship in trouble.
    expect(gauges(['APR', undefined, 4])).toEqual([{ caption: 'APR', now: 0, max: 4 }]);
  });

  it('keeps the order given, which is SSD order', () => {
    const out = gauges(['fore', 4, 4], ['aft', 2, 8], ['centre', 0, 0]);
    expect(out.map(g => g.caption)).toEqual(['fore', 'aft']);
  });
});

describe('gaugeColour', () => {
  it('shows a destroyed system loudly, where the shield ramp goes quiet', () => {
    // This is the one deliberate divergence. shieldStrengthColor greys out at zero because a
    // shield at zero is merely down and (D3.3) brings it back; a warp box at zero is gone for
    // the rest of the scenario. If these two ever agree again, the panel has started telling
    // the player their engine room is fine.
    expect(shieldStrengthColor(0, 8)).toBe('#333333');
    expect(gaugeColour(0, 8)).toBe('#f85149');
  });

  it('shares the shield ramp everywhere above zero', () => {
    // Deliberate: a half-wrecked engine room should read the same amber in the panel that a
    // failing shield reads on the map, so the player learns one colour language, not two.
    for (const [now, max] of [[8, 8], [6, 8], [3, 8], [2, 8], [1, 8]] as const)
      expect(gaugeColour(now, max)).toBe(shieldStrengthColor(now, max));
  });

  it('crosses from green to amber to red as the boxes go', () => {
    expect(gaugeColour(8, 8)).toBe('#56d364');   // untouched
    expect(gaugeColour(3, 8)).toBe('#f0c040');   // 37.5%, past the 60% green threshold
    expect(gaugeColour(2, 8)).toBe('#f85149');   // 25%, at the red threshold
    expect(gaugeColour(0, 8)).toBe('#f85149');   // gone
  });
});
