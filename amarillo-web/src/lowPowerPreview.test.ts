import { describe, expect, it } from 'vitest';
import {
  canFireLowPower, getWeaponDamagePreview, PHASER3_MAX_RANGE,
} from './weaponDamageTables';

/**
 * E2.25 in the damage preview: a phaser elected to fire as a ph-3 must be previewed as one.
 *
 * <p>The web never decides rules outcomes, but it does MIRROR them for previews, and this is
 * the mirror going out of step. Core has fired phasers at low power since the 16th and the
 * Fire Orders pad could elect it since the 17th — but `previewFor` passed `armingType`, which
 * says nothing about the shot mode, so the pad kept quoting full-power numbers for a shot that
 * would not deliver them.
 *
 * <p>Two different kinds of wrong, and the second is the one that matters:
 * <ul>
 *   <li>At point blank a ph-1 averages 6.5 where a ph-3 averages 3.83 — an overstatement of
 *       about seventy per cent.</li>
 *   <li>Past range 15 a ph-1 still scores and a ph-3 cannot reach at all. Core REFUSES the
 *       shot (E2.251), so a figure there is not optimism, it is a promise about a volley that
 *       is going to be rejected.</li>
 * </ul>
 */

const avg = (rows: { damage: number }[] | null) =>
  rows === null ? null : rows.reduce((s, r) => s + r.damage, 0) / rows.length;

const full = (name: string, range: number) =>
  getWeaponDamagePreview(name, null, range, range, true, false);
const low = (name: string, range: number) =>
  getWeaponDamagePreview(name, null, range, range, true, true);

describe('a phaser elected to fire low', () => {
  it('previews the ph-3 table, not its own', () => {
    // A ph-3 at range 0 is 4,4,4,4,4,3 whatever the phaser firing it.
    expect(low('Phaser1-1', 0)!.map(r => r.damage)).toEqual([4, 4, 4, 4, 4, 3]);
    expect(low('Phaser2-1', 0)!.map(r => r.damage)).toEqual([4, 4, 4, 4, 4, 3]);
    expect(low('Phaser4-1', 0)!.map(r => r.damage)).toEqual([4, 4, 4, 4, 4, 3]);
  });

  it('is worth markedly less than the same phaser at full power', () => {
    // The overstatement the pad was carrying: 6.5 against 3.83 at point blank.
    expect(avg(full('Phaser1-1', 0))).toBeCloseTo(6.5, 2);
    expect(avg(low('Phaser1-1', 0))).toBeCloseTo(3.83, 2);
  });

  it('previews NOTHING past the ph-3’s reach, where core refuses the shot', () => {
    // Not a weak figure — no figure. Every other weapon clamps to its last band when asked
    // about a range it cannot reach, which is merely optimistic; here the shot is rejected
    // outright, so a number would be a different and worse kind of wrong.
    expect(low('Phaser1-1', PHASER3_MAX_RANGE)).not.toBeNull();
    expect(low('Phaser1-1', PHASER3_MAX_RANGE + 1)).toBeNull();
    expect(low('Phaser1-1', 20)).toBeNull();

    // ...while the same phaser at full power still reaches 20 and says so.
    expect(avg(full('Phaser1-1', 20))).toBeGreaterThan(0);
  });

  it('leaves a full-power shot exactly as it was', () => {
    // The regression that matters most: nearly every shot is an ordinary one.
    for (const r of [0, 1, 5, 15, 30, 75])
      expect(full('Phaser1-1', r)).toEqual(
        getWeaponDamagePreview('Phaser1-1', null, r, r, true));
  });
});

describe('which phasers may be elected at all (E2.251)', () => {
  it('names the three the rule names', () => {
    expect(canFireLowPower('Phaser1-1')).toBe(true);
    expect(canFireLowPower('Phaser2-3')).toBe(true);
    expect(canFireLowPower('Phaser4-1')).toBe(true);
  });

  it('excludes the ph-3, which is already the floor', () => {
    expect(canFireLowPower('Phaser3-1')).toBe(false);
  });

  it('excludes the ph-G, which is not on E2.251’s list', () => {
    // It has its own blinding clause instead (G24.1342, keyed on firing twice in an impulse),
    // so it must not be swept in by a looser match on "phaser".
    expect(canFireLowPower('PhaserG-1')).toBe(false);
  });

  it('admits a fighter’s ph-2, because the server does and the rule does', () => {
    // This asserted the opposite first, on a feeling that a fighter's weapon is its own
    // thing. Four fighters in the data mount a ph-2, the server's rule is
    // `instanceof LowPowerPhaser` and admits them, and nothing in E2.25 excludes a fighter.
    // A second gate that disagrees with the first does not add safety — it silently drops a
    // legitimate election. Found by a mutant that removed the exclusion and passed.
    expect(canFireLowPower('Phaser2-1')).toBe(true);
    // ...and a fighter's ph-3 is still excluded, for being a ph-3.
    expect(canFireLowPower('FighterPhaser3-1')).toBe(false);
  });

  it('ignores the election for a weapon that cannot take it', () => {
    // Defence in depth: the pad only offers the toggle where the server says canLowPower, but
    // a stray flag must not turn a ph-3 or a photon into something else.
    expect(low('Phaser3-1', 0)).toEqual(full('Phaser3-1', 0));
    expect(low('Photon-A', 3)).toEqual(full('Photon-A', 3));
  });
});
