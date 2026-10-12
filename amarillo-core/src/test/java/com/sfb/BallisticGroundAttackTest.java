package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.samples.KzintiShips;
import com.sfb.weapons.DroneRack;

/**
 * Ballistic bombardment of a ground base (P2.713) — ground bases, slice 5b.
 *
 * <p>"Seeking weapons can be fired at a ground base using ballistic targeting (F4.0), but only
 * from a range of 4 hexes or less. Weapons fired in this manner cannot be distracted by ECM or
 * WW. Weapons fired ballistically at ground targets do explode on impact. Weapons cannot be
 * fired for ballistic bombardment under passive fire control (D19.0) as target tracking is
 * inadequate."
 *
 * <p>Four sentences, and they pull in two directions at once. The explosion is an EXCEPTION to
 * F4.21, which says a ballistic weapon "will normally never explode" — a ground target is the
 * normal case's exception. And the fire control requirement runs OPPOSITE to ordinary ballistic
 * targeting: D19.224 lets a ship on passive fire control throw ballistic weapons at a hex
 * freely, while P2.713 forbids it at a ground target. So a bombardment is the one ballistic
 * shot that needs active fire control.
 *
 * <p>What the attacker buys with the four-hex approach is immunity: no ECM, and no wild weasel.
 * P2.732 lets a ground base put a weasel up against ordinary seekers and explicitly exempts
 * ballistic ones, which is the entire reason to come in this close.
 */
public class BallisticGroundAttackTest {

    private Game game;
    private Terrain planet;
    private Ship base;
    private Ship kzinti;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();

        planet = new Terrain(TerrainType.PLANET, 20, 20, 1);
        game.addTerrain(planet);

        ShipSpec spec = ShipLibrary.get("Neutral", "GBD1");
        assertNotNull("fixture: the GBD1 should be in the library", spec);
        base = ShipLibrary.createShip(spec);
        base.setName("Ground Battery");
        assertTrue(game.placeGroundBase(base, planet, 1).isSuccess());

        kzinti = new Ship();
        kzinti.init(KzintiShips.getKzinBC());
        kzinti.setName("KHS Quasar");
        kzinti.setFacing(1);
        kzinti.setActiveFireControl(true);
        game.getShips().add(kzinti);

        // Three hexes "north" of the base, which is inside P2.713's four.
        placeAttackerAtRange(3);

        game.advancePhase();
        assertEquals(Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private void placeAttackerAtRange(int range) {
        Location hex = base.getLocation();
        kzinti.setLocation(new Location(hex.getX(), hex.getY() - range));
        assertEquals("fixture: attacker should be at range " + range, range,
                com.sfb.utilities.MapUtils.getRange(kzinti.getLocation(), base.getLocation()));
    }

    private DroneRack rack() {
        return kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack)
                .map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));
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

    // ---------------------------------------------------------------- it explodes

    /** The exception to F4.21, and the point of the whole slice. */
    @Test
    public void aBallisticDroneExplodesAgainstAGroundBase() {
        ActionResult r = game.launchDroneGroundAttack(kzinti, base, rack());
        assertTrue(r.getMessage(), r.isSuccess());

        Drone drone = (Drone) game.getSeekers().get(game.getSeekers().size() - 1);
        drone.setSpeed(32);
        assertTrue("it flies a ballistic course", drone.isBallistic());
        assertTrue("aimed at the base, not merely its hex",
                drone.getBallisticCourse().isGroundAttack());

        int shieldBefore = base.getShields().getShieldStrength(1);

        for (int i = 0; i < 6; i++)
            impulse();

        assertFalse("the drone is spent — it exploded (P2.713)", game.getSeekers().contains(drone));
        assertTrue("and the base took the warhead on its one shield (P2.73)",
                base.getShields().getShieldStrength(1) < shieldBefore);
    }

    /**
     * The contrast that makes the rule visible: the SAME weapon aimed at the base's hex rather
     * than at the base flies straight through and evades (F4.21).
     */
    @Test
    public void thatSameDroneAimedAtTheHexDoesNotExplode() {
        ActionResult r = game.launchDroneBallistic(kzinti, base.getLocation(), rack());
        assertTrue(r.getMessage(), r.isSuccess());

        Drone drone = (Drone) game.getSeekers().get(game.getSeekers().size() - 1);
        drone.setSpeed(32);
        assertFalse("a hex is not a ground target", drone.getBallisticCourse().isGroundAttack());

        int shieldBefore = base.getShields().getShieldStrength(1);

        for (int i = 0; i < 6; i++)
            impulse();

        assertTrue("still flying (F4.21)", game.getSeekers().contains(drone));
        assertEquals("and the base was never touched",
                shieldBefore, base.getShields().getShieldStrength(1));
    }

    /**
     * "Weapons fired in this manner cannot be distracted by ECM or WW." This is what the
     * four-hex approach BUYS, and it is tested at the rule rather than through a flight.
     * <p>
     * The first version of this test flew a drone at a base with six points of ECM forced onto
     * it and asserted that no proximity roll was made. It passed with the rule REMOVED, because
     * the forced allocation did not survive to the impulse of impact — the shift was zero
     * either way and the test was asserting nothing. Hence {@code impactEcmShift}, which can be
     * asked directly.
     * <p>
     * P2.732 is the other half: a ground base may put up a wild weasel against ordinary seekers
     * and ballistic ones are explicitly exempt. Our weasel redirect happens at launch and keys
     * off the target UNIT, of which a ballistic weapon has none, so that half holds structurally.
     */
    @Test
    public void ecmCannotDistractABombardment() {
        base.setEcmAllocated(6);
        Drone bombard = new Drone(com.sfb.objects.DroneType.TypeI);
        bombard.setBallisticCourse(new com.sfb.objects.BallisticCourse(base.getLocation(), base));

        Drone ordinary = new Drone(com.sfb.objects.DroneType.TypeI);
        ordinary.setTarget(base);

        assertTrue("fixture: six points of ECM must actually shift an ordinary seeker",
                SeekerMover.computeSeekerEcmShift(ordinary, base) > 0);
        assertEquals("a bombardment ignores all of it (P2.713)",
                0, SeekerMover.impactEcmShift(bombard, base, 0));
    }

    /** And terrain ECM along the line — the ground clutter it is sitting in — is ignored too. */
    @Test
    public void terrainEcmCannotDistractABombardmentEither() {
        Drone bombard = new Drone(com.sfb.objects.DroneType.TypeI);
        bombard.setBallisticCourse(new com.sfb.objects.BallisticCourse(base.getLocation(), base));

        assertEquals(0, SeekerMover.impactEcmShift(bombard, base, 8));
    }

    // ---------------------------------------------------------------- the four-hex limit

    @Test
    public void fourHexesIsAllowed() {
        placeAttackerAtRange(4);

        assertTrue(game.launchDroneGroundAttack(kzinti, base, rack()).isSuccess());
    }

    @Test
    public void fiveHexesIsRefused() {
        placeAttackerAtRange(5);

        ActionResult r = game.launchDroneGroundAttack(kzinti, base, rack());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("P2.713"));
        assertTrue(r.getMessage(), r.getMessage().contains("5 hexes away"));
    }

    // ---------------------------------------------------------------- fire control

    /**
     * The clause that runs against the grain. An ordinary ballistic shot is fine on passive
     * fire control (D19.224); a bombardment is not, "as target tracking is inadequate".
     */
    @Test
    public void passiveFireControlCannotBombard() {
        kzinti.setActiveFireControl(false);

        ActionResult r = game.launchDroneGroundAttack(kzinti, base, rack());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("passive fire control"));
        assertTrue(r.getMessage(), r.getMessage().contains("P2.713"));
    }

    /** And the same ship, on the same passive fire control, may still fire ballistically at a HEX. */
    @Test
    public void passiveFireControlMayStillFireAtAPlainHex() {
        kzinti.setActiveFireControl(false);

        assertTrue("D19.224 — ballistic targeting is open to a ship on passive FC",
                game.launchDroneBallistic(kzinti, new Location(10, 10), rack()).isSuccess());
    }

    // ---------------------------------------------------------------- every weapon, not just drones

    /**
     * P2.713 says "seeking weapons", not "drones". A plasma torpedo bombards exactly as a drone
     * does, and FP4.23 is no obstacle — there is no target to acquire, only a hex to reach and a
     * base to detonate against.
     */
    @Test
    public void aPlasmaTorpedoCanBombardAGroundBase() {
        Ship romulan = new Ship();
        romulan.init(com.sfb.samples.RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setLocation(kzinti.getLocation());
        romulan.setFacing(13);                      // pointing down the column, at the planet
        romulan.setActiveFireControl(true);
        game.getShips().add(romulan);

        com.sfb.weapons.PlasmaLauncher tube = romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.PlasmaLauncher)
                .map(w -> (com.sfb.weapons.PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));

        ActionResult r = game.launchPlasmaGroundAttack(romulan, base, tube, false, 0);

        assertTrue(r.getMessage(), r.isSuccess());
        com.sfb.objects.PlasmaTorpedo torp = (com.sfb.objects.PlasmaTorpedo)
                game.getSeekers().get(game.getSeekers().size() - 1);
        assertTrue(torp.isBallistic());
        assertTrue("aimed at the base (P2.713)", torp.getBallisticCourse().isGroundAttack());
        assertNull("and with no target to acquire (FP4.23)", torp.getTarget());
    }

    /** The four-hex limit belongs to the RULE, not to the drone rack that first enforced it. */
    @Test
    public void thePlasmaIsBoundByTheSameFourHexes() {
        Ship romulan = new Ship();
        romulan.init(com.sfb.samples.RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setFacing(13);
        romulan.setActiveFireControl(true);
        Location hex = base.getLocation();
        romulan.setLocation(new Location(hex.getX(), hex.getY() - 6));
        game.getShips().add(romulan);

        com.sfb.weapons.PlasmaLauncher tube = romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.PlasmaLauncher)
                .map(w -> (com.sfb.weapons.PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));

        ActionResult r = game.launchPlasmaGroundAttack(romulan, base, tube, false, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("P2.713"));
    }

    /** A suicide shuttle is a seeking weapon too, and this is the one time it does explode. */
    @Test
    public void aSuicideShuttleCanBombardAGroundBase() {
        com.sfb.systemgroups.ShuttleBay bay = kzinti.getShuttles().getBays().get(0);
        com.sfb.objects.shuttles.SuicideShuttle ss =
                new com.sfb.objects.shuttles.SuicideShuttle(
                        new com.sfb.objects.shuttles.AdminShuttle());
        ss.setName("SH-1");
        bay.replaceShuttle(bay.getInventory().get(0), ss);
        ss.arm(3);
        ss.arm(3);
        ss.arm(3);

        ActionResult r = game.launchSuicideShuttleGroundAttack(kzinti, bay, ss, base, 13, 6);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue(ss.isBallistic());
        assertTrue("it will detonate on the base (P2.713)",
                ss.getBallisticCourse().isGroundAttack());
    }

    /** And the passive-fire-control bar is the rule's, so it binds a plasma the same way. */
    @Test
    public void passiveFireControlBarsEveryWeaponNotJustDrones() {
        Ship romulan = new Ship();
        romulan.init(com.sfb.samples.RomulanShips.getRomKr());
        romulan.setName("IRW Gauntlet");
        romulan.setLocation(kzinti.getLocation());
        romulan.setFacing(13);
        romulan.setActiveFireControl(false);
        game.getShips().add(romulan);

        com.sfb.weapons.PlasmaLauncher tube = romulan.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof com.sfb.weapons.PlasmaLauncher)
                .map(w -> (com.sfb.weapons.PlasmaLauncher) w)
                .findFirst().orElseThrow(() -> new AssertionError("the KR should carry plasma"));

        ActionResult r = game.launchPlasmaGroundAttack(romulan, base, tube, false, 0);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("passive fire control"));
    }

    // ---------------------------------------------------------------- what counts as a ground base

    @Test
    public void anOrdinaryShipIsNotAGroundTarget() {
        Ship cruiser = new Ship();
        cruiser.init(KzintiShips.getKzinBC());
        cruiser.setName("Not A Base");
        cruiser.setLocation(new Location(kzinti.getLocation().getX(),
                kzinti.getLocation().getY() - 1));
        game.getShips().add(cruiser);

        ActionResult r = game.launchDroneGroundAttack(kzinti, cruiser, rack());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("not a ground base"));
    }

    /** F4.412 is the general form of the same thing: ballistic weapons never hit ships in space. */
    @Test
    public void theRefusalNamesTheRule() {
        Ship cruiser = new Ship();
        cruiser.init(KzintiShips.getKzinBC());
        cruiser.setName("Not A Base");
        cruiser.setLocation(new Location(10, 10));
        game.getShips().add(cruiser);

        assertTrue(game.launchDroneGroundAttack(kzinti, cruiser, rack())
                .getMessage().contains("P2.713"));
    }
}
