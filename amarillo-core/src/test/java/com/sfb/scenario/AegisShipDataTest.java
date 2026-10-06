package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.properties.AegisLevel;
import com.sfb.weapons.Weapon;

/**
 * Which ships actually carry aegis, read from the ship files (D13.0).
 * <p>
 * A guard on the DATA rather than the mechanism, and only where the data is a RULING. Aegis is
 * declared per hull and nothing computes it, so the ship file is the single source of truth: a
 * new escort needs nothing but its own {@code "aegis"} line to work.
 * <p>
 * There is deliberately NO roster of hulls that may carry aegis. One was written and removed
 * the same day: D13.0 says most carrier escorts have the system, so any such list becomes a
 * second place to edit for every correct addition, and a guard that fires on good work is a
 * chore rather than a safety net. Nobody types {@code "aegis": "FULL"} by accident.
 * <p>
 * The Klingon D5 is the one the rulebook singles out: D13.0 says aegis "was almost never used
 * on ships other than carrier escorts (the Klingon D5 being an exception)", and D13.22 names it
 * again as the example of a hull whose aegis cannot control everything. Both halves are pinned
 * here, because both are owner rulings rather than anything derivable.
 */
public class AegisShipDataTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private Ship ship(String faction, String type) {
        ShipSpec spec = ShipLibrary.get(faction, type);
        assertNotNull(faction + " " + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    /**
     * All three CW hulls carry LIMITED aegis — including the D5L, despite its Y175 service
     * year. That is the owner's ruling and it is the reason the engine never infers a level
     * from a date: D13.24 puts full aegis at 1 Jan Y175, so a year-based guess would have made
     * this hull full and been wrong. The escort version of the D5 does have full aegis and is
     * not in the game yet.
     */
    @Test
    public void everyD5VariantHasLimitedAegis() {
        for (String type : List.of("D5", "D5C", "D5L"))
            assertEquals(type + " should have limited aegis",
                    AegisLevel.LIMITED, ship("Klingon", type).getAegisFitted());
    }

    /** D13.22's exception: a D5's aegis reaches its ADDs and its four phaser-3s, nothing else. */
    @Test
    public void aD5sAegisReachesOnlyItsAddsAndPhaser3s() {
        for (String type : List.of("D5", "D5C", "D5L")) {
            Ship d5 = ship("Klingon", type);
            int controlled = 0, excluded = 0;
            for (Weapon w : d5.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof com.sfb.weapons.DirectFire))
                    continue;
                if (d5.aegisMayControl(w)) {
                    controlled++;
                    assertTrue(type + ": " + w.getName() + " should not be aegis-controlled",
                            List.of("ADD", "Phaser3").contains(w.getType()));
                } else {
                    excluded++;
                }
            }
            assertEquals(type + " should offer its aegis two ADDs and four phaser-3s",
                    6, controlled);
            assertTrue(type + " should have direct-fire weapons its aegis cannot touch",
                    excluded > 0);
        }
    }

    /** A hull with no declaration gets D13.22's default: aegis controls every direct-fire gun. */
    @Test
    public void anUnrestrictedHullControlsEverything() {
        Ship d7 = ship("Klingon", "D7");

        assertEquals("the D7 has no aegis at all", AegisLevel.NONE, d7.getAegisFitted());
        assertTrue("and so declares no restriction", d7.getAegisWeaponTypes().isEmpty());
        for (Weapon w : d7.getWeapons().fetchAllWeapons())
            assertTrue("an unrestricted hull controls " + w.getName(), d7.aegisMayControl(w));
    }
}
