import React from 'react';
import type { PendingBlindChoice, BlindChannelOption } from '../types/gameState';

interface Props {
  choice:   PendingBlindChoice;
  onSubmit: (channelDesignator: string) => void;
}

/**
 * A scout's own weapons fire blinded one of its channels (G24.13). The firing player picks
 * which powered channel goes dark — the menu shows each channel's current role so they can
 * sacrifice the most expendable one (G24.131). Only unblinded channels are offered.
 */
export const BlindChoiceDialog: React.FC<Props> = ({ choice, onSubmit }) => {
  function role(ch: BlindChannelOption): { text: string; expendable: boolean } {
    switch (ch.function) {
      case 'LEND_EW':
        return ch.target === choice.scoutName
          ? { text: `Self-protection — ${ch.lentEcm} ECM`, expendable: false }
          : { text: `Lending ${ch.lentEcm} ECM${ch.lentEccm > 0 ? `/${ch.lentEccm} ECCM` : ''} → ${ch.target}`, expendable: false };
      case 'BREAK_LOCKON':
        return { text: `Breaking lock-ons — ${ch.breakAttempts}/3 attempts used`, expendable: ch.breakAttempts >= 3 };
      case 'IDENTIFY':
        return { text: `Identifying seekers — ${ch.identifyAttempts}/4 attempts used`, expendable: ch.identifyAttempts >= 4 };
      case 'OFFENSIVE_EW':
        return { text: `Jamming ${ch.target} — ${ch.lentEcm} offensive EW`, expendable: false };
      default:
        return { text: 'Idle — no function this turn', expendable: true };
    }
  }

  const AMBER = '#f0c040';

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 200,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        background: '#161b22',
        border: `1px solid ${AMBER}`,
        borderRadius: 10,
        padding: '1.5rem',
        minWidth: 400,
        maxWidth: 540,
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
      }}>
        <div style={{ fontSize: '1.1rem', fontWeight: 700, color: AMBER, marginBottom: '0.5rem' }}>
          Sensor Blinding — {choice.scoutName}
        </div>
        <div style={{ color: '#8b949e', fontSize: 13, marginBottom: '1rem' }}>
          Your weapons fire blinded a scout channel (G24.13). Choose which powered channel goes
          dark for 32 impulses:
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {choice.channels.map(ch => {
            const r = role(ch);
            return (
              <button
                key={ch.designator}
                onClick={() => onSubmit(ch.designator)}
                style={{
                  background: '#21262d',
                  border: `1px solid ${r.expendable ? '#3fb950' : '#30363d'}`,
                  color: '#e6edf3',
                  borderRadius: 6,
                  padding: '8px 16px',
                  fontSize: 14,
                  cursor: 'pointer',
                  textAlign: 'left',
                  width: '100%',
                }}
                onMouseEnter={e => (e.currentTarget.style.borderColor = AMBER)}
                onMouseLeave={e => (e.currentTarget.style.borderColor = r.expendable ? '#3fb950' : '#30363d')}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontWeight: 500 }}>Channel {ch.designator}</span>
                  {r.expendable && (
                    <span style={{ color: '#3fb950', fontSize: 11, marginLeft: 8, fontStyle: 'italic' }}>expendable</span>
                  )}
                </div>
                <div style={{ color: '#8b949e', fontSize: 12, marginTop: 2 }}>{r.text}</div>
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
};
