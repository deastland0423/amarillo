package com.sfb.properties;

/**
 * End-of-battle status for a ship (S2.20).
 * Used by VictoryCalculator to determine victory point awards.
 */
public enum BattleStatus {
    /** Ship is still on the map and fighting. */
    ACTIVE,

    /** Ship was destroyed by excess damage (S2.41 / excess damage rules). */
    DESTROYED,

    /** Ship left the map voluntarily or was forced to disengage (S2.272). */
    DISENGAGED
}
