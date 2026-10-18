import { describe, it, expect } from 'vitest';
import { moveRefusal } from './moveLegality';

describe('moveRefusal — sideslip (C2.44)', () => {
  it('allows a sideslip when a hex has been moved since the last one', () => {
    expect(moveRefusal('SIDESLIP_LEFT', { canSideslip: true })).toBeNull();
    expect(moveRefusal('SIDESLIP_RIGHT', { canSideslip: true })).toBeNull();
  });

  it('refuses both sideslips when it has not, and says why', () => {
    const why = moveRefusal('SIDESLIP_LEFT', { canSideslip: false });
    expect(why).toContain('C2.44');
    expect(moveRefusal('SIDESLIP_RIGHT', { canSideslip: false })).toBe(why);
  });
});

describe('moveRefusal — turning (C2.2)', () => {
  it('allows a turn when the turn mode has been satisfied', () => {
    expect(moveRefusal('TURN_LEFT', { hexesUntilTurn: 0 })).toBeNull();
  });

  it('refuses a turn and names how many hexes are still needed', () => {
    expect(moveRefusal('TURN_RIGHT', { hexesUntilTurn: 3 })).toContain('3 more hexes');
    expect(moveRefusal('TURN_RIGHT', { hexesUntilTurn: 3 })).toContain('C2.2');
  });

  /** Singular, because "1 more hexes" is the kind of thing that makes a UI look unfinished. */
  it('says hex rather than hexes when only one is needed', () => {
    const why = moveRefusal('TURN_LEFT', { hexesUntilTurn: 1 });
    expect(why).toContain('1 more hex ');
    expect(why).not.toContain('hexes');
  });

  /** A base has no turn mode: it rotates under its own rule and never turns (C3.7). */
  it('refuses a turn for a unit with no turn mode', () => {
    expect(moveRefusal('TURN_LEFT', { turnMode: null, hexesUntilTurn: 0 }))
      .toContain('C3.7');
  });
});

describe('moveRefusal — what it must not refuse', () => {
  it('never refuses a forward move', () => {
    expect(moveRefusal('FORWARD', { canSideslip: false, hexesUntilTurn: 5 })).toBeNull();
  });

  /**
   * Missing information must read as permissive. An older server sends neither flag, and Jackson
   * serialises an absent value as null rather than omitting the key — so a falsy test would grey
   * buttons that are perfectly legal. Greying a legal button is the worse failure: the engine
   * still refuses an illegal move, but a wrongly greyed button leaves the player stuck.
   */
  it('allows everything when the ship says nothing about these rules', () => {
    expect(moveRefusal('SIDESLIP_LEFT', {})).toBeNull();
    expect(moveRefusal('TURN_LEFT', {})).toBeNull();
    expect(moveRefusal('SIDESLIP_LEFT', null)).toBeNull();
    expect(moveRefusal('TURN_RIGHT', undefined)).toBeNull();
  });

  it('does not treat an undefined sideslip flag as a refusal', () => {
    expect(moveRefusal('SIDESLIP_RIGHT', { canSideslip: undefined })).toBeNull();
  });
});
