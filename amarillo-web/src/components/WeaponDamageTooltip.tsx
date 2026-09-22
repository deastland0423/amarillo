import type { WeaponState } from '../types/gameState';
import { getPlasmaBoltPreview, getWeaponDamagePreview } from '../weaponDamageTables';

/**
 * What a weapon does at this range, die by die.
 *
 * Its own file because two panels want it: the sidebar fire panel and the Fire Orders pad.
 * A second copy would be two renderings of the same tables, free to disagree — and the
 * tables are a mirror of the Java weapons already, so one mirror is quite enough.
 *
 * Anchored by the caller: it positions itself absolutely at the bottom-left of whatever it
 * sits in, so that element needs position: relative.
 */
export default function WeaponDamageTooltip({
  w, range, adjustedRange, directFire,
}: {
  w:             WeaponState;
  range:         number;
  adjustedRange: number;
  directFire:    boolean;
}) {
  const rows = w.launcherType
    ? getPlasmaBoltPreview(w.plasmaType, range)
    : getWeaponDamagePreview(w.name, w.armingType, range, adjustedRange, directFire);
  if (!rows) return null;

  const isRollTable = rows.length === 6;
  const label       = w.launcherType
    ? ` — ${w.plasmaType ?? w.launcherType} bolt`
    : w.armingType && w.armingType !== 'STANDARD'
      ? ` (${w.armingType.toLowerCase()})`
      : '';

  return (
    <div className="dmg-tooltip">
      <div className="dmg-tooltip-header">Range {range}{label}</div>
      {isRollTable ? (
        <table className="dmg-tooltip-table">
          <thead>
            <tr><th>Die</th><th>Dmg</th></tr>
          </thead>
          <tbody>
            {rows.map(r => (
              <tr key={r.roll} className={r.damage === 0 ? 'dmg-zero' : ''}>
                <td>{r.roll}</td>
                <td>{r.damage}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <div className="dmg-tooltip-rows">
          {rows.map(r => (
            <div key={r.roll} className={`dmg-tooltip-row ${r.damage === 0 ? 'dmg-zero' : ''}`}>
              <span className="dmg-roll-label">{r.roll}</span>
              <span className="dmg-val">{r.damage}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
