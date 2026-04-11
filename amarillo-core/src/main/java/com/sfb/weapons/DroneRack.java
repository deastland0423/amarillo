package com.sfb.weapons;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.utilities.ArcUtils;

public class DroneRack extends Weapon implements Launcher {

	private DroneRackType type = null; // The type of drone rack (A-H)

	private int spaces = 0; // The number of spaces in the rack (usually 4 or 6)

	private List<Drone> ammoList = new ArrayList<Drone>(); // The drones in the rack.
	private List<List<Drone>> reloads = new ArrayList<>(); // Each entry is one full reload set.
	private int numberOfReloads = 0; // The number of reload sets available (mirrors reloads.size()).

	private boolean reloadingThisTurn = false; // True if this rack is being reloaded this turn — blocks firing.
	private List<Drone> pendingReloadSet = null; // Drones staged for reload — in transit until 8C.

	private int addAmmo = 0; // The number of ADD shots in the drone rack.

	private int addReloads = 0; // The number of ADD reloads available.

	// Base constructor. Sets the arcs to full.
	public DroneRack() {
		setDacHitLocaiton("drone");
		setType("Drone");
		setArcs(ArcUtils.FULL);
	}

	// Constructor with type. Sets the arcs to full.
	public DroneRack(DroneRackType type) {
		this();
		this.type = type;

		switch (type) {
			case TYPE_A:
				this.spaces = 4;
				this.numberOfReloads = 1;
				break;
			case TYPE_B:
				this.spaces = 6;
				this.numberOfReloads = 2;
				break;
			case TYPE_C:
				this.spaces = 4;
				this.numberOfReloads = 2;
				this.setMaxShotsPerTurn(2);
				this.setMinImpulseGap(12);
				break;
			case TYPE_D:
				this.spaces = 12;
				this.numberOfReloads = 2;
				break;
			case TYPE_E:
				this.spaces = 4;
				this.numberOfReloads = 1;
				break;
			case TYPE_F:
				this.spaces = 4;
				this.numberOfReloads = 1;
				break;
			case TYPE_G:
				this.spaces = 4;
				this.numberOfReloads = 1;
				break;
			case TYPE_H:
				this.spaces = 20;
				this.numberOfReloads = 2;
				break;
			default: // Defaults to Type A if no type is specified.
				this.spaces = 4;
				this.numberOfReloads = 1;
		}
	}

	// Enum for the different types of drone racks. Each type has a different number
	// of spaces and different reloads.
	public enum DroneRackType {
		TYPE_A,
		TYPE_B,
		TYPE_C,
		TYPE_D,
		TYPE_E,
		TYPE_F,
		TYPE_G,
		TYPE_H

	}

	public DroneRackType getRackType() {
		return type;
	}

	public void setType(DroneRackType type) {
		this.type = type;
	}

	@Override
	public Drone launch(int weaponNumber) {
		Drone launchedDrone = ammoList.get(weaponNumber);
		return launchedDrone;
	}

	/**
	 * Get a list of drones ready to fire.
	 * 
	 * @return A list of drones in the rack.
	 */
	public List<Drone> getAmmo() {
		return ammoList;
	}

	public int getSpaces() {
		return spaces;
	}

	public void setSpaces(int spaces) {
		this.spaces = spaces;
	}

	public int getAddAmmo() {
		return addAmmo;
	}

	public void setAddAmmo(int addAmmo) {
		this.addAmmo = addAmmo;
	}

	/**
	 * Returns all available reload sets. Each entry is one full loadout
	 * that can be loaded into the rack during energy allocation.
	 */
	public List<List<Drone>> getReloads() {
		return reloads;
	}

	public int getAddReloads() {
		return addReloads;
	}

	public void setAddReloads(int addReloads) {
		this.addReloads = addReloads;
	}

	/**
	 * Set the rack's initial ammo and automatically build reload sets.
	 * Each reload set is an identical copy of the initial ammo list.
	 * The number of sets built equals numberOfReloads (set before calling this).
	 */
	public void setAmmo(List<Drone> ammoList) {
		this.ammoList = ammoList;
		this.reloads = new ArrayList<>();
		for (int i = 0; i < numberOfReloads; i++) {
			List<Drone> set = new ArrayList<>();
			for (Drone d : ammoList) {
				if (d.getDroneType() != null) set.add(new Drone(d.getDroneType()));
			}
			if (!set.isEmpty()) this.reloads.add(set);
		}
	}

	public int getNumberOfReloads() {
		return reloads.isEmpty() ? numberOfReloads : reloads.size();
	}

	public void setNumberOfReloads(int numberOfReloads) {
		this.numberOfReloads = numberOfReloads;
	}

	/**
	 * Checks if the drone rack is empty.
	 *
	 * @return True if there is no ammo in the rack, false otherwise.
	 */
	public boolean isEmpty() {
		return ammoList.size() == 0;
	}

	/**
	 * A rack cannot fire if it is being reloaded this turn.
	 */
	@Override
	public boolean canFire() {
		return !reloadingThisTurn && super.canFire();
	}

	/**
	 * Record that a drone was launched this impulse, stamping the once-per-turn
	 * and 8-impulse cooldown timestamps.
	 */
	public void recordLaunch() {
		registerFire();
	}

	/**
	 * Stage a reload during energy allocation (Phase 5 / EA).
	 * The drones are held in transit — they do not enter the rack yet and are
	 * not removed from reloads yet. The rack is blocked from firing this turn.
	 */
	public void stagePendingReload(List<Drone> reloadSet) {
		this.pendingReloadSet = reloadSet;
		this.reloadingThisTurn = true;
	}

	/**
	 * Complete the reload during Record Keeping 8C.
	 * If the rack is still functional, the pending drones move into ammo and the
	 * reload set is consumed. If the rack was destroyed during the turn, the
	 * pending drones are returned to reloads so they are not lost.
	 */
	public void completePendingReload() {
		if (pendingReloadSet == null) return;
		if (isFunctional()) {
			this.ammoList = new ArrayList<>(pendingReloadSet);
			this.reloads.remove(pendingReloadSet);
		} else {
			// Rack was destroyed — return drones to reloads (they survive)
			if (!this.reloads.contains(pendingReloadSet)) {
				this.reloads.add(pendingReloadSet);
			}
		}
		this.pendingReloadSet = null;
	}

	/**
	 * Returns whether this rack is being reloaded this turn.
	 */
	public boolean isReloadingThisTurn() {
		return reloadingThisTurn;
	}

	/**
	 * Returns the reload set currently staged for this rack, or null if none.
	 */
	public List<Drone> getPendingReloadSet() {
		return pendingReloadSet;
	}

	/**
	 * Calculates the total deck crew cost (sum of rackSize) of a reload set.
	 */
	public static double reloadCost(List<Drone> reloadSet) {
		double cost = 0;
		for (Drone d : reloadSet) {
			cost += d.getRackSize();
		}
		return cost;
	}

	@Override
	public void cleanUp() {
		super.cleanUp();
		// 8C: complete any pending reload before clearing the reloading flag
		completePendingReload();
		reloadingThisTurn = false;
	}
}
