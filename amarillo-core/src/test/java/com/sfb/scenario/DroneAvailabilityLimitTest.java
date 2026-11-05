package com.sfb.scenario;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneFrame;
import com.sfb.objects.DroneSpec;
import com.sfb.objects.DroneType;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Propulsion;
import com.sfb.objects.Ship;
import com.sfb.objects.FrameAddition;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
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
     * Leave the hull with ONE empty rack, and hand it back.
     *
     * <p>A cap is a proportion, and FD10.641's denominator is the rack capacity aboard, so a
     * fixture that wants to talk about four spaces has to leave the ship with four spaces —
     * emptying the other three racks of a Kzinti BC no longer narrows anything.
     *
     * <p>It used to be the other way about, and both versions of this helper have been wrong
     * in the same way: measuring a different ship from the one the test describes. The first
     * left twelve default type-Is in the racks it was ignoring; the second emptied them, which
     * read as four spaces only while the denominator was the loadout. {@code fetchAllWeapons}
     * hands back the live list, which is what lets a test change the hull at all.
     */
    private static DroneRack keepOneRack(Ship ship) {
        List<Weapon> weapons = ship.getWeapons().fetchAllWeapons();
        DroneRack keep = null;
        for (Weapon w : weapons)
            if (w instanceof DroneRack r) {
                keep = r;
                break;
            }
        assertNotNull("fixture: the hull must have a drone rack", keep);
        final DroneRack kept = keep;
        weapons.removeIf(w -> w instanceof DroneRack && w != kept);
        kept.setAmmo(new ArrayList<>());
        return kept;
    }

    /** Put exactly these drones in the hull's one remaining rack, and nothing else anywhere. */
    private static void load(Ship ship, DroneType... types) {
        List<Drone> drones = new ArrayList<>();
        for (DroneType t : types)
            drones.add(new Drone(t));
        keepOneRack(ship).setAmmo(drones);
    }

    /** A Klingon D7: not Kzinti, not a carrier, not D% — an ordinary hull of the empire. */
    private static Ship klingonShip() {
        Ship ship = new Ship();
        ship.init(KlingonShips.getD7());
        return ship;
    }

    /** FD10.622's "Type-III-ECM": the type-III's guidance surcharge, and ECM in its one space. */
    private static DroneSpec typeIiiEcm() {
        return DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                java.util.Set.of(FrameAddition.TYPE_III_GUIDANCE), PayloadModule.ECM);
    }

    /**
     * Swap the hull's drone racks for a single rack of this type.
     *
     * <p>Only two racks hold a dogfight drone at all (FD3.5, FD3.84) and one of them is the
     * type-G, so a rack that shows FD10.643's general rule rather than its exception has to be
     * built: no sample hull carries a type-H. {@code fetchAllWeapons} hands back the live list,
     * which is what makes this possible from a test.
     */
    private static DroneRack refitTo(Ship ship, DroneRack.DroneRackType type) {
        List<Weapon> weapons = ship.getWeapons().fetchAllWeapons();
        weapons.removeIf(w -> w instanceof DroneRack);
        DroneRack rack = new DroneRack(type);
        rack.setDesignator("Drone Rack");
        weapons.add(rack);
        return rack;
    }

    /** One payload space of Restricted: an ECM module (FD10.621) on a General frame and engine. */
    private static DroneSpec oneRestrictedSpace() {
        return DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12, PayloadModule.ECM);
    }

    /** As {@link #load}, but for drones built rather than taken from the catalogue. */
    private static void loadDrones(Ship ship, Drone... drones) {
        keepOneRack(ship).setAmmo(new ArrayList<>(List.of(drones)));
    }

    /** Add a rack of this type to the hull, keeping what it already mounts. */
    private static DroneRack addRack(Ship ship, DroneRack.DroneRackType type) {
        DroneRack rack = new DroneRack(type);
        rack.setDesignator(type.name());
        rack.setAmmo(new ArrayList<>());
        ship.getWeapons().fetchAllWeapons().add(rack);
        return rack;
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
     * FD10.641 counts "warhead spaces", so a type-IV is two and a type-I is one — in the
     * NUMERATOR, which is the drones actually aboard.
     */
    @Test
    public void theNumeratorIsWarheadSpacesNotDrones() {
        Ship ship = ordinaryShip();
        loadDrones(ship, new Drone(DroneType.TypeIV, DroneSpec.of(DroneFrame.TWO_SPACE,
                        Propulsion.SPEED_12, PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF,
                        PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF)),
                new Drone(DroneType.TypeI));

        assertEquals("the heavy drone is two Restricted spaces, not one Restricted drone",
                2.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180),
                0.0001);
    }

    /**
     * And the DENOMINATOR is the rack capacity, loaded or not.
     *
     * <p>This is the playtest bug, in the smallest shape that shows it. A Kzinti BC mounts four
     * type-A racks: sixteen spaces whatever is in them. Counting the loadout instead made the
     * basis shrink as the player emptied racks, so eight medium drones in twelve loaded spaces
     * breached a cap that the same eight in sixteen spaces did not.
     */
    @Test
    public void theDenominatorIsTheRackCapacityNotTheLoad() {
        Ship full = kzintiShip();
        assertEquals("four type-A racks of four spaces", 16.0,
                DroneAvailabilityLimit.countedSpaces(full), 0.0001);

        Ship light = kzintiShip();
        for (DroneRack r : racksOf(light))
            r.setAmmo(new ArrayList<>());
        assertEquals("and the empty ship is the same ship", 16.0,
                DroneAvailabilityLimit.countedSpaces(light), 0.0001);
    }

    /**
     * The property the capacity basis exists for: loading a GENERAL drone can never make an
     * illegal ship legal.
     *
     * <p>Eight medium drones in a Kzinti BC's sixteen spaces is 50% exactly, which FD10.622
     * allows a Kzinti hull — and it stays exactly 50% however many of the other eight spaces
     * are filled. Under the old basis the same eight drones were a breach until four more
     * drones were bought to widen the denominator, which is the reported bug.
     */
    @Test
    public void addingGeneralDronesCannotCureOrCauseABreach() {
        for (int slowDrones : new int[] { 0, 4, 8 }) {
            Ship ship = kzintiShip();
            List<DroneRack> racks = racksOf(ship);
            for (DroneRack r : racks)
                r.setAmmo(new ArrayList<>());
            List<Drone> medium = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                medium.add(new Drone(DroneType.TypeIM));
            racks.get(0).setAmmo(new ArrayList<>(medium.subList(0, 4)));
            racks.get(1).setAmmo(new ArrayList<>(medium.subList(4, 8)));
            List<Drone> slow = new ArrayList<>();
            for (int i = 0; i < slowDrones; i++)
                slow.add(new Drone(DroneType.TypeI));
            for (int i = 0; i < slow.size(); i++)
                racks.get(2 + i / 4).getAmmo().add(slow.get(i));

            assertEquals("the basis is the hull, not the load", 16.0,
                    DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
            assertEquals("50% of sixteen", 8,
                    DroneAvailabilityLimit.allowance(ship, Availability.RESTRICTED));
            assertTrue("eight medium drones is exactly the Kzinti 50%, with " + slowDrones
                    + " slow drones beside them: "
                    + DroneAvailabilityLimit.violations(ship, 166),
                    DroneAvailabilityLimit.violations(ship, 166).isEmpty());
        }
    }

    /**
     * FD10.643: "Type-VI drones are not included in any of the calculations to determine how
     * many special drones a given ship may carry."
     *
     * <p>In a rack that is not a type-G, which is the rule before its exception. A type-H is
     * the fixture because it is the other rack that takes dogfight drones at all (FD3.84) and
     * has no anti-drone targeting system, so it is the one place the plain rule can be seen.
     * The FFG's own rack is a type-G and answers differently — see the test below.
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
        DroneRack h = refitTo(ship, DroneRack.DroneRackType.TYPE_H);
        h.setAmmo(new ArrayList<>(List.of(new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeVIM), new Drone(DroneType.TypeVIM))));

        assertEquals("FD3.8: a starbase launcher of five four-space magazines",
                20.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and in Y166 the two medium type-VIs count against nothing at all", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("not even in the dogfight pool: a type-H is not a dogfight-only rack", 0.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
    }

    // ------------------------------------------------ FD10.643's type-G exception

    /**
     * FD10.643's exception, worked exactly as the rule writes it.
     *
     * <blockquote>"For purposes of type-G drone racks, type-VI and ADDs loaded in the rack count
     * as 'General Availability' drone spaces, e.g., a type-G rack on a non-Kzinti or
     * non-carrier ship might have one restricted availability drone space and three spaces of
     * type-VI or ADDs or a combination of the two."</blockquote>
     *
     * <p>The FFG is that example's subject down to the hull: one four-space type-G, not Kzinti,
     * not a carrier, not D%. One ECM drone (Restricted, FD10.621) and six type-VIs at half a
     * rack space each is four counted spaces, and 25% of four is one.
     *
     * <p><b>Without the exception this legal loadout is a violation</b>, which is why the
     * exception had to be built before anything refused a fleet over these caps: the type-VIs
     * would supply none of the denominator, leaving one counted space, an allowance of
     * {@code Math.round(1 * 0.25) == 0}, and the ECM drone over it.
     */
    @Test
    public void aTypeGCountsItsTypeVisAsGeneralSpaces() {
        Ship ship = ordinaryShip();
        List<Drone> load = new ArrayList<>();
        load.add(new Drone(DroneType.TypeI, oneRestrictedSpace()));
        for (int i = 0; i < 6; i++)
            load.add(new Drone(DroneType.TypeVI));
        loadDrones(ship, load.toArray(new Drone[0]));

        assertEquals("one space of ECM drone plus three spaces of type-VI",
                4.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and the type-VIs are General, so none of them is Restricted-or-worse",
                1.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180),
                0.0001);
        assertEquals("25% of four spaces is one", 1,
                DroneAvailabilityLimit.allowance(ship, Availability.RESTRICTED));
        assertTrue("the rulebook's own example must be legal: "
                + DroneAvailabilityLimit.violations(ship, 180),
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    /** "...or ADDs or a combination of the two", at FD3.70's half space each. */
    @Test
    public void anAddRoundIsHalfAGeneralSpaceInATypeG() {
        Ship ship = ordinaryShip();
        loadDrones(ship, new Drone(DroneType.TypeI, oneRestrictedSpace()));
        racksOf(ship).get(0).setAddAmmo(6);

        assertEquals("one drone space plus three spaces of anti-drone rounds",
                4.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("ADDs are General and count against nothing above it", 1.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).toString(),
                DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    /**
     * The exception's second sentence, which is the half of it that can refuse a loadout:
     * "type-VI spaces that receive speed upgrades in some years (FD10.65) count against the
     * G-rack's Restricted and Limited Availability drone spaces."
     *
     * <p>Four type-VIMs are two counted spaces, and in Y166 medium speed is Restricted, so both
     * of them are — against an allowance of one. The same four in Y167 are General and the ship
     * is clean, which is the year doing the work rather than the drone.
     */
    @Test
    public void aSpeedUpgradedTypeViInATypeGCountsAgainstTheRestrictedCap() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeVIM, DroneType.TypeVIM, DroneType.TypeVIM, DroneType.TypeVIM);

        assertEquals("the type-G's own four spaces", 4.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("all of them Restricted in Y166", 2.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("25% of the rack's four spaces is one", 1,
                DroneAvailabilityLimit.allowance(ship, Availability.RESTRICTED));
        assertFalse("two Restricted spaces against an allowance of one breaches FD10.622",
                DroneAvailabilityLimit.violations(ship, 166).isEmpty());

        assertEquals("and General in Y167", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 167), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 167).toString(),
                DroneAvailabilityLimit.violations(ship, 167).isEmpty());
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

    /**
     * FD10.641's split, at ship level, on a drone that could not be loaded until now.
     *
     * <p>The owner's armored type-IV: a two-space frame carrying three 1/2-PS armor modules and
     * one 1/2-PS explosive. Armor is Restricted (FD10.621) and the explosive is General, and
     * the frame modifications are General too — a speed-8 engine on a type-IV frame — so the
     * floor is General and only the armor raises its own spaces.
     *
     * <p>One and a half spaces of armor, half a space of explosive. Under worst-tier-wins the
     * whole drone would count as two Restricted spaces.
     *
     * <p>This is the test that forced {@code Drone} to take a {@code DroneSpec}. A rack holds
     * {@code Drone}s, a {@code Drone} was built only from a {@code DroneType}, and none of the
     * fourteen standard types has a module more restricted than its frame — so the half of
     * FD10.641 that matters here had nothing to act on and a mutation reverting it failed
     * nothing.
     */
    @Test
    public void anArmoredCustomDroneSplitsAcrossTiersInTheCount() {
        Ship ship = ordinaryShip();
        DroneSpec armored = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);

        List<Drone> load = new ArrayList<>();
        load.add(new Drone(DroneType.TypeIV, armored));
        load.add(new Drone(DroneType.TypeI));
        load.add(new Drone(DroneType.TypeI));
        for (DroneRack r : racksOf(ship))
            r.setAmmo(new ArrayList<>());
        racksOf(ship).get(0).setAmmo(load);

        assertEquals("two spaces of armored heavy plus two one-space drones",
                4.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        // BOTH of the armored drone's spaces, not one and a half. FD10.66: "there are no
        // 'half spaces' of drone warheads" — the two leftover halves, one armor and one
        // explosive, share a payload space and that space counts "as the more restricted type
        // of the two halves". So armor+armor is a Restricted space and armor+explosive is
        // another, and the General explosive half is dragged up by the armor beside it.
        //
        // This read 1.5 until FD10.66 was found, which was a rule nobody had looked for
        // because per-module accounting is the obvious way to do it and gives a plausible
        // number.
        assertEquals("both of the armored drone's spaces are Restricted (FD10.66)",
                2.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180),
                0.0001);
        assertEquals("and nothing is Limited", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.LIMITED, 180), 0.0001);
    }

    /** And the drone itself is what slice 1 said it was, now that one can be loaded. */
    @Test
    public void theArmoredDroneKeepsItsTwelveHullInsideARack() {
        DroneSpec armored = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);
        Drone d = new Drone(DroneType.TypeIV, armored);

        assertEquals("FD12.21: six from the frame plus two per half-space module", 12, d.getHull());
        assertEquals("FD10.41: one half-space explosive", 6, d.impact());
        assertEquals("still two rack spaces", 2.0, d.getRackSize(), 0.0001);
        assertEquals("and still a type-IV for everything that asks what KIND it is",
                DroneType.TypeIV, d.getDroneType());
        assertEquals("including its endurance, which no rule derives from the composition",
                DroneType.TypeIV.endurance, d.getEndurance());
    }

    /**
     * And the rulebook's own worst case, at ship level: a type-IV with medium speed in Y166 is
     * "two spaces of Restricted Availability even if both of its payload spaces are General".
     * The frame modification drags the whole drone up, and the cap feels all of it.
     */
    @Test
    public void aMediumSpeedHeavyDroneCountsBothItsSpacesInY166() {
        Ship ship = ordinaryShip();
        load(ship, DroneType.TypeIVM, DroneType.TypeI, DroneType.TypeI);

        assertEquals("two spaces of heavy plus two of light", 4.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("FD10.641: both of the heavy drone's spaces are Restricted in Y166",
                2.0, DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166),
                0.0001);
        assertEquals("25% of four spaces is one, so two is a breach",
                1, DroneAvailabilityLimit.allowance(ship, Availability.RESTRICTED));
        assertFalse(DroneAvailabilityLimit.violations(ship, 166).isEmpty());

        assertEquals("and in Y167 medium speed is General, so none of it counts", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 167), 0.0001);
    }

    /**
     * An empty rack is not a breach of anything — now by way of an empty numerator rather
     * than an empty basis, since the spaces are there whether or not a drone is in them.
     */
    @Test
    public void aShipWithNoDronesIsNotJudged() {
        Ship ship = ordinaryShip();
        keepOneRack(ship);
        assertEquals("the rack is still four spaces of capacity", 4.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("with nothing restricted in them", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    /** And a hull with no drone rack at all has no basis and is never judged. */
    @Test
    public void aShipWithNoRacksHasNoBasis() {
        Ship ship = ordinaryShip();
        ship.getWeapons().fetchAllWeapons().removeIf(w -> w instanceof DroneRack);
        assertEquals(0.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals(0.0, DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 180).isEmpty());
    }

    // ------------------------------------------- FD10.644: dogfight drones count separately

    /**
     * FD10.644, applied to a ship's own type-E rack.
     *
     * <blockquote>"Fighter squadrons count type-VI drones loaded on type-VI rails separately
     * from all other drones."</blockquote>
     *
     * <p>FD3.5's type-E holds eight dogfight drones in four spaces and "can carry no other
     * types", so those four spaces are a basis of their own. A Klingon hull gets FD10.622's
     * ordinary 25%, which is one space — two type-VIMs. Eight of them is four spaces of
     * Restricted in Y166 and a breach; the same eight in Y167 are General and fine.
     */
    @Test
    public void aTypeERackIsItsOwnBasis() {
        Ship ship = klingonShip();
        DroneRack e = refitTo(ship, DroneRack.DroneRackType.TYPE_E);
        List<Drone> load = new ArrayList<>();
        for (int i = 0; i < 8; i++)
            load.add(new Drone(DroneType.TypeVIM));
        e.setAmmo(load);

        assertEquals("the ordinary basis is empty: a type-E holds nothing that belongs in it",
                0.0, DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("eight dogfight drones at half a space each", 4.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
        assertEquals("all four spaces are medium, and medium is Restricted in Y166", 4.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(ship, Availability.RESTRICTED, 166),
                0.0001);
        assertEquals("25% of four spaces is one", 1,
                DroneAvailabilityLimit.dogfightAllowance(ship, Availability.RESTRICTED));

        List<String> breaches = DroneAvailabilityLimit.violations(ship, 166);
        assertFalse("upgrading every dogfight drone in Y166 breaches FD10.622", breaches.isEmpty());
        assertTrue("and the sentence must say which count it is about: " + breaches,
                breaches.get(0).contains("dogfight"));

        assertTrue("in Y167 medium speed is General: "
                + DroneAvailabilityLimit.violations(ship, 167),
                DroneAvailabilityLimit.violations(ship, 167).isEmpty());
    }

    /** Two of the eight is the quarter FD10.622 allows, and the loadout is legal in Y166. */
    @Test
    public void aQuarterOfTheDogfightDronesMayBeUpgraded() {
        Ship ship = klingonShip();
        DroneRack e = refitTo(ship, DroneRack.DroneRackType.TYPE_E);
        List<Drone> load = new ArrayList<>(List.of(
                new Drone(DroneType.TypeVIM), new Drone(DroneType.TypeVIM)));
        for (int i = 0; i < 6; i++)
            load.add(new Drone(DroneType.TypeVI));
        e.setAmmo(load);

        assertEquals("two half spaces of medium", 1.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(ship, Availability.RESTRICTED, 166),
                0.0001);
        assertTrue(DroneAvailabilityLimit.violations(ship, 166).toString(),
                DroneAvailabilityLimit.violations(ship, 166).isEmpty());
    }

    /**
     * "Separately from all other drones" in both directions: neither pool spends the other's
     * allowance.
     *
     * <p>The D6C's shape — two type-A racks and two type-E (the one hull in the data that
     * mounts a type-E at all). Its eight ordinary spaces allow two Restricted at 25%, and its
     * four dogfight spaces allow one, and filling one pool to its ceiling leaves the other's
     * ceiling exactly where it was. Under a single pooled basis the twelve spaces would allow
     * three between them, and either side could eat the other's share.
     */
    @Test
    public void theTwoPoolsDoNotSpendEachOthersAllowance() {
        Ship ship = klingonShip();          // two type-F racks, four spaces each
        DroneRack e = addRack(ship, DroneRack.DroneRackType.TYPE_E);

        assertEquals("eight ordinary spaces", 8.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and four dogfight spaces beside them, in nobody else's total", 4.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
        assertEquals(2, DroneAvailabilityLimit.allowance(ship, Availability.RESTRICTED));
        assertEquals(1, DroneAvailabilityLimit.dogfightAllowance(ship, Availability.RESTRICTED));

        // The ordinary racks filled to their own ceiling: two medium drones of eight spaces.
        List<DroneRack> ordinary = new ArrayList<>();
        for (DroneRack r : racksOf(ship))
            if (r != e)
                ordinary.add(r);
        ordinary.get(0).setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeIM), new Drone(DroneType.TypeIM),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI))));
        ordinary.get(1).setAmmo(new ArrayList<>(List.of(
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI))));
        // And the dogfight rack to its own: two of its eight drones medium.
        List<Drone> dogfight = new ArrayList<>(List.of(
                new Drone(DroneType.TypeVIM), new Drone(DroneType.TypeVIM)));
        for (int i = 0; i < 6; i++)
            dogfight.add(new Drone(DroneType.TypeVI));
        e.setAmmo(dogfight);

        assertEquals("the dogfight drones are no part of the ordinary count", 2.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("nor the ordinary drones of theirs", 1.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(ship, Availability.RESTRICTED, 166),
                0.0001);
        assertTrue("both pools are at their ceiling and neither is over it: "
                + DroneAvailabilityLimit.violations(ship, 166),
                DroneAvailabilityLimit.violations(ship, 166).isEmpty());
    }

    /** A Kzinti hull's doubled cap applies to the dogfight pool as well: half of eight. */
    @Test
    public void theRelaxedCapDoublesTheDogfightPoolToo() {
        Ship ship = kzintiShip();
        DroneRack e = refitTo(ship, DroneRack.DroneRackType.TYPE_E);
        List<Drone> load = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            load.add(new Drone(DroneType.TypeVIM));
        for (int i = 0; i < 4; i++)
            load.add(new Drone(DroneType.TypeVI));
        e.setAmmo(load);

        assertEquals("50% of four spaces", 2,
                DroneAvailabilityLimit.dogfightAllowance(ship, Availability.RESTRICTED));
        assertEquals("four medium dogfight drones are two spaces", 2.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(ship, Availability.RESTRICTED, 166),
                0.0001);
        assertTrue("half the dogfight drones upgraded is exactly the Kzinti ceiling: "
                + DroneAvailabilityLimit.violations(ship, 166),
                DroneAvailabilityLimit.violations(ship, 166).isEmpty());
    }

    // ------------------------------------------------- FD10.622's Klingon type-III-ECM clause

    /**
     * <blockquote>FD10.622: "Type-III-ECM drones on Klingon ships are Restricted
     * Availability."</blockquote>
     *
     * The type-III frame is Limited (FD10.631) and ECM is Restricted (FD10.621), so this drone
     * is Limited in anyone else's rack — as hard to get as its hardest part. On a Klingon ship
     * it is not, and that is a change of POOL rather than a free pass: under FD10.64's nesting
     * it still spends Restricted allowance, which is simply the larger one.
     */
    @Test
    public void aKlingonTypeIiiEcmIsRestrictedRatherThanLimited() {
        Ship ship = klingonShip();
        loadDrones(ship, new Drone(DroneType.TypeIII, typeIiiEcm()),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI));

        assertEquals("nothing aboard is Limited any more", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.LIMITED, 180), 0.0001);
        assertEquals("it is a Restricted space instead", 1.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 180), 0.0001);
    }

    /**
     * And the demotion is what makes the load legal. One space in four is 25%, which the
     * Restricted cap allows exactly; at the Limited cap's 10% the allowance rounds to nothing,
     * so the same drone in the same rack is a violation for anyone but a Klingon.
     */
    @Test
    public void theSameDroneIsIllegalOnAFederationShipAndLegalOnAKlingonOne() {
        Ship klingon = klingonShip();
        loadDrones(klingon, new Drone(DroneType.TypeIII, typeIiiEcm()),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI));
        assertTrue("a Klingon D7 may carry it: " + DroneAvailabilityLimit.violations(klingon, 180),
                DroneAvailabilityLimit.violations(klingon, 180).isEmpty());

        Ship federation = ordinaryShip();
        loadDrones(federation, new Drone(DroneType.TypeIII, typeIiiEcm()),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI));
        assertEquals("on a Federation hull it is still Limited", 1.0,
                DroneAvailabilityLimit.spacesAtLeast(federation, Availability.LIMITED, 180),
                0.0001);
        assertFalse("and one Limited space in four breaches FD10.632's 10%",
                DroneAvailabilityLimit.violations(federation, 180).isEmpty());
    }

    /**
     * And the one drone that could have tested the other half of {@code isTypeIiiEcm} — a
     * type-III-ECM carrying a second Limited item — <b>cannot be built</b>, which is worth
     * pinning rather than leaving to be rediscovered.
     *
     * <p>FD10.443 bars ECM from sharing a drone with armor of either kind, and external armor
     * is the only Limited item that takes no payload space. Everything else Limited — a
     * multi-warhead or a swordfish module — needs the payload space the ECM module is sitting
     * in, and a type-III has exactly one. So the "nothing else Limited" clause in
     * {@code DroneSpec.isTypeIiiEcm} has nothing to bite on today. It is kept for the same
     * reason FD10.643's type-VI exclusion is kept: it says what the RULE means, and the thing
     * making it unnecessary is a different rule that could move.
     */
    @Test
    public void aTypeIiiEcmCannotCarryASecondLimitedItemAtAll() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                    java.util.Set.of(FrameAddition.TYPE_III_GUIDANCE,
                                     FrameAddition.EXTERNAL_ARMOR_HALF),
                    PayloadModule.ECM);
            fail("FD10.443 should have refused ECM alongside armor");
        } catch (IllegalArgumentException expected) {
            assertTrue("and it should say which rule: " + expected.getMessage(),
                    expected.getMessage().contains("FD10.443"));
        }
    }

    /** And a type-III with no ECM in it is an ordinary Limited drone, Klingon or not. */
    @Test
    public void aKlingonTypeIiiWithoutEcmIsStillLimited() {
        Ship ship = klingonShip();
        loadDrones(ship, new Drone(DroneType.TypeIII),
                new Drone(DroneType.TypeI), new Drone(DroneType.TypeI),
                new Drone(DroneType.TypeI));

        assertEquals("the type-III frame alone is Limited (FD10.631)", 1.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.LIMITED, 180), 0.0001);
    }

    // ------------------------------- FD10.641: "fighter ready racks or launch rails (FD2.45)"

    /** The hull's one remaining rack, plus a fighter of this catalogue type seated in box 0. */
    private static com.sfb.systemgroups.ShuttleSpace seatFighter(Ship ship, String type) {
        keepOneRack(ship);
        com.sfb.systemgroups.ShuttleSpace box =
                ship.getShuttles().getBays().get(0).getSpaces().get(0);
        box.reequipFor(com.sfb.objects.shuttles.CataloguedFighter.of(type));
        return box;
    }

    /**
     * A fighter's rails are part of the ship's basis, at FD2.52's rail sizes.
     *
     * <p>A Kzinti HAAS carries two STANDARD rails, so a hull with one four-space rack and one
     * HAAS aboard has six warhead spaces rather than four. Before this, a carrier was judged on
     * its hull racks alone — understating the load on exactly the ships FD10.622's doubled cap
     * is written for.
     */
    @Test
    public void aFightersRailsJoinTheOrdinaryBasis() {
        Ship ship = kzintiShip();
        seatFighter(ship, "haas");

        assertEquals("four rack spaces plus two STANDARD rails", 6.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and no dogfight rail on a HAAS", 0.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
    }

    /**
     * And a LIGHT rail goes to the other pool — FD2.52 makes it type-VI only, so FD10.644
     * counts it "separately from all other drones".
     *
     * <p>A TAAS carries two STANDARD and two LIGHT: two ordinary spaces and one dogfight space,
     * since a type-VI rail is half a space.
     */
    @Test
    public void aLightRailIsCountedInTheDogfightPool() {
        Ship ship = kzintiShip();
        seatFighter(ship, "taas");

        assertEquals("the rack and the two STANDARD rails", 6.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("two LIGHT rails at half a space each", 1.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
    }

    /**
     * FD10.641's "or" — the basis takes the rails OR the ready rack, never both.
     *
     * <p>Seating a fighter also fits its box's ready rack (J4.822), which holds one reload of
     * the same drones. Counting the pair would double the carrier's denominator, and since the
     * denominator is under a ceiling, doubling it is the permissive direction: a CVS would get
     * twice the Restricted allowance FD10.622 means it to have.
     */
    @Test
    public void aFightersReadyRackIsNotCountedBesideItsRails() {
        Ship ship = kzintiShip();
        com.sfb.systemgroups.ShuttleSpace box = seatFighter(ship, "haas");

        assertNotNull("fixture: seating a drone fighter must fit a ready rack (J4.822)",
                box.getReadyRack());
        assertEquals("the rack is a reload, not a second loading", 6.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
    }

    /**
     * And the case FD10.641 names the ready racks for: an escort that has them and no fighters.
     *
     * <blockquote>FD2.45: "Carrier Escorts with ready racks for drone-armed fighters compute the
     * costs for drone upgrades for drones in their fighter ready racks and storage for those
     * racks as if they were carrying fighters (J4.621)."</blockquote>
     *
     * <p>{@code fitFacilitiesFor} is how one gets a rack with an empty box — the COI's fighter
     * facility choice — and a rack built to service a HAAS has that fighter's two standard
     * slots.
     */
    @Test
    public void anEscortsReadyRackStandsInForTheFighterItHasNot() {
        Ship ship = kzintiShip();
        keepOneRack(ship);
        com.sfb.systemgroups.ShuttleSpace box =
                ship.getShuttles().getBays().get(0).getSpaces().get(0);
        box.setShuttle(null);
        assertTrue("fixture: the facility choice must fit a rack", box.fitFacilitiesFor("haas"));

        assertEquals("four rack spaces plus the escort rack's two slots", 6.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
    }

    /**
     * The numerator counts the rails AND the rack, because a drone is only ever in one of them.
     *
     * <p>J4.8223 rests a carrier with "racks full, fighters unloaded", and arming MOVES the
     * drone across ({@code ReadyRack.takeFor}). So the sum is the single loading FD2.45 charges
     * for, whatever the player's readiness choices were — and it must not change when the
     * fighter is armed. Reading the rails alone would score a resting carrier at zero against a
     * basis that counted all of its rails.
     */
    @Test
    public void aFightersDronesCountWhereverTheyHappenToSit() {
        Ship ship = kzintiShip();
        com.sfb.systemgroups.ShuttleSpace box = seatFighter(ship, "haas");
        // The year's refit door (FD2.45): the box is stocked with medium-speed drones, which
        // FD10.65 makes Restricted in Y166.
        box.getReadyRack().refit(d -> new Drone(DroneType.TypeIM));

        assertEquals("at rest the loading is in the rack", 2.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);

        box.armOccupantFully();
        assertEquals("and arming moves it to the rails without changing the total", 2.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("the basis did not move either", 6.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
    }

    /**
     * FD10.645: a type-VI on an ordinary rail is "a space of General Availability for EACH such
     * drone" — a whole space, not the half it takes in a rack, because it displaced an
     * ordinary drone.
     *
     * <p>General by default. A speed-upgraded one still counts at its engine's tier, by the
     * clause FD10.643 spells out for the type-G; FD10.645 does not repeat it, and the strict
     * reading would let a player launder speed upgrades by parking dogfight drones on standard
     * rails. Recorded as a reading in {@code DroneAvailabilityLimit}.
     */
    @Test
    public void aTypeViOnAnOrdinaryRailIsAWholeSpaceNotAHalf() {
        Ship ship = kzintiShip();
        com.sfb.systemgroups.ShuttleSpace box = seatFighter(ship, "haas");
        // Armed first so the rack is empty and the rails are the only thing holding drones,
        // then the loading swapped for dogfight drones -- FD2.52's fighter exchange.
        box.armOccupantFully();
        for (com.sfb.weapons.Weapon w
                : ((com.sfb.objects.shuttles.Fighter) box.getShuttle())
                        .getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail) {
                rail.getAmmo().clear();
                rail.loadDrone(new Drone(DroneType.TypeVIM));
            }

        assertEquals("two type-VIMs on STANDARD rails are two WHOLE spaces of Restricted in"
                + " Y166, not one", 2.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("and they are in the ordinary pool, not the dogfight one", 0.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(
                        ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("General in Y167, when medium speed is", 0.0,
                DroneAvailabilityLimit.spacesAtLeast(ship, Availability.RESTRICTED, 167), 0.0001);
    }

    /**
     * A dogfight fighter's whole loadout lands in the dogfight pool — a Kzinti DAS flies two
     * LIGHT rails and nothing else, so it is one space, and a quarter of one space is none.
     */
    @Test
    public void aDogfightFightersDronesAreJudgedInTheirOwnPool() {
        Ship ship = klingonShip();                  // 25%, not the Kzinti 50%
        ship.getWeapons().fetchAllWeapons().removeIf(w -> w instanceof DroneRack);
        com.sfb.systemgroups.ShuttleSpace box =
                ship.getShuttles().getBays().get(0).getSpaces().get(0);
        box.reequipFor(com.sfb.objects.shuttles.CataloguedFighter.of("das"));
        box.getReadyRack().refit(d -> new Drone(DroneType.TypeVIM));

        assertEquals("no hull rack and no ordinary rail", 0.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("two LIGHT rails", 1.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
        assertEquals("both upgraded, at half a space each", 1.0,
                DroneAvailabilityLimit.dogfightSpacesAtLeast(
                        ship, Availability.RESTRICTED, 166), 0.0001);
        assertEquals("25% of one space rounds to none", 0,
                DroneAvailabilityLimit.dogfightAllowance(ship, Availability.RESTRICTED));

        List<String> breaches = DroneAvailabilityLimit.violations(ship, 166);
        assertFalse("a DAS loaded entirely with medium dogfight drones is over FD10.622",
                breaches.isEmpty());
        assertTrue("and it is the dogfight count: " + breaches,
                breaches.get(0).contains("dogfight"));
    }

    /**
     * A plasma rail is in neither pool. J4.28: plasma "use a unique type of launch rail which
     * cannot carry drones", so its spaces are not warhead spaces of drones at all.
     *
     * <p>Worth a test because the test for a dogfight mount is "it will not take an ordinary
     * drone", and a plasma rail will not take any drone — so it answers yes to a question it
     * was never asked, and would have landed in FD10.644's pool as free headroom.
     */
    @Test
    public void aPlasmaRailIsInNeitherPool() {
        Ship ship = kzintiShip();
        // A Gorn G-18 in a Kzinti box is not a fleet anyone would field; what is under test is
        // the MOUNT, and the G-18's two PLASMA_D rails are the only kind in the data.
        seatFighter(ship, "g18");

        assertEquals("the hull rack alone", 4.0,
                DroneAvailabilityLimit.countedSpaces(ship), 0.0001);
        assertEquals("and nothing in the dogfight pool", 0.0,
                DroneAvailabilityLimit.dogfightSpaces(ship), 0.0001);
    }
}
