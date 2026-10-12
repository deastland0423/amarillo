/**
 * P2.713 ballistic bombardment of a ground base, mirrored for the launch pad.
 *
 * Core owns the rule and refuses an illegal order authoritatively; this exists so the player is
 * told WHY before they draft one, rather than discovering the four-hex limit by having an order
 * come back rejected. Same relationship as `weaponDamageTables.ts` has to the damage rules.
 *
 * The rule, in full: "Seeking weapons can be fired at a ground base using ballistic targeting
 * (F4.0), but only from a range of 4 hexes or less. Weapons fired in this manner cannot be
 * distracted by ECM or WW. Weapons fired ballistically at ground targets do explode on impact.
 * Weapons cannot be fired for ballistic bombardment under passive fire control (D19.0) as target
 * tracking is inadequate."
 */

/** P2.713's reach. Named because it appears in the refusal text as well as the test. */
export const BOMBARDMENT_RANGE = 4;

/**
 * The face of the planet a base stands on, as the player sees it: side 1 is A, side 6 is F.
 *
 * The same mapping the deployment headings use, and the reason the face is shown at all is that
 * a hex cannot identify a base — a radius-zero planet keeps all six faces' bases in its own hex,
 * so without the letter a player could order an attack on a base that is not where they think.
 */
export function faceLetter(face: number | undefined): string | null {
  if (face == null || face < 1 || face > 6) return null;
  return String.fromCharCode('A'.charCodeAt(0) + face - 1);
}

/** How a ground base should be named in a target list: "Ground Battery (face B)". */
export function groundBaseLabel(name: string, face: number | undefined): string {
  const letter = faceLetter(face);
  return letter ? `${name} (face ${letter})` : name;
}

export interface BombardmentTarget {
  range: number;
  groundBase?: boolean;
}

export interface BombardmentAttacker {
  activeFireControl?: boolean;
}

/**
 * Why this ship cannot bombard this base right now, or null if it can.
 *
 * Returns the RANGE refusal first because it is the one a player can do something about: close
 * the distance. The fire control refusal is second and is the surprising one — ordinary ballistic
 * targeting is open to a ship on passive fire control (D19.224), and only a bombardment is not.
 */
export function bombardmentRefusal(
  target: BombardmentTarget | null | undefined,
  attacker: BombardmentAttacker | null | undefined,
): string | null {
  if (!target?.groundBase) return null;
  if (target.range > BOMBARDMENT_RANGE)
    return `out of reach — a ground base can only be bombarded from ${BOMBARDMENT_RANGE}`
         + ` hexes or less, and this is ${target.range} (P2.713)`;
  // Jackson serialises an absent boolean as null rather than omitting it, so this must be an
  // explicit false test and never a falsy one: `!attacker.activeFireControl` would read an
  // unknown state as passive and hide the order for no reason.
  if (attacker && attacker.activeFireControl === false)
    return 'passive fire control — target tracking is inadequate for ballistic'
         + ' bombardment (P2.713)';
  return null;
}
