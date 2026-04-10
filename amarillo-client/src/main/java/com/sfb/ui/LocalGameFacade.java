package com.sfb.ui;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.commands.*;
import com.sfb.objects.*;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.systems.Energy;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

import java.util.List;

/**
 * GameFacade implementation for local single-machine play.
 * Every method delegates directly to the in-process Game object.
 */
public class LocalGameFacade implements GameFacade {

    private final Game game = new Game();

    @Override public void setup()                                           { game.setup(); }

    @Override public List<Ship>   getShips()                               { return game.getShips(); }
    @Override public List<Ship>   getMovableShips()                        { return game.getMovableShips(); }
    @Override public List<com.sfb.objects.Shuttle> getMovableShuttles()   { return game.getMovableShuttles(); }
    @Override public List<com.sfb.objects.Shuttle> getActiveShuttles()    { return game.getActiveShuttles(); }
    @Override public List<Seeker>     getSeekers()                         { return game.getSeekers(); }
    @Override public List<SpaceMine>  getMines()                           { return game.getMines(); }
    @Override public Game.ImpulsePhase getCurrentPhase()                   { return game.getCurrentPhase(); }
    @Override public int getCurrentTurn()                                   { return game.getCurrentTurn(); }
    @Override public int getCurrentImpulse()                                { return game.getCurrentImpulse(); }
    @Override public int getAbsoluteImpulse()                              { return game.getAbsoluteImpulse(); }
    @Override public boolean canFireThisPhase()                            { return game.canFireThisPhase(); }
    @Override public boolean canLaunchThisPhase()                          { return game.canLaunchThisPhase(); }
    @Override public int     getReadyCount()                               { return 0; }
    @Override public int     getPlayerCount()                              { return 1; }
    @Override public boolean isAwaitingAllocation()                        { return game.isAwaitingAllocation(); }
    @Override public Ship    nextShipNeedingAllocation()                   { return game.nextShipNeedingAllocation(); }
    @Override public int     getRange(Unit a, Unit t)                      { return game.getRange(a, t); }
    @Override public int     getEffectiveRange(Ship a, Unit t)             { return game.getEffectiveRange(a, t); }
    @Override public int     getShieldNumber(Marker a, Ship t)             { return game.getShieldNumber(a, t); }
    @Override public List<Weapon>       getBearingWeapons(Ship a, Unit t)  { return game.getBearingWeapons(a, t); }
    @Override public List<SystemTarget> getTargetableSystems(Ship s)       { return game.getTargetableSystems(s); }

    @Override public ActionResult advancePhase()                           { return game.execute(new AdvancePhaseCommand()); }
    @Override public ActionResult unready()                                { return ActionResult.ok(""); } // no-op in local play
    @Override public ActionResult moveShip(Ship s, MoveCommand.Action a)   { return game.execute(new MoveCommand(s, a)); }
    @Override public ActionResult moveShuttle(com.sfb.objects.Shuttle s, ShuttleMoveCommand.Action a) {
        return game.execute(new ShuttleMoveCommand(s, a));
    }
    @Override public ActionResult allocateEnergy(Ship s, Energy e)         { return game.execute(new AllocateEnergyCommand(s, e)); }
    @Override public ActionResult launchShuttle(Ship ship, ShuttleBay bay, com.sfb.objects.Shuttle shuttle, int speed, int facing) {
        return game.launchShuttle(ship, bay, shuttle, speed, facing);
    }
    @Override public ActionResult launchScatterPack(Ship launcher, com.sfb.objects.ScatterPack pack, Unit target) {
        for (com.sfb.systemgroups.ShuttleBay bay : launcher.getShuttles().getBays()) {
            if (bay.getInventory().contains(pack))
                return game.launchScatterPack(launcher, bay, pack, target);
        }
        return ActionResult.fail("Shuttle bay not found for " + pack.getName());
    }
    @Override public ActionResult launchSuicideShuttle(Ship launcher, com.sfb.objects.SuicideShuttle shuttle, Unit target) {
        // Find the bay containing this shuttle
        for (com.sfb.systemgroups.ShuttleBay bay : launcher.getShuttles().getBays()) {
            if (bay.getInventory().contains(shuttle))
                return game.launchSuicideShuttle(launcher, bay, shuttle, target);
        }
        return ActionResult.fail("Shuttle bay not found for " + shuttle.getName());
    }
    @Override public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone) {
        return game.execute(new LaunchDroneCommand(launcher, target, rack, drone));
    }
    @Override public ActionResult launchPlasma(Ship attacker, Unit target, PlasmaLauncher weapon, boolean pseudo) {
        return game.execute(new LaunchPlasmaCommand(attacker, target, weapon, pseudo));
    }
    @Override public ActionResult fire(Ship attacker, Unit target, List<Weapon> weapons, int range, int adjustedRange, int shield) {
        return game.execute(new FireCommand(attacker, target, weapons, range, adjustedRange, shield));
    }
    @Override public ActionResult hitAndRun(Ship acting, Ship target, List<SystemTarget> targets) {
        return game.execute(new HitAndRunCommand(acting, target, targets));
    }
    @Override public ActionResult placeTBomb(Ship ship, Location loc, boolean isReal) {
        return game.execute(new PlaceTBombCommand(ship, loc, isReal));
    }
    @Override public ActionResult cloak(Ship ship)   { return game.execute(new CloakCommand(ship)); }
    @Override public ActionResult uncloak(Ship ship) { return game.execute(new UncloakCommand(ship)); }
}
