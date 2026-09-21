package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a saved {@link FleetSpec} into the ships {@link FleetValidator} judges.
 * <p>
 * One path, used by every caller. A fleet is resolved when it is validated in the builder, when
 * it is saved, when it is listed, and when it is brought into a battle; four places resolving
 * it four ways is how a fleet comes to be legal on one screen and illegal on the next.
 * <p>
 * Expects {@link ShipLibrary} to be loaded already — the caller knows where the data lives.
 */
public final class FleetLoader {

    /**
     * The ships a spec names, the entries that named nothing real, and which ship ended up
     * being the flagship (names are assigned here, so the caller cannot know it in advance).
     */
    public record Resolution(List<Ship> ships, List<String> unknown, String flagshipName) {
        public boolean isComplete() {
            return unknown.isEmpty();
        }
    }

    private FleetLoader() {
    }

    public static Resolution resolve(FleetSpec spec) {
        List<Ship> ships = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        String flagshipName = null;

        for (FleetSpec.ShipEntry entry : spec.ships) {
            String faction = spec.factionOf(entry);
            ShipSpec shipSpec = ShipLibrary.get(faction, entry.type);
            if (shipSpec == null) {
                unknown.add(faction + " " + entry.type);
                continue;
            }
            Ship ship = ShipLibrary.createShip(shipSpec);

            // Several ships of one type are normal, and a player may not have named them
            // apart. Violations have to point at one ship, so make the names unique here.
            String wanted = entry.name != null && !entry.name.isBlank()
                    ? entry.name
                    : (shipSpec.name != null && !shipSpec.name.isBlank() ? shipSpec.name : entry.type);
            int n = seen.merge(wanted, 1, Integer::sum);
            ship.setName(n == 1 ? wanted : wanted + " #" + n);
            ship.setCoiSpend(entry.coiSpend);
            ships.add(ship);

            // The flagship may be named by the name the player gave it or by its type, since
            // a builder that has not renamed anything only knows the type.
            if (flagshipName == null && spec.flagship != null
                    && (ship.getName().equalsIgnoreCase(spec.flagship)
                        || entry.type.equalsIgnoreCase(spec.flagship)))
                flagshipName = ship.getName();
        }

        return new Resolution(ships, unknown, flagshipName);
    }

    /** The resolved fleet, ready to validate against the conditions the spec records. */
    public static FleetValidator.Fleet toFleet(FleetSpec spec) {
        Resolution r = resolve(spec);
        return new FleetValidator.Fleet(r.ships(), r.flagshipName(), spec.budget, spec.year);
    }
}
