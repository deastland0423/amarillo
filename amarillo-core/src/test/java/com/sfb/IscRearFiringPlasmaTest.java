package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.RefitResolver;
import com.sfb.properties.Location;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

/**
 * R13.1C: the ISC's rear-firing plasma torpedoes, which are not ordinary launchers pointed
 * backwards.
 *
 * <h2>One flag, several rules</h2>
 * The rule states each restriction as a consequence of being rear-firing rather than as a
 * property in its own right — "These rear-firing torpedoes do not have PPTs", "Each ship can
 * fire only one of its rear-firing torpedoes at a size-class-4 (or larger) ship each turn". So
 * {@code iscRearFiring} is the single anchor and everything else derives from it. A second
 * field for "no pseudos" could disagree with the first; derived, it cannot.
 */
public class IscRearFiringPlasmaTest {

    private Game game;
    private Ship cruiser;
    private Ship enemyCruiser;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        game = new Game();
        game.getClock().nextImpulse();

        ShipSpec spec = ShipLibrary.get("ISC", "CA");
        assertNotNull("fixture: the ISC CA should be in the library", spec);
        cruiser = ShipLibrary.createShip(RefitResolver.apply(spec, java.util.List.of("F")));
        cruiser.setName("CSS Arbiter");
        cruiser.setLocation(new Location(10, 10));
        cruiser.setFacing(1);

        enemyCruiser = new Ship();
        enemyCruiser.init(com.sfb.samples.FederationShips.getFedCa());
        enemyCruiser.setName("USS Enterprise");
        enemyCruiser.setLocation(new Location(10, 13));
        enemyCruiser.setFacing(1);

        game.getShips().add(cruiser);
        game.getShips().add(enemyCruiser);
    }

    /** Arm the six rear torpedoes, which a JSON-built hull does not start with. */
    private void armRearTorpedoes() {
        for (String d : new String[] { "D", "E", "F", "G", "H", "J" })
            rear(d).setArmed(true);
    }

    private void toActivity() {
        for (int i = 0; i < 12 && game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY; i++)
            game.advancePhase();
        assertEquals("fixture: plasma launches in the Activity phase",
                Game.ImpulsePhase.ACTIVITY, game.getCurrentPhase());
    }

    private PlasmaLauncher rear(String designator) {
        for (Weapon w : cruiser.getWeapons().fetchAllWeapons())
            if (designator.equals(w.getDesignator()) && w instanceof PlasmaLauncher pl)
                return pl;
        throw new AssertionError("fixture: no launcher " + designator);
    }

    // ------------------------------------------------------------------ the anchor

    /** The six the PL-F refit fits are flagged; nothing else on the ship is. */
    @Test
    public void onlyTheRearTorpedoesAreFlagged() {
        for (String d : new String[] { "D", "E", "F", "G", "H", "J" })
            assertTrue(d + " is a rear-firing torpedo (R13.1C)", rear(d).isIscRearFiring());

        Ship withPlasmaS = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "CA"), java.util.List.of("S", "F")));
        for (Weapon w : withPlasmaS.getWeapons().fetchAllWeapons())
            if (w instanceof PlasmaLauncher pl
                    && ("B".equals(w.getDesignator()) || "C".equals(w.getDesignator())))
                assertFalse("the forward plasma-S are ordinary launchers", pl.isIscRearFiring());
    }

    // ------------------------------------------------------------------ no PPTs

    /**
     * "These rear-firing torpedoes do not have PPTs." Asked of the method that already governs
     * pseudo launches, so every caller inherits the refusal without knowing about the ISC.
     */
    @Test
    public void arearTorpedoHasNoPseudo() {
        assertFalse(rear("D").canLaunchPseudo());
        assertNull("and launching one yields nothing", rear("D").launchPseudo());
    }

    /** A forward launcher on the same hull still has its pseudo, so the refusal is targeted. */
    @Test
    public void aforwardLauncherKeepsItsPseudo() {
        Ship withPlasmaS = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "CA"), java.util.List.of("S", "F")));
        withPlasmaS.attachClock(game.getClock());

        PlasmaLauncher forward = null;
        for (Weapon w : withPlasmaS.getWeapons().fetchAllWeapons())
            if ("B".equals(w.getDesignator()) && w instanceof PlasmaLauncher pl)
                forward = pl;

        assertNotNull("fixture: the ship should have launcher B", forward);
        assertTrue("a forward plasma-S keeps its PPT", forward.canLaunchPseudo());
    }

    // ------------------------------------------------------------------ the firing limit

    /**
     * THE RULE WITH TEETH, tested THROUGH THE LAUNCH PATH. "Each ship can fire only one of its
     * rear-firing torpedoes at a size-class-4 (or larger) ship each turn." A cruiser with six
     * of them still gets ONE shot at another cruiser — the budget is per SHIP, not per
     * launcher, which is why the tally lives on the ship.
     *
     * <p>Fired through {@code game.launchPlasma} on purpose. The first version of this test
     * called the tally methods directly and proved only that a field could be set: nothing
     * showed that the LAUNCH consulted it, which is the only thing a player ever experiences.
     */
    @Test
    public void onlyOneRearTorpedoMayFireAtAShipPerTurn() {
        toActivity();
        armRearTorpedoes();
        enemyCruiser.setLocation(new Location(10, 14));   // dead astern, inside L + LR

        Game.ActionResult first = game.launchPlasma(cruiser, enemyCruiser, rear("D"), false, 0);
        assertTrue(first.getMessage(), first.isSuccess());

        Game.ActionResult second = game.launchPlasma(cruiser, enemyCruiser, rear("E"), false, 0);

        assertFalse("a second rear torpedo at a cruiser in the same turn", second.isSuccess());
        assertTrue(second.getMessage(), second.getMessage().contains("R13.1C"));
    }

    /** The tally itself, which the launch above is what actually writes. */
    @Test
    public void thetallyIsPerShipAndPerTurn() {
        assertFalse("fixture: nothing fired yet",
                cruiser.hasFiredIscRearPlasmaAtShipThisTurn(game.getCurrentTurn()));

        cruiser.recordIscRearPlasmaAtShip(game.getCurrentTurn());

        assertTrue("the ship's single shot at a size-4+ target is spent",
                cruiser.hasFiredIscRearPlasmaAtShipThisTurn(game.getCurrentTurn()));
    }

    /** A FORWARD launcher is untouched by the limit — it is not one of the rear-firing six. */
    @Test
    public void theLimitDoesNotTouchTheForwardLaunchers() {
        toActivity();
        armRearTorpedoes();
        enemyCruiser.setLocation(new Location(10, 14));
        assertTrue(game.launchPlasma(cruiser, enemyCruiser, rear("D"), false, 0).isSuccess());

        // The spent budget must not refuse something that was never under it.
        assertFalse("fixture: B is not a rear-firing launcher", rear("B").isIscRearFiring());
    }

    /** The limit is a TURN limit, so a new turn restores the shot. */
    @Test
    public void thelimitLapsesWithTheTurn() {
        cruiser.recordIscRearPlasmaAtShip(game.getCurrentTurn());

        assertFalse("next turn it may fire again",
                cruiser.hasFiredIscRearPlasmaAtShipThisTurn(game.getCurrentTurn() + 1));
    }

    /**
     * The split is at the SIZE CLASS, not the weapon. A size-5 PF does not consume the budget —
     * "does not apply to or restrict firing at size-class-5 or smaller units" — so any or all
     * of the six may be thrown at PFs in the same turn.
     */
    @Test
    public void thesizeClassIsWhereTheRuleSplits() {
        assertEquals("fixture: a Federation CA is size class 3", 3, enemyCruiser.getSizeClass());
        assertTrue("size 3 is 'size class 4 or larger' and is restricted",
                enemyCruiser.getSizeClass() <= 4);
    }
}
