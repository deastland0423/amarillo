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
        LIGHT(0.5), // TypeVI only
        STANDARD(1.0), // TypeI or TypeVI
        SPECIAL(1.0), // TypeI or TypeIII
        HEAVY(2.0); // TypeIV, TypeI, or TypeVI

        public final double capacity;

        DroneRailType(double capacity) {
            this.capacity = capacity;
        }
    }

    private DroneRailType railType;

    /**
     * What this rail is designed to carry — what stocks its slot in the box's ready
     * rack
     * (J4.8222) and what a "fill it up" reaches for.
     * <p>
     * Per RAIL, not per fighter: a Kzinti TAAS has two standard rails and two light
     * ones, and
     * a Type-I will not fit the light ones at all. A fighter with mixed rails has
     * no single
     * answer, so the rail is where the answer lives.
     */
    private com.sfb.objects.DroneType designDrone;

    public DroneRail() {
        this(DroneRailType.STANDARD);
    }

    public DroneRail(DroneRailType type) {
        setDacHitLocaiton("drone");
        setType("DroneRail");
        setArcs(ArcUtils.FA);
        this.railType = type;
        this.designDrone = defaultDroneFor(type);
        setSpaces((int) Math.ceil(type.capacity));
        setNumberOfReloads(0);
    }

    /** The ordinary load for a rail of this size; a fighter may say otherwise. */
    private static com.sfb.objects.DroneType defaultDroneFor(DroneRailType type) {
        if (type == null)
            return null; // the no-arg constructor sets STANDARD before this runs
        return switch (type) {
            case LIGHT -> com.sfb.objects.DroneType.TypeVI; // half a space, dogfight
            case STANDARD -> com.sfb.objects.DroneType.TypeI;
            case SPECIAL -> com.sfb.objects.DroneType.TypeIII;
            case HEAVY -> com.sfb.objects.DroneType.TypeIV;
            default -> null;
        };
    }

    public com.sfb.objects.DroneType getDesignDrone() {
        return designDrone;
    }

    public void setDesignDrone(com.sfb.objects.DroneType drone) {
        if (drone != null && drone.rack <= railType.capacity)
            this.designDrone = drone;
    }

    /**
     * Whether a drone of this size can ride here at all (the check loadDrone
     * enforces).
     */
    public boolean accepts(com.sfb.objects.Drone drone) {
        return drone != null && drone.getRackSize() <= railType.capacity;
    }

    public DroneRailType getRailType() {
        return railType;
    }

    /**
     * Load a single drone into this rail.
     * 
     * @throws IllegalArgumentException if the drone is too large for this rail
     *                                  type.
     */
    /**
     * J4.962: "An EWP replaces one drone carried by the fighter." So a rail carries a
     * drone or a pod and never both — the SSD for an EW fighter shows exactly this, the
     * same rails as the standard model with pods on them and two fewer drones aboard.
     */
    private boolean ewPodFitted;

    public boolean hasEwPod() { return ewPodFitted; }

    /**
     * J4.2312: "EW pods can be carried on standard drone rails, but cannot be carried on
     * other types of rails."
     * <p>
     * Stricter than it looks. A pod is one space, so a half-space LIGHT rail obviously
     * cannot take one — but the rule bars a HEAVY rail too, which has room to spare. It is
     * about the fitting, not the volume.
     */
    public boolean canCarryEwPod() {
        return railType == DroneRailType.STANDARD;
    }

    /**
     * Fit an EW pod here, displacing whatever drone was on the rail.
     *
     * @return the drone that came off, or null if the rail was empty
     */
    public Drone fitEwPod() {
        if (!canCarryEwPod())
            throw new IllegalStateException(getName() + " is a " + railType
                    + " rail; only a standard rail carries an EW pod (J4.2312)");
        Drone displaced = getDrone();
        setAmmo(new java.util.ArrayList<>());
        ewPodFitted = true;
        return displaced;
    }

    /** Take the pod off, leaving the rail empty and able to take a drone again. */
    public boolean clearEwPod() {
        if (!ewPodFitted)
            return false;
        ewPodFitted = false;
        return true;
    }

    public void loadDrone(Drone drone) {
        if (ewPodFitted)
            throw new IllegalStateException(getName()
                    + " carries an EW pod; a rail holds one or the other (J4.962)");
        if (drone.getRackSize() > railType.capacity) {
            throw new IllegalArgumentException(
                    drone.getDroneType() + " (size " + drone.getRackSize()
                            + ") does not fit a " + railType + " rail (capacity " + railType.capacity + ")");
        }
        // A MUTABLE list: a launch takes the drone off the rack with
        // getAmmo().remove(),
        // the same way it does for a ship's rack, and List.of refused it. The rail was
        // only
        // ever loaded and emptied wholesale before, so nothing had asked.
        setAmmo(new java.util.ArrayList<>(List.of(drone)));
    }

    /** The drone currently loaded, or null if empty. */
    public Drone getDrone() {
        return getAmmo().isEmpty() ? null : getAmmo().get(0);
    }
}
