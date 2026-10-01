package com.sfb.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.ShuttleBay;

/**
 * A fighter still in its bay belongs to whoever owns the carrier holding it.
 * <p>
 * {@code GameSession.ownsShip} is the gate every action passes through, and it knew about two kinds
 * of thing: ships, and shuttles already on the map. A fighter in a bay is neither — so any action
 * naming one was refused with a 403, and the pod-EW declaration (J4.961) is exactly such an action.
 * It is made during the energy allocation, while the whole squadron is still aboard, and it sends
 * the fighter's own name. A player who opened the EA form and declared a split got
 * "You do not own ship: HAAS-E-1" — reported from a real game, 2026-10-01.
 * <p>
 * It went unnoticed because {@code GameSessionPodEwTest} calls {@code executeAction} directly,
 * BELOW the layer that checks ownership. That is the lesson worth keeping: a rule enforced in the
 * controller is not covered by a test that starts inside the session. This one asks ownsShip.
 * <p>
 * A bay fighter has no owner of its own — ownership is stamped on launch — so the carrier is the
 * only thing that can answer, and is the right thing to ask.
 */
class BayFighterOwnershipTest {

    private static final String TOKEN = "player-token";

    private GameSession session;
    private Player kzinti;

    @BeforeEach
    void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        session = new GameSession("game-1", "host-token", "Host");

        kzinti = new Player();
        kzinti.setTeamName("Kzinti");
    }

    /** A Kzinti CVS owned by {@code owner}, its complement still aboard. */
    private Ship seatCarrier(Player owner) throws Exception {
        Ship cvs = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cvs.json")));
        cvs.setName("KHS Watchful");
        cvs.setOwner(owner);
        session.getGame().getShips().add(cvs);
        return cvs;
    }

    /** Register a player holding {@code core} — the state the lobby leaves behind. */
    private void seatPlayer(Player core) {
        GameSession.PlayerInfo info = session.addPlayer(TOKEN, "Alice");
        info.setCorePlayer(core);
    }

    private List<Fighter> bayFighters(Ship ship) {
        List<Fighter> out = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (Shuttle craft : bay.getInventory())
                if (craft instanceof Fighter f)
                    out.add(f);
        return out;
    }

    @Test
    void everyFighterInItsBayBelongsToTheCarriersOwner() throws Exception {
        Ship cvs = seatCarrier(kzinti);
        seatPlayer(kzinti);

        List<Fighter> aboard = bayFighters(cvs);
        assertFalse(aboard.isEmpty(), "the CVS should have fighters aboard");

        for (Fighter f : aboard)
            assertTrue(session.ownsShip(TOKEN, f.getName()),
                    f.getName() + " sits in a bay on a ship this player owns");
    }

    /** The EW fighter specifically, since the pod declaration is made for it. */
    @Test
    void theEwFighterIsOwnedBeforeItEverLaunches() throws Exception {
        Ship cvs = seatCarrier(kzinti);
        seatPlayer(kzinti);

        Fighter ewf = bayFighters(cvs).stream()
                .filter(f -> f.getEwPods() > 0)
                .findFirst().orElseThrow();

        assertFalse(session.getGame().getActiveShuttles().contains(ewf),
                "it has not launched, which is the whole point");
        assertTrue(session.ownsShip(TOKEN, ewf.getName()),
                "J4.961's declaration names this craft while it is still aboard");
    }

    /** Still the CARRIER's ownership that decides — not a free pass for any name in a bay. */
    @Test
    void anotherPlayersBayFighterIsNotOwned() throws Exception {
        Ship cvs = seatCarrier(kzinti);

        Player hydran = new Player();
        hydran.setTeamName("Hydran");
        seatPlayer(hydran);                  // not the carrier's owner

        Fighter aboard = bayFighters(cvs).get(0);
        assertFalse(session.ownsShip(TOKEN, aboard.getName()),
                aboard.getName() + " sits in someone else's bay");
    }

    @Test
    void anUnknownNameIsStillRefused() throws Exception {
        seatCarrier(kzinti);
        seatPlayer(kzinti);
        assertFalse(session.ownsShip(TOKEN, "Nothing Called This"));
    }

    /** A player with no core Player owns nothing, bay or otherwise. */
    @Test
    void aPlayerWithNoCorePlayerOwnsNothing() throws Exception {
        Ship cvs = seatCarrier(kzinti);
        session.addPlayer(TOKEN, "Alice");   // registered, but never given a core Player

        assertFalse(session.ownsShip(TOKEN, bayFighters(cvs).get(0).getName()));
        assertFalse(session.ownsShip(TOKEN, "KHS Watchful"));
    }
}
