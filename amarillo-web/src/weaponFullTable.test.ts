import { describe, expect, it } from 'vitest';
import type { RangeBand } from './weaponDamageTables';
import { getWeaponDamagePreview, getWeaponFullTable } from './weaponDamageTables';

/**
 * The printed table and the firing preview must not drift apart.
 *
 * `getWeaponFullTable` collapses the same per-hex tables `getWeaponDamagePreview` reads one
 * column of. That is the point of collapsing rather than re-typing the bands — but it is only
 * worth anything if the two actually agree, so the central test here asks the preview for a
 * handful of ranges and insists the band covering each range says the same thing.
 *
 * These are a MIRROR of the Java weapons (see the header of weaponDamageTables.ts), so none of
 * this is authority — the server decides every shot. What it protects is a player reading a
 * table that tells them something the game will not do.
 */
describe('getWeaponFullTable', () => {

  /** The band whose label covers this range, or undefined. */
  function bandFor(bands: RangeBand[], range: number) {
    return bands.find(b => {
      const [lo, hi] = b.label.split('–');
      return hi === undefined
        ? Number(lo) === range
        : range >= Number(lo) && range <= Number(hi);
    });
  }

  it('gives the phaser-1 table the rulebook prints', () => {
    const t = getWeaponFullTable('Phaser1-1', null);
    expect(t).not.toBeNull();
    expect(t!.kind).toBe('roll');
    // 76 hex columns collapse to 11 bands; range 0 and range 1 differ, 6-8 share.
    expect(t!.bands.length).toBe(11);
    expect(t!.bands[0].label).toBe('0');
    expect(t!.bands[0].damage).toEqual([9, 8, 7, 6, 5, 4]);
    expect(t!.bands[6].label).toBe('6–8');
    expect(t!.bands[6].damage).toEqual([4, 3, 3, 2, 1, 0]);
    expect(t!.bands[t!.bands.length - 1].label).toBe('51–75');
  });

  it('agrees with the per-range preview at every range it covers', () => {
    for (const name of ['Phaser1-1', 'Phaser2-1', 'Phaser3-1', 'PhaserG-1']) {
      const t = getWeaponFullTable(name, null);
      expect(t, name).not.toBeNull();
      for (const band of t!.bands) {
        const lo = Number(band.label.split('–')[0]);
        const preview = getWeaponDamagePreview(name, null, lo, lo);
        expect(preview, `${name} at range ${lo}`).not.toBeNull();
        expect(preview!.map(r => r.damage), `${name} at range ${lo}`)
          .toEqual([...band.damage]);
      }
    }
  });

  it('spot-checks the preview against the band that covers it, not just the band start', () => {
    const t = getWeaponFullTable('Phaser1-1', null)!;
    for (const range of [0, 1, 5, 7, 12, 20, 40, 60, 75]) {
      const band = bandFor(t.bands, range);
      expect(band, `no band covers range ${range}`).toBeDefined();
      const preview = getWeaponDamagePreview('Phaser1-1', null, range, range)!;
      expect(preview.map(r => r.damage), `range ${range}`).toEqual([...band!.damage]);
    }
  });

  it('bands a hit-chart weapon by to-hit AND damage together', () => {
    const t = getWeaponFullTable('Disruptor-A', null);
    expect(t!.kind).toBe('hit');
    // Every band must differ from its neighbour in one of the two, or it would not be a band.
    for (let i = 1; i < t!.bands.length; i++) {
      const a = t!.bands[i - 1], b = t!.bands[i];
      expect(a.hitOn !== b.hitOn || a.damage[0] !== b.damage[0],
             `bands ${a.label} and ${b.label} are identical`).toBe(true);
    }
  });

  it('reads the hellbore straight off its printed bands', () => {
    const t = getWeaponFullTable('Hellbore-A', null)!;
    expect(t.bands.length).toBe(7);
    expect(t.bands[0].label).toBe('0–1');
    expect(t.bands[0].twoDice).toBe(true);
    expect(t.bands[0].hitOn).toBe(11);
    expect(t.bands[0].damage).toEqual([20]);           // environmental
    const df = getWeaponFullTable('Hellbore-A', null, true)!;
    expect(df.bands[0].damage).toEqual([10]);          // direct fire is half
  });

  it('halves the torpedo strength for a plasma bolt (FP8.43)', () => {
    const t = getWeaponFullTable('PlasmaLauncher-A', null, false, 'S')!;
    expect(t.note).toBe('type-S bolt');
    expect(t.bands[0].damage).toEqual([15]);           // the type-S is 30 at range 0
  });

  it('distinguishes a fighter disruptor from a ship one, as the preview does', () => {
    const fighter = getWeaponFullTable('FighterDisruptor-1', null)!;
    const ship    = getWeaponFullTable('Disruptor-A', null)!;
    const reach = (t: typeof ship) => Number(t.bands[t.bands.length - 1].label.split('–').pop());
    expect(reach(fighter)).toBeLessThan(reach(ship));
  });

  it('says nothing rather than something wrong for a weapon it does not know', () => {
    expect(getWeaponFullTable('ScoutChannel-1', null)).toBeNull();
    expect(getWeaponFullTable('DroneRack-Rack 1', null)).toBeNull();
  });

  it('drops trailing dead range instead of printing a band of zeroes', () => {
    for (const name of ['Phaser1-1', 'Phaser3-1', 'Disruptor-A']) {
      const t = getWeaponFullTable(name, null)!;
      const last = t.bands[t.bands.length - 1];
      expect(last.damage.some(d => d > 0), `${name} ends on an all-zero band`).toBe(true);
    }
  });
});
