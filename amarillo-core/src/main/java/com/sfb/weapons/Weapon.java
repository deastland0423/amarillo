package com.sfb.weapons;

import com.sfb.objects.Unit;
import com.sfb.utilities.ArcUtils;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parent class for all weapons. Contains common functionality shared by weapons
 * of all types.
 * Class is abstract, as you will never instantiate a "Weapon" object; only a
 * Phaser, Disruptor, etc.
 * 
 * @author Daniel Eastland
 *
 */
public abstract class Weapon {

	private String type; // The type of weapon (Phaser1, Disruptor30, Photon, ESG, etc.)
	private String designator; // The unique designator for the weapon (A, B, C...1, 2, 3...etc.)'
	private String dacHitLocaiton; // What DAC 'hit' destroys this weapon ('phaser', 'drone', etc.)
	private int arcs = ArcUtils.FULL; // Bitmask of the 24 directions (1-24) into which the weapon can fire.
	private String arcLabel = "FULL"; // Human-readable arc label, e.g. "FA", "FX + 13", "LF + L"
	private boolean functional = true; // True if the weapon is undamaged, false otherwise.
	private int lastImpulseFired = -9; // The last impulse on which this weapon was fired.
	private int lastTurnFired = -1; // The last turn on which this weapon was fired. -1 = never fired. (used by
																	// Fusion)
	private int maxShotsPerTurn = 1; // How many times this weapon may fire per turn (default 1).
	private int minImpulseGap = 8; // Minimum global impulses between shots (default 8).
	private int shotsThisTurn = 0; // Shots fired so far this turn; reset by cleanUp().
	private int shotsThisImpulse = 0; // Shots on lastImpulseFired; see getShotsThisImpulse.
	private int lastRoll = 0; // Die roll from most recent fire(); 0 = no roll (plasma, etc.)
	private int ecmShift = 0; // Net ECM shift applied to this weapon's next fire() call; set by
														// Game.fireWeapons()

	private int maxRange; // The maximum distance that this weapon can do damage.
	private int minRange; // The range below which this weapon can not fire.

	private Unit owningShip; // The unit on which this weapon is mounted.

	/**
	 * Determine what value on the DAC ('torp', 'drone', etc.) will damage this
	 * weapon.
	 * 
	 * @return The DAC string that affects this weapon.
	 */
	public String getDacHitLocaiton() {
		return dacHitLocaiton;
	}

	/**
	 * Specifies which weapon type on the Damage Allocation Chart
	 * will destroy this weapon.
	 * 
	 * @param dacHitLocaiton A string representing the DAC weapon type.
	 */
	public void setDacHitLocaiton(String dacHitLocaiton) {
		this.dacHitLocaiton = dacHitLocaiton;
	}

	/**
	 * Returns the arc bitmask for this weapon.
	 */
	public int getArcs() {
		return arcs;
	}

	/**
	 * Set the arc bitmask for this weapon (use ArcUtils constants or
	 * ArcUtils.mask()).
	 */
	public void setArcs(int arcMask) {
		this.arcs = arcMask;
	}

	// This handles the List<String> to bitmask conversion automatically!
	@JsonProperty("arcs")
	public void setArcsFromJSON(List<String> arcList) {
		this.arcs = ArcUtils.calculateMask(arcList);
		this.arcLabel = String.join(" + ", arcList);
	}

	public String getArcLabel() {
		return arcLabel;
	}

	/**
	 * Check to see if the weapon can hit a target within the provided arc.
	 *
	 * @param targetArc The 1-based bearing (1-24) to the target.
	 * @return True if the target is within the weapon arcs, false otherwise.
	 */
	public boolean inArc(int targetArc) {
		return ArcUtils.inArc(targetArc, arcs);
	}

	/**
	 * Checks to see if the weapon is undamaged.
	 * 
	 * @return True if weapon is undamaged, false otherwise.
	 */
	public boolean isFunctional() {
		return functional;
	}

	/**
	 * Apply damage to the weapon, rendering it non-functional.
	 */
	public void damage() {
		functional = false;
	}

	/**
	 * Repair a damaged weapon, rendering it functional again.
	 */
	public void repair() {
		functional = true;
	}

	/**
	 * Get the name of the weapon (Phaser1, Photon, etc.).
	 * 
	 * @return The name of the weapon
	 */
	public String getDesignator() {
		return designator;
	}

	/**
	 * Set the unique designator for this weapon.
	 * 
	 * @param designator Simple designator (A, B, C...1, 2, 3)
	 */
	@JsonProperty("designator")
	public void setDesignator(String designator) {
		this.designator = designator;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getName() {
		return type + "-" + designator;
	}

	/**
	 * Find out when the weapon last fired.
	 * 
	 * @return The last impulse this weapon fired.
	 */
	public int getLastImpulseFired() {
		return lastImpulseFired;
	}

	// TODO: Should this be private only?
	public void setLastImpulseFired(int lastImpulseFired) {
		this.lastImpulseFired = lastImpulseFired;
	}

	protected void setLastTurnFired(int turn) {
		this.lastTurnFired = turn;
	}

	public int getLastTurnFired() {
		return this.lastTurnFired;
	}

	protected void setMaxRange(int range) {
		this.maxRange = range;
	}

	/**
	 * Lower this weapon's reach, never raise it — for a mounting that is worse than the weapon
	 * (J1.31: no shuttle fires a direct-fire weapon beyond fifteen hexes).
	 * <p>
	 * Public where {@link #setMaxRange} is protected, and one-directional for the same reason:
	 * a mount may handicap a weapon but must not improve on what the weapon can do. A weapon
	 * that already reaches less far keeps its own figure, which is how a fighter disruptor
	 * holds its Range 10 under a fifteen-hex cap.
	 */
	public void capMaxRange(int cap) {
		if (cap < this.maxRange)
			this.maxRange = cap;
	}

	protected void setMinRange(int range) {
		this.minRange = range;
	}

	public int getMaxRange() {
		return this.maxRange;
	}

	public int getMinRange() {
		return this.minRange;
	}

	public Unit fetchOwningShip() {
		return owningShip;
	}

	// Per-game impulse clock, injected via Ship.attachClock(). Unit tests that
	// exercise fire cooldowns directly must inject their own instance.
	// Defaults to a private clock so bare weapons (unit tests, prototypes)
	// work standalone; Game overwrites it via Ship.attachClock().
	protected com.sfb.TurnTracker clock = new com.sfb.TurnTracker();

	public void setClock(com.sfb.TurnTracker clock) {
		this.clock = clock;
	}

	public void setOwningShip(Unit owningShip) {
		this.owningShip = owningShip;
	}

	/**
	 * Returns true if this weapon is allowed to fire on the current impulse.
	 * Two conditions must both be met:
	 * 1. Has not exceeded maxShotsPerTurn this turn.
	 * 2. At least minImpulseGap global impulses since last fired.
	 */
	public double energyToFire() {
		return 1.0;
	}

	public boolean canFire() {
		int currentImpulse = clock.getImpulse();
		return shotsThisTurn < maxShotsPerTurn
				&& (currentImpulse - lastImpulseFired) >= minImpulseGap;
	}

	/**
	 * Whether this weapon is ready to fire AT A TARGET this impulse.
	 *
	 * For almost everything that is {@link #canFire()} and nothing more. A drone rack is the
	 * exception, because its canFire asks whether it may LAUNCH — a different question with a
	 * different answer, and one that goes false for a whole turn the moment a type-G commits
	 * to firing anti-drones (FD3.71). Anything deciding whether a weapon may be SHOT should
	 * ask this instead.
	 */
	public boolean readyToFireAtTarget() {
		return canFire();
	}

	/**
	 * G24.1342: firing most weapons blinds one of the scout's powered channels (G24.13).
	 * The exceptions (G24.1341) — phaser-3, ADD, and rack-launched drones / suicide /
	 * scatter shuttles — override this to false.
	 */
	public boolean blindsScoutChannels() {
		return true;
	}

	/**
	 * Register that this weapon fired on the current impulse and turn.
	 */
	protected void registerFire() {
		// Count shots WITHIN the impulse before lastImpulseFired moves, so the counter
		// resets itself whenever the impulse changes and nothing external has to clear it.
		shotsThisImpulse = (lastImpulseFired == clock.getImpulse()) ? shotsThisImpulse + 1 : 1;
		lastImpulseFired = clock.getImpulse();
		lastTurnFired = clock.getTurn();
		lastShotUnderAegis = firingUnderAegis;
		shotsThisTurn++;
	}

	/**
	 * Shots this weapon has put out on the impulse it last fired, counting the shot just
	 * registered. Zero before it ever fires.
	 * <p>
	 * Exists for G24.1342, where a phaser-G blinds a scout channel only when it fires more
	 * than once in one impulse. {@code shotsThisTurn} cannot answer that and
	 * {@code lastImpulseFired} alone cannot either — after a shot resolves, every weapon that
	 * just fired has {@code lastImpulseFired == now}.
	 */
	public int getShotsThisImpulse() {
		return lastImpulseFired == clock.getImpulse() ? shotsThisImpulse : 0;
	}

	// --- D13.22: a weapon may not fire both ways in one impulse ---

	/** Set by the firing path immediately before a shot; captured by {@link #registerFire}. */
	private boolean firingUnderAegis;

	/** Which way the most recent shot went, for the D13.22 test below. */
	private boolean lastShotUnderAegis;

	/**
	 * Tell this weapon which kind of shot is about to be taken (D13.22). The ordinary fire
	 * path leaves it false; the aegis path sets it true and clears it afterwards.
	 */
	public void setFiringUnderAegis(boolean underAegis) {
		this.firingUnderAegis = underAegis;
	}

	/**
	 * D13.22: "Any non-aegis use of a given weapon cannot take place on the same impulse as
	 * the weapon is fired under aegis control." The rule's own example is a phaser-G, which is
	 * exactly the case ordinary rate limits do NOT catch — it has four shots a turn, so
	 * {@code canFire} is perfectly happy to let it fire again in the same impulse. A phaser-1
	 * is protected by its own once-per-turn limit and would never reach this.
	 *
	 * @param currentImpulse the absolute impulse
	 * @param underAegis     whether the shot being considered is an aegis shot
	 * @return true if this weapon has already fired the OTHER way this impulse
	 */
	public boolean barredByAegisExclusivity(int currentImpulse, boolean underAegis) {
		return lastImpulseFired == currentImpulse && lastShotUnderAegis != underAegis;
	}

	/**
	 * End of turn cleanup. Resets per-turn shot counter and impulse gap tracker
	 * so the minImpulseGap restriction doesn't carry over into the next turn.
	 */
	public void cleanUp() {
		shotsThisTurn = 0;
		shotsThisImpulse = 0;
		lastImpulseFired = -9;
	}

	public int getMaxShotsPerTurn() {
		return maxShotsPerTurn;
	}

	public void setMaxShotsPerTurn(int maxShotsPerTurn) {
		this.maxShotsPerTurn = maxShotsPerTurn;
	}

	public int getMinImpulseGap() {
		return minImpulseGap;
	}

	public void setMinImpulseGap(int minImpulseGap) {
		this.minImpulseGap = minImpulseGap;
	}

	public int getShotsThisTurn() {
		return shotsThisTurn;
	}

	public int getLastRoll() {
		return lastRoll;
	}

	protected void setLastRoll(int roll) {
		this.lastRoll = roll;
	}

	public int getEcmShift() {
		return ecmShift;
	}

	public void setEcmShift(int shift) {
		this.ecmShift = shift;
	}
}
