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
        HEAVY(2.0), // TypeIV, TypeI, or TypeVI
        /**
         * A type-D plasma torpedo rail (FP9.2) - a rail, not a plasma launcher, because the
         * rules make it one. FP9.21: type-Ds are "stored, transported, handled, and loaded as
         * drones are, each taking one 'space'", and J4.825 adds that "the rearming and storage
         * rules for drones are used for type-D plasma torpedoes" and that one "is the same
         * size as a one-space drone".
         * <p>
         * So everything about the MOUNT is a drone rail: the box's ready rack stocks it, a deck
         * crew loads it, and it occupies one space. Only the payload differs, and a torpedo is
         * not a drone - which is why it is held in a field of its own rather than in the
         * inherited drone ammo list. That also means every drone path asking
         * {@link #getDrone()} sees null here and skips the rail, which is exactly right: a
         * drone launch must never fire a plasma torpedo.
         * <p>
         * J4.825 again: "No fighter in the game can use both type-D plasmas and drones, so you
         * cannot load drones on a plasma-D-armed fighter (nor vice versa)."
         */
        PLASMA_D(1.0),
        /**
         * A type-K plasma rail — a "K-RAIL" in the rulebook's own words (FP13.31): "A few
         * fighters (see racial sections) were designed to carry plasma-Ks on light rails,
         * including the Gorn copy of the Shenyang F-7 (R6.F11)."
         * <p>
         * Half a space, because FP13.32 says "a plasma-K capsule is half of the size of a
         * plasma-D" — so it also costs half a deck crew action to load.
         * <p>
         * Its OWN type rather than LIGHT-with-plasma, even though FP13.31 calls these light
         * rails. A plain LIGHT rail means "one type-VI drone" (J4.232), and J4.28 keeps drones
         * and plasma off each other's rails entirely, so overloading LIGHT would make it mean
         * two incompatible things depending on which fighter it was bolted to.
         * <p>
         * Distinct from a type-D rail loaded with a K, which is a different thing and also
         * legal: FP13.3 lets "a plasma-K replace a plasma-D on a launch rail (one for one) of a
         * fighter... that carries plasma-Ds", without raising the launch rate. So a D-rail takes
         * either; a K-rail only ever takes a K.
         */
        PLASMA_K(0.5);

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
        setDisplayName("Drone Rail");
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
            // A plasma rail has no design DRONE: J4.825 bars drones from such a fighter
            // outright, so there is no ordinary drone load for a rack to reach for.
            case PLASMA_D, PLASMA_K -> null;
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
        if (carriesPlasma())
            return false;   // J4.825 / J4.28: no drones on a plasma-armed fighter
        return drone != null && drone.getRackSize() <= railType.capacity;
    }

    /**
     * True if this rail carries plasma torpedoes rather than drones — a type-D rail (FP9.2) or a
     * type-K rail (FP13.31). J4.28: plasma "use a unique type of launch rail which cannot carry
     * drones", so this is the question nearly every caller means.
     */
    public boolean carriesPlasma() {
        return railType == DroneRailType.PLASMA_D || railType == DroneRailType.PLASMA_K;
    }

    /**
     * Which torpedo this rail is built for, or null on a drone rail.
     * <p>
     * A K-rail only ever takes a type-K; a D-rail is built for a type-D but may carry a type-K
     * one for one instead (FP13.3), which is a LOADING choice and not a property of the rail.
     */
    public com.sfb.properties.PlasmaType plasmaType() {
        return switch (railType) {
            case PLASMA_D -> com.sfb.properties.PlasmaType.D;
            case PLASMA_K -> com.sfb.properties.PlasmaType.K;
            default -> null;
        };
    }

    /** True only for a type-D rail (FP9.2). Prefer {@link #carriesPlasma()} unless the type matters. */
    public boolean isPlasmaD() {
        return railType == DroneRailType.PLASMA_D;
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
        if (isPlasmaD())
            throw new IllegalStateException(getName() + " is a plasma-D rail; no fighter can"
                    + " use both type-D plasmas and drones (J4.825)");
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

    // ---------------------------------------------------------------- plasma-D (FP9.2)

    /**
     * The torpedo on a plasma-D rail. Held apart from the inherited drone ammo list because a
     * {@link com.sfb.objects.PlasmaTorpedo} is not a {@link Drone}: that list is typed for
     * drones, is shared with every ship rack, and carries reload sets and size trimming that
     * mean nothing to a torpedo.
     */
    private com.sfb.objects.PlasmaTorpedo torpedo;

    /**
     * Put a type-D torpedo on the rail. The handling is a drone's (J4.825), so this is the
     * deck crew's single action and nothing else - FP9.22's activation is a separate step and
     * a separate half point of energy.
     */
    public void loadTorpedo(com.sfb.objects.PlasmaTorpedo loaded) {
        loadTorpedo(loaded, false);
    }

    /**
     * As above, saying whether it arrives already activated.
     * <p>
     * FP9.22's last clause is the reason this exists: "Torpedoes on fighters assumed to be
     * loaded before a scenario (due to weapon status) are assumed to be active." A torpedo a
     * deck crew puts on mid-game is NOT - it needs its half point first.
     */
    public void loadTorpedo(com.sfb.objects.PlasmaTorpedo loaded, boolean alreadyActive) {
        if (!isPlasmaD())
            throw new IllegalStateException(getName() + " is a " + railType
                    + " rail and cannot carry a plasma torpedo (FP9.2)");
        this.torpedo = loaded;
        this.torpedoActivated = loaded != null && alreadyActive;
    }

    // ------------------------------------------------- activation (FP9.22, FP10.32)

    /**
     * FP9.22: "When placed on a fighter ready rack, plasma rack, or fighter, they can be
     * activated, which requires 1/2 of an energy point (reserve or allocated) per torpedo. The
     * weapon cannot be launched until it has been activated."
     * <p>
     * Half a point, which is why this is a constant rather than an int somewhere: the fighter
     * energy accounting elsewhere deals in whole points, and a figure of 1 here would double
     * what a carrier pays to make its squadron dangerous.
     */
    public static final double ACTIVATION_ENERGY = 0.5;

    /**
     * Activation belongs to the TORPEDO IN THIS MOUNT, not to the torpedo itself, which is
     * what FP10.33 requires: "an activated torpedo automatically switches itself off when
     * unloaded from a rack/fighter and requires new activation energy after being installed on
     * another rack/fighter." Keeping the flag on the rail gets that for nothing - the torpedo
     * carries no state to leak into its next mount.
     */
    private boolean torpedoActivated;

    public boolean isTorpedoActivated() {
        return torpedoActivated;
    }

    /**
     * Spend energy activating the torpedo on this rail.
     * <p>
     * FP10.32 says where the energy may come from: "This can be supplied during energy
     * allocation or by reserve power at any point after loading and before firing. Torpedoes
     * activated by reserve power can be fired immediately (within the Sequence of Play)." So
     * there is no timing restriction to enforce here - any point before firing will do, which
     * is why this takes energy and not an impulse.
     *
     * @param energy energy offered
     * @return true if the torpedo is now active; false if there was nothing to activate, it
     *         was active already, or the energy was short of half a point
     */
    public boolean activateTorpedo(double energy) {
        if (!isPlasmaD() || torpedo == null || torpedoActivated)
            return false;
        if (energy + 1e-9 < ACTIVATION_ENERGY)
            return false;
        torpedoActivated = true;
        return true;
    }

    /** Energy this rail still needs before its torpedo could be fired, or zero. */
    public double activationEnergyWanted() {
        return isPlasmaD() && torpedo != null && !torpedoActivated ? ACTIVATION_ENERGY : 0;
    }

    /**
     * FP9.22: "The weapon cannot be launched until it has been activated." The gate a launch
     * asks, so a loaded-but-inert torpedo is refused rather than fired.
     */
    public boolean canLaunchTorpedo() {
        return isPlasmaD() && torpedo != null && torpedoActivated;
    }

    public com.sfb.objects.PlasmaTorpedo getTorpedo() {
        return torpedo;
    }

    /** Take the torpedo off - a launch, or a deck crew unloading it. */
    public com.sfb.objects.PlasmaTorpedo removeTorpedo() {
        com.sfb.objects.PlasmaTorpedo was = torpedo;
        torpedo = null;
        // FP10.33: it switches itself off on the way out, and the next mount pays again.
        torpedoActivated = false;
        return was;
    }

    /**
     * Whether this rail is carrying anything at all, whichever kind of rail it is.
     * <p>
     * Ask THIS rather than {@code getDrone() != null} anywhere the question is "has this rail
     * still got something on it" - a loaded plasma-D rail answers null to {@code getDrone()},
     * and code reading that as "empty" would try to load a drone onto it and break J4.825.
     */
    public boolean isLoaded() {
        return isPlasmaD() ? torpedo != null : getDrone() != null;
    }
}
