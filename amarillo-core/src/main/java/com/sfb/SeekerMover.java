package com.sfb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneController;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.MovementUtil;

/**
 * Handles all seeker (drone, plasma torpedo, shuttle) movement and collision
 * resolution each impulse. Extracted from Game to keep Game focused on state
 * and action routing. Holds direct references to Game's final collections so
 * mutations are always visible to both sides.
 */
class SeekerMover {

    private final Game game;
    private final List<Seeker> seekers;
    private final List<Shuttle> activeShuttles;
    private final List<Game.PendingVolley> pendingVolleys;
    private final Map<Unit, Location> prevLocations;

    SeekerMover(Game game,
                List<Seeker> seekers,
                List<Shuttle> activeShuttles,
                List<Game.PendingVolley> pendingVolleys,
                Map<Unit, Location> prevLocations) {
        this.game = game;
        this.seekers = seekers;
        this.activeShuttles = activeShuttles;
        this.pendingVolleys = pendingVolleys;
        this.prevLocations = prevLocations;
    }

    // -------------------------------------------------------------------------
    // Public entry points (called by Game)
    // -------------------------------------------------------------------------

    List<String> moveSeekers() {
        List<String> log = new ArrayList<>();
        // No early exit while a Wild Weasel is on the map: its explosion/expiry
        // state machine below must keep ticking even after every seeker is gone
        // (otherwise a weasel whose pursuers all expired mid-explosion would stay
        // "exploding" — with full ECM — forever).
        boolean wwPresent = activeShuttles.stream()
                .anyMatch(sh -> sh instanceof WildWeaselShuttle);
        if (seekers.isEmpty() && !wwPresent)
            return log;

        int impulse = game.getCurrentImpulse();
        List<Seeker> expired = new ArrayList<>();

        // Order seekers so that a seeker whose target is also a seeker moves after its
        // target. Simple two-pass: targets first, then hunters.
        Set<Seeker> seekerSet = new HashSet<>(seekers);
        List<Seeker> ordered = new ArrayList<>();
        Set<Seeker> placed = new HashSet<>();
        for (Seeker s : seekers) {
            Unit target = (s instanceof Drone) ? ((Drone) s).getTarget() : null;
            if (target instanceof Seeker && seekerSet.contains((Seeker) target) && !placed.contains((Seeker) target)) {
                ordered.add((Seeker) target);
                placed.add((Seeker) target);
            }
            if (!placed.contains(s)) {
                ordered.add(s);
                placed.add(s);
            }
        }

        for (Seeker seeker : ordered) {
            // G7.5: a tractored seeker is held fast — it cannot move itself
            // (position changes only by the holder dragging it) and cannot
            // close on its target while the tractor is maintained.
            if (seeker instanceof Unit && ((Unit) seeker).isTractored())
                continue;
            if (seeker instanceof Drone) {
                Drone drone = (Drone) seeker;
                if (!MovementUtil.moveThisImpulse(impulse, drone.getSpeed()))
                    continue;

                Unit target = drone.getTarget();
                if (target == null) {
                    // Orphaned drone — no guidance, self-destructs immediately
                    log.add("  Drone (" + drone.getDroneType() + ") lost guidance — self-destructed");
                    expired.add(drone);
                    continue;
                }

                int bearing = MapUtils.getGeometricBearing(drone, target);
                int idealFacing = bearing != 0 ? snapToCardinal(bearing) : drone.getFacing();
                drone.setFacing(chooseSeekerFacing(drone, idealFacing));

                prevLocations.putIfAbsent(drone, drone.getLocation());
                drone.goForward(game.getMapCols(), game.getMapRows());

                if (drone.getLocation() == null) {
                    log.add("  Drone (" + drone.getDroneType() + ") moved off the map");
                    expired.add(seeker);
                    continue;
                }

                if (game.isAsteroidHex(drone.getLocation()) || game.isRingHex(drone.getLocation())) {
                    String asteroidResult = applyTerrainCollisionToDrone(drone);
                    log.add(asteroidResult);
                    if (drone.getHull() <= 0) {
                        expired.add(drone);
                        continue;
                    }
                }

                drone.setEndurance(drone.getEndurance() - 1);

                if (target != null && target.getLocation() != null
                        && drone.getLocation().equals(target.getLocation())) {
                    if (target instanceof Drone) {
                        Drone targetDrone = (Drone) target;
                        log.add("  Drone (" + drone.getDroneType() + ") collided with drone ("
                                + targetDrone.getDroneType() + ") — both destroyed");
                        expired.add(seeker);
                        expired.add(targetDrone);
                    } else if (target instanceof WildWeaselShuttle) {
                        WildWeaselShuttle ww = (WildWeaselShuttle) target;
                        if (!ww.isExploding()) {
                            ww.startExplosion(impulse);
                            log.add("  Drone (" + drone.getDroneType() + ") hit Wild Weasel "
                                    + ww.getName() + " — WW exploding for 4 impulses");
                        } else {
                            log.add("  Drone (" + drone.getDroneType()
                                    + ") caught in Wild Weasel explosion — destroyed");
                        }
                        expired.add(seeker);
                    } else if (target instanceof Ship) {
                        queueSeekerImpact(drone, (Ship) target,
                                "Drone (" + drone.getDroneType() + ") impacted " + target.getName(), log);
                        expired.add(seeker);
                    }
                    continue;
                }

                if (drone.getEndurance() <= 0) {
                    log.add("  Drone (" + drone.getDroneType() + ") targeting "
                            + (target != null ? target.getName() : "?") + " ran out of endurance");
                    expired.add(seeker);
                }

            } else if (seeker instanceof ScatterPack) {
                ScatterPack pack = (ScatterPack) seeker;

                // Release check happens every impulse, regardless of movement schedule
                if (!pack.isReleased() && pack.isReadyToRelease(game.getAbsoluteImpulse())) {
                    Unit target = pack.getTarget();
                    Unit controller = pack.getController();
                    // Free the scatter pack's own control channel before drones compete for capacity
                    if (controller instanceof DroneController)
                        ((DroneController) controller).releaseControl(pack);
                    List<Drone> released = pack.release();
                    String launcherName = controller != null ? controller.getName() : null;
                    for (Drone drone : released) {
                        drone.setName((launcherName != null ? launcherName : "SP") + "-Drone-" + game.nextSeekerSeq());
                        drone.setLocation(pack.getLocation());
                        drone.setFacing(pack.getFacing());
                        if (launcherName != null)
                            drone.setLauncherName(launcherName);
                        drone.setLaunchImpulse(game.getAbsoluteImpulse());
                        if (!drone.isSelfGuiding() && controller instanceof DroneController
                                && ((DroneController) controller).hasLockOn(target)) {
                            drone.setTarget(target);
                            drone.setController(controller);
                            // Force-add — overflow interrupt will fire if over limit
                            ((Ship) controller).forceAcquireControl(drone);
                        } else if (drone.isSelfGuiding()) {
                            drone.setTarget(target);
                        }
                        seekers.add(drone);
                    }
                    log.add("  Scatter pack released " + released.size() + " drones at "
                            + (target != null ? target.getName() : "?"));
                    game.checkControlOverflow();
                    // The shuttle stays on the map at speed 0 (recoverable
                    // later). This is a seeker→shuttle TRANSITION, not a
                    // removal — it must NOT go through removeSeekerFromPlay,
                    // which would pull it off the map and kill legitimate
                    // chasers and lock-ons.
                    activeShuttles.add(pack);
                    seekers.remove(pack); // safe: loop iterates a copy
                    continue;
                }

                // Movement: only on scheduled impulses, only while still en route
                if (!pack.isReleased() && MovementUtil.moveThisImpulse(impulse, pack.getSpeed())) {
                    Unit target = pack.getTarget();
                    if (target != null) {
                        int bearing = MapUtils.getGeometricBearing(pack, target);
                        int idealFacing = bearing != 0 ? snapToCardinal(bearing) : pack.getFacing();
                        pack.setFacing(chooseSeekerFacing(pack, idealFacing));
                    }
                    pack.goForward(game.getMapCols(), game.getMapRows());
                    if (pack.getLocation() == null) {
                        log.add("  Scatter pack moved off the map — lost");
                        expired.add(pack);
                    }
                }

            } else if (seeker instanceof SuicideShuttle) {
                SuicideShuttle ss = (SuicideShuttle) seeker;
                if (!MovementUtil.moveThisImpulse(impulse, ss.getSpeed()))
                    continue;

                Unit target = ss.getTarget();
                if (target == null) {
                    log.add("  Suicide shuttle lost guidance — removed");
                    expired.add(ss);
                    continue;
                }
                int bearing = MapUtils.getGeometricBearing(ss, target);
                int idealFacing = bearing != 0 ? snapToCardinal(bearing) : ss.getFacing();
                ss.setFacing(chooseSeekerFacing(ss, idealFacing));
                prevLocations.putIfAbsent(ss, ss.getLocation());
                ss.goForward(game.getMapCols(), game.getMapRows());
                if (ss.getLocation() == null) {
                    log.add("  Suicide shuttle moved off the map");
                    expired.add(ss);
                    continue;
                }
                if (target.getLocation() != null && ss.getLocation().equals(target.getLocation())) {
                    if (target instanceof WildWeaselShuttle) {
                        WildWeaselShuttle ww = (WildWeaselShuttle) target;
                        if (!ww.isExploding()) {
                            ww.startExplosion(impulse);
                            log.add("  Suicide shuttle hit Wild Weasel " + ww.getName()
                                    + " — WW exploding for 4 impulses");
                        } else {
                            log.add("  Suicide shuttle caught in Wild Weasel explosion — destroyed");
                        }
                    } else if (target instanceof Ship) {
                        queueSeekerImpact(ss, (Ship) target,
                                "Suicide shuttle impacted " + target.getName(), log);
                    }
                    expired.add(ss);
                }

            } else if (seeker instanceof PlasmaTorpedo) {
                PlasmaTorpedo torp = (PlasmaTorpedo) seeker;
                if (!MovementUtil.moveThisImpulse(impulse, torp.getSpeed()))
                    continue;

                Unit target = torp.getTarget();
                if (target == null) {
                    log.add("  Plasma torpedo has no target — removed");
                    expired.add(seeker);
                    continue;
                }

                int bearing = MapUtils.getGeometricBearing(torp, target);
                int idealFacing = bearing != 0 ? snapToCardinal(bearing) : torp.getFacing();
                torp.setFacing(chooseSeekerFacing(torp, idealFacing));

                prevLocations.putIfAbsent(torp, torp.getLocation());
                torp.goForward(game.getMapCols(), game.getMapRows());
                torp.incrementDistance();

                if (torp.getLocation() == null) {
                    log.add("  Plasma-" + torp.getPlasmaType() + " moved off the map");
                    expired.add(seeker);
                    continue;
                }

                if (game.isAsteroidHex(torp.getLocation()) || game.isRingHex(torp.getLocation())) {
                    log.add(applyTerrainCollisionToPlasma(torp));
                    if (torp.getCurrentStrength() <= 0) {
                        expired.add(seeker);
                        continue;
                    }
                }

                if (torp.getCurrentStrength() <= 0) {
                    log.add("  Plasma-" + torp.getPlasmaType() + " targeting "
                            + (target != null ? target.getName() : "?") + " dissipated");
                    expired.add(seeker);
                    continue;
                }

                if (target != null && target.getLocation() != null
                        && torp.getLocation().equals(target.getLocation())) {
                    if (target instanceof WildWeaselShuttle) {
                        WildWeaselShuttle ww = (WildWeaselShuttle) target;
                        if (!ww.isExploding()) {
                            ww.startExplosion(impulse);
                            log.add("  Plasma-" + torp.getPlasmaType() + " hit Wild Weasel "
                                    + ww.getName() + " — WW exploding for 4 impulses");
                        } else {
                            log.add("  Plasma-" + torp.getPlasmaType()
                                    + " caught in Wild Weasel explosion — destroyed");
                        }
                    } else if (target instanceof Ship) {
                        Ship ship = (Ship) target;
                        queueSeekerImpact(torp, ship,
                                "Plasma-" + torp.getPlasmaType()
                                        + (torp.isEnveloping() ? " (enveloping)" : "")
                                        + " impacted " + ship.getName(), log);
                    } else {
                        int dmg = torp.impact();
                        String dmgLog = game.applyDamageToUnit(dmg, target, 1);
                        log.add("  Plasma-" + torp.getPlasmaType() + " impacted " + target.getName()
                                + "  " + dmgLog);
                    }
                    expired.add(seeker);
                }
            }
        }

        for (Seeker s : expired)
            game.removeSeekerFromPlay(s);

        // Transition exploding WWs whose 4-impulse window just ended to post-explosion (J3.212)
        for (Shuttle shuttle : activeShuttles) {
            if (shuttle instanceof WildWeaselShuttle) {
                WildWeaselShuttle ww = (WildWeaselShuttle) shuttle;
                if (ww.isExplosionOver(impulse)) {
                    ww.startPostExplosion();
                    log.add("  Wild Weasel " + ww.getName()
                            + " explosion ended — ionized radiation; no ECM; new seekers ignore WW");
                }
            }
        }

        // Natural expiry: post-explosion WW with no seekers still targeting it is removed (J3.212)
        List<WildWeaselShuttle> doneWws = new ArrayList<>();
        for (Shuttle shuttle : activeShuttles) {
            if (shuttle instanceof WildWeaselShuttle) {
                WildWeaselShuttle ww = (WildWeaselShuttle) shuttle;
                if (ww.isPostExplosion()) {
                    boolean anyTargeting = seekers.stream().anyMatch(s -> s.getTarget() == ww);
                    if (!anyTargeting)
                        doneWws.add(ww);
                }
            }
        }
        for (WildWeaselShuttle ww : doneWws) {
            log.add("  Wild Weasel " + ww.getName() + " post-explosion period ended — counter removed");
            game.voidWildWeasel(ww.getParentShip());
        }

        return log;
    }

    List<String> checkSeekerCollisions(Ship ship) {
        List<String> log = new ArrayList<>();
        if (ship.getLocation() == null)
            return log;
        List<Seeker> toRemove = new ArrayList<>();
        for (Seeker seeker : seekers) {
            if (!(seeker instanceof Unit))
                continue;
            Unit unit = (Unit) seeker;
            if (unit.getLocation() == null)
                continue;
            if (!unit.getLocation().equals(ship.getLocation()))
                continue;
            if (seeker.getTarget() != ship)
                continue;
            // G7.5: a held seeker cannot impact while the tractor is maintained,
            // even if the target moves into its hex
            if (unit.isTractored())
                continue;

            String description = seeker instanceof PlasmaTorpedo && ((PlasmaTorpedo) seeker).isEnveloping()
                    ? ship.getName() + " moved into plasma-"
                            + ((PlasmaTorpedo) seeker).getPlasmaType() + " (enveloping)"
                    : ship.getName() + " moved into " + unit.getName();
            queueSeekerImpact(seeker, ship, description, log);
            toRemove.add(seeker);
        }
        for (Seeker s : toRemove)
            game.removeSeekerFromPlay(s);
        return log;
    }

    /**
     * Shared tail for every seeker-vs-ship impact, whichever unit moved into
     * the other's hex: proximity roll (D6.361), G13.37 cloak adjustment, volley
     * queued for the Reinforcement phase, and log line. The impact shield comes
     * from the seeker's direction of travel — it strikes the facing it
     * approached, which also holds when the SHIP moved into the seeker's hex
     * (a position bearing is degenerate at range 0). Enveloping plasma instead
     * wraps the ship: "shield 0", spread across all shields, with the torpedo
     * attached for enveloping resolution.
     */
    private void queueSeekerImpact(Seeker seeker, Ship target, String description, List<String> log) {
        Unit unit = (Unit) seeker;
        boolean enveloping = seeker instanceof PlasmaTorpedo && ((PlasmaTorpedo) seeker).isEnveloping();
        int shieldNum = enveloping ? 0 : getDroneImpactShield(unit, target);
        // P3.33: terrain ECM along the guidance line (guiding ship → target,
        // or the seeker itself → target when self-guided) adds to the target's
        // ECM against the seeker.
        com.sfb.properties.Location guideLoc = seeker.getController() instanceof Ship
                ? ((Ship) seeker.getController()).getLocation()
                : unit.getLocation();
        int terrainEcm = game.terrainEcmAlongLine(guideLoc, target.getLocation());
        int ecmShift = computeSeekerEcmShift(seeker, target, terrainEcm);
        int dmg = adjustForCloak(applyProximityRoll(seeker.impact(), ecmShift, log), target, log);
        String controllerName = seeker.getController() instanceof Ship
                ? seeker.getController().getName()
                : unit.getName();
        String hitMsg = enveloping
                ? "  " + description + "  total damage " + dmg + " spread to all shields"
                : "  " + description + " shield #" + shieldNum + "  damage " + dmg;
        pendingVolleys.add(new Game.PendingVolley(controllerName, null, target,
                shieldNum, dmg, 0, false, false, hitMsg,
                enveloping ? (PlasmaTorpedo) seeker : null));
        log.add(hitMsg + " — queued for Reinforcement phase");
    }

    /**
     * G13.35: a seeker reaching a fully cloaked ship's hex has a substantial
     * chance of not finding the target — its warhead rolls the G13.37 fire
     * adjustment chart. No-op unless the target is fully cloaked.
     */
    private int adjustForCloak(int dmg, Ship target, List<String> log) {
        com.sfb.systemgroups.CloakingDevice cloak = target.getCloakingDevice();
        if (dmg <= 0 || cloak == null || !cloak.breaksLockOn())
            return dmg;
        int die = new com.sfb.utilities.DiceRoller().rollOneDie();
        int scaled = com.sfb.systemgroups.CloakingDevice.fireAdjustedDamage(dmg, die);
        log.add("  Fire adjustment vs cloak (G13.37): die " + die + " → "
                + com.sfb.systemgroups.CloakingDevice.fireAdjustmentLabel(die)
                + (scaled != dmg ? " (" + dmg + " → " + scaled + ")" : ""));
        return scaled;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Snap a 1–24 bearing to the nearest cardinal (1, 5, 9, 13, 17, 21). */
    private static int snapToCardinal(int bearing) {
        int[] cardinals = { 1, 5, 9, 13, 17, 21 };
        int best = cardinals[0];
        int bestDist = Integer.MAX_VALUE;
        for (int c : cardinals) {
            int diff = Math.abs(bearing - c);
            // wrap around the 24-direction circle
            if (diff > 12)
                diff = 24 - diff;
            if (diff < bestDist) {
                bestDist = diff;
                best = c;
            }
        }
        return best;
    }

    /**
     * Choose the cardinal facing for a seeker that avoids planet hexes while
     * staying as close as possible to the ideal bearing toward the target.
     */
    private int chooseSeekerFacing(Unit seeker, int idealFacing) {
        int[] cardinals = { 1, 5, 9, 13, 17, 21 };
        int idealIdx = 0;
        for (int i = 0; i < cardinals.length; i++) {
            if (cardinals[i] == idealFacing) {
                idealIdx = i;
                break;
            }
        }
        // Try offset 0 (ideal) first, then ±1, ±2, ±3 — nearest to target wins
        for (int offset = 0; offset <= 3; offset++) {
            int[] signs = (offset == 0) ? new int[] { 0 } : new int[] { 1, -1 };
            for (int sign : signs) {
                int altIdx = ((idealIdx + sign * offset) % 6 + 6) % 6;
                Location nextHex = MapUtils.getAdjacentHex(seeker.getLocation(),
                        MapUtils.getTrueBearing(1, cardinals[altIdx]), game.getMapCols(), game.getMapRows());
                if (nextHex != null && !game.isPlanetHex(nextHex))
                    return cardinals[altIdx];
            }
        }
        return idealFacing; // completely surrounded — shouldn't happen
    }

    /**
     * Determine which shield a drone hits based on its direction of travel.
     * The drone's facing is where it is going; the hit shield faces the opposite direction.
     */
    private static int getDroneImpactShield(Unit drone, Ship target) {
        int incomingFacing = (drone.getFacing() + 11) % 24 + 1;
        int absShieldFacing = ((incomingFacing - 1) / 4) * 2 + 1;
        int relFacing = MapUtils.getRelativeShieldFacing(absShieldFacing, target.getFacing());
        int shieldNumber = (relFacing % 2 == 0) ? relFacing / 2 : (relFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    /**
     * D6.36: ECM net shift for a seeker impact roll. The guiding ship's ECCM
     * (active FC required) and the weapon's built-in ECCM stack (D6.34 Step 2):
     * shift = floor(sqrt(max(0, targetEcm - controllerEccm - builtInEccm)))
     * TypeVI warp-seekers are immune (D6.38) — returns 0.
     * Package-private for tests.
     */
    static int computeSeekerEcmShift(Seeker seeker, Unit target) {
        return computeSeekerEcmShift(seeker, target, 0);
    }

    /** As above, plus P3.33 terrain ECM along the guidance line (0 = none). */
    static int computeSeekerEcmShift(Seeker seeker, Unit target, int terrainEcm) {
        if (seeker.isWarpSeeker())
            return 0; // TypeVI warp-seekers are immune to EW (D6.38)
        int targetEcm = terrainEcm;
        if (target instanceof Ship) {
            Ship tship = (Ship) target;
            targetEcm += tship.getEcmAllocated() + tship.getWwEcmBonus();
        }
        int controllerEccm = 0;
        Unit controller = seeker.getController();
        if (controller instanceof Ship) {
            Ship cship = (Ship) controller;
            // D19.12: ECCM cannot be *used* under PFC, even if energy was spent on it
            if (cship.isActiveFireControl())
                controllerEccm = cship.getEccmAllocated();
        }
        int netEcm = Math.max(0, targetEcm - controllerEccm - seeker.getBuiltInEccm());
        return (int) Math.floor(Math.sqrt(netEcm));
    }

    /**
     * D6.361: Roll 1d6 + ecmShift against proximity detonation table.
     * 1-6 → 100%, 7-8 → 50%, 9-10 → 25%, 11+ → 0% damage.
     */
    private static int applyProximityRoll(int baseDamage, int ecmShift, List<String> log) {
        if (ecmShift <= 0)
            return baseDamage;
        int roll = new DiceRoller().rollOneDie();
        int total = roll + ecmShift;
        int damage;
        if (total <= 6)
            damage = baseDamage;
        else if (total <= 8)
            damage = baseDamage / 2;
        else if (total <= 10)
            damage = baseDamage / 4;
        else
            damage = 0;
        log.add("    ECM proximity roll: d6=" + roll + " + shift " + ecmShift + " = " + total
                + " → " + damage + " dmg (of " + baseDamage + ")");
        return damage;
    }

    /**
     * Roll terrain collision damage — asteroid (P3.2) or planetary ring
     * (P2.223) — and apply it directly to a drone's hull. Returns a log line;
     * removes the drone from play if hull reaches 0.
     */
    private String applyTerrainCollisionToDrone(Drone drone) {
        // Seeking weapons are not shuttlecraft/fighters, so not nimble (C11 note)
        Game.TerrainHit hit = game.rollTerrainCollision(drone.getLocation(), drone.getSpeed(),
                false, null);
        if (hit == null)
            return "";
        String base = "  Drone (" + drone.getDroneType() + ") enters " + hit.terrainName + " hex"
                + " (speed " + drone.getSpeed() + ", die " + hit.die + (hit.nimble ? " −1 nimble" : "") + ")";
        if (hit.damage == 0)
            return base + " — no damage";
        int remaining = drone.getHull() - hit.damage;
        drone.setHull(Math.max(0, remaining));
        if (drone.getHull() <= 0) {
            seekers.remove(drone);
            if (drone.getController() instanceof DroneController)
                ((DroneController) drone.getController()).releaseControl(drone);
            return base + " — " + hit.damage + " hull damage — destroyed";
        }
        return base + " — " + hit.damage + " hull damage — " + drone.getHull() + " remaining";
    }

    /**
     * Roll terrain collision damage — asteroid (P3.2) or planetary ring
     * (P2.223) — and apply it as phaser damage to a plasma torpedo. Each point
     * reduces strength by 0.5 (same as direct phaser fire). Returns a log line;
     * removes the torpedo if strength reaches 0.
     */
    private String applyTerrainCollisionToPlasma(PlasmaTorpedo torp) {
        // Seeking weapons are not shuttlecraft/fighters, so not nimble (C11 note)
        Game.TerrainHit hit = game.rollTerrainCollision(torp.getLocation(), torp.getSpeed(),
                false, null);
        if (hit == null)
            return "";
        String base = "  Plasma-" + torp.getPlasmaType() + " enters " + hit.terrainName + " hex"
                + " (speed " + torp.getSpeed() + ", die " + hit.die + (hit.nimble ? " −1 nimble" : "") + ")";
        if (hit.damage == 0)
            return base + " — no damage";
        int before = torp.getCurrentStrength();
        torp.applyPhaserDamage(hit.damage);
        int after = torp.getCurrentStrength();
        if (after <= 0) {
            seekers.remove(torp);
            return base + " — " + hit.damage + " phaser pts — destroyed";
        }
        return base + " — " + hit.damage + " phaser pts — strength " + before + " → " + after;
    }
}
