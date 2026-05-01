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

    public Ship getParentShip() { return parentShip; }

    /** Begin the 4-impulse explosion period (J3.211). */
    public void startExplosion(int currentImpulse) {
        exploding     = true;
        postExplosion = false;
        explosionEndsAtImpulse = currentImpulse + 3;
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
}
