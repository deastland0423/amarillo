package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

public class Transporters implements Systems {

	private static final double ENERGY_PER_USE = 0.2;

	private int    trans;
	private int    availableTrans;
	private double bankedEnergy   = 0.0; // energy allocated this turn, ready to spend
	private double energyUsed     = 0.0; // energy spent this turn

	private Unit owningShip;
	
	public Transporters(Unit owner) {
		this.owningShip = owner;
	}
	
	@Override
	public void init(Map<String, Object> values) {
		availableTrans = trans = values.get("trans") == null ? 0 : (Integer)values.get("trans");
		
	}

	public int getAvailableTrans() {
		return availableTrans;
	}

	/** Energy cost per transporter use. */
	public static double energyPerUse() {
		return ENERGY_PER_USE;
	}

	/** Bank energy allocated at the start of the turn (or drawn mid-turn from batteries). */
	public void bankEnergy(double energy) {
		bankedEnergy += energy;
	}

	/** How many uses remain given currently banked energy. */
	public int availableUses() {
		if (availableTrans == 0) return 0;
		return (int) ((bankedEnergy - energyUsed) / ENERGY_PER_USE);
	}

	/** Spend energy for one transporter use. Returns false if insufficient energy or no working transporters. */
	public boolean useTransporter() {
		if (availableTrans == 0) return false;
		if (bankedEnergy - energyUsed < ENERGY_PER_USE) return false;
		energyUsed += ENERGY_PER_USE;
		return true;
	}

	/** Energy banked this turn but not yet spent. */
	public double getBankedEnergy() {
		return bankedEnergy - energyUsed;
	}
	
	@Override
	public int fetchOriginalTotalBoxes() {
		return trans;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		return availableTrans;
	}

	public boolean damage() {
		if (availableTrans == 0) {
			return false;
		}
		
		availableTrans++;
		return true;
	}
	
	public boolean repair(int numberToRepair) {
		if (availableTrans + numberToRepair > trans) {
			return false;
		}
		
		availableTrans += numberToRepair;
		return true;
	}
	
	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningShip;
	}

}
