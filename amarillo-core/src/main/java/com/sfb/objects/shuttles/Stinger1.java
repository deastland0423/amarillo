package com.sfb.objects.shuttles;

import com.sfb.objects.*;

import java.util.List;

import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.Phaser3;

/**
 * Hydran Stinger-I fighter (R9.F1 / J4.83).
 * Year available: Y134. Speed 12. BPV 8. Hull 8, crippled at 6.
 * Weapons: 1× Phaser-3 (FA), 2× FighterFusion (FULL), each with 2 charges.
 */
public class Stinger1 extends Fighter {

    public Stinger1() {
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(12);
        setCurrentSpeed(12);
        setHull(8);
        setCrippledHull(6);
        setBpv(8);

        Phaser3 ph = new Phaser3();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        FighterFusion fA = new FighterFusion();
        fA.setDesignator("A");
        fA.setArcs(ArcUtils.FULL);
        fA.setArcsFromJSON(List.of("FULL"));
        getWeapons().addWeapon(fA);

        FighterFusion fB = new FighterFusion();
        fB.setDesignator("B");
        fB.setArcs(ArcUtils.FULL);
        fB.setArcsFromJSON(List.of("FULL"));
        getWeapons().addWeapon(fB);
    }
}
