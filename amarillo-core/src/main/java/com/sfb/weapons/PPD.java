package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

/**
 * ISC Plasmatic Pulsar Device (E11.0).
 *
 * <h2>Why this weapon is not like the others</h2>
 * Every other direct-fire weapon in the game resolves in the impulse it is fired. A PPD fires
 * ONCE and then operates "over the current impulse and the next three impulses" (E11.30) — four
 * shots, termed pulses, all at the same target, which it cannot change. Each pulse rolls for a
 * wave-lock; once one is achieved every remaining pulse scores automatically until the lock is
 * lost. That persistence is the weapon, not a detail of it.
 *
 * <p>This class is the state machine for one device: how it arms, and how a firing run plays
 * out pulse by pulse. It does not know about the map, the target or the damage track — the
 * caller supplies a range per pulse and routes the damage through {@link PpdSplash}, the same
 * division of labour the Hellbore uses.
 *
 * <h2>Arming (E11.2)</h2>
 * Four points a turn for two turns is the standard charge and buys four pulses. Two points buys
 * one pulse (E11.25), so an underloaded device can fire fewer, and a third turn of arming can
 * bring it up — but never past four, because more than four points in a turn is an overload
 * (E11.65), which is NOT implemented and is refused rather than silently accepted. Holding
 * costs two a turn (E11.22). Reserve power cannot arm a PPD at all (E11.24).
 *
 * <h2>Two ranges, and they are not the same one</h2>
 * E11.312: "the dice roll is made at the effective range and the damage is based on the true
 * range." We pass ONE range and use it for both, because nothing in the engine distinguishes
 * them yet. That is a real gap, not a simplification of the rule: a scout or a web that moves
 * effective range away from true range would make the roll and the damage disagree.
 *
 * <p>The same clause bars firing at a true range of three or less "regardless of what the
 * effective range is", which is why the chart's dead band is a firing restriction here and not
 * merely a zero.
 */
public class PPD extends HitOrMissWeapon implements DirectFire, HeavyWeapon {

    /** E11.21: four points a turn, twice over, is the full charge. */
    public static final int ENERGY_PER_TURN = 4;
    /** E11.25: "For every two points of energy added to the PPD, it can fire one pulse." */
    public static final int ENERGY_PER_PULSE = 2;
    /** E11.30: a full charge gets four shots at the target. */
    public static final int MAX_PULSES = 4;
    /** E11.22: holding an armed charge costs two a turn. */
    public static final int HOLDING_COST = 2;
    /** E11.312: "The weapon cannot be fired if the true range is three or less." */
    public static final int MINIMUM_RANGE = 4;
    /** E11.61: eight points on the second turn of arming buys six pulses instead of four. */
    public static final int OVERLOAD_ENERGY = 8;
    /** E11.61: "the weapon fires six pulses over six impulses rather than four over four." */
    public static final int MAX_PULSES_OVERLOADED = 6;
    /** E11.64: "The maximum true range for an overloaded PPD is eight hexes." */
    public static final int OVERLOAD_MAX_RANGE = 8;

    // ---- arming state -------------------------------------------------------
    private int armingTurn = 0;      // consecutive turns energy has been added
    private int armingEnergy = 0;    // total points in the current charge
    private boolean armed = false;
    /**
     * E11.6. Determined by the ENERGY, never declared: more than four points on the second turn
     * of arming makes it an overload, and E11.65 keeps it one "even though it will have fewer
     * total pulses" than a full charge. So there is nothing to keep in step with the energy.
     */
    private boolean overloaded = false;

    // ---- firing state -------------------------------------------------------
    private int pulsesRemaining = 0;
    private boolean waveLocked = false;
    /**
     * The EW shift the current wave-lock was achieved at (E11.53).
     *
     * <p>SHIFTS, not ECM points, and that distinction is the rule: the worked example has a
     * target go from four net ECM to six and roll again for nothing, "as there is no change in
     * the EW level". Only a change in the TARGET's favour forces a new roll — an improvement
     * for the firing ship forces none.
     */
    private int lockedAtShift = 0;

    public PPD() {
        setDacHitLocaiton("drone");   // E11.12: PPDs are destroyed on "drone" hits
        setType("PPD");
        setMinRange(MINIMUM_RANGE);
        setMaxRange(40);
    }

    // -------------------------------------------------------------------------
    // Arming (E11.2)
    // -------------------------------------------------------------------------

    /**
     * Add a turn's arming energy.
     *
     * <p>Two or four points. More is an overload (E11.65) and is refused: accepting it would
     * quietly build a weapon this class cannot fire correctly. Odd amounts are refused because
     * a pulse costs exactly two and the rule never prices a half one.
     */
    @Override
    public boolean arm(int energy) {
        // E11.611: "No more than four points of energy can be allocated on the first turn."
        int ceiling = armingTurn == 0 ? ENERGY_PER_TURN : OVERLOAD_ENERGY;
        if (energy < ENERGY_PER_PULSE || energy > ceiling || energy % ENERGY_PER_PULSE != 0)
            return false;
        // E11.61/E11.65: more than a normal turn's worth on a later turn IS the overload, and
        // stays one however few pulses it ends up with (E11.612's six points buys just one
        // extra). Latched, because a third turn at two points does not undo it.
        if (energy > ENERGY_PER_TURN)
            overloaded = true;
        // E11.23: a device still pulsing is mid-firing, not mid-arming. Arming energy for the
        // NEXT charge may be allocated while an earlier firing run is still resolving (the
        // worked example in E11.23 does exactly that), so this checks the charge, not the run.
        if (armingEnergy + energy > ENERGY_PER_PULSE * maxPulses())
            return false;
        armingEnergy += energy;
        armingTurn++;
        // E11.25: "arming still requires two consecutive turns with at least two points added
        // on each turn" — so a single turn of four points is NOT a firing charge.
        if (armingTurn >= totalArmingTurns())
            armed = true;
        return true;
    }

    /** E11.22: hold a completed charge for two points a turn, for as long as it is paid. */
    @Override
    public boolean hold(int energy) throws WeaponUnarmedException {
        if (!armed)
            throw new WeaponUnarmedException("PPD has no charge to hold.");
        // E11.62: "Overloaded PPDs cannot be held."
        if (overloaded)
            return false;
        return energy == HOLDING_COST;
    }

    /**
     * Add energy to a charge that is already complete and being held (E11.25, E11.63).
     *
     * <p>A different operation from an arming turn, and the rules treat it as one. E11.25 brings
     * an UNDERLOADED charge up "by holding it for an additional turn and allocating more
     * energy"; E11.63 overloads a HELD one the same way, "in addition to the holding energy".
     * Both are the same act — more energy onto a charge that already exists — and what makes it
     * an overload is the TOTAL passing what a standard full charge costs.
     *
     * <p>That reading is what reproduces the rule's own numbers. A held full charge (eight
     * points, four pulses) plus four is twelve, which is E11.61's overload at six pulses; plus
     * two is ten, which is E11.612's "only one additional pulse" at five. A half-charge of four
     * topped up by four is eight — a plain full charge, no overload, exactly as E11.25 intends.
     *
     * @return false if the amount buys nothing, leaving the charge untouched
     */
    public boolean addEnergyToHeldCharge(int points) {
        if (!armed)
            return false;
        if (points < ENERGY_PER_PULSE || points % ENERGY_PER_PULSE != 0)
            return false;
        int total = armingEnergy + points;
        if (total > ENERGY_PER_PULSE * MAX_PULSES_OVERLOADED)
            return false;
        armingEnergy = total;
        if (total > ENERGY_PER_PULSE * MAX_PULSES)
            overloaded = true;
        return true;
    }

    /** How many pulses the current charge will fire (E11.25, E11.61). */
    public int pulsesCharged() {
        return Math.min(maxPulses(), armingEnergy / ENERGY_PER_PULSE);
    }

    /** Four pulses normally, six overloaded (E11.61). */
    private int maxPulses() {
        return overloaded ? MAX_PULSES_OVERLOADED : MAX_PULSES;
    }

    /** Whether this charge is an overload (E11.6), which the energy decides and nothing else. */
    public boolean isOverloaded() {
        return overloaded;
    }

    /**
     * E11.64: an overloaded PPD reaches eight hexes, not forty.
     *
     * <p>A severe trade for the two extra pulses, and the reason it is a tactical choice rather
     * than a free upgrade — it gives up the long reach the whole weapon exists for. E11.541
     * reads this too, so a target that runs past eight breaks the lock outright.
     */
    @Override
    public int getMaxRange() {
        return overloaded ? OVERLOAD_MAX_RANGE : super.getMaxRange();
    }

    @Override public int energyToArm() { return ENERGY_PER_TURN; }
    @Override public int totalArmingTurns() { return 2; }
    @Override public int getArmingTurn() { return armingTurn; }
    @Override public boolean isArmed() { return armed; }
    @Override public void setArmed(boolean armed) { this.armed = armed; }
    @Override
    public WeaponArmingType getArmingType() {
        return overloaded ? WeaponArmingType.OVERLOAD : WeaponArmingType.STANDARD;
    }

    @Override
    public void setArmingTurn(int turn) {
        this.armingTurn = turn;
        if (turn >= totalArmingTurns()) {
            armed = true;
            if (armingEnergy == 0)
                armingEnergy = ENERGY_PER_TURN * totalArmingTurns();
        }
    }

    // -------------------------------------------------------------------------
    // Firing (E11.3)
    // -------------------------------------------------------------------------

    /**
     * Begin a firing run and resolve its first pulse (E11.30/E11.31).
     *
     * @return damage this pulse scores — the chart total, which the caller spreads with
     *         {@link PpdSplash}; 0 when no wave-lock was achieved
     */
    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        if (!armed)
            throw new WeaponUnarmedException("PPD is not armed.");
        if (isPulsing())
            throw new WeaponUnarmedException("PPD is already firing; it cannot start again.");
        if (range < MINIMUM_RANGE)
            throw new TargetOutOfRangeException(
                    "PPD cannot be fired at a true range of three or less (E11.312).");
        if (range > getMaxRange())
            throw new TargetOutOfRangeException("Target out of PPD range.");

        pulsesRemaining = pulsesCharged();
        waveLocked = false;
        // The charge is spent the moment it is fired; what remains is the run, not the charge.
        // `overloaded` stays set until the run ends, because getMaxRange() answers for the run.
        armed = false;
        armingEnergy = 0;
        armingTurn = 0;
        registerFire();
        return pulse(range);
    }

    /**
     * Resolve one pulse of a run already under way (E11.31, E11.331, E11.34).
     *
     * <p>A pulse is consumed whatever happens. With no wave-lock it rolls for one; with a lock
     * it scores automatically. A lock lost to changing conditions (E11.5) is re-rolled for on
     * the pulses that remain — the target cannot change either way (E11.34).
     *
     * @return damage this pulse scores, or 0 for a miss, for a run that is over, or inside the
     *         minimum range
     */
    public int pulse(int range) {
        if (!isPulsing())
            return 0;
        pulsesRemaining--;

        // E11.312 bars firing inside range 4; E11.521 lets a wave-lock survive the approach, so
        // the pulse is spent and scores nothing rather than ending the run.
        if (range < MINIMUM_RANGE || range > getMaxRange())
            return 0;

        // E11.53: the electronic warfare shift has moved in the target's favour since the lock
        // was taken, so it must be rolled for again. Strictly greater — an unchanged shift
        // costs nothing, and one that has improved for the firer costs nothing either.
        if (waveLocked && getEcmShift() > lockedAtShift)
            waveLocked = false;

        if (!waveLocked) {
            int roll = new DiceRoller().rollTwoDice();
            setLastRoll(roll);
            int hitNumber = PpdSplash.hitNumber(range);
            if (hitNumber == 0 || roll + getEcmShift() > hitNumber)
                return 0;                       // E11.331: this pulse is lost, the run goes on
            waveLocked = true;
            lockedAtShift = getEcmShift();
        }
        return PpdSplash.totalDamage(range);
    }

    /** Whether a firing run is still under way, with pulses left to resolve. */
    public boolean isPulsing() {
        return pulsesRemaining > 0;
    }

    /** Pulses still to come in the current run. */
    public int pulsesRemaining() {
        return pulsesRemaining;
    }

    /** Whether the wave is currently locked on, so the next pulse scores without a roll. */
    public boolean isWaveLocked() {
        return waveLocked;
    }

    /**
     * E11.5: conditions changed, so the lock is dropped and the remaining pulses must roll
     * again (E11.34). The run itself continues — losing a lock is not losing the weapon.
     */
    public void loseWaveLock() {
        waveLocked = false;
    }

    /** The EW shift this run's wave-lock was taken at (E11.53); meaningless when not locked. */
    public int lockedAtShift() {
        return lockedAtShift;
    }

    /**
     * End the run outright: every remaining pulse is lost and no new wave-lock is rolled for.
     *
     * <p>Two rules land here and they have the same effect. E11.362/E11.548 is the owning player
     * ceasing fire, and you cannot skip one pulse and resume (E11.333). E11.54 is the
     * involuntary half — target destroyed, out of arc, out of range, the PPD box shot off — and
     * it is explicit: "the wave-lock is broken and all subsequent pulses are lost. Do not roll
     * for a new wave-lock."
     *
     * <p>That is the distinction {@link #loseWaveLock()} does NOT share: E11.53's conditions
     * drop the lock but leave the run alive to roll again (E11.34). Confusing the two would
     * make a weapon that either never stops or never recovers.
     */
    public void shutDown() {
        pulsesRemaining = 0;
        waveLocked = false;
        overloaded = false;
    }

    /**
     * Energy allocation at the start of a turn (E11.2).
     *
     * <p>Null or nothing discharges the charge into space (E1.24). Two points hold an armed
     * device; anything else is arming energy. Reserve power never reaches here — E11.24 bars
     * it from arming a PPD, and the reserve path must refuse it rather than routing it in.
     */
    @Override
    public void applyAllocationEnergy(Double energy, WeaponArmingType type) {
        if (energy == null || energy <= 0) {
            reset();
            return;
        }
        int points = (int) Math.floor(energy);

        if (!armed) {
            if (!arm(points))
                reset();                  // an amount the rule does not allow buys nothing
            return;
        }

        // An ARMED charge. The first two points are the holding cost (E11.22) and anything
        // beyond them is further arming energy — E11.25 topping up an underloaded charge, or
        // E11.63 overloading a held one, "in addition to the holding energy".
        if (points < HOLDING_COST) {
            reset();                      // holding not paid, so the charge is lost (E1.24)
            return;
        }
        int extra = points - HOLDING_COST;
        // An extra amount the rule does not allow buys nothing — but the holding cost WAS paid,
        // so the charge survives. It used to be discarded here, which punished a player for
        // following E11.63 by destroying two turns of arming.
        if (extra > 0)
            addEnergyToHeldCharge(extra);
    }

    /**
     * E11.62/E11.63: an overload cannot be held, so one still armed at the end of the turn is
     * ejected — "the PPD must be fired or ejected by the end of the turn".
     *
     * <p>A standard charge is untouched: it may be held for as long as the holding cost is paid.
     */
    @Override
    public void cleanUp() {
        if (armed && overloaded)
            reset();
        super.cleanUp();
    }

    /**
     * Lose the charge, as an unheld weapon does at the start of a turn (E1.24, E11.22).
     *
     * <p>Deliberately does NOT touch a firing run in progress: E11.23's worked example has a
     * device fired on impulse 31 still resolving pulses into the next turn while that turn's
     * allocation happens. The charge and the run are two different things.
     */
    @Override
    public void reset() {
        armed = false;
        armingEnergy = 0;
        armingTurn = 0;
        overloaded = false;
    }

    /**
     * Arming modes (E11.6). A PPD is standard or overloaded, and overload is not built yet —
     * so standard is the only mode it will accept, and the other two refuse rather than
     * silently arming something this class would then fire at the wrong strength.
     */
    @Override public boolean setStandard() { return !overloaded; }
    /**
     * Overload is possible (E11.6) but is not a mode you switch into: it is what allocating more
     * than four points on a later arming turn MEANS. Saying so here changes nothing, which is
     * why this reports the state rather than setting it.
     */
    @Override public boolean setOverload() { return overloaded; }
    @Override public boolean setSpecial()  { return false; }

    @Override
    public int[] getHitChart() {
        // The PPD's to-hit depends on range through PpdSplash.hitNumber, which also owns the
        // band edges. Returning a bare array here would be a second copy of half that table.
        return new int[0];
    }
}
