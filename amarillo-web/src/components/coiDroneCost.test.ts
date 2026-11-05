import { fighterLoadoutCost } from './fighterLoadout';
import { describe, it, expect } from 'vitest';
import { droneSurcharge, rackSurcharge, droneLoadoutCost } from './coiDroneCost';
import { plainDrone, parseDroneName } from './droneComposition';
import type {
  CoiDroneAddition, CoiDroneModule, CoiDroneRack, CoiDroneType, CoiFighterLoadout, CoiShipData,
  CoiSubmunitionSpeed,
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

/**
 * FD2.45's other half, mirrored: a drone-armed FIGHTER is a launch rack and somebody pays for
 * its load.
 *
 * <blockquote>"The cost of improved drones (FD2.22) is paid only for a single loading of each
 * launch rack <b>and/or fighter</b>; the reloads (and drones stored on a carrier for use by its
 * fighters) are presumed to be of the same type without additional cost."</blockquote>
 *
 * Asserted against the same `cost()` helper the rack tests use, because the two must agree: on
 * the server, one method prices both and the fighter simply passes no rack.
 */
describe('fighterLoadoutCost', () => {
  function tads(name: string): CoiFighterLoadout {
    const rail = (railType: string, designator: string, spaces: number, special: boolean) => ({
      railType, designator, spaces, drone: 'TypeI',
      acceptsTypeIii: special, acceptsMultiWarhead: special,
    });
    return {
      name, type: 'tads', designation: 'TADS', squadron: 'Squadron 1',
      rails: [
        rail('STANDARD', 'A', 1, false),
        rail('STANDARD', 'B', 1, false),
        rail('SPECIAL', 'E', 1, true),
        rail('SPECIAL', 'F', 1, true),
      ],
    };
  }

  const SQUADRON = [tads('T-1'), tads('T-2')];
  const CARRIER = { ...SHIP, fighterLoadouts: SQUADRON } as CoiShipData;

  it('costs nothing while the squadron flies what it was issued (FD2.31)', () => {
    expect(fighterLoadoutCost({}, SQUADRON, CARRIER)).toBe(0);
  });

  it('bills the one rail that was changed', () => {
    const overrides = { 'T-1': [null, null, 'TypeIII', null] };
    expect(fighterLoadoutCost(overrides, SQUADRON, CARRIER)).toBeCloseTo(cost('TypeIII'), 5);
  });

  it('bills each fighter separately, because each is its own loading', () => {
    const overrides = {
      'T-1': [null, null, 'TypeIII', null],
      'T-2': [null, null, 'TypeIII', null],
    };
    expect(fighterLoadoutCost(overrides, SQUADRON, CARRIER))
      .toBeCloseTo(2 * cost('TypeIII'), 5);
  });

  /**
   * And the ready rack is free. J4.8222 makes it one reload of the same drones, and
   * `ShuttleSpace.armOccupantFully` arms a fighter by taking them OUT of the rack (J4.8224) —
   * so the rails and the rack are two states of ONE loading. Billing both would double every
   * carrier's bill, which is the shape this test exists to pin: four rails changed is four
   * drones, not eight.
   */
  it('charges a loading rather than a loading and its reload', () => {
    const overrides = { 'T-1': ['TypeIII', 'TypeIII', 'TypeIII', 'TypeIII'] };
    expect(fighterLoadoutCost(overrides, SQUADRON, CARRIER))
      .toBeCloseTo(4 * cost('TypeIII'), 5);
  });

  it('bills nothing for a rail set back to the drone it was issued with', () => {
    const overrides = { 'T-1': ['TypeI', null, null, null] };
    expect(fighterLoadoutCost(overrides, SQUADRON, CARRIER)).toBe(0);
  });

  /**
   * A fighter named in the overrides that is not aboard is not billed. The editor is seeded
   * from a saved fleet (S8.12) and a hull can be re-seated for the year between one battle and
   * the next, so an entry can outlive the fighter it was written for.
   */
  /**
   * And a drone that was ALREADY aboard is not billed again.
   *
   * The payload reports what each rail is CARRYING, so a fighter whose special rail was
   * stocked with a type-III by an earlier pass arrives holding one. Pricing the whole load
   * rather than the change would charge for it a second time -- and the first version of
   * these tests could not tell the two apart, because every rail in the fixture was carrying
   * the type-I-slow baseline, whose surcharge is zero either way.
   */
  it('bills the change and not what the fighter was already carrying', () => {
    const stocked = tads('T-3');
    stocked.rails[2] = { ...stocked.rails[2], drone: 'TypeIII' };
    const squadron = [stocked];
    const carrier = { ...SHIP, fighterLoadouts: squadron } as CoiShipData;

    expect(fighterLoadoutCost({}, squadron, carrier))
      .toBe(0);
    expect(fighterLoadoutCost({ 'T-3': ['TypeIII', null, null, null] }, squadron, carrier))
      .toBeCloseTo(cost('TypeIII'), 5);
  });

  it('ignores an override naming a fighter that is not aboard', () => {
    const overrides = { 'Ghost-1': [null, null, 'TypeIII', null] };
    expect(fighterLoadoutCost(overrides, SQUADRON, CARRIER)).toBe(0);
  });
});
