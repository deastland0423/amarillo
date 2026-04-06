package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;
import com.sfb.systems.Probe;

public class ProbeLaunchers implements Systems {

	private Probe[] launcherArray = new Probe[] {};
	
	private Unit owningUnit;
	
	public ProbeLaunchers(Unit owner) {
		this.owningUnit = owner;
	}
	
	// Create probe boxes equal to the number
	// of probes specified in the argument.
	@Override
	public void init(Map<String, Object> values) {
		int numberOfProbes = values.get("probe") == null ? 0 : (Integer)values.get("probe");
		launcherArray = new Probe[numberOfProbes];
		for (int i=0; i < launcherArray.length; i++) {
			launcherArray[i] = new Probe();
		}
	}
	
	// return the number of non-destroyed probe boxes
	public int availableProbes() { 
		int counter = 0;
		for (Probe probe : launcherArray) {
			if (probe.isFunctional()) {
				counter++;
			}
		}
		
		return counter;
	}
	
	@Override
	public int fetchOriginalTotalBoxes() {
		return launcherArray.length;
	}
	
	@Override
	public int fetchRemainingTotalBoxes() {
		return availableProbes();
	}

	/**
	 * Returns one of a ship's probes.
	 * 
	 * @param probeNumber The number of the probe system (starting at 1)
	 * @return The probe indicated by probeNumber.
	 */
	public Probe get(int probeNumber) {
		return this.launcherArray[probeNumber];
	}
	
	/**
	 * Returns the index of an undamaged probe launcher, or -1 if none exists.
	 * @return The index of an undamaged probe, -1 otherwise.
	 */
	public int getUndamagedIndex() {
		for(int i=0; i < this.launcherArray.length; i++) {
			if (launcherArray[i].isFunctional()) {
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * Checks to see if there are any undamaged probes remaining.
	 * @return True if there is an undamaged probe, false otherwise.
	 */
	public boolean hasUndamagedProbe() {
		for (Probe probe : launcherArray) {
			if (probe.isFunctional()) {
				return true;
			}
		}
		
		return false;
		
	}
	/**
	 * Returns the first available, undamaged probe on the ship if there is one.
	 * @return Probe object that is undamaged. Null if none exist.
	 */
	public Probe getFirstUndamaged() {
		for (Probe probe : launcherArray) {
			if (probe.isFunctional()) {
				return probe;
			}
		}
		
		return null;
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
