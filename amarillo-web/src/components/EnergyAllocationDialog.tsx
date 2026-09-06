import { useCallback, useEffect, useRef, useState } from 'react';
import type { ShipObject, ShuttleObject, WeaponState } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import type { GuardOptions } from '../api/gameApi';

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
  capCharge:            number;       // energy to add to the phaser capacitor this turn (partial refill)
  esgEnergy:            Record<string, number>;   // ESG designator → energy this turn (G23.21)
  poweredChannels:      string[];                 // scout channel designators to power (1 energy each, G24.14)
  scoutEwPoints:        number;                    // ship-level pool of EW the scout generates to lend (G24.211)
  energizeCaps:         boolean;
  weaponArming:         Record<string, ArmChoice>;
  photonArming:         Record<string, number>;   // warp energy dialled into each photon tube
  droneReloads:         Record<string, Record<string, number>>;  // rackName → {droneType → count}
  scatterPackLoading:   Record<string, Record<string, number>>;  // shuttleName → {droneType → count}
  suicideArming:        Record<string, number>;   // shuttleName → energy (1–3); 0 = not arming
  suicideHold:          Record<string, boolean>;  // shuttleName → paying hold this turn
  transUses:            number;
  cloakPaid:            boolean;
  doubleLwarp:          boolean;   // G15.2 engine doubling
  doubleRwarp:          boolean;
  doubleCwarp:          boolean;
  doubleImpulse:        boolean;
  batteryDraw:          number;
  batteryRecharge:      number;
  hetEnergy:            number;   // warp energy reserved for HETs (C6.2)
  warpTacs:             number;   // 0–4 warp Tactical Maneuvers pre-paid (C5.22)
  sublightTac:          boolean;  // pay 1 impulse point for sublight TAC (C5.12)
  ecm:                  number;   // ECM points (hide)
  eccm:                 number;   // ECCM points (seek)
  tractorEnergy:        number;   // energy pool for tractor beams (G7.15)
  shuttleSpeeds:        Record<string, number>;  // shuttle name → speed (active shuttles only)
  wwCharge:             Set<string>;             // shuttle names being charged as WW this turn
}

const SHIELD_NAMES = ['#1', '#2', '#3', '#4', '#5', '#6'];

// ---- Default allocation ----

function defaultAlloc(ship: ShipObject, myShuttles: ShuttleObject[] = []): ShipAlloc {
  const shuttleSpeeds: Record<string, number> = {};
  for (const s of myShuttles) shuttleSpeeds[s.name] = s.speed;
  const arming: Record<string, ArmChoice> = {};
  for (const w of ship.weapons ?? []) {
    if (!w.isHeavy || !w.functional) continue;
    if (w.cooldown)     arming[w.name] = 'SKIP';   // fired last turn — can't arm this turn (E7.x)
    else if (w.isRolling)    arming[w.name] = 'ROLL';
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
    capCharge:       ship.capacitorsCharged
                       ? Math.max(0, (ship.phaserCapacitorMax ?? 0) - (ship.phaserCapacitor ?? 0))
                       : 0,   // default to a full top-off; player can dial it down
    esgEnergy:       {},
    poweredChannels: [],
    scoutEwPoints:   0,
    energizeCaps:    false,
    weaponArming:    arming,
    photonArming:    Object.fromEntries(
      (ship.weapons ?? [])
        .filter(w => w.photonTube && w.functional && !w.armed)
        .map(w => [w.name, 2]),
    ),
    droneReloads:        {},
    scatterPackLoading:  {},
    suicideArming:       {},
    suicideHold:         {},
    transUses:       0,
    cloakPaid:       (ship.cloakCost ?? 0) > 0,
    doubleLwarp:     false,
    doubleRwarp:     false,
    doubleCwarp:     false,
    doubleImpulse:   false,
    batteryDraw:     0,
    batteryRecharge: 0,
    hetEnergy:         0,
    warpTacs:          0,
    sublightTac:       false,
    ecm:               0,
    eccm:              0,
    tractorEnergy:     0,
    shuttleSpeeds,
    wwCharge: new Set(
      (ship.shuttleBays ?? []).flatMap(bay =>
        bay.shuttles.filter(s => s.type === 'admin' && s.wwReady).map(s => s.name)
      )
    ),
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
             : Math.min(alloc.capCharge, Math.max(0, (ship.phaserCapacitorMax ?? 0) - (ship.phaserCapacitor ?? 0)));

  let arm = 0;
  for (const w of ship.weapons ?? []) {
    if (!w.isHeavy || !w.functional) continue;
    // A photon still arming is dialled by energy (E4.21/E4.411) — it costs what was dialled.
    if (w.photonTube && !w.armed && alloc.photonArming[w.name] != null) {
      arm += alloc.photonArming[w.name];
      continue;
    }
    const choice = alloc.weaponArming[w.name] ?? 'SKIP';
    if      (choice === 'HOLD' || choice === 'HOLD_PROX' || choice === 'HOLD_STD') arm += w.holdCost;
    else if (choice === 'STANDARD' || choice === 'FINISH') arm += w.armingCost;
    else if (choice === 'PROX')                        arm += w.armed ? w.holdCost : w.armingCost;
    // Overload costs the overload rate whatever the weapon is doing now. armingCost is
    // type-aware (Photon.energyToArm: 2 standard, 4 overload), so double it only when the
    // weapon is not ALREADY in overload mode — a mid-arm STANDARD photon at WS-2 still owes
    // the full 4. Keyed on armingType, not armingTurn, to match GameSession's ALLOCATE.
    else if (choice === 'OVERLOAD')                    arm += w.armingType === 'OVERLOAD' ? w.armingCost : w.armingCost * 2;
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
  const tac       = alloc.warpTacs * (ship.moveCost ?? 1);
  const sublTac   = alloc.sublightTac ? 1 : 0;

  const ew        = alloc.ecm + alloc.eccm;
  const ssArming  = Object.values(alloc.suicideArming ?? {}).reduce((a, b) => a + b, 0);
  const ssHold    = Object.values(alloc.suicideHold   ?? {}).filter(Boolean).length;
  const wwCost    = alloc.wwCharge.size;  // 1 energy per WW shuttle being charged
  const tractorCost = alloc.tractorEnergy;
  const esg = Object.values(alloc.esgEnergy).reduce((a, b) => a + b, 0);  // ESG generator charging (G23.21)
  // scout channels: 1 energy per powered channel (G24.14) + the ship's EW-lending pool (G24.211)
  const channels = alloc.poweredChannels.length + alloc.scoutEwPoints;
  const spent = ls + fc + mv + imp + sh + cap + arm + genReinf + specReinf + trans + cloak + recharge + het + tac + sublTac + ew + ssArming + ssHold + wwCost + tractorCost + esg + channels;
  // G15.2 engine doubling — a doubled engine outputs an extra copy of its available boxes this turn.
  const doublingBonus =
      (alloc.doubleLwarp   ? (ship.availableLWarp   ?? 0) : 0) +
      (alloc.doubleRwarp   ? (ship.availableRWarp   ?? 0) : 0) +
      (alloc.doubleCwarp   ? (ship.availableCWarp   ?? 0) : 0) +
      (alloc.doubleImpulse ? (ship.availableImpulse ?? 0) : 0);
  const total  = (ship.totalPower ?? 0) + alloc.batteryDraw + doublingBonus;
  return { spent, total, doublingBonus };
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

// ---- Guards (D7.83) ----
// Guard posts are secret: state comes from the owner-only guard-options
// endpoint, never the broadcast DTO. Posting/withdrawal fire immediately as
// ASSIGN_GUARD/REMOVE_GUARD actions (guards cost boarding parties, not
// energy, and persist across turns), so server refusals surface at click time.

export function GuardSection({ gameId, playerToken, shipName, readOnly = false }: {
  gameId: string; playerToken: string; shipName: string; readOnly?: boolean;
}) {
  const [opts, setOpts]         = useState<GuardOptions | null>(null);
  const [err, setErr]           = useState('');
  const [busy, setBusy]         = useState(false);
  const [commando, setCommando] = useState(false);

  const load = useCallback(() => {
    gameApi.getGuardOptions(gameId, playerToken, shipName)
      .then(o => { setOpts(o); setErr(''); })
      .catch(e => setErr(e instanceof Error ? e.message : String(e)));
  }, [gameId, playerToken, shipName]);

  useEffect(() => { load(); }, [load]);

  async function act(type: 'ASSIGN_GUARD' | 'REMOVE_GUARD', code: string) {
    setBusy(true);
    try {
      const res = await gameApi.submitAction(gameId, playerToken, {
        type, shipName, action: code,
        commando: type === 'ASSIGN_GUARD' ? commando : false,
      });
      setErr(res.success ? '' : res.message);
      load();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  if (!opts) return null;
  const bpFree = opts.normalAvailable + opts.commandosAvailable;
  if (bpFree === 0 && opts.totalPosted === 0) return null;

  const btn: React.CSSProperties = {
    background: '#21262d', border: '1px solid #30363d', color: '#e6edf3',
    borderRadius: 4, padding: '1px 8px', fontSize: 11, cursor: 'pointer', marginLeft: 6,
  };

  return (
    <Collapsible title={`GUARDS  (${opts.totalPosted} posted · ${bpFree} BP free)`} color="#d29922">
      <div className="ea-note-dim" style={{ marginBottom: 6 }}>
        Guards are boarding parties — while posted they do not fight boarding actions (D7.834).
        Posts persist across turns and are hidden from the enemy.
      </div>
      {!readOnly && opts.commandosAvailable > 0 && (
        <label className="ea-note-dim" style={{ display: 'block', marginBottom: 6 }}>
          <input type="checkbox" checked={commando}
                 onChange={e => setCommando(e.target.checked)} /> post commando squads
        </label>
      )}
      <div style={{ maxHeight: 190, overflowY: 'auto' }}>
        {opts.targets.map(t => (
          <div key={t.code} style={{ display: 'flex', alignItems: 'center',
              justifyContent: 'space-between', padding: '1px 0', fontSize: 12 }}>
            <span style={{ color: t.guarded ? '#d29922' : '#8b949e' }}>
              {t.label}
              {t.kind === 'pool' && <> — {t.guards ?? 0}/{t.boxes ?? 0} guarded</>}
              {t.kind === 'exact' && t.guarded && <> — guarded</>}
            </span>
            {!readOnly && (
              <span style={{ whiteSpace: 'nowrap' }}>
                {t.kind === 'pool' ? (
                  <>
                    <button style={btn} disabled={busy || bpFree === 0 || (t.guards ?? 0) >= (t.boxes ?? 0)}
                            onClick={() => act('ASSIGN_GUARD', t.code)}>+</button>
                    <button style={btn} disabled={busy || (t.guards ?? 0) === 0}
                            onClick={() => act('REMOVE_GUARD', t.code)}>−</button>
                  </>
                ) : t.guarded ? (
                  <button style={btn} disabled={busy}
                          onClick={() => act('REMOVE_GUARD', t.code)}>Withdraw</button>
                ) : (
                  <button style={btn} disabled={busy || bpFree === 0}
                          onClick={() => act('ASSIGN_GUARD', t.code)}>Post</button>
                )}
              </span>
            )}
          </div>
        ))}
      </div>
      {err && <div style={{ color: '#f85149', fontSize: 12, marginTop: 4 }}>{err}</div>}
    </Collapsible>
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

  const [activeTab, setActiveTab] = useState(() => myPending[0] ?? '');

  useEffect(() => {
    if (activeTab) onTabChange?.(activeTab);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
  // One circuit per point of sensor rating (D6.312), capped at the six points a ship may
  // generate in total (D6.310). A scout's lending pool is separate and not bound by this (G24.31).
  const ewLimit  = Math.min(ship.sensorRating ?? 0, 6);
  const hasCloak = (ship.cloakCost ?? 0) > 0;
  const batMax   = ship.availableBattery ?? 0;
  const batCharge = ship.batteryCharge ?? 0;
  const capFull  = ship.capacitorsCharged
    && (ship.phaserCapacitor ?? 0) >= (ship.phaserCapacitorMax ?? 0);

  const { spent, total, doublingBonus } = calcBudget(ship, alloc);
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

  function setPhotonArming(name: string, energy: number) {
    setAlloc(a => ({ ...a, photonArming: { ...a.photonArming, [name]: energy } }));
  }

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
          capacitorCharge:       a.capCharge,
          esgEnergy:             Object.keys(a.esgEnergy).length > 0 ? a.esgEnergy : undefined,
          poweredChannels:       a.poweredChannels.length > 0 ? a.poweredChannels : undefined,
          scoutEwPoints:         a.scoutEwPoints > 0 ? a.scoutEwPoints : undefined,
          energizeCaps:          a.energizeCaps,
          weaponArming:          a.weaponArming,
          photonArming:          Object.keys(a.photonArming).length > 0 ? a.photonArming : undefined,
          transUses:             a.transUses,
          cloakPaid:             a.cloakPaid,
          doubleLwarp:           a.doubleLwarp,
          doubleRwarp:           a.doubleRwarp,
          doubleCwarp:           a.doubleCwarp,
          doubleImpulse:         a.doubleImpulse,
          batteryDraw:           a.batteryDraw,
          batteryRecharge:       a.batteryRecharge,
          hetEnergy:             a.hetEnergy,
          warpTacticalTurns:     a.warpTacs,
          sublightTacticalTurn:  a.sublightTac,
          ecm:                   a.ecm,
          eccm:                  a.eccm,
          tractorEnergy:         a.tractorEnergy,
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
          wwCharge: a.wwCharge.size > 0 ? Array.from(a.wwCharge) : undefined,
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
          {/* Warp Tactical Maneuvers — only when speed 0 and warp power available (C5.22) */}
          {alloc.speed === 0 && !alloc.impulse && warpEnginePower > 0 && (() => {
            const moveCost  = ship.moveCost ?? 1;
            const tacBudget = total - (spent - alloc.warpTacs * moveCost);
            const maxTacs   = Math.min(4, Math.floor(tacBudget / moveCost));
            return (
              <div className="ea-het-row">
                <Stepper
                  value={alloc.warpTacs}
                  min={0}
                  max={Math.max(alloc.warpTacs, maxTacs)}
                  onChange={v => setAlloc(a => ({ ...a, warpTacs: v }))}
                  label={`Warp TAC${alloc.warpTacs > 0 ? ` (${(alloc.warpTacs * moveCost).toFixed(1)} energy)` : ` (${moveCost.toFixed(1)}/turn, C5.22)`}`}
                />
              </div>
            );
          })()}
          {/* Sublight Tactical Maneuver — speed 0 + impulse power available (C5.12) */}
          {alloc.speed === 0 && !alloc.impulse && (ship.availableImpulse ?? 0) > 0 && (
            <label className="ea-radio-label" style={{ gap: 8, marginTop: 4 }}>
              <input type="checkbox"
                checked={alloc.sublightTac}
                onChange={e => setAlloc(a => ({ ...a, sublightTac: e.target.checked }))}
              />
              Sublight TAC (1 impulse energy, C5.12)
            </label>
          )}
        </div>

        {/* ---- Engine Doubling (G15.2) — Orion warships only ---- */}
        {ship.canDoubleEngines && (
          <div className="ea-section">
            <Collapsible
              title={`ENGINE DOUBLING (G15.2)${doublingBonus > 0 ? `  +${doublingBonus} power` : ''}`}
              color="#ff8c42">
              <div style={{ fontSize: 12, color: '#8b949e', marginBottom: 6 }}>
                A doubled engine outputs twice its boxes this turn.
              </div>
              {([
                ['doubleLwarp',   'Left Warp',   ship.maxLWarp,   ship.availableLWarp,   true],
                ['doubleRwarp',   'Right Warp',  ship.maxRWarp,   ship.availableRWarp,   true],
                ['doubleCwarp',   'Center Warp', ship.maxCWarp,   ship.availableCWarp,   true],
                ['doubleImpulse', 'Impulse',     ship.maxImpulse, ship.availableImpulse, false],
              ] as [keyof ShipAlloc, string, number, number, boolean][])
                .filter(([, , max]) => (max ?? 0) > 0)
                .map(([key, label, , avail]) => (
                  <label key={key} className="ea-radio-label" style={{ gap: 8, marginTop: 4 }}>
                    <input type="checkbox"
                      checked={alloc[key] as boolean}
                      onChange={e => setAlloc(a => ({ ...a, [key]: e.target.checked }))}
                    />
                    {label} <span style={{ color: '#8b949e' }}>(+{avail ?? 0} power)</span>
                  </label>
                ))}
              {(alloc.doubleLwarp || alloc.doubleRwarp || alloc.doubleCwarp) && (
                <div style={{ fontSize: 12, color: '#f0c040', marginTop: 6 }}>
                  ⚠ −1 warp box destroyed at end of turn (G15.21); stealth bonus lost this turn (G15.82).
                </div>
              )}
            </Collapsible>
          </div>
        )}

        {/* ---- Electronic Warfare ---- */}
        {(ship.sensorRating ?? 0) > 0 && (
          <div className="ea-section">
            <Collapsible title={`ELECTRONIC WARFARE  (max ${ewLimit}, used ${alloc.ecm + alloc.eccm})`} color="#a78bfa">
              <Stepper value={alloc.ecm}  min={0} max={ewLimit - alloc.eccm}
                onChange={v => setAlloc(a => ({ ...a, ecm: v }))}
                label="ECM (hide)" />
              <Stepper value={alloc.eccm} min={0} max={ewLimit - alloc.ecm}
                onChange={v => setAlloc(a => ({ ...a, eccm: v }))}
                label="ECCM (seek)" />
            </Collapsible>
          </div>
        )}

        {/* ---- Tractor Beams ---- */}
        {(ship.availableTractors ?? 0) > 0 && (
          <div className="ea-section">
            <Collapsible title={`TRACTOR BEAMS  (${ship.availableTractors ?? 0} beam(s), used ${alloc.tractorEnergy})`} color="#22d3ee">
              <Stepper value={alloc.tractorEnergy} min={0} max={ship.totalPower}
                onChange={v => setAlloc(a => ({ ...a, tractorEnergy: v }))}
                label="Energy pool (G7.15)" />
            </Collapsible>
          </div>
        )}

        {/* ---- Guards (D7.83) — posted/withdrawn immediately, not part of ALLOCATE ---- */}
        <div className="ea-section">
          <GuardSection gameId={gameId} playerToken={playerToken} shipName={ship.name} />
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
              {!capFull ? (() => {
                const capNeeded = Math.max(0, (ship.phaserCapacitorMax ?? 0) - (ship.phaserCapacitor ?? 0));
                const charge    = Math.min(alloc.capCharge, capNeeded);
                return (
                  <Stepper
                    value={charge}
                    min={0}
                    max={capNeeded}
                    step={0.5}
                    onChange={v => setAlloc(a => ({ ...a, capCharge: v }))}
                    label={`Recharge (${charge.toFixed(1)} / ${capNeeded.toFixed(1)} to full)`}
                  />
                );
              })() : (
                <div className="ea-note ea-note-dim">Capacitor full — no recharge needed</div>
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

        {/* ---- ESG Generators (G23.21) ---- */}
        {(ship.weapons ?? []).some(w => w.esg) && (
          <div className="ea-section">
            <div className="ea-section-title" style={{ color: '#ff8c42' }}>ESG Generators</div>
            {(ship.weapons ?? []).filter(w => w.esg).map(w => {
              const key    = w.designator ?? w.name;
              const stored = w.esgStoredEnergy ?? 0;
              const max    = w.esgMaxEnergy ?? 5;
              const room   = Math.max(0, max - stored);
              const add    = Math.min(alloc.esgEnergy[key] ?? 0, room);
              return (
                <div key={w.name} className="ea-het-row">
                  <Stepper
                    value={add}
                    min={0}
                    max={room}
                    onChange={v => setAlloc(a => ({ ...a, esgEnergy: { ...a.esgEnergy, [key]: v } }))}
                    label={`ESG ${w.designator ?? ''} — holds ${stored}/${max}`
                      + (w.esgActive ? ` · field up (r${w.esgRadius}, str ${w.esgStrength})` : '')}
                  />
                </div>
              );
            })}
          </div>
        )}

        {/* ---- Scout Channels (G24.14) ---- */}
        {(ship.weapons ?? []).some(w => w.scoutChannel) && (
          <div className="ea-section">
            <div className="ea-section-title" style={{ color: '#58c8ff' }}>Scout Channels</div>
            {(ship.weapons ?? []).filter(w => w.scoutChannel).map(w => {
              const key  = w.designator ?? w.name;
              const on   = alloc.poweredChannels.includes(key);
              const dead = !w.functional;
              return (
                <label key={w.name} className="ea-het-row"
                  style={{ display: 'flex', alignItems: 'center', gap: 8, opacity: dead ? 0.5 : 1 }}>
                  <input
                    type="checkbox"
                    checked={on}
                    disabled={dead}
                    onChange={ev => setAlloc(a => ({
                      ...a,
                      poweredChannels: ev.target.checked
                        ? [...a.poweredChannels, key]
                        : a.poweredChannels.filter(k => k !== key),
                    }))}
                  />
                  <span>Channel {w.designator ?? ''}{dead ? ' — destroyed' : ' (1 energy)'}</span>
                </label>
              );
            })}
            {/* Ship-level EW pool the scout generates to lend (G24.211): 1 energy per point,
                drawn through any channel during the turn — you aim it from the sidebar. */}
            <label className="ea-het-row" style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 4 }}>
              <span style={{ opacity: 0.85 }}>EW points to generate (lending pool):</span>
              <input
                type="number"
                min={0}
                max={18}
                value={alloc.scoutEwPoints}
                style={{ width: 52 }}
                onChange={ev => {
                  const v = Math.max(0, Math.min(18, Number(ev.target.value) || 0));
                  setAlloc(a => ({ ...a, scoutEwPoints: v }));
                }}
              />
              <span style={{ fontSize: '0.8em', opacity: 0.65 }}>1 energy each (G24.211)</span>
            </label>
          </div>
        )}

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
                      {w.cooldown ? (
                        <span className="ea-note-dim">Cooling down — fired last turn, cannot arm this turn (E7)</span>
                      ) : w.isRolling ? (
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
                      ) : w.photonTube ? (
                        /* Photons are dialled by energy, not by mode (E4.21/E4.411) */
                        <PhotonDial w={w} paid={alloc.photonArming[w.name] ?? 2}
                          prox={choice === 'PROX'}
                          onChange={e => {
                            setPhotonArming(w.name, e);
                            if (e !== 2 && choice === 'PROX') setArming(w.name, 'STANDARD');
                          }}
                          onProx={p => {
                            setArming(w.name, p ? 'PROX' : 'STANDARD');
                            if (p) setPhotonArming(w.name, 2);
                          }} />
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
          const wwCandidates = (ship.shuttleBays ?? []).flatMap(bay =>
            bay.shuttles.filter(s => s.type === 'admin' && s.wwChargeCount !== undefined)
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
          if (suicideCandidates.length === 0 && wwCandidates.length === 0 && !hasScatterPack && myShuttles.length === 0) return null;

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

              {/* Wild Weasel Charging */}
              {wwCandidates.length > 0 && (
                <div className="ea-section">
                  <div className="ea-section-title" style={{ color: '#a78bfa' }}>Wild Weasel Charging</div>
                  <div className="ea-note">Pay 1 energy/turn for 2 consecutive turns to ready a WW decoy (J3.12).</div>
                  {wwCandidates.map(s => {
                    const charge = s.wwChargeCount ?? 0;
                    const ready  = s.wwReady ?? false;
                    const paying = alloc.wwCharge.has(s.name);
                    const label  = ready ? 'Ready to launch!' : charge === 1 ? 'Primed (1/2)' : 'Uncharged';
                    return (
                      <div key={s.name} className="ea-weapon-alloc-block">
                        <div className="ea-weapon-alloc-name">
                          {s.name}
                          <span className="ea-note-dim"> — {label}</span>
                        </div>
                        {!ready && (
                          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                            <input type="checkbox" checked={paying}
                              onChange={e => setAlloc(a => {
                                const next = new Set(a.wwCharge);
                                if (e.target.checked) next.add(s.name); else next.delete(s.name);
                                return { ...a, wwCharge: next };
                              })} />
                            Charge WW (1 energy) — turn {charge + (paying ? 1 : 0)}/2
                          </label>
                        )}
                        {ready && (
                          <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
                            <input type="checkbox" checked={paying}
                              onChange={e => setAlloc(a => {
                                const next = new Set(a.wwCharge);
                                if (e.target.checked) next.add(s.name); else next.delete(s.name);
                                return { ...a, wwCharge: next };
                              })} />
                            Maintain WW ready (1 energy) — uncheck to cancel
                          </label>
                        )}
                      </div>
                    );
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


/**
 * A photon tube's arming dial (E4.21/E4.411). Two points of warp energy are mandatory and arm it
 * as a standard torpedo; every point above that is overload energy, to a maximum of four, so a
 * turn takes 2 to 6. The readout shows what the tube will hold and what that makes it, because
 * the choice is really "how hard do I want this to hit" — and what it costs beyond the energy.
 */
function PhotonDial({ w, paid, prox, onChange, onProx }: {
  w: WeaponState; paid: number; prox: boolean;
  onChange: (energy: number) => void; onProx: (prox: boolean) => void;
}) {
  const inTube        = w.armingEnergy ?? 0;
  const armingTurn    = w.armingTurn ?? 0;
  const overloadSoFar = Math.max(0, inTube - 2 * armingTurn);
  const overloadNow   = Math.min(Math.max(0, paid - 2), 4 - overloadSoFar);
  const total         = inTube + 2 + overloadNow;
  const willBeArmed   = armingTurn + 1 >= 2;
  const overloaded    = total > 4;
  const warhead       = overloaded ? total * 2 : 8;
  const feedback      = !overloaded ? 0 : total <= 5 ? 1 : total <= 6 ? 2 : total <= 7 ? 3 : 4;
  const maxThisTurn   = 2 + Math.max(0, 4 - overloadSoFar);
  // Zero is a real choice, not a smaller payment: allocate nothing and the tube is discharged
  // and starts over (E4.21/E1.24). One point is never legal — the two are mandatory (E4.21) —
  // so the dial steps 0 ↔ 2.
  const arming = paid > 0;
  // E4.31: the fuse is recorded when the second turn's arming is, so it is a choice only on
  // the turn that completes the torpedo — and never together with overload energy (E4.34).
  const canFuse = (w.canProximity ?? false) && willBeArmed && overloadSoFar === 0;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
      <span style={{ color: '#8b949e' }}>Arm</span>
      <button className="action-strip-btn" style={{ padding: '0 6px' }}
        disabled={paid <= 0}
        onClick={() => onChange(paid <= 2 ? 0 : paid - 1)}>−</button>
      <span style={{ color: !arming ? '#8b949e' : overloaded ? '#ffa050' : '#56d364',
                     minWidth: 10, textAlign: 'center' }}>
        {arming ? paid : '—'}
      </span>
      <button className="action-strip-btn" style={{ padding: '0 6px' }}
        disabled={paid >= maxThisTurn}
        onClick={() => onChange(paid < 2 ? 2 : Math.min(maxThisTurn, paid + 1))}>+</button>
      {canFuse && (
        <label className="ea-radio-label" style={{ color: '#a0d0ff', marginLeft: 4 }}
          title="Proximity fuse (E4.31): free, recorded with this arming turn. Warhead 4, and it misses entirely inside range 9 (E4.32/E4.33). Cannot be overloaded (E4.34).">
          <input type="checkbox" checked={prox} disabled={paid !== 2}
            onChange={e => onProx(e.target.checked)} />
          Prox
        </label>
      )}
      <span style={{ color: '#8b949e', fontSize: '0.72rem' }}>
        {prox && arming
          ? <>→ proximity: <strong style={{ color: '#a0d0ff' }}>4 damage</strong> · minimum range 9</>
          : !arming
          ? (inTube > 0
              ? <>not arming — <strong style={{ color: '#f0a0a0' }}>discharges</strong>, losing the
                  {' '}{inTube} point{inTube === 1 ? '' : 's'} in the tube (E4.21)</>
              : <>not arming</>)
          : willBeArmed
          ? <>→ {total} in tube, <strong style={{ color: overloaded ? '#ffa050' : '#56d364' }}>
              {warhead} damage
            </strong>{overloaded && <> · max range 8 · feedback {feedback} at range 0–1</>}</>
          : <>→ {total} in tube, needs another arming turn</>}
      </span>
    </div>
  );
}
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
