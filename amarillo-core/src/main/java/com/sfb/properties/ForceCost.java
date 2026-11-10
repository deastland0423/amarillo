package com.sfb.properties;

/**
 * (S3.21) A charge that counts against the overall FORCE total but escapes the per-ship
 * Commander's Option percentage — and, separately, may or may not raise the ship's Gross
 * Adjusted BPV.
 *
 * <h2>The category</h2>
 * S3.21 names the pattern for Annex #6A items: they "could be purchased as Commander's options"
 * but "are normally purchased as part of the overall force structure of a patrol scenario and
 * <b>are not within the percentage limits given here</b>." S3.211 then defines the basis that
 * percentage is taken of — "the ship, its refits, and its fighters, but not including the cost
 * of mandatory drone speed upgrades or crew quality adjustments" — so a member of this category
 * is excluded TWICE: not in the basis, and not in the cap. M9.17 says it again for mines ("the
 * cost of mines in mine racks is part of the overall force total, not a part of the cost of the
 * minesweeper"), SH.432 for drone speed, and Annex #6A for Legendary Officers ("if used, are
 * purchased as part of the base force, not as Commander's Options").
 *
 * <h2>Why this is an enum and not one number</h2>
 * The members do NOT behave alike, which is the trap this type exists to close. (S2.14) defines
 * the Gross Adjusted BPV — the basis victory percentages are taken of — as including
 * <ul>
 *   <li>"the cost of drone speed upgrades (FD2.22)",</li>
 *   <li>"the effect of crew quality (G21.0)",</li>
 *   <li>"the value of Legendary Officers (G22.0)",</li>
 * </ul>
 * and explicitly NOT including "Commander's Optional Purchases (S3.2)". Rack mines are bought
 * under S3.2 (M9.17 says so in terms), so they are in the force total and out of the GABPV,
 * while the other three are in both. A single figure added to the GABPV the way the drone speed
 * charge is would silently inflate what destroying a minesweeper is worth.
 *
 * <h2>An ADJUSTMENT, not a spend</h2>
 * Annex #6A prices crew quality as a percentage of the ship, and <b>Poor Crew is −20%</b> — a
 * discount. So a member's value may be negative and may be fractional, which is why
 * {@link com.sfb.objects.Ship#getForceCost} is a {@code double} and not the {@code int} the
 * first member got away with.
 */
public enum ForceCost {

    /**
     * (FD2.22, S2.11) The mandatory drone speed charge for the year's drones.
     * <p>
     * The first member, and for a long time the only one — it lived on {@code Ship} as its own
     * {@code droneSpeedBpv} field before this category existed. {@code getDroneSpeedBpv()}
     * remains as a delegate so its callers and tests did not have to move.
     * <p>
     * (S3.221) carries the one exception, and it is already built in
     * {@code CoiLoadout.droneUpgradeCost(Ship, int)}: "Faster drones available as limited or
     * restricted weapons in years prior to their general introduction are purchased as
     * Commander's Options." Speed bought AHEAD of general availability is therefore an ordinary
     * option inside the cap and never reaches this member.
     */
    DRONE_SPEED(true),

    /**
     * (G21.0) The crew quality adjustment: Annex #6A's Outstanding Crew +50%, Poor Crew −20%.
     * <p>
     * Named by S3.211 alongside drone speed as excluded from the option basis, and by S2.14 as
     * included in the GABPV. The only member that can be NEGATIVE.
     *
     * <h2>DO NOT derive this from {@code ship.getCrew().getCrewQuality()}</h2>
     * It is the obvious wiring and it is wrong, which is why this paragraph is longer than the
     * rule. Four Klingon penal hulls — D5J, D6J, E4J, F5J — declare {@code crewQuality: "poor"}
     * in their data, and for all four the discount is ALREADY IN THE STATED BPV. {@code d6j.json}
     * says so outright: "epv 123, bpv 98, and the 98 is live here BECAUSE crewQuality is poor."
     * That is R3.R51's economic/combat split, and it does not even track Annex #6A's percentage
     * — the E4J is 62/40, a 35% gap, where the rule would give 20%. Deriving the adjustment from
     * the declaration would discount those hulls twice and overrule a figure the chart states.
     *
     * <p>The two are different acts. A penal ship does not PURCHASE a poor crew; it comes with
     * one, priced by its chart entry, and the declaration exists so G21.1's combat penalties
     * apply. Annex #6A prices a player CHOOSING a crew quality for a scenario, which S3.21 calls
     * buying "as part of the overall force structure". Only that second act belongs here.
     *
     * <p>So this member is deliberately never set today: nothing in {@code ShipSpec} or the
     * fleet builder offers crew quality for sale yet. When something does, it sets this member
     * and leaves the hull's declaration alone. See the standing rule that where a rule derives a
     * number the data may also state it, and the stated one wins.
     */
    CREW_QUALITY(true),

    /**
     * (G22.0) Legendary Officers, which Annex #6A puts in this category in so many words: "if
     * used, are purchased as part of the base force, not as Commander's Options."
     * <p>
     * Not otherwise implemented — this member exists so that the day they are, the economics are
     * already right. Annex #6A prices them as a mix of percentages with floors (Legendary
     * Captain 25%, minimum 25 points) and flat figures (Legendary Engineer 15), which is why
     * this type holds a value computed elsewhere rather than a rate.
     */
    LEGENDARY_OFFICERS(true),

    /**
     * (M9.17) Mines carried in mine racks.
     * <p>
     * The member that forced this enum to carry a flag. M9.17: "The BPV of a minesweeper or
     * minelayer does not include its mines. These are purchased as Commander's Option Items
     * (S3.2), but mine purchases by minesweepers are not restricted to a percentage of the BPV
     * (M6.31). Essentially, the cost of mines in mine racks is part of the overall force total,
     * not a part of the cost of the minesweeper." Being an S3.2 purchase, (S2.14) keeps it out
     * of the Gross Adjusted BPV — unlike every other member here.
     * <p>
     * M9.17 also lets a scenario waive the charge entirely, "perhaps where a minesweeper or
     * minelayer is surprised by enemy units while going about its normal business". That waiver
     * belongs to whatever sets this member, not to the member itself.
     */
    RACK_MINES(false);

    private final boolean inGabpv;

    ForceCost(boolean inGabpv) {
        this.inGabpv = inGabpv;
    }

    /**
     * (S2.14) Whether this charge raises the Gross Adjusted BPV, the basis victory percentages
     * are taken of. True for everything the rule lists; false for an S3.2 purchase.
     */
    public boolean countsInGabpv() {
        return inGabpv;
    }
}
