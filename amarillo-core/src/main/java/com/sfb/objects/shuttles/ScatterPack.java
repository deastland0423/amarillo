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

    /**
     * FD7.21: "An admin shuttle used as an SP carries up to six spaces of drones. Other
     * types of shuttles could carry more or fewer as provided in their rules." Set from the
     * catalogue when the pack is built, so the number follows the shuttle it was made from
     * rather than being the same six for everything.
     */
    private int maxDroneSpaces = 6;

    private Unit        target;
    private Unit        controller;
    // identify()/isIdentified() inherited from Shuttle (single source of truth, G24.25).
    private int         launchImpulse = -1;
    private boolean     released      = false; // true after drones have deployed

    private List<Drone> payload        = new ArrayList<>(); // drones loaded onto this shuttle
    private List<Drone> pendingPayload = new ArrayList<>(); // drones being loaded this turn (available next turn)

    public ScatterPack(Shuttle base) {
        setHull(base.getHull());
        setMaxSpeed(base.getMaxSpeed());
        setName(base.getName());
        // Keep what it was built FROM. J3.18 and FD7.11 qualify shuttles by type, and a
        // converted shuttle that forgot its type could not be named or labelled honestly.
        setCatalogType(base.getCatalogType());
        // FD7.21: the capacity belongs to the type this was built from. Without this every
        // pack carried six spaces whatever it was — the catalogue column said otherwise and
        // nothing read it.
        int spaces = base.scatterPackSpaces();
        if (spaces > 0)
            setMaxDroneSpaces(spaces);
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
     * J4.8172: two deck crews may work one shuttle box and no more, and a crew action moves
     * one drone space (FD7.22) — so a pack takes at most two SPACES of drones in a turn,
     * however many crews the ship has standing idle.
     * <p>
     * Spaces, not drones: four type-VIs at half a space each is a legal turn's work, and
     * three type-Is is not. A six-space admin shuttle therefore takes three turns to fill,
     * which is the constraint that makes a scatter pack something you plan rather than
     * something you produce on demand.
     */
    public static final double MAX_SPACES_LOADED_PER_TURN = 2.0;

    /**
     * Stage a drone to be loaded onto this scatter pack at end of turn.
     * <p>
     * Two limits, and they are different things: how much the pack HOLDS (FD7.21, the whole
     * scenario) and how much its deck crews can move in one turn (J4.8172). The per-turn one
     * lives here rather than in the server's request validation because a rule enforced only
     * where the wire happens to touch it is a rule that drifts the first time anything else
     * loads a pack.
     *
     * @return false if it would exceed either the pack's capacity or this turn's two spaces
     */
    public boolean addPendingDrone(Drone drone) {
        if (drone == null)
            return false;
        double committed = getPayloadSpaces() + getPendingSpaces();
        if (committed + drone.getRackSize() > maxDroneSpaces)
            return false;
        if (getPendingSpaces() + drone.getRackSize() > MAX_SPACES_LOADED_PER_TURN)
            return false;   // J4.8172: two crews, two actions, two spaces
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
        setSpeed(0);
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
    @Override public SeekerType getSeekerType()           { return SeekerType.SHUTTLE; }
    @Override public void setSeekerType(SeekerType type)  {}
    @Override public int getEndurance()                   { return Integer.MAX_VALUE; }
    @Override public void setEndurance(int e)             {}
    @Override public int getLaunchImpulse()               { return launchImpulse; }
    @Override public void setLaunchImpulse(int impulse)   { this.launchImpulse = impulse; }
    @Override public int getWarheadDamage()               { return 0; } // no direct damage
    @Override public void setWarheadDamage(int dmg)       {}
    @Override public int impact()                         { return 0; }
    // identify()/isIdentified() inherited from Shuttle

    @Override public boolean isArmed()                    { return true; } // D12.121

    /** Unmanned: a drone carrier flies empty (FD7.0). Revealed by identification (G4.233). */
    @Override
    public boolean isManned() {
        return false;
    }

    /** Prepared, so not launchable as an ordinary shuttle. */
    @Override
    public String specialRole() {
        return "scatterpack";
    }

    /** FD7.0: an admin shuttle in this role presents as the role, not as an admin shuttle. */
    @Override
    public String dtoType() {
        return "scatterpack";
    }

    /** F4.0 ballistic course: a target HEX instead of a target unit. Null when pursuing a unit. */
    private com.sfb.objects.BallisticCourse ballisticCourse;

    @Override
    public com.sfb.objects.BallisticCourse getBallisticCourse() {
        return ballisticCourse;
    }

    @Override
    public void setBallisticCourse(com.sfb.objects.BallisticCourse course) {
        this.ballisticCourse = course;
    }

    // -------------------------------------------------------------------------
    // Saved game (see Seeker.exportState)
    // -------------------------------------------------------------------------

    /** What {@link #exportState} writes under {@link Seeker#SAVE_KIND}. */
    public static final String SAVE_KIND = "SCATTER_PACK";

    /**
     * PARTIAL, and deliberately so: enough for a load to REPORT this craft, not enough to
     * rebuild it.
     *
     * This class wraps a {@code Shuttle}, and rebuilding that shuttle is the deferred half of
     * the saved-game work — a shuttle is created in play from a bay, and restoring one means
     * restoring the bay it came from. So {@code Game.applyState} does not revive this kind; it
     * names it among the problems it returns, which is the whole reason this method writes
     * anything at all. A silent default would have let a scatter-pack in flight simply cease to
     * exist with nothing said.
     */
    /**
     * A scatter-pack in flight, completely enough to rebuild it — payload included.
     *
     * <h2>The payload was a COUNT, and a count is not ammunition</h2>
     * This method used to write {@code payloadCount} and nothing else about what was aboard,
     * because the class was only ever meant to be reported and dropped. Counting them would
     * have been the drone-rack mistake over again from the other side: six type-I drones and six
     * type-VI drones are both "6", and FD7.21 makes the pack's spaces depend on what is loaded.
     * So each drone goes through {@link Drone#exportState()}, the same path a drone in flight
     * takes.
     *
     * <p>{@code pendingPayload} is kept separate because it is not loaded yet — FD7.3 makes a
     * drone loaded this turn available the next — and collapsing the two would hand the player
     * a pack ready to release a turn early.
     */
    @Override
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = super.exportState();
        state.put(Seeker.SAVE_KIND, SAVE_KIND);
        state.put("target", getTarget() == null ? null : getTarget().getName());
        state.put("controller", controller == null ? null : controller.getName());
        state.put("launchImpulse", launchImpulse);
        state.put("released", released);
        state.put("maxDroneSpaces", maxDroneSpaces);

        java.util.List<Object> aboard = new java.util.ArrayList<>();
        for (Drone drone : payload)
            if (drone != null)
                aboard.add(drone.exportState());
        state.put("payload", aboard);

        java.util.List<Object> loading = new java.util.ArrayList<>();
        for (Drone drone : pendingPayload)
            if (drone != null)
                loading.add(drone.exportState());
        state.put("pendingPayload", loading);

        state.put("ballisticCourse",
                ballisticCourse == null ? null : ballisticCourse.exportState());
        return state;
    }

    /**
     * Rebuild a scatter-pack from a save.
     *
     * The base craft comes back through {@link Shuttle#fromState}, which is what makes this
     * possible without the bay it was built in, and the wrapper is CONSTRUCTED around it because
     * {@link #ScatterPack(Shuttle)} is what reads FD7.21's capacity off the catalogue row.
     *
     * @param groundTarget a ballistic course's target, already resolved by the caller
     * @return the pack, or null if the saved craft type is not one this build has
     */
    public static ScatterPack fromState(java.util.Map<String, Object> state, Unit groundTarget) {
        if (state == null)
            return null;
        Shuttle base = Shuttle.fromState(state);
        if (base == null)
            return null;
        ScatterPack pack = new ScatterPack(base);
        pack.applyState(state);

        pack.launchImpulse = com.sfb.systemgroups.StateIo.integer(state, "launchImpulse", -1);
        pack.released = com.sfb.systemgroups.StateIo.flag(state, "released", false);
        pack.maxDroneSpaces = com.sfb.systemgroups.StateIo.integer(state, "maxDroneSpaces",
                pack.maxDroneSpaces);
        pack.payload = dronesFrom(state.get("payload"));
        pack.pendingPayload = dronesFrom(state.get("pendingPayload"));
        pack.setBallisticCourse(com.sfb.objects.BallisticCourse.fromState(
                com.sfb.systemgroups.StateIo.child(state, "ballisticCourse"), groundTarget));
        return pack;
    }

    /** The drones in one saved list, through the same path a drone in flight takes. */
    private static List<Drone> dronesFrom(Object raw) {
        List<Drone> drones = new ArrayList<>();
        if (!(raw instanceof List<?> list))
            return drones;
        for (Object item : list) {
            if (!(item instanceof java.util.Map<?, ?> entry))
                continue;
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> droneState = (java.util.Map<String, Object>) entry;
            Drone drone = Drone.fromState(droneState, null, null, null);
            if (drone != null)
                drones.add(drone);
        }
        return drones;
    }
}
