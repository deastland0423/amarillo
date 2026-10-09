package com.sfb.objects;

import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A ship file can say that a hull is a base, and saying so reaches the rule.
 *
 * <h2>What this is guarding</h2>
 * {@link Ship#isBase()} gates G24.135 — a base never blinds its own scout channels when it fires
 * — and the flag existed for some time with no way to set it. {@code ShipSpec} had no field,
 * {@code toMap} wrote no key, and no ship file mentioned it; the only two places it was ever true
 * were two tests that called {@code setBase(true)} by hand. Every base the game might have
 * fielded would have blinded itself like a ship.
 *
 * <p>That is the third field found in this shape. {@code crewQuality} had an enum, a DTO and two
 * rule sites with no data entry point, so every ship in the game was NORMAL. {@code epv} was a
 * primitive int that could not say "unset", so the fallback meant to give a scout its combat BPV
 * never ran and two of them were free. The pattern is a flag the rules READ and the data cannot
 * WRITE, and nothing fails while it goes unnoticed — the feature simply never happens.
 *
 * <p>So this test deliberately goes the whole way from a JSON key to the rule, rather than
 * asserting the field exists. A field that exists is exactly what the bug looked like.
 */
public class BaseFlagTest {

    /** The least a spec needs before toInitMap will convert it. */
    private static ShipSpec minimal() {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Federation";
        spec.type = "BS";
        spec.name = "Test";
        spec.turnMode = "D";
        return spec;
    }

    /** Build a ship the way the library does, from a spec, with the base flag as given. */
    private static Ship shipWith(boolean isBase) {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Federation";
        spec.type = isBase ? "BS" : "CA";
        spec.name = isBase ? "Test Base" : "Test Ship";
        spec.shields = new int[] { 10, 10, 10, 10, 10, 10 };
        spec.turnMode = "D";
        spec.isBase = isBase;
        Map<String, Object> values = spec.toInitMap();
        Ship ship = new Ship();
        ship.init(values);
        return ship;
    }

    @Test
    public void aSpecThatSaysBaseProducesABase() {
        assertTrue("isBase: true in the data must reach Ship.isBase()", shipWith(true).isBase());
    }

    @Test
    public void aSpecThatDoesNotSayItIsNotABase() {
        assertFalse("and the default is a ship", shipWith(false).isBase());
    }

    /** The key really is written — a toInitMap that dropped it would fail the two tests above too. */
    @Test
    public void theFlagTravelsUnderTheKeyShipReads() {
        ShipSpec spec = minimal();
        spec.isBase = true;
        assertTrue("toInitMap must write the key Ship.init looks for",
                Boolean.TRUE.equals(spec.toInitMap().get("isbase")));

        assertNull("and must not write it for an ordinary hull",
                minimal().toInitMap().get("isbase"));
    }

    /**
     * And the rule it exists for: G24.135, a base does not blind its own channels.
     * <p>
     * This is the assertion that would have caught the original gap. The two above only prove a
     * field is carried; this one proves carrying it changes what the game does.
     */
    @Test
    public void aBaseDoesNotBlindItsOwnScoutChannels() {
        Ship base = shipWith(true);
        Ship ship = shipWith(false);
        for (Ship s : new Ship[] { base, ship }) {
            ScoutChannel c = new ScoutChannel();
            c.setPowered(true);
            s.getWeapons().addWeapon(c);
        }

        assertNull("G24.135: a base blinds nothing", base.blindOneScoutChannel(1));
        assertNotNull("but a ship blinds a channel", ship.blindOneScoutChannel(1));
    }
}
