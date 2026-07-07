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
            ship.clearLockOns();
            if (!ship.isActiveFireControl())
                continue; // D6.1143: no fire control = no lock-on
            int sensorRating = ship.getSpecialFunctions().getSensor();

            // Roll for each other ship
            for (Ship target : ships) {
                if (target == ship)
                    continue;
                if (target.getCloakingDevice() != null && target.getCloakingDevice().breaksLockOn()) {
                    lastLockOnLog.add(
                            ship.getName() + " cannot acquire lock-on to " + target.getName() + " (fully cloaked)");
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
     * If the target is cloaked, all lock-ons to it are removed immediately.
     * (Future: G13.332/G13.333 will replace this with a cloaked re-acquisition
     * roll instead of a hard remove.)
     *
     * @param target The ship whose conditions just changed.
     * @return Log lines describing the re-check results.
     */
    List<String> checkLockOnsForUnit(Ship target) {
        List<String> log = new ArrayList<>();

        // If the target is cloaked, no one can lock onto it (D6.111)
        // Future hook: replace this block with cloaked lock-on attempt (G13.332)
        boolean targetCloaked = target.getCloakingDevice() != null
                && target.getCloakingDevice().breaksLockOn();
        if (targetCloaked) {
            for (Ship attacker : ships) {
                if (attacker == target)
                    continue;
                if (attacker.hasLockOn(target)) {
                    attacker.removeLockOn(target);
                    log.add(attacker.getName() + " lost lock-on to " + target.getName() + " (cloaked)");
                }
            }
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
