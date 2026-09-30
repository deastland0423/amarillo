import { describe, expect, it } from 'vitest';
import type { DroneRackState, WeaponState } from '../types/gameState';
import { chipColour, weaponChips } from './weaponCondition';

/**
 * The DAC choice dialog asks the player which working weapon is destroyed, and these chips are
 * the whole basis for the answer. Getting one wrong does not crash anything — it quietly
 * advises the player to throw away the wrong weapon.
 */

/** A phaser: nothing to arm, one shot, ready. Every case below starts from this. */
function weapon(over: Partial<WeaponState> = {}): WeaponState {
  return {
    name: 'Phaser1-A',
    armed: false,
    armingTurn: 0,
    armingType: null,
    lastImpulseFired: 0,
    readyToFire: true,
    arcLabel: 'FA',
    arcMask: 0,
    launchDirectionsMask: 0,
    functional: true,
    plasmaType: null,
    launcherType: null,
    pseudoPlasmaReady: false,
    isHeavy: false,
    armingCost: 0,
    holdCost: 0,
    canOverload: false,
    canSuicide: false,
    canProximity: false,
    overloadFinalTurnOnly: false,
    totalArmingTurns: 0,
    isRolling: false,
    rollingCost: 0,
    canEpt: false,
    eptCost: 0,
    maxShotsPerTurn: 1,
    shotsThisTurn: 0,
    minImpulseGap: 0,
    ...over,
  };
}

function rack(over: Partial<DroneRackState> = {}): DroneRackState {
  return {
    name: 'DroneRack-A',
    functional: true,
    canFire: true,
    drones: [],
    reloadCount: 0,
    reloadingThisTurn: false,
    reloadPool: [],
    launchDirectionsMask: 0,
    ...over,
  };
}

const texts = (w: WeaponState, r?: DroneRackState | null) =>
  weaponChips(w, r).map(c => c.text);

describe('arming state', () => {
  it('says an armed heavy weapon is armed, and that it is worth something', () => {
    const chips = weaponChips(weapon({ isHeavy: true, armed: true, armingType: 'STANDARD' }));
    expect(chips.map(c => c.text)).toEqual(['armed', 'std']);
    expect(chips[0].tone).toBe('valuable');
  });

  it('marks an overloaded weapon with the mode, which is what makes it expensive to lose', () => {
    const chips = weaponChips(weapon({ isHeavy: true, armed: true, armingType: 'OVERLOAD' }));
    expect(chips.map(c => c.text)).toEqual(['armed', 'ovl']);
    expect(chipColour(chips[1])).toBe('#f0c040');
  });

  it('reports how far a part-armed weapon got, since that energy is lost too', () => {
    expect(texts(weapon({ isHeavy: true, armed: false, armingTurn: 1, totalArmingTurns: 2 })))
      .toContain('arming 1/2');
  });

  it('calls an empty tube unarmed, and does not count it as a loss', () => {
    const chips = weaponChips(weapon({ isHeavy: true, armed: false }));
    expect(chips.map(c => c.text)).toEqual(['unarmed']);
    expect(chips[0].tone).toBe('spent');
    expect(chips.some(c => c.tone === 'valuable')).toBe(false);
  });

  it('counts the energy already in a photon tube (E4.413)', () => {
    expect(texts(weapon({
      isHeavy: true, armed: false, armingTurn: 1, photonTube: true, armingEnergy: 3,
    }))).toContain('3 in tube');
  });

  it('shows a rolling plasma as rolling, and which torpedo is on the rail', () => {
    expect(texts(weapon({
      isHeavy: true, armed: true, armingType: 'STANDARD', isRolling: true, plasmaType: 'R',
    }))).toEqual(['armed', 'roll', 'type-R']);
  });
});

describe('whether it can still shoot', () => {
  it('flags a fusion that fired last turn as dead weight this turn', () => {
    const chips = weaponChips(weapon({ cooldown: true }));
    expect(chips.map(c => c.text)).toEqual(['cooled down']);
    expect(chips[0].tone).toBe('spent');
  });

  it('says when a weapon has used all its shots', () => {
    expect(texts(weapon({ maxShotsPerTurn: 2, shotsThisTurn: 2, readyToFire: false })))
      .toEqual(['2/2 shots used']);
  });

  /**
   * The impulse gap is a rule and readyToFire is core's answer to it. This asserts the chip is
   * driven by that field and never recomputed from lastImpulseFired.
   */
  it('reads cooldown off readyToFire rather than working the impulse gap out again', () => {
    expect(texts(weapon({ readyToFire: false, minImpulseGap: 8, lastImpulseFired: 5 })))
      .toEqual(['cooldown']);
  });

  it('does not claim cooldown on an unarmed heavy weapon — it is unarmed, not cooling', () => {
    expect(texts(weapon({ isHeavy: true, armed: false, readyToFire: false })))
      .toEqual(['unarmed']);
  });

  it('notes a weapon that has fired but can fire again', () => {
    expect(texts(weapon({ maxShotsPerTurn: 2147483647, shotsThisTurn: 1 }))).toEqual(['fired']);
  });

  it('short-circuits on a destroyed weapon', () => {
    expect(texts(weapon({ functional: false, isHeavy: true, armed: true }))).toEqual(['destroyed']);
  });
});

describe('ammunition and loads', () => {
  it('lists what is on a drone rack, grouped by type', () => {
    const drones = [
      { droneType: 'TypeI', warheadDamage: 12, speed: 20, endurance: 20 },
      { droneType: 'TypeI', warheadDamage: 12, speed: 20, endurance: 20 },
      { droneType: 'TypeIV', warheadDamage: 24, speed: 20, endurance: 20 },
    ];
    const chips = weaponChips(weapon({ name: 'DroneRack-A' }), rack({ drones, reloadCount: 4 }));
    expect(chips.map(c => c.text)).toContain('loaded: 2x TypeI, TypeIV');
    expect(chips.map(c => c.text)).toContain('4 reloads');
    expect(chips.some(c => c.tone === 'valuable')).toBe(true);
  });

  it('calls an empty rack empty, and does not count it as a loss', () => {
    const chips = weaponChips(weapon({ name: 'DroneRack-A' }), rack());
    expect(chips.map(c => c.text)).toContain('rack empty');
    expect(chips.some(c => c.tone === 'valuable')).toBe(false);
  });

  it('shows a type-G rack committed to anti-drones for the turn (FD3.71)', () => {
    expect(texts(weapon({ name: 'DroneRack-G' }), rack({ mode: 'ANTI_DRONE' })))
      .toContain('anti-drone');
  });

  it('counts anti-drone rounds against a capacity when there is one', () => {
    expect(texts(weapon({ addShots: 2, addCapacity: 4, addReloads: 6 })))
      .toContain('2/4 ADD (+6)');
  });

  it('omits a capacity a type-G does not have', () => {
    expect(texts(weapon({ addShots: 3 }))).toContain('3 ADD');
  });

  it('shows a fighter fusion its charges', () => {
    expect(texts(weapon({ chargesRemaining: 1 }))).toContain('1 charge');
    expect(texts(weapon({ chargesRemaining: 2 }))).toContain('2 charges');
  });
});

describe('systems that sit among the weapons', () => {
  it('shows what a scout channel is lending, because the loan dies with it (G24.21)', () => {
    const chips = weaponChips(weapon({
      name: 'ScoutChannel-A', scoutChannel: true, channelPowered: true,
      channelFunction: 'LEND_EW', channelLendTarget: 'Kzinti CV',
      channelLentEcm: 3, channelLentEccm: 1,
    }));
    expect(chips.map(c => c.text)).toContain('lending 3/1 to Kzinti CV');
    expect(chips.some(c => c.tone === 'valuable')).toBe(true);
  });

  it('marks an unpowered channel as the cheap one to lose (G24.14)', () => {
    const chips = weaponChips(weapon({
      name: 'ScoutChannel-B', scoutChannel: true, channelPowered: false,
    }));
    expect(chips.map(c => c.text)).toContain('unpowered');
    expect(chips.some(c => c.tone === 'valuable')).toBe(false);
  });

  it('shows an ESG field that is up, and the energy held behind it', () => {
    expect(texts(weapon({
      name: 'ESG-A', esg: true, esgActive: true, esgRadius: 2,
      esgStoredEnergy: 5, esgMaxEnergy: 7,
    }))).toEqual(expect.arrayContaining(['field up r2', '5/7 stored']));
  });
});

/**
 * armed === null is the server declining to say. A DAC choice is always over the player's own
 * ship, so this should not arise here — but the guard is the one that keeps a display helper
 * from becoming an information leak if it is ever reused on an enemy's weapons.
 */
describe('undisclosed arming', () => {
  it('says nothing about how an undisclosed weapon is armed', () => {
    const chips = weaponChips(weapon({
      isHeavy: true, armed: null, armingTurn: 2, armingType: 'OVERLOAD', readyToFire: false,
    }));
    expect(chips.map(c => c.text)).toEqual([]);
  });

  it('still reports the public fact that it has fired', () => {
    expect(texts(weapon({
      isHeavy: true, armed: null, shotsThisTurn: 1, maxShotsPerTurn: 2147483647,
    }))).toEqual(['fired']);
  });
});

describe('chip colours', () => {
  it('greys what is cheap to lose and highlights what is not', () => {
    expect(chipColour({ text: 'unarmed', tone: 'spent' })).toBe('#6e7681');
    expect(chipColour({ text: 'armed', tone: 'valuable' })).toBe('#f0c040');
  });

  it('gives each arming mode its own colour', () => {
    expect(chipColour({ text: 'ovl', tone: 'mode' })).toBe('#f0c040');
    expect(chipColour({ text: 'spl', tone: 'mode' })).toBe('#79c0ff');
    expect(chipColour({ text: 'std', tone: 'mode' })).toBe('#8b949e');
  });
});

describe('the two meanings of SPECIAL', () => {
  const titleOf = (w: WeaponState) => weaponChips(w).find(c => c.text === 'spl')?.title;

  it('names proximity on a photon', () => {
    expect(titleOf(weapon({
      isHeavy: true, armed: true, armingType: 'SPECIAL', canProximity: true,
    }))).toMatch(/Proximity/);
  });

  it('names suicide overload on a fusion', () => {
    expect(titleOf(weapon({
      isHeavy: true, armed: true, armingType: 'SPECIAL', canSuicide: true,
    }))).toMatch(/Suicide/);
  });
});
