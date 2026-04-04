package com.sfb.properties;

import com.sfb.weapons.Weapon;

/**
 * Identifies a specific system on a ship that can be targeted by a Hit &amp; Run
 * boarding raid.
 *
 * <p>For {@link Type#WEAPON} targets the {@code weapon} field carries the exact
 * weapon instance.  All other types represent a single undifferentiated system
 * box (one warp engine box, one impulse box, etc.) and {@code weapon} is null.
 */
public class SystemTarget {

    public enum Type {
        WEAPON,
        WARP,
        IMPULSE,
        SENSORS,
        SCANNERS,
        TRANSPORTERS,
        CREW,
        FHULL,
        AHULL,
        CHULL,
        CLOAKING_DEVICE,
        DERFACS
    }

    private final Type   type;
    private final Weapon weapon;      // non-null only for WEAPON targets
    private final String displayName;

    /** Constructor for non-weapon system targets. */
    public SystemTarget(Type type, String displayName) {
        if (type == Type.WEAPON) {
            throw new IllegalArgumentException("Use the weapon constructor for WEAPON targets");
        }
        this.type        = type;
        this.weapon      = null;
        this.displayName = displayName;
    }

    /** Constructor for weapon targets. */
    public SystemTarget(Weapon weapon) {
        this.type        = Type.WEAPON;
        this.weapon      = weapon;
        this.displayName = weapon.getName();
    }

    public Type getType() {
        return type;
    }

    /** Returns the weapon instance, or null if this is not a WEAPON target. */
    public Weapon getWeapon() {
        return weapon;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
