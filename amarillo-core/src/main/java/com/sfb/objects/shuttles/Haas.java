package com.sfb.objects.shuttles;

import com.sfb.objects.*;
import com.sfb.properties.TurnMode;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Phaser3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Kzinti Advanced Attack Shuttle (J4.43).
 * Year Y163. Speed 24. Hull 4, crippled at 2. BPV 20.
 * Weapons: 1× Ph-1 (FA), 2× DroneRail (one standard drone each).
 * Built-in 2 ECM + 2 ECCM. Controls its own drones only (capacity 2).
 * Must have lock-on to target in FA arc to launch drones (J4.431).
 */
public class Haas extends Fighter implements DroneController {

    private static final int CONTROL_CAPACITY = 2;

    private final Set<Seeker> controlledSeekers = new HashSet<>();
    private final Set<Unit> lockOns = new HashSet<>();

    /** True if a drone was already launched this turn (one-per-turn limit). */
    private boolean dronesFiredThisTurn = false;

    public Haas() {
        setTurnMode(TurnMode.Shuttle);
        setMaxSpeed(15);
        setCurrentSpeed(15);
        setHull(11);
        setCrippledHull(8);
        setBpv(8);

        Phaser3 ph = new Phaser3();
        ph.setDesignator("1");
        ph.setArcs(ArcUtils.FA);
        ph.setArcsFromJSON(List.of("FA"));
        getWeapons().addWeapon(ph);

        DroneRail railA = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railA.setDesignator("A");
        railA.loadDrone(new Drone(DroneType.TypeI));
        getWeapons().addWeapon(railA);

        DroneRail railB = new DroneRail(DroneRail.DroneRailType.STANDARD);
        railB.setDesignator("B");
        railB.loadDrone(new Drone(DroneType.TypeI));
        getWeapons().addWeapon(railB);
    }

    // --- DroneController ---

    @Override
    public boolean acquireControl(Seeker seeker) {
        if (controlledSeekers.size() >= CONTROL_CAPACITY)
            return false;
        controlledSeekers.add(seeker);
        return true;
    }

    @Override
    public void releaseControl(Seeker seeker) {
        controlledSeekers.remove(seeker);
    }

    @Override
    public boolean hasLockOn(Unit target) {
        return lockOns.contains(target);
    }

    @Override
    public int getControlCapacity() {
        return CONTROL_CAPACITY;
    }

    @Override
    public int getControlUsed() {
        return controlledSeekers.size();
    }

    // --- Lock-on management ---

    public void addLockOn(Unit target) {
        lockOns.add(target);
    }

    public void removeLockOn(Unit target) {
        lockOns.remove(target);
    }

    public Set<Unit> getLockOns() {
        return lockOns;
    }

    // --- One-drone-per-turn limit (J4.431) ---

    public boolean isDronesFiredThisTurn() {
        return dronesFiredThisTurn;
    }

    public void recordDroneFired() {
        dronesFiredThisTurn = true;
    }

    @Override
    public void startTurn() {
        super.startTurn();
        dronesFiredThisTurn = false;
    }
}
