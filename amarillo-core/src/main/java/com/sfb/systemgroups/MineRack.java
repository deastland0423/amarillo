package com.sfb.systemgroups;

/**
 * (M9.11) One mine storage box on a minesweeper, minelayer or converted hull.
 *
 * <blockquote>M9.11: "Minesweepers and minelayers are equipped with mine storage boxes (mine
 * racks) on their SSD sheets. <b>Each such box holds four large mines. Two small mines can be
 * substituted for one large one.</b> ... The mines held in mine racks do not come with free
 * dummy mines as T-bombs (M3.224) do."</blockquote>
 *
 * <h2>Capacity is in LARGE-mine slots, which is what makes the substitution work</h2>
 * A box is {@value #SLOTS} slots. A large mine (an NSM) fills one; two small mines (T-bombs)
 * share one. So a full box is four NSMs, or eight T-bombs, or any mix in between — three NSMs
 * and two T-bombs is legal and full. Counting mines instead of slots cannot express that, which
 * is why {@link #spaceUsed()} is the figure the capacity check reads and the mine counts are
 * derived from it rather than the other way round.
 *
 * <p>Odd numbers of small mines round UP to a whole slot: five T-bombs occupy three slots, not
 * two and a half. A half-filled slot is a physical half-empty cradle, not spare capacity — the
 * sixth T-bomb goes into the slot the fifth opened, and the rule gives no way to put a large
 * mine in half a slot.
 *
 * <h2>What this class deliberately does NOT have</h2>
 * <b>Reloads.</b> M9.12 is explicit: "The mines carried by minesweepers are those in the mine
 * racks. <b>There are no 'reloads' for these mine racks.</b>" {@link DroneRack} is the pattern
 * this class copies for capacity-that-varies-by-content, and it has a reload list; copying that
 * part would invent a rule. A rack that is empty is empty for the rest of the scenario.
 *
 * <p><b>Free dummies.</b> M3.224 gives a T-bomb allocation free dummy mines. M9.11's last
 * sentence removes that for rack mines in so many words. So nothing here mints a dummy, and a
 * dummy loaded into a rack had to be bought.
 *
 * <h2>The rate is NOT here</h2>
 * How often a rack may be fired is (M9.21) and depends on the SHIP, not the box: a minesweeper
 * or minelayer lays one mine per rack per turn, while anything else — including an S7.0
 * conversion, which M9.16 says is "not considered a minesweeper for any purpose" — drops through
 * the shuttle hatch at one per bay per turn however many racks it has. So this class tracks WHEN
 * it was last fired ({@link #getLastLaidTurn()}) and lets {@code MineResolver} decide whether
 * that matters, exactly as it decides whether a bay is needed at all.
 */
public class MineRack {

    /** (M9.11) "Each such box holds four large mines." */
    public static final int SLOTS = 4;

    /** (M9.11) "Two small mines can be substituted for one large one." */
    public static final int SMALL_MINES_PER_SLOT = 2;

    private int largeMines;      // NSMs
    private int smallMines;      // T-bombs, real
    private int smallDummyMines; // T-bombs, dummy (M9.11: never free here)
    private boolean destroyed;
    private int lastLaidTurn = -1;
    private int outOfServiceTurn = -1;

    /** An empty rack, which is how every hull in the data starts (M2.76). */
    public MineRack() {
    }

    // ---------------------------------------------------------------- capacity (M9.11)

    /** Slots filled, counting two small mines to the slot and rounding a lone small one up. */
    public int spaceUsed() {
        int smalls = smallMines + smallDummyMines;
        return largeMines + (smalls + SMALL_MINES_PER_SLOT - 1) / SMALL_MINES_PER_SLOT;
    }

    /** Slots still free; 0 on a destroyed rack, which can hold nothing. */
    public int spaceFree() {
        return destroyed ? 0 : Math.max(0, SLOTS - spaceUsed());
    }

    /** Whether one more large mine would fit. */
    public boolean hasRoomForLarge() {
        return spaceFree() >= 1;
    }

    /**
     * Whether one more small mine would fit — true either when a slot is free OR when a slot
     * already holds a single small mine with its partner cradle empty.
     */
    public boolean hasRoomForSmall() {
        if (destroyed)
            return false;
        boolean halfSlotOpen = (smallMines + smallDummyMines) % SMALL_MINES_PER_SLOT != 0;
        return halfSlotOpen || spaceFree() >= 1;
    }

    // ---------------------------------------------------------------- contents

    public int getLargeMines() {
        return largeMines;
    }

    public int getSmallMines() {
        return smallMines;
    }

    public int getSmallDummyMines() {
        return smallDummyMines;
    }

    /** Every mine aboard, real or dummy, large or small — what M9.13 destroys with the rack. */
    public int getTotalMines() {
        return largeMines + smallMines + smallDummyMines;
    }

    public boolean isEmpty() {
        return getTotalMines() == 0;
    }

    /**
     * Load large mines, up to what fits. Returns how many went in, which may be fewer than
     * asked for and is never negative.
     *
     * <p>This is the SETUP and purchasing door, not M9.19's one-mine-per-turn transfer: a rack
     * is filled before the scenario from what the force total bought. M9.19's in-play load is a
     * separate, slower action and is not built.
     */
    public int loadLarge(int count) {
        int room = Math.min(Math.max(0, count), spaceFree());
        largeMines += room;
        return room;
    }

    /** Load small mines (real T-bombs), up to what fits. */
    public int loadSmall(int count) {
        return loadSmallInternal(count, false);
    }

    /** Load small DUMMY mines. M9.11 gives no free ones, so these were paid for. */
    public int loadSmallDummy(int count) {
        return loadSmallInternal(count, true);
    }

    private int loadSmallInternal(int count, boolean dummy) {
        int loaded = 0;
        for (int i = 0; i < Math.max(0, count); i++) {
            if (!hasRoomForSmall())
                break;
            if (dummy)
                smallDummyMines++;
            else
                smallMines++;
            loaded++;
        }
        return loaded;
    }

    // ---------------------------------------------------------------- laying

    /** Whether this rack holds a mine of the given kind to lay. */
    public boolean has(boolean large, boolean dummy) {
        if (destroyed)
            return false;
        if (large)
            return largeMines > 0; // M10.112: there is no large dummy, nor a large PA mine
        return dummy ? smallDummyMines > 0 : smallMines > 0;
    }

    /**
     * Take one mine out to lay it, recording the turn so (M9.21)'s one-per-rack-per-turn rate
     * can be checked by the caller. Returns false and changes nothing if none of that kind is
     * aboard.
     */
    public boolean takeOne(boolean large, boolean dummy, int turn) {
        if (!has(large, dummy))
            return false;
        if (large)
            largeMines--;
        else if (dummy)
            smallDummyMines--;
        else
            smallMines--;
        lastLaidTurn = turn;
        return true;
    }

    /** (M9.21) The turn a mine was last laid from this rack, or -1 if none has been. */
    public int getLastLaidTurn() {
        return lastLaidTurn;
    }

    /** (M9.21) Whether this rack has already been fired on the given turn. */
    public boolean laidThisTurn(int turn) {
        return lastLaidTurn == turn;
    }

    // ---------------------------------------------------------------- out of service (M9.19)

    /**
     * (M9.19) Mark the rack out of service for a whole turn, which loading or unloading a mine
     * in play costs: "The rack must be taken out of service for the entire turn for this to be
     * done."
     * <p>
     * Exposed before its caller exists, because the state belongs to the rack and the one-mine
     * transfer that spends it is a separate slice. Nothing sets this today.
     */
    public void takeOutOfService(int turn) {
        this.outOfServiceTurn = turn;
    }

    /** (M9.19) Whether this rack is unusable for the given turn. */
    public boolean isOutOfService(int turn) {
        return outOfServiceTurn == turn;
    }

    // ---------------------------------------------------------------- damage (M9.13)

    /**
     * (M9.13) Destroy the rack and everything in it.
     *
     * <blockquote>M9.13: "Cargo hits may be scored on mine rack boxes and must be scored on them
     * if no other cargo boxes are available. Shuttle hits must be scored on shuttle or mine rack
     * boxes if any of either type exist. See Annex #7E. <b>The mines on a destroyed rack are
     * also destroyed (but will not detonate).</b>"</blockquote>
     *
     * <p>That last clause is the one worth being deliberate about: a rack of four NSMs taking a
     * cargo hit does not set off four nuclear mines inside the ship. The mines are simply gone,
     * so this zeroes them rather than routing anything to {@code ExplosionResolver}.
     */
    public void destroy() {
        this.destroyed = true;
        this.largeMines = 0;
        this.smallMines = 0;
        this.smallDummyMines = 0;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /** Whether this rack can be fired at all, ignoring the ship-level rate (M9.21). */
    public boolean isUsable(int turn) {
        return !destroyed && !isOutOfService(turn) && !isEmpty();
    }
}
