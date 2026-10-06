package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A SQUADRON is eleven fighters and one EW fighter, and a bay is not a squadron.
 * <p>
 * This is the shape the Federation data forced, and it is worth pinning because the obvious
 * reading of a carrier is wrong. The CVA does not fly a squadron per bay: it carries an F-14
 * squadron AND an A-10 squadron, and splits each across BOTH of its twelve-box bays — six and
 * five, five and six. One bay's EW fighter is therefore an F-14E and the other's an A-10E.
 * <p>
 * That is what {@code <role>_ew} exists for. A bay flying one programme names plain
 * {@code ew} and lets the line decide; a bay flying two has to say which. The alternative
 * considered first was a line per bay — the answer that settled the Klingon Z-1 versus Z-D
 * split — and it cannot work here, because a line cannot decide what an EW fighter is when
 * the bay flies two programmes.
 * <p>
 * It also explains the six balcony positions per bay, which had looked arbitrary: six is
 * exactly the A-10s (or F-14s) in one bay. The SSD gives a player enough track to stage half
 * a squadron of one type and launch it in a single impulse (J1.53).
 */
public class FederationSquadronTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private static Ship cva() {
        return ShipLibrary.createShip(ShipLibrary.get("Federation", "CVA"));
    }

    /** Every fighter aboard, by catalogue type. */
    private static Map<String, Integer> fightersByType(Ship ship) {
        Map<String, Integer> byType = new LinkedHashMap<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof com.sfb.objects.shuttles.Fighter)
                    byType.merge(s.getCatalogType(), 1, Integer::sum);
        return byType;
    }

    // ---------------------------------------------------------------- the squadrons

    /**
     * Two complete squadrons: 11 F-14 + 1 F-14E, and 11 A-10 + 1 A-10E. The numbers only come
     * out right when the two bays are added together, which is the whole point.
     */
    @Test
    public void theCvaCarriesTwoWholeSquadronsAcrossItsTwoBays() {
        Map<String, Integer> byType = fightersByType(cva());

        assertEquals("F-14 squadron", Integer.valueOf(11), byType.get("f14"));
        assertEquals("its EW fighter", Integer.valueOf(1), byType.get("f14_e"));
        assertEquals("A-10 squadron", Integer.valueOf(11), byType.get("a10"));
        assertEquals("its EW fighter", Integer.valueOf(1), byType.get("a10_e"));
        assertEquals("and nothing else", 4, byType.size());
        assertEquals("24 fighters, two squadrons of twelve",
                24, byType.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * The CVA flies NO standard-line fighter. Its elite and assault roles both resolve to
     * their own programmes, and if either stopped doing so it would silently fall back to the
     * superiority type (LineEra.typeFor falls back to STANDARD) and seat F-18s instead — a
     * mistake that would look like a working carrier.
     */
    @Test
    public void noF18EverReachesTheCva() {
        Map<String, Integer> byType = fightersByType(cva());

        assertNull("the CVA does not fly the standard line", byType.get("f18"));
        assertNull(byType.get("f18_e"));
        assertNull(byType.get("f4"));
    }

    /** Each bay is a full twelve boxes, and holds BOTH programmes. */
    @Test
    public void eachBayMixesTheTwoProgrammes() {
        Ship ship = cva();
        List<ShuttleBay> fighterBays = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            if (bay.hasBalcony())
                fighterBays.add(bay);
        assertEquals("the two fighter bays", 2, fighterBays.size());

        for (ShuttleBay bay : fighterBays) {
            Map<String, Integer> here = new LinkedHashMap<>();
            for (Shuttle s : bay.getInventory())
                here.merge(s.getCatalogType(), 1, Integer::sum);

            assertEquals("twelve craft in the bay", 12,
                    here.values().stream().mapToInt(Integer::intValue).sum());
            assertTrue("F-14s here: " + here, here.containsKey("f14"));
            assertTrue("A-10s here: " + here, here.containsKey("a10"));
        }
    }

    /**
     * One bay's EW fighter is an F-14E and the other's an A-10E — the fact that made a line
     * per bay impossible and {@code <role>_ew} necessary.
     */
    @Test
    public void theTwoBaysCarryDifferentKindsOfEwFighter() {
        Ship ship = cva();
        List<String> ewTypes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s.getCatalogType() != null && s.getCatalogType().endsWith("_e"))
                    ewTypes.add(s.getCatalogType());

        assertEquals("one EW fighter per squadron", 2, ewTypes.size());
        assertTrue(ewTypes.toString(), ewTypes.contains("f14_e"));
        assertTrue(ewTypes.toString(), ewTypes.contains("a10_e"));
    }

    /** Six positions per bay is six of one type — half a squadron, staged to go at once. */
    @Test
    public void aBaysBalconyHoldsAllOfOneProgrammeInThatBay() {
        Ship ship = cva();
        for (ShuttleBay bay : ship.getShuttles().getBays()) {
            if (!bay.hasBalcony())
                continue;
            int a10s = 0;
            for (Shuttle s : bay.getInventory())
                if ("a10".equals(s.getCatalogType()))
                    a10s++;
            assertTrue("the bay's A-10s (" + a10s + ") must fit its balcony ("
                    + bay.getBalconyPositions() + ")", a10s <= bay.getBalconyPositions());
        }
    }

    // ---------------------------------------------------------------- the role mechanism

    /** J4.463 counts EW fighters; a programme-specific one is still an EW fighter. */
    @Test
    public void ewCountSeesTheProgrammeSpecificSeats() {
        FighterComplement mixed = new FighterComplement("federation-fighter",
                Map.of("elite", 6, "assault", 5, "assault_ew", 1));

        assertEquals("assault_ew is an EW fighter", 1, mixed.ewCount());
        assertEquals(12, mixed.total());
    }

    /** The EW keys are derived from the combat roles, not listed by hand. */
    @Test
    public void everyCombatRoleHasAnEwCounterpart() {
        for (String role : FighterComplement.COMBAT_ROLES) {
            String ew = FighterComplement.ewRoleFor(role);
            assertTrue(role + " has no EW key", FighterComplement.ROLES.contains(ew));
            assertTrue(ew + " must read as an EW role", FighterComplement.isEwRole(ew));
            assertFalse(role + " is not an EW role", FighterComplement.isEwRole(role));
        }
        assertTrue("the generic key is still an EW role",
                FighterComplement.isEwRole(FighterComplement.EW));
    }

    /** EW fighters are seated LAST, whichever programme they belong to (S4.10-S4.12). */
    @Test
    public void everyEwSeatComesAfterEveryCombatSeat() {
        int lastCombat = -1, firstEw = Integer.MAX_VALUE;
        for (int i = 0; i < FighterComplement.ROLES.size(); i++) {
            if (FighterComplement.isEwRole(FighterComplement.ROLES.get(i)))
                firstEw = Math.min(firstEw, i);
            else
                lastCombat = Math.max(lastCombat, i);
        }
        assertTrue("combat roles must all precede the EW ones: " + FighterComplement.ROLES,
                lastCombat < firstEw);
    }
}
