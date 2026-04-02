package com.sfb.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Drone.DroneType;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Photon;
import com.sfb.weapons.Weapon;

public class FederationShips {

    /** Federation Heavy Cruiser — Constitution Class (e.g. USS Lexington) */
    public static Map<String, Object> getFedCa() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Federation);
        s.put("hull", "CA");
        s.put("name", "USS Lexington");
        s.put("serviceyear", 130);
        s.put("bpv", 125);
        s.put("turnmode", TurnMode.D);
        s.put("sizeclass", 3);

        s.put("shield1", 30); s.put("shield2", 24); s.put("shield3", 20);
        s.put("shield4", 20); s.put("shield5", 20); s.put("shield6", 24);

        s.put("fhull", 12); s.put("ahull", 4);

        s.put("lwarp", 15); s.put("rwarp", 15); s.put("impulse", 4); s.put("battery", 3);

        s.put("bridge", 2); s.put("emer", 2); s.put("auxcon", 2);

        s.put("damcon",    new int[] { 4, 4, 2, 2, 0 });
        s.put("scanner",   new int[] { 0, 0, 1, 3, 5, 9 });
        s.put("sensor",    new int[] { 6, 6, 5, 3, 1, 0 });
        s.put("excess", 6);
        s.put("controlmod", 0.5);

        s.put("trans", 3); s.put("tractor", 3); s.put("lab", 8);
        s.put("probe", 1); s.put("shuttle", 4);
        s.put("crew", 43); s.put("boardingparties", 10); s.put("minimumcrew", 4);
        s.put("movecost", 1.0); s.put("breakdown", 5); s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Fore phasers (FH)
        Phaser1 p1 = new Phaser1();
        p1.setArcs(new int[] { 19,20,21,22,23,24,1,2,3,4,5,6,7 }); p1.setDesignator("1");
        weapons.add(p1);
        Phaser1 p2 = new Phaser1();
        p2.setArcs(new int[] { 19,20,21,22,23,24,1,2,3,4,5,6,7 }); p2.setDesignator("2");
        weapons.add(p2);

        // Left phasers (LF + L + aft)
        Phaser1 p3 = new Phaser1();
        p3.setArcs(new int[] { 17,18,19,20,21,22,23,24,1,13 }); p3.setDesignator("3");
        weapons.add(p3);
        Phaser1 p4 = new Phaser1();
        p4.setArcs(new int[] { 17,18,19,20,21,22,23,24,1,13 }); p4.setDesignator("4");
        weapons.add(p4);

        // Right phasers (RF + R + aft)
        Phaser1 p5 = new Phaser1();
        p5.setArcs(new int[] { 1,2,3,4,5,6,7,8,9,13 }); p5.setDesignator("5");
        weapons.add(p5);
        Phaser1 p6 = new Phaser1();
        p6.setArcs(new int[] { 1,2,3,4,5,6,7,8,9,13 }); p6.setDesignator("6");
        weapons.add(p6);

        // Photon torpedoes (FA)
        int[] faArc = { 21,22,23,24,1,2,3,4,5 };
        for (String des : new String[] { "A","B","C","D" }) {
            Photon ph = new Photon(); ph.setArcs(faArc); ph.setDesignator(des); weapons.add(ph);
        }

        s.put("weapons", weapons);
        return s;
    }

    /** Federation Old Light Cruiser — Texas Class (e.g. USS Texas) */
    public static Map<String, Object> getFedOcl() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Federation);
        s.put("hull", "OCL");
        s.put("name", "USS Texas");
        s.put("serviceyear", 120);
        s.put("bpv", 98);
        s.put("turnmode", TurnMode.C);
        s.put("sizeclass", 3);

        s.put("shield1", 16); s.put("shield2", 12); s.put("shield3", 10);
        s.put("shield4", 12); s.put("shield5", 10); s.put("shield6", 12);

        s.put("fhull", 6); s.put("ahull", 6); s.put("armor", 6);

        s.put("lwarp", 12); s.put("rwarp", 12); s.put("impulse", 4);
        s.put("apr", 2); s.put("battery", 4);

        s.put("bridge", 2); s.put("emer", 1); s.put("auxcon", 2);

        s.put("damcon",    new int[] { 4, 4, 2, 2, 2, 0 });
        s.put("scanner",   new int[] { 0, 0, 1, 3, 5, 9 });
        s.put("sensor",    new int[] { 6, 6, 5, 3, 1, 0 });
        s.put("excess", 6);
        s.put("controlmod", 0.5);

        s.put("trans", 2); s.put("tractor", 2); s.put("lab", 6);
        s.put("probe", 1); s.put("shuttle", 2);
        s.put("crew", 37); s.put("boardingparties", 8); s.put("minimumcrew", 4);
        s.put("movecost", 3.0 / 4.0); s.put("breakdown", 4); s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        Phaser1 p1 = new Phaser1();
        p1.setArcs(new int[] { 19,20,21,22,23,24,1,2,3,4,5,6,7 }); p1.setDesignator("1");
        weapons.add(p1);
        Phaser1 p2 = new Phaser1();
        p2.setArcs(new int[] { 1,2,3,4,5,6,7,8,9,10,11,12,13 }); p2.setDesignator("2");
        weapons.add(p2);
        Phaser1 p3 = new Phaser1();
        p3.setArcs(new int[] { 13,14,15,16,17,18,19,20,21,22,23,24,1 }); p3.setDesignator("3");
        weapons.add(p3);

        int[] faArc = { 21,22,23,24,1,2,3,4,5 };
        for (String des : new String[] { "A","B" }) {
            Photon ph = new Photon(); ph.setArcs(faArc); ph.setDesignator(des); weapons.add(ph);
        }

        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        rack.setSpaces(4);
        rack.setNumberOfReloads(1);
        rack.setDesignator("Drone Rack");
        rack.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack);

        s.put("weapons", weapons);
        return s;
    }

    /** Federation Improved Frigate — Burke Class (e.g. USS Perry) */
    public static Map<String, Object> getFedFfg() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Federation);
        s.put("hull", "FFG");
        s.put("name", "USS Perry");
        s.put("serviceyear", 160);
        s.put("bpv", 75);
        s.put("turnmode", TurnMode.B);
        s.put("sizeclass", 4);

        s.put("shield1", 18); s.put("shield2", 18); s.put("shield3", 18);
        s.put("shield4", 18); s.put("shield5", 18); s.put("shield6", 18);

        s.put("chull", 6);

        s.put("lwarp", 6); s.put("rwarp", 6); s.put("impulse", 3); s.put("battery", 2);

        s.put("bridge", 2); s.put("emer", 1); s.put("auxcon", 1);

        s.put("damcon",    new int[] { 2, 2, 2, 0 });
        s.put("scanner",   new int[] { 0, 1, 3, 5, 9 });
        s.put("sensor",    new int[] { 6, 5, 3, 1, 0 });
        s.put("excess", 4);
        s.put("controlmod", 1.0);

        s.put("trans", 2); s.put("tractor", 2); s.put("lab", 2);
        s.put("probe", 1); s.put("shuttle", 2);
        s.put("crew", 16); s.put("boardingparties", 6); s.put("minimumcrew", 4);
        s.put("movecost", 1.0 / 3.0); s.put("breakdown", 5); s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        Phaser1 p1 = new Phaser1();
        p1.setArcs(new int[] { 19,20,21,22,23,24,1,2,3,4,5,6,7 }); p1.setDesignator("1");
        weapons.add(p1);
        Phaser1 p2 = new Phaser1();
        p2.setArcs(new int[] { 1,2,3,4,5,6,7,8,9,10,11,12,13 }); p2.setDesignator("2");
        weapons.add(p2);
        Phaser1 p3 = new Phaser1();
        p3.setArcs(new int[] { 13,14,15,16,17,18,19,20,21,22,23,24,1 }); p3.setDesignator("3");
        weapons.add(p3);

        int[] faArc = { 21,22,23,24,1,2,3,4,5 };
        for (String des : new String[] { "A","B" }) {
            Photon ph = new Photon(); ph.setArcs(faArc); ph.setDesignator(des); weapons.add(ph);
        }

        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        rack.setSpaces(4);
        rack.setNumberOfReloads(1);
        rack.setDesignator("Drone Rack");
        rack.setAmmo(ShipDataUtils.makeDrones(4, DroneType.TypeI));
        weapons.add(rack);

        s.put("weapons", weapons);
        return s;
    }
}
