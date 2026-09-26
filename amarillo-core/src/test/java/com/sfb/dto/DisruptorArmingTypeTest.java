package com.sfb.dto;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.Disruptor;
import com.sfb.weapons.Weapon;

/**
 * A disruptor reports how it is armed, exactly as a photon does.
 *
 * Asked during a playtest: the Fire Orders panel badges an overloaded photon "ovl" but never
 * badges a disruptor. The badge is drawn from {@code armingType} on the weapon DTO, which is
 * filled for anything implementing HeavyWeapon — and a Disruptor is one. So the path exists;
 * this pins it, so that "no badge" always means "armed standard" rather than "the field never
 * arrives for this weapon".
 *
 * Worth knowing while reading a screen: a disruptor's overload lasts the turn it was
 * allocated for. {@link Disruptor#cleanUp()} calls reset(), which sets it back to STANDARD,
 * so a disruptor overloaded on turn 2 shows no badge on turn 3.
 */
public class DisruptorArmingTypeTest {

    private Game game;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();
        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Ambush");
        klingon.setLocation(new Location(10, 10));
        klingon.setFacing(1);
        game.getShips().add(klingon);
    }

    private Disruptor firstDisruptor() {
        for (Weapon w : klingon.getWeapons().fetchAllWeapons())
            if (w instanceof Disruptor)
                return (Disruptor) w;
        throw new AssertionError("fixture needs a disruptor");
    }

    private GameStateDto.WeaponDto weaponDto(String name) {
        GameStateDto dto = new GameStateDto(game, null);
        for (GameStateDto.MapObjectDto o : dto.mapObjects) {
            if (!(o instanceof GameStateDto.ShipDto))
                continue;
            for (GameStateDto.WeaponDto w : ((GameStateDto.ShipDto) o).weapons)
                if (name.equals(w.name))
                    return w;
        }
        throw new AssertionError("no weapon named " + name + " in the snapshot");
    }

    @Test
    public void aDisruptorIsAHeavyWeaponAndSaysSo() {
        GameStateDto.WeaponDto w = weaponDto(firstDisruptor().getName());

        assertTrue("the badge and the arming controls both key off this", w.isHeavy);
        assertTrue("and a disruptor can be overloaded", w.canOverload);
    }

    @Test
    public void anOrdinaryDisruptorReportsStandard() {
        assertEquals("STANDARD", weaponDto(firstDisruptor().getName()).armingType);
    }

    /** The case the playtest was looking for. */
    @Test
    public void anOverloadedDisruptorReportsOverload() {
        Disruptor d = firstDisruptor();
        assertTrue(d.setOverload());

        assertEquals("OVERLOAD", weaponDto(d.getName()).armingType);
    }

    /**
     * And it lasts the turn only. A disruptor arms in a single turn, so end-of-turn cleanup
     * puts it back to standard — which is why an overload set two turns ago shows nothing.
     */
    @Test
    public void theOverloadDoesNotSurviveTheTurn() {
        Disruptor d = firstDisruptor();
        d.setOverload();
        assertEquals("OVERLOAD", weaponDto(d.getName()).armingType);

        d.cleanUp();

        assertEquals("STANDARD", weaponDto(d.getName()).armingType);
    }
}
