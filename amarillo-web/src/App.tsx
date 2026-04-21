import { useState, useEffect } from 'react';
import Lobby from './components/Lobby';
import type { LobbyResult } from './components/Lobby';
import PreGame from './components/PreGame';
import GameBoard from './components/GameBoard';
import { gameApi } from './api/gameApi';
import './App.css';

type Screen = 'lobby' | 'pregame' | 'game';

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
    clearSession();
    setSession(null);
    setScreen('lobby');
  }

  if (resuming) {
    return <div className="lobby"><p className="subtitle">Reconnecting…</p></div>;
  }

  if (screen === 'lobby') {
    return <Lobby onJoined={handleJoined} />;
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
