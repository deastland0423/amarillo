package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Shuttle;

public class ShuttleMoveCommand implements Command {

    public enum Action { FORWARD, TURN_LEFT, TURN_RIGHT }

    private final Shuttle shuttle;
    private final Action  action;

    public ShuttleMoveCommand(Shuttle shuttle, Action action) {
        this.shuttle = shuttle;
        this.action  = action;
    }

    @Override
    public ActionResult execute(Game game) {
        switch (action) {
            case FORWARD:     return game.moveShuttleForward(shuttle);
            case TURN_LEFT:   return game.turnShuttleLeft(shuttle);
            case TURN_RIGHT:  return game.turnShuttleRight(shuttle);
            default:          return ActionResult.fail("Unknown shuttle action: " + action);
        }
    }
}
