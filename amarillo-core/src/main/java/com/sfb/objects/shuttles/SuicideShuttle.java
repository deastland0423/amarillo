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
    @Override public boolean isSelfGuiding()              { return false; }
    @Override public void setSelfGuiding(boolean sg)      {}
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
}
