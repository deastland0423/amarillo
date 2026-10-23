package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.scenario.FleetValidator.Fleet;
import com.sfb.scenario.FleetValidator.Violation;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Fleet construction for patrol scenarios (S8.0). Built from synthetic ships rather than the
 * ship library, so these pin the rules rather than the state of the data files.
 */
public class FleetValidatorTest {

    /** A ship with only the properties the construction rules read. */
    private Ship ship(String name, int bpv, int sizeClass, int commandRating, int year) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Federation);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", bpv);
        v.put("sizeclass", sizeClass);
        v.put("serviceyear", year);
        if (commandRating > 0)
            v.put("commandrating", commandRating);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    private Ship ship(String name, int bpv, int sizeClass, int commandRating) {
        return ship(name, bpv, sizeClass, commandRating, 100);
    }

    private Ship escort(String name, int sizeClass) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Federation);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 60);
        v.put("sizeclass", sizeClass);
        v.put("serviceyear", 100);
        v.put("isescort", true);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    private Ship carrier(String name, int sizeClass) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Federation);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 150);
        v.put("sizeclass", sizeClass);
        v.put("serviceyear", 100);
        v.put("commandrating", 8);
        v.put("requiresescort", true);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    private List<String> rulesBroken(List<Violation> vs) {
        List<String> out = new ArrayList<>();
        for (Violation v : vs)
            if (v.isError())
                out.add(v.rule);
        return out;
    }

    // ---- S8.21 command limits ----

    @Test
    public void aFleetWithinItsFlagshipsCommandRatingIsLegal() {
        Ship flag = ship("Flagship", 120, 3, 3);
        Fleet fleet = new Fleet(List.of(flag, ship("A", 80, 4, 0), ship("B", 80, 4, 0)),
                "Flagship", 500, 175);

        assertTrue(FleetValidator.validate(fleet).toString(),
                FleetValidator.isLegal(FleetValidator.validate(fleet)));
    }

    @Test
    public void tooManyShipsForTheCommandRatingIsRefused() {
        Ship flag = ship("Flagship", 120, 3, 1);   // may lead one other ship
        Fleet fleet = new Fleet(List.of(flag, ship("A", 80, 4, 0), ship("B", 80, 4, 0)),
                "Flagship", 500, 175);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.21"));
    }

    @Test
    public void aShipThatCannotCommandIsNotALegalFlagship() {
        Ship freighter = ship("Freighter", 18, 4, 0);   // commandRating 0 — civilian
        Fleet fleet = new Fleet(List.of(freighter), "Freighter", 500, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.21"));
        assertTrue(vs.toString(), vs.get(0).message.contains("no command rating"));
    }

    @Test
    public void aFleetMustNameAFlagship() {
        Fleet fleet = new Fleet(List.of(ship("A", 80, 4, 5)), "Nobody", 500, 175);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.21"));
    }

    // ---- S8.11 budget, and the scout exception ----

    @Test
    public void aFleetOverBudgetIsRefused() {
        Fleet fleet = new Fleet(List.of(ship("Flagship", 400, 3, 4), ship("A", 200, 4, 0)),
                "Flagship", 500, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.11"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> v.message.contains("100 over")));
    }

    /**
     * G24.35 via S8.11: a scout's chart entry is economic/combat, and the economic value is
     * "what it costs to build" — so it is bought at that price whatever else is in the fleet.
     * The company it keeps changes its VICTORY value (G24.351/G24.352), not its price.
     */
    @Test
    public void aScoutIsAlwaysBoughtAtItsEconomicValue() {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Federation);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 100);
        v.put("epv", 120);
        v.put("sizeclass", 4);
        v.put("serviceyear", 100);
        Ship scout = new Ship();
        scout.init(v);
        scout.setName("Scout");
        com.sfb.weapons.ScoutChannel c = new com.sfb.weapons.ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        scout.getWeapons().addWeapon(c);

        assertTrue(FleetValidator.isScout(scout));
        assertEquals("bought at its economic value", 120, FleetValidator.costOf(scout), 1e-9);
        assertEquals("even as the only ship in the force",
                120, FleetValidator.fleetCost(List.of(scout)), 1e-9);

        Ship consort = ship("Consort", 100, 4, 5);
        assertEquals("fleet total uses the economic value",
                220, FleetValidator.fleetCost(List.of(scout, consort)), 1e-9);
    }

    // ---- S8.6 mixed allied forces ----

    private Ship ofEmpire(String name, Faction faction, int commandRating) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", faction);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 100);
        v.put("sizeclass", 4);
        v.put("serviceyear", 100);
        if (commandRating > 0)
            v.put("commandrating", commandRating);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    /** S8.61: the flagship comes from the empire providing the most ships. */
    @Test
    public void theFlagshipComesFromTheLargestContingent() {
        Fleet wrong = new Fleet(List.of(
                ofEmpire("Lyran lead", Faction.Lyran, 9),
                ofEmpire("Klingon A", Faction.Klingon, 0),
                ofEmpire("Klingon B", Faction.Klingon, 0)), "Lyran lead", 900, 175);

        List<Violation> vs = FleetValidator.validate(wrong);
        assertTrue(rulesBroken(vs).contains("S8.61"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> v.message.contains("Klingon")));
    }

    @Test
    public void theLargestContingentMayLead() {
        Fleet ok = new Fleet(List.of(
                ofEmpire("Klingon lead", Faction.Klingon, 9),
                ofEmpire("Klingon B", Faction.Klingon, 0),
                ofEmpire("Lyran ally", Faction.Lyran, 0)), "Klingon lead", 900, 175);

        assertFalse(FleetValidator.validate(ok).toString(),
                rulesBroken(FleetValidator.validate(ok)).contains("S8.61"));
    }

    /** S8.612: level contingents, so either empire may provide the flagship. */
    @Test
    public void anEvenSplitMayBeLedByEither() {
        Fleet fleet = new Fleet(List.of(
                ofEmpire("Lyran lead", Faction.Lyran, 9),
                ofEmpire("Lyran B", Faction.Lyran, 0),
                ofEmpire("Klingon A", Faction.Klingon, 0),
                ofEmpire("Klingon B", Faction.Klingon, 0)), "Lyran lead", 900, 175);

        assertFalse(rulesBroken(FleetValidator.validate(fleet)).contains("S8.61"));
    }

    /** A single-empire force never has a nationality question to answer. */
    @Test
    public void aSingleEmpireForceIsUnaffected() {
        Fleet fleet = new Fleet(List.of(
                ofEmpire("Lead", Faction.Klingon, 9),
                ofEmpire("Other", Faction.Klingon, 0)), "Lead", 900, 175);

        assertFalse(rulesBroken(FleetValidator.validate(fleet)).contains("S8.61"));
    }

    // ---- S8.12 / S3.2 Commander's Options ----

    /**
     * S8.11 and S8.12 both describe purchases and neither grants a separate pool, so option
     * points come out of the agreed total. A fleet that spends every point on hulls has
     * nothing left for extra drones.
     */
    @Test
    public void commanderOptionsComeOutOfTheFleetBudget() {
        Ship flag = ship("Flagship", 200, 3, 4);
        Ship consort = ship("Consort", 200, 4, 0);

        Fleet exact = new Fleet(List.of(flag, consort), "Flagship", 400, 175);
        assertTrue("400 points of hulls fits a 400 point budget",
                FleetValidator.isLegal(FleetValidator.validate(exact)));

        flag.setCoiSpend(10);
        List<Violation> vs = FleetValidator.validate(exact);
        assertTrue("the same fleet with options bought is over",
                rulesBroken(vs).contains("S8.11"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> v.message.contains("Commander's Options")));
    }

    @Test
    public void theFleetCostIncludesWhatWasSpentOnOptions() {
        Ship flag = ship("Flagship", 100, 3, 4);
        flag.setCoiSpend(12.5);

        assertEquals(100, FleetValidator.fleetCost(List.of(flag)), 1e-9);
        assertEquals(112.5, FleetValidator.totalCost(List.of(flag)), 0.001);
    }

    /** S3.2: a ship's own option spending is capped at a share of its combat BPV. */
    @Test
    public void aShipCannotOverspendItsOwnOptionAllowance() {
        Ship flag = ship("Flagship", 100, 3, 4);
        assertEquals("20 percent of 100", 20.0, FleetValidator.coiAllowance(flag), 0.001);

        flag.setCoiSpend(20);
        Fleet atTheLimit = new Fleet(List.of(flag), "Flagship", 900, 175);
        assertFalse(rulesBroken(FleetValidator.validate(atTheLimit)).contains("S3.2"));

        flag.setCoiSpend(20.5);
        List<Violation> vs = FleetValidator.validate(new Fleet(List.of(flag), "Flagship", 900, 175));
        assertTrue(rulesBroken(vs).contains("S3.2"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> "Flagship".equals(v.shipName)));
    }

    /**
     * The allowance is a share of combat BPV even for a scout, whose economic value is what it
     * costs to build rather than what it brings to the fight.
     */
    @Test
    public void aScoutsOptionAllowanceUsesItsCombatValue() {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Federation);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 100);
        v.put("epv", 120);
        v.put("sizeclass", 4);
        v.put("serviceyear", 100);
        Ship scout = new Ship();
        scout.init(v);
        scout.setName("Scout");
        com.sfb.weapons.ScoutChannel c = new com.sfb.weapons.ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        scout.getWeapons().addWeapon(c);

        assertEquals("bought at 120", 120, FleetValidator.costOf(scout), 1e-9);
        assertEquals("but options are 20 percent of the 100 it fights at",
                20.0, FleetValidator.coiAllowance(scout), 0.001);
    }

    // ---- S8.33 heavy ships ----

    @Test
    public void onlyOneSizeClassTwoShipIsAllowed() {
        Fleet fleet = new Fleet(List.of(ship("DN", 200, 2, 9), ship("BB", 200, 2, 0)),
                "DN", 900, 175);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.33"));
    }

    // ---- S8.131 scenario date ----

    @Test
    public void aShipNotYetInServiceIsRefused() {
        Fleet fleet = new Fleet(List.of(ship("Flagship", 120, 3, 4), ship("Prototype", 90, 4, 0, 180)),
                "Flagship", 500, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.131"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> "Prototype".equals(v.shipName)));
    }

    // ---- S8.315 carrier groups ----

    @Test
    public void aSizeClassThreeCarrierNeedsTwoEscortsOneOfThemClassFour() {
        Ship cv = carrier("Carrier", 3);
        Fleet ok = new Fleet(List.of(cv, escort("E1", 4), escort("E2", 3)), "Carrier", 900, 175);
        assertTrue(FleetValidator.validate(ok).toString(),
                FleetValidator.isLegal(FleetValidator.validate(ok)));

        Fleet tooFew = new Fleet(List.of(cv, escort("E1", 4)), "Carrier", 900, 175);
        assertTrue(rulesBroken(FleetValidator.validate(tooFew)).contains("S8.315"));
    }

    @Test
    public void aSizeClassTwoCarrierNeedsThree() {
        Ship cv = carrier("BigCarrier", 2);
        Fleet fleet = new Fleet(List.of(cv, escort("E1", 4), escort("E2", 3)), "BigCarrier", 900, 175);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.315"));
    }

    @Test
    public void escortsMustIncludeOneOfSizeClassFour() {
        Ship cv = carrier("Carrier", 4);              // needs one escort
        Fleet fleet = new Fleet(List.of(cv, escort("E1", 3)), "Carrier", 900, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.315"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> v.message.contains("size class 4")));
    }

    /** S8.311: an escort with no carrier to serve is not a legal purchase. */
    @Test
    public void anEscortWithoutACarrierIsRefused() {
        Fleet fleet = new Fleet(List.of(ship("Flagship", 120, 3, 4), escort("Lonely", 4)),
                "Flagship", 500, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.311"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> "Lonely".equals(v.shipName)));
    }

    /** Escorts serve one carrier each — two carriers cannot share a single group. */
    @Test
    public void twoCarriersNeedTheirOwnEscorts() {
        Fleet fleet = new Fleet(
                List.of(carrier("CV1", 4), carrier("CV2", 4), escort("E1", 4)),
                "CV1", 1200, 175);

        assertTrue("one escort cannot satisfy both",
                rulesBroken(FleetValidator.validate(fleet)).contains("S8.315"));
    }

    // ---- S8.17 guideline ----

    @Test
    public void aLargeFleetIsAdvisedAgainstButStillLegal() {
        List<Ship> ships = new ArrayList<>();
        ships.add(ship("Flagship", 100, 3, 9));
        for (int i = 1; i <= 4; i++)
            ships.add(ship("Escort" + i, 60, 4, 0));
        Fleet fleet = new Fleet(ships, "Flagship", 900, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue("still legal", FleetValidator.isLegal(vs));
        assertTrue("but advised against",
                vs.stream().anyMatch(v -> v.rule.equals("S8.17") && !v.isError()));
    }

    /** Errors come first, so a builder can lead with what actually blocks the fleet. */
    @Test
    public void errorsSortAheadOfAdvisories() {
        List<Ship> ships = new ArrayList<>();
        ships.add(ship("Flagship", 100, 3, 1));
        for (int i = 1; i <= 4; i++)
            ships.add(ship("Extra" + i, 60, 4, 0));
        List<Violation> vs = FleetValidator.validate(new Fleet(ships, "Flagship", 900, 175));

        assertTrue(vs.size() > 1);
        assertTrue("first is an error", vs.get(0).isError());
        assertFalse("last is not", vs.get(vs.size() - 1).isError());
    }

    // ---- S8.36 leader variants ----

    /** Movement cost per line, as data/shiplines/shiplines.json publishes it. */
    private static final Map<String, Double> LINE_COST = Map.of(
            "DN", 1.5, "CA", 1.0, "CL", 0.6666667, "CW", 0.6666667,
            "DD", 0.5, "FF", 0.3333333);

    private Ship lineShip(String name, String line, int sizeClass, boolean leader) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Klingon);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 100);
        v.put("sizeclass", sizeClass);
        v.put("serviceyear", 100);
        v.put("commandrating", 9);
        v.put("line", line);
        // S8.362 ranks leaders by size, and movement cost is what separates a D7C from a D5L
        // when both are size class 3 — so a leader test without it proves nothing.
        v.put("movecost", LINE_COST.get(line));
        if (leader)
            v.put("isleader", true);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    @Test
    public void oneLeaderNeedsNoConsorts() {
        Fleet fleet = new Fleet(List.of(lineShip("Flag", "CA", 3, false),
                lineShip("D7C", "CA", 3, true)), "Flag", 900, 175);

        assertFalse(rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    @Test
    public void aSecondLeaderNeedsTheFirstToHaveTwoConsorts() {
        // Flagship is a war cruiser, so it is no use as a CA consort.
        Fleet tooFew = new Fleet(List.of(lineShip("Flag", "CW", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("D7L", "CA", 3, true),
                lineShip("D7", "CA", 3, false)), "Flag", 900, 175);
        assertTrue("one CA consort cannot serve the leader that has to pay",
                rulesBroken(FleetValidator.validate(tooFew)).contains("S8.36"));

        Fleet enough = new Fleet(List.of(lineShip("Flag", "CW", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("D7L", "CA", 3, true),
                lineShip("D7", "CA", 3, false), lineShip("D6", "CA", 3, false)), "Flag", 900, 175);
        assertFalse(FleetValidator.validate(enough).toString(),
                rulesBroken(FleetValidator.validate(enough)).contains("S8.36"));
    }

    /**
     * The whole point of keying on line rather than size class: every ship here is size class
     * 3, so the old "two consorts of the same size class" reading would pass this fleet. The
     * CA leaders have no CA to lead, and war cruisers are no substitute.
     */
    @Test
    public void consortsMustBeOfTheLeadersOwnLineNotMerelyItsSizeClass() {
        Fleet fleet = new Fleet(List.of(lineShip("Flag", "CW", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("D7L", "CA", 3, true),
                lineShip("D5", "CW", 3, false), lineShip("D5B", "CW", 3, false)), "Flag", 900, 175);

        assertTrue("three size class 3 consorts, none of them CA",
                rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    /**
     * S8.362: the smaller leader rides free and the larger must be supported — not whichever
     * way round makes the fleet legal. Two F5s serve the F5C perfectly well, but it is the D7C
     * that has to be accompanied, and no CA is present.
     */
    @Test
    public void theLargerLeaderIsTheOneThatMustBeSupported() {
        Fleet fleet = new Fleet(List.of(lineShip("Flag", "CW", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("F5C", "DD", 4, true),
                lineShip("F5", "DD", 4, false), lineShip("F5B", "DD", 4, false)), "Flag", 900, 175);

        assertTrue("the F5C rides free; the D7C needs CAs it does not have",
                rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    /** The same fleet with the consorts on the right line: the D7C is served, the F5C is free. */
    @Test
    public void theSmallestLeaderRidesFree() {
        Fleet fleet = new Fleet(List.of(lineShip("Flag", "CW", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("F5C", "DD", 4, true),
                lineShip("D7", "CA", 3, false), lineShip("D6", "CA", 3, false)), "Flag", 900, 175);

        assertFalse(FleetValidator.validate(fleet).toString(),
                rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    /**
     * S8.361, with the rulebook's own arithmetic: "you may have one D5L if you wish, but if you
     * want two of them, there must be two other D5s in the fleet". Two, not four — only the
     * earlier leader must be accompanied.
     */
    @Test
    public void twoLeadersOfOneLineNeedTwoConsortsBetweenThem() {
        Fleet two = new Fleet(List.of(lineShip("Flag", "CA", 3, false),
                lineShip("D5L", "CW", 3, true), lineShip("D5L2", "CW", 3, true),
                lineShip("D5", "CW", 3, false), lineShip("D5B", "CW", 3, false)), "Flag", 900, 175);
        assertFalse(FleetValidator.validate(two).toString(),
                rulesBroken(FleetValidator.validate(two)).contains("S8.36"));

        Fleet one = new Fleet(List.of(lineShip("Flag", "CA", 3, false),
                lineShip("D5L", "CW", 3, true), lineShip("D5L2", "CW", 3, true),
                lineShip("D5", "CW", 3, false)), "Flag", 900, 175);
        assertTrue("one D5 cannot support the D5L that has to pay",
                rulesBroken(FleetValidator.validate(one)).contains("S8.36"));
    }

    /** Three of a line claim their consorts exclusively: two must be supported, so four D5s. */
    @Test
    public void threeLeadersOfOneLineNeedFourConsorts() {
        List<Ship> ships = new ArrayList<>();
        ships.add(lineShip("Flag", "CA", 3, false));
        ships.add(lineShip("D5L", "CW", 3, true));
        ships.add(lineShip("D5L2", "CW", 3, true));
        ships.add(lineShip("D5L3", "CW", 3, true));
        for (int i = 1; i <= 3; i++)
            ships.add(lineShip("D5#" + i, "CW", 3, false));
        assertTrue("three D5s is one short",
                rulesBroken(FleetValidator.validate(new Fleet(ships, "Flag", 1500, 175))).contains("S8.36"));

        ships.add(lineShip("D5#4", "CW", 3, false));
        assertFalse(rulesBroken(FleetValidator.validate(new Fleet(ships, "Flag", 1500, 175))).contains("S8.36"));
    }

    /** S8.363: the flagship leads by definition and never counts against the allowance. */
    @Test
    public void theFlagshipIsExempt() {
        Fleet fleet = new Fleet(List.of(lineShip("D7C", "CA", 3, true),
                lineShip("D7L", "CA", 3, true)), "D7C", 900, 175);

        assertFalse("the flagship does not count, so D7L is the one free leader",
                rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    /** A leader cannot be its own consort, nor serve another leader. */
    @Test
    public void leadersDoNotCountAsConsorts() {
        Fleet fleet = new Fleet(List.of(lineShip("Flag", "CA", 3, false),
                lineShip("D7C", "CA", 3, true), lineShip("D7L", "CA", 3, true),
                lineShip("D7L2", "CA", 3, true)), "Flag", 900, 175);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.36"));
    }

    // ---- S8.333 heavy battlecruisers ----

    private Ship bch(String name) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Klingon);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 180);
        v.put("sizeclass", 3);
        v.put("serviceyear", 177);
        v.put("commandrating", 10);
        v.put("isbch", true);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    @Test
    public void onlyOneBattlecruiserIsAllowed() {
        Fleet fleet = new Fleet(List.of(bch("C7"), bch("C7 #2")), "C7", 900, 180);

        assertTrue(rulesBroken(FleetValidator.validate(fleet)).contains("S8.333"));
    }

    /** S8.333: a BCH may be taken alongside the one size class 2 ship, not instead of it. */
    @Test
    public void aBattlecruiserMayAccompanyTheSizeClassTwoShip() {
        Fleet fleet = new Fleet(List.of(ship("DN", 200, 2, 9), bch("C7")), "DN", 900, 180);

        List<String> broken = rulesBroken(FleetValidator.validate(fleet));
        assertFalse("one of each is fine", broken.contains("S8.33"));
        assertFalse(broken.contains("S8.333"));
    }

    // ---- S8.331 the company a size class 2 ship keeps ----

    /** An allied hull, for fleets drawn from more than one empire. */
    private Ship allied(String name, int sizeClass) {
        Map<String, Object> v = new HashMap<>();
        v.put("faction", Faction.Klingon);
        v.put("turnmode", com.sfb.properties.TurnMode.D);
        v.put("bpv", 80);
        v.put("sizeclass", sizeClass);
        v.put("serviceyear", 100);
        Ship s = new Ship();
        s.init(v);
        s.setName(name);
        return s;
    }

    @Test
    public void aSizeClassTwoShipNeedsThreeOtherShips() {
        Fleet fleet = new Fleet(List.of(ship("DN", 200, 2, 9), ship("A", 80, 4, 0),
                ship("B", 80, 4, 0)), "DN", 900, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.331"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> "DN".equals(v.shipName)));
    }

    @Test
    public void twoOfThoseShipsMustFlyTheSameFlag() {
        Fleet fleet = new Fleet(List.of(ship("DN", 200, 2, 9), ship("A", 80, 4, 0),
                allied("K1", 4), allied("K2", 4)), "DN", 900, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertTrue(rulesBroken(vs).contains("S8.331"));
        assertTrue(vs.toString(), vs.stream().anyMatch(v -> v.message.contains("Federation")));
    }

    @Test
    public void aProperlyAccompaniedSizeClassTwoShipIsLegal() {
        Fleet fleet = new Fleet(List.of(ship("DN", 200, 2, 9), ship("A", 80, 4, 0),
                ship("B", 80, 4, 0), allied("K1", 4)), "DN", 900, 175);

        List<Violation> vs = FleetValidator.validate(fleet);
        assertFalse(vs.toString(), rulesBroken(vs).contains("S8.331"));
        assertTrue("three other ships, two of them Federation", FleetValidator.isLegal(vs));
    }

    /** Nothing to check when the fleet's heaviest hull is a cruiser. */
    @Test
    public void aFleetWithNoHeavyShipIsUnaffected() {
        Fleet fleet = new Fleet(List.of(ship("Flagship", 120, 3, 3), ship("A", 80, 4, 0)),
                "Flagship", 500, 175);

        assertFalse(rulesBroken(FleetValidator.validate(fleet)).contains("S8.331"));
    }

    // -------------------------------------------------------------------------
    // Distinct names — not a rule in the book, a consequence of how orders work
    // -------------------------------------------------------------------------

    /**
     * Reported from a playtest: two ships were given the same name in the builder, and the
     * mission would not set up. A name is how every order addresses a unit — fire, lock-on,
     * tractor, the map DTO, each player's redacted view — so the second ship of a pair is
     * simply unreachable.
     */
    @Test
    public void twoShipsWithTheSameNameIsAnError() {
        Ship flag = ship("Flagship", 120, 3, 3);
        Fleet fleet = new Fleet(List.of(flag, ship("Excelsior", 80, 4, 0),
                ship("Excelsior", 80, 4, 0)), "Flagship", 500, 175);

        List<FleetValidator.Violation> v = FleetValidator.validate(fleet);

        assertFalse("a fleet with two identically named ships is not legal",
                FleetValidator.isLegal(v));
        assertTrue(v.toString(), v.toString().toLowerCase().contains("excelsior"));
    }

    @Test
    public void namesDifferingOnlyByCaseStillCollide() {
        // findShip and friends compare case-insensitively, so these are the same ship.
        Ship flag = ship("Flagship", 120, 3, 3);
        Fleet fleet = new Fleet(List.of(flag, ship("Excelsior", 80, 4, 0),
                ship("EXCELSIOR", 80, 4, 0)), "Flagship", 500, 175);

        assertFalse(FleetValidator.isLegal(FleetValidator.validate(fleet)));
    }

    @Test
    public void distinctNamesAreFine() {
        Ship flag = ship("Flagship", 120, 3, 3);
        Fleet fleet = new Fleet(List.of(flag, ship("Excelsior", 80, 4, 0),
                ship("Reliant", 80, 4, 0)), "Flagship", 500, 175);

        assertTrue(FleetValidator.validate(fleet).toString(),
                FleetValidator.isLegal(FleetValidator.validate(fleet)));
    }

    // ---- E11.17 the PPD fleet limit ----

    /** A ship carrying {@code ppds} pulsar devices, and nothing else the limit reads. */
    private Ship ppdShip(String name, int sizeClass, int commandRating, int ppds) {
        Ship s = ship(name, 150, sizeClass, commandRating);
        for (int i = 0; i < ppds; i++)
            s.getWeapons().addWeapon(new com.sfb.weapons.PPD());
        return s;
    }

    /** A force of {@code total} ships led by a flagship carrying {@code flagPpds} PPDs. */
    private List<Ship> force(int total, int flagPpds, int ppdsElsewhere) {
        List<Ship> ships = new ArrayList<>();
        ships.add(ppdShip("Flagship", 2, 10, flagPpds));
        int left = ppdsElsewhere;
        for (int i = 1; i < total; i++) {
            int mine = Math.min(left, 4);
            left -= mine;
            ships.add(ppdShip("Consort " + i, 3, 0, mine));
        }
        assertEquals("the fixture could not place every PPD", 0, left);
        return ships;
    }

    /**
     * The rule states its own worked example - "This yields a maximum of eight PPDs in a
     * standard eleven-ship fleet" - so that example IS the test. Four from the flagship, plus
     * one for each group of three among the other ten.
     */
    @Test
    public void elevenShipsYieldEightPpds() {
        Fleet eight = new Fleet(force(11, 4, 4), "Flagship", 9000, 175);
        assertFalse("E11.17's own example must be legal",
                rulesBroken(FleetValidator.validate(eight)).contains("E11.17"));

        Fleet nine = new Fleet(force(11, 4, 5), "Flagship", 9000, 175);
        assertTrue("a ninth PPD in an eleven-ship fleet breaks E11.17",
                rulesBroken(FleetValidator.validate(nine)).contains("E11.17"));
    }

    /**
     * The fractional group COUNTS. Ten other ships are three whole groups and one leftover;
     * reading the leftover away would yield seven and contradict the rule's own example.
     */
    @Test
    public void aPartGroupOfShipsStillBuysAPpd() {
        // Flagship with none, plus four others: one whole group of three and one left over.
        Fleet two = new Fleet(force(5, 0, 2), "Flagship", 9000, 175);
        assertFalse("four other ships are a group of three plus a fraction, so two PPDs",
                rulesBroken(FleetValidator.validate(two)).contains("E11.17"));

        Fleet three = new Fleet(force(5, 0, 3), "Flagship", 9000, 175);
        assertTrue("but not three", rulesBroken(FleetValidator.validate(three)).contains("E11.17"));
    }

    /** "with whatever PPDs it has (a maximum of four)" - a fifth on the flagship buys nothing. */
    @Test
    public void theFlagshipContributesAtMostFour() {
        Fleet six = new Fleet(List.of(ppdShip("Flagship", 2, 10, 6)), "Flagship", 9000, 175);
        assertTrue("a flagship's own PPDs cap at four",
                rulesBroken(FleetValidator.validate(six)).contains("E11.17"));

        Fleet four = new Fleet(List.of(ppdShip("Flagship", 2, 10, 4)), "Flagship", 9000, 175);
        assertFalse("four is allowed",
                rulesBroken(FleetValidator.validate(four)).contains("E11.17"));
    }

    /** "There is also an overall limit of nine PPDs in any given fleet." */
    @Test
    public void nineIsTheCeilingHoweverLargeTheFleet() {
        // 31 ships: 4 + ceil(30/3) = 14 by the formula, but the fleet ceiling is nine.
        Fleet nine = new Fleet(force(31, 4, 5), "Flagship", 99000, 175);
        assertFalse("nine is allowed",
                rulesBroken(FleetValidator.validate(nine)).contains("E11.17"));

        Fleet ten = new Fleet(force(31, 4, 6), "Flagship", 99000, 175);
        List<Violation> vs = FleetValidator.validate(ten);
        assertTrue("a tenth PPD breaks the overall ceiling", rulesBroken(vs).contains("E11.17"));
        assertTrue("and the message says WHICH limit bit: " + vs,
                vs.stream().anyMatch(v -> "E11.17".equals(v.rule)
                        && v.message.contains("overall limit")));
    }

    /** "PPDs on bases do not count for purposes of this limit." */
    @Test
    public void basesCountForNeitherTheirPpdsNorTheirHulls() {
        Ship flag = ppdShip("Flagship", 2, 10, 4);
        Ship base = ppdShip("Star Base", 1, 0, 4);
        base.setBase(true);

        // The flagship alone allows four. The base adds four more PPDs and one more hull, and
        // neither may change the sum.
        Fleet fleet = new Fleet(List.of(flag, base), "Flagship", 9000, 175);
        assertFalse("a base's PPDs are not fielded for this limit: "
                        + FleetValidator.validate(fleet),
                rulesBroken(FleetValidator.validate(fleet)).contains("E11.17"));
    }

    /** A force with no pulsar devices is not this rule's business. */
    @Test
    public void aFleetWithoutPpdsIsUntouched() {
        Fleet fleet = new Fleet(List.of(ship("Flagship", 120, 3, 3), ship("Consort", 80, 4, 0)),
                "Flagship", 500, 175);
        assertFalse(rulesBroken(FleetValidator.validate(fleet)).contains("E11.17"));
    }

    /**
     * And it bites on the REAL DATA, not only on synthetic ships. The ISC DN carries four PPDs
     * off its own sheet, which is the whole of a flagship's allowance; a single Star Cruiser
     * with its PPD refit is a fractional group and buys the fifth, so the force is legal at
     * five and over at six.
     *
     * <p>That boundary is here because the first draft of this test asserted the wrong one. It
     * expected five across two ships to be illegal, having read "every group of three ships"
     * as needing three whole ships. One ship IS a group, counted as the one fractional group
     * the rule allows. The test failed, the code was right, and the misreading would have been
     * invisible had this test only ever exercised synthetic fleets that happened to agree.
     *
     * <p>Here rather than only in the synthetic block deliberately. A limit that passes every
     * unit test but never fires on a fleet a player could actually build is a limit that is not
     * enforced, and this repo has shipped exactly that twice.
     */
    @Test
    public void theLimitFiresOnRealIscHulls() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");

        Ship dn = com.sfb.objects.ShipLibrary.createShip(
                com.sfb.objects.ShipLibrary.get("ISC", "DN"));
        dn.setName("CSS Concordium");
        assertEquals("the DN's sheet carries four PPDs", 4, ppdCountOf(dn));

        Fleet alone = new Fleet(List.of(dn), "CSS Concordium", 9000, 185);
        assertFalse("a DN on its own sits exactly at its allowance: "
                        + FleetValidator.validate(alone),
                rulesBroken(FleetValidator.validate(alone)).contains("E11.17"));

        Ship ca = starCruiserWithPpd("CSS Advantageous");
        assertEquals("the refitted Star Cruiser carries one", 1, ppdCountOf(ca));

        // One other ship is a fractional group, and the rule counts it: five is allowed.
        Fleet pair = new Fleet(List.of(dn, ca), "CSS Concordium", 9000, 185);
        assertFalse("the DN's four plus one fractional group is five: "
                        + FleetValidator.validate(pair),
                rulesBroken(FleetValidator.validate(pair)).contains("E11.17"));

        // A second Star Cruiser brings a sixth PPD, and two ships are still one group.
        Fleet trio = new Fleet(List.of(dn, ca, starCruiserWithPpd("CSS Arbiter")),
                "CSS Concordium", 9000, 185);
        assertTrue("six PPDs across three ships breaks E11.17",
                rulesBroken(FleetValidator.validate(trio)).contains("E11.17"));
    }

    /** An ISC Star Cruiser wearing its PPD refit, off the real ship data. */
    private Ship starCruiserWithPpd(String name) {
        Ship ca = com.sfb.objects.ShipLibrary.createShip(com.sfb.objects.RefitResolver.apply(
                com.sfb.objects.ShipLibrary.get("ISC", "CA"), List.of("P")));
        ca.setName(name);
        return ca;
    }

    private int ppdCountOf(Ship s) {
        return (int) s.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.PPD).count();
    }
}
