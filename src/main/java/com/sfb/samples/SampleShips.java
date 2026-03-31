package com.sfb.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Drone;
import com.sfb.objects.Drone.DroneType;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Photon;
import com.sfb.weapons.Weapon;

/**
 * This object will have static methods that return the maps
 * used to initialize ships.
 * 
 * @author deastland
 *
 */
public class SampleShips {

	/**
	 * This map represents a Federation Heavy Cruiser (Constitution Class)
	 * 
	 * @return A map with all the data for a Fed CA
	 */
	public static Map<String, Object> getFedCa() {
		Map<String, Object> shipSpecs = new HashMap<String, Object>();

		// Ship basics
		shipSpecs.put("faction", Faction.Federation);
		shipSpecs.put("hull", "CA");
		shipSpecs.put("name", "USS Lexington");
		shipSpecs.put("serviceyear", new Integer(130));
		shipSpecs.put("bpv", new Integer(125));
		shipSpecs.put("turnmode", TurnMode.D);
		shipSpecs.put("sizeclass", new Integer(3));

		// Shields
		shipSpecs.put("shield1", new Integer(30));
		shipSpecs.put("shield2", new Integer(24));
		shipSpecs.put("shield3", new Integer(20));
		shipSpecs.put("shield4", new Integer(20));
		shipSpecs.put("shield5", new Integer(20));
		shipSpecs.put("shield6", new Integer(24));

		// Hull
		shipSpecs.put("fhull", new Integer(12));
		shipSpecs.put("ahull", new Integer(4));

		// Power
		shipSpecs.put("lwarp", new Integer(15));
		shipSpecs.put("rwarp", new Integer(15));
		shipSpecs.put("impulse", new Integer(4));
		shipSpecs.put("battery", new Integer(3));

		// Control
		shipSpecs.put("bridge", new Integer(2));
		shipSpecs.put("emer", new Integer(2));
		shipSpecs.put("auxcon", new Integer(2));

		// Special Functions
		shipSpecs.put("damcon", new int[] { 4, 4, 2, 2, 0 });
		shipSpecs.put("scanner", new int[] { 0, 0, 1, 3, 5, 9 });
		shipSpecs.put("sensor", new int[] { 6, 6, 5, 3, 1, 0 });
		shipSpecs.put("excess", new Integer(6));
		shipSpecs.put("controlmod", new Double(0.5)); // Multiplier for control channels

		// Operations
		shipSpecs.put("trans", new Integer(3));
		shipSpecs.put("tractor", new Integer(3));
		shipSpecs.put("lab", new Integer(8));

		// Probes
		shipSpecs.put("probe", new Integer(1));

		// Shuttles
		shipSpecs.put("shuttle", new Integer(4));

		// Crew
		shipSpecs.put("crew", new Integer(43));
		shipSpecs.put("boardingparties", new Integer(10));
		shipSpecs.put("minimumcrew", new Integer(4));

		// Performance
		shipSpecs.put("movecost", new Double(1));
		shipSpecs.put("breakdown", new Integer(5));
		shipSpecs.put("bonushets", new Integer(1));

		// Weapons
		List<Weapon> weaponList = new ArrayList<>();

		// Fore Phasers (FH)
		Phaser1 phaser1 = new Phaser1();
		phaser1.setArcs(new int[] { 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7 });
		phaser1.setDesignator("1");
		weaponList.add(phaser1);
		Phaser1 phaser2 = new Phaser1();
		phaser2.setArcs(new int[] { 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7 });
		phaser2.setDesignator("2");
		weaponList.add(phaser2);

		// Left Phasers (LF + L + directly aft)
		Phaser1 phaser3 = new Phaser1();
		phaser3.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 13 });
		phaser3.setDesignator("3");
		weaponList.add(phaser3);
		Phaser1 phaser4 = new Phaser1();
		phaser4.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 13 });
		phaser4.setDesignator("4");
		weaponList.add(phaser4);

		// RH Phasers (RF + R + directly aft)
		Phaser1 phaser5 = new Phaser1();
		phaser5.setArcs(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser5.setDesignator("5");
		weaponList.add(phaser5);
		Phaser1 phaser6 = new Phaser1();
		phaser6.setArcs(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser6.setDesignator("6");
		weaponList.add(phaser6);

		// Photons
		Photon photonA = new Photon();
		photonA.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonA.setDesignator("A");
		weaponList.add(photonA);
		Photon photonB = new Photon();
		photonB.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonB.setDesignator("B");
		weaponList.add(photonB);
		Photon photonC = new Photon();
		photonC.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonC.setDesignator("C");
		weaponList.add(photonC);
		Photon photonD = new Photon();
		photonD.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonD.setDesignator("D");
		weaponList.add(photonD);

		shipSpecs.put("weapons", weaponList);

		return shipSpecs;
	}

	/**
	 * This map represents a Federation Improved Frigate (Burke Class)
	 * 
	 * @return A map with all the data for a Fed FFG
	 */
	public static Map<String, Object> getFedFfg() {
		Map<String, Object> shipSpecs = new HashMap<String, Object>();

		// Ship basics
		shipSpecs.put("faction", Faction.Federation);
		shipSpecs.put("hull", "FFG");
		shipSpecs.put("name", "USS Perry");
		shipSpecs.put("serviceyear", new Integer(160));
		shipSpecs.put("bpv", new Integer(75));
		shipSpecs.put("turnmode", TurnMode.B);
		shipSpecs.put("sizeclass", new Integer(4));

		// Shields
		shipSpecs.put("shield1", new Integer(18));
		shipSpecs.put("shield2", new Integer(18));
		shipSpecs.put("shield3", new Integer(18));
		shipSpecs.put("shield4", new Integer(18));
		shipSpecs.put("shield5", new Integer(18));
		shipSpecs.put("shield6", new Integer(18));

		// Hull
		shipSpecs.put("chull", new Integer(6));

		// Power
		shipSpecs.put("lwarp", new Integer(6));
		shipSpecs.put("rwarp", new Integer(6));
		shipSpecs.put("impulse", new Integer(3));
		shipSpecs.put("battery", new Integer(2));

		// Control
		shipSpecs.put("bridge", new Integer(2));
		shipSpecs.put("emer", new Integer(1));
		shipSpecs.put("auxcon", new Integer(1));

		// Special Functions
		shipSpecs.put("damcon", new int[] { 2, 2, 2, 0 });
		shipSpecs.put("scanner", new int[] { 0, 1, 3, 5, 9 });
		shipSpecs.put("sensor", new int[] { 6, 5, 3, 1, 0 });
		shipSpecs.put("excess", new Integer(4));
		shipSpecs.put("controlmod", new Double(1)); // Multiplier for control channels

		// Operations
		shipSpecs.put("trans", new Integer(2));
		shipSpecs.put("tractor", new Integer(2));
		shipSpecs.put("lab", new Integer(2));

		// Probes
		shipSpecs.put("probe", new Integer(1));

		// Shuttles
		shipSpecs.put("shuttle", new Integer(2));

		// Crew
		shipSpecs.put("crew", new Integer(16));
		shipSpecs.put("boardingparties", new Integer(6));
		shipSpecs.put("minimumcrew", new Integer(4));

		// Performance
		shipSpecs.put("movecost", new Double(1.0 / 3.0));
		shipSpecs.put("breakdown", new Integer(5));
		shipSpecs.put("bonushets", new Integer(1));

		// Weapons
		List<Weapon> weaponList = new ArrayList<>();

		// Fore Phasers (FH)
		Phaser1 phaser1 = new Phaser1();
		phaser1.setArcs(new int[] { 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7 });
		phaser1.setDesignator("1");
		weaponList.add(phaser1);

		// Right Half Phasers (RH)
		Phaser1 phaser2 = new Phaser1();
		phaser2.setArcs(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13 });
		phaser2.setDesignator("2");
		weaponList.add(phaser2);

		// Left Half Phasers (LH))
		Phaser1 phaser3 = new Phaser1();
		phaser3.setArcs(new int[] { 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 1 });
		phaser3.setDesignator("3");
		weaponList.add(phaser3);

		// Photons
		Photon photonA = new Photon();
		photonA.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonA.setDesignator("A");
		weaponList.add(photonA);
		Photon photonB = new Photon();
		photonB.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		photonB.setDesignator("B");
		weaponList.add(photonB);

		DroneRack droneRack = new DroneRack(DroneRack.DroneRackType.TYPE_G);
		droneRack.setSpaces(4);
		droneRack.setNumberOfReloads(1);
		droneRack.setDesignator("Drone Rack");
		droneRack.setAmmo(makeDrones(4, DroneType.TypeI));
		droneRack.setReloads(makeDrones(4, DroneType.TypeI));
		weaponList.add(droneRack);
		// Drone Rack

		shipSpecs.put("weapons", weaponList);

		return shipSpecs;
	}

	/**
	 * This map represents a Klingon D7 (Barbarous Class)
	 * 
	 * @return A map with all the data for a D7 Battlecruiser
	 */
	public static Map<String, Object> getD7() {
		Map<String, Object> shipSpecs = new HashMap<String, Object>();

		// Basics
		shipSpecs.put("faction", Faction.Klingon);
		shipSpecs.put("hull", "D7");
		shipSpecs.put("name", "IKV Saber");
		shipSpecs.put("serviceyear", new Integer(120));
		shipSpecs.put("bpv", new Integer(121));
		shipSpecs.put("turnmode", TurnMode.B);
		shipSpecs.put("sizeclass", new Integer(3));

		// Performance
		shipSpecs.put("movecost", new Double(1));
		shipSpecs.put("breakdown", new Integer(5));
		shipSpecs.put("bonushets", new Integer(1));

		// Shields
		shipSpecs.put("shield1", new Integer(30));
		shipSpecs.put("shield2", new Integer(22));
		shipSpecs.put("shield3", new Integer(15));
		shipSpecs.put("shield4", new Integer(12));
		shipSpecs.put("shield5", new Integer(15));
		shipSpecs.put("shield6", new Integer(22));

		// Hull boxes
		shipSpecs.put("fhull", new Integer(4));
		shipSpecs.put("ahull", new Integer(7));

		// Power systems
		shipSpecs.put("lwarp", new Integer(15));
		shipSpecs.put("rwarp", new Integer(15));
		shipSpecs.put("impulse", new Integer(5));
		shipSpecs.put("apr", new Integer(4));
		shipSpecs.put("battery", new Integer(5));

		// Control Boxes
		shipSpecs.put("bridge", new Integer(2));
		shipSpecs.put("emer", new Integer(1));
		shipSpecs.put("auxcon", new Integer(2));
		shipSpecs.put("security", new Integer(2));

		// Special Functions
		shipSpecs.put("damcon", new int[] { 4, 4, 2, 2, 0 });
		shipSpecs.put("scanner", new int[] { 0, 0, 1, 3, 5, 9 });
		shipSpecs.put("sensor", new int[] { 6, 6, 5, 3, 1, 0 });
		shipSpecs.put("excess", new Integer(5));
		shipSpecs.put("controlmod", new Double(1)); // Multiplier for control channels

		// Operations Systems
		shipSpecs.put("trans", new Integer(5));
		shipSpecs.put("tractor", new Integer(3));
		shipSpecs.put("lab", new Integer(4));

		// Probes
		shipSpecs.put("probe", new Integer(1));

		// Shuttles
		shipSpecs.put("shuttle", new Integer(2));

		// Crew
		shipSpecs.put("crew", new Integer(45));
		shipSpecs.put("boardingparties", new Integer(10));
		shipSpecs.put("minimumcrew", new Integer(4));

		// Weapons
		List<Weapon> weaponList = new ArrayList<>();

		// Boom Phasers (FX + directly aft)
		Phaser2 phaser1 = new Phaser2();
		phaser1.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser1.setDesignator("1");
		weaponList.add(phaser1);
		Phaser2 phaser2 = new Phaser2();
		phaser2.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser2.setDesignator("2");
		weaponList.add(phaser2);
		Phaser2 phaser3 = new Phaser2();
		phaser3.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser3.setDesignator("3");
		weaponList.add(phaser3);

		// Left Wing Phaser (L + LF + RR, plus 5 cross deck)
		Phaser2 phaser4 = new Phaser2();
		phaser4.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 9, 10, 11, 12, 13, 5 });
		phaser4.setDesignator("4");
		weaponList.add(phaser4);

		// Right Wing Phaser (RF + R + LR, plus 21 cross deck)
		Phaser2 phaser5 = new Phaser2();
		phaser5.setArcs(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 13, 14, 15, 16, 17, 21 });
		phaser5.setDesignator("5");
		weaponList.add(phaser5);

		// Left Waist Phasers (L + LR)
		Phaser2 phaser6 = new Phaser2();
		phaser6.setArcs(new int[] { 13, 14, 15, 16, 17, 18, 19, 20, 21 });
		phaser6.setDesignator("6");
		weaponList.add(phaser6);
		Phaser2 phaser7 = new Phaser2();
		phaser7.setArcs(new int[] { 13, 14, 15, 16, 17, 18, 19, 20, 21 });
		phaser7.setDesignator("7");
		weaponList.add(phaser7);

		// Right Waist Phasers (R + RR)
		Phaser2 phaser8 = new Phaser2();
		phaser8.setArcs(new int[] { 5, 6, 7, 8, 9, 10, 11, 12, 13 });
		phaser8.setDesignator("8");
		weaponList.add(phaser8);
		Phaser2 phaser9 = new Phaser2();
		phaser9.setArcs(new int[] { 5, 6, 7, 8, 9, 10, 11, 12, 13 });
		phaser9.setDesignator("9");
		weaponList.add(phaser9);

		// Disruptors (FA)
		Disruptor disrA = new Disruptor(30);
		disrA.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrA.setDesignator("A");
		weaponList.add(disrA);
		Disruptor disrB = new Disruptor(30);
		disrB.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrB.setDesignator("B");
		weaponList.add(disrB);
		Disruptor disrC = new Disruptor(30);
		disrC.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrC.setDesignator("C");
		weaponList.add(disrC);
		Disruptor disrD = new Disruptor(30);
		disrD.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrD.setDesignator("D");
		weaponList.add(disrD);

		DroneRack droneRack1 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
		droneRack1.setDesignator("Rack 1");
		droneRack1.setAmmo(makeDrones(4, DroneType.TypeI));
		droneRack1.setReloads(makeDrones(4, DroneType.TypeI));
		weaponList.add(droneRack1);
		DroneRack droneRack2 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
		droneRack2.setDesignator("Rack 2");
		droneRack2.setAmmo(makeDrones(4, DroneType.TypeI));
		droneRack2.setReloads(makeDrones(4, DroneType.TypeI));
		weaponList.add(droneRack2);

		shipSpecs.put("weapons", weaponList);

		return shipSpecs;
	}

	/**
	 * This map represents a Klingon F5 (Fury Class)
	 * 
	 * @return A map with all the data for a F5 Frigate
	 */
	public static Map<String, Object> getF5() {
		Map<String, Object> shipSpecs = new HashMap<String, Object>();

		shipSpecs.put("faction", Faction.Klingon);
		shipSpecs.put("hull", "F5");
		shipSpecs.put("name", "IKV Dagger");
		shipSpecs.put("serviceyear", new Integer(135));
		shipSpecs.put("bpv", new Integer(71));
		shipSpecs.put("sizeclass", new Integer(4));
		shipSpecs.put("turnmode", TurnMode.A);

		// Performance
		shipSpecs.put("movecost", new Double(0.5));
		shipSpecs.put("breakdown", new Integer(5));
		shipSpecs.put("bonushets", new Integer(1));

		// Shields
		shipSpecs.put("shield1", new Integer(21));
		shipSpecs.put("shield2", new Integer(16));
		shipSpecs.put("shield3", new Integer(9));
		shipSpecs.put("shield4", new Integer(9));
		shipSpecs.put("shield5", new Integer(9));
		shipSpecs.put("shield6", new Integer(16));

		// Hull boxes
		shipSpecs.put("fhull", new Integer(2));
		shipSpecs.put("ahull", new Integer(5));

		// Power systems
		shipSpecs.put("lwarp", new Integer(8));
		shipSpecs.put("rwarp", new Integer(8));
		shipSpecs.put("impulse", new Integer(3));
		shipSpecs.put("apr", new Integer(1));
		shipSpecs.put("battery", new Integer(2));

		// Control Boxes
		shipSpecs.put("bridge", new Integer(1));
		shipSpecs.put("emer", new Integer(1));
		shipSpecs.put("auxcon", new Integer(1));
		shipSpecs.put("security", new Integer(2));

		// Special Functions
		shipSpecs.put("damcon", new int[] { 2, 2, 2, 0 });
		shipSpecs.put("scanner", new int[] { 0, 1, 3, 9 });
		shipSpecs.put("sensor", new int[] { 6, 5, 3, 0 });
		shipSpecs.put("excess", new Integer(4));
		shipSpecs.put("controlmod", new Double(1)); // Multiplier for control channels

		// Operations Systems
		shipSpecs.put("trans", new Integer(2));
		shipSpecs.put("tractor", new Integer(1));
		shipSpecs.put("lab", new Integer(2));

		// Probes
		shipSpecs.put("probe", new Integer(1));

		// Shuttles
		shipSpecs.put("shuttle", new Integer(1));

		// Crew
		shipSpecs.put("crew", new Integer(22));
		shipSpecs.put("boardingparties", new Integer(8));
		shipSpecs.put("minimumcrew", new Integer(4));

		List<Weapon> weaponList = new ArrayList<>();

		// Left Boom Phaser (FA + L + hex row directly aft)
		Phaser2 phaser1 = new Phaser2();
		phaser1.setArcs(new int[] { 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3, 4, 5, 13 });
		phaser1.setDesignator("1");
		weaponList.add(phaser1);

		// Right Boom Phaser (FA + R + hex row directly aft)
		Phaser2 phaser2 = new Phaser2();
		phaser2.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 13 });
		phaser2.setDesignator("2");
		weaponList.add(phaser2);

		// Aft Phasers (RX)
		Phaser2 phaser3 = new Phaser2();
		phaser3.setArcs(new int[] { 5, 6, 7, 8, 9, 10, 11, 12, 23, 14, 15, 16, 17, 18, 19, 20, 21 });
		phaser3.setDesignator("3");
		weaponList.add(phaser3);
		Phaser2 phaser4 = new Phaser2();
		phaser4.setArcs(new int[] { 5, 6, 7, 8, 9, 10, 11, 12, 23, 14, 15, 16, 17, 18, 19, 20, 21 });
		phaser4.setDesignator("4");
		weaponList.add(phaser4);
		Phaser2 phaser5 = new Phaser2();
		phaser5.setArcs(new int[] { 5, 6, 7, 8, 9, 10, 11, 12, 23, 14, 15, 16, 17, 18, 19, 20, 21 });
		phaser5.setDesignator("5");
		weaponList.add(phaser5);

		// Disruptors
		Disruptor disrA = new Disruptor(15);
		disrA.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrA.setDesignator("A");
		weaponList.add(disrA);
		Disruptor disrB = new Disruptor(15);
		disrB.setArcs(new int[] { 21, 22, 23, 24, 1, 2, 3, 4, 5 });
		disrB.setDesignator("B");
		weaponList.add(disrB);

		DroneRack droneRack1 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
		droneRack1.setDesignator("Rack 1");
		droneRack1.setAmmo(makeDrones(4, DroneType.TypeI));
		droneRack1.setReloads(makeDrones(4, DroneType.TypeI));
		weaponList.add(droneRack1);

		shipSpecs.put("weapons", weaponList);

		return shipSpecs;
	}

	/** Creates a list of drones of the given type and count. */
	private static List<Drone> makeDrones(int count, DroneType type) {
		List<Drone> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(new Drone(type));
		}
		return list;
	}
}
