package com.sfb.scenario;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Annex #3's "D%"/"DB" marking: the hulls allowed more special drones than their empire, and a
 * bigger Commander's Option budget to buy them with.
 *
 * <h2>Why it is declared rather than worked out</h2>
 * FD10.622 and FD10.632 each grant the doubled caps to three populations, and only two of them can
 * be computed. Kzinti ships are a faction check; carriers with ten or more fighters (or five heavy
 * ones) come from the complement. The third is "any other unit with <b>'D%' or 'DB'</b> in the notes
 * column of the Master Ship Chart" — the rulebook's own representation is a mark on a chart, so a
 * boolean on the hull is faithful to it rather than a workaround.
 *
 * <h2>The two effects</h2>
 * FD10.622/FD10.632 double the caps (Restricted 25% to 50%, Limited 10% to 20%) — those caps are not
 * built yet, so the flag waits for them. S3.223 raises the option budget from 20% of Effective
 * Combat BPV to <b>30%</b>, and that half is live and tested here.
 *
 * <p>One flag serves both markings, but a DB ship is only <b>conditionally</b> the same as a D% one —
 * see {@link #aDbShipIsOnlyADPercentShipWhileItIsNotBombarding()}.
 */
public class DPercentShipTest {

    @BeforeClass
    public static void loadData() {
        ShipLibrary.loadAllSpecs("../data/factions");
        assertTrue("fixture: the library should have loaded", ShipLibrary.all().size() > 100);
    }

    private static Ship built(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull(faction + "/" + type + " is not in the library", spec);
        Ship ship = new Ship();
        ship.init(spec.toInitMap());
        return ship;
    }

    /**
     * The flag survives the trip from JSON through {@code toInitMap} into the built ship.
     *
     * <p>Worth its own assertion because that trip has swallowed a field before: {@code crewQuality}
     * was read by {@code Crew.init} and never written by {@code toInitMap}, so every ship in the
     * game was NORMAL and nothing failed. A declared flag that no built ship carries is the same
     * bug with a different name.
     */
    @Test
    public void theMarkingReachesTheBuiltShip() {
        assertTrue("Klingon D5D is DB-marked in Annex #3", built("Klingon", "D5D").isDPercent());
        assertTrue("Kzinti DF likewise", built("Kzinti", "DF").isDPercent());
        assertTrue("and the Federation NCD", built("Federation", "NCD").isDPercent());
    }

    /** And an ordinary hull does not acquire it. */
    @Test
    public void anUnmarkedHullIsNotDPercent() {
        assertFalse("a plain heavy cruiser has no such marking",
                built("Federation", "CA").isDPercent());
        assertFalse("nor does a Klingon D7", built("Klingon", "D7").isDPercent());
    }

    /**
     * S3.223's extra tenth, and the thing that made it worth checking: the bonus has to arrive
     * through {@code allowanceFor(ship, percent)}, because every real caller passes a percentage —
     * FleetValidator, ScenarioLoader, the /coi endpoint and refusalFor all do. Put only on the
     * no-argument overload it would have been dead on arrival.
     */
    @Test
    public void aDPercentShipGetsThirtyPercentThroughThePathCallersUse() {
        Ship marked = built("Klingon", "D5D");
        Ship plain = built("Klingon", "D7");

        double basis = CoiBudget.effectiveAdjustedCombatBpv(marked);
        assertEquals("S3.223: 30% of the Effective Combat BPV",
                Math.floor(basis * 30 / 100.0),
                CoiBudget.allowanceFor(marked, CoiBudget.DEFAULT_PERCENT), 0.001);

        double plainBasis = CoiBudget.effectiveAdjustedCombatBpv(plain);
        assertEquals("an unmarked hull stays at 20%",
                Math.floor(plainBasis * 20 / 100.0),
                CoiBudget.allowanceFor(plain, CoiBudget.DEFAULT_PERCENT), 0.001);
    }

    /** The no-argument convenience overload agrees with the explicit one, rather than double-counting. */
    @Test
    public void theTwoOverloadsAgree() {
        Ship marked = built("Klingon", "D5D");
        assertEquals(CoiBudget.allowanceFor(marked, CoiBudget.DEFAULT_PERCENT),
                CoiBudget.allowanceFor(marked), 0.001);
    }

    /**
     * A scenario's own percentage still gets the extra tenth on top, because S3.223 grants it
     * against the Effective Combat BPV rather than against the house rate.
     */
    @Test
    public void aScenarioPercentageStillGetsTheBonus() {
        Ship marked = built("Kzinti", "DF");
        assertEquals(40, CoiBudget.percentFor(marked, 30));
        assertEquals("an unmarked hull takes the scenario's figure unchanged",
                30, CoiBudget.percentFor(built("Kzinti", "FF"), 30));
    }

    /** The ring-fenced share is reported, even though nothing enforces it yet. */
    @Test
    public void theDroneOnlyReserveIsTheExtraTenth() {
        Ship marked = built("Federation", "NCD");
        double basis = CoiBudget.effectiveAdjustedCombatBpv(marked);

        assertEquals(Math.floor(basis * 10 / 100.0), CoiBudget.droneOnlyReserve(marked), 0.001);
        assertEquals("nothing is reserved on an unmarked hull",
                0.0, CoiBudget.droneOnlyReserve(built("Federation", "CA")), 0.001);
    }

    /**
     * The flag is NOT redundant on a Kzinti hull, which is the obvious objection to marking any.
     *
     * <p>The owner raised it 2026-10-06: Kzinti ships get the better ratio just for being Kzinti. True
     * — FD10.622's and FD10.632's FIRST bullet gives every Kzinti ship 50% Restricted and 20% Limited,
     * so on a Kzinti hull the cap half of this flag adds exactly nothing.
     *
     * <p>But S3.223's extra tenth of Commander's Option budget is gated on the CHART MARKING and not
     * on the empire, and Kzinti rows in Annex #3 carry no "D%" in their notes column. So on a Kzinti
     * drone frigate this flag is doing one job and one only — and it is worth roughly half again as
     * much to spend: the DF goes from 14 points to 22, the SDF from 18 to 27.
     *
     * <p>FD10.671 is what brings DB ships inside S3.223 at all: "a drone bombardment ship is treated
     * as a D% ship (S3.223), i.e., as if it were a carrier with ten or more fighters."
     */
    @Test
    public void theFlagStillEarnsItsKeepOnAKzintiHull() {
        Ship droneFrigate = built("Kzinti", "DF");
        Ship plainFrigate = built("Kzinti", "FF");

        assertTrue("the DF is DB-marked", droneFrigate.isDPercent());
        assertFalse("an ordinary Kzinti frigate is not", plainFrigate.isDPercent());

        // The point: being Kzinti does not buy the 30%, so the two differ by more than their BPVs.
        assertEquals("S3.223 reaches a Kzinti DB ship", 30, CoiBudget.percentFor(droneFrigate, 20));
        assertEquals("and not an unmarked Kzinti hull", 20, CoiBudget.percentFor(plainFrigate, 20));

        double basis = CoiBudget.effectiveAdjustedCombatBpv(droneFrigate);
        assertEquals(Math.floor(basis * 30 / 100.0),
                CoiBudget.allowanceFor(droneFrigate, 20), 0.001);
        assertTrue("so the flag is worth real points on a Kzinti hull",
                CoiBudget.allowanceFor(droneFrigate, 20) > Math.floor(basis * 20 / 100.0));
    }

    /**
     * A DB ship equals a D% ship only while it is NOT on a bombardment mission — and this engine
     * cannot put it on one, which is why the single-state flag is right for now.
     *
     * <p>The owner asked directly on 2026-10-06 whether the two markings are treated the same, and the
     * flat answer I had been giving was wrong. <b>S3.222</b> gives a DB ship two modes:
     *
     * <ul>
     *   <li><b>On an independent bombardment mission:</b> "might be loaded entirely with type-III-XX
     *       drones (FD10.671), using their <b>normal racial drone percentages</b> for special
     *       warheads... the ship pays the normal costs for its drone racks (with free reloads) and 25%
     *       of the total cost of the drones in the cargo boxes." So it gives UP the doubled caps in
     *       exchange for a cheap hold of heavy drones.
     *   <li><b>Otherwise:</b> "If not on such a mission, a drone bombardment ship is treated as a D%
     *       ship (S3.223), i.e., as if it were a carrier with ten or more fighters."
     * </ul>
     *
     * <p>Nothing in this engine can assign a bombardment mission, so the second branch is true of every
     * scenario it can express, and that is the branch {@code dPercent} implements. The assertion below
     * is therefore about an ABSENCE: there is no mission concept to consult, so no second state can be
     * entered by accident. The day missions arrive, this test is where the fork belongs — along with
     * FD10.671's arithmetic trap, since a type-III-XX is two spaces with ONE payload space (FD10.24)
     * and FD10.641 takes its percentages of payload spaces, halving the basis a bombardment load is
     * judged against.
     */
    @Test
    public void aDbShipIsOnlyADPercentShipWhileItIsNotBombarding() {
        Ship db = built("Klingon", "D5D");

        // The engine has no bombardment mission to be on, so the S3.222 branch cannot be reached and
        // the flag is unconditional by construction rather than by a reading of the rule.
        assertTrue("while no mission exists, S3.222's second branch is always the live one",
                db.isDPercent());
        assertEquals("so the 30% applies in every scenario we can currently build",
                30, CoiBudget.percentFor(db, 20));

        // If a mission concept ever appears, it must be asked here and this will stop compiling or
        // stop passing. Deliberately no fake mission state is introduced to assert against: inventing
        // one to test it would BE the feature, half-built, which is how allowedEwFighters and
        // crewQuality both came to exist with no caller.
    }

    /**
     * Every hull we have marked is one the Master Annex File's drone-storage table actually lists,
     * so the flag stays a transcription and does not drift into a judgement about which ships look
     * drone-heavy. Several of ours DO look the part — Klingon D6DB, F5D, F5DB, Kzinti CVA, Federation
     * NEA/NEC all carry five or six racks — and none of them is on the roster, so none is marked.
     */
    @Test
    public void onlyRosteredHullsAreMarked() {
        List<String> rostered = List.of(
                "Federation/NCD", "Federation/NCD+",
                "Klingon/D5D", "Klingon/D6D",
                "Kzinti/DF", "Kzinti/DF+", "Kzinti/SDF", "Kzinti/SDF+");

        List<String> marked = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.dPercent)
                marked.add(spec.faction + "/" + spec.type);

        assertFalse("fixture: something should be marked", marked.isEmpty());
        for (String m : marked)
            assertTrue(m + " is marked dPercent but is not on the Annex #3 roster transcribed in"
                    + " this test — add it there with its rule reference, or unmark the hull",
                    rostered.contains(m));
    }
}
