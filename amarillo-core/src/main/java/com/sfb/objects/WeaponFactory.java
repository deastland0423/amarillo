package com.sfb.objects;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.ShipSpec.WeaponSpec;
import com.sfb.properties.PlasmaType;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.ADD;
import com.sfb.weapons.ADD.AddType;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Fusion;
import com.sfb.weapons.Hellbore;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.Photon;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

/**
 * Builds a {@link Weapon} instance from a {@link WeaponSpec} recipe. Extracted
 * from {@code ShipSpec} so the recipe → weapon translation has a single home,
 * reused by ship loading, pinned option mounts (G15.4), and the option-mount
 * loadout catalog. A recipe whose {@code type} has no implementing class yields
 * null — that is how "not yet implemented" is represented.
 */
public final class WeaponFactory {

    private WeaponFactory() {}

    /**
     * Build a weapon for the given arc labels (e.g. ["FA"]), setting both the
     * arc bitmask and the human-readable label. Returns null for an unknown or
     * absent recipe.
     */
    public static Weapon build(WeaponSpec ws, List<String> arcs) {
        if (ws == null) {
            return null;
        }
        List<String> resolved = (arcs == null || arcs.isEmpty()) ? List.of("FULL") : arcs;
        Weapon w = build(ws, ArcUtils.calculateMask(resolved));
        if (w != null) {
            w.setArcsFromJSON(resolved); // sets both bitmask and arcLabel
        }
        return w;
    }

    /** Build a weapon with a pre-computed arc bitmask. Returns null for an unknown recipe. */
    public static Weapon build(WeaponSpec ws, int arcMask) {
        if (ws == null || ws.type == null) {
            return null;
        }
        switch (ws.type) {
            case "Phaser1": {
                Phaser1 p = new Phaser1();
                p.setArcs(arcMask);
                p.setDesignator(ws.designator);
                return p;
            }
            case "Phaser2": {
                Phaser2 p = new Phaser2();
                p.setArcs(arcMask);
                p.setDesignator(ws.designator);
                return p;
            }
            case "Phaser3": {
                Phaser3 p = new Phaser3();
                p.setArcs(arcMask);
                p.setDesignator(ws.designator);
                return p;
            }
            case "Photon": {
                Photon p = new Photon();
                p.setArcs(arcMask);
                p.setDesignator(ws.designator);
                return p;
            }
            case "Disruptor": {
                Disruptor d = new Disruptor(ws.range > 0 ? ws.range : 30);
                d.setArcs(arcMask);
                d.setDesignator(ws.designator);
                return d;
            }
            case "PlasmaLauncher": {
                PlasmaType pt = PlasmaType.valueOf(ws.plasmaType != null ? ws.plasmaType : "R");
                PlasmaLauncher pl = new PlasmaLauncher(pt);
                pl.setArcs(arcMask);
                pl.setDesignator(ws.designator);
                if (ws.launchDirections != null && !ws.launchDirections.isEmpty()) {
                    pl.setLaunchDirections(ArcUtils.calculateMask(ws.launchDirections));
                }
                return pl;
            }
            case "DroneRack": {
                DroneRack.DroneRackType rackType = ws.rackType != null
                        ? DroneRack.DroneRackType.valueOf(ws.rackType)
                        : DroneRack.DroneRackType.TYPE_F;
                DroneRack rack = new DroneRack(rackType);
                if (ws.spaces > 0) {
                    rack.setSpaces(ws.spaces);
                }
                rack.setDesignator(ws.designator);
                // Default ammo: fill all spaces with TypeI drones; setAmmo builds reloads automatically.
                List<Drone> ammo = new ArrayList<>();
                for (int i = 0; i < rack.getSpaces(); i++) {
                    ammo.add(new Drone(DroneType.TypeI));
                }
                rack.setAmmo(ammo);
                return rack;
            }
            case "PhaserG": {
                PhaserG pg = new PhaserG();
                pg.setArcs(arcMask);
                pg.setDesignator(ws.designator);
                return pg;
            }
            case "Fusion": {
                Fusion f = new Fusion();
                f.setArcs(arcMask);
                f.setDesignator(ws.designator);
                return f;
            }
            case "Hellbore": {
                Hellbore h = new Hellbore();
                h.setArcs(arcMask);
                h.setDesignator(ws.designator);
                return h;
            }
            case "ADD": {
                AddType addType = ws.addType != null ? AddType.valueOf(ws.addType) : AddType.ADD_12;
                ADD add = new ADD(addType, ws.shots > 0 ? ws.shots : 2);
                add.setDesignator(ws.designator);
                return add;
            }
            case "ESG": {
                // ESG has no firing arc — it generates a field (G23.0). Slice 1:
                // no capacitor; the with-capacitor variant is a later slice.
                com.sfb.weapons.ESG esg = new com.sfb.weapons.ESG();
                esg.setDesignator(ws.designator);
                return esg;
            }
            default:
                System.err.println("Unknown weapon type: " + ws.type);
                return null;
        }
    }
}
