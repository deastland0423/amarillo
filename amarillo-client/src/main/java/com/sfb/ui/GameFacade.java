package com.sfb.ui;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.commands.MoveCommand;
import com.sfb.commands.ShuttleMoveCommand;
import com.sfb.objects.*;
import com.sfb.objects.SuicideShuttle;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.systems.Energy;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

import java.util.List;

/**
 * Abstraction over game state and actions.
 * LocalGameFacade wraps the in-process Game object.
 * ServerGameClient talks to a remote Spring Boot server via REST.
 */
public interface GameFacade {

    // Lifecycle
    void setup();

    // State reads
    List<Ship>                  getShips();
    List<Ship>                  getMovableShips();
    List<com.sfb.objects.Shuttle> getMovableShuttles();
    List<com.sfb.objects.Shuttle> getActiveShuttles();
    List<Seeker>                getSeekers();
    List<SpaceMine>             getMines();
    Game.ImpulsePhase           getCurrentPhase();
    int                         getCurrentTurn();
    int                         getCurrentImpulse();
    int                         getAbsoluteImpulse();
    boolean                     canFireThisPhase();
    boolean                     canLaunchThisPhase();
    int                         getReadyCount();
    int                         getPlayerCount();
    boolean                     isAwaitingAllocation();
    Ship                        nextShipNeedingAllocation();
    int                         getRange(Unit attacker, Unit target);
    int                         getEffectiveRange(Ship attacker, Unit target);
    int                         getShieldNumber(Marker attacker, Ship target);
    List<Weapon>                getBearingWeapons(Ship attacker, Unit target);
    List<SystemTarget>          getTargetableSystems(Ship ship);

    // Actions
    ActionResult advancePhase();
    ActionResult unready();
    ActionResult moveShip(Ship ship, MoveCommand.Action action);
    ActionResult moveShuttle(com.sfb.objects.Shuttle shuttle, ShuttleMoveCommand.Action action);
    ActionResult allocateEnergy(Ship ship, Energy allocation);
    ActionResult launchShuttle(Ship ship, ShuttleBay bay, com.sfb.objects.Shuttle shuttle, int speed, int facing);
    ActionResult launchSuicideShuttle(Ship launcher, com.sfb.objects.SuicideShuttle shuttle, Unit target);
    ActionResult launchScatterPack(Ship launcher, com.sfb.objects.ScatterPack pack, Unit target);
    ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone);
    ActionResult launchPlasma(Ship attacker, Unit target, PlasmaLauncher weapon, boolean pseudo);
    ActionResult fire(Ship attacker, Unit target, List<Weapon> weapons, int range, int adjustedRange, int shieldNumber, boolean useUim);
    ActionResult hitAndRun(Ship actingShip, Ship target, List<SystemTarget> targets);
    ActionResult boardingAction(Ship actingShip, Ship target, int normal, int commandos);
    ActionResult placeTBomb(Ship ship, Location loc, boolean isReal);
    ActionResult cloak(Ship ship);
    ActionResult uncloak(Ship ship);
}
