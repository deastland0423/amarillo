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

  // Host-only: player list with tokens (needed for assignment dropdowns)
  const [players,         setPlayers]         = useState<PlayerListing[]>([]);
  // Host-only: which player token is selected per ship in the assignment UI
  const [selectedPlayer,  setSelectedPlayer]  = useState<Record<string, string>>({});
  // Host-only: scenario list + selection
  const [scenarios,       setScenarios]       = useState<ScenarioSummary[]>([]);
  const [selectedScenario,setSelectedScenario]= useState('');
  // Raw COI data for the whole scenario (fetched once per scenario)
  const [rawCoiData,      setRawCoiData]      = useState<CoiSideData[] | null>(null);
  // Filtered to this player's assigned ships
  const [coiData,         setCoiData]         = useState<CoiSideData[] | null>(null);
  const [coiSubmitted,    setCoiSubmitted]    = useState(false);

  const [error,  setError]  = useState('');
  const [busy,   setBusy]   = useState(false);

  // ---- Load scenario list for host picker ----
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

  // ---- Host: refresh token-bearing player list whenever lobby player count changes ----
  useEffect(() => {
    if (session.isHost) {
      gameApi.getPlayers(session.gameId, session.playerToken)
        .then(setPlayers)
        .catch(() => {/* non-fatal */});
    }
  }, [session.isHost, session.gameId, session.playerToken, lobby?.players.length]);

  // ---- Fetch raw COI data once when a scenario is loaded ----
  useEffect(() => {
    if (!lobby?.scenarioLoaded || !lobby.scenarioId) return;
    gameApi.getCoiData(lobby.scenarioId)
      .then(setRawCoiData)
      .catch(() => setError('Could not load COI data.'));
  }, [lobby?.scenarioLoaded, lobby?.scenarioId]);

  // ---- Re-filter COI data whenever assignments or raw data change ----
  useEffect(() => {
    if (!rawCoiData) { setCoiData(null); return; }
    const myShips = lobby?.players
      .find(p => p.name === session.playerName)?.assignedShips ?? [];
    if (myShips.length === 0) { setCoiData(null); return; }
    const filtered = rawCoiData.map(side => ({
      ...side,
      ships: side.ships.filter(s => myShips.includes(s.shipName)),
    })).filter(side => side.ships.length > 0);
    setCoiData(filtered.length > 0 ? filtered : null);
  }, [rawCoiData, lobby?.players, session.playerName]);

  // ---- Transition to game board once started ----
  useEffect(() => {
    if (lobby?.started) onGameStarted();
  }, [lobby?.started, onGameStarted]);

  // ---- Reset COI state if scenario reloaded ----
  useEffect(() => {
    setRawCoiData(null);
    setCoiData(null);
    setCoiSubmitted(false);
  }, [lobby?.scenarioId]);

  async function handleScenarioLoad(id: string) {
    setSelectedScenario(id);
    if (!id) return;
    setBusy(true); setError('');
    try {
      await gameApi.loadScenario(session.gameId, session.playerToken, id);
      // lobby WebSocket will push the updated state (scenarioLoaded + unassignedShips)
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not load scenario.');
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

  async function handleCoiSkip() {
    setBusy(true); setError('');
    try {
      await gameApi.submitCoi(session.gameId, session.playerToken, {});
      setCoiSubmitted(true);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not skip COI.');
    } finally {
      setBusy(false);
    }
  }

  async function handleStart() {
    setBusy(true); setError('');
    try {
      await gameApi.startGame(session.gameId, session.playerToken);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not start game.');
    } finally {
      setBusy(false);
    }
  }

  const activeScenario = scenarios.find(s => s.id === selectedScenario);
  const myLobbyEntry   = lobby?.players.find(p => p.name === session.playerName);
  const myShips        = myLobbyEntry?.assignedShips ?? [];
  const iAmCoiDone     = myLobbyEntry?.coiDone ?? false;

  return (
    <div className="lobby">
      <h1>Amarillo</h1>

      {/* Game code */}
      <div className="pregame-header">
        <span className="game-id-label">Game ID</span>
        <span className="game-id">{session.gameId}</span>
        <span className="subtitle">Share this ID with other players</span>
      </div>

      {/* Player list with COI status, grouped by team */}
      <div className="card" style={{ width: '100%', maxWidth: 480 }}>
        <h3 style={{ margin: 0 }}>Players</h3>
        {(!lobby || lobby.players.length === 0) && (
          <p className="subtitle">Waiting for players…</p>
        )}
        {(() => {
          const allPlayers = lobby?.players ?? [];
          // Collect unique team names in order of first appearance; null → ungrouped
          const teams: (string | null)[] = [];
          for (const p of allPlayers) {
            if (!teams.includes(p.teamName)) teams.push(p.teamName);
          }
          const grouped = teams.length > 1 || teams[0] !== null;
          return teams.map(team => (
            <div key={team ?? '__none__'}>
              {grouped && team && (
                <div className="team-header">{team}</div>
              )}
              <ul className="player-list">
                {allPlayers.filter(p => p.teamName === team).map(p => (
                  <li key={p.name} className="player-row">
                    <span className="player-name">
                      {p.name}
                      {p.isHost && <span className="badge">host</span>}
                      {p.name === session.playerName && <span className="badge you">you</span>}
                    </span>
                    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      {p.assignedShips.length > 0 && (
                        <span className="ship-tags">
                          {p.assignedShips.map(s => <span key={s} className="ship-tag">{s}</span>)}
                        </span>
                      )}
                      {lobby?.scenarioLoaded && (
                        <span style={{ fontSize: 12, color: p.coiDone ? '#56d364' : '#8b949e' }}>
                          {p.coiDone ? '✓ COI' : '○ COI'}
                        </span>
                      )}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          ));
        })()}

        {/* Host: scenario picker */}
        {session.isHost && (
          <div className="scenario-picker">
            <label htmlFor="scenario-select">Scenario</label>
            <select
              id="scenario-select"
              value={selectedScenario}
              onChange={e => handleScenarioLoad(e.target.value)}
              disabled={busy}
            >
              <option value="">— choose a scenario —</option>
              {scenarios.map(s => (
                <option key={s.id} value={s.id}>[{s.id}] {s.name} (Y{s.year})</option>
              ))}
            </select>
          </div>
        )}

        {/* Non-host: waiting message when no scenario yet */}
        {!session.isHost && !lobby?.scenarioLoaded && (
          <p className="subtitle">Waiting for host to select a scenario…</p>
        )}

        {error && <p className="error">{error}</p>}

        {/* Host: Start button — enabled only when all COI done */}
        {session.isHost && lobby?.scenarioLoaded && (
          <div className="button-row" style={{ marginTop: 12 }}>
            <button
              onClick={handleStart}
              disabled={busy || !lobby.allCoiReady}
              title={lobby.allCoiReady ? '' : 'Waiting for all players to submit COI'}
            >
              {busy ? 'Starting…' : lobby.allCoiReady ? 'Start game' : 'Waiting for COI…'}
            </button>
            <button className="secondary" onClick={onLeave}>Leave</button>
          </div>
        )}

        {!session.isHost && (
          <button className="secondary" style={{ marginTop: 12 }} onClick={onLeave}>Leave game</button>
        )}
      </div>

      {/* Host: ship assignment panel (after scenario loaded) */}
      {session.isHost && lobby?.scenarioLoaded && lobby.unassignedShips.length > 0 && (
        <div className="card" style={{ width: '100%', maxWidth: 480 }}>
          <h3 style={{ margin: 0 }}>Assign ships</h3>
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
              <button
                onClick={() => handleAssign(ship)}
                disabled={busy || !selectedPlayer[ship]}
              >
                Assign
              </button>
            </div>
          ))}
        </div>
      )}

      {/* COI dialog — appears when this player has ships and hasn't submitted yet */}
      {myShips.length > 0 && !iAmCoiDone && coiData && (
        <CoiDialog
          sides={coiData}
          playerToken={session.playerToken}
          onSubmit={handleCoiSubmit}
          onSkip={handleCoiSkip}
          busy={busy}
        />
      )}

      {/* Waiting message when ships assigned but COI data not loaded yet */}
      {myShips.length > 0 && !iAmCoiDone && !coiData && lobby?.scenarioLoaded && (
        <div className="card" style={{ width: '100%', maxWidth: 480 }}>
          <p className="subtitle">Loading COI options…</p>
        </div>
      )}

      {/* Confirmation once COI submitted */}
      {iAmCoiDone && !lobby?.started && (
        <div className="card" style={{ width: '100%', maxWidth: 480 }}>
          <p className="subtitle" style={{ color: '#56d364' }}>
            ✓ COI submitted — waiting for other players…
          </p>
        </div>
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
                      <th>Ship</th><th>Hull</th><th>Hex</th>
                      <th>Hdg</th><th>Spd</th><th>WS</th><th>Refits</th>
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

          {activeScenario.victoryNotes && (
            <div className="scenario-section">
              <div className="scenario-section-title">Victory Conditions</div>
              <p className="scenario-section-text">{activeScenario.victoryNotes}</p>
            </div>
          )}

          {(!activeScenario.warpBoosterPacks || !activeScenario.megapacks ||
            !activeScenario.mrsShuttles || !activeScenario.pfs) && (
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
    </div>
  );
}
