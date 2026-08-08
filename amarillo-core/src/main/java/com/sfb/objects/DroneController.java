package com.sfb.objects;

/**
 * Any unit capable of controlling drones: ships, drone-armed fighters, and eventually bases.
 * Controllers hold a lock-on to the target and manage a finite number of control channels.
 */
public interface DroneController {

    /** Claim a control channel for a seeker. Returns false if at capacity. */
    boolean acquireControl(Seeker seeker);

    /** Release a control channel when a seeker impacts, is destroyed, or expires. */
    void releaseControl(Seeker seeker);

    /** True if this controller has sensor lock-on to the given target. */
    boolean hasLockOn(Unit target);

    /** Maximum number of seekers this unit can simultaneously guide. */
    int getControlCapacity();

    /** Number of control channels currently in use. */
    int getControlUsed();
}
