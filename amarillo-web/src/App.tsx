import { useState } from 'react';
import Lobby from './components/Lobby';
import type { LobbyResult } from './components/Lobby';
import PreGame from './components/PreGame';
import GameBoard from './components/GameBoard';
import './App.css';

type Screen = 'lobby' | 'pregame' | 'game';

export default function App() {
  const [screen, setScreen] = useState<Screen>('lobby');
  const [session, setSession] = useState<LobbyResult | null>(null);

  function handleJoined(result: LobbyResult) {
    setSession(result);
    setScreen('pregame');
  }

  function handleLeave() {
    setSession(null);
    setScreen('lobby');
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
