package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A ship that carries fighters must say how many deck crews it has.
 * <p>
 * J4.814 gives every ordinary ship two deck crews, and that default is written into
 * {@code Crew.init} — so a carrier whose file forgets the number does not fail, it quietly
 * sails with two. Since rearming spends one crew per two fusion charges (J4.833), two crews
 * reload exactly one Stinger a turn no matter how many the ship carries: a nine-fighter
 * Ranger would take nine turns to turn its squadron around instead of one. Nothing in the
 * game says so, no test fails, and the ship just fights worse than its SSD.
 * <p>
 * Found the day rearming landed (2026-09-26), when the Hydran LN was the last carrier in the
 * library still taking the default. Every other one names the count, and all of them happen
 * to name one crew per fighter — but the counts come from Annex #7G, which we do not have in
 * the repository, so this guard checks only that a decision was RECORDED. Getting the number
 * right stays the data's job; getting it stated is the build's.
 */
public class CarrierDeckCrewsTest {

    private static final File FACTIONS = new File("../data/factions");

    @Test
    public void everyShipThatCarriesFightersNamesItsDeckCrews() throws Exception {
        List<String> silent = new ArrayList<>();
        int carriers = 0;

        for (File faction : dirs(FACTIONS)) {
            for (File f : jsonFiles(faction)) {
                ShipSpec spec = ShipSpec.fromJson(f);
                if (spec == null)
                    continue;
                Ship ship = ShipLibrary.createShip(spec);
                int fighters = fightersAboard(ship);
                if (fighters == 0)
                    continue;
                carriers++;
                if (!namesDeckCrews(spec))
                    silent.add(faction.getName() + "/" + f.getName() + " carries " + fighters
                            + " fighter(s) and falls back to the J4.814 default of "
                            + ship.getCrew().getDeckCrews());
            }
        }

        assertTrue("the library should have carriers in it", carriers >= 10);
        assertTrue("a carrier that does not name its deck crews silently gets the two J4.814"
                + " gives an ordinary ship, which reloads one fighter a turn however many it"
                + " carries (J4.833):\n  " + String.join("\n  ", silent), silent.isEmpty());
    }

    /**
     * A hull that declares a {@code carrierClass} names its deck crews, whether or not it is
     * carrying fighters of its own.
     *
     * <p><b>Why the test above cannot see this.</b> That one asks "does this ship carry fighters?",
     * and a CASUAL carrier by definition does not — its bays hold admin shuttles, and the facilities
     * are there to service fighters belonging to other ships in the fleet (J4.62/J4.621). J4.814 and
     * J4.897 still give it one deck crew per ready rack with a minimum of two, precisely because it
     * does that work. So "carries fighters" and "has fighter facilities" are different questions and
     * the first one misses every casual carrier.
     *
     * <p>Found by eye on 2026-10-06: the Federation DWA, a CASUAL carrier with three admin shuttles
     * and no {@code deckCrews}, among 90 hulls that declare a carrierClass and 89 that got it right.
     * It would have serviced nothing and reported the ordinary ship's two by default, with no error
     * anywhere — the same silence as the rest of this file's subject.
     */
    @Test
    public void everyHullWithFighterFacilitiesNamesItsDeckCrews() throws Exception {
        List<String> silent = new ArrayList<>();
        int facilities = 0;

        for (File faction : dirs(FACTIONS)) {
            for (File f : jsonFiles(faction)) {
                ShipSpec spec = ShipSpec.fromJson(f);
                if (spec == null || spec.carrierClass == null)
                    continue;
                facilities++;
                if (!namesDeckCrews(spec))
                    silent.add(faction.getName() + "/" + f.getName() + " is a "
                            + spec.carrierClass + " carrier and names no deckCrews");
            }
        }

        assertTrue("the library should have carrier-class hulls in it", facilities >= 20);
        assertTrue("a hull with fighter facilities services fighters whether or not it carries any"
                + " (J4.62/J4.621), and J4.814 gives it a crew per ready rack:\n  "
                + String.join("\n  ", silent), silent.isEmpty());
    }

    /**
     * The J4.814 default is right for a ship that is not a carrier — stated as a test so that
     * an attempt to make the number mandatory everywhere fails here, not in eleven data files.
     */
    @Test
    public void aShipWithNoFightersIsLeftWithTheDefaultTwo() throws Exception {
        Ship ca = ShipLibrary.createShip(
                ShipSpec.fromJson(new File(FACTIONS, "federation/ca.json")));

        assertEquals("no fighters aboard a plain CA", 0, fightersAboard(ca));
        assertEquals("two crews for scatter-pack loading and the rest of J4.81's work",
                2, ca.getCrew().getDeckCrews());
    }

    private static int fightersAboard(Ship ship) {
        int fighters = 0;
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter)
                    fighters++;
        return fighters;
    }

    /**
     * Whether the FILE states a count, as opposed to the ship ending up with the default.
     * <p>
     * {@code CrewSpec.deckCrews} is an {@code Integer}, so this asks the real question. It used
     * to be a primitive and this read {@code > 0}, which answered "states a count above zero" —
     * indistinguishable from silence, and the same confusion that let {@code "deckCrews": 0}
     * load as two.
     */
    private static boolean namesDeckCrews(ShipSpec spec) {
        return spec.crewData != null && spec.crewData.deckCrews != null;
    }

    private static File[] dirs(File root) {
        File[] d = root.listFiles(File::isDirectory);
        return d == null ? new File[0] : d;
    }

    private static File[] jsonFiles(File dir) {
        File[] f = dir.listFiles(n -> n.getName().endsWith(".json"));
        return f == null ? new File[0] : f;
    }
}
