package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.Player;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.PlasmaLauncher;

/**
 * Every kind of seeking weapon can be put on a ballistic course (F4.0), not just a drone off a
 * ship's rack.
 * <p>
 * The movement half was never in doubt — the movers steer by
 * {@link com.sfb.objects.Seeker#destination()} and do not care what they are moving. What each
 * weapon needed was a DOOR: a launch path that takes a hex. The gates differ per weapon and
 * none of the differences are about the target, so what drops out is the same every time —
 * the lock-on (D6.121 has nothing to lock on to), the control channel (F4.12), and for a
 * plasma the self-guidance (FP4.23).
 */
public class BallisticLaunchDoorsTest {

    private Game game;
    private Ship romulan;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();

        romulan = new Ship();
        romulan.init(RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setLocation(new Location(10, 10));
        romulan.setFacing(1);                       // pointing up the column

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 20));
        klingon.setFacing(13);

        game.getShips().add(romulan);
        game.getShips().add(klingon);

        game.advancePhase();                        // MOVEMENT -> ACTIVITY
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private PlasmaLauncher plasmaTube() {
        return romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof PlasmaLauncher)
                .map(w -> (PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));
    }

    private void impulse() {
        for (int i = 0; i < 20; i++) {
            boolean wasMovement = game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT;
            game.advancePhase();
            if (wasMovement)
                return;
        }
        fail("fixture: never completed a movement phase");
    }

    // ------------------------------------------------------------------ plasma, from a tube

    /**
     * FP4.23: "a ballistically launched plasma torpedo cannot acquire its own target." A plasma
     * is otherwise self-guiding, so this is the clause that stops a ballistic one quietly
     * behaving like an ordinary one. It is enforced by giving it nothing to acquire with.
     */
    @Test
    public void aPlasmaTorpedoCanBeLaunchedAtAHex() {
        ActionResult r = game.launchPlasmaBallistic(romulan, new Location(10, 6),
                plasmaTube(), false, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("ballistic course (F4.0)"));

        PlasmaTorpedo torp = (PlasmaTorpedo) game.getSeekers().get(game.getSeekers().size() - 1);
        assertTrue(torp.isBallistic());
        assertNull("no target to re-acquire (FP4.23)", torp.getTarget());
        assertNull("and nobody guiding it (F4.12)", torp.getController());
        assertEquals(new Location(10, 6), torp.destination());
    }

    /** It flies the course and does not explode on whatever is standing there (F4.21/F4.412). */
    @Test
    public void aBallisticPlasmaFliesPastAShipInItsHex() {
        klingon.setLocation(new Location(10, 6));
        int shieldsBefore = totalShields(klingon);

        assertTrue(game.launchPlasmaBallistic(romulan, new Location(10, 6),
                plasmaTube(), false, 0).isSuccess());
        Seeker torp = game.getSeekers().get(game.getSeekers().size() - 1);
        ((PlasmaTorpedo) torp).setSpeed(32);

        for (int i = 0; i < 5; i++)
            impulse();

        assertEquals("a ballistic torpedo cannot hit a ship in space (F4.412)",
                shieldsBefore, totalShields(klingon));
    }

    /** The launcher's own firing arc still binds — the hex is judged exactly as a unit was. */
    @Test
    public void theLauncherArcStillBinds() {
        ActionResult r = game.launchPlasmaBallistic(romulan, new Location(10, 14),
                plasmaTube(), false, 0);

        assertFalse("astern of a forward launcher", r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("outside launcher arc"));
    }

    @Test
    public void aPlasmaRefusesAHexOffTheMap() {
        ActionResult r = game.launchPlasmaBallistic(romulan, new Location(0, 0),
                plasmaTube(), false, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("F4.11"));
    }

    // ------------------------------------------------------------------ suicide shuttle

    /**
     * F4.21 again, and it is the whole point of sending one: it will NOT explode at the hex.
     * What it buys is deception, which the rule names outright.
     */
    @Test
    public void aSuicideShuttleCanBeLaunchedAtAHex() {
        ShuttleBay bay = romulan.getShuttles().getBays().get(0);
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("SH-1");
        bay.replaceShuttle(bay.getInventory().get(0), ss);
        ss.arm(3);
        ss.arm(3);
        ss.arm(3);

        ActionResult r = game.launchSuicideShuttleBallistic(romulan, bay, ss,
                new Location(10, 6), 1, 6);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("ballistic course (F4.0)"));
        assertTrue(ss.isBallistic());
        assertNull("no target unit", ss.getTarget());
        assertEquals(new Location(10, 6), ss.destination());
    }

    /** No lock-on is needed, where the ordinary door demands one. */
    @Test
    public void theSuicideShuttleNeedsNoLockOn() {
        ShuttleBay bay = romulan.getShuttles().getBays().get(0);
        SuicideShuttle ss = new SuicideShuttle(new AdminShuttle());
        ss.setName("SH-1");
        bay.replaceShuttle(bay.getInventory().get(0), ss);
        ss.arm(3);
        ss.arm(3);
        ss.arm(3);
        assertFalse("fixture: no lock-on to anything", romulan.hasLockOn(klingon));

        assertTrue(game.launchSuicideShuttleBallistic(romulan, bay, ss,
                new Location(10, 6), 1, 6).isSuccess());
    }

    // ------------------------------------------------------------------ a fighter's own drone

    /**
     * The interesting one. J4.222 exists because some fighters carry no guidance equipment of
     * their own — the Kzinti AS, "only a manned drone launch platform" — and must hand their
     * drones to a carrier. A BALLISTIC drone needs no guidance from anybody (F4.12), so that
     * fighter can launch one with no friendly unit involved at all.
     */
    @Test
    public void aFighterWithNoGuidanceOfItsOwnCanStillLaunchBallistically() {
        Game g = new Game();
        Player kz = new Player();
        kz.setTeamName("Kzinti");

        Fighter aas = CataloguedFighter.of("as");
        aas.setName("AS-1");
        aas.setLocation(new Location(10, 10));
        aas.setFacing(1);
        aas.setOwner(kz);
        g.getActiveShuttles().add(aas);
        g.advancePhase();                                     // MOVEMENT -> ACTIVITY
        assertEquals(Game.ImpulsePhase.ACTIVITY, g.getCurrentPhase());
        aas.setLaunchImpulse(g.getAbsoluteImpulse() - 100);   // J1.341 satisfied

        DroneRail rail = aas.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRail)
                .map(w -> (DroneRail) w)
                .findFirst().orElseThrow(() -> new AssertionError("the AS should carry rails"));
        if (rail.getDrone() == null)
            rail.loadDrone(new Drone(DroneType.TypeI));

        assertFalse("fixture: this is the fighter J4.222 was written for",
                aas.guidesItsOwnDrones());

        ActionResult r = g.launchFighterDroneBallistic(aas, new Location(10, 6), rail, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        Drone drone = (Drone) g.getSeekers().get(g.getSeekers().size() - 1);
        assertTrue(drone.isBallistic());
        assertNull("nobody had to take guidance (F4.12)", drone.getController());
    }

    // ------------------------------------------------------------------ the one refusal

    /**
     * A RULING, not a quotation, and worth a test for that reason. Defensive mode buys its rate
     * of fire with FP10.212's restrictions on the target's size class and range; a hex has
     * neither, so a defensive ballistic shot would take the rate without paying for it.
     */
    @Test
    public void aPlasmaRackMayOnlyFireBallisticallyInOffensiveMode() {
        com.sfb.weapons.PlasmaRack rack = new com.sfb.weapons.PlasmaRack();
        rack.setDesignator("1");

        ActionResult r = game.launchPlasmaRackBallistic(romulan, new Location(10, 6), rack,
                com.sfb.weapons.PlasmaRack.RackMode.DEFENSIVE, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP10.212"));
    }

    private int totalShields(Ship ship) {
        int total = 0;
        for (int s = 1; s <= ship.getShields().getShieldCount(); s++)
            total += ship.getShields().getShieldStrength(s);
        return total;
    }
}
