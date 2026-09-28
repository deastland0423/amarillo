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
  // Endur Spd Dmg Rack Hull SelfGuide WarpSeek YearAvail

  // Standard drones — 3-turn (96 impulse) endurance at all speeds
  TypeI(96, 8, 12, 1.0, 4, false, false, 0),
  TypeIM(96, 20, 12, 1.0, 4, false, false, 165),
  TypeIF(96, 32, 12, 1.0, 4, false, false, 178),

  // Standard heavy drones — 3-turn (96 impulse) endurance at all speeds
  TypeIV(96, 8, 24, 2.0, 6, false, false, 0),
  TypeIVM(96, 20, 24, 2.0, 6, false, false, 165),
  TypeIVF(96, 32, 24, 2.0, 6, false, false, 178),

  // Transitional speed-12 drones — 2-turn (64 impulse) endurance, obsolete at Y165
  TypeII(64, 12, 12, 1.0, 4, false, false, 77),
  TypeV(64, 12, 24, 2.0, 6, false, false, 77),

  // Self-guiding drones
  TypeIII(800, 12, 12, 1.0, 4, true, false, 0),
  TypeIIIM(800, 20, 12, 1.0, 4, true, false, 165),
  TypeIIIF(800, 32, 12, 1.0, 4, true, false, 178),

  // Warp-seeking drones (self-guiding + warp seeker)
  TypeVI(32, 12, 8, 0.5, 3, true, true, 0),
  TypeVIM(32, 20, 8, 0.5, 3, true, true, 165),
  TypeVIF(32, 32, 8, 0.5, 3, true, true, 178);

  public final int endurance, speed, damage, hull, availableFromYear;
  public final double rack;
  public final boolean selfGuiding;
  public final boolean warpSeeker;

  DroneType(int endur, int spd, int dmg, double rack, int hull,
      boolean selfGuiding, boolean warpSeeker, int availableFromYear) {
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
