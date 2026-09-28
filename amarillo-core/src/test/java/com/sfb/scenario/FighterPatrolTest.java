package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * Fighters already flying when the scenario opens (S4.12, S4.13).
 * <p>
 * The interesting thing about a Combat Space Patrol is that it costs something. A fighter on
 * the board can be shot at before its carrier has fired a weapon, and the patrol pins the
 * carrier's own speed — which through C2.2 pins next turn's acceleration too. So the test
 * that matters most here is the one about speed, not the one about hexes.
 */
public class FighterPatrolTest {

    private Game game;
    private Ship rn;

    @Before
    public void setUp() throws Exception {
        game = new Game();
        rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        rn.setName("HMS Loyalty");
        rn.setLocation(new Location(20, 20));
        rn.setFacing(1);
        rn.setSpeed(20);
        rn.setSpeedPreviousTurn(20);
        rn.setSpeedTwoTurnsAgo(20);
        game.getShips().add(rn);
    }

    private String firstFighterName() {
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter)
                    return s.getName();
        throw new IllegalStateException("the Ranger should carry fighters");
    }

    private List<String> fighterNames(int count) {
        List<String> names = new java.util.ArrayList<>();
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter && names.size() < count)
                    names.add(s.getName());
        return names;
    }

    private List<FighterPatrol.Posting> patrolOf(int count) {
        List<FighterPatrol.Posting> patrol = new java.util.ArrayList<>();
        int i = 0;
        for (String name : fighterNames(count))
            patrol.add(new FighterPatrol.Posting(name, new Location(20 + (i++ % 2), 21)));
        return patrol;
    }

    // -------------------------------------------------------------------------
    // How many, and when
    // -------------------------------------------------------------------------

    @Test
    public void theWeaponStatusSaysHowManyMayBeFlying() {
        assertEquals("S4.10: armed and ready to launch is not the same as flying",
                0, FighterPatrol.maxDeployed(0));
        assertEquals("S4.11 adds nothing for fighters", 0, FighterPatrol.maxDeployed(1));
        assertEquals("S4.12: two as a Combat Space Patrol", 2, FighterPatrol.maxDeployed(2));
        assertEquals("S4.13: four", 4, FighterPatrol.maxDeployed(3));
    }

    @Test
    public void noPatrolIsAllowedAtTheLowerStatuses() {
        List<String> problems = FighterPatrol.check(rn, patrolOf(1), 0, 40, 40);

        assertFalse(problems.isEmpty());
        assertTrue(problems.get(0), problems.get(0).contains("may not deploy fighters at WS-0"));
    }

    @Test
    public void aThirdFighterIsOneTooManyAtWsTwo() {
        assertTrue("two is fine", FighterPatrol.check(rn, patrolOf(2), 2, 40, 40).isEmpty());

        List<String> problems = FighterPatrol.check(rn, patrolOf(3), 2, 40, 40);

        assertFalse(problems.isEmpty());
        assertTrue(problems.get(0), problems.get(0).contains("may deploy 2 fighter(s) at WS-2"));
    }

    @Test
    public void fourMayFlyAtWsThree() {
        assertTrue(FighterPatrol.check(rn, patrolOf(4), 3, 40, 40).isEmpty());
        assertFalse(FighterPatrol.check(rn, patrolOf(5), 3, 40, 40).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Where
    // -------------------------------------------------------------------------

    @Test
    public void aPatrolStaysWithinTwoHexesOfItsCarrier() {
        List<FighterPatrol.Posting> tooFar = List.of(
                new FighterPatrol.Posting(firstFighterName(), new Location(20, 25)));

        List<String> problems = FighterPatrol.check(rn, tooFar, 3, 40, 40);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("within 2"));
    }

    @Test
    public void aFighterThatIsNotAboardCannotBePutUp() {
        List<FighterPatrol.Posting> stranger = List.of(
                new FighterPatrol.Posting("Some Other Ship-Stinger-1-1", new Location(20, 21)));

        List<String> problems = FighterPatrol.check(rn, stranger, 3, 40, 40);

        assertEquals(1, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("not a fighter in"));
    }

    @Test
    public void thesameFighterCannotBePostedTwice() {
        String name = firstFighterName();
        List<FighterPatrol.Posting> twice = List.of(
                new FighterPatrol.Posting(name, new Location(20, 21)),
                new FighterPatrol.Posting(name, new Location(21, 21)));

        List<String> problems = FighterPatrol.check(rn, twice, 3, 40, 40);

        assertTrue(problems.toString(), problems.stream().anyMatch(p -> p.contains("twice")));
    }

    // -------------------------------------------------------------------------
    // What it costs
    // -------------------------------------------------------------------------

    @Test
    public void postingAPatrolHoldsTheCarrierToItsFightersSpeed() {
        assertEquals("a Ranger set up at speed 20", 20, rn.getSpeed());

        List<String> log = FighterPatrol.deploy(game, rn, patrolOf(2));

        assertEquals("a Stinger-1 tops out at 12, so the carrier does too (S4.12)",
                12, rn.getSpeed());
        assertTrue("and it says so: " + log,
                log.stream().anyMatch(l -> l.contains("held to speed 12")));
    }

    @Test
    public void theSpeedCapReachesNextTurnsAcceleration() {
        // C2.2 builds next turn's ceiling from the two turns of speed history, so a carrier
        // that posts a patrol is not merely slow now — it accelerates from a lower number.
        int unrestricted = rn.getMaxAccelerationSpeed();

        FighterPatrol.deploy(game, rn, patrolOf(2));

        assertEquals("max(20+10, 20*2) capped at 31", 31, unrestricted);
        assertEquals("max(12+10, 12*2) — seven hexes given up for two fighters already up",
                24, rn.getMaxAccelerationSpeed());
    }

    @Test
    public void aCarrierSlowerThanItsFightersIsNotSpedUp() {
        rn.setSpeed(8);
        rn.setSpeedPreviousTurn(8);
        rn.setSpeedTwoTurnsAgo(8);

        FighterPatrol.deploy(game, rn, patrolOf(2));

        assertEquals("the cap is a ceiling, not a speed", 8, rn.getSpeed());
    }

    // -------------------------------------------------------------------------
    // Putting them up
    // -------------------------------------------------------------------------

    @Test
    public void aDeployedFighterLeavesItsBoxAndFliesTheCarriersHeading() {
        String name = firstFighterName();
        int inBaysBefore = countInBays();

        FighterPatrol.deploy(game, rn,
                List.of(new FighterPatrol.Posting(name, new Location(20, 21))));

        assertEquals("one fewer fighter aboard", inBaysBefore - 1, countInBays());
        Shuttle flying = game.getActiveShuttles().stream()
                .filter(s -> s.getName().equals(name)).findFirst().orElse(null);
        assertNotNull("and one more on the map", flying);
        assertEquals(new Location(20, 21), flying.getLocation());
        assertEquals("S4.12: the fighters and the ship share a facing",
                rn.getFacing(), flying.getFacing());
        assertEquals("J1.21: as fast as it can, unless told otherwise", 12, flying.getSpeed());
        assertTrue("its box is empty now",
                boxOf(name) == null || boxOf(name).isEmpty());
    }

    @Test
    public void aPatrolFighterMayFlySlowerIfTheCommanderWants() {
        String name = firstFighterName();

        FighterPatrol.deploy(game, rn,
                List.of(new FighterPatrol.Posting(name, new Location(20, 21), 6)));

        Shuttle flying = game.getActiveShuttles().get(0);
        assertEquals(6, flying.getSpeed());
        assertEquals("and the carrier is still held to the fighter's MAXIMUM, not its speed",
                12, rn.getSpeed());
    }

    private int countInBays() {
        int n = 0;
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter)
                    n++;
        return n;
    }

    private ShuttleSpace boxOf(String fighterName) {
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() != null && box.getShuttle().getName().equals(fighterName))
                    return box;
        return null;
    }
}
