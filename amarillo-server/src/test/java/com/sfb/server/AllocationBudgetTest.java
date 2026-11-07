package com.sfb.server;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * (D22.0) The server will not accept an allocation the ship cannot pay for.
 *
 * <h2>The hole this closes</h2>
 * Nothing on the server compared a form against the ship's power. The OVER BUDGET warning lived
 * only in the Energy Allocation dialog, so a modified — or simply buggy — client could allocate
 * whatever it liked and be believed. Movement was the lone exception, and still is: the ALLOCATE
 * handler rejects a speed the warp engines cannot output, which is why the largest single term
 * was never the exposure.
 *
 * <h2>Why the spend is a FLOOR</h2>
 * An exact total would mean restating the dialog's twenty-six rate calculations on this side,
 * and a single error there refuses a LEGAL form in the middle of somebody's turn — worse than
 * the gap being closed. {@code Energy.committedEnergyFloor} therefore sums only what Energy
 * holds at one-for-one with energy points and can only UNDER-count, so this catches the gross
 * case and never the honest one. ECM/ECCM, suicide-shuttle arming and weasel charging are held
 * elsewhere and are not counted yet.
 */
public class AllocationBudgetTest {

    /** A form well within the ship's power is accepted, which is the case that must not break. */
    @Test
    public void anOrdinaryFormIsAccepted() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);

        Energy e = modest(hood);
        assertNull(game.allocationOverspend(hood, e));
    }

    /**
     * The whole budget spent exactly is still within it. A ship at full power is the normal
     * state of a warship in a fight, not an edge case.
     */
    @Test
    public void spendingEverythingIsNotOverspending() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(power);

        assertNull(game.allocationOverspend(hood, e));
    }

    /**
     * The float artefact that started all of this: movement cost is stored as a truncated
     * decimal, so two thirds is 0.6666667 and thirty hexes costs 20.000001 rather than 20. A
     * strict comparison would refuse every such hull at full power — which is the same artefact
     * that had the dialog printing "23.0 / 23" and OVER BUDGET together.
     */
    @Test
    public void aTruncatedThirdDoesNotCountAsAnOverspend() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(30 * 0.6666667);      // 20.000001, the real data value

        assertTrue(30 * 0.6666667 > 20);
        assertTrue(20 < power);
        assertNull(game.allocationOverspend(hood, e));
    }

    /** Over by a real amount is refused, and the message says what the budget was. */
    @Test
    public void aFormBeyondTheShipsPowerIsRefused() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(power + 10);

        String refusal = game.allocationOverspend(hood, e);
        assertNotNull(refusal);
        assertTrue(refusal.contains("committed") && refusal.contains("available"));
    }

    /**
     * Battery draw is INCOME and raises the ceiling (D22.0): a ship may spend beyond its power
     * by exactly what it pulls from its batteries. Counting it as spend instead would make a
     * ship leaning on its batteries look like a cheat.
     */
    @Test
    public void batteryDrawRaisesTheBudget() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(power + 3);
        assertNotNull(game.allocationOverspend(hood, e));

        e.setBatteryDraw(3);
        assertNull(game.allocationOverspend(hood, e));
    }

    /** Recharging batteries is the other direction — it SPENDS ship power. */
    @Test
    public void batteryRechargeCountsAsSpend() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(power);
        e.setBatteryRecharge(4);

        assertNotNull(game.allocationOverspend(hood, e));
    }

    /** A null form is not an overspend — declareSelfDestruct submits an empty one. */
    @Test
    public void nothingAllocatedIsNotAnOverspend() {
        Game game = gameWithFedCa();
        assertNull(game.allocationOverspend(game.getShips().get(0), null));
        assertNull(game.allocationOverspend(game.getShips().get(0), new Energy()));
    }

    /**
     * The floor counts the terms Energy holds, and weapon arming is one of them — it is the
     * largest discretionary spend on most ships after movement.
     */
    @Test
    public void weaponArmingCountsTowardTheFloor() {
        Game game = gameWithFedCa();
        Ship hood = game.getShips().get(0);
        int power = hood.getPowerSystems().getTotalAvailablePower();

        Energy e = new Energy();
        e.setWarpMovement(power - 2);
        assertNull(game.allocationOverspend(hood, e));

        com.sfb.weapons.Weapon photon = hood.getWeapons().fetchAllWeapons().stream()
                .filter(w -> "Photon".equals(w.getType())).findFirst().orElse(null);
        if (photon == null)
            return;   // a hull without photons proves nothing here
        e.getArmingEnergy().put(photon, 8.0);

        assertNotNull(game.allocationOverspend(hood, e));
    }

    private static Energy modest(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        e.setActivateShields(ship.getActiveShieldCost());
        e.setWarpMovement(8);
        return e;
    }

    private static Game gameWithFedCa() {
        Game game = new Game();
        Ship hood = new Ship();
        hood.init(FederationShips.getFedCa());
        hood.setName("USS Hood");
        hood.setLocation(new com.sfb.properties.Location(10, 10));
        hood.setFacing(1);
        game.getShips().add(hood);
        return game;
    }
}
