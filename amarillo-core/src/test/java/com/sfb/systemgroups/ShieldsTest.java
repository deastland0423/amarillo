package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for Shields — specifically the distinction between base strength
 * (visible to all players) and reinforced strength (visible only to owner).
 *
 * getBaseShieldStrength() — current shield boxes, no reinforcement
 * getShieldStrength()     — current shield boxes + specific reinforcement
 */
public class ShieldsTest {

    private Shields shields;

    @Before
    public void setUp() {
        Map<String, Object> values = new HashMap<>();
        values.put("shield1", 30);
        values.put("shield2", 20);
        // shields 3-6 default to 0
        shields = new Shields();
        shields.init(values);
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    public void baseStrength_matchesCurrentValueInitially() {
        assertEquals(30, shields.getBaseShieldStrength(1));
        assertEquals(20, shields.getBaseShieldStrength(2));
    }

    @Test
    public void shieldStrength_matchesBaseWithNoReinforcement() {
        assertEquals(shields.getBaseShieldStrength(1), shields.getShieldStrength(1));
        assertEquals(shields.getBaseShieldStrength(2), shields.getShieldStrength(2));
    }

    // -------------------------------------------------------------------------
    // Specific reinforcement — only visible to owner
    // -------------------------------------------------------------------------

    @Test
    public void baseStrength_unchangedAfterSpecificReinforcement() {
        shields.reinforceShield(1, 10);
        assertEquals("Enemy-visible base strength must not include reinforcement",
                30, shields.getBaseShieldStrength(1));
    }

    @Test
    public void shieldStrength_includesSpecificReinforcement() {
        shields.reinforceShield(1, 10);
        assertEquals("Owner-visible strength must include specific reinforcement",
                40, shields.getShieldStrength(1));
    }

    @Test
    public void reinforcedAndBaseStrengthsDiverge() {
        shields.reinforceShield(1, 8);
        int ownerView = shields.getShieldStrength(1);
        int enemyView = shields.getBaseShieldStrength(1);
        assertEquals(38, ownerView);
        assertEquals(30, enemyView);
        assertTrue("Owner sees more than enemy when reinforced", ownerView > enemyView);
    }

    @Test
    public void reinforcement_doesNotAffectOtherShields() {
        shields.reinforceShield(1, 10);
        // Shield 2 is unreinforced — both views should match
        assertEquals(shields.getBaseShieldStrength(2), shields.getShieldStrength(2));
    }

    // -------------------------------------------------------------------------
    // Damage — base strength tracks actual shield boxes
    // -------------------------------------------------------------------------

    @Test
    public void damageReducesBaseStrength() {
        shields.damageShield(1, 10);
        assertEquals(20, shields.getBaseShieldStrength(1));
    }

    @Test
    public void damageFullyAbsorbedByReinforcement_baseStrengthUnchanged() {
        shields.reinforceShield(1, 10);
        // 5 damage falls entirely within the 10-point reinforcement buffer
        shields.damageShield(1, 5);
        assertEquals("Shield boxes intact when reinforcement absorbs all damage",
                30, shields.getBaseShieldStrength(1));
    }

    @Test
    public void damageExceedsReinforcement_baseStrengthReduced() {
        shields.reinforceShield(1, 5);
        // 8 damage: 5 absorbed by reinforcement, 3 hits shield boxes
        shields.damageShield(1, 8);
        assertEquals(27, shields.getBaseShieldStrength(1));
    }

    @Test
    public void damageWithNoReinforcement_baseAndStrengthBothReduce() {
        shields.damageShield(1, 12);
        assertEquals(18, shields.getBaseShieldStrength(1));
        assertEquals(18, shields.getShieldStrength(1));
    }

    // -------------------------------------------------------------------------
    // Max shield strength
    // -------------------------------------------------------------------------

    @Test
    public void maxShieldStrength_unchangedAfterDamage() {
        shields.damageShield(1, 10);
        assertEquals("Max shield not affected by combat damage", 30, shields.getMaxShieldStrength(1));
    }

    @Test
    public void maxShieldStrength_unchangedAfterReinforcement() {
        shields.reinforceShield(1, 10);
        assertEquals(30, shields.getMaxShieldStrength(1));
    }
}
