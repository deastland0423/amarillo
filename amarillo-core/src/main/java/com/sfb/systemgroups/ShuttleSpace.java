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

    private Shuttle shuttle;
    private DroneRack droneRack;    // bay-mounted drone rack (D12.3)
    private boolean hasReadyRack;   // ready service rack in this space
    private int deckCrews;          // crew currently working here; killed on destruction

    /**
     * The weapon capacitor built into this fighter box (J4.831/J4.834).
     *
     * A fusion box holds eight charges — two complete reloads for a two-weapon Stinger. A
     * hellbore box holds one charge instead. Full at the start of a scenario (J4.886),
     * destroyed with the box (J4.831), and able to reload only the fighter in THIS box
     * (J4.881: never straight from the ship).
     *
     * The SSD marks the box, not its occupant, so the capacity is learned from the first
     * fighter seated here and then KEPT: a Stinger that launches leaves an empty box, and
     * an empty box whose capacity fell to zero would silently lose its charges while the
     * fighter was away.
     */
    private int capacitorCapacity = -1;  // -1 = no fighter has sat here yet
    private int capacitorCharges = -1;   // -1 = not yet filled (J4.886)

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
        destroyed = true;
        deckCrews = 0;
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
        if (capacitorCapacity < 0 && shuttle != null)
            capacitorCapacity = capacityFor(shuttle);
    }

    public DroneRack getDroneRack() { return droneRack; }
    public void setDroneRack(DroneRack droneRack) { this.droneRack = droneRack; }

    public boolean isHasReadyRack() { return hasReadyRack; }
    public void setHasReadyRack(boolean hasReadyRack) { this.hasReadyRack = hasReadyRack; }

    public int getDeckCrews() { return deckCrews; }
    public void setDeckCrews(int deckCrews) { this.deckCrews = deckCrews; }

    /**
     * How many charges this box's capacitor holds when full: eight for a fusion box, one for
     * a hellbore box (J4.834 puts that capacitor there "in place of" the fusion one), and
     * nothing for a box that has never held a Hydran fighter.
     */
    public int capacitorCapacity() {
        return Math.max(0, capacitorCapacity);
    }

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

    /** Charges in the capacitor. J4.886: full at the start of a scenario. */
    public int getCapacitorCharges() {
        if (capacitorCharges < 0)
            capacitorCharges = capacitorCapacity();
        return Math.min(capacitorCharges, capacitorCapacity());
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
