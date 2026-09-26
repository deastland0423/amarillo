package com.sfb.server;

import com.sfb.objects.DroneType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.scenario.CoiLoadout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * JSON body for a single ship's COI selections in POST /api/games/{id}/coi.
 */
public class CoiRequest {

    public int    extraBoardingParties = 0;
    public int    convertBpToCommando  = 0;
    public int    extraCommandoSquads  = 0;
    public int    extraTBombs          = 0;

    /** Rack index (as string key) → list of DroneType names. */
    public Map<String, List<String>> droneRackLoadouts = new LinkedHashMap<>();

    /**
     * Rack index (as string key) → anti-drone rounds to load, type-G only (FD3.70).
     *
     * Separate from droneRackLoadouts because an anti-drone is not a DroneType; they share
     * the rack's four spaces, at half a space each, and the loader budgets them together.
     */
    public Map<String, Integer> antiDroneLoadouts = new LinkedHashMap<>();

    /** Weapon designator → arming mode name ("STANDARD", "OVERLOAD", "SPECIAL"). */
    public Map<String, String> weaponArmingModes = new LinkedHashMap<>();

    /**
     * Photon designator → free WS-III overload energy for that tube (S4.32).
     *
     * Half-points are legal (E4.414), so this is fractional. Without a field here Jackson
     * drops the key on arrival and the selection vanishes between the dialog and the ship —
     * silently, because an empty map is exactly what "no overload" looks like.
     */
    public Map<String, Double> photonOverload = new LinkedHashMap<>();

    /** Orion option-mount choices (G15.4): mount designator → Annex #8B option name. */
    public Map<String, String> optionMounts = new LinkedHashMap<>();

    /** The fleet's Orion cartel (G15.44), echoed on each ship's request; null if none. */
    public String cartel;

    /** Pre-game special shuttle preparations. */
    public static class ShuttlePrepRequest {
        public String shuttleName   = "";
        public String type          = "";   // "suicide" or "scatterpack"
        public int    energyPerTurn = 3;    // suicide only
        public List<String> drones  = new ArrayList<>();  // scatterpack only
    }
    public List<ShuttlePrepRequest> specialShuttlePrep = new ArrayList<>();

    public CoiLoadout toLoadout() {
        CoiLoadout out = new CoiLoadout();
        out.extraBoardingParties = extraBoardingParties;
        out.convertBpToCommando  = convertBpToCommando;
        out.extraCommandoSquads  = extraCommandoSquads;
        out.extraTBombs          = extraTBombs;

        for (Map.Entry<String, List<String>> entry : droneRackLoadouts.entrySet()) {
            int rackIndex;
            try { rackIndex = Integer.parseInt(entry.getKey()); }
            catch (NumberFormatException e) { continue; }
            List<DroneType> types = new ArrayList<>();
            for (String name : entry.getValue()) {
                try { types.add(DroneType.valueOf(name)); }
                catch (IllegalArgumentException e) { /* skip unknown types */ }
            }
            out.droneRackLoadouts.put(rackIndex, types);
        }

        if (antiDroneLoadouts != null) {
            for (Map.Entry<String, Integer> entry : antiDroneLoadouts.entrySet()) {
                if (entry.getValue() == null || entry.getValue() <= 0)
                    continue;
                try {
                    out.antiDroneLoadouts.put(Integer.parseInt(entry.getKey()), entry.getValue());
                } catch (NumberFormatException e) { /* skip unparsable rack keys */ }
            }
        }

        for (Map.Entry<String, String> entry : weaponArmingModes.entrySet()) {
            try {
                out.weaponArmingModes.put(entry.getKey(), WeaponArmingType.valueOf(entry.getValue()));
            } catch (IllegalArgumentException e) { /* skip unknown modes */ }
        }

        if (photonOverload != null) {
            out.photonOverload.putAll(photonOverload);
        }

        if (optionMounts != null) {
            out.optionMounts.putAll(optionMounts);
        }

        if (specialShuttlePrep != null) {
            for (ShuttlePrepRequest req : specialShuttlePrep) {
                if (req.shuttleName == null || req.shuttleName.isBlank()) continue;
                CoiLoadout.SpecialShuttlePrep prep = new CoiLoadout.SpecialShuttlePrep();
                prep.shuttleName   = req.shuttleName;
                prep.type          = req.type;
                prep.energyPerTurn = Math.max(1, Math.min(3, req.energyPerTurn));
                if (req.drones != null) {
                    for (String name : req.drones) {
                        try { prep.drones.add(DroneType.valueOf(name)); }
                        catch (IllegalArgumentException e) { /* skip unknown types */ }
                    }
                }
                out.specialShuttlePrep.add(prep);
            }
        }

        return out;
    }
}
