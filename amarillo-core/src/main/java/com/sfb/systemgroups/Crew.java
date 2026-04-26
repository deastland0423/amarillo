package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.TroopCount;
import com.sfb.objects.Unit;

public class Crew implements Systems {

	/** Quality of the ship's crew for hit-and-run defense rolls (D7.73). */
	public enum CrewQuality { POOR, NORMAL, OUTSTANDING }

	private int crewUnits    = 0;
	private int minimumCrew  = 0;
	private int deckCrews    = 2; // Most ships have 2 deck crews for drone reloads or shuttle operations.

	private int availableCrewUnits = 0;
	private int capturedCrew       = 0; // enemy crew taken prisoner (D7.50)
	private int availableDeckCrews = 0;

	/** Friendly boarding parties available on this ship. */
	private final TroopCount friendlyTroops = new TroopCount();

	private CrewQuality crewQuality = CrewQuality.NORMAL;

	private Unit owningUnit;

	public Crew(Unit owner) {
		this.owningUnit = owner;
	}

	public void init(Map<String, Object> values) {
		crewUnits   = values.get("crew")        == null ? 0 : (Integer) values.get("crew");
		minimumCrew = values.get("minimumcrew") == null ? 0 : (Integer) values.get("minimumcrew");
		deckCrews   = values.get("deckcrews")   == null ? 2 : (Integer) values.get("deckcrews");

		int normalBPs   = values.get("boardingparties") == null ? 0 : (Integer) values.get("boardingparties");
		int commandoBPs = values.get("commandos")       == null ? 0 : (Integer) values.get("commandos");
		friendlyTroops.normal    = normalBPs;
		friendlyTroops.commandos = commandoBPs;

		String quality = (String) values.get("crewquality");
		if ("outstanding".equalsIgnoreCase(quality))   crewQuality = CrewQuality.OUTSTANDING;
		else if ("poor".equalsIgnoreCase(quality))     crewQuality = CrewQuality.POOR;
		else                                           crewQuality = CrewQuality.NORMAL;

		availableCrewUnits = crewUnits;
		availableDeckCrews = deckCrews;
	}

	// --- Friendly troops ---

	public TroopCount getFriendlyTroops() {
		return friendlyTroops;
	}

	/** Convenience: total available boarding parties (normal + commandos). */
	public int getAvailableBoardingParties() {
		return friendlyTroops.total();
	}

	/**
	 * Backward-compatible setter used by existing H&R code that adjusts the
	 * total by subtracting losses. Removes from normal first, then commandos.
	 */
	public void setAvailableBoardingParties(int total) {
		int current = friendlyTroops.total();
		int loss = current - total;
		if (loss > 0) friendlyTroops.removeCasualties(loss);
		else if (loss < 0) friendlyTroops.normal += (-loss); // gaining parties (e.g. reinforcements)
	}

	// --- Crew quality ---

	public CrewQuality getCrewQuality() {
		return crewQuality;
	}

	public void setCrewQuality(CrewQuality crewQuality) {
		this.crewQuality = crewQuality;
	}

	// --- Crew units ---

	public boolean isSkeleton() {
		return availableCrewUnits < minimumCrew;
	}

	public int getAvailableCrewUnits() {
		return availableCrewUnits;
	}

	public void setAvailableCrewUnits(int availableCrewUnits) {
		this.availableCrewUnits = availableCrewUnits;
	}

	public int getMinimumCrew() {
		return this.minimumCrew;
	}

	public int getCapturedCrew() { return capturedCrew; }
	public void setCapturedCrew(int n) { this.capturedCrew = n; }
	public void addCapturedCrew(int n) { this.capturedCrew += n; }

	// --- Deck crews ---

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
