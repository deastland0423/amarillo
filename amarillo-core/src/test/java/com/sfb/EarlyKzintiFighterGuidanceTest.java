package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * J4.222, early Kzinti fighters: the attack shuttle launches, somebody else guides.
 *
 * <p>"When Kzinti fighters first appeared, they did not have their own guidance equipment. These
 * were known as attack shuttles (AS)... This condition applies only during the historical period
 * Y161-Y163. During this period, the carrier (or another unit, e.g., an MRS) <b>must immediately
 * assume guidance duties</b> for any drone launched by one of its shuttles." The Y161 timeline
 * entry puts it more bluntly: the AS "cannot guide its own drones and is considered only a manned
 * drone launch platform."
 *
 * <p>So this is a HANDOFF, not a refusal, and the distinction is the whole point of the test. A
 * fighter reaches a control capacity of zero three different ways — no guidance equipment (here),
 * erratic manoeuvres (C10.512), or carrying no drones at all — and only the first one may still
 * launch. Marking the AS "No drone control" without this would have left it unable to throw a
 * drone at all, which is worse than the gap it was added to close.
 */
public class EarlyKzintiFighterGuidanceTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;
    private Fighter as;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player kz = new Player();
        kz.setTeamName("Kzinti");
        Player fed = new Player();
        fed.setTeamName("Federation");

        enemy = new Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Target");
        enemy.setLocation(new Location(12, 10));
        enemy.setFacing(13);
        enemy.setOwner(fed);
        game.getShips().add(enemy);

        carrier = new Ship();
        carrier.init(com.sfb.samples.KzintiShips.getKzinCS());
        carrier.setName("KZS Carrier");
        carrier.setLocation(new Location(10, 11));
        carrier.setFacing(1);
        carrier.setOwner(kz);
        game.getShips().add(carrier);

        as = CataloguedFighter.of("as");
        as.setName("AS-1");
        as.setLocation(new Location(10, 10));
        as.setFacing(1);
        as.setOwner(kz);
        as.setParentShipName(carrier.getName());
        game.getActiveShuttles().add(as);

        game.startTurn();
        as.setLaunchImpulse(game.getAbsoluteImpulse() - 100);
        for (DroneRail rail : rails())
            if (rail.getDrone() == null)
                rail.loadDrone(new Drone(DroneType.TypeI));
    }

    private java.util.List<DroneRail> rails() {
        java.util.List<DroneRail> out = new java.util.ArrayList<>();
        for (Weapon w : as.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                out.add(rail);
        return out;
    }

    private Game.ActionResult launch() {
        int guard = 0;
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY && guard++ < 40)
            game.advancePhase();
        as.addLockOn(enemy);
        carrier.addLockOn(enemy);
        return game.launchFighterDrone(as, enemy, rails().get(0), 0);
    }

    @Test
    public void theAsCannotGuideAnything() {
        assertEquals("the chart's 'No drone control'", 0, as.getControlCapacity());
        assertFalse(as.guidesItsOwnDrones());
    }

    @Test
    public void itStillLaunches() {
        int before = game.getSeekers().size();

        Game.ActionResult result = launch();

        assertTrue(result.getMessage(), result.isSuccess());
        assertEquals("the drone reached the map", before + 1, game.getSeekers().size());
    }

    @Test
    public void theCarrierAssumesGuidance() {
        Game.ActionResult result = launch();

        Seeker drone = game.getSeekers().get(game.getSeekers().size() - 1);
        assertSame("J4.222: the carrier must immediately assume guidance duties",
                carrier, drone.getController());
        assertEquals("and it costs the carrier a channel", 1, carrier.getControlUsed());
        assertEquals("while the fighter guides nothing", 0, as.getControlUsed());
        assertTrue("and the player is told who took it: " + result.getMessage(),
                result.getMessage().contains("J4.222"));
    }

    /** With nobody able to take it, the refusal names the rule rather than the capacity. */
    @Test
    public void withNoOneToGuideItTheLaunchIsRefusedForTheRightReason() {
        carrier.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);

        Game.ActionResult result = launch();

        assertFalse(result.isSuccess());
        assertTrue("should cite J4.222, not J4.25's capacity: " + result.getMessage(),
                result.getMessage().contains("J4.222"));
    }
}
