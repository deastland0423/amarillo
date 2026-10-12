import { describe, it, expect } from 'vitest';
import {
  BOMBARDMENT_RANGE, faceLetter, groundBaseLabel, bombardmentRefusal,
} from './groundBombardment';

describe('faceLetter', () => {
  it('maps the six planet sides onto A-F', () => {
    expect([1, 2, 3, 4, 5, 6].map(faceLetter)).toEqual(['A', 'B', 'C', 'D', 'E', 'F']);
  });

  it('has nothing to say about a unit that is not on a planet', () => {
    expect(faceLetter(undefined)).toBeNull();
    expect(faceLetter(0)).toBeNull();
    expect(faceLetter(7)).toBeNull();
  });
});

describe('groundBaseLabel', () => {
  /**
   * The point of the letter: a radius-zero planet keeps all six faces' bases in ONE hex, so the
   * name alone cannot tell two of them apart.
   */
  it('names the face so two bases in one hex can be told apart', () => {
    expect(groundBaseLabel('Ground Battery', 2)).toBe('Ground Battery (face B)');
    expect(groundBaseLabel('Ground Battery', 6)).toBe('Ground Battery (face F)');
  });

  it('leaves an ordinary unit alone', () => {
    expect(groundBaseLabel('IKV Saber', undefined)).toBe('IKV Saber');
  });
});

describe('bombardmentRefusal', () => {
  const base = (range: number) => ({ range, groundBase: true });

  it('says nothing about a target that is not a ground base', () => {
    expect(bombardmentRefusal({ range: 30 }, { activeFireControl: false })).toBeNull();
  });

  it('allows the attack at exactly four hexes', () => {
    expect(bombardmentRefusal(base(BOMBARDMENT_RANGE), { activeFireControl: true })).toBeNull();
  });

  it('refuses at five, and says how far away it is', () => {
    const why = bombardmentRefusal(base(5), { activeFireControl: true });
    expect(why).toContain('P2.713');
    expect(why).toContain('5');
  });

  /**
   * The surprising half of the rule. D19.224 lets a ship on passive fire control fire
   * ballistically at a hex freely; only a bombardment is barred.
   */
  it('refuses under passive fire control even in range', () => {
    expect(bombardmentRefusal(base(2), { activeFireControl: false }))
      .toContain('passive fire control');
  });

  /**
   * Jackson serialises an absent boolean as null rather than omitting the key, so an unknown
   * fire control state must not read as passive — that would hide a legal order.
   */
  it('does not treat an unknown fire control state as passive', () => {
    expect(bombardmentRefusal(base(2), {})).toBeNull();
    expect(bombardmentRefusal(base(2), null)).toBeNull();
  });

  it('reports the range first, since that is the one the player can act on', () => {
    expect(bombardmentRefusal(base(9), { activeFireControl: false })).toContain('out of reach');
  });
});
