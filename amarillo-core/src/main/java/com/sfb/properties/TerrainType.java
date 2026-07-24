package com.sfb.properties;

public enum TerrainType {
    ASTEROID,
    PLANET,      // class-M — always fills exactly one hex (P2.211)
    GAS_GIANT,   // multi-hex, center + radius (P2.22); 7+ across gains an atmosphere ring (P2.222)
    NEBULA       // future
}
