package com.sfb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneController;
import com.sfb.objects.Marker;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DirectFire;
import com.sfb.weapons.Weapon;
import com.sfb.Game.FireResult;
import com.sfb.Game.PendingDacChoice;
import com.sfb.Game.PendingDamage;
import com.sfb.Game.PendingVolley;

/**
 * Direct-fire combat and damage resolution: weapon fire, volley aggregation
 * (C3.14), shield damage and bleed-through, internal damage via the DAC, and
 * player DAC choices. Extracted from Game to keep Game focused on state and
 * turn sequencing. Shares Game's pending-damage lists by reference; phase
 * transitions stay in Game and are reached through package-private hooks.
 */
class DamageResolver {

    private final Game                game;
    private final List<Seeker>        seekers;
    private final List<com.sfb.objects.shuttles.Shuttle> activeShuttles;
    private final List<PendingVolley> pendingVolleys;
    private final List<PendingDamage> pendingInternalDamage;
    private final List<PendingDacChoice> pendingDacChoices;
    private final Set<String>         firedPairsThisPhase;
    private final Map<Ship, List<com.sfb.weapons.Disruptor>> uimUsedThisImpulse;

    DamageResolver(Game game, List<Seeker> seekers,
            List<com.sfb.objects.shuttles.Shuttle> activeShuttles,
            List<PendingVolley> pendingVolleys,
            List<PendingDamage> pendingInternalDamage,
            List<PendingDacChoice> pendingDacChoices,
            Set<String> firedPairsThisPhase,
            Map<Ship, List<com.sfb.weapons.Disruptor>> uimUsedThisImpulse) {
        this.game                  = game;
        this.seekers               = seekers;
        this.activeShuttles        = activeShuttles;
        this.pendingVolleys        = pendingVolleys;
        this.pendingInternalDamage = pendingInternalDamage;
        this.pendingDacChoices     = pendingDacChoices;
        this.firedPairsThisPhase   = firedPairsThisPhase;
        this.uimUsedThisImpulse    = uimUsedThisImpulse;
    }

    // -------------------------------------------------------------------------
    // Shield geometry
    // -------------------------------------------------------------------------

    int getShieldNumber(Marker attacker, Ship target) {
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        int shieldNumber = (shieldFacing % 2 == 0) ? shieldFacing / 2 : (shieldFacing + 1) / 2;
        return Math.max(1, Math.min(6, shieldNumber));
    }

    /**
     * Returns the shield(s) of {@code target} that face toward {@code attacker}.
     * Size 1: attacker is directly in front of a shield face.
     * Size 2: attacker is on the seam between two adjacent shields — caller must
     * ask the player which shield to use.
     */
    java.util.List<Integer> getShieldCandidates(Marker attacker, Ship target) {
        int shieldFacing = target.getRelativeShieldFacing(attacker);
        if (shieldFacing % 2 != 0) {
            // Odd = center of a shield face → single candidate
            return java.util.List.of((shieldFacing + 1) / 2);
        }
        // Even = seam between two adjacent shields → two candidates
        // e.g. 2 → shields 1 & 2, 12 → shields 6 & 1
        int upper = shieldFacing / 2;
        int lower = (upper % 6) + 1;
        return java.util.List.of(upper, lower);
    }

    // -------------------------------------------------------------------------
    // Shield damage and bleed-through
    // -------------------------------------------------------------------------

    /**
     * Mark shield damage from one firing volley (6D2). Bleed-through is queued as
     * pending internal damage; it will not be resolved until
     * resolveInternalDamage() is called at the end of the Direct-Fire segment
     * (6D4).
     */
    FireResult markShieldDamage(Ship target, int shieldNumber, int totalDamage, Ship attacker) {
        int bleed = target.damageShield(shieldNumber, totalDamage);
        if (bleed > 0) {
            pendingInternalDamage.add(new PendingDamage(target, bleed, attacker));
        }
        return new FireResult(bleed, new ArrayList<>());
    }

    /**
     * Apply Hellbore enveloping damage (E10.4) to a ship.
     *
     * Step A: consume general shield reinforcement against the total damage.
     * Step B: find the weakest shield(s); apply one equal damage group to each.
     * Fractional groups: round up for weak shields when fraction >= 0.5,
     * down otherwise (E10.412).
     * Step C: distribute the remaining group across non-weakest shields one point
     * at a time, weakest first.
     *
     * @return log lines describing how damage was distributed.
     */
    List<String> applyHellboreEnvelopingDamage(Ship target, int damage) {
        List<String> log = new ArrayList<>();
        if (damage <= 0)
            return log;

        // Step A: consume general reinforcement
        int genReinf = target.getShields().getGeneralReinforcement();
        if (genReinf > 0) {
            int absorbed = Math.min(damage, genReinf);
            target.getShields().clearGeneralReinforcement();
            damage -= absorbed;
            log.add("  Enveloping — general reinforcement absorbed " + absorbed);
            if (damage <= 0)
                return log;
        }

        // Collect current shield strengths (1-indexed), including specific
        // reinforcement
        int[] strength = new int[6];
        for (int i = 0; i < 6; i++)
            strength[i] = target.getShields().getShieldStrength(i + 1);

        // Step B: find the weakest shield strength
        int minStrength = strength[0];
        for (int s : strength)
            if (s < minStrength)
                minStrength = s;

        int weakCount = 0;
        for (int s : strength)
            if (s == minStrength)
                weakCount++;

        int stepCDamage;
        if (weakCount == 6) {
            // All shields equal — skip Step B, distribute everything in Step C
            stepCDamage = damage;
        } else {
            int groups = 1 + weakCount;
            int perGroup = damage / groups;
            int remainder = damage % groups;
            // Round up for weak shields when fractional part >= 0.5 (E10.412)
            int eachWeak = (remainder * 2 >= groups) ? perGroup + 1 : perGroup;
            stepCDamage = damage - weakCount * eachWeak;

            for (int i = 0; i < 6; i++) {
                if (strength[i] == minStrength) {
                    int bleed = target.damageShield(i + 1, eachWeak);
                    strength[i] = Math.max(0, strength[i] - eachWeak); // track for Step C ordering
                    if (bleed > 0)
                        pendingInternalDamage.add(new PendingDamage(target, bleed));
                    log.add("  Enveloping step B — shield #" + (i + 1) + " (weakest)  " + eachWeak);
                }
            }
        }

        // Step C: distribute remaining damage one point at a time, weakest first
        if (stepCDamage > 0) {
            for (int pt = 0; pt < stepCDamage; pt++) {
                // Find shield with lowest current strength (track externally)
                int minNow = Integer.MAX_VALUE;
                for (int i = 0; i < 6; i++)
                    if (strength[i] < minNow)
                        minNow = strength[i];
                // Pick first shield at that strength
                for (int i = 0; i < 6; i++) {
                    if (strength[i] == minNow) {
                        target.damageShield(i + 1, 1);
                        strength[i] = Math.max(0, strength[i] - 1);
                        break;
                    }
                }
            }
            log.add("  Enveloping step C — " + stepCDamage + " pts distributed weakest-first");
        }

        log.add("  Enveloping total: " + damage + " across all shields");
        return log;
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
    String applyDamageToUnit(int damage, Unit target, int shieldNumber) {
        if (target instanceof Ship) {
            if (damage == com.sfb.weapons.ADD.HIT) {
                return "ADD has no effect on ships";
            }
            Ship ship = (Ship) target;
            FireResult result = markShieldDamage(ship, shieldNumber, damage, null);
            return "Hit " + ship.getName() + " shield " + shieldNumber
                    + " for " + damage + " damage"
                    + (result.getBleed() > 0 ? " (" + result.getBleed() + " bleed)" : "");
        } else if (target instanceof Drone) {
            Drone drone = (Drone) target;
            if (damage == com.sfb.weapons.ADD.HIT) {
                game.removeSeekerFromPlay(drone);
                return "HIT — " + drone.getName() + " destroyed";
            }
            int remaining = drone.getHull() - damage;
            drone.setHull(Math.max(0, remaining));
            if (drone.getHull() <= 0) {
                game.removeSeekerFromPlay(drone);
                return drone.getName() + " destroyed (" + damage + " damage)";
            }
            return drone.getName() + " hit for " + damage
                    + " — " + drone.getHull() + " hull remaining";
        } else if (target instanceof com.sfb.objects.shuttles.Shuttle) {
            com.sfb.objects.shuttles.Shuttle shuttle = (com.sfb.objects.shuttles.Shuttle) target;
            boolean isSeeker = shuttle instanceof Seeker;
            if (damage == com.sfb.weapons.ADD.HIT) {
                int roll = new com.sfb.utilities.DiceRoller().rollOneDie();
                shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - roll));
                if (shuttle.getCurrentHull() <= 0) {
            if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle) {
                // J3.21: a destroyed WW is NOT removed (and not voided) — it flips
                // to its 4-impulse explosion period; ECM continues (J3.2111) and
                // seekers keep following it. Post-explosion pockets can't die again.
                com.sfb.objects.shuttles.WildWeaselShuttle ww =
                        (com.sfb.objects.shuttles.WildWeaselShuttle) shuttle;
                if (!ww.isExploding() && !ww.isPostExplosion()) {
                    ww.startExplosion(game.getAbsoluteImpulse());
                    return "Wild Weasel " + ww.getName()
                            + " destroyed — exploding for 4 impulses (J3.21)";
                }
                return "Wild Weasel " + ww.getName() + " is already destroyed";
            }
                    removeDeadShuttle(shuttle, isSeeker);
                    return "HIT — " + shuttle.getName() + " destroyed (" + roll + " hull damage)";
                }
                StringBuilder addLog = new StringBuilder(
                        "HIT — " + shuttle.getName() + " took " + roll + " hull damage ("
                                + shuttle.getCurrentHull() + " remaining)");
                appendCrippleCheck(shuttle, addLog);
                return addLog.toString();
            }
            shuttle.setCurrentHull(Math.max(0, shuttle.getCurrentHull() - damage));
            if (shuttle.getCurrentHull() <= 0) {
            if (shuttle instanceof com.sfb.objects.shuttles.WildWeaselShuttle) {
                // J3.21: a destroyed WW is NOT removed (and not voided) — it flips
                // to its 4-impulse explosion period; ECM continues (J3.2111) and
                // seekers keep following it. Post-explosion pockets can't die again.
                com.sfb.objects.shuttles.WildWeaselShuttle ww =
                        (com.sfb.objects.shuttles.WildWeaselShuttle) shuttle;
                if (!ww.isExploding() && !ww.isPostExplosion()) {
                    ww.startExplosion(game.getAbsoluteImpulse());
                    return "Wild Weasel " + ww.getName()
                            + " destroyed — exploding for 4 impulses (J3.21)";
                }
                return "Wild Weasel " + ww.getName() + " is already destroyed";
            }
                removeDeadShuttle(shuttle, isSeeker);
                return shuttle.getName() + " destroyed (" + damage + " damage)";
            }
            StringBuilder hitLog = new StringBuilder(shuttle.getName() + " hit for " + damage
                    + " — " + shuttle.getCurrentHull() + " hull remaining");
            appendCrippleCheck(shuttle, hitLog);
            return hitLog.toString();
        } else if (target instanceof PlasmaTorpedo) {
            PlasmaTorpedo torp = (PlasmaTorpedo) target;
            if (damage == com.sfb.weapons.ADD.HIT)
                return "ADD has no effect on plasma torpedoes";
            int before = torp.getCurrentStrength();
            torp.applyPhaserDamage(damage);
            int after = torp.getCurrentStrength();
            if (after <= 0) {
                game.removeSeekerFromPlay(torp);
                return torp.getName() + " destroyed by phaser fire (" + damage + " pts)";
            }
            return torp.getName() + " hit for " + damage + " phaser pts — strength " + before + " → " + after;
        }
        return "Damage to unknown unit type ignored";
    }

    private void removeDeadShuttle(com.sfb.objects.shuttles.Shuttle shuttle, boolean isSeeker) {
        if (isSeeker) {
            game.removeSeekerFromPlay((Seeker) shuttle);
        } else {
            game.removeShuttleFromPlay(shuttle, "target destroyed");
        }
    }

    private void appendCrippleCheck(com.sfb.objects.shuttles.Shuttle shuttle, StringBuilder log) {
        if (shuttle instanceof com.sfb.objects.shuttles.Fighter) {
            com.sfb.objects.shuttles.Fighter f = (com.sfb.objects.shuttles.Fighter) shuttle;
            if (f.shouldCripple())
                log.append("\n  ").append(f.applyCripplingEffects());
        }
    }

    // -------------------------------------------------------------------------
    // Weapons fire
    // -------------------------------------------------------------------------

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
    String fireWeapons(Unit attacker, Unit target, List<Weapon> selected,
            int range, int adjustedRange, int shieldNumber, boolean useUim, boolean directFire) {
        if (attacker instanceof Ship && ((Ship) attacker).isCaptured())
            return attacker.getName() + " cannot fire — ship is captured (D7.55)";
        if (attacker instanceof Ship && ((Ship) attacker).getCrew().isSkeleton())
            return attacker.getName() + " cannot fire — undermanned (G9.42)";
        if (attacker instanceof Ship && ((Ship) attacker).isInBreakdownLockout(game.getAbsoluteImpulse()))
            return attacker.getName() + " cannot fire — breakdown lockout for 8 impulses (C6.5471)";
        // G7.91: tractored ship can only fire direct-fire weapons at the holding ship
        if (attacker instanceof Ship && ((Ship) attacker).isTractored() && target instanceof Ship) {
            Unit holder = ((Ship) attacker).getTractoringUnit();
            if (target != holder)
                return attacker.getName() + " cannot fire at " + target.getName()
                        + " — tractored ships may only fire direct-fire weapons at the holding ship (G7.91)";
        }
        if (attacker instanceof Ship && !((Ship) attacker).isActiveFireControl()) {
            if (range > 5)
                return attacker.getName()
                        + " cannot fire — target out of passive fire control range (5 hexes max, D19.23)";
        }
        if (attacker instanceof com.sfb.objects.shuttles.Shuttle) {
            com.sfb.objects.shuttles.Shuttle s = (com.sfb.objects.shuttles.Shuttle) attacker;
            if (s.isBeingRecovered())
                return attacker.getName() + " is shut down for recovery and cannot fire (J1.622)";
            if (!s.canFireDirect(game.getAbsoluteImpulse()))
                return attacker.getName() + " cannot fire yet — 8 impulses must pass since launch";
            if (s.isChaffLockedOut(game.getAbsoluteImpulse()))
                return attacker.getName() + " cannot fire — chaff lockout for 8 impulses (D11.41)";
        }
        Game.ActionResult cloakBlock = attacker instanceof Ship ? game.cloakActionBlock((Ship) attacker) : null;
        if (cloakBlock != null)
            return cloakBlock.getMessage();

        // Each attacker may fire at a given target only once per Direct-Fire segment
        String firePair = attacker.getName() + "→" + target.getName();
        if (!firedPairsThisPhase.add(firePair))
            return attacker.getName() + " has already fired at " + target.getName() + " this segment";

        // Firing while WW active voids the WW (J3.132)
        if (attacker instanceof Ship && ((Ship) attacker).hasActiveWildWeasel())
            game.voidWildWeasel((Ship) attacker);

        // Only phasers can damage plasma torpedoes (FP7.0)
        if (target instanceof PlasmaTorpedo) {
            boolean allPhasers = selected.stream().allMatch(w -> w instanceof com.sfb.weapons.PhaserWeapon);
            if (!allPhasers)
                return "Only phasers can damage plasma torpedoes (FP7.0)";
        }

        StringBuilder log = new StringBuilder();
        log.append(attacker.getName()).append("  →  ").append(target.getName())
                .append("   range ").append(range);
        if (adjustedRange != range)
            log.append("  (effective ").append(adjustedRange).append(")");
        log.append("   shield #").append(shieldNumber).append("\n");

        int totalDamage = 0;
        int envelopingHellboreDamage = 0;
        boolean addHit = false;
        boolean fusionSuicideFired = false;

        Ship attackerShip = attacker instanceof Ship ? (Ship) attacker : null;
        com.sfb.systemgroups.DERFACS derfacs = attackerShip != null ? attackerShip.getDerfacs() : null;
        boolean hasDerfacs = derfacs != null && derfacs.isFunctional();

        int currentImpulse = game.getAbsoluteImpulse();
        com.sfb.systemgroups.UIM activeUim = (useUim && attackerShip != null)
                ? attackerShip.getActiveUim(currentImpulse)
                : null;
        boolean uimInUse = activeUim != null;
        java.util.List<com.sfb.weapons.Disruptor> uimFiredDisruptors = new java.util.ArrayList<>();

        // D6.34/D6.35: net ECM = target ECM − attacker ECCM; shift = floor(√net)
        Ship targetShip = target instanceof Ship ? (Ship) target : null;
        int targetEcm = targetShip != null ? targetShip.getEcmAllocated() : 0;
        int attackerEccm = attackerShip != null && attackerShip.isActiveFireControl()
                ? attackerShip.getEccmAllocated()
                : 0;
        int netEcm = Math.max(0, targetEcm - attackerEccm);
        int ecmShift = (int) Math.floor(Math.sqrt(netEcm));
        if (ecmShift > 0)
            log.append("  ECM shift: +").append(ecmShift).append(" (target ECM ").append(targetEcm)
                    .append(", attacker ECCM ").append(attackerEccm).append(")\n");

        for (Weapon w : selected) {
            w.setEcmShift(ecmShift);
            if (!w.isFunctional()) {
                log.append("  ").append(w.getName()).append("  destroyed — cannot fire\n");
                continue;
            }
            try {
                boolean isFusionSuicide = w instanceof com.sfb.weapons.Fusion
                        && ((com.sfb.weapons.Fusion) w).getArmingType() == com.sfb.properties.WeaponArmingType.SPECIAL;
                int dmg;
                if (uimInUse && w instanceof com.sfb.weapons.Disruptor) {
                    com.sfb.weapons.Disruptor d = (com.sfb.weapons.Disruptor) w;
                    if (d.isUimLocked(currentImpulse)) {
                        log.append("  ").append(w.getName()).append("  UIM-locked\n");
                        continue;
                    }
                    dmg = d.fireUim(range, adjustedRange);
                    uimFiredDisruptors.add(d);
                } else if (hasDerfacs && w instanceof com.sfb.weapons.Disruptor) {
                    dmg = ((com.sfb.weapons.Disruptor) w).fireDerfacs(range, adjustedRange);
                } else if (directFire && w instanceof com.sfb.weapons.Hellbore) {
                    dmg = ((com.sfb.weapons.Hellbore) w).fireDirect(adjustedRange);
                } else {
                    dmg = ((DirectFire) w).fire(range, adjustedRange);
                }
                if (isFusionSuicide)
                    fusionSuicideFired = true;
                // G13.37: each weapon that hits a fully cloaked ship rolls the
                // fire adjustment chart. Fades use normal EW instead (G13.362).
                if (dmg > 0 && dmg != ADD.HIT && targetShip != null
                        && targetShip.getCloakingDevice() != null
                        && targetShip.getCloakingDevice().breaksLockOn()) {
                    int fireAdj = new com.sfb.utilities.DiceRoller().rollOneDie();
                    int scaled = com.sfb.systemgroups.CloakingDevice.fireAdjustedDamage(dmg, fireAdj);
                    log.append("  ").append(w.getName())
                            .append("  fire adjustment vs cloak (G13.37): die ").append(fireAdj)
                            .append(" → ").append(com.sfb.systemgroups.CloakingDevice.fireAdjustmentLabel(fireAdj))
                            .append(scaled != dmg ? " (" + dmg + " → " + scaled + ")" : "")
                            .append("\n");
                    dmg = scaled;
                }
                String rollStr = w.getLastRoll() > 0 ? "  (die " + w.getLastRoll() + ")" : "";
                if (dmg == ADD.HIT) {
                    addHit = true;
                    log.append("  ").append(w.getName()).append(rollStr).append("  HIT\n");
                } else if (!directFire && w instanceof com.sfb.weapons.Hellbore) {
                    envelopingHellboreDamage += dmg;
                    log.append("  ").append(w.getName()).append(rollStr)
                            .append(dmg > 0 ? "  HIT  " + dmg + " (enveloping)" : "  MISS").append("\n");
                } else {
                    totalDamage += dmg;
                    log.append("  ").append(w.getName()).append(rollStr)
                            .append(dmg > 0
                                    ? "  HIT  " + dmg
                                            + (directFire && w instanceof com.sfb.weapons.Hellbore ? " (direct)" : "")
                                    : "  MISS")
                            .append("\n");
                }
            } catch (WeaponUnarmedException ex) {
                log.append("  ").append(w.getName()).append("  unarmed\n");
            } catch (TargetOutOfRangeException ex) {
                log.append("  ").append(w.getName()).append("  out of range\n");
            } catch (CapacitorException ex) {
                log.append("  ").append(w.getName()).append("  no capacitor energy\n");
            } finally {
                w.setEcmShift(0);
            }
        }

        // UIM: accumulate disruptors that fired under UIM this impulse.
        if (uimInUse && !uimFiredDisruptors.isEmpty() && attackerShip != null) {
            uimUsedThisImpulse
                    .computeIfAbsent(attackerShip, k -> new ArrayList<>())
                    .addAll(uimFiredDisruptors);
        }

        if (target instanceof Ship) {
            // Queue the volley — damage applied after defenders spend reserve power.
            // (When Base is implemented add: || target instanceof Base)
            log.append("  Total damage: ").append(totalDamage)
                    .append(" — queued, resolves in Reinforcement phase\n");
            pendingVolleys.add(new PendingVolley(
                    attacker.getName(), attackerShip, target,
                    shieldNumber, totalDamage, envelopingHellboreDamage,
                    addHit, fusionSuicideFired, log.toString(), null));
        } else {
            // Non-ship targets (seekers, shuttles) have no shields — apply immediately.
            if (fusionSuicideFired && attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(attackerShip, 1));
                log.append("  Fusion suicide overload — 1 internal damage to ")
                        .append(attackerShip.getName()).append("\n");
            }
            if (addHit) {
                String dmgLog = applyDamageToUnit(ADD.HIT, target, shieldNumber);
                log.append("  ADD result: ").append(dmgLog).append("\n");
            }
            if (totalDamage > 0) {
                String dmgLog = applyDamageToUnit(totalDamage, target, shieldNumber);
                log.append("  ").append(dmgLog).append("\n");
            }
        }

        return log.toString();
    }

    // -------------------------------------------------------------------------
    // Volley aggregation and application
    // -------------------------------------------------------------------------

    /**
     * Apply all pending fire volleys (called when transitioning out of
     * REINFORCEMENT). Defenders must have already submitted any reinforcement
     * decisions before this runs.
     *
     * All damage arriving through the same shield facing in the same phase is one
     * volley (C3.14): shields absorb the combined total and bleed-through runs
     * through the DAC once.  EPT volleys (envelopingTorp != null) are always
     * separate and distribute across all 6 shields.  Enveloping Hellbore damage
     * is always a separate volley per E10.43 and is not combined.
     */
    List<String> applyPendingVolleys() {
        List<String> log = new ArrayList<>();

        // Helper: one accumulated group per (target identity, shieldNumber)
        class ShieldGroup {
            final Unit        target;
            final int         shieldNumber;
            final StringBuilder pvLog = new StringBuilder();
            int               totalDamage = 0;
            Ship              lastAttacker = null;
            final List<Integer> hellboreDamages = new ArrayList<>();

            ShieldGroup(Unit target, int shieldNumber) {
                this.target      = target;
                this.shieldNumber = shieldNumber;
            }
        }

        List<ShieldGroup>  groups  = new ArrayList<>();
        List<PendingVolley> eptVolleys = new ArrayList<>();

        for (PendingVolley pv : pendingVolleys) {
            // EPT: distribute to all shields — always separate
            if (pv.envelopingTorp != null) { eptVolleys.add(pv); continue; }

            // Non-Ship targets (seekers, shuttles) have no shields — apply immediately
            if (!(pv.target instanceof Ship)) {
                StringBuilder pvLog = new StringBuilder(pv.attackerLog);
                if (pv.fusionSuicideFired && pv.attackerShip != null) {
                    pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                    pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                         .append(pv.attackerShip.getName()).append("\n");
                }
                if (pv.addHit) {
                    pvLog.append("  ADD result: ")
                         .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
                }
                if (pv.totalDamage > 0) {
                    pvLog.append("  ")
                         .append(applyDamageToUnit(pv.totalDamage, pv.target, pv.shieldNumber))
                         .append("\n");
                }
                log.add(pvLog.toString());
                continue;
            }

            // Find or create the group for this (target, shield facing)
            ShieldGroup g = null;
            for (ShieldGroup candidate : groups)
                if (candidate.target == pv.target && candidate.shieldNumber == pv.shieldNumber)
                    { g = candidate; break; }
            if (g == null) { g = new ShieldGroup(pv.target, pv.shieldNumber); groups.add(g); }

            // Per-volley effects accumulated into the group log
            g.pvLog.append(pv.attackerLog);
            if (pv.fusionSuicideFired && pv.attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                g.pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                       .append(pv.attackerShip.getName()).append("\n");
            }
            if (pv.addHit) {
                g.pvLog.append("  ADD result: ")
                       .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
            }

            g.totalDamage += pv.totalDamage;
            if (pv.attackerShip != null) g.lastAttacker = pv.attackerShip;
            // Hellbore enveloping stays per-volley (E10.43)
            if (pv.envelopingHellboreDamage > 0)
                g.hellboreDamages.add(pv.envelopingHellboreDamage);
        }

        // Apply each group: combined shield damage → single bleed-through chain
        for (ShieldGroup g : groups) {
            Ship target = (Ship) g.target;
            int bleed = target.damageShield(g.shieldNumber, g.totalDamage);
            if (bleed > 0) {
                g.pvLog.append("  BLEED-THROUGH: ").append(bleed)
                       .append(" (resolves at end of segment)\n");
                pendingInternalDamage.add(new PendingDamage(target, bleed, g.lastAttacker));
            }
            // Enveloping Hellbore: each volley is its own separate bleed chain (E10.43)
            for (int envDmg : g.hellboreDamages) {
                g.pvLog.append("  Hellbore enveloping volley: ").append(envDmg).append("\n");
                for (String line : applyHellboreEnvelopingDamage(target, envDmg))
                    g.pvLog.append(line).append("\n");
            }
            log.add(g.pvLog.toString());
        }

        // EPT volleys: spread damage across all 6 shields (always separate)
        for (PendingVolley pv : eptVolleys) {
            StringBuilder pvLog = new StringBuilder(pv.attackerLog);
            if (pv.fusionSuicideFired && pv.attackerShip != null) {
                pendingInternalDamage.add(new PendingDamage(pv.attackerShip, 1));
                pvLog.append("  Fusion suicide overload — 1 internal damage to ")
                     .append(pv.attackerShip.getName()).append("\n");
            }
            if (pv.addHit) {
                pvLog.append("  ADD result: ")
                     .append(applyDamageToUnit(ADD.HIT, pv.target, pv.shieldNumber)).append("\n");
            }
            int[] spread = pv.envelopingTorp.computeEnvelopingDamage(pv.totalDamage);
            for (int i = 0; i < 6; i++)
                if (spread[i] > 0) markShieldDamage((Ship) pv.target, i + 1, spread[i], null);
            if (pv.envelopingHellboreDamage > 0) {
                pvLog.append("  Hellbore enveloping volley: ").append(pv.envelopingHellboreDamage).append("\n");
                for (String line : applyHellboreEnvelopingDamage((Ship) pv.target, pv.envelopingHellboreDamage))
                    pvLog.append(line).append("\n");
            }
            log.add(pvLog.toString());
        }

        pendingVolleys.clear();
        firedPairsThisPhase.clear();
        return log;
    }

    // -------------------------------------------------------------------------
    // Internal damage (DAC)
    // -------------------------------------------------------------------------

    /**
     * Resolve all queued internal damage (6D4). Stops early and transitions to
     * DAC_CHOICE (via Game) if the defender must pick which system is hit.
     * Remaining items stay in {@code pendingInternalDamage} so resolution
     * resumes after the choice.
     */
    void resolveInternalDamage() {
        while (!pendingInternalDamage.isEmpty()) {
            PendingDamage pd = pendingInternalDamage.remove(0);
            if (!pd.isContinuation)
                pd.target.resetPhaserDacGroup();
            Ship.DamageResult result = pd.target.applyInternalDamage(pd.bleed, pd.attacker);
            game.internalDamageLog().add("=== Internal damage — " + pd.target.getName() + " ===");
            game.internalDamageLog().addAll(result.log);
            if (result.choiceRequired) {
                pendingDacChoices.add(new PendingDacChoice(
                        pd.target, pd.attacker, result.choiceType,
                        result.choiceRoll, result.options, result.remainingBleed));
                game.enterDacChoicePhase();
                game.cleanupDestroyedShips();
                return;
            }
        }
        // D7.832/D7.8375: combat damage may have destroyed guarded posts —
        // roll guard casualties once resolution completes
        for (Ship ship : game.getShips()) {
            for (String line : ship.getGuardPosts().reconcileAfterDamage())
                game.internalDamageLog().add("  [" + ship.getName() + "] " + line);
        }
        game.cleanupDestroyedShips();
        game.checkControlOverflow();
    }

    /**
     * Apply the defender's DAC system choice (the mechanics half of
     * Game.submitDacChoice — validation and phase bookkeeping stay in Game).
     */
    void applyDacChoice(PendingDacChoice pending, String chosenSystem) {
        if ("shuttle".equals(pending.dacType)) {
            // Parse "bay:<b>:space:<s>"
            String[] parts = chosenSystem.split(":");
            int bayIdx = Integer.parseInt(parts[1]);
            int spaceIdx = Integer.parseInt(parts[3]);

            com.sfb.systemgroups.ShuttleBay bay = pending.targetShip.getShuttles().getBays().get(bayIdx);
            int killedCrews = bay.getSpaces().get(spaceIdx).getDeckCrews();
            com.sfb.objects.shuttles.Shuttle was = bay.destroySpace(spaceIdx);

            String occupant = was != null ? was.getName() : "empty space";
            game.internalDamageLog().add("  shuttle DAC hit: bay " + bayIdx + " space " + spaceIdx
                    + " (" + occupant + ") DESTROYED");

            if (killedCrews > 0) {
                pending.targetShip.getCrew().killDeckCrews(killedCrews);
                game.internalDamageLog().add("  → " + killedCrews + " deck crew(s) killed in bay destruction");
            }

            if (was != null && was.isArmed()) {
                game.internalDamageLog().add("  → armed shuttle destroyed — chain reaction! (D12.10)");

                // Chain reaction: one additional space destroyed in the same bay (player
                // chooses)
                java.util.List<String> chainOpts = new java.util.ArrayList<>();
                java.util.List<com.sfb.systemgroups.ShuttleSpace> baySpaces = bay.getSpaces();
                for (int s = 0; s < baySpaces.size(); s++) {
                    if (!baySpaces.get(s).isDestroyed())
                        chainOpts.add("bay:" + bayIdx + ":space:" + s);
                }
                if (!chainOpts.isEmpty()) {
                    pendingDacChoices.add(0, new PendingDacChoice(
                            pending.targetShip, pending.attackerShip, "shuttle",
                            -1, chainOpts, 0, bayIdx));
                }

                // One random internal damage point on the ship (separate volley, D12.10)
                pendingInternalDamage.add(0, new PendingDamage(
                        pending.targetShip, 1, pending.attackerShip));
            }
        } else {
            String hitLabel = pending.targetShip.applyDacChoiceHit(
                    pending.dacType, chosenSystem, pending.attackerShip);
            game.internalDamageLog().add("  internal [" + pending.roll + "]: " + pending.dacType
                    + " — player chose " + chosenSystem
                    + (hitLabel != null ? " → " + hitLabel : " (no effect)"));

            // Prepend remaining bleed for this ship so resolveInternalDamage picks it up
            if (pending.remainingBleed > 0)
                pendingInternalDamage.add(0, new PendingDamage(
                        pending.targetShip, pending.remainingBleed, pending.attackerShip, true));
        }
    }
}
