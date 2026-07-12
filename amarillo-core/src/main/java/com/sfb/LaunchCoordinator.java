package com.sfb;

import java.util.List;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneController;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.utilities.ArcUtils;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;

/**
 * Launch coordination: drones, plasma (real and pseudo), shuttles, suicide
 * shuttles, scatter packs, wild weasels, and chaff. Extracted from Game to
 * keep Game focused on state and turn sequencing. Shares Game's seekers and
 * activeShuttles lists by reference; naming, lock-on checks for new units,
 * and control-overflow processing go through package hooks on Game.
 */
class LaunchCoordinator {

    private final Game game;
    private final List<Seeker> seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;

    LaunchCoordinator(Game game, List<Seeker> seekers,
            List<com.sfb.objects.shuttles.Shuttle> activeShuttles) {
        this.game           = game;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
    }

    /**
     * Launch a charged Wild Weasel decoy from ship's shuttle bay (J3.111).
     * The WW appears at the ship's current hex, all seekers targeting the ship
     * retarget to the WW, the ship gains +6 ECM (J3.23), and lock-ons are cleared.
     */
    /**
     * Drop a chaff pack from a fighter or shuttle (D11.3).
     * Must be called during the Activity phase (6B6 Seeking Weapons Stage).
     * On a roll of 1–4, all seekers currently targeting the shuttle lose tracking
     * and are removed.
     * Applies an 8-impulse lockout on direct-fire and seeker weapons (D11.41–42).
     */
    public ActionResult dropChaff(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Chaff can only be dropped during the Activity phase (D11.31)");
        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle
                || shuttle instanceof com.sfb.objects.shuttles.ScatterPack
                || shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return ActionResult.fail("SP, SS, and WW shuttles cannot drop chaff (D11.312)");
        if (shuttle.isBeingRecovered())
            return ActionResult.fail(shuttle.getName()
                    + " is shut down for recovery and cannot drop chaff (J1.622)");
        if (shuttle.getChaffPacks() <= 0)
            return ActionResult.fail(shuttle.getName() + " has no chaff packs remaining");
        if (shuttle.isChaffLockedOut(game.getAbsoluteImpulse()))
            return ActionResult.fail(shuttle.getName() + " is in chaff lockout and cannot drop another pack");

        int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
        shuttle.applyChaffLockout(game.getAbsoluteImpulse());

        if (roll >= 5) {
            return ActionResult.ok(shuttle.getName() + " dropped chaff (roll " + roll + ") — no effect; "
                    + shuttle.getChaffPacks() + " pack(s) remaining");
        }

        // Roll 1–4: all seekers targeting this shuttle lose tracking
        List<Seeker> distracted = seekers.stream()
                .filter(s -> shuttle.equals(s.getTarget()))
                .collect(java.util.stream.Collectors.toList());

        StringBuilder sb = new StringBuilder(shuttle.getName() + " dropped chaff (roll " + roll
                + ") — " + distracted.size() + " seeker(s) distracted");

        for (Seeker s : distracted) {
            sb.append("\n  ").append(s instanceof Unit ? ((Unit) s).getName() : "seeker").append(" — lost tracking");
            game.removeSeekerFromPlay(s);
        }

        sb.append("; ").append(shuttle.getChaffPacks()).append(" pack(s) remaining");
        return ActionResult.ok(sb.toString());
    }

    public ActionResult launchWildWeasel(Ship ship, String shuttleName, int facing, int speed) {
        // Find the charged admin shuttle in any bay
        com.sfb.objects.shuttles.AdminShuttle foundShuttle = null;
        com.sfb.systemgroups.ShuttleBay foundShuttleBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                if (s instanceof com.sfb.objects.shuttles.AdminShuttle
                        && s.getName().equalsIgnoreCase(shuttleName)
                        && ((com.sfb.objects.shuttles.AdminShuttle) s).isWwReady()) {
                    foundShuttle = (com.sfb.objects.shuttles.AdminShuttle) s;
                    foundShuttleBay = bay;
                    break;
                }
            }
            if (foundShuttle != null)
                break;
        }
        if (foundShuttle == null)
            return ActionResult.fail(shuttleName + " is not a charged Wild Weasel");
        if (ship.hasActiveWildWeasel())
            return ActionResult.fail(ship.getName() + " already has an active Wild Weasel");
        if (ship.isTractored())
            return ActionResult.fail("Cannot launch Wild Weasel while held in a tractor beam (G7.98)");
        if (ship.getSpeed() > 4)
            return ActionResult.fail("Cannot launch Wild Weasel — ship speed " + ship.getSpeed()
                    + " exceeds maneuver rate limit of 4 (J3.131)");
        if (!foundShuttleBay.canLaunch(foundShuttle, game.getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay is not ready to launch");

        int wwFacing = (facing >= 1 && facing <= 24) ? facing : ship.getFacing();
        int wwSpeed = Math.max(0, Math.min(6, speed));

        com.sfb.objects.shuttles.WildWeaselShuttle ww = new com.sfb.objects.shuttles.WildWeaselShuttle(ship);
        ww.setName(foundShuttle.getName());
        ww.setParentShipName(ship.getName());
        ww.setOwner(ship.getOwner());
        foundShuttleBay.launch(foundShuttle, wwSpeed, wwFacing, game.getAbsoluteImpulse());
        ww.setLocation(ship.getLocation());
        ww.setFacing(wwFacing);
        ww.setCurrentSpeed(wwSpeed);
        ww.setSpeed(wwSpeed);
        activeShuttles.add(ww);
        ship.setActiveWildWeasel(ww);

        // Retarget all seekers aimed at this ship to the WW (J3.111)
        for (Seeker seeker : seekers) {
            if (seeker.getTarget() == ship)
                seeker.setTarget(ww);
        }

        // Deactivate fire control and clear all lock-ons (J3.132, J3.13)
        ship.setActiveFireControl(false);
        ship.clearLockOns();

        return ActionResult.ok(ship.getName() + " launched Wild Weasel " + ww.getName()
                + " — fire control deactivated, all lock-ons lost, +6 ECM active");
    }

    /**
     * Void the active Wild Weasel for this ship (J3.13x). Seekers retarget back
     * to the ship. Called when the WW is destroyed or the ship voids it by firing.
     */
    public void voidWildWeasel(Ship ship) {
        com.sfb.objects.shuttles.WildWeaselShuttle ww = ship.getActiveWildWeasel();
        if (ww == null)
            return;

        // Retarget seekers from WW back to parent ship
        for (Seeker seeker : seekers) {
            if (seeker.getTarget() == ww)
                seeker.setTarget(ship);
        }

        activeShuttles.remove(ww);
        ship.setActiveWildWeasel(null);
    }

    /**
     * Launch one drone from the given rack at the given target.
     * The drone is placed at the launching ship's location, faced toward the
     * target, and added to the active seekers list.
     *
     * @return ActionResult describing success or reason for failure.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch seeking weapons — breakdown lockout for 8 impulses (C6.5473)");
        // G7.943: tractored ship may only launch seeking weapons at the holding ship
        if (launcher.isTractored() && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only launch seeking weapons at the holding ship (G7.943)");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (rack.isEmpty())
            return ActionResult.fail(rack.getName() + " has no drones loaded");
        if (!launcher.isActiveFireControl()) {
            // PFC: only self-guiding drones may be launched (D19.221)
            if (!rack.getAmmo().get(0).isSelfGuiding())
                return ActionResult.fail("Passive fire control — cannot launch non-self-guiding drones (D19.22)");
            // Self-guiding drones acquire their own lock-on after launch — no pre-launch
            // lock-on needed
        } else if (!launcher.hasLockOn(target)) {
            return ActionResult.fail("No sensor lock-on to target — cannot launch seeking weapons (D6.121)");
        }

        return launchDrone(launcher, target, rack, rack.getAmmo().get(0), 0);
    }

    /**
     * Launch a specific drone from the given rack at the given target.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone, int facing) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch seeking weapons — breakdown lockout for 8 impulses (C6.5473)");
        // G7.943: tractored ship may only launch seeking weapons at the holding ship
        if (launcher.isTractored() && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only launch seeking weapons at the holding ship (G7.943)");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (!rack.getAmmo().contains(drone))
            return ActionResult.fail("Drone is not in " + rack.getName());
        if (!launcher.isActiveFireControl() && !drone.isSelfGuiding())
            return ActionResult.fail("Passive fire control — cannot launch non-self-guiding drones (D19.22)");
        if (!drone.isSelfGuiding()) {
            if (!launcher.acquireControl(drone)) {
                // Over limit — force-add and queue overflow interrupt for player to resolve
                launcher.forceAcquireControl(drone);
            }
        }

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        rack.getAmmo().remove(drone);
        rack.recordLaunch();
        drone.setName(launcher.getName() + "-Drone-" + game.nextSeekerSeq());
        drone.setLocation(launcher.getLocation());
        drone.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit droneTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                droneTarget = ww;
        }
        drone.setTarget(droneTarget);
        if (drone.getController() == null)
            drone.setController(launcher);
        drone.setLauncherName(launcher.getName());
        drone.setLaunchImpulse(game.getAbsoluteImpulse());
        drone.setSeekerType(Seeker.SeekerType.DRONE);
        seekers.add(drone);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, drone);

        String msg = launcher.getName() + " launched " + drone.getDroneType()
                + " drone at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        game.checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * Launch a plasma torpedo from the given launcher at the target.
     * The launcher must be armed. The torpedo is placed at the launcher's
     * location, faced toward the target, and added to the active seekers list.
     */
    public ActionResult launchPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, boolean fastLoad, int facing) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch plasma — breakdown lockout for 8 impulses (C6.5473)");
        // G7.91: tractored ship cannot fire plasma torpedoes at non-holding ships
        if (launcher.isTractored() && target instanceof Ship && target != launcher.getTractoringUnit())
            return ActionResult.fail("Tractored ships may only fire plasma at the holding ship (G7.91)");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (fastLoad) {
            if (!weapon.canFastLoad())
                return ActionResult.fail(weapon.getName() + " is not eligible for fast-load (FP1.93)");
            if (!launcher.getPowerSystems().useBattery(2))
                return ActionResult.fail("Not enough battery for fast-load — requires 2 points (FP1.93)");
            weapon.applyFastLoad();
        }
        if (!weapon.isArmed())
            return ActionResult.fail(weapon.getName() + " is not armed");
        // Validate launch facing is within the launcher's allowed directions
        // (ship-relative arc)
        if (facing > 0) {
            int launchDirs = weapon.getLaunchDirections() != 0 ? weapon.getLaunchDirections() : weapon.getArcs();
            int relFacing = MapUtils.getRelativeBearing(facing, launcher.getFacing());
            if (!ArcUtils.inArc(relFacing, launchDirs))
                return ActionResult
                        .fail(weapon.getName() + " cannot launch in direction " + facing + " — outside launcher arc");
        }

        PlasmaTorpedo torpedo = weapon.launch();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch");

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        torpedo.setName(launcher.getName() + "-Plasma-" + game.nextSeekerSeq());
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit torpTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                torpTarget = ww;
        }
        torpedo.setTarget(torpTarget);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(game.getAbsoluteImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, torpedo);

        String msg = launcher.getName() + " launched plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        return ActionResult.ok(msg);
    }

    public ActionResult launchPseudoPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, int facing) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch plasma — breakdown lockout for 8 impulses (C6.5473)");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (!weapon.canLaunchPseudo())
            return ActionResult.fail(weapon.getName() + " cannot launch pseudo plasma now");
        PlasmaTorpedo torpedo = weapon.launchPseudo();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch pseudo plasma");

        torpedo.setName(launcher.getName() + "-Pseudo-" + game.nextSeekerSeq());
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(facing > 0 ? facing : MapUtils.getBearing(launcher, target));
        torpedo.setTarget(target);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(game.getAbsoluteImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, torpedo);

        String msg = launcher.getName() + " launched pseudo plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName() + " [PSEUDO]";
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        return ActionResult.ok(msg);
    }

    /**
     * Launch a standard (admin/GAS) shuttle from a bay.
     * The shuttle moves independently on the map but is not a seeker.
     */
    public ActionResult launchShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.Shuttle shuttle, int speed, int facing) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (!bay.canLaunch(shuttle, game.getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");

        com.sfb.objects.shuttles.Shuttle launched = bay.launch(shuttle, speed, facing, game.getAbsoluteImpulse());
        if (launched == null)
            return ActionResult.fail("Shuttle not found in bay");

        launched.setLocation(launcher.getLocation());
        launched.setParentShipName(launcher.getName());
        launched.setOwner(launcher.getOwner());
        launched.setLaunchImpulse(game.getAbsoluteImpulse());
        activeShuttles.add(launched);
        return ActionResult.ok(launcher.getName() + " launched shuttle " + launched.getName());
    }

    /**
     * Launch a fully-armed suicide shuttle at a target.
     * Requires lock-on. Speed capped at shuttle's maxSpeed.
     */
    public ActionResult launchSuicideShuttle(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.SuicideShuttle shuttle, Unit target, int facing, int speed) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (!shuttle.isFullyArmed())
            return ActionResult.fail("Suicide shuttle is not fully armed (needs 3 turns)");
        if (!bay.canLaunch(game.getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No lock-on to target — cannot launch suicide shuttle");
        launcher.forceAcquireControl(shuttle);

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        bay.launch(shuttle, Math.min(speed, shuttle.getMaxSpeed()), facing, game.getAbsoluteImpulse());
        shuttle.setName(launcher.getName() + "-Suicide-" + game.nextSeekerSeq());
        shuttle.setLocation(launcher.getLocation());
        // J3.201: redirect to WW if target ship has an active/exploding WW (not
        // post-explosion)
        Unit ssTarget = target;
        if (target instanceof Ship) {
            com.sfb.objects.shuttles.WildWeaselShuttle ww = ((Ship) target).getActiveWildWeasel();
            if (ww != null && !ww.isPostExplosion())
                ssTarget = ww;
        }
        shuttle.setTarget(ssTarget);
        shuttle.setController(launcher);
        shuttle.setLaunchImpulse(game.getAbsoluteImpulse());
        seekers.add(shuttle);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, shuttle);

        String msg = launcher.getName() + " launched suicide shuttle at " + target.getName()
                + " (warhead " + shuttle.getWarheadDamage() + ")";
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        game.checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * Launch a scatter pack at a target hex.
     * Requires lock-on. Releases its drones after 8 impulses.
     */
    public ActionResult launchScatterPack(Ship launcher, com.sfb.systemgroups.ShuttleBay bay,
            com.sfb.objects.shuttles.ScatterPack pack, Unit target, int facing, int speed) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only be launched during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(launcher);
        if (cloakBlock != null)
            return cloakBlock;
        if (launcher.isInPostHetWindow(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles within 4 impulses of a HET (C6.38)");
        if (launcher.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot launch shuttles — breakdown lockout for 8 impulses (C6.5472)");
        if (pack.getPayload().isEmpty())
            return ActionResult.fail("Scatter pack has no drones loaded");
        if (!bay.canLaunch(game.getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No lock-on to target — cannot launch scatter pack");

        launcher.forceAcquireControl(pack);

        bay.launch(pack, Math.min(speed, pack.getMaxSpeed()), facing, game.getAbsoluteImpulse());
        pack.setName(launcher.getName() + "-Pack-" + game.nextSeekerSeq());
        pack.setLocation(launcher.getLocation());
        pack.setTarget(target);
        pack.setController(launcher);
        pack.setLaunchImpulse(game.getAbsoluteImpulse());
        seekers.add(pack);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, pack);

        String msg = launcher.getName() + " launched scatter pack ("
                + pack.getPayload().size() + " drones) at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        game.checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * Unassisted landing aboard a friendly ship (J1.61): the pilot flies the
     * shuttle through the hatch. Requires same hex, ship not moving faster than
     * the shuttle, a bay with an empty box, and an available hatch — the hatch
     * cooldown is shared with launches (J1.50, one operation per 2 impulses).
     * Active suicide shuttles, scatter packs, and Wild Weasels cannot land this
     * way (J1.611); they need a tractor (J1.62, deferred).
     */
    ActionResult landShuttle(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only land during the Activity phase");

        com.sfb.objects.shuttles.Shuttle shuttle = activeShuttles.stream()
                .filter(sh -> sh.getName().equalsIgnoreCase(shuttleName))
                .findFirst().orElse(null);
        if (shuttle == null)
            return ActionResult.fail("Shuttle not found on the map: " + shuttleName);

        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle
                || shuttle instanceof com.sfb.objects.shuttles.ScatterPack
                || shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return ActionResult.fail(
                    "Active suicide shuttles, scatter packs, and Wild Weasels cannot land aboard (J1.611)");

        String shipTeam    = ship.getOwner()    != null ? ship.getOwner().getTeamName()    : null;
        String shuttleTeam = shuttle.getOwner() != null ? shuttle.getOwner().getTeamName() : null;
        if (shipTeam == null || !shipTeam.equals(shuttleTeam))
            return ActionResult.fail("Only friendly shuttles may land aboard unassisted (J1.61/J1.612)");

        if (shuttle.getLocation() == null || ship.getLocation() == null
                || !shuttle.getLocation().equals(ship.getLocation()))
            return ActionResult.fail(shuttleName + " must be in the same hex as "
                    + ship.getName() + " to land (J1.61)");

        if (ship.getSpeed() > shuttle.getSpeed())
            return ActionResult.fail(ship.getName() + " (speed " + ship.getSpeed()
                    + ") is moving faster than " + shuttleName + " (speed " + shuttle.getSpeed()
                    + ") — cannot land aboard (J1.61)");

        int impulse = game.getAbsoluteImpulse();
        com.sfb.systemgroups.ShuttleBay bay = null;
        boolean anySpace = false;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays()) {
            if (b.getEmptySpaceCount() > 0) {
                anySpace = true;
                if (b.canLaunch(impulse)) {
                    bay = b;
                    break;
                }
            }
        }
        if (!anySpace)
            return ActionResult.fail("No empty shuttle box available on " + ship.getName() + " (J1.61)");
        if (bay == null)
            return ActionResult.fail("Shuttle bay hatch on cooldown — one operation every 2 impulses (J1.50)");

        // Land: the hatch operation shares the launch cooldown (J1.50)
        bay.markUsed(impulse);
        bay.addShuttle(shuttle);
        activeShuttles.remove(shuttle);
        shuttle.setLocation(null);
        shuttle.setParentShipName(ship.getName());

        StringBuilder msg = new StringBuilder(shuttleName + " landed aboard " + ship.getName() + " (J1.61)");

        // Seekers chasing the shuttle lose their target — it is no longer in space
        java.util.List<Seeker> chasing = new java.util.ArrayList<>();
        for (Seeker sk : seekers) {
            if (shuttle.equals(sk.getTarget()))
                chasing.add(sk);
        }
        for (Seeker sk : chasing) {
            msg.append("\n  ").append(sk instanceof Unit ? ((Unit) sk).getName() : "seeker")
               .append(" lost tracking — target landed");
            game.removeSeekerFromPlay(sk);
        }
        for (com.sfb.objects.Ship s : game.getShips())
            s.removeLockOn(shuttle); // no lock-ons on a shuttle in a bay

        return ActionResult.ok(msg.toString());
    }

    /**
     * Declare the J1.621 special recovery procedure for a friendly shuttle the
     * ship already holds in a tractor beam. The shuttle shuts down (J1.622: no
     * fire, no chaff, no movement) and is pulled one hex closer per impulse,
     * boarding on arrival. Releasing the tractor cancels the procedure
     * (J1.6221). Not usable on Wild Weasels (J3.25 pull-in not implemented) or
     * drones (J1.6216 — structurally impossible here).
     */
    ActionResult beginRecovery(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Recovery can only be declared during the Activity phase");

        com.sfb.objects.shuttles.Shuttle shuttle = activeShuttles.stream()
                .filter(sh -> sh.getName().equalsIgnoreCase(shuttleName))
                .findFirst().orElse(null);
        if (shuttle == null)
            return ActionResult.fail("Shuttle not found on the map: " + shuttleName);
        if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return ActionResult.fail("Wild Weasels cannot be recovered while active (J3.25)");
        if (shuttle.isBeingRecovered())
            return ActionResult.fail(shuttleName + " is already being recovered");
        if (shuttle.getTractoringUnit() != ship)
            return ActionResult.fail(ship.getName() + " must hold " + shuttleName
                    + " in a tractor beam first (J1.62/J1.6215)");

        String shipTeam    = ship.getOwner()    != null ? ship.getOwner().getTeamName()    : null;
        String shuttleTeam = shuttle.getOwner() != null ? shuttle.getOwner().getTeamName() : null;
        if (shipTeam == null || !shipTeam.equals(shuttleTeam))
            return ActionResult.fail("Only friendly shuttles may use the special recovery procedure (J1.6214)");

        shuttle.setBeingRecovered(true);
        return ActionResult.ok(ship.getName() + " begins recovering " + shuttleName
                + " — shuttle shut down, pulled one hex closer each impulse (J1.621)");
    }

    /**
     * Final step of J1.621: pull the recovered shuttle aboard. Returns the log
     * line on success, or null if no bay currently has both an empty box and a
     * ready hatch (the shuttle holds at Range 0 — J1.6213). The hatch cooldown
     * is shared with launches (J1.50).
     */
    String completeRecovery(Ship ship, com.sfb.objects.shuttles.Shuttle shuttle) {
        int impulse = game.getAbsoluteImpulse();
        com.sfb.systemgroups.ShuttleBay bay = null;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays()) {
            if (b.getEmptySpaceCount() > 0 && b.canLaunch(impulse)) {
                bay = b;
                break;
            }
        }
        if (bay == null)
            return null;

        bay.markUsed(impulse);
        bay.addShuttle(shuttle);
        activeShuttles.remove(shuttle);
        if (ship.getTractors() != null && ship.getTractors().getTractoredUnits().contains(shuttle))
            ship.getTractors().releaseTractor(shuttle); // also clears beingRecovered
        shuttle.setBeingRecovered(false);
        shuttle.setLocation(null);
        shuttle.setParentShipName(ship.getName());
        // Chasers lose tracking (target no longer in space) and lock-ons clear —
        // same as landing (J1.61); log lines surface via the phase log
        game.clearChasersOf(shuttle, "target recovered aboard " + ship.getName());
        for (com.sfb.objects.Ship s : game.getShips())
            s.removeLockOn(shuttle);
        return shuttle.getName() + " recovered aboard " + ship.getName() + " (J1.621)";
    }
}
