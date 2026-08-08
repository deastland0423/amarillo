package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.MapUtils;

/**
 * Sensor lock-on (D6.1): turn-start acquisition rolls, mid-turn
 * re-acquisition when a target's conditions change, lock-on for newly
 * launched seekers, and effective-range computation (D6.21/D6.123).
 * Extracted from Game to keep Game focused on state and turn sequencing.
 * Owns the lock-on roll log; Game delegates drainLastLockOnLog to it.
 */
class LockOnResolver {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;
    private final List<String> lastLockOnLog = new ArrayList<>();

    // G13.3322: a retained lock-on is only re-rolled when the equation result
    // has changed. Last computed P per "attacker→target" pair; refreshed on
    // every retention roll, so a later re-cloak always re-baselines it.
    private final java.util.Map<String, Integer> lastRetentionP = new java.util.HashMap<>();

    // P2.322: ship pairs whose line of sight was planet-blocked at the END of
    // the previous movement phase. LOS is evaluated only at phase boundaries —
    // the rule's own passing exemption (both units moving in the same step
    // with sight at start and end never lose lock) means mid-phase flicker is
    // deliberately invisible. Unordered pair keys (names sorted).
    private final java.util.Set<String> losBlockedPairs = new java.util.HashSet<>();

    LockOnResolver(Game game, List<Ship> ships, List<Seeker> seekers,
            List<com.sfb.objects.shuttles.Shuttle> activeShuttles) {
        this.game           = game;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
    }

    /**
     * Sensor Lock-On Phase (D6.1): each ship rolls 1d6 per other unit on the map.
     * Roll ≤ sensor rating → lock-on achieved. Sensor 6 is automatic (always
     * succeeds).
     * Per D6.113, each ship gets only one roll per turn.
     */
    void performLockOnRolls() {
        lastLockOnLog.clear();
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            // G13.402/G13.3321: lock-ons retained on fully cloaked ships survive
            // the turn boundary — snapshot them before the D6.11 fresh-roll clear
            List<Ship> retainedCloaked = new ArrayList<>();
            for (Ship target : ships)
                if (target != ship && isFullyCloaked(target) && ship.hasLockOn(target))
                    retainedCloaked.add(target);
            ship.clearLockOns();
            if (!ship.isActiveFireControl())
                continue; // D6.1143: no fire control = no lock-on
            int sensorRating = ship.getSpecialFunctions().getSensor();

            // Roll for each other ship
            for (Ship target : ships) {
                if (target == ship)
                    continue;
                // G7.412: an attached tractor makes lock-on automatic in both
                // directions — no roll, cannot fail, cloak state irrelevant.
                // (The FC gate above still applies: D6.62 lock-ons need active
                // fire control; the physical link only replaces the sensor roll.)
                if (game.tractorLinkBetween(ship, target)) {
                    ship.addLockOn(target);
                    lastLockOnLog.add(ship.getName() + " lock-on to " + target.getName()
                            + " (automatic — tractor link, G7.412)");
                    continue;
                }
                // P2.322: no lock-on can be held or gained through a planet
                if (game.losBlocked(ship.getLocation(), target.getLocation())) {
                    lastLockOnLog.add(ship.getName() + " cannot acquire lock-on to "
                            + target.getName() + " — planet blocks line of sight (P2.322)");
                    continue;
                }
                if (isFullyCloaked(target)) {
                    if (retainedCloaked.contains(target)) {
                        ship.addLockOn(target);
                        maybeRerollRetention(ship, target, dice); // G13.3322
                    } else {
                        rollReacquisition(ship, target, dice); // G13.333
                    }
                    continue;
                }
                rollLockOn(ship, target, sensorRating, dice);
            }

            // Roll for each seeker already on the map; controller always has lock-on to its
            // own
            for (Seeker seeker : seekers) {
                if (!(seeker instanceof Unit))
                    continue;
                Unit seekerUnit = (Unit) seeker;
                if (seeker.getController() == ship) {
                    ship.addLockOn(seekerUnit); // own seeker — automatic
                    continue;
                }
                rollLockOn(ship, seekerUnit, sensorRating, dice);
            }

            // Roll for each active shuttle on the map (fighters, admin shuttles, etc.)
            for (com.sfb.objects.shuttles.Shuttle shuttle : activeShuttles) {
                if (shuttle.getOwner() == ship.getOwner()) {
                    ship.addLockOn(shuttle); // own-side shuttle — automatic
                    continue;
                }
                rollLockOn(ship, shuttle, sensorRating, dice);
            }
        }
    }

    /**
     * "Flashcube" (G13.401/.552/.57): a fully cloaked ship that suffers ESG or mine
     * damage is momentarily exposed. In the same impulse's Lock-On Stage (6B3), every
     * enemy with line of sight and active fire control that does NOT already hold a
     * lock-on may gain one — the D6.11 acquisition (automatic at sensor 6, else a roll)
     * — and must immediately pass the G13.331 retention roll to keep it. A unit that
     * already had a lock-on keeps it with no new roll (G13.401/.402). The lock-on then
     * persists until the next retention roll like any other cloaked-ship lock-on.
     */
    List<String> resolveFlashcube(Ship cloaked) {
        List<String> log = new ArrayList<>();
        if (!isFullyCloaked(cloaked)) {
            return log; // only a fully cloaked ship is exposed by the flash
        }
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            if (ship == cloaked || !ship.isActiveFireControl() || ship.hasLockOn(cloaked)) {
                continue;
            }
            if (game.losBlocked(ship.getLocation(), cloaked.getLocation())) {
                continue; // no lock-on through a planet (P2.322)
            }
            int sensor = ship.getSpecialFunctions().getSensor();
            if (sensor < 6 && dice.rollOneDie() > sensor) {
                log.add(ship.getName() + " cannot acquire the exposed " + cloaked.getName()
                        + " — sensors too degraded (D6.11)");
                continue;
            }
            int p = retentionProbability(ship, cloaked);
            int roll = dice.rollOneDie();
            if (roll <= p) {
                ship.addLockOn(cloaked);
                log.add(ship.getName() + " locks onto the exposed " + cloaked.getName()
                        + " (flashcube, G13.401; retain roll " + roll + " ≤ " + p + ")");
            } else {
                log.add(ship.getName() + " fails to hold a lock-on to the exposed "
                        + cloaked.getName() + " (roll " + roll + " > " + p + ", G13.331)");
            }
        }
        return log;
    }

    private void rollLockOn(Ship ship, Unit target, int sensorRating, DiceRoller dice) {
        if (sensorRating >= 6) {
            ship.addLockOn(target);
        } else {
            int roll = dice.rollOneDie();
            if (roll <= sensorRating) {
                ship.addLockOn(target);
                lastLockOnLog.add(ship.getName() + " acquired lock-on to " + target.getName()
                        + " (roll " + roll + " \u2264 " + sensorRating + ")");
            } else {
                lastLockOnLog.add(ship.getName() + " failed lock-on to " + target.getName()
                        + " (roll " + roll + " > " + sensorRating + ")");
            }
        }
    }

    // -------------------------------------------------------------------------
    // G13.33 \u2014 retaining a lock-on against a cloaking ship
    // -------------------------------------------------------------------------

    /**
     * G13.331: when a ship completes fade-out, every ship holding a lock-on to
     * it rolls to retain it. Roll one die; retained on roll \u2264 P where
     * P = Sensor \u2212 EW \u2212 RangeFactor + SpeedFactor \u2212 4.
     */
    List<String> rollRetention(Ship cloaked) {
        List<String> log = new ArrayList<>();
        DiceRoller dice = new DiceRoller();
        for (Ship attacker : ships) {
            if (attacker == cloaked || !attacker.hasLockOn(cloaked))
                continue;
            rollRetentionFor(attacker, cloaked, dice, log);
        }
        return log;
    }

    private void rollRetentionFor(Ship attacker, Ship cloaked, DiceRoller dice, List<String> log) {
        int p = retentionProbability(attacker, cloaked);
        lastRetentionP.put(retentionKey(attacker, cloaked), p);
        int roll = dice.rollOneDie();
        if (roll <= p) {
            log.add(attacker.getName() + " RETAINS lock-on to cloaked " + cloaked.getName()
                    + " (die " + roll + " \u2264 " + p + "; G13.331)");
        } else {
            attacker.removeLockOn(cloaked);
            log.add(attacker.getName() + " loses lock-on to cloaked " + cloaked.getName()
                    + " (die " + roll + " > " + p + "; G13.331)");
        }
    }

    /**
     * Turn-start check for a lock-on retained on a still-cloaked ship: re-roll
     * only if the equation result changed since the last roll (G13.3322),
     * otherwise the lock-on is kept without a roll.
     */
    private void maybeRerollRetention(Ship attacker, Ship cloaked, DiceRoller dice) {
        int p = retentionProbability(attacker, cloaked);
        Integer last = lastRetentionP.get(retentionKey(attacker, cloaked));
        if (last != null && last == p) {
            lastLockOnLog.add(attacker.getName() + " keeps lock-on to cloaked " + cloaked.getName()
                    + " (conditions unchanged; G13.3322)");
            return;
        }
        rollRetentionFor(attacker, cloaked, dice, lastLockOnLog);
    }

    /** P = S \u2212 EW \u2212 RF + SF \u2212 4 (G13.331). */
    int retentionProbability(Ship attacker, Ship cloaked) {
        return attacker.getSpecialFunctions().getSensor()
                - ewAdjustment(attacker, cloaked)
                - rangeFactor(MapUtils.getRange(attacker, cloaked))
                + speedFactor(cloaked.getSpeed())
                - 4;
    }

    /** P = S \u2212 EW \u2212 RF + SF \u2212 10 (G13.333) \u2014 reacquiring a lost/never-held lock-on. */
    int reacquisitionProbability(Ship attacker, Ship cloaked) {
        return attacker.getSpecialFunctions().getSensor()
                - ewAdjustment(attacker, cloaked)
                - rangeFactor(MapUtils.getRange(attacker, cloaked))
                + speedFactor(cloaked.getSpeed())
                - 10;
    }

    /**
     * G13.331 EW Adjustment: the D6.34 Step-3 differential (cloaked ship's ECM
     * minus the attacker's ECCM; ECCM requires active fire control, D6.32),
     * pushed through the net-shift chart. A negative differential \u2014 attacker
     * ECCM out-jamming the cloak's ECM \u2014 applies as a negative number, i.e. a
     * retention bonus.
     */
    private static int ewAdjustment(Ship attacker, Ship cloaked) {
        int eccm = attacker.isActiveFireControl() ? attacker.getEccmAllocated() : 0;
        return signedNetEcmShift(cloaked.getEcmAllocated() - eccm);
    }

    /**
     * D6.34 net ECM shift with sign preserved: \u230a\u221a|net|\u230b (the Step-5 chart),
     * negated when the differential is negative (G13.331 EW Adjustment).
     */
    static int signedNetEcmShift(int netEcm) {
        int shift = (int) Math.floor(Math.sqrt(Math.abs(netEcm)));
        return netEcm < 0 ? -shift : shift;
    }

    /**
     * G13.333: turn-start attempt to acquire a lock-on to a fully cloaked ship
     * when none is held. Practically only succeeds against a fast cloaked ship
     * at short range. A success is a retained lock-on thereafter \u2014 its G13.3322
     * baseline is stored so later turn-start checks use the retention rules.
     * (Mid-turn attempts when conditions improve are deferred with EW.)
     */
    private void rollReacquisition(Ship attacker, Ship cloaked, DiceRoller dice) {
        int p = reacquisitionProbability(attacker, cloaked);
        int roll = dice.rollOneDie();
        if (roll <= p) {
            attacker.addLockOn(cloaked);
            lastRetentionP.put(retentionKey(attacker, cloaked),
                    retentionProbability(attacker, cloaked));
            lastLockOnLog.add(attacker.getName() + " RE-ACQUIRES lock-on to cloaked "
                    + cloaked.getName() + " (die " + roll + " \u2264 " + p + "; G13.333)");
        } else {
            lastLockOnLog.add(attacker.getName() + " cannot acquire lock-on to cloaked "
                    + cloaked.getName() + " (die " + roll + " > " + p + "; G13.333)");
        }
    }

    private static String retentionKey(Ship attacker, Ship cloaked) {
        return attacker.getName() + "\u2192" + cloaked.getName();
    }

    /** Range Adjustment Factor by true range (G13.331). */
    static int rangeFactor(int trueRange) {
        if (trueRange == 0)   return -1;
        if (trueRange <= 4)   return 0;
        if (trueRange <= 10)  return 1;
        if (trueRange <= 15)  return 2;
        if (trueRange <= 20)  return 3;
        if (trueRange <= 30)  return 4;
        if (trueRange <= 40)  return 5;
        return 6;
    }

    /** Speed Adjustment Factor by the cloaked ship's maneuver rate (G13.331/C2.42). */
    static int speedFactor(int maneuverRate) {
        if (maneuverRate == 0)   return -2;
        if (maneuverRate <= 4)   return 0;
        if (maneuverRate <= 8)   return 1;
        if (maneuverRate <= 12)  return 2;
        if (maneuverRate <= 15)  return 3;
        if (maneuverRate <= 17)  return 4;
        if (maneuverRate == 18)  return 5;
        return 6;
    }

    // -------------------------------------------------------------------------
    // P2.322 — planets blocking line of sight
    // -------------------------------------------------------------------------

    /**
     * End-of-movement-phase sweep: recompute which ship pairs have planetary
     * surface strictly between them and act on the TRANSITIONS only.
     * Newly blocked → both lock-ons lost (P2.322). Newly cleared → each side
     * without a lock-on rolls to re-acquire, timed as "the Activity Segment of
     * the first impulse after the obstacle has passed" — which is exactly the
     * phase this sweep advances into.
     */
    List<String> sweepPlanetLos() {
        List<String> log = new ArrayList<>();
        if (!game.anyPlanetSurface())
            return log; // fast path — planetless maps pay nothing
        DiceRoller dice = new DiceRoller();
        java.util.Set<String> nowBlocked = new java.util.HashSet<>();

        for (int i = 0; i < ships.size(); i++) {
            for (int j = i + 1; j < ships.size(); j++) {
                Ship a = ships.get(i), b = ships.get(j);
                if (a.getLocation() == null || b.getLocation() == null)
                    continue;
                String key = pairKey(a, b);
                if (game.losBlocked(a.getLocation(), b.getLocation())) {
                    nowBlocked.add(key);
                    boolean hadLock = a.hasLockOn(b) || b.hasLockOn(a);
                    // Idempotent strip: nothing may hold a lock through a planet
                    a.removeLockOn(b);
                    b.removeLockOn(a);
                    if (!losBlockedPairs.contains(key) && hadLock)
                        log.add("Planet blocks line of sight between " + a.getName()
                                + " and " + b.getName() + " — lock-ons lost (P2.322)");
                } else if (losBlockedPairs.contains(key)) {
                    // Obstacle passed — each side may roll to re-acquire
                    reacquireAfterLos(a, b, dice, log);
                    reacquireAfterLos(b, a, dice, log);
                }
            }
        }
        losBlockedPairs.clear();
        losBlockedPairs.addAll(nowBlocked);
        return log;
    }

    /** Re-acquisition roll after a planet clears the line (P2.322 → D6.11). */
    private void reacquireAfterLos(Ship attacker, Ship target, DiceRoller dice, List<String> log) {
        if (!attacker.isActiveFireControl() || attacker.hasLockOn(target))
            return;
        if (game.tractorLinkBetween(attacker, target)) {
            attacker.addLockOn(target);
            log.add(attacker.getName() + " lock-on to " + target.getName()
                    + " (automatic — tractor link, G7.412)");
            return;
        }
        if (isFullyCloaked(target))
            return; // no new lock-on on a cloaked ship (G13.301)
        int sensorRating = attacker.getSpecialFunctions().getSensor();
        int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
        if (roll <= sensorRating) {
            attacker.addLockOn(target);
            log.add(attacker.getName() + " re-acquired lock-on to " + target.getName()
                    + " after clearing the planet (P2.322)");
        } else {
            log.add(attacker.getName() + " failed to re-acquire lock-on to " + target.getName()
                    + " after clearing the planet (rolled " + roll + ", needs ≤" + sensorRating + ")");
        }
    }

    private static String pairKey(Ship a, Ship b) {
        return a.getName().compareTo(b.getName()) <= 0
                ? a.getName() + "|" + b.getName()
                : b.getName() + "|" + a.getName();
    }

    private static boolean isFullyCloaked(Ship ship) {
        return ship.getCloakingDevice() != null && ship.getCloakingDevice().breaksLockOn();
    }

    List<String> drainLastLockOnLog() {
        List<String> copy = new ArrayList<>(lastLockOnLog);
        lastLockOnLog.clear();
        return copy;
    }

    /**
     * Mid-turn lock-on re-check for a specific target (D6.113).
     * Called when a condition changes for {@code target} — e.g. it uncloaks,
     * emerges from behind a planet, etc.
     *
     * Each other ship with active fire control that does NOT already have
     * lock-on rolls to re-acquire. Ships that already have lock-on keep it
     * (no need to re-roll — they haven't lost it).
     *
     * If the target is fully cloaked, no new lock-on can be gained (G13.301);
     * lock-ons retained via G13.331 are untouched. (Future: G13.333 will add
     * the reacquisition attempt against cloaked ships.)
     *
     * @param target The ship whose conditions just changed.
     * @return Log lines describing the re-check results.
     */
    List<String> checkLockOnsForUnit(Ship target) {
        List<String> log = new ArrayList<>();

        // Fully cloaked target: no NEW lock-on can be gained (G13.301) —
        // reacquisition (G13.333) is not yet implemented. Lock-ons retained
        // through the G13.331 retention roll persist untouched.
        if (isFullyCloaked(target)) {
            log.add("No lock-on can be acquired on " + target.getName() + " (fully cloaked; G13.301)");
            return log;
        }

        // Target is visible — ships without lock-on roll to re-acquire (D6.113)
        DiceRoller dice = new DiceRoller();
        for (Ship attacker : ships) {
            if (attacker == target)
                continue;
            if (!attacker.isActiveFireControl())
                continue;
            if (attacker.hasLockOn(target))
                continue; // already locked on — keep it
            // G7.412: an attached tractor makes lock-on automatic — no roll
            if (game.tractorLinkBetween(attacker, target)) {
                attacker.addLockOn(target);
                log.add(attacker.getName() + " lock-on to " + target.getName()
                        + " (automatic — tractor link, G7.412)");
                continue;
            }
            // P2.322: no lock-on through a planet
            if (game.losBlocked(attacker.getLocation(), target.getLocation()))
                continue;

            int sensorRating = attacker.getSpecialFunctions().getSensor();
            int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
            if (roll <= sensorRating) {
                attacker.addLockOn(target);
                log.add(attacker.getName() + " re-acquired lock-on to " + target.getName());
            } else {
                log.add(attacker.getName() + " failed to re-acquire lock-on to " + target.getName()
                        + " (rolled " + roll + ", needs ≤" + sensorRating + ")");
            }
        }
        return log;
    }

    /**
     * Lock-on acquisition for a newly launched seeker (D6.121 / D6.113).
     * <p>
     * The launcher automatically has lock-on to the seeker it just launched.
     * Every other ship with active fire control rolls 1d6 per its sensor rating
     * to acquire lock-on on the new unit (same mechanic as
     * {@link #checkLockOnsForUnit}).
     *
     * @param launcher The ship that launched the new seeker.
     * @param newUnit  The newly launched seeker (drone, plasma, suicide shuttle,
     *                 scatter pack).
     * @return Log lines describing the acquisition results.
     */
    List<String> checkLockOnsForNewUnit(Ship launcher, Unit newUnit) {
        List<String> log = new ArrayList<>();

        // Launcher always has lock-on to its own seeker
        launcher.addLockOn(newUnit);

        // All other ships with active fire control roll to acquire lock-on
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            if (ship == launcher)
                continue;
            if (!ship.isActiveFireControl())
                continue;

            int sensorRating = ship.getSpecialFunctions().getSensor();
            int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
            if (roll <= sensorRating) {
                ship.addLockOn(newUnit);
                log.add(ship.getName() + " acquired lock-on to " + newUnit.getName()
                        + " (rolled " + roll + ", needs ≤" + sensorRating + ")");
            }
        }
        return log;
    }

    /**
     * Compute the effective range from attacker to target (D6.21 + D6.123).
     * Formula: (noLockOn ? trueRange * 2 : trueRange) + scannerAdjustment +
     * cloakBonus
     */
    int getEffectiveRange(Ship attacker, Unit target) {
        int trueRange = MapUtils.getRange(attacker, target);
        boolean hasLock = attacker.hasLockOn(target);
        int base = hasLock ? trueRange : trueRange * 2;
        int scanner = attacker.getSpecialFunctions().getScanner();
        int cloakBonus = 0;
        if (target instanceof Ship) {
            com.sfb.systemgroups.CloakingDevice cloak = ((Ship) target).getCloakingDevice();
            if (cloak != null)
                cloakBonus = cloak.getCloakBonus(game.getAbsoluteImpulse());
        }
        return base + scanner + cloakBonus;
    }
}
