package com.sfb.objects;

import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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

    /** True unless the entry sits at exactly the size-class bar (helper for readers). */
    public boolean allowedOnSizeClass(int sizeClass) {
        return sizeClass <= maxSizeClass;
    }

    /** True if this option may be mounted in the given position ("CENTERLINE"/"WING"). */
    public boolean allowedInPosition(String position) {
        return positions.contains(position);
    }
}
