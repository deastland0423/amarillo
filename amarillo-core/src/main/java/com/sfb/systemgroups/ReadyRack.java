package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;

/**
 * The drone ready rack built into a fighter box (J4.822).
 * <p>
 * "Each shuttle box that originally contained a fighter that can carry drones is presumed to
 * have a ready rack to store reloads." It is the drone fighter's answer to the Hydran fusion
 * capacitor: per box, tied to the kind of fighter that box was built for, destroyed with the
 * box, and the only place that box's fighter can be armed from.
 * <p>
 * Two things differ from the capacitor and are easy to get wrong:
 * <ul>
 *   <li>It holds ONE reload — "the same drones that the fighter it is designed to service
 *       carries" (J4.8222) — where a fusion capacitor holds two.</li>
 *   <li>J4.8222 makes it type-specific: a rack built to service an AAS cannot arm some other
 *       fighter that happens to be sitting in the box.</li>
 * </ul>
 * J4.8223 gives the resting state: racks full, fighters unloaded. The fighters are loaded when
 * a strike is called, and the crews refill the racks while the strike is away.
 */
public class ReadyRack {

    /** The fighter class this rack services (J4.8222); a rack cannot arm anything else. */
    private final String servesFighterType;

    /** How many drones a full rack holds: one reload for the fighter it serves. */
    private final int capacity;

    /**
     * The shape of a full rack — one entry per slot, of what that slot was built to hold.
     * <p>
     * Kept because a part-empty rack has to be able to say WHAT it is short of, not just how
     * many: a TAAS rack missing a light slot and a standard one wants a type-VI and a type-I,
     * and a refill that fetched two type-Is would bring one the fighter cannot load.
     */
    private final List<DroneType> design;

    private final List<Drone> drones = new ArrayList<>();

    /**
     * A plasma-D rack (J4.825): {@code capacity} torpedoes, held as a count.
     * <p>
     * Starts FULL, as a drone rack does, and for the same reason - J4.886 has a scenario open
     * with the stores forward and the fighters empty (J4.8223), so everything the fighter is
     * not carrying is in its box.
     */
    private ReadyRack(String servesFighterType, int plasmaCapacity,
            com.sfb.properties.PlasmaType plasmaType) {
        this.servesFighterType = servesFighterType;
        this.design = List.of();
        this.capacity = plasmaCapacity;
        this.plasmaType = plasmaType;
        this.plasmaHeld = plasmaCapacity;
    }

    /**
     * Which plasma this rack stocks, or null if it holds drones.
     * <p>
     * A TYPE rather than a boolean, because FP13.31's K-rail fighters need their box stocked with
     * type-Ks and J4.8222 makes a rack serve one kind of fighter and no other. Counted in
     * TORPEDOES here, not spaces: a rack holds one per rail whatever size they are, and it is the
     * carrier's hold that measures itself in spaces (FP13.33).
     */
    private com.sfb.properties.PlasmaType plasmaType;

    /** Torpedoes in a plasma rack. Meaningless on a drone rack, where it stays zero. */
    private int plasmaHeld;

    /** True if this rack holds plasma torpedoes of any type rather than drones. */
    public boolean carriesPlasma() { return plasmaType != null; }

    /** Which plasma this rack stocks, or null on a drone rack. */
    public com.sfb.properties.PlasmaType plasmaType() { return plasmaType; }

    /** True only for a type-D rack. Prefer {@link #carriesPlasma()} unless the type matters. */
    public boolean isPlasmaD() { return plasmaType == com.sfb.properties.PlasmaType.D; }

    /** Torpedoes this rack is short of its full load. */
    public int plasmaDMissing() {
        return carriesPlasma() ? Math.max(0, capacity - plasmaHeld) : 0;
    }

    /** Take one torpedo out to load onto a fighter, or false if the rack is empty. */
    public boolean takePlasmaD() {
        if (!carriesPlasma() || plasmaHeld <= 0)
            return false;
        plasmaHeld--;
        return true;
    }

    /** Put one back - a refill from stores, or a torpedo taken off a fighter. */
    public boolean putPlasmaD() {
        if (!carriesPlasma() || plasmaHeld >= capacity)
            return false;
        plasmaHeld++;
        return true;
    }

    private ReadyRack(String servesFighterType, List<DroneType> stock) {
        this.servesFighterType = servesFighterType;
        this.capacity = stock.size();
        this.design = List.copyOf(stock);
        for (DroneType type : stock)
            drones.add(new Drone(type));   // J4.8223/J4.886: racks start full
    }

    /**
     * The rack a box would have been built with to SERVE this fighter type, with no such fighter
     * aboard (J4.62, J4.621).
     * <p>
     * A casual carrier is the case {@link #forFighter} cannot express: J4.62 says "some ships have
     * ready racks for fighters, and may even carry one or two, but are not carriers" - most carrier
     * escorts, the Hydran Pegasus and Gendarme, many WYN ships. Their boxes hold administrative
     * shuttles, or nothing, and the rack is still there. Deriving a rack from its occupant could
     * never give them one.
     * <p>
     * J4.621 decides WHICH type: "the fighters on the carrier will determine what type of ready
     * racks are on the escort, and this will in turn determine the numbers of drones held in the
     * racks." So the type is resolved from the line and the scenario year exactly as a carrier's
     * complement is, and the rack is then built as if that fighter were the occupant - same
     * contents, same capacity, same J4.8222 restriction on what it will service.
     *
     * @param catalogueType the fighter type this rack is built for, e.g. "aas" or "gsf"
     * @return the rack, or null if that type carries nothing a rack would hold
     */
    public static ReadyRack forType(String catalogueType) {
        if (catalogueType == null || catalogueType.isBlank())
            return null;
        com.sfb.objects.shuttles.Fighter pattern;
        try {
            pattern = com.sfb.objects.shuttles.CataloguedFighter.of(catalogueType);
        } catch (RuntimeException e) {
            return null;              // not a fighter the catalogue knows
        }
        // Built from a throwaway instance of the fighter rather than from a parallel reading of
        // the catalogue, so a rack and the craft it serves cannot drift apart - which is the
        // whole of J4.8222 ("each ready rack holds the same drones that the fighter it is
        // designed to service carries").
        return forFighter(pattern);
    }

    /**
     * The rack a box would have been built with for this occupant, or null if the fighter
     * carries no drones (J4.822 only presumes a rack where one was needed).
     */
    public static ReadyRack forFighter(Shuttle occupant) {
        if (!(occupant instanceof com.sfb.objects.shuttles.Fighter fighter))
            return null;   // only a fighter box gets a rack (J4.822)

        // One drone per rail, of what THAT rail is designed to carry. Per rail rather than
        // per fighter because a Kzinti TAAS has two standard rails and two light ones, and a
        // Type-I will not go in a light rail at all — a rack stocked from one answer would
        // hold two drones its own fighter cannot take.
        //
        // A rail carrying an EW pod is skipped. J4.962: "An EWP replaces one drone carried by
        // the fighter" — so that rail has no drone to reload, and a rack that stocked one
        // anyway would show a HAAS-E holding two Type-Is it can never load and charge its
        // carrier two spaces of stores for them. A fighter whose every rail is podded needs
        // no rack at all, which falls out of the empty check below.
        List<DroneType> stock = new ArrayList<>();
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.getDesignDrone() != null
                    && !rail.hasEwPod())
                stock.add(rail.getDesignDrone());

        // J4.825 puts type-D plasma torpedoes under the drone storage rules, so a plasma-D
        // fighter's box gets a ready rack exactly as a drone fighter's does - one torpedo per
        // rail. It is a COUNT rather than a list because FP9.18 says they have no variants:
        // "Pl-Ds, like other plasmas, do not have guidance options, different speeds, or
        // warhead modules as drones do." One type-D is any other, so there is nothing to
        // distinguish and no DroneType to stand in for them.
        //
        // Never both kinds in one rack: J4.825 forbids a fighter carrying drones and type-Ds
        // at once, so the rack is one or the other, as its fighter is.
        int plasmaRails = 0;
        com.sfb.properties.PlasmaType plasmaKind = null;
        for (Weapon w : fighter.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRail rail && rail.carriesPlasma()) {
                plasmaRails++;
                // One kind per fighter, as J4.825 requires of drones and plasma: the first rail
                // decides, and a mixed-plasma fighter does not exist in the data.
                if (plasmaKind == null)
                    plasmaKind = rail.plasmaType();
            }
        if (plasmaRails > 0)
            return new ReadyRack(fighter.getCatalogType(), plasmaRails, plasmaKind);

        if (stock.isEmpty())
            return null;
        // Keyed on the CATALOGUE type, not the Java class name. J4.8222 makes a ready rack
        // serve one kind of fighter and no other, and the class name only ever stood in for
        // "kind" because each fighter happened to have a class of its own. Now that a fighter is
        // a catalogue row they share one class, and a rack keyed on the class name would serve
        // EVERY fighter — the type-specific rule silently becoming type-agnostic.
        return new ReadyRack(fighter.getCatalogType(), stock);
    }

    public String getServesFighterType() { return servesFighterType; }

    public int capacity() { return capacity; }

    public int count() { return carriesPlasma() ? plasmaHeld : drones.size(); }

    public boolean isFull() { return count() >= capacity; }

    public boolean isEmpty() { return count() == 0; }

    /** J4.8222: this rack services one kind of fighter and no other. */
    public boolean serves(Shuttle fighter) {
        return fighter != null && servesFighterType != null
                && servesFighterType.equals(fighter.getCatalogType());
    }

    /** Take one drone out to load onto the fighter, or null if the rack is empty. */
    public Drone take() {
        return drones.isEmpty() ? null : drones.remove(drones.size() - 1);
    }

    /**
     * Take a drone this rail can actually carry, largest first, or null if it holds none.
     * <p>
     * Needed even when the rack was stocked correctly: a standard rail will happily take a
     * Type-VI, and if it does, the Type-I meant for it is left with only a light rail to go
     * in. Serving the biggest drone that fits keeps the awkward ones moving first.
     */
    public Drone takeFor(DroneRail rail) {
        Drone best = null;
        for (Drone d : drones)
            if (rail.accepts(d) && (best == null || d.getRackSize() > best.getRackSize()))
                best = d;
        if (best != null)
            drones.remove(best);
        return best;
    }

    /** One entry per slot, of what a full rack of this shape holds (J4.8222). */
    public List<DroneType> design() {
        return design;
    }

    /**
     * The slots this rack is short of, largest first — what a refill should go and fetch.
     * <p>
     * A multiset difference rather than a count: a rack holding a spare type-VI where a
     * type-I belongs is short of the type-I, however full it looks. Largest first because
     * those are the ones a part-stocked hold runs out of.
     */
    public List<DroneType> slotsMissing() {
        List<DroneType> missing = new ArrayList<>(design);
        for (Drone held : drones)
            missing.remove(held.getDroneType());   // removes one matching slot, not all
        missing.sort((a, b) -> Double.compare(b.rack, a.rack));
        return missing;
    }

    /** Spaces the drones in here take up — what the fighter's load is measured in (FD7.211). */
    public double spaces() {
        // FP9.21/J4.825: a type-D is "the same size as a one-space drone", so a torpedo is a
        // space and the arithmetic is the count. A type-K is half of one (FP13.32), so a K-rack
        // of the same torpedo count occupies half the spaces.
        if (carriesPlasma())
            return plasmaHeld * DroneStore.spacesPerTorpedo(plasmaType);
        double total = 0;
        for (Drone d : drones)
            total += d.getRackSize();
        return total;
    }

    /** Put a drone back — unloading a fighter, or a deck crew refilling from stores. */
    public boolean put(Drone drone) {
        if (drone == null || isFull())
            return false;
        drones.add(drone);
        return true;
    }

    /** What is in it, for the owner's readout. Never handed to an enemy: G4.233. */
    public List<Drone> contents() {
        return java.util.Collections.unmodifiableList(drones);
    }

    /** J4.831's rule for capacitors applies here too: the box dies, its stores die with it. */
    public void destroy() {
        drones.clear();
    }
}
