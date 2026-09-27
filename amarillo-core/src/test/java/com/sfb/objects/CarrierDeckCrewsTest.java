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
     * CrewSpec.deckCrews is a primitive, so an absent key and a stated zero are the same
     * value — which is harmless here, since no ship states zero deck crews on purpose.
     */
    private static boolean namesDeckCrews(ShipSpec spec) {
        return spec.crewData != null && spec.crewData.deckCrews > 0;
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
