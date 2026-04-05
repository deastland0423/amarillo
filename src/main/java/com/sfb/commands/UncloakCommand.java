package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;

/**
 * Deactivates a ship's cloaking device, beginning the fade-in sequence.
 */
public class UncloakCommand implements Command {

    private final Ship ship;

    public UncloakCommand(Ship ship) {
        this.ship = ship;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.uncloak(ship);
    }
}
