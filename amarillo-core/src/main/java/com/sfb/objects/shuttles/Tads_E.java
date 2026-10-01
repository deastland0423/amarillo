package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Phaser3;

import java.util.List;

/**
 * Kzinti Advanced Attack Shuttle (J4.43).
 * Year Y177. Speed 15. Hull 11, crippled at 8. BPV 8.
 * Weapons: 2× Ph-3 (FA), 2× Standard DroneRail (one 1-space drone each),
 * 2× Light DroneRail (one dogfight drone each).
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only (capacity 4).
 * Must have lock-on to a target in the FA arc of the rail to launch drones
 * (D6.121).
 */
public class Tads_E extends Fighter {

    public Tads_E() {
        setCatalogType("tads_e");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(11);
        setCrippledHull(8);
        setBpv(13);

        Phaser3 ph = new Phaser3();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        Phaser3 ph2 = new Phaser3();
        ph2.setDesignator("2");
        ph2.setArcs(ArcUtils.FA);
        ph2.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph2);

        DroneRail railA = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railA.setDesignator("A");
        getWeapons().addWeapon(railA);

        DroneRail railB = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railB.setDesignator("B");
        getWeapons().addWeapon(railB);

        DroneRail railC = new DroneRail(DroneRail.DroneRailType.LIGHT);
        railC.setDesignator("C");
        getWeapons().addWeapon(railC);

        DroneRail railD = new DroneRail(DroneRail.DroneRailType.LIGHT);
        railD.setDesignator("D");
        getWeapons().addWeapon(railD);

        DroneRail railE = new DroneRail(DroneRail.DroneRailType.SPECIAL);
        railE.setDesignator("E");
        getWeapons().addWeapon(railE);

        DroneRail railF = new DroneRail(DroneRail.DroneRailType.SPECIAL);
        railF.setDesignator("F");
        getWeapons().addWeapon(railF);

        // The SSD shows the EW variant with the same rails as the standard HAAS,
        // carrying
        // pods on them and two fewer drones (J4.962). The pods are not free: they cost
        // the
        // whole of this fighter's offensive armament, which is what it trades for being
        // able to lend eight points to its squadron (J4.965, J4.941).
        fitEwPods(2);
    }

    // --- J4.242 exemptions ---

    /**
     * J4.242: "the F-15 and TAAS (which can violate A if the drones are not
     * launched on the
     * same impulse...)". So a TAAS may split its pair between two targets, provided
     * it does
     * not let both go on one impulse.
     */
    @Override
    public boolean mayLaunchAtDifferentTargets() {
        return true;
    }

    /**
     * J4.242: "...and which can violate B in any case." A TAAS may launch two
     * standard
     * drones, with no dogfight drone among them — which the AAS and HAAS may not,
     * and is
     * most of what the extra BPV buys.
     */
    @Override
    public boolean mayLaunchTwoStandardDrones() {
        return true;
    }
}
