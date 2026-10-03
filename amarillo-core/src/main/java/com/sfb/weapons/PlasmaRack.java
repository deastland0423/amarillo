package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

/**
 * The plasma rack (FP10.0) — "a rapid-fire launcher for type-D plasma torpedoes, first deployed
 * in Y165. It is intended primarily for defense against massed fighter and drone attacks, but
 * has a supplementary offensive capability."
 *
 * <h2>Why this is not a PlasmaLauncher subclass</h2>
 * A type-F on a fighter IS an ordinary launcher carried by something small (J4.27), which is
 * why {@code FighterPlasmaF} extends {@link PlasmaLauncher}. A rack is not: it arms nothing,
 * holds four pre-packaged torpedoes, fires at two different rates depending on a mode it
 * chooses each turn, and has no pseudo-torpedo at all (FP9.13). Inheriting the launcher would
 * mean suppressing the arming schedule, the hold, the pseudo and the overload — more removed
 * than kept.
 *
 * <h2>Why it is not a DroneRack type either, which is the closer call</h2>
 * The fighter MOUNT for a type-D is a {@link DroneRail} with a {@code PLASMA_D} rail type,
 * because J4.825 authorises exactly that: "the rearming and storage rules for drones are used
 * for type-D plasma torpedoes". The ship mount is the opposite case, and two rules say so:
 * <ul>
 *   <li><b>FP10.16</b>: "Plasma racks are destroyed on <i>torpedo</i> hits. (A change from an
 *       earlier edition.)" — so it must not reach {@code DacPriority.dronePriority}, which
 *       switches on {@code instanceof DroneRack} and would file it under drones.</li>
 *   <li><b>FP10.11</b>: it "cannot hold any type of drone or anti-drone" — there is no shared
 *       ammunition to justify a shared class.</li>
 * </ul>
 * What IS shared is storage, and that is reused: FP9.21 gives a torpedo one space of the same
 * hold the fighters draw on, and FP10.31 says so outright — "Plasma-Ds stored for use by
 * fighters are interchangeable with those stored for use by plasma racks."
 *
 * <h2>Torpedoes are counts</h2>
 * As on the fighter rails and in the stores, for FP9.18's reason: "Pl-Ds, like other plasmas,
 * do not have guidance options, different speeds, or warhead modules as drones do." One type-D
 * is any other, so there is nothing to distinguish and a {@link PlasmaTorpedo} is minted at
 * launch.
 */
public class PlasmaRack extends Weapon implements Launcher, DirectFire {

    /**
     * FP10.1: "holding four one-space type-D plasma torpedoes", and FP10.14 makes that final —
     * "Due to the violent nature of the launch of the type-D plasma torpedo, there can be no
     * larger rack for this weapon (such as the type-B drone rack) on non-bases. The ammunition
     * (four torpedoes per rack) cannot be increased."
     */
    public static final int CAPACITY = 4;

    /** FP10.31: "Each plasma rack has four reload torpedoes." */
    public static final int TORPEDOES_PER_RELOAD_SET = 4;

    /** FP10.312: "Each rack comes with one set of reloads (four torpedoes)." */
    public static final int BASE_RELOAD_SETS = 1;

    /**
     * FP10.312: "Along with the Y175 drone rack refits, each plasma rack has two sets of
     * reloads; there is no extra cost for this."
     * <p>
     * Not faction-specific, unlike the Y175 refits beside it in {@code applyYearUpgrades} —
     * the rule is about the weapon, so every plasma rack on every hull gets it.
     */
    public static final int Y175_RELOAD_SETS = 2;

    /** YFP10.0: "PLASMA RACK: Not invented until Y165." */
    public static final int FIRST_YEAR = 165;

    /**
     * FP9.22 and FP10.32: half an energy point per torpedo, "reserve or allocated". The same
     * figure the fighter rails pay, which is the point of FP10.32 — "Plasma-Ds placed in a
     * plasma rack require the same activation energy (FP9.22) as those loaded on fighters or
     * ready racks (1/2 point)."
     */
    public static final double ACTIVATION_ENERGY = 0.5;

    /** FP10.211: the offensive-mode gap, measured back into the previous turn. */
    public static final int OFFENSIVE_SPACING = 8;

    /** FP10.212: defensive mode reaches "an effective range of six hexes". */
    public static final int DEFENSIVE_RANGE = 6;

    /** FP10.212: defensive mode engages "size-5 and smaller targets". */
    public static final int DEFENSIVE_MAX_TARGET_SIZE = 5;

    /**
     * Which way this rack is working this turn (FP10.21).
     * <p>
     * Nobody declares it: "The decision on which mode to use is made at the point of the first
     * firing of a given plasma rack during a given turn. The rack operates in the selected mode
     * for the remainder of the turn, but it can change modes when first fired during the next
     * turn."
     * <p>
     * Deliberately the same shape as {@link DroneRack.RackMode}, which settles the type-G's
     * drone-or-anti-drone choice the same way and for the same reason (FD3.71). Two instances
     * of one idiom; not yet extracted, because the axes differ — a type-G's mode decides WHAT
     * it fires, a plasma rack's decides how fast and at what.
     */
    public enum RackMode { UNDECIDED, OFFENSIVE, DEFENSIVE }

    private RackMode modeThisTurn = RackMode.UNDECIDED;

    /** Torpedoes aboard, 0..CAPACITY (FP10.1). */
    private int torpedoes = CAPACITY;

    /** How many of them have been paid for under FP9.22; never more than {@link #torpedoes}. */
    private int activeTorpedoes;

    /** Reload sets still available (FP10.312), each of four torpedoes. */
    private int reloadSets = BASE_RELOAD_SETS;

    /** FP10.23: a rack being reloaded "cannot be fired in either offensive or defensive mode". */
    private boolean reloadingThisTurn;

    /** Torpedoes this rack has sent this turn — the offensive mode limit is one (FP10.211). */
    private int firedThisTurn;

    /**
     * Torpedoes this rack has BOLTED this turn (FP10.221).
     * <p>
     * Counted separately from {@link #firedThisTurn} because it is the one limit the mode does
     * not already imply: "In either mode, the rack can fire a maximum of one torpedo per turn as
     * a plasma bolt." Offensive mode fires once a turn anyway, so this binds only in DEFENSIVE
     * mode, where a rack may otherwise fire every impulse — four torpedoes at a drone wave, of
     * which at most one may be a bolt.
     */
    private int boltsThisTurn;

    /**
     * The absolute impulse this rack last fired on, or far in the past.
     * <p>
     * Outlives the turn on purpose. FP10.211's offensive shot may not come "within 1/4 turn of
     * a torpedo fired in either mode during the previous turn", so the gap is measured from the
     * firing and reaches back across the boundary that clears {@link #firedThisTurn} — the same
     * trap J4.24 sets for a fighter's drones.
     */
    private int lastFiredImpulse = -OFFENSIVE_SPACING;

    public PlasmaRack() {
        // "PlasmaDRack" because DacPriority.torpPriority ALREADY has a case for that exact
        // string - priority 69, from Annex #7E - and the type is the key it switches on. A
        // weapon whose type matches no case falls to Integer.MAX_VALUE and is damaged last in
        // its whole column.
        //
        // So this is not a free choice of label. "PL-D" was the first spelling here, after
        // FP10.1's "The plasma rack is designated 'PL-D' on SSD sheets", and it read better
        // while quietly putting the rack behind every other torpedo system on the ship.
        setType("PlasmaDRack");
        // FP10.16: "Plasma racks are destroyed on 'torpedo' hits. (A change from an earlier
        // edition.)" NOT "drone", which is what a drone rack takes and what an earlier edition
        // of this rule said - so this is the one line that keeps the rack off the drone column.
        //
        // "torp", not "torpedo": the string is a KEY, matched by Ship's DAC resolution
        // (`"torp".equals(w.getDacHitLocaiton())`), and a weapon whose key matches no column is
        // not protected by the rule, it is INVULNERABLE - nothing can ever select it. Spelled
        // "torpedo" first, which read correctly and left the rack unhittable.
        setDacHitLocaiton("torp");
        // FP10.12: "All plasma racks have a 180 degree field of fire (usually LS or RS)." The
        // arc comes from the ship file like any other weapon; nothing is assumed here.
    }

    // ---------------------------------------------------------------- ammunition

    public int getTorpedoes() {
        return torpedoes;
    }

    public boolean isEmpty() {
        return torpedoes <= 0;
    }

    public boolean isFull() {
        return torpedoes >= CAPACITY;
    }

    /** Spaces this rack holds, for J4.72's accounting: a torpedo is one space (FP9.21). */
    public double spacesHeld() {
        return torpedoes;
    }

    /**
     * Put a torpedo aboard, inactive. Returns false if the rack is already full — FP10.14
     * forbids any larger one, so this is a hard ceiling rather than a default.
     */
    public boolean addTorpedo() {
        if (isFull())
            return false;
        torpedoes++;
        return true;
    }

    /** Torpedoes this rack is short of a full load, for a reload pass. */
    public int missing() {
        return CAPACITY - torpedoes;
    }

    // ---------------------------------------------------------------- activation (FP9.22)

    public int getActiveTorpedoes() {
        return activeTorpedoes;
    }

    /** Torpedoes aboard that nobody has paid the half point for yet. */
    public int inactiveTorpedoes() {
        return Math.max(0, torpedoes - activeTorpedoes);
    }

    /** What it would cost to activate everything aboard (FP9.22) — the EA ceiling. */
    public double activationEnergyWanted() {
        return inactiveTorpedoes() * ACTIVATION_ENERGY;
    }

    /**
     * Spend energy activating torpedoes (FP9.22, FP10.32). Half a point each; anything left
     * over is returned rather than kept, since a half-paid torpedo does not exist.
     *
     * @return the energy actually consumed
     */
    public double activate(double energy) {
        int affordable = (int) Math.floor(energy / ACTIVATION_ENERGY);
        int toActivate = Math.min(affordable, inactiveTorpedoes());
        activeTorpedoes += toActivate;
        return toActivate * ACTIVATION_ENERGY;
    }

    /**
     * FP10.25 WEAPON STATUS: at status II "one torpedo per rack is active", at status III "all
     * torpedoes on racks are active", and at 0 and I they are inactive.
     */
    public void applyWeaponStatus(int status) {
        if (status >= 3)
            activeTorpedoes = torpedoes;
        else if (status == 2)
            activeTorpedoes = Math.min(1, torpedoes);
        else
            activeTorpedoes = 0;
    }

    // ---------------------------------------------------------------- mode (FP10.21)

    public RackMode getModeThisTurn() {
        return modeThisTurn;
    }

    /** FP10.312: the Y175 refit, which gives every plasma rack a second set of reloads. */
    public void applyY175Refit() {
        reloadSets = Math.max(reloadSets, Y175_RELOAD_SETS);
    }

    public int getReloadSets() {
        return reloadSets;
    }

    public void setReloadSets(int sets) {
        reloadSets = Math.max(0, sets);
    }

    public boolean isReloadingThisTurn() {
        return reloadingThisTurn;
    }

    public void setReloadingThisTurn(boolean reloading) {
        this.reloadingThisTurn = reloading;
    }

    public int getFiredThisTurn() {
        return firedThisTurn;
    }

    /**
     * Why this rack may not launch a torpedo in {@code mode} right now, or null if it may.
     * <p>
     * The two rates are the whole point of the mode (FP10.211, FP10.212):
     * <ul>
     *   <li><b>offensive</b>: one torpedo per turn, "during any impulse of the turn but not
     *       within 1/4 turn of a torpedo fired in either mode during the previous turn";</li>
     *   <li><b>defensive</b>: "no limit on the firing rate (other than ammunition and one shot
     *       per impulse) or on how long after a previous firing the weapon can be used".</li>
     * </ul>
     * A mode already settled this turn cannot be changed until the next (FP10.21), so asking
     * for the other one is refused rather than silently honoured.
     */
    public String launchRefusal(RackMode mode) {
        if (mode == null || mode == RackMode.UNDECIDED)
            return getName() + " must be fired in offensive or defensive mode (FP10.21)";
        if (!isFunctional())
            return getName() + " is destroyed";
        if (reloadingThisTurn)
            return getName() + " is reloading this turn and cannot fire in either mode (FP10.23)";
        if (isEmpty())
            return getName() + " is empty";
        if (activeTorpedoes <= 0)
            return getName() + " has no activated torpedo - half an energy point each, and one"
                    + " cannot be launched without it (FP9.22)";
        if (modeThisTurn != RackMode.UNDECIDED && modeThisTurn != mode)
            return getName() + " committed to " + modeThisTurn.name().toLowerCase()
                    + " mode when it first fired this turn and cannot change until the next"
                    + " (FP10.21)";

        int impulse = clock.getImpulse();
        if (mode == RackMode.OFFENSIVE) {
            if (firedThisTurn > 0)
                return getName() + " has fired its one torpedo for this turn - offensive mode"
                        + " fires once a turn (FP10.211)";
            int wait = OFFENSIVE_SPACING - (impulse - lastFiredImpulse);
            if (wait > 0)
                return getName() + " fired " + (OFFENSIVE_SPACING - wait) + " impulse"
                        + (OFFENSIVE_SPACING - wait == 1 ? "" : "s") + " ago - an offensive shot"
                        + " may not come within a quarter turn of the last (FP10.211)";
            return null;
        }
        // Defensive: one shot per impulse and no gap at all, not even across the turn
        // boundary. FD3.71's example for the type-G is the same shape - impulse 32 and then
        // impulse 1 "with no delay".
        if (impulse <= lastFiredImpulse)
            return getName() + " has already fired this impulse - defensive mode allows one"
                    + " shot per impulse (FP10.212)";
        return null;
    }

    public boolean canLaunch(RackMode mode) {
        return launchRefusal(mode) == null;
    }

    /**
     * Take an activated torpedo out of the rack and settle the mode for the turn.
     *
     * @return the torpedo, or null if {@link #launchRefusal} would have refused
     */
    public PlasmaTorpedo launch(RackMode mode) {
        if (!canLaunch(mode))
            return null;
        torpedoes--;
        activeTorpedoes--;
        firedThisTurn++;
        lastFiredImpulse = clock.getImpulse();
        setLastImpulseFired(lastFiredImpulse);   // kept in step; see cleanUp
        // FP10.21: the first firing of the turn settles the mode for the rest of it.
        modeThisTurn = mode;
        return new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
    }

    /**
     * {@link Launcher}'s entry point. The rack has no numbered tubes — one type-D is any other
     * (FP9.18) — so the argument is ignored, as the interface's own contract allows for large
     * plasma torpedoes. Defaults to DEFENSIVE, which is the mode the weapon exists for
     * (FP10.0: "intended primarily for defense against massed fighter and drone attacks");
     * callers that mean an offensive shot say so with {@link #launch(RackMode)}.
     */
    @Override
    public Seeker launch(int weaponNumber) {
        return launch(RackMode.DEFENSIVE);
    }

    /**
     * Whether this rack could launch in EITHER mode — what a general "can this weapon fire"
     * guard wants, since the mode is chosen at the moment of firing and not before.
     */
    @Override
    public boolean canFire() {
        return canLaunch(RackMode.DEFENSIVE) || canLaunch(RackMode.OFFENSIVE);
    }

    // ---------------------------------------------------------------- the other means (FP10.22)

    public int getBoltsThisTurn() {
        return boltsThisTurn;
    }

    /**
     * Why this rack may not BOLT a torpedo in {@code mode} right now, or null if it may
     * (FP10.22, FP10.221).
     * <p>
     * FP10.22 makes bolt and seeking two "means" of using the same torpedo, so everything that
     * refuses a launch refuses a bolt too - the mode commitment, the rates, the activation, the
     * ammunition - and {@link #launchRefusal} is asked first rather than restated.
     * <p>
     * On top of that, FP10.221: "In either mode, the rack can fire a maximum of one torpedo per
     * turn as a plasma bolt." The only limit the rates do not already imply, and it bites in
     * defensive mode, where the rack may fire on every impulse but bolt on only one of them.
     * <p>
     * FP10.22's other clause - "During a given impulse, a rack can use only one means" - needs
     * nothing here: one shot per impulse in defensive mode and one per turn in offensive both
     * make it impossible to use two means in an impulse anyway. It would start to matter under
     * FP10.13's aegis steps, which are deferred.
     */
    public String boltRefusal(RackMode mode) {
        String common = launchRefusal(mode);
        if (common != null)
            return common;
        if (boltsThisTurn >= 1)
            return getName() + " has already bolted a torpedo this turn - a rack may bolt one"
                    + " per turn in either mode (FP10.221)";
        return null;
    }

    public boolean canBolt(RackMode mode) {
        return boltRefusal(mode) == null;
    }

    /**
     * Why {@code mode} may not be used against a target of this size at this range, or null if
     * it may (FP10.211, FP10.212).
     * <p>
     * This is what the mode choice actually buys. Defensive mode trades reach for rate: "Plasma
     * racks may fire in this mode at size-5 and smaller targets within an effective range of six
     * hexes from the firing ship." Offensive mode has "no restrictions as to target type or range
     * other than the capabilities of the weapon itself and (FP10.24)".
     * <p>
     * Size class counts UP as a unit gets smaller - a shuttle is 6, a cruiser 3 - so "size-5 and
     * smaller" is {@code sizeClass >= 5}. The complement of FP10.241's "size-4 or larger", which
     * is deliberate: the per-ship bolt limit covers exactly the targets defensive mode cannot
     * engage.
     *
     * @param targetSizeClass the target's size class
     * @param range           effective range, which is what FP10.212 measures
     */
    public String targetRefusal(RackMode mode, int targetSizeClass, int range) {
        if (mode != RackMode.DEFENSIVE)
            return null;              // FP10.211: offensive mode restricts nothing itself
        if (targetSizeClass < DEFENSIVE_MAX_TARGET_SIZE)
            return getName() + " is in defensive mode, which engages size-5 and smaller targets"
                    + " only (FP10.212)";
        if (range > DEFENSIVE_RANGE)
            return getName() + " is in defensive mode, which reaches six hexes (FP10.212)";
        return null;
    }

    /**
     * Bolt one torpedo (FP8.43): "the amount of damage scored (if the torpedo hits) is equal to
     * one-half of the warhead strength of the corresponding plasma torpedo (S-bolt = S-torpedo)
     * at the true range to the target. Retain fractions throughout the calculation, then drop
     * all remaining fractions before applying any damage."
     * <p>
     * Both ranges matter and they are not the same range, which is why the two-argument form is
     * the real implementation: FP8.42 bases the to-hit on the EFFECTIVE range, FP8.43 bases the
     * damage on the TRUE range. A scanner should make a bolt harder to hit with, not weaker when
     * it lands.
     * <p>
     * Note on arcs: FP8.35 narrows a swivel launcher's bolt arc (LS becomes L+LF) but lists the
     * rack as the exception in the same table - "LS for plas-D-rack" - and FP10.12 says why: "The
     * bolt arcs for plasma racks are less restrictive than those for plasma torpedoes due to the
     * nature of the system." So the rack bolts across its whole declared 180 degrees, and no
     * narrowing is applied anywhere here.
     */
    @Override
    public int fire(int realRange, int adjustedRange)
            throws WeaponUnarmedException, TargetOutOfRangeException {
        return bolt(effectiveModeForDirectFire(), realRange, adjustedRange);
    }

    /**
     * Single-range form, for callers with no scanner adjustment to apply. Delegates so the
     * to-hit and the damage cannot drift apart: FP8.42 and FP8.43 read the same number only when
     * there is no scanner in play.
     */
    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        return fire(range, range);
    }

    /**
     * Which mode a bare {@link #fire} call is taken to mean.
     * <p>
     * The committed one if the rack has already fired this turn, since FP10.21 does not let it
     * change; otherwise DEFENSIVE, matching {@link #launch(int)} and for the same reason - it is
     * the mode the weapon exists for (FP10.0). A caller that means an offensive bolt says so
     * through {@link #bolt}, because choosing offensive mode spends one of the ship's two places
     * under FP10.242 and that is not a decision to make by default.
     */
    private RackMode effectiveModeForDirectFire() {
        return modeThisTurn == RackMode.UNDECIDED ? RackMode.DEFENSIVE : modeThisTurn;
    }

    /**
     * Bolt one torpedo in {@code mode} (FP8.43, FP10.22).
     * <p>
     * This is the real implementation and {@link #fire} delegates to it, rather than the other way
     * round, for two reasons. It must ENFORCE the rules rather than trust the caller to have asked
     * {@link #boltRefusal} first - {@code DroneRack.fire} guards itself the same way, throwing
     * {@link WeaponUnarmedException} with the rule cited - and it must SETTLE the mode, because
     * FP10.21 makes the first firing of a turn the declaration and a bolt is a firing.
     * <p>
     * Both ranges matter and they are not the same range: FP8.42 bases the to-hit on the EFFECTIVE
     * range, FP8.43 the damage on the TRUE range - "one-half of the warhead strength of the
     * corresponding plasma torpedo (S-bolt = S-torpedo) at the true range to the target. Retain
     * fractions throughout the calculation, then drop all remaining fractions." A scanner makes a
     * bolt harder to land, not weaker when it lands.
     * <p>
     * Note on arcs: FP8.35 narrows a swivel launcher's bolt arc (LS becomes L+LF) but names the
     * rack as the exception in that very table - "LS for plas-D-rack" - and FP10.12 says why:
     * "The bolt arcs for plasma racks are less restrictive than those for plasma torpedoes due to
     * the nature of the system." So the rack bolts across its whole declared 180 degrees and no
     * narrowing is applied anywhere here.
     *
     * @return the damage scored, or 0 on a miss
     */
    public int bolt(RackMode mode, int realRange, int adjustedRange)
            throws WeaponUnarmedException, TargetOutOfRangeException {
        String refusal = boltRefusal(mode);
        if (refusal != null)
            throw new WeaponUnarmedException(refusal);

        int needs = PlasmaLauncher.boltHitNeeds(adjustedRange);
        if (needs < 0)
            throw new TargetOutOfRangeException(
                    getName() + " cannot bolt at an effective range of " + adjustedRange);

        // FP8.43's "corresponding plasma torpedo... at the true range": a type-D walked out to
        // the real range, so the rack and a type-D seeker agree on strength by construction
        // rather than by a copied table.
        PlasmaTorpedo reference = new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
        for (int i = 0; i < realRange; i++)
            reference.incrementDistance();
        int boltDamage = reference.getCurrentStrength() / 2;
        if (boltDamage <= 0)
            throw new TargetOutOfRangeException(
                    getName() + " has no warhead strength at range " + realRange);

        torpedoes--;
        activeTorpedoes--;
        firedThisTurn++;
        boltsThisTurn++;
        lastFiredImpulse = clock.getImpulse();
        // registerFire rather than setLastImpulseFired alone: a bolt IS direct fire, so the
        // generic bookkeeping applies - the turn it happened, the shot count, and D13.22's
        // "fired under aegis" flag that stops a weapon firing both ways in one impulse.
        registerFire();
        // FP10.21: a bolt is a firing, so it settles the mode exactly as a launch does.
        modeThisTurn = mode;

        int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
        setLastRoll(roll);
        return roll <= needs ? boltDamage : 0;
    }

    /**
     * Whether a bolt is possible at all this impulse. The mode is not known until the shot, so
     * this answers for either - a guard asking "can this be shot" wants to know it is loaded,
     * activated, not reloading and has its bolt for the turn, which is what both share.
     */
    @Override
    public boolean canBeFiredAtTarget() {
        return canBolt(RackMode.DEFENSIVE) || canBolt(RackMode.OFFENSIVE);
    }

    /**
     * A rack is SHOT only as a bolt launcher, so its readiness to fire at a target is that
     * question and not {@link #canFire()}, which asks whether it may send a SEEKER. The same
     * split {@code DroneRack} makes between launching a drone and firing an anti-drone.
     */
    @Override
    public boolean readyToFireAtTarget() {
        return canBeFiredAtTarget();
    }

    /**
     * The per-turn reset. {@code cleanUp} rather than a {@code startTurn} of its own, because
     * this is the hook {@code Weapons.cleanUp} already calls for every weapon at the turn
     * boundary — a method nothing invokes resets nothing.
     * <p>
     * {@link Weapon#cleanUp} clears the inherited fire timestamp so an ordinary weapon starts
     * each turn free, which is right for a phaser and wrong here: FP10.211 measures its quarter
     * turn "of a torpedo fired in either mode during the previous turn", so the gap must survive
     * the boundary. {@link DroneRack#cleanUp} preserves it for the same reason (FD3.0). The
     * rule's own authority is {@link #lastFiredImpulse}, which {@code super.cleanUp} never
     * touches; the inherited one is kept in step so anything reading {@code getLastImpulseFired}
     * sees the truth rather than a reset.
     */
    @Override
    public void cleanUp() {
        int firedAt = getLastImpulseFired();
        super.cleanUp();
        setLastImpulseFired(firedAt);
        // FP10.21: "it can change modes when first fired during the next turn" - so a new turn
        // finds the rack undecided, while the timestamps above do not move.
        modeThisTurn = RackMode.UNDECIDED;
        firedThisTurn = 0;
        boltsThisTurn = 0;
        reloadingThisTurn = false;
    }

    @Override
    public String toString() {
        return getName() + " [" + torpedoes + "/" + CAPACITY + ", " + activeTorpedoes
                + " active, " + reloadSets + " reload set" + (reloadSets == 1 ? "" : "s")
                + ", " + modeThisTurn + "]";
    }
}
