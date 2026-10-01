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



	private int chaffLockoutUntilImpulse = -999; // impulse through which chaff lockout is active (-999 = none)

	private String parentShipName; // Name of the ship that launched this shuttle (set at launch time)

	// Absolute impulse (TurnTracker.getImpulse()) when this shuttle was launched
	// onto the map.
	// Default -999 so elapsed is always huge for in-bay shuttles (they always pass
	// any readiness check).
	/** J1.342: a quarter turn before direct-fire weapons may be used after a launch. */
	public static final int DIRECT_FIRE_DELAY = 8;

	/** J1.341: half a turn before seeking weapons may be launched or guided. */
	public static final int SEEKER_DELAY = 16;

	/**
	 * J1.343: a quarter turn before a shuttle may LOAN EW points (or lay or sweep mines, or
	 * collect information).
	 * <p>
	 * The same eight impulses as {@link #DIRECT_FIRE_DELAY}, kept as its own constant because
	 * it is its own rule: the two happen to coincide today and a revision to one must not
	 * silently move the other.
	 */
	public static final int EW_LENDING_DELAY = 8;

	/**
	 * The absolute impulse this craft last left a bay on (J1.34), or far in the past while
	 * it is sitting in one — a shuttle in a bay is not serving out a launch delay.
	 * <p>
	 * Read through {@link #getLaunchImpulse()} and never directly, even in this class:
	 * ScatterPack keeps its own and overrides the accessor, so a check reading the field saw
	 * -999 for every pack and waved it through.
	 */
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
		com.sfb.objects.ShuttleCatalog.Entry e = catalogEntry();
		return e != null && e.canSuicide;
	}

	/**
	 * True if this shuttle can be converted to a scatter pack before game start.
	 */
	/** FD7.11: only admin, MRS, MLS, MSS shuttles and fighters qualify. */
	public boolean canBecomeScatterPack() {
		return scatterPackSpaces() > 0;
	}

	/**
	 * Whether this shuttle carries a pilot. Every shuttle and fighter does, except in the
	 * three roles that fly empty: a wild weasel, a scatter pack and a suicide shuttle,
	 * each of which overrides this.
	 * <p>
	 * It matters because identification reveals it (G4.233) — and reveals nothing that
	 * separates the last two, since both read "unmanned, on a seeking course". Manning
	 * follows the ROLE rather than the craft, which is why it lives on the role classes
	 * and not in the shuttle catalogue.
	 */
	public boolean isManned() {
		return true;
	}

	/**
	 * The special role this shuttle is prepared for, or null if it is just a shuttle.
	 *
	 * A prepared shuttle is not interchangeable with a plain one: a charged Wild Weasel
	 * cannot be turned back into an admin shuttle mid-turn, and neither can an armed
	 * suicide shuttle or a loaded scatter pack. Each has its own launch action, and this
	 * is what stops the ordinary one from spending them by mistake — which wasted the
	 * preparation and the energy behind it, with nothing to show.
	 *
	 * A charged weasel is the case that needs saying: it is still an ADMIN shuttle, same
	 * class and same type, so nothing else distinguishes it in a launch list.
	 */
	public String specialRole() {
		return getWwChargeCount() > 0 ? "Wild Weasel" : null;
	}

	// --- Wild Weasel charging (J3.12) ---
	// On Shuttle, not AdminShuttle: J3.18 lets any non-fighter shuttle serve as a weasel,
	// and while this state lived on AdminShuttle every gate had to test for that class —
	// so a GAS or an HTS could never be charged no matter what the rules said.

	private int wwChargeCount = 0;

	public int getWwChargeCount() {
		return wwChargeCount;
	}

	/** J3.12: two turns of charging before it can be launched as a weasel. */
	public boolean isWwReady() {
		return wwChargeCount >= 2;
	}

	public void incrementWwCharge() {
		if (wwChargeCount < 2)
			wwChargeCount++;
	}

	public void resetWwCharge() {
		wwChargeCount = 0;
	}

	/**
	 * The catalogue key for what this shuttle IS — "admin", "gas", "hts", "stinger1".
	 * Set by each concrete type's constructor. A shuttle converted to a role keeps the key
	 * of what it was built from, which is how its name and label stay honest.
	 */
	private String catalogType;

	public String getCatalogType() { return catalogType; }

	/**
	 * The key the CLIENT identifies this craft by — its hangar and launch pad both group on it.
	 * <p>
	 * The catalogue type for anything that is catalogue stock, which is almost everything. The
	 * three ROLE craft override it: a scatter pack, a suicide shuttle and a wild weasel are an
	 * administrative shuttle playing a part (J2.0, J3.0, FD7.0), so each keeps "admin" as its
	 * catalogue type while needing to present as what it is now doing.
	 * <p>
	 * This used to be derived in the DTO from the Java class name, which worked only while every
	 * fighter had a class of its own. Once a fighter became a catalogue row they shared one, and
	 * every fighter in every bay would have reported "cataloguedfighter" to the client.
	 */
	public String dtoType() {
		return catalogType;
	}

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
		return Math.max(0, maxSpeed - (emSpeedCommitted ? 1 : 0) - speedPenalty());
	}

	/**
	 * Speed given up for equipment bolted on rather than carried in place of something.
	 * Zero for a plain shuttle; J4.9621 charges a fighter a point for each EXTRA EW pod.
	 */
	protected int speedPenalty() {
		return 0;
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

	/**
	 * Mend damage (J4.818): one point per deck crew action.
	 * <p>
	 * The point of it is the threshold. A fighter is crippled by accumulated damage (J1.33),
	 * so a single point of repair can carry it back under the line and give it its speed and
	 * its weapons again — which is why a crew is sometimes better spent here than loading.
	 * What it cannot give back is what the crippling SPENT: J1.3324 discharged the fusion
	 * capacitors, and those charges are gone until a crew reloads them.
	 *
	 * @return a line describing what changed, or null if there was nothing to mend
	 */
	public String repairDamage(int points) {
		int damage = getHull() - getCurrentHull();
		if (points <= 0 || damage <= 0)
			return null;
		int mended = Math.min(points, damage);
		setCurrentHull(getCurrentHull() + mended);

		StringBuilder sb = new StringBuilder(getName() + ": " + mended + " point"
				+ (mended == 1 ? "" : "s") + " of damage repaired (J4.818)");
		if (crippled && crippledHull > 0 && (getHull() - getCurrentHull()) < crippledHull) {
			uncripple();
			sb.append(" — back under the crippling threshold and fully operational (J1.33)");
		}
		return sb.toString();
	}

	/**
	 * Undo what crippling did, short of what it spent. Subclasses override to give back the
	 * weapons J1.332 took away.
	 */
	public void uncripple() {
		crippled = false;
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
	 * J1.342: a shuttle cannot fire direct-fire weapons for a quarter turn — eight impulses
	 * — after its most recent launch. Launched on impulse 5, it may fire on impulse 13.
	 * <p>
	 * "Most recent" is what makes this the launch impulse rather than a one-off flag: a
	 * shuttle recovered and sent out again starts the count afresh, which setLaunchImpulse
	 * does for free.
	 */
	public boolean canFireDirect(int currentImpulse) {
		return (currentImpulse - getLaunchImpulse()) >= DIRECT_FIRE_DELAY;
	}

	/**
	 * Impulses still to serve before this craft may use direct-fire weapons (J1.342), or
	 * zero if it may already. The countdown rather than the boolean, so a readout can say
	 * how long instead of only that it cannot — and so the client is never the thing
	 * subtracting impulses to work out a rules answer.
	 */
	public int impulsesUntilDirectFire(int currentImpulse) {
		return Math.max(0, DIRECT_FIRE_DELAY - (currentImpulse - getLaunchImpulse()));
	}

	/**
	 * J1.343: a shuttle "cannot loan EW points ... for 1/4 turn (eight impulses) after its
	 * most recent launch" — the same wait as its direct-fire weapons.
	 * <p>
	 * The rule is deliberately one-sided, and its last sentence says so outright: "A shuttle
	 * can receive EW lending immediately upon launch." So this gates the LENDER only. A
	 * fighter launched into a formation is protected by its EW fighter at once; an EW fighter
	 * launched into one protects nobody for eight impulses.
	 */
	public boolean canLoanEw(int currentImpulse) {
		return (currentImpulse - getLaunchImpulse()) >= EW_LENDING_DELAY;
	}

	/** Impulses still to serve before this craft may lend EW (J1.343), or zero if it may. */
	public int impulsesUntilEwLending(int currentImpulse) {
		return Math.max(0, EW_LENDING_DELAY - (currentImpulse - getLaunchImpulse()));
	}

	/** The same for seeking weapons (J1.341), which wait twice as long. */
	public int impulsesUntilSeekers(int currentImpulse) {
		if (this instanceof ScatterPack)
			return 0;   // FD7.33 instead; isReadyToRelease holds a pack to its quarter turn
		return Math.max(0, SEEKER_DELAY - (currentImpulse - getLaunchImpulse()));
	}

	/**
	 * J1.341: a shuttle cannot launch or guide seeking weapons until half a turn — sixteen
	 * impulses — after its most recent launch. Twice the direct-fire wait, and the gap
	 * between the two is real: a fighter that may fire its phasers on impulse 13 still may
	 * not release a drone until 21.
	 * <p>
	 * Scatter-packs are the rule's own exception, and the rule gives them a quarter turn
	 * rather than none at all: {@link ScatterPack#isReadyToRelease} holds them to that
	 * eight-impulse delay (FD7.33, FD7.44), so a pack answering true here is deferring to
	 * that check, not escaping one.
	 */
	public boolean canLaunchSeeker(int currentImpulse) {
		if (this instanceof ScatterPack)
			return true;   // held instead to FD7.33's quarter turn, by isReadyToRelease
		return (currentImpulse - getLaunchImpulse()) >= SEEKER_DELAY;
	}

}
