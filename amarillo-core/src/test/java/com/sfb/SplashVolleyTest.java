package com.sfb;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

/**
 * A volley that lands on several shields at once, which is what a PPD pulse does (E11.35).
 *
 * <h2>Why it cannot go through the ordinary path</h2>
 * Damage normally collects into a group per (target, shield): two attackers hitting your #3
 * combine, the shield absorbs the total, and what gets through becomes one internal allocation.
 * A PPD pulse spreads over three shields — 1+4+1 at range 4-10 — and E11.332 calls that "a
 * single volley separate from other weapons (including other PPDs) and other pulses of that
 * PPD". So it must bypass the grouping entirely, and the bleed from its three shields must POOL
 * into one allocation. Three allocations would run the DAC three times for one pulse.
 *
 * <p>Tested here on the mechanism rather than through a PPD, because the weapon does not exist
 * yet — this is the foundation it will stand on.
 */
public class SplashVolleyTest {

    private Game game;
    private Ship attacker;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();
        game.getClock().nextImpulse();

        attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("ISC Pulsar");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("USS Enterprise");
        target.setLocation(new Location(10, 12));
        target.setFacing(1);

        game.getShips().add(attacker);
        game.getShips().add(target);
    }

    /** Queue one splash volley: shield → damage, centre first. */
    private void splash(Map<Integer, Integer> perShield) {
        game.queueVolley(new Game.PendingVolley(
                attacker.getName(), attacker, target, 3, perShield, ""));
    }

    private static Map<Integer, Integer> hits(int... pairs) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2)
            m.put(pairs[i], pairs[i + 1]);
        return m;
    }

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

    // ------------------------------------------------------------------ each shield absorbs

    /** The centre shield and both neighbours take their own share, independently. */
    @Test
    public void eachShieldTakesItsOwnShare() {
        int before2 = target.getShields().getShieldStrength(2);
        int before3 = target.getShields().getShieldStrength(3);
        int before4 = target.getShields().getShieldStrength(4);

        splash(hits(3, 4, 2, 1, 4, 1));   // 1+4+1 against the #3 shield
        resolve();

        assertEquals("the facing shield takes the main blast",
                before3 - 4, target.getShields().getShieldStrength(3));
        assertEquals("the clockwise neighbour takes its splash element",
                before2 - 1, target.getShields().getShieldStrength(2));
        assertEquals("and the counter-clockwise one takes the other",
                before4 - 1, target.getShields().getShieldStrength(4));
    }

    // ------------------------------------------------------------------ one allocation

    /**
     * THE POINT. Three shields down to nothing, so every element penetrates — and E11.332 makes
     * the result ONE internal allocation of the pooled total, not one per shield.
     */
    @Test
    public void whatGetsThroughPoolsIntoASingleAllocation() {
        for (int shield : new int[] { 2, 3, 4 })
            target.getShields().setShieldValue(shield, 0);

        splash(hits(3, 4, 2, 1, 4, 1));
        String log = String.join("\n", resolve());

        assertEquals("one bleed-through line, not three", 1,
                log.split("BLEED-THROUGH", -1).length - 1);
        assertTrue("and it reports the pooled total of 6, not a per-shield figure\n" + log,
                log.contains("BLEED-THROUGH: 6"));
    }

    /** A shield that holds contributes nothing, and the rest still pool. */
    @Test
    public void aShieldThatHoldsAddsNothingToThePool() {
        target.getShields().setShieldValue(3, 0);   // only the centre is down

        splash(hits(3, 4, 2, 1, 4, 1));
        String log = String.join("\n", resolve());

        assertTrue("only the centre element penetrates\n" + log,
                log.contains("BLEED-THROUGH: 4"));
    }

    /** Every shield holding is reported as such rather than silently producing nothing. */
    @Test
    public void shieldsHoldingIsSaidOutLoud() {
        splash(hits(3, 4, 2, 1, 4, 1));
        String log = String.join("\n", resolve());

        assertTrue("the player is told the shields held\n" + log,
                log.contains("shields hold"));
    }

    // ------------------------------------------------------------------ it stays separate

    /**
     * E11.332: separate from other weapons AND from other pulses. Two splash volleys on the
     * same shields stay two allocations, where two phaser volleys on one shield would combine.
     */
    @Test
    public void twoSplashVolleysDoNotCombine() {
        for (int shield : new int[] { 2, 3, 4 })
            target.getShields().setShieldValue(shield, 0);

        splash(hits(3, 4, 2, 1, 4, 1));
        splash(hits(3, 4, 2, 1, 4, 1));
        String log = String.join("\n", resolve());

        assertEquals("two pulses, two allocations", 2,
                log.split("BLEED-THROUGH", -1).length - 1);
    }
}
