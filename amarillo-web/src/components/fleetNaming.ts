/**
 * Names for ships as they are bought.
 *
 * <h2>Why the builder has to care</h2>
 * Every ship needs its own name, and not as a matter of taste: a name is how the whole game
 * addresses a unit — fire orders, lock-ons, tractor targets, the map DTO, each player's redacted
 * view — so two ships called the same thing means the second cannot be given an order.
 * `FleetValidator.checkDistinctNames` makes it an ERROR for exactly that reason.
 *
 * Buying three Klingon F5s used to hand you three "IKS Fury" and that error, to be fixed by hand
 * before the fleet was legal. The builder should not manufacture a problem it can avoid: the
 * first of a kind keeps the hull's own name and the rest are numbered.
 */

/** The validator compares trimmed and case-insensitively, so this must too. */
const key = (s: string) => s.trim().toLowerCase();

/** Type codes contain "+" and "-" — regex metacharacters — so CA+ must not match "CAAA". */
const escapeRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\-]/g, '\\$&');

/**
 * What to call a newly bought hull, given what the fleet already carries.
 *
 * The hull's own name if it is free, otherwise "<type> #N" at the lowest N from 2 that nobody is
 * using. Lowest rather than a count, because a count repeats itself: buy three, delete the second,
 * buy a fourth, and counting hands you "#3" a second time — the duplicate this exists to prevent.
 */
export function nameForNewShip(type: string, defaultName: string, taken: string[]): string {
  const used = new Set(taken.filter(n => n != null && n.trim() !== '').map(key));
  const own = (defaultName ?? '').trim();
  if (own !== '' && !used.has(key(own))) return own;
  for (let n = 2; ; n++) {
    const candidate = `${type} #${n}`;
    if (!used.has(key(candidate))) return candidate;
  }
}

/**
 * Does this name look generated rather than chosen?
 *
 * Derived from the shape rather than stored on the entry, so it needs no new field on the wire
 * and survives a save and reload. A player who types "F5 #2" by hand is indistinguishable, which
 * costs nothing: the worst case is a hint suggesting they rename a ship they just named.
 */
export function isGeneratedName(type: string, name: string | undefined): boolean {
  if (!name) return false;
  return new RegExp(`^${escapeRegex(type)} #\\d+$`, 'i').test(name.trim());
}
