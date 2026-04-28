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
  type:       string;    // "Photon", "Disruptor", etc.
  isPlasma:   boolean;
}

export interface CoiDroneRack {
  index:        number;
  designator:   string;
  spaces:       number;
  reloadCount:  number;
  defaultAmmo:  string[];  // drone type names before any COI loadout
  canLoadTypeVI: boolean;  // only TYPE_E, TYPE_G, TYPE_H
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
  convertibleShuttles: { name: string; types: string[] }[];
  maxPreparedShuttles: number;        // WS2=1, WS3=2, else 0
}

export interface CoiSideData {
  faction: string;
  name:    string;
  ships:   CoiShipData[];
}

export interface CoiShuttlePrepEntry {
  shuttleName:    string;
  type:           string;          // "suicide" | "scatterpack" | "wildweasel"
  energyPerTurn?: number;          // suicide only
  drones?:        string[];        // scatterpack only
}

/** Per-ship COI selections to POST to /api/games/{id}/coi */
export interface CoiSubmission {
  [shipName: string]: {
    extraBoardingParties?: number;
    convertBpToCommando?:  number;
    extraCommandoSquads?:  number;
    extraTBombs?:          number;
    droneRackLoadouts?:    Record<string, string[]>;
    weaponArmingModes?:    Record<string, 'STANDARD' | 'OVERLOAD' | 'SPECIAL' | 'ROLLING'>;
    specialShuttlePrep?:   CoiShuttlePrepEntry[];
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

  getLobbyState(gameId: string): Promise<unknown> {
    return request(`/api/games/${gameId}/lobby`);
  },

  listScenarios(): Promise<ScenarioSummary[]> {
    return request('/api/games/scenarios');
  },

  loadScenario(gameId: string, hostToken: string, scenarioId: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/scenario`, {
      method: 'POST',
      headers: { 'X-Player-Token': hostToken },
      body: JSON.stringify({ scenarioId }),
    });
  },

  startGame(gameId: string, playerToken: string): Promise<{ message: string }> {
    return request(`/api/games/${gameId}/start`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
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

  getHarOptions(
    gameId: string,
    playerToken: string,
    attacker: string,
    target: string,
  ): Promise<{ code: string; label: string }[]> {
    return request(
      `/api/games/${gameId}/har-options?attacker=${encodeURIComponent(attacker)}&target=${encodeURIComponent(target)}`,
      { headers: { 'X-Player-Token': playerToken } },
    );
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

  boardingAction(
    gameId: string,
    playerToken: string,
    shipName: string,
    targetName: string,
    normalParties: number,
    commandoParties: number,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({
        type:            'BOARDING_ACTION',
        shipName,
        targetName,
        normalParties,
        commandoParties,
      }),
    });
  },

  identifySeekers(
    gameId: string,
    playerToken: string,
    shipName: string,
    seekerNames: string[],
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({ type: 'IDENTIFY_SEEKERS', shipName, seekerNames }),
    });
  },

  placeTBomb(
    gameId: string,
    playerToken: string,
    shipName: string,
    col: number,
    row: number,
    isReal: boolean,
    shieldNumber?: number,
  ): Promise<{ success: boolean; message: string }> {
    return request(`/api/games/${gameId}/action`, {
      method: 'POST',
      headers: { 'X-Player-Token': playerToken },
      body: JSON.stringify({
        type:         'PLACE_TBOMB',
        shipName,
        action:       `${col}|${row}`,
        pseudo:       !isReal,   // server treats pseudo=true as dummy
        shieldNumber: shieldNumber ?? 0,
      }),
    });
  },
};
