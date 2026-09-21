import { useEffect, useMemo, useState } from 'react';
import type { WeaponState } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import { getWeaponDamagePreview } from '../weaponDamageTables';

/**
 * The Fire Orders pad (D6.315 written orders).
 *
 * Giving fire orders used to mean operating the map: find your own ship under a stack of
 * drones, pan to a target without misclicking a different one, then un-tick the weapons you
 * did not want. Every field of the order was entered by clicking a crowded spatial surface
 * that is good at showing where things are and bad at being an input control.
 *
 * B2.0 step 6.D says what the interface should have been all along — "players might give
 * their detailed firing orders to a non-playing referee". Orders are WRITTEN. So this is a
 * pad: three lists and a plan. The map goes back to being the thing you look at.
 *
 * Nothing here decides a rule. Candidates, range, shield facing and which weapons bear all
 * come from /fire-targets, which is the same computation /fire-options performs for a single
 * pair. Damage figures are a preview from weaponDamageTables (the tables the hover tooltip
 * already uses) and the server remains the authority for the actual shot.
 */

/** One row of /fire-targets: a unit this attacker could fire at, with the figures resolved. */
export interface FireCandidate {
  name:          string;
  kind:          'SHIP' | 'DRONE' | 'PLASMA' | 'SHUTTLE' | 'WEASEL';
  range:         number;
  adjustedRange: number;
  shieldNumber:  number;
  weaponsInArc:  string[];
  hasLockOn:     boolean;
  ecmPoints:     number;
  eccm:          number;
  ecmShift:      number;
  ecmSources:    string | null;
}

/** A unit of mine that can be given fire orders — a ship, or an armed shuttle/fighter. */
export interface FiringUnit {
  name:    string;
  isShip:  boolean;
  weapons: WeaponState[];
  /** Shown beside the name so a fighter reads as a fighter, not as a ship. */
  note:    string | null;
}

export interface DraftOrder {
  label:       string;
  shipName:    string;
  targetName:  string | null;
  weaponNames: string[];
  shotModes?:  Record<string, string>;
  range:         number;
  adjustedRange: number;
  shieldNumber:  number;
  useUim:      boolean;
  directFire:  boolean;
  hexCol?:     number;
  hexRow?:     number;
  planetSide?: number;
}

interface Props {
  gameId:      string;
  playerToken: string;
  turn:        number;
  impulse:     number;

  units:        FiringUnit[];
  attackerName: string | null;
  onSelectAttacker: (name: string) => void;

  orders:        DraftOrder[];
  onAddOrder:    (order: DraftOrder) => void;
  onRemoveOrder: (index: number) => void;

  /** Aiming at a place instead (P3.25, P2.311) — hands off to the map hex pick. */
  onStartHexFire: () => void;

  ew:      Record<string, { ecm: number; eccm: number }>;
  onSetEw: (shipName: string, value: { ecm: number; eccm: number }) => void;
  /** Sensor track and current allocation for the selected ship, for the EW stepper. */
  ewLimits: { sensor: number; ecm: number; eccm: number; battery: number } | null;

  /**
   * Whether a declaration is convened. Until one is, there is nothing to seal — the pad is
   * a place to draft, and calling is what obliges everyone else to answer.
   */
  declarationOpen: boolean;
  onCall:   () => void;
  onCommit: () => void;
  onPass:   () => void;
  error:    string | null;
}

/** One shared empty set, so deriving "nothing picked" does not allocate on every render. */
const EMPTY_PICK: Set<string> = new Set();

const KIND_LABEL: Record<FireCandidate['kind'], string> = {
  SHIP:    'ship',
  DRONE:   'drone',
  PLASMA:  'plasma',
  SHUTTLE: 'shuttle',
  WEASEL:  'weasel',
};

const COL: React.CSSProperties = {
  flex: '1 1 14rem',
  minWidth: 0,
  display: 'flex',
  flexDirection: 'column',
  gap: 2,
};

const COL_TITLE: React.CSSProperties = {
  fontSize: '0.7rem',
  letterSpacing: '0.08em',
  textTransform: 'uppercase',
  color: '#8b949e',
  marginBottom: 2,
};

const ROW: React.CSSProperties = {
  display: 'flex',
  alignItems: 'baseline',
  gap: 6,
  padding: '2px 6px',
  background: 'none',
  border: '1px solid transparent',
  color: '#c9d1d9',
  font: 'inherit',
  fontSize: '0.85em',
  textAlign: 'left',
  cursor: 'pointer',
  width: '100%',
};

/**
 * Roughly what a volley of these weapons is worth at this range, averaged over the die.
 *
 * "In range" turns out to be almost always true — a Ph-1 reaches 75 hexes — so the useful
 * figure is not whether a weapon bears but what the shot would do: 4-6 points at range 3
 * against one point on a roll of 1-2 at range 30. An estimate on purpose, and labelled as
 * one; the server rolls the real dice.
 */
function volleyEstimate(weapons: WeaponState[], names: string[],
                        range: number, adjustedRange: number): number | null {
  let total = 0;
  let known = false;
  for (const name of names) {
    const w = weapons.find(x => x.name === name);
    if (!w) continue;
    const rows = getWeaponDamagePreview(w.name, w.armingType, range, adjustedRange, true);
    if (!rows || rows.length === 0) continue;
    known = true;
    total += rows.reduce((sum, r) => sum + r.damage, 0) / rows.length;
  }
  return known ? Math.round(total) : null;
}

export default function FireOrdersPad({
  gameId, playerToken, turn, impulse,
  units, attackerName, onSelectAttacker,
  orders, onAddOrder, onRemoveOrder, onStartHexFire,
  ew, onSetEw, ewLimits,
  declarationOpen, onCall, onCommit, onPass, error,
}: Props) {
  // Both of these are keyed by the attacker they belong to, and read back only when that
  // still matches. Nothing has to be reset when the selection moves: the stale value simply
  // stops being the answer, so there is no render showing the last ship's target.
  const [loaded, setLoaded] = useState<{
      attacker: string | null; rows: FireCandidate[]; error: string | null }>(
      { attacker: null, rows: [], error: null });
  const [sel, setSel] = useState<{ attacker: string | null; target: string | null; picked: Set<string> }>(
      { attacker: null, target: null, picked: new Set() });
  // Loading is not state: it is simply "the answer we hold is for a different ship".
  const answered  = loaded.attacker === attackerName;
  const loading   = !answered;
  const loadError = answered ? loaded.error : null;

  const candidates = answered ? loaded.rows : [];
  const targetName = sel.attacker === attackerName ? sel.target : null;
  const picked     = sel.attacker === attackerName && sel.target === targetName
      ? sel.picked : EMPTY_PICK;

  const attacker = units.find(u => u.name === attackerName) ?? null;
  const target   = candidates.find(c => c.name === targetName) ?? null;

  const pick = (target: string | null, weapons: Set<string>) =>
      setSel({ attacker: attackerName, target, picked: weapons });

  // One call per attacker, not per candidate. Re-asked when the impulse moves, because
  // ranges and arcs change with it.
  useEffect(() => {
    if (!attackerName) return;
    let live = true;
    // The only setState here runs when the fetch resolves, which is the one moment the rows
    // exist. `live` drops an answer whose attacker has already been left behind.
    gameApi.getFireTargets(gameId, playerToken, attackerName)
      .then(rows => { if (live) setLoaded({ attacker: attackerName, rows, error: null }); })
      .catch((e: unknown) => {
        if (live) setLoaded({ attacker: attackerName, rows: [],
            error: e instanceof Error ? e.message : 'Could not list targets' });
      });
    return () => { live = false; };
  }, [gameId, playerToken, attackerName, turn, impulse]);

  /** Weapons this attacker has already promised elsewhere this declaration. */
  const spokenFor = useMemo(() => {
    const map = new Map<string, string>();
    for (const o of orders) {
      if (o.shipName !== attackerName) continue;
      for (const name of o.weaponNames)
        if (!map.has(name))
          map.set(name, o.targetName ?? `hex ${o.hexCol}|${o.hexRow}`);
    }
    return map;
  }, [orders, attackerName]);

  function toggle(name: string) {
    const next = new Set(picked);
    if (next.has(name)) next.delete(name); else next.add(name);
    pick(targetName, next);
  }

  function addOrder() {
    if (!attacker || !target || picked.size === 0) return;
    const names = [...picked];
    onAddOrder({
      label: `${attacker.name} → ${target.name} (${names.length} wpn)`,
      shipName:      attacker.name,
      targetName:    target.name,
      weaponNames:   names,
      range:         target.range,
      adjustedRange: target.adjustedRange,
      shieldNumber:  target.shieldNumber,
      useUim:        false,
      directFire:    false,
    });
    pick(null, new Set());
  }

  const sealed = orders.length;

  return (
    <div className="board-log" style={{ borderColor: '#a78bfa', padding: '8px 12px' }}>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 6 }}>
        <span style={{ color: '#a78bfa', fontWeight: 600 }}>⚔ Fire orders</span>
        <span style={{ fontSize: '0.78em', color: '#8b949e' }}>
          turn {turn}, impulse {impulse} —{' '}
          {declarationOpen
            ? 'sealed together, revealed together (D6.315). Committing nothing is a legal bluff.'
            : 'draft freely; nobody is waiting on you until a declaration is called.'}
        </span>
      </div>

      <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'flex-start' }}>

        {/* ---------------------------------------------------- my units */}
        <div style={COL}>
          <div style={COL_TITLE}>Your units</div>
          {units.length === 0 && (
            <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Nothing of yours on the map.</div>
          )}
          {units.map(u => {
            const mine = orders.filter(o => o.shipName === u.name);
            const isSel = u.name === attackerName;
            return (
              <button
                key={u.name}
                style={{
                  ...ROW,
                  borderColor: isSel ? '#a78bfa' : 'transparent',
                  background: isSel ? 'rgba(167,139,250,0.10)' : 'none',
                }}
                onClick={() => onSelectAttacker(u.name)}
              >
                <span style={{ fontWeight: 600, color: mine.length ? '#e6edf3' : '#f0c040' }}>
                  {u.name}
                </span>
                {u.note && <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{u.note}</span>}
                <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                  {mine.length === 0 ? 'holds' : `${mine.length} ord`}
                </span>
              </button>
            );
          })}
        </div>

        {/* ---------------------------------------------------- candidates */}
        <div style={COL}>
          <div style={COL_TITLE}>
            {attacker ? `${attacker.name} can fire on` : 'Can fire on'}
          </div>
          {!attacker && (
            <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Pick one of your units.</div>
          )}
          {attacker && loading && (
            <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Looking…</div>
          )}
          {attacker && !loading && loadError && (
            <div style={{ fontSize: '0.85em', color: '#f85149' }}>{loadError}</div>
          )}
          {attacker && !loading && !loadError && candidates.length === 0 && (
            <div style={{ fontSize: '0.85em', color: '#8b949e' }}>
              Nothing bears. It can still change EW, or hold fire.
            </div>
          )}
          {attacker && !loading && candidates.map(c => {
            const isSel = c.name === targetName;
            const already = orders.some(o => o.shipName === attacker.name && o.targetName === c.name);
            const worth = volleyEstimate(attacker.weapons, c.weaponsInArc, c.range, c.adjustedRange);
            return (
              <button
                key={c.name}
                style={{
                  ...ROW,
                  borderColor: isSel ? '#a78bfa' : 'transparent',
                  background: isSel ? 'rgba(167,139,250,0.10)' : 'none',
                  opacity: already ? 0.55 : 1,
                }}
                onClick={() => pick(c.name, new Set())}
                title={already ? 'already has an order from this unit this segment' : undefined}
              >
                <span style={{ color: '#e6edf3' }}>{c.name}</span>
                <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{KIND_LABEL[c.kind]}</span>
                <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                  r{c.range}
                  {c.kind === 'SHIP' && c.shieldNumber > 0 ? ` · sh#${c.shieldNumber}` : ''}
                  {' · '}{c.weaponsInArc.length} bear
                  {worth != null ? ` · ~${worth}` : ''}
                </span>
              </button>
            );
          })}
          {attacker && attacker.isShip && (
            <button style={{ ...ROW, color: '#8b949e', marginTop: 4 }} onClick={onStartHexFire}>
              a hex on the map — clear a path or bombard (P3.25, P2.311)
            </button>
          )}
        </div>

        {/* ---------------------------------------------------- weapons */}
        <div style={COL}>
          <div style={COL_TITLE}>
            {target ? `${attacker?.name} → ${target.name}` : 'Weapons'}
          </div>
          {!target && (
            <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Pick a target.</div>
          )}
          {target && attacker && (
            <>
              <div style={{ display: 'flex', gap: 6, marginBottom: 2 }}>
                <button className="secondary" style={{ padding: '0 6px', fontSize: '0.72rem' }}
                        onClick={() => pick(targetName,
                            new Set(target.weaponsInArc.filter(n => !spokenFor.has(n))))}>
                  all bearing
                </button>
                <button className="secondary" style={{ padding: '0 6px', fontSize: '0.72rem' }}
                        onClick={() => pick(targetName, new Set())}>
                  none
                </button>
              </div>
              {target.weaponsInArc.length === 0 && (
                <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Nothing bears on it.</div>
              )}
              {target.weaponsInArc.map(name => {
                const w = attacker.weapons.find(x => x.name === name);
                const elsewhere = spokenFor.get(name);
                const unavailable = elsewhere
                  ? `→ ${elsewhere}`
                  : w && w.isHeavy && !w.armed ? 'unarmed'
                  : w && !w.readyToFire ? 'on cooldown'
                  : null;
                const rows = w
                  ? getWeaponDamagePreview(w.name, w.armingType, target.range, target.adjustedRange, true)
                  : null;
                const best = rows && rows.length ? Math.max(...rows.map(r => r.damage)) : null;
                return (
                  <label key={name} style={{ ...ROW, cursor: unavailable ? 'default' : 'pointer' }}>
                    <input
                      type="checkbox"
                      disabled={!!unavailable}
                      checked={picked.has(name)}
                      onChange={() => toggle(name)}
                    />
                    <span style={{ color: unavailable ? '#8b949e' : '#e6edf3' }}>{name}</span>
                    {w?.arcLabel && (
                      <span style={{ color: '#8b949e', fontSize: '0.9em' }}>[{w.arcLabel}]</span>
                    )}
                    <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                      {unavailable ?? (best != null ? `up to ${best}` : '')}
                    </span>
                  </label>
                );
              })}
              <button
                disabled={picked.size === 0}
                style={{ marginTop: 4 }}
                onClick={addOrder}
              >
                Add order ({picked.size})
              </button>
            </>
          )}
        </div>
      </div>

      {/* ---------------------------------------------------- EW for the selected ship */}
      {attacker && ewLimits && ewLimits.sensor > 0 && (() => {
        const cur = ew[attacker.name] ?? { ecm: ewLimits.ecm, eccm: ewLimits.eccm };
        const added = Math.max(0, cur.ecm - ewLimits.ecm) + Math.max(0, cur.eccm - ewLimits.eccm);
        const step = (field: 'ecm' | 'eccm', delta: number) => {
          const next = { ...cur, [field]: Math.max(0, cur[field] + delta) };
          if (next.ecm + next.eccm <= ewLimits.sensor)
            onSetEw(attacker.name, next);
        };
        return (
          <div style={{ fontSize: '0.85em', marginTop: 8, color: '#c9d1d9' }}>
            <span style={COL_TITLE}>EW — {attacker.name}</span>{'  '}
            ECM <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('ecm', -1)}>−</button>
            {' '}{cur.ecm}{' '}
            <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('ecm', 1)}>+</button>
            {'   '}ECCM <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('eccm', -1)}>−</button>
            {' '}{cur.eccm}{' '}
            <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('eccm', 1)}>+</button>
            <span style={{ color: '#8b949e' }}>
              {'   '}(sensor {ewLimits.sensor}, battery {ewLimits.battery}
              {added > 0 ? `, +${added} costs ${added} battery` : ''}) — drops are lost for the turn
            </span>
          </div>
        );
      })()}

      {/* ---------------------------------------------------- the plan */}
      <div style={{ marginTop: 8, borderTop: '1px solid #30363d', paddingTop: 6 }}>
        <div style={COL_TITLE}>The plan</div>
        {units.map(u => {
          const mine = orders.filter(o => o.shipName === u.name);
          return (
            <div key={u.name} style={{ fontSize: '0.85em', color: '#c9d1d9' }}>
              <span style={{ color: mine.length ? '#e6edf3' : '#8b949e' }}>{u.name}</span>
              {mine.length === 0 ? (
                <span style={{ color: '#8b949e' }}> — holds fire</span>
              ) : (
                mine.map(o => (
                  <span key={orders.indexOf(o)} style={{ marginLeft: 8 }}>
                    {o.targetName ?? `hex ${o.hexCol}|${o.hexRow}`} ({o.weaponNames.length}){' '}
                    <button className="secondary" style={{ padding: '0 5px' }}
                            onClick={() => onRemoveOrder(orders.indexOf(o))}>✕</button>
                  </span>
                ))
              )}
            </div>
          );
        })}
        {Object.entries(ew).length > 0 && (
          <div style={{ fontSize: '0.85em', color: '#8b949e', marginTop: 2 }}>
            EW changes: {Object.entries(ew)
              .map(([name, v]) => `${name} ECM ${v.ecm} / ECCM ${v.eccm}`)
              .join('; ')}
          </div>
        )}
      </div>

      {error && <div style={{ color: '#f85149', fontSize: '0.85em', marginTop: 4 }}>{error}</div>}

      <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
        {declarationOpen ? (
          <>
            <button onClick={onCommit}>Commit orders ({sealed})</button>
            <button className="secondary" onClick={onPass}>Pass</button>
          </>
        ) : (
          <>
            <button onClick={onCall}>Call for fire declaration</button>
            <span style={{ fontSize: '0.78em', color: '#8b949e' }}>
              — everyone stops and commits. Adding an order calls one too; calling with
              nothing drafted is a bluff, and spends the impulse for everybody.
            </span>
          </>
        )}
      </div>
    </div>
  );
}
