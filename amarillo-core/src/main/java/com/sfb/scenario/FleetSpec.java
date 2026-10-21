package com.sfb.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A battle force a player built and saved, ready to drop into a battle (S8.0).
 * <p>
 * This is the JSON under data/fleets, and it is also what the builder posts to have a fleet
 * checked — one shape for buying, saving, loading and validating, so the client never has to
 * translate between them.
 * <p>
 * Deliberately absent: where the ships start. Start hex, heading, speed and weapon status
 * depend on the map and the terrain, which are agreed per battle (S8.15, S8.135), so a fleet
 * that carried them could only ever be played on the map it was born on. Deployment is a
 * battle-time concern; a fleet is the ships and what was spent on them.
 * <p>
 * The conditions it was built under — year, budget, the empires it may draw from — travel with
 * it, because a fleet is only legal against those. A 1000-point Y175 force cannot be dropped
 * into a 750-point Y168 battle, and a fleet saved before its ships were edited may no longer
 * be legal at all; both are for the loader to catch by revalidating.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FleetSpec {

    /** Filename stem under data/fleets, and the id every endpoint addresses it by. */
    public String id;

    /** What to call it in a list, e.g. "Dan — Klingon border patrol". */
    public String name;

    /** Who built it. No account system: this is a label, not an owner. */
    public String author;

    /**
     * The allied empires this force draws on (S8.6), agreed before anyone buys (S8.14).
     * The first is the default for ships that do not name one, so a single-empire force is
     * just ["Klingon"].
     */
    public List<String> factions = new ArrayList<>();

    /** The scenario date this force was assembled for (S8.13). */
    public int year;

    /** The points agreed for this side (S8.11), including Commander's Options (S8.12). */
    public int budget;

    /**
     * The Orion cartel this force flies for (G15.44, R8.1), or null for a fleet that is not
     * Orion or has not said.
     *
     * <h2>On the fleet, because the quota is on the force</h2>
     * G15.44: "The limits below apply to the entire Orion battle force, as otherwise no ship
     * would have enough option mounts to have a 10% availability weapon." G15.441 then splits
     * the force's mounts 70/20/10 across home, operating-zone and any empire. A per-ship cartel
     * could not express that; a fleet IS the battle force, so it is asked here and carried to
     * {@code SideSpec.cartel} when the fleet takes the field.
     */
    public String cartel;

    /** The name of the ship leading it (S8.21). */
    public String flagship;

    public List<ShipEntry> ships = new ArrayList<>();

    /** ISO-8601 instant of the last save, for sorting a list by what you touched last. */
    public String updated;

    /** One ship in the force. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ShipEntry {
        /** Empire it comes from; blank means the force's first (S8.6). */
        public String faction;
        /** SSD type designation, e.g. "D7C". */
        public String type;
        /** What the player called it; blank means the ship file's own name. */
        public String name;
        /** Points spent on Commander's Option items (S3.2). */
        public double coiSpend;

        /**
         * Refit codes ticked for this ship (S3.24), applied to {@link #type} when the fleet
         * resolves — e.g. type "CA" with refits ["O", "+"].
         *
         * <h2>Why, when a refitted hull can also be a TYPE</h2>
         * A refit that someone NAMED is a hull in its own right: a Klingon D6 with the B refit
         * is a D6B, and the library registers it. Most refits were never named. Sixteen of the
         * seventeen Orion files declare their refits as switches and name no variant, so OAKDISC
         * and the cloak could be ticked in the builder and never bought — the combination is
         * legal under S3.24 and only the catalogue was silent.
         *
         * <p>Naming them is not the answer: the Orion CA has four refits, so fifteen
         * combinations, and an OAKDISC-fitted CA has no canonical name to give it. So the fleet
         * records what was TICKED and the loader applies it, exactly as it does for option
         * mounts — and a hull bought by its named type simply carries no codes here.
         */
        public java.util.List<String> refits = new java.util.ArrayList<>();

        /**
         * Orion option-mount choices bought with the ship (G15.4), by mount designator → Annex
         * #8B option name. The same shape as {@code ScenarioSpec.ShipSetup.optionMounts} and
         * {@code CoiLoadout.optionMounts}: one record of one fact, in whichever hand bought it.
         *
         * <h2>Why a field, when refits are not one</h2>
         * A refit is recorded by TYPE — the builder resolves the ticked refits to a variant hull
         * and stores that hull, so a plus-refitted LR is simply an "LR+". A loadout cannot
         * follow it: the BCH has four mounts against a catalogue of seventy-four, and no
         * catalogue of variant types could hold the combinations.
         *
         * <h2>Bought, not chosen on the day</h2>
         * G15.415 puts a mount change in a shipyard "taking several weeks or months", so the
         * loadout is part of the purchase and belongs in the saved fleet. It is also part of the
         * PRICE: G15.411 adds each option's cost to the ship's combat and economic values alike,
         * which is why {@code FleetValidator.costOf} must read the effective BPV.
         */
        public java.util.Map<String, String> optionMounts = new java.util.LinkedHashMap<>();
        /**
         * R8.R3's wing arc choice per mount: designator -> "FORWARD" or "OUTBOARD".
         *
         * <p>A disruptor or fusion beam in a WING option mount may bear FA or outboard
         * (L + LF on the left, RF + R on the right) "depending on the taste of the Orion
         * player" — one ship may carry one of each. Nothing derives it, so it is bought with
         * the weapon like the weapon itself (G15.415). Absent or unrecognised means FORWARD,
         * and it is ignored for any mount where R8.R3 leaves no choice.
         */
        public java.util.Map<String, String> optionMountArcs = new java.util.LinkedHashMap<>();
    }

    /** The empire a ship entry belongs to, falling back to the force's first. */
    public String factionOf(ShipEntry entry) {
        if (entry.faction != null && !entry.faction.isBlank())
            return entry.faction;
        return factions.isEmpty() ? null : factions.get(0);
    }

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public static FleetSpec fromJson(File file) throws IOException {
        return MAPPER.readValue(file, FleetSpec.class);
    }

    public void toJson(File file) throws IOException {
        MAPPER.writeValue(file, this);
    }
}
