package com.sfb.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.objects.Propulsion;
import com.sfb.weapons.DroneRack;

/**
 * Ready-made loadouts for one drone rack — "fill it with heavy drones", "fill it with ADDs".
 *
 * <h2>Why these exist at all</h2>
 * FD2.41 lets a player mix drones in a rack "as the players may elect", and the COI dialog honours
 * that literally: a rack is built one drone at a time. Between leaving a rack alone and placing
 * four chips by hand there was nothing, and a Kzinti player bringing three ships of four racks
 * each had twelve magazines to fill that way. Most of those twelve want one of a handful of
 * answers.
 *
 * <h2>Why core offers them rather than the dialog</h2>
 * Which drones a rack may hold is a rules question on three axes — FD3.5's rack type, FD10.65's
 * availability ladder for the year, and how many fit (FD3.0) — and the view does not decide those.
 * It also means the fleet builder can offer the same presets, at the same prices, without a second
 * implementation deciding them differently.
 *
 * <h2>They are always free, and that is a consequence of what they offer</h2>
 * A preset loads the drone the year itself would fit (FD2.31, FD2.22), and FD2.11 exchanges those
 * at par: "two type-Is with one type-IV is 0.00", "one type-I with two type-VIs is 0.00". The two
 * uncapped drones that DO cost — the type-II and the type-V, half a point each — are not speed
 * upgrades at all but special purchases in their own right, and are left to the picker. The price
 * is still computed rather than written as a zero, so the day a General Availability item costs
 * something the button says so.
 *
 * <h2>Only General Availability, which is what makes a preset safe to offer</h2>
 * FD10.62 and FD10.63 cap special drones as a percentage of the drones a SHIP carries, so whether
 * a rack of ECM drones is legal depends on what the other racks hold. A preset cannot know that,
 * so it does not offer one: every preset here is General Availability (FD10.61), which is uncapped
 * and therefore legal in every rack at once. Special drones stay where the cap can be judged
 * against the whole ship — the per-drone payload editor, and the endpoint's refusal behind it.
 */
public final class DroneRackPresets {

    private DroneRackPresets() {
    }

    /**
     * One offered loadout: the drones, FD3.70's anti-drone rounds beside them in the same
     * magazine, and what it costs the Commander's Option budget (S3.2).
     *
     * <p>It carries no label. The dialog already renders a drone list as "4 × Type-IF" for the
     * ammunition a rack arrived with, and a preset is the same kind of thing said in advance, so
     * labelling it here would be a second spelling of a name the client already knows how to
     * write.
     */
    public record Preset(List<DroneLoad> drones, int antiDrones, double surcharge) {
    }

    /**
     * The presets worth offering for this rack in this year: one per drone family the rack
     * accepts, filled with the fastest uncapped drone of that family, plus a full load of
     * anti-drones where the rack has the system for them (FD3.70).
     *
     * <p>A preset identical to what the rack already holds is left out, because the dialog offers
     * that one separately — "edit this load" copies the ship's own ammunition into the builder,
     * and a preset button beside it saying the same thing would be two routes to one place.
     */
    public static List<Preset> forRack(DroneRack rack, int year) {
        List<Preset> out = new ArrayList<>();
        if (rack == null)
            return out;
        List<DroneLoad> current = currentLoad(rack);

        for (DroneType.Family family : DroneType.Family.values()) {
            DroneLoad pick = standardLoadFor(rack, family, year);
            if (pick == null)
                continue;
            // FD3.0: whole drones only, so a 1-space magazine offers no heavy preset rather
            // than half of one.
            int count = (int) Math.floor(rack.getSpaces() / pick.rackSpaces());
            if (count == 0)
                continue;
            List<DroneLoad> load = Collections.nCopies(count, pick);
            if (sameLoad(load, current))
                continue;
            out.add(new Preset(List.copyOf(load), 0, CoiLoadout.rackUpgradeCost(load, year)));
        }

        // FD3.70: a type-G's anti-drone loading "must always be planned", and nothing reloads an
        // ADD into it in play (E5.74), so a full set is a loadout decision like any other.
        //
        // Deliberately NOT asking canLoadAntiDrones, which answers a different question: whether
        // this many rounds would fit ON TOP of what the rack is holding. A loadout replaces the
        // load, so the magazine it has to fit in is the empty one, and a rack with its default
        // drones aboard reports no room at all. Only the Y140 gate is shared.
        if (rack.acceptsAntiDrones() && year >= DroneRack.ANTI_DRONE_FIRST_YEAR) {
            int rounds = rack.fullAntiDroneSet();
            if (rounds > 0)
                out.add(new Preset(List.of(), rounds, 0));
        }
        return out;
    }

    /**
     * This family's standard load at the year's standard speed — the drone the ship would be
     * sailing with anyway, offered for a rack that is carrying something else.
     *
     * <h2>The year's SPEED, not the family's fastest member</h2>
     * This asked for the fastest drone in the family and it was wrong, in the way FD2.31 and
     * FD2.22 between them explain. FD2.31: "All drone racks are presumed to be loaded with
     * type-I-slow drones... This is included in the BPV of the ship." FD2.22 then enumerates
     * what a ship is normally refitted with as its year comes, and there are four — active
     * terminal guidance, extended range, medium speed, fast speed. <b>Moderate speed is not on
     * the list.</b>
     *
     * <p>So the speed-12 drones are not faster type-Is. A type-II is its own drone with TWO turns
     * of endurance where the type-I has three (FD2.1), and FD2.11 charges half a point to put one
     * in a rack — "one type-I with one type-II is 0.50". Same for the type-V against the type-IV.
     * Asking for the fastest member bought a shorter-legged drone in Y145, called it an upgrade,
     * and billed the player two points for the privilege.
     *
     * <p>{@link Propulsion#generalIn} is the mechanism that already knows this, because
     * {@code isImprovement} leaves speed 12 off FD2.22's list, and {@link DroneType#atSpeed}
     * carries the endurance across with the engine. They are what {@code ScenarioLoader}
     * upgrades a rack with, so a preset built from them offers what the year itself would fit
     * rather than a second opinion about it.
     *
     * <p>Which is also why these presets never cost anything: see the test
     * {@code everyPresetIsFreeBecauseTheLadderIsAtPar}. A player wanting a rack of type-IIs is
     * buying a special drone and does it in the picker, beside the ECM and swordfish drones.
     */
    private static DroneLoad standardLoadFor(DroneRack rack, DroneType.Family family, int year) {
        // The family's own base: its slowest member. Type-I and type-IV at speed 8, and —
        // FD2.227 — the type-III and type-VI at speed 12, which is their slow.
        DroneType base = null;
        for (DroneType type : DroneType.values())
            if (type.family == family
                    && (base == null || type.spec.propulsion().speed < base.spec.propulsion().speed))
                base = type;
        if (base == null)
            return null;

        // The engine the year hands out as standard, and the catalogue sibling that carries it.
        // Null where the chart has no such drone -- FD2.11 notes a speed-8 type-III "technically
        // does not exist" -- and then this family has no standard load for the year and is
        // offered nothing. Falling back to the base would offer a drone at a speed the year did
        // NOT fit, which is the thing this method exists to stop doing.
        //
        // Only the frame and the family of {@code base} are read from here on, and atSpeed
        // searches by family, so which member it anchors on cannot change the answer.
        Propulsion want = Propulsion.generalIn(year, base.spec.effectiveFrame());
        DroneType pick = base.atSpeed(want);

        if (pick == null || !rack.accepts(pick))
            return null;
        DroneLoad load = DroneLoad.of(pick);
        if (!load.availableIn(year) || load.availabilityIn(year) != Availability.GENERAL)
            return null;
        return load;
    }

    /** What the rack holds now, in the same form a loadout is written in. */
    private static List<DroneLoad> currentLoad(DroneRack rack) {
        List<DroneLoad> out = new ArrayList<>();
        for (Drone d : rack.getAmmo()) {
            if (d.getDroneType() == null)
                continue;
            out.add(new DroneLoad(d.getDroneType(), d.getSpec()));
        }
        return out;
    }

    /** Whether two loads are the same drones, order included — they are both generated. */
    private static boolean sameLoad(List<DroneLoad> a, List<DroneLoad> b) {
        if (a.size() != b.size())
            return false;
        for (int i = 0; i < a.size(); i++)
            if (!a.get(i).format().equals(b.get(i).format()))
                return false;
        return true;
    }
}
