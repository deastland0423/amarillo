package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.shuttles.Shuttle;
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
                        Shuttle shuttle = ShuttleBay.buildShuttle(type, name);
                        bay.addSpace(new ShuttleSpace(shuttle));
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
                    Shuttle shuttle = ShuttleBay.buildShuttle("admin", "Shuttle" + (i + 1));
                    bay.addSpace(new ShuttleSpace(shuttle));
                }
                bays.add(bay);
            }
        }
    }

    @Override
    public int fetchOriginalTotalBoxes() {
        return bays.stream().mapToInt(ShuttleBay::getTotalSpaces).sum();
    }

    @Override
    public int fetchRemainingTotalBoxes() {
        return bays.stream().mapToInt(ShuttleBay::getRemainingSpaces).sum();
    }

    @Override
    public void cleanUp() {
        for (ShuttleBay bay : bays) {
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                if (s instanceof com.sfb.objects.shuttles.ScatterPack) {
                    ((com.sfb.objects.shuttles.ScatterPack) s).applyPendingPayload();
                } else if (s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                    com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) s;
                    // Nothing paid for this turn, by either route: all the arming is lost and
                    // it is a plain admin shuttle again. While arming, the arming energy is
                    // the upkeep; once fully armed, the 1-point hold is.
                    if (ss.isArmed() && !ss.isUpkeepPaid()) {
                        com.sfb.objects.shuttles.AdminShuttle admin = new com.sfb.objects.shuttles.AdminShuttle();
                        admin.setName(ss.getName());
                        admin.setMaxSpeed(ss.getMaxSpeed());
                        admin.setHull(ss.getHull());
                        admin.setCurrentHull(ss.getCurrentHull());
                        // Through the BAY. getInventory() builds a fresh list on every call,
                        // so the old inv.set(i, admin) rewrote a throwaway copy and the
                        // shuttle was never actually reverted - the whole lapse was a no-op.
                        bay.replaceShuttle(ss, admin);
                    } else {
                        ss.resetUpkeep();
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
