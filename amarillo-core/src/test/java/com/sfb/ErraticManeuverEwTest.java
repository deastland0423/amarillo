package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.weapons.PhaserWeapon;
import com.sfb.weapons.Weapon;

/**
 * Erratic Maneuvers against direct fire (C10.41, C10.413, C10.414).
 * <p>
 * "Using EM produces the effect of four points of ECM", applied both to weapons fired AT
 * the unit (C10.413) and to direct-fire weapons fired BY it (C10.414). Those four points
 * are a NATURAL source (C10.412, D6.3143), so they sit outside the generated and lending
 * limits and cannot be ignored between friendly units.
 * <p>
 * This did nothing. {@code Game.ewAgainst} had it right, and so did the fire PREVIEW which
 * routes through it — but the fire resolution assembled its own ECM sum inline and never
 * asked, so a ship bought EM and was shot at as though standing still. The preview and the
 * shot disagreed, which is the shape of the bug: a second copy of a calculation that the
 * first one has since outgrown.
 */
public class ErraticManeuverEwTest {

    private Game game;
    private Ship attacker;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();
        Player red = new Player();
        red.setTeamName("Federation");
        Player blue = new Player();
        blue.setTeamName("Klingon");

        attacker = new Ship();
        attacker.init(com.sfb.samples.FederationShips.getFedCa());
        attacker.setName("USS Attacker");
        attacker.setLocation(new Location(10, 10));
        attacker.setFacing(1);
        attacker.setOwner(red);
        attacker.setActiveFireControl(true);
        game.getShips().add(attacker);

        target = new Ship();
        target.init(com.sfb.samples.KlingonShips.getD7());
        target.setName("IKV Target");
        target.setLocation(new Location(10, 8));
        target.setFacing(13);
        target.setOwner(blue);
        game.getShips().add(target);

        game.startTurn();
    }

    /** Put the target on Erratic Maneuvers, announcement applied. */
    private void targetStartsEm() {
        int now = game.getAbsoluteImpulse();
        target.announceEm(true, now);
        target.applyEmAnnouncement(now);
        assertTrue("fixture: the target must actually be on EM", target.isUsingEm());
    }

    private Weapon anyPhaser(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof PhaserWeapon)
                return w;
        throw new IllegalStateException("fixture needs a phaser");
    }

    // -------------------------------------------------------------------------

    /** C10.41: four points, which the D6.34 Step 5 chart turns into a +2 die shift. */
    @Test
    public void emGivesFourPointsOfEcmAgainstFire() {
        targetStartsEm();

        assertEquals("four points of natural ECM (C10.412)",
                4, game.ewAgainst(attacker, target).natural());
        assertEquals("which is +2 on the die (C10.42)",
                2, game.fireEcmShift(attacker, target));
    }

    /**
     * The bug. Whatever the preview says, the SHOT has to agree — and it did not, because
     * the resolution never consulted ewAgainst.
     */
    @Test
    public void theShotAgreesWithThePreview() {
        targetStartsEm();
        int preview = game.fireEcmShift(attacker, target);

        java.util.List<Weapon> shot = new java.util.ArrayList<>();
        shot.add(anyPhaser(attacker));
        String log = game.fireWeapons(attacker, target, shot, 2, 2, 1);

        assertTrue("preview promised +" + preview + ", so the fire must apply it. Log:\n"
                + log, log.contains("ECM shift: +" + preview));
    }

    /** C10.414: the EM unit's OWN fire suffers the same four points. */
    @Test
    public void anEmUnitShootsThroughItsOwnManeuvers() {
        int now = game.getAbsoluteImpulse();
        attacker.announceEm(true, now);
        attacker.applyEmAnnouncement(now);

        assertEquals("the firer's own EM counts against it (C10.414)",
                4, game.ewAgainst(attacker, target).natural());
    }

    /** With nobody manoeuvring there is no natural ECM and no shift, EM aside. */
    @Test
    public void withoutEmThereIsNoNaturalEcm() {
        assertEquals(0, game.ewAgainst(attacker, target).natural());
        assertEquals(0, game.fireEcmShift(attacker, target));
    }
}
