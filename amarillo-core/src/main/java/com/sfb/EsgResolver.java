package com.sfb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneController;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.ESG;
import com.sfb.weapons.Weapon;

/**
 * Expanding Sphere Generator fields (G23.0). Each impulse, for every active ESG,
 * the ring of hexes at its radius is recomputed around the generating ship's
 * current position (the field moves with the ship, G23.45), and any unit that
 * ENTERS a ring hex this impulse takes damage (G23.51). Ships take it on the
 * facing shield; drones are destroyed; the field's strength depletes and it
 * collapses at 0 or after 32 impulses (G23.32). Plasma torpedoes are immune
 * (G23.81).
 *
 * <p>Slice 1: entry is detected from the ship's and unit's positions this
 * impulse vs. the start of the impulse (shared {@code prevLocations}); damage is
 * split sequentially rather than by the G23.52 priority order.
 */
class EsgResolver {

    private final Game game;
    private final List<Ship> ships;
    private final List<Seeker> seekers;
    private final List<Shuttle> activeShuttles;
    private final List<com.sfb.objects.SpaceMine> mines;
    private final Map<Unit, Location> prevLocations;

    EsgResolver(Game game, List<Ship> ships, List<Seeker> seekers, List<Shuttle> activeShuttles,
                List<com.sfb.objects.SpaceMine> mines, Map<Unit, Location> prevLocations) {
        this.game           = game;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
        this.mines          = mines;
        this.prevLocations  = prevLocations;
    }

    /** A live ESG field: its generating ship and the generator. */
    private static final class Field {
        final Ship ship;
        final ESG  esg;
        Field(Ship ship, ESG esg) { this.ship = ship; this.esg = esg; }
    }

    /** Process every active ESG field for this impulse. Returns log lines. */
    List<String> processFields() {
        List<String> log = new ArrayList<>();
        int impulse = game.getAbsoluteImpulse();

        // Pass 1a: expire old fields (deactivation precedes activation, G23.711), then
        // release announced ones — keeping the survivors and the just-formed fields
        // apart for the overlap check below.
        List<Field> preExisting  = new ArrayList<>();
        List<Field> justReleased = new ArrayList<>();
        for (Ship ship : ships) {
            if (ship.getLocation() == null) {
                continue;
            }
            for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof ESG)) {
                    continue;
                }
                ESG esg = (ESG) w;
                // Release a previously-announced field (G23.31). It forms now but does
                // NOT damage anything this impulse — only units that ENTER on a later
                // impulse are hit (G23.56/.46).
                if (esg.readyToRelease(impulse)) {
                    esg.release(impulse);
                    if (esg.isActive()) {
                        justReleased.add(new Field(ship, esg));
                    } else {
                        log.add("  " + ship.getName() + "'s ESG released with no stored energy — no field (G23.3121)");
                    }
                    continue;
                }
                if (!esg.isActive()) {
                    continue;
                }
                if (esg.isExpired(impulse)) {
                    esg.deactivate();
                    esg.recordDrop(impulse);
                    log.add("  " + ship.getName() + "'s ESG field collapsed — 32 impulses elapsed (G23.32)");
                    continue;
                }
                preExisting.add(new Field(ship, esg));
            }
        }

        // Pass 1b: G23.71/.712 — the spheres of two different ships cannot overlap or be
        // contained. A field released into an already-active enemy field is the "second"
        // and collapses (energy lost); two forming into each other simultaneously both
        // fail. Same-ship fields are exempt (G23.12 — they operate independently).
        java.util.Set<ESG> failed = new java.util.HashSet<>();
        for (int i = 0; i < justReleased.size(); i++) {
            Field jr = justReleased.get(i);
            for (Field pe : preExisting) {
                if (pe.ship != jr.ship && discsOverlap(jr, pe)) {
                    failed.add(jr.esg); // the just-formed field is the second one
                }
            }
            for (int j = i + 1; j < justReleased.size(); j++) {
                Field jr2 = justReleased.get(j);
                if (jr2.ship != jr.ship && discsOverlap(jr, jr2)) {
                    failed.add(jr.esg);
                    failed.add(jr2.esg);
                }
            }
        }
        for (Field jr : justReleased) {
            if (failed.contains(jr.esg)) {
                jr.esg.deactivate();
                jr.esg.recordDrop(impulse); // never formed; counts as dropped (G23.3121/.323)
                log.add("  " + jr.ship.getName()
                        + "'s ESG field could not form — it would overlap another ship's field (G23.712)");
            } else {
                log.add("  " + jr.ship.getName() + "'s ESG field formed at radius "
                        + jr.esg.getRadius() + " — strength " + jr.esg.getStrength() + " (G23.44)");
            }
        }

        List<Field> active = new ArrayList<>(preExisting);
        for (Field jr : justReleased) {
            if (jr.esg.isActive()) {
                active.add(jr);
            }
        }

        // Pass 2: ESG-vs-ESG (G23.73) — fields from different ships whose rings share a
        // hex damage each other. This is priority 2 in G23.52, ahead of units (step 5).
        resolveFieldVsField(active, impulse, log);

        // Pass 3: each ship's surviving fields damage the units that entered them,
        // resolved outermost-ring-first and combined into one volley per unit
        // (G23.122 encounter order + G23.75 double-ram).
        for (Ship ship : ships) {
            List<ESG> shipFields = new ArrayList<>();
            for (Field f : active) {
                if (f.ship == ship && f.esg.isActive()) {
                    shipFields.add(f.esg);
                }
            }
            if (!shipFields.isEmpty()) {
                resolveShipFields(ship, shipFields, impulse, log);
            }
        }
        return log;
    }

    /**
     * Resolve ESG-vs-ESG interactions (G23.73): two fields generated by different ships
     * whose rings occupy a common hex strike each other, each reducing the other by its
     * strength (the smaller collapses; equal fields destroy each other). Fields on the
     * same ship do not interact this way (G23.12 — they operate independently).
     */
    private void resolveFieldVsField(List<Field> active, int impulse, List<String> log) {
        for (int i = 0; i < active.size(); i++) {
            Field a = active.get(i);
            if (!a.esg.isActive()) {
                continue;
            }
            for (int j = i + 1; j < active.size(); j++) {
                Field b = active.get(j);
                if (!b.esg.isActive() || a.ship == b.ship) {
                    continue;
                }
                if (!ringsIntersect(a.ship.getLocation(), a.esg.getRadius(),
                                    b.ship.getLocation(), b.esg.getRadius())) {
                    continue;
                }
                int as = a.esg.getStrength();
                int bs = b.esg.getStrength();
                a.esg.absorbDamage(bs); // each field takes the other's strength (G23.73)
                b.esg.absorbDamage(as);
                if (!a.esg.isActive()) { a.esg.recordDrop(impulse); }
                if (!b.esg.isActive()) { b.esg.recordDrop(impulse); }
                log.add("  " + a.ship.getName() + "'s and " + b.ship.getName()
                        + "'s ESG fields struck each other (" + as + " vs " + bs + ", G23.73)");
                if (!a.esg.isActive()) {
                    break; // this field is gone — move to the next one
                }
            }
        }
    }

    /**
     * True if two fields' spheres (filled discs, not just the rings) overlap or one
     * contains the other (G23.71) — i.e. the centers are within the sum of the radii.
     */
    private boolean discsOverlap(Field a, Field b) {
        Location la = a.ship.getLocation();
        Location lb = b.ship.getLocation();
        if (la == null || lb == null) {
            return false;
        }
        return MapUtils.getRange(la, lb) <= a.esg.getRadius() + b.esg.getRadius();
    }

    /**
     * True if the radius-{@code rA} ring around {@code cA} and the radius-{@code rB} ring
     * around {@code cB} share at least one hex (G23.73). Scans A's ring and tests each
     * hex against B's range.
     */
    static boolean ringsIntersect(Location cA, int rA, Location cB, int rB) {
        if (cA == null || cB == null) {
            return false;
        }
        for (int x = cA.getX() - rA; x <= cA.getX() + rA; x++) {
            for (int y = cA.getY() - rA; y <= cA.getY() + rA; y++) {
                Location h = new Location(x, y);
                if (MapUtils.getRange(cA, h) == rA && MapUtils.getRange(cB, h) == rB) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Resolve all of one ship's fields against the units that entered them this impulse.
     * Fields are taken outermost-ring-first, since a closing target meets the larger ring
     * first (G23.122), and each field must be fully resolved before the next — so a unit
     * a field destroys is out of reach of the inner fields. The damage a unit takes from
     * this ship's several fields is combined into a single volley (G23.75).
     */
    private void resolveShipFields(Ship ship, List<ESG> fields, int impulse, List<String> log) {
        fields.sort((x, y) -> Integer.compare(y.getRadius(), x.getRadius())); // outer ring first (G23.122)

        Location shipNow  = ship.getLocation();
        Location shipPrev = prevLocations.getOrDefault(ship, shipNow);
        List<Unit> targets = collectTargets(ship);

        // Combined damage per unit across this ship's fields — one volley each (G23.75).
        Map<Unit, Integer> combined = new LinkedHashMap<>();

        for (ESG esg : fields) {
            int r = esg.getRadius();

            // G23.653 (priority step 3): a field that strikes a planet or moon is spread
            // over too wide an area — it collapses entirely, doing no damage to the planet.
            if (strikesPlanet(shipNow, r)) {
                esg.deactivate();
                esg.recordDrop(impulse);
                log.add("  " + ship.getName() + "'s ESG field struck a planet and collapsed (G23.653)");
                continue;
            }

            // G23.61 (priority step 4, ahead of units): the field detonates active mines
            // it sweeps over, spending strength and possibly collapsing before it reaches
            // any units.
            detonateMines(ship, esg, r, shipNow, shipPrev, impulse, log);
            if (!esg.isActive()) {
                continue; // field spent itself on the mines
            }

            List<Entrant> entrants = new ArrayList<>();
            for (Unit unit : targets) {
                if (unit.getLocation() == null) {
                    continue;
                }
                int already = combined.getOrDefault(unit, 0);
                int cap = capToDestroy(unit);
                if (already >= cap) {
                    continue; // already destroyed by an outer field (G23.122)
                }
                int rPrev = MapUtils.getRange(shipPrev, prevLocations.getOrDefault(unit, unit.getLocation()));
                int rNow  = MapUtils.getRange(shipNow, unit.getLocation());
                if (entersRing(rPrev, rNow, r)) {
                    entrants.add(new Entrant(unit, cap - already)); // remaining cap after outer fields
                }
            }
            if (entrants.isEmpty()) {
                continue;
            }

            // G23.52: the field scores one point on each entrant in turn, smallest size-
            // class first, capped at what destroys each, until spent or all destroyed.
            entrants.sort(Comparator.comparingLong(e -> sizeKey(e.unit)));
            int[] caps = new int[entrants.size()];
            for (int i = 0; i < caps.length; i++) {
                caps[i] = entrants.get(i).cap;
            }
            int[] dmg = roundRobin(esg.getStrength(), caps);
            int spent = 0;
            for (int i = 0; i < dmg.length; i++) {
                spent += dmg[i];
                if (dmg[i] > 0) {
                    combined.merge(entrants.get(i).unit, dmg[i], Integer::sum);
                }
            }
            esg.absorbDamage(spent);
            if (!esg.isActive()) {
                esg.recordDrop(impulse); // strength spent → reactivation lockout (G23.323)
            }
        }

        for (Map.Entry<Unit, Integer> e : combined.entrySet()) {
            applyCombinedDamage(ship, e.getKey(), e.getValue(), log);
        }
    }

    /** True if any hex of the radius-{@code r} ring around {@code shipNow} lies in a planet footprint (G23.653). */
    private boolean strikesPlanet(Location shipNow, int r) {
        if (shipNow == null) {
            return false;
        }
        for (int x = shipNow.getX() - r; x <= shipNow.getX() + r; x++) {
            for (int y = shipNow.getY() - r; y <= shipNow.getY() + r; y++) {
                Location h = new Location(x, y);
                if (MapUtils.getRange(shipNow, h) == r && game.isPlanetHex(h)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * G23.61: the field detonates every active, real mine it sweeps over this impulse.
     * Each mine's strength is absorbed by the field; any overflow (mine stronger than the
     * remaining field) spills onto the ESG ship's facing shield (G23.61) — the explosion
     * hits no other unit. Mines are taken smallest-first (G23.6112).
     */
    private void detonateMines(Ship ship, ESG esg, int r, Location shipNow, Location shipPrev,
                               int impulse, List<String> log) {
        if (mines.isEmpty()) {
            return;
        }
        List<com.sfb.objects.SpaceMine> touched = new ArrayList<>();
        for (com.sfb.objects.SpaceMine mine : mines) {
            if (!mine.isActive() || !mine.isReal() || mine.getLocation() == null) {
                continue;
            }
            int rPrev = MapUtils.getRange(shipPrev, mine.getLocation());
            int rNow  = MapUtils.getRange(shipNow, mine.getLocation());
            if (entersRing(rPrev, rNow, r)) {
                touched.add(mine);
            }
        }
        if (touched.isEmpty()) {
            return;
        }
        touched.sort(Comparator.comparingInt(m -> m.getMineType().damage));
        for (com.sfb.objects.SpaceMine mine : touched) {
            int mineDmg  = mine.getMineType().damage;
            int absorbed = Math.min(mineDmg, esg.getStrength());
            esg.absorbDamage(absorbed);
            int overflow = mineDmg - absorbed;
            int shieldNum = game.getShieldNumber(mine, ship);
            if (overflow > 0) {
                game.markShieldDamage(ship, shieldNum, overflow);
            }
            log.add("  " + ship.getName() + "'s ESG field detonated a " + mine.getMineType().label
                    + " (" + mineDmg + " dmg — field absorbed " + absorbed
                    + (overflow > 0 ? ", " + overflow + " to shield #" + shieldNum : "") + ", G23.61)");
        }
        mines.removeAll(touched);
        if (!esg.isActive()) {
            esg.recordDrop(impulse); // spent itself on the mines
        }
    }

    /** Units an ESG can hit: other ships, drones/seeker-shuttles, admin/WW shuttles. Plasma is immune (G23.81). */
    private List<Unit> collectTargets(Ship esgShip) {
        List<Unit> targets = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit && !(s instanceof PlasmaTorpedo)) {
                targets.add((Unit) s);
            }
        }
        targets.addAll(activeShuttles);
        targets.remove(esgShip); // a ship is not hit by its own field
        return targets;
    }

    /** One entrant sharing a field's strength this impulse (G23.52). */
    private static final class Entrant {
        final Unit unit;
        final int  cap;   // remaining damage that destroys it (MAX_VALUE for ships, G23.511)
        Entrant(Unit unit, int cap) {
            this.unit = unit;
            this.cap  = cap;
        }
    }

    /** Damage needed to destroy a unit; a ship is effectively immune to destruction (G23.511). */
    private static int capToDestroy(Unit unit) {
        if (unit instanceof Drone)   return Math.max(1, ((Drone) unit).getHull());
        if (unit instanceof Shuttle) return Math.max(1, ((Shuttle) unit).getCurrentHull());
        return Integer.MAX_VALUE;
    }

    /**
     * Sort key for the smallest-first share order (G23.52): drones, then shuttles/
     * fighters, then ships (larger size class = smaller hull = earlier). Ties within a
     * band keep list order — the rulebook's die-roll tie-break (G23.52) is not modelled.
     */
    private static long sizeKey(Unit unit) {
        if (unit instanceof Drone)   return 0;
        if (unit instanceof Shuttle) return 1_000;
        if (unit instanceof Ship)    return 2_000 - ((Ship) unit).getSizeClass();
        return 3_000;
    }

    /**
     * G23.52 share order: one point per pass to each not-yet-destroyed unit, in the
     * given (smallest-first) order, each capped at {@code caps[i]}, repeating until the
     * field's {@code strength} is spent or every unit is destroyed. Returns the points
     * dealt to each unit (parallel to {@code caps}).
     */
    static int[] roundRobin(int strength, int[] caps) {
        int[] dmg = new int[caps.length];
        boolean[] destroyed = new boolean[caps.length];
        int remaining = strength;
        int live = caps.length;
        while (remaining > 0 && live > 0) {
            for (int i = 0; i < caps.length && remaining > 0; i++) {
                if (destroyed[i]) {
                    continue;
                }
                dmg[i]++;
                remaining--;
                if (dmg[i] >= caps[i]) {
                    destroyed[i] = true;
                    live--;
                }
            }
        }
        return dmg;
    }

    /**
     * True if a unit whose range to the ESG ship was {@code rPrev} at the start of
     * the impulse and {@code rNow} at the end has ENTERED the radius-{@code r} ring
     * this impulse — either landing on it or crossing it (G23.51/.571). A unit
     * already on the ring at the start of the impulse is not re-entering. Because
     * ships move one hex at a time, a single mover changes the range by at most one;
     * only combined same-impulse movement can jump the ring, and that still counts
     * as entering (the target cannot "jump" the field unharmed, G23.571).
     */
    static boolean entersRing(int rPrev, int rNow, int r) {
        if (rPrev == r) {
            return false; // already in the field at the start of the impulse
        }
        return Math.min(rPrev, rNow) <= r && r <= Math.max(rPrev, rNow);
    }

    /**
     * Apply a unit's combined ESG damage from one ship this impulse as a single volley
     * (G23.75): ship → facing shield, seeker/shuttle → destroyed or wounded.
     */
    private void applyCombinedDamage(Ship esgShip, Unit unit, int dmg, List<String> log) {
        if (dmg <= 0) {
            return;
        }
        boolean destroyed = dmg >= capToDestroy(unit);

        if (unit instanceof Ship) {
            // The field scores on the shield facing the generating ship (G23.513).
            Ship target = (Ship) unit;
            int shieldNum = game.getShieldNumber(esgShip, target);
            game.markShieldDamage(target, shieldNum, dmg);
            log.add("  " + esgShip.getName() + "'s ESG field struck " + target.getName()
                    + " (shield #" + shieldNum + ", " + dmg + " points, G23.51)");
            // G23.62/G13.57: ESG damage exposes a cloaked ship to lock-on this impulse.
            log.addAll(game.flashcubeLockOn(target));

        } else if (unit instanceof Drone) {
            Drone drone = (Drone) unit;
            if (destroyed) {
                seekers.remove(drone);
                if (drone.getController() instanceof DroneController) {
                    ((DroneController) drone.getController()).releaseControl(drone);
                }
                log.add("  " + esgShip.getName() + "'s ESG field destroyed a drone (G23.51)");
            } else {
                log.add("  " + esgShip.getName() + "'s ESG field hit a drone for " + dmg);
            }

        } else if (unit instanceof Shuttle) {
            Shuttle shuttle = (Shuttle) unit;
            if (destroyed) {
                game.removeShuttleFromPlay(shuttle, "destroyed by ESG field");
                log.add("  " + esgShip.getName() + "'s ESG field destroyed " + shuttle.getName() + " (G23.51)");
            } else {
                shuttle.setCurrentHull(shuttle.getCurrentHull() - dmg);
                log.add("  " + esgShip.getName() + "'s ESG field hit " + shuttle.getName() + " for " + dmg);
            }
        }
    }
}
