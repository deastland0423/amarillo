package com.sfb.systemgroups;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.DroneRack;

/**
 * One slot in a shuttle bay.
 *
 * A space can hold a shuttle, a bay-mounted drone rack (D12.3, Klingon ships),
 * or nothing. It can be permanently destroyed by a DAC hit. Deck crews working
 * in a space are killed if the space is destroyed (tracked for future rules).
 */
public class ShuttleSpace {

    /**
     * J4.831: a fusion fighter box's capacitor holds two complete reloads, and a Stinger is
     * two weapons of two charges — J4.832 fills it for 8 power at 1 power a charge.
     */
    public static final int FUSION_CAPACITOR = 8;

    /** J4.834: a hellbore box carries one hellbore charge instead. */
    public static final int HELLBORE_CAPACITOR = 1;

    /**
     * J4.842: a disruptor fighter's box holds two disruptor charges — one complete reload for
     * the single two-charge disruptor a DAS carries (J4.845).
     */
    public static final int DISRUPTOR_CAPACITOR = 2;

    /**
     * J4.842: "two points per charge; both points must be provided on the same turn."
     * <p>
     * The same price as a hellbore charge per point but a different rule about time: J4.834
     * SPREADS the hellbore's four points over two turns, while this one refuses to be split at
     * all. {@link #addCapacitorEnergy} enforces that by never banking an odd point on a
     * disruptor box, so a half-paid charge cannot exist to carry over.
     */
    public static final int POWER_PER_DISRUPTOR_CHARGE = 2;

    /**
     * Which weapon's capacitor a box has, and therefore how its charges are priced.
     * <p>
     * An explicit kind because the alternative — inferring it from the CAPACITY — had already
     * run out: the pricing used to read {@code capacitorCapacity() == HELLBORE_CAPACITOR}, which
     * works only while one is 1 and the other is 8. A disruptor box holds 2, so it would have
     * been silently priced as a fusion box at a point a charge, half the rule's price, with no
     * per-turn limit.
     */
    public enum CapacitorKind { NONE, FUSION, HELLBORE, DISRUPTOR, PHOTON, PLASMA_F }

    /** J4.832: a fusion charge costs the ship one point, so 8 fills a fusion box. */
    public static final int POWER_PER_FUSION_CHARGE = 1;

    /**
     * J4.834: the hellbore charge costs two points on each of two turns — four in all, and
     * never fewer than two turns. Hence the banking: the first turn's points buy no charge.
     */
    public static final int POWER_PER_HELLBORE_CHARGE = 4;

    /**
     * J4.852: a photon fighter's box holds "a capacitor for a single photon torpedo", and
     * J4.862 gives a plasma-F fighter's box "a storage facility ... for a single type-F plasma
     * torpedo". One apiece, like the hellbore and for the same reason: the fighter carries one
     * heavy weapon and the box holds one reload for it.
     */
    public static final int PHOTON_CAPACITOR = 1;
    public static final int PLASMA_F_CAPACITOR = 1;

    /**
     * J4.852, in as many words: "two points of warp power for two consecutive turns, but
     * overloads are not allowed" - the same as a photon tube, so four points and two turns.
     */
    public static final int POWER_PER_PHOTON_CHARGE = 4;
    public static final int PHOTON_POWER_PER_TURN = 2;

    /**
     * A type-F plasma torpedo: 1 + 1 + 3 over THREE turns, five points in all.
     * <p>
     * DERIVED from the ship's own figures rather than restated, because J4.881 does not say
     * "similar to", it says identical: "there is no difference whatsoever in arming the type-F
     * plasma torpedo on a ship than in arming one to be held for use by a fighter." So the
     * numbers come from {@code Constants.fArmingCost} and the launcher's arming turns, and a
     * change to the ship's plasma automatically reaches the fighter box.
     * <p>
     * {@code fArmingCost[0]} is the per-turn rolling cost and {@code fArmingCost[1]} the final
     * burst, which is the part worth saying out loud: the array is NOT a per-turn schedule,
     * and reading it as "1 then 3 over two turns" is how this constant was first written wrong
     * in both total and duration (owner's correction, 2026-10-02).
     */
    private static final int PLASMA_F_ARMING_TURNS = 3;   // PlasmaLauncher.totalArmingTurns()
    public static final int PLASMA_F_ROLLING_POWER_PER_TURN =
            com.sfb.constants.Constants.fArmingCost[0];
    public static final int PLASMA_F_FINAL_BURST =
            com.sfb.constants.Constants.fArmingCost[1];
    public static final int POWER_PER_PLASMA_F_CHARGE =
            PLASMA_F_ROLLING_POWER_PER_TURN * (PLASMA_F_ARMING_TURNS - 1) + PLASMA_F_FINAL_BURST;

    /** The most a hellbore box will take in one turn, which is what makes it take two. */
    public static final int HELLBORE_POWER_PER_TURN = 2;

    private Shuttle shuttle;
    private DroneRack droneRack;    // bay-mounted drone rack (D12.3)
    /**
     * The drone ready rack built into this box (J4.822), or null if it was never a
     * drone-fighter box. Learned from the first fighter seated, like the capacitor, and for
     * the same reason: the SSD marks the BOX, so an empty box must not lose its stores.
     */
    private ReadyRack readyRack;
    /**
     * Crews working here this turn, by what they are doing (J4.817).
     * <p>
     * Per task rather than a single number because two crews on one fighter need not be doing
     * the same thing — one loading while the other repairs. The TOTAL is what J4.811 kills
     * when the box is destroyed; the breakdown is what the end-of-turn pass acts on.
     */
    private final java.util.Map<CrewTask, Integer> crewTasks =
            new java.util.EnumMap<>(CrewTask.class);

    /**
     * The weapon capacitor built into this fighter box (J4.831/J4.834).
     *
     * A fusion box holds eight charges — two complete reloads for a two-weapon Stinger. A
     * hellbore box holds one charge instead. Full at the start of a scenario (J4.886) LESS
     * whatever its fighter is already armed with, destroyed with the box (J4.831), and able
     * to reload only the fighter in THIS box (J4.881: never straight from the ship).
     *
     * The SSD marks the box, not its occupant, so the capacity is learned from the first
     * fighter seated here and then KEPT: a Stinger that launches leaves an empty box, and
     * an empty box whose capacity fell to zero would silently lose its charges while the
     * fighter was away.
     */
    private int capacitorCapacity = -1;  // -1 = no fighter has sat here yet
    private int capacitorCharges = -1;   // -1 = not yet filled (J4.886)

    /** Learned with the capacity, from the first fighter seated, and kept for the same reason. */
    private CapacitorKind capacitorKind = CapacitorKind.NONE;

    /**
     * Power paid towards the NEXT charge but not yet worth one (J4.834).
     *
     * Only hellbore boxes ever bank anything: their single charge costs two points, and the
     * rule spreads that over two turns, so the first point has to sit somewhere. A fusion
     * charge costs one point and lands immediately.
     */
    private int capacitorEnergyBanked;

    /**
     * The turn this space last received its occupant; a fighter must sit a whole one before
     * a deck crew action on it can have finished (J4.8174).
     *
     * Starts at -1, not 0: TurnTracker counts the first turn of a game as turn ZERO, so a
     * fighter that was in its box before the scenario began has to predate that.
     */
    private int occupiedSinceTurn = -1;
    private boolean destroyed;

    public ShuttleSpace() {}

    public ShuttleSpace(Shuttle shuttle) {
        setShuttle(shuttle);
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    public boolean isEmpty() {
        return !destroyed && shuttle == null && droneRack == null;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public boolean isOccupied() {
        return !destroyed && (shuttle != null || droneRack != null);
    }

    // -------------------------------------------------------------------------
    // Destruction
    // -------------------------------------------------------------------------

    /**
     * Permanently destroy this space. Clears contents and kills any deck crews.
     * Returns the shuttle that was in the space (null if empty), so the caller
     * can check isArmed() and trigger a chain reaction if needed.
     */
    /** J4.831: "These capacitors are destroyed with the fighter box." */
    public Shuttle destroy() {
        this.capacitorCharges = 0;
        this.capacitorCapacity = 0;
        this.capacitorEnergyBanked = 0;
        if (readyRack != null)
            readyRack.destroy();   // J4.831's rule for capacitors, applied to the stores
        destroyed = true;
        crewTasks.clear();
        droneRack = null;
        Shuttle was = shuttle;
        shuttle = null;
        return was;
    }

    // -------------------------------------------------------------------------
    // Getters / setters
    // -------------------------------------------------------------------------

    public Shuttle getShuttle() { return shuttle; }

    public void setShuttle(Shuttle shuttle) {
        this.shuttle = shuttle;
        if (capacitorCapacity < 0 && shuttle != null) {
            capacitorKind = kindFor(shuttle);
            capacitorCapacity = capacityFor(shuttle);
            if (capacitorCharges < 0)
                capacitorCharges = Math.max(0,
                        capacitorCapacity - FighterArming.chargesCarriedBy(shuttle));
        }
        // The drone half of the same idea (J4.822). A rack starts full and a fighter starts
        // empty (J4.8223), so anything the fighter IS holding came out of this rack and is
        // taken back off the top — the same books the capacitor keeps.
        if (readyRack == null && !destroyed) {
            readyRack = ReadyRack.forFighter(shuttle);
            if (readyRack != null)
                for (int i = FighterArming.dronesCarriedBy(shuttle); i > 0; i--)
                    readyRack.take();
        }
    }

    /**
     * Seat {@code fighter} and re-derive this box's equipment from scratch, as though no
     * fighter had ever sat here. SETUP ONLY — see the warning below.
     * <p>
     * {@link #setShuttle} deliberately learns the capacitor and ready rack from the FIRST
     * occupant and keeps them: the SSD marks the box, not the craft, so a Stinger that
     * launches must leave its charges behind rather than take them with it. That is right
     * during play and wrong at setup, because {@code FighterComplement.reseat} changes which
     * fighter a box holds — and a box that keeps the old fighter's fittings then services the
     * new one by the wrong rule.
     * <p>
     * It was wrong on 393 boxes across fifteen hulls and five factions. A Kzinti CV reseated
     * to Y180 flies TADS with six rails apiece and kept the AAS's two-drone rack, so it
     * reloaded a third of a squadron; a Federation CVA reseated back to Y167 kept the A-10's
     * PHOTON capacitor under an F-4 that has no photon; a Hydran RN+ box that should bank
     * eight points of fusion charge (J4.831) banked one, because a hellbore Stinger had sat
     * there first. Every one of them looked like a working carrier.
     * <p>
     * <b>Never call this during play.</b> A fighter landing in a box goes through
     * {@link #setShuttle}, and re-deriving there would hand it a full capacitor and a full
     * rack for free — J4.886's opening stock, granted mid-scenario, every time anything landed.
     */
    public void reequipFor(Shuttle fighter) {
        if (destroyed)
            return;
        capacitorCapacity = -1;          // the sentinels setShuttle reads, back to "never seated"
        capacitorCharges = -1;
        capacitorKind = CapacitorKind.NONE;
        capacitorEnergyBanked = 0;
        readyRack = null;
        setShuttle(fighter);
    }

    public DroneRack getDroneRack() { return droneRack; }
    public void setDroneRack(DroneRack droneRack) { this.droneRack = droneRack; }

    /**
     * Arm this box's fighter to the top and take what that cost out of the box's own stores
     * (S4.13 / J4.8224).
     * <p>
     * The books are kept by re-deriving rather than by bookkeeping: a full capacitor less what
     * the fighter now holds, a full rack less the drones now on its rails. Same rule as when
     * the box was first stocked, so the invariant — fighter plus box equals one load — cannot
     * drift apart from the thing that establishes it.
     */
    public void armOccupantFully() {
        if (destroyed || shuttle == null)
            return;
        FighterArming.armFully(shuttle);
        capacitorCharges = Math.max(0,
                capacitorCapacity() - FighterArming.chargesCarriedBy(shuttle));
        if (readyRack != null && readyRack.isPlasmaD()) {
            // J4.825/FP9.21: a plasma rack's load is a COUNT, so the subtraction is arithmetic
            // rather than a list of objects. Back to full, then one out for every torpedo now on
            // a rail - the fighter's load came out of this box (J4.8224).
            //
            // A branch of its own because put()/take() are drone methods and answer false on a
            // plasma rack, so the drone path below silently balanced nothing: a Gladiator-F armed
            // from a full rack left the rack still full, and the box held two torpedoes that were
            // also on the fighter.
            while (readyRack.plasmaDMissing() > 0)
                readyRack.putPlasmaD();
            for (int i = FighterArming.plasmaDsCarriedBy(shuttle); i > 0; i--)
                readyRack.takePlasmaD();
        } else if (readyRack != null) {
            // Refill from the rails' own designs, so a mixed fighter's rack comes back
            // with drones each of its rails can take.
            for (com.sfb.weapons.Weapon w : shuttle.getWeapons().fetchAllWeapons())
                if (w instanceof com.sfb.weapons.DroneRail rail && !readyRack.isFull()
                        && rail.getDesignDrone() != null)
                    readyRack.put(new com.sfb.objects.Drone(rail.getDesignDrone()));
            for (int i = FighterArming.dronesCarriedBy(shuttle); i > 0; i--)
                readyRack.take();
        }
    }

    /** J4.822: this box was built for a drone-carrying fighter and has the rack to prove it. */
    public boolean isHasReadyRack() { return readyRack != null; }

    public ReadyRack getReadyRack() { return readyRack; }

    /** Everyone working in this box, whatever they are doing — what a hit here kills. */
    public int getDeckCrews() {
        int total = 0;
        for (int n : crewTasks.values())
            total += n;
        return total;
    }

    /** Post crews to the obvious job. Shorthand for the common case and for older callers. */
    public void setDeckCrews(int deckCrews) {
        crewTasks.clear();
        if (deckCrews > 0)
            crewTasks.put(CrewTask.LOAD, deckCrews);
    }

    /**
     * Post crews to one particular job; zero takes them off it.
     * <p>
     * Refuses a job J4.8172 will not let run beside one already posted here — filling the
     * ready rack while it is being drawn from to arm the fighter. The refusal is here rather
     * than at the end-of-turn pass so an impossible order is rejected when it is given,
     * while the player can still give a different one.
     *
     * @return false if the posting was refused as conflicting; true otherwise
     */
    public boolean postCrews(CrewTask task, int crews) {
        if (crews <= 0) {
            crewTasks.remove(task);
            return true;
        }
        for (CrewTask posted : crewTasks.keySet())
            if (task.conflictsWith(posted))
                return false;   // J4.8172: not both on the same rack in the same turn
        crewTasks.put(task, crews);
        return true;
    }

    /** Whether J4.8172 would let this job be posted here alongside what is already posted. */
    public boolean canPost(CrewTask task) {
        for (CrewTask posted : crewTasks.keySet())
            if (task.conflictsWith(posted))
                return false;
        return true;
    }

    public int getCrews(CrewTask task) {
        return crewTasks.getOrDefault(task, 0);
    }

    public java.util.Map<CrewTask, Integer> getCrewTasks() {
        return java.util.Collections.unmodifiableMap(crewTasks);
    }

    public void clearCrews() {
        crewTasks.clear();
    }

    /**
     * How many charges this box's capacitor holds when full: eight for a fusion box, one for
     * a hellbore box (J4.834 puts that capacitor there "in place of" the fusion one), and
     * nothing for a box that has never held a Hydran fighter.
     */
    public int capacitorCapacity() {
        return Math.max(0, capacitorCapacity);
    }

    /** Charges this box's capacitor is short of full. */
    public int capacitorChargesMissing() {
        return Math.max(0, capacitorCapacity() - getCapacitorCharges());
    }

    /**
     * What a charge costs this box: J4.832 for a fusion box, J4.834 for a hellbore one, J4.842
     * for a disruptor one.
     */
    public int powerPerCapacitorCharge() {
        switch (capacitorKind) {
            case HELLBORE:  return POWER_PER_HELLBORE_CHARGE;
            case DISRUPTOR: return POWER_PER_DISRUPTOR_CHARGE;
            case PHOTON:    return POWER_PER_PHOTON_CHARGE;
            case PLASMA_F:  return POWER_PER_PLASMA_F_CHARGE;
            default:        return POWER_PER_FUSION_CHARGE;
        }
    }

    /**
     * Power this box could still absorb THIS TURN, counting what is already banked.
     *
     * A fusion box has no rate limit — J4.832 prices the charge and says nothing about how
     * many a turn. A hellbore box does: two points a turn, which is what makes its single
     * charge take the two turns J4.834 gives it. The limit lives here rather than in a
     * per-turn counter because this is also what the allocation dialog offers and what the
     * server bounds the line by, so a turn's allocation cannot exceed it in the first place.
     */
    public int capacitorPowerWanted() {
        int missing = capacitorChargesMissing();
        if (missing == 0)
            return 0;
        int owed = missing * powerPerCapacitorCharge() - capacitorEnergyBanked;
        return Math.min(owed, maxCapacitorPowerPerTurn());
    }

    private int maxCapacitorPowerPerTurn() {
        switch (capacitorKind) {
            case HELLBORE: return HELLBORE_POWER_PER_TURN;
            // The rate is what makes a charge take the turns its rule gives it. Without one
            // the box would fill the moment the energy existed (J4.834, J4.852).
            case PHOTON:   return PHOTON_POWER_PER_TURN;
            // The plasma's rate CHANGES partway, which no other capacitor's does: a type-F
            // rolls at one point a turn and then takes a three-point burst to finish
            // (Constants.fArmingCost). A single ceiling cannot express 1 + 1 + 3 - a cap of
            // three would let it finish in two turns, and a cap of one would take five - so
            // the ceiling is read off how much is already banked.
            case PLASMA_F: return capacitorEnergyBanked
                    < PLASMA_F_ROLLING_POWER_PER_TURN * (PLASMA_F_ARMING_TURNS - 1)
                            ? PLASMA_F_ROLLING_POWER_PER_TURN : PLASMA_F_FINAL_BURST;
            default:       return Integer.MAX_VALUE;
        }
    }

    /**
     * Whether this box may carry part-payment for a charge from one turn into the next.
     * <p>
     * Only a hellbore box may: J4.834 spreads its four points over two turns, so its first two
     * have to sit somewhere. A disruptor box may NOT — J4.842 requires "both points ... on the
     * same turn" — and a fusion charge costs a single point, so nothing is ever left over.
     */
    private boolean banksAcrossTurns() {
        return capacitorKind == CapacitorKind.HELLBORE
                || capacitorKind == CapacitorKind.PHOTON
                || capacitorKind == CapacitorKind.PLASMA_F;
    }

    /**
     * Put allocated power into this box's capacitor (J4.832).
     *
     * A fusion charge is a point, so the charges appear as the points go in. A hellbore
     * charge is two points and J4.834 spreads them over two consecutive turns, so a lone
     * point banks and the charge appears when the second arrives. We do not enforce the
     * "consecutive" part: a banked point waits rather than lapsing.
     *
     * @param power points offered
     * @return points actually taken
     */
    public int addCapacitorEnergy(int power) {
        int wanted = Math.min(Math.max(0, power), capacitorPowerWanted());
        int price = powerPerCapacitorCharge();
        // J4.842: a disruptor charge takes both its points on the SAME turn, so an odd point is
        // refused outright rather than banked. Nothing then exists to carry over, which is how
        // the same-turn rule is kept without a turn boundary to clear a bank at.
        if (!banksAcrossTurns() && price > 1)
            wanted -= wanted % price;
        if (wanted == 0)
            return 0;
        capacitorEnergyBanked += wanted;
        int charges = capacitorEnergyBanked / price;
        if (charges > 0) {
            capacitorEnergyBanked -= charges * price;
            setCapacitorCharges(getCapacitorCharges() + charges);
        }
        return wanted;
    }

    public int getCapacitorEnergyBanked() { return capacitorEnergyBanked; }

    /**
     * The capacity the SSD would print for a box holding this fighter.
     *
     * Read off the fighter because we do not model the SSD's box markings: a box marked "="
     * carries a fusion fighter and one marked "+" a hellbore one, so asking the occupant
     * gives the same answer everywhere it matters.
     * <p>
     * It knows fusions and hellbores and nothing else, so J4.84's disruptor fighters, J4.85's
     * photon fighters and J4.86's plasma-F ones all fall through to zero — no capacitor at
     * all, silently. That is a gap waiting for those fighters rather than a ruling about them,
     * and the first one to arrive needs a case here.
     * <p>
     * Note a box may have a capacitor AND a ready rack: they are separate fields set by
     * separate tests, because a fighter can carry a capacitor weapon and drones at once — a
     * Federation photon fighter with drone rails being the case to expect. Nothing in the
     * catalogue does yet, which is why nothing exercises it.
     */
    private static int capacityFor(Shuttle occupant) {
        switch (kindFor(occupant)) {
            case HELLBORE:  return HELLBORE_CAPACITOR;
            case FUSION:    return FUSION_CAPACITOR;
            case DISRUPTOR: return DISRUPTOR_CAPACITOR;
            case PHOTON:    return PHOTON_CAPACITOR;
            case PLASMA_F:  return PLASMA_F_CAPACITOR;
            default:        return 0;
        }
    }

    /**
     * Which capacitor the SSD would print for a box holding this fighter.
     * <p>
     * Hellbore is tested before fusion because a Stinger-H carries both and its box is the
     * hellbore one — the original code had the same ordering for the same reason, as two
     * separate loops.
     * <p>
     * The photon and plasma-F boxes come last, which costs nothing: a fighter carries one
     * heavy weapon, so no craft can match two of these tests. They were a documented gap here
     * until those fighters existed - the A-10 and the Gladiator - and closing it is what gives
     * a deck crew anything to do for either (J4.85, J4.86).
     */
    private static CapacitorKind kindFor(Shuttle occupant) {
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterHellbore)
                return CapacitorKind.HELLBORE;
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterFusion)
                return CapacitorKind.FUSION;
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterDisruptor)
                return CapacitorKind.DISRUPTOR;
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterPhoton)
                return CapacitorKind.PHOTON;
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.FighterPlasmaF)
                return CapacitorKind.PLASMA_F;
        return CapacitorKind.NONE;
    }

    /** Which weapon's capacitor this box has (J4.831/J4.834/J4.842). */
    public CapacitorKind getCapacitorKind() {
        return capacitorKind;
    }

    /**
     * Charges in the capacitor (J4.886: full at the start of a scenario, less whatever its
     * fighter was already armed with).
     *
     * Settled when the box receives its first fighter, NOT lazily on the first read: a box
     * asked about itself in the middle of a battle, after its fighter had spent everything,
     * would otherwise have discovered a full capacitor at exactly the wrong moment.
     */
    public int getCapacitorCharges() {
        return Math.max(0, Math.min(capacitorCharges, capacitorCapacity()));
    }

    public void setCapacitorCharges(int charges) {
        this.capacitorCharges = Math.max(0, Math.min(charges, capacitorCapacity()));
    }

    /** Take up to n charges out; returns how many were actually there. */
    public int drawCharges(int n) {
        int taken = Math.min(Math.max(0, n), getCapacitorCharges());
        capacitorCharges = getCapacitorCharges() - taken;
        return taken;
    }

    public int getOccupiedSinceTurn() { return occupiedSinceTurn; }
    public void setOccupiedSinceTurn(int turn) { this.occupiedSinceTurn = turn; }
}
