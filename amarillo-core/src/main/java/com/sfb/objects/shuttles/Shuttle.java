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
	private int crippledHull = 0;   // damage that cripples it; 0 = no crippled state (J1.33)
	private int chaffPacks = 0;
	private int ewPods = 0; // Number of EW pods carried by the shuttle
	// True once an enemy scout has identified this shuttle (G24.25). Every shuttle can be
	// identified — a plain shuttle looks just like a disguised seeker until then. Seeking
	// shuttles satisfy the Seeker interface's identify()/isIdentified() through these.
	private boolean identified = false;

	public void identify() { this.identified = true; }

	public boolean isIdentified() { return this.identified; }

	// G24.235: a shuttle that has not been identified may answer a scout's attraction as if it
	// were a seeking weapon, to fool the scout's owner. Holds the scout's name while the bluff
	// stands; the shuttle is obliged to fly at that scout for as long as it keeps it up.
	private String claimedAttractedTo;

	public String getClaimedAttractedTo() { return this.claimedAttractedTo; }

	public void setClaimedAttractedTo(String scoutName) { this.claimedAttractedTo = scoutName; }

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
	/** FD7.11: only admin, MRS, MLS, MSS shuttles and fighters qualify. */
	public boolean canBecomeScatterPack() {
		return scatterPackSpaces() > 0;
	}

	/**
	 * The catalogue key for what this shuttle IS — "admin", "gas", "hts", "stinger1".
	 * Set by each concrete type's constructor. A shuttle converted to a role keeps the key
	 * of what it was built from, which is how its name and label stay honest.
	 */
	private String catalogType;

	public String getCatalogType() { return catalogType; }

	protected void setCatalogType(String type) { this.catalogType = type; }

	private com.sfb.objects.ShuttleCatalog.Entry catalogEntry() {
		return catalogType == null ? null : com.sfb.objects.ShuttleCatalog.get(catalogType);
	}

	/**
	 * J3.18: any non-fighter shuttle may be charged as a Wild Weasel unless its own
	 * description says otherwise; fighters never may (J4.41).
	 * <p>
	 * Read from the catalogue rather than overridden per class, because this list and the
	 * scatter-pack list of FD7.11 are NOT the same — a GAS may weasel but not scatter-pack,
	 * a fighter the reverse — so neither can be derived from the other or from the class
	 * hierarchy. Side by side as data, each can be checked against its rule.
	 */
	public boolean canBecomeWildWeasel() {
		com.sfb.objects.ShuttleCatalog.Entry e = catalogEntry();
		return e != null && e.canWeasel;
	}

	/** FD7.11: rack spaces of drones this may carry as a scatter pack; 0 = not qualified. */
	public int scatterPackSpaces() {
		com.sfb.objects.ShuttleCatalog.Entry e = catalogEntry();
		return e == null ? 0 : e.scatterPackSize;
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
	private int atmosphereEnteredTurn = -1; // turn it entered the atmosphere (descent lands the NEXT turn)
	private int takeoffTurn = -1;           // turn it lifted off (may leave the planet hex the NEXT turn)

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

	/** Turn on which this shuttle entered the atmosphere; it lands the next turn (P2.4113). */
	public int getAtmosphereEnteredTurn() {
		return atmosphereEnteredTurn;
	}

	public void setAtmosphereEnteredTurn(int turn) {
		this.atmosphereEnteredTurn = turn;
	}

	/** Turn on which this shuttle lifted off the surface; it may leave the hex the next turn (P2.412). */
	public int getTakeoffTurn() {
		return takeoffTurn;
	}

	public void setTakeoffTurn(int turn) {
		this.takeoffTurn = turn;
	}

	// -------------------------------------------------------------------------
	// Cargo / personnel hold (J2.2 / G25.13)
	// -------------------------------------------------------------------------
	//
	// The hold carries passengers — crew units, boarding parties, commandos —
	// and (later) cargo. Personnel occupy "spaces": a boarding party or commando
	// is 1, a crew unit is 2, so a standard admin shuttle (capacity 2) holds one
	// crew unit OR two boarding parties (J2.211). Cargo is a separate track
	// (G25.11 50-space boxes) reduced by personnel aboard; its capacity is
	// scaffolded here but not yet enforced (Annex #7K item sizes deferred).

	private final com.sfb.objects.Manifest hold = new com.sfb.objects.Manifest();
	private int personnelCapacity = 0; // personnel spaces (admin shuttle = 2, J2.211)
	private int cargoCapacity     = 0; // base cargo spaces (G25.13; scaffolded, not yet enforced)

	public com.sfb.objects.Manifest getHold() {
		return hold;
	}

	public int getPersonnelCapacity() {
		return personnelCapacity;
	}

	public void setPersonnelCapacity(int spaces) {
		this.personnelCapacity = spaces;
	}

	public int getCargoCapacity() {
		return cargoCapacity;
	}

	public void setCargoCapacity(int spaces) {
		this.cargoCapacity = spaces;
	}

	/** Personnel spaces occupied by everyone in the hold (J2.211 sizing). */
	public int personnelSpacesUsed() {
		return hold.personnelSpaces();
	}

	/** Personnel spaces still available in the hold. */
	public int personnelSpacesFree() {
		return personnelCapacity - personnelSpacesUsed();
	}

	@Override
	public void releaseTractor() {
		super.releaseTractor();
		// J1.6221: releasing the tractor ends the landing procedure
		beingRecovered = false;
	}

	// --- Erratic Maneuvers: the point of speed it costs (C10.13/C10.131) ---

	private boolean emSpeedCommitted = false;

	public boolean isEmSpeedCommitted() { return emSpeedCommitted; }

	/**
	 * C10.13/C10.131: a shuttle or fighter buys EM with one movement point - a point of
	 * speed - and the commitment binds for the WHOLE turn. It is recorded during energy
	 * allocation if the shuttle is already launched, or on the impulse of launch if it is
	 * not, and "the shuttle cannot cancel this written commitment and accelerate to its
	 * full speed during the turn". Switching EM itself off does not give the point back,
	 * which is why this is separate state from {@code isUsingEm()}.
	 */
	public void commitEmSpeed() {
		emSpeedCommitted = true;
		if (getCurrentSpeed() > effectiveMaxSpeed())
			setCurrentSpeed(effectiveMaxSpeed());
		if (getSpeed() > effectiveMaxSpeed())
			setSpeed(effectiveMaxSpeed());
	}

	/** Turn boundary: the commitment must be recorded afresh each turn (C10.131). */
	public void clearEmSpeedCommitment() {
		emSpeedCommitted = false;
	}

	/**
	 * The fastest this shuttle may move, after any point of speed dedicated to Erratic
	 * Maneuvers (C10.13). C10.134: a shuttle at this speed counts as being at "maximum
	 * speed" for G7.55, even though it is one below its rating.
	 */
	public int effectiveMaxSpeed() {
		return Math.max(0, maxSpeed - (emSpeedCommitted ? 1 : 0));
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

	/**
	 * C11.1: "All shuttlecraft and fighters (including those on seeking courses) are nimble
	 * unless noted otherwise in the rules."
	 */
	@Override
	public boolean isNimbleUnit() {
		return true;
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

	/**
	 * Damage at which this shuttle is crippled rather than merely hurt (J1.33). Zero means it
	 * has no crippled state and is destroyed outright — so a type that has never been given a
	 * threshold is not crippled the instant it takes its first point.
	 */
	public int getCrippledHull() {
		return crippledHull;
	}

	public void setCrippledHull(int crippledHull) {
		this.crippledHull = crippledHull;
	}

	/** True once enough damage has accumulated to cripple this shuttle (J1.33). */
	public boolean shouldCripple() {
		return crippledHull > 0 && !crippled && (getHull() - getCurrentHull()) >= crippledHull;
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
