import { useEffect, useMemo, useState } from 'react';
import type { ShipObject } from '../types/gameState';
import { parseLocation } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import { useDraggable } from '../hooks/useDraggable';
import { bearsOn } from '../hex/geometry';

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
  const [facing, setFacing] = useState<number>(0);        // 0 = straight at the target
  const [speed, setSpeed] = useState<number>(6);
  const drag = useDraggable(savedPosition());

  useEffect(() => {
    try { localStorage.setItem(POSITION_KEY, JSON.stringify(drag.position)); }
    catch { /* storage blocked; the panel forgets where it was */ }
  }, [drag.position]);

  const answered   = loaded.attacker === attackerName;
  const loading    = !answered;
  const loadError  = answered ? loaded.error : null;
  const candidates = answered ? loaded.rows : EMPTY_ROWS;

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

  function draft(order: LaunchOrder) {
    onAddOrder(order);
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
                {/* Facing: six only, and one that cannot track is not offered. */}
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 4 }}>
                  <span style={{ fontSize: '0.72rem', color: '#8b949e' }}>Facing</span>
                  <button className={facing === 0 ? '' : 'secondary'} style={{ padding: '0 6px' }}
                          onClick={() => setFacing(0)} title="straight at the target">auto</button>
                  {FACINGS.map(f => (
                    <button key={f}
                            className={facing === f ? '' : 'secondary'}
                            style={{ padding: '0 6px' }}
                            disabled={!canTrack(f)}
                            title={canTrack(f) ? undefined
                                 : 'the target would be outside the seeker’s forward arc'}
                            onClick={() => setFacing(f)}>
                      {FACING_LABEL[f]}
                    </button>
                  ))}
                </div>

                {plasma.length === 0 && racks.length === 0
                  && suicideReady.length === 0 && packsReady.length === 0 && (
                  <div style={{ fontSize: '0.85em', color: '#8b949e' }}>Nothing ready to send.</div>
                )}

                {plasma.map(w => {
                  const bears = target.plasmaLaunchers.includes(w.name);
                  const used = spent.has(w.name);
                  const why = used ? 'already sent' : !bears ? 'out of arc' : null;
                  return (
                    <div key={w.name} style={{ ...ROW, cursor: 'default', flexWrap: 'wrap' }}>
                      <span style={{ color: why ? '#8b949e' : '#e6edf3' }}>{w.name}</span>
                      {w.arcLabel && (
                        <span style={{ color: '#8b949e', fontSize: '0.9em' }}>[{w.arcLabel}]</span>
                      )}
                      {why && <span style={{ color: '#8b949e', fontSize: '0.9em' }}>{why}</span>}
                      {!why && (
                        <span style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
                          {w.armed && (
                            <button style={{ padding: '0 6px' }}
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${w.name}`,
                                      kind: 'PLASMA', shipName: attacker.name,
                                      targetName: target.name, weaponName: w.name, facing,
                                    })}>send</button>
                          )}
                          {w.pseudoPlasmaReady && (
                            <button className="secondary" style={{ padding: '0 6px' }}
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
                        <span style={{ marginLeft: 'auto', display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                          {r.drones.map((d, i) => (
                            <button key={i} className="secondary" style={{ padding: '0 6px' }}
                                    title={`speed ${d.speed}, ${d.warheadDamage} damage`}
                                    onClick={() => draft({
                                      label: `${attacker.name} → ${target.name}: ${d.droneType}`,
                                      kind: 'DRONE', shipName: attacker.name,
                                      targetName: target.name, weaponName: r.name,
                                      droneIndex: i, facing,
                                    })}>{d.droneType}</button>
                          ))}
                        </span>
                      )}
                    </div>
                  );
                })}

                {suicideReady.map(s => (
                  <div key={s.name} style={{ ...ROW, cursor: 'default' }}>
                    <span>{s.name}</span>
                    <span style={{ color: '#ff6060', fontSize: '0.9em' }}>
                      suicide, {s.warheadDamage} dmg
                    </span>
                    <button style={{ marginLeft: 'auto', padding: '0 6px' }}
                            disabled={spent.has(s.name)}
                            onClick={() => draft({
                              label: `${attacker.name} → ${target.name}: ${s.name}`,
                              kind: 'SUICIDE', shipName: attacker.name,
                              targetName: target.name, shuttleName: s.name, facing,
                            })}>send</button>
                  </div>
                ))}

                {packsReady.map(s => (
                  <div key={s.name} style={{ ...ROW, cursor: 'default' }}>
                    <span>{s.name}</span>
                    <span style={{ color: '#f0c040', fontSize: '0.9em' }}>
                      pack, {s.payload?.length ?? 0} aboard
                    </span>
                    <button style={{ marginLeft: 'auto', padding: '0 6px' }}
                            disabled={spent.has(s.name)}
                            onClick={() => draft({
                              label: `${attacker.name} → ${target.name}: ${s.name}`,
                              kind: 'SCATTER_PACK', shipName: attacker.name,
                              targetName: target.name, shuttleName: s.name, facing,
                            })}>send</button>
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
            {attacker && weaselsReady.length > 0 && (
              <>
                <div style={{ ...COL_TITLE, marginTop: 8 }}>No target needed</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginBottom: 2 }}>
                  <span style={{ fontSize: '0.72rem', color: '#8b949e' }}>Speed</span>
                  <button className="secondary" style={{ padding: '0 5px' }}
                          onClick={() => setSpeed(s => Math.max(0, s - 1))}>−</button>
                  <span style={{ minWidth: 14, textAlign: 'center' }}>{speed}</span>
                  <button className="secondary" style={{ padding: '0 5px' }}
                          onClick={() => setSpeed(s => s + 1)}>+</button>
                  <span style={{ fontSize: '0.72rem', color: '#8b949e' }}>
                    capped at the shuttle's own maximum
                  </span>
                </div>
                {weaselsReady.map(s => (
                  <div key={s.name} style={{ ...ROW, cursor: 'default' }}>
                    <span>{s.name}</span>
                    <span style={{ color: JADE, fontSize: '0.9em' }}>weasel</span>
                    <button style={{ marginLeft: 'auto', padding: '0 6px' }}
                            disabled={spent.has(s.name)}
                            onClick={() => draft({
                              label: `${attacker.name}: ${s.name} (weasel)`,
                              kind: 'WEASEL', shipName: attacker.name,
                              shuttleName: s.name, facing: facing || undefined, speed,
                            })}>launch</button>
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
