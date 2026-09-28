package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Phaser3;

import java.util.List;

/**
 * Kzinti Advanced Attack Shuttle (J4.43).
 * Year Y164. Speed 8. Hull 8, crippled at 6 damage. BPV 6.
 * Weapons: 1× Ph-3 (FA), 2× DroneRail (one standard drone each).
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only (capacity 2).
 * Must have lock-on to a target in the FA arc of the rail to launch drones (D6.121).
 */
public class Aas extends Fighter {

    public Aas() {
		setCatalogType("aas");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(8);
        setCurrentSpeed(8);
        setHull(8);
        setCrippledHull(6);
        setBpv(6);

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
