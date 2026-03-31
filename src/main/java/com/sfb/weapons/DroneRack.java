package com.sfb.weapons;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;

public class DroneRack extends Weapon implements Launcher {

	private DroneRackType type = null; // The type of drone rack (A-H)

	private int spaces = 0; // The number of spaces in the rack (usually 4 or 6)

	private List<Drone> ammoList = new ArrayList<Drone>(); // The drones in the rack.
	private List<Drone> reloads = new ArrayList<Drone>(); // The drone reloads available.
	private int numberOfReloads = 0; // The number of reloads available.

	private int addAmmo = 0; // The number of ADD shots in the drone rack.

	private int addReloads = 0; // The number of ADD reloads available.

	// Base constructor. Sets the arcs to full.
	public DroneRack() {
		setDacHitLocaiton("drone");
		setType("Drone");
		setArcs(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24 });
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

	public List<Drone> getReloads() {
		return reloads;
	}

	public void setReloads(List<Drone> reloads) {
		this.reloads = reloads;
	}

	public int getAddReloads() {
		return addReloads;
	}

	public void setAddReloads(int addReloads) {
		this.addReloads = addReloads;
	}

	public void setAmmo(List<Drone> ammoList) {
		this.ammoList = ammoList;
		// Reloads always mirror the initial loadout — build a fresh copy.
		List<Drone> reloadCopy = new ArrayList<>();
		for (Drone d : ammoList) {
			reloadCopy.add(new Drone(d.getDroneType()));
		}
		this.reloads = reloadCopy;
	}

	public int getNumberOfReloads() {
		return numberOfReloads;
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
	 * Record that a drone was launched this impulse, stamping the once-per-turn
	 * and 8-impulse cooldown timestamps.
	 */
	public void recordLaunch() {
		registerFire();
	}
}
