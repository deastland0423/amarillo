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

    public enum MineType {
        T_BOMB("tBomb", 10),
        NSM("NSM", 25);

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
     * Check whether this active tBomb detects a unit moving at {@code speed}.
     * Rolls 1d6 for slow units. Returns true if the unit is detected.
     *
     * <p>
     * The caller is responsible for confirming the unit is within range 1
     * before calling this method.
     *
     * @param speed    The speed of the unit being checked.
     * @param diceRoll A pre-rolled 1d6 value (1–6). Ignored when speed &ge; 6.
     * @return true if the tBomb detects the unit and should detonate.
     */
    public boolean detectsUnit(int speed, int diceRoll) {
        if (!active)
            return false;
        if (speed >= 6)
            return true;
        return diceRoll > speed;
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
        if (state.get("x") instanceof Number x && state.get("y") instanceof Number y)
            mine.setLocation(new com.sfb.properties.Location(x.intValue(), y.intValue()));
        return mine;
    }
}
