package com.sfb.objects;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One Orion cartel (G15.44): its home empire and its operating-zone empires.
 * These define the access tier for an option weapon based on the empire(s) that
 * produce it — which in turn drives the fleet-level cartel quota (unlimited from
 * Home, ≤20% of fleet mounts from the Operating Zone, ≤10% from anywhere else).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrionCartel {

    /** Access tier of an empire (or weapon) relative to this cartel. */
    public enum Access { HOME, OPERATING, OUTSIDE }

    public String name;

    /** The empire whose weapons the cartel has unlimited access to. */
    public String home;

    /** Bordering empires the cartel also operates in (≤20% of fleet option mounts). */
    public List<String> operatingZone = new ArrayList<>();

    /** The access tier this cartel has to weapons produced in {@code empire}. */
    public Access accessFor(String empire) {
        if (empire == null) {
            return Access.OUTSIDE;
        }
        if (empire.equalsIgnoreCase(home)) {
            return Access.HOME;
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
