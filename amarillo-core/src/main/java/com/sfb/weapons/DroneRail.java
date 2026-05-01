package com.sfb.weapons;

import com.sfb.objects.Drone;
import com.sfb.utilities.ArcUtils;

import java.util.List;

/**
 * Single-slot drone launcher carried by fighters.
 * Rail type determines which drone sizes are accepted.
 */
public class DroneRail extends DroneRack {

    public enum DroneRailType {
        LIGHT(0.5),     // TypeVI only
        STANDARD(1.0),  // TypeI or TypeVI
        HEAVY(2.0);     // TypeIV, TypeI, or TypeVI

        public final double capacity;
        DroneRailType(double capacity) { this.capacity = capacity; }
    }

    private DroneRailType railType;

    public DroneRail() {
        this(DroneRailType.STANDARD);
    }

    public DroneRail(DroneRailType type) {
        setDacHitLocaiton("drone");
        setType("DroneRail");
        setArcs(ArcUtils.FULL);
        this.railType = type;
        setSpaces((int) Math.ceil(type.capacity));
        setNumberOfReloads(0);
    }

    public DroneRailType getRailType() { return railType; }

    /**
     * Load a single drone into this rail.
     * @throws IllegalArgumentException if the drone is too large for this rail type.
     */
    public void loadDrone(Drone drone) {
        if (drone.getRackSize() > railType.capacity) {
            throw new IllegalArgumentException(
                drone.getDroneType() + " (size " + drone.getRackSize()
                + ") does not fit a " + railType + " rail (capacity " + railType.capacity + ")");
        }
        setAmmo(List.of(drone));
    }

    /** The drone currently loaded, or null if empty. */
    public Drone getDrone() {
        return getAmmo().isEmpty() ? null : getAmmo().get(0);
    }
}
