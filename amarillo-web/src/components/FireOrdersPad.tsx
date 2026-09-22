import { useEffect, useMemo, useState } from 'react';
import type { WeaponState } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import { useDraggable } from '../hooks/useDraggable';
import WeaponDamageTooltip from './WeaponDamageTooltip';
import { getPlasmaBoltPreview, getWeaponDamagePreview } from '../weaponDamageTables';

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
  /**
   * Which of YOUR units this seeker is bearing down on, or null — inferred from its public
   * position and facing, never from its target, which identification (G4.2) is the way to
   * learn. Absent on ships.
   */
  closingOn?:    string | null;
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
  /** Ships only: a working UIM offers its targeting to a volley (D6.51). */
  uimFunctional?: boolean;
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

  /**
   * The chosen target, held by the board rather than here, so clicking an enemy on the map is
   * a shortcut INTO the pad rather than a second way to compose an order.
   */
  targetName:     string | null;
  onSelectTarget: (name: string | null) => void;

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
  /** Units under the cursor on the map, so the matching rows can light up. */
  hoveredOnMap: string[];
  /** A row was hovered here; the map rings that unit. */
  onHoverCandidate: (name: string | null) => void;

  declarationOpen: boolean;
  onCall:   () => void;
  onCommit: () => void;
  onPass:   () => void;
  error:    string | null;
}

/**
 * Everything chosen for ONE volley, keyed by the attacker and target it belongs to. Keyed
 * rather than reset, so moving the selection discards it without an effect to do the clearing
 * — and so there is never a render showing the last target's weapons.
 */
interface Sel {
  attacker: string | null;
  target:   string | null;
  picked:   Set<string>;
  /** Weapon name -> shots this impulse, for weapons that may fire more than once. */
  shots:    Record<string, number>;
  /** Weapon name -> SINGLE or DOUBLE, for a fighter's fusion. */
  modes:    Record<string, 'SINGLE' | 'DOUBLE'>;
  useUim:   boolean;
  /** A Hellbore firing in direct-fire mode: half damage, the facing shield (E10.7). */
  hellbore: boolean;
}

const EMPTY_PICK:  Set<string> = new Set();
const EMPTY_SHOTS: Record<string, number> = {};
const EMPTY_MODES: Record<string, 'SINGLE' | 'DOUBLE'> = {};
const EMPTY_SEL: Sel = {
  attacker: null, target: null, picked: EMPTY_PICK,
  shots: EMPTY_SHOTS, modes: EMPTY_MODES, useUim: false, hellbore: false,
};

/**
 * Likewise for "no candidates": a fresh [] changes identity on every render, which makes the
 * grouping memo below re-run every time and defeats its own purpose.
 */
const EMPTY_ROWS: FireCandidate[] = [];

const KIND_LABEL: Record<FireCandidate['kind'], string> = {
  SHIP:    'ship',
  DRONE:   'drone',
  PLASMA:  'plasma',
  SHUTTLE: 'shuttle',
  WEASEL:  'weasel',
};

const PANEL: React.CSSProperties = {
  position: 'fixed',
  width: 'min(94vw, 820px)',
  zIndex: 45,
  background: '#161b22',
  border: '1px solid #a78bfa',
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
};

const HEADER: React.CSSProperties = {
  cursor: 'grab',
  display: 'flex',
  alignItems: 'baseline',
  gap: 10,
  marginBottom: 6,
};

/** The lists scroll; the footer with Commit and Pass never does. */
const BODY: React.CSSProperties = {
  maxHeight: '46vh',
  overflowY: 'auto',
  overflowX: 'hidden',
};

const POSITION_KEY = 'amarillo-fire-pad-position';

function savedPosition(): { left: number; top: number } {
  const fallback = {
    left: 16,
    top: Math.max(60, (typeof window === 'undefined' ? 800 : window.innerHeight) - 470),
  };
  try {
    const raw = localStorage.getItem(POSITION_KEY);
    if (!raw) return fallback;
    const p = JSON.parse(raw) as { left?: number; top?: number };
    if (typeof p.left !== 'number' || typeof p.top !== 'number') return fallback;
    // A window that shrank since last time must not strand the panel off screen.
    return {
      left: Math.min(p.left, Math.max(0, window.innerWidth - 120)),
      top: Math.min(p.top, Math.max(0, window.innerHeight - 60)),
    };
  } catch {
    return fallback;   // private window, or storage blocked
  }
}

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
 * The damage rows for one weapon at this range.
 *
 * A plasma launcher selected for direct fire is a BOLT (half the torpedo's strength, its own
 * hit chart, max range 30) and reads from a different table — the same split the hover
 * tooltip makes. Without this a launcher showed no figure at all and was quietly missing
 * from the volley estimate.
 */
function previewFor(w: WeaponState, range: number, adjustedRange: number) {
  return w.launcherType
    ? getPlasmaBoltPreview(w.plasmaType, range)
    : getWeaponDamagePreview(w.name, w.armingType, range, adjustedRange, true);
}

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
    const rows = previewFor(w, range, adjustedRange);
    if (!rows || rows.length === 0) continue;
    known = true;
    total += rows.reduce((sum, r) => sum + r.damage, 0) / rows.length;
  }
  return known ? Math.round(total) : null;
}

export default function FireOrdersPad({
  gameId, playerToken, turn, impulse,
  units, attackerName, onSelectAttacker, targetName, onSelectTarget,
  orders, onAddOrder, onRemoveOrder, onStartHexFire,
  hoveredOnMap, onHoverCandidate,
  ew, onSetEw, ewLimits,
  declarationOpen, onCall, onCommit, onPass, error,
}: Props) {
  // Both of these are keyed by the attacker they belong to, and read back only when that
  // still matches. Nothing has to be reset when the selection moves: the stale value simply
  // stops being the answer, so there is no render showing the last ship's target.
  const [loaded, setLoaded] = useState<{
      attacker: string | null; rows: FireCandidate[]; error: string | null }>(
      { attacker: null, rows: [], error: null });
  const [sel, setSel] = useState<Sel>(EMPTY_SEL);
  const [collapsed, setCollapsed] = useState(false);
  const [hoveredWeapon, setHoveredWeapon] = useState<string | null>(null);
  const drag = useDraggable(savedPosition());

  // Remembered across impulses: this panel reappears 32 times a turn, and one that keeps
  // landing back over the part of the map you just moved it off is its own annoyance.
  useEffect(() => {
    try { localStorage.setItem(POSITION_KEY, JSON.stringify(drag.position)); }
    catch { /* storage blocked; the panel simply forgets */ }
  }, [drag.position]);

  // Loading is not state: it is simply "the answer we hold is for a different ship".
  const answered  = loaded.attacker === attackerName;
  const loading   = !answered;
  const loadError = answered ? loaded.error : null;

  const candidates = answered ? loaded.rows : EMPTY_ROWS;
  const onTarget   = sel.attacker === attackerName && sel.target === targetName;
  const picked     = onTarget ? sel.picked : EMPTY_PICK;
  const shots      = onTarget ? sel.shots : EMPTY_SHOTS;
  const modes      = onTarget ? sel.modes : EMPTY_MODES;
  const useUim     = onTarget && sel.useUim;
  const hellbore   = onTarget && sel.hellbore;

  const attacker = units.find(u => u.name === attackerName) ?? null;
  const target   = candidates.find(c => c.name === targetName) ?? null;

  /** Replace the selection. Changing target or attacker drops every per-volley choice. */
  const pick = (target: string | null, weapons: Set<string>) => {
    onSelectTarget(target);
    setSel({ ...EMPTY_SEL, attacker: attackerName, target, picked: weapons });
  };

  const amend = (patch: Partial<Sel>) =>
      setSel({ ...sel, attacker: attackerName, target: targetName,
               picked, shots, modes, useUim, hellbore, ...patch });

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

  /**
   * How much of the WHOLE fleet is already aimed at each target, across every unit's orders.
   *
   * This is the number the old sidebar panel could never show, because it only ever knew
   * about one ship. With fifteen drones inbound the hard part is not telling them apart, it
   * is not putting four phasers into one while another sails through untouched.
   */
  const aimedAt = useMemo(() => {
    const m = new Map<string, { weapons: number; units: string[] }>();
    for (const o of orders) {
      const key = o.targetName ?? `hex ${o.hexCol}|${o.hexRow}`;
      const row = m.get(key) ?? { weapons: 0, units: [] };
      row.weapons += o.weaponNames.length;
      if (!row.units.includes(o.shipName)) row.units.push(o.shipName);
      m.set(key, row);
    }
    return m;
  }, [orders]);

  /**
   * Ships first, then seekers gathered by which of your units they are CLOSING ON — the
   * word matters: their real targets are hidden until identified (G4.2), and this is read
   * off the board from where they are and where they point.
   *
   * Fifteen inbound drones as fifteen rows of names is unreadable; as "closing on Kongo (4)"
   * it is a picture. Range order within each group is preserved from the server, which is
   * the priority a player actually uses.
   */
  const groupedCandidates = useMemo(() => {
    const ships = candidates.filter(c => c.kind === 'SHIP');
    const seekers = candidates.filter(c => c.kind !== 'SHIP');
    const groups: { label: string; rows: FireCandidate[] }[] = [];

    // No ceremony when there is nothing to organise: one flat list reads better.
    if (seekers.length === 0)
      return [{ label: '', rows: ships }];

    if (ships.length > 0) groups.push({ label: 'ships', rows: ships });

    const byUnit = new Map<string, FireCandidate[]>();
    const elsewhere: FireCandidate[] = [];
    for (const c of seekers) {
      if (c.closingOn) {
        const rows = byUnit.get(c.closingOn) ?? [];
        rows.push(c);
        byUnit.set(c.closingOn, rows);
      } else {
        elsewhere.push(c);
      }
    }
    // Most-threatened unit first: the group whose nearest seeker is nearest.
    const units = [...byUnit.entries()].sort(
      (a, b) => (a[1][0]?.range ?? 99) - (b[1][0]?.range ?? 99));
    for (const [unit, rows] of units)
      groups.push({ label: `closing on ${unit}`, rows });
    if (elsewhere.length > 0)
      groups.push({ label: 'elsewhere', rows: elsewhere });
    return groups;
  }, [candidates]);

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
    amend({ picked: next });
  }

  /** How many shots this weapon will actually take, capped by what it has left this turn. */
  function shotsFor(w: WeaponState): number {
    if (!(w.minImpulseGap === 0 && w.maxShotsPerTurn > 1)) return 1;
    const left = Math.max(1, w.maxShotsPerTurn - w.shotsThisTurn);
    return Math.min(shots[w.name] ?? 1, left);
  }

  function addOrder() {
    if (!attacker || !target || picked.size === 0) return;
    // A weapon firing more than once appears once per shot: the wire format counts shots by
    // repetition, which is how the sidebar has always sent them.
    const names = [...picked].flatMap(name => {
      const w = attacker.weapons.find(x => x.name === name);
      return w ? Array(shotsFor(w)).fill(name) as string[] : [name];
    });
    const shotModes: Record<string, string> = {};
    for (const name of picked)
      if (attacker.weapons.find(x => x.name === name)?.chargesRemaining !== undefined)
        shotModes[name] = modes[name] ?? 'SINGLE';

    onAddOrder({
      label: `${attacker.name} → ${target.name} (${names.length} wpn)`,
      shipName:      attacker.name,
      targetName:    target.name,
      weaponNames:   names,
      shotModes:     Object.keys(shotModes).length > 0 ? shotModes : undefined,
      range:         target.range,
      adjustedRange: target.adjustedRange,
      shieldNumber:  target.shieldNumber,
      useUim,
      directFire:    hellbore,
    });
    pick(null, new Set());
  }

  const sealed = orders.length;

  return (
    <div style={{ ...PANEL, left: drag.position.left, top: drag.position.top }}>
      <div style={HEADER} {...drag.handleProps} title="Drag to move">
        <span style={{ color: '#a78bfa', fontWeight: 600 }}>⚔ Fire orders</span>
        <span style={{ fontSize: '0.78em', color: '#8b949e' }}>
          turn {turn}, impulse {impulse} —{' '}
          {declarationOpen
            ? 'sealed together, revealed together (D6.315). Committing nothing is a legal bluff.'
            : 'draft freely; nobody is waiting on you until a declaration is called.'}
        </span>
        <button
          className="secondary"
          style={{ padding: '0 8px', marginLeft: 'auto' }}
          onClick={() => setCollapsed(c => !c)}
          title={collapsed ? 'Show the pad' : 'Collapse — Commit and Pass stay available'}
        >
          {collapsed ? '▸' : '▾'}
        </button>
      </div>

      {!collapsed && (
      <div style={BODY}>
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
          {attacker && !loading && groupedCandidates.map(group => (
            <div key={group.label}>
              {group.label && (
                <div style={{ ...COL_TITLE, marginTop: 6, color: '#6e7681' }}>
                  {group.label} ({group.rows.length})
                </div>
              )}
              {group.rows.map(c => {
            const isSel = c.name === targetName;
            const already = orders.some(o => o.shipName === attacker.name && o.targetName === c.name);
            const worth = volleyEstimate(attacker.weapons, c.weaponsInArc, c.range, c.adjustedRange);
            const aimed = aimedAt.get(c.name);
            const lit = hoveredOnMap.includes(c.name);
            return (
              <button
                key={c.name}
                style={{
                  ...ROW,
                  borderColor: isSel ? '#a78bfa' : lit ? '#6e5cb8' : 'transparent',
                  background: isSel ? 'rgba(167,139,250,0.10)'
                            : lit ? 'rgba(167,139,250,0.06)' : 'none',
                  opacity: already ? 0.55 : 1,
                }}
                onClick={() => pick(c.name, new Set())}
                onMouseEnter={() => onHoverCandidate(c.name)}
                onMouseLeave={() => onHoverCandidate(null)}
                title={already ? 'already has an order from this unit this segment' : undefined}
              >
                <span style={{ color: '#e6edf3' }}>{c.name}</span>
                <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{KIND_LABEL[c.kind]}</span>
                {aimed && (
                  <span
                    style={{ color: '#f0c040', fontSize: '0.9em', whiteSpace: 'nowrap' }}
                    title={`already aimed here: ${aimed.units.join(', ')}`}
                  >
                    ◀{aimed.weapons}
                  </span>
                )}
                <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                  r{c.range}
                  {c.kind === 'SHIP' && c.shieldNumber > 0 ? ` · sh#${c.shieldNumber}` : ''}
                  {' · '}{c.weaponsInArc.length} bear
                  {worth != null ? ` · ~${worth}` : ''}
                </span>
              </button>
            );
          })}
            </div>
          ))}
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
          {!target && targetName && (
            <div style={{ fontSize: '0.85em', color: '#f0c040' }}>
              {attacker?.name} has nothing that bears on {targetName}.
            </div>
          )}
          {!target && !targetName && (
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
                return (
                  <label
                    key={name}
                    // position: relative anchors the tooltip, which places itself at the
                    // bottom-left of whatever it sits in.
                    style={{ ...ROW, position: 'relative',
                             cursor: unavailable ? 'default' : 'pointer' }}
                    onMouseEnter={() => setHoveredWeapon(name)}
                    onMouseLeave={() => setHoveredWeapon(null)}
                  >
                    <input
                      type="checkbox"
                      disabled={!!unavailable}
                      checked={picked.has(name)}
                      onChange={() => toggle(name)}
                    />
                    <span style={{ color: unavailable ? '#8b949e' : '#e6edf3' }}>{name}</span>
                    {w?.launcherType && (
                      <span
                        style={{ color: '#f0a050', fontSize: '0.9em' }}
                        title={'Fires as a direct-fire BOLT: half the torpedo’s strength, '
                             + 'its own hit chart, maximum range 30. This does not launch the '
                             + 'torpedo — launching is an Activity-phase action.'}
                      >
                        bolt
                      </span>
                    )}
                    {w?.arcLabel && (
                      <span style={{ color: '#8b949e', fontSize: '0.9em' }}>[{w.arcLabel}]</span>
                    )}
                    {w?.addCapacity != null && (
                      <span style={{ color: '#50d0f0', fontSize: '0.9em', whiteSpace: 'nowrap' }}
                            title="anti-drone shots loaded / capacity (reloads available)">
                        {w.addShots}/{w.addCapacity}
                        {(w.addReloads ?? 0) > 0 ? ` (+${w.addReloads})` : ''}
                      </span>
                    )}
                    <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                      {unavailable ?? ''}
                    </span>
                    {/* Shots this impulse, for a weapon that may fire more than once
                        (a phaser with no minimum gap). Only once it is picked. */}
                    {w && picked.has(name) && w.minImpulseGap === 0 && w.maxShotsPerTurn > 1 && (
                      <span style={{ display: 'flex', alignItems: 'center', gap: 3 }}
                            onClick={e => e.preventDefault()}>
                        <button className="secondary" style={{ padding: '0 5px' }}
                                onClick={e => { e.preventDefault();
                                  amend({ shots: { ...shots, [name]: Math.max(1, shotsFor(w) - 1) } }); }}>
                          −
                        </button>
                        <span style={{ minWidth: 16, textAlign: 'center' }}>{shotsFor(w)}×</span>
                        <button className="secondary" style={{ padding: '0 5px' }}
                                onClick={e => { e.preventDefault();
                                  amend({ shots: { ...shots, [name]:
                                    Math.min(w.maxShotsPerTurn - w.shotsThisTurn, shotsFor(w) + 1) } }); }}>
                          +
                        </button>
                      </span>
                    )}

                    {/* A fighter's fusion may fire both charges at once (J-section). */}
                    {w && picked.has(name) && w.chargesRemaining !== undefined && (
                      <span style={{ display: 'flex', gap: 3 }} onClick={e => e.preventDefault()}>
                        {(['SINGLE', 'DOUBLE'] as const).map(m => (
                          <button
                            key={m}
                            className={(modes[name] ?? 'SINGLE') === m ? '' : 'secondary'}
                            style={{ padding: '0 5px', fontSize: '0.72rem' }}
                            disabled={m === 'DOUBLE' && (w.chargesRemaining ?? 0) < 2}
                            onClick={e => { e.preventDefault();
                              amend({ modes: { ...modes, [name]: m } }); }}
                          >
                            {m === 'SINGLE' ? '1×' : '2×'}
                          </button>
                        ))}
                      </span>
                    )}

                    {/* The whole table, die by die — a single "up to N" was a worse
                        summary of it than the thing itself. */}
                    {hoveredWeapon === name && w && (
                      <WeaponDamageTooltip
                        w={w}
                        range={target.range}
                        adjustedRange={target.adjustedRange}
                        directFire
                      />
                    )}
                  </label>
                );
              })}
              {attacker.uimFunctional && (
                <label style={{ ...ROW, cursor: 'pointer' }}>
                  <input type="checkbox" checked={useUim}
                         onChange={() => amend({ useUim: !useUim })} />
                  <span>UIM targeting (D6.51)</span>
                </label>
              )}
              {[...picked].some(n => n.startsWith('Hellbore')) && (
                <label style={{ ...ROW, cursor: 'pointer' }}>
                  <input type="checkbox" checked={hellbore}
                         onChange={() => amend({ hellbore: !hellbore })} />
                  <span>Hellbore direct fire — half damage, facing shield (E10.7)</span>
                </label>
              )}
              <button
                disabled={picked.size === 0}
                style={{ marginTop: 4 }}
                onClick={addOrder}
              >
                {(() => {
                  const total = [...picked].reduce((sum, n) => {
                    const w = attacker.weapons.find(x => x.name === n);
                    return sum + (w ? shotsFor(w) : 1);
                  }, 0);
                  return `Add order (${total} shot${total === 1 ? '' : 's'})`;
                })()}
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
        <div style={COL_TITLE}>
          The plan
          {aimedAt.size > 0 && (
            <span style={{ textTransform: 'none', letterSpacing: 0, marginLeft: 6 }}>
              — ◀ marks how many weapons the fleet already has on a target
            </span>
          )}
        </div>
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

      </div>
      )}

      {collapsed && (
        <div style={{ fontSize: '0.85em', color: '#8b949e', marginBottom: 4 }}>
          {sealed === 0
            ? 'No orders drafted — every unit holds fire.'
            : `${sealed} order${sealed > 1 ? 's' : ''} drafted across ` +
              `${new Set(orders.map(o => o.shipName)).size} unit(s).`}
        </div>
      )}

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
