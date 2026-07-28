package com.sfb.objects;

import java.util.EnumSet;
import java.util.Set;

import com.sfb.properties.RetrievalMethod;

/**
 * A scenario objective object — a stasis box, probe canister, cargo pod, or
 * similar thing that ships try to capture and carry off the map. First-class so
 * each instance keeps its own identity (retrieval rules, carrier, and later a
 * reveal/fog state).
 *
 * <p>Location has two states: FREE (on the map at {@link #getLocation()}, no
 * carrier) or CARRIED (aboard {@link #getCarrier()}, position derived from it).
 * Pickup moves free → carried; the carrier's destruction drops it back to free
 * in that hex, or annihilates it if it does not survive (SH35.454 vs SH47.475).
 */
public class Objective extends Marker implements Tractorable {

    private final Set<RetrievalMethod> allowedRetrieval = EnumSet.noneOf(RetrievalMethod.class);
    private boolean survivesCarrierDestruction = true;
    private Ship carrier;                 // null = free on the map (live possession)
    private com.sfb.Player securedBy;     // permanent owner once carried off a valid edge

    // While free, an objective can be caught in a tractor beam and drawn aboard
    // via the friendly-shuttle rotation system (J1.621 / SH35.452). These track
    // that transient pull-in; the objective stays FREE (carrier == null) until
    // recovery completes and sets the carrier.
    private Unit tractoringUnit;          // the ship whose beam holds it, or null
    private boolean beingRecovered;       // J1.621 pull-in declared

    public Objective() {}

    public Objective(String name, int x, int y) {
        super(x, y);
        setName(name);
    }

    public Set<RetrievalMethod> getAllowedRetrieval() { return allowedRetrieval; }

    public boolean allows(RetrievalMethod method) { return allowedRetrieval.contains(method); }

    public boolean isSurvivesCarrierDestruction() { return survivesCarrierDestruction; }

    public void setSurvivesCarrierDestruction(boolean survives) {
        this.survivesCarrierDestruction = survives;
    }

    public Ship getCarrier() { return carrier; }

    public void setCarrier(Ship carrier) { this.carrier = carrier; }

    // --- Tractorable: the beam's view of a free objective (J1.621 pull-in) ---

    @Override
    public Unit getTractoringUnit() { return tractoringUnit; }

    @Override
    public void applyTractor(Unit holder) { this.tractoringUnit = holder; }

    @Override
    public void releaseTractor() {
        this.tractoringUnit = null;
        this.beingRecovered = false; // link break ends the recovery (J1.6221)
    }

    public boolean isBeingRecovered() { return beingRecovered; }

    public void setBeingRecovered(boolean beingRecovered) { this.beingRecovered = beingRecovered; }

    public boolean isFree() { return carrier == null && securedBy == null; }

    public boolean isCarried() { return carrier != null && securedBy == null; }

    /**
     * Permanent owner, set once a ship carries this objective off a valid map
     * edge (SH35.5 "on board at disengagement"). One-way — never changes after.
     * A secured objective is out of play: no carrier, no map location.
     */
    public com.sfb.Player getSecuredBy() { return securedBy; }

    public void setSecuredBy(com.sfb.Player player) { this.securedBy = player; }

    public boolean isSecured() { return securedBy != null; }

    /**
     * Who controls this objective right now: the permanent owner if secured,
     * else the live carrier's owner (which changes hands when stolen), else
     * null when free on the map.
     */
    public com.sfb.Player getCurrentOwner() {
        if (securedBy != null)
            return securedBy;
        return carrier != null ? carrier.getOwner() : null;
    }

    /** Effective hex: the carrier's when carried, else its own free location. */
    public com.sfb.properties.Location getEffectiveLocation() {
        return carrier != null ? carrier.getLocation() : getLocation();
    }
}
