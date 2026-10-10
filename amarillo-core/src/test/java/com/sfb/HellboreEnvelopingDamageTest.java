package com.sfb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;

/**
 * E10.4: an enveloping hellbore spreads over the WHOLE ship.
 *
 * <h2>What this is guarding</h2>
 * "Upon striking the target, it spreads over the entire ship (by a special magnetic field) and
 * then implodes. Due to the nature of the shields themselves, more damage is done to the weakest
 * shield than any other" (E10.0). Not "all of it goes to the weakest shield" — the weakest shield
 * takes one share and the rest is spread over the others.
 *
 * <p>Reported in play 2026-10-09: two hellbores hit for thirteen each, the weakest shield took
 * thirteen, and the other thirteen were never seen again. Two faults in Step C, which compounded:
 *
 * <ol>
 *   <li>E10.413 distributes the remainder over "the REMAINING (usually five) shields" — the ones
 *       Step B did not already hit. The loop walked all six, and since Step B had just weakened
 *       the weakest shield it was still the weakest, so every remaining point landed straight
 *       back on it.</li>
 *   <li>Step C threw away the return of {@code damageShield}, which is the bleed-through. Once
 *       that shield reached zero, every further point vanished instead of becoming internal
 *       damage.</li>
 * </ol>
 *
 * <p>Neither was visible from the weapon's own tests, which check what a hellbore ROLLS. Nothing
 * had ever tested what happens to the damage afterwards.
 */
public class HellboreEnvelopingDamageTest {

    private Game game;
    private DamageResolver resolver;
    private List<Game.PendingDamage> pendingInternal;

    @Before
    public void setUp() {
        game = new Game();
        pendingInternal = new ArrayList<>();
        resolver = new DamageResolver(game, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), pendingInternal, new ArrayList<>(),
                new HashSet<>(), new HashMap<>());
    }

    private static int[] shieldsOf(Ship s) {
        int[] out = new int[6];
        for (int i = 0; i < 6; i++)
            out[i] = s.getShields().getShieldStrength(i + 1);
        return out;
    }

    private static int total(int[] a) {
        int n = 0;
        for (int x : a) n += x;
        return n;
    }

    private int pendingTotal() {
        int n = 0;
        for (Game.PendingDamage p : pendingInternal) n += p.bleed;
        return n;
    }

    // -------------------------------------------------------------------------

    /**
     * The reported case. One weak shield, a volley of 26, and every point accounted for.
     *
     * <p>26 over (1 + 1) groups is 13 to the weak shield and 13 to spread over the other five —
     * so the other five must lose 13 between them. Before the fix they lost nothing.
     */
    @Test
    public void theHalfThatIsNotTheWeakShieldReachesTheOtherShields() {
        // A Fed CA leaves the yard at 30/24/20/20/20/24, so #3, #4 and #5 are equally weak
        // already. ONE weak shield is the case the owner met, so #3 is taken down below the
        // others — far enough that Step B's share cannot empty it, leaving the arithmetic to be
        // purely about distribution.
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.damageShield(3, 2);                       // 20 -> 18, now the lone weakest
        int[] before = shieldsOf(target);
        assertEquals("fixture: #3 is the one weak shield", 18, before[2]);
        assertEquals("fixture: and the next weakest is above it", 20, before[3]);

        resolver.applyHellboreEnvelopingDamage(target, 26);
        int[] after = shieldsOf(target);

        assertEquals("the weak shield takes its own group of 13 (E10.412)", 5, after[2]);
        int elsewhere = (total(before) - before[2]) - (total(after) - after[2]);
        assertEquals("the other 13 must land on the OTHER five shields (E10.413)", 13, elsewhere);
        assertEquals("and nothing should have bled through", 0, pendingTotal());
    }

    /**
     * Step C must not pile onto the shield Step B just emptied — which is what made the reported
     * damage disappear, because a shield at zero swallows every further point.
     */
    @Test
    public void stepCSkipsTheShieldStepBAlreadyHit() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.damageShield(3, target.getShields().getShieldStrength(3)); // #3 flat at zero
        int[] before = shieldsOf(target);
        assertEquals("fixture: #3 is down and alone at zero", 0, before[2]);
        assertEquals("fixture: #4 is not", 20, before[3]);

        resolver.applyHellboreEnvelopingDamage(target, 26);
        int[] after = shieldsOf(target);

        // Step B's 13 go through the downed shield as internal damage; Step C's 13 must still
        // find the other five shields rather than following them into the hole.
        int elsewhere = (total(before) - before[2]) - (total(after) - after[2]);
        assertEquals("Step C's share still reaches the other five shields", 13, elsewhere);
        assertEquals("and Step B's share bleeds through, rather than being lost", 13,
                pendingTotal());
    }

    /**
     * Nothing may be silently dropped. Whatever a volley is worth, every point either sits on a
     * shield or arrives as internal damage — which is the property the bug broke, and the one
     * worth pinning for every future change to this method.
     */
    @Test
    public void everyPointIsEitherOnAShieldOrInside() {
        for (int volley : new int[] { 1, 7, 13, 26, 40, 41, 120 }) {
            Ship target = new Ship();
            target.init(FederationShips.getFedCa());
            target.damageShield(2, 10);          // an uneven starting state
            target.damageShield(5, 4);
            pendingInternal.clear();

            int before = total(shieldsOf(target));
            resolver.applyHellboreEnvelopingDamage(target, volley);
            int absorbed = before - total(shieldsOf(target));

            assertEquals("volley of " + volley + ": shields absorbed " + absorbed
                    + " and " + pendingTotal() + " went inside",
                    volley, absorbed + pendingTotal());
        }
    }

    /** E10.412: with every shield equal there is no "weakest", so Step B is skipped entirely. */
    @Test
    public void allShieldsEqualSkipsStepB() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        int[] start = shieldsOf(target);
        for (int i = 0; i < 6; i++)
            if (start[i] > 20)
                target.damageShield(i + 1, start[i] - 20);   // flatten them all to 20
        assertEquals("fixture: all six equal", 120, total(shieldsOf(target)));

        resolver.applyHellboreEnvelopingDamage(target, 12);
        int[] after = shieldsOf(target);

        for (int i = 0; i < 6; i++)
            assertEquals("shield #" + (i + 1) + " should take an even two", 18, after[i]);
    }

    /** E10.42: a shield at zero IS the weakest, so it still draws Step B's share. */
    @Test
    public void aDownedShieldIsStillTheWeakest() {
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.damageShield(4, target.getShields().getShieldStrength(4));
        pendingInternal.clear();

        resolver.applyHellboreEnvelopingDamage(target, 20);

        assertTrue("a shield at zero takes Step B's group as internal damage",
                pendingTotal() >= 10);
    }
}
