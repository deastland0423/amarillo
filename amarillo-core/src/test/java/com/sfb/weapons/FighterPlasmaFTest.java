package com.sfb.weapons;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.PlasmaType;
import com.sfb.utilities.ArcUtils;

/**
 * A fighter's type-F plasma torpedo (J4.27, J4.86x) — the Romulan Gladiator's armament.
 * <p>
 * The design choice worth recording: this is a {@link PlasmaLauncher} with things taken AWAY,
 * not a new weapon built on the {@link FighterDisruptor} charge pattern that every other
 * fighter heavy weapon uses. A plasma torpedo is a seeking weapon — launching it puts a unit on
 * the map with its own speed, endurance, strength decay and lock-on — and all of that already
 * works. J4.27 says the fighter's torpedo "is subject to all of the above restrictions", so
 * inheriting is not a shortcut, it is the rule.
 * <p>
 * What the fighter may not do is three things, and these tests are mostly about those. Two came
 * from the owner (no pseudo, no self-rearming); the third, no plasma bolt, came out of J4.864
 * and is the one that would have been missed — bolting is the inherited {@code fire(int)} path,
 * so a fighter would have had a direct-fire attack the rules deny it.
 */
public class FighterPlasmaFTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    private static FighterPlasmaF loaded() {
        FighterPlasmaF w = new FighterPlasmaF();
        w.loadTorpedo();
        return w;
    }

    // ---------------------------------------------------------------- it is a type F

    /** J4.27: "standard fighters can only carry" the type F, so there is nothing to configure. */
    @Test
    public void itIsAlwaysATypeF() {
        assertEquals(PlasmaType.F, new FighterPlasmaF().getLauncherType());
    }

    /**
     * And it behaves as one. The torpedo's strength is the launcher's, not something restated
     * here — which is the whole value of inheriting rather than reimplementing.
     */
    @Test
    public void aLoadedLauncherHoldsARealTorpedo() {
        FighterPlasmaF w = loaded();

        assertTrue(w.isLoaded());
        assertTrue(w.isArmed());
        assertEquals(PlasmaType.F, w.getPlasmaType());
        assertTrue("a type-F warhead has strength", w.getArmedStrength() > 0);
    }

    /** Empty until the box loads it: a fighter cannot arm a torpedo itself (J4.861). */
    @Test
    public void itStartsEmpty() {
        FighterPlasmaF w = new FighterPlasmaF();

        assertFalse(w.isLoaded());
        assertFalse(w.isArmed());
        assertEquals("nothing to deliver", 0, w.getArmedStrength());
    }

    // ---------------------------------------------------------------- what J4.86x removes

    /** J4.865: "No fighter has pseudo plasma torpedoes (FP6.14)." */
    @Test
    public void itHasNoPseudoTorpedo() {
        FighterPlasmaF w = loaded();

        assertFalse("the capability must read false, or the UI offers the button",
                w.canLaunchPseudo());
        assertNull("and the launch itself must refuse", w.launchPseudo());
    }

    /** A ship's launcher DOES have one — the contrast that makes the override meaningful. */
    @Test
    public void aShipsLauncherStillHasItsPseudo() {
        PlasmaLauncher ship = new PlasmaLauncher(PlasmaType.F);
        ship.setArmedState();

        assertTrue("fixture: a ship launcher keeps FP6.14", ship.isPseudoPlasmaReady());
    }

    /**
     * J4.864: "No fighter can fire a plasma bolt (FP8.23)."
     * <p>
     * {@code fire(int)} is the BOLT path on a plasma launcher — the torpedo leaves through
     * {@code launch()} as a seeking weapon — so this one override is the whole of the denial.
     */
    @Test
    public void itCannotFireAPlasmaBolt() {
        FighterPlasmaF w = loaded();

        try {
            w.fire(5);
            fail("a fighter fired a plasma bolt (J4.864)");
        } catch (Exception expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("J4.864"));
        }
        assertTrue("and the refusal did not cost it the torpedo", w.isLoaded());
    }

    /** Refused at every range, not merely the long ones: it is the weapon, not the reach. */
    @Test
    public void theBoltIsRefusedAtEveryRange() {
        for (int range : new int[] { 0, 1, 8, 30 }) {
            FighterPlasmaF w = loaded();
            try {
                w.fire(range);
                fail("bolt allowed at range " + range);
            } catch (Exception expected) {
                assertTrue(expected.getMessage().contains("J4.864"));
            }
        }
    }

    // ---------------------------------------------------------------- as the data builds it

    /**
     * The G-1 off the catalogue: one launcher, forward arc, launching forward. The arc is what
     * it may be aimed at; the launch DIRECTION is the facing the torpedo leaves on, and a
     * fighter's is dead ahead — a distinction the catalogue parser dropped silently until a
     * fighter had a launcher to need it.
     */
    @Test
    public void theGladiatorCarriesOneForwardLaunchingTorpedo() {
        Shuttle g1 = CataloguedFighter.of("g1");
        assertNotNull("fixture: the G-1 should be in the catalogue", g1);

        FighterPlasmaF launcher = null;
        for (Weapon w : g1.getWeapons().fetchAllWeapons())
            if (w instanceof FighterPlasmaF f)
                launcher = f;

        assertNotNull("the G-1 must actually carry its plasma", launcher);
        assertEquals("one launcher", 1, g1.getWeapons().fetchAllWeapons().size());
        assertEquals("forward arc", ArcUtils.calculateMask(java.util.List.of("FA")),
                launcher.getArcs());
        assertEquals("launching forward (direction 1)",
                ArcUtils.calculateMask(java.util.List.of("1")),
                launcher.getLaunchDirections());
    }

    /**
     * J1.31's shuttle cap DOES clip this launcher's {@code maxRange} to fifteen, and that is
     * harmless rather than correct-by-accident, which is worth writing down.
     * <p>
     * On a plasma launcher {@code maxRange} is the BOLT range (set from the bolt hit chart,
     * 0-30), because the torpedo itself is a seeking weapon whose reach is its own endurance
     * and nothing to do with this field. J1.31 caps direct-fire reach on a shuttle mount, so
     * clipping it is the right thing to do to a direct-fire figure - and for a fighter the
     * figure is inert anyway, since J4.864 bars the bolt outright.
     * <p>
     * Pinned because the first version of this test asserted the OPPOSITE, on the assumption
     * that a clipped maxRange must be shortening the torpedo. It is not: the torpedo never
     * consults it.
     */
    @Test
    public void theShuttleCapClipsTheBoltRangeAndNothingElse() {
        Shuttle g1 = CataloguedFighter.of("g1");
        FighterPlasmaF launcher = null;
        for (Weapon w : g1.getWeapons().fetchAllWeapons())
            if (w instanceof FighterPlasmaF f)
                launcher = f;
        assertNotNull(launcher);

        assertEquals("clipped to the shuttle cap (J1.31)",
                CataloguedFighter.SHUTTLE_MAX_RANGE, launcher.getMaxRange());

        // And it costs nothing, because the only thing that field governs is barred.
        launcher.loadTorpedo();
        assertTrue("the torpedo is unaffected", launcher.isLoaded());
        assertTrue("with its full warhead", launcher.getArmedStrength() > 0);
        try {
            launcher.fire(10);
            fail("the bolt that maxRange governs is barred anyway (J4.864)");
        } catch (Exception expected) {
            assertTrue(expected.getMessage().contains("J4.864"));
        }
    }
}
