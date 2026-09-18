package com.sfb.properties;

/**
 * The ECM bearing on one unit's action against another, split by the five sources that
 * D6.314 names.
 * <p>
 * Kept separate rather than pre-summed for two reasons. D6.3146 exempts three of the five
 * between friendly units, so a rule genuinely needs to tell them apart — and until this
 * existed, the same sum was assembled by hand in four places that had already drifted
 * apart from one another.
 * <p>
 * <b>These are POINTS, not the shift.</b> The die-roll modifier is the D6.34 Step 5 chart
 * — the square root with fractions dropped — so 3 points is still only +1, and it takes 4
 * to reach +2. Convert once, at the end, with {@code Game.netEcmShift}. ECCM is subtracted
 * from the point total BEFORE that conversion (D6.34 Step 3), which is why enough ECCM
 * removes the roll altogether rather than merely improving it.
 *
 * @param generated D6.3141 — paid for out of the unit's own power at allocation
 * @param builtIn   D6.3142 — built into the unit and received automatically: Orion stealth
 *                  (G15.8), a fighter's two points (J4.47)
 * @param natural   D6.3143 — from natural causes: asteroids and rings (P3.33/P2.223), and
 *                  in due course Erratic Maneuvers (C10.0), atmospheres (P2.54) and small
 *                  target modifiers (E1.7), none of which are modelled yet. Anything added
 *                  there belongs in THIS field, because it is the one a friendly unit
 *                  cannot ignore.
 * @param lent      D6.3144 — received from lending: scout channels (G24.21), ECM drones
 *                  (FD9.0), EW fighters (R1.F7), and Wild Weasels (J3.23)
 * @param weasel    J3.23 Wild Weasel ECM, held apart from {@code lent} ONLY because the
 *                  code disagrees with itself about it: seekers and the D6.37 systems have
 *                  always counted it, direct fire never has. D6.3144 lists weasels among
 *                  the lending sources, which suggests it should fold into {@code lent} and
 *                  count everywhere — unresolved pending J3.23.
 * @param offensive D6.3145 — "negative ECM" a unit receives from one enemy scout (G24.219).
 *                  Belongs to the ACTOR, not the target: it degrades that ship's own
 *                  systems, so it applies to whatever it points them at, friend or foe.
 */
public record EwBreakdown(int generated, int builtIn, int natural, int lent, int weasel,
        int offensive) {

    public static final EwBreakdown NONE = new EwBreakdown(0, 0, 0, 0, 0, 0);

    /** Every source, for an action against an enemy. */
    public int total() {
        return generated + builtIn + natural + lent + weasel + offensive;
    }

    /**
     * What an action against a FRIENDLY unit has to burn through (D6.3146): its generated,
     * built-in and lent ECM are ignored, but natural sources and offensive ECM are not.
     */
    public int totalFriendly() {
        return natural + offensive;
    }

    /** Whichever of the two the target calls for. */
    public int totalAgainst(boolean friendly) {
        return friendly ? totalFriendly() : total();
    }

    /** A one-line account for the combat log, naming only the sources actually present. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        append(sb, generated, "generated");
        append(sb, builtIn, "built-in");
        append(sb, natural, "natural");
        append(sb, lent, "lent");
        append(sb, weasel, "weasel");
        append(sb, offensive, "offensive");
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static void append(StringBuilder sb, int points, String label) {
        if (points == 0)
            return;
        if (sb.length() > 0)
            sb.append(" + ");
        sb.append(points).append(' ').append(label);
    }
}
