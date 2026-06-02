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

    private Shuttle shuttle;
    private DroneRack droneRack;    // bay-mounted drone rack (D12.3)
    private boolean hasReadyRack;   // ready service rack in this space
    private int deckCrews;          // crew currently working here; killed on destruction
    private boolean destroyed;

    public ShuttleSpace() {}

    public ShuttleSpace(Shuttle shuttle) {
        this.shuttle = shuttle;
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
    public Shuttle destroy() {
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
    public void setShuttle(Shuttle shuttle) { this.shuttle = shuttle; }

    public DroneRack getDroneRack() { return droneRack; }
    public void setDroneRack(DroneRack droneRack) { this.droneRack = droneRack; }

    public boolean isHasReadyRack() { return hasReadyRack; }
    public void setHasReadyRack(boolean hasReadyRack) { this.hasReadyRack = hasReadyRack; }

    public int getDeckCrews() { return deckCrews; }
    public void setDeckCrews(int deckCrews) { this.deckCrews = deckCrews; }
}
