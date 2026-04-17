/**
 * Thin wrappers around the Amarillo REST API.
 * All paths are relative — Vite proxies /api → localhost:8080 in dev.
 */

export interface CreateGameResponse {
  gameId: string;
  hostToken: string;
  message: string;
}

export interface JoinGameResponse {
  playerToken: string;
  message: string;
}

export interface GameStatus {
  gameId: string;
  started: boolean;
  players: Array<{
    name: string;
    role: 'host' | 'player';
    ships: string[];
  }>;
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const { headers: extraHeaders, ...restOptions } = options ?? {};
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...extraHeaders },
    ...restOptions,
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error ?? `HTTP ${res.status}`);
  }
  return res.json();
}

export interface PlayerListing {
  name: string;
  token: string;
}

export interface ScenarioShip {
  hull:         string;
  shipName:     string;
  startHex:     string;
  startHeading: string;
  startSpeed:   number;
  weaponStatus: number;
  refits:       string[];
}

export interface ScenarioSide {
  faction:             string;
  name:                string;
  ships:               ScenarioShip[];
  reinforcementGroups: number;
}

export interface ScenarioSummary {
  id:               string;
  name:             string;
  year:             number;
  numPlayers:       number;
  mapType:          string;
  description:      string;
  specialRules:     string[];
  victoryType:      string;
  victoryNotes:     string;
  warpBoosterPacks: boolean;
  megapacks:        boolean;
  mrsShuttles:      boolean;
  pfs:              boolean;
  sides:            ScenarioSide[];
}

// ---- COI data types ----

export interface CoiHeavyWeapon {
  designator: string;
  type:       string;  // "Photon", "Disruptor", etc.
}

export interface CoiDroneRack {
  index:      number;
  designator: string;
  spaces:     number;
}

export interface CoiDroneType {
  name:   string;
  speed:  number;
  damage: number;
  rack:   number;   // spaces consumed per drone
}

export interface CoiShipData {
  shipName:            string;
  bpv:                 number;
  weaponStatus:        number;
  coiBudget:           number;
  allowTBombs:         boolean;
  allowCommandos:      boolean;
  maxTBombs:           number;
  maxDroneSpeed:       number | null;
  heavyWeapons:        CoiHeavyWeapon[];
  droneRacks:          CoiDroneRack[];
  availableDroneTypes: CoiDroneType[];
}

export interface CoiSideData {
  faction: string;
  name:    string;
  ships:   CoiShipData[];
}

/** Per-ship COI selections to POST to /api/games/{id}/coi */
export interface CoiSubmission {
  [shipName: string]: {
    extraBoardingParties?: number;
    convertBpToCommando?:  number;
    extraCommandoSquads?:  number;
    extraTBombs?:          number;
    droneRackLoadouts?:    Record<string, string[]>;
    weaponArmingModes?:    Record<string, 'STANDARD' | 'OVERLOAD' | 'SPECIAL'>;
  };
}

export const gameApi = {
  createGame(hostName: string): Promise<CreateGameResponse> {
    return request('/api/games', {
      method: 'POST',
      body: JSON.stringify({ name: hostName }),
    });
  },

  joinGame(gameId: string, playerName: string): Promise<JoinGameResponse> {
    return request(`/api/games/${gameId}/join`, {
      method: 'POST',
      body: JSON.stringify({ name: playerName }),
    });
  },

  getStatus(gameId: string): Promise<GameStatus> {
    return request(`/api/games/${gameId}/status`);
  },

  listScenarios(): Promise<ScenarioSummary[]> {
    return request('/api/games/scenarios');
  },

  startGame(gameId: string, playerToken: string, scenarioId: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/start`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({ scenarioId }),
    });
  },

  /** Host-only: returns the full player list including tokens (needed for ship assignment). */
  getPlayers(gameId: string, hostToken: string): Promise<PlayerListing[]> {
    return request(`/api/games/${gameId}/players`, {
      headers: { 'X-Player-Token': hostToken },
    });
  },

  assignShip(gameId: string, hostToken: string, playerToken: string, shipName: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/assign`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify({ playerToken, shipName }),
    });
  },

  getGameState(gameId: string, playerToken: string): Promise<unknown> {
    return request(`/api/games/${gameId}/state`, {
      headers: { 'X-Player-Token': playerToken },
    });
  },

  getFireOptions(
    gameId: string,
    playerToken: string,
    attacker: string,
    target: string,
  ): Promise<{ range: number; adjustedRange: number; shieldNumber: number; weaponsInArc: string[] }> {
    return request(
      `/api/games/${gameId}/fire-options?attacker=${encodeURIComponent(attacker)}&target=${encodeURIComponent(target)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
  },

  getCoiData(scenarioId: string): Promise<CoiSideData[]> {
    return request(`/api/games/scenarios/${scenarioId}/coi-data`);
  },

  submitCoi(gameId: string, playerToken: string, body: CoiSubmission): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/coi`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify(body),
    });
  },

  submitAction(
    gameId: string,
    playerToken: string,
    body: Record<string, unknown>,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify(body),
    });
  },
};
