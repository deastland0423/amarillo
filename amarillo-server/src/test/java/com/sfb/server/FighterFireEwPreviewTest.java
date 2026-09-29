package com.sfb.server;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.Squadron;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KzintiShips;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The electronic warfare a fire preview shows, when either end of the shot is a fighter.
 * <p>
 * The preview used to compute nothing at all unless the ATTACKER was a Ship, so a fighter
 * taking a shot was shown zero ECM on its target, zero ECCM of its own and a shift of zero —
 * while the resolution behind it counted J4.47's two points and whatever the squadron was
 * lending (J4.92). {@code Game.ewAgainst} and {@code Game.eccmOf} both take a Unit; the
 * Ship-only gate was simply left behind when they were widened, which is the drift this tier
 * is known for.
 */
class FighterFireEwPreviewTest {

    private GameController controller;
    private Game game;
    private String gameId;
    private String host;
    private Haas_E ewf;
    private Haas fighter;
    private Ship enemy;

    @BeforeEach
    void setUp() {
        GameSessionService service = new GameSessionService();
        controller = new GameController(service, null);
        GameSession session = service.createSession("Alice");
        gameId = session.getId();
        host = session.getPlayers().keySet().iterator().next();
        game = session.getGame();

        Ship carrier = new Ship();
        carrier.init(KzintiShips.getKzinBC());
        carrier.setName("KHS Longsword");
        carrier.setLocation(new Location(10, 10));
        carrier.setFacing(1);
        game.getShips().add(carrier);

        enemy = new Ship();
        enemy.init(FederationShips.getFedCa());
        enemy.setName("USS Attacker");
        enemy.setLocation(new Location(10, 13));
        enemy.setFacing(13);
        enemy.setActiveFireControl(true);
        game.getShips().add(enemy);

        ewf = new Haas_E();
        ewf.setName("HAAS-E");
        ewf.setLocation(new Location(10, 11));
        ewf.setFacing(13);

        fighter = new Haas();
        fighter.setName("HAAS-1");
        fighter.setLocation(new Location(10, 12));
        fighter.setFacing(13);

        game.getActiveShuttles().add(ewf);
        game.getActiveShuttles().add(fighter);
        Squadron squadron = new Squadron("Gold", carrier);
        assertNull(squadron.add(ewf));
        assertNull(squadron.add(fighter));
        fighter.addLockOn(ewf);          // J4.921: the recipient holds the lock-on
        fighter.addLockOn(enemy);        // and one on what it is shooting at

        Player alice = new Player();
        alice.setName("Alice");
        alice.getPlayerUnits().add(carrier);
        session.getPlayers().get(host).setCorePlayer(alice);

        game.startTurn();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> options(String attacker, String target) {
        Object body = controller.getFireOptions(gameId, host, attacker, target).getBody();
        assertNotNull(body, "no fire options for " + attacker + " -> " + target);
        return (Map<String, Object>) body;
    }

    /**
     * A fighter shooting: it brings J4.47's two points of ECCM, and the preview must say so.
     * Zero here meant the pad and the resolution disagreed about every shot a fighter took.
     */
    @Test
    void aFighterShowsItsOwnEccmWhenItShoots() {
        // Four, not two: J4.47's built-in pair plus the two its EW fighter is lending, which
        // J4.965 makes available for shooting through as well as for hiding behind.
        assertEquals(4, ((Number) options("HAAS-1", "USS Attacker").get("eccm")).intValue(),
                "2 built-in (J4.47) + 2 lent (J4.93)");

        // Alone, only its own. Zero was what this reported before, for both cases.
        fighter.setLocation(new Location(10, 20));
        assertEquals(2, ((Number) options("HAAS-1", "USS Attacker").get("eccm")).intValue(),
                "J4.47 gives every fighter two points of its own");
    }

    /** And it holds lock-ons like anything else (J1.31, D6.11) — this reported false. */
    @Test
    void aFighterIsAllowedToHaveALockOn() {
        assertEquals(Boolean.TRUE, options("HAAS-1", "USS Attacker").get("hasLockOn"));
    }

    /**
     * Shooting AT a fighter already worked, because the attacker was a ship — the target's
     * built-in and lent points were counted. Pinned so the two directions stay in step.
     */
    @Test
    void aShipShootingAtAFighterSeesItsEw() {
        Map<String, Object> o = options("USS Attacker", "HAAS-1");

        int ecm = ((Number) o.get("ecmPoints")).intValue();
        assertTrue(ecm >= 2, "J4.47's two at least, plus anything lent: " + ecm);
        assertNotNull(o.get("ecmSources"));
    }

    /**
     * The whole point of an EW fighter, seen from the firing end: a squadron-mate in reach is
     * a harder shot than the same fighter would be alone (J4.93).
     */
    @Test
    void aLentSquadronMateIsAHarderShot() {
        int helped = ((Number) options("USS Attacker", "HAAS-1").get("ecmPoints")).intValue();

        // Out past J4.921's three hexes the loan lapses and only J4.47's two remain.
        fighter.setLocation(new Location(10, 20));
        int alone = ((Number) options("USS Attacker", "HAAS-1").get("ecmPoints")).intValue();

        assertEquals(4, helped, "2 built-in + 2 lent");
        assertEquals(2, alone, "its own two only");
    }
}
