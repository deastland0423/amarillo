import { describe, expect, it } from 'vitest';
import {
  canSplitShots, shotsRemaining, tallyDraftedShots, weaponsSpokenFor,
  type DraftedOrder, type ShotAllowance,
} from './gatlingShots';

/** A Ph-G as the server reports one: four shots a turn, no gap (E2.151). */
const gatling = (shotsThisTurn = 0): ShotAllowance =>
  ({ name: 'PhaserG-1', maxShotsPerTurn: 4, minImpulseGap: 0, shotsThisTurn });

/** A Ph-1: one shot, then eight impulses of nothing. */
const phaser1 = (shotsThisTurn = 0): ShotAllowance =>
  ({ name: 'Phaser1-1', maxShotsPerTurn: 1, minImpulseGap: 8, shotsThisTurn });

/** An ADD, which repeats but sets a gap of 1 precisely to bar two shots in one impulse. */
const add = (shotsThisTurn = 0): ShotAllowance =>
  ({ name: 'ADD-1', maxShotsPerTurn: 2, minImpulseGap: 1, shotsThisTurn });

const order = (target: string, weaponNames: string[], shipName = 'Stinger2-1'): DraftedOrder =>
  ({ shipName, targetName: target, weaponNames });

describe('which weapons may be spread over several targets', () => {
  it('a gatling may: four shots a turn with no gap between them', () => {
    expect(canSplitShots(gatling())).toBe(true);
  });

  it('a phaser-1 may not, having only one shot to give', () => {
    expect(canSplitShots(phaser1())).toBe(false);
  });

  it('an ADD may not: its gap bars a second shot in the same impulse', () => {
    expect(canSplitShots(add())).toBe(false);
  });
});

describe('shots remaining', () => {
  it('counts fired shots and drafted shots alike', () => {
    expect(shotsRemaining(gatling(0), 0)).toBe(4);
    expect(shotsRemaining(gatling(1), 0)).toBe(3);
    expect(shotsRemaining(gatling(1), 2)).toBe(1);
    expect(shotsRemaining(gatling(0), 4)).toBe(0);
  });

  it('gives a single-shot weapon one shot whatever its allowance says', () => {
    // The ADD's allowance is 2 per TURN, but not in one impulse, so drafting spends it.
    expect(shotsRemaining(add(0), 0)).toBe(1);
    expect(shotsRemaining(add(0), 1)).toBe(0);
  });
});

describe('a gatling engaging two drones in one impulse (E2.151)', () => {
  it('is still offerable after one shot is promised to the first drone', () => {
    const orders = [order('Drone-1', ['PhaserG-1'])];

    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [gatling()]);

    expect(spoken.has('PhaserG-1')).toBe(false);
  });

  it('is spent only once all four shots are drafted', () => {
    const orders = [
      order('Drone-1', ['PhaserG-1', 'PhaserG-1']),
      order('Drone-2', ['PhaserG-1']),
    ];
    expect(weaponsSpokenFor(orders, 'Stinger2-1', [gatling()]).has('PhaserG-1')).toBe(false);

    orders.push(order('Drone-3', ['PhaserG-1']));
    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [gatling()]);

    expect(spoken.get('PhaserG-1')).toBe('Drone-1');
  });

  it('counts shots already fired this turn against the split', () => {
    // Three fired earlier in the turn, one drafted now: nothing left for a second drone.
    const orders = [order('Drone-1', ['PhaserG-1'])];

    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [gatling(3)]);

    expect(spoken.get('PhaserG-1')).toBe('Drone-1');
  });

  it('names the hex when the first order was fired at one', () => {
    const orders: DraftedOrder[] = [
      { shipName: 'Stinger2-1', targetName: null, weaponNames: ['PhaserG-1'], hexCol: 12, hexRow: 9 },
    ];

    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [gatling(3)]);

    expect(spoken.get('PhaserG-1')).toBe('hex 12|9');
  });
});

describe('everything that is not a gatling', () => {
  it('a phaser-1 is spoken for the moment it is promised', () => {
    const orders = [order('IKV Saber', ['Phaser1-1'])];

    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [phaser1()]);

    expect(spoken.get('Phaser1-1')).toBe('IKV Saber');
  });

  it('another unit’s orders do not spend this one’s weapons', () => {
    const orders = [order('Drone-1', ['PhaserG-1'], 'Stinger2-2')];

    const { spent } = tallyDraftedShots(orders, 'Stinger2-1');

    expect(spent.size).toBe(0);
    expect(weaponsSpokenFor(orders, 'Stinger2-1', [gatling()]).size).toBe(0);
  });

  it('an order naming a weapon the attacker has not got is not offerable', () => {
    const orders = [order('Drone-1', ['Fusion-9'])];

    const spoken = weaponsSpokenFor(orders, 'Stinger2-1', [gatling()]);

    expect(spoken.get('Fusion-9')).toBe('Drone-1');
  });
});
