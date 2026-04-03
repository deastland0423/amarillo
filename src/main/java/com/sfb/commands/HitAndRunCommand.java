package com.sfb.commands;

import java.util.List;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.SystemTarget;

/**
 * Executes a Hit &amp; Run boarding raid from one ship against another.
 *
 * <p>Each element of {@code targetSystems} represents one boarding party
 * and the system it is targeting on the enemy ship.  The number of parties
 * sent is therefore {@code targetSystems.size()}.
 *
 * <p>The command validates range, shield passability, available boarding
 * parties, and transporter energy.  The acting ship's facing shield is
 * lowered automatically if needed (triggering the 8-impulse lockout).
 */
public class HitAndRunCommand implements Command {

    private final Ship              actingShip;
    private final Ship              target;
    private final List<SystemTarget> targetSystems;

    public HitAndRunCommand(Ship actingShip, Ship target, List<SystemTarget> targetSystems) {
        this.actingShip    = actingShip;
        this.target        = target;
        this.targetSystems = targetSystems;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.performHitAndRun(actingShip, target, targetSystems);
    }
}
