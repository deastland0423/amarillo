import { describe, it, expect } from 'vitest';
import { droneSurcharge, rackSurcharge, droneLoadoutCost } from './coiDroneCost';
import { plainDrone, parseDroneName } from './droneComposition';
import type {
  CoiDroneAddition, CoiDroneModule, CoiDroneRack, CoiDroneType, CoiShipData, CoiSubmunitionSpeed,
} from '../api/gameApi';

/**
 * The other half of core's `DroneCostDecompositionTest`.
 *
 * Every figure asserted here is asserted there too, in Java, on the same drone in the same year.
 * That is the whole safeguard: this file adds up parts the server sent, and the only way to know
 * the sum is still the price is to check it against the price. If core's numbers move, these
 * fail.
 *
 * The fixtures below are what the server actually sends — FD10.5's charts as core reads them:
 * speed-8 is 0.50 on a one-space frame and 1.00 on a two-space one, speed-12 is 1.00 and 1.50,
 * speed-32 is 1.50; an explosive module is 0.50 a space, armor and a half-explosive 0.25, a
 * swordfish 1.00, a three-drone multi-warhead 2.50; extended range and a type-III's guidance are
 * half a point each.
 */

const TYPES: CoiDroneType[] = [
  // A type-I-slow: the baseline every surcharge is measured against (FD2.22).
  { name: 'TypeI', rack: 1, engineCost: 0.50, payloadCost: 0.50, frameCost: 0, speedCredit: 0 },
  // Fast, in Y180: the ship's BPV carries the whole point (S2.11), so the budget owes nothing.
  { name: 'TypeIF', rack: 1, engineCost: 1.50, payloadCost: 0.50, frameCost: 0, speedCredit: 1.00 },
  { name: 'TypeIV', rack: 2, engineCost: 1.00, payloadCost: 1.00, frameCost: 0, speedCredit: 0 },
  // A type-III pays half a point for its guidance (FD10.22's named exception), and FD10.24 grows
  // it to a two-space drone when extended range is fitted.
  { name: 'TypeIII', rack: 1, engineCost: 1.00, payloadCost: 0.50, frameCost: 0.50,
    speedCredit: 0.50, rackWithExtendedRange: 2,
    engineCostWithExtendedRange: 1.50, speedCreditWithExtendedRange: 0.50 },
] as CoiDroneType[];

const MODULES: CoiDroneModule[] = [
  { name: 'SWORDFISH_PHASER3', cost: 1.00, spaces: 1.0, submunitions: 0 },
  { name: 'EXPLOSIVE_HALF',    cost: 0.25, spaces: 0.5, submunitions: 0 },
  { name: 'ARMOR_HALF',        cost: 0.25, spaces: 0.5, submunitions: 0 },
  { name: 'MW_SMALL',          cost: 2.50, spaces: 1.0, submunitions: 3 },
] as CoiDroneModule[];

const ADDITIONS: CoiDroneAddition[] = [
  { name: 'EXTENDED_RANGE',      cost: 0.50 },
  { name: 'EXTERNAL_ARMOR_HALF', cost: 0.25 },
] as CoiDroneAddition[];

const SUB_SPEEDS: CoiSubmunitionSpeed[] = [
  { name: 'SPEED_20', speed: 20, availability: 'GENERAL', surchargePerSubmunition: 0.25 },
];

/** One drone by name, the way a loadout spells it. */
function cost(name: string, types: CoiDroneType[] = TYPES): number {
  return droneSurcharge(
    parseDroneName(name, MODULES, ADDITIONS), types, MODULES, ADDITIONS, SUB_SPEEDS);
}

const SHIP: CoiShipData = {
  availableDroneTypes:        TYPES,
  availableDroneModules:      MODULES,
  availableDroneAdditions:    ADDITIONS,
  availableSubmunitionSpeeds: SUB_SPEEDS,
} as CoiShipData;

describe('droneSurcharge', () => {
  /** A type-I-slow is what the ship's BPV already paid for, so it is free by definition. */
  it('charges nothing for the drone the baseline is made of', () => {
    expect(cost('TypeI')).toBe(0);
  });

  /**
   * S3.221's exception: fast drones are standard issue in Y180 and the ship pays, but in Y179
   * they are still Restricted and the player buys the last half point from the 20%. The only
   * thing that changes between the two is the credit the server sends.
   */
  it('charges for speed only before it is standard issue', () => {
    expect(cost('TypeIF')).toBe(0);

    const y179 = TYPES.map(t => t.name === 'TypeIF' ? { ...t, speedCredit: 0.50 } : t);
    expect(cost('TypeIF', y179)).toBe(0.50);
  });

  /** FD10.51: a swordfish module is a point where the explosive it displaces is half. */
  it('prices a chosen module over the payload it replaces', () => {
    expect(cost('TypeI+SWORDFISH_PHASER3')).toBe(0.50);
  });

  /** FD2.11's trades: three half-armors and a half-explosive cost what two explosives did. */
  it('charges nothing for rearranging a payload within its own spaces', () => {
    expect(cost('TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF')).toBe(0);
  });

  /**
   * FD10.48: a half-filled type-IV is billed as a type-IV. Priced from its parts it would be
   * 1.25 points against 2.00 of displaced type-I-slows — a 0.75 credit for carrying a worse
   * drone, which no line of FD2.11's table gives. The warhead was bought before it was
   * discarded. `anUnderFilledPayloadIsBilledAsThoughItWereFull` on the core side.
   */
  it('bills an under-filled drone as though it were full', () => {
    expect(cost('TypeIV+EXPLOSIVE_HALF')).toBe(0);
  });

  /**
   * FD10.24, and the one place this has to choose between two sets of figures: a type-III given
   * extended range is a two-space drone, so its engine is priced from the two-space column and
   * its footprint doubles.
   */
  it('prices a type-III with extended range on the frame it grew into', () => {
    expect(cost('TypeIII+EXTENDED_RANGE')).toBe(0.50);
    expect(cost('TypeIII')).toBe(0.50);   // the same figure by a different route, not a typo
  });

  /** FD8.44: the bus pays for the engine of every submunition it carries. */
  it('multiplies the submunition surcharge by how many are aboard', () => {
    expect(cost('TypeI+MW_SMALL+SPEED_20')).toBe(2.75);
  });

  /** FD12.132: external armor is bought on the frame, and costs the drone speed instead of space. */
  it('prices a frame addition on top of the payload it keeps', () => {
    expect(cost('TypeI+EXTERNAL_ARMOR_HALF')).toBe(0.25);
  });

  /** A server from before the costs existed gets silence rather than a guess. */
  it('says nothing when the server sent no cost figures', () => {
    const bare = [{ name: 'TypeI', rack: 1 }] as CoiDroneType[];
    expect(droneSurcharge(plainDrone('TypeI'), bare, MODULES, ADDITIONS, SUB_SPEEDS)).toBe(0);
  });

  /** And nothing for a drone no catalogue describes, rather than NaN. */
  it('says nothing about a drone it has never heard of', () => {
    expect(cost('TypeXVII')).toBe(0);
  });
});

describe('rackSurcharge', () => {
  /**
   * A plain sum, and the claim that broke before: the gutted type-IV used to report −0.75 and a
   * clamp on the rack total hid it, so the swordfish beside it flew free on points the type-IV
   * never saved. `aRackIsThePlainSumOfItsDrones` on the core side, same answer.
   */
  it('does not let a gutted drone pay for the drone in the next space', () => {
    expect(rackSurcharge(['TypeIV+EXPLOSIVE_HALF', 'TypeI+SWORDFISH_PHASER3'], SHIP)).toBe(0.50);
  });

  it('charges a rack that is over the baseline', () => {
    expect(rackSurcharge(['TypeI+SWORDFISH_PHASER3', 'TypeI', 'TypeI', 'TypeI'], SHIP))
      .toBe(0.50);
  });

  it('has nothing to charge for an empty rack', () => {
    expect(rackSurcharge([], SHIP)).toBe(0);
  });
});

/**
 * FD2.45's magazine exception, which is the one place `rackSurcharge` is not a plain sum.
 *
 * `CoiMagazineSurchargeTest` on the core side, same rule and the same arithmetic — this is the
 * mirror, so if the two disagree the dialog quotes a bill the server will not honour.
 */
describe('rackSurcharge in a magazine launcher', () => {
  /** FD3.4: three magazines of four spaces behind one launcher. FD3.44 charges for one. */
  const TYPE_D = { index: 0, spaces: 12, paidLoadingSpaces: 4 } as CoiDroneRack;
  /** An ordinary four-space rack, whose reloads are kept where no bill looks. */
  const ORDINARY = { index: 1, spaces: 4, paidLoadingSpaces: 4 } as CoiDroneRack;

  const twelveSpecials = Array<string>(12).fill('TypeI+SWORDFISH_PHASER3');

  it('charges one magazine of the three', () => {
    expect(rackSurcharge(twelveSpecials, SHIP, TYPE_D)).toBe(2.00);
  });

  it('charges the whole load when the rack is an ordinary one', () => {
    expect(rackSurcharge(['TypeI+SWORDFISH_PHASER3', 'TypeI+SWORDFISH_PHASER3',
      'TypeI+SWORDFISH_PHASER3', 'TypeI+SWORDFISH_PHASER3'], SHIP, ORDINARY)).toBe(2.00);
  });

  /**
   * And without a rack at all, which is what a server that predates `paidLoadingSpaces` sends:
   * the whole load, as before. Six where the rule asks two, but the alternative fallback —
   * treating the missing figure as zero — would charge nothing at all.
   */
  it('bills the whole load when the server has not said otherwise', () => {
    expect(rackSurcharge(twelveSpecials, SHIP)).toBe(6.00);
  });

  /**
   * FD2.452 picks WHICH magazine: "the reload drones are presumed to be of the same cost (or
   * lower) as the drones in the rack", so it has to be the dearest. Eight cheap drones first in
   * the list and four dear ones behind them, so the answer cannot come from the ordering — and
   * a type-III rather than a type-I for the cheap eight, because a type-I-slow is free either
   * way and would prove nothing.
   */
  it('charges the dearest magazine rather than the first one it finds', () => {
    const load = [...Array<string>(8).fill('TypeIII'),
                  ...Array<string>(4).fill('TypeIII+SWORDFISH_PHASER3')];
    expect(rackSurcharge(load, SHIP, TYPE_D)).toBe(4.00);
    expect(rackSurcharge(load, SHIP)).toBe(8.00);
  });

  /** And the racks are told apart by index, which is how a loadout names them. */
  it('bills a magazine launcher and an ordinary rack differently in one loadout', () => {
    const ship = { ...SHIP, droneRacks: [TYPE_D, ORDINARY] } as CoiShipData;
    expect(droneLoadoutCost({
      0: twelveSpecials,
      1: ['TypeI+SWORDFISH_PHASER3', 'TypeI', 'TypeI', 'TypeI'],
    }, ship)).toBe(2.50);
  });
});

describe('droneLoadoutCost', () => {
  /**
   * And a cheap rack must not pay for the rack beside it either. This is
   * `aCheapRackDoesNotSubsidiseAnExpensiveOneBesideIt` on the core side, same answer.
   */
  it('does not let a cheap rack subsidise an expensive one', () => {
    expect(droneLoadoutCost({
      0: ['TypeIV+EXPLOSIVE_HALF', 'TypeIV+EXPLOSIVE_HALF'],
      1: ['TypeI+SWORDFISH_PHASER3', 'TypeI', 'TypeI', 'TypeI'],
    }, SHIP)).toBe(0.50);
  });

  /**
   * A rack the player never touched keeps what the ship came with (S2.11) and is not in the
   * loadouts at all, so it costs this budget nothing — which is also what `applyCoi` does: it
   * visits only the racks the loadout names.
   */
  it('charges nothing for racks nobody has built', () => {
    expect(droneLoadoutCost({}, SHIP)).toBe(0);
  });
});
