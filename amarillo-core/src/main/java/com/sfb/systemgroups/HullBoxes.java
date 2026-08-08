package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.ShipSpec;
import com.sfb.objects.Unit;

public class HullBoxes implements Systems {

	private int fhull;
	private int ahull;
	private int chull;
	private int armor;
	private int cargo;
	private int barracks; // Not a real hull box type, but used for boarding party calculations

	private int availableFhull;
	private int availableAhull;
	private int availableChull;
	private int availableArmor;
	private int availableCargo;
	private int availableBarracks;

	private Unit owningUnit = null;

	public HullBoxes() {
	}

	public HullBoxes(Unit owner) {
		this.owningUnit = owner;
	}

	// Use a <String, Integer> map to set the initial values.
	// Acceptable keys are: fhull, ahull, chull, cargo, barracks
	@Override
	public void init(Map<String, Object> values) {
		// If map has matching value, get it. Otherwise set to 0.
		availableFhull = fhull = values.get("fhull") == null ? 0 : (Integer) values.get("fhull");
		availableAhull = ahull = values.get("ahull") == null ? 0 : (Integer) values.get("ahull");
		availableChull = chull = values.get("chull") == null ? 0 : (Integer) values.get("chull");
		availableArmor = armor = values.get("armor") == null ? 0 : (Integer) values.get("armor");
		availableCargo = cargo = values.get("cargo") == null ? 0 : (Integer) values.get("cargo");
		availableBarracks = barracks = values.get("barracks") == null ? 0 : (Integer) values.get("barracks");

	}

	// Replace the Map-based init with a type-safe Spec-based init
	public void initFromSpec(ShipSpec.HullSpec spec) {
		this.fhull = spec.fhull;
		this.ahull = spec.ahull;
		this.chull = spec.chull;
		this.armor = spec.armor;
		this.cargo = spec.cargo;
		this.barracks = spec.barracks;

		// Reset available counts to max
		this.availableFhull = this.fhull;
		this.availableAhull = this.ahull;
		this.availableChull = this.chull;
		this.availableCargo = this.cargo;
		this.availableBarracks = this.barracks;
		this.availableArmor = this.armor;
	}

	///// GETTERS //////
	public int getAvailableFhull() {
		return this.availableFhull;
	}

	public int getAvailableAhull() {
		return this.availableAhull;
	}

	public int getAvailableChull() {
		return this.availableChull;
	}

	public int getAvailableCargo() {
		return this.availableCargo;
	}

	public int getAvailableBarracks() {
		return this.availableBarracks;
	}

	public int getMaxFhull()        { return fhull; }
	public int getMaxAhull()        { return ahull; }
	public int getMaxChull()        { return chull; }
	public int getOriginalArmor()   { return armor; }
	public int getAvailableArmor()  { return availableArmor; }

	// Total original hull boxes on SSD (cripple calculations).
	@Override
	public int fetchOriginalTotalBoxes() {
		return this.ahull + this.cargo + this.chull + this.fhull + this.barracks + this.armor;
	}

	// Total current hull boxes (cripple calculations).
	@Override
	public int fetchRemainingTotalBoxes() {
		return this.availableAhull + this.availableCargo + this.availableChull + this.availableFhull
				+ this.availableBarracks + this.availableArmor;
	}

	//// SETTERS (for client-side sync from server state) ////
	public void setAvailableFhull(int v) { availableFhull = Math.max(0, Math.min(v, fhull)); }
	public void setAvailableAhull(int v) { availableAhull = Math.max(0, Math.min(v, ahull)); }
	public void setAvailableChull(int v) { availableChull = Math.max(0, Math.min(v, chull)); }

	//// DAMAGE //////
	public boolean damageFhull() {
		if (availableFhull > 0) {
			availableFhull--;
			return true;
		}
		return damageChull();
	}

	public boolean damageAhull() {
		if (availableAhull > 0) {
			availableAhull--;
			return true;
		}
		return damageChull();
	}

	public boolean damageChull() {
		if (availableChull == 0) {
			return false;
		}

		availableChull--;
		return true;
	}

	public boolean damageCargo() {
		if (availableCargo == 0) {
			return false;
		}

		availableCargo--;
		return true;
	}

	public boolean damageBarracks() {
		if (availableBarracks == 0) {
			return false;
		}

		availableBarracks--;
		return true;
	}

	public boolean damageArmor() {
		if (availableArmor == 0) {
			return false;
		}

		availableArmor--;
		return true;
	}

	//// REPAIR ////
	public boolean repairFhull(int amount) {
		if (availableFhull + amount > fhull) {
			return false;
		}

		availableFhull += amount;
		return true;
	}

	public boolean repairAhull(int amount) {
		if (availableAhull + amount > ahull) {
			return false;
		}

		availableAhull += amount;
		return true;
	}

	public boolean repairChull(int amount) {
		if (availableChull + amount > chull) {
			return false;
		}

		availableChull += amount;
		return true;
	}

	public boolean repairCargo(int amount) {
		if (availableCargo + amount > cargo) {
			return false;
		}

		availableCargo += amount;
		return true;
	}

	public boolean repairBarracks(int amount) {
		if (availableBarracks + amount > barracks) {
			return false;
		}

		availableBarracks += amount;
		return true;
	}

	public boolean repairArmor(int amount) {
		if (availableArmor + amount > armor) {
			return false;
		}

		availableArmor += amount;
		return true;
	}

	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub

	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}

}
