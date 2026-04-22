package com.sfb.objects;

import java.util.List;

import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.PhaserG;

/**
 * Hydran Stinger-H fighter (R9.F4 / J4.834).
 * Speed 15. BPV 12. Hull 10, crippled at 7 damage.
 * Weapons: 1× Ph-G (FA), 1× FighterHellbore (FULL, range 0–10).
 * The hellbore fires once; the fighter must return to its carrier to reload.
 * When crippled: speed → 8, Ph-G reduced to Ph-3, hellbore ceases to operate.
 */
public class StingerH extends Fighter {

    public StingerH() {
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(10);
        setCrippledHull(7);
        setBpv(12);

        PhaserG ph = new PhaserG();
        ph.setDesignator("P");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        FighterHellbore hb = new FighterHellbore();
        hb.setDesignator("H");
        hb.setArcs(ArcUtils.FULL);
        hb.setArcsFromJSON(List.of("FULL"));
        getWeapons().addWeapon(hb);
    }
}
