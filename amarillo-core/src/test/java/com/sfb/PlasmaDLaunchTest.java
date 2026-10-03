package com.sfb;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * A fighter launches a type-D plasma torpedo off its rail (FP9.2).
 * <p>
 * The last piece: the mount is a rail, the gate is FP9.22's activation, and what leaves is a
 * seeking plasma torpedo placed exactly as a ship's would be. So the fighter-side conditions
 * are a drone launch's - crippled (J1.332), tractored (J1.6202), the half turn since its own
 * launch (J1.341) - while the torpedo-side placement is {@code launchPlasma}'s.
 *
 * <h2>The judgement call in here</h2>
 * J4.24's drone firing rate is NOT applied. It is a rule about drones ("DRONE FIRING RATES: A
 * fighter can always launch one drone per turn..."), and a plasma-D is not a drone: J4.825
 * shares the drone rules for "rearming and storage" only, and Annex #4's listing of Pl-Ds in
 * the drone column is by its own admission "to avoid confusing them with the plasma-Fs" - a
 * presentation choice. Nothing in FP9.2, FP9.3 or FP10.3 gives a fighter's plasma-Ds a rate of
 * their own.
 * <p>
 * So a Gladiator-F may send both torpedoes in the same impulse, which
 * {@link #bothTorpedoesCanGoInOneImpulse} pins deliberately rather than by accident. J1.341
 * still bites and is the only spacing there is. If a rate does turn out to apply, it belongs
 * beside the J4.242 flags where the other per-fighter launch limits live.
 */
public class PlasmaDLaunchTest {

    private Game game;
    private Ship carrier;
    private Ship enemy;
    private Fighter gf;
    private Player romulan;
    private Player federation;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
        game = new Game();

        romulan = new Player();
        romulan.setTeamName("Romulan");
        federation = new Player();
        federation.setTeamName("Federation");

        carrier = ShipLibrary.createShip(ShipLibrary.get("Romulan", "WH"));
        carrier.setName("RIS Warhawk");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        carrier.setOwner(romulan);
        carrier.setActiveFireControl(true);
        game.getShips().add(carrier);

        enemy = ShipLibrary.createShip(ShipLibrary.get("Federation", "CA"));
        enemy.setName("USS Constitution");
        enemy.setLocation(new Location(10, 6));     // dead ahead, four hexes off
        enemy.setFacing(13);
        enemy.setOwner(federation);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        game.startTurn();
        for (Ship s : new Ship[] { carrier, enemy }) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setTractors(4);          // a beam takes no hold without power for it (G7.4)
            game.submitAllocation(s, e);
        }
        toActivity();

        // A Gladiator-F in space, its rails loaded. Put straight onto the map rather than
        // launched from the carrier, because J1.341 would otherwise forbid a seeking weapon
        // for half a turn after it left the bay - which its own test covers.
        gf = CataloguedFighter.of("gf");
        gf.setName("GF-1");
        gf.setOwner(romulan);
        gf.setLocation(carrier.getLocation());
        gf.setFacing(1);
        gf.setParentShipName(carrier.getName());
        game.getActiveShuttles().add(gf);
        for (DroneRail rail : railsOf())
            rail.loadTorpedo(torpedo(), true);      // pre-loaded, so active (FP9.22)
    }

    private void toActivity() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    private java.util.List<DroneRail> railsOf() {
        java.util.List<DroneRail> rails = new java.util.ArrayList<>();
        for (Weapon w : gf.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.isPlasmaD())
                rails.add(rail);
        return rails;
    }

    private static PlasmaTorpedo torpedo() {
        return new PlasmaTorpedo(PlasmaType.D, WeaponArmingType.STANDARD);
    }

    private long torpedoesInSpace() {
        return game.getSeekers().stream()
                .filter(s -> s.getSeekerType() == Seeker.SeekerType.PLASMA).count();
    }

    // ---------------------------------------------------------------- it flies

    @Test
    public void anActivatedTorpedoLaunches() {
        DroneRail rail = railsOf().get(0);

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, rail, 1);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("plasma-D"));
        assertEquals("one torpedo in space", 1, torpedoesInSpace());
        assertFalse("and the rail is empty", rail.isLoaded());
    }

    /** It reaches the map as a plasma seeker, aimed, controlled and placed on the fighter. */
    @Test
    public void theTorpedoIsPlacedLikeAnyOther() {
        assertTrue(game.launchFighterPlasmaD(gf, enemy, railsOf().get(0), 1).isSuccess());

        PlasmaTorpedo torpedo = (PlasmaTorpedo) game.getSeekers().stream()
                .filter(s -> s.getSeekerType() == Seeker.SeekerType.PLASMA)
                .findFirst().orElseThrow();

        assertEquals(PlasmaType.D, torpedo.getPlasmaType());
        assertEquals("in the fighter's hex", gf.getLocation(), torpedo.getLocation());
        assertEquals(enemy, torpedo.getTarget());
        assertEquals("the fighter guides it", gf, torpedo.getController());
        assertTrue("named for its launcher", torpedo.getName().startsWith("GF-1-Plasma-"));
    }

    // ---------------------------------------------------------------- FP9.22's gate

    /**
     * The gate the previous slice built. An unactivated torpedo is refused, and refused by
     * name so the player knows what it costs.
     */
    @Test
    public void anUnactivatedTorpedoIsRefused() {
        DroneRail rail = railsOf().get(0);
        rail.removeTorpedo();
        rail.loadTorpedo(torpedo());            // a deck crew's reload: inert

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, rail, 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("FP9.22"));
        assertTrue("still on the rail", rail.isLoaded());
        assertEquals("nothing in space", 0, torpedoesInSpace());
    }

    /** And paying for it makes the same launch succeed. */
    @Test
    public void activatingItLetsTheSameLaunchThrough() {
        DroneRail rail = railsOf().get(0);
        rail.removeTorpedo();
        rail.loadTorpedo(torpedo());
        assertFalse(game.launchFighterPlasmaD(gf, enemy, rail, 1).isSuccess());

        assertTrue(rail.activateTorpedo(DroneRail.ACTIVATION_ENERGY));

        assertTrue(game.launchFighterPlasmaD(gf, enemy, rail, 1).isSuccess());
        assertEquals(1, torpedoesInSpace());
    }

    @Test
    public void anEmptyRailIsRefused() {
        DroneRail rail = railsOf().get(0);
        rail.removeTorpedo();

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, rail, 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("empty"));
    }

    /** A drone rail is not a plasma rail, and the mix-up is refused rather than guessed at. */
    @Test
    public void aDroneRailIsRefused() {
        DroneRail standard = new DroneRail(DroneRail.DroneRailType.STANDARD);
        standard.setDesignator("X");

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, standard, 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("drone rail"));
    }

    // ---------------------------------------------------------------- the rate question

    /**
     * BOTH torpedoes in one impulse, which is the judgement recorded in the class comment:
     * J4.24's rate is a rule about drones and a plasma-D is not one.
     * <p>
     * Pinned so the decision is visible. If a rate does apply, this is the test that should
     * fail and be rewritten - not one that quietly keeps passing because nobody looked.
     */
    @Test
    public void bothTorpedoesCanGoInOneImpulse() {
        int impulse = game.getAbsoluteImpulse();

        for (DroneRail rail : railsOf())
            assertTrue("rail " + rail.getDesignator(),
                    game.launchFighterPlasmaD(gf, enemy, rail, 1).isSuccess());

        assertEquals("same impulse", impulse, game.getAbsoluteImpulse());
        assertEquals("two torpedoes away", 2, torpedoesInSpace());
        for (DroneRail rail : railsOf())
            assertFalse(rail.isLoaded());
    }

    // ---------------------------------------------------------------- the fighter's own state

    /** J1.332: a crippled fighter has dropped its external weapons. */
    @Test
    public void aCrippledFighterLaunchesNothing() {
        gf.applyCripplingEffects();     // J1.331/J1.332, the real path

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, railsOf().get(0), 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J1.332"));
    }

    /** J1.6202: held in a tractor, a shuttle "may not fire, launch, or guide any weapon". */
    @Test
    public void aTractoredFighterLaunchesNothing() {
        // G7.412: a beam needs a lock-on first. The carrier never acquired one because the
        // fixture puts the fighter on the map directly rather than launching it.
        carrier.addLockOn(gf);
        Game.ActionResult grab = game.establishTractor(carrier, gf.getName(), 1);
        assertTrue("fixture: " + grab.getMessage(), grab.isSuccess());

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, railsOf().get(0), 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J1.6202"));
    }

    /**
     * C10.511: "A unit using EM cannot launch drones, shuttles, fighters, probes..., PFs, or
     * plasma torpedoes." A fighter on erratic maneuvers is included.
     */
    @Test
    public void aFighterOnErraticManeuversLaunchesNothing() {
        // Announcing is not conducting: C10.3 puts the announcement at Stage 6E and
        // applyEmAnnouncement is what brings it into force on that impulse.
        gf.announceEm(true, game.getAbsoluteImpulse());
        assertTrue("fixture: EM must actually be in force",
                gf.applyEmAnnouncement(game.getAbsoluteImpulse()));

        Game.ActionResult r = game.launchFighterPlasmaD(gf, enemy, railsOf().get(0), 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("C10.511"));
    }
}
