package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;

/**
 * What a ship may spend on Commander's Option items, and whether a loadout fits (S3.2, S3.211).
 * <p>
 * One class because the rule was being computed in three places that had quietly drifted apart:
 * {@code FleetValidator.coiAllowance}, {@code ScenarioLoader.applyCoi} and the lobby endpoint
 * each did their own {@code CoiLoadout.budget(ship.getBpv(), 20)}. All three read the bare hull
 * BPV, and all three were therefore wrong for a carrier — {@code FleetValidator} most visibly,
 * since {@link #carriedFighterBpv} sits two methods above the allowance that ignored it.
 *
 * <h2>S3.211: what the percentage is taken OF</h2>
 * The budget is a share of the "Effective Adjusted Combat BPV" — "the ship, its refits, and its
 * fighters, but not including the cost of mandatory drone speed upgrades or crew quality
 * adjustments". So:
 * <ul>
 *   <li><b>the ship and its refits</b> — the hull BPV, which already carries its refits;</li>
 *   <li><b>plus Orion option-mount deltas</b> ({@link Ship#getEffectiveBpv}), whose own
 *       documentation says budgets should use it rather than {@code getBpv()};</li>
 *   <li><b>plus its fighters</b>, which the hull BPV does NOT include — a Kzinti CV is 147 with
 *       twelve 6-point AAS aboard, so its true basis is 219 and its budget 43, not 29.</li>
 * </ul>
 * The two exclusions need no code yet: neither mandatory drone speed upgrades (S3.211, see the
 * drone-cost work) nor crew quality adjustments are implemented. When drone speed upgrades
 * arrive they must be kept OUT of this basis, or a mandatory upgrade will inflate the very
 * budget it is excluded from.
 */
public final class CoiBudget {

    private CoiBudget() { }

    /** S3.2: the share of a ship's effective BPV that may go on options. */
    public static final int DEFAULT_PERCENT = 20;

    /**
     * BPV of the fighters a ship carries, which the hull BPV does not include.
     * <p>
     * Counted from the bays alone. At the point any budget is struck — fleet building, the
     * Commander's Options step — nothing has launched yet, so the bays hold the whole
     * complement. (Victory scoring in {@code Game} sums the same fighters plus any already on
     * the map, because by then they have scattered; that is a different question at a different
     * time and deliberately not shared with this one.)
     * <p>
     * Admin shuttles select themselves out: only {@link Fighter} carries a BPV, and the
     * catalogue gives a bpv of 0 to anything not bought with points.
     */
    public static int carriedFighterBpv(Ship ship) {
        if (ship == null || ship.getShuttles() == null) return 0;
        int total = 0;
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            if (s instanceof Fighter)
                total += ((Fighter) s).getBpv();
        return total;
    }

    /**
     * S3.211's "Effective Adjusted Combat BPV" — the basis the percentage is taken of.
     * <p>
     * Note on Orion mounts: {@link Ship#getEffectiveBpv} counts the deltas of mounts that are
     * already FITTED. During {@code applyCoi} the mounts named in the loadout have not been
     * equipped yet, so a budget struck there still sees the bare hull. That is the pre-existing
     * ordering, not something this method can decide; it is right for every caller that runs
     * after setup, and no worse than {@code getBpv()} for the one that does not.
     */
    public static double effectiveAdjustedCombatBpv(Ship ship) {
        if (ship == null) return 0;
        return ship.getEffectiveBpv() + carriedFighterBpv(ship);
    }

    /** The most this ship may spend on options, at the scenario's percentage. */
    public static double allowanceFor(Ship ship, int percent) {
        return Math.floor(effectiveAdjustedCombatBpv(ship) * percent / 100.0);
    }

    /** The most this ship may spend on options, at the standard 20% (S3.2). */
    public static double allowanceFor(Ship ship) {
        return allowanceFor(ship, DEFAULT_PERCENT);
    }

    /**
     * Why this loadout may not be bought, or null if it may.
     * <p>
     * Phrased for a player rather than a log: it names the ship, what the loadout costs and what
     * the ship had to spend, because "over budget" alone leaves them to work out by how much.
     */
    public static String refusalFor(Ship ship, CoiLoadout loadout, int percent) {
        if (ship == null || loadout == null) return null;
        double allowance = allowanceFor(ship, percent);
        double cost = loadout.totalCost();
        if (cost <= allowance) return null;
        return ship.getName() + " spends " + trim(cost)
                + " on Commander's Options but may spend only " + trim(allowance)
                + " (" + percent + "% of " + trim(effectiveAdjustedCombatBpv(ship)) + " BPV, S3.2)";
    }

    /** Whole numbers without a trailing ".0"; halves are real here (a boarding party is 0.5). */
    static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
