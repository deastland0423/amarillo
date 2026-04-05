package com.sfb.objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Weapon;

/**
 * Ground Attack Shuttle — heavier hull than the admin shuttle, same speed and armament.
 * Hull: 8, Max speed: 6, Weapon: Phaser-3 (full arc).
 */
public class GASShuttle extends Shuttle {

    public GASShuttle() {
        setHull(8);
        setMaxSpeed(6);

        Phaser3 phaser = new Phaser3();
        phaser.setDesignator("1");
        phaser.setArcs(ArcUtils.FULL);
        List<Weapon> weaponList = new ArrayList<>();
        weaponList.add(phaser);
        Map<String, Object> values = new HashMap<>();
        values.put("weapons", weaponList);
        getWeapons().init(values);
    }
}
