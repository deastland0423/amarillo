import React from 'react';
import type { PendingDacChoice, ShipObject } from '../types/gameState';

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
  shuttle: 'Shuttle Bay Space',
};

const WARP_LABELS: Record<string, string> = {
  lwarp: 'Left Warp',
  cwarp: 'Center Warp',
  rwarp: 'Right Warp',
};

// Maps Java weapon class name prefix → short display name.
// PlasmaLauncher is handled separately using launcherType.
const WEAPON_TYPE_LABELS: Record<string, string> = {
  'Phaser1':        'Ph-1',
  'Phaser2':        'Ph-2',
  'Phaser3':        'Ph-3',
  'Disruptor30':    'Dis-30',
  'FusionBeam':     'Fusion',
  'Photon':         'Photon',
  'Hellbore':       'HB',
  'SpatterGun':     'Spatter',
  'FighterFusion':  'Ftr Fusion',
  'FighterHellbore':'Ftr HB',
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

  function weaponInfo(opt: string): { displayName: string; arcLabel: string; fired: boolean } | null {
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

    const displayName = designator ? `${shortType} (${designator})` : shortType;
    return {
      displayName,
      arcLabel: ws.arcLabel ?? '',
      fired: ws.shotsThisTurn > 0,
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
            const sInfo = isShuttle ? shuttleSpaceLabel(opt) : null;
            const wInfo = (!isShuttle && !isWarp) ? weaponInfo(opt) : null;
            const armedWarning = sInfo?.armed ?? false;

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
                    {armedWarning && (
                      <span style={{ color: '#f0883e', fontSize: 12, marginLeft: 8 }}>ARMED ⚠</span>
                    )}
                  </div>
                )}

                {/* Warp engine option */}
                {isWarp && (
                  <span>{WARP_LABELS[opt]}</span>
                )}

                {/* Weapon option — friendly name + arc + fired status */}
                {wInfo && (
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontWeight: 500 }}>{wInfo.displayName}</span>
                      {wInfo.fired && (
                        <span style={{ color: '#8b949e', fontSize: 11, marginLeft: 8, fontStyle: 'italic' }}>fired</span>
                      )}
                    </div>
                    {wInfo.arcLabel && (
                      <div style={{ color: '#8b949e', fontSize: 11, marginTop: 2 }}>{wInfo.arcLabel}</div>
                    )}
                  </div>
                )}

                {/* Fallback for unrecognised options */}
                {!isShuttle && !isWarp && !wInfo && (
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
