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
        /**
         * NOT final, and E11.546 is the only reason.
         *
         * <p>E11.34 is emphatic that a PPD "cannot switch targets" — the run is tied to what it
         * was fired at. The single exception is involuntary: when a planet or other major body
         * comes between the two, "the wave locks onto the object and will damage it... unless
         * firing is stopped". Nothing else may write this field.
         */
        com.sfb.objects.Marker target;
        /** The body a wave-lock was dragged onto (E11.546), so it is announced only once. */
        com.sfb.objects.Terrain lockedOntoBody;

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
        // E11.151: a pulsing PPD occupies a seeking-weapon control channel.
        //
        // Refused rather than allowed to overflow, and NOT because there is nothing to give up
        // — a ship controlling six plasma torpedoes has six things it could give up, and
        // Game.releaseSeekerControl and transferSeekerControl both exist for exactly that. It
        // is refused because the overflow procedure is F3.532's INVOLUNTARY transfer: it would
        // make a voluntary act of firing destroy a torpedo the player never chose to let go.
        // Making room is the player's decision, so they make it first, and the message says so.
        if (firer.getControlUsed() >= firer.getControlCapacity())
            return Game.ActionResult.fail(
                    "No control channel free for the PPD (E11.151): " + firer.getControlUsed()
                            + " of " + firer.getControlCapacity()
                            + " in use. Release or transfer a seeking weapon to make room.");

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
            // E11.546 FIRST, because it is the specific rule for an obstructed line of fire
            // and E11.549 is the general one. The engine already drops a lock-on when a planet
            // comes between two ships (P2.322, LockOnResolver), so asking "has the lock gone?"
            // before "has something come between?" would end every run a planet interrupted —
            // the exact case E11.546 says continues, against the planet.
            String transfer = transferToBlockingBody(run);
            if (transfer != null)
                log.add(transfer);

            String ended = reasonTheRunCannotContinue(run);
            if (ended != null) {
                run.device.shutDown();
                log.add("  " + run.firer.getName() + " PPD run ends — " + ended);
                it.remove();
                continue;
            }

            int range = MapUtils.getRange(run.firer, run.target);
            // E11.53: the shift the pulse faces now, which PPD compares against the one its
            // wave-lock was taken at. Set the same way every other weapon's is.
            run.device.setEcmShift(currentShift(run));
            int damage = run.device.pulse(range);
            if (damage > 0)
                log.add(run.lockedOntoBody != null
                        ? damageBlockingBody(run, range)
                        : queueSplash(run, range));
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
        // The lock-on requirement is against a UNIT. A run dragged onto a planet (E11.546) is
        // no longer pointed at one, and nothing in the engine can hold a lock-on to terrain —
        // the beam is on the obstruction whether the firer likes it or not, and E11.546 says
        // the only way out is to stop firing.
        if (run.lockedOntoBody == null && run.target instanceof Unit unit
                && !run.firer.hasLockOn(unit))
            return "lock-on lost (E11.15)";
        // E11.541: out of arc or out of RANGE breaks the lock outright — unlike closing inside
        // range 4, which merely wastes the pulse (E11.312, E11.521).
        int range = MapUtils.getRange(run.firer, run.target);
        if (range > run.device.getMaxRange())
            return "the target is beyond PPD range (E11.541)";
        if (!bearsOnTarget(run))
            return "the target has moved out of arc (E11.541)";
        // E11.544: the device itself is gone. E11.542 (target destroyed) arrives as the target
        // leaving play, which the location check above already catches.
        if (!run.device.isFunctional())
            return "the PPD box was destroyed (E11.544)";
        return null;
    }

    /**
     * E11.546: a planet or other major body has come between the firer and the target.
     *
     * <p>"In this case the wave locks onto the object and will damage it (if it can be damaged
     * in the scenario rules) unless firing is stopped." So this is NOT a break — the run keeps
     * going, against the obstruction, and the only way out is for the player to shut the device
     * down (E11.362). The lock itself is kept: the beam has not lost anything, it has been
     * dragged onto something nearer.
     *
     * @return a line to log when the transfer happens, else null
     */
    private String transferToBlockingBody(PpdRun run) {
        if (run.lockedOntoBody != null)
            return null;                        // already dragged onto it; announce once
        com.sfb.objects.Terrain body = game.blockingBody(
                run.firer.getLocation(), run.target.getLocation());
        if (body == null)
            return null;

        String was = run.target.getName();
        run.lockedOntoBody = body;
        run.target = body;
        return "  " + run.firer.getName() + " PPD wave-lock drags onto "
                + body.getTerrainType() + " between it and " + was
                + " — it will damage that instead unless firing stops (E11.546)";
    }

    /** Whether the device still bears on its target (D2.0, E11.541). */
    private boolean bearsOnTarget(PpdRun run) {
        int trueBearing = MapUtils.getBearing(
                run.firer.getLocation(), run.target.getLocation());
        return run.device.inArc(MapUtils.getRelativeBearing(trueBearing, run.firer.getFacing()));
    }

    /**
     * The electronic warfare shift this pulse faces (E11.53).
     *
     * <p>Only the shift matters, not the raw ECM: the rule's own example has a target add two
     * ECM points without changing the shift and draw no new roll for it.
     */
    private int currentShift(PpdRun run) {
        com.sfb.properties.EwBreakdown ew = game.ewAgainst(run.firer, run.target);
        int eccm = run.firer.getEccmAllocated() + run.firer.getLentEccm();
        return Game.netEcmShift(ew.total() - eccm);
    }

    /**
     * Turn one scoring pulse into a splash volley (E11.35).
     *
     * <p>E11.353 gives a target that never had shields the centre element alone. We ask whether
     * the target IS a Ship: a seeker or a shuttle has no shield track at all, which is the same
     * case. A ship that has merely lost its shields still takes the full splash, as the rule is
     * careful to say.
     */
    /**
     * Damage the body a wave-lock was dragged onto (E11.546, E11.353).
     *
     * <p>E11.353 puts planets among the targets that "ignore the splash effect and use only the
     * central number", so an obstruction takes the centre element and nothing spreads. Applied
     * straight to the body rather than queued as a volley: a PendingVolley targets a Unit, and
     * terrain is a Marker with its own per-side damage track.
     */
    private String damageBlockingBody(PpdRun run, int range) {
        int damage = PpdSplash.centreOnly(range);
        com.sfb.objects.Terrain body = run.lockedOntoBody;
        java.util.Set<Integer> faces = MapUtils.visiblePlanetSides(
                body.getLocation(), run.firer.getLocation());
        // The face the beam arrives on. Lowest-numbered visible side when several are, which is
        // the same reproducible tie-break the splash uses for equal shields.
        int side = faces.isEmpty() ? 1 : java.util.Collections.min(faces);
        body.addDamage(side, damage);
        return "  " + run.firer.getName() + " PPD pulse strikes the "
                + body.getTerrainType() + " for " + damage + " on side " + side
                + " (E11.546, centre element only per E11.353)";
    }

    private String queueSplash(PpdRun run, int range) {
        String who = run.firer.getName();
        if (!(run.target instanceof Ship targetShip)) {
            int centre = PpdSplash.centreOnly(range);
            pendingVolleys.add(new Game.PendingVolley(
                    who, run.firer, (Unit) run.target, 0, centre, 0, false, false, "", null));
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
