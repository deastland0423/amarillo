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
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.FighterHellbore;
import com.sfb.weapons.FighterPhoton;
import com.sfb.weapons.FighterPlasmaF;
import com.sfb.weapons.Fusion;
import com.sfb.weapons.Hellbore;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Phaser2;
import com.sfb.weapons.Phaser3;
import com.sfb.weapons.Phaser4;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.Photon;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import com.sfb.objects.shuttles.Fighter;

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
            // The phaser-4 is a BASE weapon (E2.0), which is why it was never reachable: the
            // class and its full damage chart have existed all along, but no hull in the data
            // carried one until the Federation Base Station, and the factory had no case for it.
            // ShipWeaponsBuildTest caught it the moment the first BS listed six.
            case "Phaser4": {
                Phaser4 p = new Phaser4();
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
                // DERIVED from the arc (D2.34), because the arc fixes it completely and a hull
                // has no say. A file may still state it — nothing in the data does any more —
                // and a stated list wins, so an exception remains expressible if one is ever
                // found. See ArcUtils.plasmaLaunchDirections for what stating it used to cost.
                pl.setLaunchDirections(ws.launchDirections != null && !ws.launchDirections.isEmpty()
                        ? ArcUtils.calculateMask(ws.launchDirections)
                        : ArcUtils.plasmaLaunchDirections(ws.arcs));
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
            case "PlasmaRack": {
                // FP10.0, the ship-mounted type-D launcher. Capacity is fixed at four by
                // FP10.14 and reload sets at one by FP10.312, so neither is read from data -
                // a ship file cannot declare a bigger one because the rule forbids it.
                com.sfb.weapons.PlasmaRack pr = new com.sfb.weapons.PlasmaRack();
                pr.setArcs(arcMask);
                pr.setDesignator(ws.designator);
                return pr;
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
                // No reserve to read: E5.71 gives every ADD two full sets, so the rack
                // is the only thing a ship file has to say.
                ADD add = new ADD(addType);
                add.setDesignator(ws.designator);
                return add;
            }
            case "ScoutChannel": {
                // A scout function channel (G24.0). It occupies the DAC hit location of the
                // weapon it replaced (G24.17); default to "torp" if unspecified.
                com.sfb.weapons.ScoutChannel ch = new com.sfb.weapons.ScoutChannel();
                ch.setDesignator(ws.designator);
                ch.setDacHitLocaiton(ws.dacHitLocation != null ? ws.dacHitLocation : "torp");
                return ch;
            }
            case "ESG": {
                // ESG has no firing arc — it generates a field (G23.0). Slice 1:
                // no capacitor; the with-capacitor variant is a later slice.
                com.sfb.weapons.ESG esg = new com.sfb.weapons.ESG();
                esg.setDesignator(ws.designator);
                return esg;
            }
            // ---- Fighter-borne weapons (J4.8x) ----
            // A fighter carries no reactor, so these run on CHARGES from its box rather than
            // arming energy. They are ordinary recipes all the same: what differs is the weapon
            // class, not the way a ship file names it.
            case "FighterFusion": {
                FighterFusion ff = new FighterFusion();
                ff.setArcs(arcMask);
                ff.setDesignator(ws.designator);
                return ff;
            }
            case "FighterHellbore": {
                FighterHellbore fh = new FighterHellbore();
                fh.setArcs(arcMask);
                fh.setDesignator(ws.designator);
                return fh;
            }
            case "FighterDisruptor": {
                FighterDisruptor fd = new FighterDisruptor();
                fd.setArcs(arcMask);
                fd.setDesignator(ws.designator);
                return fd;
            }
            case "FighterPhoton": {
                FighterPhoton fp = new FighterPhoton();
                fp.setArcs(arcMask);
                fp.setDesignator(ws.designator);
                return fp;
            }
            case "FighterPlasmaF": {
                // Always type F (J4.27), so plasmaType is not read - a data file naming
                // anything else would be declaring a weapon no fighter carries.
                FighterPlasmaF fpf = new FighterPlasmaF();
                fpf.setArcs(arcMask);
                fpf.setDesignator(ws.designator);
                // Same launch-direction handling a ship's launcher gets: the ARC is what the
                // torpedo may be launched at, the DIRECTION is the facing it leaves on, and a
                // fighter's is forward.
                fpf.setLaunchDirections(ws.launchDirections != null && !ws.launchDirections.isEmpty()
                        ? ArcUtils.calculateMask(ws.launchDirections)
                        : ArcUtils.plasmaLaunchDirections(ws.arcs));
                return fpf;
            }
            /*
             * A rail is not a rack: the RAIL decides what will fit on it and what that costs
             * (J4.231), so railType is required rather than defaulted. A rail of the wrong type
             * silently accepts the wrong drones, which is worse than refusing to build.
             */
            case "DroneRail": {
                if (ws.railType == null) {
                    System.err.println("DroneRail " + ws.designator + " has no railType");
                    return null;
                }
                DroneRail rail = new DroneRail(DroneRail.DroneRailType.valueOf(ws.railType));
                rail.setDesignator(ws.designator);
                return rail;
            }
            default:
                System.err.println("Unknown weapon type: " + ws.type);
                return null;
        }
    }
}
