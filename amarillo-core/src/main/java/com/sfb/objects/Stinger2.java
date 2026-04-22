package com.sfb.objects;

import java.util.List;

import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.PhaserG;

/**
 * Hydran Stinger-II fighter (R9.F2 / J4.83).
 * Year available: Y170. Speed 15. BPV 10. Hull 10, crippled at 7 damage.
 * Weapons: 1× Ph-G (FA), 2× FighterFusion (FULL), each with 2 charges.
 * Standard Hydran fighter during the General War.
 * When crippled: speed → 8, Ph-G reduced to Ph-3 (1 shot), FighterFusions offline.
 */
public class Stinger2 extends Fighter {

    public Stinger2() {
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(10);
        setCrippledHull(7);
        setBpv(10);

        PhaserG ph = new PhaserG();
        ph.setDesignator("P");
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
