/**
 * The scenario briefing, as the player aid shows it.
 *
 * Everything here is read straight off the server's answer, which reads it off the scenario
 * file. Nothing is computed: an edge a ship may leave by is a rule, and a rule the client works
 * out for itself is a rule that can disagree with the one being enforced.
 */

/** Which way out of the map, in the words the scenario file uses. */
export type MapEdge = 'TOP' | 'BOTTOM' | 'LEFT' | 'RIGHT';

export interface BriefingSide {
  name:              string;
  faction:           string;
  /** Whether this is the viewer's side, decided by the server from the player token. */
  yours:             boolean;
  /** Edges this side may disengage across. */
  exitEdges:         MapEdge[];
  /** Edges that destroy it instead. */
  destructionEdges:  MapEdge[];
  ships:             string[];
}

export interface BriefingData {
  name:          string;
  description:   string;
  specialRules:  string[];
  victoryType?:  string;
  victoryNotes?: string;
  maxTurns?:     number;
  sides:         BriefingSide[];
}

/**
 * Where an edge is, in words a player can act on without counting columns.
 *
 * The scenario prints "the 42xx map edge" and the file says RIGHT; neither phrase is much use
 * while staring at a map, so this says both — the side of the screen, and the row or column
 * number that edge actually is. The numbers come from the map size rather than being baked in,
 * since not every scenario is 42 × 30.
 */
export function edgeLabel(edge: MapEdge, mapCols: number, mapRows: number): string {
  switch (edge) {
    case 'LEFT':   return `left (column 1)`;
    case 'RIGHT':  return `right (column ${mapCols})`;
    case 'TOP':    return `top (row 1)`;
    case 'BOTTOM': return `bottom (row ${mapRows})`;
  }
}

/** The viewer's own side, or null when the game has no scenario or no seat for them. */
export function yourSide(data: BriefingData | null): BriefingSide | null {
  return data?.sides.find(s => s.yours) ?? null;
}
