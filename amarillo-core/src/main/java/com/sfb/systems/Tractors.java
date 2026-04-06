package com.sfb.systems;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Unit;
import com.sfb.systemgroups.Systems;

public class Tractors implements Systems {

	int tractor;									// The number of tractor beams on the undamaged ship.
	int availableTractor;							// The number of undamaged tractor beams on the ship.
	
	int totalTractorEnergy;							// The total energy allocated to tractors for the turn.
	int remainingTractorEnergy;						// Unspent tractor energy
	
	int negativeTractorEnergy;						// Energy applied to fight off tractor attempts.
	
	int tractorsUsed;								// The number of tractors that are currently in use.
	Unit owningUnit;								// The unit on which the tractors are installed.
	
	List<Unit> tractoredUnits = new ArrayList<>();	// Any units currently being tractored.
	
	public Tractors(Unit owningUnit) {
		this.owningUnit = owningUnit;
	}
	
	public void init(Map<String, Object> values) {
		availableTractor = tractor = values.get("tractor") == null ? 0 : (Integer)values.get("tractor");
		totalTractorEnergy = remainingTractorEnergy = 0;
	}
	
	public int getTotalTractorEnergy() {
		return this.totalTractorEnergy;
	}
	
	public int getRemainingTractorEnergy() {
		return this.remainingTractorEnergy;
	}
	
	public int getNegativeTractorEnergy() {
		return this.negativeTractorEnergy;
	}
	
	public void addNegativeTractorEnergy(int energy) {
		negativeTractorEnergy += energy;
	}
	
	public void tractorUnit(int energy, Unit target) {

		//TODO: Should I calculate range here and do the range penalty for energy?
		
		// If we have enough tractor energy and a free tractor beam, attempt to tractor the target.
		if (energy <= remainingTractorEnergy && tractorsUsed < availableTractor) {

			// If we succeed in applying the tractor, add it as a tractored object.
			if (target.applyTractor(energy, owningUnit)) {
				tractoredUnits.add(target);
				tractorsUsed++;
			}
			
			// Even on a failure, we use up the energy.
			remainingTractorEnergy -= energy;
		} else {
			//TODO: Handle bad energy value or lack of free tractor beam
		}
	}
	
	public void releaseTractor(Unit target) {
		target.releaseTractor();
		tractoredUnits.remove(target);
		tractorsUsed--;
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return tractor;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return availableTractor;
	}

	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Unit fetchOwningUnit() {
		return owningUnit;
	}
	
	/**
	 * Destroy a tractor box.
	 * 
	 * @return True if there are tractors remaining, false otherwise.
	 */
	public boolean damage() {
		// If there are not tractors left, we can't do damage.
		if (availableTractor == 0) {
			return false;
		// Otherwise, destroy a tractor box.
		} else {
			// If all tractors are occupied, we must drop one tractor
			if (tractorsUsed == availableTractor) {

				//TODO: Figure out some way to decide which unit to un-tractor
				
				// For now, just drop the first one in the list.
				releaseTractor(tractoredUnits.get(0));
			}

			availableTractor--;
			return true;
		}
	}
	
	/**
	 * Repair a single tractor box.
	 * 
	 * @return True if there is a damaged tractor box, false otherwise.
	 */
	public boolean repair() {
		return repair(1);
	}
	
	/**
	 * Repair a number of tractor boxes specified.
	 * 
	 * @param value The number of boxes to repair.
	 * 
	 * @return True if there are damage boxes, false otherwise.
	 */
	public boolean repair(int value) {
		if (availableTractor + value > tractor) {
			return false;
		}
		
		availableTractor += value;
		return true;

	}
}
