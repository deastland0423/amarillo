package com.sfb.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.sfb.Game;
import com.sfb.objects.*;
import com.sfb.systemgroups.CloakingDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializable snapshot of all game state, broadcast over WebSocket after
 * each action.
 *
 * Map objects form a hierarchy that mirrors the core object model:
 *
 *   MapObjectDto          (type, name, location)     ← mirrors Marker
 *     ShipDto             (hull, faction, shields, cloak)
 *     ShuttleDto          (parentShip, speed, facing)
 *     DroneDto            (droneType, warhead, target, faction)
 *     PlasmaTorpedoDto    (currentStrength, controllerFaction)
 *     MineDto             (active, revealed)
 *
 * Adding a new map object type in the future (base, asteroid, monster, etc.)
 * means adding a new subclass and a @JsonSubTypes entry — nothing else changes.
 */
public class GameStateDto {

    // -------------------------------------------------------------------------
    // Polymorphic base — mirrors Marker
    // -------------------------------------------------------------------------

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = ShipDto.class,          name = "SHIP"),
        @JsonSubTypes.Type(value = ShuttleDto.class,       name = "SHUTTLE"),
        @JsonSubTypes.Type(value = DroneDto.class,         name = "DRONE"),
        @JsonSubTypes.Type(value = PlasmaTorpedoDto.class, name = "PLASMA"),
        @JsonSubTypes.Type(value = MineDto.class,          name = "MINE"),
    })
    public static abstract class MapObjectDto {
        public String name;
        public String location; // "<x|y>" or null if off-map
    }

    // -------------------------------------------------------------------------
    // Ship
    // -------------------------------------------------------------------------

    public static class ShieldDto {
        public int     shieldNum;
        public int     current;
        public int     max;
        public boolean active;
    }

    public static class ShipDto extends MapObjectDto {
        public String         hull;
        public String         faction;
        public int            facing;
        public int            speed;
        public List<ShieldDto> shields;
        public String         cloakState;    // CloakingDevice.CloakState name, or "NONE"
        public int            cloakFadeStep;
    }

    // -------------------------------------------------------------------------
    // Shuttle
    // -------------------------------------------------------------------------

    public static class ShuttleDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public String parentPlayer; // name of the player who owns this shuttle
    }

    // -------------------------------------------------------------------------
    // Drone
    // -------------------------------------------------------------------------

    public static class DroneDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public String droneType;          // "I", "II", etc.
        public int    warheadDamage;
        public int    hull;
        public String targetName;
        public String controllerFaction;
    }

    // -------------------------------------------------------------------------
    // Plasma torpedo
    // -------------------------------------------------------------------------

    public static class PlasmaTorpedoDto extends MapObjectDto {
        public int    facing;
        public int    speed;
        public int    currentStrength;
        public String controllerFaction;
    }

    // -------------------------------------------------------------------------
    // Space mine / tBomb
    // -------------------------------------------------------------------------

    public static class MineDto extends MapObjectDto {
        public boolean active;
        public boolean revealed;
    }

    // -------------------------------------------------------------------------
    // Top-level fields
    // -------------------------------------------------------------------------

    public int               turn;
    public int               impulse;
    public String            phase;
    public List<String>      movableNow;
    public List<MapObjectDto> mapObjects;

    // -------------------------------------------------------------------------
    // Constructor — builds from live Game state
    // -------------------------------------------------------------------------

    public GameStateDto(Game game) {
        this.turn      = game.getCurrentTurn();
        this.impulse   = game.getCurrentImpulse();
        this.phase     = game.getCurrentPhase().getLabel();

        this.movableNow = new ArrayList<>();
        for (Ship s : game.getMovableShips())
            movableNow.add(s.getName());

        this.mapObjects = new ArrayList<>();

        for (Ship ship : game.getShips())
            mapObjects.add(fromShip(ship, game));

        for (com.sfb.objects.Shuttle shuttle : game.getActiveShuttles())
            mapObjects.add(fromShuttle(shuttle));

        for (Seeker seeker : game.getSeekers()) {
            if (seeker instanceof Drone)
                mapObjects.add(fromDrone((Drone) seeker));
            else if (seeker instanceof PlasmaTorpedo)
                mapObjects.add(fromPlasma((PlasmaTorpedo) seeker));
        }

        for (SpaceMine mine : game.getMines())
            mapObjects.add(fromMine(mine));
    }

    // -------------------------------------------------------------------------
    // Builders
    // -------------------------------------------------------------------------

    private static ShipDto fromShip(Ship ship, Game game) {
        ShipDto dto     = new ShipDto();
        dto.name        = ship.getName();
        dto.location    = ship.getLocation() != null ? ship.getLocation().toString() : null;
        dto.facing      = ship.getFacing();
        dto.speed       = ship.getSpeed();
        dto.hull        = ship.getHullType();
        dto.faction     = ship.getFaction() != null ? ship.getFaction().name() : "Federation";

        dto.shields = new ArrayList<>();
        for (int s = 1; s <= 6; s++) {
            ShieldDto sd  = new ShieldDto();
            sd.shieldNum  = s;
            sd.current    = ship.getShields().getShieldStrength(s);
            sd.max        = ship.getShields().getMaxShieldStrength(s);
            sd.active     = ship.getShields().isShieldActive(s);
            dto.shields.add(sd);
        }

        CloakingDevice cloak = ship.getCloakingDevice();
        if (cloak != null) {
            dto.cloakState    = cloak.getState().name();
            dto.cloakFadeStep = cloak.getFadeStep(game.getAbsoluteImpulse());
        } else {
            dto.cloakState    = "NONE";
            dto.cloakFadeStep = 0;
        }

        return dto;
    }

    private static ShuttleDto fromShuttle(com.sfb.objects.Shuttle shuttle) {
        ShuttleDto dto   = new ShuttleDto();
        dto.name         = shuttle.getName();
        dto.location     = shuttle.getLocation() != null ? shuttle.getLocation().toString() : null;
        dto.facing       = shuttle.getFacing();
        dto.speed        = shuttle.getSpeed();
        dto.parentPlayer = shuttle.getOwner() != null ? shuttle.getOwner().getName() : null;
        return dto;
    }

    private static DroneDto fromDrone(Drone drone) {
        DroneDto dto          = new DroneDto();
        dto.name              = drone.getName();
        dto.location          = drone.getLocation() != null ? drone.getLocation().toString() : null;
        dto.facing            = drone.getFacing();
        dto.speed             = drone.getSpeed();
        dto.droneType         = drone.getDroneType() != null ? drone.getDroneType().toString() : "?";
        dto.warheadDamage     = drone.getWarheadDamage();
        dto.hull              = drone.getHull();
        dto.targetName        = drone.getTarget() != null ? drone.getTarget().getName() : null;
        dto.controllerFaction = controllerFaction(drone.getController());
        return dto;
    }

    private static PlasmaTorpedoDto fromPlasma(PlasmaTorpedo torp) {
        PlasmaTorpedoDto dto  = new PlasmaTorpedoDto();
        dto.name              = torp.getName();
        dto.location          = torp.getLocation() != null ? torp.getLocation().toString() : null;
        dto.facing            = torp.getFacing();
        dto.speed             = torp.getSpeed();
        dto.currentStrength   = torp.getCurrentStrength();
        dto.controllerFaction = controllerFaction(torp.getController());
        return dto;
    }

    private static MineDto fromMine(SpaceMine mine) {
        MineDto dto  = new MineDto();
        dto.name     = mine.getName();
        dto.location = mine.getLocation() != null ? mine.getLocation().toString() : null;
        dto.active   = mine.isActive();
        dto.revealed = mine.isRevealed();
        return dto;
    }

    private static String controllerFaction(Unit controller) {
        if (controller instanceof Ship) {
            com.sfb.properties.Faction f = ((Ship) controller).getFaction();
            return f != null ? f.name() : null;
        }
        return null;
    }
}
