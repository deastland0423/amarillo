package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Proves that concurrent Game instances have fully independent clocks.
 *
 * Before 2026-07-07 TurnTracker was a JVM-wide static: creating or advancing
 * a second game reset/advanced EVERY game's impulse counter, corrupting all
 * impulse-keyed state (shield lockouts, FC countdowns, cloak fades, weapon
 * cooldowns). This test pins the fix and must never be deleted while the
 * server hosts multiple sessions in one JVM.
 */
public class GameIsolationTest {

    private Game newRunningGame() {
        Game game = new Game();

        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);

        Ship klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(20, 20));
        klingon.setFacing(1);
        klingon.setSpeedPreviousTurn(31);
        klingon.setSpeedTwoTurnsAgo(31);

        game.getShips().add(fed);
        game.getShips().add(klingon);
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(20.0);
            game.submitAllocation(s, e);
        }
        return game; // MOVEMENT phase, impulse 1
    }

    @Test
    public void startingSecondGame_doesNotResetFirstGamesClock() {
        Game gameA = newRunningGame();
        // Advance A into its turn: 4 phases per impulse
        for (int i = 0; i < 20; i++)
            gameA.advancePhase();
        int impulseA = gameA.getAbsoluteImpulse();
        assertTrue("Game A should be past impulse 1", impulseA > 1);

        // A second lobby starts a fresh game — the old static reset() would
        // have snapped Game A's clock back to zero right here.
        Game gameB = newRunningGame();

        assertEquals("Game B starts at impulse 1", 1, gameB.getAbsoluteImpulse());
        assertEquals("Game A's clock must be untouched by Game B's creation",
                impulseA, gameA.getAbsoluteImpulse());
    }

    @Test
    public void advancingOneGame_leavesTheOtherUntouched() {
        Game gameA = newRunningGame();
        Game gameB = newRunningGame();

        for (int i = 0; i < 12; i++)
            gameB.advancePhase();

        assertEquals("Game A must stay at impulse 1 while B advances",
                1, gameA.getAbsoluteImpulse());
        assertTrue("Game B advanced independently", gameB.getAbsoluteImpulse() > 1);
        assertEquals("Turn displays are independent too",
                1, gameA.getCurrentImpulse());
    }

    @Test
    public void shipSystems_readTheirOwnGamesClock() {
        Game gameA = newRunningGame();
        Game gameB = newRunningGame();

        // Advance B far enough that a shield toggled in A at impulse 1 would
        // wrongly appear past its 8-impulse lockout if it read B's clock.
        for (int i = 0; i < 40; i++)
            gameB.advancePhase();

        Ship fedA = gameA.getShips().get(0);
        assertTrue("Initial lower succeeds", fedA.getShields().lowerShield(1));
        assertFalse("Immediate re-raise must be blocked by the 8-impulse lockout "
                + "against Game A's own clock (impulse 1), regardless of Game B's clock",
                fedA.getShields().raiseShield(1));
    }
}
