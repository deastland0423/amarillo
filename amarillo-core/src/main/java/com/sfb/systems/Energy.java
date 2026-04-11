package com.sfb.systems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Drone;
import com.sfb.properties.WeaponArmingType;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * This will be the energy allocation for a ship. It will indicate where all power generated is to be sent.
 * 
 * @author Daniel Eastland
 *
 */
public class Energy {

	// Housekeeping
	private double lifeSupport;
	private double fireControl;
	private double activateShields;
	
	// Shields
	private int generalReinforcement;
	private int[] specificReinforcement = new int[6];
	
	// Movement
	// Warp energy allocated to movement: each moveCost energy = 1 speed (max 30).
	private double warpMovement;
	// Impulse points allocated to movement: 1 point = 1 extra speed hex, flat cost
	// regardless of moveCost (allows speed 31).
	private int    impulseMovement;
	private double highEnergyTurns;
	private double erraticManuvers;
	private double warpTacticalTurns;
	private int    impulseTacticalTurn;
	
	// Phasers
	private double phaserCapacitor;
	
	// Probes
	private int probes;
	
	// Tractors
	private int tractors;

	// Operations
	private double transporters;
	private int damageControl;
	private boolean cloakPaid;      // true if the player paid the cloak cost this turn
	private boolean energizeCaps;   // true if the player paid 1 pt to energize uncharged capacitors (WS-0)

	// Batteries
	private int batteryDraw;       // Energy drawn FROM batteries this turn (adds to effective budget)
	private int batteryRecharge;   // Energy put INTO batteries this turn (costs from ship power)
	
	// Weapons Map with <Weapon, Energy for Weapon>
	Map<Weapon, Double> armingEnergy = new HashMap<>();
	Map<Weapon, WeaponArmingType> armingType = new HashMap<>();

	// Drone reload assignments: which reload set to load into each rack this turn.
	Map<DroneRack, List<Drone>> reloadAssignments = new HashMap<>();

	public Energy() {
		
	}

	public double getLifeSupport() {
		return lifeSupport;
	}

	public void setLifeSupport(double lifeSupport) {
		this.lifeSupport = lifeSupport;
	}

	public double getFireControl() {
		return fireControl;
	}

	public void setFireControl(double fireControl) {
		this.fireControl = fireControl;
	}

	public double getActivateShields() {
		return activateShields;
	}

	public void setActivateShields(double activateShields) {
		this.activateShields = activateShields;
	}

	public int getGeneralReinforcement() {
		return generalReinforcement;
	}

	public void setGeneralReinforcement(int generalReinforcement) {
		this.generalReinforcement = generalReinforcement;
	}

	public int[] getSpecificReinforcement() {
		return specificReinforcement;
	}

	public void setSpecificReinforcement(int[] specificReinforcement) {
		this.specificReinforcement = specificReinforcement;
	}

	public double getWarpMovement() {
		return warpMovement;
	}

	public void setWarpMovement(double warpMovement) {
		this.warpMovement = warpMovement;
	}

	public int getImpulseMovement() {
		return impulseMovement;
	}

	public void setImpulseMovement(int impulseMovement) {
		this.impulseMovement = impulseMovement;
	}

	public double getHighEnergyTurns() {
		return highEnergyTurns;
	}

	public void setHighEnergyTurns(double highEnergyTurns) {
		this.highEnergyTurns = highEnergyTurns;
	}

	public double getErraticManuvers() {
		return erraticManuvers;
	}

	public void setErraticManuvers(double erraticManuvers) {
		this.erraticManuvers = erraticManuvers;
	}

	public double getWarpTacticalTurns() {
		return warpTacticalTurns;
	}

	public void setWarpTacticalTurns(double warpTacticalTurns) {
		this.warpTacticalTurns = warpTacticalTurns;
	}

	public int getImpulseTacticalTurn() {
		return impulseTacticalTurn;
	}

	public void setImpulseTacticalTurn(int impulseTacticalTurn) {
		this.impulseTacticalTurn = impulseTacticalTurn;
	}

	public double getPhaserCapacitor() {
		return phaserCapacitor;
	}

	public void setPhaserCapacitor(double phaserCapacitor) {
		this.phaserCapacitor = phaserCapacitor;
	}

	public int getProbes() {
		return probes;
	}

	public void setProbes(int probes) {
		this.probes = probes;
	}

	public double getTransporters() {
		return transporters;
	}

	public void setTransporters(double transporters) {
		this.transporters = transporters;
	}

	public int getTractors() {
		return tractors;
	}

	public void setTractors(int tractors) {
		this.tractors = tractors;
	}

	public int getDamageControl() {
		return damageControl;
	}

	public void setDamageControl(int damageControl) {
		this.damageControl = damageControl;
	}

	public int getBatteryDraw() {
		return batteryDraw;
	}

	public void setBatteryDraw(int batteryDraw) {
		this.batteryDraw = batteryDraw;
	}

	public int getBatteryRecharge() {
		return batteryRecharge;
	}

	public void setBatteryRecharge(int batteryRecharge) {
		this.batteryRecharge = batteryRecharge;
	}

	public boolean isCloakPaid() {
		return cloakPaid;
	}

	public void setCloakPaid(boolean cloakPaid) {
		this.cloakPaid = cloakPaid;
	}

	public boolean isEnergizeCaps() {
		return energizeCaps;
	}

	public void setEnergizeCaps(boolean energizeCaps) {
		this.energizeCaps = energizeCaps;
	}

	public Map<Weapon, Double> getArmingEnergy() {
		return armingEnergy;
	}

	public void setArmingEnergy(Map<Weapon, Double> armingEnergy) {
		this.armingEnergy = armingEnergy;
	}

	public Map<Weapon, WeaponArmingType> getArmingType() {
		return armingType;
	}

	public void setArmingType(Map<Weapon, WeaponArmingType> armingType) {
		this.armingType = armingType;
	}

	public Map<DroneRack, List<Drone>> getReloadAssignments() {
		return reloadAssignments;
	}
}
