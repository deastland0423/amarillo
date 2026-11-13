import { describe, expect, it } from 'vitest';
import type { ShipObject, WeaponState } from '../types/gameState';
import { systemsSummary, weaponsSummary } from './ssdSummaries';
import { worstGauge, type Gauge } from './systemGauge';

/**
 * These lines are what a COLLAPSED section still tells the player. If one is wrong, nothing
 * breaks and nobody finds out: the player reads the header, believes it, and never opens the
 * section that would have corrected it.
 */

/** A working phaser with a forward arc. Every case starts from this. */
function weapon(over: Partial<WeaponState> = {}): WeaponState {
  return {
    name: 'Phaser1-A', armed: false, armingTurn: 0, armingType: null, lastImpulseFired: 0,
    readyToFire: true, arcLabel: 'FA', arcMask: 0, launchDirectionsMask: 0, functional: true,
    plasmaType: null, launcherType: null, pseudoPlasmaReady: false, isHeavy: false,
    armingCost: 0, holdCost: 0, canOverload: false, canSuicide: false, canProximity: false,
    overloadFinalTurnOnly: false, totalArmingTurns: 0, isRolling: false, rollingCost: 0,
    canEpt: false, eptCost: 0, maxShotsPerTurn: 1, shotsThisTurn: 0, shotsAvailableNow: 1,
    minImpulseGap: 0,
    ...over,
  };
}

describe('weaponsSummary', () => {
  it('says so when the hull has nothing with an arc', () => {
    expect(weaponsSummary([], null)).toBe('none with a firing arc');
  });

  it('counts what still works, which is the headline a shut list owes the player', () => {
    expect(weaponsSummary([weapon(), weapon(), weapon()], null)).toBe('3/3 working');
    expect(weaponsSummary([weapon(), weapon({ functional: false }), weapon()], null))
      .toBe('2/3 working');
  });

  it('names the selected weapon, because its arc stays lit while the list is shut', () => {
    // The whole reason this function takes the selection. Collapsing the weapons does not
    // collapse the diagram, so an unnamed selection leaves an arc burning on screen with
    // nothing anywhere saying which weapon drew it.
    const selected = weapon({ label: 'Phaser-3 10', arcLabel: 'L + LR' });
    expect(weaponsSummary([weapon(), selected], selected))
      .toBe('2/2 working · showing Ph-3 10 (L + LR)');
  });

  it('still names a selected weapon that has been destroyed', () => {
    // A destroyed row is dimmed, not unclickable, so it can be the selection. Dropping it
    // here would be the same unlabelled-arc bug by a quieter route.
    const dead = weapon({ label: 'Phaser-1 7', arcLabel: 'FULL', functional: false });
    expect(weaponsSummary([weapon(), dead], dead))
      .toBe('1/2 working · showing Ph-1 7 (FULL)');
  });
});

describe('worstGauge', () => {
  const g = (caption: string, now: number, max: number): Gauge => ({ caption, now, max });

  it('is null while everything is untouched', () => {
    expect(worstGauge([g('fore', 4, 4), g('aft', 8, 8)])).toBeNull();
    expect(worstGauge([])).toBeNull();
  });

  it('ranks by surviving FRACTION, not by the smallest count', () => {
    // The claim worth pinning. Counting boxes says "fore, it has only one left"; the ship is
    // in fact far worse off aft, where ten of twelve are gone. Naming fore would send the
    // player to look at the healthier system.
    const worst = worstGauge([g('fore', 1, 3), g('aft', 2, 12)]);
    expect(worst?.caption).toBe('aft');
  });

  it('puts a destroyed system ahead of a merely damaged one', () => {
    expect(worstGauge([g('impulse', 1, 6), g('APR', 0, 4)])?.caption).toBe('APR');
  });

  it('keeps SSD order on a tie, so the answer does not wander between renders', () => {
    expect(worstGauge([g('L warp', 4, 8), g('R warp', 4, 8)])?.caption).toBe('L warp');
  });
});

describe('systemsSummary', () => {
  it('claims only hull and power, which are the rows it can actually see', () => {
    // Not "undamaged": the systems section also carries crew, command, support and special
    // equipment, none of which are gauges. A cheerful blanket claim would be a promise this
    // function is in no position to make.
    const ship = { maxFhull: 4, availableFhull: 4, maxImpulse: 6, availableImpulse: 6 } as ShipObject;
    expect(systemsSummary(ship)).toBe('hull and power intact');
  });

  it('names the worst-hit row across BOTH the power and hull gauges', () => {
    // Power and hull are separate rows in the panel but one question to the reader, so the
    // summary has to range over both -- a header that only searched its own row would call a
    // ship intact while its engine room was gone.
    const ship = {
      maxFhull: 4, availableFhull: 3,
      maxAhull: 8, availableAhull: 2,
      maxImpulse: 6, availableImpulse: 6,
    } as ShipObject;
    expect(systemsSummary(ship)).toBe('worst: aft 2/8');
  });

  it('reaches into power as readily as hull', () => {
    const ship = {
      maxFhull: 4, availableFhull: 3,
      maxLWarp: 12, availableLWarp: 1,
    } as ShipObject;
    expect(systemsSummary(ship)).toBe('worst: L warp 1/12');
  });
});
