package com.sfb.properties;

/**
 * Whether a ship is a dedicated mine-warfare vessel (M8.0, M9.0), which decides how fast it can
 * lay the mines in its racks.
 *
 * <h2>The rule this exists for</h2>
 * M9.21: "Ships can lay one mine from each mine rack during each turn. <b>Only minesweepers and
 * minelayers use these rates</b> to lay mines. Other ships converted (S7.0) to carry mine racks
 * drop mines through the shuttle hatch at the rate of one per turn per shuttle bay, regardless of
 * the number of racks in the bay."
 * <p>
 * So a mine rack is not one thing: on a minelayer it is a launcher in its own right, bypassing
 * the shuttle hatch entirely — no quarter-turn spacing (M2.11), no M2.111 conflict with launching
 * a shuttle — while on anything else it is only an inventory and the bay is the bottleneck. The
 * two roles are listed together everywhere the RATE is concerned, which is why they share a
 * field.
 *
 * <h2>Why a role and not a pair of booleans</h2>
 * A ship is one or the other, never both, and nothing should be able to write that down — the
 * same reasoning as {@link CarrierClass}. The rules do ask about them separately, and a single
 * field still answers either question: M9.17 exempts <b>minesweepers</b> specifically from the
 * Commander's Option percentage (M6.31), and M9.16 is emphatic that an S7.0 conversion is "not
 * considered a minesweeper <b>for any purpose</b>".
 *
 * <h2>What MINESWEEPER does NOT yet mean</h2>
 * Minesweeping itself (M8.0) is unimplemented — there is no citation of it anywhere in the
 * engine. So declaring this role today confers the faster LAYING rate and nothing else: such a
 * ship cannot actually sweep. Both values exist regardless because M9.21 names the two together
 * and splitting them would mean a second data migration when sweeping arrives. Recorded here
 * rather than left as a surprise, because a flag whose name promises more than it delivers is how
 * {@code requiresEscort} came to carry two consequences and cover only one.
 */
public enum MineRole {

    /** Not a mine-warfare vessel. Lays from a rack at the bay rate, if it has racks at all. */
    NONE,

    /** M8.0: a minesweeper. Carries a few mines to patch friendly fields (M9.12). */
    MINESWEEPER,

    /** R1.12: a dedicated minelayer, the specially equipped freighters that lay real fields. */
    MINELAYER;

    /**
     * M9.21: whether this ship lays one mine per RACK per turn, rather than one per shuttle bay.
     * <p>
     * True for both roles and false for everything else. This is the question the rack code asks,
     * so it lives next to the rule rather than being re-derived from the enum at each call site.
     */
    public boolean laysOneMinePerRack() {
        return this == MINESWEEPER || this == MINELAYER;
    }

    /** Lenient parse for ship files; anything unrecognised is NONE, as with CarrierClass. */
    public static MineRole from(Object raw) {
        if (raw instanceof MineRole r)
            return r;
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return MineRole.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
        return NONE;
    }
}
