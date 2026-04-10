package com.sfb.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.ADD;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Weapon;
import com.sfb.weapons.ADD.AddType;

public class KlingonShips {

    /** Klingon D7 Battlecruiser — Barbarous Class (e.g. IKV Saber) */
    public static Map<String, Object> getD7() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Klingon);
        s.put("hull", "D7");
        s.put("name", "IKV Saber");
        s.put("serviceyear", 120);
        s.put("bpv", 121);
        s.put("turnmode", TurnMode.B);
        s.put("sizeclass", 3);

        s.put("shield1", 30);
        s.put("shield2", 22);
        s.put("shield3", 15);
        s.put("shield4", 12);
        s.put("shield5", 15);
        s.put("shield6", 22);

        s.put("fhull", 4);
        s.put("ahull", 7);

        s.put("lwarp", 15);
        s.put("rwarp", 15);
        s.put("impulse", 5);
        s.put("apr", 4);
        s.put("battery", 5);

        s.put("bridge", 2);
        s.put("emer", 1);
        s.put("auxcon", 2);
        s.put("security", 2);

        s.put("damcon", new int[] { 4, 4, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 0, 1, 3, 5, 9 });
        s.put("sensor", new int[] { 6, 6, 5, 3, 1, 0 });
        s.put("excess", 5);
        s.put("controlmod", 1.0);

        s.put("trans", 5);
        s.put("tbombs", 1);
        s.put("dummytbombs", 1);
        s.put("tractor", 3);
        s.put("lab", 4);
        s.put("probe", 1);
        s.put("shuttle", 2);
        s.put("derfacs", true);
        s.put("uim", 4);
        s.put("crew", 45);
        s.put("boardingparties", 10);
        s.put("minimumcrew", 4);
        s.put("movecost", 1.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Boom phasers (FX + aft)
        int fxAft = ArcUtils.FX | ArcUtils.of(13);
        for (String des : new String[] { "1", "2", "3" }) {
            Phaser2 p = new Phaser2();
            p.setArcs(fxAft);
            p.setDesignator(des);
            weapons.add(p);
        }

        // Left wing phaser (L + LF + RR + cross-deck 5)
        Phaser2 p4 = new Phaser2();
        p4.setArcs(ArcUtils.L | ArcUtils.LF | ArcUtils.RR | ArcUtils.of(5));
        p4.setDesignator("4");
        weapons.add(p4);

        // Right wing phaser (RF + R + LR + cross-deck 21)
        Phaser2 p5 = new Phaser2();
        p5.setArcs(ArcUtils.RF | ArcUtils.R | ArcUtils.LR | ArcUtils.of(21));
        p5.setDesignator("5");
        weapons.add(p5);

        // Left waist phasers (L + LR)
        int leftArc = ArcUtils.L | ArcUtils.LR;
        Phaser2 p6 = new Phaser2();
        p6.setArcs(leftArc);
        p6.setDesignator("6");
        weapons.add(p6);
        Phaser2 p7 = new Phaser2();
        p7.setArcs(leftArc);
        p7.setDesignator("7");
        weapons.add(p7);

        // Right waist phasers (R + RR)
        int rightArc = ArcUtils.R | ArcUtils.RR;
        Phaser2 p8 = new Phaser2();
        p8.setArcs(rightArc);
        p8.setDesignator("8");
        weapons.add(p8);
        Phaser2 p9 = new Phaser2();
        p9.setArcs(rightArc);
        p9.setDesignator("9");
        weapons.add(p9);

        // Disruptors (FA)
        for (String des : new String[] { "A", "B", "C", "D" }) {
            Disruptor d = new Disruptor(30);
            d.setArcs(ArcUtils.FA);
            d.setDesignator(des);
            weapons.add(d);
        }

        // Drone racks
        DroneRack rack1 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
        rack1.setDesignator("Rack 1");
        rack1.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack1);

        DroneRack rack2 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
        rack2.setDesignator("Rack 2");
        rack2.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack2);

        s.put("weapons", weapons);
        return s;
    }

    /** Klingon D5 War Cruiser — Klodode Class (e.g. IKV Saber) */
    public static Map<String, Object> getD5() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Klingon);
        s.put("hull", "D5");
        s.put("name", "IKV Ransacker");
        s.put("serviceyear", 168);
        s.put("bpv", 110);
        s.put("turnmode", TurnMode.B);
        s.put("sizeclass", 3);

        s.put("shield1", 30);
        s.put("shield2", 26);
        s.put("shield3", 26);
        s.put("shield4", 26);
        s.put("shield5", 26);
        s.put("shield6", 26);

        s.put("fhull", 3);
        s.put("ahull", 6);

        s.put("lwarp", 12);
        s.put("rwarp", 12);
        s.put("impulse", 5);
        s.put("apr", 2);
        s.put("battery", 3);

        s.put("bridge", 2);
        s.put("emer", 1);
        s.put("auxcon", 2);
        s.put("security", 2);

        s.put("damcon", new int[] { 4, 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 1, 3, 5, 9 });
        s.put("sensor", new int[] { 6, 5, 3, 1, 0 });
        s.put("excess", 4);
        s.put("controlmod", 1.0);

        s.put("trans", 3);
        s.put("tractor", 3);
        s.put("lab", 2);
        s.put("probe", 1);
        s.put("shuttle", 2);
        s.put("crew", 40);
        s.put("boardingparties", 8);
        s.put("minimumcrew", 4);
        s.put("movecost", 2.0 / 3.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Boom phasers (FX + aft)
        int fxAft = ArcUtils.FX | ArcUtils.of(13);
        for (String des : new String[] { "1", "2" }) {
            Phaser1 p = new Phaser1();
            p.setArcs(fxAft);
            p.setDesignator(des);
            weapons.add(p);
        }

        // Left wing phaser (L + LF + RR + cross-deck 5)
        Phaser2 p3 = new Phaser2();
        p3.setArcs(ArcUtils.LF | ArcUtils.L | ArcUtils.RR | ArcUtils.of(5));
        p3.setDesignator("3");
        weapons.add(p3);

        // Right wing phaser (RF + R + LR + cross-deck 21)
        Phaser2 p4 = new Phaser2();
        p4.setArcs(ArcUtils.RF | ArcUtils.R | ArcUtils.LR | ArcUtils.of(21));
        p4.setDesignator("4");
        weapons.add(p4);

        // Left waist phasers (L + LR)
        int leftArc = ArcUtils.L | ArcUtils.LR;
        Phaser3 p6 = new Phaser3();
        p6.setArcs(leftArc);
        p6.setDesignator("5");
        weapons.add(p6);
        Phaser3 p7 = new Phaser3();
        p7.setArcs(leftArc);
        p7.setDesignator("6");
        weapons.add(p7);

        // Right waist phasers (R + RR)
        int rightArc = ArcUtils.R | ArcUtils.RR;
        Phaser3 p8 = new Phaser3();
        p8.setArcs(rightArc);
        p8.setDesignator("8");
        weapons.add(p8);
        Phaser3 p9 = new Phaser3();
        p9.setArcs(rightArc);
        p9.setDesignator("9");
        weapons.add(p9);

        // Disruptors (FA)
        for (String des : new String[] { "A", "B", "C", "D" }) {
            Disruptor d = new Disruptor(30);
            d.setArcs(ArcUtils.FA);
            d.setDesignator(des);
            weapons.add(d);
        }

        // Drone racks
        DroneRack rack1 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
        rack1.setDesignator("Rack 1");
        rack1.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack1);

        DroneRack rack2 = new DroneRack(DroneRack.DroneRackType.TYPE_F);
        rack2.setDesignator("Rack 2");
        rack2.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack2);

        ADD add1 = new ADD(AddType.ADD_12, 2);
        add1.setDesignator("ADD 1");
        weapons.add(add1);
        ADD add2 = new ADD(AddType.ADD_12, 2);
        add2.setDesignator("ADD 2");
        weapons.add(add2);

        s.put("weapons", weapons);
        return s;
    }

    /** Klingon F5 Frigate — Fury Class (e.g. IKV Dagger) */
    public static Map<String, Object> getF5() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Klingon);
        s.put("hull", "F5");
        s.put("name", "IKV Dagger");
        s.put("serviceyear", 135);
        s.put("bpv", 71);
        s.put("turnmode", TurnMode.A);
        s.put("sizeclass", 4);

        s.put("shield1", 21);
        s.put("shield2", 16);
        s.put("shield3", 9);
        s.put("shield4", 9);
        s.put("shield5", 9);
        s.put("shield6", 16);

        s.put("fhull", 2);
        s.put("ahull", 5);

        s.put("lwarp", 8);
        s.put("rwarp", 8);
        s.put("impulse", 3);
        s.put("apr", 1);
        s.put("battery", 2);

        s.put("bridge", 1);
        s.put("emer", 1);
        s.put("auxcon", 1);
        s.put("security", 2);

        s.put("damcon", new int[] { 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 1, 3, 9 });
        s.put("sensor", new int[] { 6, 5, 3, 0 });
        s.put("excess", 4);
        s.put("controlmod", 1.0);

        s.put("trans", 2);
        s.put("tractor", 1);
        s.put("lab", 2);
        s.put("probe", 1);
        s.put("shuttle", 1);
        s.put("crew", 22);
        s.put("boardingparties", 8);
        s.put("minimumcrew", 4);
        s.put("movecost", 0.5);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Left boom phaser (FA + L + aft)
        Phaser2 p1 = new Phaser2();
        p1.setArcs(ArcUtils.FA | ArcUtils.L | ArcUtils.of(13));
        p1.setDesignator("1");
        weapons.add(p1);

        // Right boom phaser (FA + R + aft)
        Phaser2 p2 = new Phaser2();
        p2.setArcs(ArcUtils.FA | ArcUtils.R | ArcUtils.of(13));
        p2.setDesignator("2");
        weapons.add(p2);

        // Aft phasers (RH + LS aft overlap — effectively RX)
        int rxArc = ArcUtils.RH | ArcUtils.LS | ArcUtils.of(23);
        for (String des : new String[] { "3", "4", "5" }) {
            Phaser2 p = new Phaser2();
            p.setArcs(rxArc);
            p.setDesignator(des);
            weapons.add(p);
        }

        // Disruptors (FA)
        Disruptor dA = new Disruptor(15);
        dA.setArcs(ArcUtils.FA);
        dA.setDesignator("A");
        weapons.add(dA);
        Disruptor dB = new Disruptor(15);
        dB.setArcs(ArcUtils.FA);
        dB.setDesignator("B");
        weapons.add(dB);

        // Drone rack
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_F);
        rack.setDesignator("Rack 1");
        List<Drone> ammo = new ArrayList<>();
        ammo.add(new Drone(DroneType.TypeI));
        ammo.add(new Drone(DroneType.TypeI));
        ammo.add(new Drone(DroneType.TypeIV));
        rack.setAmmo(ammo);
        rack.getReloads().clear();
        rack.getReloads().add(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack);

        s.put("weapons", weapons);
        return s;
    }
}
