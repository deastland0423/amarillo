/**
 * Who leads a battle force while the player has not said (S8.21).
 *
 * <h2>Why there is a default at all</h2>
 * Reported from live play: nobody realised the star beside a roster row was theirs to click, so
 * every fleet came back illegal — and the message said "No flagship chosen", which names the
 * fault and not the remedy. A force with no flagship is not a force.
 *
 * <h2>Why the best commander and not the first ship bought</h2>
 * A flagship's command rating is also the ceiling on how many ships may sail with it, so "first
 * purchased" is a trap rather than a shortcut: buy an escort, then a cruiser, and the flag sits
 * on the escort while the validator reports that it can lead two ships and the fleet has five.
 * Trading one violation for a more confusing one is not a default worth having.
 *
 * Taking the best commander keeps a growing fleet legal, and it is what S8.21 describes in
 * practice — the flag goes to the ship built to carry it.
 */

/** What this decision needs to know about one ship. Everything else about a hull is irrelevant. */
export interface CommandCandidate {
  name?:         string;
  /** S8.21: how many other ships it may lead. Zero means it cannot hold the flag at all. */
  commandRating: number;
  bpv:           number;
}

/**
 * The ship that should hold the flag, or undefined when none of them can.
 *
 * Highest command rating wins; the bigger ship breaks a tie, because two hulls that lead equally
 * well are not equally the obvious flagship. An unnamed entry is skipped rather than returned as
 * undefined — `flagship` is matched BY NAME, so a nameless flagship is the same as none and
 * would leave the post looking filled while the validator called it empty.
 */
export function bestCommander(
  candidates: readonly CommandCandidate[],
): string | undefined {
  // Filtered FIRST, which also makes the sort safe: `filter` hands back a new array, so this
  // never reorders the caller's roster. Sorting `spec.ships` in place would be a React state
  // mutation, and the kind that only shows up as a stale render.
  const ranked = candidates
    .filter(c => c.commandRating > 0 && !!c.name)
    .sort((a, b) => (b.commandRating - a.commandRating) || (b.bpv - a.bpv));
  return ranked[0]?.name;
}
