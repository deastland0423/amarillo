package com.sfb.systemgroups;

/**
 * What a deck crew is doing in a fighter box this turn (J4.817).
 * <p>
 * Two crews may work one fighter (J4.8172) and they need not be doing the same thing — one
 * loading its weapons while the other repairs it is ordinary. So a posting is a crew count
 * per TASK, not per box, and the box's total is what it holds for J4.811's purposes.
 */
public enum CrewTask {

    /** Arm the occupant from its box's own capacitor or ready rack (J4.83, J4.82). */
    LOAD,

    /**
     * Take the drones back off it and return them to where they came from: a fighter's to
     * its box's ready rack, a scatter pack's to the rack reloads it was filled from.
     * <p>
     * A rack holds one reload of one drone type, so this is how a commander changes their
     * mind about what a fighter is carrying.
     */
    UNLOAD,

    /** Repair damage to the shuttle or fighter, one point an action (J4.818). */
    REPAIR,

    /**
     * Move drones from the ship's stores up into this box's ready rack (J4.82, J4.821).
     * <p>
     * The first leg of the journey J4.82 describes, and the one that makes a spent rack
     * anything but spent for the rest of the scenario. One action PER SPACE, the same price
     * as the second leg, so a type-I costs two actions to get from the hold onto a fighter.
     * <p>
     * It is the odd one out in two ways, both from J4.8172. It does not compete for the two
     * crews that may work a fighter — "two MORE deck crews can load the ready rack in that
     * box" — so it carries its own allowance of two. And it cannot run alongside
     * {@link #LOAD} on the same box: the rack "cannot be simultaneously loaded by one set of
     * deck crews and provide drones for other deck crews to load on the fighter, even if
     * this is done in different positions on the rack".
     */
    REFILL;

    /**
     * Whether this job is work on the FIGHTER, and so inside J4.8172's limit of two crews
     * per fighter. Refilling the rack is work on the box and has its own allowance.
     */
    public boolean isFighterWork() {
        return this != REFILL;
    }

    /**
     * Whether this job is the separate crews STOCKING the rack from the hold — J4.8172's
     * "two more deck crews [who] can load the ready rack in that box".
     * <p>
     * {@link #UNLOAD} is deliberately not one of these, though drones do end up in the rack.
     * J4.8172's restriction is drawn between the pair working the FIGHTER and the pair
     * working the RACK, and unloading is fighter work: it is a crew at the rails taking a
     * drone off, not a crew at the hoist putting one on. Reading it the other way would make
     * swapping a fighter's loadout — load one rail, unload another — illegal, which no rule
     * says and which the deck crew orders already allow.
     */
    public boolean loadsTheRack() {
        return this == REFILL;
    }

    /** Whether this job draws drones OUT of the box's ready rack to arm the fighter. */
    public boolean drawsFromRack() {
        return this == LOAD;
    }

    /**
     * J4.8172: the rack "cannot be simultaneously loaded by one set of deck crews and provide
     * drones for other deck crews to load on the fighter, even if this is done in different
     * positions on the rack". Both jobs are legal; they just cannot run in the same turn.
     */
    public boolean conflictsWith(CrewTask other) {
        if (other == null || other == this)
            return false;
        return (loadsTheRack() && other.drawsFromRack())
                || (drawsFromRack() && other.loadsTheRack());
    }

    /** How the wire names a job: the box, then the task — "1-3:LOAD". */
    public String keyFor(String boxId) {
        return boxId + ":" + name();
    }

    /** The task half of a job key, or null if it names none. */
    public static CrewTask fromKey(String key) {
        int colon = key == null ? -1 : key.indexOf(':');
        if (colon < 0)
            return LOAD;   // an older key naming only a box means the obvious job
        try {
            return valueOf(key.substring(colon + 1).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The box half of a job key. */
    public static String boxOfKey(String key) {
        if (key == null)
            return null;
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(0, colon);
    }
}
