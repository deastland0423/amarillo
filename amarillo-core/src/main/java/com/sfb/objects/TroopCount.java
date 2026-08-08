package com.sfb.objects;

/**
 * Counts boarding parties by quality tier on a single side.
 *
 * <p>Commandos are removed last when casualties are applied; normal parties
 * are removed first. A {@code poor} tier may be added later without
 * changing the existing interface.
 */
public class TroopCount {

    public int normal;
    public int commandos;

    public TroopCount() {
        this(0, 0);
    }

    public TroopCount(int normal, int commandos) {
        this.normal    = normal;
        this.commandos = commandos;
    }

    /** Total boarding parties across all tiers. */
    public int total() {
        return normal + commandos;
    }

    public boolean isEmpty() {
        return total() == 0;
    }

    /**
     * Remove up to {@code count} boarding parties, normal first then commandos.
     * Returns the number actually removed.
     */
    public int removeCasualties(int count) {
        int removed = 0;
        int fromNormal = Math.min(count, normal);
        normal  -= fromNormal;
        removed += fromNormal;
        int fromCommandos = Math.min(count - removed, commandos);
        commandos -= fromCommandos;
        removed   += fromCommandos;
        return removed;
    }

    /** Dominant quality for H&R table lookup: COMMANDO if any commandos present, else NORMAL. */
    public com.sfb.properties.BoardingPartyQuality dominantQuality() {
        return commandos > 0
                ? com.sfb.properties.BoardingPartyQuality.COMMANDO
                : com.sfb.properties.BoardingPartyQuality.NORMAL;
    }

    @Override
    public String toString() {
        if (commandos == 0) return normal + " BP(s)";
        return normal + " BP(s) + " + commandos + " commando(s)";
    }
}
