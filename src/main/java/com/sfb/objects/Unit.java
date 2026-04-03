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
public class Unit extends Marker {

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
		return TurnModeUtil.getTurnMode(this.turnMode, this.speed);
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
		setLocation(MapUtils.getAdjacentHex(getLocation(), MapUtils.getTrueBearing(relativeBearing, getFacing())));

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
		setLocation(MapUtils.getAdjacentHex(getLocation(), MapUtils.getTrueBearing(relativeBearing, getFacing())));

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

		// Find the hex directly in front of the ship and move the ship to that hex.
		setLocation(MapUtils.getAdjacentHex(getLocation(), MapUtils.getTrueBearing(1, getFacing())));

		return true;
	}

	/**
	 * Move the unit a single hex backward.
	 * 
	 * @return True if this is a legal move.
	 */
	public boolean goBackward() {
		sideslipCount++;
		turnCount++;

		// Find the hex directly behind of the ship and move the ship to that hex.
		setLocation(MapUtils.getAdjacentHex(getLocation(), MapUtils.getTrueBearing(13, getFacing())));

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
	public boolean applyTractor(int energy, Unit tractoringUnit) {

		return true;
	}

	/**
	 * Release the unit from whatever tractor beam is holding it.
	 */
	public void releaseTractor() {
		this.tractoringUnit = null;
		this.tractored = false;
	}
}
