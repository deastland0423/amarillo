package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Marker;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.utilities.MapUtils;

/**
 * Blast effects in a hex and its neighbours: the explosion of a destroyed ship (D5.0) and the
 * shared application of any area blast, which the mine detonation in {@link MineResolver} also
 * goes through.
 *
 * <p>Extracted from Game in the usual way -- shares Game's ships, seekers and activeShuttles
 * collections by reference, and reaches shield geometry and damage through Game's damage API.
 *
 * <h2>Why a mine and an exploding ship share one method</h2>
 * (D5.41)'s last clause and the mine's detonation are the same sentence: the strength "is applied
 * to every unit within the explosion zone on the shield facing the exploding unit(s)". The mine
 * code had it first and had it right, so {@link #applyBlast} is that loop with the two things
 * that actually differ turned into parameters -- the radius, and whether anything is exempt.
 *
 * <h2>What a blast is NOT allowed to do here</h2>
 * {@code Game.markShieldDamage} QUEUES bleed-through as pending internal damage rather than
 * resolving it (6D4). A blast therefore destroys nothing by the time this method returns; it only
 * damages shields and leaves work on the queue. Every consequence of that -- a ship dying of the
 * bleed, and so that ship's own explosion -- happens when the queue is drained. The caller owns
 * that, and {@code DamageResolver.resolveInternalDamage} is where it is done.
 *
 * <h2>Scope of this slice (2026-11-06)</h2>
 * Built: (D5.12) a ship destroyed in combat explodes immediately, (D5.13) who explodes and who
 * does not, (D5.41) the strength-dependent radius and the facing shield, (D5.42) cloaking ignored,
 * and (D5.32) a ship destroyed in another hex exploding in its own right.
 *
 * <p><b>(D5.31) will not be built.</b> Two ships destroyed in the SAME hex are, by the rule, one
 * combined explosion of their summed strength; here they are two separate explosions. The owner
 * ruled it out on 2026-11-06 as too rare to be worth what it costs, and the cost is the point:
 * combining means knowing the whole wave of destructions BEFORE resolving any of them, and this
 * engine cannot know that. Bleed-through is queued rather than applied (see above), so the ships
 * a blast kills are not destroyed until the queue drains -- after the blast that killed them has
 * already been resolved. Honouring (D5.31) would mean holding every explosion back until the
 * damage queue is empty, which reorders the whole of 6D4 for an edge case.
 *
 * <p>So this is settled, not pending: two wrecks in one hex each explode on their own, which
 * over-damages slightly against the rulebook. {@code ShipExplosionTest} pins that behaviour so
 * nobody reads it as a bug and 'fixes' it into a half-combined state.
 *
 * <p>Still open, and these are real gaps rather than decisions: (D5.2)'s docked-unit addition,
 * (D5.14)'s separated section, (D5.55)'s planets and webs, and (D5.11)'s interruption of a
 * declared destruction. See BACKLOG.md.
 *
 * <p>(D5.1)/(D5.5) self-destruction as a player action IS built — {@code Game.declareSelfDestruct}
 * with (D5.51)'s crew restriction and (G15.1)'s Orion exemption, resolved in the Self-Destruction
 * Phase at the top of {@code beginImpulses()}. This comment said it was unbuilt until 2026-11-06,
 * on the same day the backlog recorded it as done, and an outside reviewer read the comment and
 * believed it. A doc claim about what exists is worth no more than the last time somebody checked.
 */
class ExplosionResolver {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;

    ExplosionResolver(Game game, List<Ship> ships, List<Seeker> seekers,
            List<com.sfb.objects.shuttles.Shuttle> activeShuttles) {
        this.game           = game;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
    }

    /**
     * (D5.41) How far a blast of this strength reaches, in hexes.
     *
     * <blockquote>"If the BES is nine points or less, the explosion affects only the hex
     * containing the exploding unit(s). If the BES is ten points or more it affects the hex
     * containing the exploding unit(s) and the six surrounding hexes (radius one)."</blockquote>
     *
     * <p>Ten is the whole of the rule and there is nothing between the two cases, so this is a
     * threshold rather than a curve. It matters more than it looks: most frigates and police
     * ships sit at nine or below and cannot touch an adjacent hex at all, while a heavy cruiser
     * at twenty clears the ring. A destroyer at exactly ten does reach.
     */
    static int blastRadius(int strength) {
        return strength >= 10 ? 1 : 0;
    }

    /**
     * (D5.12) Resolve the explosion of every ship that is destroyed and has not exploded yet.
     *
     * <p>Called with the destroyed ships still on the map, because the blast needs the exploding
     * ship's own hex and every victim needs a bearing to it. Removal is the caller's next step,
     * and it is also what stops a wreck exploding twice: the step that explodes a ship removes
     * it, so it is not here to be found again. A "hasExploded" flag on Ship was tried and
     * deleted -- mutation testing showed no test could fail with it ignored, because removal had
     * already made it unreachable. Anything that separates the two steps must put it back.
     *
     * @return log lines, empty when nothing exploded
     */
    List<String> explodeDestroyedShips() {
        List<String> log = new ArrayList<>();
        List<Ship> exploding = new ArrayList<>();
        for (Ship s : ships) {
            if (s.isDestroyed() && s.getLocation() != null)
                exploding.add(s);
        }
        if (exploding.isEmpty())
            return log;

        for (Ship s : exploding) {
            if (!s.explodes()) {
                // (D5.13). Deliberately silent for a ship with no strength on the chart: it is
                // not an event, and a line per destroyed ground base would be noise.
                continue;
            }
            int bes = s.getExplosionStrength();
            int radius = blastRadius(bes);
            log.add("  " + s.getName() + " EXPLODES at " + s.getLocation() + " — strength " + bes
                    + (radius == 0 ? " (its own hex only, D5.41)"
                                   : " (and the six surrounding hexes, D5.41)"));
            List<Ship> shipsHit = applyBlast(s, radius, bes, log);
            if (shipsHit.isEmpty() && radius == 0)
                log.add("    nothing else in the hex");
        }
        return log;
    }

    /**
     * Apply a blast of {@code damage} to every unit within {@code radius} hexes of {@code origin}.
     *
     * <p>(D5.41): a ship takes it "on the shield facing the exploding unit(s)", which is what
     * {@code origin} being a {@link Marker} buys -- a Marker is a thing with a location and
     * nothing more, so the origin can be a mine, a ship, or a bare hex with no unit in it at all.
     * (D5.31)'s combined explosion will need that last case.
     *
     * <p>(D5.42): cloaking is ignored, and gets no code because nothing here consults it. The
     * flash-cube exposure that follows a MINE blast is (G13.552) and belongs to the mine -- it is
     * written for mine explosions specifically, which is why this returns the ships it hit rather
     * than locking onto them itself.
     *
     * <p>Friendly units are not spared. "Every unit within the explosion zone" is the rule, and
     * the tactic of killing a drone swarm by destroying the ship it is chasing depends on it.
     *
     * @return the ships that took damage, in map order
     */
    List<Ship> applyBlast(Marker origin, int radius, int damage, List<String> log) {
        List<Ship> shipsHit = new ArrayList<>();
        if (origin.getLocation() == null || damage <= 0)
            return shipsHit;

        for (Unit unit : unitsInZone(origin, radius)) {
            if (unit instanceof Ship) {
                Ship ship = (Ship) unit;
                int shieldNum = game.getShieldNumber(origin, ship);
                Game.FireResult result = game.markShieldDamage(ship, shieldNum, damage);
                log.add("    " + ship.getName() + " shield #" + shieldNum + " hit for " + damage
                        + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                shipsHit.add(ship);
            } else {
                log.add("    " + game.applyDamageToUnit(damage, unit, 0));
            }
        }
        return shipsHit;
    }

    /**
     * Every unit in the blast zone: ships, seekers and shuttles alike.
     *
     * <p>Shuttles are in here because a fighter IS a shuttle in this codebase, and fighters in
     * the blast are most of the point -- (D5.13) says they do not explode, not that they are not
     * hurt. Seekers likewise: drones chasing the ship that just died are sitting in its hex.
     *
     * <p>A unit with no location is off-map and unreachable. A DESTROYED ship is skipped, and
     * that one line is carrying two jobs: damaging a wreck means nothing, and it is also what
     * keeps the exploding ship out of its own blast, since it is destroyed by the time the blast
     * is applied. There was an explicit exemption for the centre as well until mutation testing
     * showed neither guard could fail on its own -- two guards for one job, so the general one
     * stayed. (D5.31) wants this too: a ship already destroyed is not a fresh casualty of the
     * explosion it goes up with.
     */
    private List<Unit> unitsInZone(Marker origin, int radius) {
        List<Unit> all = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit)
                all.add((Unit) s);
        }
        all.addAll(activeShuttles);

        List<Unit> inZone = new ArrayList<>();
        for (Unit unit : all) {
            if (unit.getLocation() == null)
                continue;
            if (unit instanceof Ship && ((Ship) unit).isDestroyed())
                continue;
            if (MapUtils.getRange(origin.getLocation(), unit.getLocation()) <= radius)
                inZone.add(unit);
        }
        return inZone;
    }
}
