package com.sfb.systemgroups;

import java.util.ArrayList;
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
        List<List<String>> baySpecs = (List<List<String>>) values.get("shuttlebays");
        if (baySpecs != null) {
            // New format: explicit bay list with typed shuttles
            for (int b = 0; b < baySpecs.size(); b++) {
                ShuttleBay bay = new ShuttleBay(owningUnit);
                List<String> shuttleTypes = baySpecs.get(b);
                for (int s = 0; s < shuttleTypes.size(); s++) {
                    String type = shuttleTypes.get(s);
                    String name = "Bay" + (b + 1) + "-" + type + (s + 1);
                    bay.addShuttle(ShuttleBay.buildShuttle(type, name));
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
    public void cleanUp() {}

    @Override
    public Unit fetchOwningUnit() { return owningUnit; }

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public List<ShuttleBay> getBays() { return bays; }

    /** All shuttles across all bays that are currently in inventory. */
    public List<Shuttle> getAllShuttles() {
        List<Shuttle> all = new ArrayList<>();
        for (ShuttleBay bay : bays) all.addAll(bay.getInventory());
        return all;
    }
}
