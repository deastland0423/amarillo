import React from 'react';
import type { MineRackState, PendingDacChoice, ShipObject } from '../types/gameState';
import type { WeaponChip } from './weaponCondition';
import { weaponChips, chipColour } from './weaponCondition';

interface Props {
  pendingChoices: PendingDacChoice[];
  myShipNames:    Set<string>;
  allShips:       ShipObject[];
  onSubmit: (chosenSystem: string) => void;
}

const DAC_LABELS: Record<string, string> = {
  phaser:  'Phaser',
  drone:   'Drone / ADD Rack',
  torp:    'Heavy Weapon (Torp)',
  weapon:  'Any Weapon',
  warp:    'Warp Engine',
  // Annex #7E: "Shuttle ....... Fighter, Mine Rack, ..." (M9.13) gives the row no opt-out, so a
  // minelayer soaks shuttle hits on its racks whether its owner likes it or not.
  shuttle: 'Shuttle Row (Bay Space / Mine Rack)',
  tractor: 'Tractor Beam',
  // Annex #7E: "Cargo ..... Repair, Mine Rack, ship in Starbase docking module (R1.1D), Works."
  // Named for the row rather than for cargo, because on a Base Station or the ROC the cargo box
  // is not the likeliest thing to lose and may not exist at all.
  cargo:   'Cargo Row (Cargo / Repair / Mine Rack)',
  // Annex #7E: "Excess Damage ....... Cargo, Repair, Mine Rack." (D4.40) is why the row matters
  // more than its name suggests: the ship is destroyed only once EVERY box on it is gone, so
  // these buttons are the difference between dying now and dying later.
  excess:  'Excess Damage Row (Excess / Cargo / Repair / Mine Rack)',
  // Annex #7E: "Forward Hull ... C Hull, Repair, Barracks." and the same for Aft Hull, where
  // balcony craft (J1.531) must be hit first and so are the only option while any are parked.
  fhull:   'Forward Hull Row (Hull / Repair / Barracks)',
  ahull:   'Aft Hull Row (Hull / Repair / Barracks)',
  afthull: 'Aft Hull Row (Hull / Repair / Barracks)',
};

const WARP_LABELS: Record<string, string> = {
  lwarp: 'Left Warp',
  cwarp: 'Center Warp',
  rwarp: 'Right Warp',
};

/**
 * (Annex #7E) The cargo row's own option strings, which name a KIND of box rather than one
 * identified system — unlike a weapon (by name), a bay space (by index) or a warp engine.
 *
 * A cargo box and a repair box are interchangeable within their kind, so there is nothing to
 * index; a mine rack is not, because what is loaded in it differs rack by rack, and that is why
 * `minerack:` carries an index while the other two do not.
 */
const CONVERSION_ROW_LABELS: Record<string, string> = {
  cargo:    'Cargo Box',
  repair:   'Repair Box (G17.0)',
  excess:   'Excess Damage Box',
  barracks: 'Barracks Box (G28.0)',
  fhull:    'Forward Hull Box',
  ahull:    'Aft Hull Box',
  afthull:  'Aft Hull Box',
};

/**
 * Maps a weapon's type string → short display name.
 *
 * The keys are the `type` values in the ship JSON, because a weapon's name is
 * `type + "-" + designator` (Weapon.getName). Three of these were wrong and silently fell
 * through to the raw type: there is no "Disruptor30", "FusionBeam" or "SpatterGun" in the data —
 * they are "Disruptor" and "Fusion", and the spatter gun does not exist yet. A miss is not
 * visibly broken, which is why it survived: the option still reads "Disruptor (A)".
 *
 * ScoutChannel, ESG, ADD and DroneRack are here because they are all destroyed on weapon hits
 * and so appear among the options — a channel on phaser hits (G24.17), an ESG on drone hits
 * (G23.14). PlasmaLauncher is handled separately using launcherType.
 */
const WEAPON_TYPE_LABELS: Record<string, string> = {
  'Phaser1':        'Ph-1',
  'Phaser2':        'Ph-2',
  'Phaser3':        'Ph-3',
  'PhaserG':        'Ph-G',
  'Disruptor':      'Dis',
  'Fusion':         'Fusion',
  'Photon':         'Photon',
  'Hellbore':       'HB',
  'ADD':            'ADD',
  'DroneRack':      'Rack',
  'ScoutChannel':   'Channel',
  'ESG':            'ESG',
  'FighterFusion':  'Ftr Fusion',
  'FighterHellbore':'Ftr HB',
  'FighterDisruptor':'Ftr Dis',
  'FighterPhoton':  'Ftr Photon',
  'PlasmaLauncher': 'Plasma',
};

export const DacChoiceDialog: React.FC<Props> = ({ pendingChoices, myShipNames, allShips, onSubmit }) => {
  // In solo/unassigned mode myShipNames is empty — fall back to the first pending choice.
  const myChoice = myShipNames.size > 0
    ? pendingChoices.find(c => myShipNames.has(c.targetShipName))
    : pendingChoices[0];
  if (!myChoice) return null;

  const targetShip = allShips.find(s => s.name === myChoice.targetShipName);

  function shuttleSpaceLabel(opt: string): { label: string; armed: boolean; empty: boolean } {
    // opt format: "bay:0:space:1"
    const parts = opt.split(':');
    const bayIdx   = parseInt(parts[1]);
    const spaceIdx = parseInt(parts[3]);
    const bay = targetShip?.shuttleBays[bayIdx];
    const space = bay?.spaces[spaceIdx];
    const bayNum = bayIdx + 1;
    const spaceNum = spaceIdx + 1;
    if (!space) return { label: `Bay ${bayNum}, Space ${spaceNum}`, armed: false, empty: true };
    if (space.empty)   return { label: `Bay ${bayNum}, Space ${spaceNum} — empty`, armed: false, empty: true };
    const name = space.shuttle?.name ?? 'unknown';
    return {
      label: `Bay ${bayNum}, Space ${spaceNum} — ${name}`,
      armed: space.armed,
      empty: false,
    };
  }

  interface ConversionRowInfo {
    label:  string;
    detail: string;
    /** True when the choice costs more than a box, so the button gets the warning border. */
    costly: boolean;
  }

  /**
   * (Annex #7E) Describe one candidate on a DAMAGE CONVERSION CHART row.
   *
   * The whole point of a dialog on these rows is that the kinds are not equivalent. A cargo box
   * and a repair box are plain absorption. A mine rack takes its mines with it — and (M9.13) is
   * explicit that those mines "will not detonate", so it is an expensive loss and not a lethal
   * one, worth saying because a player who thinks four NSMs are about to go off inside their own
   * hull will pick differently. An excess damage box is the countdown to the ship exploding and
   * cripples it outright (S2.41 C), which makes it the costliest box on the sheet to spend even
   * though nothing is stored in it.
   */
  function conversionRowInfo(opt: string): ConversionRowInfo {
    if (opt.startsWith('minerack:')) {
      const idx = parseInt(opt.slice('minerack:'.length), 10);
      const rack: MineRackState | undefined = targetShip?.mineRacks?.[idx];
      const label = `Mine Rack #${idx + 1}`;
      if (!rack) return { label, detail: '', costly: false };
      const parts: string[] = [];
      if (rack.largeMines > 0)      parts.push(`${rack.largeMines} NSM`);
      if (rack.smallMines > 0)      parts.push(`${rack.smallMines} T-bomb`);
      if (rack.smallDummyMines > 0) parts.push(`${rack.smallDummyMines} dummy`);
      const loaded = rack.largeMines + rack.smallMines + rack.smallDummyMines;
      return {
        label,
        detail: loaded === 0
          ? 'empty — nothing lost with the box'
          : `${parts.join(', ')} destroyed, no detonation (M9.13)`,
        costly: loaded > 0,
      };
    }
    const left = opt === 'cargo'    ? targetShip?.availableCargo
               : opt === 'repair'   ? targetShip?.availableRepair
               : opt === 'barracks' ? targetShip?.availableBarracks
               : opt === 'fhull'    ? targetShip?.availableFhull
               : (opt === 'ahull' || opt === 'afthull') ? targetShip?.availableAhull
               :                      targetShip?.availableExcess;
    const max  = opt === 'cargo'    ? targetShip?.maxCargo
               : opt === 'repair'   ? targetShip?.maxRepair
               : opt === 'barracks' ? targetShip?.maxBarracks
               : opt === 'fhull'    ? targetShip?.maxFhull
               : (opt === 'ahull' || opt === 'afthull') ? targetShip?.maxAhull
               :                      targetShip?.maxExcess;
    // `!= null` and not `!== undefined`: Jackson writes an absent value as `"field": null`
    // rather than omitting the key, and `null !== undefined` is true.
    const count = left != null && max != null ? `${left} of ${max} left` : '';
    return {
      label:  CONVERSION_ROW_LABELS[opt] ?? opt,
      detail: opt === 'excess'
        ? [count, 'spending one cripples the ship (S2.41 C)'].filter(Boolean).join(' — ')
        : opt === 'barracks'
        ? [count, barracksLoss()].filter(Boolean).join(' — ')
        : count,
      costly: opt === 'excess' || (opt === 'barracks' && barracksPartiesPerBox() > 0),
    };
  }

  /**
   * (G28.33) How many boarding parties go with one barracks box: "Any boarding parties in a
   * destroyed barracks box are destroyed."
   *
   * <p>Mirrors the server's even-spread default rather than computing a rule of its own. The
   * server is authoritative; this only has to tell the player what the button costs.
   */
  function barracksPartiesPerBox(): number {
    const boxes = targetShip?.availableBarracks ?? 0;
    const parties = targetShip?.boardingParties ?? 0;
    if (boxes <= 0 || parties <= 0) return 0;
    return Math.floor(Math.min(parties, boxes * 10) / boxes);
  }

  function barracksLoss(): string {
    const n = barracksPartiesPerBox();
    if (n <= 0) return 'empty';
    return `${n} boarding part${n === 1 ? 'y' : 'ies'} lost with it (G28.33)`;
  }

  interface WeaponInfo {
    displayName: string;
    arcLabel:    string;
    chips:       WeaponChip[];
    /** True when something real dies with this weapon — an armed warhead, a loaded rack. */
    costly:      boolean;
  }

  function weaponInfo(opt: string): WeaponInfo | null {
    if (!targetShip) return null;
    const ws = targetShip.weapons.find(w => w.name === opt);
    if (!ws) return null;

    const lastDash = opt.lastIndexOf('-');
    const typeName  = lastDash >= 0 ? opt.slice(0, lastDash) : opt;
    const designator = lastDash >= 0 ? opt.slice(lastDash + 1) : '';

    let shortType = WEAPON_TYPE_LABELS[typeName] ?? typeName;
    if (typeName === 'PlasmaLauncher' && ws.launcherType) {
      shortType = `Plasma-${ws.launcherType}`;
    }

    // A drone rack rides in BOTH lists: `weapons` carries its arc and readiness, `droneRacks`
    // alone says what is on it. Matched on name, which is the same string in both.
    const rack = targetShip.droneRacks?.find(r => r.name === opt) ?? null;
    const chips = weaponChips(ws, rack);

    const displayName = designator ? `${shortType} (${designator})` : shortType;
    return {
      displayName,
      arcLabel: ws.arcLabel ?? '',
      chips,
      costly: chips.some(c => c.tone === 'valuable'),
    };
  }

  const isChainReaction = myChoice.dacType === 'shuttle' && myChoice.bayIndex >= 0;

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 200,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        background: '#161b22',
        border: '1px solid #f85149',
        borderRadius: 10,
        padding: '1.5rem',
        minWidth: 380,
        maxWidth: 520,
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
      }}>
        <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f85149', marginBottom: '0.5rem' }}>
          {isChainReaction ? '⚠ Chain Reaction — ' : 'DAC Hit — '}
          {myChoice.targetShipName}
        </div>
        <div style={{ color: '#8b949e', fontSize: 13, marginBottom: '1rem' }}>
          {myChoice.roll > 0 && <>Roll: <strong style={{ color: '#e6edf3' }}>{myChoice.roll}</strong>{' — '}</>}
          <strong style={{ color: '#e6edf3' }}>{DAC_LABELS[myChoice.dacType] ?? myChoice.dacType}</strong>
          {isChainReaction
            ? <> hit in Bay {myChoice.bayIndex + 1}. Choose which space takes the chain reaction:</>
            : <> destroyed. Choose which system takes the hit:</>}
        </div>

        {myChoice.dacType === 'shuttle' && (
          <div style={{ color: '#8b949e', fontSize: 12, marginBottom: '0.75rem' }}>
            Tip: score the hit on an empty or unarmed space to stop the chain reaction.
          </div>
        )}

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {myChoice.options.map(opt => {
            const isShuttle = opt.startsWith('bay:');
            const isWarp    = opt in WARP_LABELS;
            const isConversionRow = opt in CONVERSION_ROW_LABELS || opt.startsWith('minerack:');
            const sInfo = isShuttle ? shuttleSpaceLabel(opt) : null;
            const cInfo = isConversionRow ? conversionRowInfo(opt) : null;
            const wInfo = (!isShuttle && !isWarp && !isConversionRow) ? weaponInfo(opt) : null;
            // One warning colour for every kind of "you are about to lose something": an armed
            // shuttle in its space, a weapon holding a warhead or a load, a loaded mine rack.
            const armedWarning = (sInfo?.armed ?? false) || (wInfo?.costly ?? false)
              || (cInfo?.costly ?? false);

            return (
              <button
                key={opt}
                onClick={() => onSubmit(opt)}
                style={{
                  background: '#21262d',
                  border: `1px solid ${armedWarning ? '#f0883e' : '#30363d'}`,
                  color: '#e6edf3',
                  borderRadius: 6,
                  padding: '8px 16px',
                  fontSize: 14,
                  cursor: 'pointer',
                  textAlign: 'left',
                  width: '100%',
                }}
                onMouseEnter={e => (e.currentTarget.style.borderColor = '#f85149')}
                onMouseLeave={e => (e.currentTarget.style.borderColor = armedWarning ? '#f0883e' : '#30363d')}
              >
                {/* Shuttle space option */}
                {isShuttle && (
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <span>{sInfo!.label}</span>
                    {sInfo!.armed && (
                      <span style={{ color: '#f0883e', fontSize: 12, marginLeft: 8 }}>ARMED ⚠</span>
                    )}
                  </div>
                )}

                {/* Warp engine option */}
                {isWarp && (
                  <span>{WARP_LABELS[opt]}</span>
                )}

                {/* Weapon option — name, arc, and what state it is in */}
                {wInfo && (
                  <div>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: 6, flexWrap: 'wrap' }}>
                      <span style={{ fontWeight: 500 }}>{wInfo.displayName}</span>
                      {wInfo.arcLabel && (
                        <span style={{ color: '#8b949e', fontSize: 11 }}>[{wInfo.arcLabel}]</span>
                      )}
                    </div>
                    {/* The reason this dialog exists: which of these is cheapest to lose. */}
                    {wInfo.chips.length > 0 && (
                      <div style={{
                        display: 'flex', flexWrap: 'wrap', gap: '2px 8px',
                        marginTop: 3, fontSize: 11, lineHeight: 1.4,
                      }}>
                        {wInfo.chips.map((chip, i) => (
                          <span key={i} title={chip.title} style={{ color: chipColour(chip) }}>
                            {chip.text}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Conversion row option (Annex #7E) — which box, and what goes with it */}
                {cInfo && (
                  <div>
                    <div style={{ fontWeight: 500 }}>{cInfo.label}</div>
                    {cInfo.detail && (
                      <div style={{
                        color: cInfo.costly ? '#f0883e' : '#8b949e',
                        fontSize: 11, marginTop: 3,
                      }}>
                        {cInfo.detail}
                      </div>
                    )}
                  </div>
                )}

                {/* Fallback for unrecognised options */}
                {!isShuttle && !isWarp && !isConversionRow && !wInfo && (
                  <span>{opt}</span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};
