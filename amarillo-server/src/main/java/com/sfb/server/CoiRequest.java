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

    /** Weapon designator → arming mode name ("STANDARD", "OVERLOAD", "SPECIAL"). */
    public Map<String, String> weaponArmingModes = new LinkedHashMap<>();

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

        for (Map.Entry<String, String> entry : weaponArmingModes.entrySet()) {
            try {
                out.weaponArmingModes.put(entry.getKey(), WeaponArmingType.valueOf(entry.getValue()));
            } catch (IllegalArgumentException e) { /* skip unknown modes */ }
        }

        return out;
    }
}
