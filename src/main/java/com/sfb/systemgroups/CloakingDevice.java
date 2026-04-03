package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

public class CloakingDevice implements Systems {

	int powerToActivate = 0;
	int fadeInImpulses = 0;
	int fadeOutImpulses = 0;
	boolean active = false;

	private Unit owningUnit = null;

	public CloakingDevice() {

	}

	public CloakingDevice(Unit owner) {
		this.owningUnit = owner;
	}

	@Override
	public void init(Map<String, Object> values) {
		// TODO Auto-generated method stub

	}

	@Override
	public int fetchOriginalTotalBoxes() {
		return 1;
	}

	@Override
	public int fetchRemainingTotalBoxes() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void cleanUp() {
		// TODO Auto-generated method stub

	}

	@Override
	public Unit fetchOwningUnit() {
		return this.owningUnit;
	}

	public int getPowerToActivate() {
		return this.powerToActivate;
	}

	public boolean isActive() {
		return this.active;
	}
}
