package com.sfb.objects;

import java.util.List;

import com.sfb.weapons.Weapon;

/**
 * An Orion (or WYN) optional weapons mount (G15.4) — an "OPT" box on the SSD
 * that holds ONE chosen weapon (or non-weapon system) selected at scenario
 * setup. Empty until filled.
 *
 * <p>{@link Position} distinguishes CENTERLINE mounts (can take anything
 * allowed) from WING mounts, which are too weak for some weapons (G15.43).
 * Cartel availability (G15.44) and the legal-weapon list (Annex #8B) gate what
 * may be placed — enforced when the loadout mechanism is built.
 */
public class OptionMount {

    public enum Position { CENTERLINE, WING }

    private final Position position;
    private final String designator;
    private final List<String> arcs;   // arc labels, e.g. ["FA"] — applied to the weapon when filled
    private Weapon weapon;              // chosen weapon, or null when empty
    private double bpvCost;             // BPV delta of the chosen option (G15.4, Annex #8B); may be negative/fractional
    private String optionName;         // Annex #8B name of the equipped option (for display + cartel quota); null if empty/pinned

    public OptionMount(Position position, String designator, List<String> arcs) {
        this.position = position;
        this.designator = designator;
        this.arcs = arcs;
    }

    public Position getPosition()   { return position; }
    public String getDesignator()   { return designator; }
    public List<String> getArcs()   { return arcs; }
    public Weapon getWeapon()       { return weapon; }
    public void setWeapon(Weapon w) { this.weapon = w; }
    public double getBpvCost()      { return bpvCost; }
    public void setBpvCost(double c) { this.bpvCost = c; }
    public String getOptionName()   { return optionName; }
    public void setOptionName(String n) { this.optionName = n; }
    public boolean isEmpty()        { return weapon == null; }

    /**
     * True when this mount holds the far half of a weapon seated on a neighbour (G15.4).
     *
     * <h2>Occupied, not empty</h2>
     * A Plasma-G needs "two adjacent centerline optional mounts" (Annex #8B), so it consumes
     * both. Both therefore hold the SAME weapon object — one of them as the primary, the other
     * as a continuation — and {@link #isEmpty()} is false on both, which is what stops a second
     * weapon being seated in the half that looks free.
     *
     * <h2>What the continuation does NOT carry</h2>
     * The BPV and the option name sit on the primary alone. A two-mount weapon is one purchase,
     * and anything summing {@link #getBpvCost()} across a hull's mounts would otherwise charge
     * for it twice.
     */
    private boolean continuation;

    public boolean isContinuation() { return continuation; }

    /** Seat the far half of a neighbour's weapon here. */
    public void setContinuationOf(Weapon w) {
        this.weapon = w;
        this.continuation = true;
        this.bpvCost = 0;
        this.optionName = null;
    }
}
