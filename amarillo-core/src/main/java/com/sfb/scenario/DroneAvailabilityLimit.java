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
 * <h2>What is counted: the warhead spaces the racks HOLD</h2>
 * <blockquote>FD10.641: "All percentages are based on 'warhead spaces' of drones in drone racks,
 * and fighter ready racks or launch rails (FD2.45). Round fractions of 0.49- down and 0.50+
 * up."</blockquote>
 *
 * <p>The basis is the hull's rack CAPACITY, not the drones a player happens to have loaded in
 * it. Read alone that sentence says the opposite, and this counted loaded drones until a
 * playtest showed what that does. A Kzinti BC with four type-A racks, eight medium drones and
 * one rack left empty was refused: twelve loaded spaces allow six Restricted, while the same
 * eight drones aboard a full sixteen-space ship allow eight. Two consequences follow that no
 * rule in FD10.6 contemplates — adding four GENERAL drones cures the breach, and a ship can
 * be refused for carrying LESS — and both of them are gone once the denominator is the ship
 * instead of the loadout.
 *
 * <p>The rest of FD10.6 describes the basis that way throughout. FD10.66: "A ship HAS ten drone
 * spaces and is allowed to have one of them (10%) of the Limited Availability type." FD10.671:
 * "six type-B drone racks WOULD HOLD 36 spaces of drones but only eighteen payload spaces for
 * warhead calculation purposes." Both are what the hull can hold. The two readings agree on
 * every fully-loaded ship — which is every ship in a printed scenario, and presumably why the
 * rulebook never has to say which one it means.
 *
 * <p>The NUMERATOR is still the drones actually aboard, because that is the thing a player
 * chooses. So a cap can only ever be breached by loading something hard to obtain, never by
 * leaving a space empty.
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
 * <h2>Dogfight drones are not unlimited — they have a pool of their own (FD10.644)</h2>
 * FD10.643 takes type-VIs out of "the calculations to determine how many special drones a given
 * ship may carry", and FD10.644 says what is done with them instead:
 *
 * <blockquote>"Fighter squadrons count type-VI drones loaded on type-VI rails separately from
 * all other drones."<br>
 * EXAMPLE: a squadron of twelve fighters with 24 type-VI and 24 type-I rails "computes the
 * number of limited and restricted availability speed upgrades of its type-VI drones under
 * (FD10.65) allowing it in Y166 to have twelve type-VIM drones, the remainder being type-VIS.
 * In that year half of the type-Is could also be Medium speed."</blockquote>
 *
 * <p>Twelve of 24 is half of one basket and twelve of 24 is half of the other, each at the
 * squadron's own percentage, and neither spends the other's allowance. So a type-VI's speed
 * upgrade is limited after all: the exclusion is from the ORDINARY basis, not from availability
 * altogether. A rack that will take nothing but dogfight drones — FD3.5's type-E, and the
 * LIGHT fighter rail when fighters arrive — contributes its spaces to a second pool with its
 * own denominator and its own allowance, at the same percentages. {@code accepts} is what
 * decides, rather than a list of rack types, because the question is the rule's own: a rack that
 * cannot hold an ordinary drone has no part in the ordinary basis.
 *
 * <p>One hull in the data mounts one today — the Klingon D6C, with two type-E racks — so a
 * D6C in Y166 may upgrade a quarter of its sixteen dogfight drones to type-VIM and no more.
 *
 * <p><b>This is a reading, not a quotation.</b> FD10.644 is written for fighter squadrons, and a
 * ship's own type-E rack is not one; nothing in FD10.6 addresses that rack directly. The
 * alternative reading is that FD10.643 exempts it outright, which would let a D6C upgrade all
 * sixteen while a fighter wing may upgrade four — a distinction no rule draws, for a rack
 * FD3.5 describes as holding the same drones.
 *
 * <p>Outside both pools the exclusion still has no arithmetic to do: the half-space frame
 * reports no payload spaces, so a type-VI in an ordinary rack contributes nothing whether or not
 * anyone checks. The check is kept for the reason it always was — FD10.643 leaves type-VIs
 * out of the CALCULATION, while the zero is an artefact of modelling a fixed warhead as no
 * configurable payload.
 *
 * <p><b>The type-H's dogfight magazine is still not modelled</b>, and it is the one place the
 * two pools are known to disagree with the rulebook. FD3.81 gives a type-H a fifth magazine of
 * eight type-VIs that "can fire as a type-E drone rack (FD3.5)"; the rack as a whole is not
 * dogfight-only, so all twenty of its spaces land in the ordinary pool and type-VIs loaded into
 * it count in neither. Harmless while no hull in the data declares a type-H — it is
 * starbase-only — and the fix is FD3.81's magazines, not a special case here.
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
 * alone. FD10.644's separate basis for dogfight drones is built, and a LIGHT rail will fall
 * into it unchanged the day a squadron's rails are counted, but the counting itself is a slice
 * of its own — as is FD10.645, where a type-VI on an ORDINARY rail is "a space of General
 * Availability for each such drone": a full space, in the ordinary pool, rather than the half
 * space it occupies in a rack.
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
     * FD10.641's "warhead spaces" aboard: the spaces the hull's racks HOLD, loaded or not.
     *
     * <p>Rack capacity rather than the drones in it — see the class javadoc for why, and for
     * the playtest that found it. The dogfight-only racks are not here; they are their own
     * basis, in {@link #dogfightSpaces}.
     *
     * <p>A magazine launcher counts all of its magazines, which is FD10.671's arithmetic
     * ("six type-B drone racks would hold 36 spaces of drones") applied to a rack that keeps
     * its reloads inside itself. Reload STORAGE is a separate quantity and no part of this.
     */
    public static double countedSpaces(Ship ship) {
        if (ship == null || ship.getWeapons() == null)
            return 0;
        double spaces = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack && !isDogfightOnly(rack))
                spaces += rack.getSpaces();
        return spaces;
    }

    /**
     * FD10.644's separate basis: the spaces of racks that will hold nothing but dogfight drones.
     *
     * <p>Zero for all but one hull in the data, and the whole of that pool's denominator.
     */
    public static double dogfightSpaces(Ship ship) {
        if (ship == null || ship.getWeapons() == null)
            return 0;
        double spaces = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack && isDogfightOnly(rack))
                spaces += rack.getSpaces();
        return spaces;
    }

    /**
     * Whether this rack belongs to FD10.644's pool rather than FD10.641's.
     *
     * <p>Asked of the rack rather than matched against a list of types: FD3.5's type-E "can
     * carry no other types", and {@code accepts} is where that already lives. A LIGHT fighter
     * rail (type-VI only, FD2.52) answers yes through the same question without being named
     * here, which is what will make FD10.644's own subject — the squadron — cost nothing
     * but the walk to its rails.
     */
    private static boolean isDogfightOnly(DroneRack rack) {
        return !rack.accepts(com.sfb.objects.DroneType.TypeI);
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
            if (isDogfightOnly(rack))
                continue;               // FD10.644's pool, counted by dogfightSpacesAtLeast
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
     * FD10.644's numerator: dogfight spaces holding an upgrade at least as hard to obtain as
     * {@code tier}, in that year.
     *
     * <p>Only the ENGINE is asked about. A dogfight warhead is fixed and FD10.21 forbids
     * modifying the frame, so a speed upgrade is the only thing a type-VI can carry that any
     * year makes hard to get — which is exactly what FD10.644's example counts ("the number of
     * limited and restricted availability speed upgrades of its type-VI drones under FD10.65").
     */
    public static double dogfightSpacesAtLeast(Ship ship, Availability tier, int year) {
        if (ship == null || ship.getWeapons() == null)
            return 0;
        double spaces = 0;
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack rack) || !isDogfightOnly(rack))
                continue;
            for (Drone d : rack.getAmmo()) {
                var spec = d.getSpec();
                if (spec == null)
                    continue;
                if (spec.propulsion().availabilityIn(year).atLeastAsRestrictedAs(tier))
                    spaces += spec.rackSpaces();
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
        return Math.round(countedSpaces(ship) * percentFor(ship, tier) / 100.0);
    }

    /** The same allowance over FD10.644's separate basis of dogfight spaces. */
    public static long dogfightAllowance(Ship ship, Availability tier) {
        return Math.round(dogfightSpaces(ship) * percentFor(ship, tier) / 100.0);
    }

    private static int percentFor(Ship ship, Availability tier) {
        return tier == Availability.LIMITED
                ? limitedPercentFor(ship) : restrictedPercentFor(ship);
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
        // The two pools in order, so a ship that breaches both is told about its ordinary
        // drones first -- that is the one the player was almost certainly looking at.
        addBreaches(out, ship, year, false);
        addBreaches(out, ship, year, true);
        return out;
    }

    private static void addBreaches(List<Breach> out, Ship ship, int year, boolean dogfight) {
        double total = dogfight ? dogfightSpaces(ship) : countedSpaces(ship);
        if (total <= 0)
            return;
        for (Availability tier : new Availability[] {
                Availability.RESTRICTED, Availability.LIMITED }) {
            double used = dogfight ? dogfightSpacesAtLeast(ship, tier, year)
                                   : spacesAtLeast(ship, tier, year);
            long allowed = Math.round(total * percentFor(ship, tier) / 100.0);
            if (used <= allowed)
                continue;
            String rule = tier == Availability.LIMITED ? "FD10.632" : "FD10.622";
            out.add(new Breach(rule, String.format(
                    "%s: %.1f of %.1f %swarhead spaces are %s availability or worse,"
                    + " over the %d%% allowed (%d spaces) — %s",
                    ship.getName(), used, total, dogfight ? "dogfight " : "",
                    tier.name().toLowerCase(), percentFor(ship, tier), allowed,
                    dogfight ? rule + " over FD10.644's separate count" : rule)));
        }
    }

    /** {@link #breaches} as sentences, for a caller that only means to say them. */
    public static List<String> violations(Ship ship, int year) {
        List<String> out = new ArrayList<>();
        for (Breach b : breaches(ship, year))
            out.add(b.message());
        return out;
    }
}
