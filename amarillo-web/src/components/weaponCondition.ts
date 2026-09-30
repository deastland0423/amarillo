import type { WeaponState, DroneRackState } from '../types/gameState';

/**
 * What state a ship's weapon is in, in words — the vocabulary shared by every panel that has
 * to describe one.
 *
 * Written for the DAC choice dialog (D4.3), where the player is asked which of several working
 * weapons is destroyed and has to judge which is cheapest to lose. That question is
 * unanswerable from a name and an arc alone: an empty photon tube and one holding four points
 * of overloaded warhead look identical, and so do a fusion that can fire this impulse and one
 * that fired last turn and is dead weight until next.
 *
 * Nothing here works anything out. Every fact is read straight off the DTO — core decides
 * whether a weapon is armed, whether its impulse gap is satisfied (readyToFire) and whether a
 * fusion is in cooldown, and this module only chooses how to say so. In particular it does NOT
 * recompute the impulse gap from lastImpulseFired: that is a rule, it lives in core, and
 * mirroring it here is how the two tiers drift apart.
 *
 * The mode vocabulary (std/ovl/spl/roll) is the one the Fire Orders pad already uses, and is
 * exported from here so the two cannot diverge — a player should not have to learn "ovl" twice.
 */

/** How much a chip wants the eye. */
export type ChipTone =
  | 'valuable' // something real is lost with this weapon: a warhead, a load, a field
  | 'mode'     // which arming mode, where that changes what the shot is worth
  | 'spent'    // little lost: unarmed, in cooldown, out of shots
  | 'neutral'; // a plain fact

export interface WeaponChip {
  text: string;
  tone: ChipTone;
  /** Spelled out for the abbreviations, which are short enough to be cryptic. */
  title?: string;
}

/**
 * How a heavy weapon is armed, short enough to sit beside its name.
 *
 * STANDARD is included deliberately: armingType is null on a weapon that does not arm at all,
 * so with no badge for standard a phaser and a standard-armed disruptor look identical — and
 * one of them CAN be overloaded while the other cannot. Absence has to mean "this weapon has
 * no modes", so every mode says its name.
 */
export const ARMING_LABEL: Record<string, string> = {
  STANDARD: 'std',
  OVERLOAD: 'ovl',
  SPECIAL:  'spl',
  ROLLING:  'roll',
};

/**
 * Overload changes what the shot is worth, so it is the one that carries colour. Standard is
 * the same grey as the arc label beside it: present, legible, and not asking for attention.
 */
export const ARMING_COLOUR: Record<string, string> = {
  STANDARD: '#8b949e',
  OVERLOAD: '#f0c040',
  SPECIAL:  '#79c0ff',
  ROLLING:  '#8b949e',
};

const ARMING_TITLE: Record<string, string> = {
  STANDARD: 'Armed to standard strength',
  OVERLOAD: 'Overloaded — a heavier warhead at short range, and lost with the weapon',
  SPECIAL:  'Special mode',
  ROLLING:  'Rolling delay — the torpedo is held on the launcher',
};

/**
 * SPECIAL means two different things — a photon armed to proximity (E4.4) and a fusion armed to
 * suicide overload (E7.421) — and "spl" is opaque enough that the tooltip should say which. The
 * capability flags tell them apart: only a photon can go to proximity, only a fusion to suicide.
 */
function modeTitle(w: WeaponState, mode: string): string | undefined {
  if (mode !== 'SPECIAL') return ARMING_TITLE[mode];
  if (w.canProximity) return 'Proximity armed (E4.4) — lost with the tube';
  if (w.canSuicide) return 'Suicide overload (E7.421)';
  return ARMING_TITLE.SPECIAL;
}

export const CHIP_COLOUR: Record<ChipTone, string> = {
  valuable: '#f0c040',
  mode:     '#8b949e', // overridden per mode by ARMING_COLOUR
  spent:    '#6e7681',
  neutral:  '#8b949e',
};

/** Java's Integer.MAX_VALUE, which is how core says "no per-turn shot limit". */
const UNLIMITED_SHOTS = 2147483647;

function shotsAreLimited(w: WeaponState): boolean {
  return w.maxShotsPerTurn > 1 && w.maxShotsPerTurn < UNLIMITED_SHOTS;
}

/**
 * Whether the server told us how this weapon is armed.
 *
 * armed === null means NOT DISCLOSED: it is someone else's ship, and whether a heavy weapon is
 * armed is exactly what an opponent may not know. Do not reconstruct it from armingTurn or
 * armingType — that is the leak the null is there to close. A DAC choice is always the owner's
 * own ship so this is normally true, but the guard belongs here rather than in the caller.
 */
function armingDisclosed(w: WeaponState): boolean {
  return w.armed !== null && w.armed !== undefined;
}

/**
 * The state of one weapon as a short list of chips, most decision-relevant first.
 *
 * @param w    the weapon
 * @param rack its drone rack entry, when it has one — a drone rack rides in BOTH the weapons
 *             list and droneRacks, and only the latter says what is loaded
 */
export function weaponChips(w: WeaponState, rack?: DroneRackState | null): WeaponChip[] {
  const chips: WeaponChip[] = [];

  // Destroyed weapons are filtered out of the DAC options by core, so this is a safety net
  // rather than an expected state — but if one ever appears it is obviously the free choice.
  if (!w.functional) {
    return [{ text: 'destroyed', tone: 'spent' }];
  }

  // ---- What is loaded, and how ----
  if (w.isHeavy && armingDisclosed(w)) {
    if (w.armed) {
      chips.push({
        text: 'armed',
        tone: 'valuable',
        title: 'Loaded and ready — the warhead is lost with the weapon',
      });
    } else if (w.armingTurn > 0) {
      const total = w.totalArmingTurns > 0 ? `/${w.totalArmingTurns}` : '';
      chips.push({
        text: `arming ${w.armingTurn}${total}`,
        tone: 'neutral',
        title: 'Part-armed — the energy already spent on it is lost',
      });
    } else {
      chips.push({ text: 'unarmed', tone: 'spent', title: 'Nothing loaded to lose' });
    }
  }

  // A photon is dialled by energy rather than by mode (E4.21/E4.413), so the points already in
  // the tube are the measure of what a hit costs.
  if (w.photonTube && (w.armingEnergy ?? 0) > 0) {
    chips.push({
      text: `${w.armingEnergy} in tube`,
      tone: 'valuable',
      title: 'Warp energy already committed to this tube (E4.413)',
    });
  }

  // ---- Which mode ----
  if (w.isRolling) {
    chips.push({ text: ARMING_LABEL.ROLLING, tone: 'mode', title: ARMING_TITLE.ROLLING });
  } else if (w.armingType && ARMING_LABEL[w.armingType] && armingDisclosed(w)) {
    chips.push({
      text: ARMING_LABEL[w.armingType],
      tone: 'mode',
      title: modeTitle(w, w.armingType),
    });
  }

  // Which plasma torpedo is in the launcher — an R is worth far more than an F.
  if (w.plasmaType) {
    chips.push({ text: `type-${w.plasmaType}`, tone: 'neutral', title: 'Torpedo currently loaded' });
  }

  // ---- Whether it can still shoot, which is the other half of what it is worth ----
  if (w.cooldown) {
    chips.push({
      text: 'cooled down',
      tone: 'spent',
      title: 'Fired last turn — cannot arm or fire this turn (E7.x)',
    });
  } else if (shotsAreLimited(w) && w.shotsThisTurn >= w.maxShotsPerTurn) {
    chips.push({
      text: `${w.shotsThisTurn}/${w.maxShotsPerTurn} shots used`,
      tone: 'spent',
      title: 'No shots left this turn',
    });
  } else if (!w.readyToFire && (!w.isHeavy || w.armed === true)) {
    // Loaded (or needs no loading) and still not ready: the impulse gap has not elapsed.
    chips.push({
      text: 'cooldown',
      tone: 'spent',
      title: 'Cannot fire yet — the minimum impulse gap since its last shot has not elapsed',
    });
  } else if (w.shotsThisTurn > 0) {
    chips.push({
      text: shotsAreLimited(w) ? `${w.shotsThisTurn}/${w.maxShotsPerTurn} shots used` : 'fired',
      tone: 'neutral',
    });
  }

  // ---- Ammunition ----
  // Anti-drone rounds. An ADD rack has a capacity to measure them against; a type-G keeps them
  // in a magazine shared with its drones (FD3.70) and so reports rounds and no capacity.
  if (w.addShots != null) {
    const cap = w.addCapacity != null ? `/${w.addCapacity}` : '';
    const reloads = w.addReloads != null && w.addReloads > 0 ? ` (+${w.addReloads})` : '';
    chips.push({
      text: `${w.addShots}${cap} ADD${reloads}`,
      tone: w.addShots > 0 ? 'valuable' : 'spent',
      title: 'Anti-drone rounds loaded, and reloads behind them',
    });
  }

  if (rack) {
    if (rack.drones.length > 0) {
      const byType = new Map<string, number>();
      for (const d of rack.drones) byType.set(d.droneType, (byType.get(d.droneType) ?? 0) + 1);
      const summary = [...byType].map(([t, n]) => (n > 1 ? `${n}x ${t}` : t)).join(', ');
      chips.push({
        text: `loaded: ${summary}`,
        tone: 'valuable',
        title: 'Drones on the rack — destroyed with it',
      });
    } else {
      chips.push({ text: 'rack empty', tone: 'spent' });
    }
    if (rack.reloadCount > 0) {
      chips.push({ text: `${rack.reloadCount} reloads`, tone: 'neutral' });
    }
    // FD3.71: a type-G committed to anti-drones cannot fire drones for the rest of the turn.
    if (rack.mode && rack.mode !== 'UNDECIDED') {
      chips.push({
        text: rack.mode.toLowerCase().replace('_', '-'),
        tone: 'neutral',
        title: 'The mode this rack is committed to for the turn (FD3.71)',
      });
    }
  }

  // FighterFusion carries charges rather than arming energy (chargesRemaining is null on
  // everything else, which is why the field is an Integer).
  if (w.chargesRemaining != null) {
    chips.push({
      text: `${w.chargesRemaining} charge${w.chargesRemaining === 1 ? '' : 's'}`,
      tone: w.chargesRemaining > 0 ? 'valuable' : 'spent',
    });
  }

  // ---- Systems that sit in the weapons list but are not weapons ----
  // A scout channel is hit on phaser hits (G24.17), so it turns up among the options.
  if (w.scoutChannel) {
    if (!w.channelPowered) {
      chips.push({ text: 'unpowered', tone: 'spent', title: 'Not powered this turn (G24.14)' });
    }
    if (w.channelBlinded) {
      chips.push({
        text: 'blinded',
        tone: 'spent',
        title: 'Blinded by weapons fire this impulse (G24.13)',
      });
    }
    if (w.channelLendTarget) {
      const ecm = w.channelLentEcm ?? 0;
      const eccm = w.channelLentEccm ?? 0;
      chips.push({
        text: `lending ${ecm}/${eccm} to ${w.channelLendTarget}`,
        tone: 'valuable',
        title: 'ECM/ECCM this channel is lending — the loan ends with the channel (G24.21)',
      });
    } else if (w.channelFunction && w.channelFunction !== 'NONE') {
      chips.push({
        text: w.channelFunction.toLowerCase().replace('_', ' '),
        tone: 'valuable',
        title: 'The function this channel is committed to this turn (G24.12)',
      });
    }
  }

  // An ESG is destroyed on drone hits (G23.14), so it turns up among those options.
  if (w.esg) {
    if (w.esgActive) {
      chips.push({
        text: `field up r${w.esgRadius ?? '?'}`,
        tone: 'valuable',
        title: 'A field is currently up — it collapses with the generator',
      });
    }
    if ((w.esgStoredEnergy ?? 0) > 0) {
      chips.push({
        text: `${w.esgStoredEnergy}/${w.esgMaxEnergy ?? 5} stored`,
        tone: 'valuable',
        title: 'Energy held in the generator, lost with it (G23.24)',
      });
    }
  }

  return chips;
}

/** The colour a chip is drawn in; mode chips defer to the mode vocabulary. */
export function chipColour(chip: WeaponChip): string {
  if (chip.tone === 'mode') {
    const key = Object.keys(ARMING_LABEL).find(k => ARMING_LABEL[k] === chip.text);
    return (key && ARMING_COLOUR[key]) || CHIP_COLOUR.mode;
  }
  return CHIP_COLOUR[chip.tone];
}
