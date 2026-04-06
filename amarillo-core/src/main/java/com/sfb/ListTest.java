package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.weapons.Disruptor;
import com.sfb.weapons.Weapon;

public class ListTest {
	
	List<Weapon> weaponList1 = new ArrayList<>();
	List<Weapon> weaponList2 = new ArrayList<>();

	// Verify that lists of objects are all pointers to the same "physical" object.
	public static void main(String[] args) {
		
		ListTest listTest = new ListTest();
		Disruptor disrA = new Disruptor(30);
		Disruptor disrB = new Disruptor(30);
	
		listTest.weaponList1.add(disrA);
		listTest.weaponList1.add(disrB);
		
		listTest.weaponList2.add(disrA);
		listTest.weaponList2.add(disrB);
		
		// Get the disruptor's status from List 2
		System.out.println(listTest.weaponList2.get(0).isFunctional());

		// Damage the disruptor in list 1
		listTest.weaponList1.get(0).damage();
		
		// Verify that the disruptor is damaged in List 2
		System.out.println(listTest.weaponList2.get(0).isFunctional());
		
		// Repair the diruptor in list 1
		listTest.weaponList1.get(0).repair();

		// Verify that the disruptor is functional in List 2
		System.out.println(listTest.weaponList2.get(0).isFunctional());
	}
}
