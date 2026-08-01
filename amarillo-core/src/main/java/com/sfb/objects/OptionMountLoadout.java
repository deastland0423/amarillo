package com.sfb.objects;

import com.sfb.weapons.Weapon;

/**
 * Setup-time placement of a weapon into an Orion option mount (G15.4). Validates
 * a choice against the Annex #8B catalog — availability, position (G15.43), hull
 * size, service year — then equips it: builds the weapon in the mount's arc,
 * fills the mount, records the BPV delta, and registers the weapon so it fires.
 *
 * <p>This is the player-choice counterpart to a scenario's pinned loadout; both
 * end up as a {@link Weapon} on the mount and in the ship's weapons list.
 */
public final class OptionMountLoadout {

    private OptionMountLoadout() {}

    /**
     * @return null if {@code entry} may be placed in {@code mount} on {@code ship},
     *         otherwise a human-readable reason it may not.
     */
    public static String validate(Ship ship, OptionMount mount, OptionCatalogEntry entry) {
        if (mount == null) {
            return "no such option mount";
        }
        if (entry == null) {
            return "no such option in the catalog";
        }
        if (!mount.isEmpty()) {
            return "mount " + mount.getDesignator() + " is already filled";
        }
        if (!entry.available) {
            return entry.name + " is not available on Orion option mounts"
                    + (entry.notes != null ? " (" + entry.notes + ")" : "");
        }
        if (!entry.isBuildableWeapon()) {
            return entry.name + " cannot be equipped yet — no weapon implementation";
        }
        if (!entry.allowedInPosition(mount.getPosition().name())) {
            return entry.name + " may not be mounted in a " + mount.getPosition()
                    + " option mount (G15.43)";
        }
        if (!entry.allowedOnSizeClass(ship.getSizeClass())) {
            return entry.name + " may not be used on a size-" + ship.getSizeClass()
                    + " hull (G15.4)";
        }
        if (ship.getYearInService() > 0 && entry.yearAvailable > ship.getYearInService()) {
            return entry.name + " is not available until Y" + entry.yearAvailable
                    + " (this ship is Y" + ship.getYearInService() + ")";
        }
        if (entry.mountsRequired > 1) {
            return entry.name + " requires " + entry.mountsRequired
                    + " adjacent centerline mounts (multi-mount weapons not yet supported)";
        }
        return null;
    }

    /**
     * Validate and equip. On success the mount holds the built weapon, carries
     * its BPV cost, and the weapon is live in the ship's weapons list.
     *
     * @throws IllegalArgumentException with the validation reason if the choice is illegal.
     */
    public static Weapon equip(Ship ship, OptionMount mount, OptionCatalogEntry entry) {
        String reason = validate(ship, mount, entry);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        Weapon weapon = entry.buildWeapon(mount.getArcs());
        weapon.setDesignator("OPT-" + mount.getDesignator());
        mount.setWeapon(weapon);
        mount.setBpvCost(entry.cost);
        ship.getWeapons().addWeapon(weapon);
        return weapon;
    }

    // --- Name-keyed convenience for callers that hold the catalog (server, tests) ---

    public static String validate(Ship ship, OptionMountCatalog catalog,
                                  String mountDesignator, String optionName) {
        return validate(ship, findMount(ship, mountDesignator), catalog.get(optionName));
    }

    public static Weapon equip(Ship ship, OptionMountCatalog catalog,
                               String mountDesignator, String optionName) {
        OptionMount mount = findMount(ship, mountDesignator);
        if (mount == null) {
            throw new IllegalArgumentException("no option mount '" + mountDesignator + "' on " + ship.getName());
        }
        return equip(ship, mount, catalog.get(optionName));
    }

    private static OptionMount findMount(Ship ship, String designator) {
        for (OptionMount m : ship.getOptionMounts()) {
            if (m.getDesignator().equalsIgnoreCase(designator)) {
                return m;
            }
        }
        return null;
    }
}
