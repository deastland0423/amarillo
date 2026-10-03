package com.sfb.systemgroups;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.FighterPhoton;
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
    /** J4.843: "a single deck crew action" per charge - twice what a fusion charge costs. */
    private static final int HALF_ACTIONS_PER_DISRUPTOR_CHARGE = 2;
    private static final int HALF_ACTIONS_PER_DRONE_SPACE = 2;      // J4.82

    /**
     * J4.853: "Reloading a shuttle with a photon torpedo is a single deck crew action."
     * J4.863 says the same for a type-F plasma torpedo. A whole action, like the hellbore and
     * the disruptor charge - so a budget short of one buys nothing at all (J4.8174).
     */
    private static final int HALF_ACTIONS_PER_PHOTON_CHARGE = 2;    // J4.853
    private static final int HALF_ACTIONS_PER_PLASMA_F = 2;         // J4.863

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
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterFusion ff)
                charges += ff.getChargesRemaining();
            else if (w instanceof FighterDisruptor fd)
                charges += fd.getChargesRemaining();
            else if (w instanceof FighterPhoton fp)
                charges += fp.getChargesRemaining();
            else if (w instanceof com.sfb.weapons.FighterPlasmaF fpf)
                // A loaded torpedo is the one charge its box holds (J4.862). Counted the same
                // way, so a Gladiator that starts armed has taken its box's torpedo (J4.886).
                charges += fpf.isLoaded() ? 1 : 0;
        }
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
            if (w instanceof DroneRail rail && isArmable(rail) && rail.getDrone() != null)
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
            else if (w instanceof FighterDisruptor)
                half += FighterDisruptor.FULL_CHARGES * HALF_ACTIONS_PER_DISRUPTOR_CHARGE;
            else if (w instanceof FighterPhoton)
                half += FighterPhoton.FULL_CHARGES * HALF_ACTIONS_PER_PHOTON_CHARGE;
            else if (w instanceof com.sfb.weapons.FighterPlasmaF)
                half += HALF_ACTIONS_PER_PLASMA_F;
            else if (w instanceof DroneRail rail && isArmable(rail))
                half += halfActionsFor(rail);
        }
        return half;
    }

    /**
     * Whether a deck crew has anything to do to this craft at all.
     * <p>
     * False for a fighter whose whole armament is phasers, and the Hydran Stinger-E (R1.F7)
     * is the case that matters: one Ph-G and two permanently-fitted EW pods, so no fusion
     * charge, no hellbore charge, no drone, and nothing its box can give it. Such a fighter
     * is ALREADY ready, which is why the weapon-status allowances must not spend one of
     * their slots on it (S4.10/S4.11).
     */
    public static boolean needsArming(Shuttle craft) {
        return craft != null && halfActionsToFullyArm(craft) > 0;
    }

    /**
     * How near this craft is to flying a mission: READY, PARTIAL, EMPTY, or null where the
     * question does not apply at all.
     * <p>
     * A FIGHTER with nothing to arm reads READY, because it is: a Hydran Stinger-E (R1.F7)
     * carries one Ph-G and two permanent EW pods and is as armed as it can ever be, so
     * showing it blank in the launch pad beside its green-dotted squadron-mates said the
     * opposite of the truth. Null is reserved for craft the question does not reach — an
     * admin shuttle is not "unarmed", it is not a combat craft.
     * <p>
     * Note this deliberately disagrees with {@link #needsArming}, which stays false for such
     * a fighter. They answer different questions: whether a deck crew has work to do, and
     * whether the craft can fly its mission. A Stinger-E is no work and fully ready.
     * <p>
     * Computed here rather than in the client, and once rather than per panel. The hangar
     * asks it to colour a row and the launch pad asks it to say which two fighters can go
     * this impulse; both are the same question, and a view that works it out for itself is a
     * view deciding a rules outcome.
     * <p>
     * PARTIAL is a real and separate state, not a rounding of EMPTY: a Stinger with one
     * fusion charge of four CAN launch and CAN fire, it just cannot do it four times. Only
     * the captain can say whether that is worth a sortie, so the DTO must not answer for
     * them by folding it into "not ready".
     */
    public static String armingState(Shuttle craft) {
        if (craft == null)
            return null;
        if (halfActionsToFullyArm(craft) <= 0)
            // Nothing to arm. For a fighter that is the finished article; for anything else
            // the question never applied.
            return craft instanceof Fighter ? "READY" : null;
        int loaded = chargesCarriedBy(craft) + dronesCarriedBy(craft);
        if (loaded == 0)
            return "EMPTY";
        return halfActionsOutstanding(craft) == 0 ? "READY" : "PARTIAL";
    }

    /** Half-actions to finish arming this fighter from where it is now. */
    public static int halfActionsOutstanding(Shuttle fighter) {
        int half = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons()) {
            if (w instanceof FighterHellbore hb)
                half += hb.isSpent() ? HALF_ACTIONS_PER_HELLBORE_CHARGE : 0;
            else if (w instanceof FighterFusion ff)
                half += ff.chargesMissing() * HALF_ACTIONS_PER_FUSION_CHARGE;
            else if (w instanceof FighterDisruptor fd)
                half += fd.chargesMissing() * HALF_ACTIONS_PER_DISRUPTOR_CHARGE;
            else if (w instanceof DroneRail rail && isArmable(rail))
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

        // Before the drones, because a DAS carries both and the gun is the reason it is there.
        // A later pass fills its rails.
        Load disruptor = loadDisruptors(box, fighter, halfActionBudget);
        if (disruptor != Load.NOTHING)
            return disruptor;

        // Same reasoning for the other heavy weapons: an A-10 carries a photon AND drone
        // rails, and the photon is why the carrier embarked it.
        Load photon = loadPhoton(box, fighter, halfActionBudget);
        if (photon != Load.NOTHING)
            return photon;

        Load plasma = loadPlasmaF(box, fighter, halfActionBudget);
        if (plasma != Load.NOTHING)
            return plasma;

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
                    + rack.getServesFighterType() + ", not " + fighter.getCatalogType()
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
            if (w instanceof DroneRail rail && isArmable(rail))
                rails.add(rail);
        return rails;
    }

    /**
     * Whether a deck crew can put a drone on this rail at all.
     * <p>
     * False for a rail carrying an EW pod. J4.962: "An EWP replaces one drone carried by the
     * fighter" — the pod IS what that rail holds, so there is no drone to load, none to
     * unload, and no half-action to budget for it. A Kzinti HAAS-E has pods on both of its
     * rails and so has nothing to arm at all, exactly like a Hydran Stinger-E that has no
     * rails to begin with.
     * <p>
     * Every arming figure in this class has to agree about that or they contradict each other:
     * counting the rail as work made the HAAS-E look armable, which offered it in the S4.10
     * picker, which then called armFully, which asked the rail to take a drone — and
     * DroneRail.loadDrone throws rather than quietly drop the pod.
     */
    private static boolean isArmable(DroneRail rail) {
        return rail != null && !rail.hasEwPod();
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

    /**
     * Reload a fighter disruptor from its box's capacitor (J4.84).
     * <p>
     * J4.843 prices a charge at "a single deck crew action", so unlike a fusion charge — half an
     * action, two to a crew — a budget short of a whole action buys nothing at all (J4.8174). The
     * same all-or-nothing shape as the hellbore, which costs the same.
     * <p>
     * J4.881 still holds: the box's capacitor is the only supply, never the ship directly.
     */
    private static Load loadDisruptors(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        int missing = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterDisruptor fd)
                missing += fd.chargesMissing();
        if (missing == 0)
            return Load.NOTHING;

        int affordable = halfActionBudget / HALF_ACTIONS_PER_DISRUPTOR_CHARGE;
        if (affordable == 0)
            return Load.NOTHING;   // a disruptor charge is one whole action or nothing (J4.843)

        int drawn = box.drawCharges(Math.min(missing, affordable));
        if (drawn == 0)
            return new Load(0, 0, fighter.getName()
                    + ": disruptor capacitor empty, not reloaded (J4.842)");

        int loaded = 0;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterDisruptor fd)
                while (loaded < drawn && fd.loadCharge())
                    loaded++;
        // Anything the weapon would not take stays in the capacitor.
        if (loaded < drawn)
            box.setCapacitorCharges(box.getCapacitorCharges() + (drawn - loaded));

        return new Load(loaded * HALF_ACTIONS_PER_DISRUPTOR_CHARGE, loaded, fighter.getName()
                + ": " + loaded + " disruptor charge" + (loaded == 1 ? "" : "s") + " loaded, "
                + box.getCapacitorCharges() + " left in the box (J4.843)");
    }

    /**
     * Reload a fighter photon from its box's capacitor (J4.85).
     * <p>
     * J4.853 prices it at "a single deck crew action", so it is all or nothing like the
     * hellbore. The torpedo comes from the box and never from the ship directly (J4.882: "The
     * ship cannot reload the fighter directly, but must reload the storage capacitor").
     * <p>
     * Loaded as a STANDARD torpedo. J4.854 lets the fuse be set "at the time the charge is
     * loaded on the fighter", so proximity is a choice the player owns rather than something
     * a deck crew pass should decide for them; {@code FighterPhoton.setProximity} is the later
     * change, which J4.854 prices at another deck crew action.
     */
    private static Load loadPhoton(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        FighterPhoton photon = null;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof FighterPhoton fp && fp.isFunctional()
                    && fp.getChargesRemaining() < FighterPhoton.FULL_CHARGES)
                photon = fp;
        if (photon == null)
            return Load.NOTHING;
        if (halfActionBudget < HALF_ACTIONS_PER_PHOTON_CHARGE)
            return Load.NOTHING;
        if (box.drawCharges(1) == 0)
            return new Load(0, 0, fighter.getName()
                    + ": photon capacitor empty, not reloaded (J4.852)");
        if (!photon.loadCharge()) {
            box.setCapacitorCharges(box.getCapacitorCharges() + 1);  // it would not take it
            return Load.NOTHING;
        }
        return new Load(HALF_ACTIONS_PER_PHOTON_CHARGE, 1, fighter.getName()
                + ": photon reloaded from the fighter box capacitor (J4.853)");
    }

    /**
     * Reload a fighter's type-F plasma torpedo from its box's storage facility (J4.86).
     * <p>
     * J4.863: "a single deck crew action", the same all-or-nothing shape. J4.861 is the reason
     * a carrier is needed at all: "the fighters cannot rearm plasma torpedoes themselves".
     */
    private static Load loadPlasmaF(ShuttleSpace box, Shuttle fighter, int halfActionBudget) {
        com.sfb.weapons.FighterPlasmaF launcher = null;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterPlasmaF f && f.isFunctional()
                    && !f.isLoaded())
                launcher = f;
        if (launcher == null)
            return Load.NOTHING;
        if (halfActionBudget < HALF_ACTIONS_PER_PLASMA_F)
            return Load.NOTHING;
        if (box.drawCharges(1) == 0)
            return new Load(0, 0, fighter.getName()
                    + ": plasma storage facility empty, not reloaded (J4.862)");
        launcher.loadTorpedo();
        return new Load(HALF_ACTIONS_PER_PLASMA_F, 1, fighter.getName()
                + ": type-F plasma torpedo loaded from the fighter box (J4.863)");
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
            else if (w instanceof DroneRail rail && isArmable(rail)
                    && rail.getDrone() == null && rail.getDesignDrone() != null)
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
