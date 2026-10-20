package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.RefitResolver;
import com.sfb.properties.Location;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * FD4.4: an Orion may USE only three racks in a turn, however many it carries.
 *
 * <h2>The rule, and the distinction that is easy to lose</h2>
 * "None of these ships, however, can launch drones from more than three of their drone racks
 * during a turn (at the appropriate firing rate for the type of rack). ... The limit of three
 * drone racks or plasma-D racks firing per turn (each at up to their maximum rate) includes both
 * types (i.e., three total, not three of each). The launch restriction does not include
 * anti-drones or seeking shuttles (or any other weapon)."
 *
 * <p>So the cap is on <b>racks used</b>, not drones launched. A rack that fires twice spends one
 * of the three, which is exactly why Orions favour type-C racks: three of them lawfully put six
 * drones in the air. The SAL's Y175 refit converts its racks A→C for that reason — it buys RATE,
 * where this rule governs PERMISSION.
 *
 * <h2>Why it was worth building</h2>
 * Nothing capped rack firing before, so every Orion fired every rack every turn and the engine
 * behaved as though all of them carried OAKDISC — which also made the refit's launch half
 * invisible. The Orion Salvage Cruiser is the prototype: four racks, one over the cap.
 */
public class OrionRackFiringLimitTest {

    private Game game;
    private Ship sal;
    private Ship target;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();
        sal = salFrom(ShipLibrary.get("Orion", "SAL"));
        target = enemy();
        game.getShips().add(sal);
        game.getShips().add(target);
        while (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            game.advancePhase();
        sal.setActiveFireControl(true);
        sal.addLockOn(target);
    }

    private static Ship salFrom(ShipSpec spec) {
        assertNotNull("fixture: the Orion Salvage Cruiser should be in the library", spec);
        Ship ship = ShipLibrary.createShip(spec);
        ship.setName("Finders Keepers");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        return ship;
    }

    private static Ship enemy() {
        Ship ship = new Ship();
        ship.init(com.sfb.samples.FederationShips.getFedCa());
        ship.setName("USS Target");
        ship.setLocation(new Location(10, 12));
        ship.setFacing(1);
        return ship;
    }

    private List<DroneRack> racks(Ship ship) {
        List<DroneRack> out = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                out.add(rack);
        return out;
    }

    // ---------------------------------------------------------------- the fixture

    @Test
    public void theSalCarriesFourRacksAndDeclaresACapOfThree() {
        assertEquals("fixture: a SAL prints four drone racks", 4, racks(sal).size());
        assertEquals("fixture: and declares FD4.4's cap", Integer.valueOf(3),
                sal.getRackFiringLimit());
    }

    /** Everything outside the Orions declares nothing, and so is unlimited. */
    @Test
    public void anOrdinaryWarshipHasNoSuchCap() {
        Ship fed = enemy();

        assertNull("no other empire declares a rack limit", fed.getRackFiringLimit());
        for (Weapon w : fed.getWeapons().fetchAllWeapons())
            assertTrue("so every rack may always fire", fed.mayFireAnotherRack(w));
    }

    // ---------------------------------------------------------------- the cap

    /**
     * THE TEST THIS EXISTS FOR: the fourth rack is refused, and by name.
     *
     * The first three must SUCCEED, or a refusal on the fourth would prove nothing — it could
     * equally be an empty rack or a missing lock-on.
     */
    @Test
    public void aFourthRackMayNotLaunch() {
        List<DroneRack> all = racks(sal);
        for (int i = 0; i < 3; i++) {
            Game.ActionResult fired = game.launchDrone(sal, target, all.get(i));
            assertTrue("fixture: rack " + (i + 1) + " must get its drone away, or the refusal"
                    + " below proves nothing — " + fired.getMessage(), fired.isSuccess());
        }

        Game.ActionResult fourth = game.launchDrone(sal, target, all.get(3));

        assertFalse("FD4.4 allows three racks a turn", fourth.isSuccess());
        assertTrue("and the refusal must name the rule: " + fourth.getMessage(),
                fourth.getMessage().contains("FD4.4"));
    }

    /**
     * A rack that has ALREADY fired may fire again — it has spent its permission.
     *
     * This is the half of the rule that makes type-C racks worth buying, so it is pinned
     * separately from the refusal above.
     */
    @Test
    public void aRackThatHasFiredMayFireAgainWithinItsOwnRate() {
        List<DroneRack> all = racks(sal);
        for (int i = 0; i < 3; i++)
            assertTrue("fixture", game.launchDrone(sal, target, all.get(i)).isSuccess());

        assertTrue("a rack already counted is not blocked by FD4.4",
                sal.mayFireAnotherRack(all.get(0)));
        assertFalse("while an untouched fourth rack still is",
                sal.mayFireAnotherRack(all.get(3)));
    }

    /** Below the cap nothing is refused, so the rule costs a two-rack ship nothing. */
    @Test
    public void underTheCapEveryRackIsFree() {
        List<DroneRack> all = racks(sal);

        assertTrue(sal.mayFireAnotherRack(all.get(0)));
        assertTrue("fixture", game.launchDrone(sal, target, all.get(0)).isSuccess());
        assertTrue("one used, two still available", sal.mayFireAnotherRack(all.get(1)));
        assertTrue("fixture", game.launchDrone(sal, target, all.get(1)).isSuccess());
        assertTrue("two used, one still available", sal.mayFireAnotherRack(all.get(2)));
    }

    // ---------------------------------------------------------------- OAKDISC

    /**
     * FD4.5: OAKDISC has "sufficient tracking capacity to fire every rack every turn", so the
     * refit CLEARS the limit — which the refit model supports because setFields treats a null as
     * removing the field.
     */
    @Test
    public void oakdiscLiftsTheCapEntirely() {
        Ship fitted = salFrom(RefitResolver.apply(ShipLibrary.get("Orion", "SAL"), List.of("O")));

        assertNull("OAKDISC removes the limit outright", fitted.getRackFiringLimit());
        for (DroneRack rack : racks(fitted))
            assertTrue("so every rack may fire", fitted.mayFireAnotherRack(rack));
    }

    /** And with it fitted, the fourth rack actually gets its drone away. */
    @Test
    public void withOakdiscTheFourthRackLaunches() {
        Game fresh = new Game();
        Ship fitted = salFrom(RefitResolver.apply(ShipLibrary.get("Orion", "SAL"), List.of("O")));
        Ship enemy = enemy();
        fresh.getShips().add(fitted);
        fresh.getShips().add(enemy);
        while (fresh.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            fresh.advancePhase();
        fitted.setActiveFireControl(true);
        fitted.addLockOn(enemy);

        List<DroneRack> all = racks(fitted);
        for (int i = 0; i < 3; i++)
            assertTrue("fixture", fresh.launchDrone(fitted, enemy, all.get(i)).isSuccess());

        Game.ActionResult fourth = fresh.launchDrone(fitted, enemy, all.get(3));

        assertTrue("OAKDISC fires every rack (FD4.5): " + fourth.getMessage(),
                fourth.isSuccess());
    }
}
