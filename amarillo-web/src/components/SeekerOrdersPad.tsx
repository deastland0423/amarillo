import { useEffect, useMemo, useState } from 'react';
import type { ShipObject, ShuttleInBayState } from '../types/gameState';
import { parseLocation } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import { useDraggable } from '../hooks/useDraggable';
import { allowedFacingsFromMask, bearsOn, hexGetBearingBetween } from '../hex/geometry';
import { FacingPicker } from './FacingPicker';

/**
 * The Seeker Orders pad — launches, sealed and revealed together (Annex #2, 6B).
 *
 * The launch half of what the Fire Orders pad did for direct fire, and the same diagnosis:
 * a launch used to be composed by finding the ship, finding the rack, and clicking the map.
 *
 * A separate round from the fire one, because it belongs to a different segment: seeking
 * weapons resolve in stage 6B6 and shuttles in 6B8, so in one impulse the drones are away
 * before a weasel goes up, whoever drafted first.
 *
 * Its own glyph and colour on purpose. The fire pad is ⚔ and violet; this is ☢ and jade,
 * because the palette already spends red on errors, amber on warnings, cyan on anti-drones
 * and violet on the fire declaration — a fifth meaning needs a colour nothing else uses.
 */

/** One row of /launch-targets. */
export interface LaunchCandidate {
  name:      string;
  kind:      'SHIP' | 'DRONE' | 'PLASMA' | 'SHUTTLE' | 'WEASEL';
  range:     number;
  /** Where it is, as "<col|row>" — public, and what the facing preview needs. */
  location:  string;
  hasLockOn: boolean;
  /**
   * Plasma launchers whose FIRING arc covers this target. Empty is normal and does not mean
   * "unreachable": a drone rack has no arc, so any candidate will take a drone.
   */
  plasmaLaunchers: string[];
}

/** A ship of mine with something it could send. */
export interface LaunchingUnit {
  name: string;
  ship: ShipObject;
}

/** One drafted launch — the client-side twin of the server's ActivityOrder. */
export interface LaunchOrder {
  label:       string;
  kind:        'PLASMA' | 'DRONE' | 'SUICIDE' | 'SCATTER_PACK' | 'WEASEL' | 'SHUTTLE';
  shipName:    string;
  targetName?: string;
  weaponName?: string;
  droneIndex?: number;
  pseudo?:     boolean;
  fastLoad?:   boolean;
  facing?:     number;
  shuttleName?: string;
  speed?:      number;
}

interface Props {
  gameId:      string;
  playerToken: string;
  turn:        number;
  impulse:     number;

  units:        LaunchingUnit[];
  attackerName: string | null;
  onSelectAttacker: (name: string) => void;

  targetName:     string | null;
  onSelectTarget: (name: string | null) => void;

  orders:        LaunchOrder[];
  onAddOrder:    (order: LaunchOrder) => void;
  onRemoveOrder: (index: number) => void;

  hoveredOnMap:     string[];
  onHoverCandidate: (name: string | null) => void;

  declarationOpen: boolean;
  onCall:   () => void;
  onCommit: () => void;
  onPass:   () => void;
  error:    string | null;
}

/** The six directions a unit may face — A to F, as the rest of the game labels them. */
const FACINGS = [1, 5, 9, 13, 17, 21];
const FACING_LABEL: Record<number, string> = { 1: 'A', 5: 'B', 9: 'C', 13: 'D', 17: 'E', 21: 'F' };

/**
 * A seeker's own forward arc: the nine directions 21-5. What is launched has to be able to
 * see where it is going, so the target must lie inside this arc of whatever facing it leaves
 * on. Mirrored from core for a preview only — the server refuses what it disagrees with.
 */
const FA_MASK = [21, 22, 23, 24, 1, 2, 3, 4, 5]
  .reduce((mask, dir) => mask | (1 << (dir - 1)), 0);

const JADE = '#3fb8a0';

/**
 * Turn a ship-relative arc mask into absolute hex directions.
 *
 * A weapon's arcs are written relative to the bow, so a forward tube on a ship facing D is
 * pointed down the map. Recovered from the launch panel this pad replaced, where it fed the
 * same picker.
 */
function rotateArcMask(mask: number, facing: number): number {
  if (!mask || facing <= 1) return mask;
  const shift = facing - 1;
  return ((mask << shift) | (mask >>> (24 - shift))) & 0xFFFFFF;
}

const ALL_FACINGS = new Set(FACINGS);

function intersect(a: Set<number>, b: Set<number>): Set<number> {
  return new Set([...a].filter(x => b.has(x)));
}

/**
 * Where a unit points if the player names no direction: the bearing to its target, snapped.
 *
 * A bearing is one of twenty-four; a facing is one of six. Core does this snap at launch —
 * this is the preview of it, so a row can say which way "auto" would actually send a thing.
 */
function snapToFacing(bearing: number): number {
  if (bearing <= 0) return 0;
  let best = FACINGS[0];
  let bestDist = 99;
  for (const f of FACINGS) {
    let d = Math.abs(bearing - f);
    if (d > 12) d = 24 - d;                 // the short way round the circle
    if (d < bestDist) { bestDist = d; best = f; }
  }
  return best;
}

const EMPTY_ROWS: LaunchCandidate[] = [];

const PANEL: React.CSSProperties = {
  position: 'fixed',
  width: 'min(94vw, 820px)',
  zIndex: 45,
  background: '#161b22',
  border: `1px solid ${JADE}`,
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
};

const HEADER: React.CSSProperties = {
  cursor: 'grab', display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 6,
};

const BODY: React.CSSProperties = { maxHeight: '46vh', overflowY: 'auto', overflowX: 'hidden' };

const COL: React.CSSProperties = {
  flex: '1 1 14rem', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2,
};

const COL_TITLE: React.CSSProperties = {
  fontSize: '0.7rem', letterSpacing: '0.08em', textTransform: 'uppercase',
  color: '#8b949e', marginBottom: 2,
};

const ROW: React.CSSProperties = {
  display: 'flex', alignItems: 'baseline', gap: 6, padding: '2px 6px',
  background: 'none', border: '1px solid transparent', color: '#c9d1d9',
  font: 'inherit', fontSize: '0.85em', textAlign: 'left', cursor: 'pointer', width: '100%',
};

const POSITION_KEY = 'amarillo-seeker-pad-position';

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
    return {
      left: Math.min(p.left, Math.max(0, window.innerWidth - 120)),
      top: Math.min(p.top, Math.max(0, window.innerHeight - 60)),
    };
  } catch {
    return fallback;
  }
}

export default function SeekerOrdersPad({
  gameId, playerToken, turn, impulse,
  units, attackerName, onSelectAttacker, targetName, onSelectTarget,
  orders, onAddOrder, onRemoveOrder,
  hoveredOnMap, onHoverCandidate,
  declarationOpen, onCall, onCommit, onPass, error,
}: Props) {
  const [loaded, setLoaded] = useState<{
      attacker: string | null; rows: LaunchCandidate[]; error: string | null }>(
      { attacker: null, rows: [], error: null });
  const [collapsed, setCollapsed] = useState(false);
  /**
   * A heading per launchable thing, keyed by its name; 0 (or absent) means "auto".
   *
   * One shared facing made the pad a mode you had to remember you were in. A ship can send an
   * admin shuttle E and two plasmas F and E in the same impulse, and each of those is a
   * separate order on the wire — so each is a separate answer here.
   */
  const [facings, setFacings] = useState<Record<string, number>>({});
  /** Which row the graphical picker is open for, if any. */
  const [pickerFor, setPickerFor] = useState<string | null>(null);
  /**
   * A speed per craft, keyed by name; absent means "that craft's own maximum".
   *
   * Not one number for the pad: a fighter and an admin shuttle leaving the same bay in the
   * same impulse are two orders, and each names its own speed on the wire.
   */
  const [speeds, setSpeeds] = useState<Record<string, number>>({});
  const drag = useDraggable(savedPosition());

  useEffect(() => {
    try { localStorage.setItem(POSITION_KEY, JSON.stringify(drag.position)); }
    catch { /* storage blocked; the panel forgets where it was */ }
  }, [drag.position]);

  const answered   = loaded.attacker === attackerName;
  const loading    = !answered;
  const loadError  = answered ? loaded.error : null;
  const candidates = answered ? loaded.rows : EMPTY_ROWS;

  // Panel width follows min(94vw, 820px); the picker needs about 170.
  const viewport = typeof window === 'undefined' ? 1400 : window.innerWidth;
  const panelWidth = Math.min(viewport * 0.94, 820);
  const roomOnRight = drag.position.left + panelWidth + 170 < viewport;

  const attacker = units.find(u => u.name === attackerName) ?? null;
  const target   = candidates.find(c => c.name === targetName) ?? null;

  useEffect(() => {
    if (!attackerName) return;
    let live = true;
    gameApi.getLaunchTargets(gameId, playerToken, attackerName)
      .then(rows => { if (live) setLoaded({ attacker: attackerName, rows, error: null }); })
      .catch((e: unknown) => {
        if (live) setLoaded({ attacker: attackerName, rows: [],
            error: e instanceof Error ? e.message : 'Could not list targets' });
      });
    return () => { live = false; };
  }, [gameId, playerToken, attackerName, turn, impulse]);

  /**
   * Seeker control after this round: what a ship holds now, plus what it has drafted.
   *
   * A launch past the limit does not fail. Something already flying stops being tracked
   * instead — first in, first dropped — so the cost of the ninth drone is paid by one of the
   * eight already out there. That is a loss worth seeing before it happens rather than
   * noticing afterwards, when the only evidence is a drone that stopped chasing.
   *
   * Only seekers occupy a channel: a weasel or an ordinary shuttle is not controlled.
   */
  function controlAfter(unitName: string): { used: number; limit: number; drafted: number } {
    const u = units.find(x => x.name === unitName);
    const used = u?.ship.controlUsed ?? 0;
    const limit = u?.ship.controlLimit ?? 0;
    const drafted = orders.filter(o => o.shipName === unitName
        && (o.kind === 'PLASMA' || o.kind === 'DRONE' || o.kind === 'SCATTER_PACK')).length;
    return { used, limit, drafted };
  }

  /** How much of the fleet is already sending something at each target. */
  const aimedAt = useMemo(() => {
    const m = new Map<string, number>();
    for (const o of orders)
      if (o.targetName)
        m.set(o.targetName, (m.get(o.targetName) ?? 0) + 1);
    return m;
  }, [orders]);

  /** Launchers and racks already committed this round, so nothing is sent twice. */
  const spent = useMemo(() => {
    const s = new Set<string>();
    for (const o of orders)
      if (o.shipName === attackerName && (o.weaponName || o.shuttleName))
        s.add(o.weaponName ?? o.shuttleName ?? '');
    return s;
  }, [orders, attackerName]);

  /**
   * Whether a seeker leaving on this facing could still track the target. A preview: core
   * applies the same rule and refuses what it disagrees with.
   */
  function canTrack(dir: number): boolean {
    if (!attacker || !target) return false;
    const from = parseLocation(attacker.ship.location);
    const to   = parseLocation(target.location);
    if (!from || !to) return false;
    return bearsOn({ col: from[0], row: from[1] }, dir, FA_MASK, { col: to[0], row: to[1] });
  }

  /**
   * The facings a seeker could leave on and still see its target: ones whose forward arc
   * holds it. A preview of the rule core applies, and the picker dims the rest.
   */
  const seekerFacings: Set<number> = target
    ? new Set(FACINGS.filter(canTrack))
    : ALL_FACINGS;

  /**
   * The facing is taken AT THIS MOMENT, not at commit — so it goes in the label, where the
   * plan shows it back. Choosing a direction after pressing send changes nothing, and a
   * playtest found that out the hard way: the order read "auto" and nobody could see it.
   */
  function draft(order: LaunchOrder) {
    const dir = order.facing ? FACING_LABEL[order.facing] : 'auto';
    onAddOrder({ ...order, label: `${order.label} · ${dir}` });
  }

  const ship = attacker?.ship ?? null;
  const plasma = (ship?.weapons ?? []).filter(w =>
    w.launcherType && w.functional && (w.armed || w.pseudoPlasmaReady || w.canFastLoad));
  const racks = (ship?.droneRacks ?? []).filter(r => r.functional && r.canFire && r.drones.length > 0);
  const bayCraft = (ship?.shuttleBays ?? []).flatMap(b => b.shuttles);
  const suicideReady = bayCraft.filter(s => s.type === 'suicide' && s.armed && s.canLaunch);
  const packsReady   = bayCraft.filter(s => s.type === 'scatterpack' && s.canLaunch
                                       && (s.payload?.length ?? 0) > 0);
  const weaselsReady = bayCraft.filter(s => s.wwReady && s.canLaunch);
  /** Ordinary craft — a shuttle or a fighter going out with no special role to play. */
  const plainReady = bayCraft.filter(s => !s.specialRole && s.canLaunch && !s.wwReady);
  /**
   * What this craft launches at: what was set for it, or its own maximum.
   *
   * effectiveMaxSpeed, not maxSpeed — a craft that has committed a point of speed to erratic
   * maneuvers (C10.13) cannot launch above what it has left, and the shuttle is the one that
   * knows. Core clamps to the same figure, so the default is never a speed it will refuse.
   */
  function speedOf(craft: ShuttleInBayState): number {
    const set = speeds[craft.name];
    return set === undefined ? craft.effectiveMaxSpeed
                             : Math.max(0, Math.min(craft.effectiveMaxSpeed, set));
  }

  /** One craft's speed, bounded by its own cap rather than by the fastest thing in the bay. */
  function speedStepper(craft: ShuttleInBayState) {
    const cap = craft.effectiveMaxSpeed;
    const at  = speedOf(craft);
    const em  = cap < craft.maxSpeed;
    return (
      <span style={{ display: 'flex', alignItems: 'center', gap: 2 }}
            title={em ? `up to ${cap}: a point of speed is committed to erratic maneuvers (C10.13)`
                      : `launch speed, up to ${cap}`}>
        <button className="secondary" style={{ padding: '0 5px' }} disabled={at <= 0}
                onClick={() => setSpeeds(m => ({ ...m, [craft.name]: at - 1 }))}>−</button>
        <span style={{ minWidth: 16, textAlign: 'center',
                       color: em ? '#f0c040' : undefined }}>{at}</span>
        <button className="secondary" style={{ padding: '0 5px' }} disabled={at >= cap}
                onClick={() => setSpeeds(m => ({ ...m, [craft.name]: at + 1 }))}>+</button>
      </span>
    );
  }

  const shipFacing = ship?.facing ?? 1;

  /**
   * Which way "auto" sends something AIMED at the target: its bearing, snapped to one of six.
   * Shown on the chip so a heading is never invisible, and used to check a tube can throw
   * that way. What goes out with no target of its own leaves on its ship's heading instead,
   * which is why the rows below carry an auto each rather than sharing this one.
   */
  const seekerAuto: number = (() => {
    if (!attacker || !target) return shipFacing;
    const from = parseLocation(attacker.ship.location);
    const to   = parseLocation(target.location);
    if (!from || !to) return shipFacing;
    const snapped = snapToFacing(
        hexGetBearingBetween({ col: from[0], row: from[1] }, { col: to[0], row: to[1] }));
    return snapped || shipFacing;
  })();

  /**
   * A tube's own launch directions, narrowed by the seeker's forward-arc rule.
   *
   * Two different constraints, and they are not the same arc: a Romulan KR's Plasma-G may
   * TARGET anything in its firing arc but may only THROW one in direction A. The picker shows
   * the intersection, so what it offers is what core will accept.
   */
  function tubeFacings(mask: number): Set<number> {
    if (!mask) return seekerFacings;
    return intersect(seekerFacings, allowedFacingsFromMask(rotateArcMask(mask, shipFacing)));
  }

  /**
   * Every row's allowed set and its auto, by the name the row is keyed on.
   *
   * The two halves of "no target needed" both land here: a weasel may leave on any of six,
   * and with nothing to aim at, auto is the ship's own heading.
   */
  const allowedByKey = new Map<string, Set<number>>();
  const autoByKey    = new Map<string, number>();
  function offer(key: string, allowed: Set<number>, auto: number) {
    allowedByKey.set(key, allowed);
    autoByKey.set(key, auto);
  }
  for (const w of plasma)
    offer(w.name, tubeFacings(w.launchDirectionsMask || w.arcMask), seekerAuto);
  for (const r of racks)        offer(r.name, seekerFacings, seekerAuto);
  for (const c of suicideReady) offer(c.name, seekerFacings, seekerAuto);
  for (const c of packsReady)   offer(c.name, seekerFacings, seekerAuto);
  for (const c of weaselsReady) offer(c.name, ALL_FACINGS, shipFacing);
  for (const c of plainReady)   offer(c.name, ALL_FACINGS, shipFacing);

  function autoFor(key: string): number {
    return autoByKey.get(key) ?? shipFacing;
  }

  /** The heading this row will launch on: what was picked, or where its auto points. */
  function headingOf(key: string): number {
    return facings[key] || autoFor(key);
  }

  function legalHeading(key: string): boolean {
    return (allowedByKey.get(key) ?? ALL_FACINGS).has(headingOf(key));
  }

  /**
   * One row's heading, as a button that opens the graphical picker for THAT row. The letter
   * alone means nothing without the hex to read it against, which is what the picker draws.
   */
  function facingChip(key: string) {
    const picked = facings[key] ?? 0;
    const allowed = allowedByKey.get(key) ?? ALL_FACINGS;
    const dead = allowed.size === 0;
    const ok = legalHeading(key);
    return (
      <button className={picked === 0 ? 'secondary' : ''}
              style={{ padding: '0 6px', minWidth: '3.6rem',
                       borderColor: ok ? undefined : '#f85149' }}
              disabled={dead}
              title={dead ? 'no direction this could leave on and still track its target'
                   : picked === 0
                     ? `auto — ${FACING_LABEL[autoFor(key)]}; click to choose another`
                     : `launches ${FACING_LABEL[picked]}; click to change`}
              onClick={() => setPickerFor(k => (k === key ? null : key))}>
        {picked === 0 ? `auto ${FACING_LABEL[autoFor(key)]} ▸`
                      : `${FACING_LABEL[picked]} ▸`}
      </button>
    );
  }

  const sealed = orders.length;

  return (
    <div style={{ ...PANEL, left: drag.position.left, top: drag.position.top }}>
      <div style={HEADER} {...drag.handleProps} title="Drag to move">
        <span style={{ color: JADE, fontWeight: 600 }}>☢ Seeker orders</span>
        <span style={{ fontSize: '0.78em', color: '#8b949e' }}>
          turn {turn}, impulse {impulse} —{' '}
          {declarationOpen
            ? 'sealed together; seekers away (6B6) before shuttles launch (6B8).'
            : 'draft freely; nobody is waiting on you until launches are called.'}
        </span>
        <button className="secondary" style={{ padding: '0 8px', marginLeft: 'auto' }}
                onClick={() => setCollapsed(c => !c)}
                title={collapsed ? 'Show the pad' : 'Collapse — Commit and Pass stay available'}>
          {collapsed ? '▸' : '▾'}
        </button>
      </div>

      {/* A picker left open on another ship's rack is closed by the rows themselves: a key
          no row offers any more has no set to show. Target changes keep it open, with the
          allowed set refreshed under it, which is what you want mid-choice. */}
      {pickerFor && allowedByKey.has(pickerFor) && !collapsed && (
        <div style={{
          // The pad is draggable, so the right edge is not always where there is room. Flip
          // to the other side rather than let the picker hang off the screen.
          position: 'absolute', top: 40,
          ...(roomOnRight
            ? { left: '100%', marginLeft: 8 }
            : { right: '100%', marginRight: 8 }),
          background: '#161b22', border: `1px solid ${JADE}`, borderRadius: 6,
          padding: 8, boxShadow: '0 6px 24px rgba(0,0,0,0.5)', zIndex: 1,
        }}>
          <FacingPicker
            label={`${pickerFor} launches`}
            value={facings[pickerFor] || null}
            onChange={f => {
              setFacings(m => ({ ...m, [pickerFor]: f }));
              setPickerFor(null);
            }}
            allowedFacings={allowedByKey.get(pickerFor) ?? ALL_FACINGS}
          />
          <button className="secondary" style={{ width: '100%', marginTop: 4 }}
                  onClick={() => {
                    setFacings(m => ({ ...m, [pickerFor]: 0 }));
                    setPickerFor(null);
                  }}>
            auto — {FACING_LABEL[autoFor(pickerFor)]}
          </button>
        </div>
      )}

      {!collapsed && (
      <div style={BODY}>
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'flex-start' }}>

          {/* ------------------------------------------------ your ships */}
          <div style={COL}>
            <div style={COL_TITLE}>Your ships</div>
            {units.length === 0 && (
              <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Nothing of yours can launch.</div>
            )}
            {units.map(u => {
              const mine = orders.filter(o => o.shipName === u.name);
              const isSel = u.name === attackerName;
              return (
                <button key={u.name}
                        style={{ ...ROW,
                                 borderColor: isSel ? JADE : 'transparent',
                                 background: isSel ? 'rgba(63,184,160,0.10)' : 'none' }}
                        onClick={() => onSelectAttacker(u.name)}>
                  <span style={{ fontWeight: 600, color: mine.length ? '#e6edf3' : '#f0c040' }}>
                    {u.name}
                  </span>
                  {(() => {
                    const c = controlAfter(u.name);
                    if (c.limit === 0) return null;
                    const after = c.used + c.drafted;
                    const over = after > c.limit;
                    return (
                      <span style={{ color: over ? '#f85149' : '#8b949e', fontSize: '0.9em',
                                     whiteSpace: 'nowrap' }}
                            title={over
                              ? `${after - c.limit} already flying would stop being tracked`
                              : 'seeker control channels held, once this round is away'}>
                        {after}/{c.limit}{over ? ' ⚠' : ''}
                      </span>
                    );
                  })()}
                  <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                    {mine.length === 0 ? 'launches nothing' : `${mine.length} ord`}
                  </span>
                </button>
              );
            })}
          </div>

          {/* ------------------------------------------------ candidates */}
          <div style={COL}>
            <div style={COL_TITLE}>{attacker ? `${attacker.name} can send one at` : 'Can send one at'}</div>
            {!attacker && <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Pick a ship.</div>}
            {attacker && loading && <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Looking…</div>}
            {attacker && loadError && (
              <div style={{ fontSize: '0.85em', color: '#f85149' }}>{loadError}</div>
            )}
            {attacker && !loading && !loadError && candidates.length === 0 && (
              <div style={{ fontSize: '0.85em', color: '#8b949e' }}>
                Nothing to send one at. Shuttles below still launch.
              </div>
            )}
            {attacker && !loading && candidates.map(c => {
              const isSel = c.name === targetName;
              const lit = hoveredOnMap.includes(c.name);
              const aimed = aimedAt.get(c.name);
              return (
                <button key={c.name}
                        style={{ ...ROW,
                                 borderColor: isSel ? JADE : lit ? '#2d8272' : 'transparent',
                                 background: isSel ? 'rgba(63,184,160,0.10)'
                                           : lit ? 'rgba(63,184,160,0.06)' : 'none' }}
                        onClick={() => onSelectTarget(c.name)}
                        onMouseEnter={() => onHoverCandidate(c.name)}
                        onMouseLeave={() => onHoverCandidate(null)}>
                  <span style={{ color: '#e6edf3' }}>{c.name}</span>
                  {!c.hasLockOn && (
                    <span style={{ color: '#f0c040', fontSize: '0.9em' }}
                          title="no lock-on — only a self-guiding drone under passive fire control may be sent (D6.121/D19.221)">
                      no lock
                    </span>
                  )}
                  {aimed && (
                    <span style={{ color: '#f0c040', fontSize: '0.9em' }}
                          title="launches already drafted at this target">◀{aimed}</span>
                  )}
                  <span style={{ marginLeft: 'auto', color: '#8b949e', whiteSpace: 'nowrap' }}>
                    r{c.range}
                    {c.plasmaLaunchers.length > 0 ? ` · ${c.plasmaLaunchers.length} tube` : ''}
                  </span>
                </button>
              );
            })}
          </div>

          {/* ------------------------------------------------ what to send */}
          <div style={COL}>
            <div style={COL_TITLE}>
              {target ? `Send at ${target.name}` : 'What to send'}
            </div>

            {attacker && target && (
              <>
                {/* Each row says where its own launch goes; the button opens the picker. */}
                <div style={{ fontSize: '0.72rem', color: '#8b949e', marginBottom: 4 }}>
                  Each launch carries its own heading — the button on its row.
                </div>

                {plasma.length === 0 && racks.length === 0
                  && suicideReady.length === 0 && packsReady.length === 0 && (
                  <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Nothing ready to send.</div>
                )}

                {plasma.map(w => {
                  const bears = target.plasmaLaunchers.includes(w.name);
                  const used = spent.has(w.name);
                  // Whether the tube may send one at all, before any question of direction:
                  // two separate answers, and only the second is one the player can fix here.
                  const blocked = used ? 'already sent' : !bears ? 'out of arc' : null;
                  const ok = legalHeading(w.name);
                  const facing = facings[w.name] ?? 0;
                  const why = blocked
                            ?? (ok ? null : `cannot launch ${FACING_LABEL[headingOf(w.name)]}`);
                  return (
                    <div key={w.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                      <span style={{ color: why ? '#8b949e' : '#e6edf3' }}>{w.name}</span>
                      {w.arcLabel && (
                        <span style={{ color: '#8b949e', fontSize: '0.9em' }}>[{w.arcLabel}]</span>
                      )}
                      {why && <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{why}</span>}
                      {!blocked && (
                        <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                       alignItems: 'center' }}>
                          {facingChip(w.name)}
                          {w.armed && (
                            <button style={{ padding: '0 6px' }} disabled={!ok}
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${w.name}`,
                                      kind: 'PLASMA', shipName: attacker.name,
                                      targetName: target.name, weaponName: w.name, facing,
                                    })}>launch</button>
                          )}
                          {w.pseudoPlasmaReady && (
                            <button className="secondary" style={{ padding: '0 6px' }}
                                    disabled={!ok}
                                    title="a bluff: no warhead, and bound by every rule a real one is"
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${w.name} (pseudo)`,
                                      kind: 'PLASMA', shipName: attacker.name,
                                      targetName: target.name, weaponName: w.name,
                                      pseudo: true, facing,
                                    })}>pseudo</button>
                          )}
                          {w.canFastLoad && (
                            <button className="secondary" style={{ padding: '0 6px' }}
                                    disabled={!ok}
                                    title="FP1.93 accelerated arming — 2 reserve power"
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${w.name} (fast)`,
                                      kind: 'PLASMA', shipName: attacker.name,
                                      targetName: target.name, weaponName: w.name,
                                      fastLoad: true, facing,
                                    })}>fast</button>
                          )}
                        </span>
                      )}
                    </div>
                  );
                })}

                {racks.map(r => {
                  const used = spent.has(r.name);
                  return (
                    <div key={r.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                      <span style={{ color: used ? '#8b949e' : '#e6edf3' }}>{r.name}</span>
                      {used ? (
                        <span style={{ color: '#8b949e', fontSize: '0.9em' }}>already sent</span>
                      ) : (
                        <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                       flexWrap: 'wrap', alignItems: 'center' }}>
                          {facingChip(r.name)}
                          {r.drones.map((d, i) => (
                            <button key={i} className="secondary" style={{ padding: '0 6px' }}
                                    disabled={!legalHeading(r.name)}
                                    title={`speed ${d.speed}, ${d.warheadDamage} damage`}
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${d.droneType}`,
                                      kind: 'DRONE', shipName: attacker.name,
                                      targetName: target.name, weaponName: r.name,
                                      droneIndex: i, facing: facings[r.name] ?? 0,
                                    })}>{d.droneType}</button>
                          ))}
                        </span>
                      )}
                    </div>
                  );
                })}

                {suicideReady.map(s => (
                  <div key={s.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                    <span>{s.name}</span>
                    <span style={{ color: '#ff6060', fontSize: '0.9em' }}>
                      suicide, {s.warheadDamage} dmg
                    </span>
                    <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                   alignItems: 'center' }}>
                      {facingChip(s.name)}
                      {speedStepper(s)}
                      <button style={{ padding: '0 6px' }}
                              disabled={spent.has(s.name) || !legalHeading(s.name)}
                              onClick={() => draft({
                                label: `${attacker.name} → ${target.name}: ${s.name}`
                                     + ` (speed ${speedOf(s)})`,
                                kind: 'SUICIDE', shipName: attacker.name,
                                targetName: target.name, shuttleName: s.name,
                                facing: facings[s.name] ?? 0, speed: speedOf(s),
                              })}>launch</button>
                    </span>
                  </div>
                ))}

                {packsReady.map(s => (
                  <div key={s.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                    <span>{s.name}</span>
                    <span style={{ color: '#f0c040', fontSize: '0.9em' }}>
                      pack, {s.payload?.length ?? 0} aboard
                    </span>
                    <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                   alignItems: 'center' }}>
                      {facingChip(s.name)}
                      {speedStepper(s)}
                      <button style={{ padding: '0 6px' }}
                              disabled={spent.has(s.name) || !legalHeading(s.name)}
                              onClick={() => draft({
                                label: `${attacker.name} → ${target.name}: ${s.name}`
                                     + ` (speed ${speedOf(s)})`,
                                kind: 'SCATTER_PACK', shipName: attacker.name,
                                targetName: target.name, shuttleName: s.name,
                                facing: facings[s.name] ?? 0, speed: speedOf(s),
                              })}>launch</button>
                    </span>
                  </div>
                ))}
              </>
            )}

            {attacker && !target && (
              <div style={{ fontSize: '0.85em', color: '#8b949e' }}>
                Pick a target for seekers. Shuttles below need none.
              </div>
            )}

            {/* Launches with no target: a weasel is a decoy, not an attack (6B8). */}
            {attacker && (weaselsReady.length > 0 || plainReady.length > 0) && (
              <>
                <div style={{ ...COL_TITLE, marginTop: 8 }}>No target needed</div>
                {plainReady.map(craft => (
                  <div key={craft.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                    <span>{craft.name}</span>
                    <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{craft.type}</span>
                    <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                   alignItems: 'center' }}>
                      {facingChip(craft.name)}
                      {speedStepper(craft)}
                      <button className="secondary" style={{ padding: '0 6px' }}
                              disabled={spent.has(craft.name)}
                              onClick={() => draft({
                                label: `${attacker.name}: ${craft.name}`
                                     + ` (speed ${speedOf(craft)})`,
                                kind: 'SHUTTLE', shipName: attacker.name,
                                shuttleName: craft.name,
                                facing: facings[craft.name] || undefined,
                                speed: speedOf(craft),
                              })}>launch</button>
                    </span>
                  </div>
                ))}

                {weaselsReady.map(craft => (
                  <div key={craft.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                    <span>{craft.name}</span>
                    <span style={{ color: JADE, fontSize: '0.9em' }}>weasel</span>
                    <span style={{ marginLeft: 'auto', display: 'flex', gap: 4,
                                   alignItems: 'center' }}>
                      {facingChip(craft.name)}
                      {speedStepper(craft)}
                      <button style={{ padding: '0 6px' }}
                              disabled={spent.has(craft.name)}
                              onClick={() => draft({
                                label: `${attacker.name}: ${craft.name}`
                                     + ` (weasel, speed ${speedOf(craft)})`,
                                kind: 'WEASEL', shipName: attacker.name,
                                shuttleName: craft.name,
                                facing: facings[craft.name] || undefined,
                                speed: speedOf(craft),
                              })}>launch</button>
                    </span>
                  </div>
                ))}
              </>
            )}
          </div>
        </div>

        {/* ------------------------------------------------ the plan */}
        <div style={{ marginTop: 8, borderTop: '1px solid #30363d', paddingTop: 6 }}>
          <div style={COL_TITLE}>The plan</div>
          {units.map(u => {
            const mine = orders.filter(o => o.shipName === u.name);
            return (
              <div key={u.name} style={{ fontSize: '0.85em', color: '#c9d1d9' }}>
                <span style={{ color: mine.length ? '#e6edf3' : '#8b949e' }}>{u.name}</span>
                {mine.length === 0 ? (
                  <span style={{ color: '#8b949e' }}> — launches nothing</span>
                ) : (
                  mine.map(o => (
                    <span key={orders.indexOf(o)} style={{ marginLeft: 8 }}>
                      {o.label.replace(`${u.name} → `, '').replace(`${u.name}: `, '')}{' '}
                      <button className="secondary" style={{ padding: '0 5px' }}
                              onClick={() => onRemoveOrder(orders.indexOf(o))}>✕</button>
                    </span>
                  ))
                )}
              </div>
            );
          })}
        </div>
      </div>
      )}

      {collapsed && (
        <div style={{ fontSize: '0.85em', color: '#8b949e', marginBottom: 4 }}>
          {sealed === 0 ? 'Nothing drafted — every ship launches nothing.'
                        : `${sealed} launch${sealed > 1 ? 'es' : ''} drafted.`}
        </div>
      )}

      {(() => {
        const over = units
          .map(u => ({ name: u.name, ...controlAfter(u.name) }))
          .filter(c => c.limit > 0 && c.used + c.drafted > c.limit);
        if (over.length === 0) return null;
        return (
          <div style={{ color: '#f85149', fontSize: '0.85em', marginTop: 4 }}>
            ⚠ {over.map(c => `${c.name} would hold ${c.used + c.drafted} of ${c.limit}`).join('; ')}
            {' '}— the excess stops being tracked, oldest first.
          </div>
        );
      })()}

      {error && <div style={{ color: '#f85149', fontSize: '0.85em', marginTop: 4 }}>{error}</div>}

      <div style={{ marginTop: 8, display: 'flex', gap: 8, alignItems: 'center' }}>
        {declarationOpen ? (
          <>
            <button onClick={onCommit}>Commit launches ({sealed})</button>
            <button className="secondary" onClick={onPass}>Pass</button>
          </>
        ) : (
          <>
            <button onClick={onCall}>Call for launches</button>
            <span style={{ fontSize: '0.78em', color: '#8b949e' }}>
              — everyone stops and commits. Drafting one calls it too.
            </span>
          </>
        )}
      </div>
    </div>
  );
}
