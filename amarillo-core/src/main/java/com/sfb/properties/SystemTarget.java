package com.sfb.properties;

import com.sfb.weapons.Weapon;

/**
 * Identifies a specific system on a ship that can be targeted by a Hit &amp; Run
 * boarding raid (D7.81) or covered by a guard post (D7.83).
 *
 * <p>For {@link Type#WEAPON} targets the {@code weapon} field carries the exact
 * weapon instance. For {@link Type#TRACTOR} targets {@code index} carries the
 * beam number (D7.835 — the raid names the specific beam). Warp engines are
 * targeted per engine (D7.8372). All other types represent one box of the
 * named system.
 *
 * <p>Crew units are deliberately absent: D7.826 forbids hit-and-run raids
 * against crew units, deck crews, and boarding parties.
 */
public class SystemTarget {

    public enum Type {
        WEAPON,
        TRACTOR,
        WARP_L,
        WARP_R,
        WARP_C,
        IMPULSE,
        SENSORS,
        SCANNERS,
        TRANSPORTERS,
        BATTERY,
        FHULL,
        AHULL,
        CHULL,
        CLOAKING_DEVICE,
        DERFACS,
        UIM
    }

    private final Type   type;
    private final Weapon weapon;      // non-null only for WEAPON targets
    private final int    index;       // beam number for TRACTOR targets; 0 otherwise
    private final String displayName;
    private final BoardingPartyQuality attackerQuality;

    /** Constructor for non-weapon system targets (attacker quality defaults to NORMAL). */
    public SystemTarget(Type type, String displayName) {
        this(type, 0, displayName, BoardingPartyQuality.NORMAL);
    }

    /** Constructor for non-weapon system targets with explicit attacker quality. */
    public SystemTarget(Type type, String displayName, BoardingPartyQuality attackerQuality) {
        this(type, 0, displayName, attackerQuality);
    }

    /** Constructor for indexed targets (TRACTOR beam number). */
    public SystemTarget(Type type, int index, String displayName) {
        this(type, index, displayName, BoardingPartyQuality.NORMAL);
    }

    /** Full constructor for non-weapon system targets. */
    public SystemTarget(Type type, int index, String displayName, BoardingPartyQuality attackerQuality) {
        if (type == Type.WEAPON) {
            throw new IllegalArgumentException("Use the weapon constructor for WEAPON targets");
        }
        this.type            = type;
        this.weapon          = null;
        this.index           = index;
        this.displayName     = displayName;
        this.attackerQuality = attackerQuality;
    }

    /** Constructor for weapon targets (attacker quality defaults to NORMAL). */
    public SystemTarget(Weapon weapon) {
        this(weapon, BoardingPartyQuality.NORMAL);
    }

    /** Constructor for weapon targets with explicit attacker quality. */
    public SystemTarget(Weapon weapon, BoardingPartyQuality attackerQuality) {
        this.type            = Type.WEAPON;
        this.weapon          = weapon;
        this.index           = 0;
        this.displayName     = weapon.getName();
        this.attackerQuality = attackerQuality;
    }

    public Type getType() {
        return type;
    }

    /** Returns the weapon instance, or null if this is not a WEAPON target. */
    public Weapon getWeapon() {
        return weapon;
    }

    /** Tractor beam number for TRACTOR targets; 0 otherwise. */
    public int getIndex() {
        return index;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BoardingPartyQuality getAttackerQuality() {
        return attackerQuality;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
