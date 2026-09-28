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
    // Guiding its own drones (DroneController, J4.25)
    // -------------------------------------------------------------------------

    private final java.util.Set<com.sfb.objects.Seeker> controlledSeekers =
            new java.util.LinkedHashSet<>();
    private final java.util.Set<com.sfb.objects.Unit> lockOns =
            new java.util.LinkedHashSet<>();

    /** J4.241: two drones in a turn is the most any fighter manages. */
    public static final int MAX_DRONES_PER_TURN = 2;

    /** Drones this fighter has let go this turn (J4.24, J4.241). */
    private int dronesFiredThisTurn = 0;

    /** What the first of them was sent at, for J4.241's same-target test. */
    private com.sfb.objects.Unit firstDroneTarget = null;

    /** Whether any of them was a dogfight drone, for J4.241's other test. */
    private boolean firedDogfightDroneThisTurn = false;

    /**
     * The absolute impulse this fighter last let a drone go, or far in the past.
     * <p>
     * Kept alongside the per-turn flag because J4.24 sets TWO limits and the flag only
     * catches one: "a fighter can always launch one drone per turn... but cannot launch two
     * drones on consecutive turns within 1/4 turn (eight impulses) of each other." A flag
     * that resets at the turn boundary lets a fighter fire on impulse 30 and again on
     * impulse 2, four impulses apart and across the boundary the flag is watching.
     */
    private int lastDroneLaunchImpulse = -DRONE_LAUNCH_SPACING;

    // -------------------------------------------------------------------------
    // Electronic warfare pods (J4.96)
    // -------------------------------------------------------------------------

    /** J4.964: two pods for an ordinary fighter, four for an EW or heavy fighter. */
    public static final int MAX_EW_PODS = 2;
    public static final int MAX_EW_PODS_EW_FIGHTER = 4;

    /** J4.9621: at most two of the pods may be "extra" ones carried beyond the drones. */
    public static final int MAX_EXTRA_EW_PODS = 2;

    /** J4.961: "Each EWP can provide two points of either ECM or ECCM, or one of each." */
    public static final int POINTS_PER_EW_POD = 2;

    private int extraEwPods;
    private int podEcm;
    private int podEccm;
    private boolean podsActive = true;

    /** J4.964: how many pods this fighter may carry at all. */
    public int maxEwPods() {
        return isTwoSeater() ? MAX_EW_PODS_EW_FIGHTER : MAX_EW_PODS;
    }

    /**
     * Every pod aboard: the ones occupying rails (J4.962) and the extras slung alongside
     * (J4.9621). Counted rather than stored, because a rail is where a pod actually sits
     * and two places recording the same fact drift.
     */
    public int getEwPods() {
        return railEwPods() + extraEwPods;
    }

    /** Pods carried in place of a drone, which is the ordinary way (J4.962). */
    public int railEwPods() {
        int n = 0;
        for (Weapon w : getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail && rail.hasEwPod())
                n++;
        return n;
    }

    /**
     * Fit pods to rails, each displacing a drone (J4.962), up to J4.964's limit.
     *
     * @return the number of rails now carrying one
     */
    public int fitEwPods(int pods) {
        int want = Math.max(0, Math.min(pods, maxEwPods() - extraEwPods));
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.DroneRail rail))
                continue;
            // J4.2312: a standard rail and nothing else, so a TAAS can hang pods on its
            // two standard rails and never on its light ones.
            if (!rail.canCarryEwPod())
                continue;
            if (railEwPods() < want)
                rail.fitEwPod();
            else if (rail.hasEwPod() && railEwPods() > want)
                rail.clearEwPod();
        }
        spreadPodPointsEvenly();
        return railEwPods();
    }

    /**
     * J4.9621: pods carried WITHOUT giving up a drone. Each costs a point of speed and a
     * point of dogfight rating, and no fighter may carry more than two of them.
     */
    public int getExtraEwPods() { return extraEwPods; }

    public int setExtraEwPods(int extra) {
        int room = maxEwPods() - railEwPods();
        extraEwPods = Math.max(0,
                Math.min(Math.min(extra, MAX_EXTRA_EW_PODS), Math.max(0, room)));
        spreadPodPointsEvenly();
        return extraEwPods;
    }

    /**
     * J4.9622: throw an extra pod overboard to get the speed and rating back. "The pod
     * cannot be recovered" — so this destroys it rather than returning it to the rack.
     *
     * @return true if one was dropped
     */
    public boolean dropExtraEwPod() {
        if (extraEwPods <= 0)
            return false;
        extraEwPods--;
        spreadPodPointsEvenly();   // J4.9622: "the EW situation must be rebalanced"
        return true;
    }

    /**
     * J4.9621: "for each extra one carried (to a maximum of two), reduce the speed (with
     * or without warp packs) and the dogfight rating (J7.62) of the fighter by one."
     * Pods that REPLACED a drone cost nothing: J4.962 says so outright.
     */
    @Override
    protected int speedPenalty() {
        return extraEwPods;
    }

    /** J4.967: "A fighter can turn off its EWPs during any Lock-On Stage." */
    public boolean arePodsActive() { return podsActive; }

    public void setPodsActive(boolean active) { this.podsActive = active; }

    /**
     * J4.961: declare how this turn's pod points are split. Each pod gives two points as
     * ECM, as ECCM, or one of each — so any split of twice the pod count is reachable,
     * and the only real constraint is the total.
     *
     * @return false if the split does not spend exactly the points the pods produce
     */
    public boolean allocatePodEw(int ecm, int eccm) {
        if (ecm < 0 || eccm < 0 || ecm + eccm != getEwPods() * POINTS_PER_EW_POD)
            return false;
        podEcm = ecm;
        podEccm = eccm;
        return true;
    }

    private void spreadPodPointsEvenly() {
        int points = getEwPods() * POINTS_PER_EW_POD;
        podEcm = points / 2;
        podEccm = points - podEcm;
    }

    /**
     * Whether the pods are doing anything at all.
     * <p>
     * J1.3322: "EW systems (EW pods, MRS, SWAC) cease to function if the shuttle is
     * crippled. Built-in EW points continue to operate." So crippling takes the pods and
     * leaves J4.47's two-and-two — which is the practical difference between the two.
     */
    public boolean podsWorking() {
        return podsActive && getEwPods() > 0 && !isCrippled();
    }

    /** ECM the pods are producing this turn, or none if they are off or shot away. */
    public int getPodEcm() { return podsWorking() ? podEcm : 0; }

    /** ECCM the pods are producing this turn. */
    public int getPodEccm() { return podsWorking() ? podEccm : 0; }

    /**
     * Every point of ECM this fighter has of its own: J4.47's built-in two plus whatever
     * the pods are making, held to J4.91's ceiling of six.
     * <p>
     * J4.91 counts built-in, pods and lent points against that six and excludes natural
     * sources, so Erratic Maneuvers and asteroids sit outside it — which is why the cap
     * is applied here, to the fighter's OWN points, and not to the breakdown's total.
     */
    public int totalOwnEcm() {
        return Math.min(MAX_USABLE_EW, getEcm() + getPodEcm());
    }

    public int totalOwnEccm() {
        return Math.min(MAX_USABLE_EW, getEccm() + getPodEccm());
    }

    /** J4.91: "six points each of ECM and ECCM, not six total points." */
    public static final int MAX_USABLE_EW = 6;

    /** The squadron this fighter belongs to (J4.46), or null if it is unassigned. */
    private com.sfb.objects.Squadron squadron;

    public com.sfb.objects.Squadron getSquadron() { return squadron; }

    public void setSquadron(com.sfb.objects.Squadron squadron) { this.squadron = squadron; }

    /** J4.221/J4.46: whether these two fly together, which decides who may take a handoff. */
    public boolean sharesSquadronWith(Fighter other) {
        return squadron != null && other != null && other.getSquadron() == squadron;
    }

    /**
     * J4.462: how much of a squadron's twelve this fighter spends.
     * <p>
     * One for an ordinary size-1 fighter. "Heavy fighters, medium bombers, and heavy
     * bombers count as two size-1 fighters for purposes of organizing squadrons" — so
     * those override this to two, and a squadron of them maxes out at six. None exist yet;
     * the method is here because J4.462 cannot be stated without it, and a squadron that
     * assumed every craft was worth one would be wrong the day a heavy arrives.
     */
    public int squadronSlots() {
        return 1;
    }

    /** J4.24: two drones may not leave the same fighter within a quarter turn. */
    public static final int DRONE_LAUNCH_SPACING = 8;

    /** J4.43: a two-seat fighter, and the EW fighters built from them, guide twelve. */
    public static final int TWO_SEAT_CONTROL = 12;

    /** J4.25: a drone-carrying fighter guides at least two, however few it carries. */
    public static final int MINIMUM_CONTROL = 2;

    /**
     * How many seeking weapons this fighter can guide at once (J4.25, J4.43).
     * <p>
     * J4.25: "a number of drones equal to the number of non-DFDs (non-dogfight drones, i.e.,
     * drones other than type-VI) in its nominal load exclusive of variants, if any (or two
     * drones, whichever is greater)."
     * <p>
     * Counted off the RAILS, and specifically off each rail's TYPE: every rail that is not a
     * light one is a control channel, because a light rail is the one built for dogfight
     * drones (J4.232) and a type-VI is what J4.25 declines to count. So a fighter of two
     * standard, two heavy and two light rails guides four.
     * <p>
     * The rail rather than the drone nominally on it, and that is the whole of "exclusive of
     * variants": J4.2311 lets a player drop a type-VI onto a standard rail freely, and doing
     * so must not cost a channel. A rail's type changes only in a refit (J4.232), which is a
     * real change to the fighter; what is loaded into it changes every sortie.
     * <p>
     * A fighter carrying no drones at all guides nothing — the floor of two is for the
     * fighters J4.25 is talking about, and a Stinger is not one.
     */
    @Override
    public int getControlCapacity() {
        // J4.43: two-seaters guide twelve and can take over their squadron's seekers. The
        // taking-over half is not built; it needs squadron organisation (J4.46).
        if (isTwoSeater())
            return TWO_SEAT_CONTROL;

        int rails = 0;
        int channels = 0;
        for (Weapon w : getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.DroneRail rail))
                continue;
            rails++;
            if (rail.getRailType() != com.sfb.weapons.DroneRail.DroneRailType.LIGHT)
                channels++;
        }
        if (rails == 0)
            return 0;   // carries no drones; J4.25 is not about it
        return Math.max(MINIMUM_CONTROL, channels);
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

    /** J4.24: whether this fighter has already let a drone go this turn. */
    public boolean isDronesFiredThisTurn() {
        return dronesFiredThisTurn > 0;
    }

    public int getDronesFiredThisTurn() {
        return dronesFiredThisTurn;
    }

    // --- J4.242 exemptions, declared by the fighters that have them ---

    /**
     * J4.242: whether this fighter may send its two drones at DIFFERENT targets, which
     * J4.241's condition A otherwise forbids. The F-15 and the TAAS may, but only when the
     * two are not launched on the same impulse.
     */
    public boolean mayLaunchAtDifferentTargets() {
        return false;
    }

    /**
     * J4.242: whether this fighter may launch two drones NEITHER of which is a dogfight
     * drone, which J4.241's condition B otherwise forbids. The F-14, F-15 and TAAS may, in
     * any case.
     */
    public boolean mayLaunchTwoStandardDrones() {
        return false;
    }

    /**
     * Why this fighter may not let this drone go at this target right now, or null if it
     * may (J4.24, J4.241, J4.242).
     * <p>
     * The FIRST drone of a turn is free but for the quarter-turn spacing measured from the
     * last one, which reaches back across the turn boundary (J4.24).
     * <p>
     * A SECOND is J4.241, and both its conditions must hold: the pair goes at the same
     * target, AND at least one of them is a dogfight drone. Conjunctive — read as "or" it
     * would let a fighter split two standard drones between two targets, which is the thing
     * the rule exists to stop. Note that J4.241 lifts the spacing as well as the count:
     * "two drones per turn (or within 1/4 turn)", so a qualifying second drone need not
     * wait its eight impulses.
     */
    public String droneLaunchRefusal(com.sfb.objects.Unit target,
            com.sfb.objects.Drone drone, int currentImpulse) {
        if (dronesFiredThisTurn == 0) {
            int wait = impulsesUntilNextDrone(currentImpulse);
            return wait > 0
                    ? getName() + " launched a drone " + (DRONE_LAUNCH_SPACING - wait)
                            + " impulse" + (DRONE_LAUNCH_SPACING - wait == 1 ? "" : "s")
                            + " ago - two may not leave within a quarter turn (J4.24)"
                    : null;
        }
        if (dronesFiredThisTurn >= MAX_DRONES_PER_TURN)
            return getName() + " has launched two drones this turn, which is all any"
                    + " fighter may (J4.241)";

        boolean sameTarget = target != null && target == firstDroneTarget;
        if (!sameTarget
                && !(mayLaunchAtDifferentTargets() && currentImpulse != lastDroneLaunchImpulse))
            return getName() + " may only send its second drone at the same target as the"
                    + " first (J4.241)";

        boolean pairHasDogfight = firedDogfightDroneThisTurn
                || (drone != null && drone.getDroneType() != null
                        && drone.getDroneType().isDogfightDrone());
        if (!pairHasDogfight && !mayLaunchTwoStandardDrones())
            return getName() + " may only launch a second drone when one of the pair is a"
                    + " dogfight drone (J4.241)";
        return null;
    }

    /**
     * Impulses still to wait before this fighter may let another drone go (J4.24), or zero.
     * Separate from the per-turn limit and outlives it: the spacing is measured from the
     * last launch, so it reaches back across the turn boundary that clears the flag.
     */
    public int impulsesUntilNextDrone(int currentImpulse) {
        return Math.max(0,
                DRONE_LAUNCH_SPACING - (currentImpulse - lastDroneLaunchImpulse));
    }

    public void recordDroneFired(com.sfb.objects.Unit target, com.sfb.objects.Drone drone,
            int currentImpulse) {
        if (dronesFiredThisTurn == 0)
            firstDroneTarget = target;
        dronesFiredThisTurn++;
        if (drone != null && drone.getDroneType() != null
                && drone.getDroneType().isDogfightDrone())
            firedDogfightDroneThisTurn = true;
        lastDroneLaunchImpulse = currentImpulse;
    }

    @Override
    public void startTurn() {
        tacticalManeuverUsed = false;
        // The turn's count resets; lastDroneLaunchImpulse deliberately does NOT, because
        // J4.24's quarter turn is measured from the launch and reaches across the boundary.
        dronesFiredThisTurn = 0;
        firstDroneTarget = null;
        firedDogfightDroneThisTurn = false;
        getWeapons().cleanUp();
    }
}
