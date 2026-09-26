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

	/**
	 * FD3.70: "Each anti-drone takes 1/2 space" — the same half space a dogfight drone
	 * costs, out of the same four the rack has.
	 */
	public static final double ANTI_DRONE_SPACE = 0.5;

	/**
	 * FD3.72: "Anti-drones are not available prior to Y140 (E5.0), type-VI drones are used
	 * prior to Y140."
	 */
	public static final int ANTI_DRONE_FIRST_YEAR = 140;

	private int addAmmo = 0; // Anti-drone rounds loaded in the rack (1/2 space each).

	/**
	 * Which way a type-G is working this turn (FD3.71).
	 *
	 * Nobody declares it. The rack is UNDECIDED until its first shot of the turn, and that
	 * shot settles it: "The decision as to which mode to use is made the first time (each
	 * turn) it is fired." Every other rack type stays UNDECIDED for ever, since it has only
	 * one thing it can do.
	 */
	public enum RackMode {
		UNDECIDED, DRONE, ANTI_DRONE
	}

	private RackMode modeThisTurn = RackMode.UNDECIDED;

	/** The impulse an anti-drone round last went out on; -9 so the first shot is free. */
	private int lastAntiDroneImpulse = -9;

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
			case TYPE_E:
				// FD3.5: eight dogfight drones — four spaces, since a type-VI is half a
				// space each. Fires FOUR times per turn, but is still held to the ordinary
				// quarter-turn gap, which is exactly what four launches in 32 impulses
				// costs. One reload.
				this.spaces = 4;
				this.numberOfReloads = 1;
				setMaxShotsPerTurn(4);
				break;
			case TYPE_A:
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
		// A rack that is no longer a type-G has no targeting system for anti-drones, so
		// whatever it was carrying goes with the refit.
		if (!acceptsAntiDrones())
			this.addAmmo = 0;
		// Trim loaded ammo to new space limit
		double usedSpaces = ammoList.stream().mapToDouble(d -> d.getRackSize()).sum();
		while (usedSpaces > spaces && !ammoList.isEmpty()) {
			usedSpaces -= ammoList.remove(ammoList.size() - 1).getRackSize();
		}
		reloads.clear();
	}

	/**
	 * Whether this rack may carry a drone of that type — both directions of the rule.
	 *
	 * FD2.51: dogfight (type-VI) drones "cannot be loaded on or fired by any drone racks
	 * except E and G", with the type-H carrying a magazine of them too (FD3.81).
	 * FD3.5, the other way about: the E rack holds dogfight drones and "can carry no other
	 * types" — which nothing enforced, so a type-E would happily take a Type-I.
	 *
	 * One method because it is one question, and the COI loader is not the only place that
	 * will ever ask it.
	 */
	public boolean accepts(com.sfb.objects.DroneType droneType) {
		if (droneType == null)
			return false;
		if (type == DroneRackType.TYPE_E)
			return droneType.isTypeVI();
		if (droneType.isTypeVI())
			return type == DroneRackType.TYPE_G || type == DroneRackType.TYPE_H;
		return true;
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

	/**
	 * How much of the rack is spoken for — drones and anti-drones together, because FD3.70
	 * gives them one magazine of four spaces between them.
	 *
	 * One method so the two kinds cannot disagree about how full the rack is. Every space
	 * question goes through here.
	 */
	public double spacesUsed() {
		double used = 0;
		for (Drone d : ammoList)
			used += d.getRackSize();
		return used + addAmmo * ANTI_DRONE_SPACE;
	}

	/** What is left, in spaces. */
	public double spacesFree() {
		return spaces - spacesUsed();
	}

	/**
	 * Whether this rack may carry anti-drone rounds at all.
	 *
	 * Only the type-G: it is the one "equipped with targeting system for anti-drones"
	 * (FD3.70). A type-D is told outright it may not (FD3.4), and the anti-drones on
	 * starbases are a separate five-magazine launcher of their own (FD3.86/E5.53) rather
	 * than something the type-H rack carries.
	 */
	public boolean acceptsAntiDrones() {
		return type == DroneRackType.TYPE_G;
	}

	/**
	 * Whether this rack could take {@code count} more anti-drone rounds in the given year.
	 *
	 * @param year the game year, for the Y140 gate; pass 0 to skip the check
	 */
	public boolean canLoadAntiDrones(int count, int year) {
		if (count <= 0 || !acceptsAntiDrones())
			return false;
		if (year > 0 && year < ANTI_DRONE_FIRST_YEAR)
			return false;
		return count * ANTI_DRONE_SPACE <= spacesFree() + 1e-9;
	}

	/**
	 * Load anti-drone rounds into the rack, up to what will fit.
	 *
	 * @return the number actually loaded, which is 0 if this rack cannot carry them
	 */
	public int loadAntiDrones(int count, int year) {
		if (!canLoadAntiDrones(count, year))
			return 0;
		addAmmo += count;
		return count;
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
		return ammoList.isEmpty() && addAmmo == 0;
	}

	/**
	 * Whether a DRONE may be launched now. The long-standing meaning of canFire for a rack,
	 * and what every existing caller wants, so the name keeps it.
	 *
	 * FD3.71 adds one clause for the type-G: a rack that has already fired an anti-drone
	 * this turn "cannot fire normal drones that turn".
	 */
	@Override
	public boolean canFire() {
		return !reloadingThisTurn
				&& modeThisTurn != RackMode.ANTI_DRONE
				&& super.canFire();
	}

	/**
	 * Whether an ANTI-DRONE round may be fired now — one per impulse, and no quarter-turn
	 * gap between them (owner's ruling 2026-09-25, and FD3.71's own example of firing on
	 * impulse 32 and continuing on impulse 1 "with no delay").
	 *
	 * The mode is the only thing that stops it: having launched a drone this turn, the rack
	 * is in drone mode until the turn ends.
	 */
	public boolean canFireAntiDrone() {
		return acceptsAntiDrones()
				&& !reloadingThisTurn
				&& isFunctional()
				&& addAmmo > 0
				&& modeThisTurn != RackMode.DRONE
				&& clock.getImpulse() > lastAntiDroneImpulse;
	}

	/** Which way this rack is committed for the turn; UNDECIDED until its first shot. */
	public RackMode getModeThisTurn() {
		return modeThisTurn;
	}

	/**
	 * Record that a drone was launched this impulse, stamping the once-per-turn
	 * and 8-impulse cooldown timestamps.
	 */
	public void recordLaunch() {
		modeThisTurn = RackMode.DRONE;
		registerFire();
	}

	/**
	 * Record an anti-drone round going out: it commits the rack to anti-drone mode for the
	 * turn, spends a round, and stamps the shared launch timestamp.
	 *
	 * That last part is FD3.71's "ADD fire counts as a drone launch event for this purpose"
	 * — so a rack that fires anti-drones to the end of a turn must still wait out the
	 * quarter-turn gap before launching a drone in the next one.
	 *
	 * @return false if the rack had nothing to fire or was not free to fire it
	 */
	public boolean recordAntiDroneFire() {
		if (!canFireAntiDrone())
			return false;
		modeThisTurn = RackMode.ANTI_DRONE;
		addAmmo--;
		lastAntiDroneImpulse = clock.getImpulse();
		registerFire();
		return true;
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
		// FD3.71: the choice is made afresh each turn, so a new turn finds the rack
		// undecided again — but the timestamps above, which span the boundary, do not move.
		modeThisTurn = RackMode.UNDECIDED;
		// 8C: complete any pending reload before clearing the reloading flag
		completePendingReload();
		reloadingThisTurn = false;
	}
}
