package com.sfb.scenario;

import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.WeaponArmingType;
import com.sfb.weapons.DroneRack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



/**
 * Commander's Option Items (COI) selections for a single ship.
 *
 * COI costs (per ScenarioSpec.CommanderOptions.budgetPercent of ship BPV):
 *   Extra boarding party:   0.5 BPV each,  limit 10
 *   Convert BP → commando:  0.5 BPV each,  limit 2
 *   Extra commando squad:   1.0 BPV each,  limit 2
 *   T-bomb:                 4.0 BPV each,  no hard cap (+ 1 free dummy per purchased)
 *   Extra deck crew:        0.5 BPV each,  limit 4, capable carriers only (J4.816)
 *   Drone selection:        FD2.22's surcharge over the type-I-slows already paid for,
 *                           within the year, speed and availability limits
 */
public class CoiLoadout {

    /** Extra normal boarding parties to add (beyond the ship's base count). Max 10. */
    public int extraBoardingParties = 0;

    /**
     * Number of existing normal boarding parties to convert to commandos.
     * Each conversion costs 0.5 BPV. Max 2.
     */
    public int convertBpToCommando = 0;

    /** Extra commando squads to add (beyond the ship's base commandos). Max 2. Each costs 1.0 BPV. */
    public int extraCommandoSquads = 0;

    /** Extra T-bombs to purchase. Each costs 4.0 BPV; each includes 1 free dummy T-bomb. */
    public int extraTBombs = 0;

    /**
     * Extra deck crews (S3.2). Half a point each, four at most, and only a fully capable
     * carrier may hire them (J4.816): a casual carrier with a fighter or two aboard has not
     * the facilities, whatever its budget.
     * <p>
     * What they buy is turnaround. Rearming spends one crew per two fusion charges (J4.833),
     * so a Ranger's nine reload four Stingers a turn and thirteen would reload six.
     */
    public int extraDeckCrews = 0;

    /**
     * J4.621: which fighter MODEL this ship's fighter facilities are stocked for - a casual
     * carrier's choice, free of charge.
     * <p>
     * "The fighters on the carrier will determine what type of ready racks are on the escort, and
     * this will in turn determine the numbers of drones held in the racks." A concrete catalogue
     * type rather than a role, which is sound HERE although it would be wrong in a ship file: a
     * Commander's Option is chosen for one scenario with the year settled, so the player can be
     * offered the actual fighters the line fields that year. Null means the line's standard one.
     * <p>
     * Not priced. J4.621 gives a casual carrier its drones and chaff for nothing ("enough drones
     * and chaff pods to re-arm those fighters... three times"); only SPECIAL drones and speed
     * upgrades cost, and those are the existing drone lines.
     */
    public String fighterFacilityType;

    /**
     * Which fighters start ready, in the player's order of preference (S4.10-S4.12).
     * <p>
     * Free: readiness is what the weapon status already bought, not something else to buy.
     * At WS-0 and WS-1 the first two named are armed; at WS-2 the deck crews' budget is spent
     * down the list. Empty means no preference, and the first-come pass stands.
     * <p>
     * It only matters on a mixed squadron — but a Hydran Ranger refit carries Stinger-2s and
     * Stinger-Hs in one bay, and which two are hot when the shooting starts is a real choice.
     */
    public List<String> armedFighters = new ArrayList<>();

    /**
     * Drone loadout per rack, keyed by the rack's index in the ship's weapon list
     * (counting only DroneRack entries). Value is the drones to load into that rack, in
     * rack order. If a rack index is absent, the rack keeps its default ammo.
     *
     * <p>A {@link DroneLoad} rather than a {@link DroneType} because FD10.4's payload modules
     * are a third axis the enum cannot express: ECM, armor, probe and swordfish drones are
     * built, priced and capped, and until this held something that could NAME one they were
     * unreachable from any loadout a player could submit.
     */
    public Map<Integer, List<DroneLoad>> droneRackLoadouts = new LinkedHashMap<>();

    /**
     * What each fighter's rails carry, keyed by the FIGHTER's name (J4.23, FD10.646).
     *
     * <p>The value is one entry per rail that a ready rack is stocked for, in
     * {@link com.sfb.systemgroups.ReadyRack#stockedRails} order, which is the order the rack's
     * own slots are built in. A null entry leaves that rail as issued, so a loadout can name
     * the third rail alone without restating the other two.
     *
     * <h2>Why per fighter and not per type</h2>
     * The availability caps are a budget over the whole ship (FD10.641), so spending one is a
     * decision about WHICH fighters get the expensive drones. A Klingon carrier makes that
     * sharper than any: its conversion strips every drone rack off the hull, so the fighters
     * are the only place a special drone can go at all.
     *
     * <p>Absent means the fighter flies what the hull issued it (FD2.31), which after
     * FD10.646 is an ordinary drone even on a special rail.
     */
    public Map<String, List<DroneLoad>> fighterLoadouts = new LinkedHashMap<>();

    /**
     * Rack index to anti-drone rounds loaded, type-G only (FD3.70).
     *
     * Its own map because an anti-drone is not a DroneType — it flies nothing and seeks
     * nothing — and because the two share one four-space magazine, which is what the
     * validation below has to add up.
     */
    public Map<Integer, Integer> antiDroneLoadouts = new LinkedHashMap<>();

    /**
     * Pre-game conversion of an admin shuttle into a special weapon (WS-2: max 1, WS-3: max 2).
     * The named shuttle must be an admin shuttle in the ship's bay; it is replaced by the
     * converted type at game start.
     *
     * Suicide shuttle: converted and fully armed (3 turns × energyPerTurn each).
     * Scatter pack:   converted and loaded with chosen drones.
     */
    public static class SpecialShuttlePrep {
        /** Name of the admin shuttle to convert (e.g. "Enterprise-Shuttle 1"). */
        public String shuttleName = "";
        /** Conversion type: "suicide", "scatterpack", or "wildweasel". */
        public String type = "";
        /** Suicide only: energy allocated per arming turn (1–3). */
        public int energyPerTurn = 3;
        /** Scatter pack only: drone types to load into the pack. */
        public List<DroneType> drones = new ArrayList<>();
    }

    public List<SpecialShuttlePrep> specialShuttlePrep = new ArrayList<>();

    /**
     * Arming mode overrides for WS-3 heavy weapons, keyed by weapon designator
     * (e.g. "A", "B"). Only applies to weapons that are already fully armed at
     * game start. Absent entries keep their default STANDARD mode.
     * SPECIAL = Proximity. Plasma-R and Fusion beams cannot be overridden (ignored).
     */
    public Map<String, WeaponArmingType> weaponArmingModes = new LinkedHashMap<>();

    /**
     * Free overload energy dealt out to photon tubes at WS-III (S4.32), keyed by tube
     * designator. A ship gets {@link #FREE_OVERLOAD_PER_TUBE} points per photon tube and may
     * spread them across its tubes as it likes, so one tube can be taken to a full 100%
     * overload at the price of leaving another standard. The energy is free — it is not
     * charged against the commander's-option budget — but it may only overload, never arm
     * (S4.32), and a tube that takes any of it is committed to being an overload (E4.414).
     * <p>
     * Prior-turn arming at WS-II carries no overload energy at all (S4.32), so entries are
     * ignored for any ship below WS-III.
     */
    public Map<String, Double> photonOverload = new LinkedHashMap<>();

    /** Free overload energy each photon tube contributes to the ship's pool at WS-III (S4.32). */
    public static final double FREE_OVERLOAD_PER_TUBE = 2.0;

    /**
     * Orion option-mount choices (G15.4), keyed by mount designator (e.g. "A")
     * → option name from Annex #8B (e.g. "Phaser-3"). Applied at ship setup via
     * {@code OptionMountLoadout.equip}. These are the ship's inherent loadout —
     * their BPV delta adjusts the ship's effective BPV, and (unlike BPs/T-bombs)
     * is NOT charged against the commander's-option budget.
     */
    public Map<String, String> optionMounts = new LinkedHashMap<>();
    /**
     * R8.R3's wing arc choice per mount: designator -> "FORWARD" or "OUTBOARD".
     *
     * <p>A disruptor or fusion beam in a WING option mount may bear FA or outboard
     * (L + LF on the left, RF + R on the right) "depending on the taste of the Orion
     * player" — one ship may carry one of each. Nothing derives it, so it is bought with
     * the weapon like the weapon itself (G15.415). Absent or unrecognised means FORWARD,
     * and it is ignored for any mount where R8.R3 leaves no choice.
     */
    public Map<String, String> optionMountArcs = new LinkedHashMap<>();

    // --- Cost calculation helpers ---

    public static final double COST_EXTRA_BP          = 0.5;
    public static final double COST_CONVERT_TO_COMMANDO = 0.5;
    public static final double COST_EXTRA_COMMANDO     = 1.0;
    public static final double COST_TBOMB              = 4.0;
    public static final double COST_EXTRA_DECK_CREW    = 0.5;

    public static final int MAX_EXTRA_BP              = 10;
    public static final int MAX_CONVERT_TO_COMMANDO   = 2;
    public static final int MAX_EXTRA_COMMANDOS       = 2;
    public static final int MAX_EXTRA_DECK_CREWS      = 4;

    /**
     * Total BPV cost of this loadout, against the ship's Commander's Option budget.
     *
     * <p>Without a ship, which means without the racks: see
     * {@link #droneUpgradeCost(Ship, int)} for what that costs a magazine launcher. Every other
     * line here is the ship's own business and reads the same either way.
     *
     * @param year the scenario's year, which decides how much of the drone bill the SHIP pays
     *             rather than the budget (S3.221); see {@link #droneUpgradeCost(int)}
     */
    public double totalCost(int year) {
        return totalCost(null, year);
    }

    /**
     * The same, with the hull whose racks the loadout is indexing — the figure a budget check
     * should use, because FD3.44 charges a magazine launcher for one magazine of three.
     */
    public double totalCost(Ship ship, int year) {
        return extraBoardingParties * COST_EXTRA_BP
             + convertBpToCommando  * COST_CONVERT_TO_COMMANDO
             + extraCommandoSquads  * COST_EXTRA_COMMANDO
             + extraTBombs          * COST_TBOMB
             + extraDeckCrews       * COST_EXTRA_DECK_CREW
             + droneUpgradeCost(ship, year)
             + fighterUpgradeCost(year);
    }

    /**
     * FD2.22: what the improved drones in this loadout cost over the ones the ship already had.
     *
     * <blockquote>"Many optional improvements are available, each of which costs points that
     * must be paid through (S3.2). All costs are based on additions to the basic cost of a
     * type-I-slow drone. <b>The BPV of all ships includes type-I-slow drones; the surcharges for
     * improvements below must be paid in all cases</b> (except X-ships)."</blockquote>
     *
     * <p>So a rack arrives full of type-I-slow drones that are already paid for (FD2.31), and a
     * player who loads something better pays the difference. Each drone's absolute price comes
     * from FD10.5's charts via {@code DroneSpec.pointCost}; the baseline is what type-I-slows
     * filling the same SPACES would have cost, which is one point each at one space each.
     *
     * <p>Measuring the baseline in spaces rather than in drones is what makes FD2.11's trades
     * come out right: "two type-Is with one type-IV is 0.00" and "one type-I with two type-VIs
     * is 0.00" are both free precisely because the spaces match.
     *
     * <h2>Paid once per rack, not once per drone aboard</h2>
     * <blockquote>FD2.45: "The cost of improved drones (FD2.22) is paid only for a single
     * loading of each launch rack and/or fighter; the reloads (and drones stored on a carrier
     * for use by its fighters) are presumed to be of the same type without additional
     * cost."</blockquote>
     *
     * <p>Which is what this does by construction — it prices the rack's contents and knows
     * nothing about reload storage. A Federation CA+ upgraded to medium drones costs 2 points,
     * not 4.
     *
     * <h2>The two exceptions, which need the ship to see</h2>
     * FD2.45 names type-H and type-D racks as exceptions, and both are the same shape: their
     * reloads are not behind them but inside them, as the spare magazines (FD3.43), so pricing
     * "what is in the rack" prices the free magazines too. FD3.44 charges a type-D for one
     * magazine of its three and FD3.84 a type-H for two of its five.
     *
     * <p>Which is why there are two of these methods. A loadout names its racks by INDEX into
     * the ship's weapon list and holds no hull of its own, so without one it cannot tell a
     * magazine launcher from an ordinary rack and charges for everything — right for every rack
     * but those two. {@link #droneUpgradeCost(Ship, int)} is the one a budget check wants;
     * {@link PaidLoading} does the arithmetic and explains which magazine is the paid one.
     *
     * <h2>What the SHIP pays instead, and why the year is a parameter</h2>
     * The baseline is not the whole of what this does not charge for. S3.221: "Drone speed
     * upgrade charges are not included within the limit on Commander's Options (they are paid
     * for separately or included in the victory conditions of a fixed scenario). Exception:
     * Faster drones available as limited or restricted weapons in years prior to their general
     * introduction are purchased as Commander's Options." So the speed the scenario's year flies
     * is on the ship's bill ({@code Ship.getDroneSpeedBpv}), and only speed bought AHEAD of its
     * general availability falls here -- which is what makes this a function of the year.
     *
     * <p>A rack of type-IFs in Y180 therefore costs a player's budget nothing: fast drones are
     * standard issue, the ship is charged for them, and {@code ScenarioLoader.applyDroneSpeed}
     * had already loaded them before the player asked. The same rack in Y179 costs half a point
     * per drone, fast speed being Restricted that year. Before this took a year, both cost a
     * point per drone out of the 20% -- the budget paying a bill S3.211 excludes from the very
     * figure it is a percentage of.
     *
     * <h2>No refunds</h2>
     * FD2.22 calls these "surcharges" and nothing in the rules offers points back for arming a
     * ship worse than it came — not one line of FD2.11's exchange table is negative. That is
     * enforced where the one way to go under arises, which is an under-filled payload section:
     * see {@link com.sfb.objects.DroneSpec#payloadCost}. Nothing downstream of it has to subtract
     * a floor, and {@code DroneCostDecompositionTest} sweeps every legal drone to keep it so.
     */
    public double droneUpgradeCost(int year) {
        return droneUpgradeCost(null, year);
    }

    /**
     * The same surcharge, read against the hull the rack indexes point into.
     *
     * <p>A rack index with no rack behind it is still charged in full rather than waved through:
     * {@code ScenarioLoader.applyCoi} refuses an out-of-range index and loads nothing, so a
     * budget check that quietly priced it at zero would disagree with what the ship ends up
     * carrying in the safe direction for the player and the wrong one for the rule.
     *
     * @param ship the hull being outfitted, or null when the caller has none — see
     *             {@link #droneUpgradeCost(int)}
     */
    public double droneUpgradeCost(Ship ship, int year) {
        List<DroneRack> racks = racksOf(ship);
        double total = 0;
        for (Map.Entry<Integer, List<DroneLoad>> entry : droneRackLoadouts.entrySet()) {
            int index = entry.getKey();
            DroneRack rack = index >= 0 && index < racks.size() ? racks.get(index) : null;
            total += rackUpgradeCost(rack, entry.getValue(), year);
        }
        return total;
    }

    /**
     * FD2.45's surcharge for the fighters, which is charged once per fighter however many
     * reloads sit behind it.
     *
     * <blockquote>FD2.45: "The cost of improved drones (FD2.22) is paid only for a single
     * loading of each launch rack <b>and/or fighter</b>; the reloads (and drones stored on a
     * carrier for use by its fighters) are presumed to be of the same type without additional
     * cost."</blockquote>
     *
     * <p>So a fighter's rails are the single loading, and its ready rack — which J4.8222 makes
     * one reload of the same drones — is free. That is not a simplification: {@code
     * ShuttleSpace.armOccupantFully} arms a fighter by taking the drones OUT of its rack
     * (J4.8224), so the rails and the rack are two states of one loading, never two loadings.
     * Charging both would double every carrier's bill, and the same reasoning is why
     * {@link DroneAvailabilityLimit} counts them together against one basis.
     *
     * <p>Priced with a null rack, because FD2.45's two exceptions are the type-D and type-H
     * magazine launchers and no fighter has one: a rail holds what it holds.
     *
     * @param year the scenario's year, which decides how much of the bill the SHIP pays
     *             instead (S3.221) — see {@link #droneUpgradeCost(int)}
     */
    public double fighterUpgradeCost(int year) {
        double total = 0;
        for (List<DroneLoad> rails : fighterLoadouts.values()) {
            if (rails == null)
                continue;
            List<DroneLoad> named = new ArrayList<>();
            for (DroneLoad load : rails)
                if (load != null)
                    named.add(load);   // a null entry is a rail left as issued, already paid for
            total += rackUpgradeCost(null, named, year);
        }
        return total;
    }

    /** The ship's drone racks in the order {@link #droneRackLoadouts} indexes them. */
    private static List<DroneRack> racksOf(Ship ship) {
        List<DroneRack> racks = new ArrayList<>();
        if (ship == null)
            return racks;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                racks.add(rack);
        return racks;
    }

    /**
     * The same surcharge for ONE rack, which is the figure a per-rack decision needs.
     *
     * <p>{@code ScenarioLoader.applyCoi} applies a loadout rack by rack — a rack whose drones
     * are unavailable that year, or will not fit, is skipped while its neighbours load — so the
     * bill has to divide the same way. Charging a rack that never loaded would hand the enemy
     * victory points (S2.20 B) for drones the ship is not carrying.
     *
     * <h2>A plain sum, because no drone can be negative</h2>
     * This used to clamp the total at zero, which looked like the same no-refunds rule said once
     * per rack and was in fact a bug with a floor over it. A gutted type-IV reported −0.75, the
     * clamp hid it at the rack boundary, and inside the rack that negative was free to pay for a
     * dearer drone in the space beside it — one rack space buying another, which is precisely
     * what the rule forbids. FD10.48 says why there was never a credit to net out: discarding a
     * warhead happens to a drone already bought. So the floor moved to the payload term that can
     * be under-filled ({@link com.sfb.objects.DroneSpec#payloadCost}) and the sum here is honest.
     */
    public static double rackUpgradeCost(List<DroneLoad> rack, int year) {
        return rackUpgradeCost(null, rack, year);
    }

    /**
     * The same, for a named rack — which is what FD2.45's two exceptions need to be told apart.
     *
     * <p>A type-D is charged for one of its three magazines and a type-H for two of its five
     * ({@link com.sfb.weapons.DroneRack#paidLoadingSpaces()}); every other rack, and a null one,
     * is charged for the whole load as before. {@link PaidLoading} picks which drones those
     * spaces hold and says why it picks the dearest.
     *
     * @param rack the rack this load is going into, or null when it is not known
     */
    public static double rackUpgradeCost(DroneRack rack, List<DroneLoad> load, int year) {
        if (load == null || load.isEmpty())
            return 0;
        if (rack == null || rack.paidLoadingSpaces() >= rack.getSpaces()) {
            double total = 0;
            for (DroneLoad d : load)
                total += surchargeOn(d, year);
            return total;
        }
        return PaidLoading.dearestLoading(load, rack.paidLoadingSpaces(),
                DroneLoad::rackSpaces, d -> surchargeOn(d, year));
    }

    /**
     * What ONE drone costs over the type-I-slows it displaces. Never negative, and not because
     * anything here says so.
     *
     * <h2>Why there is no floor at this line</h2>
     * FD2.11's exchange table has no negative line — "two type-Is with one type-IV is 0.00" is
     * as good as a trade gets — so a legal drone priced correctly is never worth less than what
     * it displaces. The one way to come out under is to leave payload space empty, and FD10.48
     * prices that at source: the warhead was bought before it was discarded. With that in place
     * a floor here, or on the rack, has nothing to clamp;
     * {@code DroneCostDecompositionTest.noLegalDroneIsWorthLessThanWhatItDisplaces} sweeps every
     * drone the parser will build, in every year that moves the ladder, to hold that claim.
     *
     * <p>Which is also why this is public. The COI dialog prices a drone the player is still
     * assembling, by adding up the parts core sent it, and the figure it arrives at is this one.
     * Keeping both tiers on one name for one concept is the point.
     */
    public static double surchargeOn(DroneLoad drone, int year) {
        // The spaces the hull's own type-I-slows filled, plus the speed the scenario's year
        // puts on them at the ship's expense rather than the budget's (S3.221).
        return drone.pointCost()
             - drone.rackSpaces() * TYPE_I_SLOW_COST_PER_SPACE
             - drone.spec().speedCostIn(year);
    }

    /**
     * What the drones a ship already owns are worth, per rack space: a type-I-slow costs one
     * point (FD2.11) and occupies one space (FD2.1), so the two are the same number.
     */
    public static final double TYPE_I_SLOW_COST_PER_SPACE = 1.0;

    /**
     * Returns the BPV budget for a given ship BPV and budget percentage.
     * E.g. BPV=200, percent=20 → budget=40.
     */
    public static double budget(int shipBpv, int budgetPercent) {
        return Math.floor(shipBpv * budgetPercent / 100.0);
    }

    /**
     * True if this loadout's cost is within the allowed budget.
     *
     * <p>A BPV is not a hull, so this cannot see the racks and prices a magazine launcher's
     * spare magazines along with its paid one (FD3.44). {@link CoiBudget#refusalFor} is the
     * check with a ship in its hand, and the one the endpoint uses.
     */
    public boolean isWithinBudget(int shipBpv, int budgetPercent, int year) {
        return totalCost(year) <= budget(shipBpv, budgetPercent);
    }

    /**
     * Set the loadout for a specific drone rack index, from catalogue drones.
     *
     * <p>Kept alongside {@link #setDroneRackLoads} because most loadouts are exactly this —
     * a list of the drones the enum already names — and because erasure will not let the two
     * share a name.
     */
    public void setDroneRackLoadout(int rackIndex, List<DroneType> types) {
        List<DroneLoad> loads = new ArrayList<>();
        for (DroneType type : types)
            loads.add(DroneLoad.of(type));
        droneRackLoadouts.put(rackIndex, loads);
    }

    /** Set the loadout for a specific drone rack index, modified drones included (FD10.2). */
    public void setDroneRackLoads(int rackIndex, List<DroneLoad> loads) {
        droneRackLoadouts.put(rackIndex, new ArrayList<>(loads));
    }
}
