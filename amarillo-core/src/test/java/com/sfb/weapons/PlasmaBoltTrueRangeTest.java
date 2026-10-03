package com.sfb.weapons;

import static org.junit.Assert.*;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

/**
 * A plasma bolt's TO-HIT comes from the effective range and its DAMAGE from the true range
 * (FP8.42, FP8.43). They are different numbers whenever a scanner is in play, and the launcher
 * used to read both off the inflated one.
 *
 * <h2>The bug this pins</h2>
 * {@code DirectFire.fire(realRange, adjustedRange)} defaults to passing {@code adjustedRange}
 * into the single-argument {@code fire}, which is correct for a variable-damage weapon — a
 * phaser really is weaker against a target a scanner has pushed away — and wrong for every
 * hit-or-miss weapon. {@code Photon}, {@code Disruptor} and {@code FighterPhoton} each override
 * it for that reason; {@code PlasmaLauncher} did not, so a scanner took warhead strength off a
 * plasma bolt as well as making it harder to land.
 *
 * <p>FP8.43 is explicit about which range pays: "The amount of damage scored (if the torpedo
 * hits) is equal to one-half of the warhead strength of the corresponding plasma torpedo (S-bolt
 * = S-torpedo) <b>at the true range to the target</b>."
 */
public class PlasmaBoltTrueRangeTest {

    /** A launcher of this type, armed and ready to bolt. */
    private static PlasmaLauncher armed(PlasmaType type) {
        PlasmaLauncher launcher = new PlasmaLauncher(type);
        launcher.setDesignator("A");
        // Arm it the way a ship does: the rolling cost on each turn but the last, then the burst.
        while (!launcher.isArmed()) {
            int turn = launcher.getArmingTurn();
            if (!launcher.arm(launcher.energyToArm()))
                throw new AssertionError("fixture: could not arm a type-" + type
                        + " on arming turn " + turn);
        }
        return launcher;
    }

    /** Half the warhead of this plasma type at this range, per FP8.43. */
    private static int expectedBolt(PlasmaType type, int range) {
        PlasmaTorpedo reference = new PlasmaTorpedo(type, WeaponArmingType.STANDARD);
        for (int i = 0; i < range; i++)
            reference.incrementDistance();
        return reference.getCurrentStrength() / 2;
    }

    /**
     * The fix, stated as the rule: at a true range of 2 seen as 12 hexes away, the damage is the
     * range-2 warhead and not the range-12 one.
     * <p>
     * A type-G drops from 20 to 15 between those ranges, so the two answers differ and one of
     * them is observable. Fired repeatedly because the die is unseeded — a single miss would
     * assert nothing — and the test fails if no hit ever lands rather than passing on an empty
     * set of observations.
     */
    @Test
    public void aScannerDoesNotWeakenTheWarhead() throws Exception {
        int trueRange = 2;
        int seenAt = 12;

        int atTrueRange = expectedBolt(PlasmaType.G, trueRange);
        int atAdjustedRange = expectedBolt(PlasmaType.G, seenAt);
        assertNotEquals("fixture: the two ranges must disagree for this to prove anything",
                atTrueRange, atAdjustedRange);

        Set<Integer> hits = new TreeSet<>();
        for (int attempt = 0; attempt < 60; attempt++) {
            int damage = armed(PlasmaType.G).fire(trueRange, seenAt);
            if (damage > 0)
                hits.add(damage);
        }

        assertFalse("no bolt hit in sixty attempts, so nothing was checked", hits.isEmpty());
        assertEquals("every hit is the TRUE range's warhead, halved",
                Set.of(atTrueRange), hits);
    }

    /**
     * The other half of the same split: the scanner still makes the shot HARDER. At an effective
     * range beyond FP8.42's chart there is no to-hit number at all, so the bolt is refused even
     * though the true range is point blank.
     */
    @Test
    public void aScannerStillCostsTheToHit() throws Exception {
        try {
            armed(PlasmaType.G).fire(0, 99);
            fail("an effective range off the FP8.42 chart has no to-hit number");
        } catch (TargetOutOfRangeException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("out of range"));
        }
    }

    /**
     * With no scanner the two ranges are the same number, so the single-argument form must agree
     * with the two-argument one. It delegates rather than duplicating, which is what keeps them
     * from drifting apart the way they had.
     */
    @Test
    public void withNoScannerBothFormsAgree() throws Exception {
        int range = 5;
        int expected = expectedBolt(PlasmaType.S, range);

        Set<Integer> hits = new TreeSet<>();
        for (int attempt = 0; attempt < 60; attempt++) {
            int damage = armed(PlasmaType.S).fire(range);
            if (damage > 0)
                hits.add(damage);
        }

        assertFalse("no bolt hit in sixty attempts", hits.isEmpty());
        assertEquals(Set.of(expected), hits);
    }

    /**
     * The warhead check is against the TRUE range too. A target close enough to bolt is bolted,
     * however far away a scanner makes it look — the refusal must come from FP8.42's chart
     * running out, not from the warhead table.
     */
    @Test
    public void aCloseTargetIsStillWorthBoltingHoweverFarItLooks() throws Exception {
        // Range 20 is inside FP8.42's chart, so a to-hit number exists; a type-F warhead at
        // range 20 is zero, but at the true range of 1 it is full strength.
        int damage = armed(PlasmaType.F).fire(1, 20);

        assertTrue("a miss or the point-blank warhead, never a refusal",
                damage == 0 || damage == expectedBolt(PlasmaType.F, 1));
    }

    /** An unarmed launcher bolts nothing, whichever form is called. */
    @Test
    public void anUnarmedLauncherBoltsNothing() {
        PlasmaLauncher cold = new PlasmaLauncher(PlasmaType.G);
        cold.setDesignator("B");

        try {
            cold.fire(3, 3);
            fail("an unarmed launcher has no torpedo to bolt");
        } catch (WeaponUnarmedException | TargetOutOfRangeException expected) {
            assertTrue(expected instanceof WeaponUnarmedException);
        }
    }
}
