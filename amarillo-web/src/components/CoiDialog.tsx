import { useState } from 'react';
import type { CoiSideData, CoiShipData, CoiSubmission, CoiDroneType, CoiShuttlePrepEntry } from '../api/gameApi';

interface Props {
  sides:       CoiSideData[];
  playerToken: string;
  onSubmit:    (submission: CoiSubmission) => Promise<void>;
  onSkip:      () => void;
  busy:        boolean;
}

type ArmMode = 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING';

interface ShuttlePrep {
  type:          string;       // 'suicide' | 'scatterpack' | 'wildweasel'
  energyPerTurn: number;       // suicide only
  drones:        string[];     // scatterpack only
}

interface ShipCoi {
  extraBoardingParties: number;
  convertBpToCommando:  number;
  extraCommandoSquads:  number;
  extraTBombs:          number;
  weaponArmingModes:    Record<string, ArmMode>;
  droneRackLoadouts:    Record<number, string[]>;   // rackIndex → drone type names
  shuttlePrep:          Record<string, ShuttlePrep | null>; // shuttleName → prep or null (not selected)
}

function defaultShipCoi(): ShipCoi {
  return {
    extraBoardingParties: 0,
    convertBpToCommando:  0,
    extraCommandoSquads:  0,
    extraTBombs:          0,
    weaponArmingModes:    {},
    droneRackLoadouts:    {},
    shuttlePrep:          {},
  };
}

function coiCost(c: ShipCoi): number {
  return c.extraBoardingParties * 0.5
       + c.convertBpToCommando  * 0.5
       + c.extraCommandoSquads  * 1.0
       + c.extraTBombs          * 4.0;
}

function droneSpaceUsed(drones: string[], allTypes: CoiDroneType[]): number {
  return drones.reduce((sum, name) => {
    const dt = allTypes.find(t => t.name === name);
    return sum + (dt?.rack ?? 0);
  }, 0);
}

/**
 * Compute how many drones of each type are available across all racks,
 * using custom loadouts where set, defaultAmmo otherwise.
 * Each rack contributes ammo × (1 + reloadCount) total drones.
 */
function computeDronePool(
  racks: CoiDroneRack[],
  loadouts: Record<number, string[]>,
): Record<string, number> {
  const pool: Record<string, number> = {};
  for (const rack of racks) {
    const ammo = loadouts[rack.index] ?? rack.defaultAmmo;
    const copies = 1 + rack.reloadCount;
    for (const name of ammo) {
      pool[name] = (pool[name] ?? 0) + copies;
    }
  }
  return pool;
}

/** Sum of drones committed to ALL scatter packs in the current COI state. */
function computePackCommitments(shuttlePrep: Record<string, ShuttlePrep | null>): Record<string, number> {
  const committed: Record<string, number> = {};
  for (const prep of Object.values(shuttlePrep)) {
    if (!prep || prep.type !== 'scatterpack') continue;
    for (const name of prep.drones) {
      committed[name] = (committed[name] ?? 0) + 1;
    }
  }
  return committed;
}

function ShipCoiPanel({
  ship,
  coi,
  onChange,
}: {
  ship:     CoiShipData;
  coi:      ShipCoi;
  onChange: (next: ShipCoi) => void;
}) {
  const cost     = coiCost(coi);
  const overBudget = cost > ship.coiBudget;
  const hasWs3Heavy = ship.weaponStatus === 3 && ship.heavyWeapons.length > 0;
  const hasSpecialShuttles = (ship.convertibleShuttles?.length ?? 0) > 0 && ship.maxPreparedShuttles > 0;

  const CONVERSION_LABELS: Record<string, string> = {
    suicide:     'Suicide Shuttle',
    scatterpack: 'Scatter Pack',
    wildweasel:  'Wild Weasel',
  };

  function setNum(field: keyof ShipCoi, val: number) {
    onChange({ ...coi, [field]: Math.max(0, val) });
  }

  function setArmMode(designator: string, mode: ArmMode) {
    onChange({ ...coi, weaponArmingModes: { ...coi.weaponArmingModes, [designator]: mode } });
  }

  const prepCount = Object.values(coi.shuttlePrep).filter(v => v !== null).length;

  function toggleShuttle(shuttleName: string) {
    const current = coi.shuttlePrep[shuttleName];
    if (current !== undefined && current !== null) {
      onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: null } });
    } else if (prepCount < ship.maxPreparedShuttles) {
      const shInfo = ship.convertibleShuttles?.find(s => s.name === shuttleName);
      const defaultType = shInfo?.types[0] ?? 'suicide';
      onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: { type: defaultType, energyPerTurn: 3, drones: [] } } });
    }
  }

  function setShuttleType(shuttleName: string, type: string) {
    const prep = coi.shuttlePrep[shuttleName];
    if (!prep) return;
    onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: { ...prep, type, drones: [] } } });
  }

  function setSuicideEnergy(shuttleName: string, val: number) {
    const prep = coi.shuttlePrep[shuttleName];
    if (!prep) return;
    onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: { ...prep, energyPerTurn: Math.max(1, Math.min(3, val)) } } });
  }

  function addScatterDrone(shuttleName: string, typeName: string) {
    const prep = coi.shuttlePrep[shuttleName];
    if (!prep) return;
    const dt = ship.availableDroneTypes.find(t => t.name === typeName);
    if (!dt) return;
    const used = prep.drones.reduce((s, n) => { const d = ship.availableDroneTypes.find(t => t.name === n); return s + (d?.rack ?? 0); }, 0);
    if (used + dt.rack > 6) return;
    onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: { ...prep, drones: [...prep.drones, typeName] } } });
  }

  function removeScatterDrone(shuttleName: string, idx: number) {
    const prep = coi.shuttlePrep[shuttleName];
    if (!prep) return;
    onChange({ ...coi, shuttlePrep: { ...coi.shuttlePrep, [shuttleName]: { ...prep, drones: prep.drones.filter((_, i) => i !== idx) } } });
  }

  return (
    <div className="coi-ship-panel">
      <div className="coi-ship-header">
        <span className="coi-ship-name">{ship.shipName}</span>
        <span className="coi-budget" style={{ color: overBudget ? '#f85149' : '#56d364' }}>
          {cost.toFixed(1)} / {ship.coiBudget.toFixed(1)} BPV
        </span>
      </div>

      {/* Boarding parties */}
      <div className="coi-row">
        <label className="coi-label">Extra boarding parties (0.5 ea, max 10)</label>
        <input type="number" min={0} max={10} value={coi.extraBoardingParties}
          onChange={e => setNum('extraBoardingParties', parseInt(e.target.value) || 0)} />
      </div>

      {/* Convert to commandos */}
      {ship.allowCommandos && (
        <>
          <div className="coi-row">
            <label className="coi-label">Convert BPs → commandos (0.5 ea, max 2)</label>
            <input type="number" min={0} max={2} value={coi.convertBpToCommando}
              onChange={e => setNum('convertBpToCommando', parseInt(e.target.value) || 0)} />
          </div>
          <div className="coi-row">
            <label className="coi-label">Extra commando squads (1.0 ea, max 2)</label>
            <input type="number" min={0} max={2} value={coi.extraCommandoSquads}
              onChange={e => setNum('extraCommandoSquads', parseInt(e.target.value) || 0)} />
          </div>
        </>
      )}

      {/* T-bombs */}
      {ship.allowTBombs && ship.maxTBombs > 0 && (
        <div className="coi-row">
          <label className="coi-label">T-bombs (4.0 ea, +1 free dummy each, max {ship.maxTBombs})</label>
          <input type="number" min={0} max={ship.maxTBombs} value={coi.extraTBombs}
            onChange={e => setNum('extraTBombs', Math.min(ship.maxTBombs, parseInt(e.target.value) || 0))} />
        </div>
      )}

      {/* Drone rack loadouts */}
      {ship.droneRacks.length > 0 && ship.availableDroneTypes.length > 0 && (
        <div className="coi-section">
          <div className="coi-section-title">Drone Rack Loadouts</div>
          {ship.droneRacks.map(rack => {
            const loadout = coi.droneRackLoadouts[rack.index] ?? [];
            const used    = droneSpaceUsed(loadout, ship.availableDroneTypes);
            const remaining = rack.spaces - used;

            function addDrone(typeName: string) {
              const dt = ship.availableDroneTypes.find(t => t.name === typeName);
              if (!dt || dt.rack > remaining) return;
              const next = [...loadout, typeName];
              onChange({ ...coi, droneRackLoadouts: { ...coi.droneRackLoadouts, [rack.index]: next } });
            }
            function removeDrone(idx: number) {
              const next = loadout.filter((_, i) => i !== idx);
              onChange({ ...coi, droneRackLoadouts: { ...coi.droneRackLoadouts, [rack.index]: next } });
            }

            return (
              <div key={rack.index} className="coi-rack-block">
                <div className="coi-rack-header">
                  Rack {rack.designator} — {used.toFixed(1)} / {rack.spaces} spaces
                </div>
                <div className="coi-rack-loadout">
                  {loadout.map((name, i) => {
                    const dt = ship.availableDroneTypes.find(t => t.name === name);
                    return (
                      <span key={i} className="coi-drone-chip">
                        {name} ({dt?.damage}dmg, spd {dt?.speed})
                        <button className="coi-drone-remove" onClick={() => removeDrone(i)}>✕</button>
                      </span>
                    );
                  })}
                  {loadout.length === 0 && <span className="coi-note">Empty — using scenario defaults</span>}
                </div>
                {remaining > 0 && (
                  <div className="coi-drone-add-row">
                    {ship.availableDroneTypes
                      .filter(dt => dt.rack <= remaining
                        && (rack.canLoadTypeVI || !dt.name.startsWith('TypeVI')))
                      .map(dt => (
                        <button key={dt.name} className="secondary coi-drone-add-btn"
                          onClick={() => addDrone(dt.name)}>
                          + {dt.name} ({dt.rack}sp)
                        </button>
                      ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Heavy weapon arming modes (WS-3 only) */}
      {hasWs3Heavy && (
        <div className="coi-section">
          <div className="coi-section-title">Weapon Arming (WS-III — weapons start fully armed)</div>
          {ship.heavyWeapons.map(w => {
            const mode = coi.weaponArmingModes[w.designator] ?? 'STANDARD';
            return (
              <div key={w.designator} className="coi-row">
                <label className="coi-label">{w.type} {w.designator}</label>
                <div className="coi-arm-options">
                  {(w.isPlasma
                    ? [['STANDARD', 'Armed'], ['ROLLING', 'Rolling']] as [ArmMode, string][]
                    : [['STANDARD', 'Standard'], ['SPECIAL', 'Proximity'], ['OVERLOAD', 'Overload']] as [ArmMode, string][]
                  ).map(([m, label]) => (
                    <label key={m} className="coi-arm-option">
                      <input type="radio" name={`${ship.shipName}-${w.designator}`}
                        value={m} checked={mode === m}
                        onChange={() => setArmMode(w.designator, m)} />
                      {label}
                    </label>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Special shuttle conversion (WS-2: max 1, WS-3: max 2) */}
      {hasSpecialShuttles && (
        <div className="coi-section">
          <div className="coi-section-title">
            Shuttle Conversions (WS-{ship.weaponStatus}: up to {ship.maxPreparedShuttles})
          </div>
          {(ship.convertibleShuttles ?? []).map(sh => {
            const shuttleName = sh.name;
            const prep = coi.shuttlePrep[shuttleName];
            const selected = prep !== null && prep !== undefined;
            const canSelect = !selected && prepCount < ship.maxPreparedShuttles;
            return (
              <div key={shuttleName} className="coi-rack-block">
                <div className="coi-rack-header" style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <input
                    type="checkbox"
                    checked={selected}
                    disabled={!selected && !canSelect}
                    onChange={() => toggleShuttle(shuttleName)}
                  />
                  <span>{shuttleName}</span>
                  {selected && prep && (
                    <select
                      value={prep.type}
                      onChange={e => setShuttleType(shuttleName, e.target.value)}
                      style={{ marginLeft: 8 }}
                    >
                      {sh.types.map(t => (
                        <option key={t} value={t}>{CONVERSION_LABELS[t] ?? t}</option>
                      ))}
                    </select>
                  )}
                </div>

                {selected && prep && prep.type === 'suicide' && (
                  <div className="coi-row" style={{ marginTop: 6 }}>
                    <label className="coi-label">
                      Energy/turn (1–3) — warhead: {prep.energyPerTurn * 6} dmg
                    </label>
                    <div className="coi-arm-options">
                      {[1, 2, 3].map(e => (
                        <label key={e} className="coi-arm-option">
                          <input
                            type="radio"
                            name={`${ship.shipName}-${shuttleName}-energy`}
                            value={e}
                            checked={prep.energyPerTurn === e}
                            onChange={() => setSuicideEnergy(shuttleName, e)}
                          />
                          {e} ({e * 6} dmg)
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                {selected && prep && prep.type === 'scatterpack' && (() => {
                  const spaceUsed = droneSpaceUsed(prep.drones, ship.availableDroneTypes);
                  const spaceLeft = 6 - spaceUsed;
                  const pool = computeDronePool(ship.droneRacks, coi.droneRackLoadouts);
                  const committed = computePackCommitments(coi.shuttlePrep);
                  return (
                    <div style={{ marginTop: 6 }}>
                      <div className="coi-rack-header">{spaceUsed.toFixed(1)} / 6 spaces loaded</div>
                      <div className="coi-rack-loadout">
                        {prep.drones.map((name, i) => {
                          const dt = ship.availableDroneTypes.find(t => t.name === name);
                          return (
                            <span key={i} className="coi-drone-chip">
                              {name} ({dt?.damage}dmg, spd {dt?.speed})
                              <button className="coi-drone-remove" onClick={() => removeScatterDrone(shuttleName, i)}>✕</button>
                            </span>
                          );
                        })}
                        {prep.drones.length === 0 && <span className="coi-note">No drones loaded</span>}
                      </div>
                      {spaceLeft > 0 && (
                        <div className="coi-drone-add-row">
                          {ship.availableDroneTypes
                            .filter(dt => {
                              const inPool = pool[dt.name] ?? 0;
                              const used   = committed[dt.name] ?? 0;
                              return dt.rack <= spaceLeft && inPool - used > 0;
                            })
                            .map(dt => {
                              const avail = (pool[dt.name] ?? 0) - (committed[dt.name] ?? 0);
                              return (
                                <button key={dt.name} className="secondary coi-drone-add-btn"
                                  onClick={() => addScatterDrone(shuttleName, dt.name)}>
                                  + {dt.name} ({dt.rack}sp) ×{avail}
                                </button>
                              );
                            })}
                          {ship.availableDroneTypes.every(dt => {
                            const avail = (pool[dt.name] ?? 0) - (committed[dt.name] ?? 0);
                            return dt.rack > spaceLeft || avail <= 0;
                          }) && <span className="coi-note">No drones available in racks</span>}
                        </div>
                      )}
                    </div>
                  );
                })()}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

export default function CoiDialog({ sides, onSubmit, onSkip, busy }: Props) {
  const [coiMap, setCoiMap] = useState<Record<string, ShipCoi>>(() => {
    const init: Record<string, ShipCoi> = {};
    for (const side of sides) {
      for (const ship of side.ships) {
        init[ship.shipName] = defaultShipCoi();
      }
    }
    return init;
  });

  function buildSubmission(): CoiSubmission {
    const sub: CoiSubmission = {};
    for (const [shipName, coi] of Object.entries(coiMap)) {
      // Convert droneRackLoadouts: Record<number, string[]> → Record<string, string[]>
      const rackLoadouts = Object.keys(coi.droneRackLoadouts).length > 0
        ? Object.fromEntries(
            Object.entries(coi.droneRackLoadouts)
              .filter(([, drones]) => drones.length > 0)
              .map(([idx, drones]) => [idx, drones])
          )
        : undefined;

      // Build specialShuttlePrep entries for selected shuttles
      const shuttlePrep: import('../api/gameApi').CoiShuttlePrepEntry[] = [];
      for (const [shuttleName, prep] of Object.entries(coi.shuttlePrep)) {
        if (!prep) continue;
        if (prep.type === 'suicide') {
          shuttlePrep.push({ shuttleName, type: 'suicide', energyPerTurn: prep.energyPerTurn });
        } else if (prep.type === 'scatterpack') {
          shuttlePrep.push({ shuttleName, type: 'scatterpack', drones: prep.drones });
        } else if (prep.type === 'wildweasel') {
          shuttlePrep.push({ shuttleName, type: 'wildweasel' });
        }
      }

      sub[shipName] = {
        extraBoardingParties: coi.extraBoardingParties,
        convertBpToCommando:  coi.convertBpToCommando,
        extraCommandoSquads:  coi.extraCommandoSquads,
        extraTBombs:          coi.extraTBombs,
        droneRackLoadouts:    rackLoadouts,
        weaponArmingModes:    Object.keys(coi.weaponArmingModes).length > 0
                              ? coi.weaponArmingModes : undefined,
        specialShuttlePrep:   shuttlePrep.length > 0 ? shuttlePrep : undefined,
      };
    }
    return sub;
  }

  const anyOverBudget = sides.some(side =>
    side.ships.some(ship => coiCost(coiMap[ship.shipName] ?? defaultShipCoi()) > ship.coiBudget)
  );

  return (
    <div className="card coi-dialog" style={{ width: '100%', maxWidth: 560 }}>
      <h3 style={{ margin: '0 0 0.5rem' }}>Commander's Options</h3>
      <p className="subtitle" style={{ marginBottom: '1rem' }}>
        Select pre-game loadout options for each ship. Budget is a percentage of BPV.
      </p>

      {sides.map(side => (
        <div key={side.faction} className="coi-side">
          <div className="coi-side-title">{side.name || side.faction}</div>
          {side.ships.map(ship => (
            <ShipCoiPanel
              key={ship.shipName}
              ship={ship}
              coi={coiMap[ship.shipName] ?? defaultShipCoi()}
              onChange={next => setCoiMap(prev => ({ ...prev, [ship.shipName]: next }))}
            />
          ))}
        </div>
      ))}

      <div className="button-row" style={{ marginTop: '1rem' }}>
        <button
          disabled={busy || anyOverBudget}
          onClick={() => onSubmit(buildSubmission())}
        >
          {busy ? 'Saving…' : 'Save & Continue'}
        </button>
        <button className="secondary" onClick={onSkip} disabled={busy}>
          Skip (defaults)
        </button>
      </div>
      {anyOverBudget && (
        <p style={{ color: '#f85149', marginTop: '0.5rem' }}>
          One or more ships exceed their COI budget.
        </p>
      )}
    </div>
  );
}
