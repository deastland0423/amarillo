package com.sfb.objects.shuttles;

import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.Weapon;

/**
 * Base class for all fighter units (J4.0).
 * Fighters are shuttles with improved combat capabilities: fixed weapons
 * powered
 * by an onboard engine, a crippling threshold, and one free Tactical Maneuver
 * per turn (J4.12 — no energy cost, no breakdown roll).
 */
public abstract class Fighter extends Shuttle implements com.sfb.objects.DroneController {

    /** Damage points needed to cripple this fighter (J1.33). */
    private int bpv;

    private int ecm = 2;
    private int eccm = 2;

    private boolean tacticalManeuverUsed = false;
    private boolean twoSeater = false; // True if this fighter has two seats (e.g. Kzinti AAS_E EW fighter)

    public int getEcm() {
        return ecm;
    }

    public void setEcm(int ecm) {
        this.ecm = ecm;
    }

    public int getEccm() {
        return eccm;
    }

    public void setEccm(int eccm) {
        this.eccm = eccm;
    }

    public int getBpv() {
        return bpv;
    }

    public void setBpv(int bpv) {
        this.bpv = bpv;
    }

    /**
     * Perform a Tactical Maneuver (J4.12): change facing freely, once per turn.
     * No energy cost. No breakdown roll.
     *
     * @param absoluteFacing New facing (1-24).
     * @return True if the maneuver was performed, false if already used this turn.
     */
    public boolean performTacticalManeuver(int absoluteFacing) {
        if (tacticalManeuverUsed)
            return false;
        performHet(absoluteFacing);
        tacticalManeuverUsed = true;
        return true;
    }

    public boolean isTacticalManeuverUsed() {
        return tacticalManeuverUsed;
    }

    public boolean isTwoSeater() {
        return twoSeater;
    }

    public void setTwoSeater(boolean twoSeater) {
        this.twoSeater = twoSeater;
    }

    /**
     * J1.331 + J1.332: speed halved; all non-phaser weapons cease to operate;
     * FighterFusion charges drained (J1.3324).
     */
    @Override
    public String applyCripplingEffects() {
        String baseLine = super.applyCripplingEffects();
        if (baseLine == null)
            return null; // already crippled

        StringBuilder sb = new StringBuilder(baseLine);
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (w instanceof PhaserG) {
                ((PhaserG) w).reduceToPhaserThree(); // J1.3321: Ph-G → Ph-3
            } else if (w instanceof Phaser1 || w instanceof Phaser2 || w instanceof Phaser3) {
                // other phasers remain unchanged (J1.332)
            } else {
                if (w instanceof com.sfb.weapons.FighterFusion)
                    ((com.sfb.weapons.FighterFusion) w).drainCharges(); // J1.3324
                w.damage(); // ceases to operate (J1.332)
            }
        }
        sb.append("; non-phaser weapons offline, Ph-G reduced to Ph-3");
        return sb.toString();
    }

    /**
     * Give back what J1.332 took: the Ph-G's four shots and the non-phaser weapons it put
     * offline. NOT the fusion charges — J1.3324 discharged those, and discharged is spent.
     */
    @Override
    public void uncripple() {
        super.uncripple();
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (w instanceof PhaserG phaserG)
                phaserG.restoreFromPhaserThree();
            else if (!(w instanceof Phaser1 || w instanceof Phaser2 || w instanceof Phaser3))
                w.repair();
        }
    }

    /**
     * True if this fighter has been on the map long enough to fire direct-fire
     * weapons (8 impulses).
     */
    public boolean canFireDirect(int currentImpulse) {
        return super.canFireDirect(currentImpulse);
    }

    // -------------------------------------------------------------------------
    // Guiding its own drones (DroneController, J4.431)
    // -------------------------------------------------------------------------

    private final java.util.Set<com.sfb.objects.Seeker> controlledSeekers =
            new java.util.LinkedHashSet<>();
    private final java.util.Set<com.sfb.objects.Unit> lockOns =
            new java.util.LinkedHashSet<>();

    /** J4.431: a fighter launches at most one drone per turn, whatever it carries. */
    private boolean dronesFiredThisTurn = false;

    /** J4.43: a two-seat fighter, and the EW fighters built from them, guide twelve. */
    public static final int TWO_SEAT_CONTROL = 12;

    /** J4.25: a drone-carrying fighter guides at least two, however few it carries. */
    public static final int MINIMUM_CONTROL = 2;

    /**
     * How many seeking weapons this fighter can guide at once (J4.25, J4.43).
     * <p>
     * J4.25: "a number of drones equal to the number of non-DFDs (non-dogfight drones, i.e.,
     * drones other than type-VI) in its nominal load exclusive of variants, if any (or two
     * drones, whichever is greater)." Three things in that sentence are easy to lose:
     * <ul>
     *   <li>Type-VIs do NOT count. A TAAS carries two type-Is and two type-VIs and guides
     *       TWO, not four — which is what its hand-written constant of 4 got wrong.</li>
     *   <li>NOMINAL load, not what is aboard. Counted off the rail's design drone, so
     *       substituting a type-VI for a type-I does not shrink the capacity — the rule
     *       says "exclusive of variants" and J4.25's own F-15 example turns on it.</li>
     *   <li>A floor of two, so a one-rail fighter still guides a pair.</li>
     * </ul>
     * A fighter carrying no drones at all guides nothing — the floor is for fighters the
     * rule is talking about, and a Stinger is not one.
     */
    @Override
    public int getControlCapacity() {
        // J4.43: two-seaters guide twelve and can take over their squadron's seekers. The
        // taking-over half is not built; see the squadron work.
        if (isTwoSeater())
            return TWO_SEAT_CONTROL;

        int rails = 0;
        int nonDogfight = 0;
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.DroneRail rail))
                continue;
            rails++;
            com.sfb.objects.DroneType design = rail.getDesignDrone();
            if (design != null && !design.isDogfightDrone())
                nonDogfight++;
        }
        if (rails == 0)
            return 0;   // carries no drones; J4.25 is not about it
        return Math.max(MINIMUM_CONTROL, nonDogfight);
    }

    @Override
    public boolean acquireControl(com.sfb.objects.Seeker seeker) {
        if (controlledSeekers.size() >= getControlCapacity())
            return false;
        controlledSeekers.add(seeker);
        return true;
    }

    @Override
    public void releaseControl(com.sfb.objects.Seeker seeker) {
        controlledSeekers.remove(seeker);
    }

    @Override
    public int getControlUsed() {
        return controlledSeekers.size();
    }

    public java.util.List<com.sfb.objects.Seeker> getControlledSeekers() {
        return new java.util.ArrayList<>(controlledSeekers);
    }

    // --- Lock-on (D6.121). A fighter holds its own; see LockOnResolver. ---

    @Override
    public boolean hasLockOn(com.sfb.objects.Unit target) {
        return lockOns.contains(target);
    }

    public void addLockOn(com.sfb.objects.Unit target) {
        lockOns.add(target);
    }

    public void removeLockOn(com.sfb.objects.Unit target) {
        lockOns.remove(target);
    }

    public java.util.Set<com.sfb.objects.Unit> getLockOns() {
        return lockOns;
    }

    /** J4.431: whether this fighter has already spent its one drone launch this turn. */
    public boolean isDronesFiredThisTurn() {
        return dronesFiredThisTurn;
    }

    public void recordDroneFired() {
        dronesFiredThisTurn = true;
    }

    @Override
    public void startTurn() {
        tacticalManeuverUsed = false;
        dronesFiredThisTurn = false;
        getWeapons().cleanUp();
    }
}
