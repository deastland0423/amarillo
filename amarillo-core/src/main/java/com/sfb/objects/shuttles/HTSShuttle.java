package com.sfb.objects.shuttles;

/**
 * Heavy Transport Shuttle — reinforced hull for cargo and boarding operations.
 * Hull: 12, Max speed: 6, No weapons.
 */
public class HTSShuttle extends Shuttle {

    public HTSShuttle() {
        setHull(12);
        setMaxSpeed(6);
        setPersonnelCapacity(2); // one crew unit OR two boarding parties (J2.211)
        setCargoCapacity(50); // base cargo spaces (G25.131; scaffolded, not yet enforced)
    }
}
