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
        if (entry.mountsRequired > 1 && seatFor(ship, mount, entry) == null) {
            return entry.name + " needs " + entry.mountsRequired
                    + " adjacent " + mount.getPosition().name().toLowerCase()
                    + " mounts, and " + mount.getDesignator()
                    + " has no free run of that many beside it (G15.4)";
        }
        return null;
    }

    /**
     * The mounts a weapon would occupy, starting from the one the player picked, or null if it
     * will not fit (G15.4).
     *
     * <h2>A window within one run</h2>
     * Adjacency is declared on the hull as runs of CONSECUTIVE mounts, so a two-mount weapon
     * wants two neighbours inside a single run. The Battle Raider's row of three is the case
     * that makes this more than a pair test: A-B-C seats a two-mount weapon on A+B or on B+C,
     * and never on A+C, because B lies between them.
     *
     * <p>Every mount in the window must be free and must allow the weapon's position, so a
     * player who picks B gets whichever side of it is actually open.
     */
    static java.util.List<OptionMount> seatFor(Ship ship, OptionMount mount, OptionCatalogEntry entry) {
        int wanted = Math.max(1, entry.mountsRequired);
        if (wanted == 1)
            return java.util.List.of(mount);
        for (java.util.List<String> run : ship.getAdjacentMountRuns()) {
            int at = run.indexOf(mount.getDesignator());
            if (at < 0)
                continue;
            // Every window of the right length that covers the chosen mount, nearest first.
            for (int start = Math.max(0, at - wanted + 1); start + wanted <= run.size(); start++) {
                if (start > at)
                    break;
                java.util.List<OptionMount> seat = new java.util.ArrayList<>();
                for (int i = start; i < start + wanted; i++) {
                    OptionMount m = findMount(ship, run.get(i));
                    if (m == null || !m.isEmpty()
                            || !entry.allowedInPosition(m.getPosition().name())) {
                        seat = null;
                        break;
                    }
                    seat.add(m);
                }
                if (seat != null)
                    return seat;
            }
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
        return equip(ship, mount, entry, year, null);
    }

    /**
     * Equip, with R8.R3's wing arc choice for a disruptor or fusion beam.
     *
     * @param wingArc null everywhere else, and for a player who did not choose — R8.R3 lets a
     *                wing disruptor or fusion beam bear FA or outboard "depending on the taste
     *                of the Orion player", so it is bought with the weapon and not derivable.
     */
    public static Weapon equip(Ship ship, OptionMount mount, OptionCatalogEntry entry, int year,
            OptionMountArcs.WingArc wingArc) {
        String reason = validate(ship, mount, entry, year);
        if (reason != null) {
            throw new IllegalArgumentException(reason);
        }
        java.util.List<OptionMount> seat = seatFor(ship, mount, entry);
        OptionMount primary = seat.get(0);
        // R8.R3, not the mount's printed arc. The sheet can only draw the typical case, because
        // what sits in an option mount is bought: a drone rack in a wing mount bears FULL where
        // the sheet prints LS, and a Plasma-F bears LP.
        Weapon weapon = entry.buildWeapon(OptionMountArcs.arcsFor(entry, primary, wingArc));
        // Named for every mount it fills, so "OPT-A+B" reads as one weapon in two boxes rather
        // than as a weapon that lost half of itself.
        StringBuilder designator = new StringBuilder("OPT-");
        for (int i = 0; i < seat.size(); i++)
            designator.append(i == 0 ? "" : "+").append(seat.get(i).getDesignator());
        weapon.setDesignator(designator.toString());
        primary.setWeapon(weapon);
        primary.setBpvCost(entry.cost);
        primary.setOptionName(entry.name);
        // The far halves hold the SAME weapon, so neither looks free, and carry no BPV of their
        // own: one weapon is one purchase however many boxes it fills.
        for (int i = 1; i < seat.size(); i++)
            seat.get(i).setContinuationOf(weapon);
        // Added ONCE whatever it spans. A weapon in the list twice fires twice.
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
        return equip(ship, catalog, mountDesignator, optionName, year, null);
    }

    /** By name, with R8.R3's wing arc choice. @see #equip(Ship, OptionMount, OptionCatalogEntry, int, OptionMountArcs.WingArc) */
    public static Weapon equip(Ship ship, OptionMountCatalog catalog,
                               String mountDesignator, String optionName, int year,
                               OptionMountArcs.WingArc wingArc) {
        OptionMount mount = findMount(ship, mountDesignator);
        if (mount == null) {
            throw new IllegalArgumentException("no option mount '" + mountDesignator + "' on " + ship.getName());
        }
        return equip(ship, mount, catalog.get(optionName), year, wingArc);
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
