package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Unit;
import com.sfb.systemgroups.Systems;

public class Tractors implements Systems {

	int tractors; // The number of tractor beams on the undamaged ship.
	int availableTractors; // The number of undamaged tractor beams on the ship.

	int totalTractorEnergy; // The total energy allocated to tractors for the turn.
	int remainingTractorEnergy; // Unspent tractor energy

	int negativeTractorAccumulated; // Total negative-tractor energy spent this turn (G7.35); persists across impulses.

	int tractorsUsed; // The number of tractors that are currently in use.
	int beamsUsedThisTurn; // G7.13: each beam may be used only once per turn — releasing
	                       // a link does NOT free the beam for reuse until the next turn.
	Unit owningUnit; // The unit on which the tractors are installed.

	List<Unit> tractoredUnits = new ArrayList<>(); // Any units currently being tractored.

	public Tractors(Unit owningUnit) {
		this.owningUnit = owningUnit;
	}

	public void init(Map<String, Object> values) {
		availableTractors = tractors = values.get("tractor") == null ? 0 : (Integer) values.get("tractor");
		totalTractorEnergy = remainingTractorEnergy = 0;
	}

	public int getTotalTractorEnergy() {
		return this.totalTractorEnergy;
	}

	public int getRemainingTractorEnergy() {
		return this.remainingTractorEnergy;
	}

	public int getNegativeTractorAccumulated() {
		return this.negativeTractorAccumulated;
	}

	public void addNegativeTractorAccumulated(int energy) {
		negativeTractorAccumulated += energy;
	}

	// Deduct from the pool; returns how much still needs to come from battery.
	public int spendEnergy(int amount) {
		int fromPool = Math.min(amount, remainingTractorEnergy);
		remainingTractorEnergy -= fromPool;
		return amount - fromPool;
	}

	// Establish the physical tractor link after auction resolution (no energy deduction).
	public boolean linkUnit(Unit target) {
		if (tractorsUsed >= availableTractors) return false;
		if (beamsUsedThisTurn >= availableTractors) return false; // G7.13
		target.applyTractor(owningUnit);
		tractoredUnits.add(target);
		tractorsUsed++;
		beamsUsedThisTurn++;
		return true;
	}

	/** Beams that can still initiate a NEW link this turn (G7.13). */
	public int getBeamsAvailableThisTurn() {
		return availableTractors - Math.max(tractorsUsed, beamsUsedThisTurn);
	}

	public void initForTurn(int energy) {
		totalTractorEnergy = remainingTractorEnergy = energy;
		// G7.13 usage resets each turn; beams still holding persistent links
		// (G7.42) remain in use.
		beamsUsedThisTurn = tractorsUsed;
	}

	public List<Unit> getTractoredUnits() {
		return tractoredUnits;
	}

	public int getTractors() {
		return tractors;
	}

	public int getAvailableTractors() {
		return availableTractors;
	}

	// Legacy direct-link (used only for non-contested establishes; prefer linkUnit after auction).
	public void tractorUnit(int energy, Unit target) {
		if (energy <= remainingTractorEnergy && tractorsUsed < availableTractors) {
			target.applyTractor(owningUnit);
			tractoredUnits.add(target);
			tractorsUsed++;
			remainingTractorEnergy -= energy;
		}
	}

	public void releaseTractor(Unit target) {
		target.releaseTractor();
		tractoredUnits.remove(target);
		tractorsUsed--;
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return tractors;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return availableTractors;
	}

	@Override
	public void cleanUp() {
		// Tractor links PERSIST across the turn boundary (G7.42) — at the start of
		// the next turn the holder must pay maintenance or the link is released
		// (TractorResolver.maintainLinksAtTurnStart). Only per-turn energy resets here.
		totalTractorEnergy = remainingTractorEnergy = 0;
		negativeTractorAccumulated = 0;
		beamsUsedThisTurn = tractorsUsed;
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
		if (availableTractors == 0) {
			return false;
			// Otherwise, destroy a tractor box.
		} else {
			// If all tractors are occupied, we must drop one tractor
			if (tractorsUsed == availableTractors) {

				// TODO: Figure out some way to decide which unit to un-tractor

				// For now, just drop the first one in the list.
				releaseTractor(tractoredUnits.get(0));
			}

			availableTractors--;
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
		if (availableTractors + value > tractors) {
			return false;
		}

		availableTractors += value;
		return true;

	}
}
