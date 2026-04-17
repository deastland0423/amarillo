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
  // Endur Spd Dmg Rack Hull SelfGuide YearAvail

  // Standard drones — 3-turn (96 impulse) endurance at all speeds
  TypeI(96, 8, 12, 1.0, 4, false, 0),
  TypeIM(96, 20, 12, 1.0, 4, false, 165), // medium-speed TypeI
  TypeIF(96, 32, 12, 1.0, 4, false, 178), // fast-speed TypeI

  // Standard heavy drones — 3-turn (96 impulse) endurance at all speeds
  TypeIV(96, 8, 24, 2.0, 6, false, 0),
  TypeIVM(96, 20, 24, 2.0, 6, false, 165), // medium-speed TypeIV
  TypeIVF(96, 32, 24, 2.0, 6, false, 178), // fast-speed TypeIV

  // Transitional speed-12 drones — 2-turn (64 impulse) endurance, obsolete at
  // Y165
  TypeII(64, 12, 12, 1.0, 4, false, 77), // TypeI boosted to speed 12
  TypeV(64, 12, 24, 2.0, 6, false, 77), // TypeIV boosted to speed 12

  // Special drones
  TypeIII(800, 12, 12, 1.0, 4, true, 0), // self-guiding, very long endurance
  TypeIIIM(800, 20, 12, 1.0, 4, true, 165), // medium-speed self-guiding
  TypeIIIF(800, 32, 12, 1.0, 4, true, 178), // fast self-guiding
  TypeVI(32, 12, 8, 0.5, 3, false, 0), // light/mini drone
  TypeVIM(32, 20, 8, 0.5, 3, false, 165), // medium-speed light drone
  TypeVIF(32, 32, 8, 0.5, 3, false, 178); // fast light drone

  public final int endurance, speed, damage, hull, availableFromYear;
  public final double rack;
  public final boolean selfGuiding;

  DroneType(int endur, int spd, int dmg, double rack, int hull,
      boolean selfGuiding, int availableFromYear) {
    this.endurance = endur;
    this.speed = spd;
    this.damage = dmg;
    this.rack = rack;
    this.hull = hull;
    this.selfGuiding = selfGuiding;
    this.availableFromYear = availableFromYear;
  }

  /** True if this drone type is available in the given scenario year. */
  public boolean availableIn(int year) {
    return year >= availableFromYear;
  }
}
