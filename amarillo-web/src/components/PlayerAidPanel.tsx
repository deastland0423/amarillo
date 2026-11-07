import { useEffect, useState } from 'react';
import { gameApi } from '../api/gameApi';
import { readChart, riskSummary, type TerrainChartData } from '../game/terrainCharts';

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
export function PlayerAidPanel({ gameId, shipName, onClose }: {
  gameId: string;
  shipName: string | null;
  onClose: () => void;
}) {
  const [data, setData] = useState<TerrainChartData | null>(null);
  const [error, setError] = useState<string | null>(null);

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
    <div className="sidebar-section">
      <div className="sidebar-section-title">
        Player Aid: Terrain
        <button className="secondary" style={{ float: 'right' }} onClick={onClose}>Close</button>
      </div>

      {error && <div style={{ fontSize: '0.75rem', color: '#f85149' }}>{error}</div>}
      {!data && !error && <div>Loading charts…</div>}

      {data?.you && (
        <div style={{ fontSize: 12, opacity: 0.85, margin: '4px 0 8px' }}>
          {data.you.modifiers.join(' · ')}
        </div>
      )}

      {data?.charts.map(chart => {
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

      <div style={{ fontSize: 11, opacity: 0.7 }}>
        Damage is to the shield facing the hex entered. Speed columns read EFFECTIVE speed
        (C2.451), so Erratic Maneuvers are already counted.
      </div>
    </div>
  );
}
