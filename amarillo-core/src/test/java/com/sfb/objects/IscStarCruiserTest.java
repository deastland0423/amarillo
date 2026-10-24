package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.PPD;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;

/**
 * R13.6, the ISC Star Cruiser — the first hull in the tree to carry a PPD.
 *
 * <h2>What makes this ship worth its own test</h2>
 * Its sheet draws the FULLY REFITTED ship at 185 points and prices each refit as a deduction,
 * so the hull in the data is the Y160 original: eight phaser-1s and two plasma-G, and nothing
 * else. The ISC upgraded their ships as the years passed, and the four refits walk that history
 * back up to the printed ship. A reader who took the drawing for the base hull would build a
 * Y160 cruiser with weapons that did not exist yet.
 */
public class IscStarCruiserTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    private static Ship refitted(String... codes) {
        ShipSpec base = ShipLibrary.get("ISC", "CA");
        assertNotNull("fixture: the ISC CA should be in the library", base);
        return ShipLibrary.createShip(
                codes.length == 0 ? base : RefitResolver.apply(base, List.of(codes)));
    }

    private static long count(Ship ship, Class<?> type) {
        return ship.getWeapons().fetchAllWeapons().stream().filter(type::isInstance).count();
    }

    private static Weapon byDesignator(Ship ship, String designator) {
        return ship.getWeapons().fetchAllWeapons().stream()
                .filter(w -> designator.equals(w.getDesignator()))
                .findFirst().orElseThrow(() -> new AssertionError("no weapon " + designator));
    }

    // ------------------------------------------------------------------ the Y160 hull

    /** Phaser-1s and plasma-G, as the ISC first built her. */
    @Test
    public void theOriginalHullCarriesPhaserOnesAndPlasmaG() {
        Ship original = refitted();

        assertEquals(8, count(original, com.sfb.weapons.Phaser1.class));
        assertEquals("no phaser-3s before the Y165 refit",
                0, count(original, com.sfb.weapons.Phaser3.class));
        assertEquals("no PPD before the Y168 refit", 0, count(original, PPD.class));
        assertEquals("two launchers, and they are plasma-G",
                2, count(original, PlasmaLauncher.class));
        assertEquals(com.sfb.properties.PlasmaType.G,
                ((PlasmaLauncher) byDesignator(original, "B")).getLauncherType());
    }

    // ------------------------------------------------------------------ the refits

    /** The PPD refit is what makes this the ISC's signature hull (E11.0). */
    @Test
    public void thePpdRefitFitsAPulsarDevice() {
        Ship withPpd = refitted("P");

        assertEquals(1, count(withPpd, PPD.class));
        Weapon ppd = byDesignator(withPpd, "A");
        assertTrue(ppd instanceof PPD);
        assertEquals("the sheet prints PPD-FA", "FA", ppd.getArcLabel());
    }

    /**
     * The PL-S refit CONVERTS the two launchers rather than adding any — "without the PL-S
     * refit, plasma-S torpedoes 'B' and 'C' are plasma-G torpedoes".
     */
    @Test
    public void theplasmaSRefitConvertsRatherThanAdds() {
        Ship upgraded = refitted("S");

        assertEquals("still two launchers", 2, count(upgraded, PlasmaLauncher.class));
        assertEquals(com.sfb.properties.PlasmaType.S,
                ((PlasmaLauncher) byDesignator(upgraded, "B")).getLauncherType());
        assertEquals(com.sfb.properties.PlasmaType.S,
                ((PlasmaLauncher) byDesignator(upgraded, "C")).getLauncherType());
    }

    /**
     * THE ORDERING TRAP. The PL-S conversion is scoped to designators B and C, so ticking both
     * plasma refits cannot turn the rear-firing plasma-F into plasma-S. A swap keyed on weapon
     * TYPE alone would have done exactly that, and only in the combination where both are on.
     */
    @Test
    public void theplasmaSRefitCannotReachThePlasmaF() {
        Ship both = refitted("S", "F");

        assertEquals("two plasma-S and six plasma-F", 8, count(both, PlasmaLauncher.class));
        for (String rear : new String[] { "D", "E", "F", "G", "H", "J" })
            assertEquals("rear-firing " + rear + " stays a plasma-F",
                    com.sfb.properties.PlasmaType.F,
                    ((PlasmaLauncher) byDesignator(both, rear)).getLauncherType());
    }

    /**
     * R13.1C's rear-firing torpedoes, identified by the arcs D2.35 gives them. Those arcs are
     * the whole reason they launch in "direction three or five" and cannot take swivels, so
     * getting them wrong would quietly make them ordinary forward plasma.
     */
    @Test
    public void theRearFiringPlasmaBearAstern() {
        Ship armed = refitted("F");

        for (String left : new String[] { "D", "E", "F" })
            assertEquals("L + LR", byDesignator(armed, left).getArcLabel());
        for (String right : new String[] { "G", "H", "J" })
            assertEquals("R + RR", byDesignator(armed, right).getArcLabel());
    }

    /** The PH-3 refit brings six, three to each side. */
    @Test
    public void thePhaserThreeRefitBringsSix() {
        Ship upgraded = refitted("3");

        assertEquals(6, count(upgraded, com.sfb.weapons.Phaser3.class));
        assertEquals("LS", byDesignator(upgraded, "9").getArcLabel());
        assertEquals("RS", byDesignator(upgraded, "12").getArcLabel());
    }

    // ------------------------------------------------------------------ the price

    /**
     * All four refits reach the point value the sheet prints. The deductions are the sheet's
     * own: -6 without PH-3, -15 without the PPD, -10 without PL-S, -12 without PL-F.
     */
    @Test
    public void thefullyRefittedShipIsTheShipOnTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "CA");
        ShipSpec full = RefitResolver.apply(base, List.of("3", "P", "S", "F"));

        assertEquals("the Y160 hull", 142, base.bpv);
        assertEquals("the ship the sheet draws", 185, full.bpv);
    }

    /** And she is size class 3, which is the smallest hull E11.16 allows a PPD on. */
    @Test
    public void sheIsJustLargeEnoughToCarryAPpd() {
        assertEquals(3, ShipLibrary.get("ISC", "CA").sizeClass);
    }

    // ------------------------------------------------------------------ the Light Cruiser

    /**
     * R13.9, the CL — the same progression on a lighter hull, and the contrast that shows the
     * PPD is a HEAVY-hull weapon. The sheet gives it no PPD refit line at all.
     */
    @Test
    public void thelightCruiserHasNoPulsarDevice() {
        ShipSpec cl = ShipLibrary.get("ISC", "CL");
        assertNotNull("fixture: the ISC CL should be in the library", cl);

        Ship fullyRefitted = ShipLibrary.createShip(
                RefitResolver.apply(cl, List.of("3", "S", "F")));

        assertEquals("no refit on this hull fits one", 0, count(fullyRefitted, PPD.class));
    }

    /** Its plasma-S sit on the CENTRELINE at FP, where the CA's are in the wings at LP/RP. */
    @Test
    public void thelightCruisersPlasmaSAreOnTheCentreline() {
        Ship cl = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "CL"), List.of("S")));

        assertEquals("FP", byDesignator(cl, "A").getArcLabel());
        assertEquals("FP", byDesignator(cl, "B").getArcLabel());
        assertEquals(com.sfb.properties.PlasmaType.S,
                ((PlasmaLauncher) byDesignator(cl, "A")).getLauncherType());
    }

    /** Four rear torpedoes against the CA's six, two to a side, all flagged for R13.1C. */
    @Test
    public void thelightCruiserCarriesFourRearTorpedoes() {
        Ship cl = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "CL"), List.of("F")));

        for (String left : new String[] { "C", "D" })
            assertEquals("L + LR", byDesignator(cl, left).getArcLabel());
        for (String right : new String[] { "E", "F" })
            assertEquals("R + RR", byDesignator(cl, right).getArcLabel());
        for (String d : new String[] { "C", "D", "E", "F" })
            assertTrue(d + " is rear-firing (R13.1C)",
                    ((PlasmaLauncher) byDesignator(cl, d)).isIscRearFiring());
    }

    /** And its refits reach the point value the sheet prints. */
    @Test
    public void thelightCruiserPricesOutToTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "CL");

        assertEquals("the Y160 hull", 123, base.bpv);
        assertEquals("the ship the sheet draws",
                145, RefitResolver.apply(base, List.of("3", "S", "F")).bpv);
    }

    // ------------------------------------------------------------------ the Destroyer

    /**
     * R13.17, the DD — THE SHIP THAT PROVES iscRearFiring CANNOT BE DERIVED.
     *
     * <p>All four of its torpedoes are plasma-F. Two sit forward at FP and are ordinary
     * launchers with their pseudo torpedoes; two sit astern and are R13.1C's rear-firing
     * weapons, with no pseudos and the one-per-turn limit. A model that inferred "rear-firing"
     * from the plasma TYPE, or from a hull being ISC, would get this ship exactly half wrong.
     */
    @Test
    public void thedestroyerHasPlasmaFBothForwardAndAstern() {
        Ship dd = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "DD"), List.of("F")));

        assertEquals("four plasma-F in all", 4, count(dd, PlasmaLauncher.class));

        for (String forward : new String[] { "A", "B" }) {
            PlasmaLauncher pl = (PlasmaLauncher) byDesignator(dd, forward);
            assertFalse(forward + " is a forward launcher", pl.isIscRearFiring());
            assertEquals("FP", pl.getArcLabel());
        }
        for (String rear : new String[] { "C", "D" })
            assertTrue(rear + " is rear-firing (R13.1C)",
                    ((PlasmaLauncher) byDesignator(dd, rear)).isIscRearFiring());

        assertEquals("L + LR", byDesignator(dd, "C").getArcLabel());
        assertEquals("R + RR", byDesignator(dd, "D").getArcLabel());
    }

    /**
     * And the consequence the sheet draws in its pseudo-plasma box: the forward pair carry
     * PPTs, the rear pair do not.
     */
    @Test
    public void onlyTheDestroyersForwardTorpedoesCarryPseudos() {
        Ship dd = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "DD"), List.of("F")));
        dd.attachClock(new com.sfb.Game().getClock());

        assertTrue("A is an ordinary launcher and keeps its PPT",
                ((PlasmaLauncher) byDesignator(dd, "A")).canLaunchPseudo());
        assertFalse("C is rear-firing and has none (R13.1C)",
                ((PlasmaLauncher) byDesignator(dd, "C")).canLaunchPseudo());
    }

    /** Its phaser-3s are base armament, not a refit — the only ISC hull here where that holds. */
    @Test
    public void thedestroyersPhaserThreesAreNotARefit() {
        Ship unrefitted = ShipLibrary.createShip(ShipLibrary.get("ISC", "DD"));

        assertEquals(4, count(unrefitted, com.sfb.weapons.Phaser3.class));
        assertEquals("the Y160 hull", 88, ShipLibrary.get("ISC", "DD").bpv);
        assertEquals("the ship the sheet draws", 92,
                RefitResolver.apply(ShipLibrary.get("ISC", "DD"), List.of("F")).bpv);
    }

    // ------------------------------------------------------------------ the Destroyer Leader

    /**
     * R13.16, the DDL — the DD's hull with a leader's extras. Almost everything matches, so the
     * test is the DIFFERENCES: a later service year, a higher command rating, twice the APR,
     * and plasma-G forward where the destroyer carries plasma-F.
     */
    @Test
    public void theLeaderIsTheDestroyerWithLeadersExtras() {
        ShipSpec ddl = ShipLibrary.get("ISC", "DDL");
        ShipSpec dd = ShipLibrary.get("ISC", "DD");
        assertNotNull("fixture: the ISC DDL should be in the library", ddl);

        assertEquals("the leader entered service later", 168, ddl.serviceYear);
        assertEquals(160, dd.serviceYear);
        assertEquals("and leads", 5, ddl.commandRating);
        assertEquals(4, dd.commandRating);
        assertEquals("twice the APR", 4, ddl.power.apr);
        assertEquals(2, dd.power.apr);
        assertEquals("the same hull underneath", dd.hullBoxes.chull, ddl.hullBoxes.chull);
    }

    /** Plasma-G forward, where the destroyer carries plasma-F in the same position. */
    @Test
    public void theLeaderCarriesPlasmaGForward() {
        Ship ddl = ShipLibrary.createShip(ShipLibrary.get("ISC", "DDL"));

        for (String forward : new String[] { "A", "B" }) {
            PlasmaLauncher pl = (PlasmaLauncher) byDesignator(ddl, forward);
            assertEquals(com.sfb.properties.PlasmaType.G, pl.getLauncherType());
            assertEquals("FP", pl.getArcLabel());
            assertFalse("forward launchers are not rear-firing", pl.isIscRearFiring());
        }
    }

    /**
     * NO PROBES, which is unusual for a warship and therefore worth pinning. The sheet has no
     * launcher box and no probes table; an absent probe reads exactly like an oversight, and
     * without a test the next person to notice would add one.
     */
    @Test
    public void theLeaderHasNoProbeLauncher() {
        assertEquals("R13.16 gives the DDL none", 0,
                ShipLibrary.get("ISC", "DDL").auxiliary.probes);
        assertEquals("where the destroyer has one", 1,
                ShipLibrary.get("ISC", "DD").auxiliary.probes);
    }

    /**
     * It is a LEADER, and that is not decoration: S8.361/S8.362 make a leader which is not the
     * flagship demand consorts FROM ITS OWN LINE, which `FleetValidator.checkLeaders` enforces.
     * So the flag and the line work together — a DDL draws its escorts from the ISC destroyers,
     * and lining it anywhere else would have sent the validator looking for ships that do not
     * exist.
     *
     * <p>Missed when the hull was first entered, and the Gorn DDL was sitting there as a
     * precedent. Pinned now so the pairing cannot come apart.
     */
    @Test
    public void theLeaderLeadsAndItsConsortsAreTheDestroyers() {
        ShipSpec ddl = ShipLibrary.get("ISC", "DDL");
        ShipSpec dd = ShipLibrary.get("ISC", "DD");

        assertTrue("R13.16 is a leader", ddl.isLeader);
        assertFalse("and the destroyer it leads is not", dd.isLeader);
        assertEquals("they share a line, so the DD is its consort pool (S8.361)",
                dd.line, ddl.line);
    }

    /** And it prices out to the sheet. */
    @Test
    public void theLeaderPricesOutToTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "DDL");

        assertEquals("the Y168 hull", 106, base.bpv);
        assertEquals("the ship the sheet draws", 110,
                RefitResolver.apply(base, List.of("F")).bpv);
    }

    // ---- The refits do not rename the ship, and what that costs to say ----

    /**
     * A VARIANT MAY DECLARE ITS BASE'S OWN TYPE CODE, meaning history did not rename the hull.
     *
     * <p>The ISC refit this cruiser four times between Y165 and Y179 and it is a CA throughout —
     * the C2 sheet prints one ship at 185 points with four "WITHOUT ... REFIT" deductions and no
     * new designation anywhere. The Federation is the contrast, and it is in the data: that hull
     * names {@code CA+} and {@code CAR+} as separate ships, a + without the R, so its refits
     * really are independent purchases with their own names. Both shapes have to be expressible.
     *
     * <p>Before this, a combination nobody renamed could only be called by the derived fallback,
     * and the shelf showed a fully refitted Star Cruiser as {@code CA3PSF}.
     */
    @Test
    public void theRefitsDoNotRenameTheStarCruiser() {
        ShipSpec base = ShipLibrary.get("ISC", "CA");

        for (String[] era : new String[][] {
                { "3",       "165", "Star Cruiser (Y165)" },
                { "3,P",     "168", "Star Cruiser (Y168)" },
                { "3,P,S",   "170", "Star Cruiser (Y170)" },
                { "3,P,S,F", "179", "Star Cruiser (Y179)" }}) {
            List<String> codes = List.of(era[0].split(","));
            ShipSpec built = RefitResolver.apply(base, codes);

            assertEquals(era[0] + " must still be a CA", "CA", built.type);
            assertEquals(era[0] + " typeName", era[2], built.typeName);
            assertEquals(era[0] + " service year",
                    Integer.parseInt(era[1]), built.serviceYear);
            assertEquals("and it still knows the hull it came from", "CA", built.refitOf);
            assertEquals("and which refits made it",
                    codes.size(), built.appliedRefits.size());
        }
    }

    /**
     * And the base hull SURVIVES being named by its own variants.
     *
     * <p>This is the assertion the guard exists for. {@code registerVariants} writes every named
     * variant into the registry under {@code faction + type}; a variant answering to "CA" would
     * overwrite the Y160 hull under its own key, and every ISC CA in the game would silently
     * become the fully refitted one — at 185 points, with a PPD, in a Y160 battle.
     *
     * <p>{@code ShipLibraryIntegrityTest} would catch the collision, but it reports a duplicate
     * key rather than saying which ship was lost. This says it.
     */
    @Test
    public void namingTheVariantsCaDoesNotReplaceTheHullItRefits() {
        ShipSpec registered = ShipLibrary.get("ISC", "CA");

        assertEquals("the registry must still hold the Y160 original", 142, registered.bpv);
        assertEquals(160, registered.serviceYear);
        assertNull("which is a base hull, not a refit of anything", registered.refitOf);
        assertEquals("no phaser-3s before the Y165 refit",
                0, count(ShipLibrary.createShip(registered), com.sfb.weapons.Phaser3.class));

        assertEquals("and no same-code variant reached the registry",
                Map.of(), ShipLibrary.duplicateKeys());
    }

    /** The five eras price out to the sheet's own deductions: 185 less 6, 15, 10 and 12. */
    @Test
    public void everyEraPricesOutToTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "CA");

        assertEquals("Y160, the bare hull", 142, base.bpv);
        assertEquals("Y165, with PH-3", 148, RefitResolver.apply(base, List.of("3")).bpv);
        assertEquals("Y168, with the PPD", 163,
                RefitResolver.apply(base, List.of("3", "P")).bpv);
        assertEquals("Y170, with plasma-S", 173,
                RefitResolver.apply(base, List.of("3", "P", "S")).bpv);
        assertEquals("Y179, the ship the sheet draws", 185,
                RefitResolver.apply(base, List.of("3", "P", "S", "F")).bpv);
    }

    // ---- R13.18: the Scout, and why its economic value is the one that counts ----

    /**
     * A scout is costed at its ECONOMIC value, not its BPV (FleetValidator), so a hull whose
     * sheet prints a split point value must record both or it is cheap by a third.
     *
     * <p>The ISC SC prints "POINT VALUE = 126/90". Recording only the 90 would leave a 126-point
     * ship spending 90 from a budget, and nothing else in the suite would notice - which is why
     * this is pinned here rather than trusted to the data guard.
     *
     * <p>Also pins that declaring ScoutChannel weapons is ALL it takes to be a scout:
     * {@code Ship.isScout} is simply whether any are present, and everything else - the G24
     * functions, the economic costing, S8.25's free scout slot - follows from that.
     */
    @Test
    public void theScoutIsCostedAtItsEconomicValue() {
        ShipSpec sc = ShipLibrary.get("ISC", "SC");
        assertEquals("the smaller half of 126/90 is the BPV", 90, sc.bpv);
        assertEquals("the larger half is the EPV", 126, sc.epv);

        // A SPLIT VALUE TAKES ITS DEDUCTIONS ON BOTH HALVES. The owner ruled it 2026-10-23,
        // and RefitResolver already carried the same ruling from 2026-10-11 going the other
        // way - a refit adds its cost to the epv as well as the bpv. The SC has no refits so
        // its printed 126/90 stands; its three siblings all needed correcting, and the HSC kept
        // the wrong value for a while because the convention lived in prose in three files
        // instead of in one assertion. This is that assertion.
        for (String[] hull : new String[][] {
                { "CVE", "86", "96" },     // 100/90 less the PL-F refit's 4
                { "SR",  "108", "128" },   // 140/120 less PH-3's 4 and PL-F's 8
                { "HSC", "112", "162" },   // 170/120 less the PL-F refit's 8
                { "SC",  "90",  "126" }}) { // no refits, so as printed
            ShipSpec s2 = ShipLibrary.get("ISC", hull[0]);
            assertEquals(hull[0] + " bpv", Integer.parseInt(hull[1]), s2.bpv);
            assertEquals(hull[0] + " epv", Integer.parseInt(hull[2]), s2.epv);
            assertTrue(hull[0] + " must keep its epv above its bpv", s2.epv > s2.bpv);
        }

        Ship ship = ShipLibrary.createShip(sc);
        assertTrue("four special sensor boxes make it a scout", ship.isScout());
        assertEquals("and there are four of them", 4, ship.getScoutChannels().size());
        assertEquals("which the budget charges at the economic value",
                126, ship.getEconomicBpv());
    }

    // ---- R13.34: the Plasma-G Destroyer, and the letter the rear torpedoes start at ----

    /**
     * R13.34, the DDG — the DD's hull with ONE plasma-G forward where the destroyer carries TWO
     * plasma-F. That single trade is the whole ship: shields, hull, power, crew, probes and
     * every damage track match the DD box for box.
     *
     * <p>Worth pinning because the DDL also carries plasma-G and does NOT make this trade. The
     * leader keeps a PAIR of them and pays elsewhere — a centre APR pair, 30/24 shields, four
     * more crew and no probe launcher at all. Copying either sibling's launcher block onto this
     * hull gives it one torpedo too many, and nothing else in the suite would notice.
     */
    @Test
    public void thePlasmaGDestroyerTradesTwoForwardLaunchersForOne() {
        Ship ddg = ShipLibrary.createShip(ShipLibrary.get("ISC", "DDG"));

        assertEquals("one forward launcher, not the DD's or the DDL's two",
                1, count(ddg, PlasmaLauncher.class));
        PlasmaLauncher forward = (PlasmaLauncher) byDesignator(ddg, "A");
        assertEquals(com.sfb.properties.PlasmaType.G, forward.getLauncherType());
        assertEquals("FP", forward.getArcLabel());
        assertFalse("and it is not rear-firing", forward.isIscRearFiring());

        ShipSpec dd = ShipLibrary.get("ISC", "DD");
        ShipSpec ddg2 = ShipLibrary.get("ISC", "DDG");
        assertEquals("the same chassis underneath", dd.hullBoxes.chull, ddg2.hullBoxes.chull);
        assertEquals("the same APR, where the leader has twice", dd.power.apr, ddg2.power.apr);
        assertEquals("the same crew", dd.crewData.totalCrew, ddg2.crewData.totalCrew);
        assertEquals("and the probe the leader goes without",
                1, ddg2.auxiliary.probes);
    }

    /**
     * The rear-firing pair are B and C here, not the DD's and DDL's C and D. The letters are
     * not decoration: the sheet says "WITHOUT THE PL-F REFIT, DELETE PL-Fs 'B' AND 'C'", and
     * they shift down one because the forward armament is a single launcher rather than a pair.
     *
     * <p>This is exactly the kind of detail a copied refit block gets wrong, and a wrong
     * designator is invisible until someone reads a fire order against the sheet.
     */
    @Test
    public void thePlasmaGDestroyersRearTorpedoesStartAtB() {
        Ship ddg = ShipLibrary.createShip(
                RefitResolver.apply(ShipLibrary.get("ISC", "DDG"), List.of("F")));

        assertEquals("one plasma-G forward and two plasma-F astern",
                3, count(ddg, PlasmaLauncher.class));
        for (String rear : new String[] { "B", "C" })
            assertTrue(rear + " is rear-firing (R13.1C)",
                    ((PlasmaLauncher) byDesignator(ddg, rear)).isIscRearFiring());

        assertEquals("L + LR", byDesignator(ddg, "B").getArcLabel());
        assertEquals("R + RR", byDesignator(ddg, "C").getArcLabel());

        assertTrue("there is no D on this hull",
                ddg.getWeapons().fetchAllWeapons().stream()
                        .noneMatch(w -> "D".equals(w.getDesignator())));
    }

    /**
     * And it prices out to the sheet. POINT VALUE = 94 is a SINGLE number here, not the split
     * the CVE, SC, SR and HSC print, so there is no economic value to record — and recording
     * one would make the ship dearer than the sheet for no reason.
     */
    @Test
    public void thePlasmaGDestroyerPricesOutToTheSheet() {
        ShipSpec base = ShipLibrary.get("ISC", "DDG");

        assertEquals("the Y177 hull, 94 less the 4 for going without PL-F", 90, base.bpv);
        assertEquals(177, base.serviceYear);
        assertEquals("the ship the sheet draws", 94,
                RefitResolver.apply(base, List.of("F")).bpv);
        assertEquals("a single point value leaves no epv to carry", 0, base.epv);
    }

    // ---- R13.29 / R13.29A: the escort frigates, and where their aegis comes from ----

    /**
     * The FFE carries LIMITED aegis from Y170, is an escort, and is therefore also a CASUAL
     * CARRIER — three facts that travel together and are easy to set only two of.
     *
     * <p>J4.62 is the one that catches people: "most carrier escorts" are casual carriers, so a
     * hull declaring ready racks must say so in {@code carrierClass} too. Declaring
     * {@code fighterFacilities} without it fails {@code CasualCarrierFacilitiesTest}, which is
     * exactly how this pair was caught half-finished.
     *
     * <p>The aegis reading changed once. An earlier draft had the hull start with none and the
     * free Y175 refit grant it, reading "INCLUDES LIMITED AEGIS" as part of that refit line. It
     * is a standalone statement about the ship — the FFA opposite lists "INCLUDES Y175 REFIT"
     * and "INCLUDES FULL AEGIS" as two separate lines, which is what settles it.
     */
    @Test
    public void theEscortFrigateGetsItsLimitedAegisFromAFreeY175Refit() {
        ShipSpec base = ShipLibrary.get("ISC", "FFE");
        assertEquals("the sheet lists INCLUDES LIMITED AEGIS against the FFE itself",
                "LIMITED", base.aegis);
        assertTrue("and it is an escort (S8.311/S8.315)", base.isEscort);
        assertEquals("J4.62 makes a hull with ready racks a casual carrier",
                com.sfb.properties.CarrierClass.CASUAL,
                ShipLibrary.createShip(base).getCarrierClass());

        assertTrue("the sheet's +0 Y175 refit is deliberately not modelled: with the aegis on "
                + "the hull it would change nothing, and a refit that changes nothing is refused",
                base.refits == null || base.refits.isEmpty());
    }

    /**
     * The FFA is the same hull built with that refit already in it, so it carries FULL aegis
     * from the start and must NOT also offer the Y175 refit — the sheet says "INCLUDES Y175
     * REFIT", and a buyer offered it again would pay for it twice.
     */
    @Test
    public void theAegisFrigateAlreadyIncludesWhatTheOtherOneBuys() {
        ShipSpec ffa = ShipLibrary.get("ISC", "FFA");
        assertEquals("R13.29A is built with full aegis", "FULL", ffa.aegis);
        assertEquals("at its own point value", 77, ffa.bpv);
        assertTrue("fixture: the Y175 refit must not be on offer here",
                ffa.refits == null || ffa.refits.isEmpty());

        ShipSpec ffe = ShipLibrary.get("ISC", "FFE");
        assertEquals("both are the same hull: 16 shields all round",
                ffe.shields[0], ffa.shields[0]);
        assertEquals("and the same crew", ffe.crewData.totalCrew, ffa.crewData.totalCrew);
    }
}
