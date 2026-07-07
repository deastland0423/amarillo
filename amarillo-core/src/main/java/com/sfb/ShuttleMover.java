package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.utilities.MovementUtil;

/**
 * Handles autonomous shuttle movement each impulse. Extracted from Game to keep
 * Game focused on state and action routing. Holds a direct reference to Game's
 * activeShuttles list so mutations are always visible to both sides.
 */
class ShuttleMover {

    private final Game game;
    private final List<Shuttle> activeShuttles;

    ShuttleMover(Game game, List<Shuttle> activeShuttles) {
        this.game           = game;
        this.activeShuttles = activeShuttles;
    }

    List<String> moveShuttles() {
        List<String> log = new ArrayList<>();
        int impulse = game.getCurrentImpulse();
        List<Shuttle> offMap = new ArrayList<>();

        for (Shuttle shuttle : activeShuttles) {
            if (shuttle.isPlayerControlled())
                continue;
            if (!MovementUtil.moveThisImpulse(impulse, shuttle.getSpeed()))
                continue;
            shuttle.goForward(game.getMapCols(), game.getMapRows());
            if (shuttle.getLocation() == null) {
                log.add("  Shuttle " + shuttle.getName() + " moved off the map");
                offMap.add(shuttle);
            }
        }
        activeShuttles.removeAll(offMap);
        return log;
    }
}
