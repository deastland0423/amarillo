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
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Weapon;

public class KzintiShips {

    /** Kzinti BC Battlecruiser — Quasar Class (e.g. KHS Quasar) */
    public static Map<String, Object> getKzinBC() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Kzinti);
        s.put("hull", "BC");
        s.put("name", "KHS Quasar");
        s.put("serviceyear", 160);
        s.put("bpv", 128);
        s.put("turnmode", TurnMode.C);
        s.put("sizeclass", 3);

        s.put("shield1", 30);
        s.put("shield2", 28);
        s.put("shield3", 22);
        s.put("shield4", 22);
        s.put("shield5", 22);
        s.put("shield6", 28);

        s.put("fhull", 5);
        s.put("ahull", 12);

        s.put("lwarp", 10);
        s.put("rwarp", 10);
        s.put("cwarp", 10);
        s.put("impulse", 3);
        s.put("apr", 3);
        s.put("battery", 5);

        s.put("bridge", 3);
        s.put("emer", 1);
        s.put("auxcon", 3);

        s.put("damcon", new int[] { 4, 4, 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 1, 2, 3, 5, 9 });
        s.put("sensor", new int[] { 6, 6, 6, 4, 1, 0 });
        s.put("excess", 6);
        s.put("controlmod", 1.0);

        s.put("trans", 5);
        s.put("tractor", 2);
        s.put("lab", 6);
        s.put("probe", 1);
        s.put("shuttle", 2);
        s.put("crew", 40);
        s.put("boardingparties", 16);
        s.put("minimumcrew", 4);
        s.put("movecost", 1.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Forward Phasers
        int faL = ArcUtils.FA | ArcUtils.L;
        Phaser1 p1 = new Phaser1();
        p1.setArcs(faL);
        p1.setDesignator("1");
        weapons.add(p1);

        int faR = ArcUtils.FA | ArcUtils.R;
        Phaser1 p2 = new Phaser1();
        p2.setArcs(faR);
        p2.setDesignator("2");
        weapons.add(p2);

        // Left Wing Phaser 3s
        int ls = ArcUtils.LS;
        Phaser3 p3 = new Phaser3();
        p3.setArcs(ls);
        p3.setDesignator("3");
        weapons.add(p3);
        Phaser3 p4 = new Phaser3();
        p4.setArcs(ls);
        p4.setDesignator("4");
        weapons.add(p4);

        // Right Wing Phaser 3s
        int rs = ArcUtils.RS;
        Phaser3 p5 = new Phaser3();
        p5.setArcs(rs);
        p5.setDesignator("5");
        weapons.add(p5);
        Phaser3 p6 = new Phaser3();
        p6.setArcs(rs);
        p6.setDesignator("6");
        weapons.add(p6);

        // Midship Phaser 1s
        int full = ArcUtils.FULL;
        Phaser1 p7 = new Phaser1();
        p7.setArcs(full);
        p7.setDesignator("7");
        weapons.add(p7);
        Phaser1 p8 = new Phaser1();
        p8.setArcs(full);
        p8.setDesignator("8");
        weapons.add(p8);

        // Left Rear Phaser 3s
        int llr = ArcUtils.LR | ArcUtils.L;
        Phaser3 p9 = new Phaser3();
        p9.setArcs(llr);
        p9.setDesignator("9");
        weapons.add(p9);
        Phaser3 p10 = new Phaser3();
        p10.setArcs(llr);
        p10.setDesignator("10");
        weapons.add(p10);

        // Right Rear Phaser 3s
        int rrr = ArcUtils.RR | ArcUtils.R;
        Phaser3 p11 = new Phaser3();
        p11.setArcs(rrr);
        p11.setDesignator("11");
        weapons.add(p11);
        Phaser3 p12 = new Phaser3();
        p12.setArcs(rrr);
        p12.setDesignator("12");
        weapons.add(p12);

        // Disruptors (FA + L)
        for (String des : new String[] { "A", "B" }) {
            Disruptor d = new Disruptor(30);
            d.setArcs(ArcUtils.FA | ArcUtils.L);
            d.setDesignator(des);
            weapons.add(d);
        }

        // Disruptors (FA + R)
        for (String des : new String[] { "C", "D" }) {
            Disruptor d = new Disruptor(30);
            d.setArcs(ArcUtils.FA | ArcUtils.R);
            d.setDesignator(des);
            weapons.add(d);
        }

        // Drone racks
        DroneRack rack1 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack1.setDesignator("Rack 1");
        rack1.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack1);

        DroneRack rack2 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack2.setDesignator("Rack 2");
        rack2.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack2);

        DroneRack rack3 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack3.setDesignator("Rack 3");
        rack3.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack3);

        DroneRack rack4 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack4.setDesignator("Rack 4");
        rack4.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack4);

        s.put("weapons", weapons);
        return s;
    }

    /** Kzinti CS Strike Cruiser — Nova Class (e.g. KHS Nova) */
    public static Map<String, Object> getKzinCS() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Kzinti);
        s.put("hull", "CS");
        s.put("name", "KHS Quasar");
        s.put("serviceyear", 125);
        s.put("bpv", 116);
        s.put("turnmode", TurnMode.C);
        s.put("sizeclass", 3);

        s.put("shield1", 24);
        s.put("shield2", 22);
        s.put("shield3", 22);
        s.put("shield4", 22);
        s.put("shield5", 22);
        s.put("shield6", 22);

        s.put("fhull", 5);
        s.put("ahull", 12);

        s.put("lwarp", 9);
        s.put("rwarp", 9);
        s.put("cwarp", 9);
        s.put("impulse", 3);
        s.put("apr", 3);
        s.put("battery", 5);

        s.put("bridge", 3);
        s.put("emer", 1);
        s.put("auxcon", 3);

        s.put("damcon", new int[] { 4, 4, 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 1, 2, 3, 5, 9 });
        s.put("sensor", new int[] { 6, 6, 6, 4, 1, 0 });
        s.put("excess", 6);
        s.put("controlmod", 1.0);

        s.put("trans", 5);
        s.put("tractor", 2);
        s.put("lab", 6);
        s.put("probe", 1);
        s.put("shuttle", 2);
        s.put("crew", 40);
        s.put("boardingparties", 16);
        s.put("minimumcrew", 4);
        s.put("movecost", 1.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Forward Phasers
        int lfl = ArcUtils.LF | ArcUtils.L;
        Phaser1 p1 = new Phaser1();
        p1.setArcs(lfl);
        p1.setDesignator("1");
        weapons.add(p1);

        int rfr = ArcUtils.RF | ArcUtils.R;
        Phaser1 p2 = new Phaser1();
        p2.setArcs(rfr);
        p2.setDesignator("2");
        weapons.add(p2);

        // Left Wing Phaser 3s
        int ls = ArcUtils.LS;
        Phaser3 p3 = new Phaser3();
        p3.setArcs(ls);
        p3.setDesignator("3");
        weapons.add(p3);
        Phaser3 p4 = new Phaser3();
        p4.setArcs(ls);
        p4.setDesignator("4");
        weapons.add(p4);

        // Right Wing Phaser 3s
        int rs = ArcUtils.RS;
        Phaser3 p5 = new Phaser3();
        p5.setArcs(rs);
        p5.setDesignator("5");
        weapons.add(p5);
        Phaser3 p6 = new Phaser3();
        p6.setArcs(rs);
        p6.setDesignator("6");
        weapons.add(p6);

        // Midship Phaser 3s
        int full = ArcUtils.FULL;
        Phaser3 p7 = new Phaser3();
        p7.setArcs(full);
        p7.setDesignator("7");
        weapons.add(p7);
        Phaser3 p8 = new Phaser3();
        p8.setArcs(full);
        p8.setDesignator("8");
        weapons.add(p8);

        // Left Rear Phaser 3s
        int llr = ArcUtils.LR | ArcUtils.L;
        Phaser3 p9 = new Phaser3();
        p9.setArcs(llr);
        p9.setDesignator("9");
        weapons.add(p9);
        Phaser3 p10 = new Phaser3();
        p10.setArcs(llr);
        p10.setDesignator("10");
        weapons.add(p10);

        // Right Rear Phaser 3s
        int rrr = ArcUtils.RR | ArcUtils.R;
        Phaser3 p11 = new Phaser3();
        p11.setArcs(rrr);
        p11.setDesignator("11");
        weapons.add(p11);
        Phaser3 p12 = new Phaser3();
        p12.setArcs(rrr);
        p12.setDesignator("12");
        weapons.add(p12);

        // Disruptors (LF + L)
        Disruptor d = new Disruptor(30);
        d.setArcs(ArcUtils.LF | ArcUtils.L);
        d.setDesignator("A");
        weapons.add(d);

        // Disruptors (RF + R)
        Disruptor d2 = new Disruptor(30);
        d2.setArcs(ArcUtils.RF | ArcUtils.R);
        d2.setDesignator("B");
        weapons.add(d2);

        // Drone racks
        DroneRack rack1 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack1.setDesignator("Rack 1");
        rack1.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack1);

        DroneRack rack2 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack2.setDesignator("Rack 2");
        rack2.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack2);

        DroneRack rack3 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack3.setDesignator("Rack 3");
        rack3.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack3);

        DroneRack rack4 = new DroneRack(DroneRack.DroneRackType.TYPE_A);
        rack4.setDesignator("Rack 4");
        rack4.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack4);

        s.put("weapons", weapons);
        return s;
    }

}
