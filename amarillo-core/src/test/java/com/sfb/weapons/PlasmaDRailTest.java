package com.sfb.weapons;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

/**
 * A type-D plasma torpedo rides a RAIL, not a launcher (FP9.2, J4.825).
 * <p>
 * The owner's instinct, and the rules agree in as many words. FP9.21: type-Ds are "stored,
 * transported, handled, and loaded as drones are, each taking one 'space'". J4.825: "The
 * rearming and storage rules for drones are used for type-D plasma torpedoes... A type-D
 * torpedo is the same size as a one-space drone."
 * <p>
 * So this is a new rail TYPE rather than a new weapon class, which follows a decision the
 * project already made - the rail, not the fighter, declares what fits and what it costs
 * (J4.231). Every piece of machinery that keys on {@code instanceof DroneRail} - the box's
 * ready rack, the deck crew pass, the DTO, scatter-pack sizing - therefore keeps working with
 * no change at all.
 * <p>
 * The payload is held in a field of its own rather than in the inherited drone ammo list,
 * because a {@link PlasmaTorpedo} is not a {@link Drone}: that list is typed for drones, is
 * shared with every ship rack, and carries reload sets and size trimming that mean nothing to
 * a torpedo. The useful side effect is that {@code getDrone()} answers null on a loaded
 * plasma-D rail, so every drone path skips it - which is right, since a drone launch must
 * never fire a plasma torpedo.
 * <p>
 * NOT YET BUILT, deliberately: FP9.22's activation, which costs "1/2 of an energy point
 * (reserve or allocated) per torpedo" and without which "the weapon cannot be launched". That
 * is the next slice. Its 25-turn deactivation is noted and not planned - 25 turns outlasts any
 * scenario.
 */
public class PlasmaDRailTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    private static DroneRail plasmaRail() {
        DroneRail rail = new DroneRail(DroneRail.DroneRailType.PLASMA_D);
        rail.setDesignator("A");
        return rail;
    }

    private static PlasmaTorpedo typeD() {
        return new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
    }

    private static Drone typeI() {
        Drone d = new Drone();
        d.setDroneType(DroneType.TypeI);
        return d;
    }

    // ---------------------------------------------------------------- one space

    /** FP9.21/J4.825: "the same size as a one-space drone". */
    @Test
    public void aPlasmaDRailIsOneSpace() {
        DroneRail rail = plasmaRail();

        assertEquals(1.0, DroneRail.DroneRailType.PLASMA_D.capacity, 0.001);
        assertEquals("one space, as the rule sizes it", 1, rail.getSpaces());
        assertTrue(rail.isPlasmaD());
    }

    /** It has no design DRONE: there is no ordinary drone load for such a fighter. */
    @Test
    public void itHasNoDesignDrone() {
        assertNull(plasmaRail().getDesignDrone());
    }

    // ---------------------------------------------------------------- it carries a torpedo

    @Test
    public void itCarriesATypeDTorpedo() {
        DroneRail rail = plasmaRail();
        assertFalse("empty to begin with", rail.isLoaded());

        PlasmaTorpedo torpedo = typeD();
        rail.loadTorpedo(torpedo);

        assertTrue(rail.isLoaded());
        assertSame(torpedo, rail.getTorpedo());
        assertEquals(PlasmaType.D, rail.getTorpedo().getPlasmaType());
    }

    /**
     * And the torpedo is invisible to {@code getDrone()}, which is what keeps every drone path
     * off it. A launch that read this rail as holding a drone would fire a plasma torpedo as
     * one.
     */
    @Test
    public void aLoadedPlasmaRailLooksEmptyToTheDronePaths() {
        DroneRail rail = plasmaRail();
        rail.loadTorpedo(typeD());

        assertNull("no drone here", rail.getDrone());
        assertTrue("but it is loaded", rail.isLoaded());
        assertTrue("and the ammo list stays a drone list", rail.getAmmo().isEmpty());
    }

    @Test
    public void takingTheTorpedoOffEmptiesTheRail() {
        DroneRail rail = plasmaRail();
        PlasmaTorpedo torpedo = typeD();
        rail.loadTorpedo(torpedo);

        assertSame(torpedo, rail.removeTorpedo());

        assertFalse(rail.isLoaded());
        assertNull(rail.getTorpedo());
    }

    // ---------------------------------------------------------------- J4.825's exclusivity

    /**
     * "No fighter in the game can use both type-D plasmas and drones, so you cannot load
     * drones on a plasma-D-armed fighter (nor vice versa)" (J4.825). Refused at the rail,
     * which is the only place that can refuse it per-mount.
     */
    @Test
    public void aPlasmaDRailRefusesADrone() {
        DroneRail rail = plasmaRail();

        assertFalse("it does not even accept one", rail.accepts(typeI()));
        try {
            rail.loadDrone(typeI());
            fail("a drone went onto a plasma-D rail (J4.825)");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("J4.825"));
        }
        assertFalse(rail.isLoaded());
    }

    /** And the other way: an ordinary rail will not take a torpedo. */
    @Test
    public void anOrdinaryRailRefusesATorpedo() {
        DroneRail standard = new DroneRail(DroneRail.DroneRailType.STANDARD);

        try {
            standard.loadTorpedo(typeD());
            fail("a torpedo went onto a standard drone rail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("FP9.2"));
        }
    }

    /** J4.2312 already bars a pod from anything but a standard rail, which covers this one. */
    @Test
    public void aPlasmaDRailCannotTakeAnEwPod() {
        assertFalse(plasmaRail().canCarryEwPod());
    }

    // ---------------------------------------------------------------- as the data builds it

    /**
     * The Gladiator-F off the catalogue. FP9.32: "Gladiator-F, Gladiator-SF, and Gladiator-FSF
     * fighters carry the plasma-D, as well as the Tribune heavy fighter."
     */
    @Test
    public void theGladiatorFCarriesTwoPlasmaDRails() {
        Fighter gf = CataloguedFighter.of("gf");
        assertNotNull("fixture: the G-F should be in the catalogue", gf);

        int plasmaRails = 0, droneRails = 0;
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail) {
                if (rail.isPlasmaD())
                    plasmaRails++;
                else
                    droneRails++;
            }

        assertEquals("two plasma-D rails", 2, plasmaRails);
        assertEquals("and no drone rails (J4.825)", 0, droneRails);
    }

    /**
     * Its scatter-pack capacity is ZERO, and that follows from J4.825 rather than from the
     * rails being small: a scatter pack is drones, and this fighter may not load drones at
     * all. The data said 2 before the rule was read.
     */
    @Test
    public void theGladiatorFCannotCarryAScatterPack() {
        ShuttleCatalog.Entry gf = ShuttleCatalog.get("gf");

        assertEquals("no drones on a plasma-D fighter (J4.825)", 0, gf.scatterPackSize);
        assertFalse(gf.canScatterPack());
    }
}
