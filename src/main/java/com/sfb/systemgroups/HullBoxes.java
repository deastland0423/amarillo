package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.ShipSpec;
import com.sfb.objects.Unit;

public class HullBoxes implements Systems {

	private int fhull;
	private int ahull;
	private int chull;
	private int cargo;

	private int availableFhull;
	private int availableAhull;
	private int availableChull;
	private int availableCargo;

	private Unit owningUnit = null;

	public HullBoxes() {
	}

	public HullBoxes(Unit owner) {
		this.owningUnit = owner;
	}

	// Use a <String, Integer> map to set the initial values.
	// Acceptable keys are: fhull, ahull, chull, cargo
	@Override
	public void init(Map<String, Object> values) {
		// If map has matching value, get it. Otherwise set to 0.
		availableFhull = fhull = values.get("fhull") == null ? 0 : (Integer) values.get("fhull");
		availableAhull = ahull = values.get("ahull") == null ? 0 : (Integer) values.get("ahull");
		availableChull = chull = values.get("chull") == null ? 0 : (Integer) values.get("chull");
		availableCargo = cargo = values.get("cargo") == null ? 0 : (Integer) values.get("cargo");

	}

	// Replace the Map-based init with a type-safe Spec-based init
	public void initFromSpec(ShipSpec.HullSpec spec) {
		this.fhull = spec.fhull;
		this.ahull = spec.ahull;
		this.chull = spec.chull;
		this.cargo = spec.cargo;

		// Reset available counts to max
		this.availableFhull = this.fhull;
		this.availableAhull = this.ahull;
		this.availableChull = this.chull;
		this.availableCargo = this.cargo;
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

	// Total original hull boxes on SSD (cripple calculations).
	@Override
	public int fetchOriginalTotalBoxes() {
		return this.ahull + this.cargo + this.chull + this.fhull;
	}

	// Total current hull boxes (cripple calculations).
	@Override
	public int fetchRemainingTotalBoxes() {
		return this.availableAhull + this.availableCargo + this.availableChull + this.availableFhull;
	}

	//// DAMAGE //////
	public boolean damageFhull() {
		if (availableFhull == 0) {
			return false;
		}

		availableFhull--;
		return true;
	}

	public boolean damageAhull() {
		if (availableAhull == 0) {
			return false;
		}

		availableAhull--;
		return true;
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

	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub

	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}

}
