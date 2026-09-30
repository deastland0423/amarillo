package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

/**
 * Drone families, and the invariant a loadout picker can rely on.
 * <p>
 * The naming hides what matters: the suffix is SPEED and nothing else — none is 8 or 12, M is 20,
 * F is 32 — while the numeral is the family. So a type-I and a type-IM are the same drone at two
 * speeds, which no player can guess from the names. Grouping the picker by family with speed as
 * the choice inside it is what makes that legible, and this pins the data that grouping stands on.
 */
public class DroneFamilyTest {

    /** The suffix carries speed and nothing else. That is the whole Type-I vs Type-IM question. */
    @Test
    public void theSuffixIsSpeedAndNothingElse() {
        assertEquals(8, DroneType.TypeI.speed);
        assertEquals(20, DroneType.TypeIM.speed);
        assertEquals(32, DroneType.TypeIF.speed);

        // ...and otherwise they are the same drone.
        for (DroneType d : new DroneType[] { DroneType.TypeI, DroneType.TypeIM, DroneType.TypeIF }) {
            assertEquals(1.0, d.rack, 0.001);
            assertEquals(12, d.damage);
            assertEquals(96, d.endurance);
            assertEquals(DroneType.Family.STANDARD, d.family);
        }
    }

    /** The same pattern across every family that has all three speeds. */
    @Test
    public void everyFamilyIsTheSameDroneAtSeveralSpeeds() {
        assertSameBar("heavy", DroneType.TypeIV, DroneType.TypeIVM, DroneType.TypeIVF);
        assertSameBar("self-guiding", DroneType.TypeIII, DroneType.TypeIIIM, DroneType.TypeIIIF);
        assertSameBar("dogfight", DroneType.TypeVI, DroneType.TypeVIM, DroneType.TypeVIF);
    }

    private void assertSameBar(String what, DroneType... family) {
        for (DroneType d : family) {
            assertEquals(what + ": same space", family[0].rack, d.rack, 0.001);
            assertEquals(what + ": same damage", family[0].damage, d.damage);
            assertEquals(what + ": same family", family[0].family, d.family);
        }
        assertEquals(what + ": three distinct speeds", 3,
                java.util.Arrays.stream(family).map(d -> d.speed).distinct().count());
    }

    /**
     * THE INVARIANT THE PICKER RELIES ON: space and damage are constant within a family, so a
     * family heading can state them once ("Heavy — 2 spaces, 24 damage") and the buttons inside
     * need only differ by speed.
     * <p>
     * If a new drone type ever breaks this, the heading would quietly lie about the buttons under
     * it. That is what this test is for.
     */
    @Test
    public void spaceAndDamageAreConstantWithinAFamily() {
        Map<DroneType.Family, DroneType> first = new EnumMap<>(DroneType.Family.class);
        for (DroneType d : DroneType.values()) {
            DroneType seen = first.putIfAbsent(d.family, d);
            if (seen == null)
                continue;
            assertEquals(d.family + ": " + d + " has a different rack size from " + seen,
                    seen.rack, d.rack, 0.001);
            assertEquals(d.family + ": " + d + " has different damage from " + seen,
                    seen.damage, d.damage);
        }
        assertEquals("every family should be represented",
                DroneType.Family.values().length, first.size());
    }

    /**
     * Endurance is NOT constant within a family, which is why it belongs on each button rather
     * than in the heading. The transitional speed-12 drones are the reason: a type-II is a
     * standard drone that runs out after 64 impulses instead of 96.
     */
    @Test
    public void enduranceVariesWithinAFamilyAndSoBelongsOnTheDrone() {
        assertEquals(DroneType.Family.STANDARD, DroneType.TypeII.family);
        assertEquals(64, DroneType.TypeII.endurance);
        assertEquals(96, DroneType.TypeI.endurance);

        assertEquals(DroneType.Family.HEAVY, DroneType.TypeV.family);
        assertEquals(64, DroneType.TypeV.endurance);
        assertEquals(96, DroneType.TypeIV.endurance);
    }

    /** The trap worth surfacing: a dogfight drone is spent after a single turn. */
    @Test
    public void aDogfightDroneLastsOneTurn() {
        assertEquals("32 impulses is one turn", 32, DroneType.TypeVI.endurance);
        assertTrue(DroneType.TypeVI.isDogfightDrone());
        assertEquals("while a self-guiding drone runs almost indefinitely",
                800, DroneType.TypeIII.endurance);
    }

    /** Every type has a family; none is left unclassified. */
    @Test
    public void everyDroneTypeHasAFamily() {
        for (DroneType d : DroneType.values())
            assertNotNull(d + " has no family", d.family);
    }

    /** And each family has a label fit to head a group with. */
    @Test
    public void everyFamilyHasALabel() {
        for (DroneType.Family f : DroneType.Family.values()) {
            assertNotNull(f.label);
            assertFalse(f + " has a blank label", f.label.isBlank());
        }
    }
}
