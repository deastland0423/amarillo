package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneController;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;

/**
 * Seeker control-channel management: voluntary control transfer between
 * allied ships, automatic hand-off when a controller loses lock-on, orphan
 * release (D6.122), and lab identification of enemy seekers. Extracted from
 * Game to keep Game focused on state and turn sequencing. The CONTROL_OVERFLOW
 * interrupt (queue + phase transitions) stays in Game with the phase machine.
 */
class SeekerControl {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;

    SeekerControl(Game game, List<Ship> ships, List<Seeker> seekers) {
        this.game    = game;
        this.ships   = ships;
        this.seekers = seekers;
    }

    /**
     * Try to transfer control of a seeker to the first teammate of formerController
     * that has both free control channels and lock-on to the seeker's target.
     * On success: updates controller, releases from formerController if still held
     * there,
     * and returns a log string. Returns null if no valid teammate was found.
     */
    private String autoTransferSeekerControl(Seeker seeker, Ship formerController) {
        Unit target = seeker.getTarget();
        if (target == null || formerController == null)
            return null;
        for (Ship candidate : ships) {
            if (candidate == formerController)
                continue;
            if (!game.isSameTeam(candidate, formerController))
                continue;
            if (!candidate.hasLockOn(target))
                continue;
            if (!candidate.acquireControl(seeker))
                continue;
            // Release from the former controller if it still holds this seeker
            Unit current = seeker.getController();
            if (current instanceof DroneController && current != candidate)
                ((DroneController) current).releaseControl(seeker);
            seeker.setController(candidate);
            seeker.setSelfGuiding(false);
            String seekerName = seeker instanceof Unit ? ((Unit) seeker).getName() : "seeker";
            return "  Control of " + seekerName + " transferred from "
                    + formerController.getName() + " to " + candidate.getName();
        }
        return null;
    }

    /**
     * Voluntarily give up control of a seeker (F3.4). A drone that has its own lock-on keeps
     * flying at its target; one that was relying on the controlling ship loses guidance, and a
     * seeking shuttle goes inert in its hex (FD1.72) rather than vanishing.
     * <p>
     * This is the answer to a scout that has attracted one of your drones (G24.23): rather than
     * watch it chase the scout, cut it loose — unless it has an ATG lock-on of its own, in which
     * case releasing it changes nothing and it keeps coming.
     */
    ActionResult releaseSeekerControl(String seekerName, String byShipName) {
        Seeker seeker = null;
        for (Seeker s : seekers)
            if (s instanceof Unit && ((Unit) s).getName().equalsIgnoreCase(seekerName)) {
                seeker = s;
                break;
            }
        if (seeker == null)
            return ActionResult.fail("Seeker not found: " + seekerName);

        Ship ship = null;
        for (Ship s : ships)
            if (s.getName().equalsIgnoreCase(byShipName)) {
                ship = s;
                break;
            }
        if (ship == null)
            return ActionResult.fail("Ship not found: " + byShipName);
        if (seeker.getController() != ship)
            return ActionResult.fail(ship.getName() + " does not control " + seekerName);

        String name = ((Unit) seeker).getName();
        ship.releaseControl(seeker);
        seeker.setController(null);

        if (seeker instanceof Drone && ((Drone) seeker).isSelfGuiding())
            // Its own ATG lock-on carries it on; releasing control changes nothing (G24.23).
            return ActionResult.ok(name + " released — it has its own lock-on and keeps tracking "
                    + (seeker.getTarget() != null ? seeker.getTarget().getName() : "its target"));
        if (seeker instanceof com.sfb.objects.shuttles.Shuttle) {
            game.makeSeekerShuttleInert((com.sfb.objects.shuttles.Shuttle) seeker); // FD1.72
            return ActionResult.ok(name + " released — it went inert (speed 0, holds its hex)");
        }
        game.removeSeekerFromPlay(seeker);
        return ActionResult.ok(name + " released — it lost guidance and went inert (F3.4)");
    }

    ActionResult transferSeekerControl(String seekerName, String toShipName) {
        Seeker seeker = null;
        for (Seeker s : seekers) {
            if ((s instanceof Drone
                    || s instanceof com.sfb.objects.shuttles.SuicideShuttle
                    || s instanceof com.sfb.objects.shuttles.ScatterPack)
                    && ((Unit) s).getName().equalsIgnoreCase(seekerName)) {
                seeker = s;
                break;
            }
        }
        if (seeker == null)
            return ActionResult.fail("Seeker not found: " + seekerName);
        if (seeker instanceof Drone && ((Drone) seeker).isSelfGuiding())
            return ActionResult.fail("Self-guiding drones cannot be transferred");

        Ship toShip = ships.stream()
                .filter(s -> s.getName().equalsIgnoreCase(toShipName))
                .findFirst().orElse(null);
        if (toShip == null)
            return ActionResult.fail("Ship not found: " + toShipName);

        Unit currentController = seeker.getController();
        if (!(currentController instanceof DroneController))
            return ActionResult.fail("Seeker has no valid controller");

        if (!(currentController instanceof Ship) || !game.isSameTeam(toShip, (Ship) currentController))
            return ActionResult.fail(toShipName + " is not on the same team as the current controller");

        Unit target = seeker.getTarget();
        if (target == null)
            return ActionResult.fail("Seeker has no target");
        if (!toShip.hasLockOn(target))
            return ActionResult.fail(toShipName + " does not have lock-on to " + target.getName());

        if (!toShip.acquireControl(seeker))
            return ActionResult.fail(toShipName + " is at control capacity");

        ((DroneController) currentController).releaseControl(seeker);
        seeker.setController(toShip);
        if (seeker instanceof Drone)
            ((Drone) seeker).setSelfGuiding(false);

        String label = seeker instanceof com.sfb.objects.shuttles.ScatterPack ? "Scatter pack"
                : seeker instanceof com.sfb.objects.shuttles.SuicideShuttle ? "Suicide shuttle"
                        : "Drone";
        return ActionResult
                .ok(label + " control transferred from " + currentController.getName() + " to " + toShipName);
    }

    /**
     * Attempt to identify a list of enemy contacts using the acting ship's labs.
     * Each attempt costs 1 lab. Roll 1d6; result must be STRICTLY GREATER than
     * range to succeed.
     * Pseudo-plasma torps cannot be identified (attempt always fails to reveal
     * pseudo status).
     * <p>
     * Shuttles are identifiable too, for the same reason the scout-channel path allows it
     * (G24.25): an enemy suicide shuttle or an unreleased scatter pack reaches its enemy
     * as a plain shuttle, so a shuttle-looking contact may be exactly the thing worth a
     * lab. Labs could not ask the question before: a suicide shuttle IS a seeker and so
     * was findable by name, but nothing offered the name, and a genuine shuttle was not
     * findable at all. Identifying a plain shuttle establishes that it is NOT a seeking
     * weapon, which is the whole answer being bought.
     */
    ActionResult identifySeekers(Ship actingShip, List<String> seekerNames) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Lab identification can only be attempted during the Activity phase");
        // G4.21: a cloaked ship (G13.56) or one using Erratic Maneuvers (C10.52) cannot
        // identify with labs. Note what the rule does NOT bar: this is expressly allowed
        // under wild weasel restrictions (J3.13), unlike a scout channel (J3.403).
        if (actingShip.getCloakingDevice() != null
                && actingShip.getCloakingDevice().isRestrictingActions())
            return ActionResult.fail(actingShip.getName()
                    + " is cloaked and cannot identify with labs (G4.21, G13.56)");
        if (actingShip.isUsingEm())
            return ActionResult.fail(actingShip.getName()
                    + " is using Erratic Maneuvers and cannot identify with labs (G4.21, C10.52)");
        int availLabs = freeLabs(actingShip);
        if (availLabs <= 0)
            return ActionResult.fail(actingShip.getName() + " has no available labs");
        if (seekerNames == null || seekerNames.isEmpty())
            return ActionResult.fail("No contacts selected");
        if (seekerNames.size() > availLabs)
            return ActionResult.fail("Selected " + seekerNames.size() + " contacts but only "
                    + availLabs + " labs available");

        StringBuilder log = new StringBuilder(actingShip.getName() + " lab identification attempt\n");
        DiceRoller dice = new DiceRoller();

        for (String seekerName : seekerNames) {
            Seeker seeker = seekers.stream()
                    .filter(s -> ((com.sfb.objects.Marker) s).getName().equals(seekerName))
                    .findFirst().orElse(null);
            // A suicide shuttle and an unreleased scatter pack ARE seekers and are found
            // above; a plain shuttle lives in the active-shuttle list instead.
            com.sfb.objects.shuttles.Shuttle shuttle = null;
            if (seeker == null)
                for (com.sfb.objects.shuttles.Shuttle sh : game.getActiveShuttles())
                    if (sh.getName().equals(seekerName)) {
                        shuttle = sh;
                        break;
                    }
            if (seeker == null && shuttle == null) {
                log.append("  ").append(seekerName).append(" — not found\n");
                continue;
            }

            // Cannot attempt to ID own units. A seeker is judged by whoever guides it; a
            // shuttle by its owner, since a shuttle flying on its own has no controller.
            boolean friendly;
            if (seeker != null) {
                Unit seekerShip = seeker.getController();
                friendly = seekerShip instanceof Ship
                        && ((Ship) seekerShip).getFaction() == actingShip.getFaction();
            } else {
                friendly = sameOwnerTeam(actingShip, shuttle);
            }
            if (friendly) {
                log.append("  ").append(seekerName).append(" — cannot ID friendly unit\n");
                continue;
            }

            com.sfb.objects.Marker target = seeker != null
                    ? (com.sfb.objects.Marker) seeker : shuttle;
            actingShip.getLabs().decrementLab();
            int range = MapUtils.getRange(actingShip, target);
            int roll = dice.rollOneDie();
            log.append("  ").append(seekerName)
                    .append("  range ").append(range)
                    .append("  (die ").append(roll).append(")");

            if (roll > range) {
                if (seeker != null)
                    // Pseudo-plasma: identify() call is harmless but we don't announce type
                    seeker.identify();
                else
                    shuttle.identify();
                log.append("  — IDENTIFIED");
                // G4.233: identifying a SHUTTLE reveals whether it is on a seeking course
                // and, if so, its target -- never whether it carries drones or a suicide
                // bomb. A suicide shuttle and a loaded scatter pack are both shuttles and
                // both seekers, so they read exactly alike here, which is the point: the
                // lab narrows it to one of the two and stops.
                if (target instanceof com.sfb.objects.shuttles.Shuttle) {
                    if (target instanceof Seeker) {
                        Unit t = ((Seeker) target).getTarget();
                        log.append(" (on a seeking course")
                           .append(t != null ? ", target " + t.getName() : "")
                           .append(")");
                    } else {
                        log.append(" (not on a seeking course)");
                    }
                }
                log.append("\n");
            } else {
                log.append("  — FAILED\n");
            }
        }
        return ActionResult.ok(log.toString());
    }

    /**
     * Lab boxes free to make an identification attempt (G4.21): "Each lab box on board a
     * ship, IF IT IS UNDERTAKING NO OTHER ACTION on that turn, can make one attempt."
     * <p>
     * A scout channel assigned to IDENTIFY holds a lab for the turn (G24.251) but never
     * decrements the count, because it may make four attempts with it. So the raw
     * availableLab figure includes boxes a channel is already using, and this path would
     * happily spend one of them a second time — the same box doing two jobs at once.
     */
    private int freeLabs(Ship ship) {
        int heldByChannels = 0;
        for (com.sfb.weapons.ScoutChannel c : ship.getScoutChannels())
            if (c.getTurnFunction() == com.sfb.weapons.ScoutChannel.Function.IDENTIFY)
                heldByChannels++;
        return Math.max(0, ship.getLabs().getAvailableLab() - heldByChannels);
    }

    /**
     * True if the shuttle's owner is on the acting ship's team. Owner-based rather than
     * faction-based because a shuttle flying on its own has no controller to ask; this
     * mirrors the scout-channel path. An unset owner counts as hostile, since refusing
     * the attempt would be the worse failure.
     */
    private boolean sameOwnerTeam(Ship actingShip, Unit shuttle) {
        com.sfb.Player a = actingShip.getOwner();
        com.sfb.Player b = shuttle.getOwner();
        return a != null && b != null && a.getTeamName() != null
                && a.getTeamName().equals(b.getTeamName());
    }

    /**
     * Release any non-self-guiding drone whose controlling ship no longer has
     * lock-on to its target (D6.122). Sets target and controller to null so
     * the drone flies straight until endurance expires.
     */
    List<String> releaseOrphanedDrones() {
        List<String> log = new ArrayList<>();
        List<Seeker> toRemove = new ArrayList<>();
        for (Seeker s : seekers) {
            if (s instanceof Drone) {
                Drone drone = (Drone) s;
                if (drone.isSelfGuiding()) {
                    // Self-guiding drones have their own lock-on — foiled by full cloak
                    if (isTargetFullyCloaked(drone)) {
                        log.add("  Drone lost tracking — " + drone.getTarget().getName() + " is fully cloaked");
                        toRemove.add(drone);
                    }
                } else {
                    // Controller-guided drones: released when controller loses lock-on
                    Unit target = drone.getTarget();
                    Unit controller = drone.getController();
                    if (target == null || !(controller instanceof Ship))
                        continue;
                    Ship controlShip = (Ship) controller;
                    if (!controlShip.hasLockOn(target)) {
                        String xfer = autoTransferSeekerControl(drone, controlShip);
                        if (xfer != null) {
                            log.add(xfer);
                        } else {
                            log.add("  Drone released — " + controlShip.getName()
                                    + " lost lock-on to " + target.getName() + ", no teammate available");
                            controlShip.releaseControl(drone);
                            toRemove.add(drone);
                        }
                    }
                }
            } else if (s instanceof PlasmaTorpedo) {
                // Self-guiding — foiled by full cloak unless it won its own
                // retention roll (G13.3343), made when the fade-out completed
                if (isTargetFullyCloaked(s) && !((PlasmaTorpedo) s).isCloakLockRetained()) {
                    log.add("  Plasma torpedo lost tracking — " + s.getTarget().getName() + " is fully cloaked");
                    toRemove.add(s);
                }
            }
        }
        seekers.removeAll(toRemove);
        return log;
    }

    /**
     * P2.33: a self-guided seeking weapon whose target passes behind a planet
     * loses that target, acquires the PLANET as its new target, and strikes
     * it. Simplification: the impact is resolved immediately (the weapon can
     * no longer harm any unit, and the P2.525 general-destruction scoring is
     * scenario-optional). Controlled seekers are handled separately via their
     * guiding ship's lock-on (P2.3222 → releaseOrphanedDrones); a planet
     * between a controller and its own weapon never matters (P2.34).
     */
    List<String> sweepSelfGuidedLos() {
        List<String> log = new ArrayList<>();
        if (!game.anyPlanetSurface())
            return log;
        List<Seeker> lost = new ArrayList<>();
        for (Seeker s : seekers) {
            if (!(s instanceof Unit) || s.getTarget() == null)
                continue;
            boolean selfGuided = s.isSelfGuiding() || s instanceof PlasmaTorpedo;
            if (!selfGuided)
                continue;
            Unit unit = (Unit) s;
            if (unit.getLocation() == null || s.getTarget().getLocation() == null)
                continue;
            if (game.losBlocked(unit.getLocation(), s.getTarget().getLocation())) {
                log.add("  " + unit.getName() + " loses sight of " + s.getTarget().getName()
                        + " behind the planet — acquires the planet and impacts it (P2.33)");
                lost.add(s);
            }
        }
        for (Seeker s : lost)
            game.removeSeekerFromPlay(s);
        return log;
    }

    /**
     * G13.334: self-guiding seekers (plasma) targeting a ship that just
     * completed fade-out. Must run AFTER the ships' own retention rolls
     * (G13.331):
     * - Controlled by a ship that retained its lock-on → tracks automatically,
     *   no roll of its own (G13.3341).
     * - Controlling ship failed (or none) → the torpedo is released (G13.3342/
     *   F3.4) and makes its own attempt at sensor rating 6 (G13.3343/G13.3344):
     *   P = 6 − EW − RF + SF − 4, with EW using the torpedo's built-in ECCM
     *   (D6.393). Failure removes it from play. The release itself
     *   is a no-op in this engine: plasma occupies no control channels and
     *   cannot be steered, and the launcher reference is kept for display.
     */
    List<String> rollPlasmaCloakRetention(Ship cloaked) {
        List<String> log = new ArrayList<>();
        DiceRoller dice = new DiceRoller();
        List<Seeker> toRemove = new ArrayList<>();
        for (Seeker s : seekers) {
            if (!(s instanceof PlasmaTorpedo) || s.getTarget() != cloaked)
                continue;
            PlasmaTorpedo torp = (PlasmaTorpedo) s;
            Unit controller = s.getController();
            if (controller instanceof Ship && ((Ship) controller).hasLockOn(cloaked)) {
                // G13.3341: the guiding ship retained its lock-on
                torp.setCloakLockRetained(true);
                log.add("  " + torp.getName() + " keeps tracking cloaked " + cloaked.getName()
                        + " via " + controller.getName() + "'s retained lock-on (G13.3341)");
                continue;
            }
            if (controller instanceof Ship)
                log.add("  " + torp.getName() + " released — " + controller.getName()
                        + " lost its lock-on (G13.3342); rolling own retention");
            // G13.3343: no outside ECCM, but built-in ECCM (D6.393) counts —
            // EW adjustment = signed chart of (cloaked ECM − built-in ECCM)
            int ew = LockOnResolver.signedNetEcmShift(
                    cloaked.getEcmAllocated() + cloaked.getLentEcm() - s.getBuiltInEccm());
            int p = 6
                    - ew
                    - LockOnResolver.rangeFactor(MapUtils.getRange((Unit) s, cloaked))
                    + LockOnResolver.speedFactor(cloaked.getSpeed())
                    - 4;
            int roll = dice.rollOneDie();
            if (roll <= p) {
                torp.setCloakLockRetained(true);
                log.add("  " + torp.getName() + " retains tracking on cloaked " + cloaked.getName()
                        + " (die " + roll + " ≤ " + p + "; G13.3343)");
            } else {
                log.add("  " + torp.getName() + " lost tracking — failed retention on cloaked "
                        + cloaked.getName() + " (die " + roll + " > " + p + "; G13.3343)");
                toRemove.add(s);
            }
        }
        seekers.removeAll(toRemove);
        return log;
    }

    /** Extracts the lowest-numbered direction from an arc bitmask. */
    private boolean isTargetFullyCloaked(Seeker s) {
        Unit target = s.getTarget();
        if (!(target instanceof Ship))
            return false;
        com.sfb.systemgroups.CloakingDevice cloak = ((Ship) target).getCloakingDevice();
        return cloak != null && cloak.breaksLockOn();
    }
}
