package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KzintiShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * FD10.622 and FD10.632: 25% Restricted and 10% Limited, doubled for three kinds of ship.
 *
 * <h2>Why the fixtures are small and hand-loaded</h2>
 * A percentage needs a denominator, and the interesting cases are the boundaries — four spaces
 * with one restricted drone is exactly 25%, five is not. Loading racks by hand makes the
 * arithmetic visible in the test rather than hidden in whichever hull happened to be convenient.
 */
public class DroneAvailabilityLimitTest {

    /** A Federation FFG: one type-G rack, four spaces. Not Kzinti, not a carrier, not D%. */
    private static Ship ordinaryShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedFfg());
        return ship;
    }

    /** A Kzinti BC, which FD10.622 doubles by faction alone. */
    private static Ship kzintiShip() {
        Ship ship = new Ship();
        ship.init(KzintiShips.getKzinBC());
        return ship;
    }

    private static List<DroneRack> racksOf(Ship ship) {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                out.add(r);
        return out;
    }

    /**
     * Put exactly these drones on the ship and nothing else: every rack emptied, then the first
     * filled.
     *
     * <p>Emptying the others is the whole point. A cap is a PROPORTION, so the denominator is
     * every drone aboard — and loading one rack of a four-rack hull leaves twelve default
     * type-Is in the others. An earlier version of this helper did exactly that, and the Kzinti
     * test failed with an allowance of three where one was expected, because the ship had
     * sixteen spaces rather than four. The code was right; the fixture was measuring a
     * different ship from the one the test described.
     */
    private static void load(Ship ship, DroneType... types) {
        List<DroneRack> racks = racksOf(ship);
        assertFalse("fixture: the hull must have a drone rack", racks.isEmpty());
        for (DroneRack r : racks)
            r.setAmmo(new ArrayList<>());
        List<Drone> drones = new ArrayList<>();
        for (DroneType t : types)
            drones.add(new Drone(t));
        racks.get(0).setAmmo(drones);
    }

    // ---------------------------------------------------------------- the caps themselves

    /** FD10.622/FD10.632's ordinary ceilings, on a hull with no exception to its name. */
    @Test
    public void anOrdinaryShipGetsTwentyFivePercentAndTenPercent() {
        Ship ship = ordinaryShip();
        assertFalse(DroneAvailabilityLimit.hasRelaxedCaps(ship));
        assertEquals(25, DroneAvailabilityLimit.restrictedPercentFor(ship));
        assertEquals(10, DroneAvailabilityLimit.limitedPercentFor(ship));
    }

    /** FD10.622: "Kzinti ships are allowed 50% Restricted Availability" — and 20% Limited. */
    @Test
    public void kzintiShipsAreDoubledByFactionAlone() {
        Ship ship = kzintiShip();
        assertTrue(DroneAvailabilityLimit.hasRelaxedCaps(ship));
        assertEquals(50, DroneAvailabilityLimit.restrictedPercentFor(ship));
        assertEquals(20, DroneAvailabilityLimit.limitedPercentFor(ship));
    }

    /**
     * The third clause, "any other unit with 'D%' or 'DB' in the notes column of the Master
     * Ship Chart" — which until now bought a bigger COI budget (S3.223) and nothing to spend it
     * on, because the caps it widens did not exist.
     */
    @Test
    public void aDPercentHullIsDoubledToo() {
        assertEquals("fixture: an unmarked hull first", 25,
                DroneAvailabilityLimit.restrictedPercentFor(ordinaryShip()));

        // A real marked hull rather than a flag set by hand: the Klingon D6D carries Annex #3's
        // "DB", is one of nine transcribed onto the roster DPercentShipTest guards, and is a
        // Klingon — so it cannot be passing by the Kzinti clause either.
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipSpec spec = com.sfb.objects.ShipLibrary.get("Klingon", "D6D");
        assertNotNull("fixture: Klingon/D6D must be in the library", spec);
        assertTrue("fixture: and must still be marked", spec.dPercent);
        Ship marked = com.sfb.objects.ShipLibrary.createShip(spec);

        assertTrue(DroneAvailabilityLimit.hasRelaxedCaps(marked));
        assertEquals(50, DroneAvailabilityLimit.restrictedPercentFor(marked));
        assertEquals(20, DroneAvailabilityLimit.limitedPercentFor(marked));
    }

    // ---------------------------------------------------------------- counting

    /**
     * FD10.641 counts "warhead spaces", so a type-IV is two and a type-I is one.
     */
    @Test
    public void theDenominatorIsWarheadSpacesNotDrones() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeIV, DroneType.TypeI);
        assertEquals("one two-space drone and one one-space drone",
                3.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
    }

    /**
     * FD10.643: "Type-VI drones are not included in any of the calculations to determine how
     * many special drones a given ship may carry."
     *
     * <p><b>This documents the rule; it does not prove the branch.</b> The half-space frame
     * reports no payload spaces, so a type-VI contributes nothing to the sum even with the
     * explicit check deleted — confirmed by mutation, which failed nothing. The check is kept
     * for the reason given on {@code DroneAvailabilityLimit}: the zero is an artefact of how a
     * fixed warhead is modelled, not an expression of FD10.643, and the two would come apart
     * the day the frame is given a payload space to hold that warhead in.
     */
    @Test
    public void dogfightDronesAreLeftOutOfTheSumEntirely() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeI, DroneType.TypeVI, DroneType.TypeVI);
        assertEquals("the two type-VIs count for nothing",
                1.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
    }

    /**
     * FD10.64 nests the caps, so the Restricted count includes the Limited drones. A type-III
     * is Limited in every year (FD10.631), so it shows up in both tallies.
     */
    @Test
    public void aLimitedDroneSpendsRestrictedAllowanceToo() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeIII, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI);

        assertEquals("the type-III is limited",
                1.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.LIMITED, 180), 0.0001);
        assertEquals("and therefore also counts as restricted-or-worse",
                1.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180),
                0.0001);
    }

    // ---------------------------------------------------------------- the boundary

    /**
     * Four spaces at 10%: the allowance rounds to zero, so a single Limited drone breaches it.
     * FD10.641 rounds "0.49- down and 0.50+ up", and 0.4 is down.
     */
    @Test
    public void oneLimitedDroneInFourSpacesIsOverTheOrdinaryCap() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeIII, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI);

        assertEquals("10% of four spaces rounds down to none",
                0, DroneAvailabilityLimit.allowance(ship, Availability.LIMITED));
        List<String> violations = DroneAvailabilityLimit.violations(ship, 180);
        assertFalse("a type-III on a four-space rack breaches FD10.632", violations.isEmpty());
        assertTrue(violations.toString(), violations.get(0).contains("FD10.632"));
    }

    /** And the same ship with no special drones at all is clean. */
    @Test
    public void anOrdinaryLoadBreachesNothing() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).toString(),
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    /**
     * A Kzinti ship carrying the same load is legal, because FD10.632 gives it 20% — which over
     * four spaces rounds to one. The same drones, the same year, a different answer by faction.
     */
    @Test
    public void theSameLoadIsLegalOnAKzintiHull() {
        Ship kzinti = kzintiShip();
        load(kzinti, DroneType.TypeIII, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI);

        assertEquals("20% of four spaces rounds to one",
                1, DroneAvailabilityLimit.allowance(kzinti, Availability.LIMITED));
        assertTrue(DroneAvailabilityLimit.violations(kzinti, 180).toString(),
                DroneAvailabilityLimit.violations(kzinti, 180).isEmpty());
    }

    // ---------------------------------------------------------------- the year matters

    /**
     * The point of slice 3a, made concrete: the SAME drones are legal or not depending on the
     * year, because a medium-speed drone is Limited in Y165, Restricted in Y166 and General in
     * Y167 (FD10.65).
     */
    @Test
    public void theSameLoadoutChangesLegalityWithTheYear() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeIM, DroneType.TypeIM, DroneType.TypeIM, DroneType.TypeIM);

        assertFalse("Y165: all four are Limited, and 10% of four is none",
                DroneAvailabilityLimit.violations(ship, 165).isEmpty());
        assertFalse("Y166: still Restricted, and 25% of four is one",
                DroneAvailabilityLimit.violations(ship, 166).isEmpty());
        assertTrue("Y167: General, so no cap applies at all — "
                + DroneAvailabilityLimit.violations(ship, 167),
                DroneAvailabilityLimit.violations(ship, 167).isEmpty());
    }

    /**
     * And the layer that GATES it: applyCoi must ASK.
     *
     * <p>Twice today a rule was implemented, tested, and unreachable from the only place that
     * calls it — the drone-rack compatibility check, and before that three COI tests that never
     * ran. A cap nobody consults is the same failure with a longer rule number, so this drives
     * the whole thing through {@code ScenarioLoader.applyCoi} and reads the note it leaves.
     */
    @Test
    public void applyCoiNotesTheBreachRatherThanIgnoringIt() {
        Ship ship = ordinaryShip();
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = 180;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, List.of(
                DroneType.TypeIII, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        String notes = String.join(" | ", ship.getSetupNotes());
        assertTrue("applyCoi should have noted the FD10.632 breach, but said: " + notes,
                notes.contains("FD10.632"));
    }

    /** And says nothing when the load is legal. */
    @Test
    public void applyCoiIsSilentOnALegalLoad() {
        Ship ship = ordinaryShip();
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = 180;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();

        CoiLoadout loadout = new CoiLoadout();
        loadout.setDroneRackLoadout(0, List.of(
                DroneType.TypeI, DroneType.TypeI, DroneType.TypeI, DroneType.TypeI));
        ScenarioLoader.applyCoi(ship, loadout, spec);

        String notes = String.join(" | ", ship.getSetupNotes());
        assertFalse("nothing to complain about, but said: " + notes,
                notes.contains("FD10.632") || notes.contains("FD10.622"));
    }

    /** An empty rack is not a breach of anything. */
    @Test
    public void aShipWithNoDronesIsNotJudged() {
        Ship ship = ordinaryShip();
        racksOf(ship).get(0).setAmmo(new ArrayList<>());
        assertEquals(0.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }
}
