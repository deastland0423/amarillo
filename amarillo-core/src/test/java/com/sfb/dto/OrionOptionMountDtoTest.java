package com.sfb.dto;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.OptionMountCatalog;
import com.sfb.objects.OptionMountLoadout;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Location;

/**
 * What an Orion's chosen option weapons look like on a player's screen, in a running game.
 *
 * <h2>Why this is worth pinning</h2>
 * Every other hull's armament is fixed in its JSON, so the SSD panel has never had to show a
 * weapon that was not in the file. An Orion's is decided at purchase (G15.415) and only exists
 * once {@link OptionMountLoadout#equip} has run — and that is the one place where "the rules
 * engine knows" and "the player can see it" could come apart without any test noticing.
 *
 * <p>In a battle this is not cosmetic. Which arc a mount's weapon bears in decides whether a
 * target is in range at all (G15.43 puts a wing mount's arc on its own side), so a panel that
 * omitted it or drew it wrong would mislead a player into a shot that cannot be taken.
 */
public class OrionOptionMountDtoTest {

    private OptionMountCatalog catalog;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        catalog = OptionMountCatalog.loadDefault();
    }

    private Ship orion(String type) {
        ShipSpec spec = ShipLibrary.get("Orion", type);
        assertNotNull("fixture: Orion " + type + " should be in the library", spec);
        return ShipLibrary.createShip(spec);
    }

    /** The ship as its own player sees it — the unredacted view the SSD panel draws from. */
    private GameStateDto.ShipDto seenByItsOwner(Ship ship) {
        Game game = new Game();
        ship.setLocation(new Location(10, 10));
        game.getShips().add(ship);
        for (GameStateDto.MapObjectDto o : new GameStateDto(game).mapObjects)
            if (o instanceof GameStateDto.ShipDto dto && ship.getName().equals(dto.name))
                return dto;
        throw new AssertionError("fixture: the ship did not reach the snapshot");
    }

    private static List<GameStateDto.WeaponDto> weaponsOf(GameStateDto.ShipDto dto) {
        assertNotNull("a ship its owner can see must carry its weapons", dto.weapons);
        return dto.weapons;
    }

    /**
     * The weapon in a named option mount, found by its designator.
     *
     * <p>Not by weapon type: a CA carries four Phaser-1s of its own, so asking for "the Phaser-1"
     * gets a hull phaser with a hull arc and the fitted one is never examined at all. That is how
     * the first draft of the wing-arc test below failed — it compared a built-in phaser against
     * itself. The designator is "OPT-" plus the mount, which is exactly the distinction wanted.
     */
    private static GameStateDto.WeaponDto inMount(GameStateDto.ShipDto dto, String designator) {
        return weaponsOf(dto).stream()
                .filter(w -> ("OPT-" + designator).equals(w.designator))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "nothing in option mount " + designator + "; the panel shows "
                                + weaponsOf(dto).stream().map(w -> w.designator).toList()));
    }

    // ------------------------------------------------------------------ the weapon is there

    /**
     * THE QUESTION THIS FILE EXISTS FOR. A fitted option mount puts a real weapon on the ship,
     * so it must reach the panel like any other — named, and with an arc to draw.
     */
    @Test
    public void aFittedOptionWeaponReachesTheOwnersPanel() {
        Ship ca = orion("CA");
        OptionMountLoadout.equip(ca, catalog, "A", "Disruptor-30", 180);

        GameStateDto.WeaponDto fitted = inMount(seenByItsOwner(ca), "A");

        assertEquals("the weapon that was bought, not some other", "Disruptor30", fitted.type);
        // The caption the panel prints. Core computes it (Weapon.getLabel) so the SSD panel, the
        // sidebar, the EA dialog and the combat log all call the weapon the same thing.
        assertEquals("Disruptor-30 OPT-A", fitted.label);
        assertNotNull("it needs an arc label to print", fitted.arcLabel);
        assertFalse("and a non-empty one", fitted.arcLabel.isBlank());
        assertNotEquals("and a real arc mask to draw", 0, fitted.arcMask);
    }

    /** An empty mount adds nothing: a bare Orion shows only the armament in its own file. */
    @Test
    public void anUnfittedHullShowsNoPhantomWeapon() {
        long bare = weaponsOf(seenByItsOwner(orion("CA"))).size();

        Ship fitted = orion("CA");
        OptionMountLoadout.equip(fitted, catalog, "A", "Disruptor-30", 180);

        assertEquals("exactly one weapon more, the one that was bought",
                bare + 1, weaponsOf(seenByItsOwner(fitted)).size());
    }

    /**
     * G15.43: a wing mount bears in its own wing's arc, a centerline mount forward. The panel
     * draws what it is told, so the two must not arrive carrying the same arc.
     */
    @Test
    public void aWingMountsArcIsNotTheCenterlineArc() {
        Ship centre = orion("CA");
        OptionMountLoadout.equip(centre, catalog, "A", "Phaser-1", 180);

        Ship wing = orion("CA");
        String wingDesignator = null;
        for (com.sfb.objects.OptionMount m : wing.getOptionMounts())
            if (m.getPosition() == com.sfb.objects.OptionMount.Position.WING) {
                wingDesignator = m.getDesignator();
                break;
            }
        assertNotNull("fixture: the CA should have a wing mount", wingDesignator);
        OptionMountLoadout.equip(wing, catalog, wingDesignator, "Phaser-1", 180);

        GameStateDto.WeaponDto centreGun = inMount(seenByItsOwner(centre), "A");
        GameStateDto.WeaponDto wingGun = inMount(seenByItsOwner(wing), wingDesignator);

        assertEquals("the CA's centerline mounts bear forward", "FA", centreGun.arcLabel);
        assertNotEquals("a wing phaser must not be drawn in the centerline's arc",
                centreGun.arcMask, wingGun.arcMask);
    }
}
