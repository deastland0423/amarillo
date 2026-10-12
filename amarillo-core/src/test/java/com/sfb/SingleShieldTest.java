package com.sfb;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Location;

/**
 * P2.73: one shield, covering all arcs.
 *
 * <p>"The small and medium ground bases (P2.76) have a single shield (covering all arcs) as part of
 * their design." Six shields each absorbing separately would quietly give such a base SIX TIMES its
 * protection, since a ship's shields are picked by the bearing the attack comes from and each holds
 * its own strength.
 *
 * <p>The count is a property of the base's SIZE and not of where it sits. What the siting changes
 * is the arc it can fire into and be hit from — 180° on a planet (P2.721), 360° centred on a large
 * asteroid, 180° on an asteroid hex side (P2.747) — which is a different thing and not what this
 * tests.
 *
 * <p>The declaration is the length of the shields array itself, so a hull with one shield writes
 * {@code "shields": [12]}. Anything other than 1 or 6 is refused rather than ignored: the mapping
 * used to test {@code length >= 6} and silently drop a shorter array, which would have given a
 * ground base no shields at all without a word of complaint.
 */
public class SingleShieldTest {

    private Ship shipWithShields(int... shields) {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Klingon";
        spec.type = "TEST";
        spec.shields = shields;
        Ship ship = new Ship();
        ship.init(spec.toInitMap());
        ship.setName("Test Unit");
        ship.setLocation(new Location(10, 10));
        ship.setFacing(1);
        return ship;
    }

    /** An attacker placed on each of the six facings in turn. */
    private Ship attackerAt(int col, int row) {
        Ship a = new Ship();
        Map<String, Object> empty = new HashMap<>();
        a.init(empty);
        a.setName("Attacker");
        a.setLocation(new Location(col, row));
        a.setFacing(1);
        return a;
    }

    @Test
    public void sixShieldsIsStillTheDefault() {
        Ship ship = shipWithShields(30, 22, 15, 13, 15, 22);

        assertEquals(6, ship.getShields().getShieldCount());
        assertFalse(ship.getShields().hasSingleShield());
        assertEquals(30, ship.getShields().getShieldStrength(1));
        assertEquals(13, ship.getShields().getShieldStrength(4));
    }

    @Test
    public void oneShieldIsDeclaredByTheArrayLength() {
        Ship base = shipWithShields(12);

        assertEquals(1, base.getShields().getShieldCount());
        assertTrue(base.getShields().hasSingleShield());
        assertEquals("and it holds what the file said", 12,
                base.getShields().getShieldStrength(1));
    }

    /** The point of the whole slice: every bearing lands on the one shield. */
    @Test
    public void everyBearingResolvesToShieldOne() {
        Ship base = shipWithShields(12);
        Game game = new Game();
        DamageResolver resolver = game.damageResolver();

        // All six neighbours of 10|10, so every facing is covered.
        int[][] around = { {10, 9}, {11, 9}, {11, 10}, {10, 11}, {9, 10}, {9, 9} };
        for (int[] at : around) {
            Ship attacker = attackerAt(at[0], at[1]);
            assertEquals("from " + at[0] + "|" + at[1],
                    1, resolver.getShieldNumber(attacker, base));
            assertEquals("and never a seam prompt from " + at[0] + "|" + at[1],
                    List.of(1), resolver.getShieldCandidates(attacker, base));
        }
    }

    /** A six-shielded ship still picks by facing, or this would have broken every ship. */
    @Test
    public void aSixShieldedShipStillPicksByFacing() {
        Ship ship = shipWithShields(30, 22, 15, 13, 15, 22);
        Game game = new Game();
        DamageResolver resolver = game.damageResolver();

        int ahead  = resolver.getShieldNumber(attackerAt(10, 9), ship);
        int behind = resolver.getShieldNumber(attackerAt(10, 11), ship);

        assertEquals("directly ahead is shield 1", 1, ahead);
        assertNotEquals("and astern is not", 1, behind);
    }

    @Test
    public void anyOtherShieldCountIsRefusedRatherThanIgnored() {
        try {
            shipWithShields(30, 22);
            fail("two shields should be refused");
        } catch (IllegalStateException expected) {
            assertTrue("the refusal should say what is legal: " + expected.getMessage(),
                    expected.getMessage().contains("only 1")
                            && expected.getMessage().contains("6"));
        }
    }
}
