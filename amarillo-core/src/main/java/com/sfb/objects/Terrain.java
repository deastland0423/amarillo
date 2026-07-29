package com.sfb.objects;

import com.sfb.properties.TerrainType;

/**
 * A hex-occupying terrain feature (asteroid, planet, etc.).
 * Each instance represents a single hex. Immovable — placed once at scenario load.
 */
public class Terrain extends Marker {

    private final TerrainType terrainType;
    // Footprint radius in hexes from the center (diameter 2r+1). Always 0 for
    // class-M planets (P2.211) and asteroids; gas giants use P2.221 sizes.
    private final int radius;
    // Optional per-instance counter art (path under /tokens/). Null falls back
    // to a per-type default (planet1.png for PLANET) or procedural rendering.
    private String tokenArt;
    // Planetary rings (P2.223): annular bands as {innerDist, outerDist} hex
    // distances from center. Enterable asteroid-like hexes (NOT the no-entry
    // atmosphere ring). Gas giants only. Empty for everything else.
    private java.util.List<int[]> ringBands = java.util.List.of();

    // Bombardment damage (P2.311/P2.525): total and per hex side (1..6 = A..F).
    // A planet has no shields — fire just accumulates here for victory scoring.
    private int totalDamage = 0;
    private final int[] damageBySide = new int[6];

    public Terrain(TerrainType terrainType, int col, int row) {
        this(terrainType, col, row, 0);
    }

    public Terrain(TerrainType terrainType, int col, int row, int radius) {
        super(col, row);
        this.terrainType = terrainType;
        this.radius = Math.max(0, radius);
    }

    public TerrainType getTerrainType() { return terrainType; }

    public int getRadius() { return radius; }

    public String getTokenArt() { return tokenArt; }

    public void setTokenArt(String tokenArt) { this.tokenArt = tokenArt; }

    /** Ring bands as {innerDist, outerDist} hex-distance pairs from center (P2.223). */
    public java.util.List<int[]> getRingBands() { return ringBands; }

    public void setRingBands(java.util.List<int[]> ringBands) {
        this.ringBands = ringBands != null ? ringBands : java.util.List.of();
    }

    /** P2.222: gas giants 7+ hexes across have a pure-atmosphere outer ring. */
    public boolean isLargeGasGiant() {
        return terrainType == TerrainType.GAS_GIANT && radius >= 3;
    }

    // --- Bombardment damage (P2.311/P2.525) ---

    /** Total damage scored on this planet (SM1-style "total damage" victory). */
    public int getTotalDamage() { return totalDamage; }

    /** Damage scored on one hex side (1..6 = A..F); SH63-style per-side victory. */
    public int getDamageOnSide(int side) {
        return (side >= 1 && side <= 6) ? damageBySide[side - 1] : 0;
    }

    /** Score {@code amount} damage on hex side {@code side} (1..6); adds to the total too. */
    public void addDamage(int side, int amount) {
        if (amount <= 0) return;
        totalDamage += amount;
        if (side >= 1 && side <= 6) damageBySide[side - 1] += amount;
    }
}
