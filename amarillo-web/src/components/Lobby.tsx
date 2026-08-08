import { useState } from 'react';
import { gameApi } from '../api/gameApi';

export interface LobbyResult {
  gameId: string;
  playerToken: string;
  isHost: boolean;
  playerName: string;
}

interface Props {
  onJoined: (result: LobbyResult) => void;
}

type Mode = 'choose' | 'create' | 'join';

export default function Lobby({ onJoined }: Props) {
  const [mode, setMode] = useState<Mode>('choose');
  const [name, setName] = useState('');
  const [gameId, setGameId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

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
      onJoined({ gameId: gameId.trim().toUpperCase(), playerToken: res.playerToken, isHost: false, playerName: name.trim() });
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
          <div className="button-row">
            <button onClick={() => { setError(''); setMode('create'); }}>Host a game</button>
            <button onClick={() => { setError(''); setMode('join'); }}>Join a game</button>
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
