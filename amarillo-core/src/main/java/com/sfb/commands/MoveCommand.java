package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;

/**
 * Command for all ship movement actions in the Movement segment.
 *
 * The UI creates one of these with the ship and the desired action, then
 * passes it to Game.execute(). In a multiplayer game, this object would be
 * serialized to JSON (shipName + action enum), sent to the server, and
 * reconstructed there before execution.
 */
public class MoveCommand implements Command {

    public enum Action { FORWARD, TURN_LEFT, TURN_RIGHT, SIDESLIP_LEFT, SIDESLIP_RIGHT }

    private final Ship   ship;
    private final Action action;

    public MoveCommand(Ship ship, Action action) {
        this.ship   = ship;
        this.action = action;
    }

    @Override
    public ActionResult execute(Game game) {
        switch (action) {
            case FORWARD:        return game.moveForward(ship);
            case TURN_LEFT:      return game.turnLeft(ship);
            case TURN_RIGHT:     return game.turnRight(ship);
            case SIDESLIP_LEFT:  return game.sideslipLeft(ship);
            case SIDESLIP_RIGHT: return game.sideslipRight(ship);
            default:             return ActionResult.fail("Unknown move action: " + action);
        }
    }
}
