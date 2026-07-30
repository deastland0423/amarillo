import { useState, useCallback, useEffect, useRef, Fragment } from 'react';
import type { LobbyResult } from './Lobby';
import { useGameSocket } from '../hooks/useGameSocket';
import type { MapObject, ShipObject, ShuttleObject, DroneObject, PlasmaObject, WildWeaselObject, ObjectiveObject, ShieldState, WeaponState } from '../types/gameState';
import { factionColor, parseLocation } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import HexGrid from './HexGrid';
import EnergyAllocationDialog from './EnergyAllocationDialog';
import { ReinforcementDialog } from './ReinforcementDialog';
import { DacChoiceDialog } from './DacChoiceDialog';
import { ControlOverflowDialog } from './ControlOverflowDialog';
import { FacingPicker } from './FacingPicker';
import { getWeaponDamagePreview, getPlasmaBoltPreview } from '../weaponDamageTables';

interface Props {
  session: LobbyResult;
  onLeave: () => void;
}

// ---- Utility helpers ----

const SHIELD_NAMES = ['', 'Shield #1', 'Shield #2', 'Shield #3', 'Shield #4', 'Shield #5', 'Shield #6'];

function facingLabel(facing: number): string {
  const idx = [1, 5, 9, 13, 17, 21].indexOf(facing);
  return idx >= 0 ? ['A', 'B', 'C', 'D', 'E', 'F'][idx] : `${facing}`;
}

function locationLabel(loc: string | null): string {
  const coords = parseLocation(loc);
  if (!coords) return '—';
  const [col, row] = coords;
  return `${String(col).padStart(2, '0')}${String(row).padStart(2, '0')}`;
}

function shieldFillClass(current: number, max: number): string {
  if (max === 0 || current === 0) return 'zero';
  const pct = current / max;
  if (pct > 0.6) return 'high';
  if (pct > 0.25) return 'medium';
  return 'low';
}

function weaponLabel(w: WeaponState): string {
  const name = w.name;
  if (w.launcherType) {
    // e.g. "Plasma-A" with launcherType "G" → "PlaG-A"
    return name.replace(/^Plasma-/, `Plas${w.launcherType}-`);
  }
  return name
    .replace(/^Phaser(\d)-/, 'Ph$1-')
    .replace(/^PhaserG-/, 'Ph-G-')
    .replace(/^Disruptor-/, 'Dis-')
    .replace(/^DroneRack-/, 'Rack-');
}


/** Faction color for any map object (ships use faction, seekers use controllerFaction). */
function mapObjectColor(obj: MapObject): string {
  if (obj.type === 'SHIP')   return factionColor((obj as ShipObject).faction);
  if (obj.type === 'DRONE')         return factionColor((obj as DroneObject).controllerFaction);
  if (obj.type === 'PLASMA')        return factionColor((obj as PlasmaObject).controllerFaction);
  if (obj.type === 'SUICIDE_SHUTTLE' || obj.type === 'SCATTER_PACK')
    return factionColor((obj as ShuttleObject).controllerFaction ?? '');
  return '#888';
}

/** True if this object can be selected as a fire target by the given player. */
function canBeFireTarget(obj: MapObject, myShips: Set<string>): boolean {
  if (obj.type === 'SHIP')   return !myShips.has(obj.name);
  if (obj.type === 'DRONE')  return !myShips.has((obj as DroneObject).controllerName ?? '');
  if (obj.type === 'PLASMA') return !myShips.has((obj as PlasmaObject).controllerName ?? '');
  if (obj.type === 'SHUTTLE' || obj.type === 'SUICIDE_SHUTTLE' || obj.type === 'SCATTER_PACK') {
    const s = obj as ShuttleObject;
    return !myShips.has(s.parentShipName ?? '') && !myShips.has(s.controllerName ?? '');
  }
  if (obj.type === 'WILD_WEASEL') {
    // Shooting down the decoy is the counter-tactic (J3.21) — but a weasel
    // already exploding or reduced to radiation cannot be killed again
    const w = obj as WildWeaselObject;
    return !myShips.has(w.parentShipName ?? '') && !w.exploding && !w.postExplosion;
  }
  return false;
}

// ---- Sub-components ----

/**
 * Owner-only, lazily fetched guard summary (D7.83). Guard posts are secret —
 * they never ride the broadcast state, so this row fetches from the
 * guard-options endpoint only when expanded, and only renders for own ships.
 */
function GuardSummaryRow({ gameId, playerToken, shipName }: {
  gameId: string; playerToken: string; shipName: string;
}) {
  // Mounted with key={shipName}, so state resets naturally on ship change
  const [open, setOpen] = useState(false);
  const [opts, setOpts] = useState<import('../api/gameApi').GuardOptions | null>(null);

  useEffect(() => {
    if (open)
      gameApi.getGuardOptions(gameId, playerToken, shipName)
        .then(setOpts).catch(() => setOpts(null));
  }, [open, gameId, playerToken, shipName]);

  const posted = opts?.targets.filter(t => t.guarded || (t.guards ?? 0) > 0) ?? [];
  return (
    <div className="sidebar-stat-row" style={{ flexDirection: 'column', alignItems: 'stretch' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', cursor: 'pointer' }}
           onClick={() => setOpen(o => !o)}>
        <span className="sidebar-stat-label">Guards</span>
        <span className="sidebar-stat-value">
          {open ? (opts ? `${opts.totalPosted} posted ▾` : '…') : 'show ▸'}
        </span>
      </div>
      {open && opts && (
        <div style={{ fontSize: 11, color: '#8b949e', marginTop: 2 }}>
          {posted.length === 0 && <div>No guards posted (post during Energy Allocation)</div>}
          {posted.map(t => (
            <div key={t.code}>
              {t.label}{t.kind === 'pool' ? ` — ${t.guards}/${t.boxes} guarded` : ''}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function StatRow({ label, value, dmg }: { label: string; value: string | number; dmg?: boolean }) {
  return (
    <div className="sidebar-stat-row">
      <span className="sidebar-stat-label">{label}</span>
      <span className={`sidebar-stat-value${dmg ? ' dmg' : ''}`}>{value}</span>
    </div>
  );
}

function ShieldBar({ shield, isMine }: { shield: ShieldState; isMine: boolean }) {
  const visible = isMine ? shield.current : shield.baseStrength;
  const pct = shield.max > 0 ? (visible / shield.max) * 100 : 0;
  const isDown = !shield.active;
  return (
    <div className={`shield-cell${isDown ? ' shield-down' : ''}`}>
      <span className="shield-label">
        {SHIELD_NAMES[shield.shieldNum]} {visible}/{shield.max}
        {isDown && (
          <span className="shield-down-label">
            {shield.impulsesUntilRaiseable > 0
              ? ` (down — ${shield.impulsesUntilRaiseable} imp)`
              : ' (down)'}
          </span>
        )}
        {isMine && !isDown && shield.current > shield.baseStrength && (
          <span style={{ color: '#56d364', marginLeft: 4 }}>
            +{shield.current - shield.baseStrength}
          </span>
        )}
      </span>
      <div className="shield-bar-track">
        <div
          className={`shield-bar-fill ${isDown ? 'down' : shieldFillClass(visible, shield.max)}`}
          style={{ width: isDown ? '100%' : `${pct}%` }}
        />
      </div>
    </div>
  );
}

function WeaponRow({ w }: { w: WeaponState }) {
  let dotClass = 'weapon-dot';
  let statusText = '';
  let statusClass = '';

  if (!w.functional) {
    // Destroyed by damage
    dotClass  += ' dmg';
    statusText = 'DMG';
    statusClass = 'dmg';
  } else if (w.isHeavy && !w.armed) {
    // Heavy weapon not yet armed (includes partially arming)
    dotClass   += ' idle';
    statusText  = w.armingTurn > 0 ? 'ARM' : 'UNARM';
    statusClass = '';
  } else if (!w.readyToFire) {
    // Armed (or non-heavy) but cooldown / shot limit reached
    dotClass   += ' cooldown';
    statusText  = (w.maxShotsPerTurn > 1 && w.maxShotsPerTurn < 2147483647 && w.shotsThisTurn >= w.maxShotsPerTurn)
                  ? 'MAX'
                  : 'COOL';
    statusClass = 'cooldown';
  } else {
    // Ready to fire
    const remaining = w.maxShotsPerTurn - w.shotsThisTurn;
    const baseStatus = w.armingType === 'OVERLOAD' ? 'OVL'
                     : w.armingType === 'SPECIAL'  ? 'SPL'
                     : 'RDY';
    dotClass   += ' ready';
    const unlimitedShots = w.maxShotsPerTurn >= 2147483647;
    statusText  = (w.maxShotsPerTurn > 1 && !unlimitedShots) ? `${baseStatus} ${remaining}×` : baseStatus;
    statusClass = 'ready';
  }

  return (
    <div className="weapon-row">
      <span className={dotClass} />
      <span className="weapon-name">{weaponLabel(w)}</span>
      {w.arcLabel && <span className="weapon-arc">[{w.arcLabel}]</span>}
      {statusText && (
        <span className={`weapon-status ${statusClass}`}>{statusText}</span>
      )}
    </div>
  );
}

// ---- Launch helpers ----

// G13: any non-inactive cloak state blocks weapons, seekers, tractors, transporters
function isCloakOperating(ship: ShipObject): boolean {
  return !!ship.cloakState && ship.cloakState !== 'NONE' && ship.cloakState !== 'INACTIVE';
}

function hasLaunchableWeapons(ship: ShipObject): boolean {
  const hasPlasma      = (ship.weapons    ?? []).some(w => w.launcherType && w.functional && (w.armed || w.pseudoPlasmaReady));
  const hasLoadedRack  = (ship.droneRacks ?? []).some(r => r.functional && r.drones.length > 0 && r.canFire);
  const hasSuicide     = (ship.shuttleBays ?? []).some(bay => bay.shuttles.some(s => s.type === 'suicide' && s.armed && s.canLaunch));
  const hasScatterPack = (ship.shuttleBays ?? []).some(bay => bay.shuttles.some(s => s.type === 'scatterpack' && s.canLaunch && (s.payload?.length ?? 0) > 0));
  return hasPlasma || hasLoadedRack || hasSuicide || hasScatterPack;
}

// ---- Launch panel ----

// Rotates a ship-relative 24-bit arc bitmask by the ship's absolute facing so
// the result is in hex-grid absolute coordinates (direction 1 = hex-north).
// Arc masks are always stored ship-relative (bit 0 = forward).
// The FacingPicker shows absolute directions, so we must rotate before filtering.
function rotateArcMask(mask: number, facing: number): number {
  if (!mask || facing <= 1) return mask;
  const shift = facing - 1; // facing 5 → shift 4, facing 9 → shift 8, etc.
  return ((mask << shift) | (mask >>> (24 - shift))) & 0xFFFFFF;
}

// Returns the set of FacingPicker values (1,5,9,13,17,21) that fall within an
// arc bitmask that is already in absolute hex-grid coordinates.
function allowedFacingsFromMask(arcMask: number): Set<number> {
  const FACING_DIRS = [1, 5, 9, 13, 17, 21];
  return new Set(FACING_DIRS.filter(d => (arcMask >> (d - 1)) & 1));
}

// Intersects two Sets.
function intersectSets<T>(a: Set<T>, b: Set<T>): Set<T> {
  return new Set([...a].filter(x => b.has(x)));
}

// Zone-based bearing matching MapUtils.getBearing(Marker, Marker).
// Returns SFB direction 1-24, or 0 if same hex.
function hexGetBearing(srcCol: number, srcRow: number, tgtCol: number, tgtRow: number): number {
  if (srcCol === tgtCol && srcRow === tgtRow) return 0;
  const xOffset = tgtCol - srcCol;
  if (xOffset === 0) return tgtRow < srcRow ? 1 : 13;
  const absX = Math.abs(xOffset);
  if (absX % 2 === 0 && srcRow === tgtRow) return xOffset < 0 ? 10 : 4;

  const srcEven = srcCol % 2 === 0;
  const above   = srcEven ? tgtRow <= srcRow : tgtRow < srcRow;

  const topArcY = srcEven ? srcRow - Math.floor(absX / 2)       : srcRow - Math.floor((absX + 1) / 2);
  const botArcY = srcEven ? srcRow + Math.floor((absX + 1) / 2) : srcRow + Math.floor(absX / 2);

  let spineOffset: number;
  if (absX % 2 === 0) {
    spineOffset = Math.floor(absX / 2) + absX;
  } else {
    const lg = _hexLargeOdd(absX), sm = _hexSmallOdd(absX);
    spineOffset = above ? (srcEven ? sm : lg) : (srcEven ? lg : sm);
  }
  const spineY = above ? srcRow - spineOffset : srcRow + spineOffset;

  if (xOffset < 0 && above) {
    if (tgtRow === spineY)  return 23;
    if (tgtRow === topArcY) return 21;
    if (tgtRow < spineY)    return 24;
    if (tgtRow > topArcY)   return 20;
    return 22;
  }
  if (xOffset > 0 && above) {
    if (tgtRow === spineY)  return 3;
    if (tgtRow === topArcY) return 5;
    if (tgtRow < spineY)    return 2;
    if (tgtRow > topArcY)   return 6;
    return 4;
  }
  if (xOffset < 0) {
    if (tgtRow === spineY)  return 15;
    if (tgtRow === botArcY) return 17;
    if (tgtRow > spineY)    return 14;
    if (tgtRow < botArcY)   return 18;
    return 16;
  }
  // xOffset > 0, below
  if (tgtRow === spineY)  return 11;
  if (tgtRow === botArcY) return 9;
  if (tgtRow > spineY)    return 12;
  if (tgtRow < botArcY)   return 8;
  return 10;
}
function _hexLargeOdd(x: number): number { let y = 2; for (let i = 1; i < x; i += 2) y += 3; return y; }
function _hexSmallOdd(x: number): number { let y = 1; for (let i = 1; i < x; i += 2) y += 3; return y; }

// Port of MapUtils.getRelativeBearing.
function hexGetRelativeBearing(trueBearing: number, facing: number): number {
  if (facing === 1) return trueBearing;
  return trueBearing >= facing ? trueBearing - (facing - 1) : trueBearing + (24 - (facing - 1));
}

// FA = directions 21-24 and 1-5 (the seeker's forward arc).
const FA_DIRS = new Set([21, 22, 23, 24, 1, 2, 3, 4, 5]);
const ALL_FACINGS = new Set([1, 5, 9, 13, 17, 21]);

interface LaunchPanelProps {
  ship:           ShipObject;
  target:         MapObject | null;
  onLaunch:       (plasmaSelections: {name: string; pseudo: boolean; fastLoad?: boolean}[], rackSelections: {rackName: string; droneIndex: number}[], facing: number, seekerShuttles: {name: string; type: string}[], seekerSpeed: number) => void;
  onClearTarget:  () => void;
  onCancel:       () => void;
  error:          string | null;
}

function LaunchPanel({ ship, target, onLaunch, onClearTarget, onCancel, error }: LaunchPanelProps) {
  const [selLaunchers,     setSelLaunchers]     = useState<Set<string>>(new Set());
  const [pseudoSet,        setPseudoSet]        = useState<Set<string>>(new Set());
  const [fastLoadSet,      setFastLoadSet]      = useState<Set<string>>(new Set());
  const [selRackDrones,    setSelRackDrones]    = useState<Map<string, number>>(new Map());
  const [selSeekerShuttles,setSelSeekerShuttles]= useState<Set<string>>(new Set());
  const [launchFacing,     setLaunchFacing]     = useState<number | null>(null);
  const [seekerSpeed,      setSeekerSpeed]      = useState<number>(6);

  const launchablePlasma = (ship.weapons ?? []).filter(w =>
    w.launcherType && w.functional && (w.armed || w.pseudoPlasmaReady || w.canFastLoad)
  );
  const loadedRacks = (ship.droneRacks ?? []).filter(r => r.functional && r.drones.length > 0 && r.canFire);
  const launchableSeekerShuttles = (ship.shuttleBays ?? []).flatMap(bay =>
    bay.shuttles.filter(s =>
      (s.type === 'suicide' && s.armed && s.canLaunch) ||
      (s.type === 'scatterpack' && s.canLaunch && (s.payload?.length ?? 0) > 0)
    )
  );
  const targetColor = target ? mapObjectColor(target) : '#888';

  function selectReal(name: string) {
    setSelLaunchers(prev => { const n = new Set(prev); n.add(name); return n; });
    setPseudoSet(prev => { const n = new Set(prev); n.delete(name); return n; });
    setFastLoadSet(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function deselectReal(name: string) {
    setSelLaunchers(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function selectPseudo(name: string) {
    setPseudoSet(prev => { const n = new Set(prev); n.add(name); return n; });
    setSelLaunchers(prev => { const n = new Set(prev); n.delete(name); return n; });
    setFastLoadSet(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function deselectPseudo(name: string) {
    setPseudoSet(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function selectFastLoad(name: string) {
    setFastLoadSet(prev => { const n = new Set(prev); n.add(name); return n; });
    setSelLaunchers(prev => { const n = new Set(prev); n.delete(name); return n; });
    setPseudoSet(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function deselectFastLoad(name: string) {
    setFastLoadSet(prev => { const n = new Set(prev); n.delete(name); return n; });
  }
  function selectDrone(rackName: string, droneIndex: number) {
    setSelRackDrones(prev => {
      const n = new Map(prev);
      // Clicking the already-selected drone deselects the rack
      if (n.get(rackName) === droneIndex) n.delete(rackName);
      else n.set(rackName, droneIndex);
      return n;
    });
  }

  const totalSelected      = selLaunchers.size + pseudoSet.size + fastLoadSet.size + selRackDrones.size;
  const totalSeekerShuttles = selSeekerShuttles.size;
  const anythingSelected   = totalSelected > 0 || totalSeekerShuttles > 0;
  const facingRequired     = totalSelected > 0 || totalSeekerShuttles > 0;

  // Max speed for selected seeker shuttles (min of all selected, clamped ≥ 1).
  const selectedSeekerObjs = launchableSeekerShuttles.filter(s => selSeekerShuttles.has(s.name));
  const seekerMaxSpeed     = selectedSeekerObjs.length > 0
    ? Math.max(1, Math.min(...selectedSeekerObjs.map(s => s.maxSpeed)))
    : 6;
  const effectiveSeekerSpeed = Math.min(seekerSpeed, seekerMaxSpeed);

  // Compute the intersection of all selected weapons' allowed launch facings.
  // Start with all 6 facings, then filter by launchDirectionsMask (or arcMask fallback)
  // and the FA constraint (target must be in the seeker's forward arc at launch).
  let allowedFacings: Set<number> = new Set(ALL_FACINGS);

  // Launch-direction constraint: each selected plasma launcher (real or pseudo) restricts to its own set.
  // Arc masks are ship-relative, so rotate by ship facing to get absolute hex directions.
  const allSelectedLaunchers = new Set([...selLaunchers, ...pseudoSet]);
  for (const wName of allSelectedLaunchers) {
    const w = (ship.weapons ?? []).find(x => x.name === wName);
    if (w) {
      const relativeMask = w.launchDirectionsMask || w.arcMask;
      allowedFacings = intersectSets(allowedFacings, allowedFacingsFromMask(rotateArcMask(relativeMask, ship.facing)));
    }
  }
  // Drone racks: use launchDirectionsMask if set, otherwise all facings allowed.
  for (const [rackName] of selRackDrones) {
    const rack = (ship.droneRacks ?? []).find(r => r.name === rackName);
    if (rack && rack.launchDirectionsMask) {
      allowedFacings = intersectSets(allowedFacings, allowedFacingsFromMask(rotateArcMask(rack.launchDirectionsMask, ship.facing)));
    }
  }

  // FA constraint: the target must be in the seeker's FA at the chosen launch facing.
  // For each candidate facing, check if getRelativeBearing(bearing, facing) is in FA.
  if (target) {
    const srcLoc = parseLocation(ship.location ?? null);
    const tgtLoc = parseLocation(target.location ?? null);
    if (srcLoc && tgtLoc) {
      const bearing = hexGetBearing(srcLoc[0], srcLoc[1], tgtLoc[0], tgtLoc[1]);
      if (bearing > 0) {
        const faValid = new Set(
          [1, 5, 9, 13, 17, 21].filter(f => FA_DIRS.has(hexGetRelativeBearing(bearing, f)))
        );
        allowedFacings = intersectSets(allowedFacings, faValid);
      }
    }
  }

  return (
    <div className="sidebar-section fire-panel">
      <div className="sidebar-section-title fire-title" style={{ color: '#f0a050' }}>Launch Seekers</div>

      {/* Target row */}
      <div className="fire-target-row">
        <span className="sidebar-stat-label">Target</span>
        {target ? (
          <span className="fire-target-name">
            <span className="sidebar-faction-dot" style={{ background: targetColor, display: 'inline-block' }} />
            {target.name}
            <button className="fire-clear-btn secondary" onClick={onClearTarget}>✕</button>
          </span>
        ) : (
          <span className="fire-hint">Click enemy on map</span>
        )}
      </div>

      {target && (
        <div className="sidebar-stat-row">
          <span className="sidebar-stat-label">Lock-on</span>
          <span className="sidebar-stat-value" style={{ color: ship.lockOnTargets?.includes(target.name) ? '#3fb950' : '#f85149' }}>
            {ship.lockOnTargets?.includes(target.name) ? 'Yes' : 'No — eff. range ×2'}
          </span>
        </div>
      )}

      {target && (
        <>
          {/* Plasma launchers */}
          {launchablePlasma.length > 0 && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-label" style={{ marginBottom: 4 }}>Plasma Torpedoes</div>
              {launchablePlasma.map(w => (
                <div key={w.name} className="launch-weapon-row">
                  {w.armed && (
                    <label className="ea-check-label">
                      <input type="checkbox" checked={selLaunchers.has(w.name)}
                        onChange={() => selLaunchers.has(w.name) ? deselectReal(w.name) : selectReal(w.name)} />
                      {weaponLabel(w)}
                      <span className="ea-note-dim" style={{ marginLeft: 4 }}>
                        {w.armingType === 'OVERLOAD' ? 'EPT' : 'Real'}
                      </span>
                    </label>
                  )}
                  {w.pseudoPlasmaReady && (
                    <label className="ea-check-label" style={{ marginLeft: w.armed ? 16 : 0, color: '#8b949e' }}>
                      <input type="checkbox" checked={pseudoSet.has(w.name)}
                        onChange={() => pseudoSet.has(w.name) ? deselectPseudo(w.name) : selectPseudo(w.name)} />
                      {w.armed ? 'Pseudo' : weaponLabel(w)}
                      {!w.armed && <span className="ea-note-dim" style={{ marginLeft: 4 }}>Pseudo</span>}
                    </label>
                  )}
                  {w.canFastLoad && (
                    <label className="ea-check-label" style={{ marginLeft: (w.armed || w.pseudoPlasmaReady) ? 16 : 0, color: '#f0a050' }}>
                      <input type="checkbox" checked={fastLoadSet.has(w.name)}
                        onChange={() => fastLoadSet.has(w.name) ? deselectFastLoad(w.name) : selectFastLoad(w.name)} />
                      {weaponLabel(w)}
                      <span className="ea-note-dim" style={{ marginLeft: 4 }}>Fast-F (2 bty)</span>
                    </label>
                  )}
                </div>
              ))}
            </>
          )}

          {/* Drone racks */}
          {loadedRacks.length > 0 && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-label" style={{ marginBottom: 4 }}>Drones</div>
              {loadedRacks.map(r => (
                <div key={r.name} className="launch-weapon-row">
                  <div className="sidebar-stat-label" style={{ color: '#8b949e', marginBottom: 2 }}>{r.name}</div>
                  {r.drones.map((d, i) => {
                    const isSelected = selRackDrones.get(r.name) === i;
                    return (
                      <label key={i} className="ea-check-label" style={{ marginLeft: 10 }}>
                        <input type="radio" checked={isSelected}
                          onChange={() => selectDrone(r.name, i)} />
                        <span style={{ marginLeft: 4 }}>
                          [{i + 1}] {d.droneType}
                          <span className="ea-note-dim" style={{ marginLeft: 4 }}>
                            spd {d.speed}  dmg {d.warheadDamage}  end {d.endurance}
                          </span>
                        </span>
                      </label>
                    );
                  })}
                </div>
              ))}
            </>
          )}

          {/* Seeker shuttles (suicide + scatter packs) */}
          {launchableSeekerShuttles.length > 0 && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-label" style={{ marginBottom: 4 }}>Seeker Shuttles</div>
              {launchableSeekerShuttles.map(s => (
                <div key={s.name} className="launch-weapon-row">
                  <label className="ea-check-label">
                    <input type="checkbox"
                      checked={selSeekerShuttles.has(s.name)}
                      onChange={() => setSelSeekerShuttles(prev => {
                        const n = new Set(prev);
                        n.has(s.name) ? n.delete(s.name) : n.add(s.name);
                        return n;
                      })} />
                    {s.name}
                    <span className="ea-note-dim" style={{ marginLeft: 4 }}>
                      {s.type === 'suicide'
                        ? `Suicide — dmg ${s.warheadDamage}`
                        : `Scatter Pack — ${s.payload?.join(', ') ?? '0 drones'}`}
                    </span>
                  </label>
                </div>
              ))}
            </>
          )}

          {facingRequired && (
            <>
              <div className="sidebar-divider" />
              <FacingPicker
                label="Launch Facing"
                value={launchFacing}
                onChange={setLaunchFacing}
                allowedFacings={allowedFacings}
              />
            </>
          )}

          {totalSeekerShuttles > 0 && (
            <div className="sidebar-stat-row" style={{ marginTop: 8 }}>
              <span className="sidebar-stat-label">Speed</span>
              <input
                type="number"
                min={1}
                max={seekerMaxSpeed}
                value={effectiveSeekerSpeed}
                onChange={e => setSeekerSpeed(Math.max(1, Math.min(seekerMaxSpeed, parseInt(e.target.value) || 1)))}
                style={{ width: 52, background: '#161b22', color: '#e6edf3', border: '1px solid #30363d', borderRadius: 4, padding: '2px 6px', fontSize: 13 }}
              />
              <span className="ea-note-dim" style={{ marginLeft: 4 }}>/ {seekerMaxSpeed}</span>
            </div>
          )}

          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <button className="fire-btn"
              style={{ flex: 1 }}
              disabled={!anythingSelected || (facingRequired && launchFacing === null)}
              onClick={() => onLaunch(
                [
                  ...launchablePlasma.filter(w => selLaunchers.has(w.name)).map(w => ({ name: w.name, pseudo: false, fastLoad: false })),
                  ...launchablePlasma.filter(w => pseudoSet.has(w.name)).map(w => ({ name: w.name, pseudo: true, fastLoad: false })),
                  ...launchablePlasma.filter(w => fastLoadSet.has(w.name)).map(w => ({ name: w.name, pseudo: false, fastLoad: true })),
                ],
                Array.from(selRackDrones.entries()).map(([rackName, droneIndex]) => ({ rackName, droneIndex })),
                launchFacing ?? 0,
                launchableSeekerShuttles.filter(s => selSeekerShuttles.has(s.name)).map(s => ({ name: s.name, type: s.type })),
                effectiveSeekerSpeed,
              )}>
              Launch ({totalSelected + totalSeekerShuttles})
            </button>
            <button className="secondary" onClick={onCancel}>Cancel</button>
          </div>
        </>
      )}

      {!target && (
        <button className="secondary" style={{ marginTop: 6 }} onClick={onCancel}>Cancel</button>
      )}

      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Shuttle launch panel ----

const SEEKER_TYPES = new Set(['suicide', 'scatterpack']);

function hasLaunchableShuttles(ship: ShipObject): boolean {
  return (ship.shuttleBays ?? []).some(bay =>
    bay.shuttles.some(s => !SEEKER_TYPES.has(s.type) && s.canLaunch)
  );
}

interface ShuttleLaunchPanelProps {
  ship:     ShipObject;
  onLaunch: (shuttleName: string, speed: number, facing: number) => void;
  onCancel: () => void;
  error:    string | null;
}

function ShuttleLaunchPanel({ ship, onLaunch, onCancel, error }: ShuttleLaunchPanelProps) {
  const [selected, setSelected] = useState<string | null>(null);
  const [speed,    setSpeed]    = useState(1);
  const [facing,   setFacing]   = useState<number | null>(null);

  const bays = (ship.shuttleBays ?? [])
    .map(bay => ({
      ...bay,
      shuttles: bay.shuttles.filter(s => !SEEKER_TYPES.has(s.type)),
    }))
    .filter(bay => bay.shuttles.length > 0);

  const allShuttles = bays.flatMap(b => b.shuttles);
  const selectedShuttle = allShuttles.find(s => s.name === selected);
  const multiBay = bays.length > 1;

  const typeLabel = (type: string) => {
    if (type === 'gas')      return 'GAS';
    if (type === 'hts')      return 'HTS';
    if (type === 'stinger1') return 'Stinger-1';
    if (type === 'stinger2') return 'Stinger-2';
    if (type === 'stingerh') return 'Stinger-H';
    return 'Admin';
  };

  return (
    <div className="sidebar-section fire-panel">
      <div className="sidebar-section-title fire-title" style={{ color: '#f0a050' }}>Launch Shuttle</div>

      {allShuttles.length === 0 ? (
        <div className="sidebar-stat-label" style={{ color: '#8b949e' }}>No shuttles ready to launch</div>
      ) : (
        <>
          {bays.map((bay, bi) => (
            <div key={bay.bayIndex}>
              {multiBay && (
                <div className="sidebar-stat-label" style={{ marginBottom: 2, marginTop: bi > 0 ? 6 : 0 }}>
                  Bay {bay.bayIndex + 1}
                  {bay.launchTubeCount > 0 && (
                    <span className="ea-note-dim" style={{ marginLeft: 6 }}>
                      {bay.availableTubes}/{bay.launchTubeCount} tube{bay.launchTubeCount !== 1 ? 's' : ''}
                    </span>
                  )}
                </div>
              )}
              {bay.shuttles.map(s => {
                const isSel = selected === s.name;
                return (
                  <div
                    key={s.name}
                    className={`launch-weapon-row${isSel ? ' selected' : ''}`}
                    onClick={() => { if (s.canLaunch) { setSelected(s.name); setSpeed(s.maxSpeed); } }}
                    style={{
                      cursor: s.canLaunch ? 'pointer' : 'default',
                      padding: '2px 4px', borderRadius: 4,
                      background: isSel ? '#1f3a5a' : 'transparent',
                      opacity: s.canLaunch ? 1 : 0.4,
                    }}
                  >
                    <span style={{ fontWeight: isSel ? 'bold' : 'normal' }}>{s.name}</span>
                    <span className="ea-note-dim" style={{ marginLeft: 6 }}>
                      {typeLabel(s.type)} · max {s.maxSpeed}
                      {!s.canLaunch && ' · cooldown'}
                    </span>
                  </div>
                );
              })}
            </div>
          ))}

          {selected && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-row">
                <span className="sidebar-stat-label">Speed</span>
                <input
                  type="number" min={1} max={selectedShuttle?.maxSpeed ?? 6}
                  value={speed}
                  onChange={e => setSpeed(Math.max(1, Math.min(selectedShuttle?.maxSpeed ?? 6, Number(e.target.value))))}
                  style={{ width: 52, background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d',
                           borderRadius: 4, padding: '2px 4px', textAlign: 'center' }}
                />
              </div>
              <FacingPicker value={facing} onChange={setFacing} label="Facing" />
            </>
          )}
        </>
      )}

      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
        <button disabled={!selected || facing === null} onClick={() => selected && facing !== null && onLaunch(selected, speed, facing)}>Launch</button>
        <button className="secondary" onClick={onCancel}>Cancel</button>
      </div>

      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Wild Weasel launch panel ----

function WwLaunchPanel({ ship, shuttleName, onLaunch, onCancel, error }: {
  ship:         ShipObject;
  shuttleName:  string;
  onLaunch:     (shuttleName: string, speed: number, facing: number) => void;
  onCancel:     () => void;
  error:        string | null;
}) {
  const shuttle = (ship.shuttleBays ?? []).flatMap(b => b.shuttles).find(s => s.name === shuttleName);
  const maxSpeed = shuttle?.maxSpeed ?? 6;
  const [speed,  setSpeed]  = useState(maxSpeed);
  const [facing, setFacing] = useState<number | null>(ship.facing ?? null);

  return (
    <div className="sidebar-section fire-panel">
      <div className="sidebar-section-title fire-title" style={{ color: '#a78bfa' }}>
        Launch Wild Weasel — {shuttleName}
      </div>
      <div className="sidebar-stat-row">
        <span className="sidebar-stat-label">Speed (0–{maxSpeed})</span>
        <input
          type="number" min={0} max={maxSpeed} value={speed}
          onChange={e => setSpeed(Math.max(0, Math.min(maxSpeed, Number(e.target.value))))}
          style={{ width: 52, background: '#0d1117', color: '#e6edf3', border: '1px solid #30363d',
                   borderRadius: 4, padding: '2px 4px', textAlign: 'center' }}
        />
      </div>
      <FacingPicker value={facing} onChange={setFacing} label="Course" />
      <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
        <button
          disabled={facing === null}
          onClick={() => facing !== null && onLaunch(shuttleName, speed, facing)}
          style={{ borderColor: '#a78bfa', color: '#a78bfa' }}
        >
          Launch WW
        </button>
        <button className="secondary" onClick={onCancel}>Cancel</button>
      </div>
      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Shuttle movement panel ----

function ShuttleMovementPanel({
  shuttle, isMine, canMove, phase, onMove, onHet, onClose, canLand, onLand,
  onLoadPersonnel, onUnloadPersonnel,
}: {
  shuttle:  ShuttleObject;
  isMine:   boolean;
  canMove:  boolean;
  phase:    string;
  onMove:   (action: string) => void;
  onHet:    (facing: number) => void;
  onClose:  () => void;
  canLand:  boolean;
  onLand:   () => void;
  onLoadPersonnel:   () => void;
  onUnloadPersonnel: () => void;
  onTacTurn?: (facing: number, sublight: boolean) => void;
}) {
  const [hetMode, setHetMode]     = useState(false);
  const [hetFacing, setHetFacing] = useState<number | null>(null);

  const isFighter  = (shuttle.weapons?.length ?? 0) > 0;
  const canHet     = isFighter && !shuttle.crippled && !shuttle.hetUsed && phase === 'Movement';

  const typeLabel = (s: ShuttleObject) => {
    if (s.type === 'SUICIDE_SHUTTLE') return 'Suicide Shuttle';
    if (s.type === 'SCATTER_PACK')    return 'Scatter Pack';
    if ((s.weapons?.length ?? 0) > 0) return 'Fighter';
    return 'Shuttle';
  };

  return (
    <div className="board-sidebar">
      <div className="sidebar-section">
        <div className="sidebar-header-row">
          <span className="sidebar-ship-name">{shuttle.name}</span>
          <button className="sidebar-close-btn secondary" onClick={onClose}>✕</button>
        </div>
        <div className="sidebar-stat-row">
          <span className="sidebar-stat-label">Type</span>
          <span className="sidebar-stat-value">{typeLabel(shuttle)}</span>
        </div>
        <div className="sidebar-stat-row">
          <span className="sidebar-stat-label">From</span>
          <span className="sidebar-stat-value">{shuttle.parentShipName ?? '—'}</span>
        </div>
        <div className="sidebar-stat-row">
          <span className="sidebar-stat-label">Speed</span>
          <span className="sidebar-stat-value">{shuttle.speed} / {shuttle.maxSpeed}</span>
        </div>
        <div className="sidebar-stat-row">
          <span className="sidebar-stat-label">Facing</span>
          <span className="sidebar-stat-value">{facingLabel(shuttle.facing)}</span>
        </div>
      </div>

      {isMine && phase === 'Movement' && (
        <div className="sidebar-section">
          {!canMove && (
            <div className="sidebar-stat-label" style={{ color: '#8b949e', marginBottom: 4 }}>
              Moved this impulse
            </div>
          )}
          <div className="move-grid">
            {MOVE_BUTTONS.map(btn => (
              <button
                key={btn.action}
                className="move-btn secondary"
                style={{ gridRow: btn.row, gridColumn: btn.col }}
                disabled={!canMove || btn.alwaysDisabled}
                onClick={() => onMove(btn.action)}
                title={btn.action.replace(/_/g, ' ').toLowerCase()}
              >
                {btn.label}
              </button>
            ))}
          </div>

          {/* Fighter HET (C6.42) */}
          {isFighter && (
            <div className="het-strip">
              <button
                className={`action-strip-btn${hetMode ? ' active' : ''}`}
                disabled={!canHet}
                onClick={() => { setHetMode(m => !m); setHetFacing(null); }}
                title={shuttle.hetUsed ? 'HET already used this turn (C6.42)' : shuttle.crippled ? 'Crippled fighters cannot HET (J1.336)' : 'Fighter Tactical Maneuver (C6.42)'}
              >
                HET
              </button>
              {hetMode && (
                <div className="het-picker-panel">
                  <FacingPicker value={hetFacing} onChange={setHetFacing} label="New facing" />
                  <button
                    className="secondary"
                    style={{ marginTop: '0.35rem', width: '100%', fontSize: '0.75rem' }}
                    disabled={hetFacing === null}
                    onClick={async () => {
                      if (hetFacing === null) return;
                      await onHet(hetFacing);
                      setHetMode(false);
                      setHetFacing(null);
                    }}
                  >
                    Execute HET →{hetFacing !== null ? ` facing ${['A','B','C','D','E','F'][[1,5,9,13,17,21].indexOf(hetFacing)]}` : ''}
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Unassisted landing aboard a co-hexed friendly ship (J1.61) */}
          {canLand && (
            <button
              className="action-strip-btn"
              style={{ marginTop: 6, borderColor: '#3fb950', color: '#3fb950', width: '100%' }}
              onClick={onLand}
              title="Land aboard the friendly ship in this hex (J1.61)"
            >
              Land Aboard
            </button>
          )}

        </div>
      )}

      {/* Surface cargo: a shuttle landed on a planet loads/unloads crew (SH50.46).
          Its own section — an Activity-phase action, outside the movement block. */}
      {isMine && shuttle.landingPhase === 'LANDED' && (
        <div className="sidebar-section">
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Surface</span>
            <span className="sidebar-stat-value">
              side {String.fromCharCode(64 + (shuttle.landedHexSide ?? 0))} · hold {shuttle.holdCrew ?? 0} crew
              {' '}({shuttle.holdSpacesUsed ?? 0}/{shuttle.personnelCapacity ?? 0} sp)
            </span>
          </div>
          <button
            className="action-strip-btn"
            style={{ marginTop: 4, width: '100%', borderColor: '#e0b34a', color: '#e0b34a' }}
            disabled={phase !== 'Activity'}
            onClick={onLoadPersonnel}
            title="Load survey crew from this side of the planet into the hold (SH50.46)"
          >
            Load Crew ↑
          </button>
          <button
            className="action-strip-btn"
            style={{ marginTop: 4, width: '100%', borderColor: '#e0b34a', color: '#e0b34a' }}
            disabled={phase !== 'Activity' || (shuttle.holdCrew ?? 0) === 0}
            onClick={onUnloadPersonnel}
            title="Unload crew from the hold onto this side of the planet"
          >
            Unload Crew ↓
          </button>
        </div>
      )}
    </div>
  );
}

// ---- Fire options ----

interface FireOptions {
  range:         number;
  adjustedRange: number;
  shieldNumber:  number;
  weaponsInArc:  string[];
  hasLockOn:     boolean;
}

// ---- Weapon damage preview tooltip ----

function WeaponDamageTooltip({
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

interface FirePanelProps {
  attacker:        ShipObject;
  target:          MapObject | null;
  options:         FireOptions | null;
  loadingOptions:  boolean;
  selectedWeapons: Set<string>;
  onToggleWeapon:  (name: string) => void;
  shotCounts:      Map<string, number>;
  onSetShotCount:  (name: string, count: number) => void;
  useUim:          boolean;
  onToggleUim:     () => void;
  directFire:      boolean;
  onToggleDirectFire: () => void;
  onFire:          () => void;
  onClearTarget:   () => void;
  error:           string | null;
}

function FirePanel({
  attacker, target, options, loadingOptions,
  selectedWeapons, onToggleWeapon, shotCounts, onSetShotCount,
  useUim, onToggleUim, directFire, onToggleDirectFire, onFire, onClearTarget, error,
}: FirePanelProps) {
  const targetColor    = target ? mapObjectColor(target) : '#888';
  const [hoveredWeapon, setHoveredWeapon] = useState<string | null>(null);

  return (
    <div className="sidebar-section fire-panel">
      <div className="sidebar-section-title fire-title">Direct Fire</div>

      {/* Target row */}
      <div className="fire-target-row">
        <span className="sidebar-stat-label">Target</span>
        {target ? (
          <span className="fire-target-name">
            <span className="sidebar-faction-dot" style={{ background: targetColor, display: 'inline-block' }} />
            {target.name}
            <button className="fire-clear-btn secondary" onClick={onClearTarget}>✕</button>
          </span>
        ) : (
          <span className="fire-hint">Click enemy on map</span>
        )}
      </div>

      {/* Range + shield */}
      {options && (
        <>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Range</span>
            <span className="sidebar-stat-value">
              {options.range}
              {options.adjustedRange !== options.range && (
                <span className="fire-adj-range"> ({options.adjustedRange} adj)</span>
              )}
            </span>
          </div>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Shield hit</span>
            <span className="sidebar-stat-value">
              #{options.shieldNumber} {SHIELD_NAMES[options.shieldNumber] ?? ''}
            </span>
          </div>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Lock-on</span>
            <span className="sidebar-stat-value" style={{ color: options.hasLockOn ? '#3fb950' : '#f85149' }}>
              {options.hasLockOn ? 'Yes' : 'No — eff. range ×2'}
            </span>
          </div>
          {(() => {
            const tShip = target && (target as ShipObject).ecmAllocated !== undefined ? target as ShipObject : null;
            const tEcm  = tShip?.ecmAllocated ?? 0;
            const aEccm = attacker.eccmAllocated ?? 0;
            const net   = Math.max(0, tEcm - aEccm);
            const shift = Math.floor(Math.sqrt(net));
            if (tEcm === 0 && aEccm === 0) return null;
            return (
              <div className="sidebar-stat-row">
                <span className="sidebar-stat-label">EW</span>
                <span className="sidebar-stat-value">
                  ECM {tEcm} / ECCM {aEccm}
                  {shift > 0 && <span style={{ color: '#f85149' }}> → +{shift} shift</span>}
                </span>
              </div>
            );
          })()}
        </>
      )}

      {loadingOptions && <div className="fire-hint">Calculating…</div>}

      {/* Weapon checkboxes */}
      {options && options.weaponsInArc.length > 0 && (
        <>
          <div className="sidebar-divider" />
          <div className="fire-weapon-list">
            {attacker.weapons
              .filter(w => w.functional && !w.launcherType)
              .map(w => {
                const inArc = options.weaponsInArc.includes(w.name);
                const checked = selectedWeapons.has(w.name);

                // Reason this weapon can't fire (only relevant when not in arc list)
                let unavailLabel: string | null = null;
                if (!inArc) {
                  if (w.isHeavy && !w.armed)  unavailLabel = 'unarmed';
                  else if (!w.readyToFire)     unavailLabel = 'on cooldown';
                  else                          unavailLabel = 'out of arc';
                }

                const isMultiShot = w.minImpulseGap === 0 && w.maxShotsPerTurn > 1;
                const maxShots    = w.maxShotsPerTurn - w.shotsThisTurn;
                const shots       = Math.min(shotCounts.get(w.name) ?? 1, maxShots);

                return (
                  <div
                    key={w.name}
                    className={`fire-weapon-row ${unavailLabel ? 'out-of-arc' : ''}`}
                    onMouseEnter={() => setHoveredWeapon(w.name)}
                    onMouseLeave={() => setHoveredWeapon(null)}
                  >
                    <label className="fire-weapon-label">
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!!unavailLabel}
                        onChange={() => onToggleWeapon(w.name)}
                      />
                      <span className="weapon-name">{weaponLabel(w)}</span>
                      {w.arcLabel && <span className="weapon-arc">[{w.arcLabel}]</span>}
                      {unavailLabel && <span className={`fire-ooa ${unavailLabel === 'on cooldown' ? 'fire-cooldown' : ''}`}>{unavailLabel}</span>}
                    </label>
                    {isMultiShot && checked && (
                      <div className="shot-stepper">
                        <button className="shot-step-btn" onClick={e => { e.preventDefault(); onSetShotCount(w.name, Math.max(1, shots - 1)); }}>−</button>
                        <span className="shot-count">{shots}×</span>
                        <button className="shot-step-btn" onClick={e => { e.preventDefault(); onSetShotCount(w.name, Math.min(maxShots, shots + 1)); }}>+</button>
                      </div>
                    )}
                    {hoveredWeapon === w.name && options && (
                      <WeaponDamageTooltip
                        w={w}
                        range={options.range}
                        adjustedRange={options.adjustedRange}
                        directFire={directFire}
                      />
                    )}
                  </div>
                );
              })}
          </div>

          {/* Plasma bolt section — armed plasma launchers fire as direct-fire bolts here */}
          {attacker.weapons.some(w => w.functional && w.launcherType) && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-label" style={{ marginBottom: 4, color: '#f0a050' }}>Plasma Bolts</div>
              <div className="fire-weapon-list">
                {attacker.weapons
                  .filter(w => w.functional && w.launcherType)
                  .map(w => {
                    const inArc    = options.weaponsInArc.includes(w.name);
                    const checked  = selectedWeapons.has(w.name);
                    const disabled = !inArc;
                    const unavailLabel = !inArc
                      ? (w.isHeavy && !w.armed ? 'unarmed' : 'out of arc')
                      : null;
                    return (
                      <div
                        key={w.name}
                        className={`fire-weapon-row ${disabled ? 'out-of-arc' : ''}`}
                        onMouseEnter={() => setHoveredWeapon(w.name)}
                        onMouseLeave={() => setHoveredWeapon(null)}
                      >
                        <label className="fire-weapon-label">
                          <input
                            type="checkbox"
                            checked={checked}
                            disabled={disabled}
                            onChange={() => onToggleWeapon(w.name)}
                          />
                          <span className="weapon-name">{weaponLabel(w)}</span>
                          {w.arcLabel && <span className="weapon-arc">[{w.arcLabel}]</span>}
                          {!disabled && <span className="weapon-arc" style={{ color: '#f0a050' }}>bolt</span>}
                          {unavailLabel && <span className="fire-ooa">{unavailLabel}</span>}
                        </label>
                        {hoveredWeapon === w.name && options && (
                          <WeaponDamageTooltip
                            w={w}
                            range={options.range}
                            adjustedRange={options.adjustedRange}
                            directFire={directFire}
                          />
                        )}
                      </div>
                    );
                  })}
              </div>
            </>
          )}

          {attacker.uimFunctional && (
            <label className="fire-uim-row">
              <input
                type="checkbox"
                checked={useUim}
                onChange={onToggleUim}
              />
              <span>Use UIM targeting (D6.51)</span>
            </label>
          )}
          {Array.from(selectedWeapons).some(name =>
            attacker.weapons.find(w => w.name === name)?.name.startsWith('Hellbore')
          ) && (
            <label className="fire-uim-row">
              <input
                type="checkbox"
                checked={directFire}
                onChange={onToggleDirectFire}
              />
              <span>Direct Fire mode — half dmg, facing shield (E10.7)</span>
            </label>
          )}
          <button
            className="fire-btn"
            disabled={selectedWeapons.size === 0}
            onClick={onFire}
          >
            {(() => {
              const totalShots = Array.from(selectedWeapons).reduce((sum, name) => {
                const w = attacker.weapons.find(x => x.name === name);
                return sum + (w && w.minImpulseGap === 0 && w.maxShotsPerTurn > 1
                  ? Math.min(shotCounts.get(name) ?? 1, w.maxShotsPerTurn - w.shotsThisTurn)
                  : 1);
              }, 0);
              return `Fire (${totalShots} shot${totalShots !== 1 ? 's' : ''})`;
            })()}
          </button>
        </>
      )}

      {options && options.weaponsInArc.length === 0 && (
        <div className="fire-hint">No weapons bear on this target</div>
      )}

      {/* ADD section — always visible when ship carries ADDs */}
      {attacker.weapons.some(w => w.functional && w.addCapacity) && (
        <>
          <div className="sidebar-divider" />
          <div className="sidebar-stat-label" style={{ marginBottom: 4, color: '#50d0f0' }}>
            Anti-Drone (ADD)
          </div>
          <div className="fire-weapon-list">
            {attacker.weapons
              .filter(w => w.functional && w.addCapacity)
              .map(w => {
                const inArc    = options?.weaponsInArc.includes(w.name) ?? false;
                const checked  = selectedWeapons.has(w.name);
                const noAmmo   = (w.addShots ?? 0) === 0;
                const noTarget = !options;
                let unavailLabel: string | null = null;
                if (noAmmo)        unavailLabel = 'no shots';
                else if (noTarget) unavailLabel = 'select drone/shuttle target';
                else if (!inArc)   unavailLabel = 'out of range (1-3)';
                return (
                  <div key={w.name} className={`fire-weapon-row ${unavailLabel ? 'out-of-arc' : ''}`}>
                    <label className="fire-weapon-label">
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!!unavailLabel}
                        onChange={() => onToggleWeapon(w.name)}
                      />
                      <span className="weapon-name">{w.name}</span>
                      <span className="weapon-arc" style={{ color: '#50d0f0' }}>
                        {w.addShots}/{w.addCapacity}
                        {(w.addReloads ?? 0) > 0 && ` (+${w.addReloads})`}
                      </span>
                      {unavailLabel && <span className="fire-ooa">{unavailLabel}</span>}
                    </label>
                  </div>
                );
              })}
          </div>
        </>
      )}

      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Fighter fire panel ----

interface FighterFirePanelProps {
  fighter:         ShuttleObject;
  target:          MapObject | null;
  options:         FireOptions | null;
  loadingOptions:  boolean;
  selectedWeapons: Set<string>;
  onToggleWeapon:  (name: string) => void;
  shotModes:       Record<string, 'SINGLE' | 'DOUBLE'>;
  onSetShotMode:   (name: string, mode: 'SINGLE' | 'DOUBLE') => void;
  onFire:          () => void;
  onClear:         () => void;
  error:           string | null;
}

function FighterFirePanel({
  fighter, target, options, loadingOptions,
  selectedWeapons, onToggleWeapon,
  shotModes, onSetShotMode,
  onFire, onClear, error,
}: FighterFirePanelProps) {
  const weapons = fighter.weapons ?? [];
  const targetColor = target ? mapObjectColor(target) : '#888';
  const shortName = fighter.name.includes('-')
    ? fighter.name.slice(fighter.name.lastIndexOf('-', fighter.name.lastIndexOf('-') - 1) + 1)
    : fighter.name;

  return (
    <div className="sidebar-section fire-panel" style={{ borderColor: '#9b3ad5' }}>
      <div className="sidebar-section-title fire-title" style={{ color: fighter.crippled ? '#e05050' : '#9b3ad5' }}>
        Fighter: {shortName}{fighter.crippled ? ' ⚠ CRIPPLED' : ''}
      </div>

      <div className="fire-target-row">
        <span className="sidebar-stat-label">Target</span>
        {target ? (
          <span className="fire-target-name">
            <span className="sidebar-faction-dot" style={{ background: targetColor, display: 'inline-block' }} />
            {target.name}
            <button className="fire-clear-btn secondary" onClick={onClear}>✕</button>
          </span>
        ) : (
          <span className="fire-hint">Click enemy on map</span>
        )}
      </div>

      {options && (
        <>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Range</span>
            <span className="sidebar-stat-value">{options.range}</span>
          </div>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Shield hit</span>
            <span className="sidebar-stat-value">#{options.shieldNumber} {SHIELD_NAMES[options.shieldNumber] ?? ''}</span>
          </div>
        </>
      )}

      {loadingOptions && <div className="fire-hint">Calculating…</div>}

      {options && weapons.length > 0 && (
        <>
          <div className="sidebar-divider" />
          <div className="fire-weapon-list">
            {weapons.map(w => {
              const inArc   = options.weaponsInArc.includes(w.name);
              const checked = selectedWeapons.has(w.name);
              const isFusion = w.chargesRemaining !== undefined;
              const mode = shotModes[w.name] ?? 'SINGLE';
              let unavailLabel: string | null = null;
              if (!inArc) {
                if ((w.chargesRemaining ?? 1) === 0) unavailLabel = 'no charges';
                else if (!w.readyToFire)             unavailLabel = 'on cooldown';
                else                                  unavailLabel = 'out of arc';
              }
              return (
                <div key={w.name} className={`fire-weapon-row ${unavailLabel ? 'out-of-arc' : ''}`}>
                  <label className="fire-weapon-label">
                    <input
                      type="checkbox"
                      checked={checked}
                      disabled={!!unavailLabel}
                      onChange={() => onToggleWeapon(w.name)}
                    />
                    <span className="weapon-name">{weaponLabel(w)}</span>
                    {w.arcLabel && <span className="weapon-arc">[{w.arcLabel}]</span>}
                    {isFusion && (
                      <span className="weapon-arc" style={{ color: '#f0c040' }}>⚡{w.chargesRemaining}</span>
                    )}
                    {unavailLabel && (
                      <span className={`fire-ooa${unavailLabel === 'on cooldown' ? ' fire-cooldown' : ''}`}>
                        {unavailLabel}
                      </span>
                    )}
                  </label>
                  {isFusion && checked && !unavailLabel && (
                    <div className="shot-stepper">
                      <button
                        className={`shot-step-btn${mode === 'SINGLE' ? ' active' : ''}`}
                        title="Single shot (1 charge, range 0-3)"
                        onClick={e => { e.preventDefault(); onSetShotMode(w.name, 'SINGLE'); }}
                      >1×</button>
                      <button
                        className={`shot-step-btn${mode === 'DOUBLE' ? ' active' : ''}`}
                        disabled={!w.canFireDouble}
                        title="Double shot (2 charges, range 0-10)"
                        onClick={e => { e.preventDefault(); onSetShotMode(w.name, 'DOUBLE'); }}
                      >2×</button>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
          <button
            className="fire-btn"
            disabled={selectedWeapons.size === 0}
            onClick={onFire}
          >
            Fire ({selectedWeapons.size} weapon{selectedWeapons.size !== 1 ? 's' : ''})
          </button>
        </>
      )}

      {options && weapons.length === 0 && (
        <div className="fire-hint">No weapons on this fighter</div>
      )}

      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Move button grid ----

const MOVE_BUTTONS: { label: string; action: string; row: number; col: number; alwaysDisabled?: boolean }[] = [
  { label: '↖',  action: 'SIDESLIP_LEFT',   row: 1, col: 1 },
  { label: '▲',  action: 'FORWARD',          row: 1, col: 2 },
  { label: '↗',  action: 'SIDESLIP_RIGHT',   row: 1, col: 3 },
  { label: '↰',  action: 'TURN_LEFT',        row: 2, col: 1 },
  { label: '·', action: 'COAST', row: 2, col: 2, alwaysDisabled: true },
  { label: '↱',  action: 'TURN_RIGHT',       row: 2, col: 3 },
];

// SFB hex range — replicates MapUtils.getRange (x = col, y = row).
function hexRange(s1: { col: number; row: number }, s2: { col: number; row: number }): number {
  const xDiff = Math.abs(s2.col - s1.col);
  if (xDiff === 0) return Math.abs(s2.row - s1.row);
  const even   = s1.col % 2 === 0;
  const topY    = even ? s1.row - Math.floor(xDiff / 2) : s1.row - Math.floor((xDiff + 1) / 2);
  const bottomY = even ? s1.row + Math.floor((xDiff + 1) / 2) : s1.row + Math.floor(xDiff / 2);
  if (s2.row >= topY && s2.row <= bottomY) return xDiff;
  return s2.row < topY ? xDiff + (topY - s2.row) : xDiff + (s2.row - bottomY);
}

// ---- Ship sidebar ----

interface SidebarProps {
  ship:            ShipObject;
  isMine:          boolean;
  canMove:         boolean;
  phase:           string;
  gameId:          string;
  playerToken:     string;
  fireTarget:      MapObject | null;
  fireOptions:     FireOptions | null;
  loadingOptions:  boolean;
  selectedWeapons: Set<string>;
  onToggleWeapon:  (name: string) => void;
  shotCounts:      Map<string, number>;
  onSetShotCount:  (name: string, count: number) => void;
  useUim:             boolean;
  onToggleUim:        () => void;
  directFire:         boolean;
  onToggleDirectFire: () => void;
  onFire:             () => void;
  onClearTarget:      () => void;
  fireError:          string | null;
  onMove:          (action: string) => void;
  onHet:           (facing: number) => Promise<void>;
  onTacTurn:       (facing: number, sublight: boolean) => Promise<void>;
  onCloak:         () => void;
  onUncloak:       () => void;
  onClose:         () => void;
  // Launch
  launchMode:      boolean;
  launchTarget:    MapObject | null;
  launchError:     string | null;
  onStartLaunch:   () => void;
  onClearLaunch:   () => void;
  onLaunch:        (plasma: {name: string; pseudo: boolean; fastLoad?: boolean}[], racks: {rackName: string; droneIndex: number}[], facing: number) => void;
  // T-bomb (transporter)
  tBombMode:        boolean;
  tBombPendingHex:  {col: number; row: number} | null;
  tBombShieldChoice: {isReal: boolean; shields: number[]} | null;
  onStartTBomb:     () => void;
  onCancelTBomb:    () => void;
  onPlaceTBomb:     (isReal: boolean, shieldNumber?: number) => void;
  // Drop mine (shuttle bay)
  dropMineMode:    boolean;
  onToggleDropMine: () => void;
  onDropMine:      (mineType: 'TBOMB' | 'DUMMY_TBOMB' | 'NSM') => void;
  // Boarding
  boardingMode:     boolean;
  boardingTarget:   ShipObject | null;
  boardingNormal:   number;
  boardingCommandos: number;
  boardingError:    string | null;
  onStartBoarding:  () => void;
  onCancelBoarding: () => void;
  onSetBoardingNormal:    (n: number) => void;
  onSetBoardingCommandos: (n: number) => void;
  onSubmitBoarding: () => void;
  // Lab seeker identification
  idMode:           boolean;
  idSeekers:        { name: string; type: string }[];
  idSelected:       Set<string>;
  idError:          string | null;
  onStartId:        () => void;
  onCancelId:       () => void;
  onToggleIdSeeker: (name: string) => void;
  onSubmitId:       () => void;
  // Shuttle launch
  shuttleLaunchMode:  boolean;
  shuttleLaunchError: string | null;
  onStartShuttleLaunch:  () => void;
  onCancelShuttleLaunch: () => void;
  onLaunchShuttle: (shuttleName: string, speed: number, facing: number) => void;
  wwLaunchShuttle:     string | null;
  wwLaunchError:       string | null;
  onStartWwLaunch:     (shuttleName: string) => void;
  onCancelWwLaunch:    () => void;
  onLaunchWildWeasel:  (shuttleName: string, speed: number, facing: number) => void;
  // Hit & Run
  harMode:      boolean;
  harTarget:    ShipObject | null;
  harOptions:   { code: string; label: string }[];
  harParties:   (string | null)[];
  harError:     string | null;
  harLoading:   boolean;
  onStartHar:   () => void;
  onCancelHar:  () => void;
  onSetHarParties: (parties: (string | null)[]) => void;
  onSubmitHar:  () => void;
  // Transporters submenu
  transportersOpen:    boolean;
  onToggleTransporters: () => void;
  // Beam a free objective aboard by transporter (SH47) — objective-pick mode
  beamObjectMode:       boolean;
  canBeamObjectTargets: boolean;
  onStartBeamObject:    () => void;
  onCancelBeamObject:   () => void;
  // Transport crew
  crewMode:        boolean;
  crewTarget:      ShipObject | null;
  crewAmount:      number;
  crewError:       string | null;
  onStartCrew:     () => void;
  onCancelCrew:    () => void;
  onSetCrewAmount: (n: number) => void;
  onSubmitCrew:    () => void;
  // Disengagement
  onDisengageSeparation: () => void;
  // Fire Control (D6.6)
  onGoPassiveFc: () => void;
  onGoActiveFc:  () => void;
  // Emergency deceleration (C8.0)
  absoluteImpulse: number;
  onEmergencyDecel: () => void;
  // Tractor beams (G7.0)
  tractorMode:          boolean;
  tractorError:         string | null;
  onStartTractor:       () => void;
  onCancelTractor:      () => void;
  onReleaseTractor:     (targetName: string) => void;
  // Tractor bid dialog (attacker)
  tractorBidTarget:        string | null;
  tractorBidValue:         number;
  tractorRangeMultiplier:  number;
  onSetTractorBid:         (v: number) => void;
  onSubmitTractorBid:      () => void;
  onCancelTractorBid:      () => void;
  tractorBidMax:           number;
  // Negative-tractor bid dialog (defender)
  pendingTractorAuction: import('../types/gameState').PendingTractorAuction | null;
  negTractorBidValue:  number;
  onSetNegTractorBid:  (v: number) => void;
  onSubmitNegTractorBid: () => void;
  // J1.621 shuttle recovery
  heldFriendlyShuttles: Set<string>;
  onRecoverShuttle: (shuttleName: string) => void;
  // J1.621 canister recovery (SH35.452)
  tractoredObjectives: ObjectiveObject[];
  onRecoverObjective: (objectiveName: string) => void;
  // Tractor rotation (G7.7) — Initial Activity Phase
  rotateMode:    boolean;
  rotateTarget:  string | null;
  rotateError:   string | null;
  onStartRotate: (targetName: string) => void;
  onCancelRotate: () => void;
}

function ShipSidebar({
  ship, isMine, canMove, phase, gameId, playerToken,
  fireTarget, fireOptions, loadingOptions, selectedWeapons,
  onToggleWeapon, shotCounts, onSetShotCount, useUim, onToggleUim, directFire, onToggleDirectFire, onFire, onClearTarget, fireError,
  onMove, onHet, onTacTurn, onCloak, onUncloak, onClose,
  launchMode, launchTarget, launchError, onStartLaunch, onClearLaunch, onLaunch,
  tBombMode, tBombPendingHex, tBombShieldChoice, onStartTBomb, onCancelTBomb, onPlaceTBomb,
  dropMineMode, onToggleDropMine, onDropMine,
  boardingMode, boardingTarget, boardingNormal, boardingCommandos, boardingError,
  onStartBoarding, onCancelBoarding, onSetBoardingNormal, onSetBoardingCommandos, onSubmitBoarding,
  idMode, idSeekers, idSelected, idError, onStartId, onCancelId, onToggleIdSeeker, onSubmitId,
  shuttleLaunchMode, shuttleLaunchError, onStartShuttleLaunch, onCancelShuttleLaunch, onLaunchShuttle,
  wwLaunchShuttle, wwLaunchError, onStartWwLaunch, onCancelWwLaunch, onLaunchWildWeasel,
  harMode, harTarget, harOptions, harParties, harError, harLoading,
  onStartHar, onCancelHar, onSetHarParties, onSubmitHar,
  transportersOpen, onToggleTransporters,
  beamObjectMode, canBeamObjectTargets, onStartBeamObject, onCancelBeamObject,
  crewMode, crewTarget, crewAmount, crewError,
  onStartCrew, onCancelCrew, onSetCrewAmount, onSubmitCrew,
  onDisengageSeparation,
  onGoPassiveFc, onGoActiveFc,
  absoluteImpulse, onEmergencyDecel,
  tractorMode, tractorError, onStartTractor, onCancelTractor, onReleaseTractor,
  tractorBidTarget, tractorBidValue, tractorRangeMultiplier, onSetTractorBid, onSubmitTractorBid, onCancelTractorBid, tractorBidMax,
  pendingTractorAuction, negTractorBidValue, onSetNegTractorBid, onSubmitNegTractorBid,
  heldFriendlyShuttles, onRecoverShuttle,
  tractoredObjectives, onRecoverObjective,
  rotateMode, rotateTarget, rotateError, onStartRotate, onCancelRotate,
}: SidebarProps) {
  const [hetMode,   setHetMode]   = useState(false);
  const [hetFacing, setHetFacing] = useState<number | null>(null);
  const [tacMode,   setTacMode]   = useState(false);

  const color = factionColor(ship.faction);
  const totalPower = (ship.availableLWarp  ?? 0) + (ship.availableRWarp  ?? 0)
                 + (ship.availableCWarp  ?? 0) + (ship.availableImpulse ?? 0)
                 + (ship.availableApr    ?? 0) + (ship.availableAwr     ?? 0)
                 + (ship.availableBattery ?? 0);
  const isFirePhase            = phase === 'Direct Fire';
  const isActivityPhase        = phase === 'Activity';
  const isInitialActivityPhase = phase === 'Initial Activity';
  const canLaunch       = isActivityPhase && hasLaunchableWeapons(ship);
  const canTBomb        = isActivityPhase && isMine
                        && (ship.tBombs > 0 || ship.dummyTBombs > 0)
                        && (ship.availableTransporters ?? 0) > 0;
  const canDropMine     = isActivityPhase && isMine
                        && (ship.tBombs > 0 || ship.dummyTBombs > 0 || (ship.nuclearSpaceMines ?? 0) > 0);
  const canBoard        = isActivityPhase && isMine
                        && (ship.boardingParties > 0 || ship.commandos > 0)
                        && (ship.availableTransporters ?? 0) > 0;
  const canHar          = isActivityPhase && isMine
                        && ship.boardingParties > 0
                        && (ship.availableTransporters ?? 0) > 0;
  const canTransferCrew = isActivityPhase && isMine
                        && (ship.availableCrewUnits ?? 0) > 0
                        && (ship.availableTransporters ?? 0) > 0;
  const canBeamObject   = isActivityPhase && isMine
                        && (ship.availableTransporters ?? 0) > 0
                        && canBeamObjectTargets;
  const canUseTransporters = canTBomb || canBoard || canHar || canTransferCrew || canBeamObject;
  const canIdentify     = isActivityPhase && isMine && (ship.availableLab ?? 0) > 0 && idSeekers.length > 0;
  const canHet          = phase === 'Movement' && isMine
                        && (ship.hetCost ?? 0) > 0
                        && (ship.reserveWarp ?? 0) >= (ship.hetCost ?? 1);
  const canTac          = phase === 'Movement' && isMine && ship.speed === 0
                        && ((ship.tacAvailable ?? 0) > 0 || ship.sublightTacAvailable === true);
  const maxHarParties   = Math.min(ship.boardingParties, ship.availableTransporters ?? 0);
  const maxBoardingTotal = Math.min(
    ship.boardingParties + ship.commandos,
    Math.min(ship.availableTransporters ?? 0, ship.transporterEnergyCost ? Math.floor(1 / ship.transporterEnergyCost) : 999),
  );

  return (
    <div className="board-sidebar">
      <div className="sidebar-header">
        <span className="sidebar-faction-dot" style={{ background: color }} />
        <span className="sidebar-ship-name">{ship.name}</span>
        <button className="sidebar-close secondary" onClick={onClose}>✕</button>
      </div>

      {/* ---- Action strip ---- */}
      {isMine && (
        <div className="sidebar-action-strip">

          {/* Movement phase: compact move grid */}
          {phase === 'Movement' && (
            <>
              {!canMove && (
                <div style={{ fontSize: '0.7rem', color: '#6e7681', marginBottom: '0.3rem' }}>
                  Not your move this impulse
                </div>
              )}
              <div className="move-grid">
                {MOVE_BUTTONS.map(btn => (
                  <button
                    key={btn.action}
                    className="move-btn secondary"
                    style={{ gridRow: btn.row, gridColumn: btn.col }}
                    disabled={!canMove || btn.alwaysDisabled}
                    onClick={() => onMove(btn.action)}
                    title={btn.action.replace(/_/g, ' ').toLowerCase()}
                  >
                    {btn.label}
                  </button>
                ))}
              </div>

              {/* HET button + facing picker */}
              {isMine && (ship.hetCost ?? 0) > 0 && (
                <div className="het-strip">
                  <button
                    className={`action-strip-btn${hetMode ? ' active' : ''}`}
                    disabled={!canHet}
                    onClick={() => { setHetMode(m => !m); setHetFacing(null); }}
                    title={`High Energy Turn — costs ${ship.hetCost ?? '?'} warp (${ship.reserveWarp ?? 0} reserved)`}
                  >
                    HET
                  </button>
                  {hetMode && (
                    <div className="het-picker-panel">
                      <FacingPicker
                        value={hetFacing}
                        onChange={setHetFacing}
                        label="New facing"
                      />
                      <button
                        className="secondary"
                        style={{ marginTop: '0.35rem', width: '100%', fontSize: '0.75rem' }}
                        disabled={hetFacing === null}
                        onClick={async () => {
                          if (hetFacing === null) return;
                          await onHet(hetFacing);
                          setHetMode(false);
                          setHetFacing(null);
                        }}
                      >
                        Execute HET →{hetFacing !== null ? ` facing ${['A','B','C','D','E','F'][[1,5,9,13,17,21].indexOf(hetFacing)]}` : ''}
                      </button>
                    </div>
                  )}
                </div>
              )}

              {/* Tactical Maneuver strip — shown when a TAC is available (C5.0) */}
              {isMine && (canTac || (ship.speed === 0 && ((ship.tacBudget ?? 0) > 0 || ship.sublightTacAvailable))) && (
                <div className="het-strip">
                  <button
                    className={`action-strip-btn${tacMode ? ' active' : ''}`}
                    disabled={!canTac}
                    onClick={() => setTacMode(m => !m)}
                    title={
                      (ship.tacAvailable ?? 0) > 0
                        ? `Warp Tactical Maneuver — turn 60° (${ship.tacBudget ?? 0} budget remaining)`
                        : ship.sublightTacAvailable
                        ? 'Sublight Tactical Maneuver — turn 60°'
                        : 'No Tactical Maneuver earned yet this impulse'
                    }
                  >
                    TAC {(ship.tacAvailable ?? 0) > 0 ? '(W)' : ship.sublightTacAvailable ? '(S)' : ''}
                  </button>
                  {tacMode && canTac && (() => {
                    const FACINGS = [1, 5, 9, 13, 17, 21];
                    const curIdx  = FACINGS.indexOf(ship.facing);
                    const leftFacing  = FACINGS[(curIdx + 5) % 6]; // -60°
                    const rightFacing = FACINGS[(curIdx + 1) % 6]; // +60°
                    const sublight    = (ship.tacAvailable ?? 0) === 0 && !!ship.sublightTacAvailable;
                    const facingNames = ['A','B','C','D','E','F'];
                    return (
                      <div className="het-picker-panel">
                        <div style={{ fontSize: '0.7rem', color: '#8b949e', marginBottom: 6 }}>
                          Turn 60° — current facing {facingNames[curIdx] ?? ship.facing}
                          {sublight ? ' (Sublight)' : ' (Warp)'}
                        </div>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button
                            className="secondary"
                            style={{ flex: 1, fontSize: '0.75rem' }}
                            onClick={async () => {
                              await onTacTurn(leftFacing, sublight);
                              setTacMode(false);
                            }}
                          >
                            ← {facingNames[(curIdx + 5) % 6]}
                          </button>
                          <button
                            className="secondary"
                            style={{ flex: 1, fontSize: '0.75rem' }}
                            onClick={async () => {
                              await onTacTurn(rightFacing, sublight);
                              setTacMode(false);
                            }}
                          >
                            {facingNames[(curIdx + 1) % 6]} →
                          </button>
                        </div>
                      </div>
                    );
                  })()}
                </div>
              )}
            </>
          )}

          {/* Initial Activity phase: tractor rotation (G7.7) — blocked while the cloak operates (G13) */}
          {isInitialActivityPhase && (ship.tractoredTargetNames ?? []).length > 0 &&
           !isCloakOperating(ship) && (
            <div className="action-btn-row">
              {(ship.tractoredTargetNames ?? []).map(targetName => (
                <button
                  key={`rotate-${targetName}`}
                  className={`action-strip-btn${rotateMode && rotateTarget === targetName ? ' active' : ''}`}
                  onClick={() => rotateMode && rotateTarget === targetName
                    ? onCancelRotate()
                    : onStartRotate(targetName)}
                  title={`Rotate ${targetName} to adjacent hex (G7.7)`}
                  style={{ borderColor: '#a78bfa', color: '#a78bfa' }}
                >
                  Rotate {targetName}
                </button>
              ))}
            </div>
          )}

          {/* Activity phase: row of action buttons + transporter submenu */}
          {phase === 'Activity' && (
            <>
              <div className="action-btn-row">
                {hasLaunchableWeapons(ship) && (
                  <button
                    className={`action-strip-btn${launchMode ? ' active' : ''}`}
                    onClick={launchMode ? onClearLaunch : onStartLaunch}
                    title="Launch seekers"
                  >
                    Seekers
                  </button>
                )}
                {hasLaunchableShuttles(ship) && (
                  <button
                    className={`action-strip-btn${shuttleLaunchMode ? ' active' : ''}`}
                    onClick={shuttleLaunchMode ? onCancelShuttleLaunch : onStartShuttleLaunch}
                    title="Launch shuttle"
                  >
                    Shuttle
                  </button>
                )}
                {(ship.shuttleBays ?? []).flatMap(b => b.shuttles).filter(s => s.wwReady && s.canLaunch).map(s => (
                  <button
                    key={s.name}
                    className={`action-strip-btn${wwLaunchShuttle === s.name ? ' active' : ''}`}
                    onClick={() => wwLaunchShuttle === s.name ? onCancelWwLaunch() : onStartWwLaunch(s.name)}
                    title={`Launch Wild Weasel ${s.name} — set course and speed`}
                    style={{ borderColor: '#a78bfa', color: '#a78bfa' }}
                  >
                    WW {s.name}
                  </button>
                ))}
                {/* Tractor beam — establish (G7.3); unavailable while the cloak operates (G13) */}
                {(ship.availableTractors ?? 0) > 0 && (ship.tractorEnergy ?? 0) > 0 &&
                 (ship.tractoredTargetNames ?? []).length < (ship.availableTractors ?? 0) &&
                 !isCloakOperating(ship) && (
                  <button
                    className={`action-strip-btn${tractorMode ? ' active' : ''}`}
                    onClick={tractorMode ? onCancelTractor : onStartTractor}
                    title="Click an adjacent enemy ship to establish tractor beam (G7.3)"
                    style={{ borderColor: '#22d3ee', color: '#22d3ee' }}
                  >
                    Tractor
                  </button>
                )}
                {/* J1.621 special recovery — pull a held friendly shuttle aboard */}
                {(ship.tractoredTargetNames ?? []).filter(n => heldFriendlyShuttles.has(n)).map(targetName => (
                  <button
                    key={`recover-${targetName}`}
                    className="action-strip-btn"
                    onClick={() => onRecoverShuttle(targetName)}
                    title={`Begin special recovery of ${targetName} (J1.621) — pulled one hex closer each impulse`}
                    style={{ borderColor: '#3fb950', color: '#3fb950' }}
                  >
                    Recover {targetName}
                  </button>
                ))}
                {/* J1.621 canister recovery / release — SH35.452 */}
                {tractoredObjectives.filter(o => o.tractoredBy === ship.name).map(o => (
                  <Fragment key={`obj-${o.name}`}>
                    {o.beingRecovered ? (
                      <button
                        className="action-strip-btn"
                        disabled
                        title={`${o.name} is being drawn aboard (J1.621) — pulled one hex closer each impulse`}
                        style={{ borderColor: '#3fb950', color: '#3fb950', opacity: 0.6 }}
                      >
                        Recovering {o.name}…
                      </button>
                    ) : (
                      <button
                        className="action-strip-btn"
                        onClick={() => onRecoverObjective(o.name)}
                        title={`Draw ${o.name} aboard with the J1.621 rotation procedure (SH35.452)`}
                        style={{ borderColor: '#3fb950', color: '#3fb950' }}
                      >
                        Recover {o.name}
                      </button>
                    )}
                    <button
                      className="action-strip-btn"
                      onClick={() => onReleaseTractor(o.name)}
                      title={`Release tractor beam on ${o.name} (G7.33)`}
                      style={{ borderColor: '#f87171', color: '#f87171' }}
                    >
                      Release {o.name}
                    </button>
                  </Fragment>
                ))}
                {/* Tractor beam — release (G7.33) */}
                {(ship.tractoredTargetNames ?? []).map(targetName => (
                  <button
                    key={`release-${targetName}`}
                    className="action-strip-btn"
                    onClick={() => onReleaseTractor(targetName)}
                    title={`Release tractor beam on ${targetName} (G7.33)`}
                    style={{ borderColor: '#f87171', color: '#f87171' }}
                  >
                    Release {targetName}
                  </button>
                ))}
                {(ship.totalTransporters ?? 0) > 0 && (
                  <button
                    className={`action-strip-btn${transportersOpen ? ' active' : ''}`}
                    onClick={onToggleTransporters}
                    disabled={!canUseTransporters}
                    title={canUseTransporters ? 'Transporter actions' : 'No transporter actions available'}
                  >
                    Transporters
                  </button>
                )}
                {canDropMine && (
                  <button
                    className={`action-strip-btn${dropMineMode ? ' active' : ''}`}
                    onClick={onToggleDropMine}
                    title="Drop mine from shuttle bay"
                  >
                    Drop Mine
                  </button>
                )}
                {canIdentify && (
                  <button
                    className={`action-strip-btn${idMode ? ' active' : ''}`}
                    onClick={idMode ? onCancelId : onStartId}
                    title={`Identify seekers (${ship.availableLab} lab${ship.availableLab !== 1 ? 's' : ''} available)`}
                  >
                    ID
                  </button>
                )}
                {(ship.cloakCost ?? 0) > 0 && (
                  <>
                    {(ship.cloakState === 'INACTIVE' || ship.cloakState === 'NONE' || !ship.cloakState) && (
                      <button className="action-strip-btn" onClick={onCloak} title="Activate cloaking device">
                        Cloak
                      </button>
                    )}
                    {(ship.cloakState === 'FADING_OUT' || ship.cloakState === 'FULLY_CLOAKED') && (
                      <button className="action-strip-btn" onClick={onUncloak} title="Deactivate cloaking device">
                        Uncloak
                      </button>
                    )}
                    {ship.cloakState === 'FADING_IN' && (
                      <button className="action-strip-btn" disabled title="Decloaking in progress">
                        Decloaking
                      </button>
                    )}
                  </>
                )}
                {/* Fire Control toggle (D6.6) — always show for ships that have FC */}
                {(ship.fcPaidThisTurn) && (
                  <>
                    {ship.activeFireControl && !ship.wildWeaselActive && (
                      <button
                        className="action-strip-btn"
                        style={{ borderColor: '#22c55e', color: '#22c55e' }}
                        onClick={onGoPassiveFc}
                        title="Go passive fire control (D6.6) — loses lock-ons, 4 impulses to reactivate"
                      >
                        FC Active
                      </button>
                    )}
                    {ship.fireControlActivating && (
                      <button
                        className="action-strip-btn"
                        disabled
                        style={{ borderColor: '#facc15', color: '#facc15' }}
                        title={`Fire control activating — completes at impulse ${ship.fcActivatingUntil ?? '?'} (D6.633)`}
                      >
                        FC Activating…
                      </button>
                    )}
                    {!ship.activeFireControl && !ship.fireControlActivating && (
                      <button
                        className="action-strip-btn"
                        style={{ borderColor: '#f97316', color: '#f97316' }}
                        onClick={onGoActiveFc}
                        title={ship.wildWeaselActive
                          ? 'Activate fire control — voids Wild Weasel! (D6.65)'
                          : 'Activate fire control (D6.6) — 4-impulse countdown'}
                      >
                        FC Passive
                      </button>
                    )}
                  </>
                )}
              </div>

              {/* Emergency deceleration (C8.0) — stop button + countdown */}
              {ship.speed > 0 && !ship.decelerating && !ship.immobileUntilImpulse && (
                <button
                  className="action-strip-btn"
                  style={{ marginTop: 4, borderColor: '#f85149', color: '#f85149' }}
                  onClick={onEmergencyDecel}
                  title="Emergency Deceleration (C8.0) — ship stops in 2 impulses, then 16-impulse post-decel lockout"
                >
                  STOP
                </button>
              )}
              {ship.decelerating && (
                <div style={{ marginTop: 4, fontSize: '0.72rem', color: '#f85149', textAlign: 'center' }}>
                  Stopping in {Math.max(0, (ship.decelerationEndsAtImpulse ?? 0) - absoluteImpulse)} impulse(s)
                </div>
              )}
              {!ship.decelerating && (ship.immobileUntilImpulse ?? 0) > absoluteImpulse && (
                <div style={{ marginTop: 4, fontSize: '0.72rem', color: '#f97316', textAlign: 'center' }}>
                  Post-decel: {(ship.immobileUntilImpulse ?? 0) - absoluteImpulse} impulse(s) remaining
                </div>
              )}

              {transportersOpen && (
                <div className="transporter-submenu">
                  <button
                    className={`action-strip-btn${tBombMode ? ' active' : ''}`}
                    disabled={!canTBomb}
                    onClick={tBombMode ? onCancelTBomb : onStartTBomb}
                    title="Place T-bomb via transporter"
                  >T-bomb</button>
                  <button
                    className={`action-strip-btn${boardingMode ? ' active' : ''}`}
                    disabled={!canBoard}
                    onClick={boardingMode ? onCancelBoarding : onStartBoarding}
                    title="Board enemy ship"
                  >Board</button>
                  <button
                    className={`action-strip-btn${harMode ? ' active' : ''}`}
                    disabled={!canHar}
                    onClick={harMode ? onCancelHar : onStartHar}
                    title="Hit &amp; Run raid"
                  >H&amp;R</button>
                  <button
                    className={`action-strip-btn${crewMode ? ' active' : ''}`}
                    disabled={!canTransferCrew}
                    onClick={crewMode ? onCancelCrew : onStartCrew}
                    title="Transport crew to another unit"
                  >Send Crew</button>
                  <button
                    className={`action-strip-btn${beamObjectMode ? ' active' : ''}`}
                    disabled={!canBeamObject}
                    onClick={beamObjectMode ? onCancelBeamObject : onStartBeamObject}
                    title="Beam a free objective aboard by transporter (within 5 hexes)"
                  >Beam Aboard</button>
                </div>
              )}
            </>
          )}

          {/* Separation disengagement — available any phase when conditions met */}
          {ship.canDisengageBySeparation && (
            <button
              className="action-strip-btn"
              style={{ marginTop: 4, borderColor: '#f85149', color: '#f85149' }}
              onClick={onDisengageSeparation}
              title="Disengage by separation — no enemies within 50 hexes (C7.2)"
            >
              Disengage
            </button>
          )}

        </div>
      )}

      {/* ---- Scrollable body: action details + ship stats ---- */}
      <div className="sidebar-body">

      {/* ---- Active action detail ---- */}

      {launchMode && (
        <LaunchPanel
          ship={ship}
          target={launchTarget}
          onLaunch={onLaunch}
          onClearTarget={onClearLaunch}
          onCancel={onClearLaunch}
          error={launchError}
        />
      )}

      {shuttleLaunchMode && (
        <ShuttleLaunchPanel
          ship={ship}
          onLaunch={onLaunchShuttle}
          onCancel={onCancelShuttleLaunch}
          error={shuttleLaunchError}
        />
      )}

      {wwLaunchShuttle && (
        <WwLaunchPanel
          ship={ship}
          shuttleName={wwLaunchShuttle}
          onLaunch={onLaunchWildWeasel}
          onCancel={onCancelWwLaunch}
          error={wwLaunchError}
        />
      )}

      {tractorMode && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title" style={{ color: '#22d3ee' }}>
            Tractor — click an enemy ship, drone, shuttle, or free canister
          </div>
          {tractorError && <div style={{ color: '#f85149', fontSize: '0.75rem' }}>{tractorError}</div>}
          <button className="secondary" style={{ marginTop: 4 }} onClick={onCancelTractor}>Cancel</button>
        </div>
      )}

      {beamObjectMode && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title" style={{ color: '#3fb950' }}>
            Beam Aboard — click a free objective within 5 hexes
          </div>
          <button className="secondary" style={{ marginTop: 4 }} onClick={onCancelBeamObject}>Cancel</button>
        </div>
      )}

      {rotateMode && rotateTarget && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title" style={{ color: '#a78bfa' }}>
            Rotate {rotateTarget} — click the destination hex (G7.711)
          </div>
          {rotateError && <div style={{ color: '#f85149', fontSize: '0.75rem' }}>{rotateError}</div>}
          <button className="secondary" style={{ marginTop: 4 }} onClick={onCancelRotate}>Cancel</button>
        </div>
      )}

      {/* Attacker bid dialog — shown after target selected */}
      {tractorBidTarget && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title" style={{ color: '#22d3ee' }}>
            Tractor bid — targeting {tractorBidTarget}
            {tractorRangeMultiplier > 1 && (
              <span style={{ color: '#f59e0b', fontSize: '0.7rem', marginLeft: 6 }}>
                range {tractorRangeMultiplier} (×{tractorRangeMultiplier} energy)
              </span>
            )}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginBottom: 4 }}>
            Max: {tractorBidMax} effective
            {tractorRangeMultiplier > 1 && ` (${tractorBidMax * tractorRangeMultiplier} energy)`}
            {' '}(pool {ship.tractorEnergyRemaining ?? 0} + battery {ship.batteryPower ?? 0})
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button className="secondary" onClick={() => onSetTractorBid(Math.max(1, tractorBidValue - 1))}>−</button>
            <span style={{ minWidth: 24, textAlign: 'center' }}>{tractorBidValue}</span>
            <button className="secondary" onClick={() => onSetTractorBid(Math.min(tractorBidMax, tractorBidValue + 1))}>+</button>
          </div>
          {tractorRangeMultiplier > 1 && (
            <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: 2 }}>
              = {tractorBidValue * tractorRangeMultiplier} energy spent
            </div>
          )}
          {tractorError && <div style={{ color: '#f85149', fontSize: '0.75rem', marginTop: 4 }}>{tractorError}</div>}
          <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
            <button onClick={onSubmitTractorBid} disabled={tractorBidMax < 1}>
              Bid {tractorBidValue}{tractorRangeMultiplier > 1 ? ` eff (${tractorBidValue * tractorRangeMultiplier} energy)` : ''}
            </button>
            <button className="secondary" onClick={onCancelTractorBid}>Cancel</button>
          </div>
        </div>
      )}

      {/* Defender bid dialog — shown when this ship is the auction target */}
      {pendingTractorAuction && pendingTractorAuction.targetName === ship.name && (
        <div className="sidebar-action-detail" style={{ borderColor: '#f85149' }}>
          <div className="sidebar-section-title" style={{ color: '#f85149' }}>
            Tractor attack! {pendingTractorAuction.attackerName} bids {pendingTractorAuction.attackerBid}
          </div>
          <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginBottom: 4 }}>
            Your accumulated negative tractor: {pendingTractorAuction.defenderAccumulated}<br/>
            Bid new energy (0 = waive). Max: {pendingTractorAuction.defenderMaxBid}<br/>
            Effective defence if you bid {negTractorBidValue}: {pendingTractorAuction.defenderAccumulated + negTractorBidValue}
            {pendingTractorAuction.defenderAccumulated + negTractorBidValue >= pendingTractorAuction.attackerBid
              ? ' ✓ resist' : ' ✗ held'}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <button className="secondary" onClick={() => onSetNegTractorBid(Math.max(0, negTractorBidValue - 1))}>−</button>
            <span style={{ minWidth: 24, textAlign: 'center' }}>{negTractorBidValue}</span>
            <button className="secondary" onClick={() => onSetNegTractorBid(Math.min(pendingTractorAuction.defenderMaxBid, negTractorBidValue + 1))}>+</button>
          </div>
          {tractorError && <div style={{ color: '#f85149', fontSize: '0.75rem', marginTop: 4 }}>{tractorError}</div>}
          <div style={{ marginTop: 6 }}>
            <button onClick={onSubmitNegTractorBid}>
              {negTractorBidValue === 0 ? 'Waive (accept tractor)' : `Bid ${negTractorBidValue}`}
            </button>
          </div>
        </div>
      )}

      {tBombMode && (
        <div className="sidebar-action-detail">
          {tBombShieldChoice ? (
            <>
              <div className="sidebar-section-title" style={{ color: '#f0c040' }}>
                Seam — choose which shield to lower
              </div>
              <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                {tBombShieldChoice.shields.map(s => (
                  <button key={s} onClick={() => onPlaceTBomb(tBombShieldChoice.isReal, s)}>
                    Shield {s}
                  </button>
                ))}
                <button className="secondary" onClick={onCancelTBomb}>Cancel</button>
              </div>
            </>
          ) : tBombPendingHex ? (
            <>
              <div className="sidebar-section-title">Place T-bomb at {tBombPendingHex.col}|{tBombPendingHex.row}</div>
              <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
                {ship.tBombs > 0 && <button onClick={() => onPlaceTBomb(true)}>Real ({ship.tBombs})</button>}
                {ship.dummyTBombs > 0 && <button className="secondary" onClick={() => onPlaceTBomb(false)}>Dummy ({ship.dummyTBombs})</button>}
                <button className="secondary" onClick={onCancelTBomb}>Cancel</button>
              </div>
            </>
          ) : (
            <>
              <div className="sidebar-section-title" style={{ color: '#f0c040' }}>Click a hex to place T-bomb</div>
              <button className="secondary" style={{ width: '100%', marginTop: 4 }} onClick={onCancelTBomb}>Cancel</button>
            </>
          )}
        </div>
      )}

      {dropMineMode && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title">Drop mine from shuttle bay</div>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 4 }}>
            {ship.tBombs > 0 && (
              <button onClick={() => onDropMine('TBOMB')}>T-Bomb ({ship.tBombs})</button>
            )}
            {ship.dummyTBombs > 0 && (
              <button className="secondary" onClick={() => onDropMine('DUMMY_TBOMB')}>Dummy ({ship.dummyTBombs})</button>
            )}
            {(ship.nuclearSpaceMines ?? 0) > 0 && (
              <button onClick={() => onDropMine('NSM')}>NSM ({ship.nuclearSpaceMines})</button>
            )}
            <button className="secondary" onClick={onToggleDropMine}>Cancel</button>
          </div>
        </div>
      )}

      {boardingMode && (
        <div className="sidebar-action-detail">
          {boardingTarget ? (
            <>
              <div className="sidebar-section-title">Board {boardingTarget.name}</div>
              <div className="sidebar-stat-row" style={{ marginBottom: 4 }}>
                <span className="sidebar-stat-label">Lock-on</span>
                <span className="sidebar-stat-value" style={{ color: ship.lockOnTargets?.includes(boardingTarget.name) ? '#3fb950' : '#f85149' }}>
                  {ship.lockOnTargets?.includes(boardingTarget.name) ? 'Yes' : 'No — eff. range ×2'}
                </span>
              </div>
              <div style={{ fontSize: '0.75rem', color: '#888', marginBottom: 4 }}>
                Max total: {maxBoardingTotal} (transporter limit)
              </div>
              {ship.boardingParties > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <span style={{ flex: 1, fontSize: '0.8rem' }}>Normal ({ship.boardingParties} avail)</span>
                  <button className="secondary" style={{ padding: '2px 6px' }}
                    onClick={() => onSetBoardingNormal(Math.max(0, boardingNormal - 1))}>−</button>
                  <span style={{ minWidth: 20, textAlign: 'center' }}>{boardingNormal}</span>
                  <button className="secondary" style={{ padding: '2px 6px' }}
                    onClick={() => onSetBoardingNormal(Math.min(ship.boardingParties, maxBoardingTotal - boardingCommandos, boardingNormal + 1))}>+</button>
                </div>
              )}
              {ship.commandos > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                  <span style={{ flex: 1, fontSize: '0.8rem', color: '#f0a040' }}>Commandos ({ship.commandos} avail)</span>
                  <button className="secondary" style={{ padding: '2px 6px' }}
                    onClick={() => onSetBoardingCommandos(Math.max(0, boardingCommandos - 1))}>−</button>
                  <span style={{ minWidth: 20, textAlign: 'center' }}>{boardingCommandos}</span>
                  <button className="secondary" style={{ padding: '2px 6px' }}
                    onClick={() => onSetBoardingCommandos(Math.min(ship.commandos, maxBoardingTotal - boardingNormal, boardingCommandos + 1))}>+</button>
                </div>
              )}
              {boardingError && <div style={{ color: '#f85149', fontSize: '0.75rem', marginBottom: 4 }}>{boardingError}</div>}
              <div style={{ display: 'flex', gap: 6 }}>
                <button disabled={boardingNormal + boardingCommandos === 0} onClick={onSubmitBoarding}>Transport</button>
                <button className="secondary" onClick={onCancelBoarding}>Cancel</button>
              </div>
            </>
          ) : (
            <>
              <div className="sidebar-section-title" style={{ color: '#f0c040' }}>Click an enemy ship to board</div>
              <button className="secondary" style={{ width: '100%', marginTop: 4 }} onClick={onCancelBoarding}>Cancel</button>
            </>
          )}
        </div>
      )}

      {idMode && (
        <div className="sidebar-action-detail">
          <div className="sidebar-section-title">Identify Seekers ({ship.availableLab} lab{ship.availableLab !== 1 ? 's' : ''} available)</div>
          {idSeekers.length === 0 ? (
            <div style={{ color: '#888', fontSize: '0.75rem', margin: '4px 0' }}>No unidentified enemy seekers in range.</div>
          ) : (
            <div style={{ fontSize: '0.75rem', color: '#aaa', marginBottom: 4 }}>
              Select up to {ship.availableLab} seeker{ship.availableLab !== 1 ? 's' : ''} to attempt identification.
            </div>
          )}
          {idSeekers.map(s => {
            const checked = idSelected.has(s.name);
            const disabled = !checked && idSelected.size >= (ship.availableLab ?? 0);
            return (
              <label key={s.name} style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 3, cursor: disabled ? 'default' : 'pointer', opacity: disabled ? 0.5 : 1 }}>
                <input type="checkbox" checked={checked} disabled={disabled} onChange={() => onToggleIdSeeker(s.name)} />
                <span style={{ fontSize: '0.8rem' }}>{s.name} <span style={{ color: '#888' }}>({s.type})</span></span>
              </label>
            );
          })}
          {idError && <div style={{ color: '#f85149', fontSize: '0.75rem', margin: '4px 0' }}>{idError}</div>}
          <div style={{ display: 'flex', gap: 6, marginTop: 4 }}>
            <button disabled={idSelected.size === 0} onClick={onSubmitId}>Attempt ID</button>
            <button className="secondary" onClick={onCancelId}>Cancel</button>
          </div>
        </div>
      )}

      {harMode && (
        <div className="sidebar-action-detail">
          {harTarget ? (
            <>
              <div className="sidebar-section-title">H&amp;R Raid: {harTarget.name}</div>
              <div className="sidebar-stat-row" style={{ marginBottom: 4 }}>
                <span className="sidebar-stat-label">Lock-on</span>
                <span className="sidebar-stat-value" style={{ color: ship.lockOnTargets?.includes(harTarget.name) ? '#3fb950' : '#f85149' }}>
                  {ship.lockOnTargets?.includes(harTarget.name) ? 'Yes' : 'No — eff. range ×2'}
                </span>
              </div>
              {harLoading ? (
                <div style={{ color: '#888', fontSize: '0.75rem' }}>Loading targets…</div>
              ) : (
                <>
                  <div style={{ fontSize: '0.75rem', color: '#888', marginBottom: 4 }}>
                    Parties: up to {maxHarParties} · each targets a different system
                  </div>
                  {harParties.map((code, idx) => (
                    <div key={idx} style={{ display: 'flex', gap: 4, marginBottom: 4, alignItems: 'center' }}>
                      <span style={{ fontSize: '0.72rem', color: '#888', minWidth: 18 }}>P{idx + 1}</span>
                      <select
                        value={code ?? ''}
                        onChange={e => {
                          const next = [...harParties];
                          next[idx] = e.target.value || null;
                          onSetHarParties(next);
                        }}
                        style={{ flex: 1, fontSize: '0.72rem' }}
                      >
                        <option value="">— pick system —</option>
                        {harOptions
                          .filter(opt => opt.code === code || !harParties.some((c, i) => i !== idx && c === opt.code))
                          .map(opt => (
                            <option key={opt.code} value={opt.code}>{opt.label}</option>
                          ))
                        }
                      </select>
                      {harParties.length > 1 && (
                        <button className="secondary" style={{ padding: '2px 6px' }}
                          onClick={() => onSetHarParties(harParties.filter((_, i) => i !== idx))}>✕</button>
                      )}
                    </div>
                  ))}
                  {harParties.length < maxHarParties && (
                    <button className="secondary" style={{ fontSize: '0.75rem', marginBottom: 4, width: '100%' }}
                      onClick={() => onSetHarParties([...harParties, null])}>+ Add party</button>
                  )}
                  {harError && <div style={{ color: '#f85149', fontSize: '0.75rem', marginBottom: 4 }}>{harError}</div>}
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button disabled={harParties.some(c => !c) || harParties.length === 0} onClick={onSubmitHar}>Raid</button>
                    <button className="secondary" onClick={onCancelHar}>Cancel</button>
                  </div>
                </>
              )}
            </>
          ) : (
            <>
              <div className="sidebar-section-title" style={{ color: '#f0c040' }}>Click an enemy ship to raid</div>
              <button className="secondary" style={{ width: '100%', marginTop: 4 }} onClick={onCancelHar}>Cancel</button>
            </>
          )}
        </div>
      )}

      {crewMode && (
        <div className="sidebar-action-detail">
          {crewTarget ? (
            <>
              <div className="sidebar-section-title">Send Crew → {crewTarget.name}</div>
              <div style={{ fontSize: '0.75rem', color: '#888', marginBottom: 4 }}>
                Non-combat rate: 2 crew per transporter use (G8.32)
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                <span style={{ flex: 1, fontSize: '0.8rem' }}>Crew ({ship.availableCrewUnits} avail)</span>
                <button className="secondary" style={{ padding: '2px 6px' }}
                  onClick={() => onSetCrewAmount(Math.max(1, crewAmount - 1))}>−</button>
                <span style={{ minWidth: 20, textAlign: 'center' }}>{crewAmount}</span>
                <button className="secondary" style={{ padding: '2px 6px' }}
                  onClick={() => onSetCrewAmount(Math.min(ship.availableCrewUnits, crewAmount + 1))}>+</button>
              </div>
              {crewError && <div style={{ color: '#f85149', fontSize: '0.75rem', marginBottom: 4 }}>{crewError}</div>}
              <div style={{ display: 'flex', gap: 6 }}>
                <button onClick={onSubmitCrew}>Transport</button>
                <button className="secondary" onClick={onCancelCrew}>Cancel</button>
              </div>
            </>
          ) : (
            <>
              <div className="sidebar-section-title" style={{ color: '#f0c040' }}>Click a ship to send crew to</div>
              <button className="secondary" style={{ width: '100%', marginTop: 4 }} onClick={onCancelCrew}>Cancel</button>
            </>
          )}
        </div>
      )}

      {/* Fire panel — top of sidebar during Direct Fire */}
      {isFirePhase && (
        <FirePanel
          attacker={ship}
          target={fireTarget}
          options={fireOptions}
          loadingOptions={loadingOptions}
          selectedWeapons={selectedWeapons}
          onToggleWeapon={onToggleWeapon}
          shotCounts={shotCounts}
          onSetShotCount={onSetShotCount}
          useUim={useUim}
          onToggleUim={onToggleUim}
          directFire={directFire}
          onToggleDirectFire={onToggleDirectFire}
          onFire={onFire}
          onClearTarget={onClearTarget}
          error={fireError}
        />
      )}

      <div className="sidebar-section">
        <div className="sidebar-section-title">Base Data</div>
        <StatRow label="Hull"     value={ship.hull} />
        <StatRow label="Faction"  value={ship.faction} />
        <StatRow label="Location" value={locationLabel(ship.location)} />
        <StatRow label="Facing"   value={facingLabel(ship.facing)} />
        {/* Tractor pseudo-speed (G7.34): plotted speed with the effective speed in parens */}
        <StatRow label="Speed"    value={(ship.tractorTrueSpeed ?? -1) >= 0
          ? `${ship.tractorTrueSpeed} (${ship.speed})`
          : ship.speed} />
        {isMine && (
          <GuardSummaryRow key={ship.name} gameId={gameId} playerToken={playerToken} shipName={ship.name} />
        )}
        {ship.turnMode != null && (
          <StatRow
            label="Turn Mode"
            value={
              (ship.hexesUntilTurn ?? 0) === 0
                ? `${ship.turnMode}-${ship.turnHexes} (free)`
                : `${ship.turnMode}-${ship.turnHexes} (${ship.hexesUntilTurn} more)`
            }
          />
        )}
        {(ship.cloakCost ?? 0) > 0 && (
          <StatRow
            label="Cloak"
            value={
              (ship.cloakState ?? 'INACTIVE').toLowerCase().replace(/_/g, ' ')
              + (ship.cloakFadeStep ? ` (${ship.cloakFadeStep}/5)` : '')
            }
          />
        )}
      </div>

      {ship.shields?.length > 0 && (
        <div className="sidebar-section">
          <div className="sidebar-section-title">Shields</div>
          <div className="shield-grid">
            {ship.shields.map(s => <ShieldBar key={s.shieldNum} shield={s} isMine={isMine} />)}
          </div>
        </div>
      )}

      <div className="sidebar-section">
        <div className="sidebar-section-title">Crew</div>
        {ship.skeleton && (
          <div style={{ color: '#f85149', fontSize: '0.7rem', fontWeight: 600, marginBottom: '0.2rem' }}>
            ⚠ SKELETON CREW
          </div>
        )}
        <StatRow label="Units"       value={`${ship.availableCrewUnits} (min ${ship.minimumCrew})`} />
        <StatRow label="Deck Crews"  value={ship.availableDeckCrews} />
        {isMine && <StatRow label="B-Parties" value={ship.boardingParties} />}
        {isMine && (ship.tBombs > 0 || ship.dummyTBombs > 0) && (
          <StatRow label="T-Bombs" value={`${ship.tBombs} real / ${ship.dummyTBombs} dummy`} />
        )}
        {ship.crewQuality !== 'NORMAL' && (
          <StatRow label="Quality" value={ship.crewQuality} />
        )}
      </div>

      <div className="sidebar-section">
        <div className="sidebar-section-title">Hull</div>
        <StatRow label="Forward" value={ship.availableFhull < ship.maxFhull ? `${ship.availableFhull}/${ship.maxFhull}` : ship.availableFhull} dmg={ship.availableFhull < ship.maxFhull} />
        <StatRow label="Aft"     value={ship.availableAhull < ship.maxAhull ? `${ship.availableAhull}/${ship.maxAhull}` : ship.availableAhull} dmg={ship.availableAhull < ship.maxAhull} />
        {ship.maxChull > 0 && <StatRow label="Center" value={ship.availableChull < ship.maxChull ? `${ship.availableChull}/${ship.maxChull}` : ship.availableChull} dmg={ship.availableChull < ship.maxChull} />}
      </div>

      {(ship.maxBridge > 0 || ship.maxFlag > 0 || ship.maxEmer > 0 || ship.maxAuxcon > 0 || ship.maxSecurity > 0) && (
        <div className="sidebar-section">
          <div className="sidebar-section-title">Control</div>
          {ship.maxBridge   > 0 && <StatRow label="Bridge"   value={`${ship.availableBridge}/${ship.maxBridge}`}     dmg={ship.availableBridge   < ship.maxBridge} />}
          {ship.maxFlag     > 0 && <StatRow label="Flag"     value={`${ship.availableFlag}/${ship.maxFlag}`}         dmg={ship.availableFlag     < ship.maxFlag} />}
          {ship.maxEmer     > 0 && <StatRow label="Emer"     value={`${ship.availableEmer}/${ship.maxEmer}`}         dmg={ship.availableEmer     < ship.maxEmer} />}
          {ship.maxAuxcon   > 0 && <StatRow label="Auxcon"   value={`${ship.availableAuxcon}/${ship.maxAuxcon}`}     dmg={ship.availableAuxcon   < ship.maxAuxcon} />}
          {ship.maxSecurity > 0 && <StatRow label="Security" value={`${ship.availableSecurity}/${ship.maxSecurity}`} dmg={ship.availableSecurity < ship.maxSecurity} />}
        </div>
      )}

      {((ship.totalTransporters ?? 0) > 0 || (ship.totalTractors ?? 0) > 0) && (
        <div className="sidebar-section">
          <div className="sidebar-section-title">Systems</div>
          {(ship.totalTransporters ?? 0) > 0 && (
            <StatRow
              label="Transporters"
              value={(ship.availableTransporters ?? 0) < (ship.totalTransporters ?? 0)
                ? `${ship.availableTransporters}/${ship.totalTransporters}`
                : String(ship.totalTransporters)}
              dmg={(ship.availableTransporters ?? 0) < (ship.totalTransporters ?? 0)}
            />
          )}
          {(ship.totalTractors ?? 0) > 0 && (
            <StatRow
              label="Tractors"
              value={(ship.availableTractors ?? 0) < (ship.totalTractors ?? 0)
                ? `${ship.availableTractors}/${ship.totalTractors}`
                : String(ship.totalTractors)}
              dmg={(ship.availableTractors ?? 0) < (ship.totalTractors ?? 0)}
            />
          )}
        </div>
      )}

      <div className="sidebar-section">
        <div className="sidebar-section-title">Power</div>
        <StatRow label="L-Warp"  value={ship.availableLWarp  < ship.maxLWarp    ? `${ship.availableLWarp} / ${ship.maxLWarp}`    : ship.availableLWarp}  dmg={ship.availableLWarp  < ship.maxLWarp} />
        <StatRow label="R-Warp"  value={ship.availableRWarp  < ship.maxRWarp    ? `${ship.availableRWarp} / ${ship.maxRWarp}`    : ship.availableRWarp}  dmg={ship.availableRWarp  < ship.maxRWarp} />
        {ship.availableCWarp > 0 && <StatRow label="C-Warp"  value={ship.availableCWarp  < ship.maxCWarp    ? `${ship.availableCWarp} / ${ship.maxCWarp}`    : ship.availableCWarp}  dmg={ship.availableCWarp  < ship.maxCWarp} />}
        <StatRow label="Impulse" value={ship.availableImpulse < ship.maxImpulse ? `${ship.availableImpulse} / ${ship.maxImpulse}` : ship.availableImpulse} dmg={ship.availableImpulse < ship.maxImpulse} />
        {ship.availableApr > 0 && <StatRow label="APR"       value={ship.availableApr    < ship.maxApr      ? `${ship.availableApr} / ${ship.maxApr}`       : ship.availableApr}    dmg={ship.availableApr    < ship.maxApr} />}
        {ship.availableAwr > 0 && <StatRow label="AWR"       value={ship.availableAwr    < ship.maxAwr      ? `${ship.availableAwr} / ${ship.maxAwr}`       : ship.availableAwr}    dmg={ship.availableAwr    < ship.maxAwr} />}
        <StatRow label="Battery" value={`${ship.batteryPower} / ${ship.availableBattery}`} />
        <div className="sidebar-divider" />
        <StatRow label="Total"   value={totalPower} />
      </div>

      {isMine && ship.droneRacks?.length > 0 && (
        <div className="sidebar-section">
          <div className="sidebar-section-title">Drone Racks</div>
          {ship.droneRacks.map(r => (
            <div key={r.name} style={{ marginBottom: 6 }}>
              <div className="sidebar-stat-row">
                <span className="sidebar-stat-label">{r.name}</span>
                <span className={`sidebar-stat-value${r.functional ? '' : ' dmg'}`}>
                  {r.functional ? (r.canFire ? 'ready' : 'cooling') : 'dmg'}
                </span>
              </div>
              {isMine ? (
                r.drones.length > 0 ? (
                  <div style={{ paddingLeft: 12 }}>
                    {r.drones.map((d, i) => (
                      <div key={i} className="sidebar-stat-row" style={{ fontSize: 11 }}>
                        <span className="sidebar-stat-label">[{i + 1}] {d.droneType}</span>
                        <span className="sidebar-stat-value" style={{ color: '#8b949e' }}>
                          spd {d.speed} · dmg {d.warheadDamage}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ paddingLeft: 12, fontSize: 11, color: '#8b949e' }}>empty</div>
                )
              ) : (
                <div style={{ paddingLeft: 12, fontSize: 11, color: '#8b949e' }}>
                  {r.drones.length} drone{r.drones.length !== 1 ? 's' : ''} loaded
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {ship.weapons?.length > 0 && (
        <div className="sidebar-section">
          <div className="sidebar-section-title">Weapons</div>
          <div className="sidebar-stat-row">
            <span className="sidebar-stat-label">Ph Cap</span>
            <span className="sidebar-stat-value">
              {(ship.phaserCapacitor ?? 0).toFixed(1)}
              {ship.capacitorsCharged ? '' : ' ⚠'}
            </span>
          </div>
          <div className="sidebar-divider" />
          <div className="weapon-list">
            {ship.weapons.map(w => <WeaponRow key={w.name} w={w} />)}
          </div>
        </div>
      )}

      </div>{/* end sidebar-body */}
    </div>
  );
}

// ---- Main board ----

export default function GameBoard({ session, onLeave }: Props) {
  const gameState = useGameSocket(session.gameId, session.playerToken);
  const [selected, setSelected]             = useState<MapObject | null>(null);
  const [actionError, setActionError]       = useState<string | null>(null);
  const [snapTo, setSnapTo]                 = useState<{ name: string } | null>(null);
  // Fire state
  const [fireTarget, setFireTarget]         = useState<MapObject | null>(null);
  const [fireOptions, setFireOptions]       = useState<FireOptions | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [selectedWeapons, setSelectedWeapons] = useState<Set<string>>(new Set());
  const [shotCounts, setShotCounts]           = useState<Map<string, number>>(new Map());
  const [useUim, setUseUim]                 = useState(false);
  const [directFire, setDirectFire]         = useState(false);
  const [fireError, setFireError]           = useState<string | null>(null);
  // Fire declaration round (D6.315) — locally drafted orders, sealed on commit.
  // Drafts exist only while a round is open (the first order places the call),
  // and are cleared by commit/pass; committedRound keys the sealed state to
  // this turn+impulse so it self-resets when the next round opens.
  type DeclOrder = {
    label: string; shipName: string; targetName: string; weaponNames: string[];
    shotModes?: Record<string, string>; range: number; adjustedRange: number;
    shieldNumber: number; useUim: boolean; directFire: boolean;
  };
  const [declarationOrders, setDeclarationOrders] = useState<DeclOrder[]>([]);
  // Keyed by ship name — a multi-ship player may adjust EW on several ships
  // in one sealed commit, and switching selection must not retarget a draft
  const [declarationEw, setDeclarationEw] = useState<Record<string, { ecm: number; eccm: number }>>({});
  const [committedRound, setCommittedRound] = useState<string | null>(null);
  const roundKey = `${gameState?.turn ?? 0}:${gameState?.impulse ?? 0}`;
  const declarationOpen = gameState?.fireDeclarationOpen ?? false;
  const myCommitted = committedRound === roundKey;
  // Fighter fire state
  const [fighterAttacker, setFighterAttacker]     = useState<ShuttleObject | null>(null);
  const [fighterShotModes, setFighterShotModes]   = useState<Record<string, 'SINGLE' | 'DOUBLE'>>({});
  // Launch state
  const [launchMode,        setLaunchMode]        = useState(false);
  const [launchTarget,      setLaunchTarget]      = useState<MapObject | null>(null);
  const [launchError,       setLaunchError]       = useState<string | null>(null);
  const [shuttleLaunchMode, setShuttleLaunchMode] = useState(false);
  const [shuttleLaunchError, setShuttleLaunchError] = useState<string | null>(null);
  const [wwLaunchShuttle, setWwLaunchShuttle] = useState<string | null>(null);
  const [wwLaunchError, setWwLaunchError]     = useState<string | null>(null);
  // T-bomb placement state
  const [tBombMode,         setTBombMode]         = useState(false);
  const [tBombPendingHex,   setTBombPendingHex]   = useState<{col: number; row: number} | null>(null);
  const [tBombShieldChoice, setTBombShieldChoice] = useState<{isReal: boolean; shields: number[]} | null>(null);
  // Drop mine state
  const [dropMineMode, setDropMineMode] = useState(false);
  // Tractor beam state
  const [tractorMode,        setTractorMode]        = useState(false);
  const [tractorError,       setTractorError]        = useState<string | null>(null);
  // Tractor bid dialog (attacker)
  const [tractorBidTarget,        setTractorBidTarget]        = useState<string | null>(null);
  const [tractorBidValue,         setTractorBidValue]         = useState(1);
  const [tractorRangeMultiplier,  setTractorRangeMultiplier]  = useState(1);
  // Negative-tractor bid dialog (defender)
  const [negTractorBidValue, setNegTractorBidValue] = useState(0);
  // Tractor rotation state (G7.7)
  const [rotateMode,   setRotateMode]   = useState(false);
  const [rotateTarget, setRotateTarget] = useState<string | null>(null);
  const [rotateError,  setRotateError]  = useState<string | null>(null);
  // Boarding action state
  const [boardingMode,    setBoardingMode]    = useState(false);
  const [beamObjectMode,  setBeamObjectMode]  = useState(false);
  const [boardingTarget,  setBoardingTarget]  = useState<ShipObject | null>(null);
  const [boardingNormal,  setBoardingNormal]  = useState(0);
  const [boardingCommandos, setBoardingCommandos] = useState(0);
  const [boardingError,   setBoardingError]   = useState<string | null>(null);
  // Lab seeker ID state
  const [idMode,     setIdMode]     = useState(false);
  const [idSelected, setIdSelected] = useState<Set<string>>(new Set());
  const [idError,    setIdError]    = useState<string | null>(null);
  // Hit & Run state
  const [harMode,    setHarMode]    = useState(false);
  const [harTarget,  setHarTarget]  = useState<ShipObject | null>(null);
  const [harOptions, setHarOptions] = useState<{ code: string; label: string }[]>([]);
  const [harParties, setHarParties] = useState<(string | null)[]>([null]);
  const [harError,   setHarError]   = useState<string | null>(null);
  const [harLoading, setHarLoading] = useState(false);
  // Transporters submenu + transport crew state
  const [transportersOpen, setTransportersOpen] = useState(false);
  const [crewMode,    setCrewMode]    = useState(false);
  const [crewTarget,  setCrewTarget]  = useState<ShipObject | null>(null);
  const [crewAmount,  setCrewAmount]  = useState(1);
  const [crewError,   setCrewError]   = useState<string | null>(null);
  const [isReady, setIsReady]               = useState(false);
  const [showScore, setShowScore]           = useState(false);
  const [eaDismissed, setEaDismissed]       = useState(false);
  const [log, setLog] = useState<{ stamp: string; text: string; kind: 'combat' | 'phase' | 'error' | 'info' }[]>([]);
  const [pendingCombat, setPendingCombat]   = useState<{ stamp: string; text: string }[]>([]);
  const prevPhaseRef      = useRef<string>('');
  const lastCombatLogRef  = useRef<string>(''); // dedup: skip if same batch arrives twice
  const logEndRef     = useRef<HTMLDivElement>(null);

  const phase      = gameState?.phase ?? '';
  const myShips    = new Set(gameState?.myShips ?? []);
  const movableNow = gameState?.movableNow ?? [];
  const isMovementPhase        = phase === 'Movement';
  const isFirePhase            = phase === 'Direct Fire';
  const isReinforcementPhase   = phase === 'Reinforcement';
  const isInitialActivityPhase = phase === 'Initial Activity';

  const batteryByShip: Record<string, number> = {};
  for (const obj of gameState?.mapObjects ?? []) {
    if (obj.type === 'SHIP') {
      const s = obj as ShipObject;
      batteryByShip[s.name] = s.batteryPower ?? 0;
    }
  }

  const myMovablePending       = movableNow.filter(n => myShips.has(n));
  const opponentMovablePending = movableNow.filter(n => !myShips.has(n));

  // Snap to my next ship when movableNow changes during movement phase
  const prevMovableKeyRef = useRef('');
  useEffect(() => {
    const list = gameState?.movableNow ?? [];
    const key  = list.join(',');
    if (key === prevMovableKeyRef.current) return;
    prevMovableKeyRef.current = key;
    const mine = list.find(name => (gameState?.myShips ?? []).includes(name));
    if (mine) {
      setSnapTo({ name: mine });
      const shipObj = (gameState?.mapObjects ?? []).find(o => o.name === mine && o.type === 'SHIP');
      if (shipObj) setSelected(shipObj);
    }
  }, [gameState?.movableNow]);

  // Reset EA dismissed flag each time a new allocation window opens
  const prevAwaitingRef = useRef(false);
  useEffect(() => {
    const nowAwaiting = gameState?.awaitingAllocation ?? false;
    if (nowAwaiting && !prevAwaitingRef.current) {
      setEaDismissed(false);
    }
    prevAwaitingRef.current = nowAwaiting;
  }, [gameState?.awaitingAllocation]);

  // Log phase label whenever the phase name changes; flush buffered combat events when leaving Direct Fire
  useEffect(() => {
    if (phase) {
      const turn    = gameState?.turn    ?? '?';
      const impulse = gameState?.impulse ?? '?';
      // Flush buffered fire-phase events when the fire phase ends
      if (prevPhaseRef.current === 'Direct Fire' && phase !== 'Direct Fire') {
        setPendingCombat(queued => {
          if (queued.length > 0) {
            setLog(prev => [
              ...prev,
              ...queued.map(e => ({ stamp: e.stamp, text: e.text, kind: 'combat' as const })),
            ]);
          }
          return [];
        });
      }
      prevPhaseRef.current = phase;
      setLog(prev => [...prev, {
        stamp: `T${turn}I${impulse}`,
        text:  `— ${phase} —`,
        kind:  'phase',
      }]);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  // Buffer incoming server-side combat log entries during Direct Fire; add directly otherwise
  useEffect(() => {
    if (!gameState?.combatLog?.length) return;
    const key = `${gameState.turn}:${gameState.impulse}:${gameState.combatLog.join('\x00')}`;
    if (key === lastCombatLogRef.current) return;
    lastCombatLogRef.current = key;
    const stamp = `T${gameState.turn}I${gameState.impulse}`;
    if (gameState.phase === 'Direct Fire') {
      setPendingCombat(prev => [...prev, ...gameState.combatLog.map(text => ({ stamp, text }))]);
    } else {
      setLog(prev => [...prev, ...gameState.combatLog.map(text => ({ stamp, text, kind: 'combat' as const }))]);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [gameState?.combatLog]);

  // Reset ready state whenever the server advances (phase OR impulse changes)
  const impulse = gameState?.impulse ?? 0;
  useEffect(() => {
    setIsReady(false);
  }, [phase, impulse]);

  // Auto-scroll log to bottom
  useEffect(() => {
    logEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [log]);

  // Rotation (G7.7) is only legal during Initial Activity — drop any armed
  // rotate mode when the phase moves on so stale clicks can't fire it.
  useEffect(() => {
    if (!isInitialActivityPhase && (rotateMode || rotateTarget)) {
      setRotateMode(false);
      setRotateTarget(null);
      setRotateError(null);
    }
  }, [isInitialActivityPhase, rotateMode, rotateTarget]);

  const turnLabel  = gameState
    ? (gameState.maxTurns > 0 ? `Turn ${gameState.turn}/${gameState.maxTurns}` : `Turn ${gameState.turn}`)
    : 'Turn ?';
  const phaseLabel = gameState
    ? `${turnLabel}  |  Impulse ${gameState.impulse}  |  ${phase}`
    : 'Loading…';

  const selectedShip = selected?.type === 'SHIP' ? (selected as ShipObject) : null;
  const canMove      = selectedShip !== null && movableNow.includes(selectedShip.name);

  // Always show the latest live state for the selected ship
  const liveShip = selectedShip
    ? (gameState?.mapObjects.find(o => o.name === selectedShip.name && o.type === 'SHIP') as ShipObject | undefined) ?? selectedShip
    : null;

  const selectedShuttle = selected?.type === 'SHUTTLE' ? (selected as ShuttleObject) : null;
  const liveShuttle = selectedShuttle
    ? (gameState?.mapObjects.find(o => o.name === selectedShuttle.name && o.type === 'SHUTTLE') as ShuttleObject | undefined) ?? selectedShuttle
    : null;
  const canMoveShuttle = liveShuttle !== null && movableNow.includes(liveShuttle?.name ?? '');

  const selectedDrone = selected?.type === 'DRONE' ? (selected as DroneObject) : null;
  const liveDrone = selectedDrone
    ? (gameState?.mapObjects.find(o => o.name === selectedDrone.name && o.type === 'DRONE') as DroneObject | undefined) ?? selectedDrone
    : null;

  // Seeker-type shuttles (suicide shuttle, scatter pack) — also use control channels
  const selectedSeekerShuttle = (selected?.type === 'SUICIDE_SHUTTLE' || selected?.type === 'SCATTER_PACK')
    ? (selected as ShuttleObject) : null;
  const liveSeekerShuttle = selectedSeekerShuttle
    ? (gameState?.mapObjects.find(o => o.name === selectedSeekerShuttle.name) as ShuttleObject | undefined) ?? selectedSeekerShuttle
    : null;

  // Fetch fire options whenever attacker + target are both set in Direct Fire phase
  async function fetchFireOptions(attackerName: string, targetName: string) {
    setLoadingOptions(true);
    setFireOptions(null);
    setSelectedWeapons(new Set());
    setFireError(null);
    try {
      const opts = await gameApi.getFireOptions(
        session.gameId, session.playerToken, attackerName, targetName,
      );
      setFireOptions(opts);
      // Pre-select all in-arc weapons
      setSelectedWeapons(new Set(opts.weaponsInArc));
    } catch (e: unknown) {
      setFireError(e instanceof Error ? e.message : 'Could not get fire options');
    } finally {
      setLoadingOptions(false);
    }
  }

  // Map click handler — tri-mode: select ship, pick fire target, or pick launch target
  const handleMapSelect = useCallback((obj: MapObject | null) => {
    if (harMode && liveShip && obj?.type === 'SHIP') {
      const clicked = obj as ShipObject;
      if (!myShips.has(clicked.name)) {
        setHarTarget(clicked);
        setHarParties([null]);
        setHarError(null);
        setHarLoading(true);
        gameApi.getHarOptions(session.gameId, session.playerToken, liveShip.name, clicked.name)
          .then(opts => { setHarOptions(opts); })
          .catch(() => { setHarError('Could not load target systems'); })
          .finally(() => setHarLoading(false));
        return;
      }
    }
    if (tractorMode && liveShip && obj) {
      const tractorableTypes = new Set(['SHIP', 'DRONE', 'SHUTTLE', 'SUICIDE_SHUTTLE', 'SCATTER_PACK']);
      // A probe canister is a neutral free object grabbed with the beam then
      // drawn aboard by J1.621 (SH35.452); every other target must be an enemy.
      const eligible =
        obj.type === 'OBJECTIVE'
          ? obj.carrierName == null && !obj.tractoredBy
          : tractorableTypes.has(obj.type) && (obj.type !== 'SHIP' || !myShips.has(obj.name));
      if (eligible) {
        const myCoords    = parseLocation(liveShip.location);
        const theirCoords = parseLocation(obj.location);
        if (myCoords && theirCoords) {
          const range        = hexRange({ col: myCoords[0], row: myCoords[1] }, { col: theirCoords[0], row: theirCoords[1] });
          const mult         = Math.max(1, range);
          const totalEnergy  = (liveShip.tractorEnergyRemaining ?? 0) + (liveShip.batteryPower ?? 0);
          const maxEffective = Math.floor(totalEnergy / mult);
          setTractorRangeMultiplier(mult);
          setTractorBidValue(Math.max(1, Math.min(1, maxEffective)));
          setTractorBidTarget(obj.name);
          setTractorMode(false);
          return;
        }
      }
    }
    if (beamObjectMode && liveShip && obj?.type === 'OBJECTIVE') {
      // Beam a free, transporter-eligible canister aboard (SH47). Range/energy/
      // shield/D6.37 are all validated server-side; surface any failure.
      if (!obj.carrierName && (obj.retrieval?.includes('TRANSPORTER') ?? false)) {
        handleBeamObject(obj.name);
        return;
      }
    }
    if (boardingMode && liveShip && obj?.type === 'SHIP') {
      const clicked = obj as ShipObject;
      if (!myShips.has(clicked.name)) {
        setBoardingTarget(clicked);
        setBoardingNormal(0);
        setBoardingCommandos(0);
        setBoardingError(null);
        return;
      }
    }
    if (crewMode && liveShip && obj?.type === 'SHIP') {
      setCrewTarget(obj as ShipObject);
      setCrewAmount(1);
      setCrewError(null);
      return;
    }
    if (launchMode && liveShip && obj && canBeFireTarget(obj, myShips)) {
      setLaunchTarget(obj);
      return;
    }
    // Clicking own fighter in Direct Fire phase makes it the attacker
    if (isFirePhase && obj?.type === 'SHUTTLE') {
      const shuttle = obj as ShuttleObject;
      const owned = myShips.has(shuttle.parentShipName ?? '');
      if (owned && (shuttle.weapons?.length ?? 0) > 0) {
        setFighterAttacker(shuttle);
        setFireTarget(null);
        setFireOptions(null);
        setSelectedWeapons(new Set());
        setFighterShotModes({});
        setFireError(null);
        return;
      }
    }
    // When fighter is the attacker, clicking an enemy picks the target
    if (isFirePhase && fighterAttacker && obj && canBeFireTarget(obj, myShips)) {
      setFireTarget(obj);
      fetchFireOptions(fighterAttacker.name, obj.name);
      return;
    }
    if (isFirePhase && liveShip && obj && canBeFireTarget(obj, myShips)) {
      setFireTarget(obj);
      fetchFireOptions(liveShip.name, obj.name);
      return;
    }
    // Normal: select the clicked ship as the attacker / info ship
    setFighterAttacker(null);
    setFighterShotModes({});
    setSelected(obj);
    setSnapTo(obj ? { name: obj.name } : null);
    setFireTarget(null);
    setFireOptions(null);
    setSelectedWeapons(new Set());
    setFireError(null);
    setLaunchMode(false);
    setLaunchTarget(null);
    setLaunchError(null);
    setShuttleLaunchMode(false);
    setShuttleLaunchError(null);
    setRotateMode(false);
    setRotateTarget(null);
    setRotateError(null);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isFirePhase, launchMode, boardingMode, harMode, crewMode, liveShip, fighterAttacker, myShips, session.gameId, session.playerToken]);

  function toggleWeapon(name: string) {
    setSelectedWeapons(prev => {
      const next = new Set(prev);
      if (next.has(name)) {
        next.delete(name);
        setShotCounts(sc => { const m = new Map(sc); m.delete(name); return m; });
      } else {
        next.add(name);
      }
      return next;
    });
  }

  function setShotCount(name: string, count: number) {
    setShotCounts(prev => new Map(prev).set(name, count));
  }

  function addLog(text: string, kind: 'combat' | 'phase' | 'error' | 'info') {
    const turn    = gameState?.turn    ?? '?';
    const impulse = gameState?.impulse ?? '?';
    setLog(prev => [...prev, { stamp: `T${turn}I${impulse}`, text, kind }]);
  }

  async function handleFire() {
    if ((!liveShip && !fighterAttacker) || !fireTarget || !fireOptions) return;
    setFireError(null);
    try {
      // Build the order (same shape as the FIRE wire format)
      const req: Record<string, unknown> = {
        shipName:      fighterAttacker ? fighterAttacker.name : liveShip!.name,
        targetName:    fireTarget.name,
        range:         fireOptions.range,
        adjustedRange: fireOptions.adjustedRange,
        shieldNumber:  fireOptions.shieldNumber,
      };
      if (fighterAttacker) {
        req.weaponNames = Array.from(selectedWeapons);
        req.useUim      = false;
        req.directFire  = false;
        // Include shotModes for FighterFusion weapons
        const modes: Record<string, string> = {};
        for (const wName of selectedWeapons) {
          const w = (fighterAttacker.weapons ?? []).find(x => x.name === wName);
          if (w?.chargesRemaining !== undefined)
            modes[wName] = fighterShotModes[wName] ?? 'SINGLE';
        }
        if (Object.keys(modes).length > 0) req.shotModes = modes;
      } else {
        req.weaponNames = Array.from(selectedWeapons).flatMap(name => {
          const w = liveShip!.weapons.find(x => x.name === name);
          const n = w && w.minImpulseGap === 0 && w.maxShotsPerTurn > 1
            ? Math.min(shotCounts.get(name) ?? 1, w.maxShotsPerTurn - w.shotsThisTurn)
            : 1;
          return Array(n).fill(name);
        });
        req.useUim     = useUim;
        req.directFire = directFire;
      }
      // Fire declaration flow (D6.315): the first order places the call —
      // everyone is convened to commit sealed orders; this order joins the
      // local draft plan and is submitted with COMMIT_FIRE_DECLARATION.
      if (myCommitted) {
        setFireError('Orders already sealed for this declaration');
        return;
      }
      if (!declarationOpen) {
        if (gameState?.fireDeclarationSpent) {
          setFireError("This impulse's fire declaration has already resolved (one per impulse)");
          return;
        }
        const call = await gameApi.submitAction(session.gameId, session.playerToken,
            { type: 'CALL_FIRE_DECLARATION' });
        if (!call.success) {
          setFireError(call.message);
          addLog(call.message, 'error');
          return;
        }
      }
      const attackerName = fighterAttacker ? fighterAttacker.name : liveShip!.name;
      setDeclarationOrders(prev => [...prev, {
        label: `${attackerName} → ${fireTarget.name} (${(req.weaponNames as string[]).length} wpn)`,
        shipName: attackerName,
        targetName: fireTarget.name,
        weaponNames: req.weaponNames as string[],
        shotModes: req.shotModes as Record<string, string> | undefined,
        range: fireOptions.range,
        adjustedRange: fireOptions.adjustedRange,
        shieldNumber: fireOptions.shieldNumber,
        useUim: (req.useUim as boolean) ?? false,
        directFire: (req.directFire as boolean) ?? false,
      }]);
      setFireTarget(null);
      setFireOptions(null);
      setSelectedWeapons(new Set());
      setShotCounts(new Map());
      setUseUim(false);
      setDirectFire(false);
      // Keep fighterAttacker so the player can add another order for it
      setFighterShotModes({});
    } catch (e: unknown) {
      setFireError(e instanceof Error ? e.message : 'Fire failed');
    }
  }

  async function handleCommitDeclaration() {
    setActionError(null);
    try {
      // One adjustment per ship whose drafted EW differs from its current values
      const ewAdjustments = Object.entries(declarationEw).flatMap(([shipName, ew]) => {
        const ship = (gameState?.mapObjects ?? []).find(o => o.name === shipName) as ShipObject | undefined;
        if (ship && ew.ecm === (ship.ecmAllocated ?? 0) && ew.eccm === (ship.eccmAllocated ?? 0))
          return [];
        return [{ shipName, ecm: ew.ecm, eccm: ew.eccm }];
      });
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'COMMIT_FIRE_DECLARATION',
        fireOrders: declarationOrders.map(o => ({
          shipName: o.shipName, targetName: o.targetName, weaponNames: o.weaponNames,
          shotModes: o.shotModes, range: o.range, adjustedRange: o.adjustedRange,
          shieldNumber: o.shieldNumber, useUim: o.useUim, directFire: o.directFire,
        })),
        ewAdjustments,
      });
      if (!res.success) {
        setActionError(res.message);
        return;
      }
      setCommittedRound(roundKey);
      setDeclarationOrders([]);
      setDeclarationEw({});
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Commit failed');
    }
  }

  async function handlePassDeclaration() {
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken,
          { type: 'PASS_FIRE_DECLARATION' });
      if (!res.success) {
        setActionError(res.message);
        return;
      }
      setCommittedRound(roundKey);
      setDeclarationOrders([]);
      setDeclarationEw({});
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Pass failed');
    }
  }

  async function handleMove(action: string) {
    if (!selectedShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'MOVE', shipName: selectedShip.name, action,
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Move failed');
    }
  }

  async function handleShuttleMove(action: string) {
    if (!liveShuttle) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'MOVE_SHUTTLE', shipName: liveShuttle.name, action,
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Shuttle move failed');
    }
  }

  async function handleTransferDrone(droneName: string, toShipName: string) {
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'TRANSFER_DRONE_CONTROL', shipName: toShipName, targetName: droneName,
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Drone transfer failed');
    }
  }

  async function handleFighterHet(facing: number) {
    if (!liveShuttle) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'PERFORM_FIGHTER_HET', shipName: liveShuttle.name, facing,
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Fighter HET failed');
    }
  }

  async function handleHet(facing: number) {
    if (!selectedShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'PERFORM_HET', shipName: selectedShip.name, facing,
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'HET failed');
    }
  }

  async function handleTacTurn(facing: number, sublight: boolean) {
    if (!selectedShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'PERFORM_TACTICAL_TURN',
        shipName: selectedShip.name,
        facing,
        action: sublight ? 'SUBLIGHT' : 'WARP',
      });
      if (!res.success) setActionError(res.message);
      else if (res.message) addLog(res.message, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Tactical Maneuver failed');
    }
  }

  async function handleCloak() {
    if (!liveShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'CLOAK', shipName: liveShip.name,
      });
      if (!res.success) setActionError(res.message);
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Cloak failed');
    }
  }

  async function handleUncloak() {
    if (!liveShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'UNCLOAK', shipName: liveShip.name,
      });
      if (!res.success) setActionError(res.message);
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Uncloak failed');
    }
  }

  function handleStartLaunch() {
    setLaunchMode(true);
    setLaunchTarget(null);
    setLaunchError(null);
    setTBombMode(false);
    setTBombPendingHex(null);
  }

  function handleClearLaunch() {
    setLaunchMode(false);
    setLaunchTarget(null);
    setLaunchError(null);
  }

  function handleStartShuttleLaunch() {
    setShuttleLaunchMode(true);
    setShuttleLaunchError(null);
    setLaunchMode(false);
    setLaunchTarget(null);
  }

  function handleCancelShuttleLaunch() {
    setShuttleLaunchMode(false);
    setShuttleLaunchError(null);
  }

  function handleStartBoarding() {
    setBoardingMode(true);
    setBoardingTarget(null);
    setBoardingNormal(0);
    setBoardingCommandos(0);
    setBoardingError(null);
    setLaunchMode(false);
    setLaunchTarget(null);
    setTBombMode(false);
    setTBombPendingHex(null);
    setBeamObjectMode(false);
  }

  function handleStartBeamObject() {
    setBeamObjectMode(true);
    setBoardingMode(false);
    setTractorMode(false);
    setLaunchMode(false);
    setTBombMode(false);
  }

  function handleCancelBeamObject() {
    setBeamObjectMode(false);
  }

  async function handleSubmitTractorBid() {
    if (!liveShip || !tractorBidTarget) return;
    setTractorError(null);
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'ESTABLISH_TRACTOR',
      shipName:   liveShip.name,
      targetName: tractorBidTarget,
      tractorBid: tractorBidValue,
    });
    setTractorBidTarget(null);
    if (!res.success) setTractorError(res.message);
  }

  async function handleSubmitNegTractorBid() {
    if (!liveShip) return;
    setTractorError(null);
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type:       'NEGATIVE_TRACTOR_BID',
      shipName:   liveShip.name,
      tractorBid: negTractorBidValue,
    });
    setNegTractorBidValue(0);
    if (!res.success) setTractorError(res.message);
  }

  async function handleReleaseTractor(targetName: string) {
    if (!liveShip) return;
    setTractorError(null);
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'RELEASE_TRACTOR', shipName: liveShip.name, targetName,
    });
    if (!res.success) setTractorError(res.message);
  }

  async function handleRotateTractored(destCol: number, destRow: number) {
    if (!liveShip || !rotateTarget) return;
    setRotateError(null);
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type:       'ROTATE_TRACTORED',
      shipName:   liveShip.name,
      targetName: rotateTarget,
      hexCol:     destCol,
      hexRow:     destRow,
    });
    if (!res.success) {
      setRotateError(res.message ?? 'Rotation failed');
    } else {
      setRotateMode(false);
      setRotateTarget(null);
      setRotateError(null);
    }
  }

  function handleCancelBoarding() {
    setBoardingMode(false);
    setBoardingTarget(null);
    setBoardingNormal(0);
    setBoardingCommandos(0);
    setBoardingError(null);
  }

  async function handleSubmitBoarding() {
    if (!liveShip || !boardingTarget) return;
    setBoardingError(null);
    try {
      const res = await gameApi.boardingAction(
        session.gameId, session.playerToken,
        liveShip.name, boardingTarget.name,
        boardingNormal, boardingCommandos,
      );
      if (!res.success) setBoardingError(res.message);
      else {
        addLog(`${liveShip.name} transported ${boardingNormal + boardingCommandos} boarding party(s) to ${boardingTarget.name}`, 'combat');
        handleCancelBoarding();
      }
    } catch (e: unknown) {
      setBoardingError(e instanceof Error ? e.message : 'Boarding action failed');
    }
  }

  async function handleConfirmAccelDisengage(shipName: string, confirm: boolean) {
    const ship = (gameState?.mapObjects ?? []).find(o => o.type === 'SHIP' && o.name === shipName) as ShipObject | undefined;
    if (confirm && ship) {
      const dirs = ship.destructionDirections ?? [];
      if (dirs.length > 0) {
        const dir = String.fromCharCode(65 + Math.floor((ship.facing - 1) / 4));
        if (dirs.includes(dir)) {
          if (!window.confirm(
            `Warning: ${shipName} is facing direction ${dir}, which is a destruction zone for your side.\n\nDisengaging in this direction will result in destruction. Continue?`
          )) return;
        }
      }
    }
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'CONFIRM_ACCEL_DISENGAGE', shipName, declare: confirm,
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`Accel disengage failed: ${res.message}`, 'error');
  }

  async function handleDisengageSeparation() {
    if (!liveShip) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'DISENGAGE_SEPARATION', shipName: liveShip.name,
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`Disengage failed: ${res.message}`, 'error');
  }

  async function handleEmergencyDecel() {
    if (!liveShip) return;
    if (!window.confirm(
      `Announce Emergency Deceleration for ${liveShip.name}?\n` +
      `The ship will stop in 2 impulses, then be immobile for 16 impulses (C8.0).`
    )) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'EMERGENCY_DECEL', shipName: liveShip.name,
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`Emergency decel failed: ${res.message}`, 'error');
  }

  async function handleGoPassiveFc() {
    if (!liveShip) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'FC_GO_PASSIVE', shipName: liveShip.name,
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`FC passive failed: ${res.message}`, 'error');
  }

  async function handleGoActiveFc() {
    if (!liveShip) return;
    if (liveShip.wildWeaselActive) {
      if (!window.confirm('Activating fire control will VOID the Wild Weasel. Continue?')) return;
    }
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'FC_GO_ACTIVE', shipName: liveShip.name,
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`FC activation failed: ${res.message}`, 'error');
  }

  async function handleConcede() {
    if (!window.confirm('Concede the battle? All your ships will be marked DESTROYED and the game may end.'))
      return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'CONCEDE',
    });
    if (res.success) addLog(res.message, 'combat');
    else addLog(`Concede failed: ${res.message}`, 'error');
  }

  // Unidentified enemy seekers for lab ID panel
  const idSeekers = (gameState?.mapObjects ?? [])
    .filter(o => (o.type === 'DRONE' || o.type === 'PLASMA') && !o.isIdentified
      && o.controllerFaction !== liveShip?.faction)
    .map(o => ({ name: o.name, type: o.type === 'DRONE' ? 'Drone' : 'Plasma' }));

  function handleStartId() {
    setIdMode(true);
    setIdSelected(new Set());
    setIdError(null);
    setBoardingMode(false);
    setBoardingTarget(null);
    setLaunchMode(false);
    setTBombMode(false);
  }

  function handleCancelId() {
    setIdMode(false);
    setIdSelected(new Set());
    setIdError(null);
  }

  function handleToggleIdSeeker(name: string) {
    setIdSelected(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name); else next.add(name);
      return next;
    });
  }

  async function handleSubmitId() {
    if (!liveShip || idSelected.size === 0) return;
    setIdError(null);
    try {
      const res = await gameApi.identifySeekers(
        session.gameId, session.playerToken,
        liveShip.name, Array.from(idSelected),
      );
      if (!res.success) setIdError(res.message);
      else {
        addLog(res.message, 'combat');
        handleCancelId();
      }
    } catch (e: unknown) {
      setIdError(e instanceof Error ? e.message : 'Identification failed');
    }
  }

  function handleStartHar() {
    setHarMode(true);
    setHarTarget(null);
    setHarOptions([]);
    setHarParties([null]);
    setHarError(null);
    setHarLoading(false);
    setLaunchMode(false);
    setLaunchTarget(null);
    setBoardingMode(false);
    setBoardingTarget(null);
    setTBombMode(false);
    setTBombPendingHex(null);
  }

  function handleCancelHar() {
    setHarMode(false);
    setHarTarget(null);
    setHarOptions([]);
    setHarParties([null]);
    setHarError(null);
  }

  function handleToggleTransporters() {
    const closing = transportersOpen;
    setTransportersOpen(!closing);
    if (closing) {
      setTBombMode(false); setTBombPendingHex(null); setTBombShieldChoice(null);
      setBoardingMode(false); setBoardingTarget(null);
      setHarMode(false); setHarTarget(null);
      setCrewMode(false); setCrewTarget(null);
    }
  }

  function handleStartCrew() {
    setCrewMode(true);
    setCrewTarget(null);
    setCrewAmount(1);
    setCrewError(null);
    setBoardingMode(false); setBoardingTarget(null);
    setHarMode(false); setHarTarget(null);
    setTBombMode(false); setTBombPendingHex(null);
  }

  function handleCancelCrew() {
    setCrewMode(false);
    setCrewTarget(null);
    setCrewAmount(1);
    setCrewError(null);
  }

  async function handleSubmitCrew() {
    if (!liveShip || !crewTarget) return;
    setCrewError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'TRANSPORT_CREW',
        shipName: liveShip.name,
        targetName: crewTarget.name,
        crewAmount,
      });
      if (!res.success) setCrewError(res.message);
      else {
        addLog(`${liveShip.name} transported ${crewAmount} crew to ${crewTarget.name}`, 'combat');
        handleCancelCrew();
        setTransportersOpen(false);
      }
    } catch (e: unknown) {
      setCrewError(e instanceof Error ? e.message : 'Crew transfer failed');
    }
  }

  async function handleSubmitHar() {
    if (!liveShip || !harTarget) return;
    const codes = harParties.filter((c): c is string => !!c);
    if (codes.length === 0) return;
    setHarError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type:        'HIT_AND_RUN',
        shipName:    liveShip.name,
        targetName:  harTarget.name,
        weaponNames: codes,
      });
      if (!res.success) {
        setHarError(res.message);
      } else {
        addLog(`${liveShip.name} hit & run raid on ${harTarget.name} (${codes.length} party): ${res.message}`, 'combat');
        handleCancelHar();
      }
    } catch (e: unknown) {
      setHarError(e instanceof Error ? e.message : 'H&R raid failed');
    }
  }

  function handleStartTBomb() {
    setTBombMode(true);
    setTBombPendingHex(null);
    setLaunchMode(false);
    setLaunchTarget(null);
  }

  function handleCancelTBomb() {
    setTBombMode(false);
    setTBombPendingHex(null);
    setTBombShieldChoice(null);
  }

  /** Friendly ship sharing the shuttle hex - the J1.61 landing target. */
  function landableCarrierFor(shuttle: ShuttleObject | null): ShipObject | null {
    if (!shuttle || !shuttle.location) return null;
    if (!myShips.has(shuttle.parentShipName ?? '')) return null;
    const carrier = gameState?.mapObjects.find(o =>
      o.type === 'SHIP' && myShips.has(o.name) && o.location === shuttle.location);
    return (carrier as ShipObject | undefined) ?? null;
  }

  /** Names of my shuttles currently held in any tractor (recovery candidates). */
  const heldFriendlyShuttles = new Set(
    (gameState?.mapObjects ?? [])
      .filter(o => o.type === 'SHUTTLE' && myShips.has((o as ShuttleObject).parentShipName ?? ''))
      .map(o => o.name));

  async function handleRecoverShuttle(shuttleName: string) {
    if (!liveShip) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'BEGIN_RECOVERY', shipName: liveShip.name, action: shuttleName,
    });
    if (!res.success) setActionError(res.message);
    else addLog(res.message, 'combat');
  }

  /** Probe canisters currently held in a tractor beam (recovery candidates). */
  const tractoredObjectives = (gameState?.mapObjects ?? [])
    .filter((o): o is ObjectiveObject => o.type === 'OBJECTIVE' && !!(o as ObjectiveObject).tractoredBy);

  async function handleRecoverObjective(objectiveName: string) {
    if (!liveShip) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'RECOVER_OBJECTIVE', shipName: liveShip.name, objectiveName,
    });
    if (!res.success) setActionError(res.message);
    else addLog(res.message, 'combat');
  }

  /** Free, transporter-retrievable canisters within transporter range (5) of the acting ship. */
  const beamableObjectives = (gameState?.mapObjects ?? []).filter((o): o is ObjectiveObject => {
    if (o.type !== 'OBJECTIVE') return false;
    const oo = o as ObjectiveObject;
    if (oo.carrierName || !(oo.retrieval?.includes('TRANSPORTER'))) return false;
    if (!liveShip) return false;
    const a = parseLocation(liveShip.location);
    const b = oo.location ? parseLocation(oo.location) : null;
    if (!a || !b) return false;
    return hexRange({ col: a[0], row: a[1] }, { col: b[0], row: b[1] }) <= 5;
  });

  async function handleBeamObject(objectiveName: string) {
    if (!liveShip) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'PICKUP_OBJECTIVE', shipName: liveShip.name, objectiveName, retrievalMethod: 'TRANSPORTER',
    });
    if (!res.success) setActionError(res.message);
    else addLog(res.message, 'combat');
    setBeamObjectMode(false);
  }

  async function handleLandShuttle() {
    const carrier = landableCarrierFor(liveShuttle);
    if (!liveShuttle || !carrier) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'LAND_SHUTTLE', shipName: carrier.name, action: liveShuttle.name,
    });
    if (!res.success) {
      setActionError(res.message);
    } else {
      addLog(res.message, 'combat');
      setSelected(null);
    }
  }

  async function handleLoadPersonnel() {
    if (!liveShuttle) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'LOAD_PERSONNEL', shipName: liveShuttle.name,
    });
    if (!res.success) setActionError(res.message);
    else addLog(res.message, 'combat');
  }

  async function handleUnloadPersonnel() {
    if (!liveShuttle) return;
    const res = await gameApi.submitAction(session.gameId, session.playerToken, {
      type: 'UNLOAD_PERSONNEL', shipName: liveShuttle.name,
    });
    if (!res.success) setActionError(res.message);
    else addLog(res.message, 'combat');
  }

  function handleHexClick(col: number, row: number) {
    if (tBombMode) {
      setTBombPendingHex({ col, row });
    }
    if (rotateMode && liveShip && rotateTarget && isInitialActivityPhase) {
      handleRotateTractored(col, row);
    }
  }

  async function handlePlaceTBomb(isReal: boolean, shieldNumber?: number) {
    if (!liveShip || !tBombPendingHex) return;
    setActionError(null);
    try {
      const res = await gameApi.placeTBomb(
        session.gameId, session.playerToken,
        liveShip.name, tBombPendingHex.col, tBombPendingHex.row, isReal, shieldNumber,
      );
      if (!res.success) {
        if (res.message?.startsWith('SHIELD_CHOICE:')) {
          const shields = res.message.slice('SHIELD_CHOICE:'.length).split(',').map(Number);
          setTBombShieldChoice({ isReal, shields });
          return; // keep tBombMode/tBombPendingHex; show the picker
        }
        setActionError(res.message);
      } else {
        addLog(`${liveShip.name} placed ${isReal ? 'T-bomb' : 'dummy T-bomb'} at ${tBombPendingHex.col}|${tBombPendingHex.row}`, 'combat');
      }
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'T-bomb placement failed');
    }
    setTBombMode(false);
    setTBombPendingHex(null);
    setTBombShieldChoice(null);
  }

  async function handleDropMine(mineType: 'TBOMB' | 'DUMMY_TBOMB' | 'NSM') {
    if (!liveShip) return;
    setActionError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type:     'DROP_MINE',
        shipName: liveShip.name,
        action:   mineType,
      });
      if (!res.success) setActionError(res.message);
      else {
        const label = mineType === 'NSM' ? 'NSM' : mineType === 'DUMMY_TBOMB' ? 'dummy T-Bomb' : 'T-Bomb';
        addLog(`${liveShip.name} dropped a ${label}`, 'combat');
        setDropMineMode(false);
      }
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Drop mine failed');
    }
  }

  async function handleLaunch(
    plasmaSelections: { name: string; pseudo: boolean; fastLoad?: boolean }[],
    rackSelections: { rackName: string; droneIndex: number }[],
    facing: number,
    seekerShuttles: { name: string; type: string }[] = [],
    seekerSpeed: number = 6,
  ) {
    if (!liveShip || !launchTarget) return;
    setLaunchError(null);
    let anyError = false;
    for (const { name, pseudo, fastLoad } of plasmaSelections) {
      try {
        const res = await gameApi.submitAction(session.gameId, session.playerToken, {
          type: 'LAUNCH_PLASMA', shipName: liveShip.name,
          targetName: launchTarget.name, weaponNames: [name], pseudo, fastLoad: fastLoad ?? false, facing,
        });
        if (!res.success) { setLaunchError(res.message); addLog(res.message, 'error'); anyError = true; break; }
        addLog(`${liveShip.name} launched plasma ${name} at ${launchTarget.name}`, 'combat');
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : 'Launch failed';
        setLaunchError(msg); anyError = true; break;
      }
    }
    if (!anyError) {
      for (const { rackName, droneIndex } of rackSelections) {
        try {
          const res = await gameApi.submitAction(session.gameId, session.playerToken, {
            type: 'LAUNCH_DRONE', shipName: liveShip.name,
            targetName: launchTarget.name, weaponNames: [rackName], range: droneIndex, facing,
          });
          if (!res.success) { setLaunchError(res.message); addLog(res.message, 'error'); anyError = true; break; }
          addLog(`${liveShip.name} launched drone from ${rackName} at ${launchTarget.name}`, 'combat');
        } catch (e: unknown) {
          const msg = e instanceof Error ? e.message : 'Launch failed';
          setLaunchError(msg); anyError = true; break;
        }
      }
    }
    if (!anyError) {
      for (const { name, type } of seekerShuttles) {
        const actionType = type === 'suicide' ? 'LAUNCH_SUICIDE_SHUTTLE' : 'LAUNCH_SCATTER_PACK';
        try {
          const res = await gameApi.submitAction(session.gameId, session.playerToken, {
            type: actionType, shipName: liveShip.name,
            action: name, targetName: launchTarget!.name,
            facing, speed: seekerSpeed,
          });
          if (!res.success) { setLaunchError(res.message); addLog(res.message, 'error'); anyError = true; break; }
          const label = type === 'suicide' ? 'suicide shuttle' : 'scatter pack';
          addLog(`${liveShip.name} launched ${label} ${name} at ${launchTarget!.name}`, 'combat');
        } catch (e: unknown) {
          const msg = e instanceof Error ? e.message : 'Launch failed';
          setLaunchError(msg); anyError = true; break;
        }
      }
    }
    if (!anyError) handleClearLaunch();
  }

  async function handleLaunchShuttle(shuttleName: string, speed: number, facing: number) {
    if (!liveShip) return;
    setShuttleLaunchError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'LAUNCH_SHUTTLE', shipName: liveShip.name,
        action: shuttleName, speed, range: facing,
      });
      if (!res.success) { setShuttleLaunchError(res.message); return; }
      addLog(`${liveShip.name} launched shuttle ${shuttleName}`, 'combat');
      setShuttleLaunchMode(false);
      setShuttleLaunchError(null);
    } catch (e: unknown) {
      setShuttleLaunchError(e instanceof Error ? e.message : 'Launch failed');
    }
  }

  function handleStartWwLaunch(shuttleName: string) {
    setWwLaunchShuttle(shuttleName);
    setWwLaunchError(null);
  }

  function handleCancelWwLaunch() {
    setWwLaunchShuttle(null);
    setWwLaunchError(null);
  }

  async function handleLaunchWildWeasel(shuttleName: string, speed: number, facing: number) {
    if (!liveShip) return;
    setWwLaunchError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type: 'LAUNCH_WILD_WEASEL', shipName: liveShip.name, action: shuttleName,
        speed, range: facing,
      });
      if (res.success) {
        addLog(res.message, 'combat');
        setWwLaunchShuttle(null);
      } else {
        setWwLaunchError(res.message);
      }
    } catch (e: unknown) {
      setWwLaunchError(e instanceof Error ? e.message : 'WW launch failed');
    }
  }

  async function handleAdvancePhase() {
    setActionError(null);
    try {
      if (isReady) {
        // Cancel ready
        await gameApi.submitAction(session.gameId, session.playerToken, { type: 'UNREADY' });
        setIsReady(false);
      } else {
        const res = await gameApi.submitAction(session.gameId, session.playerToken, { type: 'ADVANCE_PHASE' });
        if (!res.success && !res.message.startsWith('WAITING')) {
          setActionError(res.message);
        } else if (res.message.startsWith('WAITING')) {
          // Still waiting for other players — show Cancel so they can un-ready
          setIsReady(true);
        } else {
          // Phase advanced immediately (this player was last to ready up).
          // Phase/damage log arrives via WebSocket broadcast — don't add it here too.
          // Don't set isReady — the incoming broadcastState will change phase/impulse
          // and the useEffect will reset it cleanly, avoiding a race condition.
        }
      }
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Advance failed');
    }
  }

  return (
    <div className="board-layout">

      {/* ---- Accel disengage confirmation prompt (C7.1) ---- */}
      {(() => {
        const myPending = (gameState?.pendingAccelDisengage ?? []).filter(n => myShips.has(n));
        if (myPending.length === 0) return null;
        return (
          <div style={{
            position: 'fixed', inset: 0, zIndex: 998,
            background: 'rgba(0,0,0,0.7)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <div style={{
              background: '#161b22', border: '1px solid #f0c040',
              borderRadius: 10, padding: '1.5rem', width: 420,
            }}>
              <div style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: '0.75rem', color: '#f0c040' }}>
                Disengagement by Acceleration (C7.1)
              </div>
              {myPending.map(name => {
                const ship = (gameState?.mapObjects ?? []).find(o => o.type === 'SHIP' && o.name === name) as ShipObject | undefined;
                const dirs = ship?.destructionDirections ?? [];
                const dir = ship ? String.fromCharCode(65 + Math.floor((ship.facing - 1) / 4)) : '?';
                const isDanger = dirs.includes(dir);
                return (
                  <div key={name} style={{ marginBottom: '1rem', padding: '0.75rem', background: '#0d1117', borderRadius: 6 }}>
                    <div style={{ marginBottom: '0.4rem' }}>
                      <strong>{name}</strong> has met the speed and warp requirements.
                      Do you wish to disengage?
                    </div>
                    {isDanger && (
                      <div style={{ color: '#f85149', fontSize: '0.85rem', marginBottom: '0.4rem' }}>
                        ⚠ Currently facing direction {dir} — a destruction zone for your side. Disengaging will destroy this ship.
                      </div>
                    )}
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button onClick={() => handleConfirmAccelDisengage(name, true)}>
                        Yes — Disengage
                      </button>
                      <button className="secondary" onClick={() => handleConfirmAccelDisengage(name, false)}>
                        No — Stay
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })()}

      {/* ---- Scoreboard overlay: automatic at game over, on demand via Score ---- */}
      {(gameState?.gameOver || (showScore && gameState?.scoreboard)) && (
        <div style={{
          position: 'fixed', inset: 0, zIndex: 999,
          background: 'rgba(0,0,0,0.82)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          overflowY: 'auto', padding: '2rem',
        }}>
          <div style={{
            background: '#161b22', border: '1px solid #30363d',
            borderRadius: 12, padding: '2rem', width: '100%', maxWidth: 700,
          }}>
            {/* Header */}
            <div style={{ textAlign: 'center', marginBottom: '1.5rem' }}>
              <div style={{ fontSize: '2rem', fontWeight: 700 }}>
                {gameState.gameOver
                  ? (gameState.winnerTeam ? 'Battle Complete' : '— Draw —')
                  : 'Current Score'}
              </div>
              {gameState.gameOver && gameState.winnerTeam && (
                <div style={{ fontSize: '1.3rem', color: '#79c0ff', marginTop: '0.25rem' }}>
                  {gameState.winnerTeam} wins
                </div>
              )}
              <div style={{ color: '#8b949e', fontSize: '0.85rem', marginTop: '0.25rem' }}>
                {gameState.gameOver
                  ? gameState.endReason
                  : 'Victory points if the battle ended now (S2.21)'}
              </div>
            </div>

            {/* Per-team ship lists */}
            {gameState.scoreboard && (() => {
              const STATUS_COLOR: Record<string, string> = {
                DESTROYED:  '#f85149',
                CAPTURED:   '#9b3ad5',
                CRIPPLED:   '#f0c040',
                DISENGAGED: '#79c0ff',
                DAMAGED:    '#d29922',
                INTACT:     '#3fb950',
              };
              return gameState.scoreboard!.teams.map(team => {
                const ships = gameState.scoreboard!.ships.filter(s => s.teamName === team.teamName);
                const victoryColor = team.levelOfVictory.includes('Victory') ? '#3fb950'
                                   : team.levelOfVictory === 'Draw'           ? '#f0c040'
                                   : '#f85149';
                return (
                  <div key={team.teamName} style={{
                    background: '#0d1117', borderRadius: 8,
                    border: '1px solid #30363d', padding: '1rem', marginBottom: '1rem',
                  }}>
                    {/* Team header */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: '0.6rem' }}>
                      <span style={{ fontWeight: 700, fontSize: '1.05rem', color: '#79c0ff' }}>{team.teamName}</span>
                      <span style={{ fontSize: '0.82rem', color: victoryColor, fontWeight: 600 }}>
                        {team.vpScored} VP — {team.levelOfVictory}
                      </span>
                    </div>
                    {/* Ship rows */}
                    {ships.map(s => (
                      <div key={s.shipName} style={{
                        display: 'flex', justifyContent: 'space-between',
                        fontSize: '0.85rem', padding: '3px 0',
                        borderTop: '1px solid #21262d',
                      }}>
                        <span style={{ color: '#e6edf3' }}>{s.shipName}</span>
                        <span>
                          <span style={{ color: STATUS_COLOR[s.status] ?? '#8b949e', marginRight: '0.75rem' }}>
                            {s.status}
                          </span>
                          <span style={{ color: '#8b949e', minWidth: 60, display: 'inline-block', textAlign: 'right' }}>
                            {s.vpScored > 0 ? `${s.vpScored} vp` : '—'}
                          </span>
                        </span>
                      </div>
                    ))}
                    {/* Objectives held by this team (no points yet — just control) */}
                    {(() => {
                      const held = gameState.scoreboard!.objectives.filter(o => o.ownerTeam === team.teamName);
                      if (held.length === 0) return null;
                      return (
                        <div style={{ marginTop: '0.6rem' }}>
                          <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#8b949e', marginBottom: 2 }}>
                            Objectives held
                          </div>
                          {held.map(o => (
                            <div key={o.name} style={{
                              display: 'flex', justifyContent: 'space-between',
                              fontSize: '0.85rem', padding: '3px 0', borderTop: '1px solid #21262d',
                            }}>
                              <span style={{ color: '#e0b34a' }}>{o.name}</span>
                              <span style={{ color: o.state === 'SECURED' ? '#3fb950' : '#f0c040', fontWeight: 600 }}>
                                {o.state === 'SECURED' ? 'secured' : 'carried'}
                              </span>
                            </div>
                          ))}
                        </div>
                      );
                    })()}
                  </div>
                );
              });
            })()}
            {/* Unclaimed objectives still in play */}
            {gameState.scoreboard && gameState.scoreboard.objectives.some(o => o.state === 'FREE') && (
              <div style={{
                background: '#0d1117', borderRadius: 8,
                border: '1px solid #30363d', padding: '0.75rem 1rem', marginBottom: '1rem',
              }}>
                <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: '#8b949e', marginBottom: 4 }}>
                  Unclaimed objectives
                </div>
                {gameState.scoreboard.objectives.filter(o => o.state === 'FREE').map(o => (
                  <div key={o.name} style={{ fontSize: '0.85rem', color: '#e0b34a', padding: '2px 0' }}>{o.name}</div>
                ))}
              </div>
            )}
          </div>
          <div style={{ textAlign: 'center', marginTop: '1.5rem' }}>
            {gameState.gameOver
              ? <button onClick={onLeave}>Leave Game</button>
              : <button className="secondary" onClick={() => setShowScore(false)}>Close</button>}
          </div>
        </div>
      )}

      <div className="board-topbar">
        <span className="board-title">Amarillo</span>
        <span className="board-phase">{phaseLabel}</span>
        <div className="topbar-actions">
          {actionError && <span className="topbar-error">{actionError}</span>}
          <button className="secondary" onClick={() => setShowScore(true)}
                  title="Current victory-point standings (S2.21)">
            Score
          </button>
          {isMovementPhase && myMovablePending.length > 0 && (
            <span className="topbar-move-warn">
              Move: <strong>{myMovablePending[0]}</strong>
            </span>
          )}
          {isMovementPhase && myMovablePending.length === 0 && opponentMovablePending.length > 0 && (
            <span className="topbar-move-wait">
              Waiting for: <strong>{opponentMovablePending[0]}</strong>
            </span>
          )}
          {isFirePhase && declarationOpen && (
            <span className="topbar-move-warn" style={{ borderColor: '#a78bfa', color: '#a78bfa' }}>
              ⚔ Fire declaration by <strong>{gameState?.fireDeclarationCaller}</strong>
              {myCommitted
                ? ` — sealed ✓ (${(gameState?.fireDeclarationResponded ?? []).length}/${gameState?.playerCount ?? 0})`
                : ' — commit orders or pass'}
            </span>
          )}
          {(() => {
            const readyCount   = gameState?.readyCount  ?? 0;
            const playerCount  = gameState?.playerCount ?? 0;
            const isLastHoldout = !isReady && playerCount > 1 && readyCount === playerCount - 1;
            const btnClass = isReady ? 'secondary' : isLastHoldout ? 'btn-last-holdout' : '';
            const label = isReady
              ? 'Cancel'
              : isLastHoldout
                ? `⚠ Ready — ${readyCount}/${playerCount} waiting`
                : readyCount > 0
                  ? `Ready (${readyCount}/${playerCount})`
                  : 'Ready';
            return (
              <button
                onClick={handleAdvancePhase}
                disabled={(isMovementPhase && myMovablePending.length > 0) || isReinforcementPhase
                  || (isFirePhase && declarationOpen && !myCommitted)}
                className={btnClass}
                title={isReinforcementPhase ? 'Use the Reinforcement dialog to confirm and ready up'
                  : isFirePhase && declarationOpen && !myCommitted
                    ? 'Respond to the fire declaration first (commit orders or pass)'
                    : undefined}
              >
                {label}
              </button>
            );
          })()}
          {!gameState?.gameOver && (
            <button
              className="secondary"
              style={{ borderColor: '#f85149', color: '#f85149' }}
              onClick={handleConcede}
              title="Concede — all your ships are destroyed"
            >
              Concede
            </button>
          )}
          <button className="secondary" onClick={onLeave}>Leave</button>
        </div>
      </div>

      <div className="board-content">
        <div className="board-canvas-area">
          <HexGrid
            mapCols={gameState?.mapCols}
            mapRows={gameState?.mapRows}
            mapObjects={gameState?.mapObjects}
            myShips={gameState?.myShips ?? null}
            selectedName={selected?.name ?? null}
            fireTargetName={fireTarget?.name ?? null}
            onSelect={handleMapSelect}
            onHexClick={handleHexClick}
            pickingHex={tBombMode}
            snapTo={snapTo}
          />
        </div>

        {liveShip && (
          <ShipSidebar
            ship={liveShip}
            isMine={myShips.has(liveShip.name)}
            canMove={canMove}
            phase={phase}
            gameId={session.gameId}
            playerToken={session.playerToken}
            fireTarget={fireTarget}
            fireOptions={fireOptions}
            loadingOptions={loadingOptions}
            selectedWeapons={selectedWeapons}
            onToggleWeapon={toggleWeapon}
            shotCounts={shotCounts}
            onSetShotCount={setShotCount}
            useUim={useUim}
            onToggleUim={() => setUseUim(v => !v)}
            directFire={directFire}
            onToggleDirectFire={() => setDirectFire(v => !v)}
            onFire={handleFire}
            onClearTarget={() => { setFireTarget(null); setFireOptions(null); setSelectedWeapons(new Set()); setShotCounts(new Map()); setUseUim(false); setDirectFire(false); }}
            fireError={fireError}
            onMove={handleMove}
            onHet={handleHet}
            onTacTurn={handleTacTurn}
            onCloak={handleCloak}
            onUncloak={handleUncloak}
            onClose={() => { setSelected(null); setFireTarget(null); setFireOptions(null); handleClearLaunch(); handleCancelTBomb(); handleCancelBoarding(); handleCancelHar(); handleCancelCrew(); setTransportersOpen(false); }}
            launchMode={launchMode}
            launchTarget={launchTarget}
            launchError={launchError}
            onStartLaunch={handleStartLaunch}
            onClearLaunch={handleClearLaunch}
            onLaunch={handleLaunch}
            tBombMode={tBombMode}
            tBombPendingHex={tBombPendingHex}
            tBombShieldChoice={tBombShieldChoice}
            onStartTBomb={handleStartTBomb}
            onCancelTBomb={handleCancelTBomb}
            onPlaceTBomb={handlePlaceTBomb}
            dropMineMode={dropMineMode}
            onToggleDropMine={() => setDropMineMode(m => !m)}
            onDropMine={handleDropMine}
            boardingMode={boardingMode}
            boardingTarget={boardingTarget}
            boardingNormal={boardingNormal}
            boardingCommandos={boardingCommandos}
            boardingError={boardingError}
            onStartBoarding={handleStartBoarding}
            onCancelBoarding={handleCancelBoarding}
            onSetBoardingNormal={setBoardingNormal}
            onSetBoardingCommandos={setBoardingCommandos}
            onSubmitBoarding={handleSubmitBoarding}
            beamObjectMode={beamObjectMode}
            canBeamObjectTargets={beamableObjectives.length > 0}
            onStartBeamObject={handleStartBeamObject}
            onCancelBeamObject={handleCancelBeamObject}
            idMode={idMode}
            idSeekers={idSeekers}
            idSelected={idSelected}
            idError={idError}
            onStartId={handleStartId}
            onCancelId={handleCancelId}
            onToggleIdSeeker={handleToggleIdSeeker}
            onSubmitId={handleSubmitId}
            shuttleLaunchMode={shuttleLaunchMode}
            shuttleLaunchError={shuttleLaunchError}
            onStartShuttleLaunch={handleStartShuttleLaunch}
            onCancelShuttleLaunch={handleCancelShuttleLaunch}
            onLaunchShuttle={handleLaunchShuttle}
            wwLaunchShuttle={wwLaunchShuttle}
            wwLaunchError={wwLaunchError}
            onStartWwLaunch={handleStartWwLaunch}
            onCancelWwLaunch={handleCancelWwLaunch}
            onLaunchWildWeasel={handleLaunchWildWeasel}
            harMode={harMode}
            harTarget={harTarget}
            harOptions={harOptions}
            harParties={harParties}
            harError={harError}
            harLoading={harLoading}
            onStartHar={handleStartHar}
            onCancelHar={handleCancelHar}
            onSetHarParties={setHarParties}
            onSubmitHar={handleSubmitHar}
            transportersOpen={transportersOpen}
            onToggleTransporters={handleToggleTransporters}
            crewMode={crewMode}
            crewTarget={crewTarget}
            crewAmount={crewAmount}
            crewError={crewError}
            onStartCrew={handleStartCrew}
            onCancelCrew={handleCancelCrew}
            onSetCrewAmount={setCrewAmount}
            onSubmitCrew={handleSubmitCrew}
            onDisengageSeparation={handleDisengageSeparation}
            onGoPassiveFc={handleGoPassiveFc}
            onGoActiveFc={handleGoActiveFc}
            absoluteImpulse={gameState?.absoluteImpulse ?? 0}
            onEmergencyDecel={handleEmergencyDecel}
            tractorMode={tractorMode}
            tractorError={tractorError}
            onStartTractor={() => setTractorMode(true)}
            onCancelTractor={() => { setTractorMode(false); setTractorError(null); }}
            onReleaseTractor={handleReleaseTractor}
            tractorBidTarget={tractorBidTarget}
            tractorBidValue={tractorBidValue}
            tractorRangeMultiplier={tractorRangeMultiplier}
            onSetTractorBid={setTractorBidValue}
            onSubmitTractorBid={handleSubmitTractorBid}
            onCancelTractorBid={() => { setTractorBidTarget(null); setTractorError(null); }}
            tractorBidMax={Math.floor(((liveShip?.tractorEnergyRemaining ?? 0) + (liveShip?.batteryPower ?? 0)) / tractorRangeMultiplier)}
            pendingTractorAuction={gameState.pendingTractorAuction ?? null}
            negTractorBidValue={negTractorBidValue}
            onSetNegTractorBid={setNegTractorBidValue}
            onSubmitNegTractorBid={handleSubmitNegTractorBid}
            heldFriendlyShuttles={heldFriendlyShuttles}
            onRecoverShuttle={handleRecoverShuttle}
            tractoredObjectives={tractoredObjectives}
            onRecoverObjective={handleRecoverObjective}
            rotateMode={rotateMode}
            rotateTarget={rotateTarget}
            rotateError={rotateError}
            onStartRotate={(targetName) => { setRotateMode(true); setRotateTarget(targetName); setRotateError(null); }}
            onCancelRotate={() => { setRotateMode(false); setRotateTarget(null); setRotateError(null); }}
          />
        )}

        {liveShuttle && !liveShip && (
          <ShuttleMovementPanel
            shuttle={liveShuttle}
            isMine={myShips.has(liveShuttle.parentShipName ?? '')}
            canMove={canMoveShuttle}
            phase={phase}
            onMove={handleShuttleMove}
            onHet={handleFighterHet}
            onTacTurn={handleTacTurn}
            onClose={() => setSelected(null)}
            canLand={phase === 'Activity' && !!landableCarrierFor(liveShuttle)}
            onLand={handleLandShuttle}
            onLoadPersonnel={handleLoadPersonnel}
            onUnloadPersonnel={handleUnloadPersonnel}
          />
        )}

        {(liveDrone || liveSeekerShuttle) && !liveShip && !liveShuttle && (() => {
          const seekerName    = liveDrone?.name ?? liveSeekerShuttle!.name;
          const controllerName = liveDrone?.controllerName ?? liveSeekerShuttle!.controllerName ?? null;
          const targetName    = liveDrone?.targetName ?? liveSeekerShuttle!.targetName ?? null;
          const seekerLabel   = liveDrone
            ? `Drone${liveDrone.isIdentified ? ` (Type ${liveDrone.droneType})` : ''}`
            : liveSeekerShuttle!.type === 'SUICIDE_SHUTTLE' ? 'Suicide Shuttle' : 'Scatter Pack';
          const isMine = myShips.has(controllerName ?? '');
          const transferShips = isMine && targetName
            ? (gameState?.mapObjects ?? [])
                .filter((o): o is ShipObject =>
                  o.type === 'SHIP' &&
                  myShips.has(o.name) &&
                  o.name !== controllerName &&
                  !!(o as ShipObject).lockOnTargets?.includes(targetName)
                )
            : [];
          return (
            <div className="board-sidebar">
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
                <span style={{ fontWeight: 700, fontSize: '1rem' }}>{seekerLabel}</span>
                <button className="sidebar-close secondary" onClick={() => setSelected(null)}>✕</button>
              </div>
              <div style={{ fontSize: 13, color: '#8b949e', lineHeight: 1.6 }}>
                <div>Controller: <strong style={{ color: '#e6edf3' }}>{controllerName ?? '—'}</strong></div>
                {targetName && (
                  <div>Target: <strong style={{ color: '#e6edf3' }}>{targetName}</strong></div>
                )}
                {liveDrone?.isIdentified && (
                  <>
                    <div>Warhead: <strong style={{ color: '#e6edf3' }}>{liveDrone.warheadDamage}</strong></div>
                    <div>Endurance: <strong style={{ color: '#e6edf3' }}>{liveDrone.endurance}</strong></div>
                    <div>Hull: <strong style={{ color: '#e6edf3' }}>{liveDrone.hull}/{liveDrone.maxHull}</strong></div>
                  </>
                )}
                {liveSeekerShuttle?.warheadDamage != null && (
                  <div>Warhead: <strong style={{ color: '#e6edf3' }}>{liveSeekerShuttle.warheadDamage}</strong></div>
                )}
              </div>
              {isMine && targetName && (
                <div style={{ marginTop: 12 }}>
                  <div style={{ fontSize: 12, color: '#8b949e', marginBottom: 6 }}>Transfer Control To:</div>
                  {transferShips.length === 0
                    ? <div style={{ fontSize: 12, color: '#6e7681' }}>No allied ships have lock-on to target</div>
                    : transferShips.map(s => (
                        <button
                          key={s.name}
                          onClick={() => handleTransferDrone(seekerName, s.name)}
                          style={{
                            display: 'block', width: '100%', marginBottom: 4,
                            background: '#21262d', border: '1px solid #30363d',
                            color: '#e6edf3', borderRadius: 6, padding: '6px 12px',
                            fontSize: 13, cursor: 'pointer', textAlign: 'left',
                          }}
                          onMouseEnter={e => (e.currentTarget.style.borderColor = '#58a6ff')}
                          onMouseLeave={e => (e.currentTarget.style.borderColor = '#30363d')}
                        >
                          {s.name}
                        </button>
                      ))
                  }
                </div>
              )}
            </div>
          );
        })()}

        {/* Fighter fire panel — shown when a fighter is the attacker */}
        {isFirePhase && fighterAttacker && (
          <div className="board-sidebar">
            <FighterFirePanel
              fighter={fighterAttacker}
              target={fireTarget}
              options={fireOptions}
              loadingOptions={loadingOptions}
              selectedWeapons={selectedWeapons}
              onToggleWeapon={toggleWeapon}
              shotModes={fighterShotModes}
              onSetShotMode={(name, mode) => setFighterShotModes(prev => ({ ...prev, [name]: mode }))}
              onFire={handleFire}
              onClear={() => { setFighterAttacker(null); setFireTarget(null); setFireOptions(null); setSelectedWeapons(new Set()); setFighterShotModes({}); setFireError(null); }}
              error={fireError}
            />
          </div>
        )}
      </div>

      {/* Fire declaration panel (D6.315) — drafted orders + EW, sealed on commit */}
      {isFirePhase && declarationOpen && !myCommitted && (
        <div className="board-log" style={{ borderColor: '#a78bfa', padding: '8px 12px' }}>
          <div style={{ color: '#a78bfa', fontWeight: 600, marginBottom: 4 }}>
            ⚔ Fire declaration — seal your orders
          </div>
          <div style={{ fontSize: '0.85em', marginBottom: 4 }}>
            {declarationOrders.length === 0
              ? 'No fire orders yet — click an enemy, pick weapons, and Fire to add one. Committing nothing is a legal bluff.'
              : declarationOrders.map((o, i) => (
                  <div key={i}>
                    {o.label}{' '}
                    <button className="secondary" style={{ padding: '0 6px' }}
                      onClick={() => setDeclarationOrders(prev => prev.filter((_, j) => j !== i))}>
                      ✕
                    </button>
                  </div>
                ))}
          </div>
          {liveShip && (liveShip.sensorRating ?? 0) > 0 && (() => {
            const ew = declarationEw[liveShip.name]
                ?? { ecm: liveShip.ecmAllocated ?? 0, eccm: liveShip.eccmAllocated ?? 0 };
            const sensor = liveShip.sensorRating ?? 0;
            const added = Math.max(0, ew.ecm - (liveShip.ecmAllocated ?? 0))
                        + Math.max(0, ew.eccm - (liveShip.eccmAllocated ?? 0));
            const step = (field: 'ecm' | 'eccm', delta: number) => {
              const next = { ...ew, [field]: Math.max(0, ew[field] + delta) };
              if (next.ecm + next.eccm <= sensor)
                setDeclarationEw(prev => ({ ...prev, [liveShip.name]: next }));
            };
            return (
              <div style={{ fontSize: '0.85em', marginBottom: 4 }}>
                EW — {liveShip.name}:{' '}
                ECM <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('ecm', -1)}>−</button>
                {' '}{ew.ecm}{' '}
                <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('ecm', 1)}>+</button>
                {'   '}ECCM <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('eccm', -1)}>−</button>
                {' '}{ew.eccm}{' '}
                <button className="secondary" style={{ padding: '0 6px' }} onClick={() => step('eccm', 1)}>+</button>
                {'   '}(sensor {sensor}, battery {liveShip.batteryCharge ?? 0}
                {added > 0 ? `, +${added} costs ${added} battery` : ''})
                {' '}<span style={{ color: '#8b949e' }}>drops are lost for the turn</span>
              </div>
            );
          })()}
          <button onClick={handleCommitDeclaration} style={{ marginRight: 8 }}>
            Commit orders ({declarationOrders.length})
          </button>
          <button className="secondary" onClick={handlePassDeclaration}>Pass</button>
        </div>
      )}

      {/* Combat log */}
      <div className="board-log">
        {pendingCombat.length > 0 && (
          <div className="log-pending-banner">
            {pendingCombat.length} fire result{pendingCombat.length !== 1 ? 's' : ''} pending — revealed when fire phase ends
          </div>
        )}
        {log.map((entry, i) => (
          <div key={i} className="log-entry">
            <span className="log-stamp">{entry.stamp}</span>
            <span className={`log-text ${entry.kind}`}>{entry.text}</span>
          </div>
        ))}
        <div ref={logEndRef} />
      </div>

      {/* Reinforcement dialog — shown during Reinforcement phase */}
      {isReinforcementPhase && (
        <ReinforcementDialog
          pendingVolleys={gameState?.pendingVolleys ?? []}
          myShipNames={myShips}
          batteryByShip={batteryByShip}
          onSubmit={async decisions => {
            setActionError(null);
            try {
              if (decisions.length > 0)
                await gameApi.submitReinforcement(session.gameId, session.playerToken, decisions);
              await gameApi.submitAction(session.gameId, session.playerToken, { type: 'ADVANCE_PHASE' });
              setIsReady(true);
            } catch (e: unknown) {
              setActionError(e instanceof Error ? e.message : 'Reinforcement failed');
            }
          }}
        />
      )}

      {/* DAC choice dialog — blocks input until defender picks a system */}
      {(gameState?.pendingControlOverflows?.length ?? 0) > 0 && (
        <ControlOverflowDialog
          pendingOverflows={gameState!.pendingControlOverflows}
          myShipNames={myShips}
          onRelease={async (seekerName: string) => {
            setActionError(null);
            try {
              await gameApi.submitAction(session.gameId, session.playerToken, {
                type: 'SUBMIT_CONTROL_OVERFLOW',
                targetName: seekerName,
              });
            } catch (e: unknown) {
              setActionError(e instanceof Error ? e.message : 'Control overflow resolution failed');
            }
          }}
          onTransfer={async (seekerName: string, toShipName: string) => {
            setActionError(null);
            try {
              await gameApi.submitAction(session.gameId, session.playerToken, {
                type: 'SUBMIT_CONTROL_OVERFLOW',
                targetName: seekerName,
                shipName: toShipName,
              });
            } catch (e: unknown) {
              setActionError(e instanceof Error ? e.message : 'Control transfer failed');
            }
          }}
        />
      )}

      {(gameState?.pendingDacChoices?.length ?? 0) > 0 && (
        <DacChoiceDialog
          pendingChoices={gameState!.pendingDacChoices}
          myShipNames={myShips}
          allShips={(gameState!.mapObjects.filter(o => o.type === 'SHIP') as ShipObject[])}
          onSubmit={async (chosen: string) => {
            setActionError(null);
            try {
              await gameApi.submitAction(session.gameId, session.playerToken, {
                type: 'SUBMIT_DAC_CHOICE',
                action: chosen,
              });
            } catch (e: unknown) {
              setActionError(e instanceof Error ? e.message : 'DAC choice failed');
            }
          }}
        />
      )}

      {/* Energy allocation dialog — floating, draggable, no overlay */}
      {gameState?.awaitingAllocation && !eaDismissed && (
        <EnergyAllocationDialog
          gameId={session.gameId}
          playerToken={session.playerToken}
          myShipNames={gameState.myShips ?? []}
          pendingNames={gameState.pendingAllocation ?? []}
          allShips={(gameState.mapObjects.filter(o => o.type === 'SHIP') as ShipObject[])}
          activeShuttles={(gameState.mapObjects.filter(o => o.type === 'SHUTTLE') as ShuttleObject[])}
          onTabChange={name => {
            const ship = (gameState?.mapObjects ?? []).find(o => o.type === 'SHIP' && o.name === name);
            if (ship) setSelected(ship);
            setSnapTo({ name });
          }}
          onDone={msg => {
            addLog(msg, 'phase');
            setEaDismissed(true);
          }}
          onError={msg => addLog(msg, 'error')}
        />
      )}
    </div>
  );
}
