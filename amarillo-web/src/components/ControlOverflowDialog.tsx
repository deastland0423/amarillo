import React from 'react';
import type { PendingControlOverflow, SeekerChoice } from '../types/gameState';

interface Props {
  pendingOverflows: PendingControlOverflow[];
  myShipNames:     Set<string>;
  onRelease:       (seekerName: string) => void;
  onTransfer:      (seekerName: string, toShipName: string) => void;
}

export const ControlOverflowDialog: React.FC<Props> = ({
  pendingOverflows, myShipNames, onRelease, onTransfer,
}) => {
  const mine = pendingOverflows.find(o => myShipNames.has(o.shipName));
  if (!mine) return null;

  const remaining = mine.overLimitCount;

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 200,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        background: '#161b22',
        border: '1px solid #f0883e',
        borderRadius: 10,
        padding: '1.5rem',
        minWidth: 400,
        maxWidth: 560,
        maxHeight: '80vh',
        overflowY: 'auto',
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
      }}>
        <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f0883e', marginBottom: '0.5rem' }}>
          Control Overflow — {mine.shipName}
        </div>
        <div style={{ color: '#8b949e', fontSize: 13, marginBottom: '1rem' }}>
          Over control limit by <strong style={{ color: '#e6edf3' }}>{remaining}</strong>.
          Release or transfer a seeker:
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {mine.seekers.map((sc: SeekerChoice) => (
            <div key={sc.name} style={{
              background: '#21262d',
              border: '1px solid #30363d',
              borderRadius: 8,
              padding: '10px 12px',
            }}>
              <div style={{ fontWeight: 600, color: '#e6edf3', marginBottom: 4 }}>
                {sc.label}
                {sc.targetName && (
                  <span style={{ fontWeight: 400, color: '#8b949e', marginLeft: 8 }}>
                    → {sc.targetName}
                  </span>
                )}
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                <button
                  onClick={() => onRelease(sc.name)}
                  style={{
                    background: '#6e1c1c', border: '1px solid #f85149',
                    color: '#e6edf3', borderRadius: 6, padding: '4px 12px',
                    fontSize: 12, cursor: 'pointer',
                  }}
                  onMouseEnter={e => (e.currentTarget.style.background = '#8b2020')}
                  onMouseLeave={e => (e.currentTarget.style.background = '#6e1c1c')}
                >
                  Release
                </button>
                {sc.transferOptions.map(ship => (
                  <button
                    key={ship}
                    onClick={() => onTransfer(sc.name, ship)}
                    style={{
                      background: '#21262d', border: '1px solid #30363d',
                      color: '#e6edf3', borderRadius: 6, padding: '4px 12px',
                      fontSize: 12, cursor: 'pointer',
                    }}
                    onMouseEnter={e => (e.currentTarget.style.borderColor = '#58a6ff')}
                    onMouseLeave={e => (e.currentTarget.style.borderColor = '#30363d')}
                  >
                    → {ship}
                  </button>
                ))}
                {sc.transferOptions.length === 0 && (
                  <span style={{ fontSize: 12, color: '#6e7681', alignSelf: 'center' }}>
                    No allies can take control
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
