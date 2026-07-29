package com.sfb.objects.shuttles;

import com.sfb.objects.*;

import com.sfb.properties.TurnMode;
import com.sfb.systemgroups.Weapons;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PhaserWeapon;
import com.sfb.weapons.Weapon;

/**
 * This object represents a base shuttle.
 * 
 * @author Daniel Eastland
 *
 */
public abstract class Shuttle extends Unit {

	private int maxSpeed; // The maximum speed this shuttle can go
	private int hull; // The maximum hull value of the shuttle

	private int currentSpeed; // The speed the shuttle is currently travelling
	private int currentHull; // The number of undamaged hull remaining.
	private boolean crippled = false;
	private int chaffPacks = 0;
	private int ewPods = 0; // Number of EW pods carried by the shuttle

	public int getEwPods() {
		return ewPods;
	}

	public void setEwPods(int ewPods) {
		this.ewPods = ewPods;
	}

	private int chaffLockoutUntilImpulse = -999; // impulse through which chaff lockout is active (-999 = none)

	private String parentShipName; // Name of the ship that launched this shuttle (set at launch time)

	// Absolute impulse (TurnTracker.getImpulse()) when this shuttle was launched
	// onto the map.
	// Default -999 so elapsed is always huge for in-bay shuttles (they always pass
	// any readiness check).
	private int launchImpulse = -999;

	private Weapons weapons = new Weapons(this); // The weapons carried by the shuttle.

	public Shuttle() {
		setTurnMode(TurnMode.Shuttle);
		setSizeClass(6);
	}

	/**
	 * Returns true if this shuttle is controlled by the player (manual movement).
	 * Auto-drifting objects (e.g. released ScatterPack) override this to return
	 * false.
	 */
	public boolean isPlayerControlled() {
		return true;
	}

	// -------------------------------------------------------------------------
	// Pre-game conversion eligibility (COI special shuttle prep)
	// -------------------------------------------------------------------------

	/**
	 * True if this shuttle can be converted to a suicide shuttle before game start.
	 */
	public boolean canBecomeSuicide() {
		return false;
	}

	/**
	 * True if this shuttle can be converted to a scatter pack before game start.
	 */
	public boolean canBecomeScatterPack() {
		return false;
	}

	/** True if this shuttle can be converted to a wild weasel before game start. */
	public boolean canBecomeWildWeasel() {
		return false;
	}

	// J1.621: true while this shuttle is shut down and being pulled aboard a
	// ship one hex per impulse. Cleared whenever the tractor link breaks.
	private boolean beingRecovered = false;

	public boolean isBeingRecovered() {
		return beingRecovered;
	}

	public void setBeingRecovered(boolean beingRecovered) {
		this.beingRecovered = beingRecovered;
	}

	// Planet landing procedure (P2.4). While IN_ATMOSPHERE or LANDED the shuttle
	// sits in the planet hex; landedHexSide (1..6) is the face it entered on /
	// occupies (P2.611). NONE = in normal space.
	private com.sfb.properties.LandingPhase landingPhase = com.sfb.properties.LandingPhase.NONE;
	private int landedHexSide = 0;

	public com.sfb.properties.LandingPhase getLandingPhase() {
		return landingPhase;
	}

	public void setLandingPhase(com.sfb.properties.LandingPhase landingPhase) {
		this.landingPhase = landingPhase;
	}

	/** Planet hex side (1..6, A..F) this shuttle occupies, or 0 if not at a planet. */
	public int getLandedHexSide() {
		return landedHexSide;
	}

	public void setLandedHexSide(int landedHexSide) {
		this.landedHexSide = landedHexSide;
	}

	@Override
	public void releaseTractor() {
		super.releaseTractor();
		// J1.6221: releasing the tractor ends the landing procedure
		beingRecovered = false;
	}

	public int getMaxSpeed() {
		return maxSpeed;
	}

	public void setMaxSpeed(int maxSpeed) {
		this.maxSpeed = maxSpeed;
	}

	public int getCurrentSpeed() {
		return currentSpeed;
	}

	public void setCurrentSpeed(int currentSpeed) {
		this.currentSpeed = currentSpeed;
	}

	public int getHull() {
		return hull;
	}

	public void setHull(int maxHull) {
		this.hull = maxHull;
		if (this.currentHull == 0)
			this.currentHull = maxHull;
	}

	public int getCurrentHull() {
		return currentHull;
	}

	public void setCurrentHull(int currentHull) {
		this.currentHull = currentHull;
	}

	/** Inject the owning game's impulse clock into this shuttle's weapons. */
	public void attachClock(com.sfb.TurnTracker clock) {
		for (Weapon w : weapons.fetchAllWeapons())
			w.setClock(clock);
	}

	public Weapons getWeapons() {
		return this.weapons;
	}

	public boolean isCrippled() {
		return crippled;
	}

	public int getChaffPacks() {
		return chaffPacks;
	}

	public void setChaffPacks(int chaffPacks) {
		this.chaffPacks = chaffPacks;
	}

	/** True if this shuttle is within the 8-impulse post-chaff lockout (D11.41). */
	public boolean isChaffLockedOut(int currentImpulse) {
		return currentImpulse <= chaffLockoutUntilImpulse;
	}

	/**
	 * Record chaff use: decrement pack count and apply 8-impulse lockout (D11.41).
	 */
	public void applyChaffLockout(int currentImpulse) {
		chaffPacks--;
		chaffLockoutUntilImpulse = currentImpulse + 8;
	}

	/**
	 * Apply J1.331 speed reduction. Subclasses override to also apply J1.332 weapon
	 * effects.
	 * Returns a log line describing what changed, or null if already crippled.
	 */
	public String applyCripplingEffects() {
		if (crippled)
			return null;
		crippled = true;
		int crippledMax = (int) Math.ceil(maxSpeed / 2.0);
		StringBuilder sb = new StringBuilder(getName() + " CRIPPLED");
		sb.append(" — max speed reduced to ").append(crippledMax);
		if (currentSpeed > crippledMax) {
			currentSpeed = crippledMax;
			setSpeed(crippledMax);
			sb.append(", speed reduced to ").append(crippledMax);
		}
		return sb.toString();
	}

	public String getParentShipName() {
		return parentShipName;
	}

	public void setParentShipName(String name) {
		this.parentShipName = name;
	}

	@Override
	public void applyTractor(Unit tractoringUnit) {
		setTractoringUnit(tractoringUnit);
		setTractored(true);
	}

	public int getLaunchImpulse() {
		return launchImpulse;
	}

	public void setLaunchImpulse(int impulse) {
		this.launchImpulse = impulse;
	}

	/**
	 * True if this shuttle is "armed" for chain reaction purposes (D12.12).
	 * Subclasses override for special cases (ScatterPack, SuicideShuttle,
	 * WildWeasel).
	 */
	public boolean isArmed() {
		for (Weapon w : weapons.fetchAllWeapons()) {
			if (!w.isFunctional())
				continue;
			if (w instanceof PhaserWeapon)
				continue;
			if (w instanceof DroneRack) {
				if (!((DroneRack) w).getAmmo().isEmpty())
					return true;
			} else {
				return true; // functional non-phaser weapon counts as armed
			}
		}
		return false;
	}

	/**
	 * True if enough impulses have elapsed since launch to fire direct-fire weapons
	 * (8 impulses).
	 */
	public boolean canFireDirect(int currentImpulse) {
		return (currentImpulse - launchImpulse) >= 8;
	}

	/**
	 * True if enough impulses have elapsed since launch to launch seekers (16
	 * impulses).
	 * ScatterPacks are exempt — they ARE the seeker payload.
	 */
	public boolean canLaunchSeeker(int currentImpulse) {
		if (this instanceof ScatterPack)
			return true;
		return (currentImpulse - launchImpulse) >= 16;
	}

}
