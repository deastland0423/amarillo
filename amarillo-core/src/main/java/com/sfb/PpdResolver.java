package com.sfb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.PPD;
import com.sfb.weapons.PpdSplash;

/**
 * E11.3: PPD firing runs that outlive the impulse they started in.
 *
 * <h2>Why this exists at all</h2>
 * Every other direct-fire weapon resolves where it is fired and is done. A PPD "operates over
 * the current impulse and the next three impulses" (E11.30), so something has to remember the
 * run and pulse it again on impulses nobody fired anything on. That is this class: Game owns
 * the list, as it owns every other pending list, and hands it here by reference.
 *
 * <h2>Where in the impulse</h2>
 * E11.31 fires a PPD "during the Direct-Fire Weapons Segment before any other fire by that
 * side", so {@link #pulseActiveRuns()} runs on the way INTO the direct-fire phase, before a
 * player can declare anything. The volleys it queues are splash volleys, which
 * {@code DamageResolver} resolves ahead of ordinary fire for the same reason (E11.332).
 */
class PpdResolver {

    /** One firing run: a device, the ship that fired it, and the target it cannot change. */
    static class PpdRun {
        final Ship firer;
        final PPD device;
        final Unit target;

        PpdRun(Ship firer, PPD device, Unit target) {
            this.firer = firer;
            this.device = device;
            this.target = target;
        }
    }

    private final Game game;
    private final List<PpdRun> runs;
    private final List<Game.PendingVolley> pendingVolleys;

    PpdResolver(Game game, List<PpdRun> runs, List<Game.PendingVolley> pendingVolleys) {
        this.game = game;
        this.runs = runs;
        this.pendingVolleys = pendingVolleys;
    }

    /** Start tracking a run; the first pulse has already been resolved by the firing path. */
    void beginRun(Ship firer, PPD device, Unit target) {
        runs.add(new PpdRun(firer, device, target));
    }

    /**
     * Fire a PPD: resolve its first pulse and start the run that carries the other three.
     *
     * <p>E11.15 requires active fire control and a lock-on, E11.152 bars passive fire control
     * outright, and E11.312 bars a true range of three or less. The checks are here rather than
     * in {@link PPD} because they are facts about the ship and the map, which the weapon does
     * not know.
     */
    Game.ActionResult fire(Ship firer, PPD device, Unit target) {
        if (device.isPulsing())
            return Game.ActionResult.fail("That PPD is already firing (E11.23).");
        if (!device.isArmed())
            return Game.ActionResult.fail("That PPD is not armed.");
        if (target == null || target.getLocation() == null || firer.getLocation() == null)
            return Game.ActionResult.fail("No target to fire at.");
        if (!firer.isActiveFireControl())
            return Game.ActionResult.fail("A PPD cannot be fired with passive fire control (E11.152).");
        if (!firer.hasLockOn(target))
            return Game.ActionResult.fail("A PPD needs a lock-on to fire (E11.15).");

        int range = MapUtils.getRange(firer, target);
        int damage;
        try {
            damage = device.fire(range);
        } catch (Exception e) {
            return Game.ActionResult.fail(e.getMessage());
        }

        List<String> log = new ArrayList<>();
        PpdRun run = new PpdRun(firer, device, target);
        if (damage > 0)
            log.add(queueSplash(run, range));
        else
            log.add("  " + firer.getName() + " PPD fires — no wave-lock");
        // Tracked even when the run has no pulses left, so the list is the single answer to
        // "what is still pulsing" rather than two answers that can disagree.
        if (device.isPulsing())
            runs.add(run);
        return Game.ActionResult.ok(String.join("\n", log));
    }

    /**
     * Resolve one pulse of every run still under way (E11.30).
     *
     * @return log lines, one per pulse that did something worth saying
     */
    List<String> pulseActiveRuns() {
        List<String> log = new ArrayList<>();
        for (Iterator<PpdRun> it = runs.iterator(); it.hasNext();) {
            PpdRun run = it.next();
            if (!run.device.isPulsing()) {
                it.remove();
                continue;
            }
            String ended = reasonTheRunCannotContinue(run);
            if (ended != null) {
                run.device.shutDown();
                log.add("  " + run.firer.getName() + " PPD run ends — " + ended);
                it.remove();
                continue;
            }

            int range = MapUtils.getRange(run.firer, run.target);
            int damage = run.device.pulse(range);
            if (damage > 0)
                log.add(queueSplash(run, range));
            else
                log.add("  " + run.firer.getName() + " PPD pulse — no wave-lock"
                        + (run.device.isPulsing() ? "" : " (run complete)"));
            if (!run.device.isPulsing())
                it.remove();
        }
        return log;
    }

    /**
     * Why this run must stop, or null to carry on.
     *
     * <p>E11.15 requires active fire control AND a lock-on "for the entire time that the weapon
     * is pulsing", so losing either ends the run rather than merely dropping the wave-lock —
     * the weapon cannot pulse at all without them. A destroyed firer or a target that has left
     * the map ends it for the obvious reasons.
     */
    private String reasonTheRunCannotContinue(PpdRun run) {
        if (run.firer.isDestroyed())
            return "the firing ship is gone";
        if (run.target.getLocation() == null || run.firer.getLocation() == null)
            return "the target is no longer on the map";
        if (!run.firer.isActiveFireControl())
            return "active fire control lost (E11.15)";
        if (!run.firer.hasLockOn(run.target))
            return "lock-on lost (E11.15)";
        return null;
    }

    /**
     * Turn one scoring pulse into a splash volley (E11.35).
     *
     * <p>E11.353 gives a target that never had shields the centre element alone. We ask whether
     * the target IS a Ship: a seeker or a shuttle has no shield track at all, which is the same
     * case. A ship that has merely lost its shields still takes the full splash, as the rule is
     * careful to say.
     */
    private String queueSplash(PpdRun run, int range) {
        String who = run.firer.getName();
        if (!(run.target instanceof Ship targetShip)) {
            int centre = PpdSplash.centreOnly(range);
            pendingVolleys.add(new Game.PendingVolley(
                    who, run.firer, run.target, 0, centre, 0, false, false, "", null));
            return "  " + who + " PPD pulse hits " + run.target.getName()
                    + " for " + centre + " (no shields to splash across, E11.353)";
        }

        int facing = game.getShieldNumber(run.firer, targetShip);
        Map<Integer, Integer> spread = PpdSplash.againstShield(
                range, facing, shield -> targetShip.getShields().getShieldStrength(shield));
        if (spread.isEmpty())
            return "  " + who + " PPD pulse scores nothing at range " + range;

        pendingVolleys.add(new Game.PendingVolley(who, run.firer, targetShip, facing, spread, ""));
        StringBuilder out = new StringBuilder("  " + who + " PPD pulse hits "
                + targetShip.getName() + " — ");
        boolean first = true;
        for (Map.Entry<Integer, Integer> hit : spread.entrySet()) {
            out.append(first ? "" : ", ").append(hit.getValue())
                    .append(" on shield #").append(hit.getKey());
            first = false;
        }
        return out.toString();
    }
}
