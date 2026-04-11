package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;

/**
 * Transports boarding parties onto an enemy ship (D7.31).
 *
 * <p>Preconditions (Activity phase, range ≤ 5, lock-on, shields, transporter
 * energy) are enforced by {@link Game#performBoardingAction}.
 * Combat resolves at end of turn via {@link Game#performBoardingCombat}.
 */
public class BoardingActionCommand implements Command {

    private final Ship actingShip;
    private final Ship target;
    private final int  normal;
    private final int  commandos;

    public BoardingActionCommand(Ship actingShip, Ship target, int normal, int commandos) {
        this.actingShip = actingShip;
        this.target     = target;
        this.normal     = normal;
        this.commandos  = commandos;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.performBoardingAction(actingShip, target, normal, commandos);
    }
}
