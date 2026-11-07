package com.sfb.objects;

import java.util.EnumSet;
import java.util.Set;

import com.sfb.properties.RetrievalMethod;

/**
 * A scenario objective object — a stasis box, probe canister, cargo pod, or
 * similar thing that ships try to capture and carry off the map. First-class so
 * each instance keeps its own identity (retrieval rules, carrier, and later a
 * reveal/fog state).
 *
 * <p>Location has two states: FREE (on the map at {@link #getLocation()}, no
 * carrier) or CARRIED (aboard {@link #getCarrier()}, position derived from it).
 * Pickup moves free → carried; the carrier's destruction drops it back to free
 * in that hex, or annihilates it if it does not survive (SH35.454 vs SH47.475).
 */
public class Objective extends Marker implements Tractorable {

    private final Set<RetrievalMethod> allowedRetrieval = EnumSet.noneOf(RetrievalMethod.class);
    private boolean survivesCarrierDestruction = true;
    private Ship carrier;                 // null = free on the map (live possession)
    private com.sfb.Player securedBy;     // permanent owner once carried off a valid edge

    // For a party sitting on a specific face of a planet (SH50.46): the hex side
    // (1..6, A..F) it occupies. 0 = not tied to a planet face (a normal drifting
    // object). Retrieval then requires that side to be visible from the ship.
    private int side;
    private int points;                   // VP to the controlling side at scenario end (S2, generic objective scoring)

    // (G25.1) How much room this thing takes up, in cargo spaces; Annex #7K lists the
    // sizes (a probe is 2, a large mine 4, a stored shuttle 25, a PF 125). Boxed because
    // a scenario that never says is NOT the same as a weightless one: null means unstated,
    // and every size gate is then skipped rather than silently passed.
    private Double cargoSpaces;

    // (G25.214) "It is not possible to transport (or drop) cargo ... into space and have that
    // cargo then 'exist' for some purpose (e.g. damaging an ESG, later pickup, web anchor). A
    // special scenario rule might provide a specific type of item that could be left in space
    // for a time." So leaving a thing adrift is a SCENARIO GRANT, not a general ability, and
    // the default has to be refusal. An objective that starts free on the map is already such
    // a grant; this flag is about putting one back.
    private boolean canBeLeftInSpace;

    // While free, an objective can be caught in a tractor beam and drawn aboard
    // via the friendly-shuttle rotation system (J1.621 / SH35.452). These track
    // that transient pull-in; the objective stays FREE (carrier == null) until
    // recovery completes and sets the carrier.
    private Unit tractoringUnit;          // the ship whose beam holds it, or null
    private boolean beingRecovered;       // J1.621 pull-in declared

    public Objective() {}

    public Objective(String name, int x, int y) {
        super(x, y);
        setName(name);
    }

    public Set<RetrievalMethod> getAllowedRetrieval() { return allowedRetrieval; }

    public boolean allows(RetrievalMethod method) { return allowedRetrieval.contains(method); }

    public boolean isSurvivesCarrierDestruction() { return survivesCarrierDestruction; }

    public void setSurvivesCarrierDestruction(boolean survives) {
        this.survivesCarrierDestruction = survives;
    }

    public Ship getCarrier() { return carrier; }

    public void setCarrier(Ship carrier) { this.carrier = carrier; }

    /** Planet hex side (1..6) this party occupies, or 0 if not on a planet face. */
    public int getPoints() { return points; }

    public void setPoints(int points) { this.points = points; }

    /** Size in cargo spaces (G25.1/Annex #7K), or null when the scenario does not say. */
    public Double getCargoSpaces() { return cargoSpaces; }

    public void setCargoSpaces(Double cargoSpaces) { this.cargoSpaces = cargoSpaces; }

    /** (G25.214) Whether this specific item may be left adrift in space and still exist. */
    public boolean isCanBeLeftInSpace() { return canBeLeftInSpace; }

    public void setCanBeLeftInSpace(boolean canBeLeftInSpace) {
        this.canBeLeftInSpace = canBeLeftInSpace;
    }

    public int getSide() { return side; }

    public void setSide(int side) { this.side = side; }

    // --- Tractorable: the beam's view of a free objective (J1.621 pull-in) ---

    @Override
    public Unit getTractoringUnit() { return tractoringUnit; }

    @Override
    public void applyTractor(Unit holder) { this.tractoringUnit = holder; }

    @Override
    public void releaseTractor() {
        this.tractoringUnit = null;
        this.beingRecovered = false; // link break ends the recovery (J1.6221)
    }

    public boolean isBeingRecovered() { return beingRecovered; }

    public void setBeingRecovered(boolean beingRecovered) { this.beingRecovered = beingRecovered; }

    public boolean isFree() { return carrier == null && securedBy == null; }

    public boolean isCarried() { return carrier != null && securedBy == null; }

    /**
     * Permanent owner, set once a ship carries this objective off a valid map
     * edge (SH35.5 "on board at disengagement"). One-way — never changes after.
     * A secured objective is out of play: no carrier, no map location.
     */
    public com.sfb.Player getSecuredBy() { return securedBy; }

    public void setSecuredBy(com.sfb.Player player) { this.securedBy = player; }

    public boolean isSecured() { return securedBy != null; }

    /**
     * Who controls this objective right now: the permanent owner if secured,
     * else the live carrier's owner (which changes hands when stolen), else
     * null when free on the map.
     */
    public com.sfb.Player getCurrentOwner() {
        if (securedBy != null)
            return securedBy;
        return carrier != null ? carrier.getOwner() : null;
    }

    /**
     * Cargo spaces for a message: Annex #7K mixes whole numbers with fractions as fine as
     * 0.05, so a plain {@code double} prints "2.0" where the annex says "2".
     */
    public static String formatSpaces(double spaces) {
        return spaces == Math.rint(spaces)
                ? String.valueOf((long) spaces)
                : String.valueOf(spaces);
    }

    /** Effective hex: the carrier's when carried, else its own free location. */
    public com.sfb.properties.Location getEffectiveLocation() {
        return carrier != null ? carrier.getLocation() : getLocation();
    }

    // -------------------------------------------------------------------------
    // Save/load
    // -------------------------------------------------------------------------

    /**
     * What the battle did to this objective (save slice).
     *
     * <h2>Overlaid, not constructed — unlike a mine or a seeker</h2>
     * An objective is built by the SCENARIO, so everything that identifies one is already there
     * when a save is loaded and must not be written here: its retrieval methods, points,
     * {@code cargoSpaces}, {@code canBeLeftInSpace}, {@code survivesCarrierDestruction} and
     * {@code side} are all facts about what the scenario put on the map, and a save that carried
     * them would quietly overrule an edited scenario. What travels is only what possession and
     * the battle can change.
     *
     * <h2>Why the whole objective had to start being saved</h2>
     * Until 2026-11-07 the save had no {@code objectives} key at all, so a stasis box carried
     * aboard a ship came back FREE in the hex the scenario first put it in, with no problem
     * reported — the battle's entire object of play handed back to the map. Nothing caught it
     * because the round-trip fixture had no objective in it, and a whole-DTO comparison only
     * detects losses in state the fixture actually changes.
     *
     * <h2>null location is a VALUE here</h2>
     * A mine omits x/y when it has no location; an objective cannot, because a null location is
     * the meaningful difference between free in a hex and aboard a ship (or secured and out of
     * play). So both keys are always written, null and all, and the reader treats "not two
     * numbers" as a deliberate null rather than as a missing key.
     *
     * <p>References — the carrier, the beam holding it, and the player who secured it — are
     * written as NAMES like every other saved reference, and resolved by {@code Game} once every
     * unit exists.
     */
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("name", getName());
        state.put("x", getLocation() == null ? null : getLocation().getX());
        state.put("y", getLocation() == null ? null : getLocation().getY());
        state.put("carrier", carrier == null ? null : carrier.getName());
        state.put("securedByTeam", securedBy == null ? null : securedBy.getTeamName());
        state.put("tractoringUnit", tractoringUnit == null ? null : tractoringUnit.getName());
        state.put("beingRecovered", beingRecovered);
        return state;
    }

    /**
     * Put this objective back where the battle had got it to, with its references already
     * resolved by the caller (see {@link #exportState()} for what is deliberately absent).
     *
     * <p>A reference that did not resolve arrives as null and is accepted as null, because every
     * one of them legitimately goes missing: a carrier can be destroyed, a tractor link broken,
     * and an objective secured by a player whose team the scenario no longer has is still out of
     * play. The carrier is the one worth a word to the player, and {@code Game} says it there
     * where it can tell the difference between "not in this save" and "did not resolve".
     */
    public void applyState(java.util.Map<String, Object> state, Ship carrier,
                           com.sfb.Player securedBy, Unit tractoringUnit) {
        if (state == null)
            return;
        setLocation(state.get("x") instanceof Number x && state.get("y") instanceof Number y
                ? new com.sfb.properties.Location(x.intValue(), y.intValue())
                : null);
        this.carrier = carrier;
        this.securedBy = securedBy;
        this.tractoringUnit = tractoringUnit;
        this.beingRecovered = com.sfb.systemgroups.StateIo.flag(state, "beingRecovered", false);
    }
}
