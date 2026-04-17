import { useRef, useState } from 'react';
import type { ShipObject, WeaponState } from '../types/gameState';
import { gameApi } from '../api/gameApi';

// ---- Types ----

interface Props {
  gameId:       string;
  playerToken:  string;
  myShipNames:  string[];
  pendingNames: string[];
  allShips:     ShipObject[];
  onDone:       (log: string) => void;
  onError:      (msg: string) => void;
  onTabChange?: (shipName: string) => void;
}

type ShieldMode = 'ACTIVE' | 'MINIMUM' | 'OFF';
type ArmChoice  = 'STANDARD' | 'OVERLOAD' | 'SKIP' | 'ROLL' | 'FINISH' | 'HOLD';

interface ShipAlloc {
  speed:                number;
  impulse:              boolean;      // pay +1 impulse for speed 31
  shieldMode:           ShieldMode;
  generalReinf:         number;       // energy (multiples of 2)
  specificReinf:        number[];     // [0..5] energy per shield
  topOffCap:            boolean;
  energizeCaps:         boolean;
  weaponArming:         Record<string, ArmChoice>;
  droneReloads:         Record<string, boolean>;  // rackName → reload this turn
  transUses:            number;
  cloakPaid:            boolean;
  batteryDraw:          number;
  batteryRecharge:      number;
}

const SHIELD_NAMES = ['#1', '#2', '#3', '#4', '#5', '#6'];

// ---- Default allocation ----

function defaultAlloc(ship: ShipObject): ShipAlloc {
  const arming: Record<string, ArmChoice> = {};
  for (const w of ship.weapons ?? []) {
    if (!w.isHeavy || !w.functional) continue;
    if (w.isRolling)    arming[w.name] = 'ROLL';
    else if (w.armed)   arming[w.name] = 'HOLD';
    else                arming[w.name] = 'STANDARD';
  }

  return {
    speed:           ship.speed,
    impulse:         false,
    shieldMode:      'ACTIVE',
    generalReinf:    0,
    specificReinf:   [0, 0, 0, 0, 0, 0],
    topOffCap:       ship.capacitorsCharged,
    energizeCaps:    false,
    weaponArming:    arming,
    droneReloads:    {},
    transUses:       0,
    cloakPaid:       (ship.cloakCost ?? 0) > 0,
    batteryDraw:     0,
    batteryRecharge: 0,
  };
}

// ---- Budget calculator ----

function calcBudget(ship: ShipObject, alloc: ShipAlloc) {
  const ls   = ship.lifeSupportCost  ?? 0;
  const fc   = ship.fireControlCost  ?? 1;
  const mv   = Math.min(alloc.speed, 30) * (ship.moveCost ?? 1);
  const imp  = alloc.impulse ? 1 : 0;
  const sh   = alloc.shieldMode === 'ACTIVE'  ? (ship.activeShieldCost  ?? 0)
             : alloc.shieldMode === 'MINIMUM' ? (ship.minimumShieldCost ?? 0)
             : 0;
  const cap  = !ship.capacitorsCharged ? (alloc.energizeCaps ? 1 : 0)
             : alloc.topOffCap ? Math.max(0, (ship.phaserCapacitorMax ?? 0) - (ship.phaserCapacitor ?? 0))
             : 0;

  let arm = 0;
  for (const w of ship.weapons ?? []) {
    if (!w.isHeavy || !w.functional) continue;
    const choice = alloc.weaponArming[w.name] ?? 'SKIP';
    if      (choice === 'STANDARD' || choice === 'FINISH' || choice === 'HOLD') arm += w.armingCost;
    else if (choice === 'OVERLOAD') arm += w.armingCost * 2;
    else if (choice === 'ROLL')     arm += w.rollingCost;
  }

  const genReinf  = alloc.generalReinf;
  const specReinf = alloc.specificReinf.reduce((a, b) => a + b, 0);
  const transCost = ship.transporterEnergyCost ?? 0.2;
  const trans     = alloc.transUses * transCost;
  const cloak     = alloc.cloakPaid ? (ship.cloakCost ?? 0) : 0;
  const recharge  = alloc.batteryRecharge;

  const spent = ls + fc + mv + imp + sh + cap + arm + genReinf + specReinf + trans + cloak + recharge;
  const total  = (ship.totalPower ?? 0) + alloc.batteryDraw;
  return { spent, total };
}

// ---- Weapon label ----

function weaponLabel(w: WeaponState): string {
  return w.name
    .replace(/^Phaser(\d)-/, 'Ph$1-')
    .replace(/^Disruptor-/, 'Dis-')
    .replace(/^Plasma[A-Z]-/, m => 'Pla' + m[6] + '-');
}

function armingStatus(w: WeaponState): string {
  if (w.isRolling) return `[rolling — roll ${w.rollingCost} / finish ${w.armingCost}]`;
  if (w.armed) {
    const mode = w.armingType === 'OVERLOAD' ? 'OVL'
               : w.armingType === 'SPECIAL'  ? 'SPL'
               : 'STD';
    return w.armingCost > 0 ? `[${mode} — hold ${w.armingCost}]` : `[${mode}]`;
  }
  if (w.armingTurn > 0) return `[arming ${w.armingTurn}/${w.totalArmingTurns}]`;
  return '[unarmed]';
}

// ---- Stepper component ----

function Stepper({
  value, min, max, step = 1, onChange, label,
}: { value: number; min: number; max: number; step?: number; onChange: (n: number) => void; label: string }) {
  return (
    <div className="ea-stepper">
      <button className="ea-step-btn" onClick={() => onChange(Math.max(min, value - step))} disabled={value <= min}>−</button>
      <span className="ea-step-value">{value}</span>
      <button className="ea-step-btn" onClick={() => onChange(Math.min(max, value + step))} disabled={value >= max}>+</button>
      <span className="ea-step-label">{label}</span>
    </div>
  );
}

// ---- Collapsible section ----

function Collapsible({ title, color, children }: { title: string; color: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="ea-section">
      <button className="ea-toggle-btn" style={{ color }} onClick={() => setOpen(o => !o)}>
        {open ? '▼' : '▶'} {title}
      </button>
      {open && <div className="ea-collapsible-body">{children}</div>}
    </div>
  );
}

// ---- Main component ----

export default function EnergyAllocationDialog({
  gameId, playerToken, myShipNames, pendingNames, allShips, onDone, onError, onTabChange,
}: Props) {
  const [myPending] = useState(() => myShipNames.filter(n => pendingNames.includes(n)));

  // One alloc entry per pending ship, all initialized up front
  const [allocMap, setAllocMap] = useState<Record<string, ShipAlloc>>(() => {
    const map: Record<string, ShipAlloc> = {};
    for (const name of myShipNames.filter(n => pendingNames.includes(n))) {
      const ship = allShips.find(s => s.name === name);
      if (ship) map[name] = defaultAlloc(ship);
    }
    return map;
  });

  const [activeTab, setActiveTab] = useState(() => {
    const initial = myPending[0] ?? '';
    if (initial) onTabChange?.(initial);
    return initial;
  });

  const ship  = allShips.find(s => s.name === activeTab);
  const alloc = allocMap[activeTab];

  // Updater that writes into the active ship's alloc slot
  function setAlloc(updater: (prev: ShipAlloc) => ShipAlloc) {
    setAllocMap(m => ({ ...m, [activeTab]: updater(m[activeTab]) }));
  }

  // Drag state
  const dragRef  = useRef<{ x: number; y: number; l: number; t: number } | null>(null);
  const [pos, setPos] = useState({ left: 120, top: 80 });

  function onTitleMouseDown(e: React.MouseEvent) {
    if (e.button !== 0) return;
    e.preventDefault();
    dragRef.current = { x: e.clientX, y: e.clientY, l: pos.left, t: pos.top };
    function onMove(ev: MouseEvent) {
      if (!dragRef.current) return;
      setPos({ left: dragRef.current.l + ev.clientX - dragRef.current.x,
               top:  dragRef.current.t + ev.clientY - dragRef.current.y });
    }
    function onUp() {
      dragRef.current = null;
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup',   onUp);
    }
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup',   onUp);
  }

  const [busy,   setBusy]   = useState(false);
  const [errMsg, setErrMsg] = useState('');

  if (!ship || !alloc || myPending.length === 0) return null;

  const heavy    = (ship.weapons ?? []).filter(w => w.isHeavy && w.functional);
  const hasTrans = (ship.availableTransporters ?? 0) > 0;
  const hasCloak = (ship.cloakCost ?? 0) > 0;
  const batMax   = ship.availableBattery ?? 0;
  const batCharge = ship.batteryCharge ?? 0;
  const capFull  = ship.capacitorsCharged
    && (ship.phaserCapacitor ?? 0) >= (ship.minimumShieldCost ?? 0);

  const { spent, total } = calcBudget(ship, alloc);
  const overBudget = spent > total;

  // Check if any ship is over budget (blocks submit)
  const anyOverBudget = myPending.some(name => {
    const s = allShips.find(sh => sh.name === name);
    const a = allocMap[name];
    if (!s || !a) return false;
    const { spent: sp, total: tot } = calcBudget(s, a);
    return sp > tot;
  });

  function setArming(name: string, choice: ArmChoice) {
    setAlloc(a => ({ ...a, weaponArming: { ...a.weaponArming, [name]: choice } }));
  }

  function setSpecReinf(idx: number, val: number) {
    setAlloc(a => {
      const s = [...a.specificReinf];
      s[idx] = val;
      return { ...a, specificReinf: s };
    });
  }

  async function handleSubmitAll() {
    setBusy(true);
    setErrMsg('');
    try {
      for (const name of myPending) {
        const s = allShips.find(sh => sh.name === name);
        const a = allocMap[name];
        if (!s || !a) continue;
        const res = await gameApi.submitAction(gameId, playerToken, {
          type:                  'ALLOCATE',
          shipName:              name,
          speed:                 a.impulse ? 31 : a.speed,
          shieldMode:            a.shieldMode,
          topOffCap:             a.topOffCap,
          energizeCaps:          a.energizeCaps,
          weaponArming:          a.weaponArming,
          transUses:             a.transUses,
          cloakPaid:             a.cloakPaid,
          batteryDraw:           a.batteryDraw,
          batteryRecharge:       a.batteryRecharge,
          generalReinforcement:  a.generalReinf,
          specificReinforcement: a.specificReinf,
          droneReloads:          Object.keys(a.droneReloads).filter(k => a.droneReloads[k]),
        });
        if (!res.success) {
          setErrMsg(`${name}: ${res.message}`);
          setActiveTab(name); onTabChange?.(name);  // jump to the failing ship's tab
          return;
        }
      }
      onDone(`Energy allocated for ${myPending.join(', ')}`);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setErrMsg(msg);
      onError(msg);
    } finally {
      setBusy(false);
    }
  }

  const warpEnginePower  = (ship.availableLWarp ?? 0) + (ship.availableRWarp ?? 0) + (ship.availableCWarp ?? 0);
  const maxWarpSpeed     = Math.min(30, Math.floor(warpEnginePower / (ship.moveCost ?? 1)));
  const accelCap         = ship.maxSpeedNextTurn ?? 31;
  const effectiveMaxWarp = Math.min(maxWarpSpeed, accelCap);

  return (
    <div className="ea-dialog" style={{ left: pos.left, top: pos.top }}>

      {/* Title bar */}
      <div className="ea-titlebar" onMouseDown={onTitleMouseDown}>
        <span className="ea-title">Energy Allocation</span>
      </div>

      {/* Ship tabs — only shown when player has multiple pending ships */}
      {myPending.length > 1 && (
        <div className="ea-tabs">
          {myPending.map(name => {
            const s = allShips.find(sh => sh.name === name);
            const a = allocMap[name];
            const isOver = s && a ? calcBudget(s, a).spent > calcBudget(s, a).total : false;
            return (
              <button
                key={name}
                className={`ea-tab ${name === activeTab ? 'ea-tab-active' : ''} ${isOver ? 'ea-tab-over' : ''}`}
                onClick={() => { setActiveTab(name); onTabChange?.(name); }}
              >
                {name}
              </button>
            );
          })}
        </div>
      )}

      <div className="ea-body">
        {/* Ship name */}
        <div className="ea-ship-name">{ship.name}</div>

        {/* Budget bar */}
        <div className={`ea-budget-bar ${overBudget ? 'over' : ''}`}>
          <span>Spent</span>
          <span className="ea-budget-nums">
            {spent.toFixed(1)} / {total}
          </span>
          {overBudget && <span className="ea-budget-over">OVER BUDGET</span>}
        </div>

        {/* ---- Batteries ---- */}
        {batMax > 0 && (
          <div className="ea-section">
            <div className="ea-section-title" style={{ color: '#f0c040' }}>Batteries</div>
            <div className="ea-note">Charge: {batCharge} / {batMax}</div>
            <div className="ea-battery-row">
              <label className="ea-radio-label">
                <input type="radio" name={`bat-${ship.name}`} checked={alloc.batteryDraw === 0 && alloc.batteryRecharge === 0}
                  onChange={() => setAlloc(a => ({ ...a, batteryDraw: 0, batteryRecharge: 0 }))} />
                Neither
              </label>
              <label className={`ea-radio-label ${batCharge === 0 ? 'ea-disabled' : ''}`}>
                <input type="radio" name={`bat-${ship.name}`} disabled={batCharge === 0}
                  checked={alloc.batteryDraw > 0}
                  onChange={() => setAlloc(a => ({ ...a, batteryDraw: 1, batteryRecharge: 0 }))} />
                Draw
              </label>
              <label className={`ea-radio-label ${batCharge >= batMax ? 'ea-disabled' : ''}`}>
                <input type="radio" name={`bat-${ship.name}`} disabled={batCharge >= batMax}
                  checked={alloc.batteryRecharge > 0}
                  onChange={() => setAlloc(a => ({ ...a, batteryRecharge: 1, batteryDraw: 0 }))} />
                Recharge
              </label>
            </div>
            {alloc.batteryDraw > 0 && (
              <Stepper value={alloc.batteryDraw} min={1} max={batCharge}
                onChange={v => setAlloc(a => ({ ...a, batteryDraw: v }))}
                label={`drawn (+${alloc.batteryDraw} to budget)`} />
            )}
            {alloc.batteryRecharge > 0 && (
              <Stepper value={alloc.batteryRecharge} min={1} max={batMax - batCharge}
                onChange={v => setAlloc(a => ({ ...a, batteryRecharge: v }))}
                label={`recharged (cost ${alloc.batteryRecharge})`} />
            )}
          </div>
        )}

        {/* ---- Movement ---- */}
        <div className="ea-section">
          <div className="ea-section-title" style={{ color: '#79c0ff' }}>Movement</div>
          <div className="ea-speed-row">
            <input type="number" className="ea-speed-input"
              min={0} max={effectiveMaxWarp}
              value={alloc.speed}
              onChange={e => setAlloc(a => ({
                ...a,
                speed: Math.max(0, Math.min(effectiveMaxWarp, Number(e.target.value))),
                impulse: false,
              }))}
            />
            <span className="ea-speed-cost">
              {(Math.min(alloc.speed, 30) * (ship.moveCost ?? 1)).toFixed(1)} energy
            </span>
          </div>
          {accelCap < maxWarpSpeed && (
            <div className="ea-note" style={{ color: '#f0c040' }}>
              Accel limit: max speed {accelCap} (C2.2)
            </div>
          )}
          {(ship.availableImpulse ?? 0) > 0 && accelCap > maxWarpSpeed && (
            <label className="ea-check-label">
              <input type="checkbox" checked={alloc.impulse}
                onChange={e => setAlloc(a => ({
                  ...a,
                  impulse: e.target.checked,
                  speed: e.target.checked ? effectiveMaxWarp : a.speed,
                }))}
              />
              +1 Impulse (speed {effectiveMaxWarp + 1}, cost 1 extra)
            </label>
          )}
        </div>

        {/* ---- Shields ---- */}
        <div className="ea-section">
          <Collapsible title="SHIELDS" color="#56d364">
            <div className="ea-shield-options">
              {(['ACTIVE', 'MINIMUM', 'OFF'] as ShieldMode[]).map(mode => (
                <label key={mode} className="ea-radio-label">
                  <input type="radio" name={`shield-${ship.name}`}
                    checked={alloc.shieldMode === mode}
                    onChange={() => setAlloc(a => ({ ...a, shieldMode: mode }))}
                  />
                  {mode === 'ACTIVE'  ? `Active (${ship.activeShieldCost ?? '?'})`   : ''}
                  {mode === 'MINIMUM' ? `Minimum (${(ship.minimumShieldCost ?? 0).toFixed(1)})` : ''}
                  {mode === 'OFF'     ? 'Off (0)' : ''}
                </label>
              ))}
            </div>
            <div className="ea-section-title" style={{ color: '#56d364' }}>REINFORCEMENT</div>
            <div className="ea-reinf-general">
              <span className="ea-reinf-label">General (2 energy = +1 to all)</span>
              <Stepper value={alloc.generalReinf} min={0} max={30} step={2}
                onChange={v => setAlloc(a => ({ ...a, generalReinf: v }))}
                label={`energy → +${alloc.generalReinf / 2} pts`} />
            </div>
            <div className="ea-reinf-specific">
              <div className="ea-reinf-label">Specific (1 energy = +1 to one shield)</div>
              {SHIELD_NAMES.map((name, i) => (
                <div key={i} className="ea-reinf-row">
                  <span className="ea-reinf-shield-name">{name}</span>
                  <Stepper value={alloc.specificReinf[i]} min={0}
                    max={ship.shields?.[i]?.max ?? 0}
                    onChange={v => setSpecReinf(i, v)}
                    label="pts" />
                </div>
              ))}
            </div>
          </Collapsible>
        </div>

        {/* ---- Phaser Capacitor ---- */}
        <div className="ea-section">
          <div className="ea-section-title" style={{ color: '#f0c040' }}>Phaser Capacitor</div>
          {ship.capacitorsCharged ? (
            <>
              <div className="ea-note">{(ship.phaserCapacitor ?? 0).toFixed(1)} of {(ship.phaserCapacitorMax ?? 0).toFixed(1)} charged</div>
              {!capFull ? (
                <label className="ea-check-label">
                  <input type="checkbox" checked={alloc.topOffCap}
                    onChange={e => setAlloc(a => ({ ...a, topOffCap: e.target.checked }))} />
                  Top off capacitor
                </label>
              ) : (
                <div className="ea-note ea-note-dim">Capacitor full — no top-off needed</div>
              )}
            </>
          ) : (
            <label className="ea-check-label">
              <input type="checkbox" checked={alloc.energizeCaps}
                onChange={e => setAlloc(a => ({ ...a, energizeCaps: e.target.checked }))} />
              Energize capacitors (cost 1) — WS-0
            </label>
          )}
        </div>

        {/* ---- Heavy Weapons ---- */}
        {heavy.length > 0 && (
          <Collapsible title="Heavy Weapons" color="#ffa050">
            <div className="ea-weapon-alloc-list">
              {heavy.map(w => {
                const choice = alloc.weaponArming[w.name] ?? 'STANDARD';
                return (
                  <div key={w.name} className="ea-weapon-alloc-block">
                    <div className="ea-weapon-alloc-name">
                      {weaponLabel(w)}
                      <span className="ea-weapon-alloc-status">{armingStatus(w)}</span>
                    </div>
                    <div className="ea-weapon-alloc-options">
                      {w.isRolling ? (
                        <>
                          <ArmOption name={w.name} value="ROLL"    label={`Roll (${w.rollingCost})`}    current={choice} color="#f0c040" onChange={setArming} />
                          <ArmOption name={w.name} value="FINISH"  label={`Finish (${w.armingCost})`}   current={choice} color="#56d364" onChange={setArming} />
                          <ArmOption name={w.name} value="SKIP"    label="Discharge"                     current={choice} color="#8b949e" onChange={setArming} />
                        </>
                      ) : w.armed ? (
                        <>
                          <ArmOption name={w.name} value="HOLD"   label={`Hold (${w.armingCost})`}      current={choice} color="#56d364" onChange={setArming} />
                          <ArmOption name={w.name} value="SKIP"   label="Discharge"                      current={choice} color="#8b949e" onChange={setArming} />
                        </>
                      ) : (
                        <>
                          <ArmOption name={w.name} value="STANDARD" label={`Arm (${w.armingCost})`}      current={choice} color="#56d364" onChange={setArming} />
                          {!w.plasmaType && (
                            <ArmOption name={w.name} value="OVERLOAD" label={`Ovld (${w.armingCost * 2})`} current={choice} color="#ffa050" onChange={setArming} />
                          )}
                          <ArmOption name={w.name} value="SKIP"     label="Skip"                           current={choice} color="#8b949e" onChange={setArming} />
                        </>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </Collapsible>
        )}

        {/* ---- Drone Rack Reloads ---- */}
        {(ship.droneRacks ?? []).some(r => r.functional && r.reloadCount > 0 && !r.reloadingThisTurn) && (
          <Collapsible title="Drone Reloads" color="#d5a03a">
            <div className="ea-note">
              Deck crews available: {ship.availableDeckCrews}
              {' '}(each reload costs deck crews shown)
            </div>
            {(ship.droneRacks ?? []).map(r => {
              if (!r.functional || r.reloadCount === 0) return null;
              const checked = alloc.droneReloads[r.name] ?? false;
              const usedCrews = Object.entries(alloc.droneReloads)
                .filter(([, v]) => v)
                .reduce((sum, [name]) => {
                  const rack = (ship.droneRacks ?? []).find(dr => dr.name === name);
                  return sum + (rack?.reloadDeckCrewCost ?? 0);
                }, 0);
              const wouldExceed = !checked && (usedCrews + r.reloadDeckCrewCost) > ship.availableDeckCrews;
              return (
                <div key={r.name} className="ea-reinf-row">
                  <label className={`ea-check-label ${wouldExceed ? 'ea-disabled' : ''}`}>
                    <input type="checkbox" checked={checked} disabled={wouldExceed && !checked}
                      onChange={e => setAlloc(a => ({
                        ...a,
                        droneReloads: { ...a.droneReloads, [r.name]: e.target.checked },
                      }))}
                    />
                    {r.name} — {r.reloadCount} reload{r.reloadCount !== 1 ? 's' : ''} left
                    <span className="ea-note-dim"> ({r.reloadDeckCrewCost} deck crew)</span>
                  </label>
                </div>
              );
            })}
          </Collapsible>
        )}

        {/* ---- Transporters ---- */}
        {hasTrans && (
          <div className="ea-section">
            <div className="ea-section-title" style={{ color: '#79c0ff' }}>Transporters</div>
            <div className="ea-note">{ship.availableTransporters} available — {(ship.transporterEnergyCost ?? 0.2).toFixed(1)} energy/use</div>
            <Stepper value={alloc.transUses} min={0} max={ship.availableTransporters ?? 0}
              onChange={v => setAlloc(a => ({ ...a, transUses: v }))}
              label={`use(s) — cost ${(alloc.transUses * (ship.transporterEnergyCost ?? 0.2)).toFixed(1)}`} />
          </div>
        )}

        {/* ---- Cloaking Device ---- */}
        {hasCloak && (
          <div className="ea-section">
            <div className="ea-section-title" style={{ color: '#b388ff' }}>Cloaking Device</div>
            <div className="ea-note">{ship.cloakState?.toLowerCase().replace(/_/g, ' ')}</div>
            <label className="ea-check-label">
              <input type="checkbox" checked={alloc.cloakPaid}
                onChange={e => setAlloc(a => ({ ...a, cloakPaid: e.target.checked }))} />
              Pay cloak cost ({ship.cloakCost} energy)
            </label>
          </div>
        )}

        {errMsg && <div className="ea-error">{errMsg}</div>}

        <button className="ea-submit-btn" onClick={handleSubmitAll}
          disabled={busy || anyOverBudget}>
          {busy ? 'Submitting…' : 'Submit All'}
        </button>
      </div>
    </div>
  );
}

// ---- Arm option radio button ----

function ArmOption({ name, value, label, current, color, onChange }: {
  name: string; value: ArmChoice; label: string; current: ArmChoice;
  color: string; onChange: (n: string, c: ArmChoice) => void;
}) {
  return (
    <label className="ea-radio-label" style={{ color }}>
      <input type="radio" name={`arm-${name}`}
        checked={current === value}
        onChange={() => onChange(name, value)} />
      {label}
    </label>
  );
}
