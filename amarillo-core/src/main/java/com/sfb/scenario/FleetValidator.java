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

    /** Consorts a leader must have before another leader may join it (S8.36). */
    private static final int CONSORTS_PER_LEADER = 2;

    /** Share of a ship's combat BPV it may spend on Commander's Option items (S3.2). */
    private static final int COI_PERCENT = 20;

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
        checkFlagshipNationality(fleet, out);
        checkBudget(fleet, out);
        checkCommanderOptions(fleet, out);
        checkHeavyShips(fleet, out);
        checkHeavyShipCompany(fleet, out);
        checkBattlecruisers(fleet, out);
        checkServiceYear(fleet, out);
        checkCarrierGroups(fleet, out);
        checkLeaders(fleet, out);
        checkShipCountGuideline(fleet, out);
        checkDistinctNames(fleet, out);
        out.sort((a, b) -> Boolean.compare(b.isError(), a.isError()));
        return out;
    }

    /**
     * Every ship needs its own name. Not a matter of taste: a name is how the whole game
     * addresses a unit - fire orders, lock-ons, tractor targets, the map DTO, each
     * player's redacted view - so a duplicate makes the second ship unreachable and breaks
     * the battle before it starts. No rule number; this is a consequence of how the game
     * is played rather than a passage in the book.
     */
    private static void checkDistinctNames(Fleet fleet, List<Violation> out) {
        java.util.Map<String, Integer> seen = new java.util.LinkedHashMap<>();
        for (Ship s : fleet.ships) {
            String name = s.getName() == null ? "" : s.getName().trim();
            if (name.isEmpty())
                continue;   // an unnamed ship is given one at build time
            seen.merge(name.toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
        }
        for (java.util.Map.Entry<String, Integer> e : seen.entrySet()) {
            if (e.getValue() > 1)
                out.add(new Violation(null, Severity.ERROR,
                        e.getValue() + " ships are called \"" + e.getKey() + "\" — every ship"
                        + " needs its own name, or orders cannot tell them apart", null));
        }
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

    /**
     * The most a ship may spend on Commander's Option items: a share of its combat BPV
     * (S3.2). Computed on combat BPV even for a scout, whose economic value is what it costs
     * to build rather than what it brings to the fight.
     */
    public static double coiAllowance(Ship ship) {
        return CoiLoadout.budget(ship.getBpv(), COI_PERCENT);
    }

    /**
     * Everything a fleet spends: hulls, the fighters they carry, and Commander's Options.
     * <p>
     * S8.11 and S8.12 both describe purchases and neither grants a separate pool, so option
     * points come out of the agreed total rather than on top of it. A fleet that spends every
     * point on hulls has nothing left for extra drones.
     */
    public static double totalCost(List<Ship> ships) {
        return fleetCost(ships) + ships.stream().mapToDouble(Ship::getCoiSpend).sum();
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
        double spent = totalCost(fleet.ships);
        if (fleet.budget > 0 && spent > fleet.budget) {
            double coi = spent - fleetCost(fleet.ships);
            String options = coi > 0 ? " (" + trim(coi) + " of it on Commander's Options)" : "";
            out.add(new Violation("S8.11", Severity.ERROR,
                    "Fleet costs " + trim(spent) + " points" + options + ", " + trim(spent - fleet.budget)
                            + " over the agreed " + fleet.budget, null));
        }
    }

    /**
     * No ship may spend more than its share on Commander's Options (S3.2). Enforced here as
     * well as when a loadout is applied, because a fleet can arrive from anywhere and a limit
     * checked only where it is spent is a limit that travels badly.
     */
    private static void checkCommanderOptions(Fleet fleet, List<Violation> out) {
        for (Ship ship : fleet.ships) {
            double allowance = coiAllowance(ship);
            if (ship.getCoiSpend() > allowance)
                out.add(new Violation("S3.2", Severity.ERROR,
                        ship.getName() + " spends " + trim(ship.getCoiSpend())
                                + " on Commander's Options, over the " + trim(allowance)
                                + " allowed by its " + ship.getBpv() + "-point value",
                        ship.getName()));
        }
    }

    /** Whole numbers read as whole numbers; halves and quarters keep their fraction. */
    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
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

    /**
     * In a force drawn from several allied empires, the flagship comes from the one providing
     * the most ships (S8.61) — counted by hulls, so "a frigate carries the same weight as a
     * battlecruiser". Where two empires are level, either may provide it (S8.612).
     * <p>
     * Not modelled: S8.61 counts ship equivalents too, twelve fighters or six heavy
     * fighters/PFs/interceptors standing in for a hull, which needs the fighter limits of
     * S8.32 before it can be counted honestly.
     */
    private static void checkFlagshipNationality(Fleet fleet, List<Violation> out) {
        Ship flagship = fleet.flagship();
        if (flagship == null)
            return;   // a missing flagship is S8.21's complaint, not this one

        Map<Object, Integer> byEmpire = new LinkedHashMap<>();
        for (Ship s : fleet.ships)
            byEmpire.merge(s.getFaction(), 1, Integer::sum);
        if (byEmpire.size() < 2)
            return;   // one empire; nothing to weigh

        int mine = byEmpire.getOrDefault(flagship.getFaction(), 0);
        int most = byEmpire.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        if (mine < most) {
            String leaders = byEmpire.entrySet().stream()
                    .filter(e -> e.getValue() == most)
                    .map(e -> String.valueOf(e.getKey()))
                    .reduce((a, b) -> a + " or " + b).orElse("");
            out.add(new Violation("S8.61", Severity.ERROR,
                    flagship.getName() + " cannot lead a mixed force: " + flagship.getFaction()
                            + " provides " + mine + " of the ships where " + leaders + " provides "
                            + most + "; the flagship comes from the empire providing the most",
                    flagship.getName()));
        }
    }

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

    /**
     * A size class 2 ship does not go out alone: "most size class 2 ships never appear with less
     * than three other ships, two of them from the same empire" (S8.331).
     * <p>
     * The "most" is S8.334, which lets DNLs and light battleships like the Klingon B9 raid on
     * their own. Nothing in the data is one yet, and when one arrives it wants a flag of its
     * own - probably isFast, since a DNL is a fast dreadnought - because size class alone cannot
     * tell a raider from a dreadnought.
     */
    private static void checkHeavyShipCompany(Fleet fleet, List<Violation> out) {
        for (Ship heavy : fleet.ships) {
            if (heavy.getSizeClass() != 2)
                continue;
            int others = fleet.ships.size() - 1;
            long sameEmpire = fleet.ships.stream()
                    .filter(s -> s != heavy)
                    .filter(s -> s.getFaction() == heavy.getFaction())
                    .count();

            if (others < 3)
                out.add(new Violation("S8.331", Severity.ERROR,
                        heavy.getName() + " is a size class 2 ship and does not sail with fewer"
                                + " than three other ships; this fleet has " + others,
                        heavy.getName()));
            else if (sameEmpire < 2)
                out.add(new Violation("S8.331", Severity.ERROR,
                        heavy.getName() + " needs two of its consorts to fly its own flag; only "
                                + sameEmpire + " of this fleet " + (sameEmpire == 1 ? "is" : "are")
                                + " " + heavy.getFaction(), heavy.getName()));
        }
    }

    /**
     * One heavy battlecruiser to a fleet (S8.333). It differs from the size class 2 limit in two
     * ways: a BCH needs no squadron of followers, and it may be taken in addition to the one
     * size class 2 ship rather than instead of it.
     */
    private static void checkBattlecruisers(Fleet fleet, List<Violation> out) {
        List<String> bchs = fleet.ships.stream()
                .filter(Ship::isBCH)
                .map(Ship::getName)
                .toList();
        if (bchs.size() > 1)
            out.add(new Violation("S8.333", Severity.ERROR,
                    "No more than one BCH in a fleet; this has " + bchs.size()
                            + " (" + String.join(", ", bchs) + ")", null));
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
    // Leader variants (S8.36)
    // -------------------------------------------------------------------------

    /**
     * Leader variants need ships to lead (S8.36). Which one may go without is not a choice:
     * "no leader ship can be included unless all larger leaders have their supporting ships"
     * (S8.362), so the smallest leader rides free and every larger one must show two non-leader
     * consorts of its own line. Among leaders of one line, all but one must be supported
     * (S8.361) — two D5Ls need two D5s between them, not four.
     * <p>
     * The flagship leads the force by definition and is exempt (S8.363).
     * <p>
     * Consorts are of the leader's own line, which the rule calls the same basic hull type and
     * illustrates with the pair we have: a D7C is accompanied by "two other D7/D6 combat ships"
     * — both CA — while a D5L needs D5s. They must fly the leader's own flag, on the same
     * reasoning as S8.331, and are claimed exclusively, so three D5Ls need four D5s.
     * <p>
     * Size is read from movement cost, which is what separates a D7C from a D5L when both are
     * size class 3. Equal cost is equal size, which suits S8.361's note that CLs and CWs mix
     * freely. Not yet modelled: S8.361 also holds heavy war cruisers apart from war cruisers,
     * and heavy destroyers from destroyers, which would need lines of their own.
     */
    private static void checkLeaders(Fleet fleet, List<Violation> out) {
        List<Ship> leaders = fleet.ships.stream()
                .filter(Ship::isLeader)
                .filter(s -> s != fleet.flagship())
                .toList();
        if (leaders.size() <= 1)
            return;   // one leader is always allowed; the flagship never counts

        double smallest = leaders.stream()
                .mapToDouble(s -> s.getPerformanceData().getMovementCost())
                .min().orElse(0);

        // Group by line: every line carries one movement cost, so a group is one size.
        Map<String, List<Ship>> byLine = new LinkedHashMap<>();
        for (Ship leader : leaders)
            byLine.computeIfAbsent(consortPool(leader), k -> new ArrayList<>()).add(leader);

        Map<String, Integer> demand = new LinkedHashMap<>();
        for (Map.Entry<String, List<Ship>> group : byLine.entrySet()) {
            double cost = group.getValue().get(0).getPerformanceData().getMovementCost();
            // Larger than the smallest leader: every one of them must be supported (S8.362).
            // The smallest: all but one (S8.361).
            int needing = cost > smallest ? group.getValue().size() : group.getValue().size() - 1;
            if (needing > 0)
                demand.put(group.getKey(), needing * CONSORTS_PER_LEADER);
        }

        List<String> shortfalls = new ArrayList<>();
        for (Map.Entry<String, Integer> e : demand.entrySet()) {
            long available = fleet.ships.stream()
                    .filter(s -> !s.isLeader())
                    .filter(s -> s.getLine() != null)
                    .filter(s -> consortPool(s).equals(e.getKey()))
                    .count();
            if (available < e.getValue())
                shortfalls.add("the " + e.getKey().replace("|", " ") + " line has " + available
                        + " of the " + e.getValue() + " needed");
        }

        if (!shortfalls.isEmpty()) {
            List<String> names = leaders.stream().map(Ship::getName).toList();
            out.add(new Violation("S8.36", Severity.ERROR,
                    "Every leader but the smallest needs two non-leader consorts of its own line;"
                            + " this fleet has " + leaders.size() + " (" + String.join(", ", names)
                            + ") and " + String.join("; ", shortfalls), null));
        }
    }

    /** Consorts serve their own empire and their own line, so both key the pool. */
    private static String consortPool(Ship ship) {
        return ship.getFaction() + "|" + ship.getLine();
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
        return ship.isScout();
    }
}
