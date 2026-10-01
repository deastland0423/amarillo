package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.AegisLevel;
import com.sfb.properties.Location;

/**
 * Aegis fire control (D13.0), slice one: what a ship HAS, what it is RUNNING, and what it may
 * shoot at. The four-pulse firing structure is separate work.
 * <p>
 * Two things the rules are emphatic about and this pins:
 * <ul>
 * <li><b>Aegis belongs to the ship, not to being an escort.</b> D13.0: "This system was almost
 *     never used on ships other than carrier escorts (the Klingon D5 being an exception)."
 *     Deriving it from {@code isEscort} would be right for most hulls and wrong for the ones
 *     worth naming.</li>
 * <li><b>Fitted is not running.</b> D13.16 says aegis "cannot be destroyed", so what the hull
 *     has is permanent; but D13.52 lets a ship switch it off, D13.525 lets a full system pose
 *     as limited, and D13.523 makes it useless for four impulses after being switched up.</li>
 * </ul>
 */
public class AegisFireControlTest {

    private Game game;
    private Ship escort;

    @Before
    public void setUp() throws Exception {
        com.sfb.objects.ShuttleCatalog.loadDefault("../data");
        game = new Game();

        escort = new Ship();
        escort.init(com.sfb.samples.KzintiShips.getKzinBC());
        escort.setName("KHS Guardian");
        escort.setLocation(new Location(10, 10));
        escort.setFacing(1);
        escort.setActiveFireControl(true);
        escort.setAegisFitted(AegisLevel.FULL);
        escort.setAegisMode(AegisLevel.FULL, 0);
        game.getShips().add(escort);
    }

    /** A drone of this engine is size class 7; put it {@code hexes} straight ahead. */
    private Drone droneAt(int hexes) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName("Drone-" + hexes);
        d.setLocation(new Location(10, 10 - hexes));
        game.getSeekers().add(d);
        escort.addLockOn(d);
        return d;
    }

    // ---------------------------------------------------------------- the levels

    @Test
    public void theLevelsCarryTheirOwnFiringCounts() {
        assertEquals("D13.14: four firings", 4, AegisLevel.FULL.firings());
        assertEquals("D13.411: two, not four", 2, AegisLevel.LIMITED.firings());
        assertEquals(0, AegisLevel.NONE.firings());

        // The first firing is the ordinary one every ship gets, alongside all non-aegis fire.
        assertEquals("three extra", 3, AegisLevel.FULL.extraFirings());
        assertEquals("one extra", 1, AegisLevel.LIMITED.extraFirings());
        assertEquals(0, AegisLevel.NONE.extraFirings());
    }

    /** D13.35 against D13.412: identifying seekers is the full system's alone. */
    @Test
    public void onlyAFullSystemIdentifiesSeekers() {
        assertTrue(AegisLevel.FULL.canIdentifySeekers());
        assertFalse(AegisLevel.LIMITED.canIdentifySeekers());
        assertFalse(AegisLevel.NONE.canIdentifySeekers());
    }

    /** An unknown or missing value is NONE, so a typo in a ship file cannot grant aegis. */
    @Test
    public void anUnreadableValueIsNoAegis() {
        assertEquals(AegisLevel.NONE, AegisLevel.from(null));
        assertEquals(AegisLevel.NONE, AegisLevel.from("sort-of"));
        assertEquals(AegisLevel.FULL, AegisLevel.from("full"));
        assertEquals(AegisLevel.LIMITED, AegisLevel.from(" Limited "));
    }

    /** The default, and the thing that must never be inferred from being an escort. */
    @Test
    public void anOrdinaryShipHasNoneAndEscortStatusDoesNotGrantIt() {
        Ship plain = new Ship();
        plain.init(com.sfb.samples.KzintiShips.getKzinBC());

        assertEquals(AegisLevel.NONE, plain.getAegisFitted());
        assertEquals(0, plain.aegisFirings(0));
        assertFalse("a ship with no aegis cannot be operational", plain.isAegisOperational(0));
    }

    // ---------------------------------------------------------------- fitted vs running

    /** D13.525: a full system may pose as limited. The reverse is not on offer. */
    @Test
    public void aFullSystemMayRunAsLimitedButNotTheOtherWayRound() {
        assertTrue("full posing as limited is expressly allowed",
                escort.setAegisMode(AegisLevel.LIMITED, 10));
        assertEquals(AegisLevel.LIMITED, escort.getAegisMode());
        assertEquals("and what it HAS is untouched", AegisLevel.FULL, escort.getAegisFitted());

        Ship lesser = new Ship();
        lesser.init(com.sfb.samples.KzintiShips.getKzinBC());
        lesser.setAegisFitted(AegisLevel.LIMITED);

        assertFalse("limited cannot pretend to be full", lesser.setAegisMode(AegisLevel.FULL, 10));
        assertEquals(AegisLevel.LIMITED, lesser.getAegisMode());
    }

    /**
     * D13.523 against D13.521, and the asymmetry is the point: climbing costs four impulses,
     * dropping is immediate. That is what makes D13.525's deception worth anything — a ship can
     * look weaker for free but cannot instantly look strong again.
     */
    @Test
    public void switchingUpCostsFourImpulsesAndSwitchingDownIsFree() {
        escort.setAegisMode(AegisLevel.NONE, 20);
        assertFalse("off is off at once", escort.isAegisOperational(20));

        escort.setAegisMode(AegisLevel.FULL, 20);
        assertFalse("impulse 20: detectable, but not working yet", escort.isAegisOperational(20));
        assertFalse("impulse 23: still inside the four", escort.isAegisOperational(23));
        assertTrue("impulse 24: working", escort.isAegisOperational(24));

        // Dropping a level is immediate — no fresh wait.
        assertTrue(escort.setAegisMode(AegisLevel.LIMITED, 25));
        assertTrue("no new warm-up for going down", escort.isAegisOperational(25));
    }

    /** A ship that began the scenario with aegis on has no wait to serve. */
    @Test
    public void aegisOnFromTheStartNeedsNoWarmUp() {
        Ship ready = new Ship();
        ready.init(com.sfb.samples.KzintiShips.getKzinBC());
        ready.setActiveFireControl(true);
        ready.setAegisFitted(AegisLevel.FULL);

        assertEquals("init leaves it running at what is fitted",
                AegisLevel.FULL, ready.getAegisMode());
        assertTrue("and working from impulse one", ready.isAegisOperational(1));
    }

    /** D13.524: "Aegis can only be active if the fire control system is active." */
    @Test
    public void withoutActiveFireControlAegisDoesNothing() {
        assertTrue(escort.isAegisOperational(10));

        escort.setActiveFireControl(false);

        assertFalse(escort.isAegisOperational(10));
        assertEquals(0, escort.aegisFirings(10));
        assertEquals("but it is still FITTED — D13.16, it cannot be destroyed",
                AegisLevel.FULL, escort.getAegisFitted());
    }

    // ---------------------------------------------------------------- what it may shoot

    /** D13.21: size class 6 and smaller. A drone is 7, a fighter 6, a cruiser 3. */
    @Test
    public void onlySmallTargetsAreEligible() {
        Drone drone = droneAt(2);
        assertTrue("a drone is size class 7", escort.canAegisEngage(drone, 10));

        Fighter fighter = CataloguedFighter.of("stinger1");
        fighter.setName("Stinger-1");
        fighter.setLocation(new Location(10, 8));
        game.getActiveShuttles().add(fighter);
        escort.addLockOn(fighter);
        assertTrue("a fighter is size class 6", escort.canAegisEngage(fighter, 10));

        Ship cruiser = new Ship();
        cruiser.init(com.sfb.samples.KlingonShips.getD7());
        cruiser.setName("IKV Saber");
        cruiser.setLocation(new Location(10, 8));
        game.getShips().add(cruiser);
        escort.addLockOn(cruiser);
        assertFalse("aegis is defensive — no shooting at ships",
                escort.canAegisEngage(cruiser, 10));
    }

    /** D13.21: "within six hexes of the ship". */
    @Test
    public void sixHexesIsTheReach() {
        assertTrue(escort.canAegisEngage(droneAt(6), 10));
        assertFalse(escort.canAegisEngage(droneAt(7), 10));
    }

    /** D13.23: a lock-on to the target is required, not merely active fire control. */
    @Test
    public void aLockOnToTheTargetIsRequired() {
        Drone drone = droneAt(3);
        assertTrue(escort.canAegisEngage(drone, 10));

        escort.removeLockOn(drone);

        assertFalse("D13.23 wants the lock-on as well", escort.canAegisEngage(drone, 10));
    }

    /**
     * D13.21 is explicit that this is not self-defence: "it is not necessary for such a target
     * to be approaching the aegis-equipped ship". A drone heading elsewhere is fair game.
     */
    @Test
    public void aTargetNeedNotBeComingForYou() {
        Ship friend = new Ship();
        friend.init(com.sfb.samples.KzintiShips.getKzinBC());
        friend.setName("KHS Bystander");
        friend.setLocation(new Location(10, 6));
        game.getShips().add(friend);

        Drone drone = droneAt(2);
        drone.setTarget(friend);

        assertTrue("aimed at someone else, still engageable",
                escort.canAegisEngage(drone, 10));
    }

    /** Everything above is moot while the system is not working. */
    @Test
    public void nothingIsEngageableWhileAegisIsOff() {
        Drone drone = droneAt(2);
        assertTrue(escort.canAegisEngage(drone, 10));

        escort.setAegisMode(AegisLevel.NONE, 10);

        assertFalse(escort.canAegisEngage(drone, 10));
    }
}
