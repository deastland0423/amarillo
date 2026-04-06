package com.sfb.objects;

public enum DroneType {
  // Endur, Speed, Dmg, Rack, Hull, SelfGuiding?
  TypeI(96, 8, 12, 1.0, 4, false),
  TypeII(64, 12, 12, 1.0, 4, false),
  TypeIII(800, 12, 12, 1.0, 4, true),
  TypeIV(96, 8, 24, 2.0, 6, false),
  TypeV(64, 12, 24, 2.0, 6, false),
  TypeVI(32, 12, 8, 0.5, 3, false);

  final int endurance, speed, damage, hull;
  final double rack;
  final boolean selfGuiding;

  DroneType(int endur, int spd, int dmg, double rack, int hull, boolean selfGuiding) {
    this.endurance = endur;
    this.speed = spd;
    this.damage = dmg;
    this.rack = rack;
    this.hull = hull;
    this.selfGuiding = selfGuiding;
  }
}
