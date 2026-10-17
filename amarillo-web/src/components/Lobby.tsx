import { useEffect, useState } from 'react';
import { gameApi } from '../api/gameApi';
import type { SavedBattle } from '../api/gameApi';

export interface LobbyResult {
  gameId: string;
  playerToken: string;
  isHost: boolean;
  playerName: string;
}

interface Props {
  onJoined: (result: LobbyResult) => void;
  /** Named so the builder can stamp who built a fleet. */
  onBuildFleet: (playerName: string) => void;
  /**
   * A seat this browser already holds, if any — so leaving a game to do something else is not
   * a one-way trip.
   *
   * NOT the same as joining by game ID, which is why it is offered separately and first:
   * `joinGame` mints a new player token and seats it as a guest, so a host who came back that
   * way would arrive as a guest with their ships bound to the token they left. The saved token
   * is the seat itself.
   */
  savedSession?: LobbyResult | null;
  onResume?: () => void;
  /**
   * A saved BATTLE was opened, which is a different thing from resuming a seat.
   *
   * `onResume` above takes this browser's own saved token back. This hands over a battle restored
   * from a file: a brand-new game with a new code, whose ships belong to nobody yet. The caller
   * has to land the host on the assignment screen rather than the map, because a restored battle
   * is already "started" and the map has no way to hand out ships.
   */
  onOpenedSave?: (result: LobbyResult) => void;
}

type Mode = 'choose' | 'create' | 'join' | 'saves';

export default function Lobby({ onJoined, onBuildFleet, savedSession, onResume,
                               onOpenedSave }: Props) {
  const [mode, setMode] = useState<Mode>('choose');
  const [name, setName] = useState('');
  const [gameId, setGameId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [saves, setSaves] = useState<SavedBattle[] | null>(null);

  // Fetched when the saves door is opened rather than on mount: most visits never look at it,
  // and a landing page should not wait on a directory listing to render.
  useEffect(() => {
    if (mode !== 'saves' || saves !== null) return;
    gameApi.listSaves()
      .then(setSaves)
      .catch(() => setSaves([]));
  }, [mode, saves]);

  async function handleOpenSave(saveId: string) {
    if (!name.trim()) { setError('Enter your name first.'); return; }
    setBusy(true); setError('');
    try {
      const res = await gameApi.openSave(saveId, name.trim());
      // Problems are not failures: a battle can load usefully while still having lost a craft in
      // flight, or with a hull that has been edited since. Show them and continue.
      if (res.problems.length > 0)
        setError(res.problems.join(' · '));
      onOpenedSave?.({
        gameId: res.gameId,
        playerToken: res.hostToken,
        isHost: true,
        playerName: name.trim(),
      });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not open that battle.');
    } finally {
      setBusy(false);
    }
  }

  async function handleCreate() {
    if (!name.trim()) { setError('Enter your name first.'); return; }
    setBusy(true); setError('');
    try {
      const res = await gameApi.createGame(name.trim());
      onJoined({ gameId: res.gameId, playerToken: res.hostToken, isHost: true, playerName: name.trim() });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not create game.');
    } finally {
      setBusy(false);
    }
  }

  async function handleJoin() {
    if (!name.trim()) { setError('Enter your name first.'); return; }
    if (!gameId.trim()) { setError('Enter a game ID.'); return; }
    setBusy(true); setError('');
    try {
      const res = await gameApi.joinGame(gameId.trim().toUpperCase(), name.trim());
      // isHost comes from the SERVER, never assumed false. A join now returns the seat this
      // browser's account already holds, and that seat may be the host's — someone who left
      // their own game and came back by code must not return as a guest locked out of the
      // scenario picker they were using. Strings, because the endpoint returns Map<String,String>.
      onJoined({
        gameId: gameId.trim().toUpperCase(),
        playerToken: res.playerToken,
        isHost: res.isHost === 'true',
        playerName: name.trim(),
      });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Could not join game.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="lobby">
      <h1>Amarillo</h1>
      <p className="subtitle">Star Fleet Battles</p>

      {mode === 'choose' && (
        <div className="card">
          <div className="name-row">
            <label htmlFor="player-name">Your name</label>
            <input
              id="player-name"
              type="text"
              placeholder="Admiral..."
              value={name}
              onChange={e => setName(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && setMode('create')}
              autoFocus
            />
          </div>
          {error && <p className="error">{error}</p>}
          {savedSession && onResume && (
            <div className="button-row">
              <button onClick={onResume}>
                Resume game {savedSession.gameId}
              </button>
            </div>
          )}
          <div className="button-row">
            <button onClick={() => { setError(''); setMode('create'); }}>Host a game</button>
            <button onClick={() => { setError(''); setMode('join'); }}>Join a game</button>
          </div>
          <div className="button-row">
            <button className="secondary" onClick={() => {
              if (!name.trim()) { setError('Enter your name first.'); return; }
              setError('');
              onBuildFleet(name.trim());
            }}>Build a fleet</button>
            {/* A saved BATTLE, not this browser's seat — Resume above takes a seat back, this
                reopens a game from a file, possibly with different players. */}
            <button className="secondary" onClick={() => { setError(''); setMode('saves'); }}>
              Resume a saved battle
            </button>
          </div>
        </div>
      )}

      {mode === 'create' && (
        <div className="card">
          <p>Hosting as <strong>{name || '(no name)'}</strong></p>
          {error && <p className="error">{error}</p>}
          <div className="button-row">
            <button onClick={handleCreate} disabled={busy}>
              {busy ? 'Creating…' : 'Create game'}
            </button>
            <button className="secondary" onClick={() => { setError(''); setMode('choose'); }}>Back</button>
          </div>
        </div>
      )}

      {mode === 'saves' && (
        <div className="card">
          <p>Resuming as <strong>{name || '(no name)'}</strong></p>
          {saves === null && <p className="subtitle">Looking for saved battles…</p>}
          {saves !== null && saves.length === 0 && (
            <p className="subtitle">
              No saved battles yet. A host can save a game between turns.
            </p>
          )}
          {saves !== null && saves.map(save => (
            <div key={save.id} className="assign-row">
              <span className="assign-ship">
                <strong>{save.label || save.id}</strong>
                <span className="fb-hint" style={{ marginLeft: 6 }}>
                  {save.readable
                    ? [save.scenario, save.turn != null ? `turn ${save.turn}` : null]
                        .filter(Boolean).join(' · ')
                    /* Listed even when unreadable, with the reason. Hiding it would leave a
                       player staring at a file on disk and no explanation. */
                    : `unreadable — ${save.error ?? 'unknown problem'}`}
                </span>
              </span>
              <button
                onClick={() => handleOpenSave(save.id)}
                disabled={busy || !save.readable}
                title={save.readable ? 'Open this battle as a new game'
                                     : 'This build cannot read that file'}
              >
                {busy ? 'Opening…' : 'Resume'}
              </button>
            </div>
          ))}
          {error && <p className="error">{error}</p>}
          <div className="button-row">
            <button className="secondary" onClick={() => { setError(''); setMode('choose'); }}>
              Back
            </button>
          </div>
        </div>
      )}

      {mode === 'join' && (
        <div className="card">
          <div className="name-row">
            <label htmlFor="game-id">Game ID</label>
            <input
              id="game-id"
              type="text"
              placeholder="ABC123"
              value={gameId}
              onChange={e => setGameId(e.target.value.toUpperCase())}
              onKeyDown={e => e.key === 'Enter' && handleJoin()}
              autoFocus
            />
          </div>
          <p>Joining as <strong>{name || '(no name)'}</strong></p>
          {error && <p className="error">{error}</p>}
          <div className="button-row">
            <button onClick={handleJoin} disabled={busy}>
              {busy ? 'Joining…' : 'Join game'}
            </button>
            <button className="secondary" onClick={() => { setError(''); setMode('choose'); }}>Back</button>
          </div>
        </div>
      )}
    </div>
  );
}
