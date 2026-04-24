package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

public class PowerSystems implements Systems {

	// Base values for the unit
	private int lwarp = 0;
	private int rwarp = 0;
	private int cwarp = 0;
	private int impulse = 0;
	private int apr = 0;
	private int awr = 0;
	private int battery = 0;
	
	// Values adjusted for any damage
	private int availableLwarp = 0;
	private int availableRwarp = 0;
	private int availableCwarp = 0;
	private int availableImpulse = 0;
	private int availableApr = 0;
	private int availableAwr = 0;
	private int availableBattery = 0;
	
	// Variable power settings
	private int batteryPower = 0;
	private int reserveWarp = 0;
	private int reservePower = 0;
	
	private Unit owningUnit = null;
	
	public PowerSystems() {
		
	}
	
	public PowerSystems(Unit owner) {
		this.owningUnit = owner;
	}
	
	// Pass in mapping of all the power values
	// The mapping will have String keys and Integer values.
	// Acceptable keys are: lwarp, rwarp, cwarp, impulse, apr, awr, battery
	@Override
	public void init(Map<String, Object> values) {
		// If map has matching value, get it. Otherwise set to 0.
		availableLwarp   = lwarp   = values.get("lwarp")   == null ? 0: (Integer)values.get("lwarp"); 
		availableRwarp   = rwarp   = values.get("rwarp")   == null ? 0: (Integer)values.get("rwarp"); 
		availableCwarp   = cwarp   = values.get("cwarp")   == null ? 0: (Integer)values.get("cwarp"); 
		availableImpulse = impulse = values.get("impulse") == null ? 0: (Integer)values.get("impulse"); 
		availableApr     = apr     = values.get("apr")     == null ? 0: (Integer)values.get("apr"); 
		availableAwr     = awr     = values.get("awr")     == null ? 0: (Integer)values.get("awr"); 
		availableBattery = battery = values.get("battery") == null ? 0: (Integer)values.get("battery");
		batteryPower = availableBattery; // Batteries start fully charged

	}
	
	////////////////////////////////
	//
	// For now, commenting the base getters/setters out. Not sure if I want any method
	// to set these values other than the init() method.
	//
	///////////////////////////////
	
//	public int getLwarp() {
//		return lwarp;
//	}
//	
//	public void setLwarp(int lwarp) {
//		this.lwarp = lwarp;
//	}
//	
//	public int getRwarp() {
//		return rwarp;
//	}
//	
//	public void setRwarp(int rwarp) {
//		this.rwarp = rwarp;
//	}
//	
//	public int getCwarp() {
//		return cwarp;
//	}
//	
//	public void setCwarp(int cwarp) {
//		this.cwarp = cwarp;
//	}
//	
//	public int getImpulse() {
//		return impulse;
//	}
//	
//	public void setImpulse(int impulse) {
//		this.impulse = impulse;
//	}
//	
//	public int getApr() {
//		return apr;
//	}
//	
//	public void setApr(int apr) {
//		this.apr = apr;
//	}
//	
//	public int getAwr() {
//		return awr;
//	}
//	
//	public void setAwr(int awr) {
//		this.awr = awr;
//	}
//	
//	public int getBattery() {
//		return battery;
//	}
//	
//	public void setBattery(int battery) {
//		this.battery = battery;
//	}
	
	//////////////////////////////////////
	//
	// Available systems (undestroyed boxes)
	//
	/////////////////////////////////////
	
	public int getAvailableLWarp() { return availableLwarp; }
	public int getAvailableRWarp() { return availableRwarp; }
	public int getAvailableCWarp() { return availableCwarp; }
	public int getOriginalWarp()   { return lwarp + rwarp + cwarp; }
	public int getRemainingWarp()  { return availableLwarp + availableRwarp + availableCwarp; }
	public int getMaxLWarp()       { return lwarp; }
	public int getMaxRWarp()       { return rwarp; }
	public int getMaxCWarp()       { return cwarp; }
	public int getMaxImpulse()     { return impulse; }
	public int getMaxApr()         { return apr; }
	public int getMaxAwr()         { return awr; }

	// Setters for client-side sync from server state
	public void setAvailableLWarp(int v)   { availableLwarp   = Math.max(0, Math.min(v, lwarp)); }
	public void setAvailableRWarp(int v)   { availableRwarp   = Math.max(0, Math.min(v, rwarp)); }
	public void setAvailableCWarp(int v)   { availableCwarp   = Math.max(0, Math.min(v, cwarp)); }
	public void setAvailableImpulse(int v) { availableImpulse = Math.max(0, Math.min(v, impulse)); }
	public void setAvailableBattery(int v) { availableBattery = Math.max(0, Math.min(v, battery)); }

	public int getAvailableApr() {
		return availableApr;
	}

	public int getAvailableAwr() {
		return availableAwr;
	}

	public int getAvailableImpulse() {
		return availableImpulse;
	}

	public int getAvailableBattery() {
		return availableBattery;
	}
	
	/////////////////////////////////////
	//
	// Adjustable power settings
	//
	/////////////////////////////////////

	public int getBatteryPower() {
		return batteryPower;
	}

	public boolean setBatteryPower(int batteryPower) {
		if (batteryPower > availableBattery) {
			return false;
		}
		
		this.batteryPower = batteryPower;
		return true;
	}
	
	public int getReserveWarp() {
		return reserveWarp;
	}
	
	public void setReserveWarp(int reserveWarp) {
		this.reserveWarp = Math.max(0, reserveWarp);
	}
	
	public int getReservePower() {
		return reservePower;
	}
	
	public boolean setReservePower(int reservePower) {
		if (reservePower > availableBattery) {
			return false;
		}
		
		this.reservePower = reservePower;
		return true;
	}
	
	//////////
	// utility
	/////////
	
	public int getPower() {
		return getTotalAvailablePower();
	}
	
	public int getTotalAvailablePower() {
		
		return availableLwarp + availableRwarp + availableCwarp + availableImpulse + availableApr + availableAwr;
	}
	
	public int getAvailableWarpPower() {
		return availableLwarp + availableRwarp + availableCwarp + availableAwr;
	}
	
	public int getWarpEnginePower() {
		return availableLwarp + availableRwarp + availableCwarp;
	}
	
	public int getAvailableReactorPower() {
		return availableApr;
	}
	
	// Get current number of power boxes (cripple calculations)
	@Override
	public int fetchRemainingTotalBoxes() {
		return availableLwarp + availableRwarp + availableCwarp + availableImpulse + availableApr + availableAwr + availableBattery;
	}
	
	// Get original number of SSD power boxes (cripple calculations)
	@Override
	public int fetchOriginalTotalBoxes() {
		return lwarp + rwarp + cwarp + impulse + apr + awr + battery;
	}
	
	// Removes the specified amount from battery power.
	// If the power requested doesn't exceed the 
	// available battery power, return true.
	// Otherwise return false.
	public boolean useBattery(int amount) {
		int remainingBattery = this.batteryPower - amount;
		
		if (remainingBattery >= 0) {
			this.batteryPower = remainingBattery;
			return true;
		} else {
			return false;
		}
	}
	
	public boolean useReserveWarp(int amount) {
		if (reserveWarp < amount) {
			return false;
		}
		
		reserveWarp -= amount;
		return true;
	}
	
	public boolean useReservePower(int amount) {
		if (reservePower < amount) {
			return false;
		}
		reservePower -= amount;
		return true;
	}
	
	public boolean chargeBattery(int amount) {
		int finalBatteryPower = this.batteryPower + amount;
		
		if (finalBatteryPower > this.availableBattery) {
			return false;
		}
		
		this.batteryPower = finalBatteryPower;
		return true;
	}
	
	///////////////////////////////////////////
	//
	// DAMAGE SYSTEMS
	//
	///////////////////////////////////////////
	
	public boolean damageLWarp() {
		if (availableLwarp < 1) {
			return false;
		}
		
		availableLwarp--;
		return true;
	}

	public boolean damageRWarp() {
		if (availableRwarp < 1) {
			return false;
		}
		
		availableRwarp--;
		return true;
	}

	public boolean damageCWarp() {
		if (availableCwarp < 1) {
			return false;
		}
		
		availableCwarp--;
		return true;
	}
	
	public boolean damageImpulse() {
		if (availableImpulse < 1) {
			return false;
		}
		
		availableImpulse--;
		return true;
	}
	
	public boolean damageAwr() {
		if (availableAwr < 1) {
			return false;
		}
		
		availableAwr--;
		return true;
	}
	
	public boolean damageApr() {
		if (availableApr < 1) {
			return false;
		}
		
		availableApr--;
		return true;
	}
	
	// If there is still a battery, destroy one and return true.
	// Otherwise return false.
	public boolean damageBattery() {
		if (availableBattery < 1) {
			return false;
		}

		// Remove a battery from the ship
		availableBattery--;
		
		// if all batteries were charged, 
		// lose 1 point of battery power.
		if (batteryPower > availableBattery) {
			batteryPower = availableBattery;
		}
		
		return true;
	}
	
	///////////////////////////////////////////
	//
	// REPAIR SYSTEMS
	//
	///////////////////////////////////////////
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairLwarp(int amount) {
		if (availableLwarp + amount > lwarp) {
			return false;
		}
		
		availableLwarp += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairRwarp(int amount) {
		if (availableRwarp + amount > rwarp) {
			return false;
		}
		
		availableRwarp += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairCwarp(int amount) {
		if (availableCwarp + amount > cwarp) {
			return false;
		}
		
		availableCwarp += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairImpulse(int amount) {
		if (availableImpulse + amount > impulse) {
			return false;
		}
		
		availableImpulse += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairApr(int amount) {
		if (availableApr + amount > apr) {
			return false;
		}
		
		availableApr += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairAwr(int amount) {
		if (availableAwr + amount > awr) {
			return false;
		}
		
		availableAwr += amount;
		return true;
	}
	
	// If the repairs wouldn't exceed the original
	// number of boxes, proceed.
	public boolean repairBattery(int amount) {
		if (availableBattery + amount > battery) {
			return false;
		}
		
		availableBattery += amount;
		return true;
	}

	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}
	
}
