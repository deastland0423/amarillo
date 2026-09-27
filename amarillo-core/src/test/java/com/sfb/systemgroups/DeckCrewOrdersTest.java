package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Fighter;

/**
 * The player says which fighter boxes the deck crews work in (J4.817).
 * <p>
 * A Ranger's nine crews cover five of its nine Stingers, and until now the code chose which
 * five by the order they appear in the ship file. It is a real captain's call — you load the
 * ones you mean to launch — and the same placeholder we took out of weapon status arming.
 * <p>
 * An order is the WHOLE instruction, the way a COI drone loadout is: a box the player did not
 * name gets nobody, so deliberately holding crews back is not quietly overruled. No order at
 * all still runs the first-come pass, so a player who never opens the hangar panel loses
 * nothing.
 */
public class DeckCrewOrdersTest {

    private Ship rn;

    @Before
    public void setUp() throws Exception {
        Game game = new Game();
        rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        rn.setName("HMS Loyalty");
        game.getShips().add(rn);
        game.startTurn();
        game.getClock().nextImpulse();
    }

    private List<ShuttleSpace> fighterBoxes() {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Fighter)
                    boxes.add(box);
        return boxes;
    }

    private void postWith(Map<String, Integer> order) {
        Energy allocation = new Energy();
        allocation.setDeckCrewPostings(order);
        rn.allocateEnergy(allocation);
        rn.startTurn();
    }

    @Test
    public void theBoxIdIsTheOneTheDtoSends() {
        assertEquals("bay index and space index, as ShuttleBayDto/ShuttleSpaceDto number them",
                "2-1", Shuttles.boxId(2, 1));
    }

    @Test
    public void withNoOrderTheCrewsPostThemselvesAsBefore() {
        postWith(null);

        int posted = 0;
        for (ShuttleSpace box : fighterBoxes())
            posted += box.getDeckCrews();
        assertEquals("nine crews, first come", 9, posted);
        assertEquals(2, fighterBoxes().get(0).getDeckCrews());
    }

    @Test
    public void anOrderPutsTheCrewsWhereTheCaptainWantsThem() {
        // Bay 3's Stingers are the ones this captain means to launch. Its boxes are spaces
        // 0-2 of bay 2, which the Ranger's file gives three of.
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(Shuttles.boxId(2, 0), 2);
        order.put(Shuttles.boxId(2, 1), 2);
        order.put(Shuttles.boxId(2, 2), 2);

        postWith(order);

        assertEquals(0, fighterBoxes().get(0).getDeckCrews());
        List<ShuttleSpace> bayThree = rn.getShuttles().getBays().get(2).getSpaces();
        assertEquals(2, bayThree.get(0).getDeckCrews());
        assertEquals(2, bayThree.get(1).getDeckCrews());
        assertEquals(2, bayThree.get(2).getDeckCrews());
        assertEquals("three crews held back, as ordered",
                3, rn.getCrew().getAvailableDeckCrews());
    }

    @Test
    public void aBoxLeftOutOfTheOrderGetsNobody() {
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(Shuttles.boxId(0, 3), 2);   // exactly one box named

        postWith(order);

        int boxesWithCrews = 0;
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getDeckCrews() > 0)
                    boxesWithCrews++;
        assertEquals("an order is the whole instruction, not a hint", 1, boxesWithCrews);
        assertEquals("and the other seven crews stayed put",
                7, rn.getCrew().getAvailableDeckCrews());
    }

    @Test
    public void anOrderCannotPutMoreCrewsOnAFighterThanTheRulesAllow() {
        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(Shuttles.boxId(0, 3), 6);

        postWith(order);

        assertEquals("J4.8172: two on one fighter and no more",
                2, rn.getShuttles().getBays().get(0).getSpaces().get(3).getDeckCrews());
    }

    @Test
    public void anOrderCannotConjureCrewsTheShipDoesNotHave() {
        rn.getCrew().killDeckCrews(6);   // three left

        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(Shuttles.boxId(0, 3), 2);
        order.put(Shuttles.boxId(0, 4), 2);
        order.put(Shuttles.boxId(0, 5), 2);
        postWith(order);

        int posted = 0;
        for (ShuttleSpace box : fighterBoxes())
            posted += box.getDeckCrews();
        assertEquals("three crews is three crews", 3, posted);
    }

    @Test
    public void aBoxWithNothingToDoIsNotAPlaceToPostPeople() {
        ShuttleSpace full = rn.getShuttles().getBays().get(0).getSpaces().get(3);
        full.armOccupantFully();

        Map<String, Integer> order = new LinkedHashMap<>();
        order.put(Shuttles.boxId(0, 3), 2);
        postWith(order);

        assertEquals("no work, so nobody stands there to be shot", 0, full.getDeckCrews());
    }

    @Test
    public void whatThePanelOffersIsWhatTheServerHoldsTheOrderTo() {
        Map<String, Integer> wanted = rn.getShuttles().crewsWantedByBox();

        assertEquals("nine Stingers, all of them empty and wanting two crews each",
                9, wanted.size());
        assertEquals(Integer.valueOf(2), wanted.get(Shuttles.boxId(0, 3)));
        assertNull("an admin shuttle's box is not offered", wanted.get(Shuttles.boxId(0, 0)));

        rn.getShuttles().getBays().get(0).getSpaces().get(3).armOccupantFully();
        assertEquals("and a fighter that needs nothing drops out of the offer",
                8, rn.getShuttles().crewsWantedByBox().size());
    }
}
