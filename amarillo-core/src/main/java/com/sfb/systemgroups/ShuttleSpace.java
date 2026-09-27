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

    /** J4.832: a fusion charge costs the ship one point, so 8 fills a fusion box. */
    public static final int POWER_PER_FUSION_CHARGE = 1;

    /**
     * J4.834: the hellbore charge costs two points on each of two turns — four in all, and
     * never fewer than two turns. Hence the banking: the first turn's points buy no charge.
     */
    public static final int POWER_PER_HELLBORE_CHARGE = 4;

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
        if (readyRack != null) {
            while (!readyRack.isFull())
                readyRack.put(new com.sfb.objects.Drone(
                        ((com.sfb.objects.shuttles.Fighter) shuttle).getDefaultDroneType()));
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

    /** Post crews to one particular job; zero takes them off it. */
    public void postCrews(CrewTask task, int crews) {
        if (crews <= 0)
            crewTasks.remove(task);
        else
            crewTasks.put(task, crews);
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

    /** What a charge costs this box: J4.832 for a fusion box, J4.834 for a hellbore one. */
    public int powerPerCapacitorCharge() {
        return capacitorCapacity() == HELLBORE_CAPACITOR
                ? POWER_PER_HELLBORE_CHARGE : POWER_PER_FUSION_CHARGE;
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
        return capacitorCapacity() == HELLBORE_CAPACITOR
                ? HELLBORE_POWER_PER_TURN : Integer.MAX_VALUE;
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
        if (wanted == 0)
            return 0;
        capacitorEnergyBanked += wanted;
        int price = powerPerCapacitorCharge();
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
     */
    private static int capacityFor(Shuttle occupant) {
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.FighterHellbore)
                return HELLBORE_CAPACITOR;
        }
        for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.FighterFusion)
                return FUSION_CAPACITOR;
        }
        return 0;
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
