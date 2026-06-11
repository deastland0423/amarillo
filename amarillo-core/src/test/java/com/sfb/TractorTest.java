package com.sfb;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systems.Energy;
import com.sfb.systems.Tractors;
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
        TurnTracker.reset();
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
    public void cleanUp_releasesAllUnitsResetsEnergy() {
        Tractors t = fed.getTractors();
        t.initForTurn(10);
        t.linkUnit(klingon);
        t.addNegativeTractorAccumulated(3);

        t.cleanUp();

        assertTrue(t.getTractoredUnits().isEmpty());
        assertFalse(klingon.isTractored());
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
}
