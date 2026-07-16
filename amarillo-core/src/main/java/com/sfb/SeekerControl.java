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
     * Attempt to identify a list of enemy seekers using the acting ship's labs.
     * Each attempt costs 1 lab. Roll 1d6; result must be STRICTLY GREATER than
     * range to succeed.
     * Pseudo-plasma torps cannot be identified (attempt always fails to reveal
     * pseudo status).
     */
    ActionResult identifySeekers(Ship actingShip, List<String> seekerNames) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Lab identification can only be attempted during the Activity phase");
        if (actingShip.getLabs().getAvailableLab() <= 0)
            return ActionResult.fail(actingShip.getName() + " has no available labs");
        if (seekerNames == null || seekerNames.isEmpty())
            return ActionResult.fail("No seekers selected");
        int availLabs = actingShip.getLabs().getAvailableLab();
        if (seekerNames.size() > availLabs)
            return ActionResult
                    .fail("Selected " + seekerNames.size() + " seekers but only " + availLabs + " labs available");

        StringBuilder log = new StringBuilder(actingShip.getName() + " lab identification attempt\n");
        DiceRoller dice = new DiceRoller();

        for (String seekerName : seekerNames) {
            Seeker seeker = seekers.stream()
                    .filter(s -> ((com.sfb.objects.Marker) s).getName().equals(seekerName))
                    .findFirst().orElse(null);
            if (seeker == null) {
                log.append("  ").append(seekerName).append(" — not found\n");
                continue;
            }
            // Cannot attempt to ID own seekers
            Unit seekerShip = seeker.getController();
            if (seekerShip instanceof Ship && ((Ship) seekerShip).getFaction() == actingShip.getFaction()) {
                log.append("  ").append(seekerName).append(" — cannot ID friendly seeker\n");
                continue;
            }

            actingShip.getLabs().decrementLab();
            int range = MapUtils.getRange(actingShip, (com.sfb.objects.Marker) seeker);
            int roll = dice.rollOneDie();
            log.append("  ").append(seekerName)
                    .append("  range ").append(range)
                    .append("  (die ").append(roll).append(")");

            if (roll > range) {
                // Pseudo-plasma: identify() call is harmless but we don't announce type
                seeker.identify();
                log.append("  — IDENTIFIED\n");
            } else {
                log.append("  — FAILED\n");
            }
        }
        return ActionResult.ok(log.toString());
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
     * G13.334: self-guiding seekers (plasma) targeting a ship that just
     * completed fade-out. Must run AFTER the ships' own retention rolls
     * (G13.331):
     * - Controlled by a ship that retained its lock-on → tracks automatically,
     *   no roll of its own (G13.3341).
     * - Controlling ship failed (or none) → the torpedo is released (G13.3342/
     *   F3.4) and makes its own attempt at sensor rating 6 (G13.3343/G13.3344):
     *   P = 6 − RF + SF − 4. Failure removes it from play. The release itself
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
            int p = 6
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
