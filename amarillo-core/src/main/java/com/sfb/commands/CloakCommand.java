package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;

/**
 * Activates a ship's cloaking device, beginning the fade-out sequence.
 * The cloak cost must have been paid during energy allocation this turn.
 */
public class CloakCommand implements Command {

    private final Ship ship;

    public CloakCommand(Ship ship) {
        this.ship = ship;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.cloak(ship);
    }
}
