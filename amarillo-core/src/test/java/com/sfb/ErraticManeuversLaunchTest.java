package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.KzintiShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;

/**
 * C10.511: "A unit using EM cannot launch drones, shuttles, fighters, probes (for information
 * or as weapons), PFs, or plasma torpedoes."
 * <p>
 * Every launch the rule names goes through {@code LaunchCoordinator}, so the prohibition is one
 * helper called from nine entry points rather than nine opinions about the same rule.
 * <p>
 * The two carve-outs are as much the rule as the ban, and both are pinned below:
 * <ul>
 * <li><b>Plasma bolts</b> — C10.511's own parenthesis, "plasma bolts are direct-fire weapons and
 *     can be used while under EM at the standard EM penalties". They never pass through a launch
 *     method, so honouring this costs nothing; the test is here so that stays true.</li>
 * <li><b>Chaff</b> — C10.516, "a fighter using EM can use chaff (D11.0)". It sits in the same
 *     class as the launches and must not be swept up with them.</li>
 * </ul>
 * Landing and recovery are a different rule (C10.53) and are not tested here.
 */
public class ErraticManeuversLaunchTest {

    private Game game;
    private Ship kzinti;        // drone racks
    private Ship romulan;       // plasma
    private Ship fed;           // shuttle bays
    private Ship target;
    private Player fedPlayer;

    @Before
    public void setUp() {
        game = new Game();
        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        kzinti = place(new Ship(), KzintiShips.getKzinBC(), "KHS Quasar", 10, 10);
        romulan = place(new Ship(), RomulanShips.getRomKr(), "IRW Gauntlet", 12, 10);
        fed = place(new Ship(), FederationShips.getFedCa(), "USS Enterprise", 14, 10);

        target = place(new Ship(), KlingonShips.getD7(), "IKV Saber", 10, 6);
        target.setFacing(13);

        game.startTurn();
        // C10.11: the six points go at allocation, whether or not the maneuver is ever begun.
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setErraticManuvers(s.getPerformanceData().getErraticCost());
            game.submitAllocation(s, e);
        }
        toActivity();
    }

    private Ship place(Ship s, java.util.Map<String, Object> data, String name, int x, int y) {
        s.init(data);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setOwner(fedPlayer);
        s.setActiveFireControl(true);
        game.getShips().add(s);
        return s;
    }

    /** Bring EM into force on one ship. C10.311: it takes effect at Stage 6E, not when said. */
    private void beginEm(Ship s) {
        assertTrue(game.announceErraticManeuvers(s, true).isSuccess());
        for (int i = 0; i < 8 && game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE; i++)
            game.advancePhase();
        game.advancePhase();
        assertTrue("fixture: EM should be in force", s.isEmEffective());
        toActivity();
    }

    private void toActivity() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    private DroneRack rack() {
        return kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack).map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));
    }

    private PlasmaLauncher launcher() {
        return romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof PlasmaLauncher).map(w -> (PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));
    }

    private static void refused(Game.ActionResult r, String what) {
        assertFalse(what + " should have been refused: " + r.getMessage(), r.isSuccess());
        assertTrue("and refused for C10.511, not by accident: " + r.getMessage(),
                r.getMessage().contains("C10.511"));
    }

    // ---------------------------------------------------------------- the prohibition

    @Test
    public void aShipConductingEmCannotLaunchADrone() {
        kzinti.addLockOn(target);
        beginEm(kzinti);

        DroneRack r = rack();
        refused(game.launchDrone(kzinti, target, r, r.getAmmo().get(0), 0), "a drone launch");
    }

    @Test
    public void aShipConductingEmCannotLaunchAPlasmaTorpedo() {
        romulan.addLockOn(target);
        beginEm(romulan);

        refused(game.launchPlasma(romulan, target, launcher(), false, 0), "a plasma launch");
    }

    @Test
    public void aShipConductingEmCannotLaunchAShuttle() {
        ShuttleBay bay = fed.getShuttles().getBays().get(0);
        Shuttle s = bay.getInventory().get(0);
        beginEm(fed);

        refused(game.launchShuttle(fed, bay, s, 6, 1), "a shuttle launch");
    }

    /**
     * A wild weasel is a shuttle, so C10.511 catches it. The refusal must come from the rule
     * and not from the absence of a charged shuttle, which is why it checks the citation.
     */
    @Test
    public void aShipConductingEmCannotLaunchAWildWeasel() {
        beginEm(fed);

        refused(game.launchWildWeasel(fed, "Shuttle 1", 1, 6), "a wild weasel launch");
    }

    // ---------------------------------------------------------------- the carve-outs

    /** C10.516: "A fighter using EM can use chaff (D11.0)." */
    @Test
    public void aFighterConductingEmMayStillDropChaff() {
        Fighter f = CataloguedFighter.of("stinger1");
        f.setName("Alpha 1");
        f.setOwner(fedPlayer);
        f.setLocation(new Location(10, 10));
        f.setFacing(1);
        f.setSpeed(8);
        f.setChaffPacks(2);
        game.getActiveShuttles().add(f);

        f.announceEm(true, game.getAbsoluteImpulse());
        f.applyEmAnnouncement(game.getAbsoluteImpulse());
        assertTrue("fixture: the fighter should be manoeuvring", f.isEmEffective());

        Game.ActionResult r = game.dropChaff(f);

        assertTrue("C10.516 allows this outright: " + r.getMessage(), r.isSuccess());
    }

    /**
     * C10.511's parenthesis: a plasma BOLT is direct fire and stays legal. Bolts are fired
     * through the weapon from the direct-fire path, never through a launch method, so the ban
     * cannot reach them. This pins that separation rather than any new code.
     */
    @Test
    public void theBanReachesTheLaunchPathOnlyNotTheWeapon() {
        romulan.addLockOn(target);
        beginEm(romulan);

        assertFalse("launching a torpedo is barred",
                game.launchPlasma(romulan, target, launcher(), false, 0).isSuccess());
        assertTrue("yet the launcher itself is untouched — a bolt is still direct fire",
                launcher().isFunctional());
    }

    // ---------------------------------------------------------------- not a blanket

    @Test
    public void theSameLaunchSucceedsWhenNotConductingEm() {
        kzinti.addLockOn(target);
        assertFalse("fixture: not manoeuvring", kzinti.isEmEffective());

        DroneRack r = rack();
        Game.ActionResult res = game.launchDrone(kzinti, target, r, r.getAmmo().get(0), 0);

        assertTrue("the guard must not bite when EM is off: " + res.getMessage(),
                res.isSuccess());
    }

    /** C10.24: held by a tractor, EM "cannot be conducted" — so the launch ban lifts with it. */
    @Test
    public void aShipHeldByATractorMayStillLaunch() {
        kzinti.addLockOn(target);
        beginEm(kzinti);
        kzinti.applyTractor(target);
        assertFalse("fixture: held, so not conducting EM", kzinti.isEmEffective());

        DroneRack r = rack();
        Game.ActionResult res = game.launchDrone(kzinti, target, r, r.getAmmo().get(0), 0);

        assertFalse("not refused by C10.511: " + res.getMessage(),
                res.getMessage().contains("C10.511"));
    }
}
