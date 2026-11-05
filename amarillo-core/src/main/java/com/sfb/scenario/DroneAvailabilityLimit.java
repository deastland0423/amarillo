package com.sfb.scenario;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * FD10.622 and FD10.632: how much of a ship's drone load may be hard to come by.
 *
 * <blockquote>"No more than 25% of the drones on any ship can be (or contain) Restricted
 * Availability items."<br>
 * "No more than 10% of the drones on any ship can be (or contain) Limited Availability
 * items."</blockquote>
 *
 * <h2>The caps NEST</h2>
 * <blockquote>FD10.64: "The allowed percentage of Restricted Availability drones includes the
 * limit on Limited Availability drones. EXAMPLE: Kzinti ships can have 50% General
 * Availability, 30% Restricted (and/or General) Availability, and 20% Limited (and/or
 * Restricted and/or General) Availability drones."</blockquote>
 *
 * <p>So the Restricted test counts Limited drones too, which is what
 * {@link Availability#atLeastAsRestrictedAs} is for. A Kzinti ship at 20% Limited has used 20 of
 * its 50 Restricted points, not none of them.
 *
 * <h2>Three ways to get the doubled caps, and only one needs a flag</h2>
 * FD10.622 and FD10.632 each list the same three exceptions:
 * <ul>
 *   <li><b>Kzinti ships</b> — a faction check.</li>
 *   <li><b>Carriers with ten or more fighters, or five or more heavy fighters</b> — counted
 *       from the bays. A heavy fighter is J4.462's: one that "count[s] as two size-1 fighters",
 *       which is {@code squadronSlots() == 2}. None exists yet, so that half of the clause is
 *       written and unexercised rather than omitted.</li>
 *   <li><b>"Any other unit with 'D%' or 'DB' in the notes column of the Master Ship Chart"</b> —
 *       a chart marking, which is {@code ShipSpec.dPercent}. Nine hulls carry it, transcribed
 *       from Annex #3 and guarded by {@code DPercentShipTest} against hulls that merely look
 *       drone-heavy.</li>
 * </ul>
 *
 * <p><b>D% and DB are not the same marking</b>, though they share this list. Annex #3 points D%
 * at S3.223 and DB at S3.222, and S3.222 is conditional: a drone bombardment ship on an
 * independent bombardment mission "might be loaded entirely with type-III-XX drones", paying
 * 25% of the cost of the drones in its cargo boxes. Off that mission it "is treated as a D%
 * ship (S3.223)" — which is the only state a game of ours can be in, since nothing assigns a
 * mission. So one flag is right for everything reachable today, and the day scenarios can
 * assign one, DB needs its own.
 *
 * <h2>FD10.622's fourth exception is about a DRONE, not a hull</h2>
 * <blockquote>"Type-III-ECM drones on Klingon ships are Restricted Availability."</blockquote>
 *
 * <p>The only place in FD10.6 where who is carrying a drone changes how hard it is to obtain,
 * which is why it is applied in {@link #tierOf} here rather than in {@code DroneSpec}: the
 * drone does not know whose rack it is in. It demotes rather than exempts, so the drone still
 * spends Restricted allowance — the larger pool, which is the point.
 *
 * <p>Until now {@code dPercent} did only half of its own job: {@code CoiBudget} gave it
 * S3.223's extra tenth of budget, and the caps that extra tenth exists to buy into did not
 * exist. {@code CoiBudget.droneOnlyReserve} says so in its javadoc and names this as the thing
 * that was missing.
 *
 * <h2>What is counted: warhead spaces, and not every drone</h2>
 * <blockquote>FD10.641: "All percentages are based on 'warhead spaces' of drones in drone racks,
 * and fighter ready racks or launch rails (FD2.45). Round fractions of 0.49- down and 0.50+
 * up."</blockquote>
 *
 * <p>And FD10.643: "Type-VI drones are not included in any of the calculations to determine how
 * many special drones a given ship may carry."
 *
 * <h2>Except in a type-G rack, where FD10.643 counts them — and without that, the rule's own
 * example is a violation</h2>
 * <blockquote>"Exceptions: For purposes of type-G drone racks, type-VI and ADDs loaded in the
 * rack count as 'General Availability' drone spaces, e.g., a type-G rack on a non-Kzinti or
 * non-carrier ship might have one restricted availability drone space and three spaces of
 * type-VI or ADDs or a combination of the two. Type-VI spaces that receive speed upgrades in
 * some years (FD10.65) count against the G-rack's Restricted and Limited Availability drone
 * spaces."</blockquote>
 *
 * <p>Work that example on a Federation FFG, which is precisely its subject — one four-space
 * type-G, not Kzinti, not a carrier, not D%. One Restricted space beside three spaces of
 * type-VIs is four counted spaces of which one is Restricted, and 25% of four is one: legal, as
 * the rule says. Leave the type-VIs out and the same ship has ONE counted space, an allowance
 * of {@code Math.round(1 * 0.25) == 0}, and the loadout the rulebook prints as legal is a
 * breach of FD10.622. That cost nothing while the caps only left a setup note. It would have
 * cost a player their fleet now that {@link FleetValidator} refuses one over them.
 *
 * <p>So the exception needs the RACK and not only the drone, which is why the type-VI test sits
 * inside the rack loop rather than at the top of it. FD3.70 makes the type-G the only rack with
 * the anti-drone targeting system, so {@code acceptsAntiDrones()} is the question to ask. An
 * ADD round is half a space of General (FD3.70 gives it the same half space as a dogfight
 * drone), and a type-VI is half a space at its ENGINE's tier — the second sentence of the
 * exception — so a type-VI is General from Y120, Restricted Y100-Y119, and a type-VIM in Y166
 * is a Restricted half space.
 *
 * <p>Outside a type-G the exclusion still has no arithmetic to do: the half-space frame reports
 * no payload spaces, so a type-VI there contributes nothing whether or not anyone checks. The
 * check is kept for the reason it always was — FD10.643 leaves type-VIs out of the
 * CALCULATION, while the zero is an artefact of modelling a fixed warhead as no configurable
 * payload — but it is no longer the whole of the rule, and it never was.
 *
 * <p><b>FD10.643's last sentence is not built.</b> "Type-G drone racks and ADD racks replacing
 * some of their ADDs with type-VI drones compute the availability of speed upgrades (FD10.65)
 * based on the numbers of type-VI drones, not ADDs" — a basis of its own for the speed-upgrade
 * percentage, which wants the ADD rack of FD3.7 that is itself unbuilt.
 *
 * <h2>Two things this does NOT yet reach</h2>
 * <b>Fighters.</b> FD10.641 includes "fighter ready racks or launch rails" in the percentage,
 * and this walks the ship's own drone racks only. That understates the load on exactly the
 * ships the relaxed cap is written for, so a carrier is currently judged on its hull racks
 * alone. FD10.644 and FD10.645 give the fighter accounting its own rules — type-VI drones on
 * type-VI rails counted separately, type-VI drones on ordinary rails counted as General — and
 * that is a slice of its own.
 *
 * <p><b>Drones bought as Commander's Options (FD10.647).</b> "Extra drones purchased as
 * Commander's Options do not include reloads and must adhere to the drone percentages" — with
 * a worked example: a Klingon or Federation non-carrier non-DB ship "would have to purchase
 * four drones for one to be a restricted type". Nothing gates the purchase on that today; the
 * caps are measured over the finished ship after the fact.
 */
public final class DroneAvailabilityLimit {

    private DroneAvailabilityLimit() { }

    /** FD10.622: the ordinary ceiling on Restricted Availability drones. */
    public static final int RESTRICTED_PERCENT = 25;

    /** FD10.632: the ordinary ceiling on Limited Availability drones. */
    public static final int LIMITED_PERCENT = 10;

    /** FD10.622's exception: Kzinti ships, big carriers and "D%" hulls get this instead. */
    public static final int RESTRICTED_PERCENT_RELAXED = 50;

    /** FD10.632's matching exception. */
    public static final int LIMITED_PERCENT_RELAXED = 20;

    /** FD10.622: "Carriers with ten or more fighters..." */
    public static final int FIGHTERS_FOR_RELAXED_CAP = 10;

    /** "...or five or more heavy fighters". */
    public static final int HEAVY_FIGHTERS_FOR_RELAXED_CAP = 5;

    /**
     * Whether FD10.622's and FD10.632's exceptions apply to this hull.
     *
     * <p>All three clauses are the same list in both rules, so one method answers for both caps.
     */
    public static boolean hasRelaxedCaps(Ship ship) {
        if (ship == null)
            return false;
        if (ship.getFaction() == Faction.Kzinti)
            return true;
        if (ship.isDPercent())
            return true;
        int fighters = 0;
        int heavies = 0;
        if (ship.getShuttles() != null)
            for (com.sfb.objects.shuttles.Shuttle s : ship.getShuttles().getAllShuttles())
                if (s instanceof com.sfb.objects.shuttles.Fighter f) {
                    fighters++;
                    if (f.squadronSlots() >= 2)
                        heavies++;
                }
        return fighters >= FIGHTERS_FOR_RELAXED_CAP
                || heavies >= HEAVY_FIGHTERS_FOR_RELAXED_CAP;
    }

    /** FD10.622's ceiling for this hull, as a percentage. */
    public static int restrictedPercentFor(Ship ship) {
        return hasRelaxedCaps(ship) ? RESTRICTED_PERCENT_RELAXED : RESTRICTED_PERCENT;
    }

    /** FD10.632's ceiling for this hull, as a percentage. */
    public static int limitedPercentFor(Ship ship) {
        return hasRelaxedCaps(ship) ? LIMITED_PERCENT_RELAXED : LIMITED_PERCENT;
    }

    /**
     * FD10.641's "warhead spaces" aboard: every drone in every rack, less the type-VIs FD10.643
     * leaves out of the calculation.
     */
    public static double countedSpaces(Ship ship) {
        return spacesAtLeast(ship, Availability.GENERAL, Integer.MAX_VALUE);
    }

    /**
     * Warhead spaces holding a drone at least as hard to obtain as {@code tier}, in that year.
     *
     * <p>"At least as", not "equal to", because FD10.64 nests the caps: a Limited drone spends
     * Restricted allowance as well as its own.
     */
    public static double spacesAtLeast(Ship ship, Availability tier, int year) {
        if (ship == null || ship.getWeapons() == null)
            return 0;
        double spaces = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack rack))
                continue;
            // FD10.643's exception is about the RACK: in a type-G, type-VIs and ADDs are
            // counted rather than ignored. FD3.70 makes the type-G the only rack with the
            // anti-drone targeting system, so that is the test for one.
            boolean typeG = rack.acceptsAntiDrones();
            // "ADDs loaded in the rack count as 'General Availability' drone spaces", at
            // FD3.70's half space each. General, so they fall out of every tier above it.
            if (typeG && Availability.GENERAL.atLeastAsRestrictedAs(tier))
                spaces += rack.getAddAmmo() * DroneRack.ANTI_DRONE_SPACE;
            for (Drone d : rack.getAmmo()) {
                var spec = d.getSpec();
                if (spec == null)
                    continue;
                if (d.getDroneType() != null && d.getDroneType().isTypeVI()) {
                    if (!typeG)
                        continue;           // FD10.643's general rule
                    // The exception: half a rack space each, General by default, but "type-VI
                    // spaces that receive speed upgrades in some years (FD10.65) count against
                    // the G-rack's Restricted and Limited Availability drone spaces" -- which
                    // is the engine's own tier and nothing more, since a dogfight warhead is
                    // fixed and FD10.21 forbids modifying the frame.
                    if (spec.propulsion().availabilityIn(year).atLeastAsRestrictedAs(tier))
                        spaces += spec.rackSpaces();
                    continue;
                }
                if (year == Integer.MAX_VALUE) {
                    // The caller asking for "everything". The year cannot matter — every tier
                    // is at least as restricted as GENERAL — but the ACCOUNTING must still be
                    // FD10.66's, or the denominator would count half-spaces the numerator has
                    // already rounded into whole ones. Any year gives the same total.
                    for (var v : spec.spacesByAvailability(0).values())
                        spaces += v;
                    continue;
                }
                // FD10.641: a drone is a BILL of tiers, not one tier. A type-IV whose speed is
                // Restricted and whose second module is Limited is "one space of Restricted and
                // one space of Limited" — so each space is tested on its own.
                for (var entry : spec.spacesByAvailability(year).entrySet())
                    if (tierOf(entry.getKey(), ship, spec).atLeastAsRestrictedAs(tier))
                        spaces += entry.getValue();
            }
        }
        return spaces;
    }

    /**
     * What a space of this tier actually counts as aboard this ship (FD10.622's last clause).
     *
     * <blockquote>"Type-III-ECM drones on Klingon ships are Restricted Availability."</blockquote>
     *
     * <p>The one place in FD10.6 where WHO IS CARRYING the drone changes how hard it is to
     * obtain, which is why it cannot live on {@code DroneSpec} beside the rest of the tier
     * arithmetic: a drone does not know whose rack it is in. {@code DroneSpec.isTypeIiiEcm}
     * recognises the drone and this applies the empire.
     *
     * <p>It demotes rather than exempts. The drone still spends Restricted allowance, and under
     * FD10.64's nesting that allowance is the larger pool — which is the point of the clause.
     * Klingon doctrine leans on the type-III-ECM, and at 10% a D7 could carry one of them in
     * ten warhead spaces.
     */
    private static Availability tierOf(Availability space, Ship ship, com.sfb.objects.DroneSpec spec) {
        if (space == Availability.LIMITED
                && ship.getFaction() == Faction.Klingon
                && spec.isTypeIiiEcm())
            return Availability.RESTRICTED;
        return space;
    }

    /**
     * How many warhead spaces of a tier this hull may carry, rounded as FD10.641 says: "Round
     * fractions of 0.49- down and 0.50+ up."
     */
    public static long allowance(Ship ship, Availability tier) {
        int percent = tier == Availability.LIMITED
                ? limitedPercentFor(ship) : restrictedPercentFor(ship);
        return Math.round(countedSpaces(ship) * percent / 100.0);
    }

    /**
     * One cap a ship's drone load breaches, and the rule that sets it.
     *
     * <p>The rule number travels beside the sentence rather than only inside it because the two
     * readers want different things. A setup note wants the sentence; {@code FleetValidator}
     * wants to file the breach under "FD10.622" the way it files every other violation under
     * its clause, and parsing that back out of prose it had just been handed would be the kind
     * of round trip that breaks the first time the wording changes.
     */
    public record Breach(String rule, String message) { }

    /**
     * Which caps this ship's drone load breaches in this year, or an empty list.
     *
     * <p>Both caps are reported rather than only the first, because they are independent
     * ceilings on overlapping sets and a loadout can breach either, or both for different
     * reasons.
     */
    public static List<Breach> breaches(Ship ship, int year) {
        List<Breach> out = new ArrayList<>();
        if (ship == null)
            return out;
        double total = countedSpaces(ship);
        if (total <= 0)
            return out;

        for (Availability tier : new Availability[] {
                Availability.RESTRICTED, Availability.LIMITED }) {
            double used = spacesAtLeast(ship, tier, year);
            long allowed = allowance(ship, tier);
            if (used <= allowed)
                continue;
            String rule = tier == Availability.LIMITED ? "FD10.632" : "FD10.622";
            out.add(new Breach(rule, String.format(
                    "%s: %.1f of %.1f warhead spaces are %s availability or worse,"
                    + " over the %d%% allowed (%d spaces) — %s",
                    ship.getName(), used, total, tier.name().toLowerCase(),
                    tier == Availability.LIMITED
                            ? limitedPercentFor(ship) : restrictedPercentFor(ship),
                    allowed, rule)));
        }
        return out;
    }

    /** {@link #breaches} as sentences, for a caller that only means to say them. */
    public static List<String> violations(Ship ship, int year) {
        List<String> out = new ArrayList<>();
        for (Breach b : breaches(ship, year))
            out.add(b.message());
        return out;
    }
}
