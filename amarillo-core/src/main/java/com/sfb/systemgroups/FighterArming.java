package com.sfb.systemgroups;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.Weapon;

/**
 * What it costs a deck crew to arm a fighter, and the loading itself (J4.82, J4.83).
 * <p>
 * This exists because the same arithmetic is wanted in two places that would otherwise each
 * work it out. The end-of-turn rearm pass spends the crews a ship has loose; the pre-game
 * weapon status setup spends a budget of two turns' work per deck crew (S4.12, capped at four
 * actions on any one fighter by J4.8172's limit of two crews per box). Same costs, same
 * loading, different budget — so the budget is the parameter and everything else lives here.
 *
 * <h2>Half-actions</h2>
 * A deck crew action is 32 consecutive impulses (J4.8171) and the prices are in halves, so the
 * arithmetic is done in HALF-actions to stay in integers:
 * <ul>
 *   <li>a fusion charge: 1 half-action (J4.833)</li>
 *   <li>a hellbore charge: 2 (a full action, J4.834)</li>
 *   <li>a drone space: 2; a half-space: 1 (J4.82)</li>
 * </ul>
 * One crew working one turn is one action, so two half-actions.
 *
 * <h2>The two legs of a drone's journey</h2>
 * J4.82 moves a drone from the ship's stores to the box's ready rack, and from the rack onto
 * the fighter. J4.821 prices BOTH at one action per space, so a type-I costs two actions to
 * get from the hold onto a rail. {@link #refill} is the first leg and {@link #load} the
 * second; {@link DroneStore} and {@link ReadyRack} are the two places it rests.
 */
public final class FighterArming {

    /** A crew working one turn completes one action, and an action is two half-actions. */
    public static final int HALF_ACTIONS_PER_ACTION = 2;

    private static final int HALF_ACTIONS_PER_FUSION_CHARGE = 1;    // J4.833
    private static final int HALF_ACTIONS_PER_HELLBORE_CHARGE = 2;  // J4.834
    private static final int HALF_ACTIONS_PER_DRONE_SPACE = 2;      // J4.82

    private FighterArming() {}

    // -------------------------------------------------------------------------
    // What the fighter is holding, and what it would cost to fill it
    // -------------------------------------------------------------------------

    /**
     * Charges this fighter is holding, in the units its own box's capacitor counts.
     * <p>
     * Used at setup to work out how full the box is: a fighter armed at the start of a
     * scenario drew its charges from its own box (J4.886), so the two always account for
     * exactly one capacitor between them.
     */
    public static int chargesCarriedBy(Shuttle fighter) {
        FighterHellbore hellbore = hellboreOf(fighter);
        if (hellbore != null)
            return hellbore.isSpent() ? 0 : 1;

        int charges = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                charges += ff.getChargesRemaining();
        return charges;
    }

    /**
     * Drones this fighter is holding, in the units its own box's ready rack counts.
     * <p>
     * The drone twin of {@link #chargesCarriedBy}: a loaded fighter took these from its rack
     * (J4.8224), so the fighter and its rack always account for exactly one reload.
     */
    public static int dronesCarriedBy(Shuttle fighter) {
        int loaded = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.getDrone() != null)
                loaded++;
        return loaded;
    }

    /** Half-actions to arm this fighter from empty — its full load, whatever it is armed with. */
    public static int halfActionsToFullyArm(Shuttle fighter) {
        int half = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterHellbore)
                half += HALF_ACTIONS_PER_HELLBORE_CHARGE;
            else if (w instanceof FighterFusion)
                half += FighterFusion.FULL_CHARGES * HALF_ACTIONS_PER_FUSION_CHARGE;
            else if (w instanceof DroneRail rail)
                half += halfActionsFor(rail);
        }
        return half;
    }

    /** Half-actions to finish arming this fighter from where it is now. */
    public static int halfActionsOutstanding(Shuttle fighter) {
        int half = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterHellbore hb)
                half += hb.isSpent() ? HALF_ACTIONS_PER_HELLBORE_CHARGE : 0;
            else if (w instanceof FighterFusion ff)
                half += ff.chargesMissing() * HALF_ACTIONS_PER_FUSION_CHARGE;
            else if (w instanceof DroneRail rail)
                half += rail.getDrone() == null ? halfActionsFor(rail) : 0;
        }
        return half;
    }

    /**
     * Whole actions to arm this fighter from empty, rounded up — the figure a player budgets
     * with. A Stinger-1 is 2, a Stinger-H is 1, an AAS is 2; a fighter of four drone rails
     * would be 4, which two crews can only reach across both of the two turns S4.12 allows.
     */
    public static int actionsToFullyArm(Shuttle fighter) {
        return (halfActionsToFullyArm(fighter) + HALF_ACTIONS_PER_ACTION - 1)
                / HALF_ACTIONS_PER_ACTION;
    }

    /**
     * J4.82: an action a drone space, half an action for a half-space — measured on the DRONE
     * being handled, not the rail it goes in (owner's ruling, 2026-09-27).
     * <p>
     * It matters because the two differ: a Type-VI is half a space and rides happily in a
     * standard rail, and putting one there is half an action's work, not a whole one. So a
     * TAAS loaded entirely with dogfight drones turns round in two actions where the same
     * fighter loaded with Type-Is takes three.
     */
    private static int halfActionsFor(com.sfb.objects.Drone drone) {
        return (int) Math.round(drone.getRackSize() * HALF_ACTIONS_PER_DRONE_SPACE);
    }

    /** What an empty rail would cost to fill with what it is designed to carry. */
    private static int halfActionsFor(DroneRail rail) {
        com.sfb.objects.DroneType design = rail.getDesignDrone();
        return design == null ? 0
                : (int) Math.round(design.rack * HALF_ACTIONS_PER_DRONE_SPACE);
    }

    // -------------------------------------------------------------------------
    // The loading
    // -------------------------------------------------------------------------

    /** What one loading job did: the work spent, and a line for the ship's own log. */
    public record Load(int halfActionsUsed, int chargesLoaded, String note) {
        static final Load NOTHING = new Load(0, 0, null);
    }

    /**
     * Arm the fighter in this box as far as the box's capacitor and the budget allow.
     *
     * @param box               the fighter box, which is also the only supply (J4.881)
     * @param fighter           its occupant
     * @param halfActionBudget  crew work available for THIS fighter
     * @return what was done; {@link Load#note()} is null when there was nothing to do
     */
    public static Load load(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        if (halfActionBudget <= 0)
            return Load.NOTHING;

        FighterHellbore hellbore = hellboreOf(fighter);
        if (hellbore != null)
            return loadHellbore(box, fighter, hellbore, halfActionBudget);

        if (hasFusions(fighter))
            return loadFusions(box, fighter, halfActionBudget);

        if (dronesCarriedBy(fighter) < railsOf(fighter).size())
            return loadDrones(box, fighter, halfActionBudget);

        return Load.NOTHING;
    }

    /**
     * Fill this fighter's empty rails from its box's ready rack (J4.82).
     * <p>
     * A drone space is a whole action, so a budget short of one buys nothing for that rail —
     * J4.8174 again. The rack is the only source: J4.881 forbids arming a fighter from the
     * ship directly, and refilling the rack itself from the ship's stores is a separate job
     * we do not model yet, so an empty rack simply says so.
     */
    private static Load loadDrones(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        ReadyRack rack = box.getReadyRack();
        if (rack == null)
            return Load.NOTHING;
        if (!rack.serves(fighter))
            return new Load(0, 0, fighter.getName() + ": this box's ready rack services "
                    + rack.getServesFighterType() + ", not " + fighter.getClass().getSimpleName()
                    + " (J4.8222)");

        int budget = halfActionBudget;
        int loaded = 0;
        for (DroneRail rail : railsOf(fighter)) {
            if (rail.getDrone() != null)
                continue;
            // The drone has to fit the rail, and it is the drone that sets the price.
            com.sfb.objects.Drone drone = rack.takeFor(rail);
            if (drone == null)
                break;
            int cost = halfActionsFor(drone);
            if (budget < cost) {
                rack.put(drone);   // not enough work left for this one; leave it in the rack
                break;
            }
            rail.loadDrone(drone);
            budget -= cost;
            loaded++;
        }

        if (loaded == 0)
            return rack.isEmpty()
                    ? new Load(0, 0, fighter.getName() + ": ready rack empty, not reloaded (J4.822)")
                    : Load.NOTHING;

        return new Load(halfActionBudget - budget, loaded, fighter.getName() + ": " + loaded
                + " drone" + (loaded == 1 ? "" : "s") + " loaded, " + rack.count()
                + " left in the ready rack (J4.82)");
    }

    private static java.util.List<DroneRail> railsOf(Shuttle fighter) {
        java.util.List<DroneRail> rails = new java.util.ArrayList<>();
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail)
                rails.add(rail);
        return rails;
    }

    private static Load loadHellbore(ShuttleSpace box, Shuttle fighter, FighterHellbore hellbore,
            int halfActionBudget) {
        if (!hellbore.isSpent())
            return Load.NOTHING;
        if (halfActionBudget < HALF_ACTIONS_PER_HELLBORE_CHARGE)
            return Load.NOTHING;   // a hellbore charge is one whole action or nothing (J4.8174)
        if (box.drawCharges(1) == 0)
            return new Load(0, 0, fighter.getName()
                    + ": hellbore capacitor empty, not reloaded (J4.834)");
        hellbore.reload();
        return new Load(HALF_ACTIONS_PER_HELLBORE_CHARGE, 1, fighter.getName()
                + ": hellbore reloaded from the fighter box capacitor (J4.834)");
    }

    private static Load loadFusions(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        int missing = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                missing += ff.chargesMissing();
        if (missing == 0)
            return Load.NOTHING;

        int drawn = box.drawCharges(Math.min(missing, halfActionBudget));
        if (drawn == 0)
            return new Load(0, 0, fighter.getName()
                    + ": fusion capacitor empty, not reloaded (J4.831)");

        int loaded = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterFusion ff)
                while (loaded < drawn && ff.loadCharge())
                    loaded++;
        }
        // Anything the weapons would not take stays in the capacitor.
        if (loaded < drawn)
            box.setCapacitorCharges(box.getCapacitorCharges() + (drawn - loaded));

        return new Load(loaded * HALF_ACTIONS_PER_FUSION_CHARGE, loaded, fighter.getName()
                + ": " + loaded + " fusion charge" + (loaded == 1 ? "" : "s") + " loaded, "
                + box.getCapacitorCharges() + " left in the box (J4.833)");
    }

    /**
     * Move drones from the ship's stores up into this box's ready rack (J4.82, J4.821).
     * <p>
     * The first leg of the journey, and the one that decides whether a carrier is still a
     * carrier on turn four. J4.821 prices it at "one action (per space)" — the same rate as
     * loading a fighter from the rack, so the hold-to-rail round trip is two actions a space.
     * <p>
     * What goes in is what this rack was built to hold: the rack knows its own shape from the
     * rails of the fighter it services (J4.8222), so a slot that wants a type-VI is offered a
     * type-VI and not the type-I sitting next to it in the hold.
     *
     * @param box               the fighter box whose rack is being filled
     * @param store             the ship's supply (J4.7); null or empty means nothing to fetch
     * @param halfActionBudget  crew work available for THIS rack
     * @return what was done; {@link Load#note()} is null when there was nothing to do
     */
    public static Load refill(ShuttleSpace box, DroneStore store, int halfActionBudget) {
        ReadyRack rack = box.getReadyRack();
        if (rack == null || store == null || halfActionBudget <= 0 || rack.isFull())
            return Load.NOTHING;
        if (store.isEmpty())
            return new Load(0, 0, box.getShuttle() == null ? null
                    : box.getShuttle().getName() + ": drone stores empty, ready rack not"
                            + " refilled (J4.7)");

        int budget = halfActionBudget;
        int moved = 0;
        // The rack's shape is the shape of the fighter it serves, so ask for the slots it is
        // short of, largest first — the awkward ones are the ones a part-filled hold runs out
        // of, and a type-I fetched for a light rail would be a wasted trip.
        for (com.sfb.objects.DroneType want : rack.slotsMissing()) {
            com.sfb.objects.Drone drone = store.take(want);
            if (drone == null)
                drone = store.take();       // stores hold something else; it still beats empty
            if (drone == null)
                break;                      // hold is empty
            int cost = halfActionsFor(drone);
            if (budget < cost || !rack.put(drone)) {
                store.put(drone);           // back in the hold; it never left the ship
                break;
            }
            budget -= cost;
            moved++;
        }

        if (moved == 0)
            return Load.NOTHING;
        return new Load(halfActionBudget - budget, moved, rackOwnerName(box) + ": " + moved
                + " drone" + (moved == 1 ? "" : "s") + " moved from stores to the ready rack, "
                + rack.count() + "/" + rack.capacity() + " in it now (J4.821)");
    }

    /** What to call a box in the log when its fighter is away and cannot name it. */
    private static String rackOwnerName(ShuttleSpace box) {
        return box.getShuttle() == null ? "Ready rack" : box.getShuttle().getName();
    }

    // -------------------------------------------------------------------------
    // The other direction
    // -------------------------------------------------------------------------

    /**
     * Mend the occupant, one damage point per whole action (J4.818).
     * <p>
     * Half an action buys nothing, the same as everywhere else: J4.8174 gives no credit for
     * an action that did not finish.
     */
    public static Load repair(ShuttleSpace box, Shuttle occupant, int halfActionBudget) {
        int points = halfActionBudget / HALF_ACTIONS_PER_ACTION;
        if (points <= 0)
            return Load.NOTHING;
        int damage = occupant.getHull() - occupant.getCurrentHull();
        if (damage <= 0)
            return Load.NOTHING;
        int mended = Math.min(points, damage);
        String note = occupant.repairDamage(mended);
        if (note == null)
            return Load.NOTHING;
        return new Load(mended * HALF_ACTIONS_PER_ACTION, mended, note);
    }

    /**
     * Take drones back off a fighter, as far as the budget and the rack's room allow (J4.82).
     * <p>
     * The deck crew job behind CrewTask.UNLOAD. A ready rack holds one reload of one drone
     * type, so this is how a commander changes their mind about what a fighter carries:
     * unload now, load something else once the rack has been refilled. Costs what loading
     * costs — the drones have to be handled either way.
     *
     * @return what was done; note() is null when there was nothing to take off
     */
    public static Load unload(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        ReadyRack rack = box.getReadyRack();
        if (rack == null || halfActionBudget <= 0)
            return Load.NOTHING;

        int budget = halfActionBudget;
        int taken = 0;
        for (DroneRail rail : railsOf(fighter)) {
            com.sfb.objects.Drone drone = rail.getDrone();
            if (drone == null)
                continue;
            int cost = halfActionsFor(drone);
            if (budget < cost)
                break;
            if (!rack.put(drone))
                break;          // nowhere to put it; the rack is where they belong (J4.822)
            rail.setAmmo(new java.util.ArrayList<>());
            budget -= cost;
            taken++;
        }

        if (taken == 0)
            return Load.NOTHING;
        return new Load(halfActionBudget - budget, taken, fighter.getName() + ": " + taken
                + " drone" + (taken == 1 ? "" : "s") + " returned to the ready rack, "
                + rack.count() + " in it now (J4.82)");
    }

    /**
     * Take a fighter's charges off it and put them back in its box (S4.10/S4.11).
     * <p>
     * For the weapon status setup, which starts from fighters that are armed because that is
     * how they are built: at WS-0 only two of a carrier's fighters are armed, so the rest are
     * emptied. The charges go BACK INTO the capacitor rather than being discarded — they were
     * never anywhere else (J4.886), and the box plus its fighter have to keep accounting for
     * exactly one capacitor.
     * <p>
     * A drone-armed fighter puts its drones back in its own box's ready rack instead, which
     * is where J4.8223 says they rest; they are never dropped on the floor, because a supply
     * that is not tracked is a supply that is infinite.
     *
     * @return charges returned to the box
     */
    public static int disarm(ShuttleSpace box, Shuttle fighter) {
        FighterHellbore hellbore = hellboreOf(fighter);
        if (hellbore != null) {
            if (hellbore.isSpent())
                return 0;
            hellbore.unload();
            box.setCapacitorCharges(box.getCapacitorCharges() + 1);
            return 1;
        }

        int returned = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterFusion ff) {
                returned += ff.getChargesRemaining();
                ff.drainCharges();
            }
        }
        if (returned > 0) {
            box.setCapacitorCharges(box.getCapacitorCharges() + returned);
            return returned;
        }

        // Drones go back in the box's own ready rack (J4.8223: the resting state of a carrier
        // is racks full and fighters unloaded), never on the floor.
        ReadyRack rack = box.getReadyRack();
        if (rack == null)
            return 0;
        for (DroneRail rail : railsOf(fighter)) {
            com.sfb.objects.Drone drone = rail.getDrone();
            if (drone == null || !rack.put(drone))
                continue;
            rail.setAmmo(new java.util.ArrayList<>());
            returned++;
        }
        return returned;
    }

    /**
     * Fill a fighter's weapons outright, with no budget and no supply (S4.13, and test setup).
     * <p>
     * Weapon status is the one thing that arms a fighter without a deck crew action: at WS-3
     * "the fighters are loaded but the weapons were taken from the ready racks" (J4.8224). The
     * taking is the CALLER's job — {@link ShuttleSpace#armOccupantFully()} does it by
     * re-deriving the box's contents around the armed fighter, which is the same subtraction
     * that balances the books at every other weapon status.
     * <p>
     * Kept deliberately narrow: this is the only way into a fighter's weapons that does not go
     * through a box, and it exists so that setup and tests need not invent a second one.
     */
    public static void armFully(Shuttle fighter) {
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterHellbore hb)
                hb.reload();
            else if (w instanceof FighterFusion ff)
                while (ff.loadCharge()) { /* to the top */ }
            else if (w instanceof DroneRail rail && rail.getDrone() == null
                    && rail.getDesignDrone() != null)
                rail.loadDrone(new com.sfb.objects.Drone(rail.getDesignDrone()));
        }
    }

    // -------------------------------------------------------------------------

    private static FighterHellbore hellboreOf(Shuttle fighter) {
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterHellbore hb)
                return hb;
        return null;
    }

    private static boolean hasFusions(Shuttle fighter) {
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion)
                return true;
        return false;
    }
}
