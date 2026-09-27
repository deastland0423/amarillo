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

    /** Repair damage to the shuttle or fighter, one point an action (J4.818). Not yet built. */
    REPAIR;

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
