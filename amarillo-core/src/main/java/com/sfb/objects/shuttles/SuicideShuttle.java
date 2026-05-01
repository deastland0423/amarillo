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
    private boolean identified = false;

    // Arming state
    private int     armingTurnsComplete = 0;     // 0–3; armed when == 3
    private int     totalEnergy         = 0;     // cumulative energy across all arming turns
    private boolean holdPaidThisTurn    = false; // true if hold energy was allocated in current EA

    public SuicideShuttle(Shuttle base) {
        setHull(base.getHull());
        setMaxSpeed(base.getMaxSpeed());
        setName(base.getName());
    }

    // -------------------------------------------------------------------------
    // Arming
    // -------------------------------------------------------------------------

    /**
     * Apply energy this turn (1–3 points). Call once per energy allocation.
     * @return true if accepted, false if already armed or invalid amount.
     */
    public boolean arm(int energy) {
        if (isArmed()) return false;
        if (energy < 1 || energy > 3) return false;
        totalEnergy += energy;
        armingTurnsComplete++;
        return true;
    }

    public boolean isArmed() {
        return armingTurnsComplete >= 3;
    }

    /** Record that hold energy was paid this turn. */
    public void payHold() { this.holdPaidThisTurn = true; }

    public boolean isHoldPaid()         { return holdPaidThisTurn; }

    /** Called at end of turn — resets hold flag for next turn. */
    public void resetHold()             { this.holdPaidThisTurn = false; }

    public int getArmingTurnsComplete() { return armingTurnsComplete; }
    public int getTotalEnergy()         { return totalEnergy; }

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
    @Override public void identify()                      { identified = true; }
    @Override public boolean isIdentified()               { return identified; }
}
