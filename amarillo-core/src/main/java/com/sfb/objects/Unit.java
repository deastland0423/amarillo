package com.sfb.objects;

import java.util.Map;

import com.sfb.Player;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.TurnModeUtil;

// Units are any thing on the map that is more than
// a simple dumb object. It can be a ship, a missile,
// a monster...anything that does more than simply exist.

// In addition to a location, a unit has a facing and a speed.
public class Unit extends Marker implements Tractorable {

	// Facing is a value representing
	// a direction that the thing is facing, relative
	// to the hex map. (1 is "due north" and 4 is "due south).
	//
	// 1
	// 2 5
	// X
	// 17 9
	// 3
	//
	protected int facing = 0; // Direction the unit is facing (1 through 6)
	protected int speed = 0; // Speed the unit is moving (0 through 32)
	protected int sizeClass = 0; // Size class of the unit (0 through 6...I think?)
	protected int sideslipCount = 100; // Track number of moves since last sideslip.
	protected int turnCount = 100; // Track number of moves since last turn.
	/** Absolute direction (1–24) this unit last moved into its current hex; 0 if not yet moved this impulse. */
	protected int entryDirection = 0;
	protected boolean tractored = false; // True if the unit is tractored by another unit.
	protected Unit tractoringUnit = null; // The unit that is applying a tractor to this unit, if any.

	protected Player owner = null; // controlling player

	protected TurnMode turnMode;

	public Unit() {

	}

	/**
	 * Initialize a basic unit by setting its turn mode.
	 * 
	 * @param values
	 */
	// TODO: Should I do an "init" or just have these values explicitly set on
	// instantiation?
	public void init(Map<String, Object> values) {
		name = values.get("name") == null ? null : (String) values.get("name");
		turnMode = values.get("turnmode") == null ? null : (TurnMode) values.get("turnmode");
		sizeClass = values.get("sizeclass") == null ? 3 : (Integer) values.get("sizeclass");
	}

	/**
	 * Perform the various functions that are needed at the start of each turn,
	 * such as setting a speed.
	 */
	public void startTurn() {

	}


	public int getFacing() {
		return facing;
	}

	public void setFacing(int facing) {
		this.facing = facing;
	}

	/** Absolute direction (1–24) this unit entered its current hex; 0 = not moved this impulse. */
	public int getEntryDirection() { return entryDirection; }
	public void setEntryDirection(int dir) { this.entryDirection = dir; }
	public void clearEntryDirection() { this.entryDirection = 0; }

	public int getSpeed() {
		return speed;
	}

	public void setSpeed(int newSpeed) {
		this.speed = newSpeed;
	}

	// Given the true map-based bearing of a target
	// from this thing, adjust the bearing so that
	// it instead gives the bearing relative to the
	// front of the Thing.
	// Given the true (map-oriented) bearing and the facing of the source
	// Give the relative bearing, with the front of the source as the "1"
	// bearing.
	public int getRelativeBearing(int trueBearing, int facing) {
		if (facing == 1) {
			return trueBearing;
		}

		int adjustDown = facing - 1;
		int adjustUp = 24 - adjustDown;

		if (trueBearing >= facing) {
			return trueBearing - adjustDown;
		} else {
			return trueBearing + adjustUp;
		}

	}

	public int getSizeClass() {
		return sizeClass;
	}

	public void setSizeClass(int sizeClass) {
		this.sizeClass = sizeClass;
	}

	/**
	 * Return the turn mode for the unit at its current speed.
	 * 
	 * @return The number of hexes the unit must move before it can turn.
	 */
	public TurnMode getTurnMode() {
		return this.turnMode;
	}

	public void setTurnMode(TurnMode mode) {
		turnMode = mode;
	}

	/**
	 * Get the number of hexes the unit must move before it can turn.
	 * 
	 * @return The number of hexes that must be moved before a turn.
	 */
	public int getTurnHexes() {
		return TurnModeUtil.getTurnMode(this.turnMode, this.speed) + emTurnModePenalty();
	}

	/**
	 * C10.55: Erratic Maneuvers lengthen the Turn Mode by one hex (four becomes five). The
	 * Turn CATEGORY is unchanged - this is a hex, not a letter - and nimble units are
	 * exempt. Defined once here because Ship overrides getTurnHexes() to account for a
	 * skeleton crew, and an override that forgot this would silently drop the penalty.
	 */
	protected int emTurnModePenalty() {
		return (isUsingEm() && !isNimbleUnit()) ? 1 : 0;
	}

	// ------------------------------------------------------------------
	// Erratic Maneuvers (C10.0)
	// ------------------------------------------------------------------

	/** True once EM is actually in force - not merely announced (C10.311). */
	private boolean usingEm = false;
	/** Absolute impulse an EM start/stop was announced on, or -1 for none pending. */
	private int emAnnouncedImpulse = -1;
	/** Whether the pending announcement starts EM or stops it. */
	private boolean emAnnouncementStarts = false;
	/** C10.31: a unit may only BEGIN using EM once per turn. */
	private boolean emStartedThisTurn = false;

	public boolean isUsingEm() { return usingEm; }

	public boolean hasStartedEmThisTurn() { return emStartedThisTurn; }

	public boolean hasPendingEmAnnouncement(int absoluteImpulse) {
		return emAnnouncedImpulse == absoluteImpulse;
	}

	/**
	 * C10.311 / C10.32: announce that EM will start or stop. The announcement is made in
	 * the Final Movement Actions Stage (6A4) and does NOT take effect there - it comes into
	 * force in the Post-Combat Segment at the end of that same impulse (Stage 6E). That
	 * delay is the whole reason a ship can be shot at during the impulse it announced on
	 * without yet having the benefit.
	 */
	public void announceEm(boolean starting, int absoluteImpulse) {
		emAnnouncementStarts = starting;
		emAnnouncedImpulse = absoluteImpulse;
	}

	/**
	 * Stage 6E: bring an announcement made on this impulse into force. Returns true only if
	 * something actually changed, so the caller can log it.
	 */
	public boolean applyEmAnnouncement(int absoluteImpulse) {
		if (emAnnouncedImpulse != absoluteImpulse)
			return false;
		emAnnouncedImpulse = -1;
		if (usingEm == emAnnouncementStarts)
			return false;
		usingEm = emAnnouncementStarts;
		if (usingEm)
			emStartedThisTurn = true;
		return true;
	}

	/** Turn boundary: the once-per-turn start becomes available again (C10.31). */
	public void resetEmForNewTurn() {
		emStartedThisTurn = false;
		emAnnouncedImpulse = -1;
	}

	/** Drop EM outright - used when the energy for it was not paid again (C10.313). */
	public void dropEm() {
		usingEm = false;
		emAnnouncedImpulse = -1;
	}

	/**
	 * C11.1 / C11.23: whether this unit enjoys the nimble exemptions. A ship answers from
	 * its own data; every shuttlecraft and fighter is nimble; seeking weapons never are.
	 */
	public boolean isNimbleUnit() {
		return false;
	}

	public int getTurnCount() {
		return turnCount;
	}

	/// PLAYER ///
	public Player getOwner() {
		return this.owner;
	}

	public void setOwner(Player player) {
		this.owner = player;
	}

	// / MOVEMENT ///

	/**
	 * Sideslip the unit to the left. This is only possible if the unit
	 * has moved at least one hex since the last sideslip.
	 * The unit will move to the adjacent hex in (relative) direction 21 without
	 * changing
	 * its facing.
	 * 
	 * @return True if the sideslip was possible, false otherwise.
	 */
	public boolean sideslipLeft() {
		if (sideslipCount == 0) {
			return false;
		}

		// Calculate what hex is adjacent in the '21' relative bearing (forward left).
		// Move the ship to that hex.
		int relativeBearing = 21;
		entryDirection = MapUtils.getTrueBearing(relativeBearing, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection));

		// A sideslip is a hex of movement: it counts toward the turn mode
		// (advances "hexes until you can turn") like a forward move, but resets
		// the between-sideslips counter.
		turnCount++;
		sideslipCount = 0;
		return true;
	}

	/**
	 * Sideslip the unit to the right. This is only possible if the unit
	 * has moved at least one hex since the last sideslip.
	 * The unit will move to the adjacent hex in (relative) direction 5 without
	 * changing
	 * its facing.
	 * 
	 * @return True if the sideslip was possible, false otherwise.
	 */
	public boolean sideslipRight() {
		if (sideslipCount == 0) {
			return false;
		}
		// Calculate what hex is adjacent in the '5' relative bearing (forward right).
		// Move the ship to that hex.
		int relativeBearing = 5;
		entryDirection = MapUtils.getTrueBearing(relativeBearing, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection));

		// A sideslip is a hex of movement: it counts toward the turn mode
		// (advances "hexes until you can turn") like a forward move, but resets
		// the between-sideslips counter.
		turnCount++;
		sideslipCount = 0;
		return true;
	}

	/**
	 * Turn the unit to the left and move one hex forward. This will change the
	 * facing of the unit to (relative) direction 21 and then move it into the
	 * adjacent
	 * hex in (relative) direction 1.
	 * This is only possible if the unit has fulfilled its turn mode.
	 * 
	 * @return True if the turn was possible, false otherwise.
	 */
	public boolean turnLeft() {
		if (turnCount < getTurnHexes()) {
			return false;
		}

		// Change the facing of the ship one to the left.
		setFacing(MapUtils.getTrueBearing(21, getFacing()));

		// Then go forward one.
		goForward();
		turnCount = 1;
		return true;
	}

	/**
	 * Turn the unit to the right and move one hex forward. This will change the
	 * facing of the unit to (relative) direction 5 and then move it into the
	 * adjacent
	 * hex in (relative) direction 1.
	 * This is only possible if the unit has fulfilled its turn mode.
	 * 
	 * @return True if the turn was possible, false otherwise.
	 */
	public boolean turnRight() {
		if (turnCount < getTurnHexes()) {
			return false;
		}

		// Change the facing of the ship one to the right.
		setFacing(MapUtils.getTrueBearing(5, getFacing()));

		// Then go forward one.
		goForward();
		turnCount = 1;
		return true;
	}

	/**
	 * Move the unit a single hex forward, placing it in the adjacent hex
	 * in (relative) direction 1 without changing facing.
	 * 
	 * @return True if this is a legal move.
	 */
	public boolean goForward() {
		sideslipCount++;
		turnCount++;

		entryDirection = MapUtils.getTrueBearing(1, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection));

		return true;
	}

	public boolean goForward(int maxCols, int maxRows) {
		sideslipCount++;
		turnCount++;

		entryDirection = MapUtils.getTrueBearing(1, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection, maxCols, maxRows));

		return true;
	}

	// Tractor drag: move this unit one hex in the given absolute direction (G7.36).
	// Counts as a forward move for sideslip/turn-mode purposes.
	public void dragForwardInDirection(int absoluteDirection, int maxCols, int maxRows) {
		sideslipCount++;
		turnCount++;
		entryDirection = absoluteDirection;
		setLocation(MapUtils.getAdjacentHex(getLocation(), absoluteDirection, maxCols, maxRows));
	}

	// Tractor drag: move this unit one hex sideways in the given absolute direction (G7.36 sideslip).
	public void dragSideslipInDirection(int absoluteDirection, int maxCols, int maxRows) {
		sideslipCount = 0;
		entryDirection = absoluteDirection;
		setLocation(MapUtils.getAdjacentHex(getLocation(), absoluteDirection, maxCols, maxRows));
	}

	/**
	 * Move the unit a single hex backward.
	 * 
	 * @return True if this is a legal move.
	 */
	public boolean goBackward() {
		sideslipCount++;
		turnCount++;

		entryDirection = MapUtils.getTrueBearing(13, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection));

		return true;
	}

	public boolean goBackward(int maxCols, int maxRows) {
		sideslipCount++;
		turnCount++;

		entryDirection = MapUtils.getTrueBearing(13, getFacing());
		setLocation(MapUtils.getAdjacentHex(getLocation(), entryDirection, maxCols, maxRows));

		return true;
	}

	/**
	 * Change the facing of the unit without moving it.
	 * 
	 * @param absoluteFacing
	 *                       The new facing of the unit with respect to the map.
	 * @return True if the maneuver is possible, false otherwise.
	 */
	public boolean performHet(int absoluteFacing) {

		setFacing(absoluteFacing);
		turnCount = 0;
		sideslipCount = 0;

		return true;
	}

	/**
	 * Check if the unit is being held in a tractor.
	 * 
	 * @return True if the unit is tractored, false otherwise.
	 */
	public boolean isTractored() {
		return tractored;
	}

	protected void setTractored(boolean value) {
		this.tractored = value;
	}

	public Unit getTractoringUnit() {
		return this.tractoringUnit;
	}

	protected void setTractoringUnit(Unit unit) {
		this.tractoringUnit = unit;
	}

	/**
	 * Unit is tractored by another unit.
	 * 
	 * @param energy The tractor energy applied to this unit.
	 * 
	 * @return True if the tractor is successful, false otherwise.
	 */
	public void applyTractor(Unit tractoringUnit) {
		// Base: no-op (override in Ship)
	}

	/**
	 * Release the unit from whatever tractor beam is holding it.
	 */
	public void releaseTractor() {
		this.tractoringUnit = null;
		this.tractored = false;
	}
}
