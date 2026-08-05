package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG overlap prevention (G23.71/.712): the spheres of two different ships cannot
 * overlap. A field released into an already-active enemy field is the "second" one
 * and fails to form; two forming into each other simultaneously both fail.
 */
public class EsgOverlapTest {

    private Ship ship(Game game, String name, int x, int y) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        game.getShips().add(s);
        return s;
    }

    private ESG esgOn(Ship s, int energy) {
        ESG esg = new ESG();
        esg.setDesignator("A");
        s.getWeapons().addWeapon(esg);
        esg.setStoredEnergy(energy);
        return esg;
    }

    private void allocateStationary(Game game, Ship... ships) {
        for (Ship s : ships) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setActivateShields(s.getActiveShieldCost());
            e.setWarpMovement(0.0);
            game.submitAllocation(s, e);
        }
    }

    private void runUntilReleased(Game game, ESG watch) {
        for (int guard = 0; guard < 200 && watch.isAnnounced(); guard++) {
            game.advancePhase();
        }
    }

    @Test
    public void fieldReleasedIntoAnActiveEnemyField_failsToForm() {
        Game game = new Game();
        Ship a = ship(game, "Azure",  10, 10);
        Ship b = ship(game, "Bronze", 10, 13); // range 3: discs (r2 + r1) overlap
        ESG ea = esgOn(a, 2);
        ESG eb = esgOn(b, 1);

        ea.activate(2, 0);          // Azure's field is already up (the "first")
        eb.announce(1, 0);          // Bronze announces; it will form 4 impulses later

        game.startTurn();
        allocateStationary(game, a, b);
        runUntilReleased(game, eb);

        assertTrue("the first field is unaffected", ea.isActive());
        assertFalse("the second field cannot form over it (G23.712)", eb.isActive());
    }

    @Test
    public void twoFieldsFormingSimultaneously_bothFail() {
        Game game = new Game();
        Ship a = ship(game, "Azure",  10, 10);
        Ship b = ship(game, "Bronze", 10, 13);
        ESG ea = esgOn(a, 2);
        ESG eb = esgOn(b, 1);

        ea.announce(2, 0);          // both announced for the same release impulse
        eb.announce(1, 0);

        game.startTurn();
        allocateStationary(game, a, b);
        runUntilReleased(game, ea);

        assertFalse("both fields fail when they form into each other (G23.712)", ea.isActive());
        assertFalse(eb.isActive());
    }

    @Test
    public void nonOverlappingFields_bothForm() {
        Game game = new Game();
        Ship a = ship(game, "Azure",  10, 10);
        Ship b = ship(game, "Bronze", 10, 20); // far apart: discs do not overlap
        ESG ea = esgOn(a, 2);
        ESG eb = esgOn(b, 1);

        ea.announce(2, 0);
        eb.announce(1, 0);

        game.startTurn();
        allocateStationary(game, a, b);
        runUntilReleased(game, ea);

        assertTrue(ea.isActive());
        assertTrue(eb.isActive());
    }
}
