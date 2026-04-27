import { useRef, useState } from 'react';
import type { ShipObject, ShuttleObject, WeaponState } from '../types/gameState';
import { gameApi } from '../api/gameApi';

// Turn mode lookup — mirrors TurnModeUtil.java, indexed by speed (0–32).
const TURN_MODE_TABLES: Record<string, number[]> = {
  Seeker:  [1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1,1],
  Shuttle: [1,1,1,1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,3],
  AA:      [1,1,1,1,1,1,1,1,1,2,2,2,2,2,2,2,2,3,3,3,3,3,3,3,3,4,4,4,4,4,4,4,4],
  A:       [1,1,1,1,1,1,1,2,2,2,2,2,2,3,3,3,3,3,3,3,4,4,4,4,4,4,4,5,5,5,5,5,5],
  B:       [1,1,1,1,1,1,2,2,2,2,2,3,3,3,3,3,4,4,4,4,4,4,5,5,5,5,5,5,5,6,6,6,6],
  C:       [1,1,1,1,1,2,2,2,2,2,3,3,3,3,3,4,4,4,4,4,4,5,5,5,5,5,5,5,6,6,6,6,6],
  D:       [1,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,4,5,5,5,5,5,5,5,6,6,6,6,6,6,6,6],
  E:       [1,1,1,1,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,5,5,6,6,6,6,6,6,6,6,6,7,7,7],
  F:       [1,1,1,1,2,2,3,3,3,3,4,4,4,4,5,5,5,5,6,6,6,6,6,6,7,7,7,7,7,7,8,8,8],
};

function turnHexesForSpeed(turnMode: string | undefined, speed: number): number | null {
  if (!turnMode) return null;
  const table = TURN_MODE_TABLES[turnMode];
  if (!table) return null;
  return table[Math.min(speed, table.length - 1)];
}

// ---- Types ----

interface Props {
  gameId:          string;
  playerToken:     string;
  myShipNames:     string[];
  pendingNames:    string[];
  allShips:        ShipObject[];
  activeShuttles?: ShuttleObject[];  // on-map shuttles (all players); dialog filters by parentShipName
  onDone:          (log: string) => void;
  onError:         (msg: string) => void;
  onTabChange?:    (shipName: string) => void;
}

type ShieldMode = 'ACTIVE' | 'MINIMUM' | 'OFF';
type ArmChoice  = 'STANDARD' | 'OVERLOAD' | 'SUICIDE' | 'SKIP' | 'ROLL' | 'FINISH' | 'HOLD' | 'EPT' | 'UPGRADE_OVL' | 'UPGRADE_SUICIDE' | 'PROX' | 'HOLD_PROX' | 'HOLD_STD';

interface ShipAlloc {
  speed:                number;
  impulse:              boolean;      // pay +1 impulse for speed 31
  shieldMode:           ShieldMode;
  generalReinf:         number;       // energy (multiples of 2)
  specificReinf:        number[];     // [0..5] energy per shield
  topOffCap:            boolean;
  energizeCaps:         boolean;
  weaponArming:         Record<string, ArmChoice>;
  droneReloads:         Record<string, Record<string, number>>;  // rackName → {droneType → count}
  scatterPackLoading:   Record<string, Record<string, number>>;  // shuttleName → {droneType → count}
  suicideArming:        Record<string, number>;   // shuttleName → energy (1–3); 0 = not arming
  suicideHold:          Record<string, boolean>;  // shuttleName → paying hold this turn
  transUses:            number;
  cloakPaid:            boolean;
  batteryDraw:          number;
  batteryRecharge:      number;
  hetEnergy:            number;   // warp energy reserved for HETs (C6.2)
  ecm:                  number;   // ECM points (hide)
  eccm:                 number;   // ECCM points (seek)
  shuttleSpeeds:        Record<string, number>;  // shuttle name → speed (active shuttles only)
}

const SHIELD_NAMES = ['#1', '#2', '#3', '#4', '#5', '#6'];

// ---- Default allocation ----

function defaultAlloc(ship: ShipObject, myShuttles: ShuttleObject[] = []): ShipAlloc {
  const shuttleSpeeds: Record<string, number> = {};
  for (const s of myShuttles) shuttleSpeeds[s.name] = s.speed;
  const arming: Record<string, ArmChoice> = {};
  for (const w of ship.weapons ?? []) {
    if (!w.isHeavy || !w.functional) continue;
    if (w.isRolling)    arming[w.name] = 'ROLL';
    else if (w.armed && w.holdCost > 0)                        arming[w.name] = 'HOLD';     // armed (any mode): hold if supported
    else if (w.launcherType && w.armingTurn >= w.totalArmingTurns - 1)
                                                               arming[w.name] = 'FINISH';   // plasma final turn
    else if (w.armingTurn > 0 && w.armingType === 'OVERLOAD')  arming[w.name] = 'OVERLOAD'; // mid-arm overload: continue
    else if (w.armingTurn > 0 && w.armingType === 'SPECIAL')   arming[w.name] = 'PROX';     // mid-arm prox: continue
    else                                                        arming[w.name] = 'STANDARD';
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
    droneReloads:        {},
    scatterPackLoading:  {},
    suicideArming:       {},
    suicideHold:         {},
    transUses:       0,
    cloakPaid:       (ship.cloakCost ?? 0) > 0,
    batteryDraw:     0,
    batteryRecharge: 0,
    hetEnergy:         0,
    ecm:               0,
    eccm:              0,
    shuttleSpeeds,
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
    if      (choice === 'HOLD' || choice === 'HOLD_PROX' || choice === 'HOLD_STD') arm += w.holdCost;
    else if (choice === 'STANDARD' || choice === 'FINISH') arm += w.armingCost;
    else if (choice === 'PROX')                        arm += w.armed ? w.holdCost : w.armingCost;
    else if (choice === 'OVERLOAD')                    arm += w.armingTurn > 0 ? w.armingCost : w.armingCost * 2;
    else if (choice === 'SUICIDE')                     arm += 7;
    else if (choice === 'UPGRADE_OVL')                 arm += 3;
    else if (choice === 'UPGRADE_SUICIDE')             arm += 6;
    else if (choice === 'ROLL')                        arm += w.rollingCost;
    else if (choice === 'EPT')                         arm += w.eptCost ?? 0;
  }

  const genReinf  = alloc.generalReinf;
  const specReinf = alloc.specificReinf.reduce((a, b) => a + b, 0);
  const transCost = ship.transporterEnergyCost ?? 0.2;
  const trans     = alloc.transUses * transCost;
  const cloak     = alloc.cloakPaid ? (ship.cloakCost ?? 0) : 0;
  const recharge  = alloc.batteryRecharge;
  const het       = alloc.hetEnergy;

  const ew        = alloc.ecm + alloc.eccm;
  const ssArming  = Object.values(alloc.suicideArming ?? {}).reduce((a, b) => a + b, 0);
  const ssHold    = Object.values(alloc.suicideHold   ?? {}).filter(Boolean).length;
  const spent = ls + fc + mv + imp + sh + cap + arm + genReinf + specReinf + trans + cloak + recharge + het + ew + ssArming + ssHold;
  const total  = (ship.totalPower ?? 0) + alloc.batteryDraw;
  return { spent, total };
}

// ---- Weapon label ----

function weaponLabel(w: WeaponState): string {
  if (w.launcherType) {
    return w.name.replace(/^Plasma-/, `Plas${w.launcherType}-`);
  }
  return w.name
    .replace(/^Phaser(\d)-/, 'Ph$1-')
    .replace(/^Disruptor-/, 'Dis-');
}

function armingStatus(w: WeaponState): string {
  if (w.isRolling) return `[rolling — roll ${w.rollingCost} / finish ${w.armingCost}]`;
  if (w.armed) {
    const mode = w.armingType === 'OVERLOAD' ? 'OVL'
               : w.armingType === 'SPECIAL'  ? 'SPL'
               : 'STD';
    return w.holdCost > 0 ? `[${mode} — hold ${w.holdCost}]` : `[${mode}]`;
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

function Collapsible({ title, color, children, defaultOpen = false }: { title: string; color: string; children: React.ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
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
  gameId, playerToken, myShipNames, pendingNames, allShips, activeShuttles = [], onDone, onError, onTabChange,
}: Props) {
  const [myPending] = useState(() => myShipNames.filter(n => pendingNames.includes(n)));

  // One alloc entry per pending ship, all initialized up front
  const [allocMap, setAllocMap] = useState<Record<string, ShipAlloc>>(() => {
    const map: Record<string, ShipAlloc> = {};
    for (const name of myShipNames.filter(n => pendingNames.includes(n))) {
      const ship = allShips.find(s => s.name === name);
      if (ship) {
        const myShuttles = activeShuttles.filter(s => s.parentShipName === name);
        map[name] = defaultAlloc(ship, myShuttles);
      }
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
    && (ship.phaserCapacitor ?? 0) >= (ship.phaserCapacitorMax ?? 0);

  const { spent, total } = calcBudget(ship, alloc);
  const overBudget = spent > total;

  // Check if any ship is over energy budget or over deck crew limit (blocks submit)
  const anyOverBudget = myPending.some(name => {
    const s = allShips.find(sh => sh.name === name);
    const a = allocMap[name];
    if (!s || !a) return false;
    const { spent: sp, total: tot } = calcBudget(s, a);
    if (sp > tot) return true;
    // Each rack may reload at most 2 spaces per turn (FD2.421) — no deck crew cost
    const anyRackOverLimit = Object.entries(a.droneReloads).some(([rackName, sel]) => {
      const rack = (s.droneRacks ?? []).find(dr => dr.name === rackName);
      const spaces = (rack?.reloadPool ?? []).reduce((ss, e) => ss + (sel[e.droneType] ?? 0) * e.rackSize, 0);
      return spaces > 2;
    });
    if (anyRackOverLimit) return true;
    // Scatter pack loading uses deck crews — total spaces must not exceed availableDeckCrews
    const stockpile: Record<string, number> = {};
    for (const r of s.droneRacks ?? []) {
      for (const e of r.reloadPool ?? []) {
        stockpile[e.droneType] = e.rackSize;
      }
    }
    const spSpaces = Object.values(a.scatterPackLoading ?? {}).reduce((sum, sel) =>
      sum + Object.entries(sel).reduce((ss, [dt, cnt]) => ss + (stockpile[dt] ?? 1) * cnt, 0), 0);
    return spSpaces > (s.availableDeckCrews ?? 2);
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
          hetEnergy:             a.hetEnergy,
          ecm:                   a.ecm,
          eccm:                  a.eccm,
          generalReinforcement:  a.generalReinf,
          specificReinforcement: a.specificReinf,
          droneReloadSelections: Object.fromEntries(
            Object.entries(a.droneReloads)
              .map(([rack, sel]) => [rack, Object.fromEntries(
                Object.entries(sel).filter(([, c]) => c > 0)
              )])
              .filter(([, sel]) => Object.keys(sel as Record<string,number>).length > 0)
          ),
          shuttleSpeeds: Object.keys(a.shuttleSpeeds).length > 0 ? a.shuttleSpeeds : undefined,
          suicideShuttleArming: Object.fromEntries(
            Object.entries(a.suicideArming ?? {}).filter(([, e]) => e > 0)
          ),
          suicideShuttleHold: Object.keys(a.suicideHold ?? {}).filter(k => a.suicideHold[k]),
          scatterPackLoading: Object.fromEntries(
            Object.entries(a.scatterPackLoading ?? {})
              .map(([shuttle, sel]) => [shuttle, Object.fromEntries(
                Object.entries(sel).filter(([, c]) => c > 0)
              )])
              .filter(([, sel]) => Object.keys(sel as Record<string,number>).length > 0)
          ),
        });
        if (!res.success) {
          setErrMsg(`${name}: ${res.message}`);
          setActiveTab(name); onTabChange?.(name);
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
          {(() => {
            const th = turnHexesForSpeed(ship.turnMode, alloc.speed);
            return th != null ? (
              <div className="ea-note">Turn Mode {th}</div>
            ) : null;
          })()}
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
          {(ship.hetCost ?? 0) > 0 && (() => {
            const hetCost = ship.hetCost ?? 1;
            const budgetRemaining = total - (spent - alloc.hetEnergy);
            const maxHets = Math.floor(budgetRemaining / hetCost);
            const currentHets = alloc.hetEnergy > 0 ? Math.round(alloc.hetEnergy / hetCost) : 0;
            return (
              <div className="ea-het-row">
                <Stepper
                  value={currentHets}
                  min={0}
                  max={Math.max(currentHets, maxHets)}
                  onChange={v => setAlloc(a => ({ ...a, hetEnergy: v * hetCost }))}
                  label={`HET reserve${currentHets > 0 ? ` (${alloc.hetEnergy} energy)` : ` (${hetCost}/HET)`}`}
                />
              </div>
            );
          })()}
        </div>

        {/* ---- Electronic Warfare ---- */}
        {(ship.sensorRating ?? 0) > 0 && (
          <div className="ea-section">
            <Collapsible title={`ELECTRONIC WARFARE  (sensor ${ship.sensorRating ?? 0}, used ${alloc.ecm + alloc.eccm})`} color="#a78bfa">
              <Stepper value={alloc.ecm}  min={0} max={(ship.sensorRating ?? 0) - alloc.eccm}
                onChange={v => setAlloc(a => ({ ...a, ecm: v }))}
                label="ECM (hide)" />
              <Stepper value={alloc.eccm} min={0} max={(ship.sensorRating ?? 0) - alloc.ecm}
                onChange={v => setAlloc(a => ({ ...a, eccm: v }))}
                label="ECCM (seek)" />
            </Collapsible>
          </div>
        )}

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
                          {w.canEpt && (
                            <ArmOption name={w.name} value="EPT"   label={`EPT (${w.eptCost})`}         current={choice} color="#ff6060" onChange={setArming} />
                          )}
                          <ArmOption name={w.name} value="SKIP"    label="Discharge"                     current={choice} color="#8b949e" onChange={setArming} />
                        </>
                      ) : w.armed && w.holdCost > 0 ? (
                        <>
                          {/* Pay-to-hold weapons (Photon, Fusion): hold in current mode, optional switch/upgrade, or discharge */}
                          <ArmOption name={w.name} value="HOLD"
                            label={w.launcherType ? `Hold (${w.holdCost})` : w.armingType === 'OVERLOAD' ? `Hold Ovld (${w.holdCost})` : w.armingType === 'SPECIAL' ? `Hold Prox (${w.holdCost})` : `Hold Std (${w.holdCost})`}
                            current={choice} color="#56d364" onChange={setArming} />
                          {w.canProximity && w.armingType === 'STANDARD' && (
                            <ArmOption name={w.name} value="HOLD_PROX"      label={`→ Prox (${w.holdCost})`} current={choice} color="#a0d0ff" onChange={setArming} />
                          )}
                          {w.canProximity && w.armingType === 'SPECIAL' && (
                            <ArmOption name={w.name} value="HOLD_STD"       label={`→ Std (${w.holdCost})`}  current={choice} color="#56d364" onChange={setArming} />
                          )}
                          {w.canOverload && w.armingType === 'STANDARD' && (
                            <ArmOption name={w.name} value="UPGRADE_OVL"    label="→ Ovld (3)"              current={choice} color="#ffa050" onChange={setArming} />
                          )}
                          {w.canSuicide && w.armingType === 'STANDARD' && (
                            <ArmOption name={w.name} value="UPGRADE_SUICIDE" label="→ Suicide (6)"          current={choice} color="#ff6060" onChange={setArming} />
                          )}
                          <ArmOption name={w.name} value="SKIP"             label="Discharge"               current={choice} color="#8b949e" onChange={setArming} />
                        </>
                      ) : w.launcherType ? (
                        /* Plasma-specific unarmed options — vary by arming turn */
                        w.armingTurn >= w.totalArmingTurns - 1 ? (
                          /* Final turn: Finish (standard), Roll, EPT (if available), Discharge */
                          <>
                            <ArmOption name={w.name} value="FINISH"  label={`Finish (${w.armingCost})`} current={choice} color="#56d364" onChange={setArming} />
                            <ArmOption name={w.name} value="ROLL"    label={`Roll (${w.rollingCost})`}   current={choice} color="#f0c040" onChange={setArming} />
                            {w.canEpt && (
                              <ArmOption name={w.name} value="EPT"   label={`EPT (${w.eptCost})`}        current={choice} color="#ff6060" onChange={setArming} />
                            )}
                            <ArmOption name={w.name} value="SKIP"    label="Discharge"                   current={choice} color="#8b949e" onChange={setArming} />
                          </>
                        ) : (
                          /* Early turns: only Standard arm or Discharge */
                          <>
                            <ArmOption name={w.name} value="STANDARD" label={`Arm (${w.armingCost})`}   current={choice} color="#56d364" onChange={setArming} />
                            <ArmOption name={w.name} value="SKIP"     label="Discharge"                  current={choice} color="#8b949e" onChange={setArming} />
                          </>
                        )
                      ) : (
                        /* Non-plasma unarmed */
                        <>
                          {w.armingTurn > 0 && w.armingType === 'OVERLOAD' ? (
                            /* Mid-arm overload: mode locked, armingCost already reflects overload rate */
                            <ArmOption name={w.name} value="OVERLOAD" label={`Ovld (${w.armingCost})`}     current={choice} color="#ffa050" onChange={setArming} />
                          ) : w.armingTurn > 0 ? (
                            /* Final arming turn (non-overload): choose Standard or Prox now */
                            <>
                              <ArmOption name={w.name} value="STANDARD" label={`Std (${w.armingCost})`}    current={choice} color="#56d364" onChange={setArming} />
                              {w.canProximity && (
                                <ArmOption name={w.name} value="PROX"   label={`Prox (${w.armingCost})`}   current={choice} color="#a0d0ff" onChange={setArming} />
                              )}
                              {w.canOverload && (!w.overloadFinalTurnOnly || w.armingTurn >= w.totalArmingTurns - 1) && (
                                <ArmOption name={w.name} value="OVERLOAD" label={`Ovld (${w.armingCost * 2})`} current={choice} color="#ffa050" onChange={setArming} />
                              )}
                            </>
                          ) : (
                            /* Fresh arm (turn 1): choose arming mode */
                            <>
                              <ArmOption name={w.name} value="STANDARD" label={`Arm (${w.armingCost})`}    current={choice} color="#56d364" onChange={setArming} />
                              {w.canProximity && (
                                <ArmOption name={w.name} value="PROX"   label={`Prox (${w.armingCost})`}   current={choice} color="#a0d0ff" onChange={setArming} />
                              )}
                              {w.canOverload && !w.overloadFinalTurnOnly && (
                                <ArmOption name={w.name} value="OVERLOAD" label={`Ovld (${w.armingCost * 2})`} current={choice} color="#ffa050" onChange={setArming} />
                              )}
                              {w.canSuicide && (
                                <ArmOption name={w.name} value="SUICIDE" label="Suicide (7)"               current={choice} color="#ff6060" onChange={setArming} />
                              )}
                            </>
                          )}
                          <ArmOption name={w.name} value="SKIP" label="Skip" current={choice} color="#8b949e" onChange={setArming} />
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
        {(ship.droneRacks ?? []).some(r => r.functional && r.reloadPool?.length && !r.reloadingThisTurn) && (
          <Collapsible title="Drone Reloads" color="#d5a03a">
            <div className="ea-note">Each rack may reload up to 2 spaces per turn. Rack cannot fire while reloading.</div>
            {(ship.droneRacks ?? []).map(r => {
              if (!r.functional || !r.reloadPool?.length || r.reloadingThisTurn) return null;
              const sel = alloc.droneReloads[r.name] ?? {};
              const spacesUsed = r.reloadPool.reduce(
                (s, e) => s + (sel[e.droneType] ?? 0) * e.rackSize, 0);
              const over = spacesUsed > 2;
              return (
                <div key={r.name} className="ea-weapon-alloc-block">
                  <div className="ea-weapon-alloc-name">
                    {r.name}
                    {spacesUsed > 0 && (
                      <span className={`ea-weapon-alloc-status${over ? ' ea-budget-over' : ''}`}>
                        {' '}({spacesUsed.toFixed(1)} / 2 spaces{over ? ' — OVER LIMIT' : ''})
                      </span>
                    )}
                  </div>
                  {r.reloadPool.map(entry => {
                    const count = sel[entry.droneType] ?? 0;
                    const label = entry.droneType.replace('TYPE_', 'Type ').replace(/_/g, ' ');
                    const spacesIfAdd = spacesUsed - (count * entry.rackSize) + ((count + 1) * entry.rackSize);
                    const canAdd = count < entry.count && spacesIfAdd <= 2;
                    return (
                      <div key={entry.droneType} className="ea-stepper">
                        <button className="ea-step-btn"
                          onClick={() => setAlloc(a => ({
                            ...a,
                            droneReloads: {
                              ...a.droneReloads,
                              [r.name]: { ...sel, [entry.droneType]: Math.max(0, count - 1) },
                            },
                          }))}
                          disabled={count <= 0}>−</button>
                        <span className="ea-step-value">{count}</span>
                        <button className="ea-step-btn"
                          onClick={() => setAlloc(a => ({
                            ...a,
                            droneReloads: {
                              ...a.droneReloads,
                              [r.name]: { ...sel, [entry.droneType]: count + 1 },
                            },
                          }))}
                          disabled={!canAdd}>+</button>
                        <span className="ea-step-label">
                          {label} <span className="ea-note-dim">({entry.count} avail, {entry.rackSize} space{entry.rackSize !== 1 ? 's' : ''} each)</span>
                        </span>
                      </div>
                    );
                  })}
                </div>
              );
            })}
          </Collapsible>
        )}

        {/* ---- Shuttles (collapsible) ---- */}
        {(() => {
          const suicideCandidates = (ship.shuttleBays ?? []).flatMap(bay =>
            bay.shuttles.filter(s =>
              s.type === 'suicide' ||
              (s.type === 'admin' && (s as any).canBecomeSuicide !== false)
            )
          );
          const spEligible = (ship.shuttleBays ?? []).flatMap(bay =>
            bay.shuttles.filter(s => s.type === 'admin' || s.type === 'scatterpack')
          );
          const stockpile: Record<string, { rackSize: number; count: number }> = {};
          for (const r of ship.droneRacks ?? [])
            for (const e of r.reloadPool ?? []) {
              if (!stockpile[e.droneType]) stockpile[e.droneType] = { rackSize: e.rackSize, count: 0 };
              stockpile[e.droneType].count += e.count;
            }
          const hasScatterPack = spEligible.length > 0 && Object.keys(stockpile).length > 0;
          const myShuttles = activeShuttles.filter(s => s.parentShipName === activeTab);
          if (suicideCandidates.length === 0 && !hasScatterPack && myShuttles.length === 0) return null;

          const shortName = (s: ShuttleObject) =>
            s.name.startsWith(activeTab + '-') ? s.name.slice(activeTab.length + 1) : s.name;
          return (
            <Collapsible title="Shuttles" color="#f0c040" defaultOpen={false}>

              {/* Suicide Shuttle Arming */}
              {suicideCandidates.length > 0 && (
                <div className="ea-section">
                  <div className="ea-section-title" style={{ color: '#ff6060' }}>Suicide Shuttle Arming</div>
                  <div className="ea-note">Arming: 1–3 energy/turn for 3 turns. Hold: 1 energy/turn once armed.</div>
                  {suicideCandidates.map(s => {
                    const turns = (s as any).armingTurnsComplete ?? 0;
                    const armed = turns >= 3;
                    const dmg   = (s as any).warheadDamage ?? 0;
                    if (armed) {
                      const holding = alloc.suicideHold?.[s.name] ?? false;
                      return (
                        <div key={s.name} className="ea-weapon-alloc-block">
                          <div className="ea-weapon-alloc-name">
                            {s.name}<span className="ea-note-dim"> — Armed (dmg {dmg})</span>
                          </div>
                          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                            <input type="checkbox" checked={holding}
                              onChange={e => setAlloc(a => ({
                                ...a,
                                suicideHold: { ...a.suicideHold, [s.name]: e.target.checked },
                              }))} />
                            Hold (1 energy) — uncheck to release
                          </label>
                        </div>
                      );
                    } else {
                      const energy = alloc.suicideArming?.[s.name] ?? 0;
                      return (
                        <div key={s.name} className="ea-weapon-alloc-block">
                          <div className="ea-weapon-alloc-name">
                            {s.name}
                            <span className="ea-note-dim"> — Turn {turns}/3{energy > 0 ? `, dmg will be ${(((s as any).totalEnergy ?? 0) + energy) * 2}` : ''}</span>
                          </div>
                          <div className="ea-stepper">
                            <button className="ea-step-btn"
                              onClick={() => setAlloc(a => ({ ...a, suicideArming: { ...a.suicideArming, [s.name]: Math.max(0, energy - 1) } }))}
                              disabled={energy <= 0}>−</button>
                            <span className="ea-step-value">{energy}</span>
                            <button className="ea-step-btn"
                              onClick={() => setAlloc(a => ({ ...a, suicideArming: { ...a.suicideArming, [s.name]: Math.min(3, energy + 1) } }))}
                              disabled={energy >= 3 || turns === 0 && s.type !== 'admin'}>+</button>
                            <span className="ea-step-label">energy this turn <span className="ea-note-dim">(1–3)</span></span>
                          </div>
                        </div>
                      );
                    }
                  })}
                </div>
              )}

              {/* Scatter Pack Loading */}
              {hasScatterPack && (() => {
                const totalSpLoading = Object.values(alloc.scatterPackLoading ?? {}).reduce((sum, sel) =>
                  sum + Object.entries(sel).reduce((s, [dt, cnt]) => s + (stockpile[dt]?.rackSize ?? 1) * cnt, 0), 0);
                const deckCrewsAvail = ship.availableDeckCrews ?? 2;
                const deckCrewsOver  = totalSpLoading > deckCrewsAvail;
                return (
                  <div className="ea-section">
                    <div className="ea-section-title" style={{ color: '#79c0ff' }}>Scatter Pack Loading</div>
                    <div className={`ea-note${deckCrewsOver ? ' ea-budget-over' : ''}`}>
                      Deck crews: {totalSpLoading.toFixed(1)} / {deckCrewsAvail} used{deckCrewsOver && ' — OVER LIMIT'}
                    </div>
                    {spEligible.map(s => {
                      const sel = alloc.scatterPackLoading?.[s.name] ?? {};
                      const shuttleMax = s.maxDroneSpaces ?? 6;
                      const payloadNote = s.type === 'scatterpack'
                        ? ` — ${(s.committedSpaces ?? 0).toFixed(1)} / ${shuttleMax} spaces used` : '';
                      const thisShuttleSpaces = Object.entries(sel).reduce(
                        (sum, [dt, cnt]) => sum + (stockpile[dt]?.rackSize ?? 1) * cnt, 0);
                      const alreadyOnShuttle = s.committedSpaces ?? 0;
                      return (
                        <div key={s.name} className="ea-weapon-alloc-block">
                          <div className="ea-weapon-alloc-name">
                            {s.name}<span className="ea-note-dim">{payloadNote}</span>
                          </div>
                          {Object.entries(stockpile).map(([dt, info]) => {
                            const count = sel[dt] ?? 0;
                            const spaceIfAdd = totalSpLoading + info.rackSize;
                            const shuttleSpaceIfAdd = alreadyOnShuttle + thisShuttleSpaces + info.rackSize;
                            const canAdd = count < info.count && spaceIfAdd <= deckCrewsAvail && shuttleSpaceIfAdd <= shuttleMax;
                            return (
                              <div key={dt} className="ea-stepper">
                                <button className="ea-step-btn"
                                  onClick={() => setAlloc(a => ({ ...a, scatterPackLoading: { ...a.scatterPackLoading, [s.name]: { ...sel, [dt]: Math.max(0, count - 1) } } }))}
                                  disabled={count <= 0}>−</button>
                                <span className="ea-step-value">{count}</span>
                                <button className="ea-step-btn"
                                  onClick={() => setAlloc(a => ({ ...a, scatterPackLoading: { ...a.scatterPackLoading, [s.name]: { ...sel, [dt]: count + 1 } } }))}
                                  disabled={!canAdd}>+</button>
                                <span className="ea-step-label">
                                  {dt.replace('Type', 'Type ')} <span className="ea-note-dim">({info.count} avail, {info.rackSize} space{info.rackSize !== 1 ? 's' : ''} each)</span>
                                </span>
                              </div>
                            );
                          })}
                        </div>
                      );
                    })}
                  </div>
                );
              })()}

              {/* Active Shuttle / Fighter Speeds */}
              {myShuttles.length > 0 && (
                <div className="ea-section">
                  <div className="ea-section-title" style={{ color: '#f0c040' }}>Shuttle / Fighter Speeds</div>
                  {myShuttles.map(s => (
                    <Stepper
                      key={s.name}
                      value={alloc.shuttleSpeeds[s.name] ?? s.speed}
                      min={0}
                      max={s.maxSpeed}
                      onChange={v => setAlloc(a => ({ ...a, shuttleSpeeds: { ...a.shuttleSpeeds, [s.name]: v } }))}
                      label={`${shortName(s)}${s.crippled ? ' ⚠' : ''} (max ${s.maxSpeed}${s.crippled ? ' — crippled' : ''})`}
                    />
                  ))}
                </div>
              )}

            </Collapsible>
          );
        })()}

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
