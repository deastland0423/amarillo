package com.sfb.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Weapon;

public class AdminShuttle extends Shuttle {

	@Override public boolean canBecomeSuicide()     { return true; }
	@Override public boolean canBecomeScatterPack() { return true; }
	@Override public boolean canBecomeWildWeasel()  { return true; }

	// Wild Weasel charging state (J3.12): 0 = uncharged, 1 = primed, 2 = ready
	private int wwChargeCount = 0;

	public int  getWwChargeCount()  { return wwChargeCount; }
	public boolean isWwReady()      { return wwChargeCount >= 2; }
	public void incrementWwCharge() { if (wwChargeCount < 2) wwChargeCount++; }
	public void resetWwCharge()     { wwChargeCount = 0; }

	public AdminShuttle() {
		setHull(6);
		setMaxSpeed(6);

		// Create a phaser 3 (360 arc) and put it with the shuttle weapons.
		Phaser3 phaser1 = new Phaser3();
		phaser1.setDesignator("1");
		phaser1.setArcs(ArcUtils.FULL);
		List<Weapon> weaponList = new ArrayList<>();
		weaponList.add(phaser1);
		Map<String, Object> values = new HashMap<String, Object>();
		values.put("weapons", weaponList);

		getWeapons().init(values);
	}

}
