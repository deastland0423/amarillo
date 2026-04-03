package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.systems.Energy;

/**
 * Command issued by a player to submit their energy allocation for a ship.
 * In a multiplayer context the server collects one AllocateEnergyCommand per
 * ship per player and only calls beginImpulses() once all ships are allocated.
 */
public class AllocateEnergyCommand implements Command {

    private final Ship   ship;
    private final Energy allocation;

    public AllocateEnergyCommand(Ship ship, Energy allocation) {
        this.ship       = ship;
        this.allocation = allocation;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.submitAllocation(ship, allocation);
    }
}
