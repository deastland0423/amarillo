package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack.DroneRackType;

/**
 * Which drones a rack will take: FD2.51 one way, FD3.5 the other.
 *
 * <h2>Why this did not exist until now</h2>
 * {@code DroneRack.accepts} is one of three validations {@code ScenarioLoader.applyCoi} runs on
 * a Commander's Option drone loadout, beside the availability year and the speed cap. A mutation
 * run over all three on 2026-10-24 found that deleting the year check and deleting the speed cap
 * each failed a test, and <b>deleting this one failed nothing at all</b> — no test in the build
 * loaded a drone its rack refuses.
 *
 * <p>That matters more now than it did: the drone model (FD10.2) makes the frame the thing a
 * rack measures, so this rule is about to be leaned on rather than merely present.
 *
 * <h2>The rule, both directions</h2>
 * FD2.51: dogfight drones "cannot be loaded on or fired by any drone racks except E and G (and
 * ADDs)", and "type-H drone racks also include a magazine of type-VI drones".
 * <p>
 * FD3.5, about the type-E: "The 'E' drone rack holds eight dogfight drones. <b>It can carry no
 * other types.</b>"
 * <p>
 * Two clauses, and they are not the same statement — one restricts where a dogfight drone may
 * go, the other restricts what one rack may hold. A rack could satisfy either alone and still be
 * wrong, so both are asserted over the whole cross-product rather than on examples.
 */
public class DroneRackAcceptsTest {

    /** FD2.51: the only racks a dogfight drone may be loaded into. */
    private static final Set<DroneRackType> DOGFIGHT_RACKS =
            EnumSet.of(DroneRackType.TYPE_E, DroneRackType.TYPE_G, DroneRackType.TYPE_H);

    private static List<DroneType> dogfightDrones() {
        List<DroneType> out = new ArrayList<>();
        for (DroneType t : DroneType.values())
            if (t.isTypeVI())
                out.add(t);
        return out;
    }

    private static List<DroneType> ordinaryDrones() {
        List<DroneType> out = new ArrayList<>();
        for (DroneType t : DroneType.values())
            if (!t.isTypeVI())
                out.add(t);
        return out;
    }

    /**
     * The whole cross-product of rack type and drone type, in one assertion, so a new rack type
     * or a new drone cannot slip in without this having an opinion about it.
     */
    @Test
    public void everyRackAndDroneCombinationFollowsTheTwoRules() {
        List<String> wrong = new ArrayList<>();
        int checked = 0;

        for (DroneRackType rackType : DroneRackType.values()) {
            DroneRack rack = new DroneRack(rackType);
            for (DroneType drone : DroneType.values()) {
                checked++;
                boolean expected = rackType == DroneRackType.TYPE_E
                        ? drone.isTypeVI()                       // FD3.5: E carries nothing else
                        : !drone.isTypeVI() || DOGFIGHT_RACKS.contains(rackType);  // FD2.51
                if (rack.accepts(drone) != expected)
                    wrong.add(rackType + " / " + drone + ": expected "
                            + (expected ? "accepted" : "refused"));
            }
        }

        assertEquals("fixture: 8 rack types times 14 drones", 8 * 14, checked);
        assertTrue(String.join("\n  ", wrong), wrong.isEmpty());
    }

    /** FD3.5 stated on its own, because it is the half nothing enforced for a long time. */
    @Test
    public void theTypeERackCarriesDogfightDronesAndNothingElse() {
        DroneRack e = new DroneRack(DroneRackType.TYPE_E);

        for (DroneType vi : dogfightDrones())
            assertTrue("a type-E holds " + vi, e.accepts(vi));
        for (DroneType other : ordinaryDrones())
            assertFalse("FD3.5: a type-E can carry no other types, and that includes " + other,
                    e.accepts(other));
    }

    /** FD2.51 stated on its own: E, G and H, and nowhere else. */
    @Test
    public void aDogfightDroneGoesOnlyInTheThreeRacksThatTakeOne() {
        for (DroneRackType rackType : DroneRackType.values()) {
            DroneRack rack = new DroneRack(rackType);
            boolean allowed = DOGFIGHT_RACKS.contains(rackType);
            for (DroneType vi : dogfightDrones())
                assertEquals(rackType + " and " + vi, allowed, rack.accepts(vi));
        }
    }

    /**
     * An ordinary drone goes in any rack but the type-E — including the type-G and type-H,
     * which take dogfight drones WITHOUT being restricted to them the way the E is.
     */
    @Test
    public void anOrdinaryDroneGoesAnywhereExceptTheTypeE() {
        for (DroneRackType rackType : DroneRackType.values()) {
            if (rackType == DroneRackType.TYPE_E)
                continue;
            DroneRack rack = new DroneRack(rackType);
            for (DroneType ordinary : ordinaryDrones())
                assertTrue(rackType + " should take " + ordinary, rack.accepts(ordinary));
        }
    }

    /** A null type is not a drone, and asking is not an error. */
    @Test
    public void aNullDroneIsRefusedRatherThanThrowing() {
        assertFalse(new DroneRack(DroneRackType.TYPE_A).accepts(null));
        assertFalse(new DroneRack(DroneRackType.TYPE_E).accepts(null));
    }
}
