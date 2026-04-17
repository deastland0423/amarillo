import { useState } from 'react';
import type { CoiSideData, CoiShipData, CoiSubmission, CoiDroneType } from '../api/gameApi';

interface Props {
  sides:       CoiSideData[];
  playerToken: string;
  onSubmit:    (submission: CoiSubmission) => Promise<void>;
  onSkip:      () => void;
  busy:        boolean;
}

type ArmMode = 'STANDARD' | 'OVERLOAD' | 'SPECIAL';

interface ShipCoi {
  extraBoardingParties: number;
  convertBpToCommando:  number;
  extraCommandoSquads:  number;
  extraTBombs:          number;
  weaponArmingModes:    Record<string, ArmMode>;
  droneRackLoadouts:    Record<number, string[]>;   // rackIndex → drone type names
}

function defaultShipCoi(): ShipCoi {
  return {
    extraBoardingParties: 0,
    convertBpToCommando:  0,
    extraCommandoSquads:  0,
    extraTBombs:          0,
    weaponArmingModes:    {},
    droneRackLoadouts:    {},
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

  function setNum(field: keyof ShipCoi, val: number) {
    onChange({ ...coi, [field]: Math.max(0, val) });
  }

  function setArmMode(designator: string, mode: ArmMode) {
    onChange({ ...coi, weaponArmingModes: { ...coi.weaponArmingModes, [designator]: mode } });
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
                      .filter(dt => dt.rack <= remaining)
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
                  {(['STANDARD', 'SPECIAL', 'OVERLOAD'] as ArmMode[]).map(m => (
                    <label key={m} className="coi-arm-option">
                      <input type="radio" name={`${ship.shipName}-${w.designator}`}
                        value={m} checked={mode === m}
                        onChange={() => setArmMode(w.designator, m)} />
                      {m === 'STANDARD' ? 'Standard' : m === 'SPECIAL' ? 'Proximity' : 'Overload'}
                    </label>
                  ))}
                </div>
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

      sub[shipName] = {
        extraBoardingParties: coi.extraBoardingParties,
        convertBpToCommando:  coi.convertBpToCommando,
        extraCommandoSquads:  coi.extraCommandoSquads,
        extraTBombs:          coi.extraTBombs,
        droneRackLoadouts:    rackLoadouts,
        weaponArmingModes:    Object.keys(coi.weaponArmingModes).length > 0
                              ? coi.weaponArmingModes : undefined,
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
