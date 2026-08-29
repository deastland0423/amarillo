import React from 'react';
import type { PendingAttractChoice } from '../types/gameState';

interface Props {
  choice:   PendingAttractChoice;
  onSubmit: (attracted: boolean) => void;
}

/**
 * An enemy scout has reached out to a shuttle of yours that nobody has identified as a seeking
 * weapon (G24.235). You answer — and you are allowed to lie. Saying it is not attracted admits
 * it is manned or on a ballistic course; claiming it is attracted keeps the disguise up, at the
 * price of having to fly it at the scout as a seeking weapon would.
 */
export const AttractChoiceDialog: React.FC<Props> = ({ choice, onSubmit }) => {
  const VIOLET = '#c9a0f0';

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 200,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        background: '#161b22',
        border: `1px solid ${VIOLET}`,
        borderRadius: 10,
        padding: '1.5rem',
        minWidth: 400,
        maxWidth: 540,
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
      }}>
        <div style={{ fontSize: '1.1rem', fontWeight: 700, color: VIOLET, marginBottom: '0.5rem' }}>
          Attraction Attempt — {choice.shuttleName}
        </div>
        <div style={{ color: '#8b949e', fontSize: 13, marginBottom: '1rem' }}>
          {choice.scoutName} is using scout channel {choice.channelDesignator} to attract seeking
          weapons (G24.23). {choice.shuttleName} has not been identified, so the answer is yours
          to give — and yours to lie about (G24.235).
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <button
            onClick={() => onSubmit(false)}
            style={{
              background: '#21262d', border: '1px solid #30363d', color: '#e6edf3',
              borderRadius: 6, padding: '8px 16px', fontSize: 14, cursor: 'pointer',
              textAlign: 'left', width: '100%',
            }}
            onMouseEnter={e => (e.currentTarget.style.borderColor = VIOLET)}
            onMouseLeave={e => (e.currentTarget.style.borderColor = '#30363d')}
          >
            <div style={{ fontWeight: 500 }}>Not attracted</div>
            <div style={{ color: '#8b949e', fontSize: 12, marginTop: 2 }}>
              It does not answer — revealing it as manned or ballistic. It is identified from now on.
            </div>
          </button>

          <button
            onClick={() => onSubmit(true)}
            style={{
              background: '#21262d', border: '1px solid #30363d', color: '#e6edf3',
              borderRadius: 6, padding: '8px 16px', fontSize: 14, cursor: 'pointer',
              textAlign: 'left', width: '100%',
            }}
            onMouseEnter={e => (e.currentTarget.style.borderColor = VIOLET)}
            onMouseLeave={e => (e.currentTarget.style.borderColor = '#30363d')}
          >
            <div style={{ fontWeight: 500 }}>Attracted — play along</div>
            <div style={{ color: '#8b949e', fontSize: 12, marginTop: 2 }}>
              It answers as a seeking weapon would. It stays unidentified, and you must fly it at
              {' '}{choice.scoutName} for as long as the bluff stands.
            </div>
          </button>
        </div>
      </div>
    </div>
  );
};
