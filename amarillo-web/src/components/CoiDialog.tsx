import { Fragment, useState, useEffect } from 'react';
import type { CoiSideData, CoiShipData, CoiSubmission, CoiDroneType, CoiDroneRack,
  CoiDroneModule, CoiDroneAddition, CoiRackPreset, Cartel, DroneAvailability }
  from '../api/gameApi';
import { gameApi } from '../api/gameApi';
import { Stepper } from './Stepper';
import { tierFor, quotaOf, describeCartel } from './cartelQuota';
import { defaultShipCoi, seedShipCoi } from './coiShipState';
import type { ArmMode, ShipCoi, ShuttlePrep } from './coiShipState';
import { seatAll } from './optionMountSeating';
import { budgetBasisLabel, defaultAmmoLabel, droneChipLabel, presetLabel }
  from './coiLabels';
import { withRackLoad, withoutRack, withPreset } from './coiRackLoad';
import { coiCost } from './coiTotal';
import { capRows, dogfightCapRows, shipsOverCaps } from './droneAvailability';
import type { CapRow } from './droneAvailability';
import {
  isModified, parseDroneName, formatDroneName, payloadUsed, rackSpacesOf,
  describeDrone, moduleRefusal, additionRefusal,
} from './droneComposition';
import type { DroneComposition } from './droneComposition';
import type { Tier, Quota } from './cartelQuota';

// G15.44's tiers and quota live in ./cartelQuota, shared with the fleet builder: a loadout is
// chosen wherever the ship is BOUGHT (G15.415), which is both places, and two copies of this
// arithmetic would be two answers waiting to disagree with the server's.
type SideQuota = Quota;

/** Fleet cartel quota (G15.44): % of ALL option mounts, round to nearest, independent pools. */
function computeSideQuota(
  side:    CoiSideData,
  cartel:  Cartel | undefined,
  optionSel: (shipName: string) => Record<string, string>,
): SideQuota {
  let total = 0;
  const tiers: Tier[] = [];
  for (const ship of side.ships) {
    const mounts = ship.optionMounts ?? [];
    total += mounts.length;
    const sel = optionSel(ship.shipName);
    for (const m of mounts) {
      const chosen = sel[m.designator];
      if (!chosen) continue;
      tiers.push(tierFor(m.legalOptions.find(o => o.name === chosen)?.empires, cartel));
    }
  }
  return quotaOf(total, tiers, cartel);
}

interface Props {
  sides:       CoiSideData[];
  playerToken: string;
  onSubmit:    (submission: CoiSubmission) => Promise<void>;
  onSkip:      () => void;
  busy:        boolean;
  /**
   * Choices already made for these ships, to start from (S8.12).
   *
   * Absent before a battle, where every ship starts unbought. Present in the fleet builder, where
   * reopening the dialog has to show what the fleet already carries rather than silently offering
   * to replace it with nothing.
   */
  initial?:    CoiSubmission;
  /**
   * The drone caps as the server sees the CURRENT draft (FD10.622/FD10.632), by ship name.
   *
   * Optional, and absent in the pre-battle lobby: the figures are a fleet-builder feature,
   * because that is the caller that refuses a loadout over them. Where nothing asks, nothing is
   * shown and the dialog behaves exactly as it did.
   */
  availability?: Record<string, DroneAvailability>;
  /**
   * Called with the draft on every edit, so a caller that wants figures can go and get them.
   *
   * The dialog does not know how to price its own draft against the caps and should not learn:
   * FD10.641's per-space tier bill, FD10.66's half-space sharing, FD10.643's type-G exception and
   * FD10.622's Klingon clause are four rules deep, and a browser copy of them would be a second
   * engine with no test able to tell it had drifted. So the view reports what the player chose
   * and renders the verdict it is handed.
   */
  onDraft?: (draft: CoiSubmission) => void;
  /**
   * What the button that leaves without submitting is called.
   *
   * Before a battle it is "Skip (defaults)", and that is the truth there: nothing is sent and
   * the server fits every ship out as standard. In the fleet builder it sends nothing either,
   * but nothing is DEFAULTED — the purchases already applied to the fleet stay exactly as they
   * were and only the unsaved edits on screen are dropped. Calling that "skip" read as an offer
   * to replace the fleet's loadout with defaults, which is the one thing it does not do.
   */
  skipLabel?:  string;
}


/** Tooltip text for a preset button, kept out of the JSX so quoting stays simple. */
const PRESET_COST_TITLE = 'Load this rack — ';
const PRESET_COST_TAIL  = " BPV of this ship's Commander's Options (S3.2)";
const PRESET_FREE_TITLE = "Load this rack — nothing against this ship's Commander's Options";
const PRESET_DOT        = ' · ';

/**
 * Rack space used, to one decimal only when it needs one.
 * <p>
 * It genuinely can be fractional — an ADD round and a type-VI drone each take half a space
 * (FD3.70) — so the figure cannot simply be rounded. But "0.0 / 6" for an empty rack is noise,
 * and most loadouts are whole numbers.
 */
function spacesUsedLabel(used: number): string {
  return Number.isInteger(used) ? String(used) : used.toFixed(1);
}

/**
 * Rack spaces a list of loaded drones fills (FD3.0).
 *
 * Takes the whole ship rather than just the type list because a drone's name can now carry a
 * payload (FD10.2), and reading the footprint off a composed name needs the catalogues to take
 * the name apart — and, for a type-III given extended range, the server's figure for the
 * frame it has become (FD10.24).
 */
function droneSpaceUsed(drones: string[], ship: CoiShipData): number {
  const modules   = ship.availableDroneModules ?? [];
  const additions = ship.availableDroneAdditions ?? [];
  return drones.reduce((sum, name) => sum
    + rackSpacesOf(parseDroneName(name, modules, additions), ship.availableDroneTypes), 0);
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
  availability,
}: {
  ship:     CoiShipData;
  coi:      ShipCoi;
  onChange: (next: ShipCoi) => void;
  /** This ship's standing against the drone caps, when a caller is asking the server for it. */
  availability?: DroneAvailability;
}) {
  const cost     = coiCost(coi, ship);
  const overBudget = cost > ship.coiBudget;
  // FD10.4's catalogue, sent by the server for this scenario's year. Absent from a server that
  // predates payload modules, in which case the drone picker keeps its two axes and no pencil
  // appears — rather than a pencil that opens a panel with nothing in it.
  const dModules:   CoiDroneModule[]   = ship.availableDroneModules ?? [];
  const dAdditions: CoiDroneAddition[] = ship.availableDroneAdditions ?? [];
  const canEditPayload = dModules.length > 0 || dAdditions.length > 0;
  // Which loaded drone has its payload editor open, as "rackIndex:position". One at a time:
  // the editor is a panel under the rack it belongs to, and two open at once would separate a
  // drone from its own controls.
  const [editingDrone, setEditingDrone] = useState<string | null>(null);

  // Which racks have their drone picker open, collapsed by default. The picker is a labelled row
  // of buttons per drone family, and a Kzinti player bringing three ships of four racks had
  // twelve of them stacked open -- a great deal of scrolling to reach anything below. The chips,
  // the presets and the price stay visible while it is shut; only building drone-by-drone needs
  // the room, and FD2.41's mix-as-you-elect is the minority case.
  const [openRacks, setOpenRacks] = useState<number[]>([]);
  function toggleRack(index: number) {
    setOpenRacks(prev => prev.includes(index)
      ? prev.filter(i => i !== index)
      : [...prev, index]);
  }
  const hasWs3Heavy = ship.weaponStatus === 3 && ship.heavyWeapons.length > 0;
  // S4.32: two free overload points per photon tube, poolable across the ship's tubes.
  const photonTubes      = ship.heavyWeapons.filter(w => w.type === 'Photon');
  const freeOverloadPool = photonTubes.length * 2;
  const freeOverloadUsed = Object.values(coi.photonOverload ?? {}).reduce((a, b) => a + b, 0);
  const hasSpecialShuttles = (ship.convertibleShuttles?.length ?? 0) > 0 && ship.maxPreparedShuttles > 0;

  const CONVERSION_LABELS: Record<string, string> = {
    suicide:     'Suicide Shuttle',
    scatterpack: 'Scatterpack',
    wildweasel:  'Wild Weasel',
  };

  function setNum(field: keyof ShipCoi, val: number) {
    onChange({ ...coi, [field]: Math.max(0, val) });
  }

  function setArmMode(designator: string, mode: ArmMode) {
    onChange({ ...coi, weaponArmingModes: { ...coi.weaponArmingModes, [designator]: mode } });
  }

  function setOptionMount(designator: string, name: string) {
    const next = { ...coi.optionMounts };
    if (name) next[designator] = name;
    else delete next[designator];
    // The arc belonged to the weapon that was there (R8.R3), so it goes with it rather than
    // silently applying to whatever is chosen next.
    const arcs = { ...coi.optionMountArcs };
    delete arcs[designator];
    onChange({ ...coi, optionMounts: next, optionMountArcs: arcs });
  }

  /** R8.R3's wing arc for one mount — "the taste of the Orion player", so nothing derives it. */
  function setOptionMountArc(designator: string, choice: string) {
    const arcs = { ...coi.optionMountArcs };
    if (choice) arcs[designator] = choice;
    else delete arcs[designator];
    onChange({ ...coi, optionMountArcs: arcs });
  }

  // Running BPV delta from the chosen option mounts (Annex #8B; informational).
  /** What this ship's mounts hold once the choices are seated (G15.4). */
  const seat = seatAll(
    ship.adjacentMountRuns ?? [],
    coi.optionMounts,
    (designator, name) => (ship.optionMounts ?? [])
      .find(m => m.designator === designator)?.legalOptions.find(o => o.name === name),
    (designator, name) => (ship.optionMounts ?? [])
      .some(m => m.designator === designator && m.legalOptions.some(o => o.name === name)));

  const optionBpvDelta = (ship.optionMounts ?? []).reduce((sum, m) => {
    const sel = coi.optionMounts[m.designator];
    if (!sel) return sum;
    return sum + (m.legalOptions.find(o => o.name === sel)?.cost ?? 0);
  }, 0);

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

  function setPhotonOverload(designator: string, points: number) {
    onChange({ ...coi, photonOverload: { ...coi.photonOverload, [designator]: points } });
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
        <span
          className="coi-budget"
          style={{ color: overBudget ? '#f85149' : '#56d364' }}
          title={budgetBasisLabel(ship)}
        >
          {cost.toFixed(1)} / {ship.coiBudget.toFixed(1)} BPV
        </span>
      </div>

      {/* Boarding parties */}
      <div className="coi-row">
        <label className="coi-label">Extra boarding parties (0.5 ea, max 10)</label>
        <Stepper value={coi.extraBoardingParties} min={0} max={10}
          onChange={n => setNum('extraBoardingParties', n)} />
      </div>

      {/* Convert to commandos */}
      {ship.allowCommandos && (
        <>
          <div className="coi-row">
            <label className="coi-label">Convert BPs → commandos (0.5 ea, max 2)</label>
            <Stepper value={coi.convertBpToCommando} min={0} max={2}
              onChange={n => setNum('convertBpToCommando', n)} />
          </div>
          <div className="coi-row">
            <label className="coi-label">Extra commando squads (1.0 ea, max 2)</label>
            <Stepper value={coi.extraCommandoSquads} min={0} max={2}
              onChange={n => setNum('extraCommandoSquads', n)} />
          </div>
        </>
      )}

      {/* T-bombs */}
      {ship.allowTBombs && ship.maxTBombs > 0 && (
        <div className="coi-row">
          <label className="coi-label">T-bombs (4.0 ea, +1 free dummy each, max {ship.maxTBombs})</label>
          <Stepper value={coi.extraTBombs} min={0} max={ship.maxTBombs}
            onChange={n => setNum('extraTBombs', n)} />
        </div>
      )}

      {/* Extra deck crews (S3.2/J4.816) — offered only to a fully capable carrier, which
          is why the server sends a maximum of 0 for everyone else. What they buy is
          turnaround: two crews reload one fighter a turn (J4.833). */}
      {(ship.maxExtraDeckCrews ?? 0) > 0 && (
        <div className="coi-row">
          <label className="coi-label">
            Deck crews ({(ship.extraDeckCrewCost ?? 0.5).toFixed(1)} ea, max{' '}
            {ship.maxExtraDeckCrews}; has {ship.deckCrews})
          </label>
          <Stepper value={coi.extraDeckCrews} min={0} max={ship.maxExtraDeckCrews ?? 0}
            onChange={n => setNum('extraDeckCrews', n)} />
        </div>
      )}

      {/* J4.62/J4.621: a casual carrier's fighter facilities. The escort has the apparatus to
          service a carrier's fighters and none of its own, and J4.621 says the carrier's fighters
          decide what the apparatus is for — so the player names the model here, where the
          scenario year is settled and the list can be the actual fighters the line fields.
          Free: J4.621 gives a casual carrier its drones and chaff with the ship. */}
      {ship.fighterFacilities && ship.fighterFacilities.models.length > 0 && (
        <div className="coi-row">
          <label className="coi-label">
            Fighter facilities ({ship.fighterFacilities.count} box
            {ship.fighterFacilities.count === 1 ? '' : 'es'}) — J4.621
          </label>
          <select
            value={coi.fighterFacilityType}
            onChange={e => onChange({ ...coi, fighterFacilityType: e.target.value })}
          >
            <option value="">
              {(() => {
                const def = ship.fighterFacilities!.models
                  .find(m => m.type === ship.fighterFacilities!.defaultType);
                return def ? `Standard (${def.name})` : 'Standard';
              })()}
            </option>
            {ship.fighterFacilities.models.map(m => (
              <option key={m.type} value={m.type}>{m.name}</option>
            ))}
          </select>
        </div>
      )}

      {/* Fighter readiness (S4.10–S4.12). Free — the weapon status already granted it; this
          only says who gets it. Absent at WS-3, where S4.13 arms the whole squadron, and
          pointless on a squadron of one type, so it appears only where there is a choice. */}
      {(ship.fighters?.length ?? 0) > 1 && (ship.maxArmedFighters ?? 0) !== (ship.fighters?.length ?? 0)
        && new Set((ship.fighters ?? []).map(f => f.type)).size > 1 && (() => {
        const max = ship.maxArmedFighters ?? 0;
        const chosen = coi.armedFighters;
        const budgeted = max < 0;   // WS-2: the deck crews' budget decides, not a count
        return (
          <div className="coi-section">
            <div className="coi-section-title">
              Fighters Ready {budgeted
                ? '(WS-2: worked in this order, as far as the crews reach)'
                : `(pick up to ${max})`}
            </div>
            <div className="coi-note">
              Which fighters are armed when the scenario opens. Free — it is what the weapon
              status already allows; unchosen fighters keep their charges in their boxes.
            </div>
            {(ship.fighters ?? []).map(f => {
              const picked = chosen.includes(f.name);
              const full = !budgeted && !picked && chosen.length >= max;
              return (
                <label key={f.name} className="coi-label coi-check">
                  <input type="checkbox" checked={picked} disabled={full}
                    onChange={e => onChange({
                      ...coi,
                      armedFighters: e.target.checked
                        ? [...coi.armedFighters, f.name]
                        : coi.armedFighters.filter(n => n !== f.name),
                    })} />
                  {f.name.replace(`${ship.shipName}-`, '')}
                  <span className="coi-note"> — {f.designation ?? f.type}</span>
                </label>
              );
            })}
          </div>
        );
      })()}

      {/* Drone rack loadouts */}
      {ship.droneRacks.length > 0 && ship.availableDroneTypes.length > 0 && (
        <div className="coi-section">
          <div className="coi-section-title">Drone Rack Loadouts</div>
          {availability && <DroneAvailabilityGauge figures={availability} />}
          {ship.droneRacks.map(rack => {
            // The rack you are BUILDING, which starts empty. Touch nothing and the ship
            // keeps the loadout it arrived with; add anything and what you built is what
            // it carries. Showing the default contents here instead was worse: it made the
            // common case — load the drones I want — begin by deleting four chips.
            const loadout = coi.droneRackLoadouts[rack.index] ?? [];
            // FD3.70: drones and anti-drones share the one magazine, so the budget counts
            // both. The server adds them up the same way and refuses the loadout otherwise.
            const antiDrones = coi.antiDroneLoadouts[rack.index] ?? 0;
            const adSpace    = rack.antiDroneSpace ?? 0.5;
            const used    = droneSpaceUsed(loadout, ship)
                          + antiDrones * adSpace;
            const remaining = rack.spaces - used;
            // Whether the player has started building this rack. An untouched one keeps what
            // the ship came with (S2.11), so the builder's own figures say nothing about it yet.
            const touched = loadout.length > 0 || antiDrones > 0;
            const expanded = openRacks.includes(rack.index);

            function setAntiDrones(n: number) {
              const next = Math.max(0, n);
              onChange({ ...coi, antiDroneLoadouts:
                { ...coi.antiDroneLoadouts, [rack.index]: next } });
            }

            /** Put a whole load in the builder at once, for the buttons below the rack. */
            function setRack(next: string[]) {
              onChange({ ...coi,
                droneRackLoadouts: withRackLoad(coi.droneRackLoadouts, rack.index, next) });
            }
            /**
             * Forget this rack, which is NOT the same as emptying it -- see coiRackLoad. An
             * absent rack index keeps the drones the ship arrived with; a rack set to [] sails
             * with none at all.
             */
            /**
             * Load a ready-made set (FD2.41), drones and FD3.70's anti-drones in one move. The
             * drones and the price both came from core; nothing is decided here.
             */
            function applyPreset(preset: CoiRackPreset) {
              onChange({ ...coi, ...withPreset(coi, rack.index, preset) });
              setEditingDrone(null);
            }

            function clearRack() {
              onChange({ ...coi,
                droneRackLoadouts: withoutRack(coi.droneRackLoadouts, rack.index) });
              setEditingDrone(null);
            }

            function addDrone(typeName: string) {
              const dt = ship.availableDroneTypes.find(t => t.name === typeName);
              if (!dt || dt.rack > remaining) return;
              const next = [...loadout, typeName];
              onChange({ ...coi, droneRackLoadouts: { ...coi.droneRackLoadouts, [rack.index]: next } });
            }
            function removeDrone(idx: number) {
              const next = loadout.filter((_, i) => i !== idx);
              onChange({ ...coi, droneRackLoadouts: { ...coi.droneRackLoadouts, [rack.index]: next } });
              setEditingDrone(null);
            }
            /** Replace one loaded drone with a modified version of itself (FD10.2). */
            function setDrone(idx: number, comp: DroneComposition) {
              const next = loadout.map((n, i) => i === idx ? formatDroneName(comp) : n);
              onChange({ ...coi, droneRackLoadouts: { ...coi.droneRackLoadouts, [rack.index]: next } });
            }

            return (
              <div key={rack.index} className="coi-rack-block">
                <div className="coi-rack-header">
                  {/* Every drone rack in the ship data is already designated "Rack N", so
                      prefixing unconditionally gave "Rack Rack 1". Conditional rather than
                      simply dropped, in case a rack is ever designated like a phaser ("A"). */}
                  {rack.designator.toLowerCase().startsWith('rack') ? '' : 'Rack '}{rack.designator}
                  {/* The fraction is what the BUILDER has filled, so it only means something
                      once there is a build. An untouched rack showed "0 / 4 spaces" directly
                      above "Carrying 4 x TypeIF", which reads as an empty magazine; its
                      capacity alone is the honest thing to show, and the line below says what
                      is in it. */}
                  {' — '}{touched ? `${spacesUsedLabel(used)} / ${rack.spaces}` : rack.spaces}
                  {' spaces'}
                </div>
                <div className="coi-rack-loadout">
                  {loadout.map((name, i) => {
                    const comp = parseDroneName(name, dModules, dAdditions);
                    const dt = ship.availableDroneTypes.find(t => t.name === comp.basis);
                    const key = `${rack.index}:${i}`;
                    // FD10.21: only a drone on a frame that CHOOSES its payload has anything to
                    // edit. A type-VI "cannot be modified" and gets no pencil, rather than one
                    // that opens an empty panel.
                    const editable = canEditPayload && (dt?.modular ?? false);
                    return (
                      <span key={i}
                        className={'coi-drone-chip'
                          + (editingDrone === key ? ' coi-drone-chip-open' : '')}>
                        {isModified(comp)
                          ? describeDrone(comp, dModules, dAdditions)
                          : droneChipLabel(name, ship.availableDroneTypes)}
                        {editable && (
                          <button className="coi-drone-edit"
                            title="Choose this drone's payload (FD10.4)"
                            onClick={() => setEditingDrone(
                              editingDrone === key ? null : key)}>✎</button>
                        )}
                        <button className="coi-drone-remove" onClick={() => removeDrone(i)}>✕</button>
                      </span>
                    );
                  })}
                  {loadout.length === 0 && antiDrones === 0 && (
                    <span className="coi-note">
                      Carrying {defaultAmmoLabel(rack.defaultAmmo, ship.availableDroneTypes)}
                    </span>
                  )}
                  {/* FD3.70's rounds have no chip of their own -- they are a count, not a list --
                      and their +/- control lives in the part of the rack a preset does not open.
                      So a magazine loaded with ADDs says so here, or a player who clicked an
                      anti-drone preset would see an apparently empty rack. */}
                  {antiDrones > 0 && (
                    <span className="coi-note">{antiDrones} × anti-drone</span>
                  )}
                </div>
                {/* The way out of an empty builder for a player who wants to change ONE drone.
                    Without it the only route to "the standard load but with an ECM drone in it"
                    was to add four chips by hand first, which is the sort of thing that makes
                    people skip the screen. Loading the set changes nothing by itself: it is the
                    same drones, now editable. */}
                <div className="coi-rack-actions">
                  {loadout.length === 0
                    ? rack.defaultAmmo.length > 0 && (
                      <button className="secondary"
                        title="Copy what the rack already holds into the builder, to change it"
                        onClick={() => setRack([...rack.defaultAmmo])}>
                        Edit this load
                      </button>
                    ) : (
                      <button className="secondary"
                        title="Go back to the drones the ship sails with (S2.11)"
                        onClick={clearRack}>
                        Back to standard
                      </button>
                    )}
                  {/* Ready-made loads, from core: which ones are legal here and what each costs
                      were both decided on the server (DroneRackPresets), and only General
                      Availability drones are ever offered because FD10.62's caps are a property
                      of the whole ship. The price shown is this rack's own bill (S3.2); the
                      counter at the foot of the dialog is what the ship owes altogether. */}
                  {(rack.presets ?? []).map((p, i) => (
                    <button key={i} className="secondary"
                      title={p.surcharge > 0
                        ? PRESET_COST_TITLE + p.surcharge + PRESET_COST_TAIL
                        : PRESET_FREE_TITLE}
                      onClick={() => applyPreset(p)}>
                      {presetLabel(p.drones, p.antiDrones)}
                      {p.surcharge > 0 ? PRESET_DOT + p.surcharge : ''}
                    </button>
                  ))}
                  <button className="secondary"
                    title={expanded
                      ? 'Hide the drone picker'
                      : 'Choose this rack one drone at a time (FD2.41)'}
                    onClick={() => toggleRack(rack.index)}>
                    {expanded ? 'Done' : 'Build…'}
                  </button>
                </div>
                {/* FD10.4's payload, for the one drone whose pencil is open. Under the rack
                    rather than inside the chip: the chips are a wrapping row and a panel in the
                    middle of one would push the rest of the loadout around as it opened. */}
                {(() => {
                  if (editingDrone == null) return null;
                  const [rackKey, posKey] = editingDrone.split(':');
                  if (Number(rackKey) !== rack.index) return null;
                  const pos = Number(posKey);
                  const name = loadout[pos];
                  if (name == null) return null;
                  const comp = parseDroneName(name, dModules, dAdditions);
                  const dt = ship.availableDroneTypes.find(t => t.name === comp.basis);
                  if (!dt) return null;
                  // What this ONE drone may take, which the server decided by building each
                  // candidate: FD10.4 bars a two-space module from a one-space frame, FD10.23
                  // bars ATG from a type-III, FD2.222 bars extended range from a dogfight drone.
                  const legalModules = dModules.filter(
                    m => (dt.legalModules ?? []).includes(m.name));
                  const legalAdditions = dAdditions.filter(
                    a => (dt.legalAdditions ?? []).includes(a.name));
                  // FD10.24 is the one modification that changes the footprint, so the space it
                  // would take has to be affordable before it can be offered.
                  const own = rackSpacesOf(comp, ship.availableDroneTypes);
                  return (
                    <DronePayloadEditor
                      drone={dt}
                      comp={comp}
                      modules={legalModules}
                      additions={legalAdditions}
                      submunitionSpeeds={ship.availableSubmunitionSpeeds ?? []}
                      rackSpaceFree={remaining + own}
                      onChange={next => setDrone(pos, next)}
                      onClose={() => setEditingDrone(null)}
                    />
                  );
                })()}
                {expanded && remaining > 0 && (
                  <DronePicker
                    types={ship.availableDroneTypes.filter(dt => dt.rack <= remaining
                      && (rack.canLoadTypeVI || !dt.dogfight))}
                    onAdd={addDrone}
                  />
                )}
                {/* FD3.70: only a type-G has the anti-drone targeting system, and its
                    loading "must always be planned" — this is where that planning
                    happens, since nothing reloads an ADD into it in play (E5.74). */}
                {expanded && rack.canLoadAntiDrones && (
                  <div className="coi-drone-add-row" style={{ alignItems: 'center' }}>
                    <span className="coi-note" style={{ marginRight: 6 }}>
                      Anti-drones ({adSpace}sp each)
                    </span>
                    <button className="secondary coi-drone-add-btn"
                            disabled={antiDrones <= 0}
                            onClick={() => setAntiDrones(antiDrones - 1)}>&minus;</button>
                    <span style={{ minWidth: 18, textAlign: 'center' }}>{antiDrones}</span>
                    <button className="secondary coi-drone-add-btn"
                            disabled={adSpace > remaining}
                            onClick={() => setAntiDrones(antiDrones + 1)}>+</button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* Orion option mounts (G15.4) */}
      {(ship.optionMounts?.length ?? 0) > 0 && (
        <div className="coi-section">
          <div className="coi-section-title">
            Option Mounts (G15.4)
            {optionBpvDelta !== 0 && (
              <span className="coi-note">
                {' '}— effective BPV {optionBpvDelta > 0 ? '+' : ''}{optionBpvDelta.toFixed(2)}
              </span>
            )}
          </div>
          {ship.optionMounts!.map(m => {
            const selected = coi.optionMounts[m.designator] ?? '';
            // The far half of a two-mount option chosen in a neighbouring mount (Annex #8B's
            // asterisk). Same rule and same module as the fleet builder's picker, so the two
            // places a loadout is chosen cannot disagree about what a choice occupies.
            const held = seat.get(m.designator);
            const continuation = held != null && !held.primary;
            return (
              <div key={m.designator} className="coi-row">
                <label className="coi-label">
                  {m.designator} · {m.position === 'CENTERLINE' ? 'centerline' : 'wing'} ({m.arcs.join(', ')})
                </label>
                {m.currentOption != null ? (
                  <span className="coi-note">{m.currentOption} (fixed)</span>
                ) : continuation ? (
                  <span className="coi-note">
                    {held.option} — second mount, chosen in {held.chosenAt}
                  </span>
                ) : (
                  <select value={selected}
                    onChange={e => setOptionMount(m.designator, e.target.value)}>
                    <option value="">(empty)</option>
                    {m.legalOptions.map(o => (
                      <option key={o.name} value={o.name}>
                        {o.name}{o.cost !== 0 ? ` (${o.cost > 0 ? '+' : ''}${o.cost} BPV)` : ''}
                        {(o.mountsRequired ?? 1) > 1 ? ' — 2 mounts' : ''}
                      </option>
                    ))}
                  </select>
                )}
                {/* R8.R3's arc choice, where the rule gives one. Same two labels as the fleet
                    builder's, from core, so neither view carries a copy of the rule. */}
                {m.currentOption == null && !continuation && (() => {
                  const picked = m.legalOptions.find(o => o.name === selected);
                  if ((picked?.arcChoices?.length ?? 0) !== 2) return null;
                  return (
                    <select value={coi.optionMountArcs[m.designator] ?? 'FORWARD'}
                      title="R8.R3: a wing disruptor or fusion beam may bear either arc"
                      onChange={e => setOptionMountArc(m.designator, e.target.value)}>
                      <option value="FORWARD">{picked!.arcChoices![0]}</option>
                      <option value="OUTBOARD">{picked!.arcChoices![1]}</option>
                    </select>
                  );
                })()}
              </div>
            );
          })}
        </div>
      )}

      {/* Heavy weapon arming modes (WS-3 only) */}
      {hasWs3Heavy && (
        <div className="coi-section">
          <div className="coi-section-title">Weapon Arming (WS-III — weapons start fully armed)</div>
          {photonTubes.length > 0 && (
            <div className="coi-note">
              Free overload energy (S4.32): {freeOverloadUsed} of {freeOverloadPool} allocated
              {freeOverloadUsed < freeOverloadPool && ` — ${freeOverloadPool - freeOverloadUsed} left`}
              . Two points per tube, spread as you like; a tube that takes any is committed to
              being an overload — maximum range 8, and 2 energy a turn to hold instead of 1.
            </div>
          )}
          {ship.heavyWeapons.map(w => {
            const mode = coi.weaponArmingModes[w.designator] ?? 'STANDARD';
            const isPhoton = w.type === 'Photon';
            const ovl = coi.photonOverload?.[w.designator] ?? 0;
            const canAdd = freeOverloadUsed < freeOverloadPool && ovl < 4 && mode !== 'SPECIAL';
            return (
              <div key={w.designator} className="coi-row">
                <label className="coi-label">{w.type} {w.designator}</label>
                <div className="coi-arm-options">
                  {(w.isPlasma
                    ? [['STANDARD', 'Armed'], ['ROLLING', 'Rolling']] as [ArmMode, string][]
                    : isPhoton
                    ? [['STANDARD', 'Standard'], ['SPECIAL', 'Proximity']] as [ArmMode, string][]
                    : [['STANDARD', 'Standard'], ['SPECIAL', 'Proximity'], ['OVERLOAD', 'Overload']] as [ArmMode, string][]
                  ).map(([m, label]) => (
                    <label key={m} className="coi-arm-option">
                      <input type="radio" name={`${ship.shipName}-${w.designator}`}
                        value={m} checked={mode === m}
                        onChange={() => {
                          setArmMode(w.designator, m);
                          if (m === 'SPECIAL' && ovl > 0) setPhotonOverload(w.designator, 0);
                        }} />
                      {label}
                    </label>
                  ))}
                  {/* The overload control below is not a plain Stepper: its value is worded
                      rather than a bare number ("no ovld" / "+2 ovld"), which Stepper has no room
                      for. So it borrows the stepper's CLASSES instead — the same 24px buttons and
                      tabular value — and looks and behaves like every other numeric control
                      without losing the wording. The unclassed buttons it had before were also
                      subject to the global `button { flex: 1 }`, which stretched them. */}
                  {isPhoton && (
                    <span className="coi-arm-option ea-stepper">
                      <button type="button" className="ea-step-btn" disabled={ovl <= 0}
                        onClick={() => setPhotonOverload(w.designator, Math.max(0, ovl - 1))}>−</button>
                      <span className="ea-step-value" style={{ minWidth: 62 }}>
                        {ovl > 0 ? `+${ovl} ovld` : 'no ovld'}
                      </span>
                      <button type="button" className="ea-step-btn" disabled={!canAdd}
                        onClick={() => setPhotonOverload(w.designator, ovl + 1)}>+</button>
                      <span style={{ opacity: 0.7 }}>
                        {mode === 'SPECIAL' ? '4 damage' : `${(4 + ovl) > 4 ? (4 + ovl) * 2 : 8} damage`}
                      </span>
                    </span>
                  )}
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
                  const spaceUsed = droneSpaceUsed(prep.drones, ship);
                  const spaceLeft = 6 - spaceUsed;
                  const pool = computeDronePool(ship.droneRacks, coi.droneRackLoadouts);
                  const committed = computePackCommitments(coi.shuttlePrep);
                  return (
                    <div style={{ marginTop: 6 }}>
                      <div className="coi-rack-header">{spacesUsedLabel(spaceUsed)} / 6 spaces loaded</div>
                      <div className="coi-rack-loadout">
                        {prep.drones.map((name, i) => {
                          return (
                            <span key={i} className="coi-drone-chip">
                              {droneChipLabel(name, ship.availableDroneTypes)}
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

/**
 * The drone loadout picker, grouped by family with speed as the choice inside it.
 * <p>
 * The flat list of `+ TypeIM (1sp)` buttons it replaces was unreadable to anyone who did not
 * already know the naming, and the naming is the whole difficulty: the suffix carries SPEED and
 * nothing else — none is 8 or 12, M is 20, F is 32 — while the numeral carries the family. So a
 * Type-I and a Type-IM are the same drone at two speeds, and nothing on screen said so.
 *
 * Family-first rather than speed-first, deliberately. The family is the decision — how much
 * damage, how much of the rack, whether it steers itself — and speed is a cheap secondary axis
 * that repeats identically across families. Grouping by speed would scatter the families and put
 * a 12-damage, a 24-damage and a half-space drone side by side as if they were alternatives.
 * Three buttons in a row differing only in speed also TEACHES the naming without explaining it.
 *
 * Space and damage sit in the heading because core guarantees they are constant within a family
 * (DroneFamilyTest pins it). Endurance sits on each button because it is NOT: a Type-II is a
 * standard drone with 64 impulses instead of 96.
 *
 * The family comes from the server. Grouping on the name here would be the view deciding a rules
 * question out of string prefixes — and would get it wrong at once, since "TypeIV" starts with
 * "TypeI".
 */
/**
 * How much of this ship's drone load may be hard to come by, and how much of that is spent
 * (FD10.622, FD10.632).
 *
 * <h2>Why a gauge and not just a refusal</h2>
 * The fleet builder refuses a force over these caps. A limit that only speaks when it is already
 * breached is the shape of the cartel-quota bug: the player meets a wall with nothing to read on
 * the way to it, and on a four-space rack the Limited allowance is ZERO, so the first special
 * drone is the one that fails. Saying "0 of 0" before they click is the whole job.
 *
 * Both tiers show because FD10.64 nests them: a Limited drone spends Restricted allowance too, so
 * one drone can move both numbers and only showing the pair explains why.
 *
 * Every figure here came from the server, which has already applied the draft loadout to the real
 * ship. Nothing on this side computes a tier.
 */
function DroneAvailabilityGauge({ figures }: { figures: DroneAvailability }) {
  // FD10.644: dogfight drones are "counted separately from all other drones", so they get a
  // line of their own rather than numbers folded into the first one. Empty on every hull but
  // the D6C, which is why the line is conditional instead of always drawn at 0/0.
  const dogfight = dogfightCapRows(figures);
  // A hull with no drone rack has nothing to gauge. "0/0 of 0 spaces" is noise.
  if (figures.countedSpaces <= 0 && dogfight.length === 0) return null;
  return (
    <>
      {figures.countedSpaces > 0 && (
        <CapGauge rows={capRows(figures)} total={figures.countedSpaces}
                  relaxed={figures.relaxed}
                  fromFighters={figures.fighterSpaces ?? 0} />
      )}
      {dogfight.length > 0 && (
        <CapGauge rows={dogfight} total={figures.dogfightSpaces ?? 0}
                  relaxed={figures.relaxed} pool="Dogfight" basisRule="FD10.644" />
      )}
    </>
  );
}

/**
 * One pool's two ceilings, as a row of chips.
 *
 * The spaces are the hull's rack CAPACITY, not the drones loaded in it (FD10.641 as read in
 * {@code DroneAvailabilityLimit}) — which is why the denominator does not move as a player
 * adds and removes drones, and the "of N spaces" is worth printing beside the caps rather than
 * left to be inferred from them.
 */
function CapGauge({ rows, total, relaxed, pool, basisRule, fromFighters = 0 }: {
  rows:          CapRow[];
  total:         number;
  relaxed:       boolean;
  pool?:         string;
  basisRule?:    string;
  /** Of `total`, how much is the squadron's rails (FD10.641). Zero on a ship with no fighters. */
  fromFighters?: number;
}) {
  const spaces = spacesUsedLabel(total);
  const basis = pool ? `${pool.toLowerCase()} warhead spaces` : 'warhead spaces';
  // Where the spaces came from, for the ship whose basis is mostly its squadron. `> 0` rather
  // than truthiness so a server that sends no such figure says nothing instead of "0 of them".
  const split = fromFighters > 0
    ? ` — ${spacesUsedLabel(total - fromFighters)} in the hull's racks and`
      + ` ${spacesUsedLabel(fromFighters)} on fighter rails`
    : ``;
  return (
    <div className="coi-note coi-availability">
      {pool && <span className="coi-availability-pool">{pool}</span>}
      {rows.map(r => (
        <span key={`${pool ?? ''}${r.rule}`}
              className={r.over ? 'coi-availability-over' : undefined}
              title={`${r.rule}: ${r.percent}% of the ${spaces} ${basis} aboard`
                     + ` (${basisRule ?? 'FD10.641'})`
                     + split
                     + (relaxed
                          ? ` — this hull is on FD10.622's exception list` : ``)}>
          {r.label} {spacesUsedLabel(r.used)}/{r.allowed}
        </span>
      ))}
      <span>of {spaces} spaces</span>
    </div>
  );
}

function DronePicker({ types, onAdd }: {
  types: CoiDroneType[];
  onAdd: (name: string) => void;
}) {
  if (types.length === 0) return null;

  // Server order within a family is speed order, so no sorting is needed or wanted.
  //
  // A server that predates the family fields sends neither, and every drone then falls into one
  // nameless group — which renders as a single wrapping row of buttons, i.e. exactly the flat
  // list this component replaced, with no clue as to why. Falling back to the drone's own name
  // makes a stale server look wrong rather than look like a layout bug.
  const families: { key: string; label: string; members: CoiDroneType[] }[] = [];
  for (const dt of types) {
    const key = dt.family ?? `?${dt.name}`;
    let group = families.find(f => f.key === key);
    if (!group) {
      group = { key, label: dt.familyLabel ?? dt.name, members: [] };
      families.push(group);
    }
    group.members.push(dt);
  }

  const spaces = (n: number) => (n === 0.5 ? '\u00bd space' : n === 1 ? '1 space' : `${n} spaces`);

  return (
    <div className="coi-drone-picker">
      {families.map(f => {
        const sample = f.members[0];
        return (
          <Fragment key={f.key}>
            {/* The label sits BESIDE the buttons, not above them, so each family really is one
                row. Stacked, the four speed buttons were wide enough to wrap in a narrow ship
                panel and the whole thing read as a flat list again — which was the complaint. */}
            <span className="coi-drone-family-head">
              <strong>{f.label}</strong>
              <span className="coi-drone-family-stats">
                {/* FD2.54: a dogfight drone's damage depends on the target, so one number would
                    be a lie either way — "2/8 dmg" says which is which, ship first because that
                    is the surprise. Both figures come from the server. */}
                {spaces(sample.rack)}
                {' · '}
                {sample.damageVsShip !== sample.damage
                  ? `${sample.damageVsShip}/${sample.damage} dmg`
                  : `${sample.damage} dmg`}
                {sample.selfGuiding && ' · guides itself inside 8 hexes'}
              </span>
            </span>
            <span className="coi-drone-speeds">
              {f.members.map(dt => (
                <button
                  key={dt.name}
                  className="secondary coi-drone-add-btn"
                  onClick={() => onAdd(dt.name)}
                  title={`${dt.name} — speed ${dt.speed}, ${spaces(dt.rack)}, `
                    + `${dt.endurance} impulses of endurance; `
                    + (dt.damageVsShip !== dt.damage
                        ? `${dt.damage} damage to a fighter but only ${dt.damageVsShip} `
                          + 'to a ship (FD2.54)'
                        : `${dt.damage} damage`)
                    + (dt.selfGuiding
                        ? '; guides itself once within 8 hexes of its target, and needs a '
                          + 'control channel until then (FD5.12)'
                        : '')}
                >
                  {/* "Speed 8", not a bare "8": the number means nothing on its own to a player
                      who does not already know the drone naming, and not knowing it is the whole
                      reason this picker was rebuilt. */}
                  Speed {dt.speed}
                  {/* The type name, so a player still learns WHICH drone they took even though
                      they chose it by speed. Endurance is in the tooltip: it varies within a
                      family (a type-II has 64 impulses, not 96) but is too much to put on a
                      button this size. */}
                  <span className="coi-drone-btn-sub">{dt.name.replace('Type', '')}</span>
                </button>
              ))}
            </span>
          </Fragment>
        );
      })}
    </div>
  );
}

/**
 * One loaded drone's payload (FD10.2) — the third axis of the picker.
 *
 * The numeral in a drone's name carries its family and the suffix carries its speed, and that is
 * all a loadout could say until core learned to name a composed drone. Armor, ECM, probe,
 * swordfish and multi-warhead modules were built, priced and capped long before anyone could
 * ORDER one.
 *
 * What this deliberately does NOT show: the hull, the warhead, the speed or the price of what is
 * being built. Every one of those is derived from the composition by rules this view does not
 * hold and must not — FD12.21 adds hull per armor module, FD10.41 combines the warheads into
 * one explosion, FD12.132's chart takes a fast one-space drone from 32 to 12 for a full external
 * module, and FD2.22 charges the difference from the type-I-slows already in the ship's BPV. A
 * number here would be a second answer waiting to disagree with the server's.
 *
 * So it shows what was ticked, the space it fills, and how hard each part is to come by. The
 * ship's own budget line already reports what the server says it all costs.
 */
function DronePayloadEditor({
  drone, comp, modules, additions, submunitionSpeeds, rackSpaceFree, onChange, onClose,
}: {
  drone:     CoiDroneType;
  comp:      DroneComposition;
  modules:   CoiDroneModule[];
  additions: CoiDroneAddition[];
  submunitionSpeeds: { name: string; speed: number; availability: string }[];
  /** Rack spaces this drone may grow into, its own footprint included (FD3.0). */
  rackSpaceFree: number;
  onChange: (next: DroneComposition) => void;
  onClose:  () => void;
}) {
  const capacity = drone.payloadSpaces ?? 0;
  const used     = payloadUsed(comp, modules);

  // FD10.431: a multi-warhead module carries type-VI drones instead of a warhead, and FD8.24
  // buys them an engine of their own. Nothing to choose until one is aboard.
  const carriesSubmunitions = comp.modules.some(
    n => (modules.find(m => m.name === n)?.submunitions ?? 0) > 0);

  function countOf(name: string): number {
    return comp.modules.filter(n => n === name).length;
  }
  function addModule(m: CoiDroneModule) {
    onChange({ ...comp, modules: [...comp.modules, m.name] });
  }
  function dropModule(name: string) {
    const at = comp.modules.lastIndexOf(name);
    if (at < 0) return;
    const next = comp.modules.filter((_, i) => i !== at);
    // FD8.21: a submunition speed belongs to a drone that carries submunitions. Taking the last
    // MW module off takes the engines with it, rather than leaving a choice with nothing to
    // apply to — which core refuses outright.
    const stillHasMw = next.some(
      n => (modules.find(m => m.name === n)?.submunitions ?? 0) > 0);
    onChange({ ...comp, modules: next,
      submunitionSpeed: stillHasMw ? comp.submunitionSpeed : null });
  }
  function toggleAddition(a: CoiDroneAddition, on: boolean) {
    let next = comp.additions.filter(n => n !== a.name);
    if (on) {
      // FD12.131: "No more than one external armor module can be added to any one drone." So
      // ticking one unticks the other rather than being refused on submission.
      if (a.externalArmor)
        next = next.filter(
          n => !(additions.find(x => x.name === n)?.externalArmor ?? false));
      next = [...next, a.name];
    }
    onChange({ ...comp, additions: next });
  }

  const tier = (t: string) => t === 'GENERAL' ? '' : ` · ${t.toLowerCase()}`;
  const sp = (n: number) => n === 0.5 ? '½sp' : `${n}sp`;

  return (
    <div className="coi-payload-editor">
      <div className="coi-payload-head">
        <strong>{comp.basis}</strong>
        <span className="coi-note">
          {' — payload '}{spacesUsedLabel(used)} / {capacity}
          {capacity === 1 ? ' space' : ' spaces'}
        </span>
        <button className="coi-drone-remove" onClick={onClose} title="Done">{'✕'}</button>
      </div>

      {modules.length > 0 && (
        <>
          <div className="coi-note">
            Payload (FD10.4). A module you name REPLACES what the drone came with, so a drone
            with nothing ticked here carries its standard warhead.
          </div>
          {modules.map(m => {
            const n = countOf(m.name);
            // Every reason the rules have for not letting this module on THIS drone, in one
            // tested place: FD10.443, FD12.131, FD10.4 and FD10.402. They live in
            // moduleRefusal now because one of them was missing from here and nothing on this
            // side could be asked the question.
            const refusal = moduleRefusal(comp, m, modules, additions, capacity);
            return (
              <div key={m.name} className="coi-payload-row">
                <button className="secondary coi-drone-add-btn"
                        disabled={n <= 0}
                        onClick={() => dropModule(m.name)}>{'−'}</button>
                <span className="coi-payload-count">{n}</span>
                <button className="secondary coi-drone-add-btn"
                        disabled={refusal != null}
                        title={refusal ?? undefined}
                        onClick={() => addModule(m)}>+</button>
                <span className="coi-payload-label">{m.label}</span>
                <span className="coi-note">
                  {sp(m.spaces)}
                  {m.strength > 0 && ` · ${m.strength} dmg`}
                  {m.hullBonus > 0 && ` · +${m.hullBonus} hull`}
                  {m.submunitions > 0 && ` · ${m.submunitions} type-VIs`}
                  {m.requiresForward && ' · forward only'}
                  {tier(m.availability)}
                </span>
              </div>
            );
          })}
        </>
      )}

      {additions.length > 0 && (
        <>
          <div className="coi-note" style={{ marginTop: '0.3rem' }}>
            Frame (FD10.53). Bolted on rather than carried, so it fills no payload space.
          </div>
          {additions.map(a => {
            const on = comp.additions.includes(a.name);
            // FD10.24: extended range turns a type-III into a two-space drone. Shown on the
            // label as well as checked for room, because it is the one modification that costs
            // a player RACK space and nothing else on the row would say so.
            const grows = a.name === 'EXTENDED_RANGE'
              && (drone.rackWithExtendedRange ?? drone.rack) > drone.rack;
            const refusal = additionRefusal(comp, a, modules,
              drone.rack, rackSpaceFree, drone.rackWithExtendedRange);
            return (
              <label key={a.name} className="coi-label coi-check">
                <input type="checkbox" checked={on}
                  disabled={refusal != null}
                  title={refusal ?? undefined}
                  onChange={e => toggleAddition(a, e.target.checked)} />
                {a.label}
                <span className="coi-note">
                  {a.hullBonus > 0 && ` — +${a.hullBonus} hull`}
                  {a.externalArmor && ' · costs speed (FD12.132)'}
                  {grows && ' · two rack spaces (FD10.24)'}
                  {tier(a.availability)}
                </span>
              </label>
            );
          })}
        </>
      )}

      {carriesSubmunitions && submunitionSpeeds.length > 0 && (
        <div className="coi-row" style={{ marginTop: '0.3rem' }}>
          <label className="coi-label">Submunition speed (FD8.11)</label>
          <select value={comp.submunitionSpeed ?? ''}
            onChange={e => onChange({ ...comp, submunitionSpeed: e.target.value || null })}>
            {/* FD8.45: nothing bought means they fly at a type-VI's own baseline, which FD2.227
                puts at 12 rather than 8. That is the free option, not a missing one. */}
            <option value="">Standard</option>
            {submunitionSpeeds.map(p => (
              <option key={p.name} value={p.name}>
                Speed {p.speed}{p.availability !== 'GENERAL'
                  ? ` (${p.availability.toLowerCase()})` : ''}
              </option>
            ))}
          </select>
        </div>
      )}
      {carriesSubmunitions && (
        <div className="coi-note">
          FD8.24: every submunition buys its own engine on top of the drone's, which is what
          makes a multi-warhead drone expensive.
        </div>
      )}
    </div>
  );
}

export default function CoiDialog({ sides, onSubmit, onSkip, busy, initial, skipLabel,
                                    availability, onDraft }: Props) {
  const [coiMap, setCoiMap] = useState<Record<string, ShipCoi>>(() => {
    const init: Record<string, ShipCoi> = {};
    for (const side of sides) {
      for (const ship of side.ships) {
        init[ship.shipName] = seedShipCoi(initial?.[ship.shipName]);
      }
    }
    return init;
  });

  // Cartel table (G15.44), fetched once — lets us tier options and show the quota.
  const [cartels, setCartels] = useState<Cartel[]>([]);
  useEffect(() => { gameApi.listCartels().then(setCartels).catch(() => {}); }, []);
  const cartelsByName: Record<string, Cartel> = Object.fromEntries(cartels.map(c => [c.name, c]));

  // Chosen cartel per side (keyed by faction), seeded from any scenario-fixed value.
  const [cartelChoice, setCartelChoice] = useState<Record<string, string>>(() => {
    const init: Record<string, string> = {};
    for (const side of sides) init[side.faction] = side.cartel ?? '';
    return init;
  });

  const optionSelFor = (shipName: string) => coiMap[shipName]?.optionMounts ?? {};
  const sideQuota = (side: CoiSideData) =>
    computeSideQuota(side, cartelsByName[cartelChoice[side.faction] ?? ''], optionSelFor);

  function buildSubmission(): CoiSubmission {
    const sub: CoiSubmission = {};
    // Ship → its fleet's cartel, echoed onto each ship's request.
    const shipCartel: Record<string, string> = {};
    for (const side of sides) {
      const c = cartelChoice[side.faction];
      if (c) for (const ship of side.ships) shipCartel[ship.shipName] = c;
    }
    for (const [shipName, coi] of Object.entries(coiMap)) {
      // Convert droneRackLoadouts: Record<number, string[]> → Record<string, string[]>
      // Every rack the player TOUCHED, empty lists included. A rack emptied to make room
      // for anti-drones has to arrive as "no drones" — dropping empty entries here made it
      // arrive as "unchanged", and the server kept the drones that were in the way.
      const rackLoadouts = Object.keys(coi.droneRackLoadouts).length > 0
        ? { ...coi.droneRackLoadouts }
        : undefined;

      // Anti-drones travel on their own map (FD3.70): they are not a drone type here or
      // in core, and a rack may carry them with no drones at all.
      const antiDrones = Object.entries(coi.antiDroneLoadouts ?? {})
        .filter(([, n]) => n > 0);
      const antiDroneLoadouts = antiDrones.length > 0
        ? Object.fromEntries(antiDrones) : undefined;

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
        extraDeckCrews:       coi.extraDeckCrews,
        // Omitted rather than sent empty: the server reads absent as "keep the standard fighter".
        ...(coi.fighterFacilityType
              ? { fighterFacilityType: coi.fighterFacilityType } : {}),
        armedFighters:        coi.armedFighters,
        droneRackLoadouts:    rackLoadouts,
        antiDroneLoadouts,
        weaponArmingModes:    Object.keys(coi.weaponArmingModes).length > 0
                              ? coi.weaponArmingModes : undefined,
        photonOverload:       Object.values(coi.photonOverload ?? {}).some(v => v > 0)
                              ? coi.photonOverload : undefined,
        specialShuttlePrep:   shuttlePrep.length > 0 ? shuttlePrep : undefined,
        optionMounts:         Object.keys(coi.optionMounts).length > 0
                              ? coi.optionMounts : undefined,
        optionMountArcs:      Object.keys(coi.optionMountArcs).length > 0
                              ? coi.optionMountArcs : undefined,
        cartel:               shipCartel[shipName],
      };
    }
    return sub;
  }

  // Tell the caller what is on screen, so it can ask the server what the caps make of it.
  //
  // Keyed on the state the submission is built FROM rather than on the submission itself: a
  // fresh object every render would make this fire forever. The caller debounces the request;
  // this only has to be honest about when the draft changed.
  useEffect(() => {
    if (onDraft) onDraft(buildSubmission());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [coiMap, cartelChoice]);

  const anyOverBudget = sides.some(side =>
    side.ships.some(ship =>
      coiCost(coiMap[ship.shipName] ?? defaultShipCoi(), ship) > ship.coiBudget)
  );
  const anyOverQuota = sides.some(side => {
    const q = sideQuota(side);
    return q.hasMounts && cartelChoice[side.faction] && !q.within;
  });
  // FD10.622/FD10.632, on the same footing as the budget and the cartel quota: a loadout the
  // fleet validator will refuse cannot be saved from here either. Only when a caller supplied
  // the figures -- the lobby does not, where ScenarioLoader notes the breach and plays on.
  const overAvailability = shipsOverCaps(availability);

  return (
    <div className="card coi-dialog" style={{ width: '100%', maxWidth: 560 }}>
      <h3 style={{ margin: '0 0 0.5rem' }}>Commander's Options</h3>
      <p className="subtitle" style={{ marginBottom: '1rem' }}>
        Select pre-game loadout options for each ship. Budget is a percentage of BPV.
      </p>

      {sides.map(side => {
        const q = sideQuota(side);
        const chosenCartel = cartelChoice[side.faction] ?? '';
        return (
          <div key={side.faction} className="coi-side">
            <div className="coi-side-title">{side.name || side.faction}</div>

            {q.hasMounts && (
              <div className="coi-section">
                <div className="coi-section-title">Orion Cartel (G15.44)</div>
                <div className="coi-row">
                  <label className="coi-label">Cartel</label>
                  {side.cartelPinned ? (
                    <span className="coi-note">{side.cartel} (fixed)</span>
                  ) : (
                    <select value={chosenCartel}
                      onChange={e => setCartelChoice(prev => ({ ...prev, [side.faction]: e.target.value }))}>
                      <option value="">(none — no quota)</option>
                      {cartels.map(c => (
                        <option key={c.name} value={c.name}>{describeCartel(c)}</option>
                      ))}
                    </select>
                  )}
                </div>
                {chosenCartel && (
                  <div className="coi-note" style={{ color: q.within ? undefined : '#f85149' }}>
                    {/* A cartel with no operating zone has no such pool; the Cluster's four home
                        empires and empty zone are G15.442, not an omission. */}
                    {cartelsByName[chosenCartel]?.operatingZone
                        .some(e => !cartelsByName[chosenCartel].home.includes(e)) && (
                      <>Operating {q.op}/{q.opCap} · </>
                    )}
                    Outside {q.outUsed}/{q.outCap}
                    {' '}— of {q.total} option mounts (home &amp; universal unlimited)
                  </div>
                )}
              </div>
            )}

            {side.ships.map(ship => (
              <ShipCoiPanel
                key={ship.shipName}
                ship={ship}
                coi={coiMap[ship.shipName] ?? defaultShipCoi()}
                availability={availability?.[ship.shipName]}
                onChange={next => setCoiMap(prev => ({ ...prev, [ship.shipName]: next }))}
              />
            ))}
          </div>
        );
      })}

      <div className="button-row" style={{ marginTop: '1rem' }}>
        <button
          disabled={busy || anyOverBudget || anyOverQuota || overAvailability.length > 0}
          onClick={() => onSubmit(buildSubmission())}
        >
          {busy ? 'Saving…' : 'Save & Continue'}
        </button>
        <button className="secondary" onClick={onSkip} disabled={busy}>
          {skipLabel ?? 'Skip (defaults)'}
        </button>
      </div>
      {anyOverBudget && (
        <p style={{ color: '#f85149', marginTop: '0.5rem' }}>
          One or more ships exceed their COI budget.
        </p>
      )}
      {anyOverQuota && (
        <p style={{ color: '#f85149', marginTop: '0.5rem' }}>
          A fleet exceeds its cartel's weapon-access quota (G15.44).
        </p>
      )}
      {overAvailability.length > 0 && (
        <p style={{ color: '#f85149', marginTop: '0.5rem' }}>
          {/* Named, because the gauge sits inside a ship's own panel and a fleet of six has
              six of them further up the page than this message is. */}
          Over the drone availability caps (FD10.622/FD10.632):{' '}
          {overAvailability.join(', ')}.
        </p>
      )}
    </div>
  );
}
