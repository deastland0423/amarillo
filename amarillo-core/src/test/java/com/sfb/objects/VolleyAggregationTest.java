package com.sfb.objects;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.TurnTracker;
import com.sfb.dto.GameStateDto;
import com.sfb.dto.GameStateDto.PendingVolleyDto;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.utilities.ArcUtils;
import com.sfb.weapons.Phaser1;
import com.sfb.weapons.Weapon;

/**
 * Verifies that damage from multiple attackers hitting the same shield in the same
 * fire segment is aggregated into a single volley entry (for the UI) rather than
 * appearing as separate entries.
 *
 * The raw game.getPendingVolleys() list retains one entry per fireWeapons() call
 * (one per attacker). The DTO aggregates by (target, shieldNumber) so the
 * reinforcement dialog shows the defender the combined incoming damage.
 */
public class VolleyAggregationTest {

    private Game  game;
    private Ship  attacker1;
    private Ship  attacker2;
    private Ship  target;

    @Before
    public void setUp() {
        game = new Game();
        game.getClock().nextImpulse(); // impulse 1

        attacker1 = new Ship();
        attacker1.init(FederationShips.getFedCa());
        attacker1.setName("Attacker1");
        attacker1.setLocation(new Location(10, 10));
        attacker1.setFacing(1);

        attacker2 = new Ship();
        attacker2.init(FederationShips.getFedCa());
        attacker2.setName("Attacker2");
        attacker2.setLocation(new Location(10, 11));
        attacker2.setFacing(1);

        target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(new Location(10, 12));
        target.setFacing(1);

        game.getShips().add(attacker1);
        game.getShips().add(attacker2);
        game.getShips().add(target);
    }

    // -------------------------------------------------------------------------
    // Raw pending-volley count
    // -------------------------------------------------------------------------

    @Test
    public void twoAttackers_sameShield_rawListHasTwoEntries() {
        game.fireWeapons(attacker1, target, singlePhaser("1"), 1, 1, 1);
        game.fireWeapons(attacker2, target, singlePhaser("1"), 1, 1, 1);

        assertEquals(2, game.getPendingVolleys().size());
    }

    @Test
    public void twoAttackers_differentShields_rawListHasTwoEntries() {
        List<Weapon> w1 = singlePhaser("1");
        List<Weapon> w2 = singlePhaser("1");

        game.fireWeapons(attacker1, target, w1, 1, 1, 1);
        game.fireWeapons(attacker2, target, w2, 1, 1, 6);

        assertEquals(2, game.getPendingVolleys().size());
    }

    // -------------------------------------------------------------------------
    // DTO aggregation (what the reinforcement dialog shows the defender)
    // -------------------------------------------------------------------------

    @Test
    public void twoAttackers_sameShield_dtoShowsOneEntry() {
        List<Weapon> w1 = singlePhaser("1");
        List<Weapon> w2 = singlePhaser("1");

        game.fireWeapons(attacker1, target, w1, 1, 1, 1);
        game.fireWeapons(attacker2, target, w2, 1, 1, 1);

        List<PendingVolleyDto> dto = new GameStateDto(game).pendingVolleys;
        assertEquals("Two fires at same shield should aggregate to one DTO entry", 1, dto.size());
    }

    @Test
    public void twoAttackers_sameShield_dtoDamageSumsBothAttackers() {
        List<Weapon> w1 = singlePhaser("1");
        List<Weapon> w2 = singlePhaser("1");

        game.fireWeapons(attacker1, target, w1, 1, 1, 1);
        game.fireWeapons(attacker2, target, w2, 1, 1, 1);

        int rawTotal = game.getPendingVolleys().stream().mapToInt(v -> v.totalDamage).sum();
        PendingVolleyDto dto = new GameStateDto(game).pendingVolleys.get(0);
        assertEquals("DTO combined damage must equal sum of raw volleys", rawTotal, dto.totalDamage);
    }

    @Test
    public void twoAttackers_differentShields_dtoShowsTwoEntries() {
        List<Weapon> w1 = singlePhaser("1");
        List<Weapon> w2 = singlePhaser("1");

        game.fireWeapons(attacker1, target, w1, 1, 1, 1);
        game.fireWeapons(attacker2, target, w2, 1, 1, 6);

        List<PendingVolleyDto> dto = new GameStateDto(game).pendingVolleys;
        assertEquals("Fires at different shields must remain separate entries", 2, dto.size());
    }

    @Test
    public void twoAttackers_differentTargets_dtoShowsTwoEntries() {
        Ship target2 = new Ship();
        target2.init(FederationShips.getFedCa());
        target2.setName("Target2");
        target2.setLocation(new Location(10, 13));
        target2.setFacing(1);
        game.getShips().add(target2);

        List<Weapon> w1 = singlePhaser("1");
        List<Weapon> w2 = singlePhaser("1");

        game.fireWeapons(attacker1, target,  w1, 1, 1, 1);
        game.fireWeapons(attacker2, target2, w2, 1, 1, 1);

        List<PendingVolleyDto> dto = new GameStateDto(game).pendingVolleys;
        assertEquals("Fires at different targets must remain separate entries", 2, dto.size());
    }

    @Test
    public void dtoEntry_hasCorrectShieldNumber() {
        game.fireWeapons(attacker1, target, singlePhaser("1"), 1, 1, 3);
        game.fireWeapons(attacker2, target, singlePhaser("1"), 1, 1, 3);

        PendingVolleyDto dto = new GameStateDto(game).pendingVolleys.get(0);
        assertEquals(3, dto.shieldNumber);
        assertEquals("Target", dto.targetShipName);
    }

    // -------------------------------------------------------------------------
    // Phase advancement: combined volley doesn't block the game
    // -------------------------------------------------------------------------

    @Test
    public void twoAttackers_sameShield_advancementReachesEndOfImpulse() {
        game.fireWeapons(attacker1, target, singlePhaser("1"), 1, 1, 1);
        game.fireWeapons(attacker2, target, singlePhaser("1"), 1, 1, 1);

        // Advance from MOVEMENT → REINFORCEMENT (pending volleys present)
        game.advancePhase(); // → REINFORCEMENT

        assertEquals(Game.ImpulsePhase.REINFORCEMENT, game.getCurrentPhase());

        // Advance through REINFORCEMENT: applies volleys, resolves internal damage
        game.advancePhase(); // → END_OF_IMPULSE or DAC_CHOICE

        // Game must not still be in REINFORCEMENT — progress was made.
        // MOVEMENT-triggered reinforcement returns to ACTIVITY; DIRECT_FIRE-triggered returns
        // to END_OF_IMPULSE. A DAC choice may also have been queued.
        assertNotEquals(Game.ImpulsePhase.REINFORCEMENT, game.getCurrentPhase());

        boolean reachedExpectedPhase =
            game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY ||
            game.getCurrentPhase() == Game.ImpulsePhase.END_OF_IMPULSE ||
            game.getCurrentPhase() == Game.ImpulsePhase.DAC_CHOICE;
        assertTrue("Game must advance past REINFORCEMENT", reachedExpectedPhase);
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static List<Weapon> singlePhaser(String designator) {
        Phaser1 ph = new Phaser1();
        ph.setArcs(ArcUtils.FH);
        ph.setDesignator(designator);
        return java.util.Collections.singletonList(ph);
    }
}
