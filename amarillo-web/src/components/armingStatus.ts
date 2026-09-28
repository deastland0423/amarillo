import type { ShuttleInBayState } from '../types/gameState';

/**
 * How a craft's arming state is shown, wherever it is shown (J4.82, J4.83).
 *
 * One module because two panels ask the same question for different reasons: the hangar
 * colours a row to say what still needs deck crews, and the launch pad says which craft are
 * worth sending this impulse. If they disagreed — different words, different colours — the
 * player would have to learn two vocabularies for one fact.
 *
 * The STATE itself is decided in core (FighterArming.armingState) and arrives on the DTO.
 * Nothing here works out whether a craft is armed; this only decides how to describe the
 * answer. The dot that draws it is ArmingDot.tsx — kept apart because a module exporting
 * both a component and its constants breaks Fast Refresh.
 */

export type ArmingState = 'READY' | 'PARTIAL' | 'EMPTY';

export const ARMING_TITLE: Record<ArmingState, string> = {
  READY:   'Armed and ready',
  PARTIAL: 'Partly armed — deck crew work outstanding',
  EMPTY:   'Unarmed',
};

/** Short word for a row that has space for one. */
export const ARMING_WORD: Record<ArmingState, string> = {
  READY:   'armed',
  PARTIAL: 'part-armed',
  EMPTY:   'unarmed',
};

export const ARMING_COLOUR: Record<ArmingState, string> = {
  READY:   '#3fb950',
  PARTIAL: '#f0c040',
  EMPTY:   '#6e7681',
};

/** The state, or null where the question does not apply (an admin shuttle has nothing to arm). */
export function armingOf(craft: { armingState?: string } | null | undefined): ArmingState | null {
  const s = craft?.armingState;
  return s === 'READY' || s === 'PARTIAL' || s === 'EMPTY' ? s : null;
}

/** What is on a drone fighter's rails, grouped: "2x TypeI". Empty when it carries none. */
export function droneSummary(craft: ShuttleInBayState): string {
  const loaded = (craft.rails ?? []).filter(r => r.drone);
  if (loaded.length === 0) return '';
  const byType = new Map<string, number>();
  for (const r of loaded) byType.set(r.drone!, (byType.get(r.drone!) ?? 0) + 1);
  return [...byType].map(([t, n]) => (n > 1 ? `${n}x ${t}` : t)).join(', ');
}

/**
 * Rail by rail, for a tooltip — including the EMPTY rails and their sizes, because "which
 * slot is still open and what will go in it" is the question a half-loaded fighter raises.
 */
export function railDetail(craft: ShuttleInBayState): string {
  const rails = craft.rails ?? [];
  if (rails.length === 0) return '';
  return rails
    .map(r => `${(r.railType ?? '?').toLowerCase()}: ${r.drone ?? 'empty'}`)
    .join('\n');
}
