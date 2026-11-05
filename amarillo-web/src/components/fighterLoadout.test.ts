import { describe, expect, it } from 'vitest';
import type {
  CoiDroneModule, CoiDroneType, CoiFighterLoadout, CoiFighterRail,
} from '../api/gameApi';
import {
  bandDefault, differsFromBand, differsFromIssue, hasTypeIiiFrame, isMultiWarhead, loadOf,
  parseLoad, railRefusal, resetFighter, seedOverrides, setBandRail, setRail, toWire, typeBands,
} from './fighterLoadout';

/**
 * The fighter loadout surface's arithmetic (FD10.646, J4.23).
 *
 * The fixture is a Kzinti TADS, which is the fighter with one of every rail that matters: two
 * standard, two light and two special. Its EW variant carries two pods, and J4.962 takes those
 * rails out of the list — which is why the rails are addressed by position and not by count.
 */

function rail(
  railType: string, designator: string, spaces: number, drone: string,
  accepts: { typeIii?: boolean; mw?: boolean } = {},
): CoiFighterRail {
  return {
    railType, designator, spaces, drone,
    acceptsTypeIii: accepts.typeIii ?? false,
    acceptsMultiWarhead: accepts.mw ?? false,
  };
}

/** A TADS as the server describes it: six rails, all carrying the year's ordinary drones. */
function tads(name: string, squadron = 'KHS Cat Squadron 1'): CoiFighterLoadout {
  return {
    name, type: 'tads', designation: 'TADS', squadron,
    rails: [
      rail('STANDARD', 'A', 1, 'TypeIF'),
      rail('STANDARD', 'B', 1, 'TypeIF'),
      rail('LIGHT', 'C', 0.5, 'TypeVIF'),
      rail('LIGHT', 'D', 0.5, 'TypeVIF'),
      rail('SPECIAL', 'E', 1, 'TypeIF', { typeIii: true, mw: true }),
      rail('SPECIAL', 'F', 1, 'TypeIF', { typeIii: true, mw: true }),
    ],
  };
}

/** The EW variant: the same six rails less the two its pods took (J4.962). */
function tadsE(name: string): CoiFighterLoadout {
  return {
    name, type: 'tads_e', designation: 'TADS-E', squadron: 'KHS Cat Squadron 1',
    rails: [
      rail('LIGHT', 'C', 0.5, 'TypeVIF'),
      rail('LIGHT', 'D', 0.5, 'TypeVIF'),
      rail('SPECIAL', 'E', 1, 'TypeIF', { typeIii: true, mw: true }),
      rail('SPECIAL', 'F', 1, 'TypeIF', { typeIii: true, mw: true }),
    ],
  };
}

function droneType(name: string, family: string, rack: number): CoiDroneType {
  return {
    name, family, rack, speed: 32, damage: 12, familyLabel: family, endurance: 96,
    selfGuiding: family === 'SELF_GUIDING', dogfight: family === 'DOGFIGHT', damageVsShip: 12,
  } as CoiDroneType;
}

function module(name: string, submunitions = 0): CoiDroneModule {
  return {
    name, label: name, spaces: 1, cost: 1, availability: 'GENERAL', strength: 12,
    hullBonus: 0, requiresForward: false, armor: false, submunitions,
  };
}

const CATALOGUE = {
  types: [
    droneType('TypeIF', 'STANDARD', 1),
    droneType('TypeIM', 'STANDARD', 1),
    droneType('TypeVIF', 'DOGFIGHT', 0.5),
    droneType('TypeIIIF', 'SELF_GUIDING', 1),
    droneType('TypeIVF', 'HEAVY', 2),
  ],
  modules: [module('ECM'), module('PROBE'), module('MW_SMALL', 3), module('MW_LARGE', 5)],
  additions: [],
};

// ---------------------------------------------------------------- reading the composition

describe('parseLoad', () => {
  it('reads a bare catalogue drone', () => {
    expect(parseLoad('TypeIF')).toEqual({ basis: 'TypeIF', modifiers: [] });
  });

  it('reads what was done to it (FD10.2)', () => {
    expect(parseLoad('TypeIV+ARMOR_HALF+EXPLOSIVE_HALF'))
      .toEqual({ basis: 'TypeIV', modifiers: ['ARMOR_HALF', 'EXPLOSIVE_HALF'] });
  });

  it('survives an empty string rather than inventing a drone', () => {
    expect(parseLoad('')).toEqual({ basis: '', modifiers: [] });
  });
});

describe('what a drone IS', () => {
  it('finds the type-III frame by family, not by name', () => {
    expect(hasTypeIiiFrame(parseLoad('TypeIIIF'), CATALOGUE)).toBe(true);
    // "TypeIV" starts with "TypeI", which is why prefix matching is not allowed here.
    expect(hasTypeIiiFrame(parseLoad('TypeIVF'), CATALOGUE)).toBe(false);
  });

  it('finds it bolted on as an addition too (FD10.53)', () => {
    expect(hasTypeIiiFrame(parseLoad('TypeIF+TYPE_III_GUIDANCE'), CATALOGUE)).toBe(true);
  });

  it('finds a multi-warhead by its submunitions, which is what makes it one (FD8.0)', () => {
    expect(isMultiWarhead(parseLoad('TypeIF+MW_SMALL'), CATALOGUE)).toBe(true);
    expect(isMultiWarhead(parseLoad('TypeIF+ECM'), CATALOGUE)).toBe(false);
  });
});

// ---------------------------------------------------------------- what a rail will take

describe('railRefusal', () => {
  const rails = tads('x').rails;

  it('lets an ordinary drone onto an ordinary rail', () => {
    expect(railRefusal(rails[0], 'TypeIM', CATALOGUE)).toBeNull();
  });

  it('lets a special type-I onto a standard rail, which is J4.23 drawn exactly', () => {
    expect(railRefusal(rails[0], 'TypeIF+ECM', CATALOGUE)).toBeNull();
  });

  it('keeps a type-III frame off a standard rail (FD9.35)', () => {
    expect(railRefusal(rails[0], 'TypeIIIF', CATALOGUE)).toMatch(/FD9\.35/);
  });

  it('and lets it onto a special one (J4.233)', () => {
    expect(railRefusal(rails[4], 'TypeIIIF', CATALOGUE)).toBeNull();
  });

  it('keeps a multi-warhead off a standard rail (J4.23)', () => {
    expect(railRefusal(rails[0], 'TypeIF+MW_SMALL', CATALOGUE)).toMatch(/J4\.23/);
  });

  it('and lets it onto a special one', () => {
    expect(railRefusal(rails[4], 'TypeIF+MW_SMALL', CATALOGUE)).toBeNull();
  });

  it('refuses on size where size is the reason, and says so', () => {
    const refusal = railRefusal(rails[2], 'TypeIF', CATALOGUE);
    expect(refusal).toMatch(/spaces/);
    // Not the type-III rule, which is the mis-report that sends a player hunting for a
    // smaller drone when the answer was a different rail.
    expect(refusal).not.toMatch(/FD9\.35/);
  });

  it('names the rule BEFORE the size when both would refuse', () => {
    // A type-IV-MW is two spaces AND a multi-warhead. On a one-space special rail both are
    // true, and the useful answer is the one the player can act on.
    expect(railRefusal(rails[4], 'TypeIVF+MW_LARGE', CATALOGUE)).toMatch(/spaces/);
    expect(railRefusal(rails[0], 'TypeIVF+MW_LARGE', CATALOGUE)).toMatch(/J4\.23/);
  });

  it('refuses a drone the scenario does not offer rather than guessing at it', () => {
    expect(railRefusal(rails[0], 'TypeIX', CATALOGUE)).toMatch(/not a drone/);
  });
});

// ---------------------------------------------------------------- the bands

describe('typeBands', () => {
  it('puts one band per fighter type, in the order the bays hold them', () => {
    const bands = typeBands([tads('T-1'), tads('T-2'), tadsE('E-1')]);
    expect(bands.map(b => b.type)).toEqual(['tads', 'tads_e']);
    expect(bands[0].fighters).toHaveLength(2);
    expect(bands[1].fighters).toHaveLength(1);
  });

  it('takes the rail shape from the band, so an EW fighter is four rails and not six', () => {
    const bands = typeBands([tads('T-1'), tadsE('E-1')]);
    expect(bands[0].rails).toHaveLength(6);
    expect(bands[1].rails).toHaveLength(4);
  });

  it('leaves out a craft with no rails rather than banding an empty one', () => {
    const noRails = { ...tads('P-1'), type: 'g18', rails: [] };
    expect(typeBands([noRails])).toEqual([]);
  });
});

describe('loadOf', () => {
  it('is what the hull issued until somebody changes it (FD2.31)', () => {
    expect(loadOf(tads('T-1'), {})).toEqual(
      ['TypeIF', 'TypeIF', 'TypeVIF', 'TypeVIF', 'TypeIF', 'TypeIF']);
  });

  it('takes the override where there is one and the issue where there is not', () => {
    const f = tads('T-1');
    const overrides = setRail({}, f, 4, 'TypeIIIF');
    expect(loadOf(f, overrides)[4]).toBe('TypeIIIF');
    expect(loadOf(f, overrides)[5]).toBe('TypeIF');
  });
});

describe('bandDefault', () => {
  it('is what most of the band is carrying, not what the first fighter happens to have', () => {
    const fighters = [tads('T-1'), tads('T-2'), tads('T-3')];
    const band = typeBands(fighters)[0];
    // Change only the FIRST fighter; the band's prevailing load must not follow it.
    const overrides = setRail({}, fighters[0], 4, 'TypeIIIF');
    expect(bandDefault(band, overrides)[4]).toBe('TypeIF');
  });

  it('follows the band once most of it has moved', () => {
    const fighters = [tads('T-1'), tads('T-2'), tads('T-3')];
    const band = typeBands(fighters)[0];
    let overrides = setRail({}, fighters[0], 4, 'TypeIIIF');
    overrides = setRail(overrides, fighters[1], 4, 'TypeIIIF');
    expect(bandDefault(band, overrides)[4]).toBe('TypeIIIF');
  });
});

describe('differsFromBand', () => {
  it('marks the exception and not its neighbours', () => {
    const fighters = [tads('T-1'), tads('T-2'), tads('T-3')];
    const band = typeBands(fighters)[0];
    const overrides = setRail({}, fighters[0], 4, 'TypeIIIF');

    expect(differsFromBand(fighters[0], band, overrides)).toBe(true);
    expect(differsFromBand(fighters[1], band, overrides)).toBe(false);
  });

  it('marks nobody when the whole band moved together', () => {
    const fighters = [tads('T-1'), tads('T-2')];
    const band = typeBands(fighters)[0];
    const overrides = setBandRail({}, band, 4, 'TypeIIIF');
    for (const f of fighters)
      expect(differsFromBand(f, band, overrides)).toBe(false);
  });
});

// ---------------------------------------------------------------- editing

describe('setBandRail', () => {
  it('moves every fighter in the band and nobody outside it', () => {
    const fighters = [tads('T-1'), tads('T-2'), tadsE('E-1')];
    const bands = typeBands(fighters);
    const overrides = setBandRail({}, bands[0], 4, 'TypeIIIF');

    expect(loadOf(fighters[0], overrides)[4]).toBe('TypeIIIF');
    expect(loadOf(fighters[1], overrides)[4]).toBe('TypeIIIF');
    // The EW fighter is its own band AND its rails are differently numbered: its index 4
    // does not exist, which is the error a shared index would have caused.
    expect(differsFromIssue(fighters[2], overrides)).toBe(false);
  });

  it('leaves exceptions on OTHER rails alone', () => {
    const fighters = [tads('T-1'), tads('T-2')];
    const band = typeBands(fighters)[0];
    let overrides = setRail({}, fighters[0], 0, 'TypeIF+ECM');
    overrides = setBandRail(overrides, band, 4, 'TypeIIIF');

    expect(loadOf(fighters[0], overrides)[0]).toBe('TypeIF+ECM');
    expect(loadOf(fighters[0], overrides)[4]).toBe('TypeIIIF');
  });
});

describe('resetFighter', () => {
  it('puts it back to what the hull issued', () => {
    const f = tads('T-1');
    const overrides = resetFighter(setRail({}, f, 4, 'TypeIIIF'), f);
    expect(differsFromIssue(f, overrides)).toBe(false);
  });
});

// ---------------------------------------------------------------- the wire

describe('toWire', () => {
  it('sends nothing at all when nothing was changed', () => {
    expect(toWire({}, [tads('T-1'), tads('T-2')])).toBeUndefined();
  });

  it('sends only the fighters that differ from what they were issued', () => {
    const fighters = [tads('T-1'), tads('T-2')];
    const wire = toWire(setRail({}, fighters[0], 4, 'TypeIIIF'), fighters);
    expect(Object.keys(wire ?? {})).toEqual(['T-1']);
  });

  it('keeps the gaps, because the server reads the list by POSITION', () => {
    const fighters = [tads('T-1')];
    const wire = toWire(setRail({}, fighters[0], 4, 'TypeIIIF'), fighters);
    // Six entries, five of them null. Compacted to ["TypeIIIF"] the server would put a
    // type-III on rail A, which is standard, and refuse the whole fighter (J4.23).
    expect(wire?.['T-1']).toEqual([null, null, null, null, 'TypeIIIF', null]);
  });

  it('drops a fighter that was changed back to its issue', () => {
    const fighters = [tads('T-1')];
    const overrides = setRail({}, fighters[0], 4, 'TypeIF');   // the same drone it had
    expect(toWire(overrides, fighters)).toBeUndefined();
  });

  it('round-trips what was saved against a fleet (S8.12)', () => {
    const fighters = [tads('T-1')];
    const saved = toWire(setRail({}, fighters[0], 4, 'TypeIIIF'), fighters);
    const reseeded = seedOverrides(saved);
    expect(loadOf(fighters[0], reseeded)[4]).toBe('TypeIIIF');
    expect(toWire(reseeded, fighters)).toEqual(saved);
  });

  it('survives a stale saved entry naming more rails than the fighter has', () => {
    const fighters = [tadsE('E-1')];
    const reseeded = seedOverrides({ 'E-1': [null, null, null, null, 'TypeIIIF', 'TypeIIIF'] });
    // Four rails, so the two off the end are simply not read — the server refuses the
    // over-long list outright, and the dialog must not crash drawing it in the meantime.
    expect(loadOf(fighters[0], reseeded)).toHaveLength(4);
  });
});
