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
import com.sfb.weapons.Esg;
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
                if (!(w instanceof Esg)) {
                    continue;
                }
                Esg esg = (Esg) w;
                if (!esg.isActive()) {
                    continue;
                }
                if (esg.isExpired(impulse)) {
                    esg.deactivate();
                    log.add("  " + ship.getName() + "'s ESG field collapsed — 32 impulses elapsed (G23.32)");
                    continue;
                }
                processField(ship, esg, log);
            }
        }
        return log;
    }

    private void processField(Ship ship, Esg esg, List<String> log) {
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

            // "Entered the field this impulse": on the ring now, but not on the
            // ring at the start of the impulse (either the unit moved onto it or
            // the ship's movement swept the ring onto the unit, G23.45/.51).
            boolean inNow  = MapUtils.getRange(shipNow, unitNow) == r;
            boolean inPrev = MapUtils.getRange(shipPrev, unitPrev) == r;
            if (inNow && !inPrev) {
                applyDamage(ship, esg, unit, log);
                if (!esg.isActive()) {
                    break; // field spent
                }
            }
        }
    }

    private void applyDamage(Ship esgShip, Esg esg, Unit unit, List<String> log) {
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
