package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.Weapon;

/**
 * A volley's dice are printed once, and its resolution says what the damage did.
 *
 * <h2>The defect this pins</h2>
 * Reported from a playtest, in a log that read:
 *
 * <pre>
 *   IKV Vengeance  →  USS Enterprise   range 8   shield #1
 *     Disruptor-30 A  (die 4)  HIT  3
 *     Disruptor-30 B  (die 1)  HIT  3
 *     Total damage: 6 — queued, resolves in Reinforcement phase
 *   — Reinforcement —
 *   — Movement —
 *   IKV Vengeance  →  USS Enterprise   range 8   shield #1
 *     Disruptor-30 A  (die 4)  HIT  3
 *     Disruptor-30 B  (die 1)  HIT  3
 *     Total damage: 6 — queued, resolves in Reinforcement phase
 * </pre>
 *
 * The identical dice are the proof it was one volley printed twice and not two volleys: rolls
 * come from unseeded randomness, so a second volley could not match. Damage is QUEUED when fired
 * so defenders may spend reserve power first (C3.14), and the rolls were stored on the volley to
 * survive that wait — correctly. They were then replayed in full at resolution, which printed
 * them a second time, still carrying the words "queued, resolves in Reinforcement phase" at the
 * exact moment it was resolving.
 *
 * <h2>The second, quieter half</h2>
 * Because resolution printed only the replayed dice, a volley the shield ABSORBED ENTIRELY
 * produced no outcome line at all — which is the case in the log above, where a full shield ate
 * all six points. The player spends reserve power and is then told nothing about whether it was
 * needed. So resolution now names the target, the shield, the combined damage and who fired, and
 * says either what bled through or that the shield held.
 */
public class VolleyLogDuplicationTest {

    private Game game;
    private Ship attacker;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();
        game.getClock().nextImpulse();

        attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("IKV Vengeance");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("USS Enterprise");
        target.setLocation(new Location(10, 12));
        target.setFacing(1);

        game.getShips().add(attacker);
        game.getShips().add(target);

        // Without this the phaser reports "no capacitor energy" and never rolls, so every
        // assertion about dice passes vacuously — the first draft of this test did exactly that.
        try {
            attacker.chargeCapacitor(attacker.getWeapons().getAvailablePhaserCapacitor());
        } catch (Exception e) {
            throw new AssertionError("fixture: the CA should take a capacitor charge", e);
        }
        attacker.setActiveFireControl(true);
        attacker.addLockOn(target);
    }

    // ---------------------------------------------------------------- the duplication

    /**
     * The dice appear once across the whole exchange — when fired, not again when resolved.
     *
     * Counted over firing AND resolution together, because that is the player's experience: one
     * scrolling log. A test that looked at either half alone would have passed throughout.
     */
    @Test
    public void aWeaponsRollIsPrintedOnceAcrossFiringAndResolution() {
        String fireLog = game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);
        List<String> everything = new ArrayList<>();
        everything.add(fireLog);
        everything.addAll(resolve());

        String whole = String.join("\n", everything);
        assertEquals("the die roll line should appear exactly once: \n" + whole,
                1, occurrences(whole, "(die "));
    }

    /** And the pre-resolution wording must not survive into the resolution entry. */
    @Test
    public void theQueuedNoticeIsNotRepeatedWhenTheVolleyResolves() {
        String fireLog = game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);
        assertTrue("fixture: firing should say the damage is queued",
                fireLog.contains("queued"));

        String resolution = String.join("\n", resolve());

        assertFalse("a volley that is resolving must not still say it is queued:\n" + resolution,
                resolution.contains("queued"));
    }

    // ---------------------------------------------------------------- what resolution now says

    /** The entry has to identify itself, now that it no longer carries the rolls. */
    @Test
    public void resolutionNamesTheTargetTheShieldAndTheAttacker() {
        game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);

        String resolution = String.join("\n", resolve());

        assertTrue(resolution, resolution.contains("USS Enterprise"));
        assertTrue(resolution, resolution.contains("shield #1"));
        assertTrue("whose damage it is: " + resolution, resolution.contains("IKV Vengeance"));
    }

    /**
     * The hole the duplication was hiding: a volley the shield absorbs entirely used to log
     * nothing but the repeated dice, so the defender was never told the shield held.
     */
    @Test
    public void aShieldThatAbsorbsEverythingSaysSo() {
        // One phaser against a full shield cannot get through a CA's shield 1.
        game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);
        assertTrue("fixture: shield 1 should still be standing",
                target.getShields().getShieldStrength(1) > 0);

        String resolution = String.join("\n", resolve());

        assertTrue("the player must be told the shield held: " + resolution,
                resolution.contains("shield holds"));
    }

    /** A volley that gets through still reports the bleed-through, as it always did. */
    @Test
    public void bleedThroughIsStillReported() {
        target.getShields().damageShield(1, target.getShields().getShieldStrength(1));
        assertEquals("fixture: shield 1 is down", 0, target.getShields().getShieldStrength(1));

        game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);
        String resolution = String.join("\n", resolve());

        assertTrue(resolution, resolution.contains("BLEED-THROUGH"));
        assertFalse("and it did not hold", resolution.contains("shield holds"));
    }

    /**
     * The rolls are still RECORDED on the volley, which is what the wait needs — they are simply
     * not reprinted. Several tests read them from here, and the reinforcement dialog is built on
     * the same data.
     */
    @Test
    public void theVolleyStillCarriesItsOwnRollsForWhoeverNeedsThem() {
        game.fireWeapons(attacker, target, phasers("1"), 1, 1, 1);

        assertEquals(1, game.getPendingVolleys().size());
        assertTrue("the volley keeps its dice even though resolution does not reprint them",
                game.getPendingVolleys().get(0).attackerLog.contains("(die "));
    }

    // ---------------------------------------------------------------- helpers

    /** Walk the phases until the queued volley has been applied. */
    private List<String> resolve() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 12 && !game.getPendingVolleys().isEmpty(); i++) {
            Game.ActionResult r = game.advancePhase();
            if (r != null && r.getMessage() != null && !r.getMessage().isBlank())
                out.add(r.getMessage());
        }
        assertTrue("fixture: the volley should have resolved", game.getPendingVolleys().isEmpty());
        return out;
    }

    private List<Weapon> phasers(String designator) {
        List<Weapon> out = new ArrayList<>();
        for (Weapon w : attacker.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.Phaser1 && designator.equals(w.getDesignator()))
                out.add(w);
        assertFalse("fixture: phaser " + designator + " should exist", out.isEmpty());
        return out;
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1))
            count++;
        return count;
    }
}
