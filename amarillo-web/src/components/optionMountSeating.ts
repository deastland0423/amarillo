/**
 * Which option mounts a set of choices actually occupies (G15.4).
 *
 * <h2>Why a picker needs this</h2>
 * Annex #8B marks eight options with an asterisk — both ESGs, the four plasma-G/S entries, the
 * PPD and the stasis field generator — meaning "two adjacent centerline mounts". Core has always
 * seated them correctly, but the picker did not know the rule existed: fit an ESG in mount A of a
 * CA and mount B still read "(empty)" with a full list of options, the row still counted
 * `mounts 1/4`, and a second choice made for B was discovered only at load, where
 * `ScenarioLoader.equipDeclaredMounts` notes it and skips it. The player got a fleet different
 * from the one they bought, and nothing said so.
 *
 * <h2>The same rule as the server's</h2>
 * This mirrors `OptionMountLoadout.seatFor`: a window of consecutive mounts inside ONE run,
 * every mount in it free and allowing the weapon's position, lowest start first so the chosen
 * mount keeps whichever neighbour is actually open. Order within a run is meaningful — on the
 * Battle Raider's A-B-C, a two-mount weapon seats on A+B or B+C and never on A+C, because B lies
 * between them.
 *
 * <p>Choices are seated in the order they were made, because that is the order
 * `equipDeclaredMounts` walks the (insertion-ordered) map in. A picker that seated them in a
 * different order could show an arrangement the server would not produce.
 */

export interface SeatingOption {
  name:            string;
  mountsRequired?: number;
}

/**
 * Whether an option may sit in a given mount at all.
 *
 * <p>Deliberately asked of the DATA rather than recomputed: the server lists each mount's
 * `legalOptions` by running `OptionMountLoadout.validate` against a fresh hull, so an option's
 * presence there already answers position (G15.43), hull size and service year. Mirroring those
 * three rules here would be a second implementation free to disagree with the first.
 */
export type LegalIn = (designator: string, option: string) => boolean;

/** What one mount is holding once every choice has been seated. */
export interface Occupancy {
  /** The option's name. */
  option:    string;
  /** False for the second half of a two-mount weapon: it is filled, but not where you chose it. */
  primary:   boolean;
  /** The mount the choice was actually made in, for a continuation to point back at. */
  chosenAt:  string;
}

/**
 * The window a choice at `designator` would occupy, or null if it does not fit.
 *
 * Exported for the picker, which greys an option out when nothing is free to seat it in.
 */
export function windowFor(
  designator: string,
  option: SeatingOption,
  runs: string[][],
  taken: ReadonlySet<string>,
  legalIn: LegalIn,
): string[] | null {
  const wanted = Math.max(1, option.mountsRequired ?? 1);
  const free = (d: string) => !taken.has(d) && legalIn(d, option.name);

  if (wanted === 1) return free(designator) ? [designator] : null;

  for (const run of runs) {
    const at = run.indexOf(designator);
    if (at < 0) continue;
    for (let start = Math.max(0, at - wanted + 1); start + wanted <= run.length; start++) {
      if (start > at) break;
      const seat = run.slice(start, start + wanted);
      if (seat.every(free)) return seat;
    }
  }
  return null;
}

/**
 * Seat every choice and report what each mount ends up holding.
 *
 * `chosen` is designator → option name, in the order the choices were made. A choice that cannot
 * be seated is left out of the result entirely — the picker shows it as the problem it is rather
 * than drawing a weapon into mounts the server will refuse.
 */
export function seatAll(
  runs: string[][],
  chosen: Record<string, string>,
  optionFor: (designator: string, name: string) => SeatingOption | undefined,
  legalIn: LegalIn,
): Map<string, Occupancy> {
  const held = new Map<string, Occupancy>();
  const taken = new Set<string>();
  for (const [designator, name] of Object.entries(chosen)) {
    if (!name) continue;
    const option = optionFor(designator, name);
    if (!option) continue;
    const seat = windowFor(designator, option, runs, taken, legalIn);
    if (!seat) continue;
    for (const d of seat) {
      taken.add(d);
      held.set(d, { option: name, primary: d === designator, chosenAt: designator });
    }
  }
  return held;
}
