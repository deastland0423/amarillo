package com.sfb.samples;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;

/** Package-private helpers shared across faction ship files. */
class ShipDataUtils {

    private ShipDataUtils() {}

    static List<Drone> makeDrones(int count, DroneType type) {
        List<Drone> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Drone drone = new Drone(type);
            drone.setSpeed(32);
            list.add(drone);
        }
        return list;
    }
}
