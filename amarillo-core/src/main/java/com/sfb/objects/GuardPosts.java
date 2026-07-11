package com.sfb.objects;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.sfb.properties.BoardingPartyQuality;
import com.sfb.properties.SystemTarget;
import com.sfb.systemgroups.TractorBeam;
import com.sfb.weapons.Weapon;

/**
 * The ship's guard posts (D7.83). A guard is a real boarding party pulled
 * from the ship's roster while posted (D7.834 — it does not fight in
 * boarding combat), returned on release, and permanently lost if killed.
 * Guards persist across turns; posting changes happen during Energy
 * Allocation (D7.83).
 *
 * <p>Granularity follows the damage model:
 * <ul>
 * <li><b>Exact posts</b> — one guard per weapon instance (D7.8374), per
 * tractor beam, per warp engine / all impulse (D7.8372), per sensor or
 * scanner track (D7.8373), per cloak/DERFACS device.</li>
 * <li><b>Pool posts</b> — transporters, hull, batteries are fungible boxes
 * with no individual identity here, so up to N guards may be posted on an
 * N-box pool and a raid's chance of meeting one is guards/boxes: identical
 * in expectation to the book's hidden per-box placement (D7.8374).</li>
 * </ul>
 */
public class GuardPosts {

    private static final Set<SystemTarget.Type> SINGLETON_TYPES = EnumSet.of(
            SystemTarget.Type.WARP_L, SystemTarget.Type.WARP_R, SystemTarget.Type.WARP_C,
            SystemTarget.Type.IMPULSE, SystemTarget.Type.SENSORS, SystemTarget.Type.SCANNERS,
            SystemTarget.Type.CLOAKING_DEVICE, SystemTarget.Type.DERFACS);

    private static final Set<SystemTarget.Type> POOL_TYPES = EnumSet.of(
            SystemTarget.Type.TRANSPORTERS, SystemTarget.Type.BATTERY,
            SystemTarget.Type.FHULL, SystemTarget.Type.AHULL, SystemTarget.Type.CHULL);

    /** Handle to the guard that intercepted a raid, for D7.832 resolution. */
    public static final class Interception {
        final SystemTarget.Type type; // null for WEAPON
        final Weapon weapon;          // non-null only for weapon posts
        final int beamNumber;         // non-zero only for tractor posts
        final String detail;          // log fragment, e.g. "2 guard(s) / 3 boxes"

        private Interception(SystemTarget.Type type, Weapon weapon, int beamNumber, String detail) {
            this.type = type;
            this.weapon = weapon;
            this.beamNumber = beamNumber;
            this.detail = detail;
        }

        public String getDetail() {
            return detail;
        }
    }

    private final Ship ship;

    private final Map<Weapon, BoardingPartyQuality> weaponGuards = new LinkedHashMap<>();
    private final Map<Integer, BoardingPartyQuality> tractorGuards = new TreeMap<>();
    private final Map<SystemTarget.Type, BoardingPartyQuality> singletonGuards =
            new EnumMap<>(SystemTarget.Type.class);
    private final Map<SystemTarget.Type, List<BoardingPartyQuality>> poolGuards =
            new EnumMap<>(SystemTarget.Type.class);

    /** Pool box counts at last reconcile, to spot combat-damage losses. */
    private final Map<SystemTarget.Type, Integer> poolLastKnown =
            new EnumMap<>(SystemTarget.Type.class);

    Random random = new Random(); // package-visible for deterministic tests

    GuardPosts(Ship ship) {
        this.ship = ship;
    }

    // -------------------------------------------------------------------------
    // Posting and release (Energy Allocation, D7.83)
    // -------------------------------------------------------------------------

    /**
     * Post a boarding party as a guard on the given target.
     *
     * @return null on success, or an error message.
     */
    public String assign(SystemTarget target, BoardingPartyQuality quality) {
        if (quality != BoardingPartyQuality.NORMAL && quality != BoardingPartyQuality.COMMANDO)
            return "Guards are boarding parties: NORMAL or COMMANDO only";
        String availErr = checkTargetGuardable(target);
        if (availErr != null)
            return availErr;
        if (!takeFromRoster(quality))
            return "No " + quality + " boarding party available to post";

        switch (kindOf(target)) {
            case WEAPON:   weaponGuards.put(target.getWeapon(), quality); break;
            case TRACTOR:  tractorGuards.put(target.getIndex(), quality); break;
            default:
                if (POOL_TYPES.contains(target.getType())) {
                    poolGuards.computeIfAbsent(target.getType(), t -> new ArrayList<>()).add(quality);
                    poolLastKnown.put(target.getType(), poolBoxCount(target.getType()));
                } else {
                    singletonGuards.put(target.getType(), quality);
                }
        }
        return null;
    }

    /**
     * Withdraw a guard from the given target, returning the BP to the roster.
     *
     * @return null on success, or an error message.
     */
    public String release(SystemTarget target) {
        BoardingPartyQuality freed;
        switch (kindOf(target)) {
            case WEAPON:
                freed = weaponGuards.remove(target.getWeapon());
                break;
            case TRACTOR:
                freed = tractorGuards.remove(target.getIndex());
                break;
            default:
                if (POOL_TYPES.contains(target.getType())) {
                    List<BoardingPartyQuality> list = poolGuards.get(target.getType());
                    freed = (list == null || list.isEmpty()) ? null : list.remove(list.size() - 1);
                } else {
                    freed = singletonGuards.remove(target.getType());
                }
        }
        if (freed == null)
            return "No guard posted on " + target.getDisplayName();
        returnToRoster(freed);
        return null;
    }

    /** All guards convert back to boarding parties — ship captured (D7.834). */
    public int releaseAll() {
        int released = 0;
        for (BoardingPartyQuality q : weaponGuards.values()) { returnToRoster(q); released++; }
        for (BoardingPartyQuality q : tractorGuards.values()) { returnToRoster(q); released++; }
        for (BoardingPartyQuality q : singletonGuards.values()) { returnToRoster(q); released++; }
        for (List<BoardingPartyQuality> list : poolGuards.values())
            for (BoardingPartyQuality q : list) { returnToRoster(q); released++; }
        weaponGuards.clear();
        tractorGuards.clear();
        singletonGuards.clear();
        poolGuards.clear();
        poolLastKnown.clear();
        return released;
    }

    // -------------------------------------------------------------------------
    // Raid interception (D7.831)
    // -------------------------------------------------------------------------

    /**
     * Does a guard meet this raid? Exact posts intercept deterministically;
     * pool posts intercept with probability guards/boxes (hidden per-box
     * placement, D7.8374). Returns a handle for D7.832 resolution, or null.
     */
    public Interception intercept(SystemTarget target) {
        switch (target.getType()) {
            case WEAPON:
                return weaponGuards.containsKey(target.getWeapon())
                        ? new Interception(null, target.getWeapon(), 0, "guarded weapon")
                        : null;
            case TRACTOR:
                return tractorGuards.containsKey(target.getIndex())
                        ? new Interception(SystemTarget.Type.TRACTOR, null, target.getIndex(), "guarded beam")
                        : null;
            default:
                if (POOL_TYPES.contains(target.getType())) {
                    List<BoardingPartyQuality> list = poolGuards.get(target.getType());
                    int guards = list == null ? 0 : list.size();
                    int boxes = Math.max(1, poolBoxCount(target.getType()));
                    if (guards <= 0)
                        return null;
                    boolean met = random.nextInt(boxes) < guards;
                    String detail = guards + " guard(s) / " + boxes + " boxes — "
                            + (met ? "guard encountered" : "raided box unguarded");
                    return met ? new Interception(target.getType(), null, 0, detail) : null;
                }
                return singletonGuards.containsKey(target.getType())
                        ? new Interception(target.getType(), null, 0, "guarded system")
                        : null;
        }
    }

    /**
     * D7.832: the box the intercepting guard was defending has been destroyed
     * by the raid — 50% the guard dies with it (permanent loss); a survivor is
     * released to the roster unless its guarded group still has boxes
     * (D7.8375). Sensor/scanner guards are never killed (D7.823).
     *
     * @return log line describing the outcome.
     */
    public String onGuardedBoxDestroyedByRaid(Interception guard) {
        if (guard.type == SystemTarget.Type.SENSORS || guard.type == SystemTarget.Type.SCANNERS)
            return "guard on the " + guard.type + " track is unharmed (D7.823)";

        boolean dies = random.nextInt(2) == 0; // 50% (D7.832)
        BoardingPartyQuality q = removePost(guard);
        if (q == null)
            return "";
        if (dies)
            return "guard killed defending the raided box (D7.832)";

        // Survivor: released unless the guarded group still exists (D7.8375)
        if (guard.type != null && SINGLETON_TYPES.contains(guard.type) && singletonStillExists(guard.type)) {
            singletonGuards.put(guard.type, q); // stays at its post
            return "guard survived — remains posted (D7.8375)";
        }
        returnToRoster(q);
        return "guard survived — released to boarding party pool (D7.832)";
    }

    // -------------------------------------------------------------------------
    // Combat-damage reconcile (D7.832 / D7.8375)
    // -------------------------------------------------------------------------

    /**
     * Sweep after damage resolution: guards whose posts were destroyed by
     * combat damage roll D7.832 casualties. Group posts (engines, impulse)
     * only roll when the entire group is gone (D7.8375); sensor/scanner
     * guards are immune; pool posts roll guards/boxes per lost box.
     *
     * @return log lines (empty when nothing happened).
     */
    public List<String> reconcileAfterDamage() {
        List<String> log = new ArrayList<>();

        for (Map.Entry<Weapon, BoardingPartyQuality> e : new LinkedHashMap<>(weaponGuards).entrySet()) {
            if (!e.getKey().isFunctional()) {
                weaponGuards.remove(e.getKey());
                log.add(casualtyRoll(e.getValue(), e.getKey().getName()));
            }
        }

        for (Map.Entry<Integer, BoardingPartyQuality> e : new TreeMap<>(tractorGuards).entrySet()) {
            TractorBeam beam = findBeam(e.getKey());
            if (beam == null || !beam.isFunctional()) {
                tractorGuards.remove(e.getKey());
                log.add(casualtyRoll(e.getValue(), "Tractor #" + e.getKey()));
            }
        }

        for (SystemTarget.Type type : new ArrayList<>(singletonGuards.keySet())) {
            if (type == SystemTarget.Type.SENSORS || type == SystemTarget.Type.SCANNERS)
                continue; // never killed (D7.823); track never fully vanishes
            if (!singletonStillExists(type)) {
                BoardingPartyQuality q = singletonGuards.remove(type);
                log.add(casualtyRoll(q, type.toString()));
            }
        }

        for (SystemTarget.Type type : new ArrayList<>(poolGuards.keySet())) {
            List<BoardingPartyQuality> list = poolGuards.get(type);
            int current = poolBoxCount(type);
            int last = poolLastKnown.getOrDefault(type, current);
            if (current >= last) {
                poolLastKnown.put(type, current);
                continue;
            }
            for (int lost = last; lost > current && !list.isEmpty(); lost--) {
                if (random.nextInt(lost) < list.size()) { // the lost box was a guarded one
                    BoardingPartyQuality q = list.remove(list.size() - 1);
                    log.add(casualtyRoll(q, type.toString()));
                }
            }
            poolLastKnown.put(type, current);
            if (list.isEmpty())
                poolGuards.remove(type);
        }

        return log;
    }

    /** Roll the D7.832 50%: the guard dies, or is released to the roster. */
    private String casualtyRoll(BoardingPartyQuality quality, String postName) {
        if (random.nextInt(2) == 0)
            return "guard on " + postName + " killed with its post (D7.832)";
        returnToRoster(quality);
        return "guard on " + postName + " survived — released to boarding party pool (D7.832)";
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public boolean isGuarded(Weapon weapon) {
        return weaponGuards.containsKey(weapon);
    }

    public boolean isBeamGuarded(int beamNumber) {
        return tractorGuards.containsKey(beamNumber);
    }

    public boolean isGuarded(SystemTarget.Type type) {
        if (POOL_TYPES.contains(type)) {
            List<BoardingPartyQuality> list = poolGuards.get(type);
            return list != null && !list.isEmpty();
        }
        return singletonGuards.containsKey(type);
    }

    public int poolGuardCount(SystemTarget.Type type) {
        List<BoardingPartyQuality> list = poolGuards.get(type);
        return list == null ? 0 : list.size();
    }

    /** Total boarding parties currently standing guard. */
    public int totalPosted() {
        int total = weaponGuards.size() + tractorGuards.size() + singletonGuards.size();
        for (List<BoardingPartyQuality> list : poolGuards.values())
            total += list.size();
        return total;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private enum Kind { WEAPON, TRACTOR, OTHER }

    private Kind kindOf(SystemTarget target) {
        if (target.getType() == SystemTarget.Type.WEAPON) return Kind.WEAPON;
        if (target.getType() == SystemTarget.Type.TRACTOR) return Kind.TRACTOR;
        return Kind.OTHER;
    }

    private String checkTargetGuardable(SystemTarget target) {
        switch (target.getType()) {
            case WEAPON: {
                Weapon w = target.getWeapon();
                if (w == null || !w.isFunctional())
                    return "Weapon is destroyed or missing";
                if (weaponGuards.containsKey(w))
                    return "Already guarded — one guard per system (D7.833)";
                return null;
            }
            case TRACTOR: {
                TractorBeam beam = findBeam(target.getIndex());
                if (beam == null || !beam.isFunctional())
                    return "Tractor beam #" + target.getIndex() + " is destroyed or missing";
                if (tractorGuards.containsKey(target.getIndex()))
                    return "Already guarded — one guard per system (D7.833)";
                return null;
            }
            case UIM:
                return "UIM cannot be guarded yet"; // UIM system itself is deferred
            default:
                if (POOL_TYPES.contains(target.getType())) {
                    int boxes = poolBoxCount(target.getType());
                    if (boxes <= 0)
                        return target.getType() + " has no boxes remaining";
                    if (poolGuardCount(target.getType()) >= boxes)
                        return "Every " + target.getType() + " box already has a guard (D7.833)";
                    return null;
                }
                if (!SINGLETON_TYPES.contains(target.getType()))
                    return target.getType() + " cannot be guarded";
                if (!singletonStillExists(target.getType()))
                    return target.getType() + " is destroyed or missing";
                if (singletonGuards.containsKey(target.getType()))
                    return "Already guarded — one guard per system (D7.833)";
                return null;
        }
    }

    private boolean takeFromRoster(BoardingPartyQuality quality) {
        TroopCount troops = ship.getCrew().getFriendlyTroops();
        if (quality == BoardingPartyQuality.COMMANDO) {
            if (troops.commandos <= 0) return false;
            troops.commandos--;
        } else {
            if (troops.normal <= 0) return false;
            troops.normal--;
        }
        return true;
    }

    private void returnToRoster(BoardingPartyQuality quality) {
        TroopCount troops = ship.getCrew().getFriendlyTroops();
        if (quality == BoardingPartyQuality.COMMANDO)
            troops.commandos++;
        else
            troops.normal++;
    }

    private BoardingPartyQuality removePost(Interception guard) {
        if (guard.weapon != null)
            return weaponGuards.remove(guard.weapon);
        if (guard.beamNumber > 0)
            return tractorGuards.remove(guard.beamNumber);
        if (POOL_TYPES.contains(guard.type)) {
            List<BoardingPartyQuality> list = poolGuards.get(guard.type);
            return (list == null || list.isEmpty()) ? null : list.remove(list.size() - 1);
        }
        return singletonGuards.remove(guard.type);
    }

    private TractorBeam findBeam(int number) {
        for (TractorBeam b : ship.getTractors().getBeams())
            if (b.getNumber() == number)
                return b;
        return null;
    }

    private int poolBoxCount(SystemTarget.Type type) {
        switch (type) {
            case TRANSPORTERS: return ship.getTransporters().getAvailableTrans();
            case BATTERY:      return ship.getPowerSystems().getAvailableBattery();
            case FHULL:        return ship.getHullBoxes().getAvailableFhull();
            case AHULL:        return ship.getHullBoxes().getAvailableAhull();
            case CHULL:        return ship.getHullBoxes().getAvailableChull();
            default:           return 0;
        }
    }

    private boolean singletonStillExists(SystemTarget.Type type) {
        switch (type) {
            case WARP_L:  return ship.getPowerSystems().getAvailableLWarp() > 0;
            case WARP_R:  return ship.getPowerSystems().getAvailableRWarp() > 0;
            case WARP_C:  return ship.getPowerSystems().getAvailableCWarp() > 0;
            case IMPULSE: return ship.getPowerSystems().getAvailableImpulse() > 0;
            case SENSORS: return ship.getSpecialFunctions().canDamageSensor();
            case SCANNERS: return ship.getSpecialFunctions().canDamageScanner();
            case CLOAKING_DEVICE:
                return ship.getCloakingDevice() != null && ship.getCloakingDevice().isFunctional();
            case DERFACS:
                return ship.getDerfacs() != null && ship.getDerfacs().isFunctional();
            default: return false;
        }
    }
}
