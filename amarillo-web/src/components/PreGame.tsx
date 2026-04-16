import { useEffect, useState } from 'react';
import type { LobbyResult } from './Lobby';
import { useLobbySocket } from '../hooks/useLobbySocket';
import { gameApi } from '../api/gameApi';
import type { PlayerListing, ScenarioSummary } from '../api/gameApi';

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
                onChange={e => setSelectedScenario(e.target.value)}
              >
                <option value="">— choose a scenario —</option>
                {scenarios.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
              {activeScenario?.description && (
                <p className="scenario-description">{activeScenario.description}</p>
              )}
            </div>
            {error && <p className="error">{error}</p>}
            <div className="button-row">
              <button onClick={handleStart} disabled={busy || !selectedScenario}>
                {busy ? 'Starting…' : 'Start game'}
              </button>
              <button className="secondary" onClick={onLeave}>Leave</button>
            </div>
          </>
        )}

        {/* Non-host waiting */}
        {!session.isHost && !lobby?.started && (
          <p className="subtitle">Waiting for host to start the game…</p>
        )}
      </div>

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
