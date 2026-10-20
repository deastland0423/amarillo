package com.sfb.systemgroups;

import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;

/**
 * FP10.31's plasma-D reload supply: <b>one pool for the whole unit</b>, which any plasma rack
 * may draw from.
 *
 * <h2>The rule</h2>
 * FP10.31: "Each plasma rack has four reload torpedoes. <b>A given rack can use reloads
 * nominally assigned to another rack.</b> ... When reloading, a rack can draw on any of the
 * torpedoes at the player's option." So a rack's reload sets say how much the SHIP carries, not
 * what that rack personally owns — exactly the distinction {@link ReloadStockpile} draws for
 * drone racks under FD2.422, and this class is deliberately its smaller twin.
 *
 * <h2>Why it is a count and not a list</h2>
 * {@link ReloadStockpile} holds {@code Drone} objects because drones differ — type, speed,
 * warhead. Type-Ds do not: FP9.18, "Pl-Ds, like other plasmas, do not have guidance options,
 * different speeds, or warhead modules as drones do." One type-D is any other, so the supply is
 * a number and a torpedo is minted at launch, which is already how {@link PlasmaRack} treats
 * what it holds.
 *
 * <h2>One record: what has been DRAWN</h2>
 * Capacity is not stored. It is summed off the racks on every call, so it answers correctly
 * when it changes underneath — and it does, twice:
 * <ul>
 *   <li>The <b>Y175 refit</b> (FP10.312) doubles every rack's sets, mid-build. A stored
 *       capacity would have been computed before that and stayed wrong.</li>
 *   <li>A <b>destroyed rack</b> takes its share of the supply with it, the same way
 *       {@code ReloadStockpile} re-reads its racks so "a rack destroyed mid-battle lowers the
 *       ship's reload capacity".</li>
 * </ul>
 * Holding both a capacity and a held-count would be two records of one fact, and the second one
 * to be updated would be the one that was wrong.
 *
 * <h2>What is NOT here</h2>
 * FP10.31 also makes these interchangeable with the type-Ds stored for FIGHTERS, which live in
 * {@link DroneStore} and {@link ReadyRack}. That transfer is not built; this is the rack supply
 * only, which mirrors the split the drone side already has between {@code ReloadStockpile} and
 * {@code DroneStore}. See BACKLOG.
 */
public final class PlasmaReloadStore {

    /**
     * Re-read for its racks on every call rather than captured, so a rack destroyed mid-battle
     * lowers the supply and the Y175 refit raises it.
     */
    private final Weapons weapons;

    /** The one thing worth recording: how many torpedoes have left the hold. */
    private int drawn = 0;

    private PlasmaReloadStore(Weapons weapons) {
        this.weapons = weapons;
    }

    public static PlasmaReloadStore of(Weapons weapons) {
        return new PlasmaReloadStore(weapons);
    }

    /**
     * FP10.31: four torpedoes per reload set, summed over the racks still able to contribute.
     *
     * <p>A destroyed rack contributes nothing. That is the same reading {@code ReloadStockpile}
     * takes, and it is why this is derived rather than stored.
     */
    public int capacity() {
        int total = 0;
        for (Weapon w : weapons.fetchAllWeapons())
            if (w instanceof PlasmaRack rack && rack.isFunctional())
                total += rack.getReloadSets() * PlasmaRack.TORPEDOES_PER_RELOAD_SET;
        return total;
    }

    /** Torpedoes still in the hold. Never negative, even if racks were destroyed after a draw. */
    public int held() {
        return Math.max(0, capacity() - drawn);
    }

    /**
     * Draw up to {@code wanted} torpedoes, returning how many were actually available.
     *
     * <p>The caller gets what there is rather than an exception, because two racks may each have
     * plotted a reload against a hold that cannot fill both (FD2.421 plots in advance, and the
     * pool is only counted when the reload completes). The first to complete is served.
     */
    public int take(int wanted) {
        int taken = Math.min(Math.max(0, wanted), held());
        drawn += taken;
        return taken;
    }

    /** Put torpedoes back — a reload that could not complete never spent them. */
    public void giveBack(int count) {
        drawn = Math.max(0, drawn - Math.max(0, count));
    }

    // -------------------------------------------------------------------------
    // Saved game
    // -------------------------------------------------------------------------

    /**
     * One number, because one number is the whole state. Capacity rebuilds itself from the racks
     * on the other side of the save, which is what makes a rack destroyed before the save come
     * back with its supply still missing.
     */
    public java.util.Map<String, Object> exportState() {
        java.util.Map<String, Object> state = new java.util.LinkedHashMap<>();
        state.put("drawn", drawn);
        return state;
    }

    public void applyState(java.util.Map<String, Object> state) {
        if (state == null)
            return;
        drawn = Math.max(0, StateIo.integer(state, "drawn", drawn));
    }
}
