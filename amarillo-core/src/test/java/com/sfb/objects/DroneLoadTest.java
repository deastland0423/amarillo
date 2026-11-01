package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * {@link DroneLoad} — the loadout line that can finally name a drone from FD10.4's workshop.
 *
 * <p>Everything here is about the two things the class is for: that a composed drone can be
 * SAID, and that saying it changes nothing about the fourteen catalogue drones, whose textual
 * form is exactly what it was before this class existed.
 */
public class DroneLoadTest {

    // ------------------------------------------------------- what keeps the parser honest

    /**
     * The one assumption {@link DroneLoad#parse} rests on: a modifier token belongs to exactly
     * one enum, so offering it to each in turn cannot pick the wrong owner.
     *
     * <p>If a future module is ever called {@code SPEED_20}, or a frame addition takes a
     * payload module's name, this fails and the grammar needs a sigil rather than a lookup.
     */
    @Test
    public void theThreeModifierEnumsShareNoNames() {
        Set<String> seen = new HashSet<>();
        for (String name : DroneLoad.modifierNames())
            assertTrue(name + " is the name of two different drone parts", seen.add(name));

        // And none of them collides with a basis name, which is read from position rather
        // than by lookup -- but a collision would make every error message a riddle.
        for (DroneType type : DroneType.values())
            assertFalse(type.name() + " is both a drone and a part of one",
                    seen.contains(type.name()));
    }

    // ------------------------------------------------------- the catalogue path, unchanged

    /** Every drone the enum names round-trips as its own bare name, and nothing else. */
    @Test
    public void everyCatalogueDroneIsJustItsName() {
        for (DroneType type : DroneType.values()) {
            DroneLoad load = DroneLoad.of(type);
            assertFalse(type + " is the drone itself, not a modification of one",
                    load.isModified());
            assertEquals(type.name(), load.format());
            assertEquals(load, DroneLoad.parse(type.name()));
        }
    }

    /** The derived figures a loader reads come off the spec, so they match the enum exactly. */
    @Test
    public void aCatalogueDroneReportsItsOwnNumbers() {
        for (DroneType type : DroneType.values()) {
            DroneLoad load = DroneLoad.of(type);
            assertEquals(type + " rack spaces", type.rack, load.rackSpaces(), 0.001);
            assertEquals(type + " speed", type.speed, load.speed());
            assertEquals(type + " cost", type.spec.pointCost(), load.pointCost(), 0.001);
            assertEquals(type + " type-VI", type.isTypeVI(), load.isTypeVI());
        }
    }

    /**
     * The year gate on an unmodified drone is {@link DroneType#availableIn} and nothing more.
     *
     * <p>Pinned with the case where that matters: {@code TypeIII} declares year 0 while the
     * speed-12 engine it flies was invented in Y77 (FD10.651), so asking the spec as well would
     * quietly change an answer the engine has always given. Whether the enum's declared 0 is
     * right is a separate question; this test is here so that question gets asked out loud
     * rather than settled by a refactor.
     */
    @Test
    public void anUnmodifiedDroneKeepsTheEnumsOwnYearGate() {
        for (DroneType type : DroneType.values())
            for (int year : new int[] {50, 100, 164, 165, 177, 178, 200})
                assertEquals(type + " in Y" + year,
                        type.availableIn(year), DroneLoad.of(type).availableIn(year));

        assertTrue("the case that makes the rule worth stating",
                DroneLoad.of(DroneType.TypeIII).availableIn(50));
    }

    // ------------------------------------------------------- composed drones

    /**
     * FD12.12's drone, and the owner's: "a type-IV frame carrying three 1/2-PS armor modules
     * and a 1/2-PS explosive" — twelve hull behind a strength-six warhead, where a standard
     * type-IV is six hull and twenty-four damage.
     */
    @Test
    public void theArmoredTypeIvRoundTrips() {
        DroneSpec spec = DroneSpec.of(DroneFrame.TWO_SPACE, Propulsion.SPEED_8,
                PayloadModule.ARMOR_HALF, PayloadModule.ARMOR_HALF,
                PayloadModule.ARMOR_HALF, PayloadModule.EXPLOSIVE_HALF);
        DroneLoad load = new DroneLoad(DroneType.TypeIV, spec);

        assertTrue(load.isModified());
        assertEquals("TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF", load.format());
        assertEquals(load, DroneLoad.parse(load.format()));

        Drone built = load.build();
        assertEquals("FD12.21: four frame hull plus two per armor module", 12, built.getHull());
        assertEquals("FD10.41: the half-space explosive is all the warhead there is",
                6, built.getWarheadDamage());
        assertEquals("still a two-space drone", 2.0, built.getRackSize(), 0.001);
        assertEquals("endurance is stated by the basis, not derived",
                DroneType.TypeIV.endurance, built.getEndurance());
    }

    /**
     * Naming a module says what the payload IS. A type-IV carrying one ECM module carries one
     * ECM module — not the two explosives it came with and an ECM module as well, which would
     * want a third payload space the frame has not got (FD10.4).
     */
    @Test
    public void namingAModuleReplacesThePayload() {
        DroneLoad load = DroneLoad.parse("TypeIV+ECM");

        assertEquals(List.of(PayloadModule.ECM), load.spec().payload());
        assertEquals("FD10.44: an ECM module is no warhead at all",
                0, load.build().getWarheadDamage());
    }

    /**
     * Naming none keeps it, which is how a frame addition alone is said: an ordinary type-I
     * warhead with FD5.2's advanced guidance bolted on.
     */
    @Test
    public void namingNoModuleKeepsThePayload() {
        DroneLoad load = DroneLoad.parse("TypeI+ACTIVE_TERMINAL_GUIDANCE");

        assertEquals(DroneType.TypeI.spec.payload(), load.spec().payload());
        assertEquals("the warhead it always had", DroneType.TypeI.damage,
                load.build().getWarheadDamage());
        assertTrue(load.spec().additions().contains(FrameAddition.ACTIVE_TERMINAL_GUIDANCE));
        assertEquals("TypeI+EXPLOSIVE_1+ACTIVE_TERMINAL_GUIDANCE", load.format());
        assertEquals(load, DroneLoad.parse(load.format()));
    }

    /**
     * A modified type-III is still a type-III: FD10.53's frame surcharge IS its guidance, and
     * the 800-impulse endurance the basis supplies is the reason that matters.
     */
    @Test
    public void aModifiedTypeIiiKeepsItsGuidance() {
        DroneLoad load = DroneLoad.parse("TypeIII+ECM");

        assertTrue("the type-III frame surcharge is not optional",
                load.spec().additions().contains(FrameAddition.TYPE_III_GUIDANCE));
        assertEquals(DroneType.TypeIII.endurance, load.build().getEndurance());
        assertTrue("FD10.23: it still guides itself", load.spec().isSelfGuidingCapable());
    }

    /** FD8.11 sells submunitions at a speed of their own, so the text has to carry one. */
    @Test
    public void submunitionSpeedSurvivesTheRoundTrip() {
        DroneLoad load = DroneLoad.parse("TypeIV+MW_LARGE+SPEED_20");

        assertEquals(Propulsion.SPEED_20, load.spec().submunitionPropulsion());
        assertEquals(5, load.spec().submunitionCount());
        assertEquals(load, DroneLoad.parse(load.format()));
        assertTrue("FD8.24: two sets of engines is what makes an MW drone expensive",
                load.pointCost() > DroneLoad.parse("TypeIV+MW_LARGE").pointCost());
    }

    /**
     * A modification can outrun its own invention date even when the basis cannot. FD8.11
     * allows submunitions at "any speed available for the year" — which is a limit, not a
     * licence.
     */
    @Test
    public void aModifiedDroneIsAlsoAskedWhetherItsPartsExist() {
        DroneLoad load = DroneLoad.parse("TypeIV+MW_LARGE+SPEED_32");

        assertTrue("the bus has been flying since the beginning",
                DroneType.TypeIV.availableIn(100));
        assertFalse("but its submunitions' engines had not been invented (FD10.651)",
                load.availableIn(100));
        assertTrue(load.availableIn(180));
    }

    // ------------------------------------------------------- the dialog's own spelling

    /**
     * Every name the COI dialog can write, read back as the drone it means.
     *
     * <p>{@code droneComposition.ts} is the client half of this grammar, and the two halves can
     * drift in a way neither side's own tests would notice: the dialog writes a string, this
     * reads it, and nothing else sits between them. So these are the exact strings that file's
     * tests assert it produces, copied rather than derived — a shared constant would only
     * prove the two agree with each other.
     *
     * <p>The ORDER need not match, and does not. The client writes its frame additions in the
     * order they were ticked while {@link #format} writes them in declaration order, and both
     * mean the same drone because the additions are a set. What must match is the drone.
     */
    @Test
    public void everyNameTheDialogWritesIsReadAsTheDroneItMeans() {
        for (String text : new String[] {
                "TypeIM",
                "TypeIV+ARMOR_HALF+ARMOR_HALF+EXTENDED_RANGE",
                "TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF",
                "TypeIV+MW_LARGE+SPEED_32",
                "TypeIII+EXPLOSIVE_1+TYPE_III_GUIDANCE",
                "TypeIII+EXTENDED_RANGE",
                "TypeI+EXTENDED_RANGE",
                "TypeI+EXPLOSIVE_1+EXTERNAL_ARMOR_HALF",
                "TypeI+ECM",
                "TypeIV+EXTERNAL_ARMOR_HALF+ACTIVE_TERMINAL_GUIDANCE",
        }) {
            assertNull("the dialog can write this, so core must read it: " + text,
                    DroneLoad.refusalFor(text));
            DroneLoad load = DroneLoad.parse(text);
            assertEquals("and re-reading its own spelling must give the same drone: " + text,
                    load, DroneLoad.parse(load.format()));
        }
    }

    /**
     * The one the dialog has to be TOLD, because it is the only modification in the game that
     * changes how much of a rack a drone fills: FD10.24's "they become type-III-XX, which are
     * two-space drones with one payload space".
     */
    @Test
    public void extendedRangeOnATypeIiiTakesTwoRackSpaces() {
        assertEquals(2.0, DroneLoad.parse("TypeIII+EXTENDED_RANGE").rackSpaces(), 0.0001);
        assertEquals("and every other drone keeps its footprint",
                1.0, DroneLoad.parse("TypeI+EXTENDED_RANGE").rackSpaces(), 0.0001);
        assertEquals("FD2.222: doubled, and the frame does not override it",
                DroneType.TypeI.endurance * 2,
                DroneLoad.parse("TypeI+EXTENDED_RANGE").build().getEndurance());
    }

    /**
     * FD12.131: "External armor cannot be placed on a drone equipped with internal armor."
     *
     * <p>Here because the test above found the dialog offering it. FD12.12's armored type-IV is
     * the obvious thing to want to bolt more armor to, and the editor had only the FD10.443
     * check — which is about ECM and says nothing about this. Pinned at the core end so the
     * next client to grow a payload editor has something to fail against.
     */
    @Test
    public void externalArmorCannotGoOnAnInternallyArmoredDrone() {
        String refusal = DroneLoad.refusalFor("TypeIV+ARMOR_HALF+EXTERNAL_ARMOR_HALF");

        assertNotNull("FD12.131 refuses this", refusal);
        assertTrue(refusal, refusal.contains("FD12.131"));
    }

    // ------------------------------------------------------- what it refuses

    @Test
    public void aSpecOnAnotherFrameIsRefused() {
        try {
            new DroneLoad(DroneType.TypeI, DroneType.TypeIV.spec);
            fail("a type-I is not built on a two-space frame");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ONE_SPACE"));
        }
    }

    @Test
    public void aSpecWithAnotherEngineIsRefused() {
        try {
            new DroneLoad(DroneType.TypeI, DroneType.TypeIM.spec);
            fail("the basis is how a speed is chosen");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("SPEED_20"));
        }
    }

    @Test
    public void aTypeIiiCannotShedItsGuidance() {
        DroneSpec plain = DroneSpec.of(DroneFrame.ONE_SPACE, Propulsion.SPEED_12,
                PayloadModule.EXPLOSIVE_1);
        try {
            new DroneLoad(DroneType.TypeIII, plain);
            fail("without TYPE_III_GUIDANCE it is not a type-III");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(),
                    expected.getMessage().contains("TYPE_III_GUIDANCE"));
        }
    }

    @Test
    public void anUnknownBasisNamesItself() {
        try {
            DroneLoad.parse("TypeVII");
            fail("there is no type-VII");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("TypeVII"));
        }
    }

    @Test
    public void anUnknownModifierNamesItself() {
        try {
            DroneLoad.parse("TypeI+PHOTON_TORPEDO");
            fail("there is no such module");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("PHOTON_TORPEDO"));
        }
    }

    /** An illegal composition is {@link DroneSpec}'s refusal to make, and it still makes it. */
    @Test
    public void anIllegalCompositionIsStillRefused() {
        try {
            DroneLoad.parse("TypeVI+EXPLOSIVE_1");
            fail("FD10.21: the half-space frame takes no payload modules");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void nothingIsNotADrone() {
        for (String text : new String[] {null, "", "   "}) {
            try {
                DroneLoad.parse(text);
                fail("parsed " + text);
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        }
    }
}
