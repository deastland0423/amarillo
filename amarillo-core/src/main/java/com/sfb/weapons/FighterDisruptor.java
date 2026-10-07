package com.sfb.weapons;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;

/**
 * Disruptor as carried by a Kzinti attack shuttle — the DAS (J4.4).
 * <p>
 * Built on the same model as {@link FighterFusion}, which is the pattern for a heavy weapon put
 * on a fighter: it carries CHARGES loaded from the fighter's own box (J4.833) rather than being
 * armed with energy, because a fighter has no reactor to arm anything with. One charge per shot,
 * and the weapon is spent until a deck crew reloads it.
 * <p>
 * It shoots as a disruptor otherwise: hit-or-miss on one die against the standard disruptor hit
 * chart, damage by range off the standard damage chart, both read from {@link Disruptor} so the
 * two cannot drift apart. No overload — overloading is an energy decision and there is no energy
 * here — and no UIM or DERFACS, which are ship fire-control systems (D6.5).
 *
 * <h2>The numbers, as the owner gave them</h2>
 * <ul>
 *   <li><b>Max range 10</b> — "just like all other fighter weapons". The fighter fusion's double
 *       shot reaches the same 10, which is the ceiling a fighter-mounted weapon works to
 *       whatever it is. Note this does NOT shorten the charts: a disruptor at range 10 still
 *       needs a 4 or less and still does 3, exactly as a ship's would. The weapon is limited by
 *       reach, not by accuracy.</li>
 *   <li><b>Standard hit and damage charts</b>, read from {@link Disruptor}.</li>
 *   <li><b>Standard mode only, never overloaded.</b></li>
 *   <li><b>Two charges, one shot each</b> — so two shots per reload, the DAS carrying a single
 *       disruptor where a Hydran Stinger carries two fusions with two charges apiece
 *       (J4.831).</li>
 *   <li><b>One shot per turn</b>, "just like any disruptor" — enforced as the eight-impulse gap
 *       the base {@link Weapon} already keeps. This is a disruptor's own rate of fire, not
 *       something borrowed from the fighter fusion, which happens to share the interval.</li>
 * </ul>
 */
public class FighterDisruptor extends HitOrMissWeapon implements DirectFire {

    /** Two charges, one shot each — two shots before a deck crew has to reload it. */
    public static final int FULL_CHARGES = 2;

    /**
     * Reach of the fighter-mounted disruptor: ten, the limit every fighter weapon works to.
     * <p>
     * The CHARTS are not truncated to match — a disruptor's accuracy and damage at range 10 are
     * what a ship's disruptor has at range 10. Only the reach is cut.
     */
    public static final int MAX_RANGE = 10;

    /**
     * One shot per turn, as any disruptor fires: eight impulses between shots. With two charges
     * that means the DAS gets its second shot a quarter of a turn after its first, not on the
     * same impulse.
     */
    public static final int IMPULSE_GAP = 8;

    /**
     * Empty until something arms it (J4.8223/J4.8224).
     * <p>
     * Built unloaded for the same reason the fighter fusion is: weapon status decides who starts
     * ready (S4.10-S4.13) and the charges come out of the fighter's own box, so a fighter born
     * armed would be ammunition from nowhere.
     */
    private int chargesRemaining = 0;

    public FighterDisruptor() {
        setDacHitLocaiton("torp");
        setType("FighterDisruptor");
        setDisplayName("Disruptor");
        setMinRange(1);
        setMaxRange(MAX_RANGE);
        setMinImpulseGap(IMPULSE_GAP);
    }

    @Override
    public int[] getHitChart() {
        return Disruptor.standardHitChart();
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

    /**
     * Load one charge from the fighter box's capacitor (J4.833).
     *
     * @return true if a charge went in; false if the weapon was already full
     */
    public boolean loadCharge() {
        if (chargesRemaining >= FULL_CHARGES)
            return false;
        chargesRemaining++;
        return true;
    }

    @Override
    public boolean canFire() {
        return chargesRemaining > 0 && super.canFire();
    }

    @Override
    public int fire(int range)
            throws WeaponUnarmedException, TargetOutOfRangeException, CapacitorException {

        if (chargesRemaining <= 0)
            throw new WeaponUnarmedException("FighterDisruptor has no charges remaining.");
        if (!super.canFire())
            throw new WeaponUnarmedException("FighterDisruptor is on cooldown.");
        if (range < getMinRange() || range > MAX_RANGE)
            throw new TargetOutOfRangeException("Target at range " + range
                    + " is outside the fighter disruptor's " + getMinRange()
                    + "-" + MAX_RANGE + ".");

        int roll = rollAndRecord();
        int[] hits = getHitChart();
        int[] damage = Disruptor.standardDamageChart();
        int idx = Math.min(range, hits.length - 1);

        // Spend the charge whether it hits or misses: the shot was taken.
        chargesRemaining--;
        registerFire();

        return roll <= hits[idx] ? damage[Math.min(range, damage.length - 1)] : 0;
    }
}
