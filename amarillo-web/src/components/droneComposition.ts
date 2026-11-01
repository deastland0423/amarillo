import type { CoiDroneType, CoiDroneModule, CoiDroneAddition } from '../api/gameApi';

/**
 * A drone as the COI loadout names one (FD10.2): the catalogue drone it is, plus what was done
 * to it.
 *
 * This is the client half of core's `DroneLoad`, and it is a SPELLING, not a model. Every rule
 * about what may be built lives on the server — which modules a drone accepts, which may share
 * a drone with which, what the result weighs, costs or survives. Nothing here derives any of it.
 * All this does is take the string apart and put it back together, so the editor can show a
 * player what they have ticked.
 */
export interface DroneComposition {
  /** The catalogue drone's name, e.g. `TypeIM`. */
  basis: string;
  /** Payload modules, which may repeat: FD12.12's armored type-IV carries three of one. */
  modules: string[];
  /** FD10.53's frame additions. At most one of them is external armor (FD12.131). */
  additions: string[];
  /** FD8.11's engine for the submunitions, bought separately from the bus's (FD8.24). */
  submunitionSpeed: string | null;
  /**
   * Tokens this client did not recognise, kept so that editing a drone cannot silently drop
   * part of it. A name can arrive carrying something the catalogue does not offer as a choice —
   * a type-III's own frame surcharge is spelled out by core's formatter and is deliberately not
   * on offer, because it is what makes the drone a type-III rather than an option.
   */
  passthrough: string[];
}

/** Separates the basis from each modification, as core's `DroneLoad.SEPARATOR` does. */
const SEPARATOR = '+';

/** The drone as it is, with nothing done to it. */
export function plainDrone(basis: string): DroneComposition {
  return { basis, modules: [], additions: [], submunitionSpeed: null, passthrough: [] };
}

export function isModified(c: DroneComposition): boolean {
  return c.modules.length > 0 || c.additions.length > 0
      || c.submunitionSpeed != null || c.passthrough.length > 0;
}

/**
 * Take a loadout name apart.
 *
 * Each modifier is routed by looking it up in the catalogues the server sent, which is the same
 * division of labour as everywhere else here: the server knows what the parts ARE and this only
 * has to tell them apart. A token in none of them is kept verbatim rather than dropped.
 */
export function parseDroneName(
  name:      string,
  modules:   CoiDroneModule[],
  additions: CoiDroneAddition[],
): DroneComposition {
  const parts = name.split(SEPARATOR);
  const out = plainDrone(parts[0]);
  for (const token of parts.slice(1)) {
    if (modules.some(m => m.name === token))        out.modules.push(token);
    else if (additions.some(a => a.name === token)) out.additions.push(token);
    // A SPEED_ token can only be the submunitions' engine: the drone's own comes from its basis,
    // which is core's rule for the same grammar (DroneLoad.parse).
    else if (token.startsWith('SPEED_'))            out.submunitionSpeed = token;
    else                                            out.passthrough.push(token);
  }
  return out;
}

/**
 * Put it back together, in the order core's formatter uses so that a name written here and a
 * name written there are the same string.
 *
 * An unmodified drone is just its name, which is what every loadout the dialog sent before
 * payload modules existed already said.
 */
export function formatDroneName(c: DroneComposition): string {
  if (!isModified(c)) return c.basis;
  const parts = [c.basis, ...c.modules, ...c.additions];
  if (c.submunitionSpeed) parts.push(c.submunitionSpeed);
  parts.push(...c.passthrough);
  return parts.join(SEPARATOR);
}

/**
 * Payload spaces the chosen modules fill (FD10.4).
 *
 * Modules only — FD12.13's external armor hangs outside the payload, "i.e., in excess of the
 * normal payload space limit", and pays for itself in speed instead (FD12.132).
 */
export function payloadUsed(c: DroneComposition, modules: CoiDroneModule[]): number {
  return c.modules.reduce(
    (sum, name) => sum + (modules.find(m => m.name === name)?.spaces ?? 0), 0);
}

/**
 * Rack spaces one loaded drone fills (FD3.0).
 *
 * The frame's footprint, except where FD10.24 changes the frame: a type-III given extended range
 * "become[s] type-III-XX, which are two-space drones with one payload space". That is the only
 * modification in the game that changes how much of a rack a drone takes, and the figure is sent
 * by the server rather than worked out here — a view deciding that a frame had changed would be a
 * view deciding a rules question.
 */
export function rackSpacesOf(c: DroneComposition, types: CoiDroneType[]): number {
  const dt = types.find(t => t.name === c.basis);
  if (!dt) return 0;
  if (c.additions.includes('EXTENDED_RANGE') && dt.rackWithExtendedRange != null)
    return dt.rackWithExtendedRange;
  return dt.rack;
}

/** How this drone reads on a chip: the basis, then each module and addition by its label. */
export function describeDrone(
  c:         DroneComposition,
  modules:   CoiDroneModule[],
  additions: CoiDroneAddition[],
): string {
  if (!isModified(c)) return c.basis;
  const labels = [
    ...c.modules.map(n => modules.find(m => m.name === n)?.label ?? n),
    ...c.additions.map(n => additions.find(a => a.name === n)?.label ?? n),
  ];
  return `${c.basis} · ${labels.join(', ')}`;
}

/**
 * Why this module cannot be added to the drone as it stands, or null if it can.
 *
 * <h2>Why these live here and not in the editor's JSX</h2>
 * They were in the JSX, and one of them was missing: FD12.131 bars external armor from a drone
 * that already has internal armor, and the editor had only FD10.443's ECM check, which is a
 * different rule about a different thing. It took a test comparing the dialog's own strings
 * against core's parser to find it, because nothing on this side could be asked the question.
 *
 * These are ADVICE, not a gate. The gate is core, which refuses an illegal composition with its
 * clause number whatever the client believed; what these buy is a button that greys out before
 * the player builds something they will be told off for. Every clause here is one core enforces,
 * and {@code DroneLoadTest} pins each of them at that end.
 */
export function moduleRefusal(
  c:        DroneComposition,
  module:   CoiDroneModule,
  modules:  CoiDroneModule[],
  additions: CoiDroneAddition[],
  payloadSpaces: number,
): string | null {
  const hasInternalArmor = c.modules.some(
    n => modules.find(m => m.name === n)?.armor ?? false);
  const hasExternalArmor = c.additions.some(
    n => additions.find(a => a.name === n)?.externalArmor ?? false);
  const hasEcm = c.modules.includes('ECM');

  // FD10.443: "The ECM module can... be combined with any other module in the game except an
  // (internal or external) armor module. ECM drones will not work with armor modules."
  if (module.name === 'ECM' && (hasInternalArmor || hasExternalArmor))
    return 'An ECM module cannot share a drone with armor (FD10.443)';
  if (module.armor && hasEcm)
    return 'An ECM module cannot share a drone with armor (FD10.443)';

  // FD12.131: "External armor cannot be placed on a drone equipped with internal armor." Asked
  // of the module as well as of the addition, or the order of the clicks would decide whether
  // the drone is legal {EM} bolt the armor on first and an internal module could still go under
  // it.
  if (module.armor && hasExternalArmor)
    return 'External armor cannot go on a drone with internal armor (FD12.131)';

  // FD10.4: the payload has to fit.
  if (module.spaces > payloadSpaces - payloadUsed(c, modules))
    return 'No payload space left (FD10.4)';

  // FD10.402: "Some warhead modules can only be placed in the forward space", so a drone cannot
  // hold more than one payload space of them {EM} unless one module spans the whole payload,
  // which is forward by occupying everything (FD10.432 writes "space(s)" for exactly this).
  if (module.requiresForward && module.spaces < payloadSpaces) {
    const forwardTaken = c.modules.reduce((sum, n) => {
      const m = modules.find(x => x.name === n);
      return sum + (m?.requiresForward ? m.spaces : 0);
    }, 0);
    if (forwardTaken + module.spaces > 1.0)
      return 'This module must be in the forward payload space, and that space is taken'
           + ' (FD10.402)';
  }
  return null;
}

/**
 * Why this frame addition cannot be ticked, or null if it can.
 *
 * @param rackSpaceFree rack spaces this drone may grow into, its own footprint included
 * @param rackWithExtendedRange what the server says it would fill given extended range (FD10.24)
 */
export function additionRefusal(
  c:         DroneComposition,
  addition:  CoiDroneAddition,
  modules:   CoiDroneModule[],
  rackSpaces: number,
  rackSpaceFree: number,
  rackWithExtendedRange: number | undefined,
): string | null {
  if (c.additions.includes(addition.name)) return null;   // already on; untick is always allowed

  const hasInternalArmor = c.modules.some(
    n => modules.find(m => m.name === n)?.armor ?? false);
  const hasEcm = c.modules.includes('ECM');

  if (addition.externalArmor && hasEcm)
    return 'External armor cannot go on an ECM drone (FD10.443)';
  if (addition.externalArmor && hasInternalArmor)
    return 'External armor cannot go on a drone with internal armor (FD12.131)';

  // FD10.24: a type-III given extended range "become[s] type-III-XX, which are two-space drones
  // with one payload space" {EM} the one modification that changes a drone's footprint, so it
  // has to fit before it can be offered. The grown figure comes from the server.
  if (addition.name === 'EXTENDED_RANGE' && rackWithExtendedRange != null
      && rackWithExtendedRange > rackSpaces && rackWithExtendedRange > rackSpaceFree)
    return 'A type-III with extended range is a two-space drone (FD10.24), and the rack has'
         + ' not the room';

  // FD12.131: "No more than one external armor module can be added to any one drone." Not a
  // refusal {EM} the editor swaps one for the other, which is what a player ticking the second
  // one means.
  return null;
}
