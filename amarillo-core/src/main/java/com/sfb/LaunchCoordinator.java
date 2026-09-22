package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
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
        this.game = game;
        this.seekers = seekers;
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

    /**
     * What a launched shuttle is called: "<Ship>-<Type>-<n>", e.g. "IKS Fury-Admin-2".
     * <p>
     * It names the CRAFT, never the role. An admin shuttle, a suicide shuttle and a scatter
     * pack all built from admin shuttles read alike, so an opponent watching one leave a bay
     * cannot tell which it is — the uncertainty the weasel, the suicide shuttle and the
     * scatter pack all depend on. What it no longer hides is the shuttle TYPE, which is a
     * visible property of the craft: a GAS reads "GAS", not "Shuttle".
     */
    private String launchName(Ship launcher, com.sfb.objects.shuttles.Shuttle shuttle) {
        com.sfb.objects.ShuttleCatalog.Entry e = shuttle.getCatalogType() == null ? null
                : com.sfb.objects.ShuttleCatalog.get(shuttle.getCatalogType());
        String label = e != null ? e.shortName : "Shuttle";
        return launcher.getName() + "-" + label + "-" + game.nextSeekerSeq();
    }

    public ActionResult launchWildWeasel(Ship ship, String shuttleName, int facing, int speed) {
        // Find the charged admin shuttle in any bay
        com.sfb.objects.shuttles.Shuttle foundShuttle = null;
        com.sfb.systemgroups.ShuttleBay foundShuttleBay = null;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                // J3.18: any non-fighter shuttle may serve, so ask the capability rather
                // than the class — this used to read `instanceof AdminShuttle`.
                if (s.canBecomeWildWeasel()
                        && s.getName().equalsIgnoreCase(shuttleName)
                        && s.isWwReady()) {
                    foundShuttle = s;
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
        // A weasel may move at anything up to the MAX SPEED OF THE SHUTTLE IT IS BUILT FROM.
        // This was a hardcoded 6 — an admin shuttle's figure — which J3.18 makes wrong the
        // moment anything else is charged: any non-fighter shuttle may serve, and they do not
        // all move at six. effectiveMaxSpeed, like the plain shuttle path, so a point of
        // speed committed to erratic maneuvers is honoured too (C10.13).
        int wwSpeed = Math.max(0, Math.min(foundShuttle.effectiveMaxSpeed(), speed));

        // Built FROM the shuttle being charged, so it keeps that shuttle's hull, speed and
        // type rather than assuming an admin shuttle's (J3.18 allows any non-fighter).
        com.sfb.objects.shuttles.WildWeaselShuttle ww =
                new com.sfb.objects.shuttles.WildWeaselShuttle(ship, foundShuttle);
        ww.setName(launchName(ship, ww));
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

        // Deactivate fire control (J3.132) — the setter clears all lock-ons
        // (D6.62); drones this ship was guiding are released (D6.122)
        ship.setActiveFireControl(false);
        List<String> log = new ArrayList<>();
        log.add(ship.getName() + " launched Wild Weasel " + ww.getName()
                + " — fire control deactivated, all lock-ons lost, +6 ECM active");
        log.addAll(game.releaseOrphanedDrones());
        return ActionResult.ok(String.join("\n", log));
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
    /**
     * A launched seeker must be able to see where it is going: with the facing it is launched
     * on, the target has to lie inside the seeker's OWN forward arc (ArcUtils.FA, the nine
     * directions 21-5).
     *
     * A third constraint, distinct from the two on the launcher. A drone rack has no arc of
     * its own and a plasma tube's arcs are about the ship; this one is about the seeker, and
     * it is the only thing bounding a rack that launches in any direction at all.
     * <p>
     * It bites when a direction is NAMED that points away from the target. A launch with no
     * direction aims straight at it, so the target sits at relative bearing 1 and is
     * trivially inside its own forward arc.
     * <p>
     * Owner's ruling 2026-09-21; the F-section citation should be added when that page is to
     * hand.
     *
     * @return a refusal, or null if the seeker can track from there
     */
    private ActionResult seekerArcBlock(Unit launcher, Unit target, int seekerFacing, String what) {
        if (seekerFacing <= 0 || target == null)
            return null;
        int bearing = MapUtils.getBearing(launcher, target);
        if (bearing == 0)
            return null;               // same hex: no bearing exists to judge
        int relative = MapUtils.getRelativeBearing(bearing, seekerFacing);
        if (!ArcUtils.inArc(relative, ArcUtils.FA))
            return ActionResult.fail(what + " launched on direction " + seekerFacing
                    + " cannot track " + target.getName()
                    + " — the target must lie in the seeker's forward arc");
        return null;
    }

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
        int droneFacing = facing > 0 ? facing : MapUtils.getBearing(launcher, target);
        ActionResult droneArc = seekerArcBlock(launcher, target, droneFacing, rack.getName());
        if (droneArc != null)
            return droneArc;
        drone.setFacing(droneFacing);
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
        // TWO different constraints, and they are not the same arc.
        //
        // The TARGET must lie in the launcher's firing arc: a plasma launcher has one, unlike
        // a drone rack, which will send one any way it likes. This test was missing entirely,
        // so a forward launcher could target something dead astern.
        int bearing = MapUtils.getBearing(launcher, target);
        if (bearing > 0) {            // 0 = same hex, where no bearing exists
            int relBearing = MapUtils.getRelativeBearing(bearing, launcher.getFacing());
            if (!ArcUtils.inArc(relBearing, weapon.getArcs()))
                return ActionResult.fail(weapon.getName() + " cannot target "
                        + target.getName() + " — outside launcher arc");
        }
        // The launch DIRECTION, separately, must be one the tube can use. Narrower than the
        // firing arc on real ships: a Romulan KR's Plasma-G launches straight ahead only, yet
        // may target anything in FA. So this is checked against launchDirections and only
        // when a direction is actually named.
        if (facing > 0) {
            int launchDirs = weapon.getLaunchDirections() != 0
                    ? weapon.getLaunchDirections() : weapon.getArcs();
            int relFacing = MapUtils.getRelativeBearing(facing, launcher.getFacing());
            if (!ArcUtils.inArc(relFacing, launchDirs))
                return ActionResult.fail(weapon.getName() + " cannot launch in direction "
                        + facing + " — outside launcher arc");
        }

        int torpFacing = facing > 0 ? facing : MapUtils.getBearing(launcher, target);
        ActionResult torpArc = seekerArcBlock(launcher, target, torpFacing, weapon.getName());
        if (torpArc != null)
            return torpArc;

        PlasmaTorpedo torpedo = weapon.launch();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch");

        // J3.41: launching a seeking weapon voids the launcher's own WW
        if (launcher.hasActiveWildWeasel())
            voidWildWeasel(launcher);

        torpedo.setName(launcher.getName() + "-Plasma-" + game.nextSeekerSeq());
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(torpFacing);
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
        // G13.32: launching at a fully cloaked target is only possible on a
        // retained lock-on — the torpedo inherits that tracking
        if (torpTarget instanceof Ship) {
            com.sfb.systemgroups.CloakingDevice targetCloak = ((Ship) torpTarget).getCloakingDevice();
            if (targetCloak != null && targetCloak.breaksLockOn())
                torpedo.setCloakLockRetained(true);
        }
        seekers.add(torpedo);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, torpedo);

        // G24.1342: launching a plasma torpedo blinds one of the launcher's scout channels.
        game.queueScoutBlinds(launcher, 1);
        game.enterBlindChoiceIfPending();

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

        // A pseudo must be bound by exactly what binds a real one, or the bluff gives itself
        // away: a torpedo launched at an angle no real one could manage could only be pseudo.
        // These three were all missing here, so a pseudo could be thrown anywhere at all.
        int pseudoBearing = MapUtils.getBearing(launcher, target);
        if (pseudoBearing > 0) {
            int relBearing = MapUtils.getRelativeBearing(pseudoBearing, launcher.getFacing());
            if (!ArcUtils.inArc(relBearing, weapon.getArcs()))
                return ActionResult.fail(weapon.getName() + " cannot target "
                        + target.getName() + " — outside launcher arc");
        }
        if (facing > 0) {
            int launchDirs = weapon.getLaunchDirections() != 0
                    ? weapon.getLaunchDirections() : weapon.getArcs();
            int relFacing = MapUtils.getRelativeBearing(facing, launcher.getFacing());
            if (!ArcUtils.inArc(relFacing, launchDirs))
                return ActionResult.fail(weapon.getName() + " cannot launch in direction "
                        + facing + " — outside launcher arc");
        }
        int pseudoFacing = facing > 0 ? facing : pseudoBearing;
        ActionResult pseudoArc = seekerArcBlock(launcher, target, pseudoFacing, weapon.getName());
        if (pseudoArc != null)
            return pseudoArc;

        PlasmaTorpedo torpedo = weapon.launchPseudo();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch pseudo plasma");

        torpedo.setName(launcher.getName() + "-Plasma-" + game.nextSeekerSeq()); // named like a real one — the name
                                                                                 // must not reveal pseudo status
        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(pseudoFacing);
        torpedo.setTarget(target);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(game.getAbsoluteImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);
        List<String> lockLog = game.checkLockOnsForNewUnit(launcher, torpedo);

        // G24.1342: a pseudo launch blinds too — the disguise requires the same signature.
        game.queueScoutBlinds(launcher, 1);
        game.enterBlindChoiceIfPending();

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
        String role = shuttle.specialRole();
        if (role != null)
            return ActionResult.fail(shuttle.getName() + " is prepared as a " + role
                    + " and cannot launch as an ordinary shuttle. A special shuttle reverts"
                    + " only by not being held during Energy Allocation.");

        // C10.13: a shuttle that has committed a point of speed to EM cannot launch above
        // the reduced maximum.
        com.sfb.objects.shuttles.Shuttle launched = bay.launch(shuttle,
                Math.min(speed, shuttle.effectiveMaxSpeed()), facing, game.getAbsoluteImpulse());
        if (launched == null)
            return ActionResult.fail("Shuttle not found in bay");

        launched.setLocation(launcher.getLocation());
        launched.setParentShipName(launcher.getName());
        launched.setOwner(launcher.getOwner());
        launched.setLaunchImpulse(game.getAbsoluteImpulse());
        // Uniform anonymous naming: every launched non-fighter shuttle is
        // "<Ship>-Shuttle-<n>" so the NAME never reveals whether it is an
        // admin shuttle, suicide shuttle, scatter pack, or weasel. Fighters
        // keep their names — a fighter is visibly a fighter.
        if (!(launched instanceof com.sfb.objects.shuttles.Fighter))
            launched.setName(launchName(launcher, launched));
        activeShuttles.add(launched);
        // Everything else that appears on the map mid-turn is acquired here - drones,
        // plasma, suicide shuttles, scatter packs. A plain shuttle was not, so nobody held
        // lock-on to it, not even the ship that had just launched it, and it could not be
        // tractored back aboard (G7.412). The turn-start sweep would have sorted it out at
        // the next turn, which is why this only bit within the launching turn.
        //
        // The Wild Weasel launch deliberately does NOT do this: J3.132 turns the
        // launcher's fire control off and clears its lock-ons, and this would undo that.
        java.util.List<String> lockLog = game.checkLockOnsForNewUnit(launcher, launched);
        String msg = launcher.getName() + " launched shuttle " + launched.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        return ActionResult.ok(msg);
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

        // effectiveMaxSpeed, not getMaxSpeed: the same figure the plain shuttle launch uses,
        // so a suicide shuttle that has committed a point of speed to erratic maneuvers is
        // bounded like any other shuttle (C10.13).
        bay.launch(shuttle, Math.max(0, Math.min(speed, shuttle.effectiveMaxSpeed())),
                facing, game.getAbsoluteImpulse());
        shuttle.setName(launchName(launcher, shuttle));
                                                                                  // hidden
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
        pack.setName(launchName(launcher, pack));
        pack.setLocation(launcher.getLocation());
        // Whose it is, and where it came from. launchShuttle has always set both; this
        // path never did, so a launched pack had no owner at all - which is why it showed
        // no faction and no parent, and why anything keying off ownership (lock-on's
        // own-side rule, lab identification, per-viewer redaction) could not place it.
        pack.setOwner(launcher.getOwner());
        pack.setParentShipName(launcher.getName());
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

        String shipTeam = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
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
        String disembark = disembarkHold(ship, shuttle);
        bay.addShuttle(shuttle);
        activeShuttles.remove(shuttle);
        shuttle.setLocation(null);
        shuttle.setParentShipName(ship.getName());

        StringBuilder msg = new StringBuilder(shuttleName + " landed aboard " + ship.getName()
                + " (J1.61)" + disembark);

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

        String shipTeam = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
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
        String disembark = disembarkHold(ship, shuttle);
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
        return shuttle.getName() + " recovered aboard " + ship.getName() + " (J1.621)" + disembark;
    }

    /**
     * On recovery, the shuttle's passengers disembark into the ship's manifest
     * (survey parties, rescued crew — SH50.46), kept separate from the ship's
     * operational Crew. Returns a "— N … disembarked" log suffix, or "".
     */
    private String disembarkHold(Ship ship, com.sfb.objects.shuttles.Shuttle shuttle) {
        com.sfb.objects.Manifest hold = shuttle.getHold();
        com.sfb.objects.Manifest dest = ship.getManifest();
        StringBuilder moved = new StringBuilder();
        for (com.sfb.properties.PersonnelType t : com.sfb.properties.PersonnelType.values()) {
            int n = hold.transferTo(dest, t, hold.count(t), Integer.MAX_VALUE);
            if (n > 0) {
                if (moved.length() > 0) moved.append(", ");
                moved.append(n).append(' ').append(t.label(n));
            }
        }
        return moved.length() > 0 ? " — " + moved + " disembarked" : "";
    }

    /**
     * SH35.452: declare the J1.621 rotation procedure on a probe canister the
     * ship already holds in a tractor beam. The canister is then pulled one hex
     * closer each impulse (ShuttleMover) and brought aboard on arrival.
     * Releasing the beam cancels the procedure (J1.6221, via releaseTractor).
     */
    ActionResult beginObjectiveRecovery(Ship ship, String objectiveName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Recovery can only be declared during the Activity phase");

        com.sfb.objects.Objective obj = game.getObjectives().stream()
                .filter(o -> o.getName().equalsIgnoreCase(objectiveName))
                .findFirst().orElse(null);
        if (obj == null)
            return ActionResult.fail("Objective not found on the map: " + objectiveName);
        if (obj.getTractoringUnit() != ship)
            return ActionResult.fail(ship.getName() + " must hold " + objectiveName
                    + " in a tractor beam first (J1.621/SH35.452)");
        if (obj.isBeingRecovered())
            return ActionResult.fail(objectiveName + " is already being drawn aboard");

        obj.setBeingRecovered(true);
        return ActionResult.ok(ship.getName() + " begins drawing in " + objectiveName
                + " — pulled one hex closer each impulse (J1.621)");
    }

    /**
     * Final step of the canister's J1.621 recovery: bring it aboard. Uses one
     * bay hatch operation (shared launch/land cooldown, J1.50) because SH35.452
     * says this "counts as the landing of a shuttle for that impulse" — but the
     * canister occupies no shuttle box, so only a ready hatch is needed, not an
     * empty space. Returns the log line, or null if no hatch is ready (the
     * canister holds at Range 0 — J1.6213).
     */
    String completeObjectiveRecovery(Ship ship, com.sfb.objects.Objective objective) {
        int impulse = game.getAbsoluteImpulse();
        com.sfb.systemgroups.ShuttleBay bay = null;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays()) {
            if (b.canLaunch(impulse)) {
                bay = b;
                break;
            }
        }
        if (bay == null)
            return null;

        bay.markUsed(impulse);
        if (ship.getTractors() != null && ship.getTractors().getTractored().contains(objective))
            ship.getTractors().releaseTractor(objective); // clears tractoringUnit + beingRecovered
        objective.setBeingRecovered(false);
        objective.setCarrier(ship);   // now CARRIED — location derives from the carrier
        objective.setLocation(null);
        return objective.getName() + " brought aboard " + ship.getName() + " (J1.621/SH35.452)";
    }
}
