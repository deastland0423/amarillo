package com.sfb.utilities;

import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * Annex 7E DAC hit priority tables (D4.3221–3).
 *
 * Lower return value = higher priority (must be destroyed first under the rule of 3).
 * Integer.MAX_VALUE = unrecognised or unimplemented type (treated as lowest priority).
 *
 * All three lists are complete as of the Annex 7E source, including weapons not yet
 * implemented in Amarillo. Adding a new weapon class only requires its type string to
 * appear here — no changes needed anywhere else.
 */
public final class DacPriority {

    private DacPriority() {}

    /** A scout channel that replaced a weapon of the given DAC location (G24.17). */
    private static boolean isScoutChannelFor(Weapon w, String location) {
        return w instanceof com.sfb.weapons.ScoutChannel && location.equals(w.getDacHitLocaiton());
    }

    // -------------------------------------------------------------------------
    // Phaser category (D4.3221)
    // -------------------------------------------------------------------------

    /**
     * Priority for phaser-class weapons.
     * Priority 0 reserved for "special sensors that replaced phasers" (no type yet).
     */
    public static int phaserPriority(Weapon w) {
        if (isScoutChannelFor(w, "phaser"))
            return 0; // special sensors take required hits first (G24.17)
        switch (w.getType()) {
            case "StasisFieldGenerator":     return  1;
            case "PhBank":                   return  2;
            case "Phaser4X":                 return  3;
            case "Phaser4":                  return  4;
            case "HeavyWarpTunedLaser":      return  5;
            case "MegaGaussCannon":          return  6;
            case "Phaser1X":                 return  7;
            case "Phaser1":                  return  8;
            case "AntiFighterDefenseSystem": return  9;
            case "MediumWarpTunedLaser":     return 10;
            case "GaussCannon":              return 11;
            case "PhaserGX":                 return 12;
            case "PhaserG":                  return 13;
            case "Phaser2X":                 return 14;
            case "Phaser2":                  return 15;
            case "EarlyWarpTunedLaser":      return 16;
            case "ImprovedPulseEmitter":     return 17;
            case "PulseEmitter":             return 18;
            case "DarkMatterPulsar":         return 19;
            case "TwinWarpTunedLaser":       return 20;
            case "SonicPulser":              return 21;
            case "Phaser3X":                 return 22;
            case "Phaser3":                  return 23;
            case "LightDarkMatterPulsar":    return 24;
            case "LightWarpTunedLaser":      return 25;
            case "WarpTargetedLaser":        return 26;
            default:                         return Integer.MAX_VALUE;
        }
    }

    // -------------------------------------------------------------------------
    // Torpedo category (D4.3222)
    // -------------------------------------------------------------------------

    /**
     * Priority for torpedo-category weapons.
     * Priority 0 reserved for "special sensor replacing torpedo" (no type yet).
     * Note: PlasmaLauncher sets type "Plasma" without size suffix — cannot distinguish
     * plasma-R from plasma-G until PlasmaLauncher stores size; "Plasma" falls to MAX_VALUE.
     */
    public static int torpPriority(Weapon w) {
        if (isScoutChannelFor(w, "torp"))
            return 0; // special sensors take required hits first (G24.17)
        switch (w.getType()) {
            case "Jammer":                    return  1;
            case "FocusedEnergyBeam":         return  2;
            case "WarpRailgun":               return  3;
            case "MediumRailgun":             return  4;
            case "LightRailgun":              return  5;
            case "TachyosonicBeam":           return  6;
            case "PlasmaR":                   return  7;
            case "ImplosionS":                return  8;
            case "PlasmaM":                   return  9;
            case "PlasmaA":                   return 10;
            case "HeavyKineticWaveGenerator": return 11;
            case "PlasmaS":                   return 12;
            case "ImplosionH":                return 13;
            case "Fireball":                  return 14;
            case "SubspaceRocket":            return 15;
            case "ParticleBeam":              return 16;
            case "HEAT":                      return 17;
            case "TachyonBeam":               return 18;
            case "PositronLancet":            return 19;
            case "KineticCannonHeavy":        return 20;
            case "KineticCannonMedium":       return 21;
            case "KineticCannonLight":        return 22;
            case "TransporterCollectorBeam":  return 23;
            case "TractorRepulsorHeavy":      return 24;
            case "ChargedParticleAccelerator":return 25;
            case "TractorRepulsorLight":      return 26;
            case "DarkMatterTorpedo":         return 27;
            case "HeavyPhoton":               return 28;
            case "Photon":                    return 29;
            case "LightPhoton":               return 30;
            case "TachyonGun":                return 31;
            case "AntiprotonLance":           return 32;
            case "AntiprotonBeam":            return 33;
            case "EnergySparkRing":           return 34;
            case "PlasmaL":                   return 35;
            case "LightKineticWaveGenerator": return 36;
            case "QuantumWaveTorpedo":        return 37;
            case "PlasmaG":                   return 38;
            case "IonCannon":                 return 39;
            case "ImplosionM":                return 40;
            case "ParticleCannon":            return 41;
            case "BioelectricBolts":          return 42;
            case "Disruptor40":               return 43;
            case "HeavyHypercannon":          return 44;
            case "DisruptorCannon30":         return 45;
            case "Disruptor30":               return 46;
            case "BosonDrill":                return 47;
            case "AntiMatterCannon":          return 48;
            case "DisruptorCannon22":         return 49;
            case "Disruptor22":               return 50;
            case "EnergyHowitzer":            return 51;
            case "LightHypercannon":          return 52;
            case "PlasmaVortexLauncher":      return 53;
            case "DisruptorCannon15":         return 54;
            case "PlasmaCannon":              return 55;
            case "Disruptor15":               return 56;
            case "QuantumCannon":             return 57;
            case "DisruptorCannon10":         return 58;
            case "Disruptor10":               return 59;
            case "AxionTorpedo":              return 60;
            case "Fusion":                    return 61;
            case "NovaCannon":                return 62;
            case "StingTorpedo":              return 63;
            case "PlasmaF":                   return 64;
            case "PlasmaE":                   return 65;
            case "ImplosionL":                return 66;
            case "PlasmaV":                   return 67;
            case "PlasmaBlaster":             return 68;
            case "PlasmaDRack":               return 69;
            case "ProspectingCannon":         return 70;
            default:                          return Integer.MAX_VALUE;
        }
    }

    // -------------------------------------------------------------------------
    // Drone category (D4.3223)
    // -------------------------------------------------------------------------

    /**
     * Priority for drone-category weapons.
     * Priority 0 reserved for "special sensor replacing drone-weapon" (no type yet).
     * DroneRack priority is resolved via getRackType() since getType() always returns "Drone".
     */
    public static int dronePriority(Weapon w) {
        if (isScoutChannelFor(w, "drone"))
            return 0; // special sensors take required hits first (G24.17)
        if (w instanceof DroneRack) {
            DroneRack rack = (DroneRack) w;
            if (rack.getRackType() != null) {
                switch (rack.getRackType()) {
                    case TYPE_D: return 25; // magazine of D-rack
                    case TYPE_H: return 27; // magazine of H-rack
                    case TYPE_G: return 38; // G-rack
                    case TYPE_B: return 42; // B-rack
                    case TYPE_C: return 45; // C-rack
                    case TYPE_E: return 47; // E-rack
                    case TYPE_F: return 48; // F-rack
                    case TYPE_A: return 50; // A-rack
                    default:     return Integer.MAX_VALUE;
                }
            }
            return Integer.MAX_VALUE;
        }
        switch (w.getType()) {
            case "TargetAcquisitionGear":         return  1;
            case "HyperdroneMagazine":            return  2;
            case "IonStormGenerator":             return  3;
            case "PlasmaticPulsarDevice":         return  4;
            case "TargetAccentuators":            return  5;
            case "TargetIlluminator":             return  6;
            case "WebCaster":                     return  7;
            case "WebBreaker":                    return  8;
            case "ShieldCracker":                 return  9;
            case "FlameShieldGenerator":          return 10;
            case "FlameShield":                   return 11;
            case "Hellbore":                      return 12;
            case "AntiMatterCloudGenerator":      return 13;
            case "NeutronBeam":                   return 14;
            case "TransMortar":                   return 15;
            case "ExpandingSphereGenerator":      return 16;
            case "IonPulseGenerator":             return 17;
            case "HeavyHypercannon":              return 18;
            case "PowerAbsorberPanel":            return 19;
            case "ImplosionBolt":                 return 20;
            case "BioelectricBolts":              return 21;
            case "SubspaceCoagulator":            return 22;
            case "NeutronGun":                    return 23;
            case "Hellgun":                       return 24;
            case "ScudLauncher":                  return 26;
            case "ClassIIIMassDriver":            return 28;
            case "GxRack":                        return 29;
            case "CxRack":                        return 30;
            case "TachyonRackE":                  return 31;
            case "UltrawarpMissileRack":          return 32;
            case "TachyonRackD":                  return 33;
            case "TachyonRackC":                  return 34;
            case "TachyonRackB":                  return 35;
            case "TachyonRackA":                  return 36;
            case "ParticleSplitterTorpedo":       return 37;
            case "MissileRack":                   return 39;
            case "ClassIIMassDriver":             return 40;
            case "TransporterEmitterMissileRack": return 41;
            case "LightHypercannon":              return 43;
            case "DeathBoltRack":                 return 44;
            case "PlasmaPRack":                   return 46;
            case "ClassIMassDriver":              return 49;
            case "ChaffThrower":                  return 51;
            case "ShortRangeCannon":              return 52;
            case "HeelNipper":                    return 53;
            case "StarbaseADD":                   return 54;
            case "AntiFighterDefenseSystem":      return 55;
            case "ADD12":                         return 56;
            case "ADD":                           return 57; // ADD-6 class
            case "AtomicMissileRack":             return 58;
            default:                              return Integer.MAX_VALUE;
        }
    }
}
