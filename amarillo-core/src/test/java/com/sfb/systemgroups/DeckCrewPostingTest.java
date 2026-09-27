package com.sfb.systemgroups;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.Weapon;

/**
 * Deck crews are posted to a box for the whole turn (J4.817), and die with it (J4.811).
 * <p>
 * The kill was written long before this and could never fire: DamageResolver reads the box's
 * crew count before destroying it and calls killDeckCrews, but nothing had ever PUT a crew in
 * a box. Crews were a ship-wide pool that the end-of-turn pass spent, as though they
 * materialised at impulse 32 and chose their jobs then. An action is 32 consecutive impulses,
 * so a crew is in one box for all of them — which is what makes "who was in there when it blew
 * up" a question with an answer.
 */
public class DeckCrewPostingTest {

    private Game game;
    private Ship rn;

    @Before
    public void setUp() throws Exception {
        game = new Game();
        rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
        rn.setName("HMS Loyalty");
        game.getShips().add(rn);
        game.startTurn();
        game.getClock().nextImpulse();
        rn.startTurn();          // as beginImpulses() does — the crews take their posts
    }

    private List<ShuttleSpace> fighterBoxes() {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Fighter)
                    boxes.add(box);
        return boxes;
    }

    private static int chargesOn(Shuttle f) {
        int total = 0;
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                total += ff.getChargesRemaining();
        return total;
    }

    private int postedCrews() {
        int posted = 0;
        for (ShuttleSpace box : fighterBoxes())
            posted += box.getDeckCrews();
        return posted;
    }

    @Test
    public void theCrewsAreInTheirBoxesFromTheStartOfTheTurn() {
        // Nine crews, two to a Stinger: four boxes get a pair and the ninth crew works alone.
        assertEquals("every crew is somewhere", 9, postedCrews());
        assertEquals(2, fighterBoxes().get(0).getDeckCrews());
        assertEquals("J4.8172: two on one fighter and no more",
                2, fighterBoxes().get(3).getDeckCrews());
        assertEquals("the odd one out works by itself", 1, fighterBoxes().get(4).getDeckCrews());
        assertEquals("and there are no fighters left for a sixth posting",
                0, fighterBoxes().get(5).getDeckCrews());
    }

    @Test
    public void aBoxShotOffTakesItsCrewsWithIt() {
        ShuttleSpace box = fighterBoxes().get(0);
        assertEquals(2, box.getDeckCrews());
        int before = rn.getCrew().getDeckCrews();

        // What DamageResolver does on a shuttle DAC hit: read the crews, then destroy.
        int killed = box.getDeckCrews();
        box.destroy();
        rn.getCrew().killDeckCrews(killed);

        assertEquals("J4.811: killed with the box they were working in",
                before - 2, rn.getCrew().getDeckCrews());
        assertEquals(0, box.getDeckCrews());
    }

    @Test
    public void crewsKilledThisTurnAreGoneNextTurnToo() {
        int killed = fighterBoxes().get(0).getDeckCrews();
        fighterBoxes().get(0).destroy();
        rn.getCrew().killDeckCrews(killed);

        rn.cleanUp();        // end of turn: the survivors do their work and stand down
        rn.startTurn();      // and the next turn posts what is left

        assertEquals("the complement is permanently smaller", 7, rn.getCrew().getDeckCrews());
        assertEquals(7, postedCrews());
    }

    @Test
    public void theWorkOfADestroyedBoxSimplyDoesNotHappen() {
        ShuttleSpace doomed = fighterBoxes().get(0);
        Shuttle fighter = doomed.getShuttle();
        assertEquals("its crews were on the job", 2, doomed.getDeckCrews());

        doomed.destroy();
        rn.cleanUp();

        // J4.8174: interrupted is cancelled, with no partial credit — and there is nothing
        // left to credit anyway, since the fighter went with the box.
        assertEquals(0, chargesOn(fighter));
        assertTrue("and nothing claims otherwise: " + rn.getShuttles().getLastRearmLog(),
                rn.getShuttles().getLastRearmLog().stream()
                        .noneMatch(l -> l.startsWith(fighter.getName() + ":")));
    }

    @Test
    public void anUnpostedBoxGetsNoWorkHowerverIdleTheShipLooks() {
        // The sixth Stinger had no crew to spare, so nothing happens to it — where the old
        // pool model would have found the crews free again at the end of the turn.
        Shuttle unattended = fighterBoxes().get(5).getShuttle();
        assertEquals(0, fighterBoxes().get(5).getDeckCrews());

        rn.cleanUp();

        assertEquals("nine crews do not stretch to nine fighters", 0, chargesOn(unattended));
        assertEquals("while the first four were fully reloaded",
                4, chargesOn(fighterBoxes().get(0).getShuttle()));
    }

    @Test
    public void aBoxWhoseFighterIsFullGetsNoCrewsAndLosesNone() {
        // Crews are a pool sent where there is work. A fighter that needs nothing is not
        // work, so nobody is standing in that box to be killed.
        ShuttleSpace box = fighterBoxes().get(0);
        box.armOccupantFully();
        rn.startTurn();          // re-post, now that this one has nothing outstanding

        assertEquals("nothing to do here", 0, box.getDeckCrews());
        int before = rn.getCrew().getDeckCrews();

        int killed = box.getDeckCrews();
        box.destroy();
        rn.getCrew().killDeckCrews(killed);

        assertEquals("the box is lost, the crews are not", before, rn.getCrew().getDeckCrews());
    }

    @Test
    public void launchingTheFighterDoesNotGetItsCrewsOutOfTheBay() {
        ShuttleBay bay = rn.getShuttles().getBays().get(0);
        ShuttleSpace box = fighterBoxes().get(0);
        Shuttle fighter = box.getShuttle();
        assertEquals(2, box.getDeckCrews());
        int before = rn.getCrew().getDeckCrews();

        bay.launch(fighter, 12, 1, 6);   // off it goes on impulse 6

        // Their JOB left; they did not. A deck crew is standing in that bay for the turn,
        // and J4.811 destroys the box with whoever is in it. Releasing them here would make
        // launching a way to put crews beyond reach, which is a trick the rules never offer
        // and exactly backwards — crews are most exposed when the carrier is in the thick of
        // it, not least.
        assertEquals("still in the bay", 2, box.getDeckCrews());

        int killed = box.getDeckCrews();
        box.destroy();
        rn.getCrew().killDeckCrews(killed);
        assertEquals("and they die with it", before - 2, rn.getCrew().getDeckCrews());
    }

    @Test
    public void theirWorkStillDoesNotHappenWhenTheFighterHasGone() {
        // The other half of J4.8174, which does still hold: the action is cancelled, so
        // nothing is reloaded. It is the RISK that persists, not the credit.
        ShuttleBay bay = rn.getShuttles().getBays().get(0);
        Shuttle fighter = fighterBoxes().get(0).getShuttle();
        bay.launch(fighter, 12, 1, 6);

        rn.cleanUp();

        assertEquals("it was not in its box to be loaded", 0, chargesOn(fighter));
    }

    @Test
    public void thePostingsStandDownAtTheEndOfTheTurn() {
        rn.cleanUp();

        assertEquals("a posting lasts one turn; next turn's is made fresh", 0, postedCrews());
    }

    @Test
    public void crewsLoadingAScatterPackAreNotPostedToABox() {
        // GameSession books scatter-pack crews against the pool during Energy Allocation, so
        // by the time the postings are made they are already spoken for (J4.81: one set of
        // crews for every job).
        rn.getCrew().setAvailableDeckCrews(3);
        rn.startTurn();

        assertEquals("three left, so three posted", 3, postedCrews());
    }
}
