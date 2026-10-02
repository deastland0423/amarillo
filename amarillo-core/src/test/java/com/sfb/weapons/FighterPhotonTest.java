package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.WeaponArmingType;

/**
 * The photon torpedo carried by a Federation photon fighter — the A-10 and A-20 (J4.851).
 * <p>
 * Built on {@code FighterDisruptor}'s pattern, so most of what is interesting here is where
 * J4.85 DIFFERS from it: one charge rather than two, and a proximity fuse the disruptor has no
 * equivalent of.
 * <p>
 * The dice are unseeded, so the hit itself is not asserted. What these pin is everything around
 * it — the charge economy, the two range limits, and which chart each fuse reads — because those
 * are the parts a wrong number makes permanently wrong rather than occasionally unlucky.
 */
public class FighterPhotonTest {

    private FighterPhoton loaded() {
        FighterPhoton p = new FighterPhoton();
        assertTrue("fixture: a fresh weapon takes a charge", p.loadCharge());
        return p;
    }

    // ---------------------------------------------------------------- the charge

    /** J4.852: "a capacitor for a single photon torpedo" — one, where a disruptor holds two. */
    @Test
    public void itHoldsExactlyOneCharge() {
        assertEquals(1, FighterPhoton.FULL_CHARGES);

        FighterPhoton p = new FighterPhoton();
        assertEquals("built empty, as every fighter weapon is (J4.8223)",
                0, p.getChargesRemaining());
        assertEquals(1, p.chargesMissing());

        assertTrue(p.loadCharge());
        assertEquals(1, p.getChargesRemaining());
        assertFalse("and there is no room for a second", p.loadCharge());
    }

    @Test
    public void anEmptyWeaponCannotFire() {
        FighterPhoton p = new FighterPhoton();

        assertFalse(p.canFire());
        try {
            p.fire(5);
            fail("an empty photon must not fire");
        } catch (Exception e) {
            assertTrue(e instanceof WeaponUnarmedException);
        }
    }

    /** The shot is spent whether it hits or misses: the torpedo has gone either way. */
    @Test
    public void firingSpendsTheChargeHitOrMiss() throws Exception {
        FighterPhoton p = loaded();

        p.fire(5);

        assertEquals(0, p.getChargesRemaining());
        assertFalse("and nothing is left to fire", p.canFire());
    }

    /** J1.3324: a crippled fighter loses what it was holding. */
    @Test
    public void cripplingDrainsIt() {
        FighterPhoton p = loaded();

        p.drainCharges();

        assertEquals(0, p.getChargesRemaining());
    }

    // ---------------------------------------------------------------- the two range limits

    /**
     * J4.45: "a Federation A-10 fighter cannot fire its photon at Range 0-1". A REFUSAL, not a
     * certain miss — the standard chart's zeroes at range 0 and 1 would also yield nothing, but
     * firing would spend the only charge the fighter has.
     */
    @Test
    public void itRefusesRangeZeroAndOneWithoutSpendingTheCharge() {
        for (int range : new int[]{0, 1}) {
            FighterPhoton p = loaded();
            try {
                p.fire(range);
                fail("range " + range + " must be refused (J4.45)");
            } catch (Exception e) {
                assertTrue(e instanceof TargetOutOfRangeException);
            }
            assertEquals("a refused shot is not a shot: the charge stays",
                    1, p.getChargesRemaining());
        }
    }

    /** J1.31: "photons are limited to Range 12" — shorter than the fifteen a shuttle gets. */
    @Test
    public void twelveIsTheReach() throws Exception {
        assertEquals(12, FighterPhoton.MAX_RANGE);
        assertEquals(12, new FighterPhoton().getMaxRange());

        loaded().fire(12);                      // legal

        FighterPhoton p = loaded();
        try {
            p.fire(13);
            fail("range 13 is past a fighter photon's reach");
        } catch (Exception e) {
            assertTrue(e instanceof TargetOutOfRangeException);
        }
        assertEquals(1, p.getChargesRemaining());
    }

    // ---------------------------------------------------------------- the fuse (J4.854)

    /** "The torpedo can be set as a standard or proximity fuse at the time the charge is loaded." */
    @Test
    public void theFuseIsChosenAsTheChargeGoesIn() {
        assertFalse("a plain load is a standard fuse", loaded().isProximity());

        FighterPhoton prox = new FighterPhoton();
        assertTrue(prox.loadCharge(true));

        assertTrue(prox.isProximity());
        assertEquals(WeaponArmingType.SPECIAL, prox.getArmingType());
    }

    /** "Changing this afterwards requires a deck crew action" — the change is possible. */
    @Test
    public void theFuseCanBeChangedAfterwards() {
        FighterPhoton p = loaded();
        assertFalse(p.isProximity());

        p.setProximity(true);

        assertTrue(p.isProximity());
        assertEquals(WeaponArmingType.SPECIAL, p.getArmingType());
    }

    /** Each fuse reads its own chart (E4.4), and a proximity hit does half damage. */
    @Test
    public void eachFuseReadsItsOwnChart() throws Exception {
        // Range 5: the standard chart hits on 3 or less, the proximity chart cannot hit at all
        // (it is all zeroes inside range 9). So a proximity shot at 5 is always a miss, which
        // makes this deterministic without seeding anything.
        assertEquals("fixture: proximity cannot reach range 5",
                0, Photon.proximityChart()[5]);

        FighterPhoton prox = new FighterPhoton();
        prox.loadCharge(true);
        assertEquals("a proximity fuse scores nothing at range 5", 0, prox.fire(5));

        // And inside the proximity band it pays 4 rather than 8 when it does hit.
        assertEquals(FighterPhoton.PROXIMITY_DAMAGE, 4);
        assertEquals(FighterPhoton.STANDARD_DAMAGE, 8);
    }

    /** J4.852: "overloads are not allowed", and there is no energy aboard to overload with. */
    @Test
    public void thereIsNoOverload() {
        FighterPhoton p = loaded();

        p.setProximity(true);
        assertEquals(WeaponArmingType.SPECIAL, p.getArmingType());
        p.setProximity(false);
        assertEquals("the only two settings are standard and proximity",
                WeaponArmingType.STANDARD, p.getArmingType());
    }

    /** The charts come from Photon, so the two cannot drift apart. */
    @Test
    public void theChartsAreThePhotonsOwn() {
        assertArrayEquals(Photon.standardHitChart(), new FighterPhoton().getHitChart());
    }
}
