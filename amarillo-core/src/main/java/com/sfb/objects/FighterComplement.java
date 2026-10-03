package com.sfb.objects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A carrier's fighter complement declared by ROLE, and resolved to concrete types by the year
 * (J4.4).
 * <p>
 * Which fighter a carrier flies is a function of the era, not of the ship. A Kzinti CVS entered
 * service in Y170 with AAS aboard and re-equipped with the HAAS in Y173, the TAAS in Y177, the
 * TADS in Y180 and the TADSC in Y183 — and not one of those changes its SSD. The complement is
 * the same size with the same shape throughout; only the aircraft differ.
 * <p>
 * So the ship file says how many of each ROLE it carries and {@code fighterLines} in
 * shuttles.json says what fills them. The alternative — a file per era — would have multiplied
 * every hull correction by five and left the fighters' BPV, which feeds the carrier's S3.211
 * option budget, to be kept in step by hand in each copy.
 *
 * <h2>The fallback is what makes one declaration cover every era</h2>
 * A role the era has no type for falls back to the line's SUPERIORITY fighter. The Hydran RN
 * declares six superiority, two attack and one EW; in Y170 that is six Stinger-2s, two
 * Stinger-Hs and a Stinger-E, and in Y134 — when only the Stinger-1 existed — it is nine
 * Stinger-1s. Both printed complements, one declaration, and no need to special-case the years
 * before an EW fighter was invented. The annex states the same fallback for assault fighters:
 * "If the ship is not operating assault fighters, it will [be the] same as the other spare
 * fighter."
 *
 * <h2>The four roles, in the annex's own words</h2>
 * <ul>
 * <li><b>superiority</b> — the general-purpose fighter, intercepting enemy fighters and posing
 *     some threat to ships. The fallback every other role resolves to.</li>
 * <li><b>attack</b> — dedicated to attacking ships but carrying no heavy weapon; found on many
 *     kinds of carrier.</li>
 * <li><b>assault</b> — carrying a heavy weapon: disruptors or photons, and plasma-F for the
 *     Romulans and Gorns. The annex labels these "A" and calls them "single-space assault
 *     fighters". Mostly the largest carriers and bases, but NOT enforced by size class — the
 *     annex gives the size class 3 Klingon D6V one.</li>
 * <li><b>elite</b> — a superiority fighter of its own programme, flown by very few hulls and
 *     evolving on its own schedule rather than with the standard line. The Federation F-14 is
 *     the case: F-14, F-14A, F-14B, carried by the CVA while everything else flies the F-4 to
 *     F-18 progression.</li>
 * <li><b>ew</b> — the electronic warfare fighter (J4.463 caps how many a carrier may field).
 *     A bay flying ONE programme names this and nothing more. A bay flying two names
 *     {@code <role>_ew} instead, to say which programme its EW fighter belongs to.</li>
 * </ul>
 * <b>heavy</b> is deliberately NOT a role. The annex labels two-space heavy fighters "H", and a
 * craft occupying two shuttle boxes breaks the bay's one-craft-per-space model; J4.463 also
 * counts heavy fighters in their own right. The name is reserved for when that is built.
 */
public final class FighterComplement {

    /**
     * Roles in the order they are seated — and the order MATTERS, so it is pinned here.
     * <p>
     * Bay order decides which fighters start ready when nobody states a preference
     * (S4.10-S4.12), so it is not arbitrary. The EW fighter comes LAST because it has nothing a
     * deck crew can load - no fusion charge, no drone - so a first-come readiness pass that
     * reached it would spend a slot achieving nothing. S4.10's pass skips unarmable craft
     * anyway (FighterArming.needsArming), and this makes the two agree rather than rely on it.
     * <p>
     * The hand-written data disagreed with itself on this: the Kzinti carriers listed their EW
     * fighter last and the Hydran RN+ listed it first. One order had to win.
     */
    public static final List<String> COMBAT_ROLES =
            List.of("superiority", "elite", "attack", "assault");

    /**
     * The generic EW key: "this line's EW fighter", whichever programme that is. Every bay
     * flying a single programme uses this and nothing more.
     */
    public static final String EW = "ew";

    /**
     * The EW variant of a particular programme, e.g. {@code elite_ew}.
     * <p>
     * Derived rather than enumerated, and that is the point. A bay flying two programmes has
     * to say WHICH one its EW fighter belongs to: the Federation CVA carries an F-14 squadron
     * and an A-10 squadron split across two bays of twelve, so one bay's EW fighter is an
     * F-14E and the other's an A-10E. Naming each pair by hand would mean inventing a second
     * name every time a programme is added, and forgetting one is the silent kind of mistake
     * {@code FighterRoleNameTest} exists to catch.
     */
    public static String ewRoleFor(String combatRole) {
        return combatRole + "_" + EW;
    }

    /**
     * Every key a bay or a line era may name, in SEATING order: the combat roles first, then
     * every EW key.
     */
    public static final List<String> ROLES = buildRoles();

    private static List<String> buildRoles() {
        List<String> all = new ArrayList<>(COMBAT_ROLES);
        all.add(EW);
        for (String role : COMBAT_ROLES)
            all.add(ewRoleFor(role));
        return List.copyOf(all);
    }

    /** Every EW key, generic and programme-specific alike. */
    public static boolean isEwRole(String role) {
        return EW.equals(role) || role.endsWith("_" + EW);
    }

    private final String line;
    private final Map<String, Integer> counts;

    public FighterComplement(String line, Map<String, Integer> counts) {
        this.line = line;
        this.counts = new LinkedHashMap<>(counts);
    }

    public String getLine() {
        return line;
    }

    public int countOf(String role) {
        return counts.getOrDefault(role, 0);
    }

    public int total() {
        int n = 0;
        for (String role : ROLES)
            n += countOf(role);
        return n;
    }

    /**
     * How many EW fighters this complement calls for — what J4.463 caps.
     * <p>
     * Every EW key, not just the generic one: a bay naming {@code elite_ew} is still fielding
     * an EW fighter, and a cap that counted only {@code ew} would let a carrier field one of
     * each programme's EW variant and call it none.
     */
    public int ewCount() {
        int n = 0;
        for (String role : ROLES)
            if (isEwRole(role))
                n += countOf(role);
        return n;
    }

    /**
     * Reads a complement from the bay map a ship file produces, or null if it declares none.
     * Tolerates a missing line name, since a complement without one cannot be resolved and
     * should be ignored rather than half-applied.
     */
    @SuppressWarnings("unchecked")
    public static FighterComplement fromBayMap(Object raw) {
        if (!(raw instanceof Map))
            return null;
        Map<String, Object> m = (Map<String, Object>) raw;
        Object lineObj = m.get("line");
        if (lineObj == null || String.valueOf(lineObj).isBlank())
            return null;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String role : ROLES) {
            Object v = m.get(role);
            if (v instanceof Number)
                counts.put(role, ((Number) v).intValue());
        }
        return new FighterComplement(String.valueOf(lineObj), counts);
    }

    /**
     * The concrete fighter types this complement resolves to in {@code year}, in seating order,
     * one entry per fighter. Empty if the line is unknown or had not begun.
     */
    public List<String> typesFor(int year) {
        ShuttleCatalog.LineEra era = ShuttleCatalog.eraFor(line, year);
        List<String> types = new ArrayList<>();
        if (era == null)
            return types;
        for (String role : ROLES) {
            String type = era.typeFor(role);
            if (type == null)
                continue;
            for (int i = 0; i < countOf(role); i++)
                types.add(type);
        }
        return types;
    }

    /**
     * Seat this complement in {@code bay} for {@code year}, replacing any fighters already
     * there and leaving everything else — admin shuttles, bay drone racks, destroyed spaces —
     * exactly as it was.
     * <p>
     * Re-seating rather than adding, because this runs twice for a ship in a scenario: once at
     * construction from the ship's own service year, so a ship built outside any scenario still
     * has the right fighters, and again from the scenario's year, which is the one that counts.
     *
     * @return the types seated, in order; empty if the line could not be resolved
     */
    public List<String> applyTo(ShuttleBay bay, int year, String namePrefix) {
        return applyTo(bay, year, namePrefix, new LinkedHashMap<>());
    }

    /**
     * As {@link #applyTo(ShuttleBay, int, String)}, but numbering from a counter SHARED across
     * the whole ship.
     * <p>
     * A per-bay counter is a bug, and it bit: the Hydran RN spreads nine fighters over three
     * bays, each bay restarted at one, and the ship ended up with three craft called Stinger1-1
     * and three called Stinger1-2. Anything that looks a fighter up by name — a deck crew
     * posting, a readiness choice — then found whichever duplicate came first.
     */
    public List<String> applyTo(ShuttleBay bay, int year, String namePrefix,
                                Map<String, Integer> typeCount) {
        List<String> types = typesFor(year);
        if (bay == null || types.isEmpty())
            return List.of();

        // Clear the fighters out first so a re-seat does not stack a second complement on top
        // of the first. A space holding anything that is not a fighter is left alone.
        List<com.sfb.systemgroups.ShuttleSpace> freed = new ArrayList<>();
        for (com.sfb.systemgroups.ShuttleSpace space : bay.getSpaces()) {
            if (space.isDestroyed())
                continue;
            Shuttle occupant = space.getShuttle();
            if (occupant instanceof Fighter) {
                space.setShuttle(null);
                freed.add(space);
            } else if (space.isEmpty()) {
                freed.add(space);
            }
        }

        int i = 0;
        for (String type : types) {
            int n = typeCount.merge(type, 1, Integer::sum);
            String name = (namePrefix == null ? "" : namePrefix)
                    + ShuttleCatalog.displayNameOf(type) + "-" + n;
            Shuttle fighter = ShuttleBay.buildShuttle(type, name);
            if (i < freed.size())
                // reequipFor, not setShuttle: the box must re-derive its capacitor and ready
                // rack for the fighter it is actually holding now. setShuttle keeps what the
                // FIRST occupant taught it, which is right in play and wrong here — a bay
                // re-seated for a later era otherwise services the new squadron with the old
                // one's fittings.
                freed.get(i).reequipFor(fighter);
            else
                bay.addShuttle(fighter, 0);   // more fighters than spaces: widen the bay
            i++;
        }
        return types;
    }

    /**
     * J4.4: re-seat every declared complement aboard {@code ship} for {@code year}.
     * <p>
     * A ship is built from its own file, which knows only its service year, so a Kzinti CVS
     * arrives flying the AAS it entered service with in Y170. This is where it re-equips: HAAS
     * from Y173, TAAS from Y177, TADS from Y180, TADSC from Y183. A bay whose contents were
     * listed literally has no complement and is left exactly as the file wrote it.
     * <p>
     * It lives here, rather than in the caller that first needed it, because THREE paths build
     * ships and every one of them has to price them the same: the scenario loader, the fleet
     * resolver, and the ship-catalogue endpoint that quotes the shelf price. While this was
     * private to {@code ScenarioLoader} the other two silently sold every carrier at its hull's
     * service year — a Kzinti CVS at 243 points in a Y183 fleet instead of 315. That is the same
     * drift {@code CoiBudget} exists to prevent, one layer down.
     * <p>
     * S8.131 is the rule underneath: the scenario date "will define ... what ships, FIGHTERS,
     * and other units will be available". The year is not a preference, so there is deliberately
     * no way for a buyer to ask for an earlier, cheaper complement.
     *
     * @param year the SCENARIO's year; a year of zero or less leaves the ship untouched, which
     *             is what a catalogue listing with no date wants — it then shows the ship as its
     *             own service year built it
     * @return one note per bay that could not be re-seated, for the caller to surface
     */
    public static List<String> reseat(com.sfb.objects.Ship ship, int year) {
        List<String> notes = new ArrayList<>();
        if (ship == null || year <= 0 || ship.getShuttles() == null)
            return notes;
        // One counter for the whole ship, so a complement spread over several bays is numbered
        // straight through: the Hydran RN's nine fighters are Stinger1-1 to Stinger1-9, not three
        // bays each starting again at one.
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
            FighterComplement complement = bay.getFighterComplement();
            if (complement == null)
                continue;
            // J4.62: a casual carrier declares a LINE and no counts - the line is there to say
            // what its ready racks serve, not to seat anybody. Skipped before the note below,
            // which would otherwise tell the player every scenario that a line he never asked to
            // seat has nothing available.
            if (complement.total() == 0) {
                equipFighterServiceFacilities(bay, complement, year, ship.getFighterServiceType());
                continue;
            }
            if (complement.typesFor(year).isEmpty()) {
                notes.add("Fighters: line '" + complement.getLine()
                        + "' has nothing available in Y" + year
                        + " — keeping the complement it was built with");
                continue;
            }
            complement.applyTo(bay, year, "", typeCount);
        }
        // The hold is sized against what the ready racks hold (J4.72), and re-seating just
        // changed that. Once, after every bay, rather than per bay: the figure is a property
        // of the whole ship, so computing it while half the bays still hold the old era's
        // fighters would bank the wrong total.
        ship.getShuttles().restockDroneStore();
        return notes;
    }

    /**
     * Fit a casual carrier's ready racks (J4.62, J4.621), on a bay that seats no fighters.
     * <p>
     * J4.621: "the fighters on the carrier will determine what type of ready racks are on the
     * escort, and this will in turn determine the numbers of drones held in the racks." The player
     * says which model as a Commander's Option — by then the scenario year is known, so the choice
     * is a concrete fighter rather than a role, and the dropdown can only offer what the line
     * actually fields that year.
     * <p>
     * {@code chosen} is validated against the line here rather than trusted: a stored choice
     * outlives the scenario it was made for, and a Y183 escort must not end up with racks for a
     * fighter retired in Y177. An absent or unavailable choice falls back to the line's
     * {@code superiority} fighter, which is the role everything else already falls back to.
     * <p>
     * The racks go in the FIRST {@code n} undestroyed boxes. Which boxes is arbitrary and the
     * rules do not say; what matters is that a rack lives in a box, so it is destroyed with the
     * box (J4.831) exactly as a carrier's is.
     */
    static void equipFighterServiceFacilities(com.sfb.systemgroups.ShuttleBay bay, FighterComplement complement,
            int year, String chosen) {
        int boxes = bay.getFighterServiceFacilities();
        if (boxes <= 0)
            return;
        ShuttleCatalog.LineEra era = ShuttleCatalog.eraFor(complement.getLine(), year);
        if (era == null)
            return;                    // the line had not begun; no racks to fit

        String type = null;
        if (chosen != null && !chosen.isBlank() && era.roles().containsValue(chosen))
            type = chosen;
        if (type == null)
            type = era.typeFor(ShuttleCatalog.LineEra.STANDARD);
        if (type == null)
            return;

        int fitted = 0;
        for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces()) {
            if (fitted >= boxes)
                break;
            if (box.isDestroyed())
                continue;
            // Facilities, not just a rack: a plasma-F or photon fighter is rearmed from a
            // capacitor in the box (J4.73) and has no ready rack at all, so equipping only racks
            // left a Romulan escort supporting the assault fighter with nothing.
            if (box.fitFacilitiesFor(type))
                fitted++;
        }
    }

    @Override
    public String toString() {
        return line + counts;
    }
}
