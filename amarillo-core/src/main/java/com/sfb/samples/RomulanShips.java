package com.sfb.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.constants.Constants;
import com.sfb.properties.Faction;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

public class RomulanShips {

    /** Romulan KR Cruiser (e.g. RIS Talon) */
    public static Map<String, Object> getRomKr() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Romulan);
        s.put("hull", "KR");
        s.put("name", "RIS Talon");
        s.put("serviceyear", 160);
        s.put("bpv", 115);
        s.put("turnmode", TurnMode.B);
        s.put("sizeclass", 3);

        s.put("shield1", 30);
        s.put("shield2", 22);
        s.put("shield3", 15);
        s.put("shield4", 13);
        s.put("shield5", 15);
        s.put("shield6", 22);

        s.put("fhull", 5);
        s.put("ahull", 7);

        s.put("lwarp", 15);
        s.put("rwarp", 15);
        s.put("impulse", 4);
        s.put("apr", 3);
        s.put("battery", 4);

        s.put("bridge", 2);
        s.put("emer", 1);
        s.put("auxcon", 2);

        s.put("damcon", new int[] { 4, 4, 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 0, 1, 3, 5, 9 });
        s.put("sensor", new int[] { 6, 6, 5, 3, 1, 0 });
        s.put("excess", 4);
        s.put("controlmod", 1.0);

        s.put("trans", 5);
        s.put("tbombs", 1);
        s.put("dummytbombs", 1);
        s.put("tractor", 3);
        s.put("lab", 4);
        s.put("probe", 1);
        s.put("shuttle", 4);
        s.put("crew", 40);
        s.put("boardingparties", 10);
        s.put("minimumcrew", 4);
        s.put("movecost", 1.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        s.put("cloakcost", 20);

        List<Weapon> weapons = new ArrayList<>();

        // Boom phasers (FH + RS + aft)
        int fxAft = ArcUtils.FH | ArcUtils.RS | ArcUtils.of(13);
        Phaser1 p1 = new Phaser1();
        p1.setArcs(fxAft);
        p1.setDesignator("1");
        weapons.add(p1);
        Phaser1 p2 = new Phaser1();
        p2.setArcs(fxAft);
        p2.setDesignator("2");
        weapons.add(p2);
        Phaser1 p3 = new Phaser1();
        p3.setArcs(fxAft);
        p3.setDesignator("3");
        weapons.add(p3);

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

        // Plasma-G launchers (FA), pre-armed for testing
        for (String des : new String[] { "A", "B" }) {
            PlasmaLauncher pl = new PlasmaLauncher(PlasmaType.G);
            pl.setArcs(ArcUtils.FA);
            pl.setDesignator(des);
            pl.setLaunchDirections(ArcUtils.of(1));
            pl.arm(Constants.gArmingCost[0]);
            pl.arm(Constants.gArmingCost[0]);
            pl.arm(Constants.gArmingCost[1]);
            weapons.add(pl);
        }

        s.put("weapons", weapons);
        return s;
    }

    /** Romulan War Eagle Cruiser (e.g. RIS Senator) */
    public static Map<String, Object> getRomWe() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Romulan);
        s.put("hull", "WE");
        s.put("name", "RIS Senator");
        s.put("serviceyear", 162);
        s.put("bpv", 100);
        s.put("turnmode", TurnMode.D);
        s.put("sizeclass", 3);

        s.put("shield1", 25);
        s.put("shield2", 25);
        s.put("shield3", 25);
        s.put("shield4", 25);
        s.put("shield5", 25);
        s.put("shield6", 25);

        s.put("chull", 6);

        s.put("lwarp", 10);
        s.put("rwarp", 10);
        s.put("impulse", 6);
        s.put("battery", 6);

        s.put("bridge", 2);
        s.put("emer", 0);
        s.put("auxcon", 0);

        s.put("damcon", new int[] { 4, 4, 2, 2, 2, 0 });
        s.put("scanner", new int[] { 0, 0, 0, 3, 6, 9 });
        s.put("sensor", new int[] { 6, 6, 5, 4, 2, 0 });
        s.put("excess", 6);
        s.put("controlmod", 1.0);

        s.put("trans", 1);
        s.put("tractor", 1);
        s.put("nuclearSpaceMines", 1);
        s.put("lab", 0);
        s.put("probe", 1);
        s.put("shuttle", 2);
        s.put("crew", 20);
        s.put("boardingparties", 5);
        s.put("minimumcrew", 4);
        s.put("movecost", 1.0);
        s.put("breakdown", 5);
        s.put("bonushets", 1);

        List<Weapon> weapons = new ArrayList<>();

        // Left Phasers (FA + L)
        int faL = ArcUtils.FA | ArcUtils.L;
        Phaser1 p1 = new Phaser1();
        p1.setArcs(faL);
        p1.setDesignator("1");
        weapons.add(p1);
        Phaser1 p2 = new Phaser1();
        p2.setArcs(faL);
        p2.setDesignator("2");
        weapons.add(p2);

        // Right Phasers (FA + R)
        int faR = ArcUtils.FA | ArcUtils.R;
        Phaser1 p3 = new Phaser1();
        p3.setArcs(faR);
        p3.setDesignator("3");
        weapons.add(p3);
        Phaser1 p4 = new Phaser1();
        p4.setArcs(faR);
        p4.setDesignator("4");
        weapons.add(p4);

        // Plasma-R launcher (FA), pre-armed for testing
        PlasmaLauncher pl = new PlasmaLauncher(PlasmaType.R);
        pl.setArcs(ArcUtils.FA);
        pl.setDesignator("A");
        pl.setLaunchDirections(ArcUtils.of(1));
        pl.arm(Constants.rArmingCost[0]);
        pl.arm(Constants.rArmingCost[0]);
        pl.arm(Constants.rArmingCost[1]);
        weapons.add(pl);

        s.put("weapons", weapons);
        return s;
    }
}
