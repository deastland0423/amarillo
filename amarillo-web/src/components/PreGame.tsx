import { useEffect, useState } from 'react';
import type { LobbyResult } from './Lobby';
import { useLobbySocket } from '../hooks/useLobbySocket';
import { gameApi } from '../api/gameApi';
import type {
  PlayerListing, ScenarioSummary, CoiSideData, CoiSubmission, FleetSummary, TerrainChoice,
} from '../api/gameApi';
import CoiDialog from './CoiDialog';
import HexGrid from './HexGrid';
import DeploymentPanel from './DeploymentPanel';
import type { MapObject } from '../types/gameState';

interface Props {
  session: LobbyResult;
  onGameStarted: () => void;
  onLeave: () => void;
}

/** Zone tints, in side order. Translucent so the grid and terrain stay readable beneath. */
const ZONE_COLORS = [
  'rgba(88, 166, 255, 0.16)',   // blue
  'rgba(248, 81, 73, 0.16)',    // red
  'rgba(86, 211, 100, 0.16)',   // green
  'rgba(240, 192, 64, 0.16)',   // amber
];

export default function PreGame({ session, onGameStarted, onLeave }: Props) {
  const lobby = useLobbySocket(session.gameId);

  // Host-only: player list with tokens (needed for assignment dropdowns)
  const [players,         setPlayers]         = useState<PlayerListing[]>([]);
  // Host-only: which player token is selected per ship in the assignment UI
  const [selectedPlayer,  setSelectedPlayer]  = useState<Record<string, string>>({});
  // Host-only: scenario list + selection
  const [scenarios,       setScenarios]       = useState<ScenarioSummary[]>([]);
  const [selectedScenario,setSelectedScenario]= useState('');
  // Host-only: the other way to start a battle — saved fleets instead of a scenario file
  const [setupMode,       setSetupMode]       = useState<'scenario' | 'fleets'>('scenario');
  const [fleets,          setFleets]          = useState<FleetSummary[]>([]);
  const [chosenFleets,    setChosenFleets]    = useState<string[]>([]);
  const [fleetYear,       setFleetYear]       = useState(180);
  const [fleetBudget,     setFleetBudget]     = useState(1000);
  const [fleetWs,         setFleetWs]         = useState(2);
  const [fleetTerrain,    setFleetTerrain]    = useState<TerrainChoice>('OPEN_SPACE');
  // The situation the fleets are brought to. '' is the plain battle — two sides, open space,
  // standard victory — which is the same thing with nothing specified.
  const [situationId,     setSituationId]     = useState('');
  // Which side each chosen fleet flies for, when the situation names its sides.
  const [fleetSides,      setFleetSides]      = useState<Record<string, string>>({});
  // Raw COI data for the whole scenario (fetched once per scenario)
  const [rawCoiData,      setRawCoiData]      = useState<CoiSideData[] | null>(null);
  // Filtered to this player's assigned ships
  const [coiData,         setCoiData]         = useState<CoiSideData[] | null>(null);
  const [coiSubmitted,    setCoiSubmitted]    = useState(false);

  const [error,  setError]  = useState('');
  const [busy,   setBusy]   = useState(false);

  // ---- Load scenario list ----
  // Everyone loads it, not just the host: joiners need the full scenario
  // details (forces, map, victory, ships) to see the same summary the host
  // sees. The host also uses it to populate the picker.
  useEffect(() => {
    gameApi.listScenarios()
      .then(list => {
        setScenarios(list);
        if (session.isHost && list.length === 1) setSelectedScenario(list[0].id);
      })
      .catch(() => { if (session.isHost) setError('Could not load scenarios.'); });
  }, [session.isHost]);

  // ---- Host: the saved fleets available to field ----
  useEffect(() => {
    if (!session.isHost) return;
    gameApi.listFleets()
      .then(setFleets)
      .catch(() => {/* non-fatal: the scenario path still works */});
  }, [session.isHost]);

  // ---- Host: refresh token-bearing player list whenever lobby player count changes ----
  useEffect(() => {
    if (session.isHost) {
      gameApi.getPlayers(session.gameId, session.playerToken)
        .then(setPlayers)
        .catch(() => {/* non-fatal */});
    }
  }, [session.isHost, session.gameId, session.playerToken, lobby?.players.length]);

  // ---- Fetch raw COI data once a battle is loaded ----
  // Asked of the GAME rather than of a scenario id: a battle assembled from saved
  // fleets has no file to look up, and this route serves both.
  useEffect(() => {
    if (!lobby?.scenarioLoaded) return;
    gameApi.getGameCoiData(session.gameId)
      .then(setRawCoiData)
      .catch(() => setError('Could not load COI data.'));
  }, [lobby?.scenarioLoaded, lobby?.scenarioId, session.gameId]);

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

  async function handleFleetBattle() {
    if (chosenFleets.length === 0) { setError('Choose at least one fleet.'); return; }
    setBusy(true); setError('');
    try {
      const res = await gameApi.loadFleetsIntoGame(session.gameId, session.playerToken, {
        scenarioId: situationId || undefined,
        sides: chosenFleets.map(id => ({ fleetId: id, team: fleetSides[id] || undefined })),
        year: fleetYear,
        budget: fleetBudget,
        weaponStatus: fleetWs,
        terrain: fleetTerrain,
      });
      setError('');
      void res;
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not assemble the battle.');
    } finally {
      setBusy(false);
    }
  }

  function toggleFleet(id: string) {
    setChosenFleets(cur => cur.includes(id) ? cur.filter(f => f !== id) : [...cur, id]);
  }

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

  // Host previews their picked scenario; joiners (no selection) resolve the
  // loaded scenario from the lobby broadcast so they see the same full summary.
  const activeScenario = scenarios.find(s => s.id === (selectedScenario || lobby?.scenarioId));
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

      {/* Setting up: every player places their own ships, and nobody sees anyone else's
          until the last Done lands. */}
      {lobby?.scenarioLoaded && lobby.deploymentRequired && !lobby.started && (
        <DeploymentPanel
          gameId={session.gameId}
          playerToken={session.playerToken}
          faction={lobby.sides.find(s =>
            s.ships.some(sh => myShips.includes(sh.shipName)))?.faction ?? 'Federation'}
          terrain={lobby.terrain}
          mapCols={lobby.mapCols}
          mapRows={lobby.mapRows}
          revision={lobby.players.reduce((n, p) => n + p.shipsPlaced, 0)}
          playersDone={lobby.players.filter(p => p.deploymentDone).length}
          playerCount={lobby.players.length}
          waitingOn={lobby.players.filter(p => !p.deploymentDone).map(p => p.name)}
        />
      )}


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
          <>
            {/* Two ways to sit down to a battle: a written scenario, or fleets people built. */}
            <div className="fb-faction-chips" style={{ marginBottom: 12 }}>
              <button className={setupMode === 'scenario' ? 'fb-chip fb-chip-on' : 'fb-chip'}
                      onClick={() => setSetupMode('scenario')}>Scenario</button>
              <button className={setupMode === 'fleets' ? 'fb-chip fb-chip-on' : 'fb-chip'}
                      onClick={() => setSetupMode('fleets')}>Saved fleets</button>
            </div>

            {setupMode === 'scenario' && (
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

            {setupMode === 'fleets' && (() => {
              const situations = scenarios.filter(s => (s.openSides?.length ?? 0) > 0);
              const situation  = situations.find(s => s.id === situationId);
              const openSides  = situation?.openSides ?? [];
              const tooManyFleets = situation != null && chosenFleets.length > openSides.length;

              return (
              <div className="fleet-setup">
                {/* What kind of battle. The plain one is a situation with nothing said. */}
                <label className="fb-field">
                  <span>Situation</span>
                  <select value={situationId}
                          onChange={e => { setSituationId(e.target.value); setFleetSides({}); }}>
                    <option value="">A straight fight — open space, standard victory</option>
                    {situations.map(s => (
                      <option key={s.id} value={s.id}>
                        {s.name} ({s.openSides.length} fleets)
                      </option>
                    ))}
                  </select>
                  {situation?.description && (
                    <span className="fb-hint">{situation.description}</span>
                  )}
                </label>

                {tooManyFleets && (
                  <p className="error">
                    {situation!.name} has room for {openSides.length} fleets —
                    you have chosen {chosenFleets.length}.
                  </p>
                )}

                {fleets.length === 0 && (
                  <p className="subtitle">
                    No saved fleets yet. Build one from the opening menu, then come back.
                  </p>
                )}

                {fleets.map(f => (
                  <label key={f.id} className="fleet-choice">
                    <input type="checkbox"
                           checked={chosenFleets.includes(f.id)}
                           onChange={() => toggleFleet(f.id)} />
                    <span className="fleet-choice-name">{f.name || f.id}</span>
                    <span className="fb-hint">
                      {f.factions.join(' + ')} · Y{f.year} · {f.totalCost}/{f.budget} · {f.shipCount} ships
                    </span>
                    {/* Which side this fleet flies for, where the situation has named sides. */}
                    {openSides.length > 0 && chosenFleets.includes(f.id) && (
                      <select value={fleetSides[f.id] ?? ''}
                              onClick={e => e.preventDefault()}
                              onChange={e => setFleetSides(m => ({ ...m, [f.id]: e.target.value }))}>
                        <option value="">— which side? —</option>
                        {openSides.map(side => (
                          <option key={side} value={side}>{side}</option>
                        ))}
                      </select>
                    )}
                    <span className={f.legal ? 'fb-legal' : 'fb-illegal'}>
                      {f.legal ? 'legal' : 'illegal'}
                    </span>
                  </label>
                ))}

                {/* The conditions the battle is fought under. Fleets are rechecked against
                    these, not against whatever they were saved with. */}
                <div className="fb-conditions" style={{ marginTop: 12 }}>
                  <label className="fb-field fb-field-narrow">
                    <span>Year</span>
                    <input type="number" value={fleetYear}
                           onChange={e => setFleetYear(Number(e.target.value) || 0)} />
                  </label>
                  <label className="fb-field fb-field-narrow">
                    <span>Budget</span>
                    <input type="number" value={fleetBudget}
                           onChange={e => setFleetBudget(Number(e.target.value) || 0)} />
                  </label>
                  <label className="fb-field fb-field-narrow">
                    <span>Weapon status</span>
                    <select value={fleetWs} onChange={e => setFleetWs(Number(e.target.value))}>
                      <option value={0}>WS-0</option>
                      <option value={1}>WS-1</option>
                      <option value={2}>WS-2</option>
                      <option value={3}>WS-3</option>
                    </select>
                  </label>
                  {/* S8.15: the terrain is agreed before the forces take the field — unless
                      the situation already decided, in which case its ground may be measured
                      from what is out there and swapping it would make nonsense of the setup. */}
                  <label className="fb-field">
                    <span>Terrain</span>
                    <select value={fleetTerrain}
                            disabled={situation?.fixedTerrain ?? false}
                            onChange={e => setFleetTerrain(e.target.value as TerrainChoice)}>
                      <option value="OPEN_SPACE">Open space</option>
                      <option value="ASTEROID_FIELD">Asteroid field (P3.11)</option>
                      <option value="PLANET">A planet</option>
                      <option value="GAS_GIANT">A gas giant — size and rings rolled</option>
                    </select>
                    {situation?.fixedTerrain && (
                      <span className="fb-hint">{situation.name} sets its own.</span>
                    )}
                  </label>
                </div>

                <div className="button-row" style={{ marginTop: 12 }}>
                  <button onClick={handleFleetBattle}
                          disabled={busy || chosenFleets.length === 0 || tooManyFleets}>
                    {busy ? 'Assembling…' : `Assemble battle (${chosenFleets.length} fleets)`}
                  </button>
                </div>
              </div>
              );
            })()}
          </>
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

      {/* Battle detail card.
          Identity and forces come from the lobby broadcast, which carries the loaded spec —
          so this renders for a battle assembled from saved fleets just as it does for one
          read from data/scenarios. The extras below come from the scenario catalogue and
          simply do not appear for a built battle, which has no file to describe them. */}
      {lobby?.scenarioLoaded && (
        <div className="card scenario-detail" style={{ width: '100%', maxWidth: 640 }}>
          <div className="scenario-detail-header">
            <span className="scenario-detail-id">{lobby.scenarioId}</span>
            <span className="scenario-detail-name">{lobby.scenarioName ?? lobby.scenarioId}</span>
            {lobby.scenarioYear > 0 && (
              <span className="scenario-detail-year">Y{lobby.scenarioYear}</span>
            )}
          </div>

          {lobby.scenarioDescription && (
            <p className="scenario-detail-desc">{lobby.scenarioDescription}</p>
          )}

          {activeScenario && (
            <div className="scenario-detail-meta">
              <span>{activeScenario.numPlayers} players</span>
              <span>Map: {activeScenario.mapType}</span>
              <span>Victory: {activeScenario.victoryType}</span>
            </div>
          )}

          {/* Forces come from the lobby broadcast, which carries the loaded spec itself.
              A battle assembled from saved fleets is not a file in data/scenarios, so
              looking it up by id would show nothing. */}
          {/* The battlefield, before anyone commits to it: the terrain that was rolled and
              the ground each side may set up on. Both come from the broadcast spec, so this
              is the same map everyone else is looking at. */}
          {(() => {
            if (!lobby) return null;
            // While a player is setting up they have a map of their own, showing this
            // terrain plus their ground and their ships. Two maps on one page is worse
            // than either alone.
            if (lobby.deploymentRequired && !lobby.started) return null;
            const terrain: MapObject[] = lobby.terrain.map(t => ({
              type: 'TERRAIN',
              name: t.name ?? `${t.terrainType}-${t.hex}`,
              location: `<${Number(t.hex.slice(0, 2))}|${Number(t.hex.slice(2, 4))}>`,
              terrainType: t.terrainType as 'ASTEROID' | 'PLANET' | 'GAS_GIANT',
              radius: t.radius,
              rings: t.rings?.length ? t.rings : undefined,
            } as MapObject));

            const zones = lobby.sides
              .map((side, i) => ({
                hexes: side.deploymentZone?.hexes ?? [],
                color: ZONE_COLORS[i % ZONE_COLORS.length],
                label: side.name,
              }))
              .filter(z => z.hexes.length > 0);

            if (terrain.length === 0 && zones.length === 0) return null;

            return (
              <div className="battle-preview">
                <div className="scenario-section-title">The battlefield</div>
                <div className="battle-preview-map">
                  <HexGrid
                    mapCols={lobby.mapCols}
                    mapRows={lobby.mapRows}
                    mapObjects={terrain}
                    zones={zones}
                  />
                </div>
                {zones.length > 0 && (
                  <div className="battle-preview-key">
                    {lobby.sides.map((side, i) => side.deploymentZone && (
                      <span key={side.name + i} className="battle-preview-key-item">
                        <span className="battle-preview-swatch"
                              style={{ background: ZONE_COLORS[i % ZONE_COLORS.length] }} />
                        {side.name} — {side.deploymentZone.describe}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            );
          })()}

          {/* What the battle is being fought over, from the broadcast spec. */}
          {(lobby?.terrain?.length ?? 0) > 0 && (
            <div className="scenario-section">
              <div className="scenario-section-title">Terrain</div>
              <ul className="scenario-rules-list">
                {(() => {
                  const t = lobby!.terrain;
                  const rocks = t.filter(x => x.terrainType === 'ASTEROID').length;
                  const rows: string[] = [];
                  if (rocks > 0) rows.push(`Asteroid field — ${rocks} hexes (P3.11)`);
                  for (const body of t.filter(x => x.terrainType !== 'ASTEROID')) {
                    const across = body.radius * 2 + 1;
                    const rings = body.rings?.length
                      ? `, rings at ${body.rings.map(b => `${b[0]}-${b[1]}`).join(' and ')}`
                      : '';
                    rows.push(body.terrainType === 'GAS_GIANT'
                      ? `Gas giant at ${body.hex} — ${across} hexes across${rings}`
                      : `Planet at ${body.hex}${body.name ? ` (${body.name})` : ''}`);
                  }
                  return rows.map((r, i) => <li key={i}>{r}</li>);
                })()}
              </ul>
            </div>
          )}

          <div className="scenario-sides">
            {(lobby?.sides ?? []).map((side, i) => (
              <div key={side.name + i} className="scenario-side">
                <div className="scenario-side-header">
                  <span className="scenario-side-name">{side.name || side.faction}</span>
                </div>
                <table className="scenario-ship-table">
                  <thead>
                    <tr>
                      <th>Ship</th><th>Type</th><th>Hex</th>
                      <th>Hdg</th><th>Spd</th><th>WS</th><th>Refits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {side.ships.map(ship => (
                      <tr key={ship.shipName || ship.type}>
                        <td>{ship.shipName}</td>
                        <td>{ship.type}</td>
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

          {activeScenario?.victoryNotes && (
            <div className="scenario-section">
              <div className="scenario-section-title">Victory Conditions</div>
              <p className="scenario-section-text">{activeScenario.victoryNotes}</p>
            </div>
          )}

          {activeScenario && (!activeScenario.warpBoosterPacks || !activeScenario.megapacks ||
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

          {(activeScenario?.specialRules.length ?? 0) > 0 && activeScenario && (
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
