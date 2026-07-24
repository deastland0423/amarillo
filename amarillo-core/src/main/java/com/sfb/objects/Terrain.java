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

    /** P2.222: gas giants 7+ hexes across have a pure-atmosphere outer ring. */
    public boolean isLargeGasGiant() {
        return terrainType == TerrainType.GAS_GIANT && radius >= 3;
    }
}
