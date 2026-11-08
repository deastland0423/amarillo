import { useEffect, useState } from 'react';
import { gameApi } from '../api/gameApi';
import { useDraggable } from '../hooks/useDraggable';
import { readChart, riskSummary, type TerrainChartData } from '../game/terrainCharts';
import { edgeLabel, yourSide, type BriefingData } from '../game/briefing';

/**
 * The printed charts, on screen, knowing which cell you are reading.
 *
 * <h2>Why a pop-up and not a forecast on the ship panel</h2>
 * The first design put a computed risk line permanently beside the ship's stats. The owner asked
 * for the whole table instead, which is better for a reason worth writing down: a summary like
 * "50% chance, up to 10 points" hides the SHAPE of the gamble. On the 7-14 column a six costs
 * ten, a five costs six, a four costs two and anything lower is free. Those are four different
 * decisions and one number flattens them. A table lets the player decide; a forecast decides and
 * shows its conclusion.
 *
 * <p>It is also less interface, not more: a button that opens a panel costs one button, where a
 * permanent block costs space in every game whether there is terrain on the map or not.
 *
 * <h2>Highlighting is the only reason this beats the Annex</h2>
 * Paper cannot know you are at effective speed 8 on passive fire control. The charts arrive with
 * the server's answer for the selected ship, and every cell shows what THAT ship suffers on
 * THAT roll — the C11.21 nimble shift and the D6.628 passive penalty already folded in.
 */
/**
 * The pad holds more than one aid, and the player chooses which — a MULTI-select, not tabs,
 * because the two get read together: the briefing says which edge is yours and the charts say
 * what the asteroids between you and it will cost. Asked for in play, 2026-11-07.
 */
type AidSection = 'briefing' | 'charts';

export function PlayerAidPanel({ gameId, playerToken, shipName, mapCols, mapRows, onClose }: {
  gameId: string;
  playerToken: string;
  shipName: string | null;
  mapCols: number;
  mapRows: number;
  onClose: () => void;
}) {
  // FLOATING and draggable, like the Energy Allocation dialog and the Aegis pads — not a
  // full-screen overlay like Score. A full-screen one would cover the EA dialog, and consulting
  // the chart WHILE choosing a speed is the whole point of having it: the owner found it worked
  // that way by luck when the button lived in the sidebar, and it would have been a regression
  // to take it away by moving the button to the toolbar.
  const drag = useDraggable({ left: 24, top: 120 });
  const [data, setData] = useState<TerrainChartData | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Both open to begin with. The briefing is short and sits above the charts, so showing it
  // costs the charts a line or two of scroll and answers "what am I doing here" without a
  // click. Either can be shut off and the choice holds while the pad is open.
  const [sections, setSections] = useState<AidSection[]>(['briefing', 'charts']);
  const [briefing, setBriefing] = useState<BriefingData | null>(null);
  const [briefingError, setBriefingError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    gameApi.getBriefing(gameId, playerToken)
      .then(b => { if (live) { setBriefing(b); setBriefingError(null); } })
      .catch(e => {
        if (live) setBriefingError(e instanceof Error ? e.message : 'Could not load the briefing');
      });
    return () => { live = false; };
  }, [gameId, playerToken]);

  function toggle(section: AidSection) {
    setSections(open => open.includes(section)
      ? open.filter(s => s !== section)
      : [...open, section]);
  }

  useEffect(() => {
    let live = true;
    // The reset belongs in the callback, not here: react-hooks/set-state-in-effect rejects a
    // synchronous setState in an effect body, and rightly — it would render twice for nothing.
    gameApi.getTerrainCharts(gameId, shipName)
      .then(d => { if (live) { setData(d); setError(null); } })
      .catch(e => { if (live) setError(e instanceof Error ? e.message : 'Could not load charts'); });
    return () => { live = false; };
  }, [gameId, shipName]);

  return (
    <div style={{ ...panelStyle, left: drag.position.left, top: drag.position.top }}>
      <div style={headerStyle} {...drag.handleProps} title="Drag to move">
        <span style={{ fontWeight: 700 }}>Player Aid</span>
        <button className="secondary" style={{ padding: '0 8px' }}
                title="Close" onClick={onClose}>
          {String.fromCharCode(10005)}
        </button>
      </div>

      <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
        {(['briefing', 'charts'] as AidSection[]).map(section => (
          <button
            key={section}
            className={`secondary${sections.includes(section) ? ' active' : ''}`}
            style={{ flex: 1, fontSize: '0.75rem', textTransform: 'capitalize' }}
            onClick={() => toggle(section)}
          >
            {section}
          </button>
        ))}
      </div>

      {sections.includes('briefing') && (
        <div style={{ marginBottom: sections.includes('charts') ? 16 : 0 }}>
          {briefingError && (
            <div style={{ fontSize: '0.75rem', color: '#f85149' }}>{briefingError}</div>
          )}
          {!briefing && !briefingError && <div>Loading briefing…</div>}
          {briefing && (
            <>
              <div style={{ fontWeight: 600 }}>{briefing.name || 'Scenario'}</div>
              {briefing.description && (
                <div style={{ fontSize: 12, margin: '4px 0 8px', lineHeight: 1.45 }}>
                  {briefing.description}
                </div>
              )}

              {/* The line this panel was built for: a player carrying an objective needs to
                  know which way out is his, and leaving by any other edge destroys the ship. */}
              {(() => {
                const side = yourSide(briefing);
                if (!side) return null;
                return (
                  <div style={{
                    border: '1px solid #3fb950', borderRadius: 4,
                    padding: '6px 8px', margin: '0 0 8px', fontSize: 12,
                  }}>
                    <div style={{ color: '#3fb950', fontWeight: 600 }}>
                      Your exit — {side.name}
                    </div>
                    <div>
                      {side.exitEdges.length === 0
                        ? 'No edge: this side cannot disengage off the map.'
                        : `Disengage off the ${side.exitEdges
                            .map(e => edgeLabel(e, mapCols, mapRows)).join(' or ')}.`}
                    </div>
                    {side.destructionEdges.length > 0 && (
                      <div style={{ color: '#f85149', marginTop: 2 }}>
                        Leaving by the {side.destructionEdges
                          .map(e => edgeLabel(e, mapCols, mapRows)).join(', ')} destroys the ship.
                      </div>
                    )}
                  </div>
                );
              })()}

              {briefing.victoryNotes && (
                <div style={{ fontSize: 12, marginBottom: 8, lineHeight: 1.45 }}>
                  <span style={{ fontWeight: 600 }}>Victory. </span>
                  {briefing.victoryNotes}
                </div>
              )}

              {briefing.specialRules.length > 0 && (
                <div style={{ fontSize: 12, lineHeight: 1.45 }}>
                  <div style={{ fontWeight: 600, marginBottom: 2 }}>Special rules</div>
                  <ul style={{ margin: 0, paddingLeft: 16 }}>
                    {briefing.specialRules.map((rule, i) => (
                      <li key={i} style={{ marginBottom: 4 }}>{rule}</li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}
        </div>
      )}

      {sections.includes('charts') && error && (
        <div style={{ fontSize: '0.75rem', color: '#f85149' }}>{error}</div>
      )}
      {sections.includes('charts') && !data && !error && <div>Loading charts…</div>}

      {sections.includes('charts') && data?.you && (
        <div style={{ fontSize: 12, opacity: 0.85, margin: '4px 0 8px' }}>
          {data.you.modifiers.join(' · ')}
        </div>
      )}

      {sections.includes('charts') && data?.charts.map(chart => {
        const cells = readChart(chart, data.you);
        return (
          <div key={chart.rule} style={{ marginBottom: 14 }}>
            <div style={{ fontWeight: 600 }}>{chart.name} <span style={{ opacity: 0.6 }}>({chart.rule})</span></div>
            <div style={{ fontSize: 12, margin: '2px 0 6px' }}>{riskSummary(chart, data.you)}</div>
            <table className="aid-table">
              <thead>
                <tr>
                  <th>Die</th>
                  {data.speedColumns.map(c => <th key={c}>{c}</th>)}
                </tr>
              </thead>
              <tbody>
                {cells.map((row, dieIndex) => (
                  <tr key={dieIndex}>
                    <th>{dieIndex + 1}</th>
                    {row.map((cell, column) => (
                      <td key={column}
                          className={cell.inYourColumn ? 'aid-cell-yours' : undefined}>
                        {cell.damage}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      })}

      {sections.includes('charts') && (
        <div style={{ fontSize: 11, opacity: 0.7 }}>
          Damage is to the shield facing the hex entered. Speed columns read EFFECTIVE speed
          (C2.451), so Erratic Maneuvers are already counted.
        </div>
      )}

      {sections.length === 0 && (
        <div style={{ fontSize: 12, opacity: 0.7 }}>Pick an aid above.</div>
      )}
    </div>
  );
}

const panelStyle: React.CSSProperties = {
  position: 'fixed',
  width: 320,
  maxHeight: '80vh',
  overflowY: 'auto',
  zIndex: 45,                    // the same band as the Aegis pads
  background: '#161b22',
  border: '1px solid #f0c040',   // amber, like the allocation dialog it is usually read beside
  borderRadius: 6,
  padding: 10,
  boxShadow: '0 6px 24px rgba(0,0,0,0.5)',
};

const headerStyle: React.CSSProperties = {
  cursor: 'grab',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  marginBottom: 8,
};
