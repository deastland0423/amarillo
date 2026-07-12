package com.sfb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.Game.ActionResult;
import com.sfb.Game.PendingDamage;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.properties.Location;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.MovementUtil;

/**
 * Voluntary movement for ships, fighters, and player-controlled shuttles:
 * forward movement with tractor drag coupling (G7.36/G7.5), turns and
 * sideslips, HETs with breakdown (C6.5), tactical maneuvers (C5.0), emergency
 * deceleration (C8.0), asteroid collisions (P3.2), planet blocking (P2.0),
 * and map-edge disengagement. Extracted from Game to keep Game focused on
 * state and turn sequencing.
 *
 * Movement is the hub domain: it calls into TractorResolver (linked-ship
 * drag, link release on disengagement) and SeekerMover (collision checks),
 * and reports destruction/disengagement to Game via refreshGameEnd().
 */
class ShipMover {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;
    private final Set<Ship> movedThisImpulse;
    private final Map<Unit, Location> prevLocations;
    private final Set<com.sfb.objects.shuttles.Shuttle> movedShuttlesThisImpulse;
    private final List<Ship> destroyedShips;
    private final Map<String, Set<String>> destructionEdgesByTeam;
    private final List<PendingDamage> pendingInternalDamage;
    private final TractorResolver tractorResolver;
    private final SeekerMover seekerMover;

    ShipMover(Game game, List<Ship> ships, List<Seeker> seekers,
            List<com.sfb.objects.shuttles.Shuttle> activeShuttles,
            Set<Ship> movedThisImpulse, Map<Unit, Location> prevLocations,
            Set<com.sfb.objects.shuttles.Shuttle> movedShuttlesThisImpulse,
            List<Ship> destroyedShips, Map<String, Set<String>> destructionEdgesByTeam,
            List<PendingDamage> pendingInternalDamage,
            TractorResolver tractorResolver, SeekerMover seekerMover) {
        this.game = game;
        this.ships = ships;
        this.seekers = seekers;
        this.activeShuttles = activeShuttles;
        this.movedThisImpulse = movedThisImpulse;
        this.prevLocations = prevLocations;
        this.movedShuttlesThisImpulse = movedShuttlesThisImpulse;
        this.destroyedShips = destroyedShips;
        this.destructionEdgesByTeam = destructionEdgesByTeam;
        this.pendingInternalDamage = pendingInternalDamage;
        this.tractorResolver = tractorResolver;
        this.seekerMover = seekerMover;
    }

    /**
     * Returns all ships that may move on the current impulse and have not yet
     * moved this impulse.
     */
    public List<Ship> getMovableShips() {
        int impulse = game.getCurrentImpulse();
        List<Ship> movable = new ArrayList<>();
        for (Ship ship : ships) {
            if (ship.movesThisImpulse(impulse) && !movedThisImpulse.contains(ship)) {
                movable.add(ship);
            }
        }
        // Slower ships move first; ties broken by worst turn mode first (F > E > ... >
        // AA).
        movable.sort(Comparator.comparingInt(Ship::getSpeed)
                .thenComparingInt(s -> -s.getTurnMode().ordinal()));
        return movable;
    }

    public boolean hasMovedThisImpulse(Ship ship) {
        return movedThisImpulse.contains(ship);
    }

    public boolean canMoveThisImpulse(Ship ship) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return false;
        if (!ship.movesThisImpulse(game.getCurrentImpulse()))
            return false;
        if (movedThisImpulse.contains(ship))
            return false;
        // Enforce order: ship may only move if no higher-priority ship is still waiting
        List<Ship> movable = getMovableShips();
        return movable.isEmpty() || movable.get(0) == ship;
    }

    /** Returns the ship that must move next, or null if none need to move. */
    public Ship nextMovableShip() {
        List<Ship> movable = getMovableShips();
        return movable.isEmpty() ? null : movable.get(0);
    }

    private ActionResult moveOrderError(Ship ship) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return ActionResult.fail("Not the movement phase (current: " + game.getCurrentPhase().getLabel() + ")");
        if (movedThisImpulse.contains(ship))
            return ActionResult.fail(ship.getName() + " has already moved this impulse");
        if (!ship.movesThisImpulse(game.getCurrentImpulse()))
            return ActionResult.fail(ship.getName() + " does not move on impulse " + game.getCurrentImpulse());
        Ship first = nextMovableShip();
        if (first != null && first != ship)
            return ActionResult.fail("Move " + first.getName() + " first (speed " + first.getSpeed() + ")");
        return ActionResult.fail(ship.getName() + " cannot move this impulse");
    }

    /**
     * Announce emergency deceleration (C8.0). Ship stops at the end of the
     * 2nd subsequent impulse's movement segment. Must be announced during the
     * Activity phase (Impulse Activity Segment per C8.10).
     */
    public ActionResult emergencyDeceleration(Ship ship) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.DIRECT_FIRE
                && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Emergency deceleration must be announced during the Activity phase (C8.10)");
        if (ship.isDecelerating())
            return ActionResult.fail(ship.getName() + " has already announced emergency deceleration");
        if (ship.getSpeed() == 0)
            return ActionResult.fail(ship.getName() + " is already stopped");
        if (ship.isImmobile(game.getAbsoluteImpulse()))
            return ActionResult.fail(ship.getName() + " is in the post-deceleration period");
        ship.announceEmergencyDeceleration(game.getAbsoluteImpulse());
        return ActionResult.ok(ship.getName() + " announces emergency deceleration — stops at end of impulse "
                + ship.getDecelerationEndsAtImpulse());
    }

    public ActionResult moveForward(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        int moveDir = MapUtils.getTrueBearing(1, ship.getFacing());
        // Planet blocking — check destination before moving (P2.0)
        Location nextHex = MapUtils.getAdjacentHex(ship.getLocation(), moveDir, game.getMapCols(), game.getMapRows());

        // G7.36: pre-validate linked ships — refuse if any would be dragged into a
        // planet
        List<Ship> linked = tractorResolver.getTractorLinkedShips(ship);
        for (Ship s : linked) {
            Location sNext = MapUtils.getAdjacentHex(s.getLocation(), moveDir, game.getMapCols(), game.getMapRows());
            if (sNext != null && game.isPlanetHex(sNext))
                return ActionResult.fail(s.getName() + " cannot be tractor-dragged into a planet (G7.36)");
        }

        if (nextHex == null) {
            // Determine which edge the ship is exiting
            Location cur = ship.getLocation();
            String exitEdge;
            if (cur.getX() <= 1)
                exitEdge = "LEFT";
            else if (cur.getX() >= game.getMapCols())
                exitEdge = "RIGHT";
            else if (cur.getY() <= 1)
                exitEdge = "TOP";
            else
                exitEdge = "BOTTOM";

            String teamName = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
            Set<String> teamEdges = teamName != null ? destructionEdgesByTeam.getOrDefault(teamName, new HashSet<>())
                    : new HashSet<>();
            tractorResolver.releaseAllLinksInvolving(ship); // G7.28/G7.273
            if (teamEdges.contains(exitEdge)) {
                // Destruction edge — ship is destroyed, not disengaged
                ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
                ship.setLocation(null);
                destroyedShips.add(ship);
                ships.remove(ship);
                game.refreshGameEnd();
                return ActionResult.ok(ship.getName() + " has been destroyed (exited a destruction edge)");
            }

            // Safe edge — mark as disengaged and remove from play
            ship.setDisengaged(true);
            ship.setLocation(null);
            movedThisImpulse.add(ship);
            return ActionResult.ok(ship.getName() + " has disengaged (exited the map)");
        }
        if (game.isPlanetHex(nextHex)) {
            // Ship collides with planet — destroyed (P2.0)
            tractorResolver.releaseAllLinksInvolving(ship);
            ship.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);
            ship.setLocation(null);
            destroyedShips.add(ship);
            ships.remove(ship);
            movedThisImpulse.add(ship);
            game.refreshGameEnd();
            return ActionResult.ok(ship.getName() + " collided with a planet — ship destroyed (P2.0)");
        }
        com.sfb.properties.Location prevLoc = ship.getLocation();
        boolean moved = ship.goForward(game.getMapCols(), game.getMapRows());
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLoc);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " moved forward");
            if (game.isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            List<String> collisions = seekerMover.checkSeekerCollisions(ship);
            if (!collisions.isEmpty())
                log.append("\n").append(String.join("\n", collisions));

            // G7.36: drag tractor-linked ships in the mover's direction;
            // do NOT add them to movedThisImpulse so they can move on their own impulse
            for (Ship s : linked) {
                Location sPrev = s.getLocation();
                Location sNext = MapUtils.getAdjacentHex(s.getLocation(), moveDir, game.getMapCols(),
                        game.getMapRows());
                if (sNext == null) {
                    s.setDisengaged(true);
                    s.setLocation(null);
                    log.append("\n").append(s.getName()).append(" dragged off map — disengaged");
                } else {
                    s.dragForwardInDirection(moveDir, game.getMapCols(), game.getMapRows());
                    prevLocations.putIfAbsent(s, sPrev);
                    log.append("; ").append(s.getName()).append(" towed");
                    if (game.isAsteroidHex(s.getLocation()))
                        log.append("\n").append(applyAsteroidCollision(s));
                    List<String> sColl = seekerMover.checkSeekerCollisions(s);
                    if (!sColl.isEmpty())
                        log.append("\n").append(String.join("\n", sColl));
                }
            }

            // G7.5: drag tractored drones and shuttles in the same direction
            dragHeldSmallUnits(ship, moveDir, log);
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " could not move forward");
    }

    /**
     * G7.5: drag every tractored drone/shuttle one hex in the given direction.
     * A shuttle towed faster than twice its rated maximum speed is
     * death-dragged — destroyed in the hex it occupied before the movement
     * (G7.54/G7.541); crippled shuttles die at twice their crippled max
     * (G7.542). Drones cannot be death-dragged (G7.53). Fighter HET breakaway
     * (G7.543/G7.55) is not yet implemented.
     */
    /** A dragged small unit dies: seekers exit via the central path; plain shuttles locally. */
    private void removeHeldUnitFromPlay(com.sfb.objects.Unit held) {
        if (held instanceof com.sfb.objects.Seeker) {
            game.removeSeekerFromPlay((com.sfb.objects.Seeker) held);
        } else {
            held.setLocation(null);
            activeShuttles.removeIf(sh -> sh == held);
        }
    }

    private void dragHeldSmallUnits(Ship ship, int moveDir, StringBuilder log) {
        if (ship.getTractors() == null)
            return;
        for (com.sfb.objects.Unit held : new ArrayList<>(ship.getTractors().getTractoredUnits())) {
            if (held instanceof Ship)
                continue;
            if (held instanceof com.sfb.objects.shuttles.Shuttle) {
                com.sfb.objects.shuttles.Shuttle hs = (com.sfb.objects.shuttles.Shuttle) held;
                int rated = hs.isCrippled() ? (int) Math.ceil(hs.getMaxSpeed() / 2.0) : hs.getMaxSpeed();
                if (ship.getSpeed() > 2 * rated) {
                    ship.getTractors().releaseTractor(held);
                    removeHeldUnitFromPlay(held);
                    log.append("\n").append(held.getName())
                            .append(" death-dragged at speed ").append(ship.getSpeed())
                            .append(" (max safe tow ").append(2 * rated)
                            .append(") — destroyed (G7.54)");
                    continue;
                }
            }
            Location heldPrev = held.getLocation();
            Location heldNext = MapUtils.getAdjacentHex(held.getLocation(), moveDir,
                    game.getMapCols(), game.getMapRows());
            if (heldNext == null || game.isPlanetHex(heldNext)) {
                // Links persist across turns now — release explicitly so the
                // dead unit doesn't occupy a beam or hold the rotation phase open
                ship.getTractors().releaseTractor(held);
                removeHeldUnitFromPlay(held);
                log.append("\n").append(held.getName())
                        .append(heldNext == null ? " dragged off map — destroyed" : " dragged into planet — destroyed");
            } else {
                held.dragForwardInDirection(moveDir, game.getMapCols(), game.getMapRows());
                prevLocations.putIfAbsent(held, heldPrev);
                log.append("; ").append(held.getName()).append(" towed");
            }
        }
    }

    /**
     * G7.36: the tractor link is rigid — any hex displacement by one linked
     * ship displaces every linked unit one hex in the same absolute direction.
     * Facing changes never propagate (no swinging the tractored ship around;
     * only G7.7 rotation changes the formation's geometry).
     */
    private void dragLinkedShips(Ship ship, int dir, StringBuilder log) {
        for (Ship s : tractorResolver.getTractorLinkedShips(ship)) {
            Location sPrev = s.getLocation();
            s.dragSideslipInDirection(dir, game.getMapCols(), game.getMapRows());
            prevLocations.putIfAbsent(s, sPrev);
            log.append("; ").append(s.getName()).append(" towed");
            if (game.isAsteroidHex(s.getLocation()))
                log.append("\n").append(applyAsteroidCollision(s));
        }
    }

    public ActionResult turnLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLoc = ship.getLocation();
        boolean moved = ship.turnLeft();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLoc);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " turned left");
            if (game.isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // A turn displaces one hex in the NEW facing — the rigid link follows
            int turnDir = MapUtils.getTrueBearing(1, ship.getFacing());
            dragLinkedShips(ship, turnDir, log);
            dragHeldSmallUnits(ship, turnDir, log);
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLoc = ship.getLocation();
        boolean moved = ship.turnRight();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLoc);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " turned right");
            if (game.isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // A turn displaces one hex in the NEW facing — the rigid link follows
            int turnDir = MapUtils.getTrueBearing(1, ship.getFacing());
            dragLinkedShips(ship, turnDir, log);
            dragHeldSmallUnits(ship, turnDir, log);
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLocSl = ship.getLocation();
        boolean moved = ship.sideslipLeft();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLocSl);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " sideslipped left");
            if (game.isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // G7.36: drag tractor-linked ships in the same sideslip direction
            int slDir = MapUtils.getTrueBearing(21, ship.getFacing());
            dragLinkedShips(ship, slDir, log);
            // G7.5: held drones and shuttles follow sideslips too
            dragHeldSmallUnits(ship, slDir, log);
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return moveOrderError(ship);
        com.sfb.properties.Location prevLocSr = ship.getLocation();
        boolean moved = ship.sideslipRight();
        if (moved) {
            prevLocations.putIfAbsent(ship, prevLocSr);
            movedThisImpulse.add(ship);
            StringBuilder log = new StringBuilder(ship.getName() + " sideslipped right");
            if (game.isAsteroidHex(ship.getLocation()))
                log.append("\n").append(applyAsteroidCollision(ship));
            // G7.36: drag tractor-linked ships in the same sideslip direction
            int srDir = MapUtils.getTrueBearing(5, ship.getFacing());
            dragLinkedShips(ship, srDir, log);
            // G7.5: held drones and shuttles follow sideslips too
            dragHeldSmallUnits(ship, srDir, log);
            return ActionResult.ok(log.toString());
        }
        return ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    /**
     * Attempt a High Energy Turn (C6.0). The ship snaps to a new facing,
     * spending reserve warp energy and rolling for possible breakdown (C6.5).
     *
     * @param ship           The acting ship.
     * @param absoluteFacing New facing (0–5).
     */
    public ActionResult performHet(Ship ship, int absoluteFacing) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return ActionResult.fail("HETs can only be performed during the Movement phase");
        if (ship.isCaptured())
            return ActionResult.fail("Captured ships cannot perform HETs (D7.55)");

        // Note: cloaked ships CAN HET; docked ships cannot, but docking is not yet
        // implemented.

        int currentImpulse = game.getAbsoluteImpulse();

        // C6.37: cannot HET on impulse 1
        if (currentImpulse == 1)
            return ActionResult.fail("HETs cannot be performed on impulse 1 (C6.37)");

        // Breakdown immobility check
        if (ship.isImmobile(currentImpulse))
            return ActionResult.fail(ship.getName() + " is immobile until impulse "
                    + ship.getImmobileUntilImpulse() + " (breakdown)");

        // G9.421: skeleton crew requires a second crew unit to perform a HET
        if (ship.getCrew().isSkeleton() && ship.getCrew().getAvailableCrewUnits() < 2)
            return ActionResult.fail(ship.getName() + " is on skeleton crew with only "
                    + ship.getCrew().getAvailableCrewUnits()
                    + " crew unit(s) — a second crew unit is required for HET (G9.421)");

        // C6.36: 4-impulse gap between HETs
        int gap = currentImpulse - ship.getLastHetImpulse();
        if (gap < 4)
            return ActionResult.fail("Must wait at least 4 impulses between HETs — "
                    + (4 - gap) + " impulse(s) remaining (C6.36)");

        // C6.34: max 4 HETs per turn
        if (ship.getHetsThisTurn() >= 4)
            return ActionResult.fail("Maximum 4 HETs per turn reached (C6.34)");

        // C6.2: costs reserve warp energy
        int hetCost = (int) Math.ceil(ship.getPerformanceData().getHetCost());
        if (!ship.getPowerSystems().useReserveWarp(hetCost))
            return ActionResult.fail("Not enough reserve warp power for HET — need "
                    + hetCost + ", have " + ship.getPowerSystems().getReserveWarp() + " (C6.2)");

        // Update tracking before the roll so breakdown log has accurate values
        ship.setLastHetImpulse(currentImpulse);
        ship.incrementHetsThisTurn();

        int breakdownRoll = ship.rollAndPerformHet(absoluteFacing);
        boolean success = breakdownRoll < ship.getPerformanceData().getBreakdownChance();
        StringBuilder log = new StringBuilder();

        if (success) {
            log.append(ship.getName()).append(" HET → facing ").append(absoluteFacing)
                    .append(" (roll: ").append(breakdownRoll).append(")");
        } else {
            // Breakdown: apply effects and queue 2 internal DAC hits
            int internalHits = ship.applyBreakdown(currentImpulse);
            for (int i = 0; i < internalHits; i++)
                pendingInternalDamage.add(new PendingDamage(ship, 1));
            log.append(ship.getName())
                    .append(" BREAKDOWN during HET! (roll: ").append(breakdownRoll)
                    .append(") Speed→0, random facing, immobile for 16 impulses,")
                    .append(" crew -1/3, warp -1/5, 2 internal DAC hits pending.");
        }

        List<String> result = new ArrayList<>();
        result.add(log.toString());
        return ActionResult.ok(log.toString());
    }

    public ActionResult performTacticalTurn(Ship ship, int newFacing, boolean preferSublight) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return ActionResult.fail("Tactical Maneuvers can only be made during the Movement phase");
        if (ship.getSpeed() != 0)
            return ActionResult.fail("Tactical Maneuvers require speed 0 (C5.41)");
        int localImpulse = game.getCurrentImpulse();
        if (localImpulse < 2)
            return ActionResult.fail("Tactical Maneuvers cannot be made on Impulse 1 (C5.11)");

        // Validate exactly 60° change
        int diff = ((newFacing - ship.getFacing()) % 24 + 24) % 24;
        if (diff != 4 && diff != 20)
            return ActionResult.fail("Tactical Maneuver must be exactly 60° (one step left or right)");

        // Consume the appropriate TAC type
        String type;
        if (preferSublight) {
            if (!ship.isSublightTacAvailable())
                return ActionResult.fail("No sublight Tactical Maneuver available (C5.12)");
            ship.consumeSublightTac();
            type = "Sublight";
        } else {
            if (ship.getTacAvailable() > 0) {
                ship.consumeWarpTac();
                type = "Warp";
            } else if (ship.isSublightTacAvailable()) {
                ship.consumeSublightTac();
                type = "Sublight";
            } else {
                return ActionResult.fail("No Tactical Maneuver available — earn one on a Speed-4 impulse (C5.231)");
            }
        }

        ship.performHet(newFacing);
        return ActionResult.ok(ship.getName() + " " + type + " Tactical Maneuver → facing " + newFacing);
    }

    public ActionResult performFighterHet(com.sfb.objects.shuttles.Shuttle shuttle, int absoluteFacing) {
        if (!(shuttle instanceof com.sfb.objects.shuttles.Fighter))
            return ActionResult.fail("Only fighters can perform HETs (C6.42)");
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return ActionResult.fail("HETs can only be performed during the Movement phase");
        if (shuttle.isCrippled())
            return ActionResult.fail("Crippled fighters cannot perform HETs (J1.336)");
        com.sfb.objects.shuttles.Fighter fighter = (com.sfb.objects.shuttles.Fighter) shuttle;
        boolean performed = fighter.performTacticalManeuver(absoluteFacing);
        if (!performed)
            return ActionResult.fail(fighter.getName() + " has already used its HET this turn (C6.42)");
        return ActionResult.ok(fighter.getName() + " HET → facing " + absoluteFacing);
    }

    /**
     * Returns shuttles that move this impulse, have not yet moved, and all
     * ships have already moved (shuttles move after all ships).
     */
    public List<com.sfb.objects.shuttles.Shuttle> getMovableShuttles() {
        if (!getMovableShips().isEmpty())
            return java.util.Collections.emptyList();
        int impulse = game.getCurrentImpulse();
        List<com.sfb.objects.shuttles.Shuttle> movable = new ArrayList<>();
        for (com.sfb.objects.shuttles.Shuttle s : activeShuttles) {
            if (!s.isPlayerControlled())
                continue;
            if (s.isBeingRecovered())
                continue; // shut down for recovery (J1.622)
            if (s.isTractored())
                continue; // held fast — cannot fly out of the beam (G7.5)
            if (MovementUtil.moveThisImpulse(impulse, s.getSpeed())
                    && !movedShuttlesThisImpulse.contains(s)) {
                movable.add(s);
            }
        }
        return movable;
    }

    public boolean canMoveShuttleThisImpulse(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.MOVEMENT)
            return false;
        if (!shuttle.isPlayerControlled())
            return false;
        if (shuttle.isBeingRecovered())
            return false; // shut down for recovery (J1.622)
        if (shuttle.isTractored())
            return false; // held fast — cannot fly out of the beam (G7.5)
        if (!getMovableShips().isEmpty())
            return false;
        if (!MovementUtil.moveThisImpulse(game.getCurrentImpulse(), shuttle.getSpeed()))
            return false;
        return !movedShuttlesThisImpulse.contains(shuttle);
    }

    public ActionResult moveShuttleForward(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        shuttle.goForward(game.getMapCols(), game.getMapRows());
        if (shuttle.getLocation() == null) {
            activeShuttles.remove(shuttle);
            return ActionResult.fail(shuttle.getName() + " moved off the map");
        }
        movedShuttlesThisImpulse.add(shuttle);
        return ActionResult.ok(shuttle.getName() + " moved forward");
    }

    public ActionResult turnShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean turned = shuttle.turnLeft();
        if (turned)
            movedShuttlesThisImpulse.add(shuttle);
        return turned ? ActionResult.ok(shuttle.getName() + " turned left")
                : ActionResult.fail(shuttle.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean turned = shuttle.turnRight();
        if (turned)
            movedShuttlesThisImpulse.add(shuttle);
        return turned ? ActionResult.ok(shuttle.getName() + " turned right")
                : ActionResult.fail(shuttle.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipShuttleLeft(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean moved = shuttle.sideslipLeft();
        if (moved)
            movedShuttlesThisImpulse.add(shuttle);
        return moved ? ActionResult.ok(shuttle.getName() + " sideslipped left")
                : ActionResult.fail(shuttle.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipShuttleRight(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (!canMoveShuttleThisImpulse(shuttle))
            return ActionResult.fail(shuttle.getName() + " cannot move this impulse");
        boolean moved = shuttle.sideslipRight();
        if (moved)
            movedShuttlesThisImpulse.add(shuttle);
        return moved ? ActionResult.ok(shuttle.getName() + " sideslipped right")
                : ActionResult.fail(shuttle.getName() + " cannot sideslip (must move first)");
    }

    /**
     * Roll asteroid collision damage and apply to the appropriate shield (P3.2).
     * Shield hit is determined by the direction the ship entered the hex
     * (entryDirection relative to facing → shield 1-6).
     * Returns a log line describing the result.
     */
    private String applyAsteroidCollision(Ship ship) {
        int entryDir = ship.getEntryDirection();
        int relBearing = entryDir == 0 ? 1 : MapUtils.getRelativeBearing(entryDir, ship.getFacing());
        int shieldNum = (relBearing - 1) / 4 + 1;

        int speed = ship.getSpeed();
        int bracket = speed <= 6 ? 0 : speed <= 14 ? 1 : speed <= 25 ? 2 : 3;
        int roll = new DiceRoller().rollOneDie();
        int damage = Game.ASTEROID_DAMAGE[roll - 1][bracket];
        String base = "  " + ship.getName() + " enters asteroid hex"
                + " (speed " + speed + ", die " + roll + ", shield " + shieldNum + ")";
        if (damage == 0)
            return base + " — no damage";
        game.markShieldDamage(ship, shieldNum, damage);
        return base + " — " + damage + " to shield " + shieldNum;
    }
}
