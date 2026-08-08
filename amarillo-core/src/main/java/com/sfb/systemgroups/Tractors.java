package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Tractorable;
import com.sfb.objects.Unit;

/**
 * The ship's tractor beam group. Each beam is an individual
 * {@link TractorBeam} carrying its own state — functional, used-this-turn
 * (G7.13), held unit — so damage and hit-and-run raids can name a specific
 * beam (D7.835). Energy stays pooled at group level (G7.15), as does
 * accumulated negative tractor (G7.35).
 */
public class Tractors implements Systems {

	int totalTractorEnergy; // The total energy allocated to tractors for the turn.
	int remainingTractorEnergy; // Unspent tractor energy

	int negativeTractorAccumulated; // Total negative-tractor energy spent this turn (G7.35); persists across
																	// impulses.

	Unit owningUnit; // The unit on which the tractors are installed.

	private final List<TractorBeam> beams = new ArrayList<>();

	public Tractors(Unit owningUnit) {
		this.owningUnit = owningUnit;
	}

	public void init(Map<String, Object> values) {
		beams.clear();
		int count = values.get("tractor") == null ? 0 : (Integer) values.get("tractor");
		for (int i = 1; i <= count; i++)
			beams.add(new TractorBeam(i));
		totalTractorEnergy = remainingTractorEnergy = 0;
	}

	/** The individual beams, in SSD order. */
	public List<TractorBeam> getBeams() {
		return beams;
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

	// Establish the physical tractor link after auction resolution (no energy
	// deduction). Uses the first beam that is functional, unused this turn
	// (G7.13), and not already holding.
	public boolean linkUnit(Tractorable target) {
		TractorBeam beam = firstFreeBeam();
		if (beam == null)
			return false;
		target.applyTractor(owningUnit);
		beam.hold(target);
		return true;
	}

	private TractorBeam firstFreeBeam() {
		for (TractorBeam b : beams)
			if (b.isAvailableForNewLink())
				return b;
		return null;
	}

	/**
	 * Expend one beam's per-turn use without forming a link — a tractor attempt
	 * that failed its D6.372 EW roll still consumes the beam for the turn (the
	 * standard rate-of-operations lockout).
	 */
	public boolean expendBeamUse() {
		TractorBeam beam = firstFreeBeam();
		if (beam == null)
			return false;
		beam.markUsed();
		return true;
	}

	/** Beams that can still initiate a NEW link this turn (G7.13). */
	public int getBeamsAvailableThisTurn() {
		int count = 0;
		for (TractorBeam b : beams)
			if (b.isAvailableForNewLink())
				count++;
		return count;
	}

	public void initForTurn(int energy) {
		totalTractorEnergy = remainingTractorEnergy = energy;
		// G7.13 usage resets each turn; beams still holding persistent links
		// (G7.42) remain in use.
		for (TractorBeam b : beams)
			b.resetForTurn();
	}

	/**
	 * Held {@link Unit}s only, in beam order — the classic towing/rotation/
	 * death-drag callers care about ships, seekers and shuttles, never inert
	 * objectives. Use {@link #getTractored()} for everything the beams hold.
	 * (Fresh list — mutate via releaseTractor.)
	 */
	public List<Unit> getTractoredUnits() {
		List<Unit> held = new ArrayList<>();
		for (TractorBeam b : beams)
			if (b.getHeldUnit() instanceof Unit)
				held.add((Unit) b.getHeldUnit());
		return held;
	}

	/** Everything the beams hold, including objectives (J1.621 recovery). */
	public List<Tractorable> getTractored() {
		List<Tractorable> held = new ArrayList<>();
		for (TractorBeam b : beams)
			if (b.getHeldUnit() != null)
				held.add(b.getHeldUnit());
		return held;
	}

	public int getTractors() {
		return beams.size();
	}

	public int getAvailableTractors() {
		int count = 0;
		for (TractorBeam b : beams)
			if (b.isFunctional())
				count++;
		return count;
	}

	// Legacy direct-link (used only for non-contested establishes; prefer linkUnit
	// after auction).
	public void tractorUnit(int energy, Tractorable target) {
		TractorBeam beam = firstFreeBeam();
		if (energy <= remainingTractorEnergy && beam != null) {
			target.applyTractor(owningUnit);
			beam.hold(target);
			remainingTractorEnergy -= energy;
		}
	}

	public void releaseTractor(Tractorable target) {
		for (TractorBeam b : beams) {
			if (b.getHeldUnit() == target) {
				target.releaseTractor();
				b.dropLink(); // the beam stays used this turn (G7.13)
				return;
			}
		}
	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return beams.size();
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return getAvailableTractors();
	}

	@Override
	public void cleanUp() {
		// Tractor links PERSIST across the turn boundary (G7.42) — at the start of
		// the next turn the holder must pay maintenance or the link is released
		// (TractorResolver.maintainLinksAtTurnStart). Only per-turn energy resets here.
		totalTractorEnergy = remainingTractorEnergy = 0;
		negativeTractorAccumulated = 0;
		for (TractorBeam b : beams)
			b.resetForTurn();
	}

	@Override
	public Unit fetchOwningUnit() {
		return owningUnit;
	}

	/**
	 * True when destroying a tractor box is a genuine player decision: two or
	 * more functional beams and every one of them is holding a unit, so the
	 * owner must pick which link breaks. When any idle beam exists, destroying
	 * it is strictly dominant and {@link #damageAutoPick()} resolves without a
	 * choice.
	 */
	public boolean needsDamageChoice() {
		int functional = 0;
		for (TractorBeam b : beams) {
			if (!b.isFunctional())
				continue;
			if (b.getHeldUnit() == null)
				return false; // an idle beam exists — auto-pick is dominant
			functional++;
		}
		return functional >= 2;
	}

	/**
	 * Destroy one tractor box without a player choice, preferring beams whose
	 * loss costs least: a used-idle beam first (already spent for the turn per
	 * G7.13), then an unused-idle beam, then the sole holding beam (breaking
	 * its link).
	 *
	 * @return log label, or null if no functional beams remain.
	 */
	public String damageAutoPick() {
		TractorBeam pick = null;
		for (TractorBeam b : beams) { // used-idle first
			if (b.isFunctional() && b.getHeldUnit() == null && b.isUsedThisTurn()) {
				pick = b;
				break;
			}
		}
		if (pick == null) { // then unused-idle
			for (TractorBeam b : beams) {
				if (b.isFunctional() && b.getHeldUnit() == null) {
					pick = b;
					break;
				}
			}
		}
		if (pick == null) { // last resort: a holding beam — its link breaks
			for (TractorBeam b : beams) {
				if (b.isFunctional()) {
					pick = b;
					break;
				}
			}
		}
		if (pick == null)
			return null;
		return destroyBeam(pick.getNumber());
	}

	/**
	 * Destroy the numbered beam, breaking its link if it holds a unit.
	 *
	 * @return log label, or null if the beam does not exist or is already
	 *         destroyed.
	 */
	public String destroyBeam(int number) {
		for (TractorBeam b : beams) {
			if (b.getNumber() != number || !b.isFunctional())
				continue;
			Tractorable held = b.getHeldUnit();
			if (held != null) {
				held.releaseTractor();
				b.dropLink();
			}
			b.setFunctional(false);
			return "tractor HIT (Tractor #" + number
					+ (held != null ? " — link to " + held.getName() + " broken" : "") + ")";
		}
		return null;
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
		if (getAvailableTractors() + value > beams.size()) {
			return false;
		}
		int toRepair = value;
		for (TractorBeam b : beams) {
			if (toRepair == 0)
				break;
			if (!b.isFunctional()) {
				b.setFunctional(true);
				toRepair--;
			}
		}
		return true;
	}
}
