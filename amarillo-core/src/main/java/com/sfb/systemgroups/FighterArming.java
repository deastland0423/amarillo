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
 * <h2>What it will not do yet</h2>
 * Drone-armed fighters are PRICED here but not loaded: J4.82 moves drones from the ship's
 * stores to a ready rack and from there onto the fighter, and we model neither store nor rack
 * (the rack is still a bare boolean on {@link ShuttleSpace}). Pricing them now is what lets
 * the weapon status budget be right when the racks arrive; loading them would mean inventing
 * drones out of nothing, so it refuses instead.
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
                half += droneRailHalfActions(rail);
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
                half += rail.getDrone() == null ? droneRailHalfActions(rail) : 0;
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

    /** J4.82: an action a drone space, half an action for a half-space. */
    private static int droneRailHalfActions(DroneRail rail) {
        double spaces = rail.getRailType().capacity;
        return (int) Math.round(spaces * HALF_ACTIONS_PER_DRONE_SPACE);
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
            int cost = droneRailHalfActions(rail);
            if (budget < cost)
                break;
            com.sfb.objects.Drone drone = rack.take();
            if (drone == null)
                break;
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

    // -------------------------------------------------------------------------
    // The other direction
    // -------------------------------------------------------------------------

    /**
     * Take a fighter's charges off it and put them back in its box (S4.10/S4.11).
     * <p>
     * For the weapon status setup, which starts from fighters that are armed because that is
     * how they are built: at WS-0 only two of a carrier's fighters are armed, so the rest are
     * emptied. The charges go BACK INTO the capacitor rather than being discarded — they were
     * never anywhere else (J4.886), and the box plus its fighter have to keep accounting for
     * exactly one capacitor.
     * <p>
     * A drone-armed fighter is untouched: its drones would have to go back to the ship's
     * stores, which J4.82 defines and we do not model, and dropping them on the floor is how
     * a supply that is not tracked becomes a supply that is infinite.
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
                    && fighter instanceof com.sfb.objects.shuttles.Fighter f
                    && f.getDefaultDroneType() != null)
                rail.loadDrone(new com.sfb.objects.Drone(f.getDefaultDroneType()));
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
