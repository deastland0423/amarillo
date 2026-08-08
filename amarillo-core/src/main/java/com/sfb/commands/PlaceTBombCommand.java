package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;

/**
 * Places a tBomb (real or dummy) on the map via transporter.
 *
 * <p>Validates the Activity phase, transporter range (≤ 5 hexes), shield
 * passability, transporter energy, and tBomb inventory.  The acting ship's
 * facing shield is lowered automatically if needed.
 */
public class PlaceTBombCommand implements Command {

    private final Ship     actingShip;
    private final Location targetHex;
    private final boolean  isReal;

    /**
     * @param actingShip The ship placing the tBomb.
     * @param targetHex  The hex where the tBomb will be placed.
     * @param isReal     True for a real tBomb, false for a dummy.
     */
    public PlaceTBombCommand(Ship actingShip, Location targetHex, boolean isReal) {
        this.actingShip = actingShip;
        this.targetHex  = targetHex;
        this.isReal     = isReal;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.placeTBomb(actingShip, targetHex, isReal);
    }
}
