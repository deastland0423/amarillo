package com.sfb.weapons;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;

public class Phaser1Test {

	@Test
	public void testFire() throws TargetOutOfRangeException, WeaponUnarmedException {
		Phaser1 phaser1 = new Phaser1();

		int range = 15;

		int damage = 0;
		try {
			damage = phaser1.fire(range);
		} catch (CapacitorException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Damage: " + damage);
		
		assertEquals(phaser1.getDacHitLocaiton(), "phaser");
	}

}
