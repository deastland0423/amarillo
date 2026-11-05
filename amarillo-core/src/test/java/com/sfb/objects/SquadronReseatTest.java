package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * A squadron holds the fighters that are aboard, not the ones the hull was built with (J4.46).
 *
 * <h2>The bug</h2>
 * Squadrons are organised while a ship's systems are built, which is before the scenario's year
 * decides what the bays hold. {@code FighterComplement.reseat} then builds new craft for that
 * year and seats them, and nothing told the squadrons — so they went on holding the complement
 * the hull was constructed with.
 *
 * <p>A Kzinti CVS in Y180 carried a squadron of twelve AAS craft, the Y164 fighter, while twelve
 * TADS sat in its boxes. Not one member of the squadron was a fighter in the game; every member
 * was a distinct object with a doubled name ("Ship CVS-Ship CVS-AAS-1"), because the rename had
 * been applied to a set of craft nobody else could reach.
 *
 * <h2>What was reading them</h2>
 * Everything that resolves membership rather than counting it. J4.221 lets an EW fighter accept
 * control of a seeking weapon "only from fighters in its own squadron"; J4.463 limits EW
 * fighters per squadron; and J4.93's carrier EW lending, which the energy allocation dialog
 * offers every turn, sets the pool on a squadron whose members are not in play. The dialog
 * showed "Squadron 1 — 12 fighters" and lent to nobody.
 *
 * <p>Found while adding a squadron badge to the fighter loadout payload, which asked the
 * squadrons which fighter belonged where and got back twelve names that answered to nothing.
 */
public class SquadronReseatTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private static Ship outfitted(String faction, String type, int year) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        org.junit.Assume.assumeTrue("the library must know " + type, spec != null);
        Ship ship = ShipLibrary.createShip(spec);
        ship.setName("Ship " + type);
        com.sfb.scenario.ScenarioLoader.outfitForYear(ship, faction, year, spec);
        return ship;
    }

    private static List<Fighter> fightersAboard(Ship ship) {
        List<Fighter> aboard = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays()) {
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Fighter f)
                    aboard.add(f);
            for (com.sfb.objects.shuttles.Shuttle s : bay.getBalcony())
                if (s instanceof Fighter f)
                    aboard.add(f);
        }
        return aboard;
    }

    /** By IDENTITY, because two AAS craft with the same name are still not the same fighter. */
    private static Set<Fighter> identitySetOf(List<Fighter> fighters) {
        Set<Fighter> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(fighters);
        return set;
    }

    @Test
    public void everySquadronMemberIsAFighterThatIsActuallyAboard() {
        Ship ship = outfitted("Kzinti", "CVS", 180);
        Set<Fighter> aboard = identitySetOf(fightersAboard(ship));
        assertFalse("fixture: the carrier has a complement", aboard.isEmpty());

        List<Fighter> members = new ArrayList<>();
        for (Squadron sq : ship.getShuttles().getSquadrons())
            members.addAll(sq.getFighters());
        assertFalse("fixture: and squadrons to put it in", members.isEmpty());

        for (Fighter f : members)
            assertTrue(f.getName() + " is in a squadron but not in the game",
                    aboard.contains(f));
    }

    /** And the other way about: nobody aboard is left out of the organisation (J4.461). */
    @Test
    public void everyFighterAboardIsInASquadron() {
        Ship ship = outfitted("Kzinti", "CVS", 180);
        Set<Fighter> members = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Squadron sq : ship.getShuttles().getSquadrons())
            members.addAll(sq.getFighters());

        for (Fighter f : fightersAboard(ship))
            assertTrue(f.getName() + " is aboard but in no squadron", members.contains(f));
    }

    /**
     * The year's fighter, not the hull's. A Kzinti CVS flies the AAS from Y164 and the TADS
     * from Y180, and the squadron is the thing that was still looking at the former.
     */
    @Test
    public void theSquadronHoldsTheYearsFighterNotTheHullsOwn() {
        Ship ship = outfitted("Kzinti", "CVS", 180);
        for (Squadron sq : ship.getShuttles().getSquadrons())
            for (Fighter f : sq.getFighters())
                assertTrue("a Y180 squadron flying " + f.getCatalogType(),
                        f.getCatalogType().startsWith("tads"));
    }

    /**
     * A name is an ADDRESS in this game, and a stale member wore a doubled one — proof it had
     * been renamed on a pass the boxes never saw.
     */
    @Test
    public void noMemberWearsItsShipsNameTwice() {
        Ship ship = outfitted("Kzinti", "CVS", 180);
        for (Squadron sq : ship.getShuttles().getSquadrons())
            for (Fighter f : sq.getFighters())
                assertEquals(f.getName() + " has been renamed more than once",
                        f.getName().indexOf(ship.getName()),
                        f.getName().lastIndexOf(ship.getName()));
    }

    /** J4.461's own rule survives the re-organisation: the MINIMUM number of squadrons. */
    @Test
    public void theReorganisationStillMakesTheFewestSquadrons() {
        Ship ship = outfitted("Kzinti", "CVS", 180);
        assertEquals("twelve fighters are one squadron, not two of six",
                1, ship.getShuttles().getSquadrons().size());
        assertEquals(12, ship.getShuttles().getSquadrons().get(0).size());

        Ship big = outfitted("Kzinti", "CVA", 180);
        assertEquals("and twenty-four are two (J4.462's twelve slots)",
                2, big.getShuttles().getSquadrons().size());
    }

    /** And J4.463's one-EW-fighter-per-squadron is still honoured by the new organisation. */
    @Test
    public void theReorganisationStillSeparatesTheEwFighters() {
        Ship big = outfitted("Kzinti", "CVA", 180);
        for (Squadron sq : big.getShuttles().getSquadrons())
            assertTrue(sq.getName() + " holds " + sq.ewFighterCount() + " EW fighters",
                    sq.ewFighterCount() <= 1);
    }
}
