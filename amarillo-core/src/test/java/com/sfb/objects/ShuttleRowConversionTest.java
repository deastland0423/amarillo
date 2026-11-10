package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.Game;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;
import com.sfb.weapons.Weapon;

/**
 * (M9.13, Annex #7E) The shuttle row of the DAMAGE CONVERSION CHART, which reaches a mine rack.
 *
 * <blockquote>Annex #7E: "Shuttle ....... Fighter, <b>Mine Rack</b>, Ships in Andromedan hangars
 * (G19.2), Shard."</blockquote>
 *
 * <blockquote>M9.13: "<b>Shuttle hits must be scored on shuttle or mine rack boxes if any of
 * either type exist.</b> See Annex #7E."</blockquote>
 *
 * <h2>The force here is stronger than on the cargo row</h2>
 * There is no opt-out. A cargo hit is the owner's option between the kinds; a shuttle hit "must"
 * go to a shuttle box or a rack whenever either exists, and the only decision is which. So a
 * minelayer with loaded racks soaks shuttle hits whether its owner likes it or not. That reads
 * like a defensive bonus and is nothing of the kind: the racks are destroyed either way, and the
 * mines with them.
 *
 * <h2>Why the rack does NOT come down the resolver's shuttle branch</h2>
 * Everything that branch does is about a bay: destroy the space, kill its deck crews, destroy the
 * occupant, and chain-react if the occupant was armed (D12.10). A rack has no occupant and no deck
 * crews, and M9.13 says in so many words that its mines "will not detonate". It also has an option
 * string the branch's {@code split(":")} would read as a bay address and throw on. So a rack pick
 * is routed to the generic branch, and the integration test at the bottom is what proves it,
 * because that throw is only reachable through the real resolver.
 */
public class ShuttleRowConversionTest {

    // ---------------------------------------------------------------- the options list

    /** Bay spaces first, then every undestroyed rack. */
    @Test
    public void theShuttleRowOffersBaySpacesThenRacks() {
        Ship ship = hullWithRacks(2);

        List<String> options = ship.dacChoiceOptionsForTest("shuttle");

        assertTrue("fixture: the CA has bay spaces", options.size() > 2);
        assertTrue("bay spaces come first", options.get(0).startsWith("bay:"));
        assertEquals("and the racks come last, so the auto-resolver still empties bays first",
                List.of("minerack:0", "minerack:1"),
                options.subList(options.size() - 2, options.size()));
    }

    /**
     * (M9.13) With every bay space gone the racks are the whole row, and the hit MUST land there.
     *
     * <p>Not "may". This is the clause that makes a rack absorb shuttle hits against its owner's
     * wishes, and the non-empty list is what stops the DAC advancing past it.
     */
    @Test
    public void withTheBaysGoneTheRacksAreTheWholeRow() {
        Ship ship = hullWithRacks(2);
        destroyEveryBaySpace(ship);

        assertEquals(List.of("minerack:0", "minerack:1"),
                ship.dacChoiceOptionsForTest("shuttle"));
        assertTrue("so the owner is still asked, not skipped",
                ship.requiresPlayerChoiceForTest("shuttle"));
    }

    /** And with neither kind left the row is empty, which is what lets the DAC advance. */
    @Test
    public void withNeitherKindLeftTheRowIsEmpty() {
        Ship ship = hullWithRacks(1);
        destroyEveryBaySpace(ship);
        ship.getMineRacks().get(0).destroy();

        assertTrue(ship.dacChoiceOptionsForTest("shuttle").isEmpty());
    }

    // ---------------------------------------------------------------- applying the pick

    /** (M9.13) A rack picked on the shuttle row loses its mines, and they do not go off. */
    @Test
    public void arackPickedOnTheShuttleRowLosesItsMinesWithoutDetonating() {
        Ship ship = hullWithRacks(2);
        ship.getMineRacks().get(1).loadLarge(4);

        String label = ship.applyDacChoiceHit("shuttle", "minerack:1", null);

        assertNotNull("the hit landed", label);
        assertTrue("and says what happened: " + label,
                label.contains("4 mine(s) destroyed") && label.contains("M9.13"));
        assertTrue(ship.getMineRacks().get(1).isDestroyed());
        assertFalse("four NSMs inside the hull did not end the ship", ship.isDestroyed());
        assertFalse("the other rack is untouched", ship.getMineRacks().get(0).isDestroyed());
    }

    /**
     * A bay space handed to {@code Ship} on the shuttle row is a no-op, on purpose.
     *
     * <p>{@code DamageResolver} owns that case because it owns D12.10. If this ever started
     * returning a label the bay would be destroyed twice over — once here and once there.
     */
    @Test
    public void aBaySpaceIsNotShipsToApplyOnTheShuttleRow() {
        Ship ship = hullWithRacks(1);

        assertNull(ship.applyDacChoiceHit("shuttle", "bay:0:space:0", null));
        assertFalse("and the space is still there",
                ship.getShuttles().getBays().get(0).getSpaces().get(0).isDestroyed());
    }

    // ---------------------------------------------------------------- through the real resolver

    /**
     * The regression, through a real volley: submitting a rack on a shuttle DAC choice must work.
     *
     * <p>Only reachable this way. The resolver's shuttle branch parses its option with
     * {@code split(":")} and indexes {@code parts[3]}, so before the guard went in a
     * {@code minerack:0} submission threw {@code ArrayIndexOutOfBoundsException} out of
     * {@code submitDacChoice} — and no unit test on {@code Ship} could see it, because
     * {@code Ship} is not what parses that string.
     *
     * <p>The DAC roll is random, so this retries fresh volleys until one happens to stop on a
     * shuttle hit, which is the idiom {@code DacChoiceRemainingDamageTest} established next door.
     * It reports rather than silently passing if it never gets there.
     */
    @Test
    public void submittingARackOnAShuttleChoiceResolvesThroughTheResolver() {
        for (int attempt = 0; attempt < 120; attempt++) {
            Game game = volleyUntilPaused();
            Ship target = shipNamed(game, "Target");

            // Work THROUGH the pauses a volley produces rather than abandoning the volley at
            // the first one. A 24-point phaser volley pauses many times and the first pause is
            // nearly always a phaser, so a loop that only ever looked at pending choice zero of
            // a fresh game found no shuttle hit in four hundred volleys and passed while
            // asserting nothing. Resolving each pause with its first option is what the
            // non-interactive wrapper does anyway, and on the cargo and excess rows that first
            // option is a cargo box, which is exactly what keeps the rack alive to be offered.
            for (int step = 0; step < 80
                    && game.getCurrentPhase() == Game.ImpulsePhase.DAC_CHOICE; step++) {
                Game.PendingDacChoice pending = game.getPendingDacChoices().get(0);

                // bayIndex >= 0 is a D12.10 chain reaction, which offers bay spaces only and
                // CORRECTLY so: the rule destroys "one additional space in the same bay", and a
                // mine rack is not one.
                if ("shuttle".equals(pending.dacType) && pending.bayIndex < 0) {
                    assertEquals("with every bay space destroyed up front the rack is the whole"
                            + " row: ship=" + pending.targetShipName
                            + " opts=" + pending.options,
                            List.of("minerack:0"), pending.options);

                    Game.ActionResult r = game.submitDacChoice("minerack:0");

                    assertTrue(r.getMessage(), r.isSuccess());
                    assertTrue("the rack is gone",
                            target.getMineRacks().get(0).isDestroyed());
                    assertTrue("and the log names it: " + r.getMessage(),
                            r.getMessage().contains("mine rack"));
                    assertFalse("a rack is not a chain reaction (M9.13): " + r.getMessage(),
                            r.getMessage().contains("chain reaction"));
                    return;
                }
                game.submitDacChoice(pending.options.get(0));
            }
        }
        fail("120 volleys never produced a shuttle DAC choice, so the resolver routing this test"
                + " exists for was never exercised. That is a broken fixture, not a pass:"
                + " a shuttle hit on a hull whose only shuttle-row box is a mine rack is the"
                + " whole point.");
    }

    // ---------------------------------------------------------------- fixtures

    /** A Federation CA with mine racks bolted on, since no hull in the data has both. */
    private static Ship hullWithRacks(int racks) {
        return hullWithRacksAndCargo(racks, 0);
    }

    /** The same, plus cargo boxes, so the cargo row has somewhere to go other than a rack. */
    private static Ship hullWithRacksAndCargo(int racks, int cargo) {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("mineracks", racks);
        if (cargo > 0)
            spec.put("cargo", cargo);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("USS Minelayer");
        return ship;
    }

    private static void destroyEveryBaySpace(Ship ship) {
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (int s = bay.getSpaces().size() - 1; s >= 0; s--)
                bay.destroySpace(s);
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (ShuttleSpace space : bay.getSpaces())
                assertTrue("fixture: every space destroyed", space.isDestroyed());
    }

    /**
     * A duel paused wherever it got to, with the target carrying one mine rack.
     *
     * <p>The energy allocation is not decoration: without it the phasers fire for zero, the
     * volley scores no internals, nothing ever pauses, and a test like this passes while
     * exercising nothing.
     *
     * <h2>Two things in this fixture are load-bearing, and the first attempt had neither</h2>
     * <b>Every bay space is destroyed up front.</b> That makes the rack the WHOLE shuttle row,
     * so the choice that comes back is exactly {@code [minerack:0]} and the test asserts on it
     * rather than hunting through a list that may or may not contain one.
     *
     * <p><b>The target is given cargo boxes.</b> Without them the cargo row's only candidate on
     * this hull is the rack itself, so a single cargo hit quietly destroyed it through the forced
     * path long before any shuttle hit arrived - which is CORRECT behaviour and exactly what the
     * first version of this test tripped over, reporting four bay options and no rack. With cargo
     * boxes present the cargo row has two candidates, so it pauses for the owner instead, this
     * loop skips that pause and starts a fresh volley, and the rack survives to be offered on the
     * row this test is about.
     */
    private static Game volleyUntilPaused() {
        Game game = new Game();

        Ship attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Attacker");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setActiveFireControl(true);

        Ship target = hullWithRacksAndCargo(1, 4);
        target.setName("Target");
        destroyEveryBaySpace(target);
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

        List<Weapon> fired = new ArrayList<>();
        for (Weapon w : attacker.getWeapons().getPhaserList())
            if (w.isFunctional())
                fired.add(w);
        game.fireWeapons(attacker, target, fired, 1, 1, 1);
        for (int i = 0; i < 12 && game.getCurrentPhase() != Game.ImpulsePhase.DAC_CHOICE; i++)
            game.advancePhase();
        return game;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("fixture: no ship called " + name);
    }
}
