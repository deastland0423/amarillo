package com.sfb.systemgroups;

import java.util.Map;

import com.sfb.objects.Unit;

/**
 * DERFACS — Direct Energy Range Finding and Computation System.
 *
 * Ship-level targeting enhancement for disruptors. When functional, disruptor
 * fire uses the DERFACS hit chart instead of the standard hit chart. Does not
 * affect damage. Can be permanently damaged by a Hit and Run raid but cannot
 * be damaged by normal weapons fire.
 */
public class DERFACS implements Systems {

    private boolean damaged = false;
    private Unit owningUnit = null;

    public DERFACS() {}

    public DERFACS(Unit owner) {
        this.owningUnit = owner;
    }

    // -------------------------------------------------------------------------
    // Systems interface
    // -------------------------------------------------------------------------

    @Override
    public void init(Map<String, Object> values) {}

    @Override
    public int fetchOriginalTotalBoxes() { return 1; }

    @Override
    public int fetchRemainingTotalBoxes() { return damaged ? 0 : 1; }

    @Override
    public void cleanUp() {}

    @Override
    public Unit fetchOwningUnit() { return owningUnit; }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    public boolean isFunctional() { return !damaged; }

    /** Permanently damages the DERFACS unit (e.g. via Hit and Run raid). */
    public void damage() { this.damaged = true; }
}
