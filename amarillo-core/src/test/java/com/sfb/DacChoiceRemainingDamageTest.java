package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.Weapon;

/**
 * A volley that pauses for a DAC choice must not lose the rest of its damage.
 * <p>
 * C3.14's damage allocation runs point by point. When a point lands on a system the defender
 * must choose between (a shuttle box, a phaser, a warp engine), resolution STOPS mid-volley and
 * the undealt points are carried on the pending choice as {@code remainingBleed}. Whoever
 * applies the choice has to hand them back, or they are gone: the volley quietly scores fewer
 * points than it rolled, and the defender is rewarded for owning a system that asks a question.
 * <p>
 * The generic branch of {@code DamageResolver.applyDacChoice} does hand them back. The shuttle
 * branch did not, which is what this class was written to prove — found while adding the
 * balcony branch next to it (J1.531), which needed the same hand-back.
 * <p>
 * The DAC roll is random, so these tests retry with a fresh game until the volley happens to
 * stop on the choice they are about. That is the price of testing the real path: no test-only
 * hook into the damage queue, so the damage comes from an actual fired volley.
 */
public class DacChoiceRemainingDamageTest {

    /**
     * A game with one attacker and one shieldless target, paused in DIRECT_FIRE with charged
     * phaser capacitors.
     * <p>
     * The energy allocation is not optional decoration: without it the phasers fire for zero
     * and the volley scores no internals at all, so nothing ever pauses and a test like this
     * passes while exercising nothing. The first version of this class did exactly that.
     */
    private static Game freshDuel() {
        Game game = new Game();

        Ship attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Attacker");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setActiveFireControl(true);

        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(10, 11));
        target.setFacing(1);
        target.setActiveFireControl(true);
        for (int shield = 1; shield <= 6; shield++)
            target.getShields().setShieldValue(shield, 0);

        game.getShips().add(attacker);
        game.getShips().add(target);

        game.startTurn();
        for (Ship ship : new Ship[] { attacker, target }) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(ship.getLifeSupportCost());
            e.setFireControl(ship.getFireControlCost());
            e.setPhaserCapacitor(6);
            game.submitAllocation(ship, e);
        }
        for (int i = 0; i < 40 && game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE; i++)
            game.advancePhase();
        assertEquals("fixture: ready to fire", Game.ImpulsePhase.DIRECT_FIRE,
                game.getCurrentPhase());
        return game;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("fixture: no ship called " + name);
    }

    /** Every functional phaser the attacker has, fired at once to make a fat volley. */
    private static List<Weapon> allPhasers(Ship attacker) {
        List<Weapon> fired = new ArrayList<>();
        for (Weapon w : attacker.getWeapons().getPhaserList())
            if (w.isFunctional())
                fired.add(w);
        return fired;
    }

    /**
     * Fire one big volley and run the phase machine until it either settles or stops on a DAC
     * choice.
     *
     * @return the game, paused wherever it got to
     */
    private static Game volleyUntilPaused() {
        Game game = freshDuel();
        Ship attacker = shipNamed(game, "Attacker");
        Ship target = shipNamed(game, "Target");

        game.fireWeapons(attacker, target, allPhasers(attacker), 1, 1, 1);
        for (int i = 0; i < 12 && game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE; i++)
            game.advancePhase();
        return game;
    }

    /**
     * The regression this class exists for. With points still undealt, submitting a SHUTTLE
     * choice has to carry on resolving them — before the fix the log held the shuttle line and
     * nothing else, however many points were left.
     */
    @Test
    public void aShuttleChoiceDoesNotSwallowTheRestOfTheVolley() {
        for (int attempt = 0; attempt < 400; attempt++) {
            Game game = volleyUntilPaused();
            if (game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE)
                continue;
            Game.PendingDacChoice pending = game.getPendingDacChoices().get(0);
            if (!"shuttle".equals(pending.dacType) || pending.remainingBleed <= 0)
                continue;

            Game.ActionResult r = game.submitDacChoice(pending.options.get(0));

            assertTrue(r.getMessage(), r.isSuccess());
            assertTrue("the shuttle box was destroyed",
                    r.getMessage().contains("DESTROYED"));
            assertTrue(pending.remainingBleed + " point(s) were still owed and must still be"
                    + " scored, but the log stops at the shuttle: " + r.getMessage(),
                    r.getMessage().contains("internal ["));
            return;
        }
        // Not a failure: 400 volleys never stopped on a shuttle box with points to spare.
        // Reported rather than silently passing, so a change that makes it unreachable shows.
        System.out.println("DacChoiceRemainingDamageTest: no shuttle DAC pause with"
                + " remaining bleed in 400 volleys — assertion not exercised");
    }

    /**
     * The same guarantee for the balcony branch (J1.531), which was written with the
     * hand-back from the start. Kept beside the shuttle case so the two cannot diverge again.
     */
    @Test
    public void aBalconyChoiceDoesNotSwallowTheRestOfTheVolleyEither() {
        for (int attempt = 0; attempt < 400; attempt++) {
            Game game = freshDuel();
            Ship attacker = shipNamed(game, "Attacker");
            Ship target = shipNamed(game, "Target");

            // A CA has no balcony; give this one two parked craft so a rear hull point has to
            // ask which. The bay list is what the rule reads, so a fabricated balcony is a
            // fair stand-in for a CVA here and keeps the volley small.
            com.sfb.systemgroups.ShuttleBay bay = target.getShuttles().getBays().get(0);
            bay.setBalconyPositions(2);
            for (String name : new String[] { "Alpha", "Bravo" }) {
                com.sfb.objects.shuttles.AdminShuttle craft =
                        new com.sfb.objects.shuttles.AdminShuttle();
                craft.setName(name);
                assertTrue(bay.park(craft));
            }

            game.fireWeapons(attacker, target, allPhasers(attacker), 1, 1, 1);
            for (int i = 0; i < 12 && game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE; i++)
                game.advancePhase();
            if (game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE)
                continue;
            Game.PendingDacChoice pending = game.getPendingDacChoices().get(0);
            if (!"ahull".equals(pending.dacType) || pending.remainingBleed <= 0)
                continue;

            Game.ActionResult r = game.submitDacChoice(pending.options.get(0));

            assertTrue(r.getMessage(), r.isSuccess());
            assertTrue("the parked craft was destroyed (J1.531): " + r.getMessage(),
                    r.getMessage().contains("DESTROYED on the balcony"));
            assertTrue(pending.remainingBleed + " point(s) were still owed: " + r.getMessage(),
                    r.getMessage().contains("internal ["));
            assertFalse("the craft the owner picked is the one that died",
                    target.parkedCraft().stream().anyMatch(c -> c.getName().equals("Alpha")));
            // Bravo may well die too, and should: the volley carries on, and every further
            // rear hull point in it takes another parked craft (forced, so no second prompt).
            return;
        }
        System.out.println("DacChoiceRemainingDamageTest: no balcony DAC pause with"
                + " remaining bleed in 400 volleys — assertion not exercised");
    }
}
