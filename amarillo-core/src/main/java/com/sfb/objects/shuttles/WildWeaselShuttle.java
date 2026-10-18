package com.sfb.objects.shuttles;

import com.sfb.objects.*;

/**
 * An active Wild Weasel decoy on the map (J3.0).
 *
 * State machine:
 *   ACTIVE       — full ECM, new seekers redirect to WW
 *   EXPLODING    — 4 impulses after first seeker hit; full ECM continues (J3.2111), new seekers still redirect
 *   POST_EXPLOSION — ionized radiation pocket; no ECM (J3.2123), new seekers ignore WW (J3.2122);
 *                    existing seekers continue toward WW hex until they arrive/expire or ship voids it
 */
public class WildWeaselShuttle extends Shuttle {

    private final Ship parentShip;

    private boolean exploding      = false;
    private boolean postExplosion  = false;
    private int explosionEndsAtImpulse = -1;

    public WildWeaselShuttle(Ship parentShip) {
        this.parentShip = parentShip;
        setHull(6);
        setMaxSpeed(6);
    }

    /**
     * Charge a specific shuttle as a weasel, keeping what it actually is.
     * <p>
     * J3.18 lets any non-fighter shuttle serve, and they are not alike: an HTS has twice
     * the hull of an admin shuttle. The older constructor assumed an admin shuttle's six
     * hull and six speed, which was the only shape that could ever be charged. Keeping the
     * base's catalogue type also lets the weasel be NAMED for what it is, so it reads like
     * any other shuttle of that type rather than announcing itself.
     */
    public WildWeaselShuttle(Ship parentShip, Shuttle base) {
        this.parentShip = parentShip;
        setHull(base.getHull());
        setMaxSpeed(base.getMaxSpeed());
        setCatalogType(base.getCatalogType());
    }

    public Ship getParentShip() { return parentShip; }

    /** Begin the 4-impulse explosion period (J3.211). */
    public void startExplosion(int currentImpulse) {
        exploding     = true;
        postExplosion = false;
        explosionEndsAtImpulse = currentImpulse + 3;
        // J3.21: the wild weasel ceases to move at the instant it is destroyed
        setSpeed(0);
        setCurrentSpeed(0);
    }

    /** Transition from explosion to post-explosion ionized radiation (J3.212). */
    public void startPostExplosion() {
        exploding     = false;
        postExplosion = true;
    }

    public boolean isExploding()     { return exploding; }
    public boolean isPostExplosion() { return postExplosion; }

    /** True once the 4-impulse explosion duration has elapsed. */
    public boolean isExplosionOver(int currentImpulse) {
        return exploding && currentImpulse > explosionEndsAtImpulse;
    }

    public int getExplosionEndsAtImpulse() { return explosionEndsAtImpulse; }

    /** WW moves automatically on its pre-set course — never blocks movement phase. */
    @Override public boolean isPlayerControlled()   { return false; }
    @Override public boolean canBecomeSuicide()     { return false; }
    @Override public boolean canBecomeScatterPack() { return false; }
    @Override public boolean canBecomeWildWeasel()  { return false; }

    /**
     * Always a weasel, whatever its charge counter says.
     * <p>
     * The base implementation answers from {@code wwChargeCount}, which is right for the craft
     * being PREPARED as a weasel - still an admin shuttle of the same type, with nothing but
     * the counter to distinguish it (J3.18). But this class is the weasel itself, and it is
     * built from the base shuttle without copying that counter, so it was answering "no
     * special role" while being, by class, the most special-roled craft there is.
     * <p>
     * No live path reached it: a WildWeaselShuttle is only ever constructed at launch and goes
     * straight onto the map, and all three callers of specialRole() look at craft in a BAY. So
     * this changes no behaviour today - it removes a trap. Every role-based gate (the ordinary
     * launch's refusal, the balcony's J1.534 bar, the hangar's own listing) would have treated
     * a bayed weasel as an ordinary shuttle, and the first thing to put one in a bay will be
     * something like a mid-game reload restoring state.
     */
    @Override
    public String specialRole() {
        return "Wild Weasel";
    }

    /** Unmanned: a decoy flies empty (J3.0). Revealed by identification (G4.233). */
    @Override
    public boolean isManned() {
        return false;
    }

    /** J3.0: an admin shuttle in this role presents as the role, not as an admin shuttle. */
    @Override
    public String dtoType() {
        return "wildweasel";
    }

    // -------------------------------------------------------------------------
    // Saved game (see Shuttle.exportState)
    // -------------------------------------------------------------------------

    /** What {@link #saveRole()} reports, and what the loader rebuilds a weasel from. */
    public static final String SAVE_ROLE = "WILD_WEASEL";

    @Override
    public String saveRole() {
        return SAVE_ROLE;
    }

    /**
     * Where this weasel is in its life (J3.2111, J3.2123).
     *
     * A weasel is a timed thing: it charges, explodes, and then spends a period as a spent
     * decoy that still occupies a hex. {@code explosionEndsAtImpulse} is what decides when the
     * middle phase ends, so dropping it would either end the explosion instantly or never.
     *
     * <p>The parent ship is NOT here. It is final on this class, so a restored weasel has to be
     * constructed with it — which is why Game.applyState resolves the name and builds the weasel
     * rather than overlaying state onto one.
     */
    @Override
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = super.exportState();
        // Its OWN parent, not the inherited parentShipName: that field is set at launch, so a
        // weasel built any other way would save a null and come back belonging to nobody.
        state.put("parentShipName", parentShip == null ? null : parentShip.getName());
        state.put("exploding", exploding);
        state.put("postExplosion", postExplosion);
        state.put("explosionEndsAtImpulse", explosionEndsAtImpulse);
        return state;
    }

    @Override
    public void applyState(java.util.Map<String, Object> state) {
        super.applyState(state);
        if (state == null)
            return;
        exploding = com.sfb.systemgroups.StateIo.flag(state, "exploding", exploding);
        postExplosion = com.sfb.systemgroups.StateIo.flag(state, "postExplosion", postExplosion);
        explosionEndsAtImpulse = com.sfb.systemgroups.StateIo.integer(state,
                "explosionEndsAtImpulse", explosionEndsAtImpulse);
    }
}
