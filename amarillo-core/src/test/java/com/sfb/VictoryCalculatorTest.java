package com.sfb;

import static org.junit.Assert.*;

import com.sfb.VictoryCalculator.VictoryLevel;
import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for VictoryCalculator — S2.21 scoring and S2.3 victory levels.
 *
 * Fed CA: bpv=125, no epv → economicBpv=125
 * Klingon D7: bpv=121, no epv → economicBpv=121
 * Split-BPV ship: constructed inline with bpv=80, epv=100
 */
public class VictoryCalculatorTest {

    private Ship fedCa;
    private Ship klingonD7;

    @Before
    public void setUp() {
        fedCa = new Ship();
        fedCa.init(FederationShips.getFedCa());
        fedCa.setName("Enterprise");

        klingonD7 = new Ship();
        klingonD7.init(KlingonShips.getD7());
        klingonD7.setName("IKV Doom");
    }

    // -------------------------------------------------------------------------
    // EPV loading
    // -------------------------------------------------------------------------

    @Test
    public void economicBpv_fallsBackToBpvWhenEpvAbsent() {
        assertEquals(125, fedCa.getEconomicBpv());
    }

    @Test
    public void economicBpv_usesEpvWhenPresent() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("bpv", 80);
        spec.put("epv", 100);
        Ship splitBpv = new Ship();
        splitBpv.init(spec);
        assertEquals(100, splitBpv.getEconomicBpv());
        assertEquals(80,  splitBpv.getBpv());
    }

    // -------------------------------------------------------------------------
    // pointsForShip — S2.21 categories
    // -------------------------------------------------------------------------

    @Test
    public void pointsForShip_activeUndamagedShip_scoresZero() {
        assertEquals(0, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_destroyedShip_scores100Pct() {
        fedCa.setBattleStatus(BattleStatus.DESTROYED);
        // 125 * 100% = 125
        assertEquals(125, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_crippledShip_scores50Pct() {
        // Trigger condition C (any excess damage)
        fedCa.getSpecialFunctions().damageExcessDamage();
        assertTrue(fedCa.isCrippled());
        // 125 * 50% = 62.5 → rounds to 63
        assertEquals(63, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_disengagedShip_scores25Pct() {
        fedCa.setBattleStatus(BattleStatus.DISENGAGED);
        // 125 * 25% = 31.25 → rounds to 31
        assertEquals(31, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_capturedShip_scores200Pct() {
        fedCa.setCaptured(true);
        // 125 * 200% = 250
        assertEquals(250, VictoryCalculator.pointsForShip(fedCa));
    }

    // -------------------------------------------------------------------------
    // Highest category wins — captured beats destroyed
    // -------------------------------------------------------------------------

    @Test
    public void pointsForShip_capturedTakesPrecedenceOverDestroyed() {
        fedCa.setCaptured(true);
        fedCa.setBattleStatus(BattleStatus.DESTROYED);
        // 200% wins over 100%
        assertEquals(250, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_destroyedTakesPrecedenceOverCrippled() {
        fedCa.setBattleStatus(BattleStatus.DESTROYED);
        fedCa.getSpecialFunctions().damageExcessDamage(); // also crippled
        // 100% wins over 50%
        assertEquals(125, VictoryCalculator.pointsForShip(fedCa));
    }

    @Test
    public void pointsForShip_crippledTakesPrecedenceOverDisengaged() {
        fedCa.setBattleStatus(BattleStatus.DISENGAGED);
        fedCa.getSpecialFunctions().damageExcessDamage(); // also crippled
        // 50% wins over 25%
        assertEquals(63, VictoryCalculator.pointsForShip(fedCa));
    }

    // -------------------------------------------------------------------------
    // Split BPV — scoring uses economic BPV
    // -------------------------------------------------------------------------

    @Test
    public void pointsForShip_usesEconomicBpvNotCombatBpv() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("bpv", 80);
        spec.put("epv", 100);
        Ship splitShip = new Ship();
        splitShip.init(spec);
        splitShip.setBattleStatus(BattleStatus.DESTROYED);
        // Should use epv=100, not bpv=80
        assertEquals(100, VictoryCalculator.pointsForShip(splitShip));
    }

    // -------------------------------------------------------------------------
    // scorePoints — multiple ships
    // -------------------------------------------------------------------------

    @Test
    public void scorePoints_emptyList_returnsZero() {
        assertEquals(0, VictoryCalculator.scorePoints(Collections.emptyList()));
    }

    @Test
    public void scorePoints_sumsAcrossMultipleShips() {
        fedCa.setBattleStatus(BattleStatus.DESTROYED);     // 125 pts
        klingonD7.setBattleStatus(BattleStatus.DISENGAGED); // 121 * 25% = 30.25 → 30
        int total = VictoryCalculator.scorePoints(Arrays.asList(fedCa, klingonD7));
        assertEquals(125 + 30, total);
    }

    // -------------------------------------------------------------------------
    // S2.24 rounding — 0.500 rounds up, 0.499 rounds down
    // -------------------------------------------------------------------------

    @Test
    public void round_exactHalf_roundsUp() {
        assertEquals(1, VictoryCalculator.round(0.5));
    }

    @Test
    public void round_justBelowHalf_roundsDown() {
        assertEquals(0, VictoryCalculator.round(0.499));
    }

    @Test
    public void round_wholeNumber_unchanged() {
        assertEquals(125, VictoryCalculator.round(125.0));
    }

    // -------------------------------------------------------------------------
    // victoryLevel — S2.3 table
    // -------------------------------------------------------------------------

    @Test public void victoryLevel_500plus_astounding()   { assertEquals(VictoryLevel.ASTOUNDING_VICTORY,  VictoryCalculator.victoryLevel(500, 100)); }
    @Test public void victoryLevel_300to499_decisive()    { assertEquals(VictoryLevel.DECISIVE_VICTORY,    VictoryCalculator.victoryLevel(400, 100)); }
    @Test public void victoryLevel_200to299_substantive() { assertEquals(VictoryLevel.SUBSTANTIVE_VICTORY, VictoryCalculator.victoryLevel(200, 100)); }
    @Test public void victoryLevel_150to199_tactical()    { assertEquals(VictoryLevel.TACTICAL_VICTORY,    VictoryCalculator.victoryLevel(150, 100)); }
    @Test public void victoryLevel_110to149_marginal()    { assertEquals(VictoryLevel.MARGINAL_VICTORY,    VictoryCalculator.victoryLevel(110, 100)); }
    @Test public void victoryLevel_91to109_draw()         { assertEquals(VictoryLevel.DRAW,                VictoryCalculator.victoryLevel(100, 100)); }
    @Test public void victoryLevel_67to90_marginalDef()   { assertEquals(VictoryLevel.MARGINAL_DEFEAT,     VictoryCalculator.victoryLevel(67,  100)); }
    @Test public void victoryLevel_50to66_tacticalDef()   { assertEquals(VictoryLevel.TACTICAL_DEFEAT,     VictoryCalculator.victoryLevel(50,  100)); }
    @Test public void victoryLevel_33to49_brutal()        { assertEquals(VictoryLevel.BRUTAL_DEFEAT,       VictoryCalculator.victoryLevel(33,  100)); }
    @Test public void victoryLevel_20to32_crushing()      { assertEquals(VictoryLevel.CRUSHING_DEFEAT,     VictoryCalculator.victoryLevel(20,  100)); }
    @Test public void victoryLevel_under20_devastating()  { assertEquals(VictoryLevel.DEVASTATING_DEFEAT,  VictoryCalculator.victoryLevel(19,  100)); }

    @Test
    public void victoryLevel_opponentScoredZero_astounding() {
        assertEquals(VictoryLevel.ASTOUNDING_VICTORY, VictoryCalculator.victoryLevel(100, 0));
    }

    @Test
    public void victoryLevel_bothScoredZero_astounding() {
        // Division by zero case — treated as astounding per implementation
        assertEquals(VictoryLevel.ASTOUNDING_VICTORY, VictoryCalculator.victoryLevel(0, 0));
    }
}
