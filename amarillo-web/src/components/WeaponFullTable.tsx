import { getWeaponFullTable } from '../weaponDamageTables';

/**
 * A weapon's whole damage table, every range band — the thing the SSD prints beside the ship.
 *
 * Distinct from {@link WeaponDamageTooltip}, which answers "what does this do to the target I
 * have picked, at the range it is at". This answers the question a player asks BEFORE picking a
 * target: what is this weapon for? Asked for in play on 2026-10-09, and reasonably — the paper
 * game gives it away free, because the table is printed on the sheet.
 *
 * Range runs ACROSS and the die face runs DOWN, as the rulebook prints it, so a player who knows
 * the paper tables reads this one without being taught. A hit-chart weapon has one row instead
 * of six and says what it needs to hit.
 */
export default function WeaponFullTable({
  weaponName, armingType, directFire = false, plasmaType = null, anchored = true,
}: {
  weaponName:  string;
  armingType:  string | null;
  directFire?: boolean;
  plasmaType?: string | null;
  /** Absolutely positioned under its parent (hover). False to sit in the flow (a panel). */
  anchored?:   boolean;
}) {
  const table = getWeaponFullTable(weaponName, armingType, directFire, plasmaType);
  // Null rather than an empty box: a drone rack and a scout channel have no damage table, and
  // saying nothing is the honest answer. See getWeaponFullTable.
  if (!table) return null;

  const dieFaces = [1, 2, 3, 4, 5, 6];

  return (
    <div className={anchored ? 'dmg-tooltip dmg-full' : 'dmg-full dmg-full-inline'}>
      <div className="dmg-tooltip-header">
        Range{table.note ? ` — ${table.note}` : ''}
      </div>
      <table className="dmg-tooltip-table dmg-full-table">
        <thead>
          <tr>
            <th>{table.kind === 'roll' ? 'Die' : 'Hit'}</th>
            {table.bands.map(b => <th key={b.label}>{b.label}</th>)}
          </tr>
        </thead>
        <tbody>
          {table.kind === 'roll'
            ? dieFaces.map((face, i) => (
                <tr key={face}>
                  <td className="dmg-full-head">{face}</td>
                  {table.bands.map(b => (
                    <td key={b.label} className={b.damage[i] === 0 ? 'dmg-nil' : undefined}>
                      {b.damage[i]}
                    </td>
                  ))}
                </tr>
              ))
            : (
              <>
                <tr>
                  <td className="dmg-full-head">roll</td>
                  {table.bands.map(b => (
                    <td key={b.label}>
                      {b.hitOn == null ? '—'
                        : `≤${b.hitOn}${b.twoDice ? '*' : ''}`}
                    </td>
                  ))}
                </tr>
                <tr>
                  <td className="dmg-full-head">dmg</td>
                  {table.bands.map(b => (
                    <td key={b.label} className={b.damage[0] === 0 ? 'dmg-nil' : undefined}>
                      {b.damage[0]}
                    </td>
                  ))}
                </tr>
              </>
            )}
        </tbody>
      </table>
      {table.kind === 'hit' && table.bands.some(b => b.twoDice) && (
        <div className="dmg-full-foot">* two dice</div>
      )}
    </div>
  );
}
