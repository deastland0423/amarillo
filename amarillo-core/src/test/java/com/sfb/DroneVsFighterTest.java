package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;

/**
 * A seeker can hit a fighter.
 * <p>
 * It could not. {@code SeekerMover}'s drone branch knew how to impact a Ship, another drone and a
 * Wild Weasel, and had no case for anything else — so a drone that flew into a fighter's hex simply
 * carried on until its endurance ran out. Nothing logged, nothing failed; drones were harmless to
 * fighters for the life of every scenario.
 * <p>
 * It also made FD2.54 unreachable. A dogfight drone's whole purpose is the fighter it is sized for
 * — eight points against size class 6 and 7, two against a ship — and the eight-point tier had
 * nothing it could ever be fired at. The damage rule was implemented and could not be exercised.
 * <p>
 * The same hole existed on the SUICIDE SHUTTLE branch. Plasma already handled it, which is the
 * oddity that hid it: a plasma torpedo could kill a fighter and a drone could not.
 */
public class DroneVsFighterTest {

    private Game game;
    private Ship carrier;
    private Fighter victim;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player kzinti = new Player();
        kzinti.setTeamName("Kzinti");
        Player hydran = new Player();
        hydran.setTeamName("Hydran");

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinBC());
        carrier.setName("KHS Shooter");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kzinti);
        game.getShips().add(carrier);

        // The target: a Hydran Stinger-1, hull 8 and crippled at 6.
        victim = CataloguedFighter.of("stinger1");
        victim.setName("Stinger-1");
        victim.setLocation(new Location(10, 12));
        victim.setFacing(13);
        victim.setOwner(hydran);
        game.getActiveShuttles().add(victim);

        game.startTurn();
    }

    /**
     * A drone of {@code type} one hex short of the victim, aimed at it.
     * <p>
     * ADJACENT rather than co-located, because the mover moves a drone BEFORE testing for impact —
     * one placed in the target's hex steps straight out of it and never collides. That is the
     * fixture, not the rule: a drone in play closes from outside and the check catches it on
     * arrival.
     */
    private Drone droneNextTo(DroneType type) {
        Drone drone = new Drone(type);
        drone.setName("Drone-1");
        drone.setLocation(new Location(10, 11));
        drone.setFacing(13);                       // pointed at the victim at (10,12)
        drone.setTarget(victim);
        drone.setController(carrier);
        drone.setLaunchImpulse(game.getAbsoluteImpulse() - 50);
        game.getSeekers().add(drone);
        return drone;
    }

    /** Advance until the seeker is gone, however many impulses its speed needs. */
    private void letTheDroneArrive() {
        for (int i = 0; i < 400 && !game.getSeekers().isEmpty(); i++)
            game.advancePhase();
        assertTrue("the drone never resolved — fixture problem, not a rule",
                game.getSeekers().isEmpty());
    }

    // ---------------------------------------------------------------- the gap

    @Test
    public void aDroneDamagesTheFighterItHits() {
        int before = victim.getCurrentHull();
        droneNextTo(DroneType.TypeI);

        letTheDroneArrive();

        assertTrue("the drone should be gone, not still flying",
                game.getSeekers().isEmpty());
        assertTrue("the fighter took damage: " + before + " -> " + victim.getCurrentHull(),
                victim.getCurrentHull() < before);
    }

    /**
     * FD2.54's eight-point tier, finally reachable. A type-I's twelve would simply destroy a
     * Stinger outright, so this is the dogfight drone doing what it exists for.
     */
    @Test
    public void aDogfightDroneHitsAFighterForItsFullWarhead() {
        Drone vi = new Drone(DroneType.TypeVI);
        assertEquals("the printed warhead", 8, vi.impact());
        assertEquals("and against a fighter it is the full eight (FD2.54)",
                8, vi.impact(victim));
        assertEquals("where a ship would take two",
                2, vi.impact(carrier));
    }

    /** Twelve points on an eight-hull fighter is a kill, and it leaves the map. */
    @Test
    public void aStandardDroneDestroysAStingerOutright() {
        droneNextTo(DroneType.TypeI);

        letTheDroneArrive();

        assertFalse("the Stinger should be off the map",
                game.getActiveShuttles().contains(victim));
    }

    // ---------------------------------------------------------------- the whole matrix

    /**
     * It is Shuttle-general, not Fighter-special — which is the point of putting the case on
     * Shuttle. A Fighter IS a Shuttle, so one branch covers an admin shuttle, a GAS, a scatter
     * pack and every fighter in the game.
     */
    @Test
    public void aDroneHitsAPlainShuttleToo() {
        com.sfb.objects.shuttles.Shuttle admin =
                com.sfb.systemgroups.ShuttleBay.buildShuttle("admin", "Shuttle-1");
        admin.setLocation(new Location(10, 12));
        admin.setFacing(13);
        game.getActiveShuttles().add(admin);
        victim.setLocation(new Location(20, 20));   // out of the way

        Drone drone = new Drone(DroneType.TypeI);
        drone.setName("Drone-A");
        drone.setLocation(new Location(10, 11));
        drone.setFacing(13);
        drone.setTarget(admin);
        drone.setController(carrier);
        drone.setLaunchImpulse(game.getAbsoluteImpulse() - 50);
        game.getSeekers().add(drone);

        letTheDroneArrive();
        assertFalse("the admin shuttle should be destroyed",
                game.getActiveShuttles().contains(admin));
    }

    /** A plasma torpedo could already do this, via a general else its own branch always had. */
    @Test
    public void aPlasmaTorpedoHitsAFighter() {
        int before = victim.getCurrentHull();

        com.sfb.objects.PlasmaTorpedo torp = new com.sfb.objects.PlasmaTorpedo(
                com.sfb.properties.PlasmaType.G, com.sfb.properties.WeaponArmingType.STANDARD);
        torp.setName("Plasma-G-1");
        torp.setLocation(new Location(10, 11));
        torp.setFacing(13);
        torp.setTarget(victim);
        torp.setController(carrier);
        torp.setLaunchImpulse(game.getAbsoluteImpulse() - 50);
        game.getSeekers().add(torp);

        letTheDroneArrive();
        assertTrue("a plasma-G on a Stinger is not survivable: "
                        + before + " -> " + victim.getCurrentHull(),
                victim.getCurrentHull() < before
                        || !game.getActiveShuttles().contains(victim));
    }

    /** The drone is spent on impact either way — it does not fly on looking for another target. */
    @Test
    public void theDroneIsSpentWhenItHits() {
        droneNextTo(DroneType.TypeVI);

        letTheDroneArrive();

        assertTrue(game.getSeekers().isEmpty());
    }

}
