package com.sfb.commands;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.DroneRack;

/**
 * Command to launch a drone from a rack at a target.
 * Carries enough information to be serialized and sent to a server:
 * the launcher ship, target unit, rack, and specific drone to launch.
 */
public class LaunchDroneCommand implements Command {

    private final Ship     launcher;
    private final Unit     target;
    private final DroneRack rack;
    private final Drone    drone;
    private final int      facing; // 0 = auto-compute from target

    public LaunchDroneCommand(Ship launcher, Unit target, DroneRack rack, Drone drone, int facing) {
        this.launcher = launcher;
        this.target   = target;
        this.rack     = rack;
        this.drone    = drone;
        this.facing   = facing;
    }

    @Override
    public ActionResult execute(Game game) {
        return game.launchDrone(launcher, target, rack, drone, facing);
    }
}
