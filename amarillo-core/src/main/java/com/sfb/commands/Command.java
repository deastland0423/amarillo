package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;

/**
 * A Command encapsulates a single player action.
 *
 * The UI (or eventually a network layer) creates a Command and passes it to
 * Game.execute(). Game validates it and applies it. The result travels back
 * the same way.
 *
 * Because a Command is a plain object with simple fields, it can be serialized
 * to JSON and sent over a WebSocket — that is the multiplayer hook.
 */
public interface Command {
    ActionResult execute(Game game);
}
