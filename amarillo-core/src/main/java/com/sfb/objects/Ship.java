package com.sfb.objects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sfb.constants.Constants;
import com.sfb.exceptions.CapacitorException;
import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.Faction;
import com.sfb.properties.ShieldStatus;
import com.sfb.properties.SystemTarget;
import com.sfb.properties.TurnMode;
import com.sfb.systemgroups.CloakingDevice;
import com.sfb.systemgroups.ControlSpaces;
import com.sfb.systemgroups.Crew;
import com.sfb.systemgroups.HullBoxes;
import com.sfb.systemgroups.Labs;
import com.sfb.systemgroups.PowerSystems;
import com.sfb.systemgroups.ProbeLaunchers;
import com.sfb.systemgroups.Shields;
import com.sfb.systemgroups.Shuttles;
import com.sfb.systemgroups.Transporters;
import com.sfb.systemgroups.Weapons;
import com.sfb.systems.Energy;
import com.sfb.systems.PerformanceData;
import com.sfb.systems.SpecialFunctions;
import com.sfb.systems.Tractors;
import com.sfb.utilities.DAC;
import com.sfb.utilities.DiceRoller;
import com.sfb.utilities.ImpulseUtil;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;

/**
 * 
 * This object describes an SFB ship. In particular it should represent
 * the contents of an SSD along with all boxes, ammo, ship traits, etc.
 * 
 * @author Daniel Eastland
 *
 * @version 1.0
 */
public class Ship extends Unit {

	/// All the stuff that goes into a ship ///

	private DAC dac = new DAC(); // Damage Allocation Chart
	private Shields shields = new Shields(); // Shield systems
	private HullBoxes hullBoxes = new HullBoxes(); // Hull boxes
	private PowerSystems powerSystems = new PowerSystems(); // Power systems (warp, impulse, apr, awr, battery)
	private ControlSpaces controlSpaces = new ControlSpaces(); // Control systems (bridge, flag, aux, emer, security)
	private SpecialFunctions specialFunctions = new SpecialFunctions(); // Special functions
	private Labs labs = new Labs(this); // Labs
	private Transporters transporters = new Transporters(this); // Transporters
	private Tractors tractors = new Tractors(this); // Tractor beam systems.
	private ProbeLaunchers probes = new ProbeLaunchers(this); // Probes
	private Shuttles shuttles = new Shuttles(this); // Shuttles and shuttle bays.
	private Weapons weapons = new Weapons(this); // Weapons
	private PerformanceData performanceData = new PerformanceData(); // Base statistics for the frame.
	private Crew crew = new Crew(this); // Crew
	private CloakingDevice cloak = null; // Cloaking Device (null if none installed).
	private com.sfb.systemgroups.DERFACS derfacs = null; // DERFACS targeting system (null if none installed).
	private java.util.ArrayList<com.sfb.systemgroups.UIM> uims = new java.util.ArrayList<>(); // UIM modules (up to 4: 1
																																														// active + 3 standby).

	private Energy energyAllocated = new Energy(); // Where all the ship's energy is allocated

	// Odds and ends
	private int armor = 0; // Some early ships have armor.
	private double lifeSupportCost = 0; // cost to have life support active.
	private int activeShieldCost = 0; // Cost to have shields active.
	private double minimumShieldCost = 0; // Cost to have shields at minimum.
	private int fireControlCost = 1; // Cost for active fire control (always 1).
	private int tBombs = 0; // Number of transporter bombs available.
	private int dummyTBombs = 0; // Number of dummy transporter bombs available.
	private int ecmAllocated = 0; // ECM points allocated this turn (hide)
	private int eccmAllocated = 0; // ECCM points allocated this turn (seek)
	private int nuclearSpaceMines = 0; // Number of nuclear space mines available. (Romulan special weapon)
	/** Enemy boarding parties currently on board (D7.31). */
	private final TroopCount enemyTroops = new TroopCount();
	/**
	 * Player whose boarding parties are aboard (set when troops land; used for
	 * ownership transfer on capture).
	 */
	private com.sfb.Player boardingAttacker = null;

	/**
	 * Speed history for C2.2 acceleration limits.
	 * Max speed this turn = max(lowestRecent + 10, lowestRecent * 2), capped at 31.
	 * lowestRecent = min(speedPreviousTurn, speedTwoTurnsAgo).
	 * Both seeded from startSpeed at scenario load.
	 */
	private int speedPreviousTurn = 0;
	private int speedTwoTurnsAgo = 0;

	/** True if this ship has been captured (D7.50). */
	private boolean captured = false;

	/** True if this ship has voluntarily exited the map and is disengaged. */
	private boolean disengaged = false;

	/** End-of-battle status — set when ship leaves play. */
	private com.sfb.properties.BattleStatus battleStatus = com.sfb.properties.BattleStatus.ACTIVE;

	// HET tracking
	private int lastHetImpulse = -99; // large negative so first-turn gap check passes
	private int hetsThisTurn = 0;
	private int immobileUntilImpulse = 0; // 0 = not immobile; set to currentImpulse+16 on breakdown
	private int breakdownLockoutUntilImpulse = -1; // C6.547: weapon/shuttle/transporter lockout for 8 impulses

	// Internal damage crew/BP casualty tracking (G9.21, D7.21)
	private int internalDamagePointsTotal = 0; // cumulative internal damage points scored on this ship
	private int crewKilledByInternalDamage = 0; // crew kill events already applied
	private int bpCasualtiesProcessed = 0; // BP casualty events already processed (after 4-event grace)

	/**
	 * Guards assigned to defend specific system types this turn (D7.83). Key =
	 * system type, Value = quality of the guarding party.
	 */
	private final Map<SystemTarget.Type, BoardingPartyQuality> guards = new EnumMap<>(SystemTarget.Type.class);

	// Other data
	private int yearInService = 0; // The minimum year this ship can be deployed.
	private String hullType = null; // Descriptor of the type of ship (i.e. "CA", "FFG", "D7K", etc.)
	private String tokenArt = null; // Optional path to a PNG token image
	private Faction faction = Faction.Federation; // The faction to which this ship belongs.
	private int battlePointValue = 0; // BPV, a measure of how powerful the ship is in combat.
	private int economicPointValue = 0; // EPV (split-BPV ships only); falls back to BPV if unset.
	private int commandRating = 0; // Command Rating, the number of ships this ship can command in a scenario.

	// Real-time data
	private boolean activeFireControl = false; // True if active fire control is up, false otherwise.
	/**
	 * False only at WS-0 start; costs 1 energy to energize; true for all other
	 * weapon status levels.
	 */
	private boolean capacitorsCharged = true;
	private ShieldStatus shieldsStatus = ShieldStatus.Inactive;
	private Set<Unit> lockOns = new HashSet<>(); // Units this ship currently has sensor lock-on to. // Status of shields.
																								// Active is normal shields. Minimal is
																								// 5-point shields. Inactive is no shields at all.
	private boolean lifeSupportActive = false; // True if life support is active, false otherwise.

	/**
	 * Constructor
	 */
	public Ship() {
	}

	/**
	 * Initialize all ship statistics through the values
	 * passed in the Map.
	 */
	public void init(Map<String, Object> values) {
		// Unit values
		super.init(values);

		// Explicit Ship values
		faction = values.get("faction") == null ? null : (Faction) values.get("faction");
		hullType = values.get("hull") == null ? null : (String) values.get("hull");
		tokenArt = values.get("tokenart") == null ? null : (String) values.get("tokenart");
		yearInService = values.get("serviceyear") == null ? 0 : (Integer) values.get("serviceyear");
		battlePointValue = values.get("bpv") == null ? 0 : (Integer) values.get("bpv");
		economicPointValue = values.get("epv") == null ? battlePointValue : (Integer) values.get("epv");
		commandRating = values.get("commandrating") == null ? 0 : (Integer) values.get("commandrating");

		// Calculated Ship Values
		lifeSupportCost = Constants.LIFE_SUPPORT_COST[getSizeClass()];
		activeShieldCost = Constants.ACTIVE_SHIELD_COST[getSizeClass()];
		minimumShieldCost = Constants.MINIMUM_SHIELD_COST[getSizeClass()];

		// Odds and ends
		armor = values.get("armor") == null ? 0 : (Integer) values.get("armor");
		tBombs = values.get("tbombs") == null ? 0 : (Integer) values.get("tbombs");
		dummyTBombs = values.get("dummytbombs") == null ? 0 : (Integer) values.get("dummytbombs");
		nuclearSpaceMines = values.get("nuclearspacemines") == null ? 0 : (Integer) values.get("nuclearspacemines");

		// Subsystem values
		shields.init(values);
		hullBoxes.init(values);
		powerSystems.init(values);
		controlSpaces.init(values);
		specialFunctions.init(values);
		labs.init(values);
		transporters.init(values);
		tractors.init(values);
		probes.init(values);
		shuttles.init(values);
		weapons.init(values);
		crew.init(values);
		performanceData.init(values);

		// Optional systems
		if (values.containsKey("cloakcost")) {
			cloak = new CloakingDevice(this, (Integer) values.get("cloakcost"));
		}
		if (Boolean.TRUE.equals(values.get("derfacs"))) {
			derfacs = new com.sfb.systemgroups.DERFACS(this);
		}
		uims.clear();
		for (int i = 0; i < specialFunctions.getUim(); i++) {
			com.sfb.systemgroups.UIM uim = new com.sfb.systemgroups.UIM(this);
			if (i > 0)
				uim.scheduleActivation(Integer.MAX_VALUE); // cold standby — not yet needed
			uims.add(uim);
		}
	}

	/**
	 * Set up the energy profile for this ship for the current turn.
	 * 
	 * @param allocation Object that will contain all instructions for
	 *                   allocation of the ship's energy for the turn.
	 */
	public void allocateEnergy(Energy allocation) {
		this.energyAllocated = allocation;

	}

	@Override
	public void startTurn() {
		// Warp movement: each moveCost energy = 1 speed (max 30)
		// Impulse movement: 1 impulse point = 1 extra hex flat, regardless of moveCost
		// (max +1, giving speed 31)
		int warpSpeed = (int) (energyAllocated.getWarpMovement() / performanceData.getMovementCost());
		int impulseSpeed = Math.min(energyAllocated.getImpulseMovement(), 1);
		int requestedSpeed = Math.min(warpSpeed + impulseSpeed, 31);
		setSpeed(Math.min(requestedSpeed, getMaxAccelerationSpeed()));

		// Life support
		if (energyAllocated.getLifeSupport() >= lifeSupportCost) {
			this.lifeSupportActive = true;
		} else if (isCrippled()) {
			this.lifeSupportActive = true;
		} else {
			this.lifeSupportActive = false;
		}

		// Fire control
		if (energyAllocated.getFireControl() >= fireControlCost) {
			this.activeFireControl = true;
		} else {
			this.activeFireControl = false;
		}

		// Shields

		// General Reinforcement (1 point for every 2 energy)
		if (energyAllocated.getGeneralReinforcement() > 0) {
			this.shields.addGeneralRenforcement(energyAllocated.getGeneralReinforcement() / 2);
		}
		// Specific reinforcement
		shields.reinforceAllShields(energyAllocated.getSpecificReinforcement());
		// Shield activation.
		if (energyAllocated.getActivateShields() == activeShieldCost) {
			this.shieldsStatus = ShieldStatus.Active;
		} else if (energyAllocated.getActivateShields() == minimumShieldCost) {
			this.shieldsStatus = ShieldStatus.Minimum;
		} else {
			this.shieldsStatus = ShieldStatus.Inactive;
		}

		// Damage Control
		// TODO: Damage Control - will probably need a list of systems repaired in the
		// energy allocation

		// Batteries: draw or recharge (mutually exclusive — dialog enforces this)
		if (energyAllocated.getBatteryDraw() > 0) {
			getPowerSystems().useBattery(energyAllocated.getBatteryDraw());
		} else if (energyAllocated.getBatteryRecharge() > 0) {
			getPowerSystems().chargeBattery(energyAllocated.getBatteryRecharge());
		}

		// Reserve warp for HETs (C6.2)
		getPowerSystems().setReserveWarp((int) energyAllocated.getHighEnergyTurns());

		// Phaser Capacitor
		if (energyAllocated.isEnergizeCaps() && !capacitorsCharged) {
			capacitorsCharged = true; // takes effect this turn; player can fill cap next EA
		}
		if (capacitorsCharged) {
			try {
				chargeCapacitor(energyAllocated.getPhaserCapacitor());
			} catch (CapacitorException e) {
				e.printStackTrace();
			}
		}

		// Transporters
		if (energyAllocated.getTransporters() > 0) {
			transporters.bankEnergy(energyAllocated.getTransporters());
		}

		// Weapons
		for (Weapon weapon : weapons.fetchAllWeapons()) {
			// For heavy weapons, apply the arming type and energy (null energy = SKIP,
			// don't arm)
			if (weapon instanceof HeavyWeapon) {
				Double armEnergy = energyAllocated.getArmingEnergy().get(weapon);
				if (armEnergy != null) {
					((HeavyWeapon) weapon).applyAllocationEnergy(armEnergy,
							energyAllocated.getArmingType().get(weapon));
				}
			}
			// For drone racks, apply any assigned reload
			if (weapon instanceof DroneRack) {
				DroneRack rack = (DroneRack) weapon;
				java.util.List<com.sfb.objects.Drone> reloadSet = energyAllocated.getReloadAssignments().get(rack);
				if (reloadSet != null) {
					rack.stagePendingReload(reloadSet);
				}
			}
		}
	}

	/**
	 * Perform end-of-turn activities needed to prepare for the next energy
	 * allocation phase.
	 */
	public void cleanUp() {

		// TODO: Figure out if there is any Ship object level cleanup needed.
		// For example, if there is recharge energy remaining - put it into the
		// batteries

		// Roll speed history for C2.2 acceleration tracking
		speedTwoTurnsAgo = speedPreviousTurn;
		speedPreviousTurn = getSpeed();

		shields.cleanUp();
		hullBoxes.cleanUp();
		powerSystems.cleanUp();
		controlSpaces.cleanUp();
		specialFunctions.cleanUp();
		labs.cleanUp();
		tractors.cleanUp();
		probes.cleanUp();
		shuttles.cleanUp();
		weapons.cleanUp();
		clearGuards(); // D7.834: guards must be re-assigned each turn during EA
		crew.cleanUp();
		performanceData.cleanUp();
		ecmAllocated = 0;
		eccmAllocated = 0;
	}

	/**
	 * Maximum speed this ship may allocate next turn (C2.2).
	 * Based on the lowest speed in the last 32 impulses (= last 2 turns).
	 * Formula: max(lowestRecent + 10, lowestRecent * 2), capped at 31.
	 * Nimble ships and civilian freighters will have different rules (future).
	 */
	public int getMaxAccelerationSpeed() {
		int lowest = Math.min(speedPreviousTurn, speedTwoTurnsAgo);
		return Math.min(31, Math.max(lowest + 10, lowest * 2));
	}

	public int getSpeedPreviousTurn() {
		return speedPreviousTurn;
	}

	public int getSpeedTwoTurnsAgo() {
		return speedTwoTurnsAgo;
	}

	public void setSpeedPreviousTurn(int speed) {
		this.speedPreviousTurn = speed;
	}

	public void setSpeedTwoTurnsAgo(int speed) {
		this.speedTwoTurnsAgo = speed;
	}

	// ---- HET tracking ----

	public int getLastHetImpulse() {
		return lastHetImpulse;
	}

	public void setLastHetImpulse(int impulse) {
		this.lastHetImpulse = impulse;
	}

	public int getHetsThisTurn() {
		return hetsThisTurn;
	}

	public void incrementHetsThisTurn() {
		hetsThisTurn++;
	}

	public void resetHetsThisTurn() {
		hetsThisTurn = 0;
	}

	public int getImmobileUntilImpulse() {
		return immobileUntilImpulse;
	}

	public void setImmobileUntilImpulse(int impulse) {
		this.immobileUntilImpulse = impulse;
	}

	public boolean isImmobile(int currentImpulse) {
		return currentImpulse <= immobileUntilImpulse;
	}

	/** C6.38: true during the HET impulse and for 4 impulses thereafter. */
	public boolean isInPostHetWindow(int currentImpulse) {
		return lastHetImpulse >= 0 && (currentImpulse - lastHetImpulse) <= 4;
	}

	/**
	 * C6.547: true for 8 impulses after a breakdown (weapons, shuttles,
	 * transporters locked out).
	 */
	public boolean isInBreakdownLockout(int currentImpulse) {
		return currentImpulse <= breakdownLockoutUntilImpulse;
	}

	public int getBreakdownLockoutUntilImpulse() {
		return breakdownLockoutUntilImpulse;
	}

	public void setBreakdownLockoutUntilImpulse(int impulse) {
		this.breakdownLockoutUntilImpulse = impulse;
	}

	// ---- Skeleton crew (G9.4) ----

	/**
	 * G9.421: When on skeleton crew, effective turn mode is one step worse.
	 * Capped at F (the worst turn mode for ships).
	 */
	public TurnMode effectiveTurnMode() {
		TurnMode base = getTurnMode();
		if (!getCrew().isSkeleton())
			return base;
		TurnMode[] values = TurnMode.values();
		int next = Math.min(base.ordinal() + 1, TurnMode.F.ordinal());
		return values[next];
	}

	@Override
	public int getTurnHexes() {
		return com.sfb.utilities.TurnModeUtil.getTurnMode(effectiveTurnMode(), getSpeed());
	}

	/// BASIC SHIP DATA ///
	public void setType(String type) {
		this.hullType = type;
	}

	public String getType() {
		return this.hullType;
	}

	public String getTokenArt() {
		return this.tokenArt;
	}

	public void setFaction(Faction faction) {
		this.faction = faction;
	}

	public Faction getFacation() {
		return this.faction;
	}

	public int getYearInService() {
		return this.yearInService;
	}

	public int getBpv() {
		return this.battlePointValue;
	}

	public int getEconomicBpv() {
		return this.economicPointValue;
	}

	public int getArmor() {
		return this.armor;
	}

	public double getLifeSupportCost() {
		return this.lifeSupportCost;
	}

	public boolean isLifeSupportActive() {
		return this.lifeSupportActive;
	}

	public int getActiveShieldCost() {
		return this.activeShieldCost;
	}

	public double getMinimumShieldCost() {
		return this.minimumShieldCost;
	}

	public int getFireControlCost() {
		return this.fireControlCost;
	}

	public int getEcmAllocated() {
		return ecmAllocated;
	}

	public void setEcmAllocated(int ecm) {
		this.ecmAllocated = ecm;
	}

	public int getEccmAllocated() {
		return eccmAllocated;
	}

	public void setEccmAllocated(int eccm) {
		this.eccmAllocated = eccm;
	}

	public int getTBombs() {
		return this.tBombs;
	}

	public void setTBombs(int tBombs) {
		this.tBombs = tBombs;
	}

	public int getDummyTBombs() {
		return this.dummyTBombs;
	}

	public void setDummyTBombs(int dummyTBombs) {
		this.dummyTBombs = dummyTBombs;
	}

	public int getNuclearSpaceMines() {
		return this.nuclearSpaceMines;
	}

	public void setNuclearSpaceMines(int nuclearSpaceMines) {
		this.nuclearSpaceMines = nuclearSpaceMines;
	}

	public boolean isNimble() {
		return performanceData.isNimble();
	}

	// --- Lock-on ---

	public boolean hasLockOn(Unit target) {
		return lockOns.contains(target);
	}

	public void addLockOn(Unit target) {
		lockOns.add(target);
	}

	public void removeLockOn(Unit target) {
		lockOns.remove(target);
	}

	public void clearLockOns() {
		lockOns.clear();
	}

	public Set<Unit> getLockOns() {
		return lockOns;
	}

	// --- Guards (D7.83) ---

	/**
	 * Assign a boarding party as a guard for the given system type (D7.83).
	 * No more than one guard per system type; calling again replaces the previous
	 * assignment.
	 */
	public void assignGuard(SystemTarget.Type systemType, BoardingPartyQuality guardQuality) {
		guards.put(systemType, guardQuality);
	}

	/** Remove a guard assignment from the given system type. */
	public void removeGuard(SystemTarget.Type systemType) {
		guards.remove(systemType);
	}

	/** Returns true if the given system type has a guard assigned. */
	public boolean isGuarded(SystemTarget.Type systemType) {
		return guards.containsKey(systemType);
	}

	/**
	 * Returns the quality of the guard assigned to the given system, or null if
	 * unguarded.
	 */
	public BoardingPartyQuality getGuardQuality(SystemTarget.Type systemType) {
		return guards.get(systemType);
	}

	/**
	 * Clear all guard assignments (called at start of turn after re-posting per
	 * D7.834).
	 */
	public void clearGuards() {
		guards.clear();
	}

	// --- Enemy troops (D7.31) ---

	public TroopCount getEnemyTroops() {
		return enemyTroops;
	}

	/** Convenience: total enemy boarding parties on board. */
	public int getEnemyBoardingParties() {
		return enemyTroops.total();
	}

	/**
	 * Add enemy normal boarding parties (e.g. after a successful H&R transport).
	 */
	public void addEnemyBoardingParties(int normal) {
		enemyTroops.normal += normal;
	}

	/** Add enemy commandos on board. */
	public void addEnemyCommandos(int commandos) {
		enemyTroops.commandos += commandos;
	}

	public com.sfb.Player getBoardingAttacker() {
		return boardingAttacker;
	}

	public void setBoardingAttacker(com.sfb.Player player) {
		this.boardingAttacker = player;
	}

	// --- Destroyed state ---

	public com.sfb.properties.BattleStatus getBattleStatus() {
		return battleStatus;
	}

	public void setBattleStatus(com.sfb.properties.BattleStatus status) {
		this.battleStatus = status;
	}

	public boolean isDestroyed() {
		return battleStatus == com.sfb.properties.BattleStatus.DESTROYED;
	}

	// --- Crippled state (S2.41) ---

	/**
	 * A ship is crippled if ANY ONE of the following conditions is met (S2.41):
	 * A: 10% or less of original warp engine boxes remaining
	 * (skipped for ships with no original warp, e.g. Romulans)
	 * B: 50% or more of interior boxes destroyed
	 * (excludes shields, armor, sensor, scanner, DamCon, excess damage)
	 * C: Any excess damage hits taken
	 * D: All control spaces destroyed
	 * E: All weapons destroyed
	 */
	public boolean isCrippled() {
		// A: warp engine boxes
		int originalWarp = powerSystems.getOriginalWarp();
		if (originalWarp > 0) {
			int remainingWarp = powerSystems.getRemainingWarp();
			if (remainingWarp <= Math.floor(originalWarp * 0.1))
				return true;
		}

		// B: interior boxes (excludes armor and excess damage from the hull totals)
		int originalInterior = getTotalSSDBoxes()
				- hullBoxes.getOriginalArmor()
				- specialFunctions.getOriginalExcessDamage();
		int remainingInterior = getCurrentBoxes()
				- hullBoxes.getAvailableArmor()
				- specialFunctions.getExcessDamage();
		int destroyedInterior = originalInterior - remainingInterior;
		if (originalInterior > 0 && destroyedInterior >= originalInterior * 0.5)
			return true;

		// C: any excess damage hits
		if (specialFunctions.getExcessDamage() < specialFunctions.getOriginalExcessDamage())
			return true;

		// D: all control spaces destroyed
		if (controlSpaces.fetchRemainingTotalBoxes() == 0)
			return true;

		// E: all weapons destroyed
		if (weapons.fetchAllWeapons().stream().noneMatch(com.sfb.weapons.Weapon::isFunctional))
			return true;

		return false;
	}

	// --- Capture state (D7.50) ---

	public boolean isCaptured() {
		return captured;
	}

	public void setCaptured(boolean captured) {
		this.captured = captured;
	}

	// --- Disengagement ---

	public boolean isDisengaged() {
		return disengaged;
	}

	public void setDisengaged(boolean disengaged) {
		this.disengaged = disengaged;
	}

	/**
	 * Indicates if the shields are in Active mode
	 * 
	 * @return True if the shields are Active, false otherwise.
	 */
	public boolean shieldsActive() {
		return this.shieldsStatus == ShieldStatus.Active;
	}

	/**
	 * Indicates if the shields are in Inactive mode
	 * 
	 * @return True if the sheilds are Inactive, false otherwise.
	 */
	public boolean shieldsInactive() {
		return this.shieldsStatus == ShieldStatus.Inactive;
	}

	// Cleanup tasks for the end of the turn.
	public void endOfTurn() {
		shields.cleanUp();

	}

	/// IDENTITY ///
	public String getHullType() {
		return this.hullType;
	}

	public Faction getFaction() {
		return this.faction;
	}

	public int getBattlePointValue() {
		return this.battlePointValue;
	}

	public void setBattlePointValue(int bpv) {
		this.battlePointValue = bpv;
	}

	public int getCommandRating() {
		return this.commandRating;
	}

	/// SHIELDS ///
	public Shields getShields() {
		return this.shields;
	}

	// Get the strength of a particular shield (including specific reinforcement)
	public int getShield(Integer shieldNumber) {
		return this.shields.getShieldStrength(shieldNumber);
	}

	// Applies damage to given shield. If any damage remains, return the value.
	// Otherwise return 0.
	public int damageShield(Integer shieldNumber, Integer damageApplied) {
		return this.shields.damageShield(shieldNumber, damageApplied);
	}

	/**
	 * Discover which of this ship's shields is facing another unit.
	 * 
	 * @param otherUnit The other unit being used in the check.
	 * @return The shield facing (1..12). Odd numbers are shield facings, even
	 *         numbers are the borders between shields.
	 */
	public int getRelativeShieldFacing(Marker otherMarker) {
		int absFacing = MapUtils.getAbsoluteShieldFacing(this, otherMarker);
		int relFacing = MapUtils.getRelativeShieldFacing(absFacing, this.getFacing());

		return relFacing;
	}

	/**
	 * User energy to repair a shield, limited by the ship's DamCon rating.
	 * 
	 * @param shieldNumber The shield to repair.
	 * @param energy       The amount of energy to expend.
	 * @return True if this is a legal request, false otherwise.
	 */
	public boolean repairShield(int shieldNumber, int energy) {
		// Energy spent can not exceed current DamagaeControl rating.
		if (energy > this.specialFunctions.getDamageControl()) {
			return false;
		}

		// Energy expenditure is good. Repair a number of shield boxes
		// equal to half the energy spent.
		return this.shields.repairShield(shieldNumber, energy / 2);
	}

	/**
	 * Checks on the status of the shields:
	 * Active) Full shields
	 * Minimal) 5-point shields
	 * Inactive) No shields
	 * 
	 * @return ShieldStatus of the ship's shields.
	 */
	public ShieldStatus getShieldStatus() {
		return this.shieldsStatus;
	}

	/**
	 * Checks to see if fire control is active.
	 * 
	 * @return True if fire control is active, false otherwise.
	 */
	public boolean isCapacitorsCharged() {
		return capacitorsCharged;
	}

	public void setCapacitorsCharged(boolean capacitorsCharged) {
		this.capacitorsCharged = capacitorsCharged;
	}

	public boolean isActiveFireControl() {
		return this.activeFireControl;
	}

	/** Directly set fire control state — used to sync client state from server. */
	public void setActiveFireControl(boolean active) {
		this.activeFireControl = active;
	}

	/// HULL BOXES ///

	public HullBoxes getHullBoxes() {
		return this.hullBoxes;
	}

	/// POWER SYSTEMS ///
	public PowerSystems getPowerSystems() {
		return powerSystems;
	}

	/// CONTROL SPACES ///

	// Create control boxes.
	public ControlSpaces getControlSpaces() {
		return this.controlSpaces;
	}

	/// SPECIAL FUNCTIONS ///
	public com.sfb.systems.SpecialFunctions getSpecialFunctions() {
		return this.specialFunctions;
	}

	/// SPECIAL FUNCITONS ///
	public int getScanner() {
		return this.specialFunctions.getScanner();
	}

	public boolean hasDerfacs() {
		return this.specialFunctions.hasDerfacs();
	}

	public boolean hasUim() {
		return this.specialFunctions.hasUim();
	}

	public int getControlLimit() {
		return this.specialFunctions.getControlLimit();
	}

	public int getControlUsed() {
		return this.specialFunctions.getControlUsed();
	}

	public boolean acquireControl(Seeker seeker) {
		return this.specialFunctions.acquireControl(seeker);
	}

	public void releaseControl(Seeker seeker) {
		this.specialFunctions.releaseControl(seeker);
	}

	/// TRANSPORTERS ///
	public Transporters getTransporters() {
		return this.transporters;
	}

	/// OPERATIONS SYSTEMS ///
	public Labs getLabs() {
		return this.labs;
	}

	/// PROBES ///
	public ProbeLaunchers getProbes() {
		return this.probes;
	}

	/// WEAPONS ///
	public Weapons getWeapons() {
		return this.weapons;
	}

	/// SHUTTLES ///

	// TODO: Shuttle operations
	public Shuttles getShuttles() {
		return this.shuttles;
	}

	/// CREW ///
	public Crew getCrew() {
		return this.crew;
	}

	/// PERFORMANCE DATA ///
	public PerformanceData getPerformanceData() {
		return this.performanceData;
	}

	/// CLOAKING DEVICE ///
	public CloakingDevice getCloakingDevice() {
		return this.cloak;
	}

	/// DERFACS ///
	public com.sfb.systemgroups.DERFACS getDerfacs() {
		return this.derfacs;
	}

	/// UIM ///

	/**
	 * Returns the active UIM for this impulse, or null if none are functional.
	 * Per D6.542, a standby UIM has an 8-impulse activation delay after burnout.
	 */
	public com.sfb.systemgroups.UIM getActiveUim(int currentImpulse) {
		for (com.sfb.systemgroups.UIM uim : uims) {
			if (uim.isFunctional(currentImpulse))
				return uim;
		}
		return null;
	}

	/**
	 * After a burnout, schedules the next undamaged standby UIM to activate
	 * after an 8-impulse delay (D6.542). The burned-out UIM is already marked
	 * damaged by {@link com.sfb.systemgroups.UIM#checkBurnout}.
	 *
	 * @param burned         The UIM that just burned out.
	 * @param currentImpulse The impulse at which burnout occurred.
	 */
	public void activateNextStandby(com.sfb.systemgroups.UIM burned, int currentImpulse) {
		int activateAt = currentImpulse + 8;
		for (com.sfb.systemgroups.UIM uim : uims) {
			if (uim == burned || uim.isDamaged())
				continue;
			// First undamaged non-burned UIM becomes the next standby
			uim.scheduleActivation(activateAt);
			return;
		}
	}

	public java.util.List<com.sfb.systemgroups.UIM> getUims() {
		return java.util.Collections.unmodifiableList(uims);
	}

	/**
	 * Rolls for HET breakdown, applies the HET if successful, and returns the
	 * effective roll (after bonus HET modifier). Game.performHet() uses the roll
	 * value for combat-log reporting; breakdown is indicated by
	 * roll >= breakdownChance (caller must then invoke applyBreakdown()).
	 */
	public int rollAndPerformHet(int absoluteFacing) {
		DiceRoller roller = new DiceRoller();
		int breakdownRoll = roller.rollOneDie();

		if (performanceData.getBonusHetsRemaining() > 0) {
			breakdownRoll -= 2;
			performanceData.useBonusHet();
		}

		if (breakdownRoll < performanceData.getBreakdownChance()) {
			super.performHet(absoluteFacing);
		}

		return breakdownRoll;
	}

	@Override
	public boolean performHet(int absoluteFacing) {
		return rollAndPerformHet(absoluteFacing) < performanceData.getBreakdownChance();
	}

	/**
	 * Apply all breakdown effects (C6.54). Call only when performHet() returns
	 * false.
	 * Returns the number of internal DAC hits to queue (always 2 per C6.5423).
	 */
	public int applyBreakdown(int currentImpulse) {
		// C6.541: stop ship, random facing, immobile for 16 impulses
		setSpeed(0);
		int randomFacing = new DiceRoller().rollOneDie() - 1; // 1–6 → 0–5
		super.performHet(randomFacing); // sets facing + resets turn/sideslip counts

		immobileUntilImpulse = currentImpulse + 16;
		breakdownLockoutUntilImpulse = currentImpulse + 8; // C6.547

		// C6.5421: kill 1/3 crew (round up)
		int crewLoss = (int) Math.ceil(getCrew().getAvailableCrewUnits() / 3.0);
		getCrew().setAvailableCrewUnits(Math.max(0, getCrew().getAvailableCrewUnits() - crewLoss));

		// C6.5422: destroy every 5th warp engine box (round down)
		int totalWarp = getPowerSystems().getAvailableLWarp()
				+ getPowerSystems().getAvailableRWarp()
				+ getPowerSystems().getAvailableCWarp();
		int toDestroy = totalWarp / 5;
		for (int i = 0; i < toDestroy; i++) {
			if (!getPowerSystems().damageLWarp())
				if (!getPowerSystems().damageRWarp())
					getPowerSystems().damageCWarp();
		}

		// C6.544: reduce breakdown rating by 1 (minimum 1)
		performanceData.decrementBreakdownRating();

		return 2; // C6.5423: 2 internal DAC hits, queued by Game
	}

	/**
	 * Calculate the total number of boxes on the ship SSD.
	 * 
	 * @return The number of boxes on the ship SSD
	 */
	private int getTotalSSDBoxes() {
		int totalBoxes = 0;
		totalBoxes += this.controlSpaces.fetchOriginalTotalBoxes();
		totalBoxes += this.powerSystems.fetchOriginalTotalBoxes();
		totalBoxes += this.hullBoxes.fetchOriginalTotalBoxes();
		totalBoxes += this.labs.fetchOriginalTotalBoxes();
		totalBoxes += this.tractors.fetchOriginalTotalBoxes();
		totalBoxes += this.probes.fetchOriginalTotalBoxes();
		totalBoxes += this.transporters.fetchOriginalTotalBoxes();
		totalBoxes += this.specialFunctions.getOriginalExcessDamage();
		totalBoxes += this.shuttles.fetchOriginalTotalBoxes();
		totalBoxes += this.weapons.fetchOriginalTotalBoxes();

		return totalBoxes;
	}

	/**
	 * Calculate the total number of remaining undamaged boxes on the ship.
	 * 
	 * @return The number of undamaged boxes on the ship.
	 */
	private int getCurrentBoxes() {
		int totalBoxes = 0;
		totalBoxes += this.controlSpaces.fetchRemainingTotalBoxes();
		totalBoxes += this.powerSystems.fetchRemainingTotalBoxes();
		totalBoxes += this.hullBoxes.fetchRemainingTotalBoxes();
		totalBoxes += this.labs.fetchRemainingTotalBoxes();
		totalBoxes += this.tractors.fetchRemainingTotalBoxes();
		totalBoxes += this.probes.fetchRemainingTotalBoxes();
		totalBoxes += this.transporters.fetchRemainingTotalBoxes();
		totalBoxes += this.specialFunctions.getExcessDamage();
		totalBoxes += this.shuttles.fetchRemainingTotalBoxes();
		totalBoxes += this.weapons.fetchRemainingTotalBoxes();

		return totalBoxes;
	}

	/**
	 * Find all weapons on the ship that have a chance of hitting the target.
	 * 
	 * @param target The target which weapons must hit.
	 * 
	 * @return A list of weapons with the right arcs and range to hit the target.
	 */
	public List<Weapon> fetchAllBearingWeapons(Unit target) {
		return weapons.fetchAllBearingWeapons(this, target);
	}

	/**
	 * Build a default Energy allocation for this ship suitable for use in
	 * testing and early gameplay. Allocates all warp/impulse power to movement,
	 * fills the phaser capacitor, activates shields and life support at full cost,
	 * and arms all heavy weapons with standard arming energy.
	 *
	 * This produces an Energy object that can be passed to allocateEnergy() and
	 * then startTurn(), replacing the old direct-mutation autoAllocate().
	 */
	public Energy buildAutoAllocation() {
		Energy e = new Energy();

		// Movement: warp up to speed 30, then 1 impulse point for speed 31
		double moveCost = performanceData.getMovementCost();
		double warpAvailable = powerSystems.getAvailableWarpPower();
		e.setWarpMovement(Math.min(warpAvailable, 30.0 * moveCost));
		// Use 1 impulse point for the extra hex only if impulse power is available
		e.setImpulseMovement(powerSystems.getAvailableImpulse() >= 1 ? 1 : 0);

		// Life support
		e.setLifeSupport(lifeSupportCost);

		// Fire control
		e.setFireControl(fireControlCost);

		// Shields — activate at full cost
		e.setActivateShields(activeShieldCost);

		// Phaser capacitor — top off only (capacitor carries over between turns)
		double capacitorNeeded = weapons.getAvailablePhaserCapacitor()
				- weapons.getPhaserCapacitorEnergy();
		e.setPhaserCapacitor(Math.max(0, capacitorNeeded));

		// Heavy weapon arming — standard arming energy for each weapon
		for (Weapon weapon : weapons.fetchAllWeapons()) {
			if (weapon instanceof HeavyWeapon) {
				e.getArmingEnergy().put(weapon, (double) Constants.gArmingCost[0]);
				e.getArmingType().put(weapon, com.sfb.properties.WeaponArmingType.STANDARD);
			}
		}

		return e;
	}

	/**
	 * Apply bleed-through damage via the DAC. Rolls 2d6 once per damage point,
	 * looks up the result on this ship's DAC, and damages the indicated system.
	 * After the volley the DAC is reset so "special" (underlined) items refresh.
	 *
	 * @param bleedThrough The number of damage points that penetrated the shields.
	 * @return A list of strings describing each system hit (for the combat log).
	 */
	private List<Weapon> bearingFunctionalPhasers(Ship attacker) {
		List<Weapon> all = weapons.getPhaserList().stream()
				.filter(Weapon::isFunctional).collect(Collectors.toList());
		if (attacker == null)
			return all;
		int trueBearing = MapUtils.getBearing(this, attacker);
		if (trueBearing == 0)
			return all; // same hex — all bear
		int relBearing = MapUtils.getRelativeBearing(trueBearing, getFacing());
		return all.stream().filter(w -> w.inArc(relBearing)).collect(Collectors.toList());
	}

	public List<String> applyInternalDamage(int bleedThrough) {
		return applyInternalDamage(bleedThrough, null);
	}

	public List<String> applyInternalDamage(int bleedThrough, Ship attacker) {
		List<String> log = new ArrayList<>();
		int absorbed = Math.min(armor, bleedThrough);
		armor -= absorbed;
		bleedThrough -= absorbed;
		DiceRoller roller = new DiceRoller();

		for (int i = 0; i < bleedThrough; i++) {
			internalDamagePointsTotal++;

			// G9.21: every 10th internal damage point kills 1 crew unit
			int killThreshold = internalDamagePointsTotal / 10;
			int newCrewKills = killThreshold - crewKilledByInternalDamage;
			if (newCrewKills > 0) {
				crewKilledByInternalDamage += newCrewKills;
				int current = getCrew().getAvailableCrewUnits();
				int actual = Math.min(newCrewKills, Math.max(0, current - 1)); // G9.22: last crew unit protected
				if (actual > 0) {
					getCrew().setAvailableCrewUnits(current - actual);
					log.add("  crew casualty: " + actual + " crew unit(s) lost to internal damage (G9.21)");
				}
			}

			// D7.21: every 10th point also generates a BP casualty; first 4 events ignored,
			// last 2 BPs protected.
			// Casualties are normal parties first, commandos last. Floor applies only to
			// internal damage;
			// boarding combat and H&R raids can kill the last 2 BPs freely.
			// TODO: multi-faction support (D7.21 para 2) — requires per-faction BP tracking
			// on the defending
			// ship, which the data model does not yet support.
			int effectiveBpEvents = Math.max(0, killThreshold - 4);
			int newBpEvents = effectiveBpEvents - bpCasualtiesProcessed;
			if (newBpEvents > 0) {
				bpCasualtiesProcessed += newBpEvents;
				int currentBp = getCrew().getAvailableBoardingParties();
				int actual = Math.min(newBpEvents, Math.max(0, currentBp - 2)); // last 2 BPs protected
				if (actual > 0) {
					getCrew().getFriendlyTroops().removeCasualties(actual);
					log.add("  boarding party casualty: " + actual + " BP(s) lost to internal damage (D7.21)");
				}
			}

			int roll = roller.rollTwoDice();
			String system = dac.fetchNextHit(roll);

			if (system == null) {
				boolean boxRemaining = specialFunctions.damageExcessDamage();
				if (!boxRemaining) {
					battleStatus = com.sfb.properties.BattleStatus.DESTROYED;
					log.add("  internal [" + roll + "]: excess damage — SHIP DESTROYED");
					break;
				}
				log.add(
						"  internal [" + roll + "]: excess damage (" + specialFunctions.getExcessDamage() + " boxes remaining)");
				continue;
			}

			// Try to apply damage; if that system has no boxes, advance the DAC (C3.14).
			java.util.Set<String> tried = new java.util.HashSet<>();
			StringBuilder chain = new StringBuilder();
			String hitLabel = null;
			while (hitLabel == null) {
				hitLabel = tryApplySystemHit(system, attacker);
				if (hitLabel == null) {
					tried.add(system);
					chain.append(system).append(" (no boxes) → ");
					String next = dac.fetchNextHitExcludingAll(roll, tried);
					if (next == null)
						break; // all entries exhausted — damage wasted
					system = next;
				}
			}
			if (hitLabel != null) {
				log.add("  internal [" + roll + "]: " + chain + hitLabel);
			} else {
				log.add("  internal [" + roll + "]: " + chain + system + " (wasted)");
			}
		}

		dac.reset();
		return log;
	}

	/**
	 * Attempts to apply one internal damage point to the named system.
	 * Returns the log label (e.g. "fhull HIT") on success, or null if the system
	 * has no remaining boxes (caller should advance the DAC and retry).
	 */
	private String tryApplySystemHit(String system, Ship attacker) {
		switch (system) {
			case "bridge":
				return controlSpaces.damageBridge() ? "bridge HIT" : null;
			case "flag":
				return controlSpaces.damageFlag() ? "flag HIT" : null;
			case "emer":
				return controlSpaces.damageEmer() ? "emer HIT" : null;
			case "auxcon":
				return controlSpaces.damageAuxcon() ? "auxcon HIT" : null;
			case "lwarp":
				return powerSystems.damageLWarp() ? "lwarp HIT" : null;
			case "rwarp":
				return powerSystems.damageRWarp() ? "rwarp HIT" : null;
			case "cwarp":
				return powerSystems.damageCWarp() ? "cwarp HIT" : null;
			case "impulse":
				return powerSystems.damageImpulse() ? "impulse HIT" : null;
			case "apr":
				return powerSystems.damageApr() ? "apr HIT" : null;
			case "battery":
				return powerSystems.damageBattery() ? "battery HIT" : null;
			case "scanner":
				return specialFunctions.damageScanner() ? "scanner HIT" : null;
			case "sensor":
				return specialFunctions.damageSensor() != null ? "sensor HIT" : null;
			case "damcon":
				return specialFunctions.damageDamCon() ? "damcon HIT" : null;
			case "cargo":
				return hullBoxes.damageCargo() ? "cargo HIT" : null;
			case "fhull": {
				boolean fAvail = hullBoxes.getAvailableFhull() > 0;
				if (!hullBoxes.damageFhull())
					return null;
				return fAvail ? "fhull HIT" : "chull (fhull exhausted) HIT";
			}
			case "ahull":
			case "afthull": {
				boolean aAvail = hullBoxes.getAvailableAhull() > 0;
				if (!hullBoxes.damageAhull())
					return null;
				return aAvail ? system + " HIT" : "chull (ahull exhausted) HIT";
			}
			case "phaser": {
				List<Weapon> candidates = bearingFunctionalPhasers(attacker);
				if (candidates.isEmpty())
					return null;
				Weapon target = candidates.get(new Random().nextInt(candidates.size()));
				target.damage();
				weapons.recalculatePhaserCapacitor();
				return "phaser HIT (" + target.getName() + ")";
			}
			case "drone": {
				Weapon target = weapons.getDroneList().stream()
						.filter(Weapon::isFunctional).findFirst().orElse(null);
				if (target == null)
					return null;
				target.damage();
				return "drone HIT (" + target.getName() + ")";
			}
			case "torp":
			case "weapon": {
				Weapon target = weapons.fetchAllWeapons().stream()
						.filter(Weapon::isFunctional).findFirst().orElse(null);
				if (target == null)
					return null;
				target.damage();
				return system + " HIT (" + target.getName() + ")";
			}
			default:
				return null;
		}
	}

	/// PHASER CAPACITORS ///
	public void drainCapacitor(double energy) throws CapacitorException {
		this.weapons.drainPhaserCapacitor(energy);
	}

	public void chargeCapacitor(double energy) throws CapacitorException {
		this.weapons.chargePhaserCapacitor(energy);
	}

	/**
	 * True if this ship has taken at least one internal damage point (used for
	 * victory point scoring).
	 */
	public boolean isDamaged() {
		return internalDamagePointsTotal > 0;
	}

	public boolean movesThisImpulse(int impulse) {
		return ImpulseUtil.doesMove(impulse, getSpeed());
	}

	/**
	 * Another unit attempts to tractor this ship.
	 * 
	 * @param energy         The amount of energy applied to the tractor attempt.
	 * 
	 * @param tractoringUnit The unit attempting to tractor this ship.
	 * 
	 * @return True if the attempt is successful, false otherwise.
	 */
	@Override
	public boolean applyTractor(int energy, Unit tractoringUnit) {
		if (energy > this.tractors.getNegativeTractorEnergy()) {
			// TODO: Probably a tractor auction?

			setTractoringUnit(tractoringUnit);
			setTractored(true);
			return true;
		} else {
			return false;
		}
	}

	// Return the JSON string of the Unit object
	@Override
	public String toString() {

		String outputString = null;
		ObjectMapper mapper = new ObjectMapper();

		try {
			// mapper.writeValue(new File("e:\\ship.txt"), this);

			outputString = mapper.writeValueAsString(this);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return outputString;

		// String jsonOutput = null;
		// ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
		// try {
		// jsonOutput = ow.writeValueAsString(this);
		// } catch (JsonGenerationException e) {
		// e.printStackTrace();
		// } catch (JsonMappingException e) {
		// e.printStackTrace();
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		//
		// return jsonOutput;
	}
}
