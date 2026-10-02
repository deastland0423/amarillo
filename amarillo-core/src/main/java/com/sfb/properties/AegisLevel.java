package com.sfb.properties;

/**
 * Aegis fire control (D13.0): the high-speed tracking system that lets a ship fire at small
 * targets several times in one impulse, judging each shot before taking the next.
 * <p>
 * Three states rather than a pair of booleans, because D13.423 says outright that "a ship cannot
 * have both limited and full aegis" and D13.25 that "each ship can have only one aegis fire
 * control system". "Limited and full" is not a thing a ship can be, so nothing should be able to
 * write it down.
 * <p>
 * <b>This is a property of the SHIP, never of being an escort.</b> D13.0: "This system was almost
 * never used on ships other than carrier escorts (the Klingon D5 being an exception)." Deriving
 * it from {@code isEscort} would be right for most hulls and silently wrong for the ones that
 * matter — and {@code isEscort} answers a different question anyway (S8.311 fleet legality).
 * <p>
 * It is also not resolved from the year, which is the other tempting shortcut. D13.24 puts full
 * aegis at 1 Jan Y175 for every empire and D13.0 says most escorts exist in a pre-Y175 limited
 * version and a Y175-and-later full one — but those are SEPARATE HULLS carrying their own service
 * years and BPVs. The Kzinti EFF (Y168, limited) and AFF (Y175, full) are two ships, not one ship
 * with an era table. Unlike a fighter complement, nothing here changes under a hull as the
 * calendar moves.
 */
public enum AegisLevel {

    /** No aegis fitted — the overwhelming majority of hulls. */
    NONE(0),

    /** D13.4: two firings per impulse, and no seeker identification (D13.412). */
    LIMITED(2),

    /** D13.14: four firings per impulse, plus D13.3 seeker identification. */
    FULL(4);

    private final int firings;

    AegisLevel(int firings) {
        this.firings = firings;
    }

    /**
     * Firings permitted in one impulse (D13.14, D13.411).
     * <p>
     * The FIRST of them coincides with all ordinary fire, so only {@code firings() - 1} are
     * extra. D13.144 lines the two systems up: limited aegis's two firings are the first and
     * second of full aegis's four.
     */
    public int firings() {
        return firings;
    }

    /** Extra firings beyond the one every ship already gets: 0, 1 or 3. */
    public int extraFirings() {
        return Math.max(0, firings - 1);
    }

    /** D13.35: only a full system can identify seeking weapons; D13.412 denies it to limited. */
    public boolean canIdentifySeekers() {
        return this == FULL;
    }

    public boolean isFitted() {
        return this != NONE;
    }

    /** Lenient parse for ship files; anything unrecognised is NONE, as CarrierClass does. */
    public static AegisLevel from(Object raw) {
        if (raw instanceof AegisLevel a)
            return a;
        if (raw == null)
            return NONE;
        try {
            return valueOf(raw.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
