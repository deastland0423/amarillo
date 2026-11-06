package com.sfb.dto;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;

/**
 * (D5.2) explosion strength and the (C3.14) excess track, as the SSD panel receives them.
 *
 * <p>Asked for by the owner after watching a frigate explode in a live game: you cannot tell from
 * the map whether killing a ship clears the adjacent hexes or only its own, and that decides
 * whether it is worth standing next to one.
 *
 * <h2>The reason this test exists at all</h2>
 * {@code explosionStrength} is a boxed {@code Integer} the whole way down, and every layer has to
 * keep it that way. Zero is a real value — the chart gives ground bases zero because (D5.13) says
 * small ground bases do not explode — while absent means nobody has read a number for that hull.
 * A primitive anywhere in the chain collapses the two and reports that an unread hull explodes for
 * nothing, which is CLAUDE.md's DTO trap in its original form.
 */
public class ExplosionStrengthDtoTest {

    private Game game;

    private Ship add(Map<String, Object> spec, String name) {
        game = (game == null) ? new Game() : game;
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(10, 10));
        s.setFacing(1);
        game.getShips().add(s);
        return s;
    }

    private GameStateDto.ShipDto dtoFor(String name) {
        GameStateDto dto = new GameStateDto(game, "Federation");
        return (GameStateDto.ShipDto) dto.mapObjects.stream()
                .filter(o -> o instanceof GameStateDto.ShipDto)
                .filter(o -> name.equals(((GameStateDto.ShipDto) o).name))
                .findFirst().orElseThrow();
    }

    /** A hull with a chart number sends it, and the excess track comes with it. */
    @Test
    public void aNormalHullSendsItsStrengthAndItsExcessTrack() {
        add(FederationShips.getFedCa(), "Lexington");

        GameStateDto.ShipDto dto = dtoFor("Lexington");

        assertEquals("the CA's chart strength", Integer.valueOf(18), dto.explosionStrength);
        assertTrue("it has excess boxes", dto.maxExcess > 0);
        assertEquals("and none are gone yet", dto.maxExcess, dto.availableExcess);
    }

    /**
     * Zero must survive as zero. The chart lists every ground base at zero, and a ship that
     * explodes for nothing is a different statement from one nobody has measured.
     */
    @Test
    public void aZeroStrengthHullSendsZeroAndNotNull() {
        Map<String, Object> spec = FederationShips.getFedCa();
        spec.put("explosionstrength", 0);
        add(spec, "GroundBase");

        assertEquals("zero is the chart speaking, not a gap",
                Integer.valueOf(0), dtoFor("GroundBase").explosionStrength);
    }

    /**
     * And absent must survive as null, so a client can tell "does not explode" from "we never
     * read it". Only the Hydran GEN-F is in this state today.
     */
    @Test
    public void anUnreadHullSendsNull() {
        Map<String, Object> spec = FederationShips.getFedCa();
        spec.remove("explosionstrength");
        add(spec, "Unread");

        assertNull("a primitive anywhere in the chain would have made this 0",
                dtoFor("Unread").explosionStrength);
    }

    /** The excess track is a countdown, and it has to move when boxes are shot off. */
    @Test
    public void theExcessTrackFallsAsBoxesAreDestroyed() {
        Ship ship = add(FederationShips.getFedCa(), "Lexington");
        int before = dtoFor("Lexington").availableExcess;

        ship.getSpecialFunctions().damageExcessDamage();

        GameStateDto.ShipDto dto = dtoFor("Lexington");
        assertEquals("one box gone", before - 1, dto.availableExcess);
        assertEquals("the maximum does not move with it", before, dto.maxExcess);
    }
}
