import { useState, useCallback, useEffect, useRef } from 'react';
import type { LobbyResult } from './Lobby';
import { useGameSocket } from '../hooks/useGameSocket';
import type { MapObject, ShipObject, DroneObject, PlasmaObject, ShieldState, WeaponState } from '../types/gameState';
import { factionColor, parseLocation } from '../types/gameState';
import { gameApi } from '../api/gameApi';
import HexGrid from './HexGrid';
import EnergyAllocationDialog from './EnergyAllocationDialog';

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
  if (obj.type === 'DRONE')  return factionColor((obj as DroneObject).controllerFaction);
  if (obj.type === 'PLASMA') return factionColor((obj as PlasmaObject).controllerFaction);
  return '#888';
}

/** True if this object can be selected as a fire target by the given player. */
function canBeFireTarget(obj: MapObject, myShips: Set<string>): boolean {
  if (obj.type === 'SHIP')   return !myShips.has(obj.name);
  if (obj.type === 'DRONE')  return !myShips.has((obj as DroneObject).controllerName ?? '');
  if (obj.type === 'PLASMA') return !myShips.has((obj as PlasmaObject).controllerName ?? '');
  return false;
}

// ---- Sub-components ----

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
        {isDown && <span className="shield-down-label"> (down)</span>}
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
    statusText  = (w.maxShotsPerTurn > 1 && w.shotsThisTurn >= w.maxShotsPerTurn)
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
    statusText  = w.maxShotsPerTurn > 1 ? `${baseStatus} ${remaining}×` : baseStatus;
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

function hasLaunchableWeapons(ship: ShipObject): boolean {
  // A plasma launcher is launchable if it has a real torpedo armed OR a pseudo available
  const hasPlasma     = (ship.weapons    ?? []).some(w => w.launcherType && w.functional && (w.armed || w.pseudoPlasmaReady));
  const hasLoadedRack = (ship.droneRacks ?? []).some(r => r.functional && r.drones.length > 0 && r.canFire);
  return hasPlasma || hasLoadedRack;
}

// ---- Launch panel ----

interface LaunchPanelProps {
  ship:           ShipObject;
  target:         ShipObject | null;
  onLaunch:       (plasmaSelections: {name: string; pseudo: boolean}[], rackSelections: {rackName: string; droneIndex: number}[]) => void;
  onClearTarget:  () => void;
  onCancel:       () => void;
  error:          string | null;
}

function LaunchPanel({ ship, target, onLaunch, onClearTarget, onCancel, error }: LaunchPanelProps) {
  const [selLaunchers,  setSelLaunchers]  = useState<Set<string>>(new Set());
  const [pseudoSet,     setPseudoSet]     = useState<Set<string>>(new Set());
  // Maps rack name → chosen drone index within that rack's ammo list
  const [selRackDrones, setSelRackDrones] = useState<Map<string, number>>(new Map());

  // Include any launcher with a real torpedo armed OR a pseudo still available
  const launchablePlasma = (ship.weapons ?? []).filter(w =>
    w.launcherType && w.functional && (w.armed || w.pseudoPlasmaReady)
  );
  const loadedRacks = (ship.droneRacks ?? []).filter(r => r.functional && r.drones.length > 0 && r.canFire);
  const targetColor = target ? factionColor(target.faction) : '#888';

  function toggleLauncher(name: string) {
    setSelLaunchers(prev => { const n = new Set(prev); n.has(name) ? n.delete(name) : n.add(name); return n; });
  }
  function togglePseudo(name: string) {
    setPseudoSet(prev => { const n = new Set(prev); n.has(name) ? n.delete(name) : n.add(name); return n; });
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

  const totalSelected = selLaunchers.size + selRackDrones.size;

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
        <>
          {/* Plasma launchers */}
          {launchablePlasma.length > 0 && (
            <>
              <div className="sidebar-divider" />
              <div className="sidebar-stat-label" style={{ marginBottom: 4 }}>Plasma Torpedoes</div>
              {launchablePlasma.map(w => {
                const pseudoOnly = !w.armed; // unarmed — only pseudo available
                return (
                  <div key={w.name} className="launch-weapon-row">
                    {pseudoOnly ? (
                      /* Unarmed launcher — only pseudo option */
                      <label className="ea-check-label" style={{ color: '#8b949e' }}>
                        <input type="checkbox" checked={selLaunchers.has(w.name)}
                          onChange={() => { toggleLauncher(w.name); if (!pseudoSet.has(w.name)) togglePseudo(w.name); }} />
                        {weaponLabel(w)}
                        <span className="ea-note-dim" style={{ marginLeft: 4 }}>PSEUDO</span>
                      </label>
                    ) : (
                      /* Armed launcher — real torpedo + optional pseudo */
                      <>
                        <label className="ea-check-label">
                          <input type="checkbox" checked={selLaunchers.has(w.name)}
                            onChange={() => { toggleLauncher(w.name); if (pseudoSet.has(w.name)) togglePseudo(w.name); }} />
                          {weaponLabel(w)}
                          <span className="ea-note-dim" style={{ marginLeft: 4 }}>
                            {w.armingType === 'OVERLOAD' ? 'EPT' : 'STD'}
                          </span>
                        </label>
                        {selLaunchers.has(w.name) && w.pseudoPlasmaReady && w.armingType !== 'OVERLOAD' && (
                          <label className="ea-check-label" style={{ marginLeft: 16, color: '#8b949e' }}>
                            <input type="checkbox" checked={pseudoSet.has(w.name)}
                              onChange={() => togglePseudo(w.name)} />
                            Also launch pseudo
                          </label>
                        )}
                      </>
                    )}
                  </div>
                );
              })}
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

          <div style={{ display: 'flex', gap: 6, marginTop: 8 }}>
            <button className="fire-btn"
              style={{ flex: 1 }}
              disabled={totalSelected === 0}
              onClick={() => onLaunch(
                launchablePlasma.filter(w => selLaunchers.has(w.name))
                                .map(w => ({ name: w.name, pseudo: pseudoSet.has(w.name) })),
                Array.from(selRackDrones.entries()).map(([rackName, droneIndex]) => ({ rackName, droneIndex })),
              )}>
              Launch ({totalSelected})
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

// ---- Fire options ----

interface FireOptions {
  range:         number;
  adjustedRange: number;
  shieldNumber:  number;
  weaponsInArc:  string[];
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
  onFire:          () => void;
  onClearTarget:   () => void;
  error:           string | null;
}

function FirePanel({
  attacker, target, options, loadingOptions,
  selectedWeapons, onToggleWeapon, shotCounts, onSetShotCount,
  useUim, onToggleUim, onFire, onClearTarget, error,
}: FirePanelProps) {
  const targetColor = target ? mapObjectColor(target) : '#888';

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
        </>
      )}

      {loadingOptions && <div className="fire-hint">Calculating…</div>}

      {/* Weapon checkboxes */}
      {options && options.weaponsInArc.length > 0 && (
        <>
          <div className="sidebar-divider" />
          <div className="fire-weapon-list">
            {attacker.weapons
              .filter(w => w.functional)
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
                      {unavailLabel && <span className={`fire-ooa ${unavailLabel === 'on cooldown' ? 'fire-cooldown' : ''}`}>{unavailLabel}</span>}
                    </label>
                    {isMultiShot && checked && (
                      <div className="shot-stepper">
                        <button className="shot-step-btn" onClick={e => { e.preventDefault(); onSetShotCount(w.name, Math.max(1, shots - 1)); }}>−</button>
                        <span className="shot-count">{shots}×</span>
                        <button className="shot-step-btn" onClick={e => { e.preventDefault(); onSetShotCount(w.name, Math.min(maxShots, shots + 1)); }}>+</button>
                      </div>
                    )}
                  </div>
                );
              })}
          </div>
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

      {error && <div className="fire-error">{error}</div>}
    </div>
  );
}

// ---- Move button grid ----

const MOVE_BUTTONS: { label: string; action: string; row: number; col: number; alwaysDisabled?: boolean }[] = [
  { label: '↖',    action: 'SIDESLIP_LEFT',   row: 1, col: 1 },
  { label: '▲',    action: 'FORWARD',          row: 1, col: 2 },
  { label: '↗',    action: 'SIDESLIP_RIGHT',   row: 1, col: 3 },
  { label: '↺',    action: 'TURN_LEFT',        row: 2, col: 1 },
  { label: 'STOP', action: 'EMERGENCY_DECEL',  row: 2, col: 2, alwaysDisabled: true },
  { label: '↻',    action: 'TURN_RIGHT',       row: 2, col: 3 },
];

// ---- Ship sidebar ----

interface SidebarProps {
  ship:            ShipObject;
  isMine:          boolean;
  canMove:         boolean;
  phase:           string;
  fireTarget:      MapObject | null;
  fireOptions:     FireOptions | null;
  loadingOptions:  boolean;
  selectedWeapons: Set<string>;
  onToggleWeapon:  (name: string) => void;
  shotCounts:      Map<string, number>;
  onSetShotCount:  (name: string, count: number) => void;
  useUim:          boolean;
  onToggleUim:     () => void;
  onFire:          () => void;
  onClearTarget:   () => void;
  fireError:       string | null;
  onMove:          (action: string) => void;
  onCloak:         () => void;
  onUncloak:       () => void;
  onClose:         () => void;
  // Launch
  launchMode:      boolean;
  launchTarget:    ShipObject | null;
  launchError:     string | null;
  onStartLaunch:   () => void;
  onClearLaunch:   () => void;
  onLaunch:        (plasma: {name: string; pseudo: boolean}[], racks: {rackName: string; droneIndex: number}[]) => void;
  // T-bomb (transporter)
  tBombMode:       boolean;
  tBombPendingHex: {col: number; row: number} | null;
  onStartTBomb:    () => void;
  onCancelTBomb:   () => void;
  onPlaceTBomb:    (isReal: boolean) => void;
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
}

function ShipSidebar({
  ship, isMine, canMove, phase,
  fireTarget, fireOptions, loadingOptions, selectedWeapons,
  onToggleWeapon, shotCounts, onSetShotCount, useUim, onToggleUim, onFire, onClearTarget, fireError,
  onMove, onCloak, onUncloak, onClose,
  launchMode, launchTarget, launchError, onStartLaunch, onClearLaunch, onLaunch,
  tBombMode, tBombPendingHex, onStartTBomb, onCancelTBomb, onPlaceTBomb,
  dropMineMode, onToggleDropMine, onDropMine,
  boardingMode, boardingTarget, boardingNormal, boardingCommandos, boardingError,
  onStartBoarding, onCancelBoarding, onSetBoardingNormal, onSetBoardingCommandos, onSubmitBoarding,
  harMode, harTarget, harOptions, harParties, harError, harLoading,
  onStartHar, onCancelHar, onSetHarParties, onSubmitHar,
}: SidebarProps) {
  const color = factionColor(ship.faction);
  const totalPower = (ship.availableLWarp  ?? 0) + (ship.availableRWarp  ?? 0)
                 + (ship.availableCWarp  ?? 0) + (ship.availableImpulse ?? 0)
                 + (ship.availableApr    ?? 0) + (ship.availableAwr     ?? 0)
                 + (ship.availableBattery ?? 0);
  const isFirePhase     = phase === 'Direct Fire';
  const isActivityPhase = phase === 'Activity';
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
            </>
          )}

          {/* Activity phase: row of action buttons */}
          {phase === 'Activity' && (
            <div className="action-btn-row">
              {hasLaunchableWeapons(ship) && (
                <button
                  className={`action-strip-btn${launchMode ? ' active' : ''}`}
                  onClick={launchMode ? onClearLaunch : onStartLaunch}
                  title="Launch seekers"
                >
                  Launch
                </button>
              )}
              {canTBomb && (
                <button
                  className={`action-strip-btn${tBombMode ? ' active' : ''}`}
                  onClick={tBombMode ? onCancelTBomb : onStartTBomb}
                  title="Place T-bomb via transporter"
                >
                  T-bomb
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
              {canBoard && (
                <button
                  className={`action-strip-btn${boardingMode ? ' active' : ''}`}
                  onClick={boardingMode ? onCancelBoarding : onStartBoarding}
                  title="Board enemy ship"
                >
                  Board
                </button>
              )}
              {canHar && (
                <button
                  className={`action-strip-btn${harMode ? ' active' : ''}`}
                  onClick={harMode ? onCancelHar : onStartHar}
                  title="Hit & Run raid"
                >
                  H&amp;R
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
            </div>
          )}

        </div>
      )}

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

      {tBombMode && (
        <div className="sidebar-action-detail">
          {tBombPendingHex ? (
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

      {harMode && (
        <div className="sidebar-action-detail">
          {harTarget ? (
            <>
              <div className="sidebar-section-title">H&amp;R Raid: {harTarget.name}</div>
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
        <StatRow label="Speed"    value={ship.speed} />
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
        <StatRow label="Forward" value={ship.availableFhull} />
        <StatRow label="Aft"     value={ship.availableAhull} />
        {ship.availableChull > 0 && <StatRow label="Center" value={ship.availableChull} />}
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

      <div className="sidebar-section">
        <div className="sidebar-section-title">Power</div>
        <StatRow label="L-Warp"  value={ship.availableLWarp} />
        <StatRow label="R-Warp"  value={ship.availableRWarp} />
        {ship.availableCWarp > 0 && <StatRow label="C-Warp" value={ship.availableCWarp} />}
        <StatRow label="Impulse" value={ship.availableImpulse} />
        {ship.availableApr > 0 && <StatRow label="APR"     value={ship.availableApr} />}
        {ship.availableAwr > 0 && <StatRow label="AWR"     value={ship.availableAwr} />}
        <StatRow label="Battery" value={ship.availableBattery} />
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

    </div>
  );
}

// ---- Main board ----

export default function GameBoard({ session, onLeave }: Props) {
  const gameState = useGameSocket(session.gameId, session.playerToken);
  const [selected, setSelected]             = useState<MapObject | null>(null);
  const [actionError, setActionError]       = useState<string | null>(null);
  // Fire state
  const [fireTarget, setFireTarget]         = useState<MapObject | null>(null);
  const [fireOptions, setFireOptions]       = useState<FireOptions | null>(null);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [selectedWeapons, setSelectedWeapons] = useState<Set<string>>(new Set());
  const [shotCounts, setShotCounts]           = useState<Map<string, number>>(new Map());
  const [useUim, setUseUim]                 = useState(false);
  const [fireError, setFireError]           = useState<string | null>(null);
  // Launch state
  const [launchMode,   setLaunchMode]   = useState(false);
  const [launchTarget, setLaunchTarget] = useState<ShipObject | null>(null);
  const [launchError,  setLaunchError]  = useState<string | null>(null);
  // T-bomb placement state
  const [tBombMode,       setTBombMode]       = useState(false);
  const [tBombPendingHex, setTBombPendingHex] = useState<{col: number; row: number} | null>(null);
  // Drop mine state
  const [dropMineMode, setDropMineMode] = useState(false);
  // Boarding action state
  const [boardingMode,    setBoardingMode]    = useState(false);
  const [boardingTarget,  setBoardingTarget]  = useState<ShipObject | null>(null);
  const [boardingNormal,  setBoardingNormal]  = useState(0);
  const [boardingCommandos, setBoardingCommandos] = useState(0);
  const [boardingError,   setBoardingError]   = useState<string | null>(null);
  // Hit & Run state
  const [harMode,    setHarMode]    = useState(false);
  const [harTarget,  setHarTarget]  = useState<ShipObject | null>(null);
  const [harOptions, setHarOptions] = useState<{ code: string; label: string }[]>([]);
  const [harParties, setHarParties] = useState<(string | null)[]>([null]);
  const [harError,   setHarError]   = useState<string | null>(null);
  const [harLoading, setHarLoading] = useState(false);
  const [isReady, setIsReady]               = useState(false);
  const [eaDismissed, setEaDismissed]       = useState(false);
  const [log, setLog] = useState<{ stamp: string; text: string; kind: 'combat' | 'phase' | 'error' | 'info' }[]>([]);
  const [pendingCombat, setPendingCombat]   = useState<{ stamp: string; text: string }[]>([]);
  const prevPhaseRef      = useRef<string>('');
  const lastCombatLogRef  = useRef<string>(''); // dedup: skip if same batch arrives twice
  const logEndRef     = useRef<HTMLDivElement>(null);

  const phase      = gameState?.phase ?? '';
  const myShips    = new Set(gameState?.myShips ?? []);
  const movableNow = gameState?.movableNow ?? [];
  const isFirePhase = phase === 'Direct Fire';

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

  const phaseLabel = gameState
    ? `Turn ${gameState.turn}  |  Impulse ${gameState.impulse}  |  ${phase}`
    : 'Loading…';

  const selectedShip = selected?.type === 'SHIP' ? (selected as ShipObject) : null;
  const canMove      = selectedShip !== null && movableNow.includes(selectedShip.name);

  // Always show the latest live state for the selected ship
  const liveShip = selectedShip
    ? (gameState?.mapObjects.find(o => o.name === selectedShip.name && o.type === 'SHIP') as ShipObject | undefined) ?? selectedShip
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
    if (launchMode && liveShip && obj?.type === 'SHIP') {
      const clicked = obj as ShipObject;
      if (!myShips.has(clicked.name)) {
        setLaunchTarget(clicked);
        return;
      }
    }
    if (isFirePhase && liveShip && obj && canBeFireTarget(obj, myShips)) {
      setFireTarget(obj);
      fetchFireOptions(liveShip.name, obj.name);
      return;
    }
    // Normal: select the clicked ship as the attacker / info ship
    setSelected(obj);
    setFireTarget(null);
    setFireOptions(null);
    setSelectedWeapons(new Set());
    setFireError(null);
    setLaunchMode(false);
    setLaunchTarget(null);
    setLaunchError(null);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isFirePhase, launchMode, boardingMode, harMode, liveShip, myShips, session.gameId, session.playerToken]);

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
    if (!liveShip || !fireTarget || !fireOptions) return;
    setFireError(null);
    try {
      const res = await gameApi.submitAction(session.gameId, session.playerToken, {
        type:          'FIRE',
        shipName:      liveShip.name,
        targetName:    fireTarget.name,
        weaponNames:   Array.from(selectedWeapons).flatMap(name => {
          const w = liveShip.weapons.find(x => x.name === name);
          const n = w && w.minImpulseGap === 0 && w.maxShotsPerTurn > 1
            ? Math.min(shotCounts.get(name) ?? 1, w.maxShotsPerTurn - w.shotsThisTurn)
            : 1;
          return Array(n).fill(name);
        }),
        range:         fireOptions.range,
        adjustedRange: fireOptions.adjustedRange,
        shieldNumber:  fireOptions.shieldNumber,
        useUim:        useUim,
      });
      if (!res.success) {
        setFireError(res.message);
        addLog(res.message, 'error');
      } else {
        // Fire result arrives via WebSocket combatLog broadcast — don't add it
        // here too or it will appear twice (once per source).
        setFireTarget(null);
        setFireOptions(null);
        setSelectedWeapons(new Set());
        setShotCounts(new Map());
        setUseUim(false);
      }
    } catch (e: unknown) {
      setFireError(e instanceof Error ? e.message : 'Fire failed');
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
  }

  function handleHexClick(col: number, row: number) {
    if (tBombMode) {
      setTBombPendingHex({ col, row });
    }
  }

  async function handlePlaceTBomb(isReal: boolean) {
    if (!liveShip || !tBombPendingHex) return;
    setActionError(null);
    try {
      const res = await gameApi.placeTBomb(
        session.gameId, session.playerToken,
        liveShip.name, tBombPendingHex.col, tBombPendingHex.row, isReal,
      );
      if (!res.success) setActionError(res.message);
      else addLog(`${liveShip.name} placed ${isReal ? 'T-bomb' : 'dummy T-bomb'} at ${tBombPendingHex.col}|${tBombPendingHex.row}`, 'combat');
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'T-bomb placement failed');
    }
    setTBombMode(false);
    setTBombPendingHex(null);
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
    plasmaSelections: { name: string; pseudo: boolean }[],
    rackSelections: { rackName: string; droneIndex: number }[],
  ) {
    if (!liveShip || !launchTarget) return;
    setLaunchError(null);
    let anyError = false;
    for (const { name, pseudo } of plasmaSelections) {
      try {
        const res = await gameApi.submitAction(session.gameId, session.playerToken, {
          type: 'LAUNCH_PLASMA', shipName: liveShip.name,
          targetName: launchTarget.name, weaponNames: [name], pseudo,
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
            targetName: launchTarget.name, weaponNames: [rackName], range: droneIndex,
          });
          if (!res.success) { setLaunchError(res.message); addLog(res.message, 'error'); anyError = true; break; }
          addLog(`${liveShip.name} launched drone from ${rackName} at ${launchTarget.name}`, 'combat');
        } catch (e: unknown) {
          const msg = e instanceof Error ? e.message : 'Launch failed';
          setLaunchError(msg); anyError = true; break;
        }
      }
    }
    if (!anyError) handleClearLaunch();
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
          // Don't set isReady — the incoming broadcastState will change phase/impulse
          // and the useEffect will reset it cleanly, avoiding a race condition.
          if (res.message) addLog(res.message, 'combat');
        }
      }
    } catch (e: unknown) {
      setActionError(e instanceof Error ? e.message : 'Advance failed');
    }
  }

  return (
    <div className="board-layout">
      <div className="board-topbar">
        <span className="board-title">Amarillo</span>
        <span className="board-phase">{phaseLabel}</span>
        <div className="topbar-actions">
          {actionError && <span className="topbar-error">{actionError}</span>}
          <button
            onClick={handleAdvancePhase}
            className={isReady ? 'secondary' : ''}
          >
            {isReady ? 'Cancel' : 'Ready'}
          </button>
          <button className="secondary" onClick={onLeave}>Leave</button>
        </div>
      </div>

      <div className="board-content">
        <div className="board-canvas-area">
          <HexGrid
            mapObjects={gameState?.mapObjects}
            myShips={gameState?.myShips ?? null}
            selectedName={selected?.name ?? null}
            fireTargetName={fireTarget?.name ?? null}
            onSelect={handleMapSelect}
            onHexClick={handleHexClick}
            pickingHex={tBombMode}
          />
        </div>

        {liveShip && (
          <ShipSidebar
            ship={liveShip}
            isMine={myShips.has(liveShip.name)}
            canMove={canMove}
            phase={phase}
            fireTarget={fireTarget}
            fireOptions={fireOptions}
            loadingOptions={loadingOptions}
            selectedWeapons={selectedWeapons}
            onToggleWeapon={toggleWeapon}
            shotCounts={shotCounts}
            onSetShotCount={setShotCount}
            useUim={useUim}
            onToggleUim={() => setUseUim(v => !v)}
            onFire={handleFire}
            onClearTarget={() => { setFireTarget(null); setFireOptions(null); setSelectedWeapons(new Set()); setShotCounts(new Map()); setUseUim(false); }}
            fireError={fireError}
            onMove={handleMove}
            onCloak={handleCloak}
            onUncloak={handleUncloak}
            onClose={() => { setSelected(null); setFireTarget(null); setFireOptions(null); handleClearLaunch(); handleCancelTBomb(); handleCancelBoarding(); handleCancelHar(); }}
            launchMode={launchMode}
            launchTarget={launchTarget}
            launchError={launchError}
            onStartLaunch={handleStartLaunch}
            onClearLaunch={handleClearLaunch}
            onLaunch={handleLaunch}
            tBombMode={tBombMode}
            tBombPendingHex={tBombPendingHex}
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
          />
        )}
      </div>

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

      {/* Energy allocation dialog — floating, draggable, no overlay */}
      {gameState?.awaitingAllocation && !eaDismissed && (
        <EnergyAllocationDialog
          gameId={session.gameId}
          playerToken={session.playerToken}
          myShipNames={gameState.myShips ?? []}
          pendingNames={gameState.pendingAllocation ?? []}
          allShips={(gameState.mapObjects.filter(o => o.type === 'SHIP') as ShipObject[])}
          onTabChange={name => {
            const ship = (gameState?.mapObjects ?? []).find(o => o.type === 'SHIP' && o.name === name);
            if (ship) setSelected(ship);
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
