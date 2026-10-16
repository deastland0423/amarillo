package com.sfb;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.dto.GameStateDto;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;

/**
 * Saving a damaged ship and loading it back — slice D1 of the saved-game work.
 *
 * <h2>The test before the format</h2>
 * The design note for this feature says to write the round-trip test before choosing the format,
 * and this is it. The problem with a save is not that it fails loudly; it is that it quietly
 * forgets one field, and a battle resumes with a shield back up or a weapon re-armed. No assertion
 * written by hand finds that, because the field nobody thought to save is also the field nobody
 * thinks to assert.
 *
 * <p>So the witness here is {@code ShipDto} — about 158 public fields that the sidebar extracts
 * from a ship, maintained for its own reasons and entirely independently of the save format. The
 * test damages a ship, round-trips it through {@code exportState}/{@code applyState} onto a FRESH
 * ship built from the same hull, and compares the two DTOs field by field by reflection,
 * descending into nested DTOs. Anything the DTO can see and the save cannot carry fails the build
 * and is named.
 *
 * <h2>Reading a failure</h2>
 * A failure lists each differing field as {@code path: before != after}. That is a to-do item, not
 * necessarily a bug: this slice converts only the ship's own scalars, {@code Shields} and
 * {@code HullBoxes}. Fields belonging to groups not yet converted are listed in
 * {@link #KNOWN_GAPS} with the group that owns them, and the test holds them to "still broken" so
 * that converting a group is what removes its entry. If a gap starts passing, the entry is stale
 * and this test says so — the same discipline as {@code ShipJsonKeyGuardTest}'s parked keys.
 */
public class ShipStateRoundTripTest {

    /**
     * DTO fields that do NOT survive a round trip yet, each with the group that owns them.
     *
     * Checked in both directions: an entry that starts surviving is reported as stale, so this
     * list cannot quietly outlive the work it describes.
     */
    private static final Map<String, String> KNOWN_GAPS = Map.of(
            "phaserCapacitor", "Weapons");

    // ---------------------------------------------------------------- the round trip

    /** A CA, beaten about in several different ways, then saved and reloaded. */
    @Test
    public void whatThisSliceCoversSurvivesASaveAndReload() {
        Ship before = damagedCruiser();
        Ship after = roundTrip(before);

        List<String> differences = diffDtos(before, after);
        List<String> unexpected = new ArrayList<>();
        for (String difference : differences) {
            String top = difference.substring(0, Math.max(0, difference.indexOf(':')));
            String owner = KNOWN_GAPS.get(top.contains(".") ? top.substring(0, top.indexOf('.')) : top);
            if (owner == null)
                unexpected.add(difference);
        }

        assertTrue("these fields did not survive a save and reload, and no group claims them:\n  "
                + String.join("\n  ", unexpected), unexpected.isEmpty());
    }

    /**
     * The other half, and the reason the gap list cannot rot: every entry must STILL be broken.
     * Converting a group is what deletes its entry, not a later guess that it is probably fine.
     */
    @Test
    public void theGapListDoesNotOutliveTheGaps() {
        Ship before = damagedCruiser();
        Ship after = roundTrip(before);

        List<String> tops = new ArrayList<>();
        for (String difference : diffDtos(before, after)) {
            String top = difference.substring(0, Math.max(0, difference.indexOf(':')));
            tops.add(top.contains(".") ? top.substring(0, top.indexOf('.')) : top);
        }

        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> gap : KNOWN_GAPS.entrySet())
            if (!tops.contains(gap.getKey()))
                stale.add(gap.getKey() + " (" + gap.getValue() + ")");

        assertTrue("these fields now survive the round trip — delete them from KNOWN_GAPS:\n  "
                + String.join("\n  ", stale), stale.isEmpty());
    }

    // ---------------------------------------------------------------- what this slice does cover

    @Test
    public void damagedShieldsComeBackDamaged() {
        Ship before = damagedCruiser();
        int[] expected = before.getShields().getShieldValues().clone();

        Ship after = roundTrip(before);

        assertArrayEquals("shield strengths are the most visible thing a save could lose",
                expected, after.getShields().getShieldValues());
    }

    @Test
    public void aLoweredShieldStaysDown() {
        Ship before = freshCruiser();
        before.getShields().lowerShield(3);

        Ship after = roundTrip(before);

        assertFalse("shield 3 was down when the game was saved",
                after.getShields().isShieldActive(3));
    }

    @Test
    public void destroyedHullBoxesStayDestroyed() {
        Ship before = damagedCruiser();
        int expected = before.getHullBoxes().fetchRemainingTotalBoxes();

        Ship after = roundTrip(before);

        assertEquals(expected, after.getHullBoxes().fetchRemainingTotalBoxes());
    }

    @Test
    public void positionFacingAndSpeedComeBack() {
        Ship before = freshCruiser();
        before.setLocation(new com.sfb.properties.Location(14, 9));
        before.setFacing(13);
        before.setSpeed(23);

        Ship after = roundTrip(before);

        assertEquals(14, after.getLocation().getX());
        assertEquals(9, after.getLocation().getY());
        assertEquals(13, after.getFacing());
        assertEquals(23, after.getSpeed());
    }

    /** A passive ship must not come back shooting. */
    @Test
    public void fireControlModeComesBack() {
        Ship before = freshCruiser();
        before.setActiveFireControl(false);

        assertFalse(roundTrip(before).isActiveFireControl());
    }

    // ---------------------------------------------------------------- the format's own promises

    /** A save written before a field existed must still load. */
    @Test
    public void aStateMapMissingKeysLeavesTheBuiltValuesAlone() {
        Ship ship = freshCruiser();
        int[] asBuilt = ship.getShields().getShieldValues().clone();

        ship.applyState(Map.of("facing", 9));

        assertEquals(9, ship.getFacing());
        assertArrayEquals("absent keys must not blank out what the hull supplied",
                asBuilt, ship.getShields().getShieldValues());
    }

    @Test
    public void anEmptyOrNullStateMapIsHarmless() {
        Ship ship = freshCruiser();
        ship.applyState(Map.of());
        ship.applyState(null);

        assertTrue(ship.getShields().fetchRemainingTotalBoxes() > 0);
    }

    /**
     * The hull declares its own shape, never the save. A six-shield array restored onto a
     * single-shield ground base would otherwise be an out-of-bounds a long way from here.
     */
    @Test
    public void aSavedShieldArrayOfTheWrongLengthIsResizedToTheHull() {
        Ship ship = freshCruiser();

        ship.applyState(Map.of("shields", Map.of("current", List.of(1, 2))));

        assertEquals("the hull has six shields and keeps six",
                6, ship.getShields().getShieldValues().length);
        assertEquals(1, ship.getShields().getShieldValues()[0]);
    }

    /** The state must survive a trip through JSON-shaped types, since that is how it travels. */
    @Test
    public void stateSurvivesArrivingAsJsonTypes() {
        Ship ship = freshCruiser();

        ship.applyState(Map.of(
                "facing", 5L,                       // a long, as a JSON parser yields
                "speed", 12.0,                      // a double
                "shields", Map.of("current", List.of(10, 9, 8, 7, 6, 5))));

        assertEquals(5, ship.getFacing());
        assertEquals(12, ship.getSpeed());
        assertArrayEquals(new int[] { 10, 9, 8, 7, 6, 5 },
                ship.getShields().getShieldValues());
    }

    // ---------------------------------------------------------------- helpers

    private static Ship freshCruiser() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Testbed");
        ship.setLocation(new com.sfb.properties.Location(10, 10));
        return ship;
    }

    /**
     * Beaten about in as many SEPARATE ways as can be reached from outside.
     *
     * The breadth is the whole point, and the first draft of this test taught it the hard way:
     * it damaged only shields and hull, so weapons, crew and power compared equal between the two
     * ships and the gap list looked satisfied. A round-trip test only detects an omission in
     * state it actually CHANGES — an untouched system matches trivially and proves nothing. So
     * every group this can reach gets disturbed, whether or not the save carries it yet.
     */
    private static Ship damagedCruiser() {
        Ship ship = freshCruiser();

        // Shields and hull — carried by this slice.
        ship.getShields().damageShield(1, 7);
        ship.getShields().damageShield(4, 3);
        ship.getShields().lowerShield(2);
        ship.getHullBoxes().damageFhull();
        ship.getHullBoxes().damageChull();
        ship.getHullBoxes().damageChull();

        // Power, crew and the phaser capacitor — NOT carried yet, and disturbed precisely so
        // that the gap list above has to stay honest about them.
        ship.getPowerSystems().damageLWarp();
        ship.getPowerSystems().damageImpulse();
        ship.getPowerSystems().damageApr();
        ship.getPowerSystems().damageBattery();
        ship.getCrew().setAvailableCrewUnits(ship.getCrew().getAvailableCrewUnits() - 3);
        ship.getCrew().setAvailableBoardingParties(
                ship.getCrew().getAvailableBoardingParties() - 1);
        // Commandos must be NON-ZERO or dropping them from the save changes nothing and the net
        // has a hole in it exactly where D7.0 cares: normal parties and commandos are different
        // troops, and restoring a total would silently convert one into the other.
        ship.getCrew().getFriendlyTroops().commandos = 2;
        ship.getCrew().setCapturedCrew(3);
        ship.getCrew().killDeckCrews(1);
        ship.getPowerSystems().setBatteryPower(2);
        ship.getPowerSystems().setReserveWarp(1);
        try {
            ship.chargeCapacitor(2);
        } catch (Exception e) {
            throw new AssertionError("fixture: a CA should take two points of capacitor", e);
        }

        ship.setFacing(9);
        ship.setSpeed(16);
        ship.setLocation(new com.sfb.properties.Location(21, 17));
        return ship;
    }

    /** Export, build a brand-new ship from the same hull, import. */
    private static Ship roundTrip(Ship original) {
        Map<String, Object> state = original.exportState();
        Ship restored = new Ship();
        restored.init(FederationShips.getFedCa());
        restored.applyState(state);
        return restored;
    }

    /** One ship in a game, so the public GameStateDto path builds its DTO. */
    private static GameStateDto.ShipDto dtoFor(Ship ship) {
        Game game = new Game();
        game.getShips().add(ship);
        for (GameStateDto.MapObjectDto object : new GameStateDto(game).mapObjects)
            if (object instanceof GameStateDto.ShipDto shipDto
                    && ship.getName().equals(shipDto.name))
                return shipDto;
        throw new AssertionError("no ShipDto produced for " + ship.getName());
    }

    private static List<String> diffDtos(Ship before, Ship after) {
        List<String> out = new ArrayList<>();
        compare("", dtoFor(before), dtoFor(after), out, new IdentityHashMap<>());
        return out;
    }

    /**
     * Compare two DTO objects field by field, descending into nested DTOs and lists.
     *
     * Reflective because the point is to cover fields nobody listed by hand — a hand-written set
     * of assertions would miss exactly the field that was also forgotten in the save format.
     */
    private static void compare(String path, Object a, Object b, List<String> out,
            IdentityHashMap<Object, Object> seen) {
        if (a == null && b == null)
            return;
        if (a == null || b == null) {
            out.add(path + ": " + a + " != " + b);
            return;
        }
        if (seen.put(a, b) != null)
            return;     // cycles: a DTO graph may point back at something already walked

        if (a.getClass() != b.getClass()) {
            out.add(path + ": " + a.getClass() + " != " + b.getClass());
            return;
        }
        Class<?> type = a.getClass();

        if (type.isArray() || isLeaf(type)) {
            if (!java.util.Objects.deepEquals(a, b))
                out.add(path + ": " + describe(a) + " != " + describe(b));
            return;
        }
        if (a instanceof List<?> listA && b instanceof List<?> listB) {
            if (listA.size() != listB.size()) {
                out.add(path + ".size: " + listA.size() + " != " + listB.size());
                return;
            }
            for (int i = 0; i < listA.size(); i++)
                compare(path + "[" + i + "]", listA.get(i), listB.get(i), out, seen);
            return;
        }
        if (a instanceof Map<?, ?> mapA && b instanceof Map<?, ?> mapB) {
            if (!mapA.keySet().equals(mapB.keySet())) {
                out.add(path + ".keys: " + mapA.keySet() + " != " + mapB.keySet());
                return;
            }
            for (Object key : mapA.keySet())
                compare(path + "[" + key + "]", mapA.get(key), mapB.get(key), out, seen);
            return;
        }
        // A DTO: walk its public fields.
        for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers()))
                continue;
            try {
                compare(path.isEmpty() ? field.getName() : path + "." + field.getName(),
                        field.get(a), field.get(b), out, seen);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static boolean isLeaf(Class<?> type) {
        return type.isPrimitive() || type.isEnum()
                || Number.class.isAssignableFrom(type)
                || type == String.class || type == Boolean.class || type == Character.class;
    }

    private static String describe(Object value) {
        if (value instanceof int[] ints)
            return java.util.Arrays.toString(ints);
        if (value instanceof double[] doubles)
            return java.util.Arrays.toString(doubles);
        if (value instanceof boolean[] booleans)
            return java.util.Arrays.toString(booleans);
        if (value instanceof Object[] objects)
            return java.util.Arrays.deepToString(objects);
        return String.valueOf(value);
    }
}
