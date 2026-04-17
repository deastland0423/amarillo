package com.sfb.scenario;

import com.sfb.objects.DroneType;
import com.sfb.properties.WeaponArmingType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commander's Option Items (COI) selections for a single ship.
 *
 * COI costs (per ScenarioSpec.CommanderOptions.budgetPercent of ship BPV):
 *   Extra boarding party:   0.5 BPV each,  limit 10
 *   Convert BP → commando:  0.5 BPV each,  limit 2
 *   Extra commando squad:   1.0 BPV each,  limit 2
 *   T-bomb:                 4.0 BPV each,  no hard cap (+ 1 free dummy per purchased)
 *   Drone type selection:   free, within year + speed limits
 */
public class CoiLoadout {

    /** Extra normal boarding parties to add (beyond the ship's base count). Max 10. */
    public int extraBoardingParties = 0;

    /**
     * Number of existing normal boarding parties to convert to commandos.
     * Each conversion costs 0.5 BPV. Max 2.
     */
    public int convertBpToCommando = 0;

    /** Extra commando squads to add (beyond the ship's base commandos). Max 2. Each costs 1.0 BPV. */
    public int extraCommandoSquads = 0;

    /** Extra T-bombs to purchase. Each costs 4.0 BPV; each includes 1 free dummy T-bomb. */
    public int extraTBombs = 0;

    /**
     * Drone loadout per rack, keyed by the rack's index in the ship's weapon list
     * (counting only DroneRack entries). Value is the list of DroneType to load into
     * that rack. If a rack index is absent, the rack keeps its default ammo.
     */
    public Map<Integer, List<DroneType>> droneRackLoadouts = new LinkedHashMap<>();

    /**
     * Arming mode overrides for WS-3 heavy weapons, keyed by weapon designator
     * (e.g. "A", "B"). Only applies to weapons that are already fully armed at
     * game start. Absent entries keep their default STANDARD mode.
     * SPECIAL = Proximity. Plasma-R and Fusion beams cannot be overridden (ignored).
     */
    public Map<String, WeaponArmingType> weaponArmingModes = new LinkedHashMap<>();

    // --- Cost calculation helpers ---

    public static final double COST_EXTRA_BP          = 0.5;
    public static final double COST_CONVERT_TO_COMMANDO = 0.5;
    public static final double COST_EXTRA_COMMANDO     = 1.0;
    public static final double COST_TBOMB              = 4.0;

    public static final int MAX_EXTRA_BP              = 10;
    public static final int MAX_CONVERT_TO_COMMANDO   = 2;
    public static final int MAX_EXTRA_COMMANDOS       = 2;

    /** Total BPV cost of this loadout. */
    public double totalCost() {
        return extraBoardingParties * COST_EXTRA_BP
             + convertBpToCommando  * COST_CONVERT_TO_COMMANDO
             + extraCommandoSquads  * COST_EXTRA_COMMANDO
             + extraTBombs          * COST_TBOMB;
    }

    /**
     * Returns the BPV budget for a given ship BPV and budget percentage.
     * E.g. BPV=200, percent=20 → budget=40.
     */
    public static double budget(int shipBpv, int budgetPercent) {
        return Math.floor(shipBpv * budgetPercent / 100.0);
    }

    /**
     * True if this loadout's cost is within the allowed budget.
     */
    public boolean isWithinBudget(int shipBpv, int budgetPercent) {
        return totalCost() <= budget(shipBpv, budgetPercent);
    }

    /**
     * Set the loadout for a specific drone rack index.
     */
    public void setDroneRackLoadout(int rackIndex, List<DroneType> types) {
        droneRackLoadouts.put(rackIndex, new ArrayList<>(types));
    }
}
