package com.sfb.samples;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.OptionMount;
import com.sfb.properties.Faction;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Weapon;

/**
 * Orion sample ships. Mirrors {@code data/factions/orion/*.json} — keep the two
 * in sync (tests build from here; the app loads from JSON).
 */
public class OrionShips {

    /** Orion Light Raider (LR) — R8.7. Nimble size-4 raider; +2 stealth; cloak; 3 OPT mounts. */
    public static Map<String, Object> getLr() {
        Map<String, Object> s = new HashMap<>();

        s.put("faction", Faction.Orion);
        s.put("hull", "LR");
        s.put("name", "Lady Luck");
        s.put("serviceyear", 129);
        s.put("bpv", 68);
        s.put("turnmode", TurnMode.AA);
        s.put("sizeclass", 4);
        s.put("movecost", 1.0 / 3.0);
        s.put("breakdown", 6);
        s.put("bonushets", 2);
        s.put("nimble", true);
        s.put("stealthbonus", 2);

        s.put("shield1", 15);
        s.put("shield2", 15);
        s.put("shield3", 15);
        s.put("shield4", 15);
        s.put("shield5", 15);
        s.put("shield6", 15);

        s.put("chull", 3);
        s.put("cargo", 3);

        s.put("lwarp", 5);
        s.put("rwarp", 5);
        s.put("impulse", 2);
        s.put("battery", 3);

        s.put("bridge", 1);
        s.put("auxcon", 1);
        s.put("controlmod", 1.0);

        s.put("damcon", new int[] { 4, 2, 2, 0 });
        s.put("sensor", new int[] { 6, 5, 1, 0 });
        s.put("scanner", new int[] { 0, 1, 5, 9 });
        s.put("excess", 4);

        s.put("trans", 2);
        s.put("tractor", 2);
        s.put("shuttle", 2);
        s.put("cloakcost", 6);

        s.put("crew", 12);
        s.put("boardingparties", 8);
        s.put("minimumcrew", 3);

        // Three Phaser-1s, all 360° (any may be fired as a Ph-3 to save energy)
        List<Weapon> weapons = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            Phaser1 p = new Phaser1();
            p.setArcs(ArcUtils.FULL);
            p.setDesignator(String.valueOf(i));
            weapons.add(p);
        }
        s.put("weapons", weapons);

        // Optional weapon mounts (G15.4): one centerline (A), two wing (B, C)
        List<OptionMount> mounts = new ArrayList<>();
        mounts.add(new OptionMount(OptionMount.Position.CENTERLINE, "A", List.of("FA")));
        mounts.add(new OptionMount(OptionMount.Position.WING, "B", List.of("LS")));
        mounts.add(new OptionMount(OptionMount.Position.WING, "C", List.of("RS")));
        s.put("optionmounts", mounts);

        return s;
    }
}
