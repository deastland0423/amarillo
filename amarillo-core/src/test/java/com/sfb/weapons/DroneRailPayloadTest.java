package com.sfb.weapons;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneFrame;
import com.sfb.objects.DroneSpec;
import com.sfb.objects.DroneType;
import com.sfb.objects.PayloadModule;
import com.sfb.objects.Propulsion;
import com.sfb.weapons.DroneRail.DroneRailType;

/**
 * What a fighter's rail may carry, as opposed to how much of it will fit (FD9.35, FD8.33).
 *
 * <h2>Why size was not enough</h2>
 * A rail used to accept anything that fit, and a type-III is a ONE-space drone — so a standard
 * rail took one on size alone, which is the very thing that makes a special rail special:
 *
 * <blockquote>FD9.35: "Fighters can carry ECM drones but only fighters with 'special' (J4.233)
 * or 'heavy' (J4.234) drone rails can use type-III frames."<br>
 * FD8.33: "MW drones can be carried by ships, MRS shuttles, and fighters with special rails
 * (J4.23). They cannot be carried by SPs, DefSats, or captor mines."</blockquote>
 *
 * <p>FD8.33's citation is J4.23, the rail SECTION rather than J4.233's special rail, and J4.23
 * is the authority: it gives the MW drone to the heavy rail as well. So the STANDARD rail is the
 * whole of the exception — it carries special type-I drones and refuses the MW drone and the
 * type-III frame — and the LIGHT rail refuses everything but a type-VI on size alone.
 *
 * <p>I read FD8.33 as naming the special rail only, which would have made FD8.42's type-IV-MW
 * uncarriable by any fighter ever built: it is a TWO-space drone and the heavy rail is the only
 * fighter rail that holds one. That test is below, and it is the one that makes the reading
 * check itself against the drones rather than against the sentence.
 *
 * <p>Unreachable from the UI today: nothing loads a fighter's rail from the Commander's Options,
 * so a player cannot yet ask for either drone. It is pinned now because the loadout surface
 * that will ask is the next thing anyone builds here, and an availability cap enforced over an
 * illegal loadout is worse than no cap at all.
 */
public class DroneRailPayloadTest {

    /** A type-III frame carrying ECM: FD9.35's own subject. */
    private static Drone typeIiiEcm() {
        return new Drone(DroneType.TypeIII, DroneSpec.of(DroneFrame.ONE_SPACE,
                Propulsion.SPEED_12,
                java.util.Set.of(com.sfb.objects.FrameAddition.TYPE_III_GUIDANCE),
                PayloadModule.ECM));
    }

    /** A multi-warhead drone: a one-space bus with three type-VI submunitions (FD10.431). */
    private static Drone multiWarhead() {
        return new Drone(DroneType.TypeI, DroneSpec.of(DroneFrame.ONE_SPACE,
                Propulsion.SPEED_12, PayloadModule.MW_SMALL));
    }

    // ------------------------------------------------------------ FD9.35: the type-III frame

    @Test
    public void aStandardRailRefusesATypeIiiFrame() {
        assertFalse("FD9.35: a standard rail cannot use a type-III frame",
                new DroneRail(DroneRailType.STANDARD).accepts(typeIiiEcm()));
    }

    @Test
    public void aSpecialRailTakesATypeIiiFrame() {
        assertTrue(new DroneRail(DroneRailType.SPECIAL).accepts(typeIiiEcm()));
    }

    /** "...or 'heavy' (J4.234)", which is the half of FD9.35 easiest to drop. */
    @Test
    public void aHeavyRailTakesATypeIiiFrameToo() {
        assertTrue(new DroneRail(DroneRailType.HEAVY).accepts(typeIiiEcm()));
    }

    /** And the catalogue type answers the same way, which is what the ready racks ask. */
    @Test
    public void theSameIsTrueOfTheBareCatalogueType() {
        assertFalse(new DroneRail(DroneRailType.STANDARD).accepts(DroneType.TypeIII));
        assertTrue(new DroneRail(DroneRailType.SPECIAL).accepts(DroneType.TypeIII));
    }

    // ------------------------------------------------------------ FD8.33: the MW drone

    @Test
    public void aStandardRailRefusesAMultiWarheadDrone() {
        assertFalse("J4.23: an MW drone needs a special or heavy rail",
                new DroneRail(DroneRailType.STANDARD).accepts(multiWarhead()));
    }

    @Test
    public void aSpecialRailTakesAMultiWarheadDrone() {
        assertTrue(new DroneRail(DroneRailType.SPECIAL).accepts(multiWarhead()));
    }

    /**
     * And a HEAVY rail takes one too, which is J4.23 rather than FD8.33's wording.
     *
     * <p>FD8.33 says "fighters with special rails (J4.23)", and that citation is the rail
     * SECTION rather than J4.233's special rail. J4.23 itself gives the MW drone to the heavy
     * rail as well, and it is the only reading the drones allow -- see the next test.
     */
    @Test
    public void aHeavyRailTakesAMultiWarheadDroneToo() {
        assertTrue(new DroneRail(DroneRailType.HEAVY).accepts(multiWarhead()));
    }

    /**
     * The type-IV-MW is why: FD8.42 builds one on {@link PayloadModule#MW_LARGE}, a TWO-space
     * module, and the heavy rail is the only fighter rail that holds a two-space drone. Read
     * FD8.33 as the special rail alone and this drone could never fly on any fighter.
     *
     * <p>The special rail refuses it, and on SIZE -- which is the distinction worth pinning: a
     * special rail is one space whatever else it can do.
     */
    @Test
    public void theTypeIvMwNeedsTheHeavyRailAndNothingElseWillHoldIt() {
        Drone typeIvMw = new Drone(DroneType.TypeIV, DroneSpec.of(DroneFrame.TWO_SPACE,
                Propulsion.SPEED_12, PayloadModule.MW_LARGE));
        assertEquals("fixture: a two-space drone", 2.0, typeIvMw.getRackSize(), 0.0001);

        assertTrue(new DroneRail(DroneRailType.HEAVY).accepts(typeIvMw));
        assertFalse("a special rail is one space",
                new DroneRail(DroneRailType.SPECIAL).accepts(typeIvMw));
        assertFalse(new DroneRail(DroneRailType.STANDARD).accepts(typeIvMw));
    }

    // ------------------------------------------------------------ and nothing else moved

    @Test
    public void anOrdinaryDroneStillRidesAnOrdinaryRail() {
        assertTrue(new DroneRail(DroneRailType.STANDARD).accepts(new Drone(DroneType.TypeI)));
        assertTrue(new DroneRail(DroneRailType.HEAVY).accepts(new Drone(DroneType.TypeIV)));
        assertTrue(new DroneRail(DroneRailType.LIGHT).accepts(new Drone(DroneType.TypeVI)));
    }

    @Test
    public void sizeStillRefusesWhatWillNotFit() {
        assertFalse("a two-space drone on a one-space rail",
                new DroneRail(DroneRailType.STANDARD).accepts(new Drone(DroneType.TypeIV)));
        assertFalse("a one-space drone on a half-space rail",
                new DroneRail(DroneRailType.LIGHT).accepts(new Drone(DroneType.TypeI)));
    }

    /**
     * The stock load goes through the same gate, so a rail cannot be DESIGNED around a drone it
     * would refuse once loaded. FD10.646 already moved the special rail's own stock to a type-I;
     * this is the door that would have let it back.
     */
    @Test
    public void aRailCannotBeDesignedAroundADroneItWouldRefuse() {
        DroneRail standard = new DroneRail(DroneRailType.STANDARD);
        DroneType before = standard.getDesignDrone();
        standard.setDesignDrone(DroneType.TypeIII);
        assertEquals("FD9.35 refuses it as a design as well as a load",
                before, standard.getDesignDrone());

        DroneRail special = new DroneRail(DroneRailType.SPECIAL);
        special.setDesignDrone(DroneType.TypeIII);
        assertEquals("and a special rail may be designed around one",
                DroneType.TypeIII, special.getDesignDrone());
    }
}
