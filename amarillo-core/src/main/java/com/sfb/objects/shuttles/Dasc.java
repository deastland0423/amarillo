package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.Phaser3;

import java.util.List;

/**
 * Kzinti Disruptor Attack Shuttle-C — the DASC (J4.4).
 * Year Y183. Speed 10. Hull 10, crippled at 7 damage. BPV 11.
 * Weapons: 1× Ph-3 (FA), 1× FighterDisruptor (FA), 2× STANDARD DroneRail.
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only.
 * Must have lock-on to a target in the FA arc of the rail to launch drones
 * (D6.121).
 * <p>
 * The Kzinti line's second attack fighter, taking over the attack role from the {@link Das} in
 * Y183 — the same year the standard fighter becomes the TADSC and the EW fighter the TADSC-E.
 * <p>
 * The difference from the DAS is on the rails, not in the gun: the DAS carries LIGHT rails, which
 * take only a dogfight drone, where this carries STANDARD rails and so can fly a full-size one.
 * Same Ph-3 and same fighter disruptor, a point more BPV for the heavier load.
 */
public class Dasc extends Fighter {

    public Dasc() {
        setCatalogType("dasc");
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(10);
        setCurrentSpeed(10);
        setHull(10);
        setCrippledHull(7);
        setBpv(11);

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

        FighterDisruptor fd = new FighterDisruptor();
        fd.setDesignator("C");
        fd.setArcs(ArcUtils.FA);
        fd.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(fd);
    }
}
