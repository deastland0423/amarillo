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

    /**
     * The most this ship may spend on options: the scenario's percentage, plus S3.223's extra tenth
     * if the hull is marked D%.
     * <p>
     * The bonus is applied HERE rather than in the no-argument overload, because every caller passes
     * a percentage — FleetValidator, ScenarioLoader, the /coi endpoint and refusalFor all do — and a
     * bonus that only reached the convenience overload would have been dead on arrival. The
     * scenario's figure is the house rate; S3.223 is a property of the hull, and the hull's
     * entitlement is what every one of those callers is actually asking for.
     */
    public static double allowanceFor(Ship ship, int percent) {
        return Math.floor(effectiveAdjustedCombatBpv(ship) * percentFor(ship, percent) / 100.0);
    }

    /**
     * S3.223: the extra tenth a "D%" ship gets, which may be spent on nothing but drones.
     * <p>
     * "Certain ships (marked 'D%' in the notes column on the Master Ship Chart) are allowed a higher
     * percentage of special drones under (FD10.6). These ships may expend points up to 30% of the
     * Effective Combat BPV on Commander's Option items, <b>but the extra 10% can only be spent for
     * extra or improved drones</b>."
     */
    public static final int D_PERCENT_BONUS = 10;

    /**
     * The percentage this particular ship may spend, which is not always the scenario's.
     * <p>
     * Kept apart from {@link #allowanceFor(Ship, int)} because the two answer different questions:
     * that one is "what is 20% of this hull", this one is "what is this hull allowed". A scenario
     * that sets an unusual percentage still gets the D% ship's extra tenth on top, since S3.223
     * grants it against the Effective Combat BPV and not against the house rate.
     */
    public static int percentFor(Ship ship, int scenarioPercent) {
        return ship != null && ship.isDPercent()
                ? scenarioPercent + D_PERCENT_BONUS : scenarioPercent;
    }

    /**
     * The most of a D% ship's allowance that is reserved for drones, in points — zero on every
     * other hull.
     * <p>
     * NOT ENFORCED YET, and deliberately exposed rather than quietly folded into the total: the
     * ring-fence can only be checked once a loadout says which of its lines are drones, and the
     * drone percentage caps it belongs with (FD10.622/FD10.632) are themselves unbuilt. A caller
     * that spends the whole 30% on boarding parties is currently over-spending by this much, and
     * this method is where that check will read from when the caps arrive.
     */
    public static double droneOnlyReserve(Ship ship) {
        return ship != null && ship.isDPercent()
                ? Math.floor(effectiveAdjustedCombatBpv(ship) * D_PERCENT_BONUS / 100.0) : 0;
    }

    /**
     * The most this ship may spend on options, at the standard 20% (S3.2) — plus S3.223's extra
     * tenth if the hull is marked D%.
     */
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
