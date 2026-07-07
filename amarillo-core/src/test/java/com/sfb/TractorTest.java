package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import com.sfb.systemgroups.Tractors;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for tractor beam functionality (G7.0).
 *
 * Group 1: Tractors.java unit tests — direct system behaviour, no Game involved.
 * Group 2: Game.establishTractor() validation.
 * Group 3: Auction resolution via Game.submitNegativeTractorBid().
 * Group 4: Pseudo-speed computation for tractor-paired ships (G7.36).
 */
public class TractorTest {

    private Game  game;
    private Ship  fed;
    private Ship  klingon;

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(11, 10));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
    }

    // =========================================================================
    // GROUP 1 — Tractors.java unit tests
    // =========================================================================

    @Test
    public void damage_reducesAvailableTractors() {
        Tractors t = fed.getTractors();
        int before = t.getAvailableTractors();
        assertTrue(t.damage());
        assertEquals(before - 1, t.getAvailableTractors());
    }

    @Test
    public void damage_onZeroReturnsFalse() {
        Tractors t = fed.getTractors();
        // Damage all beams down to zero
        int total = t.getAvailableTractors();
        for (int i = 0; i < total; i++) t.damage();
        assertFalse(t.damage());
        assertEquals(0, t.getAvailableTractors());
    }

    @Test
    public void repair_increasesAvailableTractors() {
        Tractors t = fed.getTractors();
        t.damage(); // knock one out so there is something to repair
        int before = t.getAvailableTractors();
        assertTrue(t.repair());
        assertEquals(before + 1, t.getAvailableTractors());
    }

    @Test
    public void repair_beyondMaxReturnsFalse() {
        Tractors t = fed.getTractors();
        // No damage → nothing to repair
        assertFalse(t.repair());
        assertEquals(t.getTractors(), t.getAvailableTractors());
    }

    @Test
    public void initForTurn_setsBothEnergyFields() {
        Tractors t = fed.getTractors();
        t.initForTurn(7);
        assertEquals(7, t.getTotalTractorEnergy());
        assertEquals(7, t.getRemainingTractorEnergy());
    }

    @Test
    public void spendEnergy_deductsFromPool() {
        Tractors t = fed.getTractors();
        t.initForTurn(10);
        int leftOver = t.spendEnergy(4);
        assertEquals(0, leftOver);
        assertEquals(6, t.getRemainingTractorEnergy());
    }

    @Test
    public void spendEnergy_returnsRemainderWhenPoolInsufficient() {
        Tractors t = fed.getTractors();
        t.initForTurn(3);
        int leftOver = t.spendEnergy(5);
        assertEquals(2, leftOver);
        assertEquals(0, t.getRemainingTractorEnergy());
    }

    @Test
    public void linkUnit_incrementsUsedAndSetsTractored() {
        Tractors t = fed.getTractors();
        assertTrue(t.linkUnit(klingon));
        assertEquals(1, t.getTractoredUnits().size());
        assertTrue(klingon.isTractored());
    }

    @Test
    public void linkUnit_returnsFalseWhenNoBeamsFree() {
        Tractors t = fed.getTractors();
        // Exhaust all beams (FedCA has 3)
        int max = t.getAvailableTractors();
        // Use temporary ships as dummy targets to fill the slots
        for (int i = 0; i < max; i++) {
            Ship dummy = new Ship();
            dummy.init(FederationShips.getFedCa());
            dummy.setName("Dummy" + i);
            boolean linked = t.linkUnit(dummy);
            assertTrue("Should link dummy " + i, linked);
        }
        // Now all slots are occupied; next linkUnit must fail
        assertFalse(t.linkUnit(klingon));
    }

    @Test
    public void cleanUp_preservesLinksButResetsEnergy() {
        // G7.42: links persist across the turn boundary; only per-turn energy
        // resets. Unmaintained links are released at the NEXT turn's start
        // (TractorResolver.maintainLinksAtTurnStart), not here.
        Tractors t = fed.getTractors();
        t.initForTurn(10);
        t.linkUnit(klingon);
        t.addNegativeTractorAccumulated(3);

        t.cleanUp();

        assertEquals(1, t.getTractoredUnits().size());
        assertTrue(klingon.isTractored());
        assertEquals(0, t.getTotalTractorEnergy());
        assertEquals(0, t.getRemainingTractorEnergy());
        assertEquals(0, t.getNegativeTractorAccumulated());
    }

    @Test
    public void addAndGetNegativeTractorAccumulated() {
        Tractors t = klingon.getTractors();
        assertEquals(0, t.getNegativeTractorAccumulated());
        t.addNegativeTractorAccumulated(4);
        assertEquals(4, t.getNegativeTractorAccumulated());
        t.addNegativeTractorAccumulated(2);
        assertEquals(6, t.getNegativeTractorAccumulated());
    }

    // =========================================================================
    // GROUP 2 — Game.establishTractor() validation
    // =========================================================================

    /** Helper: give fed enough tractor energy, lock-on, and AFC to attempt a tractor. */
    private void readyFed(int tractorEnergy) {
        fed.getTractors().initForTurn(tractorEnergy);
        fed.addLockOn(klingon);
        fed.setActiveFireControl(true);
    }

    @Test
    public void establish_failsWhenNoBid() {
        readyFed(5);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 0);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_failsWhenBidExceedsPoolAndBattery() {
        // Pool = 2, battery starts at fed.battery (3 for FedCA); total available = 5.
        // Use bid of 6 to reliably exceed both.
        fed.getTractors().initForTurn(2);
        fed.getPowerSystems().setBatteryPower(0); // drain battery so only pool matters
        fed.addLockOn(klingon);
        fed.setActiveFireControl(true);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 3);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_failsWhenTargetIsSelf() {
        readyFed(5);
        Game.ActionResult r = game.establishTractor(fed, fed.getName(), 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_failsWhenTargetOutOfRange() {
        // Range 4 exceeds the max of 3
        klingon.setLocation(new Location(14, 10));
        readyFed(5);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_failsWhenNoLockOn() {
        fed.getTractors().initForTurn(5);
        fed.setActiveFireControl(true);
        // No addLockOn call
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_failsWhenNoActiveFireControl() {
        fed.getTractors().initForTurn(5);
        fed.addLockOn(klingon);
        fed.setActiveFireControl(false);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void establish_succeedsAndCreatesPendingAuction() {
        readyFed(5);
        assertNull(game.getPendingTractorAuction());

        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);

        assertTrue(r.isSuccess());
        assertNotNull(game.getPendingTractorAuction());
        assertEquals(fed,     game.getPendingTractorAuction().attacker);
        assertEquals(klingon, game.getPendingTractorAuction().target);
        assertEquals(2,       game.getPendingTractorAuction().attackerBid);
        assertEquals(1,       game.getPendingTractorAuction().rangeMultiplier);
    }

    // =========================================================================
    // GROUP 3 — Auction resolution
    // =========================================================================

    /**
     * Helper: establish a tractor auction with the given fed bid, then give klingon
     * tractor energy so it can counter.
     */
    private void openAuction(int fedBid, int klingonEnergy) {
        readyFed(fedBid + 5); // give fed enough pool for the bid
        game.establishTractor(fed, "IKV Saber", fedBid);
        klingon.getTractors().initForTurn(klingonEnergy);
    }

    // --- Attacker wins ---

    @Test
    public void auction_attackerWins_klingonIsTractored() {
        // Fed bids 5, klingon bids 2 → 5 > 2, attacker wins
        openAuction(5, 10);
        Game.ActionResult r = game.submitNegativeTractorBid(klingon, 2);
        assertTrue(r.isSuccess());
        assertTrue(klingon.isTractored());
        assertNull(game.getPendingTractorAuction());
    }

    @Test
    public void auction_attackerWins_fedSpendsDefenderPlusOne() {
        // Fed bids 5, klingon bids 2 → attacker spends 2+1 = 3
        openAuction(5, 10);
        int fedBefore = fed.getTractors().getRemainingTractorEnergy();
        game.submitNegativeTractorBid(klingon, 2);
        int fedAfter = fed.getTractors().getRemainingTractorEnergy();
        assertEquals(3, fedBefore - fedAfter);
    }

    @Test
    public void auction_attackerWins_klingonSpendsBid() {
        // Klingon bids 2 → klingon spends 2
        openAuction(5, 10);
        int klingBefore = klingon.getTractors().getRemainingTractorEnergy();
        game.submitNegativeTractorBid(klingon, 2);
        int klingAfter = klingon.getTractors().getRemainingTractorEnergy();
        assertEquals(2, klingBefore - klingAfter);
    }

    @Test
    public void auction_attackerWins_auctionCleared() {
        openAuction(5, 10);
        game.submitNegativeTractorBid(klingon, 2);
        assertNull(game.getPendingTractorAuction());
    }

    // --- Defender wins ---

    @Test
    public void auction_defenderWins_klingonNotTractored() {
        // Fed bids 3, klingon bids 4 → 4 >= 3, defender wins
        openAuction(3, 10);
        game.submitNegativeTractorBid(klingon, 4);
        assertFalse(klingon.isTractored());
    }

    @Test
    public void auction_defenderWins_fedSpendsFullBid() {
        // Fed bid 3 → fed spends 3
        openAuction(3, 10);
        int fedBefore = fed.getTractors().getRemainingTractorEnergy();
        game.submitNegativeTractorBid(klingon, 4);
        int fedAfter = fed.getTractors().getRemainingTractorEnergy();
        assertEquals(3, fedBefore - fedAfter);
    }

    @Test
    public void auction_defenderWins_klingonSpendsOnlyWhatNeeded() {
        // Fed bids 3, klingon has no accumulated → klingon needs to spend 3 to reach fed's bid
        // (defenderNeeded = max(0, 3 - 0) = 3; defenderSpend = min(4, 3) = 3)
        openAuction(3, 10);
        int klingBefore = klingon.getTractors().getRemainingTractorEnergy();
        game.submitNegativeTractorBid(klingon, 4);
        int klingAfter = klingon.getTractors().getRemainingTractorEnergy();
        assertEquals(3, klingBefore - klingAfter);
    }

    @Test
    public void auction_defenderWins_auctionCleared() {
        openAuction(3, 10);
        game.submitNegativeTractorBid(klingon, 4);
        assertNull(game.getPendingTractorAuction());
    }

    // --- Tie goes to defender ---

    @Test
    public void auction_tieGoesToDefender() {
        // Fed bids 3, klingon bids 3 → 3 >= 3, defender wins
        openAuction(3, 10);
        game.submitNegativeTractorBid(klingon, 3);
        assertFalse(klingon.isTractored());
    }

    // --- Accumulated negative-tractor energy carries over ---

    @Test
    public void auction_accumulatedHelpsInSubsequentAuction() {
        // Auction 1: fed bids 3, klingon bids 4 → defender wins, klingon accumulated = 3
        openAuction(3, 20);
        game.submitNegativeTractorBid(klingon, 4);
        assertFalse(klingon.isTractored());
        assertEquals(3, klingon.getTractors().getNegativeTractorAccumulated());

        // Auction 2: fed bids 4, klingon bids 0 new energy (total = 3+0 = 3 < 4) → attacker wins
        readyFed(10);
        game.establishTractor(fed, "IKV Saber", 4);
        game.submitNegativeTractorBid(klingon, 0);
        assertTrue(klingon.isTractored());
    }

    @Test
    public void auction_autoWin_defenderSpendsZeroWhenAlreadyCovered() {
        // Give klingon lots of accumulated energy from previous resistance
        klingon.getTractors().addNegativeTractorAccumulated(10);

        // Fed bids 4 → klingon accumulated (10) >= 4, so defender wins spending 0 new
        openAuction(4, 5);
        int klingBefore = klingon.getTractors().getRemainingTractorEnergy();
        game.submitNegativeTractorBid(klingon, 0);
        int klingAfter = klingon.getTractors().getRemainingTractorEnergy();

        assertFalse(klingon.isTractored());
        assertEquals(0, klingBefore - klingAfter); // no new energy spent
    }

    // =========================================================================
    // GROUP 4 — Pseudo-speed (G7.36)
    // =========================================================================

    /**
     * Helper: build a minimal Energy allocation for a ship with the given warp
     * movement amount and fire control paid.
     */
    private Energy makeAllocation(Ship ship, double warpMovement) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(warpMovement);
        return e;
    }

    @Test
    public void pseudoSpeed_tractorPairBothGetReducedSpeed() {
        // Both ships have movecost 1.0; combined = 2.0.
        // Fed allocates warp 20 → normal speed = 20/1.0 = 20; pseudo = floor(20/2.0) = 10.
        // Klingon allocates warp 20 → same calculation → pseudo = 10.

        // Establish tractor link directly (skip auction to avoid prerequisite complexity)
        fed.getTractors().initForTurn(5);
        fed.getTractors().linkUnit(klingon);

        // Submit allocations for both ships; last one triggers beginImpulses() which
        // calls computeTractorPseudoSpeeds()
        game.submitAllocation(fed,     makeAllocation(fed,     20.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 20.0));

        assertEquals(10, fed.getSpeed());
        assertEquals(10, klingon.getSpeed());
    }

    @Test
    public void pseudoSpeed_onlyCapReducedSpeed_notIncrease() {
        // Both ships allocate warp for speed 8; combined cost = 2.0.
        // Pseudo = floor(8/2.0) = 4 — slower than requested, so cap applies.
        fed.getTractors().initForTurn(5);
        fed.getTractors().linkUnit(klingon);

        game.submitAllocation(fed,     makeAllocation(fed,     8.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 8.0));

        assertTrue("Fed speed should be capped at pseudo-speed",     fed.getSpeed()     <= 4);
        assertTrue("Klingon speed should be capped at pseudo-speed", klingon.getSpeed() <= 4);
    }

    // =========================================================================
    // GROUP 5 — Extended range (G7.6)
    // =========================================================================

    @Test
    public void extendedRange_range2_succeedsAndSetsMultiplier() {
        // Range 2: (10,10) → (12,10)
        klingon.setLocation(new Location(12, 10));
        readyFed(6); // 6 pool / 2 multiplier = max effective 3
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertTrue(r.isSuccess());
        assertEquals(2, game.getPendingTractorAuction().rangeMultiplier);
        assertEquals(2, game.getPendingTractorAuction().attackerBid);
    }

    @Test
    public void extendedRange_range2_failsWhenEffectiveBidExceedsMax() {
        // Pool = 3 at range 2 → max effective = 1; bid 2 should fail
        klingon.setLocation(new Location(12, 10));
        readyFed(3);
        fed.getPowerSystems().setBatteryPower(0);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertFalse(r.isSuccess());
    }

    @Test
    public void extendedRange_range3_succeedsAndSetsMultiplier() {
        // Range 3: (10,10) → (13,10)
        klingon.setLocation(new Location(13, 10));
        readyFed(9); // 9 pool / 3 multiplier = max effective 3
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 2);
        assertTrue(r.isSuccess());
        assertEquals(3, game.getPendingTractorAuction().rangeMultiplier);
    }

    @Test
    public void extendedRange_range4_fails() {
        // Range 4 exceeds max of 3
        klingon.setLocation(new Location(14, 10));
        readyFed(10);
        Game.ActionResult r = game.establishTractor(fed, "IKV Saber", 1);
        assertFalse(r.isSuccess());
    }

    @Test
    public void extendedRange_range2_attackerWins_spendsDoubleEnergy() {
        // Range 2 bid: 2 effective → attacker pays (defenderTotal+1)*2 energy
        klingon.setLocation(new Location(12, 10));
        readyFed(6);
        fed.getPowerSystems().setBatteryPower(0);
        game.establishTractor(fed, "IKV Saber", 2); // pending auction, rangeMultiplier=2
        klingon.getTractors().initForTurn(0);

        // Defender bids 0 → defenderTotal = 0 < 2 → attacker wins
        // Attacker energy spend = (0+1)*2 = 2
        game.submitNegativeTractorBid(klingon, 0);

        assertTrue(klingon.isTractored());
        assertEquals(4, fed.getTractors().getRemainingTractorEnergy()); // 6 - 2 = 4
    }

    @Test
    public void extendedRange_range2_defenderWins_attackerSpendsDoubleEnergy() {
        // Range 2 bid: 1 effective → attacker pays 1*2 = 2 energy on loss
        klingon.setLocation(new Location(12, 10));
        readyFed(6);
        fed.getPowerSystems().setBatteryPower(0);
        game.establishTractor(fed, "IKV Saber", 1);
        klingon.getTractors().initForTurn(5);

        // Defender bids 1 → defenderTotal = 1 ≥ attackerBid 1 → defender wins
        // Attacker energy spend = 1*2 = 2
        game.submitNegativeTractorBid(klingon, 1);

        assertFalse(klingon.isTractored());
        assertEquals(4, fed.getTractors().getRemainingTractorEnergy()); // 6 - 2 = 4
    }

    // =========================================================================
    // GROUP 6 — Unit (non-Ship) targeting (G7.5)
    // =========================================================================

    private Drone makeDrone(String name, int col, int row) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        d.setLocation(new Location(col, row));
        game.getSeekers().add(d);
        return d;
    }

    private void readyFedForUnit(com.sfb.objects.Unit target, int tractorEnergy) {
        fed.getTractors().initForTurn(tractorEnergy);
        fed.addLockOn(target);
        fed.setActiveFireControl(true);
    }

    @Test
    public void unit_drone_tractored_immediately_noAuction() {
        Drone drone = makeDrone("Drone-1", 11, 10); // adjacent to fed at (10,10)
        readyFedForUnit(drone, 5);
        fed.getPowerSystems().setBatteryPower(0);

        Game.ActionResult r = game.establishTractor(fed, "Drone-1", 1);

        assertTrue(r.isSuccess());
        assertNull(game.getPendingTractorAuction());   // no auction created
        assertTrue(drone.isTractored());
        assertEquals(drone.getTractoringUnit(), fed);
    }

    @Test
    public void unit_drone_tractored_spends_one_energy_point() {
        Drone drone = makeDrone("Drone-1", 11, 10);
        readyFedForUnit(drone, 5);
        fed.getPowerSystems().setBatteryPower(0);

        game.establishTractor(fed, "Drone-1", 3); // bid 3 — but min 1 is spent

        assertEquals(4, fed.getTractors().getRemainingTractorEnergy()); // 5 - 1 = 4
    }

    @Test
    public void unit_drone_tractored_at_range2_spends_two_energy() {
        Drone drone = makeDrone("Drone-1", 12, 10); // range 2 from fed at (10,10)
        readyFedForUnit(drone, 6);
        fed.getPowerSystems().setBatteryPower(0);

        Game.ActionResult r = game.establishTractor(fed, "Drone-1", 1);

        assertTrue(r.isSuccess());
        assertEquals(4, fed.getTractors().getRemainingTractorEnergy()); // 6 - 2 = 4
    }

    // =========================================================================
    // GROUP 7 — Rotation (G7.7) — Initial Activity Phase
    // =========================================================================

    /**
     * Submit allocations for both ships so beginImpulses() fires. Links must be
     * established BEFORE calling this (mirroring real play, where they persist
     * from the previous turn): beginImpulses charges G7.42 maintenance on every
     * surviving link and only opens INITIAL_ACTIVITY if any link remains.
     */
    private void enterInitialActivity() {
        game.submitAllocation(fed,     makeAllocation(fed,     10.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 10.0));
        assertEquals(Game.ImpulsePhase.INITIAL_ACTIVITY, game.getCurrentPhase());
    }

    /** Link klingon into fed's tractor and give fed a tractor pool (pre-allocation). */
    private void linkKlingon(int fedTractorPool) {
        fed.getTractors().initForTurn(fedTractorPool);
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTractors().linkUnit(klingon);
    }

    @Test
    public void rotate_succeeds_movesTargetToAdjacentHex() {
        linkKlingon(5);
        enterInitialActivity(); // maintenance: −1 at range 1

        // (11,11) is adjacent to the klingon and range 1 from fed — cost 3
        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 11);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 11), klingon.getLocation());
    }

    @Test
    public void rotate_costsThreeEnergyAtRange1() {
        linkKlingon(5);
        enterInitialActivity(); // maintenance: −1 at range 1

        game.rotateTractored(fed, "IKV Saber", 11, 11); // stays at range 1

        assertEquals(1, fed.getTractors().getRemainingTractorEnergy()); // 5 - 1 - 3
    }

    @Test
    public void rotate_pushingFartherCostsNewRange() {
        // G7.711: the beam must cover the POST-rotation distance. (11,9) is
        // range 2 from fed at (10,10), so pushing the klingon there costs 3×2=6.
        linkKlingon(7);
        enterInitialActivity(); // maintenance: −1 at range 1

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 9), klingon.getLocation());
        assertEquals(0, fed.getTractors().getRemainingTractorEnergy()); // 7 - 1 - 6
    }

    @Test
    public void rotate_pullingCloserCostsNewRange() {
        // G7.712: pulling from range 2 in to range 1 costs only 3×1=3.
        klingon.setLocation(new Location(12, 10)); // range 2 from fed
        linkKlingon(5);
        enterInitialActivity(); // maintenance: −2 at range 2

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 10);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation());
        assertEquals(0, fed.getTractors().getRemainingTractorEnergy()); // 5 - 2 - 3
    }

    @Test
    public void rotate_failsOutsideInitialActivityPhase() {
        linkKlingon(5);
        enterInitialActivity();
        game.advancePhase(); // INITIAL_ACTIVITY → MOVEMENT

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertFalse(r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation()); // unmoved
    }

    @Test
    public void rotate_failsWhenNotTractoringTarget() {
        // Fed holds a drone (keeps the phase open) but NOT the klingon
        Drone drone = makeDrone("Drone-1", 10, 11);
        fed.getTractors().initForTurn(5);
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTractors().linkUnit(drone);
        enterInitialActivity();

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertFalse(r.isSuccess());
    }

    @Test
    public void rotate_failsOnSecondRotationSameTurn() {
        // G7.713 / G7.71: one rotation per unit per turn
        linkKlingon(10);
        enterInitialActivity();

        assertTrue(game.rotateTractored(fed, "IKV Saber", 11, 9).isSuccess());
        Game.ActionResult second = game.rotateTractored(fed, "IKV Saber", 11, 10);

        assertFalse(second.isSuccess());
        assertEquals(new Location(11, 9), klingon.getLocation()); // stayed at first dest
    }

    @Test
    public void rotate_failsWhenDestNotAdjacentToTarget() {
        // G7.711: destination must be exactly one hex from the held unit
        linkKlingon(10);
        enterInitialActivity();

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 7); // 3 hexes away

        assertFalse(r.isSuccess());
    }

    @Test
    public void rotate_failsWhenDestBeyondTractorRange() {
        // G7.714/G7.711: klingon at range 3; pushing it to range 4 would break the link
        klingon.setLocation(new Location(13, 10));
        linkKlingon(12); // ample energy — the range-4 gate must fire before any cost check
        enterInitialActivity(); // maintenance: −3 at range 3

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 14, 10);

        assertFalse(r.isSuccess());
        assertEquals(new Location(13, 10), klingon.getLocation());
    }

    @Test
    public void rotate_failsWhenInsufficientEnergy() {
        // Rotation to range 1 costs 3; pool of 2 (−1 maintenance) with no battery must fail
        linkKlingon(2);
        enterInitialActivity();

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 11);

        assertFalse(r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation());
    }

    @Test
    public void rotate_failsWhenTargetHeldByMultipleShips() {
        // G7.716: a unit held by two or more ships cannot be rotated
        Ship fed2 = new Ship();
        fed2.init(FederationShips.getFedCa());
        fed2.setName("USS Reliant");
        fed2.setLocation(new Location(12, 10));
        fed2.setSpeedPreviousTurn(31);
        fed2.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed2);
        game.startTurn(); // re-queue all three ships

        linkKlingon(10);
        fed2.getTractors().initForTurn(10);
        fed2.getTractors().linkUnit(klingon);

        game.submitAllocation(fed,     makeAllocation(fed,     10.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 10.0));
        game.submitAllocation(fed2,    makeAllocation(fed2,    10.0));
        assertEquals(Game.ImpulsePhase.INITIAL_ACTIVITY, game.getCurrentPhase());

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertFalse(r.isSuccess());
    }

    @Test
    public void rotate_drone_succeeds() {
        // G7.72: rotation applies to drones and shuttles, not just ships
        Drone drone = makeDrone("Drone-1", 10, 11); // adjacent to fed at (10,10)
        fed.getTractors().initForTurn(5);
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTractors().linkUnit(drone);
        enterInitialActivity(); // maintenance: −1 at range 1

        // (11,11) is adjacent to the drone and range 1 from fed — cost 3
        Game.ActionResult r = game.rotateTractored(fed, "Drone-1", 11, 11);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 11), drone.getLocation());
    }

    @Test
    public void rotate_dragPreservesRelativePosition_acrossColumnParity() {
        // Klingon (11,10, odd column) holds a drone at (12,9) — adjacent, direction 5.
        // Rotating the klingon in direction 9 to (12,10) must carry the drone one hex
        // in direction 9 as well: (12,9) is an EVEN column, so direction 9 lands on
        // (13,10), keeping it adjacent at the same relative bearing. A raw (dx,dy)
        // delta (+1,0) would drop it at (13,9), which is range 2 from the ship.
        Drone drone = makeDrone("Drone-1", 12, 9);
        linkKlingon(10);
        klingon.getTractors().initForTurn(5);
        klingon.getTractors().linkUnit(drone);
        enterInitialActivity(); // maintenance: fed −1, klingon −1

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 12, 10);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(12, 10), klingon.getLocation());
        assertEquals(new Location(13, 10), drone.getLocation());
        assertEquals(1, com.sfb.utilities.MapUtils.getRange(klingon.getLocation(), drone.getLocation()));
    }

    @Test
    public void rotate_shipCarriesItsTractoredDrones() {
        // G7.717: small units tractored by the rotated ship keep relative position
        Drone drone = makeDrone("Drone-1", 12, 10);
        linkKlingon(10);
        klingon.getTractors().initForTurn(5);
        klingon.getTractors().linkUnit(drone);
        enterInitialActivity(); // maintenance: fed −1, klingon −1

        // Rotate klingon (11,10) → (11,9): delta (0,-1); drone should follow to (12,9)
        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 9), klingon.getLocation());
        assertEquals(new Location(12, 9), drone.getLocation());
    }

    @Test
    public void rotate_heldDroneForcedIntoPlanet_isDestroyedNotStranded() {
        // G7.274: a held drone whose cascade destination is a planet hex is
        // destroyed (same fate as tractor-drag in moveForward), never silently
        // left behind at a now-illegal separation from its holder.
        Drone drone = makeDrone("Drone-1", 12, 10);
        game.addTerrain(new com.sfb.objects.Terrain(com.sfb.properties.TerrainType.PLANET, 12, 9));
        linkKlingon(10);
        klingon.getTractors().initForTurn(5);
        klingon.getTractors().linkUnit(drone);
        enterInitialActivity();

        // Rotate klingon (11,10) → (11,9), direction 1; drone (12,10) would go to (12,9) = planet
        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 9), klingon.getLocation());
        assertNull("Drone must be destroyed", drone.getLocation());
        assertFalse("Link must be released", drone.isTractored());
        assertFalse(game.getSeekers().contains(drone));
        assertTrue("Log must report the destruction", r.getMessage().contains("destroyed"));
    }

    @Test
    public void rotate_heldDroneForcedOffMap_isDestroyedNotStranded() {
        fed.setLocation(new Location(10, 2));
        klingon.setLocation(new Location(11, 2));
        Drone drone = makeDrone("Drone-1", 12, 1);
        linkKlingon(10);
        klingon.getTractors().initForTurn(5);
        klingon.getTractors().linkUnit(drone);
        enterInitialActivity();

        // Rotate klingon (11,2) → (11,1), direction 1; drone (12,1) would go to row 0 = off map
        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 1);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 1), klingon.getLocation());
        assertNull("Drone must be destroyed", drone.getLocation());
        assertFalse("Link must be released", drone.isTractored());
        assertFalse(game.getSeekers().contains(drone));
        assertTrue("Log must report the destruction", r.getMessage().contains("destroyed"));
    }

    @Test
    public void rotate_failsIntoPlanetHex() {
        // G7.715/G7.75: cannot rotate a unit into a planet hex
        game.addTerrain(new com.sfb.objects.Terrain(com.sfb.properties.TerrainType.PLANET, 11, 9));
        linkKlingon(10);
        enterInitialActivity();

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 9);

        assertFalse(r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation());
    }

    @Test
    public void rotate_failsOffMap() {
        fed.setLocation(new Location(2, 1));
        klingon.setLocation(new Location(1, 1));
        linkKlingon(10);
        enterInitialActivity();

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 1, 0); // row 0 = off map

        assertFalse(r.isSuccess());
        assertEquals(new Location(1, 1), klingon.getLocation());
    }

    @Test
    public void rotate_allowedAgainOnNewTurn_linkPersists() {
        // G7.71: one rotation per unit per turn — resets at the next turn. The link
        // itself persists across the boundary (G7.42) as long as maintenance is paid.
        linkKlingon(10);
        enterInitialActivity(); // maintenance: −1 at range 1
        assertTrue(game.rotateTractored(fed, "IKV Saber", 11, 9).isSuccess());

        // New turn: link persisted; fresh pool allocated; maintenance −2 (now range 2)
        fed.getTractors().initForTurn(10);
        game.startTurn();
        enterInitialActivity();
        assertTrue("Link must survive the turn boundary", klingon.isTractored());

        Game.ActionResult r = game.rotateTractored(fed, "IKV Saber", 11, 10);

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals(new Location(11, 10), klingon.getLocation());
    }

    @Test
    public void rotate_intoMineRadius_triggersDetection() {
        // Rotation must record prevLocations so processMines() sees the rotated
        // unit as having MOVED into the mine's trigger radius — otherwise the
        // classic "rotate the anchored enemy onto your T-bomb" play never works.
        com.sfb.objects.SpaceMine mine = com.sfb.objects.SpaceMine.createTBomb(fed, 0, true, false);
        mine.setLocation(new Location(11, 8));
        mine.tryActivate(2, 9); // timer + range met — force-arm before the turn
        game.getMines().add(mine);

        linkKlingon(10);
        // High warp → pseudo-speed 10 for both ships; speed ≥ 6 makes tBomb
        // detection automatic (no die roll), keeping the test deterministic.
        game.submitAllocation(fed,     makeAllocation(fed,     20.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 20.0));
        assertEquals(Game.ImpulsePhase.INITIAL_ACTIVITY, game.getCurrentPhase());

        // Rotate klingon (11,10) → (11,9): from range 2 to range 1 of the mine
        assertTrue(game.rotateTractored(fed, "IKV Saber", 11, 9).isSuccess());

        game.advancePhase(); // INITIAL_ACTIVITY → MOVEMENT (pure transition)
        Game.ActionResult moveEnd = game.advancePhase(); // MOVEMENT: seekers + mines resolve

        assertTrue("Mine must detect the rotated ship, got:\n" + moveEnd.getMessage(),
                moveEnd.getMessage().contains("TRIGGERED"));
    }

    // =========================================================================
    // GROUP 8 — Turn-boundary maintenance and auto-skip (G7.42, simplified)
    // =========================================================================

    @Test
    public void maintenance_paidAtTurnStart_linkPersists() {
        linkKlingon(5);
        enterInitialActivity();

        assertTrue(klingon.isTractored());
        assertEquals(4, fed.getTractors().getRemainingTractorEnergy()); // 5 - 1 maintenance
    }

    @Test
    public void maintenance_unpaid_linkReleasedAndPhaseSkipped() {
        linkKlingon(0); // no pool, battery zeroed by helper

        game.submitAllocation(fed,     makeAllocation(fed,     10.0));
        game.submitAllocation(klingon, makeAllocation(klingon, 10.0));

        assertFalse("Unmaintained link must be released (G7.42)", klingon.isTractored());
        // With the only link gone, the empty Initial Activity Phase is skipped
        assertEquals(Game.ImpulsePhase.MOVEMENT, game.getCurrentPhase());
    }

    @Test
    public void maintenance_atRange2_costsTwo() {
        klingon.setLocation(new Location(12, 10)); // range 2 from fed
        linkKlingon(5);
        enterInitialActivity();

        assertTrue(klingon.isTractored());
        assertEquals(3, fed.getTractors().getRemainingTractorEnergy()); // 5 - 2 (G7.6)
    }

    @Test
    public void advancePastInitialActivity_isPurePhaseTransition() {
        linkKlingon(5);
        enterInitialActivity();
        int absoluteDuringPhase = game.getClock().getImpulse();

        game.advancePhase(); // INITIAL_ACTIVITY → MOVEMENT

        assertEquals(absoluteDuringPhase, game.getClock().getImpulse()); // no second advance
        assertEquals(Game.ImpulsePhase.MOVEMENT, game.getCurrentPhase());
    }
}
