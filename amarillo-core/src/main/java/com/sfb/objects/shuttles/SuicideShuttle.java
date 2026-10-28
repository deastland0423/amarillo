package com.sfb.objects.shuttles;

import com.sfb.objects.*;

/**
 * A shuttle configured as a suicide weapon.
 *
 * Arming takes 3 turns. Each turn the player allocates 1–3 energy.
 * Warhead damage = totalEnergy * 2 (max 18 at 3 energy/turn × 3 turns).
 * Speed is capped at the base shuttle's maxSpeed.
 * Controller-guided — requires an owning ship with lock-on, like a drone.
 * If arming is abandoned or hold cost not paid, energy is lost and the
 * shuttle reverts to a normal admin shuttle.
 */
public class SuicideShuttle extends Shuttle implements Seeker {

    private Unit   target;
    private Unit   controller;
    // identify()/isIdentified() inherited from Shuttle (single source of truth, G24.25).

    // Arming state
    private int     armingTurnsComplete = 0;     // 0–3; armed when == 3
    private int     totalEnergy         = 0;     // cumulative energy across all arming turns
    // What was paid on the most recent arming turn. The cumulative total cannot answer this
    // - divided by the turns it gives the average, which stops being the rate as soon as the
    // player varies it - and the allocation form needs it to offer "same again".
    private int     lastArmingEnergy    = 0;
    // Whether this turn's upkeep has been met, by EITHER route: arming energy while it is
    // still arming, or the hold once it is fully armed. Named for the meaning rather than
    // for one of the two payments - as "holdPaid" it read as unrelated to arming, which is
    // exactly the mistake the code made.
    private boolean upkeepPaidThisTurn   = false;

    public SuicideShuttle(Shuttle base) {
        setHull(base.getHull());
        setMaxSpeed(base.getMaxSpeed());
        setName(base.getName());
        // Keep what it was built FROM. J3.18 and FD7.11 qualify shuttles by type, and a
        // converted shuttle that forgot its type could not be named or labelled honestly.
        setCatalogType(base.getCatalogType());
    }

    // -------------------------------------------------------------------------
    // Arming
    // -------------------------------------------------------------------------

    /**
     * Apply energy this turn (1–3 points). Call once per energy allocation.
     * @return true if accepted, false if already armed or invalid amount.
     */
    public boolean arm(int energy) {
        if (isFullyArmed()) return false;
        if (energy < 1 || energy > 3) return false;
        totalEnergy += energy;
        lastArmingEnergy = energy;
        armingTurnsComplete++;
        // Arming energy IS this turn's upkeep. Nothing is owed on top of it until the shuttle
        // is fully armed, at which point the 1-point hold takes over.
        upkeepPaidThisTurn = true;
        return true;
    }

    /** Armed if fully armed (3 turns) OR in the process of being armed (D12.123). */
    @Override
    public boolean isArmed() {
        return armingTurnsComplete >= 1;
    }

    public boolean isFullyArmed() {
        return armingTurnsComplete >= 3;
    }

    /**
     * Record the 1-point hold (owed only once fully armed). Arming energy pays its own way
     * through {@link #arm}, so this is not called while a shuttle is still arming.
     */
    public void payHold() { this.upkeepPaidThisTurn = true; }

    /** True if this turn's upkeep was met, by arming energy or by the hold. */
    public boolean isUpkeepPaid()       { return upkeepPaidThisTurn; }

    /** True once the shuttle owes the 1-point hold rather than arming energy. */
    public boolean owesHold()           { return isFullyArmed(); }

    /** Called at end of turn — next turn must be paid for on its own. */
    public void resetUpkeep()           { this.upkeepPaidThisTurn = false; }

    public int getArmingTurnsComplete() { return armingTurnsComplete; }
    public int getTotalEnergy()         { return totalEnergy; }

    /** Energy paid on the most recent arming turn; 0 before any. */
    public int getLastArmingEnergy()    { return lastArmingEnergy; }

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
    @Override public int getLaunchImpulse()               { return 0; }
    @Override public void setLaunchImpulse(int i)         {}
    @Override public int getWarheadDamage()               { return totalEnergy * 2; }
    @Override public void setWarheadDamage(int dmg)       {}
    @Override public int impact()                         { return getWarheadDamage(); }
    // identify()/isIdentified() inherited from Shuttle

    /** Unmanned: nobody rides the bomb (J2.2). Revealed by identification (G4.233). */
    @Override
    public boolean isManned() {
        return false;
    }

    /** Prepared, so not launchable as an ordinary shuttle. */
    @Override
    public String specialRole() {
        return "suicide shuttle";
    }

    /** J2.0: an admin shuttle in this role presents as the role, not as an admin shuttle. */
    @Override
    public String dtoType() {
        return "suicide";
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
    public static final String SAVE_KIND = "SUICIDE_SHUTTLE";

    /**
     * A suicide shuttle in flight, completely enough to rebuild it.
     *
     * <h2>This used to be partial on purpose, and no longer needs to be</h2>
     * The old note here said rebuilding one meant rebuilding the bay it came from, so the save
     * wrote just enough for a load to REPORT the craft and let it go. That reason has expired:
     * {@link Shuttle#fromState} builds a craft from its catalogue row and needs no bay at all.
     * So this now writes the whole craft — {@code super.exportState()} carries the hull damage,
     * the crippling and the position — and {@link #fromState} wraps it.
     *
     * <h2>The arming state is the whole weapon</h2>
     * J3.0 arms a suicide shuttle over three turns, and {@code armingTurnsComplete} with
     * {@code totalEnergy} is what decides whether it detonates for nothing or for its full
     * warhead. Dropping them would have turned a loaded weapon back into a bus.
     * {@code upkeepPaidThisTurn} travels too: a fully armed shuttle owes a hold cost each turn,
     * and a save taken mid-turn that forgot it would grant the hold free.
     */
    @Override
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = super.exportState();
        state.put(Seeker.SAVE_KIND, SAVE_KIND);
        state.put("target", getTarget() == null ? null : getTarget().getName());
        state.put("controller", controller == null ? null : controller.getName());
        state.put("armingTurnsComplete", armingTurnsComplete);
        state.put("totalEnergy", totalEnergy);
        state.put("lastArmingEnergy", lastArmingEnergy);
        state.put("upkeepPaidThisTurn", upkeepPaidThisTurn);
        state.put("ballisticCourse",
                ballisticCourse == null ? null : ballisticCourse.exportState());
        return state;
    }

    /**
     * Rebuild a suicide shuttle from a save.
     *
     * The base craft comes back through {@link Shuttle#fromState}, which is what makes this
     * possible without the bay it was converted in, and the wrapper is CONSTRUCTED around it
     * because the constructor is what carries the hull, the speed and the catalogue type across.
     *
     * @param groundTarget a ballistic course's target, already resolved by the caller
     * @return the shuttle, or null if the saved craft type is not one this build has
     */
    public static SuicideShuttle fromState(java.util.Map<String, Object> state,
            Unit groundTarget) {
        if (state == null)
            return null;
        Shuttle base = Shuttle.fromState(state);
        if (base == null)
            return null;
        SuicideShuttle shuttle = new SuicideShuttle(base);
        shuttle.applyState(state);

        shuttle.armingTurnsComplete = com.sfb.systemgroups.StateIo.integer(state,
                "armingTurnsComplete", 0);
        shuttle.totalEnergy = com.sfb.systemgroups.StateIo.integer(state, "totalEnergy", 0);
        shuttle.lastArmingEnergy = com.sfb.systemgroups.StateIo.integer(state,
                "lastArmingEnergy", 0);
        shuttle.upkeepPaidThisTurn = com.sfb.systemgroups.StateIo.flag(state,
                "upkeepPaidThisTurn", false);
        shuttle.setBallisticCourse(com.sfb.objects.BallisticCourse.fromState(
                com.sfb.systemgroups.StateIo.child(state, "ballisticCourse"), groundTarget));
        return shuttle;
    }
}
