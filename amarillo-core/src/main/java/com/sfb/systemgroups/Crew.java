package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

public class Crew implements Systems {

	private int crewUnits = 0;
	private int minimumCrew = 0;
	private int boardingParties = 0;
	private int deckCrews = 2; // Most ships have 2 deck crews for drone reloads or shuttle operations.

	private int availableCrewUnits = 0;
	private int availableBoardingParties = 0;
	private int availableDeckCrews = 0;

	private Unit owningUnit;

	public Crew(Unit owner) {
		this.owningUnit = owner;
	}

	public void init(Map<String, Object> values) {
		crewUnits = values.get("crew") == null ? 0 : (Integer) values.get("crew");
		boardingParties = values.get("boardingparties") == null ? 0 : (Integer) values.get("boardingparties");
		minimumCrew = values.get("minimumcrew") == null ? 0 : (Integer) values.get("minimumcrew");
		deckCrews = values.get("deckcrews") == null ? 2 : (Integer) values.get("deckcrews");
		availableCrewUnits = crewUnits;
		availableBoardingParties = boardingParties;
		availableDeckCrews = deckCrews;
	}

	/**
	 * Checks if the crew has dropped below the minimum and, therefore,
	 * the ship is running on a skeleton crew.
	 * 
	 * @return True if the ship is below minimum crew, false otherwise.
	 */
	public boolean isSkeleton() {
		return availableCrewUnits < minimumCrew;
	}

	public int getAvailableCrewUnits() {
		return availableCrewUnits;
	}

	public void setAvailableCrewUnits(int availableCrewUnits) {
		this.availableCrewUnits = availableCrewUnits;
	}

	public int getAvailableBoardingParties() {
		return availableBoardingParties;
	}

	public void setAvailableBoardingParties(int availableBoardingParties) {
		this.availableBoardingParties = availableBoardingParties;
	}

	public int getMinimumCrew() {
		return this.minimumCrew;
	}

	public int getDeckCrews() {
		return this.deckCrews;
	}

	public int getAvailableDeckCrews() {
		return this.availableDeckCrews;
	}

	public void setAvailableDeckCrews(int availableDeckCrews) {
		this.availableDeckCrews = availableDeckCrews;
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return 0;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return 0;
	}

	@Override
	public void cleanUp() {
		availableDeckCrews = deckCrews;
	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}

}
