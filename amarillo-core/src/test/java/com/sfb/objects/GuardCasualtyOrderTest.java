package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Random;

import org.junit.Test;

import com.sfb.Game;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.Weapon;

/**
 * (D7.21) "Casualties under (D7.832) are scored first, before casualties resulting from general
 * internal damage."
 *
 * <p>(D7.832) is the guard whose POST was shot out from under it. Which post that is cannot be
 * known until every point of the volley has been placed, so honouring this clause means the
 * every-tenth-point casualties cannot be taken point by point as the damage lands: they wait
 * until the end, and they are taken after the guard rolls. That is what
 * {@code Ship.applyPendingInternalCasualties()} is for, and
 * {@code DamageResolver.resolveInternalDamage()} is where the two run in order.
 *
 * <h2>Why the order is not bookkeeping</h2>
 * It decides whether (D7.21)'s own floor holds. "The last two boarding parties on the ship cannot
 * be killed by this method" — but a guard killed under (D7.832) is NOT killed by this method, so
 * it can take the ship below two, and the general casualty that was already taken in the wrong
 * order can never be given back. Three parties aboard, one of them a guard whose post is gone:
 * score the general casualty first and the ship ends with ONE party, which (D7.21) forbids.
 */
public class GuardCasualtyOrderTest {

    /** Enough internals for exactly one casualty event: (50 / 10) - 4 ignored (D7.21). */
    private static final int POINTS = 50;

    /**
     * The real resolver sequence, through the real pipeline, on a hull with no barracks.
     *
     * <p>The guarded weapon is destroyed BEFORE the volley rather than by it, because which box
     * a DAC roll takes is not something a test can choose. It makes no difference to what is
     * being measured: {@code reconcileAfterDamage} rolls for any post whose system is already
     * non-functional when resolution completes, so a weapon destroyed a moment earlier and one
     * destroyed by this volley reach it in the same state.
     */
    @Test
    public void aGuardKilledUnderD7_832CannotTakeTheShipBelowD7_21sFloor() {
        Game game = new Game();
        Ship attacker = ship("USS Attacker", 10, 10);
        Ship target = ship("USS Target", 12, 10);
        game.getShips().add(attacker);
        game.getShips().add(target);
        reachDirectFire(game, attacker, target);

        for (int shield = 1; shield <= 6; shield++)
            target.getShields().setShieldValue(shield, 0);

        // Three parties aboard: two on the roster and one at a post (D7.834 takes it off the
        // roster, which is why boardingPartiesAboard() and not the roster is the floor's yardstick).
        target.getCrew().setAvailableBoardingParties(3);
        Weapon guarded = target.getWeapons().fetchAllWeapons().get(0);
        assertNull("fixture: the guard must post",
                target.getGuardPosts().assign(new SystemTarget(guarded),
                        BoardingPartyQuality.NORMAL));
        assertEquals("fixture: two on the roster", 2,
                target.getCrew().getAvailableBoardingParties());
        assertEquals("fixture: three aboard", 3, target.boardingPartiesAboard());

        // Its post is gone, so the reconcile owes it a (D7.832) roll; force the 50% to kill.
        guarded.damage();
        target.getGuardPosts().random = alwaysZero();

        resolveVolley(game, target, POINTS);

        assertTrue("broken fixture, not a pass: the volley must land enough internals for"
                + " D7.21's fifth casualty event, got " + target.internalDamagePointsForTest(),
                target.internalDamagePointsForTest() >= POINTS);
        assertEquals("the guard died with its post (D7.832)", 0,
                target.getGuardPosts().totalPosted());
        assertEquals("that left two aboard, and D7.21 may not touch the last two: scoring the"
                + " general casualty FIRST would have spent one of them and left ONE party on"
                + " the ship", 2, target.getCrew().getAvailableBoardingParties());
        assertEquals(2, target.boardingPartiesAboard());
    }

    /**
     * The control. Same volley, same roster, no destroyed post — so nothing is owed under
     * (D7.832), the three aboard stay three, and the general casualty DOES land. Without this
     * the test above passes on a ship that simply never took a casualty.
     */
    @Test
    public void withNoGuardCasualtyToScoreFirstTheGeneralOneLands() {
        Game game = new Game();
        Ship attacker = ship("USS Attacker", 10, 10);
        Ship target = ship("USS Target", 12, 10);
        game.getShips().add(attacker);
        game.getShips().add(target);
        reachDirectFire(game, attacker, target);

        for (int shield = 1; shield <= 6; shield++)
            target.getShields().setShieldValue(shield, 0);
        target.getCrew().setAvailableBoardingParties(3);

        resolveVolley(game, target, POINTS);

        assertEquals("three aboard, floor of two, so exactly one casualty", 2,
                target.boardingPartiesAboard());
        assertEquals(2, target.getCrew().getAvailableBoardingParties());
    }

    /**
     * The non-interactive wrapper {@code applyInternalDamage(int)} resolves a whole volley on its
     * own, auto-answering DAC choices, so it owes the casualties itself: it has no Game to walk
     * and so no guard reconcile that could come first.
     *
     * <p>Mutation found that line untested - deleting it left every class green - which is the
     * easy way to ship a wrapper that silently stops killing anybody.
     *
     * <p>Counts are DERIVED from the odometer rather than stated, because armor eats the front of
     * the volley (D3.21) and the hull data decides how much.
     */
    @Test
    public void theBareWrapperTakesTheCasualtiesItself() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("USS Bare");
        target.getCrew().setAvailableBoardingParties(6);
        int crewBefore = target.getCrew().getAvailableCrewUnits();

        target.applyInternalDamage(80);

        int internals = target.internalDamagePointsForTest();
        assertTrue("broken fixture, not a pass: " + internals + " internals is short of D7.21's"
                + " fifth casualty event, so this would assert nothing", internals >= POINTS);
        int events = internals / 10;
        assertEquals("G9.21 kills a crew unit every tenth point",
                crewBefore - Math.min(events, crewBefore - 1),
                target.getCrew().getAvailableCrewUnits());
        assertEquals("D7.21 takes a party on every event past the fourth, down to a floor of two",
                6 - Math.min(Math.max(0, events - 4), 6 - 2),
                target.getCrew().getAvailableBoardingParties());
    }

    // ---------------------------------------------------------------------------------- helpers

    private static Ship ship(String name, int col, int row) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(col, row));
        s.setActiveFireControl(true);
        return s;
    }

    /** A die that always reads zero, which is the killing half of (D7.832)'s 50%. */
    private static Random alwaysZero() {
        return new Random() {
            private static final long serialVersionUID = 1L;

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    private static void reachDirectFire(Game game, Ship... ships) {
        game.startTurn();
        for (Ship ship : ships) {
            Energy e = new Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setFireControl(ship.getFireControlCost());
            game.submitAllocation(ship, e);
        }
        for (int i = 0; i < 40 && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; i++)
            game.advancePhase();
        if (game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE)
            fail("broken fixture: never reached DIRECT_FIRE, stuck in "
                    + game.getCurrentPhase());
    }

    /**
     * Queue a fixed volley on a bare shield and drive resolution to the end, answering every DAC
     * choice. {@code markShieldDamage} is the resolver's own door and takes an exact number,
     * which weapons fire does not — the point of this test is the sequence, not the dice.
     */
    private static void resolveVolley(Game game, Ship target, int points) {
        game.markShieldDamage(target, 1, points);
        for (int i = 0; i < 400; i++) {
            if (!game.getPendingDacChoices().isEmpty()) {
                Game.PendingDacChoice pending = game.getPendingDacChoices().get(0);
                String chosen = pending.options == null || pending.options.isEmpty()
                        ? null : pending.options.get(0);
                game.submitDacChoice(chosen);
                continue;
            }
            if (target.internalDamagePointsForTest() >= points)
                return;
            game.advancePhase();
        }
        fail("broken fixture: resolution never finished, "
                + target.internalDamagePointsForTest() + " of " + points + " internals landed");
    }
}
