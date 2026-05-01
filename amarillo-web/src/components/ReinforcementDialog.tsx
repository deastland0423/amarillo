import React, { useState } from 'react';
import type { PendingVolley } from '../types/gameState';

interface Props {
  pendingVolleys:  PendingVolley[];
  myShipNames:     Set<string>;
  batteryByShip:   Record<string, number>;
  onSubmit: (decisions: Array<{ shipName: string; shieldNumber: number; power: number }>) => void;
}

export const ReinforcementDialog: React.FC<Props> = ({
  pendingVolleys,
  myShipNames,
  batteryByShip,
  onSubmit,
}) => {
  const myVolleys = pendingVolleys.filter(v => myShipNames.has(v.targetShipName));

  const [spending, setSpending] = useState<number[]>(() => myVolleys.map(() => 0));

  function batteryUsed(shipName: string, excludeIdx: number): number {
    return myVolleys.reduce((sum, v, i) =>
      i !== excludeIdx && v.targetShipName === shipName ? sum + (spending[i] ?? 0) : sum, 0);
  }

  function remainingBattery(shipName: string, excludeIdx: number): number {
    return (batteryByShip[shipName] ?? 0) - batteryUsed(shipName, excludeIdx);
  }

  function setSpend(idx: number, value: number) {
    const max = remainingBattery(myVolleys[idx].targetShipName, idx);
    setSpending(prev => {
      const next = [...prev];
      next[idx] = Math.max(0, Math.min(value, max));
      return next;
    });
  }

  function handleSubmit() {
    const decisions = myVolleys
      .map((v, i) => ({ shipName: v.targetShipName, shieldNumber: v.shieldNumber, power: spending[i] ?? 0 }))
      .filter(d => d.power > 0);
    onSubmit(decisions);
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 200,
      background: 'rgba(0,0,0,0.75)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
    }}>
      <div style={{
        background: '#161b22',
        border: '1px solid #30363d',
        borderRadius: 10,
        padding: '1.5rem',
        minWidth: 460,
        maxWidth: 600,
        boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
      }}>
        {/* Title */}
        <div style={{ fontSize: '1.1rem', fontWeight: 700, color: '#f0a040', marginBottom: '1rem' }}>
          Incoming Fire — Reinforcement Phase
        </div>

        {pendingVolleys.length === 0 ? (
          <p style={{ color: '#8b949e', margin: '0 0 1rem' }}>No incoming fire this segment.</p>
        ) : (
          <>
            {/* All-volleys table */}
            <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: '1rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid #30363d', color: '#8b949e', fontSize: 12 }}>
                  <th style={{ textAlign: 'left',  padding: '4px 8px', fontWeight: 500 }}>From</th>
                  <th style={{ textAlign: 'left',  padding: '4px 8px', fontWeight: 500 }}>Target</th>
                  <th style={{ textAlign: 'center',padding: '4px 8px', fontWeight: 500 }}>Shield</th>
                  <th style={{ textAlign: 'right', padding: '4px 8px', fontWeight: 500 }}>Damage</th>
                  {myVolleys.length > 0 && (
                    <th style={{ textAlign: 'right', padding: '4px 8px', fontWeight: 500 }}>Reserve</th>
                  )}
                </tr>
              </thead>
              <tbody>
                {pendingVolleys.map((v, gi) => {
                  const isMine   = myShipNames.has(v.targetShipName);
                  const localIdx = myVolleys.indexOf(v);
                  const avail    = isMine ? remainingBattery(v.targetShipName, localIdx) + (spending[localIdx] ?? 0) : 0;
                  return (
                    <tr key={gi} style={{
                      borderBottom: '1px solid #21262d',
                      color: isMine ? '#e6edf3' : '#8b949e',
                    }}>
                      <td style={{ padding: '6px 8px', fontSize: 13 }}>{v.attackerName}</td>
                      <td style={{ padding: '6px 8px', fontSize: 13 }}>{v.targetShipName}</td>
                      <td style={{ padding: '6px 8px', fontSize: 13, textAlign: 'center' }}>
                        #{v.shieldNumber}
                      </td>
                      <td style={{ padding: '6px 8px', fontSize: 13, textAlign: 'right',
                                   color: isMine ? '#f85149' : '#8b949e', fontWeight: isMine ? 600 : 400 }}>
                        {v.totalDamage}
                        {v.envelopingHellboreDamage > 0 ? ` +${v.envelopingHellboreDamage} env` : ''}
                        {v.addHit ? ' +ADD' : ''}
                      </td>
                      {myVolleys.length > 0 && (
                        <td style={{ padding: '6px 8px', textAlign: 'right' }}>
                          {isMine ? (
                            <input
                              type="number"
                              min={0}
                              max={avail}
                              value={spending[localIdx] ?? 0}
                              onChange={e => setSpend(localIdx, parseInt(e.target.value) || 0)}
                              style={{
                                width: 60, textAlign: 'right',
                                background: '#0d1117', color: '#e6edf3',
                                border: '1px solid #30363d', borderRadius: 4,
                                padding: '2px 6px', fontSize: 13,
                              }}
                            />
                          ) : (
                            <span style={{ color: '#30363d' }}>—</span>
                          )}
                        </td>
                      )}
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Reserve summary per own ship */}
            {myVolleys.length > 0 && (
              <div style={{ fontSize: 12, color: '#8b949e', marginBottom: '1rem', lineHeight: 1.8 }}>
                {Object.entries(batteryByShip)
                  .filter(([name]) => myShipNames.has(name))
                  .map(([name, cap]) => {
                    const used = myVolleys.reduce((sum, v, i) =>
                      v.targetShipName === name ? sum + (spending[i] ?? 0) : sum, 0);
                    return (
                      <div key={name}>
                        <span style={{ color: '#e6edf3' }}>{name}</span>
                        {' — '}reserve: {cap - used} / {cap} remaining
                      </div>
                    );
                  })}
              </div>
            )}
          </>
        )}

        {/* Confirm button */}
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button
            onClick={handleSubmit}
            style={{
              background: '#1f6feb', border: '1px solid #388bfd',
              color: '#e6edf3', borderRadius: 6,
              padding: '6px 20px', fontSize: 14, cursor: 'pointer',
              fontWeight: 600,
            }}
          >
            {myVolleys.length > 0 ? 'Confirm & Ready' : 'Ready'}
          </button>
        </div>
      </div>
    </div>
  );
};
