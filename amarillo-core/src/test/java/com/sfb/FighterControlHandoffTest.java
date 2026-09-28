package com.sfb;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * What becomes of a fighter's drones when the fighter stops being in space (J1.61, J4.25).
 * <p>
 * A fighter guides its own drones, so landing one raises a question a ship never did: the
 * craft goes into a shuttle bay and whatever it was steering is still out there. Until now
 * nothing noticed — the orphan sweep tested {@code controller instanceof Ship} and skipped
 * every fighter, so a landed fighter kept guiding its drone from inside the hangar, and its
 * control channel stayed occupied for good measure.
 * <p>
 * The owner's ruling (2026-09-28): those drones become orphaned and are offered to a new
 * controller — the squadron's EW fighter or any allied ship. A ship may always take an
 * allied seeker if it has a free channel. The EW fighter half is J4.43 and waits on squadron
 * organisation (J4.46), so a ship is the only successor here.
 */
public class FighterControlHandoffTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;
    private Fighter flier;
    private Seeker drone;

    @Before
    public void setUp() throws Exception {
        game = new Game();
        Player kz = new Player();
        kz.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        carrier = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cv.json")));
        carrier.setName("KHS Sabre");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(kz);
        carrier.setSpeed(0);
        game.getShips().add(carrier);

        enemy = new Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Target");
        enemy.setLocation(new Location(12, 10));
        enemy.setFacing(13);
        enemy.setOwner(fed);
        game.getShips().add(enemy);

        game.startTurn();
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();

        ShuttleBay bay = carrier.getShuttles().getBays().get(0);
        Shuttle craft = null;
        for (Shuttle s : bay.getInventory())
            if (craft == null && s instanceof Fighter)
                craft = s;
        game.launchShuttle(carrier, bay, craft, 8, 1);

        flier = (Fighter) craft;
        flier.setLaunchImpulse(game.getAbsoluteImpulse() - 100);   // past J1.341
        flier.addLockOn(enemy);
        for (Weapon w : flier.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail r && r.getDrone() == null)
                r.loadDrone(new Drone(DroneType.TypeI));

        DroneRail rail = null;
        for (Weapon w : flier.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail r && rail == null)
                rail = r;
        assertTrue(game.launchFighterDrone(flier, enemy, rail, 0).isSuccess());
        drone = game.getSeekers().get(game.getSeekers().size() - 1);
        assertSame("the fighter guides what it launched", flier, drone.getController());
    }

    // -------------------------------------------------------------------------

    @Test
    public void landingHandsTheDroneToTheCarrier() {
        carrier.addLockOn(enemy);   // a ship may take an allied seeker if it can guide it

        Game.ActionResult landed = game.landShuttle(carrier, flier.getName());

        assertTrue(landed.getMessage(), landed.isSuccess());
        assertTrue("the drone is still flying", game.getSeekers().contains(drone));
        assertSame("and the carrier is guiding it now", carrier, drone.getController());
        assertEquals("the fighter's channel is free again", 0, flier.getControlUsed());
        assertTrue(landed.getMessage(), landed.getMessage().contains("transferred"));
    }

    @Test
    public void withNobodyAbleToTakeItTheDroneIsLetGo() {
        // The carrier has no lock-on to the target, so it cannot guide the drone (D6.121).
        carrier.removeLockOn(enemy);
        int before = game.getSeekers().size();

        Game.ActionResult landed = game.landShuttle(carrier, flier.getName());

        assertTrue(landed.getMessage(), landed.isSuccess());
        assertEquals("the drone is gone, not orphaned in place",
                before - 1, game.getSeekers().size());
        assertEquals(0, flier.getControlUsed());
        assertTrue(landed.getMessage(), landed.getMessage().contains("released"));
    }

    /**
     * The bug this began with. The sweep skipped any controller that was not a Ship, so a
     * landed fighter kept its drone and its channel indefinitely.
     */
    @Test
    public void aLandedFighterNeverKeepsGuidingFromInsideTheBay() {
        carrier.removeLockOn(enemy);
        game.landShuttle(carrier, flier.getName());

        assertNotSame("whatever happened to it, the fighter is not still steering it",
                flier, drone.getController());
        assertFalse("and it is not on the map holding a channel",
                game.getActiveShuttles().contains(flier));
    }

    /** A ship may always take an allied seeker — it does not matter that a fighter threw it. */
    @Test
    public void aShipMayInheritADroneAFighterLaunched() {
        carrier.addLockOn(enemy);
        int capacityBefore = carrier.getControlUsed();

        game.landShuttle(carrier, flier.getName());

        assertEquals("the carrier has spent a channel on it",
                capacityBefore + 1, carrier.getControlUsed());
    }

    /**
     * J4.43's exception is not built: an EW fighter may only assume control of weapons
     * launched by fighters of its own SQUADRON, and squadron organisation is J4.46. Until
     * that exists no fighter may inherit anything, and the refusal must be deliberate —
     * widening who CAN control must not widen who may TAKE OVER.
     */
    @Test
    public void noFighterInheritsADroneYet() {
        carrier.removeLockOn(enemy);

        // A wingman already in space, with a free channel and lock-on to the same target.
        // Placed directly rather than launched: a launch would spend the tunnel deck's
        // hatches (J1.58) and the landing below needs one.
        com.sfb.objects.shuttles.Aas wingman = new com.sfb.objects.shuttles.Aas();
        wingman.setName("AAS-WING");
        wingman.setLocation(new Location(10, 10));
        wingman.setFacing(1);
        wingman.setOwner(carrier.getOwner());
        game.getActiveShuttles().add(wingman);
        wingman.addLockOn(enemy);
        assertTrue("the wingman could hold it if the rules allowed",
                wingman.getControlUsed() < wingman.getControlCapacity());

        Game.ActionResult landed = game.landShuttle(carrier, flier.getName());
        assertTrue(landed.getMessage(), landed.isSuccess());

        assertEquals("no fighter may take it over until J4.46 exists",
                0, wingman.getControlUsed());
        assertFalse("so the drone is let go instead", game.getSeekers().contains(drone));
    }
}
