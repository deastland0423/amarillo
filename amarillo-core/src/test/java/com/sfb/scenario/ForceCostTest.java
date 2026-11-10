package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.ForceCost;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Crew;

/**
 * (S3.21) Charges that count against the force total but escape the per-ship option cap — and
 * which (S2.14) only partly reach the Gross Adjusted BPV.
 *
 * <p>The category existed as one field named after its first member, {@code droneSpeedBpv}, for
 * as long as drone speed was the only charge in it. These tests pin the three things that were
 * true of that field and must stay true of the whole category, plus the one axis on which its
 * members turned out to DISAGREE.
 */
public class ForceCostTest {

    /** A hull with no drones, so nothing else is quietly charging it while a test looks away. */
    private static Ship plainHull() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        return ship;
    }

    // ------------------------------------------------------------------ the force total

    /**
     * (S3.21, M9.17) The whole category reaches the force total, not just the member it used to
     * be named after.
     *
     * <p>This is the actual regression guard: {@code FleetValidator.costOf} summed
     * {@code getDroneSpeedBpv()} by name, so a second member would have been bought for free.
     */
    @Test
    public void everyMemberOfTheCategoryIsInTheForceTotal() {
        for (ForceCost member : ForceCost.values()) {
            Ship ship = plainHull();
            double before = FleetValidator.costOf(ship);
            ship.setForceCost(member, 8);

            assertEquals(member + " must reach the force total (S3.21)",
                    before + 8, FleetValidator.costOf(ship), 1e-9);
        }
    }

    /** And the fleet-wide sum picks it up through the same door. */
    @Test
    public void aFleetPaysForTheCategoryToo() {
        Ship ship = plainHull();
        double before = FleetValidator.totalCost(List.of(ship));
        ship.setForceCost(ForceCost.RACK_MINES, 24); // three NSMs at 8 (Annex #6)

        assertEquals(before + 24, FleetValidator.totalCost(List.of(ship)), 1e-9);
    }

    // ------------------------------------------------------------------ out of the option cap

    /**
     * (S3.211) The option allowance is blind to all of it. The percentage is taken of "the ship,
     * its refits, and its fighters, but not including the cost of mandatory drone speed upgrades
     * or crew quality adjustments" — so a charge in this category must not inflate the very
     * budget it is excluded from.
     */
    @Test
    public void noMemberOfTheCategoryEnlargesTheOptionAllowance() {
        for (ForceCost member : ForceCost.values()) {
            Ship ship = plainHull();
            double before = CoiBudget.allowanceFor(ship);
            ship.setForceCost(member, 40); // large enough that 20% of it would show

            assertEquals(member + " must not reach the option basis (S3.211)",
                    before, CoiBudget.allowanceFor(ship), 1e-9);
            assertEquals(member + " must not reach the basis itself (S3.211)",
                    CoiBudget.effectiveAdjustedCombatBpv(plainHull()),
                    CoiBudget.effectiveAdjustedCombatBpv(ship), 1e-9);
        }
    }

    // ------------------------------------------------------------------ the GABPV split

    /**
     * (S2.14) The axis the members DISAGREE on, and the reason this is an enum rather than one
     * number.
     *
     * <p>The Gross Adjusted BPV "includes the cost of drone speed upgrades (FD2.22), the effect
     * of crew quality (G21.0), the value of Legendary Officers (G22.0), but does NOT include
     * Commander's Optional Purchases (S3.2)". M9.17 buys a minesweeper's mines under S3.2, so
     * rack mines are in the force total and out of the victory basis.
     */
    @Test
    public void s214PutsThreeMembersInTheVictoryBasisAndKeepsMinesOut() {
        assertTrue("drone speed is named by S2.14", ForceCost.DRONE_SPEED.countsInGabpv());
        assertTrue("crew quality is named by S2.14", ForceCost.CREW_QUALITY.countsInGabpv());
        assertTrue("Legendary Officers are named by S2.14",
                ForceCost.LEGENDARY_OFFICERS.countsInGabpv());
        assertEquals("rack mines are an S3.2 purchase, which S2.14 excludes (M9.17)",
                false, ForceCost.RACK_MINES.countsInGabpv());
    }

    /** The two sums are therefore different figures, and both are needed. */
    @Test
    public void theForceTotalAndTheVictoryBasisAreDifferentSubsets() {
        Ship ship = plainHull();
        ship.setForceCost(ForceCost.DRONE_SPEED, 8);
        ship.setForceCost(ForceCost.RACK_MINES, 16);

        assertEquals("the quartermaster pays for both", 24, ship.getForceCostTotal(), 1e-9);
        assertEquals("the enemy scores only what S2.14 lists", 8, ship.getGabpvForceCost(), 1e-9);
    }

    /**
     * And it reaches the scoreboard, which is the claim that matters: a loaded minelayer must not
     * be worth more to destroy than the chart says.
     *
     * <p>Asserted as a DIFFERENCE between two otherwise identical games, because the absolute
     * GABPV carries the hull, its fighters and anything else the basis picks up.
     */
    @Test
    public void rackMinesDoNotMakeAMinelayerWorthMoreToKill() throws Exception {
        int withoutMines = gabpvOf(0, 0);
        int withMines    = gabpvOf(0, 32);
        int withSpeed    = gabpvOf(32, 0);

        assertEquals("M9.17/S2.14: mines in a rack are not part of what killing her is worth",
                withoutMines, withMines);
        assertEquals("while drone speed IS (S2.14), so the fixture can tell the two apart",
                withoutMines + 32, withSpeed);
    }

    /** One ship, one game, one scoreboard row — its GABPV with the two charges applied. */
    private static int gabpvOf(double droneSpeed, double rackMines) {
        Game game = new Game();
        Ship ship = plainHull();
        Player player = new Player();
        player.setName("Federation");
        player.setTeamName("Federation");
        ship.setOwner(player);
        player.getPlayerUnits().add(ship);
        ship.setForceCost(ForceCost.DRONE_SPEED, droneSpeed);
        ship.setForceCost(ForceCost.RACK_MINES, rackMines);
        game.getShips().add(ship);

        for (Game.ShipVpRow row : game.calculateVictoryPoints().rows())
            if (ship.getName().equals(row.shipName()))
                return row.gabpv();
        throw new AssertionError("no scoreboard row for " + ship.getName());
    }

    // ------------------------------------------------------------------ the shape of a member

    /**
     * (Annex #6A) A member may be NEGATIVE: "Poor Crew ... −20%" is a discount, not a bill. The
     * field it replaced was an {@code int} and would have been a lie the day crew quality landed.
     */
    @Test
    public void aMemberMayBeNegativeAndFractional() {
        Ship ship = plainHull();
        double hull = FleetValidator.costOf(ship);
        ship.setForceCost(ForceCost.CREW_QUALITY, -19.6);

        assertEquals("a poor crew makes the ship CHEAPER (Annex #6A)",
                hull - 19.6, FleetValidator.costOf(ship), 1e-9);
    }

    /**
     * Setting a member REPLACES it, because every one of these is derived and may be re-derived:
     * {@code ScenarioLoader.applyCoi} re-prices the drone speed after the options have changed
     * the racks. Two calls are one answer computed twice, never two bills.
     */
    @Test
    public void settingAMemberTwiceIsNotTwoCharges() {
        Ship ship = plainHull();
        ship.setForceCost(ForceCost.DRONE_SPEED, 8);
        ship.setForceCost(ForceCost.DRONE_SPEED, 12);

        assertEquals(12, ship.getForceCost(ForceCost.DRONE_SPEED), 1e-9);
        assertEquals(12, ship.getForceCostTotal(), 1e-9);
    }

    /** An unset member owes nothing, rather than throwing or needing a null check at every use. */
    @Test
    public void anUnsetMemberOwesNothing() {
        Ship ship = plainHull();
        for (ForceCost member : ForceCost.values())
            assertEquals(member + " unset", 0, ship.getForceCost(member), 1e-9);
        assertEquals(0, ship.getForceCostTotal(), 1e-9);
        assertEquals(0, ship.getGabpvForceCost(), 1e-9);
    }

    /**
     * The old accessor still answers, because {@code ScenarioLoader}, {@code FleetValidator},
     * {@code Game} and a dozen tests call it by name. It is now a delegate onto DRONE_SPEED.
     */
    @Test
    public void theDroneSpeedAccessorIsNowADelegate() {
        Ship ship = plainHull();
        ship.setDroneSpeedBpv(8);

        assertEquals(8, ship.getForceCost(ForceCost.DRONE_SPEED), 1e-9);
        assertEquals(8, ship.getDroneSpeedBpv());

        ship.setForceCost(ForceCost.DRONE_SPEED, 11);
        assertEquals("and reads back through the old door too", 11, ship.getDroneSpeedBpv());
    }

    // ------------------------------------------------------------------ the penal-hull trap

    /**
     * (R3.R51) The trap this category must not fall into: a hull that DECLARES a poor crew has
     * already been discounted by its chart entry, and must not be discounted again.
     *
     * <p>Four Klingon penal hulls declare {@code crewQuality: "poor"} in their data, and
     * {@code d6j.json} records what that means: "epv 123, bpv 98, and the 98 is live here
     * BECAUSE crewQuality is poor." The E4J is 62/40 — a 35% gap where Annex #6A's rule would
     * give 20% — so the chart's split is not even the same number the rule would derive.
     *
     * <p>Hence: nothing may derive {@link ForceCost#CREW_QUALITY} from the declaration. A ship
     * run through the full year outfit with a poor crew owes nothing under it, and its BPV is
     * untouched. If someone later wires the obvious thing, this fails.
     */
    @Test
    public void aDeclaredPoorCrewIsNotChargedAgain() {
        Ship penal = new Ship();
        penal.init(KlingonShips.getD7());
        penal.setName("IKV Penal");
        penal.getCrew().setCrewQuality(Crew.CrewQuality.POOR);
        int statedBpv = penal.getBattlePointValue();

        ScenarioLoader.outfitForYear(penal, "klingon", 180, null);

        assertEquals("R3.R51: the discount is already in the stated BPV — do not derive it",
                0, penal.getForceCost(ForceCost.CREW_QUALITY), 1e-9);
        assertEquals("and the stated BPV stands", statedBpv, penal.getBattlePointValue());
    }

    /** The same for an outstanding crew, which nothing sells yet either. */
    @Test
    public void aDeclaredOutstandingCrewIsNotChargedEither() {
        Ship ship = plainHull();
        ship.getCrew().setCrewQuality(Crew.CrewQuality.OUTSTANDING);

        ScenarioLoader.outfitForYear(ship, "federation", 180, null);

        assertEquals("Annex #6A prices a crew a player BUYS, not one the hull came with",
                0, ship.getForceCost(ForceCost.CREW_QUALITY), 1e-9);
    }
}
