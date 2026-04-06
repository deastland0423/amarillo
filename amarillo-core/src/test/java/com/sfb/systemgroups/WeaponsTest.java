package com.sfb.systemgroups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.sfb.objects.Unit;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Weapon;

public class WeaponsTest {

	@Test
	public void testWeapons() {
		
		Map<String, Object> values = new HashMap<>();
		values.put("weapons", getWeaponList());

		Weapons weapons = new Weapons(new Unit());
		weapons.init(values);
		
		assertTrue(weapons.weapons.size() > 0);
		
		// 3 Phaser1 and 6 Phaser2 means a capacitor size of 9
		assertEquals(9, weapons.getAvailablePhaserCapacitor(), 0.24);
		
		// There are 4 torpedoes (disruptors)
		assertEquals(4, weapons.getAvailableTorps());
		
	}

	
	// Weapons on a D7 (no drones yet)
	public List<Weapon> getWeaponList() {
		List<Weapon> weaponList = new ArrayList<>();
		
		// Boom Phasers
		Phaser1 phaser1 = new Phaser1();
		Phaser1 phaser2 = new Phaser1();
		Phaser1 phaser3 = new Phaser1();
		int boomArc = ArcUtils.FH | ArcUtils.RS | ArcUtils.of(13);
		phaser1.setArcs(boomArc);
		phaser2.setArcs(boomArc);
		phaser3.setArcs(boomArc);
		weaponList.add(phaser1);
		weaponList.add(phaser2);
		weaponList.add(phaser3);

		// Wing Phasers
		Phaser2 phaser4 = new Phaser2();
		phaser4.setArcs(ArcUtils.L | ArcUtils.LF | ArcUtils.RR | ArcUtils.of(5));
		weaponList.add(phaser4);
		Phaser2 phaser5 = new Phaser2();
		phaser5.setArcs(ArcUtils.RF | ArcUtils.R | ArcUtils.LR | ArcUtils.of(21));
		weaponList.add(phaser5);

		// Waist Phasers
		Phaser2 phaser6 = new Phaser2();
		phaser6.setArcs(ArcUtils.L | ArcUtils.LR);
		weaponList.add(phaser6);
		Phaser2 phaser7 = new Phaser2();
		phaser7.setArcs(ArcUtils.L | ArcUtils.LR);
		weaponList.add(phaser7);
		Phaser2 phaser8 = new Phaser2();
		phaser8.setArcs(ArcUtils.R | ArcUtils.RR);
		weaponList.add(phaser8);
		Phaser2 phaser9 = new Phaser2();
		phaser9.setArcs(ArcUtils.R | ArcUtils.RR);
		weaponList.add(phaser9);

		// Distruptors
		Disruptor disruptorA = new Disruptor(30);
		disruptorA.setArcs(ArcUtils.FA);
		Disruptor disruptorB = new Disruptor(30);
		disruptorB.setArcs(ArcUtils.FA);
		Disruptor disruptorC = new Disruptor(30);
		disruptorC.setArcs(ArcUtils.FA);
		Disruptor disruptorD = new Disruptor(30);
		disruptorD.setArcs(ArcUtils.FA);
		weaponList.add(disruptorA);
		weaponList.add(disruptorB);
		weaponList.add(disruptorC);
		weaponList.add(disruptorD);
		
		// Drones
		
		
		return weaponList;
	}

}
