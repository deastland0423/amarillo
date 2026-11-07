package com.sfb.objects;

/**
 * There are two types of space mine: tBombs and Nuclear Space Mines (NSMs).
 * 
 * <h1>Transporter Bombs (tBombs)</h1>
 * A tBomb (transporter bomb) is a mine placed on the map via transporter.
 * 
 * A tBomb may also be dropped out the shuttle bay (M2.1).
 *
 * <h3>Activation</h3>
 * A tBomb starts inactive. Per M3.223, it becomes permanently active at the
 * end of the transporter step of the second subsequent impulse after placement
 * (i.e., placed on impulse N → arms end of impulse N+2 → cannot trigger until
 * impulse N+3). The laying ship's position does not affect the arming timer;
 * see M3.32 for the separate adjacency restriction.
 *
 * <h3>Detection and detonation</h3>
 * Each impulse, every unit within range 1 (the mine's hex plus its 6
 * neighbours)
 * is checked:
 * <ul>
 * <li>Speed &ge; 6 → automatically detected → detonate.</li>
 * <li>Speed &lt; 6 → roll 1d6; if roll &gt; speed → detected → detonate.</li>
 * </ul>
 * On detonation, 10 points of damage are applied to the facing shield of every
 * unit within range 1.
 *
 * <h3>Dummy tBombs</h3>
 * Dummy tBombs appear identical to real ones on the map. They never detonate.
 * When a unit enters range 1 without an explosion the dummy is revealed.
 * 
 * <h1>Nuclear Space Mines (NSMs)</h1>
 * 
 * A Nuclear Space Mine is a mine variant that is always real (never dummy) and
 * has a higher damage value
 * It is never transported. The only way a NSM can be placed is to drop it out
 * the shuttle bay.
 * 
 */
public class SpaceMine extends Marker {

    /**
     * The two sizes (M4.1) and what each one does to the shield facing it.
     *
     * <p>(M2.501) "A large explosive mine causes 35 POINTS OF DAMAGE to the shield facing the
     * mine hex." The NSM carried 25 here until 2026-11-07, which is not a number in the rule —
     * a large mine was doing under three-quarters of its warhead, and the ships that carry one
     * (M2.7: the Romulan sublights, the Eagles, the Q-ships) carry exactly one.
     *
     * <p>(M3.31) "A T-bomb operates as an (M2.0) large mine, with the exception that the
     * explosion is equal to TEN damage points." So the small mine's ten is right, and the two
     * values come from different rules rather than one table — which is how one of them stayed
     * wrong while the other was correct.
     */
    public enum MineType {
        T_BOMB("tBomb", 10),   // M3.31
        NSM("NSM", 35);        // M2.501

        public final String label;
        public final int damage;

        MineType(String label, int damage) {
            this.label = label;
            this.damage = damage;
        }
    }

    /** How the mine was placed — determines arming logic. */
    public enum PlacementMethod { TRANSPORTER, DROPPED }

    private final MineType mineType;
    private final PlacementMethod placementMethod;
    private final boolean isReal;
    private final Ship layingShip;
    private final int placedOnImpulse;

    /** M3.32: true if transported into the laying ship's adjacent hex (range 1). */
    private final boolean placedAdjacent;

    private boolean active = false;
    private boolean revealed = false; // true once a dummy has been exposed

    /**
     * (M2.14) Which size classes (R0.6) this mine will accept as a target. EMPTY MEANS ALL,
     * which M2.141 states outright: "If no classes are specified, it will accept all size
     * classes." So every mine laid before this existed behaves exactly as it did.
     *
     * <p>The point of the setting, in the rule's own words, is to let a mine "ignore drones but
     * accept ships, thereby preventing the enemy from clearing the mines without risk by
     * detonating them with drones". A mine with settings is therefore MORE dangerous to ships
     * than one without, not less.
     *
     * <p>Secret by M2.141 — "the player laying it secretly records in writing the size classes"
     * — and kept secret the way the dummy flag is: never put in {@code MineDto}. Nothing lets a
     * player record settings yet, so the only user today is a scenario (SH47.48). The need not
     * be contiguous (M2.144: "a setting of 1-3 and 5-7 is acceptable"), which is why this is a
     * set of classes rather than a threshold.
     */
    private final java.util.Set<Integer> acceptedSizeClasses = new java.util.LinkedHashSet<>();

    /** Minimal instance for client-side rendering only — no game logic. */
    public static SpaceMine forRendering() {
        return new SpaceMine(MineType.T_BOMB, PlacementMethod.TRANSPORTER, null, 0, true, false);
    }

    public SpaceMine(MineType mineType, PlacementMethod placementMethod,
                     Ship layingShip, int placedOnImpulse, boolean isReal, boolean placedAdjacent) {
        this.mineType        = mineType;
        this.placementMethod = placementMethod;
        this.layingShip      = layingShip;
        this.placedOnImpulse = placedOnImpulse;
        this.isReal          = (mineType == MineType.NSM || isReal);
        this.name            = mineType.label;
        this.placedAdjacent  = placedAdjacent;
    }

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------

    public boolean isActive() { return active; }

    /** (M2.14) The size classes this mine accepts; empty means all of them (M2.141). */
    public java.util.Set<Integer> getAcceptedSizeClasses() { return acceptedSizeClasses; }

    /**
     * (M2.14) Whether this mine will accept a unit of {@code sizeClass} as a target.
     *
     * <p>Note which way the numbers run: in SFB a LARGER size class is a SMALLER unit, so
     * SH47.48's "size class 6 or larger" is the set {1..6} and excludes 7 and up. Getting that
     * backwards would build a mine that ignored every ship and went off on drones.
     */
    public boolean accepts(int sizeClass) {
        return acceptedSizeClasses.isEmpty() || acceptedSizeClasses.contains(sizeClass);
    }

    public PlacementMethod getPlacementMethod() { return placementMethod; }

    /**
     * Attempt to arm this mine. Call each impulse while inactive.
     *
     * <p>Transporter mines (M3.223): arm after 2 complete impulses. If placed
     * adjacent (M3.32), also requires the laying ship to be outside the
     * detection zone (range > 1). Both conditions run concurrently.
     *
     * <p>Dropped mines (M2.34): arm as soon as the laying ship is more than
     * 1 hex away (outside the detection zone).
     *
     * @param currentImpulse absolute impulse number
     * @param layerRange     hex distance from mine to laying ship
     */
    public void tryActivate(int currentImpulse, int layerRange) {
        if (active) return;
        if (placementMethod == PlacementMethod.TRANSPORTER) {
            boolean timerMet = (currentImpulse - placedOnImpulse) >= 2;
            boolean rangeMet = !placedAdjacent || layerRange > 1;
            if (timerMet && rangeMet)
                active = true;
        } else {
            if (layerRange > 1)
                active = true;
        }
    }

    // -------------------------------------------------------------------------
    // Detection
    // -------------------------------------------------------------------------

    /**
     * (M2.40/M2.42) Whether this armed mine triggers on a unit that has just moved into its
     * detection zone at {@code speed}.
     *
     * <h2>A LOW roll triggers, and this was backwards until 2026-11-07</h2>
     * M2.40: "roll a single die. If the result is LESS THAN the unit's effective speed
     * (C2.451), the mine has triggered. If the result is GREATER THAN OR EQUAL TO the unit's
     * speed, the mine has been detected in time and avoided; it does not trigger on that
     * impulse."
     *
     * <p>The code read {@code diceRoll > speed}, which inverted the whole gradient: a ship
     * creeping at speed one set a mine off five times in six while one at speed five escaped
     * five times in six. It contradicted its own endpoint, since the same method then triggered
     * unconditionally at high speed, and it contradicted the rule's note that "either
     * nimbleness or cloaking may protect a SLOW ship from detonating every mine". It survived
     * from the first client/server commit because the tests asserted the same misreading, and
     * it was found in play by a Kzinti CL that ate a nuclear space mine at speed two on a roll
     * of six — a log line that disagreed with itself ("roll 6 &le; speed 2").
     *
     * <h2>No auto-detect threshold any more</h2>
     * There was a {@code speed >= 6} short circuit. It is gone, because with the comparison the
     * right way round the automatic case falls out of the arithmetic at SEVEN and above — every
     * die roll from one to six is less than seven. Seven is also what the rules say: the worked
     * example in (M2.47) notes "at speeds of seven or more the die roll would have been
     * automatic". The old constant made speed six automatic, which it is not: a six rolled
     * against speed six is an escape.
     *
     * <p>(M2.42) is the one special case left: "a die roll result of 'one' always results in the
     * triggering of an active mine, regardless of the speed of the triggering unit." Needed
     * because a unit at speed one would otherwise be untouchable — nothing is less than one.
     *
     * <p>The caller confirms the unit is inside the detection zone, moved this impulse, and is
     * an acceptable size class (M2.14) before calling this.
     *
     * @param speed    the unit's EFFECTIVE speed (C2.451), which folds in Erratic Maneuvers
     * @param diceRoll a pre-rolled 1d6 value (1–6)
     * @return true if the mine triggers
     */
    public boolean detectsUnit(int speed, int diceRoll) {
        if (!active)
            return false;
        if (diceRoll == 1)
            return true;            // M2.42
        return diceRoll < speed;    // M2.40
    }

    // -------------------------------------------------------------------------
    // Dummy / reveal
    // -------------------------------------------------------------------------

    public boolean isReal() {
        return isReal;
    }

    public boolean isRevealed() {
        return revealed;
    }

    /**
     * Mark this dummy tBomb as revealed (a unit entered range without explosion).
     * Has no effect on real tBombs.
     */
    public void reveal() {
        if (!isReal)
            revealed = true;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public Ship getLayingShip() {
        return layingShip;
    }

    public int getPlacedOnImpulse() {
        return placedOnImpulse;
    }

    public MineType getMineType() {
        return mineType;
    }

    // -------------------------------------------------------------------------
    // Factory methods for specific mine types
    // -------------------------------------------------------------------------
    /** T-Bomb placed by transporter (M3.22). Timer-based arming; M3.32 applies if placed adjacent. */
    public static SpaceMine createTBomb(Ship layingShip, int placedOnImpulse, boolean isReal, boolean placedAdjacent) {
        return new SpaceMine(MineType.T_BOMB, PlacementMethod.TRANSPORTER, layingShip, placedOnImpulse, isReal, placedAdjacent);
    }

    /** T-Bomb dropped from a shuttle bay (M2.1). Range-based arming (M2.34). */
    public static SpaceMine createDroppedTBomb(Ship layingShip, int placedOnImpulse, boolean isReal) {
        return new SpaceMine(MineType.T_BOMB, PlacementMethod.DROPPED, layingShip, placedOnImpulse, isReal, false);
    }

    /** NSM dropped from a shuttle bay (M2.1). Range-based arming (M2.34). Always real. */
    public static SpaceMine createDroppedNSM(Ship layingShip, int placedOnImpulse) {
        return new SpaceMine(MineType.NSM, PlacementMethod.DROPPED, layingShip, placedOnImpulse, true, false);
    }

    /**
     * An NSM that was already in place before the battle — SH47.48's, hidden under a counter
     * and found by searching it.
     *
     * <h2>No laying ship, and that is what arms it</h2>
     * It needs no new creation path and no "armed" flag. A DROPPED mine arms by M2.34 as soon
     * as the laying ship is more than one hex away, and {@code MineResolver} already reads a
     * null layer as a range of {@code Integer.MAX_VALUE} — "the layer is not nearby", which is
     * the right answer for a mine somebody left here a month ago. So it arms on the first sweep
     * after it reaches the map, which is exactly SH47.48: "the mines only roll for triggering
     * AFTER they have been revealed".
     *
     * <p>That one-impulse gap is the rule, not a rounding error. SH47.48 goes on: "a unit might
     * uncover a mine after moving into the hex and then use a emergency deceleration to cancel
     * further movement after that point and thereby avoid triggering the mine." The escape only
     * exists because the move that uncovers it does not itself set it off.
     */
    public static SpaceMine createPrePlacedNSM(com.sfb.properties.Location where,
                                               java.util.Collection<Integer> sizeClasses) {
        SpaceMine mine = new SpaceMine(MineType.NSM, PlacementMethod.DROPPED, null, 0, true, false);
        mine.setLocation(where);
        if (sizeClasses != null)
            mine.acceptedSizeClasses.addAll(sizeClasses);
        return mine;
    }

    // -------------------------------------------------------------------------
    // Saved game
    // -------------------------------------------------------------------------

    /**
     * A laid mine, completely enough to rebuild it.
     *
     * <h2>Why mines had to be saved at all</h2>
     * They were the last thing in the format that could be lost SILENTLY. A minefield never
     * reached {@code exportState}, so a player who spent the energy and the impulses to lay one,
     * then saved, came back to open space with nothing said — and the enemy sailed through it.
     * Every other loss in a save either survives or reports itself.
     *
     * <h2>What is saved and what is derived</h2>
     * Damage is not here: a mine's warhead is a property of its TYPE (M2.0), so the type carries
     * it. What travels is how it was laid, when, whether it is armed, and whether a dummy has
     * been found out.
     *
     * <p>{@code isReal} is the dummy secret (M3.224) and is saved as it stands. That is safe
     * because a save file lives on the server and is never a client's view — {@code MineDto}
     * deliberately exposes only {@code active} and {@code revealed}, and restoring the truth
     * here is what lets a reloaded dummy go on fooling people exactly as far as it had.
     *
     * <p>The LAYING SHIP is a reference to another unit and is written as a name, like every
     * other saved reference. It may legitimately resolve to nothing: a mine outlives the ship
     * that laid it, and {@code MineResolver} already reads a null layer as "the layer is not
     * nearby", which is the right answer when it is dead.
     */
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("mineType", mineType.name());
        state.put("placementMethod", placementMethod.name());
        state.put("isReal", isReal);
        state.put("placedOnImpulse", placedOnImpulse);
        state.put("placedAdjacent", placedAdjacent);
        state.put("active", active);
        state.put("revealed", revealed);
        // (M2.14) Secret like isReal, and saved like it: the file is server-side and MineDto
        // never carries it. An absent key restores an empty set, which M2.141 defines as
        // "accepts all size classes" — so an older save reads correctly rather than producing
        // a mine that ignores everything.
        state.put("acceptedSizeClasses", new java.util.ArrayList<>(acceptedSizeClasses));
        state.put("layingShip", layingShip == null ? null : layingShip.getName());
        if (getLocation() != null) {
            state.put("x", getLocation().getX());
            state.put("y", getLocation().getY());
        }
        return state;
    }

    /**
     * Rebuild a laid mine from a save.
     *
     * CONSTRUCTED rather than overlaid, because everything that identifies a mine — its type,
     * how it was laid, whether it is real, when it was placed, and whether it went down next to
     * its layer — is final on the class. The same shape the wild weasel needs for its ship.
     *
     * @param layingShip the resolved layer, or null if it did not survive the battle
     * @return the mine, or null if the save names a type this build does not have
     */
    public static SpaceMine fromState(java.util.Map<String, Object> state, Ship layingShip) {
        if (state == null)
            return null;
        MineType type;
        PlacementMethod method;
        try {
            type = MineType.valueOf(com.sfb.systemgroups.StateIo.text(state, "mineType"));
            method = PlacementMethod.valueOf(
                    com.sfb.systemgroups.StateIo.text(state, "placementMethod"));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
        SpaceMine mine = new SpaceMine(type, method, layingShip,
                com.sfb.systemgroups.StateIo.integer(state, "placedOnImpulse", 0),
                com.sfb.systemgroups.StateIo.flag(state, "isReal", true),
                com.sfb.systemgroups.StateIo.flag(state, "placedAdjacent", false));
        mine.active   = com.sfb.systemgroups.StateIo.flag(state, "active", false);
        mine.revealed = com.sfb.systemgroups.StateIo.flag(state, "revealed", false);
        if (state.get("acceptedSizeClasses") instanceof java.util.List<?> classes)
            for (Object c : classes)
                if (c instanceof Number n)
                    mine.acceptedSizeClasses.add(n.intValue());
        if (state.get("x") instanceof Number x && state.get("y") instanceof Number y)
            mine.setLocation(new com.sfb.properties.Location(x.intValue(), y.intValue()));
        return mine;
    }
}
