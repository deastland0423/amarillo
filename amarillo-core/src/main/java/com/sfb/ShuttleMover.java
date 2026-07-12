package com.sfb;

import java.util.ArrayList;
import java.util.List;

import java.util.Map;

import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.MovementUtil;

/**
 * Handles autonomous shuttle movement each impulse. Extracted from Game to keep
 * Game focused on state and action routing. Holds a direct reference to Game's
 * activeShuttles list so mutations are always visible to both sides.
 */
class ShuttleMover {

    private final Game game;
    private final List<Shuttle> activeShuttles;
    private final Map<Unit, Location> prevLocations;

    ShuttleMover(Game game, List<Shuttle> activeShuttles, Map<Unit, Location> prevLocations) {
        this.game           = game;
        this.activeShuttles = activeShuttles;
        this.prevLocations  = prevLocations;
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
        for (Shuttle gone : offMap)
            game.removeShuttleFromPlay(gone, "target left the map");

        // J1.621: shuttles under the special recovery procedure are pulled one
        // hex closer to their holder each impulse, and aboard on arrival. Runs
        // at Movement resolution so processMines() sees the pulled hexes
        // (J1.6223 — the shuttle can trigger mines, at the SHIP's speed).
        for (Shuttle shuttle : new ArrayList<>(activeShuttles)) {
            if (!shuttle.isBeingRecovered())
                continue;
            if (!(shuttle.getTractoringUnit() instanceof Ship)) {
                shuttle.setBeingRecovered(false); // link broke — procedure ends (J1.6221)
                continue;
            }
            Ship holder = (Ship) shuttle.getTractoringUnit();
            if (holder.getLocation() == null || shuttle.getLocation() == null) {
                shuttle.setBeingRecovered(false);
                continue;
            }
            // J1.6223: regarded as having the ship's speed in each hex it enters
            shuttle.setSpeed(holder.getSpeed());

            if (!shuttle.getLocation().equals(holder.getLocation())) {
                // Pull one hex closer: best legal adjacent hex (no planets or
                // asteroid hexes — J1.6223)
                int curRange = MapUtils.getRange(shuttle.getLocation(), holder.getLocation());
                Location best = null;
                int bestRange = curRange;
                for (int dir : new int[] { 1, 5, 9, 13, 17, 21 }) {
                    Location cand = MapUtils.getAdjacentHex(shuttle.getLocation(), dir,
                            game.getMapCols(), game.getMapRows());
                    if (cand == null || game.isPlanetHex(cand) || game.isAsteroidHex(cand))
                        continue;
                    int r = MapUtils.getRange(cand, holder.getLocation());
                    if (r < bestRange) {
                        bestRange = r;
                        best = cand;
                    }
                }
                if (best == null) {
                    log.add("  " + shuttle.getName() + " recovery blocked — no legal hex closer to "
                            + holder.getName() + " (J1.6223)");
                } else {
                    prevLocations.putIfAbsent(shuttle, shuttle.getLocation());
                    shuttle.setLocation(best);
                    log.add("  " + shuttle.getName() + " pulled one hex closer to "
                            + holder.getName() + " (J1.621)");
                }
            }

            // Aboard on the impulse it reaches the ship's hex — or held at
            // Range 0 until a bay hatch and box are available (J1.6213)
            if (shuttle.getLocation() != null
                    && shuttle.getLocation().equals(holder.getLocation())) {
                String boarded = game.completeRecovery(holder, shuttle);
                if (boarded != null)
                    log.add("  " + boarded);
                else
                    log.add("  " + shuttle.getName() + " holding at Range 0 — bay not ready (J1.6213)");
            }
        }

        // J3.13: a Wild Weasel only diverts seekers while within 35 hexes of the
        // ship it protects. Checked after all movement resolves — the separation
        // can come from the weasel drifting OR the protected ship moving away.
        for (Shuttle shuttle : new ArrayList<>(activeShuttles)) {
            if (!(shuttle instanceof WildWeaselShuttle))
                continue;
            Ship parent = ((WildWeaselShuttle) shuttle).getParentShip();
            if (parent == null || parent.getLocation() == null || shuttle.getLocation() == null)
                continue;
            if (MapUtils.getRange(parent, shuttle) > 35) {
                log.add("  Wild Weasel " + shuttle.getName() + " is more than 35 hexes from "
                        + parent.getName() + " — voided (J3.13)");
                game.voidWildWeasel(parent);
            }
        }
        return log;
    }
}
