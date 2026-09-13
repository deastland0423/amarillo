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
        v.put("istruecarrier", true);
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
        assertEquals("bought at its economic value", 120, FleetValidator.costOf(scout));
        assertEquals("even as the only ship in the force",
                120, FleetValidator.fleetCost(List.of(scout)));

        Ship consort = ship("Consort", 100, 4, 5);
        assertEquals("fleet total uses the economic value",
                220, FleetValidator.fleetCost(List.of(scout, consort)));
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
}
