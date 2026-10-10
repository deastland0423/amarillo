import { useCallback, useEffect, useState } from 'react';
import Lobby from './components/Lobby';
import type { LobbyResult } from './components/Lobby';
import PreGame from './components/PreGame';
import FleetBuilder from './components/FleetBuilder';
import GameBoard from './components/GameBoard';
import { gameApi } from './api/gameApi';
import './App.css';

type Screen = 'lobby' | 'pregame' | 'game' | 'fleet';

const SESSION_KEY = 'amarillo_session';

function saveSession(result: LobbyResult) {
  localStorage.setItem(SESSION_KEY, JSON.stringify(result));
}

function clearSession() {
  localStorage.removeItem(SESSION_KEY);
}

function loadSession(): LobbyResult | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? JSON.parse(raw) as LobbyResult : null;
  } catch {
    return null;
  }
}

export default function App() {
  const [screen,  setScreen]  = useState<Screen>('lobby');
  const [session, setSession] = useState<LobbyResult | null>(null);
  // Seeded from storage rather than switched on inside the effect below. Setting it there was a
  // synchronous setState in an effect, which renders once with the spinner off and again with it
  // on — a flash of the lobby before the resume check has even started. A lazy initialiser gets
  // the first render right instead.
  const [resuming, setResuming] = useState(() => loadSession() != null);
  // Fleet building needs no game and no session — just a name to sign the work.
  const [builderName, setBuilderName] = useState('');

  /**
   * Take the saved seat back: check the game still exists, then go to whichever screen it is on.
   *
   * Shared by the mount check and the lobby's Resume button, because they are the same question
   * asked at different moments. It must not be confused with Join: `joinGame` mints a NEW player
   * token and seats it as a non-host, so a host who rejoined by game ID would come back as a
   * guest with their ships still bound to the token they left behind. The saved token IS the seat.
   */
  const resumeSaved = useCallback(() => {
    const saved = loadSession();
    if (!saved) return;
    // No setResuming(true) here. On mount the lazy initialiser above has already put it true,
    // and setting state synchronously inside an effect is what this component was just cleaned
    // of. The Resume BUTTON raises it instead, since it fires from a click where nothing else
    // has — see where onResume is passed below.
    gameApi.getStatus(saved.gameId)
      .then(status => {
        setSession(saved);
        setScreen(status.started ? 'game' : 'pregame');
      })
      .catch(() => {
        // Game no longer exists on the server — clear stale session.
        clearSession();
      })
      .finally(() => setResuming(false));
  }, []);

  // On mount, check for a saved session and verify it's still alive.
  useEffect(resumeSaved, [resumeSaved]);

  function handleJoined(result: LobbyResult) {
    saveSession(result);
    setSession(result);
    setScreen('pregame');
  }

  function handleLeave() {
    // Keep the saved session: the player token is the only key back to this
    // seat (ships are bound to it), so Leave must stay resumable. Stale
    // sessions still self-clean via the resume check when the game is gone,
    // and joining another game overwrites the slot.
    setSession(null);
    setScreen('lobby');
  }

  if (resuming) {
    return <div className="lobby"><p className="subtitle">Reconnecting…</p></div>;
  }

  if (screen === 'lobby') {
    return (
      <Lobby
        onJoined={handleJoined}
        onBuildFleet={n => { setBuilderName(n); setScreen('fleet'); }}
        /* Leave keeps the saved seat on purpose, so the lobby has to offer it back — otherwise
           leaving to build a fleet strands you, which is exactly how this was found. Read at
           render rather than held in state: the lobby only renders when there is no live
           session, and a localStorage read is cheaper than a value that could go stale. */
        savedSession={loadSession()}
        onResume={() => { setResuming(true); resumeSaved(); }}
      />
    );
  }

  if (screen === 'fleet') {
    return <FleetBuilder playerName={builderName} onLeave={() => setScreen('lobby')} />;
  }

  if (screen === 'pregame' && session) {
    return (
      <PreGame
        session={session}
        onGameStarted={() => setScreen('game')}
        onLeave={handleLeave}
      />
    );
  }

  return <GameBoard session={session!} onLeave={handleLeave} />;
}
