package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KzintiShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * The ready-made rack loadouts, and the two claims that make them safe to put on a button:
 * every preset is legal in the rack that was asked, and none of them spends a percentage slot.
 */
public class DroneRackPresetsTest {

    private static final double EXACT = 1e-9;

    private static DroneRack firstRack(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture needs a drone rack");
    }

    private static Ship kzinCs() {
        Ship ship = new Ship();
        ship.init(KzintiShips.getKzinCS());
        return ship;
    }

    private static Ship fedOcl() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedOcl());   // carries a type-G rack
        return ship;
    }

    // ---------------------------------------------------------------- legal by construction

    /** FD3.5, FD10.65 and FD3.0: the rack takes it, the year has it, and it fits. */
    @Test
    public void everyPresetIsLegalInTheRackItWasAskedFor() {
        DroneRack rack = firstRack(kzinCs());
        List<DroneRackPresets.Preset> presets = DroneRackPresets.forRack(rack, 180);
        assertFalse("premise: a Kzinti rack has presets to offer", presets.isEmpty());

        for (DroneRackPresets.Preset p : presets) {
            double spaces = p.antiDrones() * DroneRack.ANTI_DRONE_SPACE;
            for (DroneLoad d : p.drones()) {
                assertTrue(d + " must be loadable in a " + rack.getRackType(),
                        rack.accepts(d.basis()));
                assertTrue(d + " must exist in Y180", d.availableIn(180));
                spaces += d.rackSpaces();
            }
            assertTrue("a preset must fit: " + spaces + " > " + rack.getSpaces(),
                    spaces <= rack.getSpaces());
        }
    }

    /**
     * FD10.62/FD10.63 cap special drones against the drones the whole SHIP carries, so a preset
     * - which sees one rack - must never offer one. General Availability (FD10.61) is uncapped,
     * and so is legal in every rack at once.
     */
    @Test
    public void noPresetSpendsAPercentageSlot() {
        for (int year : new int[] {160, 166, 170, 175, 179, 180}) {
            for (Ship ship : List.of(kzinCs(), fedOcl())) {
                DroneRack rack = firstRack(ship);
                for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, year))
                    for (DroneLoad d : p.drones())
                        assertEquals(d + " in Y" + year + " must be uncapped",
                                Availability.GENERAL, d.availabilityIn(year));
            }
        }
    }

    // ---------------------------------------------------------------- what they contain

    /**
     * The ship has already paid the year's speed charge under S2.11, so a preset offers the
     * fastest drone the year hands out freely rather than the cheapest. FD10.65 has fast drones
     * Restricted in Y179 and General in Y180, which is the whole difference between these two.
     */
    @Test
    public void theYearPicksTheSpeed() {
        DroneRack rack = firstRack(kzinCs());

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
            for (DroneLoad d : p.drones())
                assertEquals("Y180 hands out fast drones: " + d, 32, d.speed());

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 179))
            for (DroneLoad d : p.drones())
                assertEquals("fast is still Restricted in Y179: " + d, 20, d.speed());
    }

    /**
     * The standard load's speed, year by year: type-I-slow to Y166, type-I-medium from Y167,
     * type-I-fast from Y180 on. FD10.65's General column, which is the only column a ship is
     * fitted from without spending anything (S2.11), and the ladder the owner stated.
     *
     * <p>Nothing between those steps, which is the point of the test: speed 12 has been General
     * since Y120 and never appears, because FD2.22 does not count it as an improvement and a
     * type-II is a drone a player buys rather than one a year fits (FD2.31).
     */
    @Test
    public void theStandardLoadFollowsTheYearsOwnLadder() {
        assertEquals("Y120", DroneType.TypeI, standardFamilyPick(120));
        assertEquals("Y145", DroneType.TypeI, standardFamilyPick(145));
        assertEquals("Y166", DroneType.TypeI, standardFamilyPick(166));
        assertEquals("Y167", DroneType.TypeIM, standardFamilyPick(167));
        assertEquals("Y179", DroneType.TypeIM, standardFamilyPick(179));
        assertEquals("Y180", DroneType.TypeIF, standardFamilyPick(180));
        assertEquals("Y200", DroneType.TypeIF, standardFamilyPick(200));
    }

    /**
     * Which standard-family drone a Kzinti rack is offered in this year.
     *
     * <p>The rack is first loaded with heavy drones so the standard preset cannot be suppressed
     * as a repeat of what it already holds -- which is what would happen on a real ship, since
     * {@code applyDroneSpeed} fits exactly this drone before the player is asked anything.
     */
    private static DroneType standardFamilyPick(int year) {
        Ship ship = kzinCs();
        DroneRack rack = firstRack(ship);
        rack.setAmmo(new ArrayList<>(List.of(
                DroneLoad.of(DroneType.TypeIV).build(),
                DroneLoad.of(DroneType.TypeIV).build())));

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, year))
            for (DroneLoad d : p.drones())
                if (d.basis().family == DroneType.Family.STANDARD)
                    return d.basis();
        throw new AssertionError("no standard-family preset offered in Y" + year);
    }

    /** One per family the rack can hold, so a Kzinti rack offers its heavy drones. */
    @Test
    public void aHeavyLoadoutIsOffered() {
        DroneRack rack = firstRack(kzinCs());
        boolean heavy = false;
        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
            for (DroneLoad d : p.drones())
                if (d.basis().family == DroneType.Family.HEAVY)
                    heavy = true;
        assertTrue("a Kzinti rack should offer a rack of heavy drones", heavy);
    }

    /** FD3.0 counts whole drones, so a magazine too small for one offers no half of it. */
    @Test
    public void aRackTooSmallForATwoSpaceDroneIsNotOfferedOne() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_B);
        rack.setSpaces(1);

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
            for (DroneLoad d : p.drones())
                assertTrue(d + " cannot fit a 1-space rack", d.rackSpaces() <= 1);
    }

    /**
     * FD3.70: a type-G's anti-drone loading "must always be planned", and nothing reloads an ADD
     * in play (E5.74), so a full set is exactly the kind of decision a button should make.
     */
    @Test
    public void aTypeGOffersAFullLoadOfAntiDrones() {
        DroneRack rack = firstRack(fedOcl());
        assertTrue("premise: the OCL rack is a type-G", rack.acceptsAntiDrones());

        int offered = 0;
        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 175))
            if (p.antiDrones() > 0) {
                assertTrue("an ADD preset carries no drones beside them", p.drones().isEmpty());
                offered = p.antiDrones();
            }
        assertEquals("a full set fills the magazine", rack.fullAntiDroneSet(), offered);
    }

    /** And a rack without the targeting system is not offered any (FD3.70). */
    @Test
    public void aRackWithoutTheAntiDroneSystemIsNotOfferedAny() {
        DroneRack rack = firstRack(kzinCs());
        assertFalse("premise: a Kzinti type-B/C rack has no ADD system", rack.acceptsAntiDrones());

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
            assertEquals("no ADDs without the system", 0, p.antiDrones());
    }

    /** And not before Y140, which is the year the round exists (DroneRack.ANTI_DRONE_FIRST_YEAR). */
    @Test
    public void aTypeGIsNotOfferedAntiDronesBeforeTheyExist() {
        DroneRack rack = firstRack(fedOcl());
        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 139))
            assertEquals("no ADDs in Y139", 0, p.antiDrones());
        assertTrue("premise: Y140 does offer them",
                DroneRackPresets.forRack(rack, 140).stream().anyMatch(p -> p.antiDrones() > 0));
    }

    // ---------------------------------------------------------------- and the price

    /** The figure on the button is the figure the budget will be charged (S3.2). */
    @Test
    public void theSurchargeIsWhatTheRackWouldBeBilled() {
        DroneRack rack = firstRack(kzinCs());
        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
            assertEquals(p.drones() + " must be priced as it will be billed",
                    CoiLoadout.rackUpgradeCost(p.drones(), 180), p.surcharge(), EXACT);
    }

    /**
     * Never a speed-12 drone, which is the whole of FD2.31 and FD2.22 said as a test.
     *
     * <p>FD2.31 has every rack "presumed to be loaded with type-I-slow drones... included in the
     * BPV of the ship", and FD2.22's list of what a year refits a ship with names four things,
     * of which moderate speed is not one. So a type-II is not a faster type-I: it is its own
     * drone with two turns of endurance against the type-I's three (FD2.1), and FD2.11 charges
     * half a point to load one. Same for the type-V against the type-IV.
     *
     * <p>This selected the family's fastest member, which in Y145 is the type-II — speed 12 has
     * been General since Y120 — and so offered a shorter-legged drone as an upgrade and billed
     * two points for a rack of them.
     */
    @Test
    public void noPresetOffersASpeed12SpecialDrone() {
        for (int year : new int[] {100, 120, 145, 160, 166, 170, 175, 179, 180, 185}) {
            for (Ship ship : List.of(kzinCs(), fedOcl())) {
                DroneRack rack = firstRack(ship);
                for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, year))
                    for (DroneLoad d : p.drones())
                        assertFalse(d + " in Y" + year + " is a special purchase (FD2.11),"
                                        + " not the speed the year fits",
                                d.basis() == DroneType.TypeII || d.basis() == DroneType.TypeV);
            }
        }
    }

    /**
     * And so a preset never costs the budget anything.
     *
     * <p>It offers the drone the year itself would fit, and FD2.11 exchanges those at par: "two
     * type-Is with one type-IV is 0.00", "one type-I with two type-VIs is 0.00". A General speed
     * is one S2.11 has already charged the ship for, so the engine the preset buys and the credit
     * it carries cancel. Every General payload item is explosive at the half point a space a
     * type-I-slow sets. The only uncapped drones that cost — the type-II and type-V — are the
     * ones the test above keeps out.
     *
     * <p>So replacing {@code forRack}'s price with a literal zero is an equivalent mutation
     * today, and survives on purpose: this test is what will notice the day it stops being one.
     */
    @Test
    public void everyPresetIsFreeBecauseTheLadderIsAtPar() {
        for (int year : new int[] {100, 120, 145, 160, 166, 170, 175, 179, 180, 185}) {
            for (Ship ship : List.of(kzinCs(), fedOcl())) {
                DroneRack rack = firstRack(ship);
                for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, year))
                    assertEquals(p.drones() + " in Y" + year + " must be at FD2.11 par",
                            0.0, p.surcharge(), EXACT);
            }
        }
    }

    // ---------------------------------------------------------------- and no duplicates

    /**
     * The dialog already offers the rack's own ammunition, as "edit this load" - it copies what
     * the ship sails with into the builder so one drone can be changed. A preset saying the same
     * thing would be two buttons for one outcome.
     */
    @Test
    public void aPresetRepeatingWhatTheRackHoldsIsNotOffered() {
        Ship ship = kzinCs();
        DroneRack rack = firstRack(ship);

        // Fill it with the load a Y180 preset would otherwise offer, then ask again.
        List<DroneRackPresets.Preset> before = DroneRackPresets.forRack(rack, 180);
        assertFalse("premise: there is something to offer first", before.isEmpty());
        DroneRackPresets.Preset first = before.get(0);

        List<Drone> ammo = new ArrayList<>();
        for (DroneLoad d : first.drones())
            ammo.add(d.build());
        rack.setAmmo(ammo);

        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180)) {
            boolean same = p.drones().size() == first.drones().size();
            if (same)
                for (int i = 0; i < p.drones().size(); i++)
                    if (!p.drones().get(i).format().equals(first.drones().get(i).format()))
                        same = false;
            assertFalse("the load the rack already holds must not be offered again", same);
        }
    }
}
