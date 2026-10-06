package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;

/**
 * The Kzinti CVA: twenty-four fighters in two bays, which is two squadrons of twelve (J4.46).
 * <p>
 * The biggest carrier in the data, and the first that exercises several limits at once — two
 * squadrons rather than one, two EW fighters rather than one, and an attack role filled rather
 * than empty. Each of those is a rule with a number in it:
 * <ul>
 *   <li>J4.462 caps a squadron at twelve fighters, so twenty-four makes exactly two;</li>
 *   <li>J4.463 allows two EW fighters for a designed complement of sixteen to twenty-four, and
 *       only ONE per squadron — so they must land one in each, not both in the first;</li>
 *   <li>and each squadron of twelve clears J4.463's eight-fighter minimum for holding one.</li>
 * </ul>
 */
public class KzintiCvaSquadronsTest {

    private Ship cva;

    @Before
    public void loadTheCva() throws Exception {
        ShuttleCatalog.loadDefault("../data");
        cva = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cva.json")));
        cva.setName("KHS Ascendant");
    }

    private Map<String, Integer> fighterTally() {
        Map<String, Integer> tally = new LinkedHashMap<>();
        for (Shuttle s : cva.getShuttles().getAllShuttles())
            if (s instanceof Fighter)
                tally.merge(s.getCatalogType(), 1, Integer::sum);
        return tally;
    }

    @Test
    public void itCarriesTwentyFourFightersInTwoBays() {
        assertEquals("two bays, each a squadron's worth",
                2, cva.getShuttles().getBays().size());
        assertEquals(24, cva.getShuttles().getDesignedFighterComplement());
    }

    /**
     * Built from its own file at Y173, so the HAAS era: eighteen standard, six attack, two EW.
     * The attack fighters are DAS — the DASC does not arrive until Y183.
     */
    @Test
    public void itsComplementResolvesToItsOwnEra() {
        assertEquals(Map.of("haas", 16, "das", 6, "haas_e", 2), fighterTally());
    }

    @Test
    public void twentyFourFightersMakeTwoSquadrons() {
        assertEquals("J4.462's twelve apiece", 2, cva.getShuttles().getSquadrons().size());
        for (Squadron sq : cva.getShuttles().getSquadrons())
            assertEquals(sq.getName() + " should be full", 12, sq.size());
    }

    /**
     * J4.463: two EW fighters are allowed at this complement, and only one may be in a squadron.
     * So one in each — which is also what makes both squadrons able to receive lent EW.
     */
    @Test
    public void eachSquadronGetsExactlyOneEwFighter() {
        assertEquals("sixteen to twenty-four fighters allows two (J4.463)",
                2, cva.getShuttles().allowedEwFighters());

        for (Squadron sq : cva.getShuttles().getSquadrons()) {
            assertEquals(sq.getName() + " must hold exactly one EW fighter",
                    1, sq.ewFighterCount());
            assertTrue(sq.getName() + " is big enough to hold one (J4.463)",
                    sq.largeEnoughForEwFighter());
        }
    }

    /**
     * Squadron membership is settled when the SHIP IS BUILT, not when fighters launch.
     * <p>
     * Worth pinning because the alternative reading is natural and wrong: nothing assigns a
     * squadron at launch, and the first twelve fighters off the deck do not become Squadron 1.
     * Every fighter already knows its squadron while it is still in its box, which is what lets
     * J4.921's lending work from the moment a pair clears the bay.
     * <p>
     * It follows from {@code organiseSquadrons()} running at the end of {@code Shuttles.init}.
     * J4.46 actually gives the choice to the player — "Players may organize fighter squadrons
     * before the scenario begins" — and this automatic grouping is a default standing in for a
     * pre-game choice that has no interface yet.
     */
    @Test
    public void squadronsAreSettledBeforeAnythingLaunches() {
        for (Shuttle s : cva.getShuttles().getAllShuttles())
            if (s instanceof Fighter f) {
                assertNotNull(f.getName() + " should already know its squadron while in its bay",
                        f.getSquadron());
                // The parent ship name is stamped ON LAUNCH, so its absence is the system's own
                // marker for a craft that has never left its bay.
                assertNull(f.getName() + " has not launched", f.getParentShipName());
            }
    }

    /**
     * How the 24 divide is NOT by bay, even though on this ship it looks like it.
     * <p>
     * {@code organiseSquadrons} pools every fighter in bay order, sorts the EW fighters to the
     * front, and fills one squadron to twelve before starting the next. The CVA's bays hold
     * exactly twelve each, so the boundary happens to land on the bay boundary — arithmetic
     * coincidence, not intent. A carrier with bays of ten and fourteen would have squadrons
     * straddling them, so nothing should be built on bay and squadron agreeing.
     */
    @Test
    public void theDivisionIsBySquadronSizeNotByBay() {
        assertEquals("both bays hold the same twelve-fighter complement",
                cva.getShuttles().getBays().get(0).getFighterComplement().total(),
                cva.getShuttles().getBays().get(1).getFighterComplement().total());
        assertEquals("which is exactly a squadron's capacity (J4.462)",
                Squadron.MAX_SLOTS, cva.getShuttles().getBays().get(0)
                        .getFighterComplement().total());
    }

    /**
     * Squadrons are named after the ship AS FIELDED, not after the placeholder in its file.
     * <p>
     * The ordering makes this easy to get wrong and the CVA showed it: squadrons are organised
     * while the ship's systems are built, and {@code Unit.init} takes the name out of the ship
     * JSON — "KHS Olympus" for the CVA — while the scenario's own name arrives later through
     * {@code setName}. Every carrier in the game had squadrons wearing its file's placeholder, and
     * the name is on the DTO for players to read.
     */
    @Test
    public void squadronsAreNamedAfterTheShipAsFielded() {
        assertEquals("KHS Ascendant", cva.getName());
        for (Squadron sq : cva.getShuttles().getSquadrons())
            assertTrue("squadron named for the file's placeholder, not the ship: " + sq.getName(),
                    sq.getName().startsWith("KHS Ascendant "));
    }

    /** And a later rename follows, since the same hook does both. */
    @Test
    public void renamingTheShipRenamesItsSquadrons() {
        cva.setName("KHS Thunderclap");
        for (Squadron sq : cva.getShuttles().getSquadrons())
            assertTrue(sq.getName(), sq.getName().startsWith("KHS Thunderclap "));
        assertEquals("and they stay distinguishable",
                cva.getShuttles().getSquadrons().size(),
                cva.getShuttles().getSquadrons().stream()
                        .map(Squadron::getName).distinct().count());
    }

    /** Every fighter aboard belongs to a squadron — none left over. */
    @Test
    public void noFighterIsLeftOutOfASquadron() {
        int inSquadrons = 0;
        for (Squadron sq : cva.getShuttles().getSquadrons())
            inSquadrons += sq.size();
        assertEquals(24, inSquadrons);

        for (Shuttle s : cva.getShuttles().getAllShuttles())
            if (s instanceof Fighter f)
                assertNotNull(f.getName() + " has no squadron", f.getSquadron());
    }

    /**
     * And the EW fighters can actually lend, which is the thing the two-seater flag decides.
     * A Y177 CVA flies TAAS-Es, and those were built without the flag — so this would have
     * passed at Y173 with HAAS-Es and failed in a Y177 game, which is the worst shape a bug
     * can have.
     */
    @Test
    public void itsEwFightersAreRecognisedAsSuchInEveryEra() {
        for (int year : new int[] { 173, 177, 180, 183 }) {
            for (com.sfb.systemgroups.ShuttleBay bay : cva.getShuttles().getBays()) {
                FighterComplement complement = bay.getFighterComplement();
                assertNotNull("the CVA's bays declare a complement", complement);
                complement.applyTo(bay, year, "");
            }
            int ewFighters = 0;
            for (Shuttle s : cva.getShuttles().getAllShuttles())
                if (s instanceof Fighter f && f.getEwPods() > 0) {
                    ewFighters++;
                    assertTrue("Y" + year + ": " + f.getClass().getSimpleName()
                                    + " carries pods and must be flagged a two-seater",
                            f.isTwoSeater());
                }
            assertEquals("Y" + year + ": two EW fighters aboard", 2, ewFighters);
        }
    }
}
