package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.shuttles.Fighter;
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
    /**
     * C10.511: "A unit using EM cannot launch drones, shuttles, fighters, probes (for
     * information or as weapons), PFs, or plasma torpedoes."
     * <p>
     * One helper rather than nine copies, and it sits here rather than on Game because every
     * launch in the rule passes through this class. Two things the rule does NOT bar, both of
     * which this class also offers and neither of which calls this:
     * <ul>
     * <li>{@link #dropChaff} - C10.516 says outright that "a fighter using EM can use chaff".</li>
     * <li>Plasma BOLTS - C10.511's own parenthesis: "plasma bolts are direct-fire weapons and
     *     can be used while under EM at the standard EM penalties." They are fired through the
     *     weapon in the direct-fire path, never through {@link #launchPlasma}, so the carve-out
     *     costs nothing to honour.</li>
     * </ul>
     * Landing and recovery are a different rule (C10.53) and are not touched here.
     * <p>
     * It asks isEmEffective() rather than isUsingEm(), as P3.254 does: C10.24's words are that
     * EM "cannot be CONDUCTED" while a tractor holds the unit, so a held unit may still launch.
     *
     * @return the refusal, or null if this unit is not conducting EM
     */
    private ActionResult emLaunchBlock(Unit launcher, String what) {
        if (launcher == null || !launcher.isEmEffective())
            return null;
        return ActionResult.fail(launcher.getName() + " cannot launch " + what
                + " while conducting Erratic Maneuvers (C10.511)");
    }

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
        ActionResult emBlock = emLaunchBlock(ship, "a wild weasel");
        if (emBlock != null)
            return emBlock;
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

        // Any of the 24 used to be accepted here; only six are directions a unit may face.
        int wwFacing = craftFacing(ship, null, facing);
        if (wwFacing == 0)
            return badFacing(facing);
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
     * The direction a launched CRAFT leaves on — shuttle, fighter, weasel, suicide shuttle
     * or scatter pack.
     *
     * The same six-facings rule as a seeker, and it was missing here: every one of these
     * paths handed its facing straight to ShuttleBay.launch, which sets it verbatim. A launch
     * with no direction named therefore left the craft facing 0, which is not a direction at
     * all and draws as a heading between F and A.
     * <p>
     * With none named, a craft that has a target points at it (snapped, since a bearing runs
     * to 24 and a facing is one of six) and one that has none takes its mother ship's facing,
     * which is what a weasel already did.
     *
     * @return the facing to use, or 0 if a named direction is not a legal facing
     */
    private int craftFacing(Ship launcher, Unit target, int named) {
        if (named > 0)
            return MapUtils.isFacing(named) ? named : 0;
        if (target != null) {
            int snapped = MapUtils.snapToFacing(MapUtils.getBearing(launcher, target));
            if (snapped > 0)
                return snapped;
        }
        return launcher.getFacing();
    }

    /** Shared refusal, so all four craft launches say the same thing. */
    private ActionResult badFacing(int named) {
        return ActionResult.fail("Direction " + named + " is not one of the six a unit may"
                + " face (1, 5, 9, 13, 17, 21)");
    }

    /**
     * The direction a seeker is actually launched on.
     *
     * A unit may only face one of the six directions (MapUtils.FACINGS), seeking weapons
     * included. A named direction must be one of them; with none named, the bearing to the
     * target is SNAPPED, because a bearing answers in 24 directions and a facing is one of
     * six - taking it raw created seekers pointed along directions nothing can face.
     *
     * @return the facing to use, or 0 if the named direction is not a legal facing
     */
    private int launchFacingFor(Unit launcher, Unit target, int named) {
        if (named > 0)
            return MapUtils.isFacing(named) ? named : 0;
        return MapUtils.snapToFacing(MapUtils.getBearing(launcher, target));
    }

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
        ActionResult emBlock = emLaunchBlock(launcher, "a drone");
        if (emBlock != null)
            return emBlock;
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
        ActionResult emBlock = emLaunchBlock(launcher, "a drone");
        if (emBlock != null)
            return emBlock;
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

        return placeLaunchedDrone(launcher, target, rack, drone, facing);
    }

    /**
     * Everything a launched drone needs once its launcher has been cleared to launch it:
     * off the rack, named, placed, faced, aimed, controlled, and onto the map.
     * <p>
     * Shared because a fighter launching from a rail does all of this identically to a ship
     * launching from a rack. The two differ entirely in what they must satisfy BEFORE the
     * launch — a ship's breakdown lockout and cloak against a fighter's one-per-turn limit
     * — and not at all in what a drone then is. The preconditions stay in the two front
     * doors; this is the part that was worth having once.
     *
     * @param launcher the unit that fired it, which becomes its controller
     */
    private ActionResult placeLaunchedDrone(Unit launcher, Unit target, DroneRack rack,
            Drone drone, int facing) {
        rack.getAmmo().remove(drone);
        rack.recordLaunch();
        drone.setName(launcher.getName() + "-Drone-" + game.nextSeekerSeq());
        drone.setLocation(launcher.getLocation());
        int droneFacing = launchFacingFor(launcher, target, facing);
        if (droneFacing == 0 && facing > 0)
            return ActionResult.fail("Direction " + facing + " is not one of the six a unit"
                    + " may face (1, 5, 9, 13, 17, 21)");
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
        List<String> lockLog = launcher instanceof Ship s
                ? game.checkLockOnsForNewUnit(s, drone)
                : java.util.Collections.emptyList();

        String msg = launcher.getName() + " launched " + drone.getDroneType()
                + " drone at " + target.getName();
        if (!lockLog.isEmpty())
            msg += "\n" + String.join("\n", lockLog);
        game.checkControlOverflow();
        return ActionResult.ok(msg);
    }

    /**
     * A fighter launches one of its own drones (J1.31, J4.24).
     * <p>
     * A separate door from the ship's because the gates differ, not because the drone does:
     * a fighter has no breakdown lockout and no cloak, and has instead a limit of ONE drone
     * a turn however many rails it carries (J4.24), and the half-turn wait after its own
     * launch (J1.341) that a ship never serves.
     */
    public ActionResult launchFighterDrone(com.sfb.objects.shuttles.Fighter fighter,
            Unit target, com.sfb.weapons.DroneRail rail, int facing) {
        ActionResult emBlock = emLaunchBlock(fighter, "a drone");
        if (emBlock != null)
            return emBlock;
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        if (target == null)
            return ActionResult.fail("No target");
        if (fighter.isCrippled())
            return ActionResult.fail(fighter.getName()
                    + " is crippled - external weapons are dropped (J1.332)");
        // J1.6202: held in a tractor, a shuttle may not fire, launch, or guide any weapon.
        // Not the same as being shut down for recovery (J1.622) — merely being HELD is
        // enough, and a fighter under tow could otherwise keep throwing drones.
        if (fighter.isTractored())
            return ActionResult.fail(fighter.getName() + " is held in a tractor beam and"
                    + " cannot launch seeking weapons (J1.6202)");
        // J1.341: half a turn after its OWN launch before it may release a seeking weapon.
        int wait = fighter.impulsesUntilSeekers(game.getAbsoluteImpulse());
        if (wait > 0)
            return ActionResult.fail(fighter.getName() + " cannot launch seeking weapons for "
                    + wait + " more impulse" + (wait == 1 ? "" : "s")
                    + " - half a turn since launch (J1.341)");
        // How often, and how many: J4.24's one-a-turn and quarter-turn spacing, J4.241's
        // second drone, and whatever J4.242 exempts this particular fighter from. The
        // fighter judges it, because the answer depends on what IT already launched.
        String rate = fighter.droneLaunchRefusal(target, rail.getDrone(),
                game.getAbsoluteImpulse());
        if (rate != null)
            return ActionResult.fail(rate);
        if (rail == null || !rail.isFunctional())
            return ActionResult.fail("That rail is destroyed");
        Drone drone = rail.getDrone();
        if (drone == null)
            return ActionResult.fail(rail.getName() + " is empty");
        // D6.121: a drone goes at something the launcher has a lock-on to. A fighter holds
        // its own (J1.31 gives it a sensor rating of six); a self-guiding drone finds its
        // own way and needs none.
        if (!drone.isSelfGuiding() && !fighter.hasLockOn(target))
            return ActionResult.fail(fighter.getName() + " has no lock-on to "
                    + target.getName() + " - cannot launch seeking weapons (D6.121)");
        if (!drone.isSelfGuiding() && !fighter.acquireControl(drone))
            return ActionResult.fail(fighter.getName() + " is already guiding all the drones"
                    + " it can (" + fighter.getControlCapacity() + ", J4.25)");

        ActionResult result = placeLaunchedDrone(fighter, target, rail, drone, facing);
        if (result.isSuccess())
            fighter.recordDroneFired(target, drone, game.getAbsoluteImpulse());
        return result;
    }

    /**
     * Launch a plasma torpedo from the given launcher at the target.
     * The launcher must be armed. The torpedo is placed at the launcher's
     * location, faced toward the target, and added to the active seekers list.
     */
    public ActionResult launchPlasma(Ship launcher, Unit target, PlasmaLauncher weapon, boolean fastLoad, int facing) {
        ActionResult emBlock = emLaunchBlock(launcher, "a plasma torpedo");
        if (emBlock != null)
            return emBlock;
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

        int torpFacing = launchFacingFor(launcher, target, facing);
        if (torpFacing == 0 && facing > 0)
            return ActionResult.fail("Direction " + facing + " is not one of the six a unit"
                    + " may face (1, 5, 9, 13, 17, 21)");
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
        ActionResult emBlock = emLaunchBlock(launcher, "a pseudo plasma torpedo");
        if (emBlock != null)
            return emBlock;
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
        int pseudoFacing = launchFacingFor(launcher, target, facing);
        if (pseudoFacing == 0 && facing > 0)
            return ActionResult.fail("Direction " + facing + " is not one of the six a unit"
                    + " may face (1, 5, 9, 13, 17, 21)");
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
        ActionResult emBlock = emLaunchBlock(launcher, "a shuttle");
        if (emBlock != null)
            return emBlock;
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
        int shuttleFacing = craftFacing(launcher, null, facing);
        if (shuttleFacing == 0)
            return badFacing(facing);
        com.sfb.objects.shuttles.Shuttle launched = bay.launch(shuttle,
                Math.min(speed, shuttle.effectiveMaxSpeed()), shuttleFacing, game.getAbsoluteImpulse());
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
        // ...and the other direction, which the line above does not cover: what the new
        // craft can see. A drone fighter needs its own lock-on to launch anything
        // (D6.121), and the turn-start sweep has already been and gone.
        if (launched instanceof com.sfb.objects.shuttles.Fighter fighter)
            game.acquireFighterLockOns(fighter);
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
        ActionResult emBlock = emLaunchBlock(launcher, "a suicide shuttle");
        if (emBlock != null)
            return emBlock;
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
        int suicideFacing = craftFacing(launcher, target, facing);
        if (suicideFacing == 0)
            return badFacing(facing);
        bay.launch(shuttle, Math.max(0, Math.min(speed, shuttle.effectiveMaxSpeed())),
                suicideFacing, game.getAbsoluteImpulse());
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
        ActionResult emBlock = emLaunchBlock(launcher, "a scatter pack");
        if (emBlock != null)
            return emBlock;
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
            return ActionResult.fail("Scatterpack has no drones loaded");
        if (!bay.canLaunch(game.getAbsoluteImpulse()))
            return ActionResult.fail("Shuttle bay on cooldown — once every 2 impulses");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No lock-on to target — cannot launch scatterpack");

        int packFacing = craftFacing(launcher, target, facing);
        if (packFacing == 0)
            return badFacing(facing);

        launcher.forceAcquireControl(pack);

        // effectiveMaxSpeed, like the other three craft launches: a pack is built on a
        // shuttle and carries that shuttle's speed limits, erratic maneuvers included
        // (C10.13, owner's ruling 2026-09-21). The floor matters too — a negative speed
        // asked for is a standing still, not a reverse.
        bay.launch(pack, Math.max(0, Math.min(speed, pack.effectiveMaxSpeed())),
                packFacing, game.getAbsoluteImpulse());
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

        String msg = launcher.getName() + " launched scatterpack ("
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
    // ---------------------------------------------------------------- Balcony (J1.53)

    /**
     * J1.534: "Scatter-packs can be held on the balcony; suicide shuttles and wild weasels
     * cannot."
     * <p>
     * Both questions have to be asked, which is the lesson this method exists to hold. The
     * CLASS catches a craft already converted - a {@link com.sfb.objects.shuttles.WildWeaselShuttle}
     * does not override {@code specialRole()}, so asking only the role let one straight out
     * onto the balcony. The ROLE catches a craft still being prepared - a shuttle charging as
     * a weasel is an ordinary admin shuttle of the same type (J3.18), so asking only the class
     * let THAT one out. Each check alone was a bug; the first version of this slice swapped one
     * for the other and slice two's own test caught it.
     * <p>
     * The scatter pack is the one role the rule allows out there, so it is named as the
     * exception rather than either check being weakened.
     *
     * @return the role that bars it, or null if it may be held outside
     */
    private String barredFromBalcony(com.sfb.objects.shuttles.Shuttle shuttle) {
        if (shuttle instanceof com.sfb.objects.shuttles.ScatterPack)
            return null;
        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle)
            return "suicide shuttle";
        if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return "Wild Weasel";
        return shuttle.specialRole();
    }

    /**
     * Move a craft from a bay out onto that bay's balcony (J1.53).
     * <p>
     * The transfer COSTS A HATCH. J1.53: "Movement from this outside track to and from the
     * hangar bay is limited by (J1.50)", and J1.532 spells out the consequence — "the rate in
     * (J1.50) includes all launch/land and bay/balcony operations; i.e., a given bay cannot
     * land a shuttle and move another one to the balcony during the same two-impulse cycle."
     * Claiming through {@link com.sfb.systemgroups.ShuttleBay#claimHatch} gets that for free:
     * it is the same counter a launch and a recovery draw on, and a tunnel deck's two hatches
     * (J1.58) go on working independently.
     * <p>
     * Only the craft's OWN bay will take it: each balcony belongs to a specific bay (J1.532),
     * so a full balcony cannot be relieved by another bay's spare positions.
     * <p>
     * J1.534 bars two kinds outright: "Scatter-packs can be held on the balcony; suicide
     * shuttles and wild weasels cannot." A scatter pack may be PARKED, but note J1.531 means
     * it cannot be PREPARED out there - that bar belongs with the other parked restrictions.
     */
    ActionResult moveToBalcony(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail(
                    "Shuttles can only be moved to the balcony during the Activity phase");

        com.sfb.systemgroups.ShuttleBay bay = null;
        com.sfb.objects.shuttles.Shuttle shuttle = null;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle sh : b.getInventory())
                if (sh.getName().equalsIgnoreCase(shuttleName)) {
                    bay = b;
                    shuttle = sh;
                }
        if (shuttle == null)
            return ActionResult.fail("No shuttle called " + shuttleName + " aboard "
                    + ship.getName());
        if (!bay.hasBalcony())
            return ActionResult.fail(shuttleName + "'s bay has no balcony (J1.53)");

        // J1.534: "Scatter-packs can be held on the balcony; suicide shuttles and wild
        // weasels cannot."
        //
        // Asked through specialRole() rather than with instanceof, which is what this check
        // used to do and what let a charged weasel straight out onto the balcony: a shuttle
        // being charged as a weasel is still an ADMIN shuttle, same class and same type
        // (J3.18), and specialRole() is the one thing that knows otherwise. A craft already
        // converted to WildWeaselShuttle is caught by the same question.
        //
        // The scatter pack is the one role the rule allows out there, so it is named as the
        // exception rather than the check being weakened.
        String barred = barredFromBalcony(shuttle);
        if (barred != null)
            return ActionResult.fail(shuttleName + " is prepared as a " + barred
                    + " and cannot be held on the balcony (J1.534)");

        if (bay.balconyFree() <= 0)
            return ActionResult.fail(shuttleName + "'s bay has all "
                    + bay.getBalconyPositions() + " balcony positions occupied");

        int impulse = game.getAbsoluteImpulse();
        if (!bay.claimHatch(impulse))
            return ActionResult.fail("The bay's hatch is still cycling — a bay/balcony move"
                    + " draws on the same rate as a launch or a recovery (J1.532)");

        // replaceShuttle(x, null) is the bay's existing idiom for vacating a box.
        bay.replaceShuttle(shuttle, null);
        bay.park(shuttle);
        return ActionResult.ok(shuttleName + " moved out to the balcony ("
                + bay.balconyFree() + " position(s) still free)");
    }

    /**
     * Bring a craft back inside from the balcony (J1.53). Costs a hatch, exactly as the outward
     * move does — the rule limits movement "to and from the hangar bay".
     * <p>
     * It needs a free shuttle box to come back to. A balcony is parking, not storage: J1.416
     * assigns every shuttle aboard to a specific box, and nothing on the balcony holds one.
     */
    ActionResult moveFromBalcony(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail(
                    "Shuttles can only be brought in from the balcony during the Activity phase");

        com.sfb.systemgroups.ShuttleBay bay = null;
        com.sfb.objects.shuttles.Shuttle shuttle = null;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays())
            for (com.sfb.objects.shuttles.Shuttle sh : b.getBalcony())
                if (sh.getName().equalsIgnoreCase(shuttleName)) {
                    bay = b;
                    shuttle = sh;
                }
        if (shuttle == null)
            return ActionResult.fail("No shuttle called " + shuttleName
                    + " on any balcony of " + ship.getName());

        if (bay.getEmptySpaceCount() <= 0)
            return ActionResult.fail(shuttleName + " has no free shuttle box to return to"
                    + " (J1.416)");

        int impulse = game.getAbsoluteImpulse();
        if (!bay.claimHatch(impulse))
            return ActionResult.fail("The bay's hatch is still cycling — a bay/balcony move"
                    + " draws on the same rate as a launch or a recovery (J1.532)");

        bay.unpark(shuttle);
        for (com.sfb.systemgroups.ShuttleSpace sp : bay.getSpaces())
            if (sp.isEmpty()) {
                sp.setShuttle(shuttle);
                break;
            }
        return ActionResult.ok(shuttleName + " brought in from the balcony");
    }

    /**
     * Land a craft from space directly onto a balcony position (J1.532).
     * <p>
     * "A ship can land shuttles (J1.6) on the balcony at any speed (and by any method) that it
     * could land them in the hangar" - so the J1.61 conditions are unchanged - and J1.53 puts
     * this in the FREE half of the rule: "any number (up to the ship's limit) may be landed on
     * or launched from this balcony during a given impulse." No hatch, no cooldown, no limit.
     * That is what lets a carrier recover a whole strike group in one impulse instead of
     * cycling them in one per two impulses.
     * <p>
     * A separate action from {@link #landShuttle} rather than a fallback inside it, because
     * which side of the hatch a returning craft stops on is a real decision: the balcony is
     * instant, but a single rear hull damage point then destroys the craft outright (J1.531).
     * The player makes that call, not the engine.
     * <p>
     * Nothing disembarks here. A craft on the balcony is outside the hull, and J1.531 shuts
     * down every other kind of work on it (no rearming, no repair, no deck crews); letting
     * passengers walk out through a closed hatch would be the odd part, not this.
     */
    ActionResult landOnBalcony(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only land during the Activity phase");

        com.sfb.objects.shuttles.Shuttle shuttle = activeShuttles.stream()
                .filter(sh -> sh.getName().equalsIgnoreCase(shuttleName))
                .findFirst().orElse(null);
        if (shuttle == null)
            return ActionResult.fail("Shuttle not found on the map: " + shuttleName);

        ActionResult ineligible = landingEligibility(ship, shuttle);
        if (ineligible != null)
            return ineligible;

        // J1.534: these may not be HELD outside even if they could land.
        String barred = barredFromBalcony(shuttle);
        if (barred != null)
            return ActionResult.fail(shuttleName + " is prepared as a " + barred
                    + " and cannot be held on the balcony (J1.534)");

        com.sfb.systemgroups.ShuttleBay bay = null;
        boolean anyBalcony = false;
        for (com.sfb.systemgroups.ShuttleBay b : ship.getShuttles().getBays()) {
            if (!b.hasBalcony())
                continue;
            anyBalcony = true;
            if (b.balconyFree() > 0) {
                bay = b;
                break;
            }
        }
        if (!anyBalcony)
            return ActionResult.fail(ship.getName() + " has no balcony (J1.53)");
        if (bay == null)
            return ActionResult.fail("Every balcony position on " + ship.getName()
                    + " is occupied");

        // Deliberately NO claimHatch and no markUsed: J1.53's free half.
        bay.park(shuttle);
        activeShuttles.remove(shuttle);
        shuttle.setLocation(null);
        shuttle.setParentShipName(ship.getName());

        StringBuilder msg = new StringBuilder(shuttleName + " landed on " + ship.getName()
                + "'s balcony (J1.532)");

        // Same housekeeping a hangar landing does: it is off the map, so nothing can still be
        // chasing or holding a lock-on to it.
        java.util.List<Seeker> chasing = new java.util.ArrayList<>();
        for (Seeker sk : seekers)
            if (shuttle.equals(sk.getTarget()))
                chasing.add(sk);
        for (Seeker sk : chasing) {
            msg.append("\n  ").append(sk instanceof Unit ? ((Unit) sk).getName() : "seeker")
                    .append(" lost tracking — target landed");
            game.removeSeekerFromPlay(sk);
        }
        for (Ship s : game.getShips())
            s.removeLockOn(shuttle);
        for (String line : game.orphanSeekersOf(shuttle))
            msg.append("\n  ").append(line);

        return ActionResult.ok(msg.toString());
    }

    /**
     * The J1.61 conditions for a craft in space to come aboard: friendly, same hex, and the
     * ship no faster than the shuttle. Shared by the hangar landing and the balcony landing,
     * because J1.532 says a ship can land on the balcony "at any speed (and by any method)
     * that it could land them in the hangar" - the conditions are the same ones, and two
     * copies of them would drift the moment either changed.
     *
     * @return the refusal, or null if the craft may come aboard
     */
    private ActionResult landingEligibility(Ship ship,
            com.sfb.objects.shuttles.Shuttle shuttle) {
        if (shuttle instanceof com.sfb.objects.shuttles.SuicideShuttle
                || shuttle instanceof com.sfb.objects.shuttles.ScatterPack
                || shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle)
            return ActionResult.fail(
                    "Active suicide shuttles, scatterpacks, and Wild Weasels cannot land aboard (J1.611)");

        // J1.531 also bars the balcony to enemies outright: "enemy shuttles cannot land or be
        // brought down on the balcony". For an unassisted landing this same check covers it.
        String shipTeam = ship.getOwner() != null ? ship.getOwner().getTeamName() : null;
        String shuttleTeam = shuttle.getOwner() != null ? shuttle.getOwner().getTeamName() : null;
        if (shipTeam == null || !shipTeam.equals(shuttleTeam))
            return ActionResult.fail("Only friendly shuttles may land aboard unassisted (J1.61/J1.612)");

        if (shuttle.getLocation() == null || ship.getLocation() == null
                || !shuttle.getLocation().equals(ship.getLocation()))
            return ActionResult.fail(shuttle.getName() + " must be in the same hex as "
                    + ship.getName() + " to land (J1.61)");

        if (ship.getSpeed() > shuttle.getSpeed())
            return ActionResult.fail(ship.getName() + " (speed " + ship.getSpeed()
                    + ") is moving faster than " + shuttle.getName() + " (speed "
                    + shuttle.getSpeed() + ") — cannot land aboard (J1.61)");

        return null;
    }

    ActionResult landShuttle(Ship ship, String shuttleName) {
        if (!game.canLaunchThisPhase())
            return ActionResult.fail("Shuttles can only land during the Activity phase");

        com.sfb.objects.shuttles.Shuttle shuttle = activeShuttles.stream()
                .filter(sh -> sh.getName().equalsIgnoreCase(shuttleName))
                .findFirst().orElse(null);
        if (shuttle == null)
            return ActionResult.fail("Shuttle not found on the map: " + shuttleName);

        ActionResult ineligible = landingEligibility(ship, shuttle);
        if (ineligible != null)
            return ineligible;

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
        bay.addShuttle(shuttle, game.getClock().getTurn());
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
        for (String line : game.orphanSeekersOf(shuttle))
            msg.append("\n  ").append(line);

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
        bay.addShuttle(shuttle, game.getClock().getTurn());
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
        StringBuilder note = new StringBuilder();
        for (String line : game.orphanSeekersOf(shuttle))
            note.append("\n  ").append(line);
        return shuttle.getName() + " recovered aboard " + ship.getName() + " (J1.621)"
                + disembark + note;
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
