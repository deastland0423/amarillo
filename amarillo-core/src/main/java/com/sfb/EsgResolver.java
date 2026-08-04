package com.sfb;

import java.util.ArrayList;
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
    private final Map<Unit, Location> prevLocations;

    EsgResolver(Game game, List<Ship> ships, List<Seeker> seekers,
                List<Shuttle> activeShuttles, Map<Unit, Location> prevLocations) {
        this.game           = game;
        this.ships          = ships;
        this.seekers        = seekers;
        this.activeShuttles = activeShuttles;
        this.prevLocations  = prevLocations;
    }

    /** Process every active ESG field for this impulse. Returns log lines. */
    List<String> processFields() {
        List<String> log = new ArrayList<>();
        int impulse = game.getAbsoluteImpulse();

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
                        log.add("  " + ship.getName() + "'s ESG field formed at radius "
                                + esg.getRadius() + " — strength " + esg.getStrength() + " (G23.44)");
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
                processField(ship, esg, log);
            }
        }
        return log;
    }

    private void processField(Ship ship, ESG esg, List<String> log) {
        int r = esg.getRadius();
        Location shipNow  = ship.getLocation();
        Location shipPrev = prevLocations.getOrDefault(ship, shipNow);

        // Candidate targets: ships, drones and seeker-shuttles, and admin/WW
        // shuttles. Plasma is immune (G23.81); the generating ship is not hit by
        // its own field.
        List<Unit> targets = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit && !(s instanceof PlasmaTorpedo)) {
                targets.add((Unit) s);
            }
        }
        targets.addAll(activeShuttles);

        for (Unit unit : targets) {
            if (unit == ship || unit.getLocation() == null || unit instanceof PlasmaTorpedo) {
                continue;
            }
            Location unitNow  = unit.getLocation();
            Location unitPrev = prevLocations.getOrDefault(unit, unitNow);

            // Entered the field this impulse: landed on a ring hex, or crossed it as
            // the unit and/or the field-carrying ship moved (G23.45/.51). When both
            // move toward each other the range can jump the ring in one step — the
            // target still cannot "jump" the field unharmed (G23.571).
            int rPrev = MapUtils.getRange(shipPrev, unitPrev);
            int rNow  = MapUtils.getRange(shipNow, unitNow);
            if (entersRing(rPrev, rNow, r)) {
                applyDamage(ship, esg, unit, log);
                if (!esg.isActive()) {
                    esg.recordDrop(game.getAbsoluteImpulse()); // strength spent → reactivation lockout (G23.323)
                    break; // field spent
                }
            }
        }
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

    private void applyDamage(Ship esgShip, ESG esg, Unit unit, List<String> log) {
        int strength = esg.getStrength();

        if (unit instanceof Ship) {
            Ship target = (Ship) unit;
            // A ship "costs" far more than any field to destroy, so the field
            // dumps all it has onto the shield facing the generating ship (G23.513).
            int shieldNum = game.getShieldNumber(esgShip, target);
            game.markShieldDamage(target, shieldNum, strength);
            esg.absorbDamage(strength);
            log.add("  " + esgShip.getName() + "'s ESG field struck " + target.getName()
                    + " (shield #" + shieldNum + ", " + strength + " points) — field collapsed (G23.51)");

        } else if (unit instanceof Drone) {
            Drone drone = (Drone) unit;
            int toDestroy = Math.max(1, drone.getHull());
            int dealt = Math.min(strength, toDestroy);
            esg.absorbDamage(dealt);
            if (dealt >= toDestroy) {
                seekers.remove(drone);
                if (drone.getController() instanceof DroneController) {
                    ((DroneController) drone.getController()).releaseControl(drone);
                }
                log.add("  " + esgShip.getName() + "'s ESG field destroyed a drone (G23.51)");
            } else {
                log.add("  " + esgShip.getName() + "'s ESG field hit a drone for " + dealt
                        + " — field spent");
            }

        } else if (unit instanceof Shuttle) {
            Shuttle shuttle = (Shuttle) unit;
            int toDestroy = Math.max(1, shuttle.getCurrentHull());
            int dealt = Math.min(strength, toDestroy);
            esg.absorbDamage(dealt);
            if (dealt >= toDestroy) {
                game.removeShuttleFromPlay(shuttle, "destroyed by ESG field");
                log.add("  " + esgShip.getName() + "'s ESG field destroyed " + shuttle.getName() + " (G23.51)");
            } else {
                shuttle.setCurrentHull(shuttle.getCurrentHull() - dealt);
                log.add("  " + esgShip.getName() + "'s ESG field hit " + shuttle.getName()
                        + " for " + dealt + " — field spent");
            }
        }
    }
}
