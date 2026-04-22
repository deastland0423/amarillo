package com.sfb.objects;

import com.sfb.properties.TerrainType;

/**
 * A hex-occupying terrain feature (asteroid, planet, etc.).
 * Each instance represents a single hex. Immovable — placed once at scenario load.
 */
public class Terrain extends Marker {

    private final TerrainType terrainType;

    public Terrain(TerrainType terrainType, int col, int row) {
        super(col, row);
        this.terrainType = terrainType;
    }

    public TerrainType getTerrainType() { return terrainType; }
}
