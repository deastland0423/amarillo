package com.sfb.scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;

/**
 * Checks a battle force assembled for a patrol scenario against the construction rules (S8.0).
 * <p>
 * Patrol scenarios — "BPV battles", "buy your fleet", pick-up games — let each side spend an
 * agreed number of points on whatever it likes, within limits. This validates a proposed fleet
 * and reports everything wrong with it at once, so a builder can show the whole picture rather
 * than one error at a time.
 * <p>
 * The rules live here rather than in the server or the browser deliberately: a limit enforced
 * only in a form is not enforced at all, and a validation that lives beside the wire format
 * drifts from the rule it encodes.
 */
public final class FleetValidator {

    /** Escorts a carrier must have, by its size class — the "flexible group" rule (S8.315). */
    private static final Map<Integer, Integer> ESCORTS_REQUIRED = Map.of(2, 3, 3, 2, 4, 1);

    /** A carrier may always take one escort beyond the minimum (S8.315). */
    private static final int EXTRA_ESCORT_ALLOWED = 1;

    /** Ships per player that keeps a game moving; a guideline, not a limit (S8.17). */
    private static final int SHIPS_PER_PLAYER_GUIDELINE = 3;

    public enum Severity {
        /** Breaks a rule — the fleet is illegal. */
        ERROR,
        /** Allowed, but the rules advise against it. */
        ADVISORY
    }

    /** One thing wrong with a fleet, named by the rule it breaks. */
    public static final class Violation {
        public final String rule;      // e.g. "S8.21"
        public final Severity severity;
        public final String message;
        public final String shipName;  // the ship at fault, or null for the fleet as a whole

        public Violation(String rule, Severity severity, String message, String shipName) {
            this.rule = rule;
            this.severity = severity;
            this.message = message;
            this.shipName = shipName;
        }

        public boolean isError() {
            return severity == Severity.ERROR;
        }

        @Override
        public String toString() {
            return "(" + rule + ") " + message + (shipName != null ? " [" + shipName + "]" : "");
        }
    }

    /** A proposed battle force: the ships, which of them leads, and what was agreed. */
    public static final class Fleet {
        public final List<Ship> ships;
        public final String flagshipName;
        public final int budget;    // points agreed for this side (S8.11)
        public final int year;      // the scenario date (S8.13)

        public Fleet(List<Ship> ships, String flagshipName, int budget, int year) {
            this.ships = List.copyOf(ships);
            this.flagshipName = flagshipName;
            this.budget = budget;
            this.year = year;
        }

        public Ship flagship() {
            return ships.stream()
                    .filter(s -> s.getName() != null && s.getName().equals(flagshipName))
                    .findFirst().orElse(null);
        }
    }

    private FleetValidator() {
    }

    /** Everything wrong with this fleet, worst first. Empty means it is legal. */
    public static List<Violation> validate(Fleet fleet) {
        List<Violation> out = new ArrayList<>();
        if (fleet.ships.isEmpty()) {
            out.add(new Violation("S8.21", Severity.ERROR, "A battle force needs at least one ship", null));
            return out;
        }
        checkFlagshipAndCommandLimit(fleet, out);
        checkBudget(fleet, out);
        checkHeavyShips(fleet, out);
        checkServiceYear(fleet, out);
        checkCarrierGroups(fleet, out);
        checkShipCountGuideline(fleet, out);
        out.sort((a, b) -> Boolean.compare(b.isError(), a.isError()));
        return out;
    }

    /** True if nothing is actually broken; advisories do not make a fleet illegal. */
    public static boolean isLegal(List<Violation> violations) {
        return violations.stream().noneMatch(Violation::isError);
    }

    // -------------------------------------------------------------------------
    // Cost (S8.11)
    // -------------------------------------------------------------------------

    /**
     * What a ship costs to buy: its combat BPV plus the fighters in its bays, which are also
     * bought at combat value (S8.11).
     * <p>
     * Scouts are the exception (G24.35, cited by S8.11). Their chart entry reads A/B, where A
     * is "the economic value (what it costs to build)" and B the combat value — so a scout is
     * bought at its economic BPV whatever else is in the fleet. What the company changes is the
     * VICTORY value: alone it is scored at its combat BPV (G24.351), while alongside non-scouts
     * the combat value is ignored and the economic one serves for both (G24.352).
     */
    public static int costOf(Ship ship) {
        int hull = isScout(ship) ? ship.getEconomicBpv() : ship.getBpv();
        return hull + carriedFighterBpv(ship);
    }

    /** BPV of the fighters sitting in a ship's bays; they are bought with it (S8.11). */
    public static int carriedFighterBpv(Ship ship) {
        int total = 0;
        for (Shuttle s : ship.getShuttles().getAllShuttles())
            if (s instanceof Fighter)
                total += ((Fighter) s).getBpv();
        return total;
    }

    /** What the whole force costs to buy. */
    public static int fleetCost(List<Ship> ships) {
        return ships.stream().mapToInt(FleetValidator::costOf).sum();
    }

    private static void checkBudget(Fleet fleet, List<Violation> out) {
        int spent = fleetCost(fleet.ships);
        if (fleet.budget > 0 && spent > fleet.budget)
            out.add(new Violation("S8.11", Severity.ERROR,
                    "Fleet costs " + spent + " points, " + (spent - fleet.budget)
                            + " over the agreed " + fleet.budget, null));
    }

    // -------------------------------------------------------------------------
    // Command limits (S8.2)
    // -------------------------------------------------------------------------

    /**
     * A fleet must have a flagship, and may include ships equal to its command rating on top of
     * it (S8.21). One scout rides free — the "free scout slot" (S8.25) — and fighters never
     * count while their carrier is present (S8.23), which they are not here since they are not
     * ships in the list.
     */
    private static void checkFlagshipAndCommandLimit(Fleet fleet, List<Violation> out) {
        Ship flag = fleet.flagship();
        if (flag == null) {
            out.add(new Violation("S8.21", Severity.ERROR,
                    "No flagship chosen — every battle force needs one", null));
            return;
        }
        if (flag.getCommandRating() <= 0) {
            out.add(new Violation("S8.21", Severity.ERROR,
                    flag.getName() + " cannot lead a fleet — it has no command rating", flag.getName()));
            return;
        }

        // One scout does not count against the limit (S8.25).
        long scouts = fleet.ships.stream().filter(FleetValidator::isScout).count();
        int freeScouts = scouts > 0 ? 1 : 0;
        int counted = fleet.ships.size() - 1 - freeScouts;   // everything but the flagship
        int allowed = flag.getCommandRating();
        if (counted > allowed)
            out.add(new Violation("S8.21", Severity.ERROR,
                    flag.getName() + " has a command rating of " + allowed + " and can lead "
                            + allowed + " other ship" + (allowed == 1 ? "" : "s") + "; this fleet has "
                            + counted + (freeScouts > 0 ? " (after the free scout slot)" : ""),
                    flag.getName()));

        if (scouts > 1)
            out.add(new Violation("S8.35", Severity.ERROR,
                    "Only one scout may occupy the free scout slot; this fleet has " + scouts, null));
    }

    // -------------------------------------------------------------------------
    // Heavy ships (S8.33) and the scenario date (S8.13)
    // -------------------------------------------------------------------------

    private static void checkHeavyShips(Fleet fleet, List<Violation> out) {
        List<String> heavies = fleet.ships.stream()
                .filter(s -> s.getSizeClass() == 2)
                .map(Ship::getName)
                .toList();
        if (heavies.size() > 1)
            out.add(new Violation("S8.33", Severity.ERROR,
                    "No more than one size class 2 ship in a fleet; this has "
                            + heavies.size() + " (" + String.join(", ", heavies) + ")", null));
    }

    private static void checkServiceYear(Fleet fleet, List<Violation> out) {
        if (fleet.year <= 0)
            return;
        for (Ship s : fleet.ships)
            if (s.getYearInService() > fleet.year)
                out.add(new Violation("S8.131", Severity.ERROR,
                        s.getName() + " does not enter service until Y" + s.getYearInService()
                                + ", after the scenario date of Y" + fleet.year, s.getName()));
    }

    private static void checkShipCountGuideline(Fleet fleet, List<Violation> out) {
        if (fleet.ships.size() > SHIPS_PER_PLAYER_GUIDELINE)
            out.add(new Violation("S8.17", Severity.ADVISORY,
                    "More than " + SHIPS_PER_PLAYER_GUIDELINE + " ships per player slows a game down"
                            + " — this fleet has " + fleet.ships.size(), null));
    }

    // -------------------------------------------------------------------------
    // Carrier groups (S8.31/S8.315)
    // -------------------------------------------------------------------------

    /**
     * The flexible group rule (S8.315): a size class 2 carrier must have three escorts and may
     * have four, class 3 two and may have three, class 4 one and may have two. At least one
     * escort must be size class 4, and escorts must be of the same empire and available in the
     * scenario year. Escorts serve one carrier each — a second carrier needs its own.
     * <p>
     * And the other direction (S8.311): an escort in a fleet with no carrier to serve is not a
     * legal purchase.
     */
    private static void checkCarrierGroups(Fleet fleet, List<Violation> out) {
        List<Ship> carriers = fleet.ships.stream().filter(Ship::isTrueCarrier).toList();
        List<Ship> escorts = new ArrayList<>(fleet.ships.stream().filter(Ship::isEscort).toList());

        if (carriers.isEmpty()) {
            for (Ship e : escorts)
                out.add(new Violation("S8.311", Severity.ERROR,
                        e.getName() + " is a carrier escort and cannot be fielded without a carrier",
                        e.getName()));
            return;
        }

        // Assign escorts to carriers, largest carrier first: it has the most to satisfy.
        List<Ship> byNeed = carriers.stream()
                .sorted((a, b) -> Integer.compare(a.getSizeClass(), b.getSizeClass()))
                .toList();
        Map<String, List<Ship>> assigned = new LinkedHashMap<>();
        for (Ship carrier : byNeed) {
            int required = ESCORTS_REQUIRED.getOrDefault(carrier.getSizeClass(), 1);
            int allowed = required + EXTRA_ESCORT_ALLOWED;
            List<Ship> mine = new ArrayList<>();

            // Same empire (S8.315) and in service by the scenario date.
            List<Ship> eligible = escorts.stream()
                    .filter(e -> e.getFaction() == carrier.getFaction())
                    .filter(e -> fleet.year <= 0 || e.getYearInService() <= fleet.year)
                    .toList();

            // Take a size class 4 first — at least one is required (S8.315).
            eligible.stream().filter(e -> e.getSizeClass() == 4).findFirst().ifPresent(mine::add);
            for (Ship e : eligible) {
                if (mine.size() >= allowed) break;
                if (!mine.contains(e)) mine.add(e);
            }
            escorts.removeAll(mine);
            assigned.put(carrier.getName(), mine);

            if (mine.size() < required)
                out.add(new Violation("S8.315", Severity.ERROR,
                        carrier.getName() + " is a size class " + carrier.getSizeClass()
                                + " carrier and needs " + required + " escort"
                                + (required == 1 ? "" : "s") + " of its own empire; it has "
                                + mine.size(), carrier.getName()));
            else if (mine.stream().noneMatch(e -> e.getSizeClass() == 4))
                out.add(new Violation("S8.315", Severity.ERROR,
                        carrier.getName() + "'s escorts must include at least one of size class 4",
                        carrier.getName()));
        }

        // Anything left over is serving nobody (S8.311).
        for (Ship e : escorts)
            out.add(new Violation("S8.311", Severity.ERROR,
                    e.getName() + " is a carrier escort with no carrier to serve", e.getName()));
    }

    // -------------------------------------------------------------------------

    /** A ship is a scout if it carries scout channels (G24.0), whatever its hull code says. */
    public static boolean isScout(Ship ship) {
        return !ship.getScoutChannels().isEmpty();
    }
}
