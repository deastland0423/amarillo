package com.sfb;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;

/**
 * J3.203: a wild weasel can be shot at in its own right, and a weapon aimed at the decoy is
 * not a weapon the decoy stole.
 *
 * <blockquote>"The WW can be targeted in its own right; in this case any seeking weapons would
 * <b>not</b> revert to the protected ship if the weasel was voided."</blockquote>
 *
 * <h2>Why one bit, and not a remembered target</h2>
 * The obvious shape for this is to have each weapon remember what it was chasing before the
 * weasel took it. It does not need to. J3.202 settles where a reverting weapon goes:
 *
 * <blockquote>"Wild weasels protect the ship that launched them and no other."</blockquote>
 *
 * <p>So a weasel's parent IS the original target of everything it drew, by construction, and a
 * stored copy could only disagree with it. What the parent cannot say is whether a given
 * weapon was DRAWN there at all — which is the one bit {@link Seeker#isDivertedToDecoy()}
 * carries, set in the two places that divert (J3.20 at launch of the weasel, J3.201 at launch
 * of the weapon) and nowhere else.
 */
public class WildWeaselRevertTest {

    private Game game;
    private Ship launcher;
    private Ship consort;
    private Ship decoyed;

    // ------------------------------------------------------------------------ fixtures

    /** A drone-armed Federation FFG four hexes from a Klingon D7 that puts a weasel up. */
    private void droneFixture() {
        game = new Game();

        launcher = new Ship();
        launcher.init(FederationShips.getFedFfg());
        launcher.setName("Burke");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);
        launcher.setActiveFireControl(true);

        // A second hull, because a rack launches once per turn (FD3.2) and one of these
        // tests needs two drones on the same decoy at once.
        consort = new Ship();
        consort.init(FederationShips.getFedFfg());
        consort.setName("Dale");
        consort.setLocation(new Location(11, 10));
        consort.setFacing(1);
        consort.setActiveFireControl(true);

        decoyed = new Ship();
        decoyed.init(KlingonShips.getD7());
        decoyed.setName("IKV Saber");
        decoyed.setLocation(new Location(10, 14));
        decoyed.setFacing(1);

        game.getShips().add(launcher);
        game.getShips().add(consort);
        game.getShips().add(decoyed);
        game.startTurn();
        for (Ship s : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        launcher.addLockOn(decoyed);
        consort.addLockOn(decoyed);
        toLaunchPhase();
        putUpWeasel();
    }

    /** A Romulan KR with plasma, against the same weaseled D7, four hexes dead ahead. */
    private void plasmaFixture() {
        game = new Game();

        launcher = new Ship();
        launcher.init(RomulanShips.getRomKr());
        launcher.setName("IRW Gauntlet");
        launcher.setLocation(new Location(10, 10));
        launcher.setFacing(1);
        launcher.setActiveFireControl(true);

        decoyed = new Ship();
        decoyed.init(KlingonShips.getD7());
        decoyed.setName("IKV Saber");
        decoyed.setLocation(new Location(10, 6));       // four hexes dead ahead
        decoyed.setFacing(13);

        game.getShips().add(launcher);
        game.getShips().add(decoyed);
        toLaunchPhase();
        putUpWeasel();
    }

    private void toLaunchPhase() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.canLaunchThisPhase())
                return;
            game.advancePhase();
        }
        fail("fixture: never reached a phase that allows launching");
    }

    /** J3.11: a weasel is two impulses of deck work away from being any use. */
    private void putUpWeasel() {
        com.sfb.objects.shuttles.Shuttle sh =
                decoyed.getShuttles().getBays().get(0).launchableInventory().get(0);
        sh.incrementWwCharge();
        sh.incrementWwCharge();
        Game.ActionResult r = game.launchWildWeasel(decoyed, sh.getName(), 13, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        assertNotNull("fixture: the decoy is up", decoyed.getActiveWildWeasel());
    }

    private WildWeaselShuttle weasel() {
        return decoyed.getActiveWildWeasel();
    }

    private DroneRack rack(Ship ship) {
        return ship.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .filter(DroneRack::canFire)
                .filter(r -> !r.isEmpty())
                .findFirst().orElseThrow(() -> new AssertionError("no rack ready to launch"));
    }

    private Drone launchDroneAt(Unit target, DroneType type) {
        return launchDroneAt(launcher, target, type);
    }

    private Drone launchDroneAt(Ship from, Unit target, DroneType type) {
        DroneRack rack = rack(from);
        Drone d = rack.getAmmo().get(0);
        d.setDroneType(type);
        Game.ActionResult r = game.launchDrone(from, target, rack, d, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        return d;
    }

    private PlasmaTorpedo launchPlasmaAt(Unit target) {
        PlasmaLauncher tube = launcher.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof PlasmaLauncher)
                .map(w -> (PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));
        Game.ActionResult r = game.launchPlasma(launcher, target, tube, false, 0);
        assertTrue(r.getMessage(), r.isSuccess());
        List<PlasmaTorpedo> live = game.getSeekers().stream()
                .filter(s -> s instanceof PlasmaTorpedo)
                .map(s -> (PlasmaTorpedo) s)
                .collect(Collectors.toList());
        assertEquals("fixture: exactly one torpedo in flight", 1, live.size());
        return live.get(0);
    }

    private boolean inPlay(Seeker s) {
        return game.getSeekers().contains(s);
    }

    // ------------------------------------------------- J3.201: drawn off, and it reverts

    /**
     * J3.201: "All seeking weapons launched with that ship... as a target while an undestroyed
     * and unvoided WW is on the map will not accept the ship as a target, but instead follow
     * the WW." And being drawn off is what earns a weapon its way back.
     */
    @Test
    public void aDroneLaunchedAtTheShipIsDrawnOffAndReverts() {
        droneFixture();
        Drone d = launchDroneAt(decoyed, DroneType.TypeI);

        assertEquals("J3.201: it took the decoy, not the ship", weasel(), d.getTarget());
        assertTrue("and it is marked as having been drawn there", d.isDivertedToDecoy());

        game.voidWildWeasel(decoyed);

        assertEquals("J3.13x: back onto the ship it was aimed at", decoyed, d.getTarget());
        assertTrue(inPlay(d));
        assertFalse("and the mark is spent", d.isDivertedToDecoy());
    }

    /** The same for a torpedo, which comes through a different launch door to the same helper. */
    @Test
    public void aTorpedoLaunchedAtTheShipIsDrawnOffAndReverts() {
        plasmaFixture();
        PlasmaTorpedo t = launchPlasmaAt(decoyed);

        assertEquals(weasel(), t.getTarget());
        assertTrue(t.isDivertedToDecoy());

        game.voidWildWeasel(decoyed);

        assertEquals(decoyed, t.getTarget());
        assertTrue(inPlay(t));
    }

    // --------------------------------------------- J3.203: aimed at it, and it does not

    /**
     * J3.203. The weapon never had a quarrel with the ship, so voiding the decoy gives it
     * nothing to transfer to — and a voided weasel leaves the map, so it has nothing left to
     * chase at all and goes inert (FD1.7).
     */
    @Test
    public void aDroneAimedAtTheWeaselDoesNotRevertToTheShip() {
        droneFixture();
        Drone d = launchDroneAt(weasel(), DroneType.TypeI);

        assertEquals("fixture: it is chasing the decoy", weasel(), d.getTarget());
        assertFalse("but nothing drew it there", d.isDivertedToDecoy());

        game.voidWildWeasel(decoyed);

        assertNotEquals("J3.203: it does not revert to the protected ship",
                decoyed, d.getTarget());
        assertFalse("FD1.7: nothing left to chase", inPlay(d));
    }

    /** And the same for a torpedo deliberately spent on the decoy. */
    @Test
    public void aTorpedoAimedAtTheWeaselDoesNotRevertToTheShip() {
        plasmaFixture();
        PlasmaTorpedo t = launchPlasmaAt(weasel());

        assertEquals(weasel(), t.getTarget());
        assertFalse(t.isDivertedToDecoy());

        game.voidWildWeasel(decoyed);

        assertNotEquals(decoyed, t.getTarget());
        assertFalse(inPlay(t));
    }

    /**
     * The two cases side by side, since the whole rule is the difference between them: same
     * weapon, same decoy, same void, opposite outcome.
     */
    @Test
    public void twoDronesOnTheSameDecoyPartCompany() {
        droneFixture();
        Drone drawn = launchDroneAt(decoyed, DroneType.TypeI);
        Drone aimed = launchDroneAt(consort, weasel(), DroneType.TypeI);
        assertEquals("fixture: both chasing the decoy", drawn.getTarget(), aimed.getTarget());

        game.voidWildWeasel(decoyed);

        assertEquals(decoyed, drawn.getTarget());
        assertTrue(inPlay(drawn));
        assertFalse(inPlay(aimed));
    }

    // ------------------------------------------------- FD5.131, now asked at launch too

    /**
     * FD5.131: a dogfight drone with its own lock-on "cannot be distracted by... wild weasels
     * (J3.2)" — and J3.201 is inside J3.2, so the exception has to be asked at the launch
     * redirect as well as at the weasel's own launch.
     *
     * <p>It bites because of FD5.12: a type-VI thrown from inside eight hexes has its lock-on
     * before it is ever pointed anywhere. The decoy is up, and it goes for the ship anyway.
     */
    @Test
    public void aDogfightDroneLaunchedInsideEightIgnoresTheDecoy() {
        droneFixture();
        Drone d = launchDroneAt(decoyed, DroneType.TypeVI);

        assertTrue("fixture: four hexes, so it locked on at launch (FD5.12)", d.hasOwnLockOn());
        assertEquals("FD5.131: the decoy does not get it", decoyed, d.getTarget());
        assertFalse(d.isDivertedToDecoy());
    }

    /** FD5.133: one that has not acquired is drawn off like anything else. */
    @Test
    public void aDogfightDroneWithNoLockOnOfItsOwnIsStillDrawnOff() {
        droneFixture();
        decoyed.setLocation(new Location(10, 30));          // twenty hexes: too far to acquire
        weasel().setLocation(new Location(10, 30));
        Drone d = launchDroneAt(decoyed, DroneType.TypeVI);

        assertFalse("fixture: nothing of its own to steer by yet", d.hasOwnLockOn());
        assertEquals("FD5.133: the decoy gets it", weasel(), d.getTarget());
        assertTrue(d.isDivertedToDecoy());
    }

    // ------------------------------------------------------------------------ the save

    /**
     * The mark has to survive a save, because the question it answers is only ever asked later
     * — possibly several turns later, by which time nothing else on the board records how a
     * weapon came to be chasing a decoy.
     */
    @Test
    public void theMarkSurvivesASave() {
        droneFixture();
        Drone d = launchDroneAt(decoyed, DroneType.TypeI);
        assertTrue(d.isDivertedToDecoy());

        Drone restored = Drone.fromState(d.exportState(), weasel(), launcher, null);
        assertNotNull(restored);
        assertTrue("J3.203: it is still a drone that was drawn off",
                restored.isDivertedToDecoy());

        Drone plain = new Drone(DroneType.TypeI);
        assertFalse(Drone.fromState(plain.exportState(), decoyed, launcher, null)
                .isDivertedToDecoy());
    }
}
