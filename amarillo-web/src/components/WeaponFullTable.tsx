import { getWeaponFullTable, type ModeLine } from '../weaponDamageTables';

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
/**
 * A to-hit cell, written as the winning range of the roll: "1-4" on one die, "2-8" on two.
 *
 * The sheet's own convention, and it beats a bare number because it shows the SIZE of the
 * chance rather than its edge -- 1-4 is visibly twice 1-2. The asterisk marks two dice, where
 * the span is not the probability: 2-8 on 2d6 is far better than 8 of 12 would suggest.
 */
function hitCell(line: ModeLine, v: number): string {
  return `${line.twoDice ? 2 : 1}\u2013${v}${line.twoDice ? '*' : ''}`;
}

export default function WeaponFullTable({
  weaponName, armingType, directFire = false, plasmaType = null, anchored = true,
  uim = false, derfacs = false,
}: {
  weaponName:  string;
  armingType:  string | null;
  directFire?: boolean;
  plasmaType?: string | null;
  /** Absolutely positioned under its parent (hover). False to sit in the flow (a panel). */
  anchored?:   boolean;
  /** The FIRING SHIP's targeting kit. A disruptor prints an extra hit line for each. */
  uim?:        boolean;
  derfacs?:    boolean;
}) {
  const table = getWeaponFullTable(weaponName, armingType, directFire, plasmaType,
                                   { uim, derfacs });
  // Null rather than an empty box: a drone rack and a scout channel have no damage table, and
  // saying nothing is the honest answer. See getWeaponFullTable.
  if (!table) return null;

  const dieFaces = [1, 2, 3, 4, 5, 6];

  // The printed multi-mode layout: range across, one labelled line per mode of fire down. Used
  // where a weapon has modes worth comparing side by side -- a disruptor's UIM gains sit in
  // bands its standard line never reaches, so showing one at a time hid the whole point.
  if (table.modes)
    return (
      <div className={anchored ? 'dmg-tooltip dmg-full' : 'dmg-full dmg-full-inline'}>
        <div className="dmg-tooltip-header">Range</div>
        <table className="dmg-tooltip-table dmg-full-table">
          <thead>
            <tr>
              <th>Mode</th>
              {table.modes.labels.map(l => <th key={l}>{l}</th>)}
            </tr>
          </thead>
          <tbody>
            {table.modes.lines.map(line => (
              <tr key={line.label}>
                <td className="dmg-full-head">{line.label}</td>
                {line.cells.map((v, i) => (
                  <td key={table.modes!.labels[i]}
                      className={v === null || v === 0 ? 'dmg-nil' : undefined}>
                    {v === null ? 'NA' : line.kind === 'hit' ? hitCell(line, v) : v}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
        {table.modes.lines.some(l => l.twoDice) && (
          <div className="dmg-full-foot">* two dice</div>
        )}
      </div>
    );

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
