package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Aas;
import com.sfb.properties.Location;

/**
 * A fighter's own electronic warfare in combat (J4.47, D6.3142).
 * <p>
 * "All fighters have two points of built-in ECM (D6.394) and two points of built-in ECCM
 * (D6.393)." Built-in is D6.3142, and for the to-hit arithmetic it behaves exactly as a
 * ship's generated points do — D6.3146 treats the two categories alike. What keeps them
 * apart is elsewhere: built-in cannot be lent (J4.965), and survives crippling when pods
 * do not (J1.3322).
 * <p>
 * None of it counted. {@code Game.ewAgainst} knew about the two ECM points, but the fire
 * resolution assembled its own Ship-typed sum and never asked — so a fighter was shot at
 * as though it had no EW, and shot as though it had no ECCM.
 */
public class FighterEwTest {

    private Game game;
    private Ship warship;
    private Aas fighter;

    @Before
    public void setUp() {
        game = new Game();
        Player red = new Player();
        red.setTeamName("Federation");
        Player blue = new Player();
        blue.setTeamName("Kzinti");

        warship = new Ship();
        warship.init(com.sfb.samples.FederationShips.getFedCa());
        warship.setName("USS Attacker");
        warship.setLocation(new Location(10, 10));
        warship.setFacing(1);
        warship.setOwner(red);
        warship.setActiveFireControl(true);
        game.getShips().add(warship);

        fighter = new Aas();
        fighter.setName("AAS-1");
        fighter.setLocation(new Location(10, 8));
        fighter.setFacing(13);
        fighter.setOwner(blue);
        game.getActiveShuttles().add(fighter);

        game.startTurn();
    }

    @Test
    public void aFighterIsHarderToHitForItsTwoBuiltInPoints() {
        com.sfb.properties.EwBreakdown ew = game.ewAgainst(warship, fighter);

        assertEquals("J4.47, and it is BUILT-IN (D6.3142) not generated", 2, ew.builtIn());
        assertEquals(0, ew.generated());
        assertEquals("two points is +1 on the die, not +2", 1,
                game.fireEcmShift(warship, fighter));
    }

    /** Enough ECCM erases the shift entirely (D6.34 Step 4), fighter or not. */
    @Test
    public void eccmCancelsIt() {
        warship.allocateEw(0, 2, game.getAbsoluteImpulse());

        assertEquals(0, game.fireEcmShift(warship, fighter));
    }

    @Test
    public void aFighterBringsItsOwnEccmWhenItShoots() {
        assertEquals("J4.47's other two points", 2, game.eccmOf(fighter));
    }

    /**
     * A fighter has no Energy Allocation form (J1.1) and so no fire control to lose in the
     * way a ship does — J1.344 launches it with active fire control unless declared
     * otherwise, which is not modelled. So its ECCM is simply always there.
     */
    @Test
    public void aFightersEccmDoesNotDependOnAllocation() {
        assertEquals(2, game.eccmOf(fighter));
        assertEquals("a ship with nothing allocated brings nothing",
                0, game.eccmOf(warship));
    }

    /** An admin shuttle is not a fighter and J4.47 does not reach it. */
    @Test
    public void anAdminShuttleHasNoEwOfItsOwn() {
        com.sfb.objects.shuttles.AdminShuttle admin =
                new com.sfb.objects.shuttles.AdminShuttle();
        admin.setName("Admin-1");
        admin.setLocation(new Location(10, 8));
        admin.setOwner(fighter.getOwner());
        game.getActiveShuttles().add(admin);

        assertEquals(0, game.ewAgainst(warship, admin).total());
        assertEquals(0, game.eccmOf(admin));
    }
}
