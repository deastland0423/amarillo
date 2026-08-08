package com.sfb.objects;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sfb.objects.ShipSpec.WeaponSpec;
import com.sfb.weapons.Weapon;

/**
 * One row of the Orion option-mount cost chart (Annex #8B, G15.4). Captures the
 * annex's rules columns so a loadout validator can enforce them; the base ship
 * data stays independent. Field defaults encode "no restriction", so a row only
 * lists the columns where it differs from the norm.
 *
 * <p>Symbol → field mapping from the annex:
 * <ul>
 *   <li>&infin; / NA (never) → {@code available=false}</li>
 *   <li>* (two adjacent centerline mounts) → {@code mountsRequired=2}</li>
 *   <li>&Dagger; (not on size-4-or-smaller) → {@code maxSizeClass=3}</li>
 *   <li>&Delta; (not in wing mounts) → {@code positions=["CENTERLINE"]}</li>
 *   <li>&sect; captured-only / T Tholian-galaxy → {@code available=false} + reason in {@code notes}</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptionCatalogEntry {

    /** Display name exactly as printed in Annex #8B, e.g. "Phaser-2", "Disruptor-30". */
    public String name;

    /** BPV delta of choosing this option (Annex #8B). May be negative or fractional. */
    public double cost;

    /** Legal mount positions. Default: either. A {@code Δ} weapon is centerline-only. */
    public List<String> positions = Arrays.asList("CENTERLINE", "WING");

    /** Adjacent option mounts consumed. Default 1; a {@code *} weapon needs 2 (plasma-R would need 4). */
    public int mountsRequired = 1;

    /**
     * Largest (numerically-highest = physically-smallest) size class allowed to
     * mount this. Default 6 = no bar. A {@code ‡} weapon sets 3 — barred on
     * size-4-or-smaller hulls (like the LR).
     */
    public int maxSizeClass = 6;

    /** False for the {@code ∞}/NA "never" weapons and the niche {@code §}/{@code T} options (reason in {@link #notes}). */
    public boolean available = true;

    /** Earliest service year the option is generally available (0 = always). */
    public int yearAvailable = 0;

    /** Free-text carrying anything not otherwise structured (PF-only, arc, ammo, captured-only, etc.). */
    public String notes;

    /**
     * Empire(s) that produce this weapon, for the Orion cartel quota (G15.44).
     * Empty/absent means UNIVERSAL (phasers, tractors, …) — available to any
     * cartel with no quota. A listed weapon takes the best cartel access tier
     * across these empires.
     */
    public List<String> empires;

    /** True if this option is produced everywhere (no origin empire → exempt from the cartel quota). */
    public boolean isUniversal() {
        return empires == null || empires.isEmpty();
    }

    /**
     * How to instantiate this option as a weapon (reuses the ship-JSON weapon
     * recipe). Present only for options that (a) are weapons and (b) have an
     * implementing class. Non-weapon systems (Cargo, Lab, Transporter, …) and
     * not-yet-implemented weapons (Ion Cannon, ESG, …) leave this null — that is
     * how the name → weapon-class translation reports "can't build this."
     */
    public WeaponSpec weaponSpec;

    /** True if this option can be turned into a live weapon instance right now. */
    public boolean isBuildableWeapon() {
        return weaponSpec != null;
    }

    /**
     * Translate this catalog row into a live weapon firing in the given arc
     * (the mount's arc labels, e.g. ["FA"]). Returns null when the option isn't
     * a buildable weapon — callers treat that as a non-weapon system or an
     * unimplemented option.
     */
    public Weapon buildWeapon(List<String> mountArcs) {
        return WeaponFactory.build(weaponSpec, mountArcs);
    }

    /** True unless the entry sits at exactly the size-class bar (helper for readers). */
    public boolean allowedOnSizeClass(int sizeClass) {
        return sizeClass <= maxSizeClass;
    }

    /** True if this option may be mounted in the given position ("CENTERLINE"/"WING"). */
    public boolean allowedInPosition(String position) {
        return positions.contains(position);
    }
}
