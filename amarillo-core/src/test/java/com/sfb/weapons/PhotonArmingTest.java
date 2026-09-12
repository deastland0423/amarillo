package com.sfb.weapons;

import com.sfb.properties.WeaponArmingType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Photon arming and overloading (E4.2/E4.4). Two points of warp energy are mandatory on each of
 * two consecutive turns (E4.21); anything above them is overload energy, which commits the
 * torpedo for good (E4.411/E4.414). A turn takes at most six points — the mandatory two plus the
 * four that reach 100% overload (E4.41) — and the warhead is twice the total energy (E4.413).
 */
public class PhotonArmingTest {

    private Photon fresh() {
        Photon p = new Photon();
        p.reset();
        return p;
    }

    /** A photon at weapon status II: first arming turn complete, its two points in the tube. */
    private Photon atWeaponStatusTwo() {
        Photon p = fresh();
        p.setArmingTurn(1);
        p.setArmingEnergy(Photon.STANDARD_PER_TURN);
        return p;
    }

    @Test
    public void standardArming_isTwoPointsOnEachOfTwoTurns() {
        Photon p = fresh();

        assertTrue(p.armWithEnergy(2));
        assertFalse("one turn in, not yet armed", p.isArmed());
        assertTrue(p.armWithEnergy(2));

        assertTrue(p.isArmed());
        assertEquals(4.0, p.getArmingEnergy(), 0.001);
        assertEquals(WeaponArmingType.STANDARD, p.getArmingType());
    }

    /** E4.21: the two-point charge is mandatory — a smaller payment buys no arming turn. */
    @Test
    public void lessThanTwoPoints_armsNothing() {
        Photon p = fresh();

        assertFalse(p.armWithEnergy(1));

        assertEquals(0, p.getArmingTurn());
        assertEquals(0.0, p.getArmingEnergy(), 0.001);
    }

    /**
     * E4.411 row #1 (turn 1 = 2+0, turn 2 = 2+4) — the weapon-status-II case. Six points on the
     * final turn reaches a fully overloaded torpedo.
     */
    @Test
    public void weaponStatusTwo_sixPointsOnTheFinalTurn_isFullyOverloaded() {
        Photon p = atWeaponStatusTwo();

        assertTrue(p.armWithEnergy(6));

        assertTrue(p.isArmed());
        assertEquals(8.0, p.getArmingEnergy(), 0.001);
        assertEquals(WeaponArmingType.OVERLOAD, p.getArmingType());
        assertEquals("warhead is twice the total (E4.413)", 16, (int) (p.getArmingEnergy() * 2));
    }

    /** The whole dial available to a weapon-status-II photon on its final arming turn. */
    @Test
    public void weaponStatusTwo_dialRunsFromEightToSixteenDamage() {
        int[][] payToDamage = { {2, 8}, {3, 10}, {4, 12}, {5, 14}, {6, 16} };
        for (int[] row : payToDamage) {
            Photon p = atWeaponStatusTwo();
            assertTrue(p.armWithEnergy(row[0]));
            assertTrue(p.isArmed());
            assertEquals("paying " + row[0], row[1], (int) (p.getArmingEnergy() * 2));
        }
    }

    /** E4.414: any overload energy at all commits the torpedo to being an overload. */
    @Test
    public void oneExtraPoint_commitsItAsAnOverload() {
        Photon p = atWeaponStatusTwo();

        p.armWithEnergy(3);

        assertEquals(WeaponArmingType.OVERLOAD, p.getArmingType());
        assertEquals(5.0, p.getArmingEnergy(), 0.001);
        assertEquals("overload range limit applies (E4.42)", 8, p.getMaxRange());
        assertFalse("and it can never be walked back (E4.414)", p.setStandard());
    }

    /** E4.41: overloading tops out at 100% — four points above the standard four. */
    @Test
    public void overloadIsCappedAtOneHundredPercent() {
        Photon p = fresh();

        assertTrue(p.armWithEnergy(6));   // 2 standard + 4 overload — the whole overload allowance
        assertTrue(p.armWithEnergy(6));   // second turn can only add its mandatory 2

        assertEquals(8.0, p.getArmingEnergy(), 0.001);
        assertEquals(4.0, p.overloadEnergy(), 0.001);
        assertEquals(16, (int) (p.getArmingEnergy() * 2));
    }

    /** E4.411 row #2: 2+2 on each turn is the other route to a full overload. */
    @Test
    public void fourAndFour_alsoReachesFullOverload() {
        Photon p = fresh();

        p.armWithEnergy(4);
        p.armWithEnergy(4);

        assertTrue(p.isArmed());
        assertEquals(8.0, p.getArmingEnergy(), 0.001);
    }

    /** E4.414: energy is recorded in half-points, anything short counting as the lower level. */
    @Test
    public void fractionalEnergy_roundsDownToTheHalfPoint() {
        Photon p = atWeaponStatusTwo();

        p.armWithEnergy(3.7);   // 3.5 accepted

        assertEquals(5.5, p.getArmingEnergy(), 0.001);
        assertEquals("half points give odd warheads (E4.413)", 11, (int) (p.getArmingEnergy() * 2));
    }

    /** E4.413's feedback column, by total energy in the tube. */
    @Test
    public void feedbackDamage_followsTheEnergyTable() {
        double[][] energyToFeedback = {
            {4.5, 1}, {5.0, 1}, {5.5, 2}, {6.0, 2}, {6.5, 3}, {7.0, 3}, {7.5, 4}, {8.0, 4}
        };
        for (double[] row : energyToFeedback) {
            Photon p = fresh();
            p.setOverload();
            p.setArmingEnergy(row[0]);
            assertEquals("at " + row[0] + " points", (int) row[1], p.feedbackDamage());
        }
    }

    /** A standard torpedo has no feedback — it cannot be fired that close at all (E4.14). */
    @Test
    public void standardTorpedo_hasNoFeedback() {
        Photon p = fresh();
        p.armWithEnergy(2);
        p.armWithEnergy(2);

        assertEquals(0, p.feedbackDamage());
    }

    /** E4.414: carrying overload energy but under 4.5 points, it cannot be fired at all. */
    @Test
    public void overloadUnderFourAndAHalf_cannotBeFired() {
        Photon p = fresh();
        p.setOverload();
        p.setArmingEnergy(4.0);

        assertFalse(p.isFirableOverload());

        p.setArmingEnergy(4.5);
        assertTrue(p.isFirableOverload());
    }

    /** E4.34: a proximity-fused torpedo cannot be overloaded. */
    @Test
    public void proximityTorpedo_refusesOverloadEnergy() {
        Photon p = fresh();
        assertTrue(p.setSpecial());

        assertFalse(p.armWithEnergy(4));

        assertEquals(WeaponArmingType.SPECIAL, p.getArmingType());
        assertEquals(0.0, p.getArmingEnergy(), 0.001);
    }

    /** E4.411: overload energy may also be added to a torpedo already sitting in the tube. */
    @Test
    public void anArmedStandardTorpedo_canStillBeOverloaded() {
        Photon p = fresh();
        p.armWithEnergy(2);
        p.armWithEnergy(2);
        assertTrue(p.isArmed());

        assertTrue(p.armWithEnergy(4));

        assertEquals(8.0, p.getArmingEnergy(), 0.001);
        assertEquals(WeaponArmingType.OVERLOAD, p.getArmingType());
    }

    /**
     * E4.21/E1.24: allocating nothing is a choice, not a smaller payment — the tube is
     * discharged and the arming cycle starts over. The energy already in it is lost.
     */
    @Test
    public void allocatingNothing_dischargesTheTube() {
        Photon p = atWeaponStatusTwo();

        p.applyAllocationEnergy(0.0, WeaponArmingType.STANDARD);

        assertEquals("arming starts again", 0, p.getArmingTurn());
        assertEquals("and the stored energy is gone", 0.0, p.getArmingEnergy(), 0.001);
        assertFalse(p.isArmed());
    }

    /** A tube that has been discharged can begin arming again from scratch. */
    @Test
    public void aDischargedTube_canStartOver() {
        Photon p = atWeaponStatusTwo();
        p.applyAllocationEnergy(0.0, WeaponArmingType.STANDARD);

        assertTrue(p.armWithEnergy(2));
        assertTrue(p.armWithEnergy(2));

        assertTrue(p.isArmed());
        assertEquals(4.0, p.getArmingEnergy(), 0.001);
    }

    // -------------------------------------------------------------------------
    // E4.412 — overloading a torpedo that is already in the tube
    // -------------------------------------------------------------------------

    private Photon loaded() {
        Photon p = fresh();
        p.armWithEnergy(2);
        p.armWithEnergy(2);
        return p;
    }

    /** Hold cost alone keeps it exactly as it is (E4.22). */
    @Test
    public void payingOnlyTheHold_leavesAStandardTorpedo() throws Exception {
        Photon p = loaded();

        assertTrue(p.holdAndOverload(1));

        assertEquals(4.0, p.getArmingEnergy(), 0.001);
        assertEquals(WeaponArmingType.STANDARD, p.getArmingType());
    }

    /** E4.412: the hold is paid first, and only what is left over overloads it. */
    @Test
    public void everythingAboveTheHoldOverloadsItInTheTube() throws Exception {
        Photon p = loaded();

        assertTrue(p.holdAndOverload(4));   // 1 to hold, 3 of overload

        assertEquals(7.0, p.getArmingEnergy(), 0.001);
        assertEquals("holding energy never counts toward the overload (E4.412)",
                3.0, p.overloadEnergy(), 0.001);
        assertEquals(14, (int) (p.getArmingEnergy() * 2));
        assertEquals(WeaponArmingType.OVERLOAD, p.getArmingType());
    }

    /** The whole dial a loaded standard torpedo offers: hold, or hold plus up to four. */
    @Test
    public void loadedTubeDialRunsFromEightToSixteenDamage() throws Exception {
        int[][] payToDamage = { {1, 8}, {2, 10}, {3, 12}, {4, 14}, {5, 16} };
        for (int[] row : payToDamage) {
            Photon p = loaded();
            assertTrue("paying " + row[0], p.holdAndOverload(row[0]));
            assertEquals("paying " + row[0], row[1], (int) (p.getArmingEnergy() * 2));
        }
    }

    /** E4.41: it cannot be pushed past 100%, however the overload was accumulated. */
    @Test
    public void aLoadedTubeCannotBeOverloadedPastFullStrength() throws Exception {
        Photon p = loaded();
        p.holdAndOverload(5);               // already a full 8-point overload
        assertEquals(8.0, p.getArmingEnergy(), 0.001);

        p.holdAndOverload(5);               // 2 to hold now, and no room for the rest

        assertEquals(8.0, p.getArmingEnergy(), 0.001);
    }

    /** E4.413: once overloaded it costs two a turn to hold, not one. */
    @Test
    public void anOverloadedTorpedoCostsMoreToHold() throws Exception {
        Photon p = loaded();
        assertEquals(1, p.holdEnergyCost());

        p.holdAndOverload(3);               // commit it

        assertEquals(2, p.holdEnergyCost());
    }

    /** Less than the holding cost does not hold it — the caller discharges it (E4.22). */
    @Test
    public void payingLessThanTheHold_doesNotHoldIt() throws Exception {
        Photon p = loaded();
        p.holdAndOverload(3);               // an overload, so holding now costs 2

        assertFalse(p.holdAndOverload(1));
    }

    /** E4.34: a proximity torpedo can be held but never overloaded. */
    @Test
    public void aProximityTorpedoInTheTubeTakesNoOverload() throws Exception {
        Photon p = fresh();
        p.setSpecial();
        p.armWithEnergy(2);
        p.armWithEnergy(2);
        assertTrue(p.isArmed());

        assertTrue(p.holdAndOverload(4));

        assertEquals(WeaponArmingType.SPECIAL, p.getArmingType());
        assertEquals("still just its arming energy", 4.0, p.getArmingEnergy(), 0.001);
    }
}
