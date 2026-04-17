import { useEffect, useState } from 'react';
import type { LobbyResult } from './Lobby';
import { useLobbySocket } from '../hooks/useLobbySocket';
import { gameApi } from '../api/gameApi';
import type { PlayerListing, ScenarioSummary, ScenarioSide, CoiSideData, CoiSubmission } from '../api/gameApi';
import CoiDialog from './CoiDialog';

interface Props {
  session: LobbyResult;
  onGameStarted: () => void;
  onLeave: () => void;
}

export default function PreGame({ session, onGameStarted, onLeave }: Props) {
  const lobby = useLobbySocket(session.gameId);
  const [players, setPlayers] = useState<PlayerListing[]>([]);
  const [selectedPlayer, setSelectedPlayer] = useState<Record<string, string>>({});
  const [scenarios, setScenarios] = useState<ScenarioSummary[]>([]);
  const [selectedScenario, setSelectedScenario] = useState('');
  const [coiData, setCoiData]       = useState<CoiSideData[] | null>(null);
  const [coiSubmitted, setCoiSubmitted] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  // Load scenario list for the host picker
  useEffect(() => {
    if (session.isHost) {
      gameApi.listScenarios()
        .then(list => {
          setScenarios(list);
          if (list.length === 1) setSelectedScenario(list[0].id);
        })
        .catch(() => setError('Could not load scenarios.'));
    }
  }, [session.isHost]);

  // Host fetches token-bearing player list once the game starts (needed for assignment)
  useEffect(() => {
    if (session.isHost && lobby?.started) {
      gameApi.getPlayers(session.gameId, session.playerToken)
        .then(setPlayers)
        .catch(() => {/* non-fatal */});
    }
  }, [session.isHost, session.gameId, session.playerToken, lobby?.started]);

  // Transition to game board once all ships are assigned
  useEffect(() => {
    if (lobby?.started && lobby.unassignedShips.length === 0 && lobby.players.some(p => p.assignedShips.length > 0)) {
      onGameStarted();
    }
  }, [lobby, onGameStarted]);

  async function handleScenarioChange(id: string) {
    setSelectedScenario(id);
    setCoiData(null);
    setCoiSubmitted(false);
    if (!id) return;
    try {
      const data = await gameApi.getCoiData(id);
      setCoiData(data);
    } catch {
      setError('Could not load COI data.');
    }
  }

  async function handleCoiSubmit(submission: CoiSubmission) {
    setBusy(true); setError('');
    try {
      await gameApi.submitCoi(session.gameId, session.playerToken, submission);
      setCoiSubmitted(true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not save COI selections.');
    } finally {
      setBusy(false);
    }
  }

  async function handleStart() {
    if (!selectedScenario) { setError('Pick a scenario first.'); return; }
    setBusy(true); setError('');
    try {
      await gameApi.startGame(session.gameId, session.playerToken, selectedScenario);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not start game.');
    } finally {
      setBusy(false);
    }
  }

  async function handleAssign(shipName: string) {
    const playerToken = selectedPlayer[shipName];
    if (!playerToken) { setError('Select a player for ' + shipName); return; }
    setBusy(true); setError('');
    try {
      await gameApi.assignShip(session.gameId, session.playerToken, playerToken, shipName);
      setSelectedPlayer(prev => { const next = { ...prev }; delete next[shipName]; return next; });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not assign ship.');
    } finally {
      setBusy(false);
    }
  }

  const activeScenario = scenarios.find(s => s.id === selectedScenario);

  return (
    <div className="lobby">
      <h1>Amarillo</h1>

      <div className="pregame-header">
        <span className="game-id-label">Game ID</span>
        <span className="game-id">{session.gameId}</span>
        {!lobby?.started && <span className="subtitle">Share this ID with other players</span>}
      </div>

      {/* Player list */}
      <div className="card" style={{ width: '100%', maxWidth: 480 }}>
        <h3 style={{ margin: 0 }}>Players</h3>
        {(!lobby || lobby.players.length === 0) && (
          <p className="subtitle">Waiting for players…</p>
        )}
        <ul className="player-list">
          {(lobby?.players ?? []).map(p => (
            <li key={p.name} className="player-row">
              <span className="player-name">
                {p.name}
                {p.isHost && <span className="badge">host</span>}
                {p.name === session.playerName && <span className="badge you">you</span>}
              </span>
              {p.assignedShips.length > 0 && (
                <span className="ship-tags">
                  {p.assignedShips.map(s => <span key={s} className="ship-tag">{s}</span>)}
                </span>
              )}
            </li>
          ))}
        </ul>

        {/* Host: scenario picker + start */}
        {session.isHost && !lobby?.started && (
          <>
            <div className="scenario-picker">
              <label htmlFor="scenario-select">Scenario</label>
              <select
                id="scenario-select"
                value={selectedScenario}
                onChange={e => handleScenarioChange(e.target.value)}
              >
                <option value="">— choose a scenario —</option>
                {scenarios.map(s => (
                  <option key={s.id} value={s.id}>[{s.id}] {s.name} (Y{s.year})</option>
                ))}
              </select>
            </div>
            {error && <p className="error">{error}</p>}
            {coiSubmitted && (
              <div className="button-row">
                <button onClick={handleStart} disabled={busy}>
                  {busy ? 'Starting…' : 'Start game'}
                </button>
                <button className="secondary" onClick={onLeave}>Leave</button>
              </div>
            )}
          </>
        )}

        {/* Non-host waiting */}
        {!session.isHost && !lobby?.started && (
          <p className="subtitle">Waiting for host to start the game…</p>
        )}
      </div>

      {/* COI dialog — appears after scenario selected, before start */}
      {session.isHost && !lobby?.started && coiData && !coiSubmitted && (
        <CoiDialog
          sides={coiData}
          playerToken={session.playerToken}
          onSubmit={handleCoiSubmit}
          onSkip={() => setCoiSubmitted(true)}
          busy={busy}
        />
      )}

      {/* Scenario detail card */}
      {activeScenario && (
        <div className="card scenario-detail" style={{ width: '100%', maxWidth: 640 }}>
          <div className="scenario-detail-header">
            <span className="scenario-detail-id">{activeScenario.id}</span>
            <span className="scenario-detail-name">{activeScenario.name}</span>
            <span className="scenario-detail-year">Y{activeScenario.year}</span>
          </div>

          {activeScenario.description && (
            <p className="scenario-detail-desc">{activeScenario.description}</p>
          )}

          <div className="scenario-detail-meta">
            <span>{activeScenario.numPlayers} players</span>
            <span>Map: {activeScenario.mapType}</span>
            <span>Victory: {activeScenario.victoryType}</span>
          </div>

          {/* Sides and ships */}
          <div className="scenario-sides">
            {activeScenario.sides.map((side: ScenarioSide) => (
              <div key={side.faction} className="scenario-side">
                <div className="scenario-side-header">
                  <span className="scenario-side-name">{side.name || side.faction}</span>
                  {side.reinforcementGroups > 0 && (
                    <span className="scenario-reinforcement-badge">+reinforcements</span>
                  )}
                </div>
                <table className="scenario-ship-table">
                  <thead>
                    <tr>
                      <th>Ship</th>
                      <th>Hull</th>
                      <th>Hex</th>
                      <th>Hdg</th>
                      <th>Spd</th>
                      <th>WS</th>
                      <th>Refits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {side.ships.map(ship => (
                      <tr key={ship.shipName || ship.hull}>
                        <td>{ship.shipName}</td>
                        <td>{ship.hull}</td>
                        <td>{ship.startHex}</td>
                        <td>{ship.startHeading}</td>
                        <td>{ship.startSpeed === 16 ? 'Max' : ship.startSpeed}</td>
                        <td>WS-{ship.weaponStatus}</td>
                        <td>{ship.refits.length > 0 ? ship.refits.join(', ') : '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>

          {/* Victory conditions notes */}
          {activeScenario.victoryNotes && (
            <div className="scenario-section">
              <div className="scenario-section-title">Victory Conditions</div>
              <p className="scenario-section-text">{activeScenario.victoryNotes}</p>
            </div>
          )}

          {/* Shuttle rules */}
          {(!activeScenario.warpBoosterPacks || !activeScenario.megapacks || !activeScenario.mrsShuttles || !activeScenario.pfs) && (
            <div className="scenario-section">
              <div className="scenario-section-title">Shuttle / PF Rules</div>
              <ul className="scenario-rules-list">
                {!activeScenario.warpBoosterPacks && <li>No warp booster packs</li>}
                {!activeScenario.megapacks        && <li>No megapacks</li>}
                {!activeScenario.mrsShuttles      && <li>No MRS shuttles</li>}
                {!activeScenario.pfs              && <li>No PFs</li>}
              </ul>
            </div>
          )}

          {/* Special rules */}
          {activeScenario.specialRules.length > 0 && (
            <div className="scenario-section">
              <div className="scenario-section-title">Special Rules</div>
              <ul className="scenario-rules-list">
                {activeScenario.specialRules.map((r, i) => <li key={i}>{r}</li>)}
              </ul>
            </div>
          )}
        </div>
      )}

      {/* Ship assignment (host only, after start) */}
      {session.isHost && lobby?.started && lobby.unassignedShips.length > 0 && (
        <div className="card" style={{ width: '100%', maxWidth: 480 }}>
          <h3 style={{ margin: 0 }}>Assign ships</h3>
          {error && <p className="error">{error}</p>}
          {lobby.unassignedShips.map(ship => (
            <div key={ship} className="assign-row">
              <span className="assign-ship">{ship}</span>
              <select
                value={selectedPlayer[ship] ?? ''}
                onChange={e => setSelectedPlayer(prev => ({ ...prev, [ship]: e.target.value }))}
              >
                <option value="">— pick player —</option>
                {players.map(p => (
                  <option key={p.token} value={p.token}>{p.name}</option>
                ))}
              </select>
              <button onClick={() => handleAssign(ship)} disabled={busy || !selectedPlayer[ship]}>
                Assign
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Non-host: waiting for ship assignment */}
      {!session.isHost && lobby?.started && (
        <div className="card" style={{ width: '100%', maxWidth: 480 }}>
          <p className="subtitle">Waiting for host to assign ships…</p>
        </div>
      )}

      {!session.isHost && (
        <button className="secondary" style={{ marginTop: '1rem' }} onClick={onLeave}>Leave game</button>
      )}
    </div>
  );
}
