package com.sfb.objects;

/**
 * There are two types of space mine: tBombs and Nuclear Space Mines (NSMs).
 * 
 * <h1>Transporter Bombs (tBombs)</h1>
 * A tBomb (transporter bomb) is a mine placed on the map via transporter.
 * 
 * A tBomb may also be dropped out the shuttle bay, but this is not yet
 * implemented.
 *
 * <h3>Activation</h3>
 * A tBomb starts inactive. It becomes permanently active once BOTH of the
 * following conditions are true simultaneously:
 * <ul>
 * <li>At least 2 impulses have elapsed since placement.</li>
 * <li>The laying ship is more than 1 hex away.</li>
 * </ul>
 * Once active it never deactivates, even if the laying ship returns to range.
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

    private final MineType mineType;
    private final boolean isReal;
    private final Ship layingShip;
    private final int placedOnImpulse;

    private boolean active = false;
    private boolean revealed = false; // true once a dummy has been exposed

    /** Minimal instance for client-side rendering only — no game logic. */
    public static SpaceMine forRendering() {
        return new SpaceMine(MineType.T_BOMB, null, 0, true);
    }

    public SpaceMine(MineType mineType, Ship layingShip, int placedOnImpulse, boolean isReal) {
        this.mineType = mineType;
        this.layingShip = layingShip;
        this.placedOnImpulse = placedOnImpulse;
        this.isReal = (mineType == MineType.NSM || isReal); // NSMs are always real, TBombs can be real or dummy
        this.name = mineType.label;
    }

    // -------------------------------------------------------------------------
    // Activation
    // -------------------------------------------------------------------------

    /**
     * Returns true if this tBomb is currently armed and will check for targets.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Attempt to activate this tBomb. Should be called each impulse while the
     * bomb is still inactive. Activates (permanently) if both conditions are met:
     * at least {@code impulsesElapsed} impulses have passed AND the laying ship
     * is more than 1 hex away.
     *
     * @param currentImpulse The current absolute impulse number.
     * @param layerRange     Hex distance from this tBomb to the laying ship.
     */
    public void tryActivate(int currentImpulse, int layerRange) {
        if (active)
            return;
        boolean timeElapsed = (currentImpulse - placedOnImpulse) >= 2;
        boolean layerGone = layerRange > 1;
        if (timeElapsed && layerGone) {
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
    public static SpaceMine createTBomb(Ship layingShip, int placedOnImpulse, boolean isReal) {
        return new SpaceMine(MineType.T_BOMB, layingShip, placedOnImpulse, isReal);
    }

    public static SpaceMine createNSMine(Ship layingShip, int placedOnImpulse) {
        return new SpaceMine(MineType.NSM, layingShip, placedOnImpulse, true);
    }
}
