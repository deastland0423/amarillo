package com.sfb.objects.shuttles;

import com.sfb.objects.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A shuttle loaded with drones and sent to a target hex, where it releases
 * them after 8 impulses (1/4 turn).
 *
 * Movement: controller-guided like a drone (requires lock-on from owning ship).
 * Speed: capped at base shuttle maxSpeed.
 * Payload: up to 6 rack spaces of drones (cost: 1 deck crew per rack space loaded).
 * After 8 impulses the payload is released and the shuttle drifts in place.
 * The drifting shuttle is NOT removed — it can be recovered by tractor beam.
 */
public class ScatterPack extends Shuttle implements Seeker {

    private static final int RELEASE_DELAY = 8; // impulses before drones deploy

    private int maxDroneSpaces = 6; // admin shuttle default; other types may differ

    private Unit        target;
    private Unit        controller;
    private boolean     identified   = false;
    private int         launchImpulse = -1;
    private boolean     released      = false; // true after drones have deployed

    private List<Drone> payload        = new ArrayList<>(); // drones loaded onto this shuttle
    private List<Drone> pendingPayload = new ArrayList<>(); // drones being loaded this turn (available next turn)

    public ScatterPack(Shuttle base) {
        setHull(base.getHull());
        setMaxSpeed(base.getMaxSpeed());
        setName(base.getName());
    }

    // -------------------------------------------------------------------------
    // Payload management
    // -------------------------------------------------------------------------

    /** Total rack spaces currently occupied by the payload. Max 6. */
    public double getPayloadSpaces() {
        return payload.stream().mapToDouble(Drone::getRackSize).sum();
    }

    /**
     * Add a drone to the payload.
     * @return true if there is space (total rack spaces ≤ 6).
     */
    public int    getMaxDroneSpaces() { return maxDroneSpaces; }
    public void   setMaxDroneSpaces(int max) { this.maxDroneSpaces = max; }

    public boolean addDrone(Drone drone) {
        if (getPayloadSpaces() + drone.getRackSize() > maxDroneSpaces) return false;
        payload.add(drone);
        return true;
    }

    public List<Drone> getPayload() { return payload; }

    /**
     * Stage a drone to be loaded onto this scatter pack at end of turn.
     * @return false if total committed spaces (payload + pending) would exceed max.
     */
    public boolean addPendingDrone(Drone drone) {
        double committed = getPayloadSpaces()
                + pendingPayload.stream().mapToDouble(Drone::getRackSize).sum();
        if (committed + drone.getRackSize() > maxDroneSpaces) return false;
        pendingPayload.add(drone);
        return true;
    }

    /** Rack spaces currently staged for next-turn loading. */
    public double getPendingSpaces() {
        return pendingPayload.stream().mapToDouble(Drone::getRackSize).sum();
    }

    public List<Drone> getPendingPayload() { return pendingPayload; }

    /** Called at end of turn — moves pending drones into the live payload. */
    public void applyPendingPayload() {
        payload.addAll(pendingPayload);
        pendingPayload.clear();
    }

    // -------------------------------------------------------------------------
    // Release logic
    // -------------------------------------------------------------------------

    /**
     * Returns true when 8 impulses have elapsed since launch and drones
     * have not yet been released.
     */
    public boolean isReadyToRelease(int currentImpulse) {
        return !released && launchImpulse >= 0
                && (currentImpulse - launchImpulse) >= RELEASE_DELAY;
    }

    /**
     * Mark drones as released. Clears the payload list (caller places the
     * drones on the map) and clears the target so the shuttle drifts.
     * @return the list of drones to place on the map.
     */
    public List<Drone> release() {
        List<Drone> toRelease = new ArrayList<>(payload);
        payload.clear();
        target    = null;
        released  = true;
        return toRelease;
    }

    public boolean isReleased() { return released; }

    /** Released scatter packs drift automatically — not player-controlled. */
    @Override public boolean isPlayerControlled() { return !released; }

    // -------------------------------------------------------------------------
    // Seeker interface
    // -------------------------------------------------------------------------

    @Override public void setTarget(Unit target)          { this.target = target; }
    @Override public Unit getTarget()                     { return target; }
    @Override public void setController(Unit controller)  { this.controller = controller; }
    @Override public Unit getController()                 { return controller; }
    @Override public boolean isSelfGuiding()              { return false; }
    @Override public void setSelfGuiding(boolean sg)      {}
    @Override public SeekerType getSeekerType()           { return SeekerType.SHUTTLE; }
    @Override public void setSeekerType(SeekerType type)  {}
    @Override public int getEndurance()                   { return Integer.MAX_VALUE; }
    @Override public void setEndurance(int e)             {}
    @Override public int getLaunchImpulse()               { return launchImpulse; }
    @Override public void setLaunchImpulse(int impulse)   { this.launchImpulse = impulse; }
    @Override public int getWarheadDamage()               { return 0; } // no direct damage
    @Override public void setWarheadDamage(int dmg)       {}
    @Override public int impact()                         { return 0; }
    @Override public void identify()                      { identified = true; }
    @Override public boolean isIdentified()               { return identified; }
}
