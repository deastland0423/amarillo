package com.sfb.objects;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One Orion cartel (G15.44): its home empires and its operating-zone empires.
 * These define the access tier for an option weapon based on the empire(s) that
 * produce it — which in turn drives the fleet-level cartel quota (unlimited from
 * Home, ≤20% of fleet mounts from the Operating Zone, ≤10% from anywhere else).
 *
 * <h2>Why home is a LIST</h2>
 * G15.441 gives a cartel one home territory and most have exactly one. G15.442 gives the
 * Cluster three more — "treats Kzinti, Lyran, and Klingon space as 'Home' territory (90% of
 * weapons)" — and no operating zone, taking its remaining 10% from anywhere.
 *
 * <p>That is the SAME arithmetic, not an exception to it. The quota is enforced as "home
 * unlimited, operating ≤20%, outside ≤10%", which restates G15.441's 70/20/10 because the three
 * shares sum to 100; give a cartel four home empires and an empty operating zone and the very
 * same caps yield 90/10. So G15.442 is a different SHAPE of the same record rather than a
 * special case in code — which is the point, because a branch here would be precedence logic
 * for a rule that was already there.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrionCartel {

    /** Access tier of an empire (or weapon) relative to this cartel. */
    public enum Access { HOME, OPERATING, OUTSIDE }

    public String name;

    /** The empires whose weapons the cartel has unlimited access to. Usually one (G15.441). */
    public List<String> home = new ArrayList<>();

    /** Bordering empires the cartel also operates in (≤20% of fleet option mounts). */
    public List<String> operatingZone = new ArrayList<>();

    /** The access tier this cartel has to weapons produced in {@code empire}. */
    public Access accessFor(String empire) {
        if (empire == null) {
            return Access.OUTSIDE;
        }
        for (String h : home) {
            if (h.equalsIgnoreCase(empire)) {
                return Access.HOME;
            }
        }
        for (String e : operatingZone) {
            if (e.equalsIgnoreCase(empire)) {
                return Access.OPERATING;
            }
        }
        return Access.OUTSIDE;
    }

    /**
     * The best (most permissive) access tier across a weapon's producing empires
     * — a weapon made in several empires uses whichever gives the cartel the best
     * access. An empty/absent origin list is treated as Outside.
     */
    public Access accessForAny(List<String> empires) {
        if (empires == null || empires.isEmpty()) {
            return Access.OUTSIDE;
        }
        Access best = Access.OUTSIDE;
        for (String empire : empires) {
            Access tier = accessFor(empire);
            if (tier == Access.HOME) {
                return Access.HOME;
            }
            if (tier == Access.OPERATING) {
                best = Access.OPERATING;
            }
        }
        return best;
    }
}
