package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * A drone out of FD10.4's workshop, ordered the way a player orders one.
 *
 * <h2>What this closes</h2>
 * Armor, ECM, probe, swordfish and multi-warhead modules have been built, priced and capped for
 * a while, and {@code DroneAvailabilityLimitTest} proved the caps on an armored type-IV by
 * reaching into the rack and putting one there by hand. Nothing a player could submit could ask
 * for one: a loadout was a list of {@code DroneType}, and the enum is the fourteen drones
 * history fielded. Every test of a composed drone had to bypass the only door into the game.
 *
 * <p>So this is deliberately end-to-end: the loadout a player builds, through
 * {@code ScenarioLoader.applyCoi}, to the drone in the rack. Testing it anywhere else would
 * prove the workshop again and leave the door shut.
 */
public class CoiComposedDroneTest {

    /** A Federation FFG: one type-G rack, four spaces. Not Kzinti, not a carrier, not D%. */
    private static Ship ordinaryShip() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedFfg());
        return ship;
    }

    private static DroneRack rackOf(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack r)
                return r;
        throw new AssertionError("fixture needs a drone rack");
    }

    private static ScenarioSpec year(int y) {
        ScenarioSpec spec = new ScenarioSpec();
        spec.year = y;
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        return spec;
    }

    private static CoiLoadout rackLoad(String... drones) {
        List<DroneLoad> loads = new ArrayList<>();
        for (String d : drones)
            loads.add(DroneLoad.parse(d));
        CoiLoadout out = new CoiLoadout();
        out.setDroneRackLoads(0, loads);
        return out;
    }

    private static String notesOf(Ship ship) {
        return String.join(" | ", ship.getSetupNotes());
    }

    // ------------------------------------------------------------------ it arrives

    /**
     * FD12.12's armored type-IV, ordered: three 1/2-PS armor modules and a 1/2-PS explosive.
     * Twelve hull behind a strength-six warhead, where a standard type-IV is six and
     * twenty-four.
     */
    @Test
    public void anArmoredTypeIvOrderedAsAnOptionReachesTheRack() {
        Ship ship = ordinaryShip();
        CoiLoadout loadout =
                rackLoad("TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF", "TypeI");
        ScenarioLoader.applyCoi(ship, loadout, year(180));

        List<Drone> ammo = rackOf(ship).getAmmo();
        assertEquals("the rack holds what was ordered and nothing else", 2, ammo.size());

        Drone armored = ammo.get(0);
        assertEquals("FD12.21: six frame hull plus two per armor module", 12, armored.getHull());
        assertEquals("FD10.41: one half-space explosive is the whole warhead",
                6, armored.getWarheadDamage());
        assertEquals("still two rack spaces", 2.0, armored.getRackSize(), 0.0001);
        assertEquals("and still a type-IV for anything asking what KIND it is",
                DroneType.TypeIV, armored.getDroneType());
        assertEquals("including the endurance no rule derives from the composition",
                DroneType.TypeIV.endurance, armored.getEndurance());

        assertEquals("the ordinary drone beside it is untouched",
                DroneType.TypeI.damage, ammo.get(1).getWarheadDamage());

        // Not silence: two Restricted spaces in three is half the ship's drones, and FD10.622
        // allows a quarter. The drone IS loaded and the breach IS noted, which is the right
        // pair -- applyCoi reports rather than refuses, so the player can see what they built.
        // oneSpecialInFourSpacesIsLetAlone below is the legal proportion.
        assertTrue(notesOf(ship), notesOf(ship).contains("FD10.622"));
    }

    /** FD10.44's ECM drone, which has no warhead at all and was equally unorderable. */
    @Test
    public void anEcmDroneReachesTheRack() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeI+ECM", "TypeI", "TypeI", "TypeI"),
                year(180));

        List<Drone> ammo = rackOf(ship).getAmmo();
        assertEquals(4, ammo.size());
        assertEquals("an ECM module is no warhead", 0, ammo.get(0).getWarheadDamage());
        assertEquals("and the three ordinary drones beside it still have theirs",
                DroneType.TypeI.damage, ammo.get(3).getWarheadDamage());
        assertEquals("", notesOf(ship));
    }

    // ------------------------------------------------------------------ it is paid for

    /**
     * FD2.22's surcharge, on a drone that can now be ordered: a swordfish module costs a point
     * (FD10.51) where the explosive it displaces costs half, against a type-I-slow baseline of
     * one point per space.
     */
    @Test
    public void aSwordfishModuleIsChargedAgainstTheOptionBudget() {
        CoiLoadout loadout = rackLoad("TypeI+SWORDFISH_PHASER3");

        assertEquals("1.50 for the drone less the 1.00 already in the ship's BPV",
                0.50, loadout.droneUpgradeCost(), 0.0001);
        assertEquals("and it is in the total the server checks the budget against",
                0.50, loadout.totalCost(), 0.0001);
    }

    /**
     * And the armored type-IV is FREE, which is not a bug in the subtraction.
     *
     * <p>FD10.5's charts price three half-space armor modules and a half-space explosive at
     * exactly what the two explosive modules of a standard type-IV cost, and both drones fill
     * the same two spaces. FD2.11's own trade table works the same way — "two type-Is with one
     * type-IV is 0.00" — so a drone that buys no engine and no exotic module costs its owner
     * nothing however differently it is arranged.
     */
    @Test
    public void rearrangingAPayloadWithinItsSpacesCostsNothing() {
        CoiLoadout loadout =
                rackLoad("TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF");

        assertEquals(0.0, loadout.droneUpgradeCost(), 0.0001);
    }

    // ------------------------------------------------------------------ it is capped

    /**
     * The point of the whole slice, and the thing that could not be reached before: FD10.632's
     * 10% Limited cap, breached by a drone nobody could previously order.
     *
     * <p>A swordfish module is Limited Availability (FD10.631). Four of them fill the FFG's
     * four-space rack, so every space aboard is Limited where the hull is allowed 10% of
     * them — and the cap is consulted by {@code applyCoi} itself, not by a test reaching
     * into the rack afterwards.
     */
    @Test
    public void applyCoiCapsAComposedDroneItHasJustLoaded() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeI+SWORDFISH_PHASER3",
                "TypeI+SWORDFISH_PHASER3", "TypeI+SWORDFISH_PHASER3",
                "TypeI+SWORDFISH_PHASER3"), year(180));

        String notes = notesOf(ship);
        assertTrue("four Limited spaces out of four, but applyCoi said: " + notes,
                notes.contains("FD10.632"));
    }

    /** One of them in four is within both caps, and draws no complaint. */
    @Test
    public void oneSpecialInFourSpacesIsLetAlone() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeI+ECM", "TypeI", "TypeI", "TypeI"),
                year(180));

        String notes = notesOf(ship);
        assertFalse("one Restricted space in four is 25%, which is the allowance: " + notes,
                notes.contains("FD10.632") || notes.contains("FD10.622"));
    }

    // ------------------------------------------------------------------ it is checked

    /** A composed drone is measured in the rack like any other: FD3.0 counts spaces. */
    @Test
    public void aComposedDroneIsMeasuredAgainstTheRackSize() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad(
                "TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF",
                "TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF",
                "TypeI"), year(180));

        String notes = notesOf(ship);
        assertTrue("five spaces into a four-space rack, but applyCoi said: " + notes,
                notes.contains("exceeds rack size"));
        assertEquals("and nothing was loaded", DroneType.TypeI,
                rackOf(ship).getAmmo().get(0).getDroneType());
    }

    /**
     * FD8.11's submunition speeds are a year gate of their own. The bus has been flying since
     * the beginning; its type-VIs had not been invented in Y100 (FD10.651).
     */
    @Test
    public void aModificationThatOutrunsItsYearIsRefused() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeIV+MW_LARGE+SPEED_32"), year(100));

        String notes = notesOf(ship);
        assertTrue("speed-32 submunitions in Y100, but applyCoi said: " + notes,
                notes.contains("not available in year 100"));
    }

    /**
     * The note names the drone in full, because "TypeIV not available" would be a lie about a
     * drone that has been available since Y0 — it is the modification that is impossible.
     */
    @Test
    public void theRefusalNamesTheWholeDrone() {
        Ship ship = ordinaryShip();
        ScenarioLoader.applyCoi(ship, rackLoad("TypeIV+MW_LARGE+SPEED_32"), year(100));

        assertTrue(notesOf(ship), notesOf(ship).contains("TypeIV+MW_LARGE+SPEED_32"));
    }
}
