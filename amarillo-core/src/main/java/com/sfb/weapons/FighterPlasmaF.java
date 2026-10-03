package com.sfb.weapons;

import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
import com.sfb.properties.PlasmaType;

/**
 * A type-F plasma torpedo as carried by a fighter — the Romulan Gladiator, and the Gorn and ISC
 * fighters J4.861 names alongside it.
 * <p>
 * Unlike every other fighter heavy weapon, this one is NOT built from the
 * {@link FighterDisruptor} charge pattern. A plasma torpedo is a seeking weapon: launching it
 * puts a {@link com.sfb.objects.PlasmaTorpedo} on the map with its own speed, endurance,
 * strength decay and lock-on, and all of that already exists in {@link PlasmaLauncher}. J4.27
 * says the fighter's torpedo "is subject to all of the above restrictions", and the owner's
 * reading is the same: it is an ordinary type-F, carried by something small. So this extends the
 * launcher and takes away the three things a fighter may not do, rather than reimplementing a
 * seeking weapon beside it.
 *
 * <h2>What J4.86x takes away</h2>
 * <ul>
 *   <li><b>No pseudo torpedo.</b> J4.865: "No fighter has pseudo plasma torpedoes (FP6.14)."
 *       Flat and without exception, so {@link #canLaunchPseudo()} is false for good rather than
 *       merely starting spent — a launcher that reported one available would offer the player a
 *       bluff the rules do not allow.</li>
 *   <li><b>No plasma bolt.</b> J4.864: "No fighter can fire a plasma bolt (FP8.23)." This is the
 *       one the owner did not mention and the one most easily missed, because bolting is the
 *       inherited {@link #fire(int)} path: a fighter would otherwise have had a direct-fire
 *       option the rule denies it.</li>
 *   <li><b>No self-rearming.</b> J4.861: "These are rearmed by this procedure; the fighters
 *       cannot rearm plasma torpedoes themselves." A fighter has no reactor to arm a torpedo
 *       with, so the energy arming path is barred and the torpedo arrives loaded from the box
 *       that holds the fighter.</li>
 * </ul>
 *
 * <h2>What the carrier does instead</h2>
 * J4.862 gives each {@code +}-marked box "a storage facility (not shown on the SSD) for a single
 * type-F plasma torpedo", and J4.863 makes reloading "a single deck crew action". That is the
 * plasma twin of the ready rack, and it is NOT built yet: {@code FighterArming} enumerates
 * weapon types explicitly and knows nothing of this one (nor, as it happens, of
 * {@link FighterPhoton}), so a deck crew currently finds no work to do on a Gladiator. The
 * weapon is correct in flight; what a carrier does between sorties is the next slice.
 */
public class FighterPlasmaF extends PlasmaLauncher {

    /**
     * Always type F. J4.27 is explicit that "standard fighters can only carry" the F, and a
     * launcher type is the max size it can hold — so there is nothing to configure and nothing
     * a ship file could get wrong.
     */
    public FighterPlasmaF() {
        super(PlasmaType.F);
    }

    /**
     * J4.865: no fighter has a pseudo torpedo. Never, rather than once-and-spent.
     * <p>
     * Both the capability and the launch are closed, because the UI asks the first and the game
     * calls the second: an answer of "yes" here would put a button on the screen.
     */
    @Override
    public boolean canLaunchPseudo() {
        return false;
    }

    @Override
    public com.sfb.objects.PlasmaTorpedo launchPseudo() {
        return null;
    }

    /**
     * J4.864: no fighter can fire a plasma bolt (FP8.23).
     * <p>
     * {@code fire(int)} is the BOLT path on a plasma launcher, not the torpedo launch — the
     * torpedo goes out through {@code launch()} as a seeking weapon. So this is the whole of
     * the bolt option, and closing it is what stops a Gladiator having a direct-fire attack the
     * rules do not give it.
     */
    @Override
    public int fire(int range) throws WeaponUnarmedException, TargetOutOfRangeException {
        throw new WeaponUnarmedException(
                "A fighter cannot fire a plasma bolt (J4.864)");
    }

    /**
     * J4.861: the fighter cannot rearm its own torpedo. Loading one is what its box does for
     * it (J4.862/J4.863), so this is how the torpedo gets there — the weapon arrives armed,
     * standard, with no energy spent anywhere.
     */
    public void loadTorpedo() {
        setArmedState();
    }

    /** True if the fighter is carrying a torpedo ready to launch. */
    public boolean isLoaded() {
        return isArmed();
    }
}
