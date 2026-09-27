package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.CarrierClass;
import com.sfb.systemgroups.ShuttleBay;

/**
 * What kind of carrier a ship is, and why that is not the same question as whether a fleet
 * list may field it alone.
 * <p>
 * J4.61 fully capable carriers and J4.62 casual carriers differ in what they can DO — buy
 * extra deck crews, lend EW to their fighters, take S4.1's weapon status provisions. S8.315
 * asks something else entirely: must this ship be bought with escorts? The Hydrans are the
 * case that forces them apart. A Ranger has a full carrier's apparatus and needs no escort
 * group, so one flag serving both questions had to lie about one of them — and did, until
 * 2026-09-27: `isTrueCarrier` was the escort test, and the weapon status code, having nothing
 * else to ask, keyed off "is there a fighter in a box".
 */
public class CarrierClassTest {

    private static final File FACTIONS = new File("../data/factions");

    private static int fightersAboard(Ship ship) {
        int fighters = 0;
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter)
                    fighters++;
        return fighters;
    }

    private static Ship shipAt(String path) throws Exception {
        return ShipLibrary.createShip(ShipSpec.fromJson(new File(FACTIONS, path)));
    }

    /**
     * A ship that carries fighters must say what kind of carrier it is.
     * <p>
     * The default is NONE, which silently withholds every carrier provision — no fighter
     * would be armed by weapon status, no patrol could be posted — and the symptom is a
     * carrier that simply does nothing special. Same shape as the deck crew guard next door:
     * a decision the data has to record rather than fall into.
     */
    @Test
    public void everyShipThatCarriesFightersDeclaresItsCarrierClass() throws Exception {
        List<String> silent = new ArrayList<>();
        int carriers = 0;

        for (File faction : dirs(FACTIONS)) {
            for (File f : jsonFiles(faction)) {
                ShipSpec spec = ShipSpec.fromJson(f);
                if (spec == null)
                    continue;
                Ship ship = ShipLibrary.createShip(spec);
                if (fightersAboard(ship) == 0)
                    continue;
                carriers++;
                if (ship.getCarrierClass() == CarrierClass.NONE)
                    silent.add(faction.getName() + "/" + f.getName() + " carries "
                            + fightersAboard(ship) + " fighter(s) and declares no carrierClass");
            }
        }

        assertTrue("the library should have carriers in it", carriers >= 10);
        assertTrue("a ship carrying fighters that does not say what kind of carrier it is gets"
                + " none of the carrier provisions, and nothing says so:\n  "
                + String.join("\n  ", silent), silent.isEmpty());
    }

    @Test
    public void aHydranFighterShipIsACapableCarrierThatNeedsNoEscorts() throws Exception {
        Ship rn = shipAt("hydran/rn.json");

        assertEquals("J4.623: most Hydran ships with fighters are carriers in full",
                CarrierClass.CAPABLE, rn.getCarrierClass());
        assertTrue(rn.getCarrierClass().isCarrier());
        assertFalse("but it is fielded without an escort group (S8.315)", rn.isTrueCarrier());
    }

    @Test
    public void aKzintiCvIsBothAtOnce() throws Exception {
        Ship cv = shipAt("kzinti/cv.json");

        assertEquals(CarrierClass.CAPABLE, cv.getCarrierClass());
        assertTrue("a purpose-built carrier needs its escorts (S8.315)", cv.isTrueCarrier());
    }

    @Test
    public void aShipWithNoFightersIsNoKindOfCarrier() throws Exception {
        Ship ca = shipAt("federation/ca.json");

        assertEquals(CarrierClass.NONE, ca.getCarrierClass());
        assertFalse(ca.getCarrierClass().isCarrier());
        assertFalse(ca.isTrueCarrier());
    }

    @Test
    public void aCasualCarrierIsNotGivenTheCarrierProvisions() {
        // J4.62: fighters aboard, none of the apparatus. Nothing in the library is one yet —
        // the first will be a Federation ship carrying a couple — so this states the ruling
        // rather than reading it off a file.
        assertFalse("S4.1 extends its provisions to J4.61 carriers and Hydrans, not to these",
                CarrierClass.CASUAL.isCarrier());
        assertFalse(CarrierClass.NONE.isCarrier());
        assertTrue(CarrierClass.CAPABLE.isCarrier());
    }

    @Test
    public void anUnreadableCarrierClassReadsAsNone() {
        assertEquals(CarrierClass.NONE, CarrierClass.from(null));
        assertEquals(CarrierClass.NONE, CarrierClass.from("hybrid-ish"));
        assertEquals(CarrierClass.CAPABLE, CarrierClass.from("capable"));
        assertEquals(CarrierClass.CASUAL, CarrierClass.from(" CASUAL "));
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
