package com.sfb.objects;

import static com.sfb.objects.DroneFrame.HALF_SPACE;
import static com.sfb.objects.DroneFrame.ONE_SPACE;
import static com.sfb.objects.DroneFrame.TWO_SPACE;
import static com.sfb.objects.PayloadModule.EXPLOSIVE_1;
import static com.sfb.objects.Propulsion.SPEED_12;
import static com.sfb.objects.Propulsion.SPEED_20;
import static com.sfb.objects.Propulsion.SPEED_32;
import static com.sfb.objects.Propulsion.SPEED_8;

/**
 * All drone types with their stats and year availability.
 *
 * Standard drones (TypeI, TypeIV) keep 3-turn (96 impulse) endurance at any
 * speed tier.
 * Transitional speed-12 drones (TypeII, TypeV) have 2-turn (64 impulse)
 * endurance and
 * become obsolete once medium-speed (TypeIM/TypeIVM) drones arrive in Y165.
 *
 * Speed tiers: Slow=8 (always), Moderate=12 (always), Medium=20 (Y165+),
 * Fast=32 (Y178+).
 * Naming convention for upgrades: base name + M (medium) or F (fast), e.g.
 * TypeIM, TypeIVF.
 */
public enum DroneType {
  // The named drones history fielded, each expressed as what FD10.2 says a drone IS: a frame,
  // a propulsion section and a payload. rack, hull and damage are DERIVED from that; endurance,
  // guidance and availability stay declared, because no rule computes them. See DroneSpec.
  //
  //      frame                propulsion            payload                     endur  guid  warp  year  family

  // Standard drones — one-space frame, one explosive module, 3-turn (96 impulse) endurance
  TypeI(ONE_SPACE, SPEED_8, 96, false, false, 0, Family.STANDARD, EXPLOSIVE_1),
  TypeIM(ONE_SPACE, SPEED_20, 96, false, false, 165, Family.STANDARD, EXPLOSIVE_1),
  TypeIF(ONE_SPACE, SPEED_32, 96, false, false, 178, Family.STANDARD, EXPLOSIVE_1),

  // Standard heavy drones — two-space frame carrying TWO explosive modules, which is where
  // the 24 comes from: FD10.41, "two one-payload space explosive modules", combined into one
  // explosion rather than resolved as separate volleys.
  TypeIV(TWO_SPACE, SPEED_8, 96, false, false, 0, Family.HEAVY, EXPLOSIVE_1, EXPLOSIVE_1),
  TypeIVM(TWO_SPACE, SPEED_20, 96, false, false, 165, Family.HEAVY, EXPLOSIVE_1, EXPLOSIVE_1),
  TypeIVF(TWO_SPACE, SPEED_32, 96, false, false, 178, Family.HEAVY, EXPLOSIVE_1, EXPLOSIVE_1),

  // Transitional speed-12 drones — 2-turn (64 impulse) endurance, obsolete at Y165
  TypeII(ONE_SPACE, SPEED_12, 64, false, false, 77, Family.STANDARD, EXPLOSIVE_1),
  TypeV(TWO_SPACE, SPEED_12, 64, false, false, 77, Family.HEAVY, EXPLOSIVE_1, EXPLOSIVE_1),

  // Self-guiding drones. FD2.227: a "slow" type-III is Speed 12, not Speed 8 — which is why
  // Propulsion is named for the number rather than for the rule's adjective.
  TypeIII(ONE_SPACE, SPEED_12, 800, true, false, 0, Family.SELF_GUIDING, EXPLOSIVE_1),
  TypeIIIM(ONE_SPACE, SPEED_20, 800, true, false, 165, Family.SELF_GUIDING, EXPLOSIVE_1),
  TypeIIIF(ONE_SPACE, SPEED_32, 800, true, false, 178, Family.SELF_GUIDING, EXPLOSIVE_1),

  // Dogfight drones on the half-space frame, which takes no payload at all (FD10.21, FD2.51):
  // its warhead is fixed and only the speed varies. FD2.227 puts the "slow" one at Speed 12.
  TypeVI(HALF_SPACE, SPEED_12, 32, true, true, 0, Family.DOGFIGHT),
  TypeVIM(HALF_SPACE, SPEED_20, 32, true, true, 165, Family.DOGFIGHT),
  TypeVIF(HALF_SPACE, SPEED_32, 32, true, true, 178, Family.DOGFIGHT);

  /**
   * J4.241's "DFD": a dogfight drone, which is the type-VI family and nothing else.
   * <p>
   * A rule turns on it — a fighter may launch a second drone in the quarter turn only if
   * one of the pair is a dogfight drone — so it is worth a name of its own. Not the same
   * question as warpSeeker, which is about how the thing flies (FD2.56); the two coincide
   * on the type-VIs and there is no reason to assume they always will.
   */
  public boolean isDogfightDrone() {
    return warpSeeker;
  }

  /**
   * What KIND of drone this is, as against how fast it flies.
   * <p>
   * The naming hides this: the suffix carries speed and nothing else — none is 8 or 12, M is 20,
   * F is 32 — while the numeral carries the family. So a type-I and a type-IM are the same drone
   * at two speeds, which is not a thing anyone can guess from the names, and a newcomer choosing
   * a rack loadout has no way to tell them apart.
   * <p>
   * Declared here rather than worked out from the name because a family is a rules fact and the
   * names are a spelling. A client grouping by {@code name.startsWith("TypeIV")} would be
   * deciding a rules question out of string prefixes — and would get TypeI/TypeIV wrong the
   * moment it tried, since "TypeIV" starts with "TypeI".
   * <p>
   * The transitional speed-12 drones (type-II and type-V, obsolete from Y165) sit in STANDARD and
   * HEAVY with their bigger siblings: they are the same space and damage, only slower and
   * shorter-legged, so they belong beside them as another speed to choose. Endurance therefore
   * varies WITHIN a family and has to be shown per drone, not per family.
   */
  public enum Family {
    STANDARD("Standard"),
    HEAVY("Heavy"),
    SELF_GUIDING("Self-guiding"),
    DOGFIGHT("Dogfight");

    public final String label;

    Family(String label) {
      this.label = label;
    }
  }

  public final Family family;
  public final int endurance, speed, damage, hull, availableFromYear;
  public final double rack;
  public final boolean selfGuiding;
  public final boolean warpSeeker;

  /** What this drone is made of (FD10.2). The derived fields below are read off it. */
  public final DroneSpec spec;

  /**
   * The fields stay PUBLIC FINAL and keep their exact names and meanings.
   *
   * <p>They are read about two hundred times across the engine, the server and the tests —
   * {@code .damage} alone in eighty-seven places — and a field cannot be computed on access.
   * Deriving them here instead means the composition is the single source of the numbers while
   * not one caller changes, which is what made this safe to do in one commit.
   */
  DroneType(DroneFrame frame, Propulsion propulsion, int endur,
      boolean selfGuiding, boolean warpSeeker, int availableFromYear, Family family,
      PayloadModule... payload) {
    // FD10.22: "the frame is effectively free. Exception: type-III, which costs 1/2 point for
    // standard 25-turn range and special guidance" — FD10.53's Type-III Frame Surcharge. Read
    // off the FAMILY rather than passed in, because the surcharge IS that guidance: the
    // SELF_GUIDING family is the three type-IIIs and nothing else, and a drone that acquired
    // the one without the other would be a contradiction rather than a configuration.
    java.util.Set<FrameAddition> additions = family == Family.SELF_GUIDING
        ? java.util.Set.of(FrameAddition.TYPE_III_GUIDANCE)
        : java.util.Set.of();
    this.spec = frame.modular
        ? DroneSpec.of(frame, propulsion, additions, payload)
        : DroneSpec.fixed(frame, propulsion);
    this.family = family;
    this.endurance = endur;
    this.selfGuiding = selfGuiding;
    this.warpSeeker = warpSeeker;
    this.availableFromYear = availableFromYear;
    // Derived (FD10.2): the frame gives the rack footprint and the hull, the propulsion gives
    // the speed, and the payload modules sum to the warhead (FD10.41).
    this.speed = this.spec.speed();
    this.damage = this.spec.warheadStrength();
    this.rack = this.spec.rackSpaces();
    this.hull = this.spec.hull();
  }

  /** True if this drone type is available in the given scenario year. */
  public boolean availableIn(int year) {
    return year >= availableFromYear;
  }

  /** True if this is a TypeVI variant (TypeVI, TypeVIM, TypeVIF) — only loadable in TYPE_E/G/H racks. */
  public boolean isTypeVI() {
    return this == TypeVI || this == TypeVIM || this == TypeVIF;
  }
}
