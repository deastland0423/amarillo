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

    /** Validate using the ship's own intro year as the availability year (standalone default). */
    public static String validate(Ship ship, OptionMount mount, OptionCatalogEntry entry) {
        return validate(ship, mount, entry, ship.getYearInService());
    }

    /**
     * @param year the battle/scenario year the option must be available by; a
     *             non-positive value skips the year check (year unknown).
     * @return null if {@code entry} may be placed in {@code mount} on {@code ship},
     *         otherwise a human-readable reason it may not.
     */
    public static String validate(Ship ship, OptionMount mount, OptionCatalogEntry entry, int year) {
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
        if (year > 0 && entry.yearAvailable > year) {
            return entry.name + " is not available until Y" + entry.yearAvailable
                    + " (this battle is Y" + year + ")";
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
        return equip(ship, mount, entry, ship.getYearInService());
    }

    /** Equip, gating option availability on the given battle/scenario year. */
    public static Weapon equip(Ship ship, OptionMount mount, OptionCatalogEntry entry, int year) {
        String reason = validate(ship, mount, entry, year);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        Weapon weapon = entry.buildWeapon(mount.getArcs());
        weapon.setDesignator("OPT-" + mount.getDesignator());
        mount.setWeapon(weapon);
        mount.setBpvCost(entry.cost);
        mount.setOptionName(entry.name);
        ship.getWeapons().addWeapon(weapon);
        applyY175ToANewlyMountedRack(ship, weapon, year);
        // Annex #8B, the Y168 DERFACS grant. Stated once, on Ship, and asked here for the same
        // timing reason as the Y175 rules above: a disruptor bought into a mount is built after
        // the year pass has run.
        ship.applyOrionDerfacs(year);
        return weapon;
    }

    /**
     * The Y175 reload rules, for a rack that did not exist when they last ran.
     *
     * <h2>Why this is here and not in {@code ScenarioLoader.applyYearUpgrades}</h2>
     * It IS there — and that pass cannot see these racks. A ship is built and outfitted for its
     * year first, and option mounts are filled afterwards, in {@code applyCoi}; a rack placed in
     * a mount is therefore created long after every Y175 rule has already run and gone. Equipping
     * is the only door such a rack comes through, so the rules are reapplied at the door.
     *
     * <p>Two rules, and only one of them is about Orions:
     * <ul>
     *   <li><b>FP10.312</b> gives every plasma rack a second set of reloads in Y175, on any hull
     *       of any empire. A plasma rack in an Orion mount was quietly missing it.</li>
     *   <li>An <b>Orion</b> drone rack gains one reload set in Y175, which is what the BR's and
     *       CR's sheets print for racks in their option mounts.</li>
     * </ul>
     *
     * <p>Nothing is applied twice: a rack reached by the pass in {@code applyYearUpgrades} is one
     * already on the hull, and a rack reached here did not exist then.
     */
    private static void applyY175ToANewlyMountedRack(Ship ship, Weapon weapon, int year) {
        if (year < 175)
            return;
        if (weapon instanceof com.sfb.weapons.PlasmaRack plasmaRack) {
            plasmaRack.applyY175Refit();                       // FP10.312, every empire
        } else if (weapon instanceof com.sfb.weapons.DroneRack droneRack
                && ship.getFaction() == com.sfb.properties.Faction.Orion) {
            droneRack.addReloadSets(1);                        // the Orion rule, owner 2026-10-20
        }
    }

    // --- Name-keyed convenience for callers that hold the catalog (server, tests) ---

    public static String validate(Ship ship, OptionMountCatalog catalog,
                                  String mountDesignator, String optionName) {
        return validate(ship, findMount(ship, mountDesignator), catalog.get(optionName));
    }

    public static Weapon equip(Ship ship, OptionMountCatalog catalog,
                               String mountDesignator, String optionName) {
        return equip(ship, catalog, mountDesignator, optionName, ship.getYearInService());
    }

    public static Weapon equip(Ship ship, OptionMountCatalog catalog,
                               String mountDesignator, String optionName, int year) {
        OptionMount mount = findMount(ship, mountDesignator);
        if (mount == null) {
            throw new IllegalArgumentException("no option mount '" + mountDesignator + "' on " + ship.getName());
        }
        return equip(ship, mount, catalog.get(optionName), year);
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
