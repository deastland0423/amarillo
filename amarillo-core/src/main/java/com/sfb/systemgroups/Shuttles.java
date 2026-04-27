package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.Shuttle;
import com.sfb.objects.Unit;

public class Shuttles implements Systems {

    private final List<ShuttleBay> bays = new ArrayList<>();
    private Unit owningUnit;

    public Shuttles(Unit owner) {
        this.owningUnit = owner;
    }

    // -------------------------------------------------------------------------
    // Systems interface
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void init(Map<String, Object> values) {
        List<Object> rawBays = (List<Object>) values.get("shuttlebays");
        if (rawBays != null) {
            // Supports two formats per element:
            //   Old: ["stinger1", "stinger1"]          — list of type strings
            //   New: {"shuttles":["stinger1"],"launchTubes":2} — object with optional tube count
            Map<String, Integer> typeCount = new HashMap<>();
            for (Object rawBay : rawBays) {
                ShuttleBay bay = new ShuttleBay(owningUnit);
                List<String> shuttleTypes;
                if (rawBay instanceof Map) {
                    Map<String, Object> bayObj = (Map<String, Object>) rawBay;
                    shuttleTypes = (List<String>) bayObj.get("shuttles");
                    Object tubesObj = bayObj.get("launchTubes");
                    if (tubesObj instanceof Number)
                        bay.setLaunchTubeCount(((Number) tubesObj).intValue());
                } else {
                    shuttleTypes = (List<String>) rawBay;
                }
                if (shuttleTypes != null) {
                    for (String type : shuttleTypes) {
                        int count = typeCount.merge(type, 1, Integer::sum);
                        String name = displayName(type) + "-" + count;
                        bay.addShuttle(ShuttleBay.buildShuttle(type, name));
                    }
                }
                bays.add(bay);
            }
        } else {
            // Legacy format: single integer count, all admin shuttles in one bay
            int count = values.get("shuttle") == null ? 0 : (Integer) values.get("shuttle");
            if (count > 0) {
                ShuttleBay bay = new ShuttleBay(owningUnit);
                for (int i = 0; i < count; i++) {
                    bay.addShuttle(ShuttleBay.buildShuttle("admin", "Shuttle" + (i + 1)));
                }
                bays.add(bay);
            }
        }
    }

    @Override
    public int fetchOriginalTotalBoxes() {
        return bays.stream().mapToInt(ShuttleBay::getCapacity).sum();
    }

    @Override
    public int fetchRemainingTotalBoxes() {
        return bays.stream().mapToInt(b -> b.getInventory().size()).sum();
    }

    @Override
    public void cleanUp() {
        for (ShuttleBay bay : bays) {
            java.util.List<com.sfb.objects.Shuttle> inv = bay.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                com.sfb.objects.Shuttle s = inv.get(i);
                if (s instanceof com.sfb.objects.ScatterPack) {
                    ((com.sfb.objects.ScatterPack) s).applyPendingPayload();
                } else if (s instanceof com.sfb.objects.SuicideShuttle) {
                    com.sfb.objects.SuicideShuttle ss = (com.sfb.objects.SuicideShuttle) s;
                    if (ss.isArmed() && !ss.isHoldPaid()) {
                        // Hold energy not paid — revert to plain admin shuttle
                        com.sfb.objects.AdminShuttle admin = new com.sfb.objects.AdminShuttle();
                        admin.setName(ss.getName());
                        admin.setMaxSpeed(ss.getMaxSpeed());
                        admin.setHull(ss.getHull());
                        inv.set(i, admin);
                    } else {
                        ss.resetHold();
                    }
                }
            }
        }
    }

    @Override
    public Unit fetchOwningUnit() { return owningUnit; }

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public List<ShuttleBay> getBays() { return bays; }

    /** Prepend the ship name to every bayed shuttle's name. Called after the ship gets its scenario name. */
    public void prefixShuttleNames(String shipName) {
        for (ShuttleBay bay : bays) {
            for (Shuttle s : bay.getInventory()) {
                s.setName(shipName + "-" + s.getName());
            }
        }
    }

    /** All shuttles across all bays that are currently in inventory. */
    public List<Shuttle> getAllShuttles() {
        List<Shuttle> all = new ArrayList<>();
        for (ShuttleBay bay : bays) all.addAll(bay.getInventory());
        return all;
    }

    /** Map shuttle type strings to display-friendly names. */
    private static String displayName(String type) {
        switch (type.toLowerCase()) {
            case "admin":       return "Admin";
            case "gas":         return "GAS";
            case "hts":         return "HTS";
            case "suicide":     return "Suicide";
            case "scatterpack": return "ScatterPack";
            case "stinger1":    return "Stinger1";
            case "stinger2":    return "Stinger2";
            case "stingerh":    return "StingerH";
            default:
                // Capitalize first letter for unknown types
                return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }
}
