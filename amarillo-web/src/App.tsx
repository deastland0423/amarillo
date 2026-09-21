import { useState, useEffect } from 'react';
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
  const [resuming, setResuming] = useState(false);
  // Fleet building needs no game and no session — just a name to sign the work.
  const [builderName, setBuilderName] = useState('');

  // On mount, check for a saved session and verify it's still alive.
  useEffect(() => {
    const saved = loadSession();
    if (!saved) return;

    setResuming(true);
    gameApi.getStatus(saved.gameId)
      .then(status => {
        if (!status.started) {
          setSession(saved);
          setScreen('pregame');
        } else {
          setSession(saved);
          setScreen('game');
        }
      })
      .catch(() => {
        // Game no longer exists on the server — clear stale session.
        clearSession();
      })
      .finally(() => setResuming(false));
  }, []);

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
