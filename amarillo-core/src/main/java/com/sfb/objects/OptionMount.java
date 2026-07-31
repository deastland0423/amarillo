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
    public boolean isEmpty()        { return weapon == null; }
}
