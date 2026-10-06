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
 * <h2>The firing rate, and the judgement call that was wrong</h2>
 * ONE torpedo per turn (FP9.36: "Fighters and MRS shuttles which carry type-D torpedoes can
 * fire one per turn unless specifically stated otherwise"), plus J4.24's quarter-turn spacing,
 * which J4.28 brings with it: type-Ds "are generally treated as type-I drones for purposes of
 * the above rules". FP13.3 shares the rate with the type-K.
 * <p>
 * This class previously argued the opposite and pinned it in a test called
 * {@code bothTorpedoesCanGoInOneImpulse}: that J4.24 was a rule about drones, that J4.825
 * shared only rearming and storage, and that "nothing in FP9.2, FP9.3 or FP10.3 gives a
 * fighter's plasma-Ds a rate of their own". The last clause was simply false - FP9.36 is in
 * FP9.3. The pin did its job in the end, but only because the owner asked about ship-mounted
 * racks and the subsection got read; a test that pins a conclusion cannot check the search
 * that produced it.
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
        ShuttleCatalog.loadDefault("../data");
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
     * FP9.36: one torpedo a turn. A Gladiator-F carries two and may send only the first.
     * <p>
     * This replaces {@code bothTorpedoesCanGoInOneImpulse}, which asserted the opposite on a
     * misreading - see the class comment. The second rail stays loaded, which is the part
     * worth asserting: the refusal must not consume the torpedo it declines to launch.
     */
    @Test
    public void onlyOneTorpedoMayGoInATurn() {
        int impulse = game.getAbsoluteImpulse();
        DroneRail first = railsOf().get(0);
        DroneRail second = railsOf().get(1);

        assertTrue(game.launchFighterPlasmaD(gf, enemy, first, 1).isSuccess());

        Game.ActionResult refused = game.launchFighterPlasmaD(gf, enemy, second, 1);
        assertFalse("a second torpedo in the same turn", refused.isSuccess());
        assertTrue(refused.getMessage(), refused.getMessage().contains("FP9.36"));

        assertEquals("same impulse", impulse, game.getAbsoluteImpulse());
        assertEquals("one torpedo away", 1, torpedoesInSpace());
        assertFalse("the one that flew is gone", first.isLoaded());
        assertTrue("the one refused is still aboard", second.isLoaded());
    }

    /**
     * J4.24's quarter turn, which J4.28 brings to the type-D by treating it as a type-I drone.
     * <p>
     * The count alone is not enough and this is why: a new turn clears it, so a fighter that
     * launched late in one turn would otherwise launch again immediately in the next - one
     * torpedo in each turn, the per-turn rule satisfied, and four impulses between them.
     */
    @Test
    public void theQuarterTurnSpacingReachesAcrossTheTurnBoundary() {
        assertTrue(game.launchFighterPlasmaD(gf, enemy, railsOf().get(0), 1).isSuccess());

        // A fresh turn clears the per-turn count but not the spacing.
        gf.startTurn();
        assertEquals("count cleared", 0, gf.getPlasmaTorpedoesFiredThisTurn());

        // Driven through the LAUNCH, not through the fighter's own method: the count and the
        // spacing come out of one refusal, so a test that asked the fighter directly would
        // keep passing with the gate unwired. The second rail is still loaded, so nothing but
        // the spacing can refuse this.
        Game.ActionResult refused =
                game.launchFighterPlasmaD(gf, enemy, railsOf().get(1), 1);
        assertFalse("still inside the quarter turn", refused.isSuccess());
        assertTrue(refused.getMessage(), refused.getMessage().contains("quarter turn"));

        // Eight impulses after the launch it is free again.
        assertNull(gf.plasmaTorpedoLaunchRefusal(
                game.getAbsoluteImpulse() + Fighter.DRONE_LAUNCH_SPACING));
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
