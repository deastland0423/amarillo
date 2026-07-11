package com.sfb;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game.ActionResult;
import com.sfb.Game.BoardingCombatResult;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.properties.SystemTarget;
import com.sfb.systemgroups.HullBoxes;
import com.sfb.systemgroups.PowerSystems;
import com.sfb.systemgroups.SpecialFunctions;
import com.sfb.utilities.DiceRoller;
import com.sfb.weapons.Weapon;

/**
 * Boarding-party operations: transporter resource checks, crew transport
 * (G8.32), boarding actions (D7.31), hit-and-run raids (D7.8), boarding
 * combat (D7.4), and capture effects (D7.5). Extracted from Game to keep Game
 * focused on state and turn sequencing. Shares Game's capturedThisTurn list by
 * reference; phase checks read Game's current phase through its public getter.
 */
class BoardingResolver {

    private final Game         game;
    private final List<Seeker> seekers;
    private final List<Ship>   capturedThisTurn;

    BoardingResolver(Game game, List<Seeker> seekers, List<Ship> capturedThisTurn) {
        this.game             = game;
        this.seekers          = seekers;
        this.capturedThisTurn = capturedThisTurn;
    }

    // -------------------------------------------------------------------------
    // Hit & Run target enumeration
    // -------------------------------------------------------------------------

    List<SystemTarget> getTargetableSystems(Ship target) {
        List<SystemTarget> systems = new ArrayList<>();

        // Individual weapons
        for (Weapon w : target.getWeapons().fetchAllWeapons()) {
            if (w.isFunctional()) {
                systems.add(new SystemTarget(w));
            }
        }

        // Tractor beams — per beam, so the raid can name the one holding a prize (D7.835)
        for (com.sfb.systemgroups.TractorBeam beam : target.getTractors().getBeams()) {
            if (beam.isFunctional()) {
                systems.add(new SystemTarget(SystemTarget.Type.TRACTOR, beam.getNumber(), beam.describe()));
            }
        }

        // Power — warp targeted per engine (D7.835/D7.8372)
        PowerSystems ps = target.getPowerSystems();
        if (ps.getAvailableLWarp() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.WARP_L, "Left Warp Engine"));
        }
        if (ps.getAvailableRWarp() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.WARP_R, "Right Warp Engine"));
        }
        if (ps.getAvailableCWarp() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.WARP_C, "Center Warp Engine"));
        }
        if (ps.getAvailableImpulse() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.IMPULSE, "Impulse Engines"));
        }
        if (ps.getAvailableBattery() > 0) {
            systems.add(new SystemTarget(SystemTarget.Type.BATTERY, "Batteries"));
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

        // Crew deliberately absent: D7.826 forbids raids on crew units,
        // deck crews, and boarding parties.

        // Cloaking device
        com.sfb.systemgroups.CloakingDevice cloak = target.getCloakingDevice();
        if (cloak != null && cloak.isFunctional()) {
            systems.add(new SystemTarget(SystemTarget.Type.CLOAKING_DEVICE, "Cloaking Device"));
        }

        // DERFACS
        com.sfb.systemgroups.DERFACS derfacsTarget = target.getDerfacs();
        if (derfacsTarget != null && derfacsTarget.isFunctional()) {
            systems.add(new SystemTarget(SystemTarget.Type.DERFACS, "DERFACS"));
        }

        // UIM
        int currentImpulseHR = game.getAbsoluteImpulse();
        com.sfb.systemgroups.UIM activeUimHR = target.getActiveUim(currentImpulseHR);
        if (activeUimHR != null) {
            systems.add(new SystemTarget(SystemTarget.Type.UIM, "UIM"));
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
     * Resolve a wire-format target code into a {@link SystemTarget} on the
     * given ship: {@code "WEAPON:<name>"}, {@code "TRACTOR:<beamNumber>"}, or
     * a {@link SystemTarget.Type} name. Shared by hit-and-run raids and guard
     * posting so both speak the same language. Returns null when unresolvable.
     */
    SystemTarget parseTargetCode(Ship ship, String code) {
        if (code == null || code.isBlank())
            return null;
        String upper = code.toUpperCase();
        if (upper.startsWith("WEAPON:")) {
            String weaponName = code.substring(7);
            Weapon w = ship.getWeapons().fetchAllWeapons().stream()
                    .filter(x -> x.getName().equalsIgnoreCase(weaponName))
                    .findFirst().orElse(null);
            return w == null ? null : new SystemTarget(w);
        }
        if (upper.startsWith("TRACTOR:")) {
            try {
                int n = Integer.parseInt(code.substring(8).trim());
                return new SystemTarget(SystemTarget.Type.TRACTOR, n, "Tractor #" + n);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            SystemTarget.Type type = SystemTarget.Type.valueOf(upper);
            if (type == SystemTarget.Type.WEAPON || type == SystemTarget.Type.TRACTOR)
                return null; // those require the prefixed forms above
            return new SystemTarget(type, code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Transporter actions
    // -------------------------------------------------------------------------

    /**
     * Shared transporter precondition check used by both boarding and H&R actions.
     *
     * <p>
     * Validates phase, cloak, range, lock-on, boarding party count, transporter
     * availability, and shield passability. Auto-lowers the acting ship's facing
     * shield if needed. Spends transporter energy on success.
     *
     * @return null if all preconditions pass (energy already spent), or a failure
     *         {@link ActionResult} describing the first violated condition.
     */
    private ActionResult checkAndSpendTransporterResources(
            Ship actingShip, Ship target, int numParties) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");
        ActionResult cloakBlock = game.cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;

        // Range check
        int range = game.getRange(actingShip, target);
        if (range > 5)
            return ActionResult.fail("Target is out of transporter range (" + range + " hexes, max 5)");

        // Lock-on check (D6.124)
        if (!actingShip.hasLockOn(target))
            return ActionResult.fail("No sensor lock-on to " + target.getName()
                    + " — cannot use transporters (D6.124)");

        // Resource checks
        int availableParties = actingShip.getCrew().getAvailableBoardingParties();
        if (numParties > availableParties)
            return ActionResult.fail("Not enough boarding parties (have " + availableParties
                    + ", need " + numParties + ")");
        int availableTrans = actingShip.getTransporters().getAvailableTrans();
        if (numParties > availableTrans)
            return ActionResult.fail("Not enough transporters (have " + availableTrans
                    + ", need " + numParties + ")");
        int availableUses = actingShip.getTransporters().availableUses();
        if (numParties > availableUses)
            return ActionResult.fail("Not enough transporter energy (have " + availableUses
                    + " use(s), need " + numParties + ")");

        // Shield checks — acting ship's shield facing target must be passable
        int actingShieldNum = game.getShieldNumber(target, actingShip);
        if (!actingShip.getShields().isTransportable(actingShieldNum)) {
            boolean lowered = actingShip.getShields().lowerShield(actingShieldNum);
            if (!lowered)
                return ActionResult.fail("Cannot lower shield #" + actingShieldNum
                        + " on " + actingShip.getName()
                        + " — must wait 8 impulses since last toggle");
        }

        // Target's shield facing the acting ship must already be passable
        int targetShieldNum = game.getShieldNumber(actingShip, target);
        if (!target.getShields().isTransportable(targetShieldNum))
            return ActionResult.fail(target.getName() + " shield #" + targetShieldNum
                    + " is active — cannot beam through");

        // Spend transporter energy
        for (int i = 0; i < numParties; i++)
            actingShip.getTransporters().useTransporter();

        return null; // all clear
    }

    /**
     * Transport crew units from one unit to another (G8.32 non-combat rate).
     *
     * <p>
     * Non-combat rate: 2 crew units per transporter use. Destination may be
     * any Unit; shield check is skipped for non-Ship destinations. Lock-on to
     * the destination is required (G8.17).
     *
     * @param source ship sending crew (must have transporters)
     * @param dest   receiving unit (ship, shuttle, or other)
     * @param amount number of crew units to transport (≥ 1)
     */
    ActionResult transportCrew(Ship source, Unit dest, int amount) {
        if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY)
            return ActionResult.fail("Transporter actions can only be performed during the Activity phase");

        ActionResult cloakBlock = game.cloakActionBlock(source);
        if (cloakBlock != null)
            return cloakBlock;

        if (amount <= 0)
            return ActionResult.fail("Must transport at least 1 crew unit");

        int range = game.getRange(source, dest);
        if (range > 5)
            return ActionResult.fail("Destination is out of transporter range (" + range + " hexes, max 5)");

        if (!source.hasLockOn(dest))
            return ActionResult.fail("No sensor lock-on to " + dest.getName() + " — cannot use transporters (G8.17)");

        int available = source.getCrew().getAvailableCrewUnits();
        if (amount > available)
            return ActionResult.fail("Not enough crew units (have " + available + ", need " + amount + ")");

        // Non-combat rate: 2 crew per transporter use (G8.32)
        int usesNeeded = (int) Math.ceil(amount / 2.0);
        int availTrans = source.getTransporters().getAvailableTrans();
        if (usesNeeded > availTrans)
            return ActionResult.fail("Not enough transporters (have " + availTrans + ", need " + usesNeeded + ")");
        int availUses = source.getTransporters().availableUses();
        if (usesNeeded > availUses)
            return ActionResult
                    .fail("Not enough transporter energy (have " + availUses + " use(s), need " + usesNeeded + ")");

        // Source shield facing dest must be passable (auto-lower if possible)
        int srcShieldNum = game.getShieldNumber(dest, source);
        if (!source.getShields().isTransportable(srcShieldNum)) {
            boolean lowered = source.getShields().lowerShield(srcShieldNum);
            if (!lowered)
                return ActionResult.fail("Cannot lower shield #" + srcShieldNum + " on " + source.getName()
                        + " — must wait 8 impulses since last toggle");
        }

        // Destination shield facing source must already be passable (ships only)
        if (dest instanceof Ship) {
            Ship destShip = (Ship) dest;
            int destShieldNum = game.getShieldNumber(source, destShip);
            if (!destShip.getShields().isTransportable(destShieldNum))
                return ActionResult.fail(dest.getName() + " shield #" + destShieldNum
                        + " is active — cannot beam through (G8.21)");
        }

        // Spend transporters
        for (int i = 0; i < usesNeeded; i++)
            source.getTransporters().useTransporter();

        // Move crew
        source.getCrew().setAvailableCrewUnits(available - amount);
        if (dest instanceof Ship) {
            Ship destShip = (Ship) dest;
            destShip.getCrew().setAvailableCrewUnits(destShip.getCrew().getAvailableCrewUnits() + amount);
        } else {
            dest.getPersonnel().addCrew(amount);
        }

        StringBuilder log = new StringBuilder();
        log.append("=== Transport Crew: ").append(source.getName())
                .append(" → ").append(dest.getName()).append(" ===\n");
        log.append("  ").append(amount).append(" crew unit(s) transported using ")
                .append(usesNeeded).append(" transporter(s) (non-combat rate G8.32)\n");
        if (dest instanceof Ship && ((Ship) dest).isCaptured() && !((Ship) dest).getCrew().isSkeleton()) {
            log.append("  Skeleton crew established — ").append(dest.getName()).append(" is now operable\n");
        }
        return ActionResult.ok(log.toString());
    }

    /**
     * Transport boarding parties onto an enemy ship (D7.31).
     *
     * <p>
     * Same preconditions as H&amp;R: Activity phase, range ≤ 5, lock-on,
     * enough boarding parties and transporter energy, shields passable.
     * The acting ship's facing shield is auto-lowered if needed.
     *
     * <p>
     * On success the parties are deducted from the acting ship and added to
     * the target's enemy troop count. Combat resolves at end of turn via
     * {@link #performBoardingCombat(Ship)}.
     *
     * @param actingShip the ship sending boarding parties
     * @param target     the ship being boarded
     * @param normal     number of normal boarding parties to transport
     * @param commandos  number of commandos to transport
     */
    ActionResult performBoardingAction(Ship actingShip, Ship target,
            int normal, int commandos) {
        int numParties = normal + commandos;
        if (numParties <= 0)
            return ActionResult.fail("Must send at least one boarding party");
        if (actingShip.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot use transporters — breakdown lockout for 8 impulses (C6.5474)");

        // Check commandos available separately
        if (commandos > actingShip.getCrew().getFriendlyTroops().commandos)
            return ActionResult.fail("Not enough commandos (have "
                    + actingShip.getCrew().getFriendlyTroops().commandos
                    + ", need " + commandos + ")");

        ActionResult check = checkAndSpendTransporterResources(actingShip, target, numParties);
        if (check != null)
            return check;

        // Deduct from acting ship
        actingShip.getCrew().getFriendlyTroops().commandos -= commandos;
        actingShip.getCrew().getFriendlyTroops().removeCasualties(normal); // removes normal first

        // Place on target; record attacker for ownership transfer if capture occurs
        // (D7.50)
        target.addEnemyBoardingParties(normal);
        target.addEnemyCommandos(commandos);
        if (target.getBoardingAttacker() == null)
            target.setBoardingAttacker(actingShip.getOwner());

        StringBuilder log = new StringBuilder();
        log.append("=== Boarding Action: ").append(actingShip.getName())
                .append("  →  ").append(target.getName()).append(" ===\n");
        log.append("  Transported: ").append(normal).append(" BP(s)");
        if (commandos > 0)
            log.append(" + ").append(commandos).append(" commando(s)");
        log.append("\n");
        log.append("  Enemy troops now aboard ").append(target.getName())
                .append(": ").append(target.getEnemyTroops()).append("\n");
        log.append("  Combat resolves at end of turn (Final Activity Phase).\n");

        return ActionResult.ok(log.toString());
    }

    // -------------------------------------------------------------------------
    // Hit & Run raids (D7.8)
    // -------------------------------------------------------------------------

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
    ActionResult performHitAndRun(Ship actingShip, Ship target,
            List<SystemTarget> targetSystems) {
        ActionResult cloakBlock = game.cloakActionBlock(actingShip);
        if (cloakBlock != null)
            return cloakBlock;
        if (actingShip.isInBreakdownLockout(game.getAbsoluteImpulse()))
            return ActionResult.fail("Cannot use transporters — breakdown lockout for 8 impulses (C6.5474)");
        if (targetSystems.isEmpty())
            return ActionResult.fail("No boarding parties assigned");

        ActionResult check = checkAndSpendTransporterResources(actingShip, target, targetSystems.size());
        if (check != null)
            return check;

        int actingShieldNum = game.getShieldNumber(target, actingShip);

        // Defending crew quality modifier (D7.73)
        int crewMod = 0;
        com.sfb.systemgroups.Crew.CrewQuality cq = target.getCrew().getCrewQuality();
        if (cq == com.sfb.systemgroups.Crew.CrewQuality.OUTSTANDING)
            crewMod = +1;
        else if (cq == com.sfb.systemgroups.Crew.CrewQuality.POOR)
            crewMod = -1;

        // Roll and apply results
        DiceRoller dice = new DiceRoller();
        StringBuilder log = new StringBuilder();
        log.append("=== Hit & Run Raid: ").append(actingShip.getName())
                .append("  →  ").append(target.getName()).append(" ===\n");
        log.append("  ").append(actingShip.getName()).append(" shield #")
                .append(actingShieldNum).append(" lowered\n");

        int partiesLost = 0;
        for (SystemTarget st : targetSystems) {
            com.sfb.properties.BoardingPartyQuality quality = st.getAttackerQuality();
            int roll = Math.min(6, Math.max(1, dice.rollOneDie() + crewMod));

            // Guard check (D7.831) — the table is indexed by the ATTACKING
            // party's quality; the guard's own quality does not matter.
            com.sfb.objects.GuardPosts.Interception guard =
                    target.getGuardPosts().intercept(st);
            if (guard != null) {
                HarGuardResult guardResult = resolveGuardTable(roll, quality);
                log.append("  Guard present (").append(guard.getDetail()).append(")  roll ").append(roll)
                        .append(" [").append(quality).append("] → ").append(guardResult).append("\n");
                if (guardResult == HarGuardResult.BP_DESTROYED) {
                    partiesLost++;
                    log.append("    Boarding party destroyed by guard\n");
                    continue;
                } else if (guardResult == HarGuardResult.BP_RETURNS) {
                    log.append("    Boarding party repelled — returns safely\n");
                    continue;
                }
                // CONDUCT_HR: fall through to normal H&R roll with a fresh die
                roll = Math.min(6, Math.max(1, dice.rollOneDie() + crewMod));
                log.append("    Guard repelled — conducting H&R roll ").append(roll).append("\n");
            }

            // Normal H&R resolution (D7.81)
            HarResult result = resolveHarTable(roll, quality);
            boolean systemHit = (result == HarResult.SYSTEM_BP_RETURNS || result == HarResult.BOTH_DESTROYED);
            boolean partyLost = (result == HarResult.BOTH_DESTROYED || result == HarResult.BP_DESTROYED);

            String hitResult;
            if (systemHit) {
                boolean damaged = applyHitAndRunHit(target, st);
                hitResult = damaged ? st.getDisplayName() + " DAMAGED"
                        : st.getDisplayName() + " already destroyed";
                // D7.832: the guard's box was just destroyed by the raid
                if (damaged && guard != null) {
                    hitResult += "; " + target.getGuardPosts().onGuardedBoxDestroyedByRaid(guard);
                }
            } else {
                hitResult = st.getDisplayName() + " not damaged";
            }

            log.append("  Roll ").append(roll).append(" [").append(quality).append("]: ")
                    .append(hitResult)
                    .append(",  boarding party ").append(partyLost ? "lost" : "safe").append("\n");
            if (partyLost)
                partiesLost++;
        }

        if (partiesLost > 0) {
            int remaining = actingShip.getCrew().getAvailableBoardingParties() - partiesLost;
            actingShip.getCrew().setAvailableBoardingParties(Math.max(0, remaining));
        }

        log.append("  Boarding parties lost: ").append(partiesLost)
                .append(" / ").append(targetSystems.size()).append(" sent");

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
            case TRACTOR:
                return target.getTractors().destroyBeam(system.getIndex()) != null;
            case WARP_L:
                return target.getPowerSystems().damageLWarp();
            case WARP_R:
                return target.getPowerSystems().damageRWarp();
            case WARP_C:
                return target.getPowerSystems().damageCWarp();
            case IMPULSE:
                return target.getPowerSystems().damageImpulse();
            case SENSORS:
                // Overflow (controlUsed > new limit) is resolved via CONTROL_OVERFLOW interrupt
                return target.getSpecialFunctions().damageSensor();
            case SCANNERS:
                return target.getSpecialFunctions().damageScanner();
            case TRANSPORTERS:
                return target.getTransporters().damage();
            case BATTERY:
                return target.getPowerSystems().damageBattery();
            case FHULL:
                return target.getHullBoxes().damageFhull();
            case AHULL:
                return target.getHullBoxes().damageAhull();
            case CHULL:
                return target.getHullBoxes().damageChull();
            case CLOAKING_DEVICE: {
                com.sfb.systemgroups.CloakingDevice cloak = target.getCloakingDevice();
                if (cloak == null || !cloak.isFunctional())
                    return false;
                cloak.damage(game.getAbsoluteImpulse());
                return true;
            }
            case DERFACS: {
                com.sfb.systemgroups.DERFACS d = target.getDerfacs();
                if (d == null || !d.isFunctional())
                    return false;
                d.damage();
                return true;
            }
            case UIM: {
                com.sfb.systemgroups.UIM uimHit = target.getActiveUim(game.getAbsoluteImpulse());
                if (uimHit == null)
                    return false;
                uimHit.damage();
                return true;
            }
            default:
                return false;
        }
    }

    // -------------------------------------------------------------------------
    // Boarding party combat (D7.3 / D7.4)
    // -------------------------------------------------------------------------

    /**
     * Resolve one round of boarding party combat on {@code defender} (D7.4).
     *
     * <p>
     * Called during the Final Activity Phase (D7.32) for each ship that has
     * enemy boarding parties on board.
     *
     * <p>
     * Step 3 (specific allocation) is skipped — casualty points go straight
     * to Step 4. The {@link BoardingCombatResult} carries all intermediate
     * values so Step 3 can be inserted later.
     *
     * @param defender the ship being boarded
     */
    BoardingCombatResult performBoardingCombat(Ship defender) {
        com.sfb.objects.TroopCount attackers = defender.getEnemyTroops();
        com.sfb.objects.TroopCount defenders = defender.getCrew().getFriendlyTroops();

        StringBuilder log = new StringBuilder();
        log.append("=== Boarding Combat: ").append(defender.getName()).append(" ===\n");
        log.append("  Attackers: ").append(attackers).append("\n");
        log.append("  Defenders: ").append(defenders).append("\n");

        // D7.422: Klingon security station die roll modifier for defender
        int securityMod = klingonSecurityMod(defender);
        if (securityMod > 0)
            log.append("  Security station modifier: +").append(securityMod).append("\n");

        // Step 1 — combat power (D7.41)
        int attackerPower = attackers.total();
        int defenderPower = defenders.total();

        // Step 2 — roll casualty points (D7.42); groups of up to 10
        int attackerPts = rollCasualtyPoints(attackerPower, 0, log, "Attacker");
        int defenderPts = rollCasualtyPoints(defenderPower, securityMod, log, "Defender");

        // Step 3 — specific allocation: DEFERRED (future hook)
        // int attackerPtsAfterStep3 = attackerPts; // will be reduced by captured rooms
        // int defenderPtsAfterStep3 = defenderPts;

        // Step 4 — apply casualties (D7.44)
        // Attacker casualty points kill defender BPs; then capture control rooms if BPs
        // exhausted.
        int defenderBPsLost = 0;
        int controlRoomsCaptured = 0;

        // 4a: kill defender BPs
        int defBPsToRemove = Math.min(attackerPts, defenderPower);
        defenderBPsLost = defenders.removeCasualties(defBPsToRemove);
        int remainingAttackerPts = attackerPts - defenderBPsLost;
        log.append("  Defender loses ").append(defenderBPsLost).append(" BP(s). Remaining: ")
                .append(defenders).append("\n");

        // 4b: excess points capture control rooms (simplified D7.361 fallback)
        if (remainingAttackerPts > 0) {
            com.sfb.systemgroups.ControlSpaces cs = defender.getControlSpaces();
            for (com.sfb.systemgroups.ControlSpaces.RoomType room : com.sfb.systemgroups.ControlSpaces.RoomType
                    .values()) {
                while (remainingAttackerPts >= com.sfb.systemgroups.ControlSpaces.captureCost(room)
                        && cs.captureRoom(room)) {
                    remainingAttackerPts -= com.sfb.systemgroups.ControlSpaces.captureCost(room);
                    controlRoomsCaptured++;
                    log.append("  Control room captured: ").append(room).append("\n");
                }
            }
        }

        // Defender casualty points kill attacker BPs
        int attackerBPsLost = Math.min(defenderPts, attackerPower);
        attackers.removeCasualties(attackerBPsLost);
        log.append("  Attacker loses ").append(attackerBPsLost).append(" BP(s). Remaining: ")
                .append(attackers).append("\n");

        // Capture check (D7.50)
        boolean shipCaptured = defender.getControlSpaces().allControlRoomsCaptured();
        if (shipCaptured) {
            defender.setCaptured(true);
            capturedThisTurn.add(defender);
            // D7.834: on capture, all guards convert to boarding-party status
            // (free to attempt to retake the ship)
            int freedGuards = defender.getGuardPosts().releaseAll();
            if (freedGuards > 0)
                log.append("  ").append(freedGuards)
                        .append(" guard(s) leave their posts and revert to boarding parties (D7.834)\n");
            applyD753CaptureEffects(defender, log);
        } else if (attackers.isEmpty()) {
            // All attackers killed — no longer boarding, clear attacker record
            defender.setBoardingAttacker(null);
        }

        return new BoardingCombatResult(attackerPts, defenderPts,
                defenderBPsLost, attackerBPsLost, controlRoomsCaptured, shipCaptured,
                log.toString());
    }

    /**
     * D7.53: Immediate effects applied the moment a ship is captured.
     */
    private void applyD753CaptureEffects(Ship defender, StringBuilder log) {
        log.append("  *** ").append(defender.getName()).append(" CAPTURED ***\n");

        // D7.50: transfer ownership to the capturing player
        Player captor = defender.getBoardingAttacker();
        Player originalOwner = defender.getOwner();
        // Remember the side the ship was captured FROM — the scoreboard awards
        // the 200% capture VP to the captor, attributed against this team.
        if (originalOwner != null)
            defender.setCapturedFromTeam(originalOwner.getTeamName());
        if (captor != null && captor != originalOwner) {
            if (originalOwner != null)
                originalOwner.getPlayerUnits().remove(defender);
            captor.getPlayerUnits().add(defender);
            defender.setOwner(captor);
            log.append("  D7.50: ").append(defender.getName())
                    .append(" ownership transferred to ").append(captor.getName()).append("\n");
        }

        // D7.512: original crew become prisoners; ship frozen until skeleton crew
        // arrives
        int prisonerCount = defender.getCrew().getAvailableCrewUnits();
        if (prisonerCount > 0) {
            defender.getCrew().addCapturedCrew(prisonerCount);
            defender.getCrew().setAvailableCrewUnits(0);
            log.append("  D7.512: ").append(prisonerCount).append(" crew unit(s) taken prisoner\n");
        }

        // D7.532: stop ECM/EW
        defender.setEcmAllocated(0);
        defender.setEccmAllocated(0);

        // D7.531: release/orphan all seeking weapons controlled by this ship
        int seekersReleased = 0;
        for (Seeker s : seekers) {
            if (defender.equals(s.getController())) {
                s.setController(null);
                seekersReleased++;
            }
        }
        if (seekersReleased > 0)
            log.append("  D7.531: ").append(seekersReleased).append(" seeking weapon(s) released\n");

        // D7.537: cancel all in-progress heavy weapon arming
        int weaponsReset = 0;
        for (com.sfb.weapons.Weapon w : defender.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.HeavyWeapon) {
                com.sfb.weapons.HeavyWeapon hw = (com.sfb.weapons.HeavyWeapon) w;
                if (hw.isArmed() || hw.getArmingTurn() > 0) {
                    hw.reset();
                    weaponsReset++;
                }
            }
        }
        if (weaponsReset > 0)
            log.append("  D7.537: ").append(weaponsReset).append(" weapon(s) disarmed\n");
    }

    /**
     * Roll casualty points for one side using groups of up to 10 (D7.42).
     * The {@code dieMod} is added to each group's roll (clamped 1–6).
     */
    private int rollCasualtyPoints(int totalBPs, int dieMod, StringBuilder log, String side) {
        if (totalBPs == 0) {
            log.append("  ").append(side).append(": 0 BPs — no roll\n");
            return 0;
        }
        DiceRoller dice = new DiceRoller();
        int totalPoints = 0;
        int remaining = totalBPs;
        int groupNum = 1;
        while (remaining > 0) {
            int groupSize = Math.min(remaining, 10);
            int roll = Math.min(6, Math.max(1, dice.rollOneDie() + dieMod));
            int pts = D7421_TABLE[roll - 1][groupSize - 1];
            totalPoints += pts;
            log.append("  ").append(side).append(" group ").append(groupNum)
                    .append(" (").append(groupSize).append(" BPs): roll ").append(roll)
                    .append(" → ").append(pts).append(" pt(s)\n");
            remaining -= groupSize;
            groupNum++;
        }
        log.append("  ").append(side).append(" total casualty pts: ").append(totalPoints).append("\n");
        return totalPoints;
    }

    /**
     * D7.421 Marine Casualty Resolution Table.
     * Index: [dieRoll-1][groupSize-1], values are casualty points.
     *
     * <pre>
     * Roll  1  2  3  4  5  6  7  8  9 10
     *   1   0  0  0  0  1  1  1  1  2  2
     *   2   0  0  1  1  1  2  2  2  2  2
     *   3   0  1  1  1  2  2  2  2  3  3
     *   4   0  1  1  2  2  2  3  3  3  4
     *   5   1  1  2  2  3  3  4  4  5  5
     *   6   1  1  2  2  3  4  4  5  5  6
     * </pre>
     */
    static final int[][] D7421_TABLE = {
            { 0, 0, 0, 0, 1, 1, 1, 1, 2, 2 }, // roll 1
            { 0, 0, 1, 1, 1, 2, 2, 2, 2, 2 }, // roll 2
            { 0, 1, 1, 1, 2, 2, 2, 2, 3, 3 }, // roll 3
            { 0, 1, 1, 2, 2, 2, 3, 3, 3, 4 }, // roll 4
            { 1, 1, 2, 2, 3, 3, 4, 4, 5, 5 }, // roll 5
            { 1, 1, 2, 2, 3, 4, 4, 5, 5, 6 }, // roll 6
    };

    /**
     * Klingon security station die-roll modifier for defender (D7.422).
     * +1 per undestroyed, uncaptured security station box, max +2.
     */
    static int klingonSecurityMod(Ship ship) {
        if (ship.getFaction() != com.sfb.properties.Faction.Klingon)
            return 0;
        int stations = ship.getControlSpaces().getAvailableSecurity()
                - ship.getControlSpaces().getCapturedSecurity();
        return Math.min(2, Math.max(0, stations));
    }

    // -------------------------------------------------------------------------
    // H&R table resolution (D7.81 / D7.831)
    // -------------------------------------------------------------------------

    enum HarResult {
        SYSTEM_BP_RETURNS, // System destroyed, BP returns safely
        BOTH_DESTROYED, // Both system and BP destroyed
        BP_DESTROYED, // BP destroyed, system ok
        BP_RETURNS // BP returns safely, system ok
    }

    enum HarGuardResult {
        BP_DESTROYED, // Attacking BP destroyed by guard
        BP_RETURNS, // Attacking BP repelled, returns safely
        CONDUCT_HR // Guard fails to stop raid — proceed to normal H&R roll
    }

    /**
     * Resolve the D7.81 Hit-and-Run table for the given die roll and attacker
     * quality.
     * Roll is already clamped 1-6 with crew quality modifier applied.
     */
    static HarResult resolveHarTable(int roll, com.sfb.properties.BoardingPartyQuality quality) {
        switch (quality) {
            case OUTSTANDING:
                if (roll <= 2)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll == 3)
                    return HarResult.BOTH_DESTROYED;
                if (roll == 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            case COMMANDO:
                if (roll == 1)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll <= 3)
                    return HarResult.BOTH_DESTROYED;
                if (roll == 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            case POOR:
                // Roll 1 on POOR column is a blank (treated as BOTH_DESTROYED — best POOR can
                // do)
                if (roll == 1)
                    return HarResult.BOTH_DESTROYED;
                if (roll <= 4)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
            default: // NORMAL
                if (roll == 1)
                    return HarResult.SYSTEM_BP_RETURNS;
                if (roll == 2)
                    return HarResult.BOTH_DESTROYED;
                if (roll <= 5)
                    return HarResult.BP_DESTROYED;
                return HarResult.BP_RETURNS;
        }
    }

    /**
     * Resolve the D7.831 guard table for the given die roll and the ATTACKING
     * boarding party's quality (the columns are attacker quality — the guard's
     * own quality plays no part; user-confirmed ruling 2026-07-11).
     */
    static HarGuardResult resolveGuardTable(int roll, com.sfb.properties.BoardingPartyQuality attackerQuality) {
        switch (attackerQuality) {
            case OUTSTANDING:
                if (roll <= 2)
                    return HarGuardResult.BP_DESTROYED;
                if (roll <= 4)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            case COMMANDO:
                if (roll <= 2)
                    return HarGuardResult.BP_DESTROYED;
                if (roll == 3)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            case POOR:
                if (roll <= 4)
                    return HarGuardResult.BP_DESTROYED;
                if (roll == 5)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
            default: // NORMAL
                if (roll <= 3)
                    return HarGuardResult.BP_DESTROYED;
                if (roll <= 5)
                    return HarGuardResult.BP_RETURNS;
                return HarGuardResult.CONDUCT_HR;
        }
    }
}
