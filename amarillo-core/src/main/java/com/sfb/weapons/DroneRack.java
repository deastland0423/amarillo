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

		applyTypeStats(type);
	}

	/**
	 * The statistics a rack has by virtue of its type: capacity, reload sets, and the two
	 * types that fire at a rate of their own.
	 *
	 * ONE table. The constructor and {@link #upgradeRackType} each used to carry a copy, and
	 * they drifted — the type-B gained its second reload set in one and not the other, so a
	 * ship that STARTED with a B rack carried two sets while a ship REFITTED to one carried
	 * a single set. Same rack, different ammunition, depending on how it got there.
	 *
	 * Reload counts are fixed per type (owner's reading of the SSDs, 2026-09-25): A and F
	 * one, B and C two, G two per FD3.72 — one of the G's being entirely anti-drones, with
	 * the Y175 refit adding a third. Type-G is the only type a ship file may add to.
	 */
	private void applyTypeStats(DroneRackType type) {
		setMaxShotsPerTurn(1);
		setMinImpulseGap(8);          // FD3.0: a quarter turn between launches
		switch (type) {
			case TYPE_B:
				this.spaces = 6;
				this.numberOfReloads = 2;
				break;
			case TYPE_C:
				// FD3.3: "rapid fire" — two per turn, and not within twelve impulses of
				// each other, even on consecutive turns.
				this.spaces = 4;
				this.numberOfReloads = 2;
				setMaxShotsPerTurn(2);
				setMinImpulseGap(12);
				break;
			case TYPE_D:
				this.spaces = 12;     // three magazines of four (FD3.4)
				this.numberOfReloads = 2;
				break;
			case TYPE_G:
				this.spaces = 4;
				this.numberOfReloads = 2;
				break;
			case TYPE_H:
				this.spaces = 20;     // five magazines (FD3.8)
				this.numberOfReloads = 2;
				break;
			case TYPE_A:
			case TYPE_E:
			case TYPE_F:              // FD3.6: functionally a type-A
			default:
				this.spaces = 4;
				this.numberOfReloads = 1;
				break;
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

	/**
	 * Re-initialize this rack as a new type, preserving designator, arcs, and ammo
	 * (capped to new space limit).
	 */
	public void upgradeRackType(DroneRackType newType) {
		this.type = newType;
		applyTypeStats(newType);
		// Trim loaded ammo to new space limit
		double usedSpaces = ammoList.stream().mapToDouble(d -> d.getRackSize()).sum();
		while (usedSpaces > spaces && !ammoList.isEmpty()) {
			usedSpaces -= ammoList.remove(ammoList.size() - 1).getRackSize();
		}
		reloads.clear();
	}

	/** Add N extra reload sets (for faction upgrades like Federation TYPE_G). */
	public void addReloads(int count) {
		this.numberOfReloads += count;
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
				if (d.getDroneType() != null)
					set.add(new Drone(d.getDroneType()));
			}
			if (!set.isEmpty())
				this.reloads.add(set);
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
		if (pendingReloadSet == null)
			return;
		if (isFunctional()) {
			this.ammoList = new ArrayList<>(pendingReloadSet);
			// For full-set reloads the list is reference-identical, so remove() finds it.
			// For custom (partial) reloads the drones were already removed from their sets
			// during staging, so remove() is a no-op; clean up any now-empty sets instead.
			this.reloads.remove(pendingReloadSet);
			this.reloads.removeIf(List::isEmpty);
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

	/**
	 * Returns all drone types available to load in this rack for the given scenario
	 * year
	 * and optional speed cap. Only types whose rack size fits within this rack's
	 * space
	 * count are included (a TYPE_A rack with 4 spaces can hold TypeIV at 2.0
	 * spaces).
	 *
	 * @param year          the scenario year
	 * @param maxDroneSpeed maximum allowed drone speed, or null for year-based only
	 */
	public static java.util.List<com.sfb.objects.DroneType> availableTypes(
			int year, Integer maxDroneSpeed) {
		java.util.List<com.sfb.objects.DroneType> result = new java.util.ArrayList<>();
		for (com.sfb.objects.DroneType dt : com.sfb.objects.DroneType.values()) {
			if (!dt.availableIn(year))
				continue;
			if (maxDroneSpeed != null && dt.speed > maxDroneSpeed)
				continue;
			result.add(dt);
		}
		return result;
	}

	@Override
	public void cleanUp() {
		// FD3.0: "no drone rack can fire two drones within 1/4 turn of each other, EVEN IF
		// ON DIFFERENT TURNS" — repeated for the type-C and type-E, and spelled out for the
		// type-G at FD3.71: the delay "includes the last firing on one turn and the first
		// firing on the next".
		//
		// So the per-turn shot COUNTER resets with the turn, but the timestamp the gap is
		// measured from must not. Weapon.cleanUp clears that timestamp so an ordinary weapon
		// starts each turn free — right for a phaser, which would otherwise be locked out of
		// the first eight impulses after firing late in the turn before, and wrong for a
		// rack, which was handing back a launch at every turn boundary.
		int firedAt = getLastImpulseFired();
		super.cleanUp();
		setLastImpulseFired(firedAt);
		// 8C: complete any pending reload before clearing the reloading flag
		completePendingReload();
		reloadingThisTurn = false;
	}
}
