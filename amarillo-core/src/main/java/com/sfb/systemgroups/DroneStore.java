package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;

/**
 * The carrier's supply of spare drones for its fighters (J4.7).
 * <p>
 * "Carriers that operate fighters carrying drones are presumed to have a supply of drones on
 * board. These drones are used to rearm the ship's fighters. The Kzinti CV has 150 'spaces'
 * of spare drones for its fighters." Other carriers are listed in Annex #7G; the supply is
 * inside the carrier's BPV and is not bought separately.
 *
 * <h2>Spaces, not drones</h2>
 * The rule counts SPACES, so the pool holds twice as many type-VIs as type-Is — the same
 * measure {@link Drone#getRackSize()} already returns, and the same one J4.821 prices deck
 * crew work in.
 *
 * <h2>One pool, not a tier behind the racks</h2>
 * J4.72 is the fact that shapes this class: drones "held in ready racks (J4.89) or loaded on
 * the fighters count as part of the ship's storage". So the 150 is a TOTAL, not a reserve
 * stacked behind the racks — a full rack is storage that has already been moved forward. What
 * this object holds is the part of that total still in the hold, and moving a drone from here
 * to a {@link ReadyRack} does not change what the ship owns, only where it is.
 * <p>
 * That is why nothing here creates or destroys drones once stocked: every transfer is a hand
 * off, and a supply that can be conjured is a supply that is infinite.
 *
 * <h2>Detonators out</h2>
 * J4.71: stored drones are kept inert, do not explode, and add nothing to the ship's final
 * explosion. So this pool is deliberately invisible to chain reactions (D12.0) — there is no
 * hook here for {@code DamageResolver} to find, and that is the rule, not an omission.
 */
public class DroneStore implements PlasmaDSupply {

    /** Spaces this carrier's supply runs to (J4.7), racks and loaded fighters included. */
    private final double capacitySpaces;

    /** The part of that supply still in the hold. */
    private final List<Drone> reserve = new ArrayList<>();

    /**
     * FP10.31: the plasma racks' hold, drawn on only once this supply is out of type-Ds. Null on
     * a carrier whose ship mounts no plasma racks, which is nearly all of them.
     */
    private PlasmaDSupply rackSupply;

    public DroneStore(double capacitySpaces) {
        this.capacitySpaces = Math.max(0, capacitySpaces);
    }

    /**
     * Stock the hold up to {@code spaces}, dealing round the LOADING the ship is carrying.
     *
     * <h2>The pattern is drones, not types, and that is the whole of FD2.45's entitlement</h2>
     * <blockquote>FD2.45: "The drones in storage are proportional to those on the
     * racks/fighters. They can be of a less expensive, but cannot be of a more expensive, type
     * than the drones in the loading paid for. For example, if a Klingon D7B with two type-A
     * drone racks had six type-I and two type-I-ECM drones in its racks, then the reloads
     * (assuming a single set) could include up to two type-I-ECM and the rest
     * type-I."</blockquote>
     *
     * <p>Dealing round the loading gives that by construction: an ECM drone appears in the
     * hold in the same proportion it appears in the racks, so the entitlement is taken up
     * without ever being computed, and never exceeded. This used to take a list of {@link
     * DroneType} and build {@code new Drone(type)}, which threw the composition away — so a
     * player who bought two ECM drones got none in storage and could never reload one, though
     * FD2.45 gives them free.
     *
     * <p>Round-robin rather than pattern-at-a-time, so a hold that runs out part way through
     * is still proportionate. A full lap with nothing fitting means the gap left is smaller
     * than the smallest drone in the pattern, and the hold is as full as it gets.
     *
     * <p>Each entry is COPIED. The pattern is the drones actually aboard, and a hold sharing
     * objects with a rack would reload a fighter with a drone it is already carrying.
     *
     * @return spaces actually stocked
     */
    public double stock(List<Drone> pattern, double spaces) {
        if (pattern == null || pattern.isEmpty() || spaces <= 0)
            return 0;
        double before = spacesHeld();
        double ceiling = Math.min(before + spaces, capacitySpaces);
        int misses = 0;
        for (int i = 0; misses < pattern.size(); i++) {
            Drone one = pattern.get(i % pattern.size());
            if (one != null && spacesHeld() + one.getRackSize() <= ceiling) {
                reserve.add(new Drone(one.getDroneType(), one.getSpec()));
                misses = 0;
            } else {
                misses++;
            }
        }
        return spacesHeld() - before;
    }

    /** Spaces this carrier's supply runs to in total (J4.7). */
    public double capacitySpaces() { return capacitySpaces; }

    // ------------------------------------------------- type-D plasma torpedoes (J4.825)

    /**
     * Type-D plasma torpedoes in the hold, as a count.
     * <p>
     * The SAME pool as the drones, not a second store. J4.7 gives a carrier one holding
     * measured in spaces, and J4.825 makes a type-D "the same size as a one-space drone" and
     * puts it under the drone storage rules - so a torpedo occupies a space of this hold like
     * anything else in it. A count rather than objects because FP9.18 denies them variants:
     * one type-D is any other.
     */
    /**
     * Plasma in the hold, measured in SPACES rather than torpedoes, because a type-D and a type-K
     * are not the same size. A type-D is one space (FP9.21); a type-K is half of one (FP13.32:
     * "a plasma-K capsule is half of the size of a plasma-D").
     * <p>
     * Spaces rather than two counters is the owner's model (2026-10-05) and it is the one FP13.33
     * describes: "for each plasma-D replaced by a plasma-K on a fighter's ready rack/launch rail,
     * TWO plasma-Ks can replace one plasma-D in a carrier's reload storage. Unlike drones, a
     * player controlling a plasma carrier can choose the mix... irrespective of the number of
     * plasma-Ds and/or plasma-Ks that are loaded." A free mix of two sizes in one pool is exactly
     * a space total, and it is the same arithmetic the drone side already does with
     * {@code Drone.getRackSize()} — a type-K is to a type-D what a type-VI is to a type-I.
     */
    private double plasmaSpaces;

    /** Spaces one torpedo of this type occupies in the hold. */
    public static double spacesPerTorpedo(com.sfb.properties.PlasmaType type) {
        return type == com.sfb.properties.PlasmaType.K ? 0.5 : 1.0;
    }

    /** How many torpedoes of this type the hold could still hand out. */
    public int plasmaCount(com.sfb.properties.PlasmaType type) {
        return (int) Math.floor(plasmaSpaces / spacesPerTorpedo(type));
    }

    /** Type-Ds the hold could hand out. Unchanged meaning for every existing caller. */
    public int plasmaDCount() { return plasmaCount(com.sfb.properties.PlasmaType.D); }

    /**
     * Stock type-D torpedoes into the spaces left, up to {@code spaces} of them.
     * <p>
     * Stocked as type-Ds because that is what a carrier's supply IS: FP13.33 has the player
     * declare some of it as type-Ks before the scenario, which is a COI choice and not a
     * stocking one.
     *
     * @return how many were stocked
     */
    public int stockPlasmaDs(double spaces) {
        int room = (int) Math.floor(Math.min(spaces, capacitySpaces() - spacesHeld()));
        if (room <= 0)
            return 0;
        plasmaSpaces += room;
        return room;
    }

    /**
     * Take one torpedo of this type out, or false if there is none to be had.
     *
     * <p>FP10.31: a type-D falls back on the plasma racks' hold once this supply is dry —
     * "Plasma-Ds stored for use by fighters are interchangeable with those stored for use by
     * plasma racks". Only the type-D does: a type-K or type-F has no rack hold to borrow from,
     * and the rack hold holds nothing else (FP10.11).
     */
    public boolean takePlasma(com.sfb.properties.PlasmaType type) {
        double cost = spacesPerTorpedo(type);
        if (plasmaSpaces >= cost) {
            plasmaSpaces -= cost;
            return true;
        }
        if (type == com.sfb.properties.PlasmaType.D && rackSupply != null)
            return rackSupply.takePlasmaDHere(1) == 1;
        return false;
    }

    /** Point this supply at the plasma racks' hold to fall back on (FP10.31). */
    public void attachRackSupply(PlasmaDSupply supply) {
        this.rackSupply = supply != this ? supply : null;
    }

    /**
     * Torpedoes of this type a fighter can actually be given: this hold, plus the racks' hold
     * where the rule allows reaching into it.
     */
    public int plasmaAvailable(com.sfb.properties.PlasmaType type) {
        int here = plasmaCount(type);
        if (type == com.sfb.properties.PlasmaType.D && rackSupply != null)
            here += rackSupply.plasmaDHeldHere();
        return here;
    }

    @Override
    public int plasmaDHeldHere() {
        return plasmaCount(com.sfb.properties.PlasmaType.D);
    }

    @Override
    public int takePlasmaDHere(int wanted) {
        int taken = 0;
        double cost = spacesPerTorpedo(com.sfb.properties.PlasmaType.D);
        while (taken < wanted && plasmaSpaces >= cost) {
            plasmaSpaces -= cost;
            taken++;
        }
        return taken;
    }

    /** Put one torpedo of this type back - it never left the ship. */
    public void putPlasma(com.sfb.properties.PlasmaType type) {
        plasmaSpaces += spacesPerTorpedo(type);
    }

    /** Take one type-D out, or false if there are none. */
    public boolean takePlasmaD() { return takePlasma(com.sfb.properties.PlasmaType.D); }

    /** Put one type-D back - it never left the ship. */
    public void putPlasmaD() { putPlasma(com.sfb.properties.PlasmaType.D); }

    /** Spaces still in the hold — what a rack can still be refilled from. */
    /**
     * Spaces in use, drones and torpedoes together - they share the hold (J4.7, J4.825).
     */
    public double spacesHeld() {
        double total = plasmaSpaces;    // already in spaces: D = 1, K = 0.5 (FP9.21, FP13.32)
        for (Drone d : reserve)
            total += d.getRackSize();
        return total;
    }

    public int count() { return reserve.size(); }

    /**
     * Nothing in the hold at all - drones AND torpedoes, since they share it (J4.825).
     * <p>
     * Counting only the drones made this answer "empty" on a ship whose entire stores were
     * type-D torpedoes, which is how the Romulan KRV's deck crews found no refilling to do
     * beside sixty spaces of them.
     */
    public boolean isEmpty() { return reserve.isEmpty() && plasmaSpaces <= 0; }

    /**
     * Take the largest drone the hold has that this rail's slot can carry, or null if it
     * holds none that fit.
     * <p>
     * Largest first for the reason {@link ReadyRack#takeFor} does it: a standard rail will
     * accept a type-VI, and if it takes one, the type-I it was stocked for has only a light
     * rail left to go in, where it does not fit at all.
     */
    public Drone takeFor(DroneRail rail) {
        Drone best = null;
        for (Drone d : reserve)
            if (rail.accepts(d) && (best == null || d.getRackSize() > best.getRackSize()))
                best = d;
        if (best != null)
            reserve.remove(best);
        return best;
    }

    /**
     * Take the drone a slot is short of: the one it asks for, or the best lawful stand-in.
     *
     * <blockquote>Owner's ruling, 2026-11-05: "if you have a Type-IM ECM drone in the ready
     * rack, there are replacement Type-IM ECM drones in reloads."</blockquote>
     *
     * <p>So an exact match first — same catalogue type, same composition. That is the whole
     * rule, and it is only askable because a design slot is a DRONE: asking by TYPE let the
     * hold answer with any drone of that type, and a rack short of one ECM drone and three
     * plain ones was handed four ECM drones. Over-issuing is the one thing FD2.45 forbids
     * outright, storage being unable to be "of a more expensive type than the drones in the
     * loading paid for".
     *
     * <p>Failing that, a drone of the same TYPE, the plainest first. FD2.452 allows the
     * substitute — "the same cost (or lower)... but might be of a different type" — and taking
     * the plainest keeps the scarce ones in the hold for the slots that actually want them.
     */
    public Drone take(Drone want) {
        if (want == null)
            return take();
        for (Drone d : reserve)
            if (com.sfb.systemgroups.ReadyRack.sameLoad(d, want)) {
                reserve.remove(d);
                return d;
            }
        Drone plainest = null;
        for (Drone d : reserve)
            if (d.getDroneType() == want.getDroneType()
                    && (plainest == null || plainerThan(d, plainest)))
                plainest = d;
        if (plainest != null)
            reserve.remove(plainest);
        return plainest;
    }

    /**
     * Which of two same-type drones to spend on a slot that asked for neither.
     *
     * <p>The LEAST SCARCE, so an ECM drone stays in the hold for the slot that wants one.
     * By availability and not by price: FD10.5 charges 0.50 for an ECM module, exactly what
     * the explosive warhead it replaces costs, so the two drones are worth the same and a
     * cost comparison is a coin flip. FD10.62 makes the module Restricted where a warhead is
     * General, and scarcity is what FD2.45's proportional rule counts in — "the LAST special
     * drone in each PERCENTAGE CATEGORY". Cost breaks a tie between two equally scarce ones.
     */
    private static boolean plainerThan(Drone candidate, Drone best) {
        com.sfb.objects.Availability mine = scarcity(candidate);
        com.sfb.objects.Availability theirs = scarcity(best);
        if (mine != theirs)
            return theirs.atLeastAsRestrictedAs(mine);
        return worth(candidate) < worth(best);
    }

    /**
     * The most restricted thing this drone carries (FD10.6), ignoring its engine.
     *
     * <p>Modules only: the propulsion's tier moves with the YEAR and this method has none, and
     * a hold stocked from the loading holds one speed throughout anyway (S2.11) — so the engine
     * cannot separate two drones here, while a payload module can.
     */
    private static com.sfb.objects.Availability scarcity(Drone d) {
        com.sfb.objects.Availability worst = com.sfb.objects.Availability.GENERAL;
        if (d == null || d.getSpec() == null)
            return worst;
        for (com.sfb.objects.PayloadModule m : d.getSpec().allModules())
            if (m.availability.atLeastAsRestrictedAs(worst))
                worst = m.availability;
        return worst;
    }

    /** Take one drone of this type, whichever comes first, or null if the hold has none. */
    public Drone take(DroneType type) {
        for (Drone d : reserve)
            if (d.getDroneType() == type) {
                reserve.remove(d);
                return d;
            }
        return null;
    }

    /** What a drone cost to put aboard (FD10.5), or zero for one with no composition. */
    private static double worth(Drone d) {
        return d == null || d.getSpec() == null ? 0 : d.getSpec().pointCost();
    }

    /** Take any drone at all, largest first, or null when the hold is empty. */
    public Drone take() {
        Drone best = null;
        for (Drone d : reserve)
            if (best == null || d.getRackSize() > best.getRackSize())
                best = d;
        if (best != null)
            reserve.remove(best);
        return best;
    }

    /**
     * Put a drone back in the hold — a rack being struck down, or a loadout being changed.
     * <p>
     * Bounded by the capacity even though conservation should make that unreachable: the
     * bound is what makes a leak show up as a refusal here rather than as a hold that quietly
     * grew past what the ship owns.
     */
    public boolean put(Drone drone) {
        if (drone == null || spacesHeld() + drone.getRackSize() > capacitySpaces)
            return false;
        reserve.add(drone);
        return true;
    }

    /** What is in the hold, for the owner's readout. Never handed to an enemy: G4.233. */
    public List<Drone> contents() {
        return Collections.unmodifiableList(reserve);
    }

    /**
     * Swap each drone in the hold for whatever {@code swap} returns for it — the scenario
     * year's speed refit (FD2.45, S2.11).
     * <p>
     * The hold is refitted and NOT charged for, which is the whole point of the rule: FD2.45
     * says the improved-drone cost "is paid only for a single loading of each launch rack
     * and/or fighter; the reloads (and drones stored on a carrier for use by its fighters) are
     * presumed to be of the same type without additional cost." Same type, same speed, no
     * second bill — so this moves with the racks and the bill does not.
     * <p>
     * A function rather than a mutable {@link #contents()} for the reason stated there: every
     * drone in here was handed over by something, and a supply that a caller can lengthen is a
     * supply that is infinite. A refit swaps what a space holds; it never changes how many
     * spaces are held.
     */
    public void refit(java.util.function.UnaryOperator<Drone> swap) {
        if (swap == null)
            return;
        for (int i = 0; i < reserve.size(); i++) {
            Drone now = swap.apply(reserve.get(i));
            if (now != null)
                reserve.set(i, now);
        }
    }

    // -------------------------------------------------------------------------
    // Saved game
    // -------------------------------------------------------------------------

    /**
     * The magazine: what is still in the hold, and how much of it has gone to plasma.
     *
     * {@code capacitySpaces} is final and comes from the hull; both of these are the part the
     * battle moves. Not a Systems implementation — a helper spliced into its owner's state.
     *
     * <h2>Why the reserve has to be written</h2>
     * It used to be left out, on the reasoning that {@code stockDroneStore} rebuilds the hold
     * from the hull at build time. It does — FULL. So a reloaded carrier got its whole spent
     * magazine back, which is free ammunition and the same fault {@code DroneRackSaveTest}
     * closed for ship racks (FD2.45's reloads are the ammunition a carrier spends).
     *
     * <p>It is also the only place a special drone the fleet PAID for is recorded: FD2.45 stocks
     * the hold proportionally to the loading, so one ECM reload among a hundred plain ones is a
     * fact about a purchase and not about the hull, and nothing rebuilds it.
     *
     * <p>Each drone goes through {@link Drone#exportState()}, the same path a drone in a rack or
     * in flight takes, which is what carries its composition (FD10.2).
     */
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        java.util.List<Object> held = new java.util.ArrayList<>();
        for (Drone drone : reserve)
            if (drone != null)
                held.add(drone.exportState());
        state.put("reserve", held);
        state.put("plasmaSpaces", plasmaSpaces);
        return state;
    }

    public void applyState(java.util.Map<String, Object> state) {
        if (state == null)
            return;
        // Absent in a save written before the key existed, and that must leave the hull's full
        // hold alone rather than empty it: a battle restored with no drones at all is a worse
        // reading of an old file than a battle restored with too many.
        if (state.get("reserve") instanceof java.util.List<?> saved) {
            reserve.clear();
            for (Object item : saved) {
                if (!(item instanceof java.util.Map<?, ?> raw))
                    continue;
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> droneState = (java.util.Map<String, Object>) raw;
                Drone drone = Drone.fromState(droneState, null, null, null);
                if (drone != null)
                    reserve.add(drone);
            }
        }
        plasmaSpaces = StateIo.real(state, "plasmaSpaces", plasmaSpaces);
    }
}
