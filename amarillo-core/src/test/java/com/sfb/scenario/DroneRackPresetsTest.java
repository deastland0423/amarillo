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
     * A preset can cost real points, and in the early years it does.
     *
     * <p>At Y145 the fastest drone the standard family hands out freely is the speed-12 type-II —
     * speed 12 has been General since Y120 — while the ship itself was only charged for speed 8,
     * because S2.11 fits the year and medium drones do not arrive until Y165. So the difference
     * is the player's to pay, and FD2.11 names it: "one type-I with one type-II is 0.50", four of
     * them 2.00.
     *
     * <p>Worth pinning because for most of the game's span these buttons ARE free, which made it
     * easy to conclude they always would be. They are free from the year a speed becomes standard
     * issue, when the engine the preset buys and the credit S2.11 already granted cancel out —
     * which is the test below.
     */
    @Test
    public void aPresetCanCostTheBudgetSomething() {
        DroneRack rack = firstRack(kzinCs());
        double dearest = 0;
        for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 145))
            dearest = Math.max(dearest, p.surcharge());

        assertEquals("FD2.11: four type-IIs where four type-Is were is 4 x 0.50",
                2.0, dearest, EXACT);
    }

    /**
     * And from the year its speed is standard issue, the same button is free.
     *
     * <p>Y180 hands out fast drones to everyone, so S2.11 has already charged the ship for speed
     * 32 and a preset filling the rack with it adds nothing. The rest of FD2.11's uncapped trades
     * are at par for the same kind of reason: "two type-Is with one type-IV is 0.00", "one type-I
     * with two type-VIs is 0.00", and every General payload item is explosive at the half point a
     * space that a type-I-slow sets.
     */
    @Test
    public void andFromTheYearItsSpeedIsStandardIssueItIsFree() {
        for (Ship ship : List.of(kzinCs(), fedOcl())) {
            DroneRack rack = firstRack(ship);
            for (DroneRackPresets.Preset p : DroneRackPresets.forRack(rack, 180))
                assertEquals(p.drones() + " is at FD2.11 par once Y180 pays for the speed",
                        0.0, p.surcharge(), EXACT);
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
