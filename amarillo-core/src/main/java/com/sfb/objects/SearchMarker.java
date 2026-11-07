package com.sfb.objects;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sfb.properties.Location;
import com.sfb.properties.RetrievalMethod;
import com.sfb.systemgroups.StateIo;

/**
 * A face-down counter: a hex somebody can examine, holding something nobody knows about yet
 * (SH47.46).
 *
 * <h2>The secret is the PAYLOAD, never the position</h2>
 * The counters are on the table and both players can see them — SH47.471 has a ship search one
 * "by entering the hex with the counter", which it could not choose to do if the counters were
 * invisible. What is hidden is which of the eighteen is an ordinary asteroid counter and which
 * is a Stasis Box. Four of the twenty-two were set aside unseen before any were placed, so the
 * COUNT is as much of the secret as the position: neither player knows whether two boxes exist,
 * one, or none.
 *
 * <p>That is what makes this marker UNIFORM by design. Every one looks the same to both players
 * and its DTO carries nothing but the hex. A marker that meant "an unidentified objective is
 * here" would be no secret at all — if only the boxes wore a counter, the counters would spell
 * out where the boxes are.
 *
 * <h2>Why there is no `revealed` flag</h2>
 * A marker never exists in a revealed state. Examining one removes it and materialises whatever
 * it held in the same step, so the live list IS the set of positions nobody has searched, and
 * "where have I already looked" is answered by which counters are still standing.
 *
 * <p>That is a departure from the tabletop, and a deliberate one. SH47.473 says the counter
 * "remains face up" once found, which reads like a record of what has been searched but is
 * really a physical necessity: on the table the counter IS the marker for the asteroid field
 * centred on it, which is why SH47.46 has to say in so many words that removing a Stasis Box
 * does not remove its asteroids. This codebase expands the field into independent asteroid hexes
 * when the map is built, so the counter has no second job and nothing is lost by taking it away.
 *
 * <h2>The payload is built but NOT INSTALLED</h2>
 * A marker holding an objective owns a real {@link Objective} that is simply not in
 * {@code Game}'s list yet. That is the whole privacy mechanism, and it is stronger than
 * filtering: an objective left in {@code game.objectives} with a hidden flag leaked through
 * {@code mapObjects} and then again through the live scoreboard, each needing its own filter and
 * each found separately. A thing that is in no list cannot leak through any walker of one.
 *
 * <h2>Not a unit</h2>
 * A marker does not block movement and cannot be fired on. It is a note about a hex rather than
 * a thing standing in it — whatever terrain is there behaves exactly as it would with no counter
 * on it.
 */
public class SearchMarker extends Marker {

    /**
     * What a counter turns out to be.
     *
     * <p>Two kinds, not three. SH47.46 also deals two NSM counters, and the obvious move was to
     * add a MINE constant here — but nothing can pre-place a mine today ({@code ScenarioSpec}
     * has no mines field at all) and SH47.48 carries its own clauses on top: armed only once
     * revealed, triggering on size class 6 or larger, detonating off the shield the ship entered
     * behind. A constant the materialiser could not honour would be worse than no constant. It
     * is one enum entry and one case when that work is done, which is what a seam is for.
     *
     * <p>Worth recording for whoever does it: SH47.48's "the mines only roll for triggering
     * AFTER they have been revealed" needs no flag in this model. An unexamined mine does not
     * exist, so it cannot trigger.
     */
    public enum PayloadKind {
        /** An ordinary asteroid counter. The hex keeps its terrain; the counter simply goes. */
        NOTHING,
        /** A Stasis Box or other capturable object (SH47.47). */
        OBJECTIVE
    }

    private final PayloadKind kind;

    /** The object underneath, already built and waiting. Null for {@link PayloadKind#NOTHING}. */
    private final Objective objective;

    private SearchMarker(Location location, PayloadKind kind, Objective objective) {
        setLocation(location);
        this.kind = kind;
        this.objective = objective;
        setName("Counter " + (location == null ? "?" : location.toString()));
    }

    /** An ordinary asteroid counter: something to search, with nothing behind it. */
    public static SearchMarker empty(Location location) {
        return new SearchMarker(location, PayloadKind.NOTHING, null);
    }

    /** A counter hiding a capturable object, which takes the counter's hex when found. */
    public static SearchMarker hiding(Location location, Objective objective) {
        if (objective != null)
            objective.setLocation(location);
        return new SearchMarker(location, PayloadKind.OBJECTIVE, objective);
    }

    public PayloadKind getKind() { return kind; }

    /** The object this counter hides, or null. Server-side only — never reaches a client. */
    public Objective getObjective() { return objective; }

    // -------------------------------------------------------------------------
    // Save/load
    // -------------------------------------------------------------------------

    /**
     * The counter and what it hides, in full.
     *
     * <h2>CREATED on load, like a mine and unlike an objective</h2>
     * An objective in the open is rebuilt by the scenario and the save only overlays what the
     * battle did to it. These are DRAWN — four counters of twenty-two withdrawn unseen and the
     * rest dealt at random — so rebuilding from the scenario would deal a different hand on
     * every load and move the box. The save therefore describes each counter outright, identity
     * and all, which is the one place an objective's identity fields are written down.
     *
     * <p>The payload is recorded as it stands, as a dummy mine's {@code isReal} is: a save file
     * lives on the server and is never a client's view, so writing the truth is what lets a
     * reloaded counter go on hiding exactly as far as it had.
     */
    public Map<String, Object> exportState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("x", getLocation() == null ? null : getLocation().getX());
        state.put("y", getLocation() == null ? null : getLocation().getY());
        state.put("kind", kind.name());
        if (objective != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("name", objective.getName());
            payload.put("points", objective.getPoints());
            payload.put("cargoSpaces", objective.getCargoSpaces());
            payload.put("canBeLeftInSpace", objective.isCanBeLeftInSpace());
            payload.put("survivesCarrierDestruction", objective.isSurvivesCarrierDestruction());
            payload.put("side", objective.getSide());
            List<String> retrieval = new ArrayList<>();
            for (RetrievalMethod m : objective.getAllowedRetrieval())
                retrieval.add(m.name());
            payload.put("retrieval", retrieval);
            state.put("payload", payload);
        }
        return state;
    }

    /**
     * Rebuild a counter from a save.
     *
     * @return the marker, or null if the save names a payload kind this build does not have —
     *         which the caller reports, because a counter that quietly disappears takes its box
     *         with it and the players would never know the scenario had changed under them.
     */
    public static SearchMarker fromState(Map<String, Object> state) {
        if (state == null)
            return null;
        PayloadKind kind;
        try {
            kind = PayloadKind.valueOf(StateIo.text(state, "kind"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        Location loc = state.get("x") instanceof Number x && state.get("y") instanceof Number y
                ? new Location(x.intValue(), y.intValue())
                : null;
        if (kind == PayloadKind.NOTHING)
            return empty(loc);

        if (!(state.get("payload") instanceof Map<?, ?> raw))
            return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) raw;

        Objective obj = new Objective();
        obj.setName(StateIo.text(payload, "name"));
        obj.setPoints(StateIo.integer(payload, "points", 0));
        obj.setCargoSpaces(payload.get("cargoSpaces") instanceof Number n ? n.doubleValue() : null);
        obj.setCanBeLeftInSpace(StateIo.flag(payload, "canBeLeftInSpace", false));
        obj.setSurvivesCarrierDestruction(
                StateIo.flag(payload, "survivesCarrierDestruction", true));
        obj.setSide(StateIo.integer(payload, "side", 0));
        if (payload.get("retrieval") instanceof List<?> methods)
            for (Object m : methods)
                try {
                    obj.getAllowedRetrieval().add(RetrievalMethod.valueOf(String.valueOf(m)));
                } catch (IllegalArgumentException e) {
                    // A retrieval method this build does not have. Skipped rather than fatal:
                    // the box is still findable by the methods that did survive, and losing the
                    // whole counter over one unknown word would be the harsher answer.
                }
        return hiding(loc, obj);
    }
}
