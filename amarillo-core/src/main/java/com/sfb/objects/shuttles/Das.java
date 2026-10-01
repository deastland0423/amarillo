package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.Phaser3;

import java.util.List;

/**
 * Kzinti Disruptor Attack Shuttle — the DAS (J4.4).
 * Year Y172. Speed 10. Hull 10, crippled at 7 damage. BPV 10.
 * Weapons: 1× Ph-3 (FA), 1× FighterDisruptor (FA), 2× light DroneRail.
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only.
 * Must have lock-on to a target in the FA arc of the rail to launch drones
 * (D6.121).
 * <p>
 * This is the Kzinti line's ATTACK fighter, which is why it carries a heavy weapon where the
 * AAS family carries none: the disruptor is a fighter-mounted one running on charges from its
 * box (J4.833), the same arrangement the Hydran Stingers use for their fusions.
 */
public class Das extends Fighter {

    public Das() {
        setCatalogType("das");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(10);
        setCurrentSpeed(10);
        setHull(10);
        setCrippledHull(7);
        setBpv(10);

        Phaser3 ph = new Phaser3();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        DroneRail railA = new DroneRail(DroneRail.DroneRailType.LIGHT);
        railA.setDesignator("A");
        getWeapons().addWeapon(railA);

        DroneRail railB = new DroneRail(DroneRail.DroneRailType.LIGHT);
        railB.setDesignator("B");
        getWeapons().addWeapon(railB);

        FighterDisruptor fd = new FighterDisruptor();
        fd.setDesignator("C");
        fd.setArcs(ArcUtils.FA);
        fd.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(fd);
    }
}
