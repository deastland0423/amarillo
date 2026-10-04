package com.sfb.systemgroups;

/**
 * Spare drones carried in a ship's cargo boxes (FD2.445).
 *
 * <h2>What this is, and what it is NOT</h2>
 * FD2.44 lists five places a drone can be. Two of them are pools measured in spaces, and they are
 * easy to confuse because the existing {@link DroneStore} was the first one built:
 * <ul>
 *   <li><b>FD2.443 fighter storage</b> — {@link DroneStore}, the J4.7 supply a CARRIER keeps to
 *       rearm its fighters. Nothing to do with cargo boxes; the Kzinti CV has 150 spaces and no
 *       cargo boxes at all.</li>
 *   <li><b>FD2.445 cargo storage</b> — this class. "Some drone-armed ships have cargo boxes to
 *       store extra drones. Unless otherwise specified a cargo box will hold 50 spaces of spare
 *       drones... These drones are lost when the cargo boxes are destroyed."</li>
 * </ul>
 * The Klingon D5D was declared with {@code droneStorageSpaces: 200} and that was the wrong field:
 * it built a 200-space FIGHTER supply on a ship with no fighters, which stocked itself with
 * nothing and published capacity 200 / held 0 to the client. Hence a separate type, so the two
 * cannot be reached for interchangeably again.
 *
 * <h2>Why two numbers and not one</h2>
 * Capacity falls with the boxes; held is a quantity with a history. Keeping them apart is what
 * stops a ship being punished twice for using its own supply: a D5D that has already drawn 60
 * spaces up into reload storage holds 140, so losing one box (capacity 200 → 150) costs it
 * nothing — those drones are already somewhere safer. Only once the surviving boxes could not
 * have held what is claimed does anything spill.
 *
 * <h2>Why held is NOT derived from the box count</h2>
 * It is tempting to compute capacity as {@code availableCargo * rate} and skip the bookkeeping.
 * That is wrong on REPAIR: {@code HullBoxes.repairCargo} can bring a cargo box back, and FD2.445
 * says the drones "are lost" when the boxes are destroyed. A repaired box is empty volume, not
 * recovered drones — so a derived figure would silently hand 50 spaces back. Contents are
 * consumed and destroyed, never restored, which is exactly what a derived value cannot express.
 *
 * <h2>Spaces, not drones (FD2.4422)</h2>
 * The measure is SPACES, as everywhere else in FD2. Drone TYPES are deliberately not tracked
 * here. FD2.445 makes cargo drones free and "proportional to the loading of the racks", and
 * FD2.45 lets stored drones "be of a less expensive, but ... not ... a more expensive, type than
 * the drones in the loading paid for" — so a player may declare cargo as plain standard drones
 * and keep anything special in reload storage, which FD2.423 protects until the last Excess
 * Damage box. Cargo is the first hit on one DAC column and appears on two more, so that is what
 * every rational player does, and it leaves nothing in here worth identifying.
 */
public class CargoDroneStore {

    /** FD2.445: "Unless otherwise specified a cargo box will hold 50 spaces of spare drones." */
    public static final int DEFAULT_SPACES_PER_BOX = 50;

    private final int spacesPerBox;
    private int capacitySpaces;
    private int spacesHeld;

    /**
     * @param cargoBoxes   cargo boxes the hull has when built
     * @param spacesPerBox FD2.445's rate, 50 unless the ship says otherwise
     */
    public CargoDroneStore(int cargoBoxes, int spacesPerBox) {
        this.spacesPerBox = Math.max(0, spacesPerBox);
        this.capacitySpaces = Math.max(0, cargoBoxes) * this.spacesPerBox;
        // FD2.445: a ship "does not have them automatically... unless specified in the ship
        // description" — and declaring this store IS that specification, so it sails full. The
        // D5D's SSD: "This ship has 200 spaces of extra drones in its cargo boxes (50/box)."
        this.spacesHeld = this.capacitySpaces;
    }

    public int getSpacesPerBox()   { return spacesPerBox; }
    public int capacitySpaces()    { return capacitySpaces; }
    public int spacesHeld()        { return spacesHeld; }
    public boolean isEmpty()       { return spacesHeld <= 0; }

    /**
     * One cargo box destroyed (FD2.445). Capacity falls by the box's worth; held spills only as
     * far as the surviving boxes can no longer account for it.
     *
     * @return spaces of drones actually lost, which is zero when the hold was already drawn down
     */
    public int loseOneBox() {
        capacitySpaces = Math.max(0, capacitySpaces - spacesPerBox);
        if (spacesHeld <= capacitySpaces)
            return 0;
        int lost = spacesHeld - capacitySpaces;
        spacesHeld = capacitySpaces;
        return lost;
    }

    /**
     * Draw up to {@code spaces} out of the hold, as FD2.4421 does when a rack reload empties a
     * slot in reload storage and a cargo drone moves up into it.
     *
     * @return spaces actually drawn, which may be less than asked
     */
    public int draw(int spaces) {
        int taken = Math.min(Math.max(0, spaces), spacesHeld);
        spacesHeld -= taken;
        return taken;
    }

    /** Put spaces back, never above capacity. Returns what was accepted. */
    public int restore(int spaces) {
        int room = Math.max(0, capacitySpaces - spacesHeld);
        int accepted = Math.min(Math.max(0, spaces), room);
        spacesHeld += accepted;
        return accepted;
    }

    @Override
    public String toString() {
        return "cargo drones " + spacesHeld + "/" + capacitySpaces
                + " spaces (" + spacesPerBox + " a box)";
    }
}
