package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

/**
 * Every drone's numbers, as they were before FD10.2's composition existed.
 *
 * <h2>Why the old values are written out longhand here</h2>
 * {@link DroneType} used to carry nine numbers per constant, typed in from the drone chart. It
 * now carries four of them derived — {@code speed} from the propulsion section, {@code rack} and
 * {@code hull} from the frame, {@code damage} from the sum of the payload modules (FD10.41) —
 * and a refactor that changes a number while claiming to change none is the failure this is
 * built to catch.
 *
 * <p>So the table below is the enum as it stood beforehand, copied before the change and
 * compared after it. It is the same device {@code RefitResolverTest} uses to carry 128 deleted
 * ship files forward: the expectations ARE the old data, and they are only worth anything
 * because they were written down first.
 *
 * <h2>What is derived and what is not</h2>
 * Derivation stops where the rulebook's does. Endurance, guidance and availability stay declared
 * on the constant, because no clause computes them from frame, propulsion or payload — a model
 * that derived them would be inventing rules to do it. The test covers all nine fields either
 * way, since a value that was supposed to stay put is exactly as easy to break.
 */
public class DroneSpecDerivationTest {

    /** name, endurance, speed, damage, rack, hull, selfGuiding, warpSeeker, year, family. */
    private record Row(DroneType type, int endurance, int speed, int damage, double rack,
                       int hull, boolean selfGuiding, boolean warpSeeker, int year,
                       DroneType.Family family) { }

    private static final List<Row> BEFORE = List.of(
        new Row(DroneType.TypeI,     96,  8, 12, 1.0, 4, false, false,   0, DroneType.Family.STANDARD),
        new Row(DroneType.TypeIM,    96, 20, 12, 1.0, 4, false, false, 165, DroneType.Family.STANDARD),
        new Row(DroneType.TypeIF,    96, 32, 12, 1.0, 4, false, false, 178, DroneType.Family.STANDARD),
        new Row(DroneType.TypeIV,    96,  8, 24, 2.0, 6, false, false,   0, DroneType.Family.HEAVY),
        new Row(DroneType.TypeIVM,   96, 20, 24, 2.0, 6, false, false, 165, DroneType.Family.HEAVY),
        new Row(DroneType.TypeIVF,   96, 32, 24, 2.0, 6, false, false, 178, DroneType.Family.HEAVY),
        new Row(DroneType.TypeII,    64, 12, 12, 1.0, 4, false, false,  77, DroneType.Family.STANDARD),
        new Row(DroneType.TypeV,     64, 12, 24, 2.0, 6, false, false,  77, DroneType.Family.HEAVY),
        new Row(DroneType.TypeIII,  800, 12, 12, 1.0, 4,  true, false,   0, DroneType.Family.SELF_GUIDING),
        new Row(DroneType.TypeIIIM, 800, 20, 12, 1.0, 4,  true, false, 165, DroneType.Family.SELF_GUIDING),
        new Row(DroneType.TypeIIIF, 800, 32, 12, 1.0, 4,  true, false, 178, DroneType.Family.SELF_GUIDING),
        new Row(DroneType.TypeVI,    32, 12,  8, 0.5, 3,  true,  true,   0, DroneType.Family.DOGFIGHT),
        new Row(DroneType.TypeVIM,   32, 20,  8, 0.5, 3,  true,  true, 165, DroneType.Family.DOGFIGHT),
        new Row(DroneType.TypeVIF,   32, 32,  8, 0.5, 3,  true,  true, 178, DroneType.Family.DOGFIGHT));

    @Test
    public void everyDroneKeepsTheNumbersItHadBeforeItWasComposed() {
        assertEquals("every drone in the enum must be listed here", DroneType.values().length,
                BEFORE.size());

        for (Row r : BEFORE) {
            String who = r.type().name();
            assertEquals(who + " endurance", r.endurance(), r.type().endurance);
            assertEquals(who + " speed",     r.speed(),     r.type().speed);
            assertEquals(who + " damage",    r.damage(),    r.type().damage);
            assertEquals(who + " rack",      r.rack(),      r.type().rack, 0.0001);
            assertEquals(who + " hull",      r.hull(),      r.type().hull);
            assertEquals(who + " selfGuiding", r.selfGuiding(), r.type().selfGuiding);
            assertEquals(who + " warpSeeker",  r.warpSeeker(),  r.type().warpSeeker);
            assertEquals(who + " availableFromYear", r.year(), r.type().availableFromYear);
            assertEquals(who + " family",    r.family(),    r.type().family);
        }
    }

    /**
     * And the derived four come from the composition rather than from a coincidence.
     *
     * <p>Worth separating because {@code damage} currently agrees with the FRAME on every drone
     * in the game — a one-space drone does 12, a two-space does 24 — so a model that read the
     * warhead off the frame would pass the table above completely. FD10.41 says it is the sum of
     * the modules, and the two readings part company the moment a drone carries a probe module,
     * a null payload (FD10.48), or four half-space explosives instead of two full ones.
     */
    @Test
    public void theDerivedFieldsComeFromTheComposition() {
        for (DroneType t : DroneType.values()) {
            DroneSpec spec = t.spec;
            assertEquals(t + " speed is the propulsion section", spec.propulsion().speed, t.speed);
            assertEquals(t + " rack footprint is the frame", spec.frame().rackSpaces, t.rack, 0.0001);
            assertEquals(t + " hull is the frame", spec.frame().hull, t.hull);

            int fromModules = 0;
            for (PayloadModule m : spec.allModules())
                fromModules += m.strength;
            assertEquals(t + " warhead is the SUM of its payload modules (FD10.41)",
                    fromModules, t.damage);
        }
    }

    /**
     * FD10.41: "Two-space drones (such as type-IV) carry two one-payload space explosive
     * modules." The heavy drones' 24 is two twelves, not one twenty-four.
     */
    @Test
    public void aHeavyDroneCarriesTwoExplosiveModulesRatherThanOneLargeOne() {
        assertEquals(List.of(PayloadModule.EXPLOSIVE_1, PayloadModule.EXPLOSIVE_1),
                DroneType.TypeIV.spec.allModules());
        assertEquals("and they combine into one explosion, not two volleys",
                24, DroneType.TypeIV.spec.warheadStrength());
    }

    /**
     * FD10.21 and FD2.51: the half-space frame takes no payload at all, and only its speed can
     * be varied. Offering it modules is a coding mistake, so it throws.
     */
    @Test
    public void theDogfightFrameRefusesAPayload() {
        assertTrue("all three type-VIs are on the half-space frame",
                DroneType.TypeVI.spec.frame() == DroneFrame.HALF_SPACE
                        && DroneType.TypeVIM.spec.frame() == DroneFrame.HALF_SPACE
                        && DroneType.TypeVIF.spec.frame() == DroneFrame.HALF_SPACE);
        assertEquals("it carries a fixed warhead rather than a chosen one",
                List.of(PayloadModule.DOGFIGHT_WARHEAD), DroneType.TypeVI.spec.allModules());

        try {
            DroneSpec.of(DroneFrame.HALF_SPACE, Propulsion.SPEED_12, PayloadModule.EXPLOSIVE_1);
            fail("a half-space frame must refuse a payload module (FD10.21)");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FD10.21"));
        }
    }

    /**
     * The dogfight warhead is EIGHT here and that is deliberate, not a stray six.
     *
     * <p>A half-space explosive module is strength six (FD10.41), but a type-VI is not built
     * from one. FD2.54 scales its warhead by the target: two points against size class 4 and
     * larger, four against size class 5, eight against size class 6 and 7. Eight is the figure
     * {@code DroneType.damage} has always carried, and {@code Drone.impact(Unit)} — which
     * implemented FD2.54 long before this model existed and was left untouched — reads it as
     * the nominal before applying the rule.
     */
    @Test
    public void theDogfightWarheadIsTheNominalAndTheRuleStaysInDrone() {
        assertEquals(8, DroneType.TypeVI.damage);
        assertEquals(8, PayloadModule.DOGFIGHT_WARHEAD.strength);
        assertNotEquals("deliberately not the half-space explosive module's six",
                PayloadModule.EXPLOSIVE_HALF.strength, PayloadModule.DOGFIGHT_WARHEAD.strength);

        Drone dogfight = new Drone(DroneType.TypeVI);
        Ship ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        assertEquals("FD2.54 still applies against a ship, and still lives in Drone",
                Drone.DOGFIGHT_DAMAGE_VS_SHIP, dogfight.impact(ship));
    }

    /**
     * FD10.24: a type-III given extended range "become[s] a two-space drone with ONE payload
     * space" — the case that makes rack footprint and payload capacity two fields rather than
     * one. No drone in the game is built this way yet; the frame can already express it.
     */
    @Test
    public void aFrameCanHaveFewerPayloadSpacesThanItTakesInTheRack() {
        assertEquals(2.0, DroneFrame.TWO_SPACE.rackSpaces, 0.0001);
        assertEquals(2, DroneFrame.TWO_SPACE.payloadSpaces);

        DroneSpec typeIIIXX = DroneSpec.of(
                DroneFrame.TWO_SPACE, Propulsion.SPEED_12, PayloadModule.EXPLOSIVE_1);
        assertEquals("two rack spaces", 2.0, typeIIIXX.rackSpaces(), 0.0001);
        assertEquals("one payload used, the other spent on extended range",
                12, typeIIIXX.warheadStrength());
    }

    /** And a payload that does not fit is refused (FD10.4). */
    @Test
    public void aPayloadLargerThanTheFrameIsRefused() {
        try {
            DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_8,
                    PayloadModule.EXPLOSIVE_1, PayloadModule.EXPLOSIVE_1);
            fail("two full explosive modules cannot fit a one-space frame");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("exceeds"));
        }
    }
}
