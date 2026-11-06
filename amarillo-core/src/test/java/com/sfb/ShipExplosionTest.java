package com.sfb;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.utilities.MapUtils;

/**
 * (D5.0) A ship destroyed in combat explodes, and the blast hits everything around it.
 *
 * <p>The fixture is deliberately built on the real chart numbers rather than invented ones,
 * because two of them straddle (D5.41)'s threshold exactly: the Federation FFG explodes for NINE
 * and so cannot reach an adjacent hex at all, while the Klingon F5 explodes for TEN and can. A
 * test written with made-up strengths would still pass if the comparison were {@code > 10} or
 * {@code >= 9}; these two do not.
 *
 * <p>Nothing here runs the impulse engine. {@code cleanupDestroyedShips} is called directly, which
 * is the step that explodes a wreck before removing it, so no dice are involved and the blast
 * damage is exact.
 */
public class ShipExplosionTest {

    /** The exploding ship's hex, and the victim's, one hex away. */
    private static final Location GROUND_ZERO = new Location(10, 10);
    private static final Location NEXT_DOOR   = new Location(10, 11);

    private Game game;

    @Before
    public void setUp() {
        game = new Game();
        assertEquals("fixture: the two hexes must actually be adjacent",
                1, MapUtils.getRange(GROUND_ZERO, NEXT_DOOR));
    }

    private Ship ship(Map<String, Object> spec, String name, Location where) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(where);
        // Facing 1 is bearing A, straight "up" the column -- so a ship at NEXT_DOOR faces
        // GROUND_ZERO and shield #1 is the one looking at the explosion.
        s.setFacing(1);
        game.getShips().add(s);
        return s;
    }

    /** Destroy {@code doomed} and run the step that explodes and removes it. */
    private void destroy(Ship doomed) {
        doomed.setBattleStatus(BattleStatus.DESTROYED);
        game.cleanupDestroyedShips();
    }

    private String log() {
        return String.join("\n", game.internalDamageLog());
    }

    // ------------------------------------------------------------------ D5.41, the radius

    /**
     * (D5.41) is a threshold at ten, with nothing in between: "If the BES is nine points or less,
     * the explosion affects only the hex containing the exploding unit(s). If the BES is ten
     * points or more it affects the hex containing the exploding unit(s) and the six surrounding
     * hexes (radius one)."
     */
    @Test
    public void theRadiusTurnsOverAtExactlyTen() {
        assertEquals("one point still only clears its own hex", 0, ExplosionResolver.blastRadius(1));
        assertEquals("nine is the top of the inner case", 0, ExplosionResolver.blastRadius(9));
        assertEquals("ten reaches the ring", 1, ExplosionResolver.blastRadius(10));
        assertEquals("and so does everything above it", 1, ExplosionResolver.blastRadius(42));
    }

    /** A cruiser's 18 points reach the next hex, and land on the shield facing the wreck. */
    @Test
    public void aDestroyedCruiserHitsTheAdjacentShipOnItsFacingShield() {
        Ship doomed = ship(FederationShips.getFedCa(), "Lexington", GROUND_ZERO);
        Ship victim = ship(FederationShips.getFedCa(), "Bystander", NEXT_DOOR);

        assertEquals("fixture: the CA's chart strength", Integer.valueOf(18),
                doomed.getExplosionStrength());
        int before = victim.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals("shield #1 faces the explosion and takes the full strength",
                before - 18, victim.getShields().getShieldStrength(1));
        for (int n = 2; n <= 6; n++) {
            assertEquals("shield #" + n + " is not facing the explosion and must be untouched",
                    victim.getShields().getBaseShieldStrength(n),
                    victim.getShields().getShieldStrength(n));
        }
        assertTrue("the log should name the explosion", log().contains("Lexington EXPLODES"));
        assertFalse("and the wreck is removed from play", game.getShips().contains(doomed));
    }

    /**
     * The FFG's nine points cannot cross a hex side — but they do hit what is standing in the
     * same hex.
     */
    @Test
    public void nineOrLessStaysInItsOwnHex() {
        Ship doomed    = ship(FederationShips.getFedFfg(), "Perry", GROUND_ZERO);
        Ship sameHex   = ship(FederationShips.getFedCa(), "Alongside", GROUND_ZERO);
        Ship adjacent  = ship(FederationShips.getFedCa(), "NextDoor", NEXT_DOOR);

        assertEquals("fixture: the FFG is the chart's nine-point hull", Integer.valueOf(9),
                doomed.getExplosionStrength());
        int sameHexBefore  = sameHex.getShields().getShieldStrength(1);
        int adjacentBefore = adjacent.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals("the ship sharing the hex takes all nine",
                sameHexBefore - 9, sameHex.getShields().getShieldStrength(1));
        assertEquals("the ship one hex away takes nothing at all",
                adjacentBefore, adjacent.getShields().getShieldStrength(1));
        assertTrue("the log should say the blast stayed put",
                log().contains("its own hex only"));
    }

    /** And the F5's ten, one point more, does cross it. The other half of the threshold. */
    @Test
    public void tenReachesTheSurroundingHexes() {
        Ship doomed = ship(KlingonShips.getF5(), "F5", GROUND_ZERO);
        Ship victim = ship(FederationShips.getFedCa(), "NextDoor", NEXT_DOOR);

        assertEquals("fixture: the F5 is the chart's ten-point hull", Integer.valueOf(10),
                doomed.getExplosionStrength());
        int before = victim.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals("one point more than the FFG, and it crosses the hex side",
                before - 10, victim.getShields().getShieldStrength(1));
    }

    // ------------------------------------------------------------------ D5.13, who is affected

    /**
     * The reason this rule is worth having: focused fire on a small ship clears the drones and
     * fighters around it.
     *
     * <p>Owner, 2026-11-06: "I've focused fire on a small ship in order to destroy nearby drones
     * and fighters with the explosion." (D5.13) says seeking weapons do not EXPLODE when
     * destroyed; it does not say they are not hurt by an explosion.
     */
    @Test
    public void dronesCaughtInTheBlastAreKilled() {
        Ship doomed = ship(FederationShips.getFedCa(), "Lexington", GROUND_ZERO);

        Drone chasing = new Drone(DroneType.TypeI);
        chasing.setName("Drone-1");
        chasing.setLocation(NEXT_DOOR);
        game.getSeekers().add(chasing);

        Drone inTheHex = new Drone(DroneType.TypeI);
        inTheHex.setName("Drone-2");
        inTheHex.setLocation(GROUND_ZERO);
        game.getSeekers().add(inTheHex);

        destroy(doomed);

        assertFalse("the drone one hex out is gone", game.getSeekers().contains(chasing));
        assertFalse("so is the one in the hex", game.getSeekers().contains(inTheHex));
    }

    /**
     * (D5.13): "small ground bases (P2.76) DO NOT explode". The chart says it in the data — every
     * ground base is listed at zero — so a strength of zero must produce no blast rather than a
     * blast of nothing.
     */
    @Test
    public void aStrengthOfZeroSetsOffNothing() {
        Map<String, Object> groundBase = FederationShips.getFedCa();
        groundBase.put("explosionstrength", 0);
        Ship doomed = ship(groundBase, "GroundBase", GROUND_ZERO);
        Ship victim = ship(FederationShips.getFedCa(), "NextDoor", NEXT_DOOR);

        assertFalse("a zero-strength hull does not explode", doomed.explodes());
        int before = victim.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals(before, victim.getShields().getShieldStrength(1));
        assertFalse("and nothing is reported", log().contains("EXPLODES"));
    }

    /**
     * A hull nobody has read a strength for must not explode for zero either — it must not
     * explode at all, which is the same outcome by a different route and the reason the field is
     * boxed. Only the Hydran GEN-F is in this state today.
     */
    @Test
    public void aHullWithNoChartNumberDoesNotExplode() {
        Map<String, Object> unread = FederationShips.getFedCa();
        unread.remove("explosionstrength");
        Ship doomed = ship(unread, "Unread", GROUND_ZERO);
        Ship victim = ship(FederationShips.getFedCa(), "NextDoor", NEXT_DOOR);

        assertNull("fixture: no strength on this hull", doomed.getExplosionStrength());
        int before = victim.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals(before, victim.getShields().getShieldStrength(1));
    }

    /** The wreck at the centre is already destroyed and must not be damaged by its own blast. */
    @Test
    public void theWreckTakesNoDamageFromItsOwnExplosion() {
        Ship doomed = ship(FederationShips.getFedCa(), "Lexington", GROUND_ZERO);
        int before = doomed.getShields().getShieldStrength(1);

        destroy(doomed);

        assertEquals("its own explosion does not touch it",
                before, doomed.getShields().getShieldStrength(1));
    }

    // ------------------------------------------------------------------ D5.31, declared not done

    /**
     * (D5.31): two ships destroyed in the same hex are ONE explosion of their summed strength.
     * That is not implemented, and resolving them separately applies two blasts where the rule
     * applies one — so the log has to say so. A silent approximation here would read as a rules
     * bug to a playtester and be hunted for in the wrong place.
     */
    @Test
    public void twoWrecksInOneHexSayWhatIsMissing() {
        Ship first  = ship(FederationShips.getFedCa(), "First", GROUND_ZERO);
        Ship second = ship(FederationShips.getFedCa(), "Second", GROUND_ZERO);

        first.setBattleStatus(BattleStatus.DESTROYED);
        second.setBattleStatus(BattleStatus.DESTROYED);
        game.cleanupDestroyedShips();

        assertTrue("the log must name the rule it is not applying, and the hex",
                log().contains("D5.31") && log().contains(GROUND_ZERO.toString()));
    }

    /**
     * One step explodes the wreck and removes it, and that is what stops it exploding twice.
     *
     * <p>This replaced a test for a {@code hasExploded} flag on Ship. The flag could not fail —
     * mutation testing showed the tests passed with it ignored, because removal had already made
     * a second explosion impossible — so the flag went and this pins the invariant it was
     * standing in for. If the two steps are ever separated (D5.31's combined explosion may want
     * that), this test keeps passing and the guard has to come back.
     */
    @Test
    public void theStepThatExplodesAWreckAlsoRemovesIt() {
        Ship doomed = ship(FederationShips.getFedCa(), "Lexington", GROUND_ZERO);
        Ship victim = ship(FederationShips.getFedCa(), "NextDoor", NEXT_DOOR);
        int before = victim.getShields().getShieldStrength(1);

        destroy(doomed);
        int afterOne = victim.getShields().getShieldStrength(1);
        game.cleanupDestroyedShips();
        game.cleanupDestroyedShips();

        assertEquals("the blast landed once", before - 18, afterOne);
        assertFalse("the wreck is off the map, so there is nothing left to set off",
                game.getShips().contains(doomed));
        assertEquals("and no later cleanup finds it again",
                afterOne, victim.getShields().getShieldStrength(1));
    }
}
