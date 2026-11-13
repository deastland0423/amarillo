import { describe, expect, it } from 'vitest';
import { getWeaponFullTable } from './weaponDamageTables';

/**
 * The photon's printed table, every mode at once.
 *
 * <pre>
 * RANGE          0-1    2    3-4   5-8  9-12 13-30
 * HIT (STD)       NA   1-5   1-4   1-3   1-2   1-1
 * HIT (OVLD)     1-6   1-5   1-4   1-3    NA    NA
 * HIT (PROX)      NA    NA    NA    NA   1-4   1-3
 * DAMAGE, STD      0     8     8     8     8     8
 * DAMAGE, OVLD    16    16    16    16     0     0
 * DAMAGE, PROX     0     0     0     0     4     4
 * </pre>
 *
 * The 0-1 column is the one worth staring at. A standard photon CANNOT FIRE there -- E4 gives
 * it a minimum range of 2 -- while an overloaded one hits automatically for 16. Reserve warp
 * and batteries keep that choice live right up to the moment of firing, and the single-mode
 * table never showed it.
 */

const NA = null;

const PAPER_BANDS = ['0–1', '2', '3–4', '5–8', '9–12', '13–30'];

const PAPER_ROWS: Record<string, (number | null)[]> = {
  'Hit (std)':    [NA, 5, 4, 3, 2, 1],
  'Hit (ovld)':   [6, 5, 4, 3, NA, NA],
  'Hit (prox)':   [NA, NA, NA, NA, 4, 3],
  'Damage, std':  [0, 8, 8, 8, 8, 8],
  'Damage, ovld': [16, 16, 16, 16, 0, 0],
  'Damage, prox': [0, 0, 0, 0, 4, 4],
};

function modes(arming: string | null = null) {
  const t = getWeaponFullTable('Photon-A', arming);
  if (!t?.modes) throw new Error('a photon must produce a multi-mode table');
  return t.modes;
}

describe('the printed photon table', () => {
  it('bands where the charts change, and nowhere else', () => {
    expect(modes().labels).toEqual(PAPER_BANDS);
  });

  it.each(Object.keys(PAPER_ROWS))('matches row by row: %s', label => {
    const line = modes().lines.find(l => l.label === label);
    expect(line, `${label} is missing`).toBeDefined();
    expect(line!.cells).toEqual(PAPER_ROWS[label]);
  });

  it('shows overload reaching point blank, where standard cannot fire at all', () => {
    // The headline the old table hid. Not a derived claim: it is the 0-1 column of two charts.
    const by = (l: string) => modes().lines.find(x => x.label === l)!.cells[0];
    expect(by('Hit (std)')).toBeNull();
    expect(by('Hit (ovld)')).toBe(6);
    expect(by('Damage, ovld')).toBe(16);
  });

  it('is the same table however the weapon happens to be armed', () => {
    // This is the bug fix, stated as a property. The old branch picked a chart off armingType
    // and so could pick the wrong one; there is no longer an armingType to get wrong.
    const base = JSON.stringify(modes());
    for (const arming of ['STANDARD', 'OVERLOAD', 'SPECIAL', 'PROXIMITY', 'ROLLING', null])
      expect(JSON.stringify(modes(arming)), `arming=${arming}`).toBe(base);
  });

  it('shows the proximity numbers at all, which the old branch could never reach', () => {
    // The old code tested mode === 'PROXIMITY'. WeaponArmingType has STANDARD, OVERLOAD,
    // SPECIAL and ROLLING -- no PROXIMITY -- so that arm was dead and a proximity-armed photon
    // silently showed the standard chart and 8 damage instead of 4. Proving the numbers are
    // now present at all is what guards against the same dead-string mistake returning.
    const prox = modes().lines.find(l => l.label === 'Hit (prox)')!;
    expect(prox.cells.filter(c => c !== null)).toEqual([4, 3]);
    const dmg = modes().lines.find(l => l.label === 'Damage, prox')!;
    expect(dmg.cells).toContain(4);
    expect(dmg.cells).not.toContain(8);
  });

  it('keeps a fighter photon on the simple table, which has only the one mode', () => {
    // A fighter has no energy to overload with, so the modes would be a lie. It takes the
    // branch above this one and must keep taking it.
    const fighter = getWeaponFullTable('FighterPhoton-1', null);
    expect(fighter?.modes).toBeUndefined();
    expect(fighter?.bands.length).toBeGreaterThan(0);
  });
});
