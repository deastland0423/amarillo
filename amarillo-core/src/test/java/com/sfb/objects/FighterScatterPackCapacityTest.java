package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * A fighter's scatter-pack capacity is the capacity of its drone rails (FD7.11, FD7.44).
 * <p>
 * The owner's rule, 2026-10-02: <b>each STANDARD rail is worth 1, each SPECIAL rail 1, and each
 * LIGHT rail 0.5.</b> A light rail takes a single type-VI drone (J4.232) and a type-VI is half a
 * drone space, which is where the half comes from.
 * <p>
 * This is a GUARD on data that had drifted eight times in thirty rows before it existed, always
 * the same way: the rails changed across an era or a refit and the figure stayed behind. The
 * Kzinti TADS and TADSC kept the TAAS's 3 after gaining SPECIAL rails; the DASC and the Klingon
 * Z-DC kept the DAS's 1 after the C-refit (J4.232) turned their light rails standard. Nothing
 * noticed, because a scatter-pack capacity is only wrong when somebody loads one.
 * <p>
 * <b>EW fighters are exempt, and that is the rule rather than an excuse.</b> An EW pod rides on a
 * standard drone rail and can ride on no other kind (J4.2312), so an EW variant's standard rails
 * are spoken for and its declared capacity already accounts for them. Deriving it would overstate
 * every EW fighter in the game.
 */
public class FighterScatterPackCapacityTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** FD7.44 as the owner states it: standard 1, special 1, light a half. */
    private static double capacityOf(DroneRail.DroneRailType type) {
        switch (type) {
            case LIGHT:    return 0.5;
            case STANDARD: return 1.0;
            case SPECIAL:  return 1.0;
            case HEAVY:    return 1.0;
            default:       return 0.0;
        }
    }

    @Test
    public void everyNonEwFightersScatterPackCapacityMatchesItsRails() {
        List<String> problems = new ArrayList<>();

        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            // J4.2312: an EW pod rides on a standard rail and can ride on no other kind, so
            // an EW variant's standard rails are spoken for. fixedEwPods counts too: those are
            // built in rather than racked, but either way the rail is not free for a drone.
            if (entry.loadout.ewPods() + entry.loadout.fixedEwPods() > 0)
                continue;

            double derived = 0;
            int rails = 0;
            for (Weapon w : f.getWeapons().fetchAllWeapons())
                if (w instanceof DroneRail rail) {
                    derived += capacityOf(rail.getRailType());
                    rails++;
                }

            int expected = (int) derived;
            if (entry.scatterPackSize != expected)
                problems.add(entry.type + ": " + rails + " rails worth " + derived
                        + " but scatterPackSize is " + entry.scatterPackSize);
        }

        assertTrue("scatter-pack capacity must equal the rails' capacity"
                + " (STANDARD 1, SPECIAL 1, LIGHT 0.5):\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }

    /**
     * A fighter with no drone rails at all cannot be a scatter pack, which the rule gives for
     * free: all four Hydran Stingers carry fusions and no rails, so they derive to zero.
     */
    @Test
    public void aFighterWithNoRailsCannotBeAScatterPack() {
        int checked = 0;
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            boolean anyRail = f.getWeapons().fetchAllWeapons().stream()
                    .anyMatch(w -> w instanceof DroneRail);
            if (anyRail)
                continue;
            assertEquals(entry.type + " has no drone rails, so it holds no drones",
                    0, entry.scatterPackSize);
            assertFalse(entry.type + " must not qualify as a scatter pack (FD7.11)",
                    entry.canScatterPack());
            checked++;
        }
        assertTrue("fixture: the Hydran Stingers should be railless", checked >= 4);
    }
}
