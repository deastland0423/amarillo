package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;
import com.sfb.utilities.DiceRoller;

/**
 * Photon torpedo as carried by a Federation photon fighter — the A-10 and A-20 (J4.851).
 * <p>
 * Built on {@link FighterDisruptor}'s pattern, which is the shape of every heavy weapon put on a
 * fighter: it holds CHARGES loaded from the fighter's own box rather than being armed with
 * energy, because a fighter has no reactor to arm anything with. The charge is spent on a shot
 * and the weapon is empty until a deck crew reloads it (J4.853 makes that a single action).
 * <p>
 * It shoots as a photon otherwise — one die against the standard photon hit chart, eight points
 * on a hit — with the charts read from {@link Photon} so the two cannot drift apart.
 *
 * <h2>Where J4.85 differs from the fighter disruptor</h2>
 * <ul>
 *   <li><b>ONE charge, not two.</b> J4.852: "Each fighter box assigned to a photon-armed fighter
 *       (marked +) includes a capacitor for a single photon torpedo." The DAS gets two shots a
 *       reload; a photon fighter gets one.</li>
 *   <li><b>Proximity fuse is available</b>, where the fighter disruptor is standard-only. J4.854:
 *       "The torpedo can be set as a standard or proximity fuse at the time the charge is loaded
 *       on the fighter. Changing this afterwards requires a deck crew action." So the fuse is
 *       chosen at load time — see {@link #loadCharge(boolean)} — and {@link #setProximity} is
 *       the later change, whose deck crew cost belongs to the caller.</li>
 *   <li><b>Minimum range TWO.</b> J4.45: heavy weapons "are fired under the same rules as
 *       ship-mounted weapons of the respective type. Thus, a Federation A-10 fighter cannot fire
 *       its photon at Range 0-1". Enforced as a refusal rather than left to the chart's zeroes,
 *       so a shot that cannot be taken does not burn the only charge.</li>
 *   <li><b>Max range TWELVE.</b> J1.31 caps a shuttle-mounted weapon at fifteen but names the
 *       photon's shorter limit outright: "photons are limited to Range 12". The CHARTS are not
 *       truncated — accuracy and damage at range 12 are a ship photon's; only reach is cut.</li>
 *   <li><b>Never overloaded.</b> J4.852 says so in as many words: the capacitor is reloaded by
 *       the ship's power "but overloads are not allowed". There is no energy here to overload
 *       with either.</li>
 * </ul>
 */
public class FighterPhoton extends HitOrMissWeapon implements DirectFire {

    /** J4.852: the fighter box holds a capacitor for a SINGLE torpedo. One shot per reload. */
    public static final int FULL_CHARGES = 1;

    /**
     * Reach of a fighter-mounted photon: twelve (J1.31).
     * <p>
     * Shorter than the fifteen J1.31 allows a shuttle generally, because the same sentence names
     * the photon's own limit. The charts keep their full length: a photon at range 12 needs the
     * same roll and does the same damage as a cruiser's would.
     */
    public static final int MAX_RANGE = 12;

    /** J4.45: "a Federation A-10 fighter cannot fire its photon at Range 0-1". */
    public static final int MIN_RANGE = 2;

    /** Eight points on a hit, as any standard photon (E4.0). */
    public static final int STANDARD_DAMAGE = 8;

    /** Four points on a hit with a proximity fuse (E4.4). */
    public static final int PROXIMITY_DAMAGE = 4;

    /**
     * Empty until something arms it (J4.8223/J4.8224).
     * <p>
     * Built unloaded for the same reason every fighter weapon is: weapon status decides who
     * starts ready (S4.10-S4.13) and the charge comes out of the fighter's own box, so a fighter
     * born armed would be ammunition from nowhere.
     */
    private int chargesRemaining = 0;

    /**
     * The fuse the loaded torpedo carries (J4.854). STANDARD or SPECIAL, never OVERLOAD.
     * <p>
     * Held even while the weapon is empty, because the fuse is a property of how the NEXT charge
     * was set rather than of the shot — and because a crew that changed the setting should not
     * have it forgotten by the weapon firing.
     */
    private WeaponArmingType fuse = WeaponArmingType.STANDARD;

    public FighterPhoton() {
        setDacHitLocaiton("torp");
        setType("FighterPhoton");
        setMinRange(MIN_RANGE);
        setMaxRange(MAX_RANGE);
    }

    /** The standard photon chart; the proximity chart is read separately when the fuse is set. */
    @Override
    public int[] getHitChart() {
        return Photon.standardHitChart();
    }

    public int getChargesRemaining() {
        return chargesRemaining;
    }

    /** J1.3324: a crippled fighter loses what it was holding. */
    public void drainCharges() {
        chargesRemaining = 0;
    }

    /** How many charges this weapon is short of full. */
    public int chargesMissing() {
        return Math.max(0, FULL_CHARGES - chargesRemaining);
    }

    /** Load the torpedo with a standard fuse. */
    public boolean loadCharge() {
        return loadCharge(false);
    }

    /**
     * Load the torpedo, choosing its fuse as it goes in (J4.854).
     *
     * @param proximity true for a proximity fuse (E4.4), false for a standard one
     * @return true if a charge went in; false if the weapon was already full
     */
    public boolean loadCharge(boolean proximity) {
        if (chargesRemaining >= FULL_CHARGES)
            return false;
        chargesRemaining++;
        fuse = proximity ? WeaponArmingType.SPECIAL : WeaponArmingType.STANDARD;
        return true;
    }

    /**
     * Change the fuse on a torpedo already aboard (J4.854).
     * <p>
     * The rule makes this "a deck crew action", which this method does NOT spend — the caller
     * owns that, the same way loading a charge does. Named apart from {@link #loadCharge(boolean)}
     * so the two cannot be confused: one is free at load time, the other costs a crew.
     */
    public void setProximity(boolean proximity) {
        fuse = proximity ? WeaponArmingType.SPECIAL : WeaponArmingType.STANDARD;
    }

    public boolean isProximity() {
        return fuse == WeaponArmingType.SPECIAL;
    }

    public WeaponArmingType getArmingType() {
        return fuse;
    }

    /** E4.4 is available to a photon fighter (J4.854), so the UI may offer the mode. */
    public boolean supportsProximity() {
        return true;
    }

    @Override
    public boolean canFire() {
        return chargesRemaining > 0 && super.canFire();
    }

    @Override
    public int fire(int realRange, int adjustedRange)
            throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {
        return fire(realRange);
    }

    @Override
    public int fire(int range)
            throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {

        if (chargesRemaining <= 0)
            throw new WeaponUnarmedException("FighterPhoton has no charge remaining.");
        if (!super.canFire())
            throw new WeaponUnarmedException("FighterPhoton is on cooldown.");
        // J4.45's minimum is a REFUSAL, not a certain miss: the chart's zeroes at range 0-1
        // would also yield nothing, but firing would spend the only charge the fighter has.
        if (range < MIN_RANGE || range > MAX_RANGE)
            throw new TargetOutOfRangeException("Target at range " + range
                    + " is outside the fighter photon's " + MIN_RANGE + "-" + MAX_RANGE + ".");

        int[] hits = isProximity() ? Photon.proximityChart() : Photon.standardHitChart();
        int idx = Math.min(range, hits.length - 1);

        int roll = new DiceRoller().rollOneDie();
        setLastRoll(roll);

        // Spend the charge whether it hits or misses: the shot was taken.
        chargesRemaining--;
        registerFire();

        if (roll > hits[idx])
            return 0;
        return isProximity() ? PROXIMITY_DAMAGE : STANDARD_DAMAGE;
    }
}
