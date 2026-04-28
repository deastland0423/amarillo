package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.exceptions.CapacitorException;
import com.sfb.objects.Unit;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;

/**
 * The collection of weapons on a ship.
 * 
 * @author Daniel Eastland
 *
 */
public class Weapons implements Systems {

	List<Weapon> weapons = new ArrayList<>();						// A list of all the weapons.
	
	private double       phaserCapacitor	= 0;					// Initial size of the phaser capacitor
	
	private List<Weapon> phaserList			= new ArrayList<>();	// List of all phasers
	private List<Weapon> torpList			= new ArrayList<>();	// List of all 'torp' type weapons (usually heavy weapons)
	private List<Weapon> droneList			= new ArrayList<>();	// List of all 'drone' type weapons (usually torps)
	
	private int          availablePhasers;							// Items hit on 'phaser' in the DAC
	private int          availableTorps;							// Items hit on 'torp' in the DAC
	private int          availableDrones;							// Items hit on 'drone' in the DAC
	private double       availablePhaserCapacitor;					// Current size of the phaser capacitor.
	
	private double       phaserCapacitorEnergy;						// Energy currently in the phaser capacitor.
	
	private Unit         owningShip;								// The ship on which this weapons system is mounted.
	
	public Weapons(Unit owningShip) {
		this.owningShip = owningShip;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void init(Map<String, Object> values) {
		weapons = values.get("weapons") == null ? new ArrayList<Weapon>() : (List<Weapon>)values.get("weapons");
		if (weapons != null) {
			for (Weapon weapon : weapons) {
				// Make sure the weapon knows what unit owns it.
				weapon.setOwningShip(owningShip);
				// Register a new phaser weapon
				if ("phaser".equals(weapon.getDacHitLocaiton())) {
					phaserList.add(weapon);
					
					// Increase the size of the phaser capacitor to match 
					if (weapon instanceof Phaser1 || weapon instanceof Phaser2) {
						phaserCapacitor++;
					}
					
					if (weapon instanceof Phaser3) {
						phaserCapacitor += 0.5;
					}

					if (weapon instanceof PhaserG) {
						phaserCapacitor += 1.0;
					}
				}
				// Register a new 'torp' type weapon
				if ("torp".equals(weapon.getDacHitLocaiton())) {
					torpList.add(weapon);
				}
				// Register a new 'drone' type weapon
				if ("drone".equals(weapon.getDacHitLocaiton())) {
					droneList.add(weapon);
				}
			}
		}		
		availablePhasers = phaserList.size();
		availableTorps = torpList.size();
		availableDrones = droneList.size();
		availablePhaserCapacitor = phaserCapacitor;
	}
	
	@Override
	public void cleanUp() {
		for (Weapon w : weapons) {
			w.cleanUp();
		}
	}

	/**
	 * Given a shooter and a target, fetch all weapons that have range and arc on the target. 
	 * @param source The shooter
	 * @param target The target
	 * @return All weapons on the shooter that have arc and range on the target.
	 */
	public List<Weapon> fetchAllBearingWeapons(Unit source, Unit target) {
		List<Weapon> bearingWeapons = new ArrayList<>();
		
		// Loop through all weapons, finding which ones are good to fire.
		for (Weapon weapon : weapons) {
			// Check to see that the target is in range.
			boolean inRange = MapUtils.getRange(source, target) <= weapon.getMaxRange();

			// Check to see that the target is in arc.
			// At range 0 (same hex), getBearing returns 0. Determine the true bearing
			// from the entry directions recorded when each unit last moved into this hex.
			int trueBearingOfTarget = MapUtils.getBearing(source, target);
			if (trueBearingOfTarget == 0) {
				int srcEntry = source.getEntryDirection();
				int tgtEntry = target.getEntryDirection();
				if (srcEntry != 0) {
					// Source moved into the hex — target is dead ahead (entry direction)
					trueBearingOfTarget = srcEntry;
				} else if (tgtEntry != 0) {
					// Target moved into source's hex — target arrived from the opposite direction
					trueBearingOfTarget = (tgtEntry - 1 + 12) % 24 + 1;
				} else {
					// Neither has an entry direction recorded; fall back to source's facing
					trueBearingOfTarget = source.getFacing();
				}
			}
			int relativeBearingToTarget = MapUtils.getRelativeBearing(trueBearingOfTarget, source.getFacing());
			boolean inArc = weapon.inArc(relativeBearingToTarget);
			
			// Heavy weapons must be armed before they appear as fireable options.
			boolean armed = !(weapon instanceof HeavyWeapon) || ((HeavyWeapon) weapon).isArmed();

			// Impulse gap and shots-per-turn must be satisfied.
			boolean gapOk = weapon.canFire();

			// If it is in range AND in arc AND ready AND functional, add it to the list.
			if (weapon.isFunctional() && inRange && inArc && armed && gapOk) {
				bearingWeapons.add(weapon);
			}
		}

		return bearingWeapons;
	}
	
	public List<Weapon> fetchAllWeapons() {
		return this.weapons;
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return phaserList.size() + torpList.size() + droneList.size();
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		long phasers = phaserList.stream().filter(Weapon::isFunctional).count();
		long torps   = torpList.stream().filter(Weapon::isFunctional).count();
		long drones  = droneList.stream().filter(Weapon::isFunctional).count();
		return (int)(phasers + torps + drones);
	}
	
	public double getPhaserCapacitorEnergy() {
		return phaserCapacitorEnergy;
	}
	
	public double getPhaserCapacitor() {
		return phaserCapacitor;
	}
	
	public void drainPhaserCapacitor(double energy) throws CapacitorException {
		if (energy > this.phaserCapacitorEnergy) {
			throw new CapacitorException("Not enough capacitor energy.");
		} else {
			this.phaserCapacitorEnergy -= energy;
		}
	}
	
	public void chargePhaserCapacitor(double energy) throws CapacitorException {
		if (this.phaserCapacitorEnergy + energy > this.availablePhaserCapacitor) {
			throw new CapacitorException("Too much energy for capacitor size.");
		} else {
			this.phaserCapacitorEnergy += energy;
		}
	}
	
	public double getAvailablePhaserCapacitor() {
		return availablePhaserCapacitor;
	}

	/**
	 * Recalculates the phaser capacitor size based on functional phasers only.
	 * Call this whenever a phaser is damaged or repaired.
	 * Also clamps the stored energy down if it now exceeds the new cap.
	 */
	public void recalculatePhaserCapacitor() {
		double cap = 0;
		for (Weapon w : phaserList) {
			if (w.isFunctional()) {
				if (w instanceof Phaser1 || w instanceof Phaser2) cap += 1.0;
				else if (w instanceof Phaser3)                    cap += 0.5;
				else if (w instanceof PhaserG)                    cap += 1.0;
			}
		}
		availablePhaserCapacitor = cap;
		if (phaserCapacitorEnergy > availablePhaserCapacitor) {
			phaserCapacitorEnergy = availablePhaserCapacitor;
		}
	}
	
	public int getAvailablePhasers() {
		return availablePhasers;
	}

	public int getAvailableTorps() {
		return availableTorps;
	}
	
	public int getAvailableDrones() {
		return availableDrones;
	}
	
	@Override
	public Unit fetchOwningUnit() {
		return this.owningShip;
	}
	
	public List<Weapon> getPhaserList() {
		return this.phaserList;
	}
	
	public List<Weapon> getTorpList() {
		return this.torpList;
	}
	
	public List<Weapon> getDroneList() {
		return this.droneList;
	}

	/**
	 * Add a single weapon programmatically (used by Fighter subclasses that build
	 * their weapon loadout in the constructor rather than from a JSON init map).
	 */
	public void addWeapon(Weapon weapon) {
		weapon.setOwningShip(owningShip);
		weapons.add(weapon);

		if ("phaser".equals(weapon.getDacHitLocaiton())) {
			phaserList.add(weapon);
			if (weapon instanceof Phaser1 || weapon instanceof Phaser2) phaserCapacitor += 1.0;
			else if (weapon instanceof Phaser3)                          phaserCapacitor += 0.5;
			else if (weapon instanceof PhaserG)                          phaserCapacitor += 1.0;
			availablePhasers          = phaserList.size();
			availablePhaserCapacitor  = phaserCapacitor;
		} else if ("torp".equals(weapon.getDacHitLocaiton())) {
			torpList.add(weapon);
			availableTorps = torpList.size();
		} else if ("drone".equals(weapon.getDacHitLocaiton())) {
			droneList.add(weapon);
			availableDrones = droneList.size();
		}
	}


}

