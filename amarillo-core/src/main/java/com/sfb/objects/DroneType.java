package com.sfb.objects;

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
  // Endur Spd Dmg Rack Hull SelfGuide WarpSeek YearAvail Family

  // Standard drones — 3-turn (96 impulse) endurance at all speeds
  TypeI(96, 8, 12, 1.0, 4, false, false, 0, Family.STANDARD),
  TypeIM(96, 20, 12, 1.0, 4, false, false, 165, Family.STANDARD),
  TypeIF(96, 32, 12, 1.0, 4, false, false, 178, Family.STANDARD),

  // Standard heavy drones — 3-turn (96 impulse) endurance at all speeds
  TypeIV(96, 8, 24, 2.0, 6, false, false, 0, Family.HEAVY),
  TypeIVM(96, 20, 24, 2.0, 6, false, false, 165, Family.HEAVY),
  TypeIVF(96, 32, 24, 2.0, 6, false, false, 178, Family.HEAVY),

  // Transitional speed-12 drones — 2-turn (64 impulse) endurance, obsolete at Y165
  TypeII(64, 12, 12, 1.0, 4, false, false, 77, Family.STANDARD),
  TypeV(64, 12, 24, 2.0, 6, false, false, 77, Family.HEAVY),

  // Self-guiding drones
  TypeIII(800, 12, 12, 1.0, 4, true, false, 0, Family.SELF_GUIDING),
  TypeIIIM(800, 20, 12, 1.0, 4, true, false, 165, Family.SELF_GUIDING),
  TypeIIIF(800, 32, 12, 1.0, 4, true, false, 178, Family.SELF_GUIDING),

  // Warp-seeking drones (self-guiding + warp seeker)
  TypeVI(32, 12, 8, 0.5, 3, true, true, 0, Family.DOGFIGHT),
  TypeVIM(32, 20, 8, 0.5, 3, true, true, 165, Family.DOGFIGHT),
  TypeVIF(32, 32, 8, 0.5, 3, true, true, 178, Family.DOGFIGHT);

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

  DroneType(int endur, int spd, int dmg, double rack, int hull,
      boolean selfGuiding, boolean warpSeeker, int availableFromYear, Family family) {
    this.family = family;
    this.endurance = endur;
    this.speed = spd;
    this.damage = dmg;
    this.rack = rack;
    this.hull = hull;
    this.selfGuiding = selfGuiding;
    this.warpSeeker = warpSeeker;
    this.availableFromYear = availableFromYear;
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
