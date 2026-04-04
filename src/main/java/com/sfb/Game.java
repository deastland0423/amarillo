package com.sfb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sfb.commands.Command;
import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DirectFire;

import com.sfb.objects.Drone;
import com.sfb.objects.Marker;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.TBomb;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.systems.Energy;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.systemgroups.HullBoxes;
import com.sfb.systemgroups.PowerSystems;
import com.sfb.systems.SpecialFunctions;
import com.sfb.utilities.DiceRoller;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;
import com.sfb.utilities.MapUtils;
import com.sfb.utilities.MovementUtil;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

/**
 * Authoritative game state. All mutations to ship positions, damage, and turn
 * progress go through here. The UI reads from Game and sends actions to Game —
 * it never mutates ship state directly. This separation is what will allow
 * multiplayer: actions can be sent over a network instead of applied locally.
 */
public class Game {

    /**
     * The four segments of each impulse, in order.
     * Actions are gated by the current phase: movement keys only work in MOVEMENT,
     * the fire dialog only opens in DIRECT_FIRE, etc.
     */
    public enum ImpulsePhase {
        MOVEMENT("Movement"),
        ACTIVITY("Activity"),
        DIRECT_FIRE("Direct Fire"),
        END_OF_IMPULSE("End of Impulse");

        private final String label;

        ImpulsePhase(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    // --- State ---
    private final List<Player> players = new ArrayList<>();
    private final List<Ship> ships = new ArrayList<>();
    private final List<Seeker> seekers = new ArrayList<>();
    private final List<TBomb> mines = new ArrayList<>();
    private final Set<Ship> movedThisImpulse = new HashSet<>();
    private final List<PendingDamage> pendingInternalDamage = new ArrayList<>();

    private ImpulsePhase currentPhase = ImpulsePhase.MOVEMENT;
    private List<String> lastInternalDamageLog = new ArrayList<>();
    private List<String> lastSeekerLog = new ArrayList<>();
    private boolean inProgress = false;
    private boolean awaitingAllocation = false;
    private final List<Ship> allocationQueue = new ArrayList<>();

    // --- Setup ---

    /**
     * Populate the game with two players and four sample ships.
     * Call this once before starting the impulse loop.
     */
    public void setup() {
        Player player1 = new Player();
        player1.setName("Knosset");
        player1.setFaction(Faction.Federation);

        Player player2 = new Player();
        player2.setName("Kumerian");
        player2.setFaction(Faction.Klingon);

        players.add(player1);
        players.add(player2);

        Ship fedCa = new Ship();
        fedCa.init(FederationShips.getFedCa());
        fedCa.setLocation(new Location(12, 1));
        fedCa.setFacing(13);
        fedCa.setSpeed(16);
        fedCa.setOwner(player1);
        ships.add(fedCa);
        player1.getPlayerUnits().add(fedCa);

        Ship fedFfg = new Ship();
        fedFfg.init(FederationShips.getFedFfg());
        fedFfg.setLocation(new Location(16, 1));
        fedFfg.setFacing(13);
        fedFfg.setSpeed(20);
        fedFfg.setOwner(player1);
        ships.add(fedFfg);
        player1.getPlayerUnits().add(fedFfg);

        Ship klnD7 = new Ship();
        klnD7.init(KlingonShips.getD7());
        klnD7.setLocation(new Location(12, 12));
        klnD7.setFacing(1);
        klnD7.setSpeed(16);
        klnD7.setOwner(player2);
        ships.add(klnD7);
        player2.getPlayerUnits().add(klnD7);

        // Ship klnF5 = new Ship();
        // klnF5.init(KlingonShips.getF5());
        // klnF5.setLocation(new Location(16, 12));
        // klnF5.setFacing(1);
        // klnF5.setSpeed(20);
        // klnF5.setOwner(player2);
        // ships.add(klnF5);
        // player2.getPlayerUnits().add(klnF5);

        Ship romKr = new Ship();
        romKr.init(RomulanShips.getRomKr());
        romKr.setLocation(new Location(8, 12));
        romKr.setFacing(1);
        romKr.setSpeed(16);
        romKr.setOwner(player2);
        ships.add(romKr);
        player2.getPlayerUnits().add(romKr);

        TurnTracker.reset();
        inProgress = true;
        startTurn(); // run energy allocation and turn setup before impulse 1
    }

    // --- Turn progression ---

    /**
     * Begin the energy allocation phase. Queues every ship for allocation and
     * blocks the impulse loop until all ships have submitted. The UI calls
     * submitAllocation() for each ship in turn; once the queue is empty,
     * beginImpulses() is called automatically.
     */
    public void startTurn() {
        allocationQueue.clear();
        allocationQueue.addAll(ships);
        awaitingAllocation = true;
    }

    /**
     * Returns the next ship waiting for energy allocation, or null if all
     * ships have been allocated this turn.
     */
    public Ship nextShipNeedingAllocation() {
        return allocationQueue.isEmpty() ? null : allocationQueue.get(0);
    }

    public boolean isAwaitingAllocation() {
        return awaitingAllocation;
    }

    /**
     * Submit the player's energy allocation for one ship. When the last ship
     * is submitted, automatically finalises all ships and advances to impulse 1.
     */
    public ActionResult submitAllocation(Ship ship, Energy allocation) {
        ship.allocateEnergy(allocation);
        allocationQueue.remove(ship);
        if (allocationQueue.isEmpty()) {
            beginImpulses();
        }
        return ActionResult.ok(ship.getName() + " energy allocated");
    }

    /**
     * Finalise all ships' startTurn() and advance to impulse 1.
     * Called automatically once every ship has submitted an allocation.
     */
    private void beginImpulses() {
        for (Ship ship : ships) {
            ship.startTurn();
        }
        performLockOnRolls();
        awaitingAllocation = false;
        TurnTracker.nextImpulse();
        movedThisImpulse.clear();
        currentPhase = ImpulsePhase.MOVEMENT;
    }

    /**
     * Sensor Lock-On Phase (D6.1): each ship rolls 1d6 per other unit on the map.
     * Roll ≤ sensor rating → lock-on achieved. Sensor 6 is automatic (always
     * succeeds).
     * Per D6.113, each ship gets only one roll per turn.
     */
    private void performLockOnRolls() {
        DiceRoller dice = new DiceRoller();
        for (Ship ship : ships) {
            ship.clearLockOns();
            if (!ship.isActiveFireControl())
                continue; // D6.1143: no fire control = no lock-on
            int sensorRating = ship.getSpecialFunctions().getSensor();
            for (Ship target : ships) {
                if (target == ship)
                    continue;
                int roll = sensorRating >= 6 ? 1 : dice.rollOneDie();
                if (roll <= sensorRating) {
                    ship.addLockOn(target);
                }
            }
        }
    }

    /**
     * Compute the effective range from attacker to target (D6.21 + D6.123).
     * Formula: (noLockOn ? trueRange * 2 : trueRange) + scannerAdjustment +
     * cloakBonus
     */
    public int getEffectiveRange(Ship attacker, Unit target) {
        int trueRange = MapUtils.getRange(attacker, target);
        boolean hasLock = attacker.hasLockOn(target);
        int base = hasLock ? trueRange : trueRange * 2;
        int scanner = attacker.getSpecialFunctions().getScanner();
        // TODO: add cloakBonus from target's CloakingDevice once implemented
        return base + scanner;
    }

    /**
     * End-of-turn cleanup. Resets per-turn weapon states, shield reinforcement,
     * etc. Then starts the next turn's energy allocation.
     */
    public void endTurn() {
        for (Ship ship : ships) {
            ship.cleanUp();
        }
        startTurn();
    }

    /**
     * Advance to the next phase. Cycles MOVEMENT → ACTIVITY → DIRECT_FIRE →
     * END_OF_IMPULSE, then rolls over to the next impulse (or next turn after
     * impulse 32).
     */
    public ActionResult advancePhase() {
        List<String> log = new ArrayList<>();
        switch (currentPhase) {
            case MOVEMENT:
                lastSeekerLog = moveSeekers();
                List<String> mineLog = processMines();
                lastSeekerLog.addAll(mineLog);
                resolveInternalDamage();
                log.addAll(lastSeekerLog);
                log.addAll(lastInternalDamageLog);
                currentPhase = ImpulsePhase.ACTIVITY;
                break;
            case ACTIVITY:
                currentPhase = ImpulsePhase.DIRECT_FIRE;
                break;
            case DIRECT_FIRE:
                resolveInternalDamage();
                log.addAll(lastInternalDamageLog);
                currentPhase = ImpulsePhase.END_OF_IMPULSE;
                break;
            case END_OF_IMPULSE:
                // Roll over to next impulse (or next turn)
                if (TurnTracker.getLocalImpulse() >= 32) {
                    endTurn();
                } else {
                    TurnTracker.nextImpulse();
                    movedThisImpulse.clear();
                }
                autoRaiseShields();
                currentPhase = ImpulsePhase.MOVEMENT;
                break;
        }
        String message = log.isEmpty() ? "" : String.join("\n", log);
        return ActionResult.ok(message);
    }

    /**
     * Automatically raise any voluntarily-lowered shields that have met the
     * 8-impulse lockout.
     */
    private void autoRaiseShields() {
        for (Ship ship : ships) {
            for (int s = 1; s <= 6; s++) {
                if (!ship.getShields().isShieldActive(s)) {
                    ship.getShields().raiseShield(s);
                }
            }
        }
    }

    public ImpulsePhase getCurrentPhase() {
        return currentPhase;
    }

    public int getCurrentTurn() {
        return (TurnTracker.getImpulse() - 1) / 32 + 1;
    }

    public int getCurrentImpulse() {
        return TurnTracker.getLocalImpulse();
    }

    // --- Queries ---

    public List<Ship> getShips() {
        return ships;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public List<Seeker> getSeekers() {
        return seekers;
    }

    /**
     * Returns all ships that may move on the current impulse and have not yet
     * moved this impulse.
     */
    public List<Ship> getMovableShips() {
        int impulse = TurnTracker.getLocalImpulse();
        List<Ship> movable = new ArrayList<>();
        for (Ship ship : ships) {
            if (ship.movesThisImpulse(impulse) && !movedThisImpulse.contains(ship)) {
                movable.add(ship);
            }
        }
        // Slower ships move first; ties broken by worst turn mode first (F > E > ... >
        // AA).
        movable.sort(Comparator.comparingInt(Ship::getSpeed)
                .thenComparingInt(s -> -s.getTurnMode().ordinal()));
        return movable;
    }

    public boolean hasMovedThisImpulse(Ship ship) {
        return movedThisImpulse.contains(ship);
    }

    public boolean canMoveThisImpulse(Ship ship) {
        if (currentPhase != ImpulsePhase.MOVEMENT)
            return false;
        if (!ship.movesThisImpulse(TurnTracker.getLocalImpulse()))
            return false;
        if (movedThisImpulse.contains(ship))
            return false;
        // Enforce order: ship may only move if no higher-priority ship is still waiting
        List<Ship> movable = getMovableShips();
        return movable.isEmpty() || movable.get(0) == ship;
    }

    public boolean canFireThisPhase() {
        return currentPhase == ImpulsePhase.DIRECT_FIRE;
    }

    // --- Command execution ---

    public ActionResult execute(Command command) {
        return command.execute(this);
    }

    // --- Movement actions ---

    public ActionResult moveForward(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.goForward();
        if (moved)
            movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " moved forward")
                : ActionResult.fail(ship.getName() + " could not move forward");
    }

    public ActionResult turnLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.turnLeft();
        if (moved)
            movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " turned left")
                : ActionResult.fail(ship.getName() + " cannot turn left yet (turn mode)");
    }

    public ActionResult turnRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.turnRight();
        if (moved)
            movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " turned right")
                : ActionResult.fail(ship.getName() + " cannot turn right yet (turn mode)");
    }

    public ActionResult sideslipLeft(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.sideslipLeft();
        if (moved)
            movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " sideslipped left")
                : ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    public ActionResult sideslipRight(Ship ship) {
        if (!canMoveThisImpulse(ship))
            return ActionResult.fail(ship.getName() + " cannot move this impulse");
        boolean moved = ship.sideslipRight();
        if (moved)
            movedThisImpulse.add(ship);
        return moved ? ActionResult.ok(ship.getName() + " sideslipped right")
                : ActionResult.fail(ship.getName() + " cannot sideslip (must move first)");
    }

    // --- Weapons fire ---

    /**
     * Returns all weapons on the attacker that bear on the target.
     */
    public List<Weapon> getBearingWeapons(Ship attacker, Unit target) {
        return attacker.fetchAllBearingWeapons(target);
    }

    /**
     * Compute the range between two units.
     */
    public int getRange(Unit attacker, Unit target) {
        return MapUtils.getRange(attacker, target);
    }

    /**
     * Compute which shield number on the target is facing the attacker.
     */
    /**
     * Determine which shield a drone hits based on its direction of travel.
     * The drone's facing is where it is going; the hit shield faces the opposite
     * direction.
     */
    private int getDroneImpactShield(Unit drone, Ship target) {
        // Reverse the drone's facing 180° to get the incoming direction (1-24)
        int incomingFacing = (drone.getFacing() + 11) % 24 + 1;
        // Convert 24-direction to 12-direction absolute shield facing
        int absShieldFacing = ((incomingFacing - 1) / 4) * 2 + 1;
        int relFacing = MapUtils.getRelativeShieldFacing(absShieldFacing, target.getFacing());
        int shieldNumber = (relFacing % 2 == 0) ? relFacing / 2 : (relFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    public int getShieldNumber(Marker attacker, Ship target) {
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        int shieldNumber = (shieldFacing % 2 == 0) ? shieldFacing / 2 : (shieldFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    /**
     * Mark shield damage from one firing volley (6D2 — Direct-Fire Weapons Fire
     * Stage).
     * Bleed-through is queued as pending internal damage; it will not be resolved
     * until resolveInternalDamage() is called at the end of the Direct-Fire segment
     * (6D4).
     *
     * @return A FireResult with the bleed-through amount and an empty internal log
     *         (log is populated later when resolveInternalDamage() runs).
     */
    public FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage) {
        int bleed = target.damageShield(shieldNumber, totalDamage);
        if (bleed > 0) {
            pendingInternalDamage.add(new PendingDamage(target, bleed));
        }
        return new FireResult(bleed, new ArrayList<>());
    }

    /**
     * Apply weapon damage to any unit — routes to the correct damage path based on
     * type.
     * Ships: damage goes through shields first, bleed-through queued as internal
     * damage.
     * Drones: damage applied directly to hull; drone destroyed and removed when
     * hull reaches 0.
     *
     * @return A log entry describing what happened.
     */
    public String applyDamageToUnit(int damage, Unit target, int shieldNumber) {
        if (target instanceof Ship) {
            if (damage == com.sfb.weapons.ADD.HIT) {
                return "ADD has no effect on ships";
            }
            Ship ship = (Ship) target;
            FireResult result = markShieldDamage(ship, shieldNumber, damage);
            return "Hit " + ship.getName() + " shield " + shieldNumber
                    + " for " + damage + " damage"
                    + (result.getBleed() > 0 ? " (" + result.getBleed() + " bleed)" : "");
        } else if (target instanceof Drone) {
            Drone drone = (Drone) target;
            int remaining = drone.getHull() - damage;
            drone.setHull(Math.max(0, remaining));
            if (drone.getHull() <= 0) {
                seekers.remove(drone);
                if (drone.getController() instanceof Ship)
                    ((Ship) drone.getController()).releaseControl(drone);
                return "Drone (" + drone.getDroneType() + ") destroyed";
            }
            return "Drone (" + drone.getDroneType() + ") hit for " + damage
                    + " — " + drone.getHull() + " hull remaining";
        } else if (target instanceof com.sfb.objects.Shuttle) {
            com.sfb.objects.Shuttle shuttle = (com.sfb.objects.Shuttle) target;
            if (damage == com.sfb.weapons.ADD.HIT) {
                int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
                shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - roll));
                if (shuttle.getCurrentHull() <= 0)
                    return shuttle.getName() + " destroyed by ADD (" + roll + " hull damage)";
                return shuttle.getName() + " hit by ADD for " + roll + " hull damage — "
                        + shuttle.getCurrentHull() + " remaining";
            }
            shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - damage));
            if (shuttle.getCurrentHull() <= 0)
                return shuttle.getName() + " destroyed (" + damage + " damage)";
            return shuttle.getName() + " hit for " + damage + " — "
                    + shuttle.getCurrentHull() + " hull remaining";
        } else if (target instanceof PlasmaTorpedo) {
            PlasmaTorpedo torp = (PlasmaTorpedo) target;
            int before = torp.getCurrentStrength();
            torp.applyPhaserDamage(damage);
            int after = torp.getCurrentStrength();
            if (after <= 0) {
                seekers.remove(torp);
                return torp.getName() + " destroyed by phaser fire (" + damage + " pts)";
            }
            return torp.getName() + " hit for " + damage + " phaser pts — strength " + before + " → " + after;
        }
        return "Damage to unknown unit type ignored";
    }

    /**
     * Fire a list of direct-fire weapons at a target, apply damage, and return a
     * combat log entry.
     *
     * @param attacker      The firing ship.
     * @param target        The unit being fired upon.
     * @param selected      Weapons chosen by the player (must implement
     *                      DirectFire).
     * @param range         True hex range to the target.
     * @param adjustedRange Range after scanner modifier.
     * @param shieldNumber  Shield facing the attacker (0 for non-ship targets).
     * @return A formatted combat log string describing every shot and the total
     *         damage applied.
     */
    public String fireWeapons(Ship attacker, Unit target, List<Weapon> selected,
            int range, int adjustedRange, int shieldNumber) {
        if (!attacker.isActiveFireControl()) {
            return attacker.getName() + " has no active fire control — cannot fire";
        }
        StringBuilder log = new StringBuilder();
        log.append(attacker.getName()).append("  \u2192  ").append(target.getName())
                .append("   range ").append(range)
                .append("   shield #").append(shieldNumber).append("\n");

        int totalDamage = 0;
        boolean addHit = false;

        for (Weapon w : selected) {
            try {
                int dmg = ((DirectFire) w).fire(range, adjustedRange);
                if (dmg == ADD.HIT) {
                    addHit = true;
                    log.append("  ").append(w.getName()).append("  HIT\n");
                } else {
                    totalDamage += dmg;
                    log.append("  ").append(w.getName())
                            .append(dmg > 0 ? "  HIT  " + dmg : "  MISS").append("\n");
                }
            } catch (WeaponUnarmedException ex) {
                log.append("  ").append(w.getName()).append("  unarmed\n");
            } catch (TargetOutOfRangeException ex) {
                log.append("  ").append(w.getName()).append("  out of range\n");
            } catch (CapacitorException ex) {
                log.append("  ").append(w.getName()).append("  no capacitor energy\n");
            }
        }

        if (addHit) {
            String dmgLog = applyDamageToUnit(ADD.HIT, target, shieldNumber);
            log.append("  ADD result: ").append(dmgLog).append("\n");
        }
        log.append("  Total damage: ").append(totalDamage);
        if (target instanceof Ship) {
            FireResult result = markShieldDamage((Ship) target, shieldNumber, totalDamage);
            if (result.getBleed() > 0) {
                log.append("   BLEED-THROUGH: ").append(result.getBleed())
                        .append(" (internal damage resolves at end of Direct-Fire segment)\n");
            }
        } else if (totalDamage > 0) {
            String dmgLog = applyDamageToUnit(totalDamage, target, shieldNumber);
            log.append("   ").append(dmgLog).append("\n");
        }

        return log.toString();
    }

    /**
     * Resolve all queued internal damage (6D4 — Direct-Fire Weapons Damage
     * Resolution Stage).
     * Called automatically when advancePhase() moves out of DIRECT_FIRE.
     */
    private void resolveInternalDamage() {
        lastInternalDamageLog = new ArrayList<>();
        for (PendingDamage pd : pendingInternalDamage) {
            List<String> entries = pd.target.applyInternalDamage(pd.bleed);
            lastInternalDamageLog.add("=== Internal damage — " + pd.target.getName() + " ===");
            lastInternalDamageLog.addAll(entries);
        }
        pendingInternalDamage.clear();
    }

    /**
     * Returns the internal damage log from the most recent resolution step.
     * The UI should read this after advancing past DIRECT_FIRE.
     */
    public List<String> getLastInternalDamageLog() {
        return lastInternalDamageLog;
    }

    // --- Drone launching ---

    public boolean canLaunchThisPhase() {
        return currentPhase == ImpulsePhase.ACTIVITY;
    }

    /**
     * Launch one drone from the given rack at the given target.
     * The drone is placed at the launching ship's location, faced toward the
     * target, and added to the active seekers list.
     *
     * @return ActionResult describing success or reason for failure.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (rack.isEmpty())
            return ActionResult.fail(rack.getName() + " has no drones loaded");
        if (!launcher.hasLockOn(target))
            return ActionResult.fail("No sensor lock-on to target — cannot launch seeking weapons (D6.121)");

        return launchDrone(launcher, target, rack, rack.getAmmo().get(0));
    }

    /**
     * Launch a specific drone from the given rack at the given target.
     */
    public ActionResult launchDrone(Ship launcher, Unit target, DroneRack rack, Drone drone) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Drones can only be launched during the Activity phase");
        if (!rack.isFunctional())
            return ActionResult.fail(rack.getName() + " is destroyed");
        if (!rack.canFire())
            return ActionResult.fail(rack.getName() + " cannot launch yet (once per turn, 8-impulse delay)");
        if (!rack.getAmmo().contains(drone))
            return ActionResult.fail("Drone is not in " + rack.getName());
        if (!drone.isSelfGuiding() && !launcher.acquireControl(drone))
            return ActionResult.fail("No control channels available (limit: "
                    + launcher.getControlLimit() + ")");

        rack.getAmmo().remove(drone);
        rack.recordLaunch();
        drone.setLocation(launcher.getLocation());
        drone.setFacing(MapUtils.getBearing(launcher, target));
        drone.setTarget(target);
        drone.setController(launcher);
        drone.setLaunchImpulse(TurnTracker.getImpulse());
        drone.setSeekerType(Seeker.SeekerType.DRONE);
        seekers.add(drone);

        return ActionResult.ok(launcher.getName() + " launched " + drone.getDroneType()
                + " drone at " + target.getName());
    }

    /**
     * Launch a plasma torpedo from the given launcher at the target.
     * The launcher must be armed. The torpedo is placed at the launcher's
     * location, faced toward the target, and added to the active seekers list.
     */
    public ActionResult launchPlasma(Ship launcher, Unit target, PlasmaLauncher weapon) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (!weapon.isArmed())
            return ActionResult.fail(weapon.getName() + " is not armed");

        PlasmaTorpedo torpedo = weapon.launch();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch");

        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(MapUtils.getBearing(launcher, target));
        torpedo.setTarget(target);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(TurnTracker.getImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);

        return ActionResult.ok(launcher.getName() + " launched plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName());
    }

    public ActionResult launchPseudoPlasma(Ship launcher, Unit target, PlasmaLauncher weapon) {
        if (!canLaunchThisPhase())
            return ActionResult.fail("Plasma can only be launched during the Activity phase");
        if (!weapon.isFunctional())
            return ActionResult.fail(weapon.getName() + " is destroyed");
        if (!weapon.canLaunchPseudo())
            return ActionResult.fail(weapon.getName() + " cannot launch pseudo plasma now");

        PlasmaTorpedo torpedo = weapon.launchPseudo();
        if (torpedo == null)
            return ActionResult.fail(weapon.getName() + " failed to launch pseudo plasma");

        torpedo.setLocation(launcher.getLocation());
        torpedo.setFacing(MapUtils.getBearing(launcher, target));
        torpedo.setTarget(target);
        torpedo.setController(launcher);
        torpedo.setLaunchImpulse(TurnTracker.getImpulse());
        torpedo.setSeekerType(Seeker.SeekerType.PLASMA);
        seekers.add(torpedo);

        return ActionResult.ok(launcher.getName() + " launched pseudo plasma-"
                + torpedo.getPlasmaType() + " at " + target.getName() + " [PSEUDO]");
    }

    /**
     * Move all active seekers that are scheduled to move this impulse.
     * Each drone re-faces its target, advances one hex, and is checked for
     * impact or endurance expiry. Returns a log of all seeker activity.
     * Called automatically when leaving the MOVEMENT phase.
     */
    private List<String> moveSeekers() {
        List<String> log = new ArrayList<>();
        if (seekers.isEmpty())
            return log;

        int impulse = TurnTracker.getLocalImpulse();
        List<Seeker> expired = new ArrayList<>();

        // Order seekers so that a seeker whose target is also a seeker moves after its
        // target.
        // Simple two-pass: targets first, then hunters.
        Set<Seeker> seekerSet = new HashSet<>(seekers);
        List<Seeker> ordered = new ArrayList<>();
        Set<Seeker> placed = new HashSet<>();
        for (Seeker s : seekers) {
            Unit target = (s instanceof Drone) ? ((Drone) s).getTarget() : null;
            if (target instanceof Seeker && seekerSet.contains(target) && !placed.contains(target)) {
                ordered.add((Seeker) target);
                placed.add((Seeker) target);
            }
            if (!placed.contains(s)) {
                ordered.add(s);
                placed.add(s);
            }
        }

        for (Seeker seeker : ordered) {
            if (seeker instanceof Drone) {
                Drone drone = (Drone) seeker;
                if (!MovementUtil.moveThisImpulse(impulse, drone.getSpeed()))
                    continue;

                Unit target = drone.getTarget();
                if (target != null) {
                    int bearing = MapUtils.getBearing(drone, target);
                    if (bearing != 0)
                        drone.setFacing(snapToCardinal(bearing));
                }

                drone.goForward();

                if (drone.getLocation() == null) {
                    log.add("  Drone (" + drone.getDroneType() + ") moved off the map");
                    expired.add(seeker);
                    continue;
                }

                drone.setEndurance(drone.getEndurance() - 1);

                if (target != null && target.getLocation() != null
                        && drone.getLocation().equals(target.getLocation())) {
                    if (target instanceof Drone) {
                        Drone targetDrone = (Drone) target;
                        log.add("  Drone (" + drone.getDroneType() + ") collided with drone ("
                                + targetDrone.getDroneType() + ") — both destroyed");
                        expired.add(seeker);
                        expired.add(targetDrone);
                    } else if (target instanceof Ship) {
                        int shieldNum = getDroneImpactShield(drone, (Ship) target);
                        int dmg = drone.impact();
                        FireResult result = markShieldDamage((Ship) target, shieldNum, dmg);
                        log.add("  Drone (" + drone.getDroneType() + ") impacted "
                                + target.getName() + " shield #" + shieldNum
                                + "  damage " + dmg
                                + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                        expired.add(seeker);
                    }
                    continue;
                }

                if (drone.getEndurance() <= 0) {
                    log.add("  Drone (" + drone.getDroneType() + ") targeting "
                            + (target != null ? target.getName() : "?") + " ran out of endurance");
                    expired.add(seeker);
                }

            } else if (seeker instanceof PlasmaTorpedo) {
                PlasmaTorpedo torp = (PlasmaTorpedo) seeker;
                if (!MovementUtil.moveThisImpulse(impulse, torp.getSpeed()))
                    continue;

                Unit target = torp.getTarget();
                if (target != null) {
                    int bearing = MapUtils.getBearing(torp, target);
                    if (bearing != 0)
                        torp.setFacing(snapToCardinal(bearing));
                }

                torp.goForward();
                torp.incrementDistance();

                if (torp.getLocation() == null) {
                    log.add("  Plasma-" + torp.getPlasmaType() + " moved off the map");
                    expired.add(seeker);
                    continue;
                }

                if (torp.getCurrentStrength() <= 0) {
                    log.add("  Plasma-" + torp.getPlasmaType() + " targeting "
                            + (target != null ? target.getName() : "?") + " dissipated");
                    expired.add(seeker);
                    continue;
                }

                if (target != null && target.getLocation() != null
                        && torp.getLocation().equals(target.getLocation())) {
                    if (target instanceof Ship) {
                        Ship ship = (Ship) target;
                        if (torp.isEnveloping()) {
                            int[] spread = torp.computeEnvelopingDamage();
                            int total = 0;
                            for (int i = 0; i < 6; i++) {
                                if (spread[i] > 0) {
                                    markShieldDamage(ship, i + 1, spread[i]);
                                    total += spread[i];
                                }
                            }
                            log.add("  Plasma-" + torp.getPlasmaType() + " (enveloping) impacted "
                                    + ship.getName() + "  total damage " + total + " spread to all shields");
                        } else {
                            int shieldNum = getDroneImpactShield(torp, ship);
                            int dmg = torp.impact();
                            FireResult result = markShieldDamage(ship, shieldNum, dmg);
                            log.add("  Plasma-" + torp.getPlasmaType() + " impacted "
                                    + ship.getName() + " shield #" + shieldNum
                                    + "  damage " + dmg
                                    + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                        }
                    } else {
                        int dmg = torp.impact();
                        String dmgLog = applyDamageToUnit(dmg, target, 1);
                        log.add("  Plasma-" + torp.getPlasmaType() + " impacted " + target.getName()
                                + "  " + dmgLog);
                    }
                    expired.add(seeker);
                }
            }
        }

        for (Seeker s : expired) {
            if (s instanceof Drone) {
                Drone d = (Drone) s;
                if (d.getController() instanceof Ship)
                    ((Ship) d.getController()).releaseControl(d);
            }
        }
        seekers.removeAll(expired);
        return log;
    }

    /**
     * Returns the seeker activity log from the most recent MOVEMENT phase.
     * The UI should read this after advancing past MOVEMENT.
     */
    public List<String> getLastSeekerLog() {
        return lastSeekerLog;
    }

    /** Snap a 1–24 bearing to the nearest cardinal (1, 5, 9, 13, 17, 21). */
    private static int snapToCardinal(int bearing) {
        int[] cardinals = { 1, 5, 9, 13, 17, 21 };
        int best = cardinals[0];
        int bestDist = Integer.MAX_VALUE;
        for (int c : cardinals) {
            int diff = Math.abs(bearing - c);
            // wrap around the 24-direction circle
            if (diff > 12)
                diff = 24 - diff;
            if (diff < bestDist) {
                bestDist = diff;
                best = c;
            }
        }
        return best;
    }

    // --- Mines ---

    public List<TBomb> getMines() {
        return mines;
    }

    /**
     * Place a tBomb (real or dummy) on the map via transporter.
     *
     * <p>
     * Validates the same transporter preconditions as a Hit &amp; Run raid:
     * Activity phase, range ≤ 5, acting ship's facing shield passable, and
     * enough transporter energy. Decrements the appropriate tBomb count on
     * the acting ship.
     */
    public ActionResult placeTBomb(Ship actingShip, com.sfb.properties.Location targetHex, boolean isReal) {
        if (currentPhase != ImpulsePhase.ACTIVITY) {
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        }

        // Range check — build a temporary marker at the target hex
        Marker targetMarker = new Marker();
        targetMarker.setLocation(targetHex);
        int range = MapUtils.getRange(actingShip, targetMarker);
        if (range > 5) {
            return ActionResult.fail("Target hex is out of transporter range (" + range + " hexes, max 5)");
        }

        // Transporter energy
        if (actingShip.getTransporters().availableUses() < 1) {
            return ActionResult.fail("No transporter energy available");
        }

        // Acting ship's facing shield toward target hex must be passable
        int actingShieldNum = getShieldNumber(targetMarker, actingShip);
        if (!actingShip.getShields().isTransportable(actingShieldNum)) {
            boolean lowered = actingShip.getShields().lowerShield(actingShieldNum);
            if (!lowered) {
                return ActionResult.fail("Cannot lower shield #" + actingShieldNum
                        + " — must wait 8 impulses since last toggle");
            }
        }

        // Check inventory
        if (isReal && actingShip.getTBombs() < 1) {
            return ActionResult.fail("No tBombs remaining");
        }
        if (!isReal && actingShip.getDummyTBombs() < 1) {
            return ActionResult.fail("No dummy tBombs remaining");
        }

        // Spend inventory and transporter energy
        if (isReal) {
            actingShip.setTBombs(actingShip.getTBombs() - 1);
        } else {
            actingShip.setDummyTBombs(actingShip.getDummyTBombs() - 1);
        }
        actingShip.getTransporters().useTransporter();

        // Place the mine
        TBomb mine = new TBomb(actingShip, TurnTracker.getImpulse(), isReal);
        mine.setLocation(targetHex);
        mines.add(mine);

        return ActionResult.ok(actingShip.getName() + " placed a "
                + (isReal ? "tBomb" : "dummy tBomb")
                + " at " + targetHex);
    }

    /**
     * Process all mines each movement phase: attempt to activate inactive mines,
     * then check active mines for detection and detonation.
     */
    private List<String> processMines() {
        List<String> log = new ArrayList<>();
        if (mines.isEmpty())
            return log;

        int currentImpulse = TurnTracker.getImpulse();
        DiceRoller dice = new DiceRoller();

        // All units currently on the map
        List<Unit> allUnits = new ArrayList<>(ships);
        for (Seeker s : seekers) {
            if (s instanceof Unit)
                allUnits.add((Unit) s);
        }

        List<TBomb> detonated = new ArrayList<>();

        for (TBomb mine : mines) {
            if (mine.getLocation() == null)
                continue;

            // Try to arm inactive mines
            if (!mine.isActive()) {
                int layerRange = MapUtils.getRange(mine, mine.getLayingShip());
                mine.tryActivate(currentImpulse, layerRange);
                if (!mine.isActive())
                    continue;
                log.add("  tBomb at " + mine.getLocation() + " is now ARMED");
            }

            // Find units within range 1
            List<Unit> inRange = new ArrayList<>();
            for (Unit unit : allUnits) {
                if (unit.getLocation() == null)
                    continue;
                if (MapUtils.getRange(mine, unit) <= 1)
                    inRange.add(unit);
            }

            if (inRange.isEmpty())
                continue;

            // Detection check — first unit that triggers detonates the mine
            boolean triggered = false;
            for (Unit unit : inRange) {
                int roll = dice.rollOneDie();
                if (mine.detectsUnit(unit.getSpeed(), roll)) {
                    triggered = true;
                    break;
                }
            }

            if (!triggered) {
                // Units in range but not detected — reveal dummy if applicable
                if (!mine.isReal() && !mine.isRevealed()) {
                    mine.reveal();
                    log.add("  Dummy tBomb at " + mine.getLocation()
                            + " revealed — no explosion");
                }
                continue;
            }

            if (!mine.isReal()) {
                mine.reveal();
                log.add("  Dummy tBomb at " + mine.getLocation()
                        + " revealed — no explosion");
                continue;
            }

            // Real mine — detonate
            log.add("  tBomb DETONATED at " + mine.getLocation() + "!");
            for (Unit unit : inRange) {
                if (unit instanceof Ship) {
                    Ship ship = (Ship) unit;
                    int shieldNum = getShieldNumber(mine, ship);
                    FireResult result = markShieldDamage(ship, shieldNum, TBomb.DAMAGE);
                    log.add("    " + ship.getName() + " shield #" + shieldNum
                            + " hit for " + TBomb.DAMAGE
                            + (result.getBleed() > 0 ? "  bleed " + result.getBleed() : ""));
                } else {
                    String dmgLog = applyDamageToUnit(TBomb.DAMAGE, unit, 0);
                    log.add("    " + dmgLog);
                }
            }
            detonated.add(mine);
        }

        mines.removeAll(detonated);
        return log;
    }

    // --- Hit & Run raids ---

    /**
     * Build the list of systems on a ship that can be targeted by a Hit &amp; Run
     * raid.
     * Only includes functional/damageable systems.
     */
    public List<SystemTarget> getTargetableSystems(Ship target) {
        List<SystemTarget> systems = new ArrayList<>();

        // Individual weapons
        for (Weapon w : target.getWeapons().fetchAllWeapons()) {
            if (w.isFunctional()) {
                systems.add(new SystemTarget(w));
            }
        }

        // Power
        PowerSystems ps = target.getPowerSysetems();
        if (ps.getAvailableLWarp() > 0 || ps.getAvailableRWarp() > 0 || ps.getAvailableCWarp() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.WARP, "Warp Engines"));
        }
        if (ps.getAvailableImpulse() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.IMPULSE, "Impulse Engines"));
        }

        // Special functions
        SpecialFunctions sf = target.getSpecialFunctions();
        if (sf.canDamageSensor()) {
            systems.add(new SystemTarget(SystemTarget.Type.SENSORS, "Sensors"));
        }
        if (sf.canDamageScanner()) {
            systems.add(new SystemTarget(SystemTarget.Type.SCANNERS, "Scanners"));
        }

        // Transporters
        if (target.getTransporters().getAvailableTrans() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.TRANSPORTERS, "Transporters"));
        }

        // Crew
        if (target.getCrew().getAvailableCrewUnits() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.CREW, "Crew"));
        }

        // Hull
        HullBoxes h = target.getHullBoxes();
        if (h.getAvailableFhull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.FHULL, "Forward Hull"));
        }
        if (h.getAvailableAhull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.AHULL, "Aft Hull"));
        }
        if (h.getAvailableChull() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.CHULL, "Center Hull"));
        }

        return systems;
    }

    /**
     * Execute a Hit &amp; Run boarding raid.
     *
     * <p>
     * Pre-conditions checked here: range ≤ 5, enough boarding parties and
     * transporter energy, target shield passable. The acting ship's facing
     * shield is lowered automatically if it has remaining strength (triggering
     * the 8-impulse lockout).
     *
     * @param actingShip    The ship sending boarding parties.
     * @param target        The ship being raided.
     * @param targetSystems One {@link SystemTarget} per boarding party sent.
     * @return ActionResult with a full raid log, or failure message.
     */
    public ActionResult performHitAndRun(Ship actingShip, Ship target,
            List<SystemTarget> targetSystems) {
        if (currentPhase != ImpulsePhase.ACTIVITY) {
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        }
        if (targetSystems.isEmpty()) {
            return ActionResult.fail("No boarding parties assigned");
        }

        // Range check
        int range = getRange(actingShip, target);
        if (range > 5) {
            return ActionResult.fail("Target is out of transporter range (" + range + " hexes, max 5)");
        }

        // Lock-on check (D6.124)
        if (!actingShip.hasLockOn(target)) {
            return ActionResult.fail("No sensor lock-on to " + target.getName()
                    + " — cannot use transporters (D6.124)");
        }

        // Resource checks
        int numParties = targetSystems.size();
        int availableParties = actingShip.getCrew().getAvailableBoardingParties();
        if (numParties > availableParties) {
            return ActionResult.fail("Not enough boarding parties (have " + availableParties
                    + ", need " + numParties + ")");
        }
        int availableTrans = actingShip.getTransporters().getAvailableTrans();
        if (numParties > availableTrans) {
            return ActionResult.fail("Not enough transporters (have " + availableTrans
                    + ", need " + numParties + ")");
        }
        int availableUses = actingShip.getTransporters().availableUses();
        if (numParties > availableUses) {
            return ActionResult.fail("Not enough transporter energy (have " + availableUses
                    + " use(s), need " + numParties + ")");
        }

        // Shield checks — acting ship's shield facing target must be passable
        int actingShieldNum = getShieldNumber(target, actingShip);
        if (!actingShip.getShields().isTransportable(actingShieldNum)) {
            boolean lowered = actingShip.getShields().lowerShield(actingShieldNum);
            if (!lowered) {
                return ActionResult.fail("Cannot lower shield #" + actingShieldNum
                        + " on " + actingShip.getName()
                        + " — must wait 8 impulses since last toggle");
            }
        }

        // Target's shield facing the acting ship must already be passable
        int targetShieldNum = getShieldNumber(actingShip, target);
        if (!target.getShields().isTransportable(targetShieldNum)) {
            return ActionResult.fail(target.getName() + " shield #" + targetShieldNum
                    + " is active — cannot beam through");
        }

        // Spend transporter energy
        for (int i = 0; i < numParties; i++) {
            actingShip.getTransporters().useTransporter();
        }

        // Roll and apply results
        DiceRoller dice = new DiceRoller();
        StringBuilder log = new StringBuilder();
        log.append("=== Hit & Run Raid: ").append(actingShip.getName())
                .append("  →  ").append(target.getName()).append(" ===\n");
        log.append("  ").append(actingShip.getName()).append(" shield #")
                .append(actingShieldNum).append(" lowered\n");

        int partiesLost = 0;
        for (SystemTarget st : targetSystems) {
            int roll = dice.rollOneDie();
            boolean systemHit = (roll == 1 || roll == 2);
            boolean partyLost = (roll >= 2 && roll <= 5);

            String hitResult;
            if (systemHit) {
                boolean damaged = applyHitAndRunHit(target, st);
                hitResult = damaged ? st.getDisplayName() + " DAMAGED"
                        : st.getDisplayName() + " already destroyed";
            } else {
                hitResult = st.getDisplayName() + " not damaged";
            }

            log.append("  Roll ").append(roll).append(": ").append(hitResult)
                    .append(",  boarding party ").append(partyLost ? "lost" : "safe").append("\n");
            if (partyLost)
                partiesLost++;
        }

        if (partiesLost > 0) {
            int remaining = actingShip.getCrew().getAvailableBoardingParties() - partiesLost;
            actingShip.getCrew().setAvailableBoardingParties(Math.max(0, remaining));
        }

        log.append("  Boarding parties lost: ").append(partiesLost)
                .append(" / ").append(numParties).append(" sent");

        return ActionResult.ok(log.toString());
    }

    /**
     * Apply a single Hit &amp; Run hit to the given system on the target ship.
     * Returns true if the system was actually damaged (false if already destroyed).
     */
    private boolean applyHitAndRunHit(Ship target, SystemTarget system) {
        switch (system.getType()) {
            case WEAPON: {
                Weapon w = system.getWeapon();
                if (!w.isFunctional())
                    return false;
                w.damage();
                return true;
            }
            case WARP: {
                PowerSystems ps = target.getPowerSysetems();
                if (ps.damageLWarp())
                    return true;
                if (ps.damageRWarp())
                    return true;
                return ps.damageCWarp();
            }
            case IMPULSE:
                return target.getPowerSysetems().damageImpulse();
            case SENSORS:
                return target.getSpecialFunctions().damageSensor();
            case SCANNERS:
                return target.getSpecialFunctions().damageScanner();
            case TRANSPORTERS:
                return target.getTransporters().damage();
            case CREW: {
                int current = target.getCrew().getAvailableCrewUnits();
                if (current <= 0)
                    return false;
                target.getCrew().setAvailableCrewUnits(current - 1);
                return true;
            }
            case FHULL:
                return target.getHullBoxes().damageFhull();
            case AHULL:
                return target.getHullBoxes().damageAhull();
            case CHULL:
                return target.getHullBoxes().damageChull();
            default:
                return false;
        }
    }

    // --- Status ---

    public boolean isInProgress() {
        return inProgress;
    }

    // -------------------------------------------------------------------------
    // Nested result types — simple value holders the UI can read without
    // needing to reach into game state directly.
    // -------------------------------------------------------------------------

    /**
     * The result of a movement or other action attempt.
     */
    public static class ActionResult {
        private final boolean success;
        private final String message;

        private ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static ActionResult ok(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult fail(String message) {
            return new ActionResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Bleed-through damage waiting to be resolved at end of Direct-Fire segment
     * (6D4).
     */
    private static class PendingDamage {
        final Ship target;
        final int bleed;

        PendingDamage(Ship target, int bleed) {
            this.target = target;
            this.bleed = bleed;
        }
    }

    /**
     * The result of applying damage: bleed-through amount and internal damage log.
     */
    public static class FireResult {
        private final int bleed;
        private final List<String> internalLog;

        public FireResult(int bleed, List<String> internalLog) {
            this.bleed = bleed;
            this.internalLog = internalLog;
        }

        public int getBleed() {
            return bleed;
        }

        public List<String> getInternalLog() {
            return internalLog;
        }
    }
}
