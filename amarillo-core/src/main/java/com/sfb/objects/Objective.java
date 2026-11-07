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

    /**
     * (D7.825/D7.824) Whether a hit-and-run raid may be made to seize this thing.
     *
     * <h2>Default FALSE, and that is the rule rather than caution</h2>
     * D7.825: "Non-SSD items and special abilities ... CANNOT be attacked by hit and run raids
     * UNLESS THE RULES SPECIFICALLY ALLOW IT." An objective is exactly such a non-SSD item, so
     * refusal is the general case and a scenario grants the exception — which is also what
     * D7.824 says in so many words: "This will normally involve items specified by a given
     * scenario."
     *
     * <p>SH47.476 is one such grant: "If the Stasis Box is aboard one ship, the other ship may
     * attempt a hit-and-run raid to seize the box (it can be guarded)." A probe canister in
     * SH35 is not — nothing there offers a raid for it, so nothing should.
     */
    private boolean raidable;

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

    // Fog of war (SH47.46/.471). A counter lies face down until somebody enters its hex, and
    // DEFAULTS TO REVEALED: every scenario written before this existed put its objectives on the
    // table in plain sight, and a default of hidden would have made all of them disappear.
    //
    // SYMMETRIC, unlike every other secret in this codebase. A drone's type is hidden from the
    // ENEMY and known to its owner, which is what Game's hiddenFrom(viewerTeam, owner) expresses;
    // a face-down counter belongs to nobody and is unknown to BOTH players. So this needs no
    // viewer and no team: it is simply not sent until it is turned over.
    //
    // ONE-WAY, like SpaceMine.reveal(). SH47.473: "the counter remains face up" — once examined
    // it stays examined, for everyone, including the player who did not do the examining.
    private boolean revealed = true;

    /**
     * How a ship earns the right to take this thing (SH47.474), and how much it needs.
     *
     * <h2>One accumulator, two accrual rules</h2>
     * SH47.474 wants two COMPLETE TURNS in the hex, flat, with no reference to the ship's
     * instruments. A survey or monster scenario instead wants G4.1 research points, where the
     * number of lab boxes is the whole point. Those are the same mechanic with different
     * arithmetic per turn, so they share one counter and one threshold rather than becoming two
     * code paths.
     *
     * <h2>Why SH47 is TURNS and not points, which was the tempting answer</h2>
     * G4.11's chart is already implemented and, until this, had no production caller at all —
     * so paying study out of it is attractive. But it would make study lab-dependent, and the
     * lab counts decide the scenario: a Federation DD has EIGHT labs against the Kzinti CL's
     * four, which turns SH47.474's even two turns into one turn against two or three.
     *
     * <p>The clincher is SH47.71, the scenario's own balance lever: "change the Kzinti CL to a
     * Kzinti DD" — which has TWO labs. The option meant to help the weaker player would
     * quadruple their disadvantage, so the lever becomes an anti-lever. SH47.474 is
     * lab-independent precisely so that cannot happen.
     */
    public enum StudyMode {
        /** No study needed; a revealed objective may be taken at once. The default. */
        NONE,
        /** Complete turns in the hex (SH47.474). One per turn, whatever the ship is. */
        TURNS,
        /** G4.11 research points, so lab boxes set the pace. Range is 0 — same hex. */
        RESEARCH_POINTS
    }

    private StudyMode studyMode = StudyMode.NONE;
    private int studyRequired;

    /**
     * How far each ship has got, by ship NAME, and reset when it breaks off.
     *
     * <p>PER SHIP, because SH47.474 gives the work to "the player in the hex with it" — one
     * side's patient fortnight is not the other's. And reset on leaving, because the rule says
     * two COMPLETE turns: a ship that breaks off loses what it had. That is what makes the
     * Kzinti tactic in SH47.8 — "if you have to stop and study the box, have a weasel ready" —
     * worth anything at all. Interrupting a rival's study has to cost them something.
     */
    private final java.util.Map<String, Integer> studyProgress = new java.util.LinkedHashMap<>();

    /**
     * Ships that have already failed to be in the hex at some point this turn, so cannot be
     * credited with a COMPLETE turn of it however the turn ends. Cleared at turn start.
     */
    private final java.util.Set<String> studyBrokenThisTurn = new java.util.LinkedHashSet<>();

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

    /** (D7.825) Whether a hit-and-run raid may be made to seize this; a scenario grant. */
    public boolean isRaidable() { return raidable; }

    public void setRaidable(boolean raidable) { this.raidable = raidable; }

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

    // --- Fog of war (SH47.46/.471) ---

    /** Whether this thing has been found. An unrevealed objective is not sent to anybody. */
    public boolean isRevealed() { return revealed; }

    /**
     * Turn this counter face down. SCENARIO SETUP ONLY — there is no way back to hidden during
     * play, because {@link #reveal()} is one-way (SH47.473). Separate from a plain setter for
     * exactly that reason: a setter invites the reverse, and the reverse is not a thing that
     * happens.
     */
    public void hide() { this.revealed = false; }

    /**
     * Somebody has examined this counter. One-way and GLOBAL: SH47.473 leaves it face up, so the
     * player who did not do the examining learns what is there too. That is the rule, not a
     * simplification — the counter is physically turned over on the table.
     */
    public void reveal() { this.revealed = true; }

    // --- Study (SH47.474) ---

    public StudyMode getStudyMode() { return studyMode; }

    public int getStudyRequired() { return studyRequired; }

    public void setStudy(StudyMode mode, int required) {
        this.studyMode = mode == null ? StudyMode.NONE : mode;
        this.studyRequired = Math.max(0, required);
    }

    /** Whether anything has to be done before this can be taken at all. */
    public boolean requiresStudy() {
        return studyMode != StudyMode.NONE && studyRequired > 0;
    }

    public int studyProgressFor(String shipName) {
        return studyProgress.getOrDefault(shipName, 0);
    }

    /** Whether this ship has done the work (SH47.474) and may now take it. */
    public boolean isStudiedBy(String shipName) {
        return !requiresStudy() || studyProgressFor(shipName) >= studyRequired;
    }

    /** Turn start: everyone is eligible for a complete turn again. */
    public void beginStudyTurn() {
        studyBrokenThisTurn.clear();
    }

    /**
     * This ship was somewhere else just now, so this turn cannot be a complete one for it.
     * Idempotent — called every impulse the ship is away.
     */
    public void breakStudy(String shipName) {
        studyBrokenThisTurn.add(shipName);
    }

    public boolean studyBrokenThisTurn(String shipName) {
        return studyBrokenThisTurn.contains(shipName);
    }

    /**
     * Turn end: credit a ship that was in the hex throughout, or wipe one that was not.
     *
     * <p>Wiping is the harsher of the two readings of "two complete turns" and the one that
     * makes the rule mean something: without it a ship could study a turn, run from a photon
     * salvo, and come back to resume where it left off, which is not spending two complete
     * turns in the hex by any reading.
     *
     * @param amount what a complete turn is worth — 1 for TURNS, the G4.11 total for points
     * @return the ship's progress after this turn
     */
    public int creditStudy(String shipName, int amount) {
        if (!requiresStudy())
            return 0;
        if (studyBrokenThisTurn.contains(shipName)) {
            studyProgress.remove(shipName);
            return 0;
        }
        int now = studyProgressFor(shipName) + Math.max(0, amount);
        studyProgress.put(shipName, now);
        return now;
    }

    /** Progress by ship name, for the save and the DTO. */
    public java.util.Map<String, Integer> getStudyProgress() { return studyProgress; }

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
        // Found or not (SH47.471). Saved as it stands, like a dummy mine's isReal: a save file
        // lives on the server and is never a client's view, so restoring the truth is what lets
        // a reloaded counter go on hiding exactly as far as it had. Without this a reload would
        // hand back every box the players had spent turns examining.
        state.put("revealed", revealed);
        // How far each ship has got (SH47.474). MUTABLE, so it travels — losing it would hand
        // back two turns of work and, worse, do it silently. The MODE and the THRESHOLD do not
        // travel: those are what the scenario says this thing is, like its retrieval methods.
        state.put("studyProgress", new java.util.LinkedHashMap<>(studyProgress));
        // Deliberately NOT saved: studyBrokenThisTurn. A save is taken at a turn boundary, and
        // beginStudyTurn() clears it there anyway — persisting it would carry a mid-turn fact
        // across the one moment it is guaranteed to be empty.
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
        // Defaults to the value the SCENARIO just gave it, not to a constant, so a save written
        // before fog existed leaves a hidden scenario's counters hidden and a plain scenario's
        // counters visible. A hardcoded true would have quietly uncovered every box in an older
        // SH47 save; a hardcoded false would have hidden every probe canister in an older SH35.
        this.revealed = com.sfb.systemgroups.StateIo.flag(state, "revealed", this.revealed);
        studyProgress.clear();
        if (state.get("studyProgress") instanceof java.util.Map<?, ?> saved)
            for (java.util.Map.Entry<?, ?> e : saved.entrySet())
                if (e.getValue() instanceof Number n)
                    studyProgress.put(String.valueOf(e.getKey()), n.intValue());
        studyBrokenThisTurn.clear();
    }
}
