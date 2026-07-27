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
public class Objective extends Marker {

    private final Set<RetrievalMethod> allowedRetrieval = EnumSet.noneOf(RetrievalMethod.class);
    private boolean survivesCarrierDestruction = true;
    private Ship carrier; // null = free on the map

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

    public boolean isFree() { return carrier == null; }

    public boolean isCarried() { return carrier != null; }

    /** Effective hex: the carrier's when carried, else its own free location. */
    public com.sfb.properties.Location getEffectiveLocation() {
        return carrier != null ? carrier.getLocation() : getLocation();
    }
}
