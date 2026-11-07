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
        UIM,
        // Control rooms. (D7.8371) one guard covers all CONNECTED boxes of a single type,
        // and "different types of control boxes (security, flag, aux, emer, bridge) can
        // never be protected by a single guard" — so each type is one post, never a pool.
        // (D7.827) also lets a raid name a control box as the room an individual is in.
        BRIDGE,
        FLAG,
        EMER,
        AUXCON,
        SECURITY,
        /**
         * A carried objective — a stasis box, a crate of special cargo, a VIP.
         *
         * <p>(D7.83) is written for "specific individuals or objects", and a box on the SSD is
         * only the commonest case of one. SH47.476 then relies on it outright: "If the Stasis
         * Box is aboard one ship, the other ship may attempt a hit-and-run raid to seize the
         * box (IT CAN BE GUARDED)." Nothing about that is particular to SH47 — a scenario with
         * a defecting admiral aboard wants the same thing, and so does one with a crate nobody
         * is allowed to lose.
         *
         * <p>Keyed by {@link #getObjectiveName()}, not by {@code displayName}: the name is the
         * identity every objective action already addresses it by, and a label would drift.
         * Always a single post, never a pool — (D7.833) "no more than one boarding party can be
         * assigned to guard one object, area, or group of people."
         */
        OBJECTIVE
    }

    private final Type   type;
    private final Weapon weapon;      // non-null only for WEAPON targets
    private final int    index;       // beam number for TRACTOR targets; 0 otherwise
    private final String displayName;
    private final BoardingPartyQuality attackerQuality;
    private final String objectiveName; // non-null only for OBJECTIVE targets

    /**
     * (D7.83) A carried object or individual, named the way every other objective action names
     * it. A separate factory because the name is IDENTITY here, and the one constructor that
     * would otherwise fit takes a {@code displayName} that is only a label.
     */
    public static SystemTarget forObjective(String objectiveName) {
        return forObjective(objectiveName, BoardingPartyQuality.NORMAL);
    }

    public static SystemTarget forObjective(String objectiveName,
                                            BoardingPartyQuality attackerQuality) {
        return new SystemTarget(objectiveName, attackerQuality);
    }

    private SystemTarget(String objectiveName, BoardingPartyQuality attackerQuality) {
        this.type            = Type.OBJECTIVE;
        this.weapon          = null;
        this.index           = 0;
        this.displayName     = objectiveName;
        this.attackerQuality = attackerQuality;
        this.objectiveName   = objectiveName;
    }

    /** The objective this target names, or null if it is not an OBJECTIVE target. */
    public String getObjectiveName() {
        return objectiveName;
    }

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
        this.objectiveName   = null;
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
        this.objectiveName   = null;
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
