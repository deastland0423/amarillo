package com.sfb.systemgroups;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FD2.442's drone rack reload storage: <b>one stockpile for the whole ship</b>, which any rack may
 * draw from, and which is where those drones now physically live.
 *
 * <h2>Why this exists, in the rules' own words</h2>
 * The rulebook says it three times, and the third leaves no room:
 * <ul>
 *   <li><b>FD2.43 STOCKPILE</b> — "All ships are presumed to carry one complete set of reloads
 *       (i.e., drones equal to the number of spaces held by <b>all</b> of their racks)." That is the
 *       capacity rule, and it is a sum across the ship.
 *   <li><b>FD2.31</b> — "<b>The ship</b> has one set of reloads (enough to reload <b>all of its
 *       racks</b> one time) on board."
 *   <li><b>FD2.422</b> — "A unit's total stockpile (FD2.43) is <b>not directly associated with any
 *       particular rack and can be loaded onto any rack on the ship</b> (or a scatterpack, or on
 *       fighter ready racks assuming the unit also had fighters)."
 * </ul>
 *
 * <h2>What was wrong, and what has now moved</h2>
 * Reloads were held per rack and searched per rack, so a Klingon D7's rack 1 could not draw a drone
 * that happened to sit in rack 2's set. The first slice put this API over the top of that storage so
 * every caller asked the ship rather than a rack. This slice moves the storage itself: the drones
 * live in one list here, and {@link DroneRack}'s reload sets no longer hold any.
 *
 * <p>That second step is not tidying. While two places could hold the same drone, the DTO reported a
 * pool PER RACK, so a two-rack ship offered the same stockpile twice and a player could spend it
 * twice over — the server's {@code take()} being authoritative, the second rack would silently come
 * up short. One list cannot be double-spent.
 *
 * <h2>The racks still say how big it is</h2>
 * Capacity is a rule about racks (FD2.43: "spaces held by all of their racks"), so it is still read
 * off them, and a rack's reload sets survive the move as empty lists. That is deliberate: a set is
 * the rack's declaration that it HAS a set, which is what the capacity sum counts, while the drones
 * that were in it belong to the ship. Destroy a rack and the ship's capacity falls accordingly,
 * which is why the racks are re-read on every call rather than captured once.
 *
 * <h2>Authoring still goes through the racks</h2>
 * {@link DroneRack#setAmmo} builds each rack's reload sets as a mirror of what is loaded (FD2.45),
 * the Y175 type-G refit adds a third set the same way (FD3.72), and the COI edits them. All of that
 * is the rack saying what reloads it came with, and it keeps working: {@link #absorb()} draws
 * anything newly sitting in a rack's sets into the ship's list, so a drone authored after the first
 * draw is not stranded. It is called at the head of every method here, which makes the move
 * invisible to the authoring path and means no caller has to know the order it happened in.
 *
 * <h2>Still to come</h2>
 * FD2.4421's conveyor (a cargo drone moves up as one leaves the stockpile — {@code
 * CargoDroneStore.draw()} exists and nothing calls it), S3.2's purchases sitting ABOVE capacity
 * ("Extra drones purchased under (S3.2) can be added to this type of storage in excess of its
 * capacity (but do not increase its capacity)"), and FD2.423's destruction with the last Excess
 * Damage box.
 *
 * <h2>Type-D and type-H contribute nothing</h2>
 * FD3.43: "While type-D (and type-H) drone racks do not have formal reloads..." — their magazines
 * ARE the reload capacity, already loaded, and FD2.4424 sends their refills straight from the cargo
 * boxes instead. So they are counted out of the capacity sum rather than counted in with zero, which
 * would read as a rack whose stockpile had been spent.
 */
public final class ReloadStockpile {

    /**
     * The ship's weapon group, re-read for its racks on every call rather than captured: a rack
     * destroyed mid-battle lowers the ship's reload capacity, and a stale list would not notice.
     */
    private final Weapons weapons;

    /** Where the drones actually are. One list, for the whole ship. */
    private final List<Drone> drones = new ArrayList<>();

    /**
     * FD2.445's cargo boxes, which FD2.4421 feeds this pile from. Null on a ship with none, which is
     * most of them — a conveyor with nothing behind it simply never moves anything.
     */
    private CargoDroneStore cargo;

    /** FD2.423: gone with the last Excess Damage box, and it does not come back. */
    private boolean destroyed = false;

    private ReloadStockpile(Weapons weapons) {
        this.weapons = weapons;
    }

    /**
     * Point the FD2.4421 conveyor at the ship's cargo boxes.
     * <p>
     * Set by {@link com.sfb.objects.Ship} rather than passed to the factory, because the cargo store
     * is built from the ship's own spec and the stockpile is reached through the ship anyway. A
     * stockpile with no cargo behind it is the normal case.
     */
    public void attachCargo(CargoDroneStore cargo) {
        this.cargo = cargo;
    }

    /**
     * A new, empty stockpile for a ship's weapon group.
     * <p>
     * {@link com.sfb.objects.Ship#reloadStockpile()} holds exactly one of these and hands the same
     * one back every time, because it is now storage rather than a view: building a second would
     * split the ship's drones between two piles. The factory stays package-visible to callers only
     * through Ship for that reason.
     */
    public static ReloadStockpile of(Weapons weapons) {
        return new ReloadStockpile(weapons);
    }

    /** The ship's racks, as they are right now. */
    private List<DroneRack> racks() {
        List<DroneRack> racks = new ArrayList<>();
        if (weapons != null)
            for (Weapon w : weapons.fetchAllWeapons())
                if (w instanceof DroneRack rack)
                    racks.add(rack);
        return racks;
    }

    /**
     * Draw into the ship's list anything that has appeared in a rack's reload sets.
     *
     * <p>The rack is where reloads are AUTHORED — {@link DroneRack#setAmmo} mirrors the load into
     * them (FD2.45), {@link DroneRack#addReloadSets} adds the Y175 set (FD3.72), the COI edits
     * them, and the sample builders write them directly. Rather than make each of those know about
     * the ship, this takes what they wrote whenever the stockpile is next touched.
     *
     * <p>The empty set lists are left in place: they are the rack's statement that it HAS that many
     * sets, which is what {@link #capacitySpaces()} counts.
     */
    private void absorb() {
        if (destroyed)
            return;
        for (DroneRack rack : racks()) {
            for (List<Drone> set : rack.getReloads()) {
                if (set.isEmpty())
                    continue;
                drones.addAll(set);
                set.clear();
            }
        }
    }

    /**
     * FD2.43's capacity: the spaces held by all of the ship's racks, times the reload sets they
     * carry — so a ship with two four-space racks at one set each stocks eight spaces.
     * <p>
     * Racks with no formal reloads (type-D, type-H) add nothing, per FD3.43.
     *
     * <h3>DAMAGED racks still count</h3>
     * FD2.442 sets this by "the capacity of the ship's <b>original</b> drone racks", and FD2.423
     * spells out why that word is there: "If all drone racks are destroyed and then one or more are
     * repaired, the repaired racks can load the remaining reload drones." The reloads outlive the
     * racks; they are "stored in various locations around the ship", not behind the launcher.
     * <p>
     * This walks every rack the ship has, damaged or not, which is the original set because
     * {@link com.sfb.weapons.Weapon#damage()} clears {@code functional} and never removes the
     * weapon. That is quiet agreement rather than an implementation of the rule, so
     * {@code ReloadStockpileStorageTest} damages every rack and then repairs one, to hold it.
     */
    public double capacitySpaces() {
        double total = 0;
        for (DroneRack rack : racks())
            total += rack.getSpaces() * (double) rack.getNumberOfReloads();
        return total;
    }

    /** Every drone in the stockpile. */
    public List<Drone> held() {
        absorb();
        return Collections.unmodifiableList(new ArrayList<>(drones));
    }

    /** How many of each type are in the stockpile, for a picker or a readout. */
    public Map<DroneType, Integer> heldByType() {
        Map<DroneType, Integer> counts = new LinkedHashMap<>();
        for (Drone d : held())
            if (d.getDroneType() != null)
                counts.merge(d.getDroneType(), 1, Integer::sum);
        return counts;
    }

    /** Spaces currently held, which is not the capacity once any have been drawn. */
    public double spacesHeld() {
        double total = 0;
        for (Drone d : held())
            total += d.getRackSize();
        return total;
    }

    /**
     * Take up to {@code count} drones of one type out of the stockpile.
     *
     * <p>Returns the drones actually found, which may be fewer than asked — the caller is choosing
     * from a stockpile it can see, so a short answer means someone else drew first rather than that
     * the request was wrong.
     *
     * <p>The drones are REMOVED. FD2.422 is what makes this legitimate for any rack on the ship: the
     * stockpile is not associated with a particular one.
     */
    public List<Drone> take(DroneType type, int count) {
        absorb();
        List<Drone> taken = new ArrayList<>();
        if (type == null || count <= 0)
            return taken;
        for (Iterator<Drone> it = drones.iterator(); it.hasNext() && taken.size() < count; ) {
            Drone d = it.next();
            if (type.equals(d.getDroneType())) {
                it.remove();
                taken.add(d);
            }
        }
        return taken;
    }

    /**
     * Put a drone back.
     * <p>
     * Used when a loading is abandoned rather than as a way to add stock — a drone bought under S3.2
     * goes in ABOVE capacity and is a different operation (FD2.442), not yet built. A put-back
     * always has room, because something was taken from here a moment ago; refusing over capacity
     * is what keeps this from quietly becoming that unbuilt operation.
     */
    public boolean put(Drone drone) {
        absorb();
        if (drone == null || destroyed)
            return false;   // FD2.423: there is nowhere to put it any more
        if (spacesHeld() + drone.getRackSize() > capacitySpaces() + 1e-9)
            return false;
        drones.add(drone);
        return true;
    }

    /**
     * Take drones <b>for a drone rack</b>, which runs FD2.4421's conveyor behind them.
     *
     * <p><b>FD2.4421</b>: "If a one-space drone from the reload storage is loaded onto a drone rack,
     * a one-space drone from cargo storage is automatically moved into the opening created in reload
     * storage." <b>FD2.4423</b>: "This applies to every rack on the ship."
     *
     * <h3>Why this is a separate method from {@link #take}</h3>
     * The rule says <b>onto a drone rack</b>, and the stockpile has two other customers that are not
     * racks: FD7.22's scatter-pack loading and the Commander's Option draw that picks the initial
     * loadout. Neither creates the opening FD2.4421 describes — a pack is being filled, not a rack
     * reloaded, and the COI is choosing what the ship sails with. Routing all three through one
     * method would quietly top a ship's reloads up every time it loaded a pack.
     *
     * <p>Whether a scatter pack ought to pull cargo up behind it is a fair question the rule does not
     * answer; it is stated for racks, so that is where it is implemented.
     *
     * @return the drones taken, exactly as {@link #take} would give them
     */
    public List<Drone> takeForRack(DroneType type, int count) {
        List<Drone> taken = take(type, count);
        for (Drone d : taken)
            refillFromCargo(d.getRackSize(), d.getDroneType());
        return taken;
    }

    /**
     * Move {@code spaces} of cargo drones up into the opening just created (FD2.4421).
     *
     * <h3>It fills to capacity and no further, which is the S3.2 rule falling out</h3>
     * FD2.442 lets a stockpile sit ABOVE its capacity: "Extra drones purchased under (S3.2) can be
     * added to this type of storage in excess of its capacity (but do not increase its capacity)."
     * The owner's worked example (2026-10-06): a ship with 8 spaces of capacity that has crammed in
     * 3 purchased spaces holds 11, and <i>nothing</i> comes up from cargo until at least 4 spaces
     * have gone out to the racks and the pile is under 8 again.
     * <p>
     * So the room available is {@code capacity - held}, which is zero or negative while the pile is
     * overfull. The special case needs no code: it is the subtraction.
     *
     * <h3>What type comes up</h3>
     * Like for like. {@link CargoDroneStore} tracks SPACES and not types on purpose, and FD2.445
     * makes cargo drones "proportional to the loading of the racks" — so replacing a two-space drone
     * with a two-space drone is both space-exact and one of the choices FD2.4422 explicitly offers
     * ("a single drone (type-IV or type-IIIXX) or two drones"). Letting the player pick the other is
     * FD2.4422 proper and is not built; neither is FD2.446's record of which special drone sits
     * where.
     * <p>
     * <b>When special drone modules arrive</b> (FD10.4x), like-for-like becomes wrong: an ECM drone
     * leaving must not conjure another from cargo, because FD10.65's percentage caps limit how many
     * the ship ever had. The conveyor should then raise the plain frame at that speed. It is safe
     * today only because {@link DroneType} is still family x speed with no module axis.
     *
     * @return spaces actually moved up, which is 0 with no cargo, a full pile, or after FD2.423
     */
    private double refillFromCargo(double spaces, DroneType type) {
        if (destroyed || cargo == null || type == null || spaces <= 0)
            return 0;
        double room = capacitySpaces() - spacesHeld();
        if (room < spaces - 1e-9)
            return 0;           // overfull, or not enough of the opening left for this drone
        int drawn = cargo.draw((int) Math.ceil(spaces));
        if (drawn <= 0)
            return 0;
        drones.add(new Drone(type));
        return spaces;
    }

    /**
     * Put back a drone taken by {@link #takeForRack}, undoing the conveyor with it.
     *
     * <h3>Why this is not {@link #put}</h3>
     * {@code takeForRack} has already pulled a cargo drone up into the opening, so the pile is full
     * again and a plain {@code put} would be refused over capacity — and a refused put-back means a
     * drone destroyed to enforce a limit, which is the exact bug the take-then-offer ordering was
     * rewritten to avoid. So the cargo drone goes back down before this one comes back: one of the
     * same size leaves the pile, its spaces return to the boxes, and the ship is where it started.
     *
     * <p>The server needs this because FD2.421's two-space-per-rack budget is checked after the
     * drones are gathered — a rack asked for more than a turn's work gets none, and nothing may be
     * lost in the refusal.
     */
    public boolean putBackFromRack(Drone drone) {
        if (drone == null || destroyed)
            return false;
        absorb();
        if (spacesHeld() + drone.getRackSize() > capacitySpaces() + 1e-9 && cargo != null) {
            // The conveyor fired for this drone; send its replacement back to the boxes.
            for (Iterator<Drone> it = drones.iterator(); it.hasNext(); ) {
                Drone sitting = it.next();
                if (Math.abs(sitting.getRackSize() - drone.getRackSize()) < 1e-9) {
                    it.remove();
                    cargo.restore((int) Math.ceil(sitting.getRackSize()));
                    break;
                }
            }
        }
        return put(drone);
    }

    /**
     * FD2.423: the reloads go with the last Excess Damage box.
     *
     * <p>"Drone and ADD reloads (<b>other than those in cargo boxes</b>) are stored in various
     * locations around the ship and are considered destroyed with the last Excess Damage box." So
     * the drones here go, every rack's anti-drone reserve goes with them — the rule names ADD
     * reloads in the same breath — and the cargo boxes are explicitly spared. A ship is not dead at
     * this point: the box that kills it is the one AFTER the last, so it fights on with whatever is
     * in the racks and nothing behind them.
     *
     * <p>It is permanent, and that is a reading worth stating. FD2.442 has this storage
     * "automatically refilled from the drones in cargo boxes", and if that survived the hit a ship
     * with full cargo would quietly regrow its reloads out of the wreckage. The storage itself was
     * destroyed, so the conveyor stops. FD2.423's own next sentence is about a repaired rack loading
     * "the <b>remaining</b> reload drones" — after this there are none remaining.
     * <p>
     * Type-D and type-H racks are unaffected, because FD2.4424 feeds their magazines straight from
     * the cargo boxes and never through here.
     *
     * @return spaces of drones lost, for the damage log
     */
    public double destroyWithLastExcessDamageBox() {
        absorb();               // anything still sitting in a rack's set is reloads too
        double lost = spacesHeld();
        drones.clear();
        for (DroneRack rack : racks()) {
            for (List<Drone> set : rack.getReloads())
                set.clear();
            rack.setAddReloads(0);
        }
        destroyed = true;
        return lost;
    }

    /** True once FD2.423 has taken it. */
    public boolean isDestroyed() {
        return destroyed;
    }

    /** True when the ship has somewhere to keep reloads at all. */
    public boolean exists() {
        return capacitySpaces() > 0;
    }
}
