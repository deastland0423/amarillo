package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.CloakingDevice.CloakState;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The "flashcube" lock-on window (G13.401/.552/.57): a fully cloaked ship damaged by an
 * ESG or mine is momentarily exposed. In the same impulse an enemy with LOS and active
 * fire control may gain a lock-on and must immediately pass the G13.331 retention roll.
 * ESG and mine damage sites call {@code game.flashcubeLockOn}; this exercises the resolver.
 *
 * Retention P = S − EW − RF + SF − 4. Attacker sensor 6, range 2 (RF 0), no ECM: a
 * cloaked ship at speed 20 gives SF 6 → P 8 (auto-retain); at speed 0, SF −2 → P 0 (auto-fail).
 */
public class FlashcubeTest {

    private Game game;
    private Ship attacker;
    private Ship cloaked;

    @Before
    public void setUp() {
        game = new Game();
        attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Hunter");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setActiveFireControl(true);

        cloaked = new Ship();
        cloaked.init(RomulanShips.getRomKr());
        cloaked.setName("Ghost");
        cloaked.setLocation(new Location(10, 12)); // range 2
        cloaked.setFacing(1);
        cloaked.getCloakingDevice().setState(CloakState.FULLY_CLOAKED);

        game.getShips().add(attacker);
        game.getShips().add(cloaked);
    }

    @Test
    public void exposedCloakedShip_canBeLockedOnto_whenRetentionSucceeds() {
        cloaked.setSpeed(20); // SF 6 → P 8
        assertFalse(attacker.hasLockOn(cloaked));

        game.flashcubeLockOn(cloaked);

        assertTrue("enemy locks onto the exposed ship (G13.401)", attacker.hasLockOn(cloaked));
    }

    @Test
    public void exposedButRetentionFails_noLockOn() {
        cloaked.setSpeed(0); // SF −2 → P 0, impossible to retain

        game.flashcubeLockOn(cloaked);

        assertFalse("gained then lost the lock-on to the retention roll (G13.331)",
                attacker.hasLockOn(cloaked));
    }

    @Test
    public void noFireControl_noLockOn() {
        cloaked.setSpeed(20);
        attacker.setActiveFireControl(false);

        game.flashcubeLockOn(cloaked);

        assertFalse("a ship without active fire control cannot lock on (D6.1143)",
                attacker.hasLockOn(cloaked));
    }

    @Test
    public void notFullyCloaked_isNoOp() {
        cloaked.setSpeed(20);
        cloaked.getCloakingDevice().setState(CloakState.INACTIVE);

        assertTrue("uncloaked → nothing to expose", game.flashcubeLockOn(cloaked).isEmpty());
        assertFalse(attacker.hasLockOn(cloaked));
    }

    @Test
    public void existingLockOn_isKeptWithoutReroll() {
        cloaked.setSpeed(0);          // any new roll would fail...
        attacker.addLockOn(cloaked);  // ...but a lock-on held before the void is not re-rolled

        game.flashcubeLockOn(cloaked);

        assertTrue("a pre-existing lock-on survives the flashcube (G13.401/.402)",
                attacker.hasLockOn(cloaked));
    }
}
