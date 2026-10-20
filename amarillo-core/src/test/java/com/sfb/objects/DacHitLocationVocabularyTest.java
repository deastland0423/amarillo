package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * Every {@code dacHitLocation} in the ship data must be a column the DAC actually has.
 *
 * <h2>Why this exists</h2>
 * A weapon names the damage column that destroys it — a scout channel standing in for a heavy
 * weapon takes {@code "torp"} hits, one standing in for a phaser takes {@code "phaser"} hits
 * (G24.17: "destroyed on the hits for the weapons they replaced"). The string is matched against
 * the DAC by name, so a value the DAC does not know simply never matches.
 *
 * <p>That failure is <b>silent and favourable</b>, which is the worst combination: the system
 * is not immune in any way the file admits to, it is just never selected. Nothing throws, no
 * test fails, and the ship quietly keeps a system every other hull would lose.
 *
 * <p>Written after exactly that happened. The Orion LRS was entered with
 * {@code "dacHitLocation": "torpedo"}, which reads perfectly and is wrong — the DAC's column is
 * {@code "torp"}, and {@code DacPriority} asks for it by that name. Two values in a dataset of
 * 124 were the only ones misspelt, and both were new.
 */
public class DacHitLocationVocabularyTest {

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    /**
     * The DAC's own column names, taken from the table rather than restated here: a list typed
     * out again would drift from the table it is meant to police.
     */
    private static Set<String> dacColumns() {
        Set<String> names = new java.util.HashSet<>();
        // 2d6, so the table's lines are rolls 2 through 12. Each line is walked by excluding
        // what has already been seen, because a non-special entry stays available for ever and
        // would otherwise be returned on every call.
        for (int roll = 2; roll <= 12; roll++) {
            com.sfb.utilities.DAC dac = new com.sfb.utilities.DAC();
            Set<String> seen = new java.util.HashSet<>();
            for (String hit = dac.fetchNextHitExcludingAll(roll, seen);
                    hit != null;
                    hit = dac.fetchNextHitExcludingAll(roll, seen))
                seen.add(hit);
            names.addAll(seen);
        }
        return names;
    }

    @Test
    public void everyDeclaredHitLocationIsAColumnTheDacHas() {
        Set<String> known = dacColumns();
        assertFalse("fixture: the DAC should yield its column names", known.isEmpty());

        List<String> wrong = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.weapons == null)
                continue;
            for (ShipSpec.WeaponSpec w : spec.weapons) {
                if (w.dacHitLocation == null || w.dacHitLocation.isBlank())
                    continue;
                if (!known.contains(w.dacHitLocation))
                    wrong.add(spec.faction + "/" + spec.type + " " + w.type + " "
                            + w.designator + " is destroyed on '" + w.dacHitLocation
                            + "', which is not a DAC column");
            }
        }
        assertTrue(String.join("; ", wrong) + "  [DAC columns: " + known + "]", wrong.isEmpty());
    }
}
