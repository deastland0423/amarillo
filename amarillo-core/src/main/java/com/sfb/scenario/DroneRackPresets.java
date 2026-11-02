package com.sfb.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sfb.objects.Availability;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
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
            DroneLoad pick = fastestUncapped(rack, family, year);
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
     * The fastest drone of this family the rack can hold without spending a percentage slot.
     *
     * <p>Fastest rather than cheapest, because the cheapest fill is already on offer without a
     * button: S2.11 has {@code applyDroneSpeed} fit the year's own speed before the player is
     * asked anything, so leaving the rack alone IS the free load. A preset earns its place by
     * offering something the rack does not already hold, and what it costs — nothing from the
     * year a speed becomes standard issue, 0.50 a drone before then (FD2.11) — is sent with it
     * for the button to say.
     *
     * <p>Uncapped means General in THIS year, which is why Y179 offers a rack of mediums where
     * Y180 offers fasts: FD10.65 has fast drones Restricted until Y180.
     */
    private static DroneLoad fastestUncapped(DroneRack rack, DroneType.Family family, int year) {
        DroneLoad best = null;
        for (DroneType type : DroneType.values()) {
            if (type.family != family || !rack.accepts(type))
                continue;
            DroneLoad load = DroneLoad.of(type);
            if (!load.availableIn(year) || load.availabilityIn(year) != Availability.GENERAL)
                continue;
            if (best == null || load.speed() > best.speed())
                best = load;
        }
        return best;
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
