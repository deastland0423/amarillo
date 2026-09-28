package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Phaser3;

import java.util.List;

/**
 * Kzinti Advanced Attack Shuttle, EW version (J4.43).
 * Year Y173. Speed 15. Hull 11, crippled at 8. BPV 10.
 * Weapons: 1× Ph-3 (FA), 2× DroneRail (one standard drone each).
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only (capacity 2).
 * Enhanced with EW capabilities (2 EW pods).
 * Must have lock-on to target in FA arc to launch drones (J4.431).
 */
public class Haas_E extends Fighter {

    public Haas_E() {
		setCatalogType("haas_e");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(11);
        setCrippledHull(8);
        setBpv(10);
        setTwoSeater(true);
        setEwPods(2);

        Phaser3 ph = new Phaser3();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        DroneRail railA = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railA.setDesignator("A");
        getWeapons().addWeapon(railA);

        DroneRail railB = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railB.setDesignator("B");
        getWeapons().addWeapon(railB);
    }
}
