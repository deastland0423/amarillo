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

export const DacChoiceDialog: React.FC<Props> = ({ pendingChoices, myShipNames, allShips, onSubmit }) => {
  const myChoice = pendingChoices.find(c => myShipNames.has(c.targetShipName));
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

  function label(opt: string): string {
    if (opt.startsWith('bay:')) return shuttleSpaceLabel(opt).label;
    return WARP_LABELS[opt] ?? opt;
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
            const info = isShuttle ? shuttleSpaceLabel(opt) : null;
            const armedWarning = info?.armed;
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
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
                onMouseEnter={e => (e.currentTarget.style.borderColor = '#f85149')}
                onMouseLeave={e => (e.currentTarget.style.borderColor = armedWarning ? '#f0883e' : '#30363d')}
              >
                <span>{label(opt)}</span>
                {armedWarning && (
                  <span style={{ color: '#f0883e', fontSize: 12, marginLeft: 8 }}>ARMED ⚠</span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};
