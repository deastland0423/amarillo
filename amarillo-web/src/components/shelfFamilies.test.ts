import { describe, expect, it } from 'vitest';
import type { CatalogShip } from '../api/gameApi';
import { familiesOf, matchesSearch, resolveFamily, toggleRefit } from './shelfFamilies';

/** Only the fields the grouping reads; the rest of CatalogShip is irrelevant here. */
function hull(type: string, extra: Partial<CatalogShip> = {}): CatalogShip {
  return { faction: 'Klingon', type, typeName: 'Battlecruiser', ...extra } as CatalogShip;
}

function variant(type: string, refitOf: string, refits: string[]): CatalogShip {
  return hull(type, { refitOf, appliedRefits: refits });
}

/** The real Klingon D6 forest, which is the shape that killed every derivation. */
const D6 = hull('D6');
const D6B = variant('D6B', 'D6', ['B']);
const D6K = variant('D6K', 'D6', ['B', 'K']);
const D6Bu = variant('D6Bu', 'D6', ['B', 'u']);
const D6Ku = variant('D6Ku', 'D6', ['B', 'K', 'u']);
const D6D = hull('D6D', { typeName: 'Drone Cruiser' });
const D6DB = variant('D6DB', 'D6D', ['B']);
const D7 = hull('D7');

describe('familiesOf', () => {
  /** Five rows become one, which is the whole point. */
  it('collapses a hull and its refits into one family', () => {
    const fams = familiesOf([D6, D6B, D6K, D6Bu, D6Ku]);

    expect(fams).toHaveLength(1);
    expect(fams[0].base.type).toBe('D6');
    expect(fams[0].variants.map(v => v.type)).toEqual(['D6B', 'D6K', 'D6Bu', 'D6Ku']);
  });

  /**
   * The case that rules out grouping by name. The D6 and D7 are both "Battlecruiser" and are
   * different ships; an earlier plan to group the shelf by typeName would have merged them.
   */
  it('keeps two hulls that share a class name apart', () => {
    const fams = familiesOf([D6, D6B, D7]);

    expect(fams.map(f => f.base.type).sort()).toEqual(['D6', 'D7']);
    expect(fams.find(f => f.base.type === 'D7')!.variants).toHaveLength(0);
  });

  /**
   * And the case that rules out grouping by type-code prefix. "D6" is a prefix of "D6D", but the
   * D6D is a drone cruiser in its own right — the owner's point — with its own B refit.
   */
  it('keeps a hull that merely shares a prefix apart, with its own refits', () => {
    const fams = familiesOf([D6, D6B, D6D, D6DB]);

    expect(fams).toHaveLength(2);
    const drone = fams.find(f => f.base.type === 'D6D')!;
    expect(drone.variants.map(v => v.type)).toEqual(['D6DB']);
  });

  /**
   * S8.131: a variant whose base is out of service this year has no row to hang under, and must not
   * vanish — a D7K is legal in Y180 whether or not a bare D7 is still being built.
   */
  it('stands a variant on its own when its base is not on the shelf', () => {
    const fams = familiesOf([D6B, D6K]);

    expect(fams.map(f => f.base.type)).toEqual(['D6B', 'D6K']);
    expect(fams.every(f => f.variants.length === 0)).toBe(true);
  });

  /** Two empires' hulls never merge, however their codes read. */
  it('separates hulls of different factions', () => {
    const lyran = hull('D6', { faction: 'Lyran' });
    expect(familiesOf([D6, lyran])).toHaveLength(2);
  });

  it('leaves an ordinary hull as a family of one', () => {
    const fams = familiesOf([D7]);
    expect(fams).toEqual([{ base: D7, variants: [] }]);
  });
});

describe('resolveFamily', () => {
  const fam = { base: D6, variants: [D6B, D6K, D6Bu, D6Ku] };

  it('resolves no refits to the base hull', () => {
    expect(resolveFamily(fam, [])!.type).toBe('D6');
  });

  it('resolves a set of refits to the hull history named', () => {
    expect(resolveFamily(fam, ['B'])!.type).toBe('D6B');
    expect(resolveFamily(fam, ['B', 'K'])!.type).toBe('D6K');
    expect(resolveFamily(fam, ['B', 'K', 'u'])!.type).toBe('D6Ku');
  });

  /** The order the player ticked them in is not a fact about the hull. */
  it('does not care what order the refits were ticked in', () => {
    expect(resolveFamily(fam, ['u', 'K', 'B'])!.type).toBe('D6Ku');
    expect(resolveFamily(fam, ['K', 'B'])!.type).toBe('D6K');
  });

  /**
   * The honest gap. S3.24 permits buying any refit the hull is eligible for, and the data can now
   * express a D6 with a UIM and no B refit — but the catalogue lists the variants a hull was NAMED
   * in, so there is no row to buy. The shelf disables the chip rather than adding a different ship.
   */
  it('returns null for a combination that was never fielded', () => {
    expect(resolveFamily(fam, ['u'])).toBeNull();
    expect(resolveFamily(fam, ['K'])).toBeNull();
  });

  it('returns null for a refit the hull does not have', () => {
    expect(resolveFamily(fam, ['B', 'p'])).toBeNull();
  });
});

describe('toggleRefit', () => {
  /**
   * The Klingon K refit arrives on top of B, so ticking K alone would select a ship that does not
   * exist. Pulling the requirement in beats refusing the click.
   */
  it('pulls in what a refit requires', () => {
    expect(toggleRefit([], 'K', ['B']).sort()).toEqual(['B', 'K']);
  });

  it('adds nothing twice', () => {
    expect(toggleRefit(['B'], 'K', ['B']).sort()).toEqual(['B', 'K']);
  });

  /** Unticking K is not a request to lose B. */
  it('leaves requirements alone when unticking', () => {
    expect(toggleRefit(['B', 'K'], 'K', ['B'])).toEqual(['B']);
  });

  it('toggles a refit with no requirements', () => {
    expect(toggleRefit([], 'B', [])).toEqual(['B']);
    expect(toggleRefit(['B'], 'B', [])).toEqual([]);
  });
});

describe('matchesSearch', () => {
  const cwl = {
    type: 'CWL', typeName: 'War Cruiser (Light Phaser)', lineName: 'War Cruiser',
  };
  const pol = { type: 'POL', typeName: 'Police Cutter', lineName: 'Police Ship' };

  it('matches everything on an empty or blank search', () => {
    expect(matchesSearch(cwl, '')).toBe(true);
    expect(matchesSearch(cwl, '   ')).toBe(true);
  });

  it('matches on the type code', () => {
    expect(matchesSearch(cwl, 'CWL')).toBe(true);
  });

  /** The point of substring over prefix: a half-typed code still narrows. */
  it('matches a partial code', () => {
    expect(matchesSearch(cwl, 'cw')).toBe(true);
  });

  /**
   * The class name, which differs from the line name on 305 of 355 hulls — so this is the only
   * field that can answer "which of these is the light-phaser one".
   */
  it('matches on the class name', () => {
    expect(matchesSearch(cwl, 'light phaser')).toBe(true);
  });

  it('matches on the line name', () => {
    expect(matchesSearch(pol, 'police ship')).toBe(true);
  });

  it('ignores case on both sides', () => {
    expect(matchesSearch(pol, 'POLICE')).toBe(true);
    expect(matchesSearch(cwl, 'cruiser')).toBe(true);
  });

  it('ignores surrounding whitespace', () => {
    expect(matchesSearch(cwl, '  CWL  ')).toBe(true);
  });

  it('does not match an unrelated hull', () => {
    expect(matchesSearch(cwl, 'police')).toBe(false);
    expect(matchesSearch(pol, 'dreadnought')).toBe(false);
  });

  /**
   * Jackson sends an absent string as null, not undefined, and a hull with no line name is a real
   * shape in the catalogue. A bare `.toLowerCase()` on it would throw and blank the whole shelf.
   */
  it('survives a null class or line name', () => {
    const bare = { type: 'DN', typeName: null, lineName: null } as unknown as
      Pick<CatalogShip, 'type' | 'typeName' | 'lineName'>;
    expect(matchesSearch(bare, 'DN')).toBe(true);
    expect(matchesSearch(bare, 'cruiser')).toBe(false);
  });
});
