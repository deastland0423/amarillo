import React from 'react';
import type { PendingDacChoice } from '../types/gameState';

interface Props {
  pendingChoices: PendingDacChoice[];
  myShipNames:    Set<string>;
  onSubmit: (chosenSystem: string) => void;
}

const DAC_LABELS: Record<string, string> = {
  phaser: 'Phaser',
  drone:  'Drone / ADD Rack',
  torp:   'Heavy Weapon (Torp)',
  weapon: 'Any Weapon',
  warp:   'Warp Engine',
};

const WARP_LABELS: Record<string, string> = {
  lwarp: 'Left Warp',
  cwarp: 'Center Warp',
  rwarp: 'Right Warp',
};

export const DacChoiceDialog: React.FC<Props> = ({ pendingChoices, myShipNames, onSubmit }) => {
  const myChoice = pendingChoices.find(c => myShipNames.has(c.targetShipName));
  if (!myChoice) return null;

  function label(opt: string): string {
    return WARP_LABELS[opt] ?? opt;
  }

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
          DAC Hit — {myChoice.targetShipName}
        </div>
        <div style={{ color: '#8b949e', fontSize: 13, marginBottom: '1rem' }}>
          Roll: <strong style={{ color: '#e6edf3' }}>{myChoice.roll}</strong>
          {' — '}
          <strong style={{ color: '#e6edf3' }}>{DAC_LABELS[myChoice.dacType] ?? myChoice.dacType}</strong> destroyed.
          Choose which system takes the hit:
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {myChoice.options.map(opt => (
            <button
              key={opt}
              onClick={() => onSubmit(opt)}
              style={{
                background: '#21262d',
                border: '1px solid #30363d',
                color: '#e6edf3',
                borderRadius: 6,
                padding: '8px 16px',
                fontSize: 14,
                cursor: 'pointer',
                textAlign: 'left',
              }}
              onMouseEnter={e => (e.currentTarget.style.borderColor = '#f85149')}
              onMouseLeave={e => (e.currentTarget.style.borderColor = '#30363d')}
            >
              {label(opt)}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
};