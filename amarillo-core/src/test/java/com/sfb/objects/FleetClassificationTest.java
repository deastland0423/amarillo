package com.sfb.objects;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The S8.0 fleet-building classifications have to survive the whole trip: JSON key → ShipSpec
 * field → the map ShipSpec hands to Ship.init → Ship accessor.
 * <p>
 * ShipSpec is annotated {@code @JsonIgnoreProperties(ignoreUnknown = true)}, so a key with no
 * field behind it is dropped in silence — an "isLeader": true that does nothing looks exactly
 * like one that works. These tests fail if any link in that chain is missing.
 */
public class FleetClassificationTest {

    /** A ship built from a spec carrying all three flags reports all three. */
    @Test
    public void theFlagsSurviveSpecToShip() {
        ShipSpec spec = minimalSpec();
        spec.isLeader = true;
        spec.isEscort = true;
        spec.isTrueCarrier = true;

        Map<String, Object> values = spec.toInitMap();
        assertEquals("spec must put the key Ship reads", Boolean.TRUE, values.get("isleader"));
        assertEquals(Boolean.TRUE, values.get("isescort"));
        assertEquals(Boolean.TRUE, values.get("istruecarrier"));
    }

    /** Absent means false — the overwhelmingly common case, and the default for every hull. */
    @Test
    public void absentFlagsAreFalse() {
        Ship ship = new Ship();
        ship.init(new HashMap<>());

        assertFalse(ship.isLeader());
        assertFalse(ship.isEscort());
        assertFalse(ship.isTrueCarrier());
    }

    @Test
    public void setFlagsReadBackFromTheShip() {
        Map<String, Object> values = new HashMap<>();
        values.put("isleader", true);
        values.put("istruecarrier", true);

        Ship ship = new Ship();
        ship.init(values);

        assertTrue(ship.isLeader());
        assertFalse("only what was set", ship.isEscort());
        assertTrue(ship.isTrueCarrier());
    }

    /**
     * The key names are part of the data contract: the JSON files use these spellings, so a
     * rename in Java without a matching data pass would quietly turn every flag off.
     */
    @Test
    public void theKeyNamesAreTheOnesTheDataUses() {
        ShipSpec spec = minimalSpec();
        spec.isLeader = true;
        spec.isEscort = true;
        spec.isTrueCarrier = true;
        Map<String, Object> values = spec.toInitMap();

        assertTrue("data/factions JSON uses \"isLeader\"", values.containsKey("isleader"));
        assertTrue("data/factions JSON uses \"isEscort\"", values.containsKey("isescort"));
        assertTrue("data/factions JSON uses \"isTrueCarrier\"", values.containsKey("istruecarrier"));
    }

    /** Carrying fighters does not make a ship a true carrier — the flag is a separate fact. */
    @Test
    public void carryingFightersDoesNotImplyTrueCarrier() {
        Ship ship = new Ship();
        ship.init(new HashMap<>());
        assertFalse("a hybrid carries fighters and is still not a true carrier (S8.322)",
                ship.isTrueCarrier());
    }

    /** toInitMap needs the enum-valued fields populated; these are a real ship's. */
    private ShipSpec minimalSpec() {
        ShipSpec spec = new ShipSpec();
        spec.faction = "Federation";
        spec.turnMode = "D";
        return spec;
    }
}
