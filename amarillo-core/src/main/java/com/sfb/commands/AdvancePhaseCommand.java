package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;

/**
 * Command issued by a player to advance the game to the next phase.
 * In a multiplayer context the server will only execute this once all
 * players have submitted it for the current phase.
 */
public class AdvancePhaseCommand implements Command {

    @Override
    public ActionResult execute(Game game) {
        return game.advancePhase();
    }
}
