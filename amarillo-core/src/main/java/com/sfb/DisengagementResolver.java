package com.sfb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.utilities.MapUtils;

/**
 * Disengagement (C7.0): the ways a ship leaves the battle other than dying
 * in it. Currently implemented: disengagement by acceleration (C7.1, with
 * per-team destruction directions) and by separation (C7.2). Future homes:
 * disengagement by sublight evasion (C7.3) and nimble-ship options.
 *
 * Extracted from Game. The turn-boundary orchestration (deferring startTurn
 * until every pending accel confirmation is answered) stays in Game with the
 * rest of the turn machine; this class owns eligibility and resolution.
 */
class DisengagementResolver {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<Ship> destroyedShips;
    private final List<Ship> pendingAccelDisengage;
    private final Map<String, Set<String>> destructionDirectionsByTeam;
    private final TractorResolver tractorResolver;

    DisengagementResolver(Game game, List<Ship> ships, List<Seeker> seekers,
            List<Ship> destroyedShips, List<Ship> pendingAccelDisengage,
            Map<String, Set<String>> destructionDirectionsByTeam,
            TractorResolver tractorResolver) {
        this.game                        = game;
        this.ships                       = ships;
        this.seekers                     = seekers;
        this.destroyedShips              = destroyedShips;
        this.pendingAccelDisengage       = pendingAccelDisengage;
        this.destructionDirectionsByTeam = destructionDirectionsByTeam;
        this.tractorResolver             = tractorResolver;
    }

    // -------------------------------------------------------------------------
    // C7.1 — Disengagement by acceleration
    // -------------------------------------------------------------------------

    /**
     * Identify ships eligible for disengagement by acceleration (C7.1) and
     * queue them for player YES/NO confirmation. Called by Game.endTurn();
     * the next turn's EA is deferred until every queued ship is answered.
     */
    void queueAccelDisengageCandidates() {
        pendingAccelDisengage.clear();
        for (Ship ship : ships) {
            if (ship.getLocation() == null || ship.isDisengaged())
                continue;
            int originalWarp = ship.getPowerSystems().getOriginalWarp();
            if (originalWarp == 0)
                continue; // no warp engines
            int currentWarp = ship.getPowerSystems().getWarpEnginePower();
            int threshold = Math.min((int) Math.ceil(originalWarp * 0.5), 15);
            if (ship.getSpeed() >= ship.getMaxAccelerationSpeed() && currentWarp >= threshold)
                pendingAccelDisengage.add(ship);
        }
    }

    /**
     * Resolve one confirmed/declined accel disengagement (C7.1). Exiting via a
     * team's destruction direction destroys the ship instead of saving it.
     * Queue management and the deferred startTurn() live in
     * Game.confirmAccelDisengage().
     */
    String resolveAccelDisengage(Ship ship, boolean confirm) {
        if (!confirm)
            return ship.getName() + " remained in the battle";

        String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
        Set<String> badDirs = teamName != null
                ? destructionDirectionsByTeam.getOrDefault(teamName, new HashSet<>())
                : new HashSet<>();
        String exitDir = String.valueOf((char) ('A' + ((ship.getFacing() - 1) / 4)));
        tractorResolver.releaseAllLinksInvolving(ship); // G7.28
        if (badDirs.contains(exitDir)) {
            // Destroyed on the way out — objectives drop (before location clears)
            List<String> dropped = game.dropObjectivesFrom(ship, false);
            ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
            ship.setLocation(null);
            destroyedShips.add(ship);
            ships.remove(ship);
            game.refreshGameEnd();
            String msg = ship.getName() + " destroyed — disengaged by acceleration in direction " + exitDir
                    + " (destruction zone)";
            return dropped.isEmpty() ? msg : msg + "\n" + String.join("\n", dropped);
        }
        // Safe exit — any carried objectives are secured to this player (permanent)
        List<String> secured = game.secureObjectivesFor(ship);
        ship.setDisengaged(true);
        noteFledIfEarly(ship);
        ship.setLocation(null);
        game.releaseTiesToDeparted(ship, "target disengaged");
        String msg = ship.getName() + " has disengaged by acceleration (C7.1)";
        return secured.isEmpty() ? msg : msg + "\n" + String.join("\n", secured);
    }

    /** S2.20 A: a unit that disengages by end of Turn 2 forfeits its side's handicap. */
    private void noteFledIfEarly(Ship ship) {
        if (game.getCurrentTurn() <= 2 && ship.getOwner() != null)
            game.noteFledByTurn2(ship.getOwner().getTeamName());
    }

    // -------------------------------------------------------------------------
    // C7.2 — Disengagement by separation
    // -------------------------------------------------------------------------

    /**
     * Check whether the given ship currently qualifies for disengagement by
     * separation (C7.2):
     * no enemy ship within 50 hexes, and no in-flight seekers targeting it.
     */
    boolean canDisengageBySeparation(Ship ship) {
        if (ship.getLocation() == null || ship.isDisengaged())
            return false;
        for (Ship other : ships) {
            if (other == ship || game.isSameTeam(ship, other))
                continue;
            if (other.getLocation() == null)
                continue;
            if (MapUtils.getRange(ship, other) <= 50)
                return false;
        }
        for (Seeker s : seekers) {
            if (ship.equals(s.getTarget()))
                return false;
        }
        return true;
    }

    /**
     * Disengage by separation (C7.2) — player-initiated after confirming
     * eligibility.
     */
    ActionResult disengageBySeparation(Ship ship) {
        if (!canDisengageBySeparation(ship))
            return ActionResult.fail(ship.getName() + " does not meet separation disengagement conditions");
        List<String> secured = game.secureObjectivesFor(ship);
        ship.setDisengaged(true);
        noteFledIfEarly(ship);
        ship.setLocation(null);
        game.releaseTiesToDeparted(ship, "target disengaged");
        String msg = ship.getName() + " has disengaged by separation (C7.2)";
        return ActionResult.ok(secured.isEmpty() ? msg : msg + "\n" + String.join("\n", secured));
    }

    // -------------------------------------------------------------------------
    // Destruction directions (scenario-defined unsafe exits)
    // -------------------------------------------------------------------------

    /** The exit directions that would destroy the given ship's team (A–F). */
    List<String> getDestructionDirections(Ship ship) {
        String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
        if (teamName == null)
            return List.of();
        Set<String> dirs = destructionDirectionsByTeam.get(teamName);
        return dirs != null ? new ArrayList<>(dirs) : List.of();
    }
}
