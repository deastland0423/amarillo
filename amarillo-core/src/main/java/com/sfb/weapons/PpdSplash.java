package com.sfb.weapons;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * E11.3: the PPD combat chart, and how one pulse's damage spreads over a target's shields.
 *
 * <pre>
 *   Range    0-3   4-10   11-15  16-20  21-25  26-30  31-40
 *   Hit #     -      9       8      7      6      5      4
 *   Damage    0      6       5      4      3      2      1
 *   Splash    0   1+4+1   1+3+1  1+2+1  1+1+1  1+1+0  0+1+0
 *   Alt       0    3+3     3+2    2+2    2+1    1+1    1+0
 * </pre>
 *
 * <p>The chart runs BACKWARDS compared with every other direct-fire weapon in the game: the PPD
 * does nothing at all inside range 4, peaks at 4-10, and still reaches 40. That is the "echelon
 * tactics" E11.0 opens with, and it is why a Hellbore's table and this one must not be read with
 * the same assumptions.
 *
 * <h2>The splash is not optional (E11.35)</h2>
 * "This is not an optional use of the weapon. It is the way that the weapon always operates."
 * The "Damage" row is a statistical total and is never what lands on a single shield.
 *
 * <h2>Three cases, and only the first is the obvious one</h2>
 * <ul>
 *   <li><b>E11.352, against a shield:</b> the centre element on the facing shield, the splash
 *       elements on the shields clockwise and counter-clockwise from it. At two damage points
 *       "ignore the weaker adjacent shield"; at one, ignore both. So the zero in 1+1+0 is NOT a
 *       fixed position -- it lands on whichever neighbour is weaker at the time.</li>
 *   <li><b>E11.351, along a hex side:</b> do not determine which shield is hit at all. Split per
 *       the alternate row between the two, "with any odd points going to the stronger shield".</li>
 *   <li><b>E11.353, no shields:</b> size class 6 and 7 units, monsters, planets, small and
 *       medium ground bases. Ignore the splash and use the centre number alone. Explicitly NOT a
 *       size 5-or-larger ship that has merely lost its shields -- the rule is about units that
 *       never had any.</li>
 * </ul>
 *
 * <h2>The tie-break is ours, not the rulebook's</h2>
 * Both E11.351 and E11.352 hand an equal-shields tie to "the owner of the target". We resolve it
 * to the LOWEST-NUMBERED tied shield instead of asking. The stake is one damage point; a PPD
 * fires four pulses and a heavy ISC hull mounts four PPDs, so prompting would interrupt the
 * player who is not even firing, a dozen times a turn, for a point each. A die roll would be
 * worse than a rule: the rules give a free choice, not a random outcome, and a roll would have
 * to be journalled to replay. If the agency is ever wanted back, the answer is a standing
 * per-ship preference set once, not a prompt.
 */
public final class PpdSplash {

    private PpdSplash() {}

    /** Upper bound of each range band; index 0 is the dead band inside range 4. */
    private static final int[] BAND_MAX = { 3, 10, 15, 20, 25, 30, 40 };

    /** 2d6 wave-lock number per band; 0 means the PPD cannot score at that range (E11.32). */
    private static final int[] HIT_NUMBER = { 0, 9, 8, 7, 6, 5, 4 };

    /** Splash row as {centre, stronger adjacent, weaker adjacent} -- see the class note. */
    private static final int[][] SPLASH = {
        { 0, 0, 0 }, { 4, 1, 1 }, { 3, 1, 1 }, { 2, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 }, { 1, 0, 0 }
    };

    /** Alternate row as {stronger shield, weaker shield} -- the odd point goes to the stronger. */
    private static final int[][] ALT = {
        { 0, 0 }, { 3, 3 }, { 3, 2 }, { 2, 2 }, { 2, 1 }, { 1, 1 }, { 1, 0 }
    };

    /** The band a range falls in, or -1 beyond range 40 where the weapon does not reach. */
    static int band(int range) {
        for (int i = 0; i < BAND_MAX.length; i++)
            if (range <= BAND_MAX[i])
                return i;
        return -1;
    }

    /** The 2d6 roll a wave-lock needs at this range, or 0 if the PPD cannot score there. */
    public static int hitNumber(int range) {
        int b = band(range);
        return b < 0 ? 0 : HIT_NUMBER[b];
    }

    /** Total damage one pulse scores at this range -- a statistical figure, never a single hit. */
    public static int totalDamage(int range) {
        int b = band(range);
        if (b < 0) {
            return 0;
        }
        return SPLASH[b][0] + SPLASH[b][1] + SPLASH[b][2];
    }

    /** The shield one step clockwise from {@code shield}, 1-6 wrapping. */
    static int clockwise(int shield) {
        return shield % 6 + 1;
    }

    /** The shield one step counter-clockwise from {@code shield}, 1-6 wrapping. */
    static int counterClockwise(int shield) {
        return (shield + 4) % 6 + 1;
    }

    /**
     * E11.352: one pulse against a facing shield, spread over it and its two neighbours.
     *
     * @param strengthOf current strength of a shield, for deciding which neighbour is weaker
     * @return shield number to damage, centre first; empty when the PPD scores nothing here
     */
    public static Map<Integer, Integer> againstShield(
            int range, int facingShield, IntUnaryOperator strengthOf) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        int b = band(range);
        if (b < 0 || SPLASH[b][0] == 0) {
            return out;
        }

        int cw = clockwise(facingShield);
        int ccw = counterClockwise(facingShield);
        // Which neighbour is the STRONGER one, since that is what the chart's asymmetric rows
        // key on. Equal strength is the rulebook's "owner of the target decides"; we take the
        // lower-numbered shield -- see the class note on why this is not asked and not rolled.
        int cwStrength = strengthOf.applyAsInt(cw);
        int ccwStrength = strengthOf.applyAsInt(ccw);
        boolean cwIsStronger = cwStrength > ccwStrength
                || (cwStrength == ccwStrength && cw < ccw);
        int strongerAdj = cwIsStronger ? cw : ccw;
        int weakerAdj = cwIsStronger ? ccw : cw;

        out.put(facingShield, SPLASH[b][0]);
        if (SPLASH[b][1] > 0) {
            out.put(strongerAdj, SPLASH[b][1]);
        }
        if (SPLASH[b][2] > 0) {
            out.put(weakerAdj, SPLASH[b][2]);
        }
        return out;
    }

    /**
     * E11.351: the line of fire runs along a hex side, so no single shield is "hit". Split per
     * the alternate row, the odd point to the stronger of the two.
     */
    public static Map<Integer, Integer> alongHexSide(
            int range, int shieldA, int shieldB, IntUnaryOperator strengthOf) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        int b = band(range);
        if (b < 0 || ALT[b][0] == 0) {
            return out;
        }

        int aStrength = strengthOf.applyAsInt(shieldA);
        int bStrength = strengthOf.applyAsInt(shieldB);
        boolean aIsStronger = aStrength > bStrength
                || (aStrength == bStrength && shieldA < shieldB);
        int stronger = aIsStronger ? shieldA : shieldB;
        int weaker = aIsStronger ? shieldB : shieldA;

        out.put(stronger, ALT[b][0]);
        if (ALT[b][1] > 0) {
            out.put(weaker, ALT[b][1]);
        }
        return out;
    }

    /**
     * E11.353: a target with no shields to splash across. The centre element only.
     */
    public static int centreOnly(int range) {
        int b = band(range);
        return b < 0 ? 0 : SPLASH[b][0];
    }
}
