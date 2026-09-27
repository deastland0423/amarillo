package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.systemgroups.FighterArming;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.ShuttleSpace;

/**
 * Weapon status decides how ready a carrier's fighters are (S4.10–S4.13, J4.8224).
 * <p>
 * Fighters are built empty and their boxes full, which is J4.8223's resting state — racks
 * loaded, fighters not. Everything a fighter is holding at the start of a scenario was moved
 * there by this pass, out of that fighter's own box, so a carrier's total ammunition never
 * changes with weapon status: only how much of it is already on the rails.
 * <p>
 * Before this, fighters were born armed and nothing debited the boxes, so a Ranger fielded
 * twelve fusion charges per box where the rules allow eight — and fielded them at WS-0, when
 * two of its nine Stingers should have been ready and the rest cold.
 */
public class FighterWeaponStatusTest {

    private static Ship ranger() throws Exception {
        return ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rn.json")));
    }

    private static List<ShuttleSpace> fighterBoxes(Ship ship) {
        List<ShuttleSpace> boxes = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Fighter)
                    boxes.add(box);
        return boxes;
    }

    private static int armedFighters(Ship ship) {
        int armed = 0;
        for (ShuttleSpace box : fighterBoxes(ship))
            if (FighterArming.chargesCarriedBy(box.getShuttle()) > 0)
                armed++;
        return armed;
    }

    /** The books, at any weapon status: fighter plus box is one capacitor. */
    private static void assertBooksBalance(Ship ship) {
        for (ShuttleSpace box : fighterBoxes(ship)) {
            Shuttle f = box.getShuttle();
            assertEquals(f.getName() + ": what it holds plus what is left in its box is one"
                    + " capacitor (J4.886/J4.8224)",
                    box.capacitorCapacity(),
                    box.getCapacitorCharges() + FighterArming.chargesCarriedBy(f));
        }
    }

    @Test
    public void aFighterIsBuiltEmptyAndItsBoxFull() throws Exception {
        Ship rn = ranger();   // ShipLibrary does not apply a weapon status

        assertEquals("no fighter is armed until something arms it", 0, armedFighters(rn));
        for (ShuttleSpace box : fighterBoxes(rn))
            assertEquals("J4.886: the capacitor starts full", 8, box.getCapacitorCharges());
    }

    @Test
    public void atWsZeroTwoFightersAreReadyAndTheRestAreCold() throws Exception {
        Ship rn = ranger();

        ScenarioLoader.applyFighterWeaponStatus(rn, 0);

        assertEquals("S4.10: a couple of fighters on the deck, no more",
                2, armedFighters(rn));
        assertEquals("and each drew four of its own box's eight",
                4, fighterBoxes(rn).get(0).getCapacitorCharges());
        assertEquals("the seventh box is untouched",
                8, fighterBoxes(rn).get(6).getCapacitorCharges());
        assertBooksBalance(rn);
    }

    @Test
    public void wsOneIsTheSameAsWsZeroForFighters() throws Exception {
        Ship zero = ranger();
        Ship one = ranger();

        ScenarioLoader.applyFighterWeaponStatus(zero, 0);
        ScenarioLoader.applyFighterWeaponStatus(one, 1);

        assertEquals(armedFighters(zero), armedFighters(one));
        assertEquals(2, armedFighters(one));
    }

    @Test
    public void atWsTwoTheDeckCrewsHaveHadTwoTurnsOfWork() throws Exception {
        Ship rn = ranger();
        assertEquals("nine deck crews on a Ranger (Annex #7G)", 9, rn.getCrew().getDeckCrews());

        ScenarioLoader.applyFighterWeaponStatus(rn, 2);

        // S4.12 gives each crew two turns: 18 actions. A Stinger-1 wants 2, so all nine are
        // armed and the budget is exactly spent — a carrier at WS-2 launches a full squadron.
        assertEquals("nine fighters at two actions each is eighteen, which is what nine crews"
                + " deliver over two turns", 9, armedFighters(rn));
        assertBooksBalance(rn);
    }

    @Test
    public void aThinDeckCrewLeavesSomeFightersColdAtWsTwo() throws Exception {
        Ship rn = ranger();
        rn.getCrew().killDeckCrews(6);   // six of its nine lost before the scenario opens

        ScenarioLoader.applyFighterWeaponStatus(rn, 2);

        // Six actions between them, two per Stinger — three ready, six cold.
        assertEquals(3, armedFighters(rn));
        assertBooksBalance(rn);
    }

    @Test
    public void atWsThreeEverythingIsLoadedAndTheBoxesPaidForIt() throws Exception {
        Ship rn = ranger();

        ScenarioLoader.applyFighterWeaponStatus(rn, 3);

        assertEquals("S4.13: the whole squadron is ready", 9, armedFighters(rn));
        for (ShuttleSpace box : fighterBoxes(rn))
            assertEquals("J4.8224: the weapons were taken from the box, which is down to one"
                    + " reload", 4, box.getCapacitorCharges());
        assertBooksBalance(rn);
    }

    @Test
    public void weaponStatusIsAppliedWhenAShipIsBuiltForAScenario() throws Exception {
        // The pass is reached through applyWeaponStatus, which is what ScenarioLoader calls —
        // so a real game gets it without anything else having to remember.
        Ship rn = ranger();

        ScenarioLoader.applyWeaponStatus(rn, 3);

        assertEquals(9, armedFighters(rn));
        assertBooksBalance(rn);
    }

    @Test
    public void aShipWithNoFightersIsUnaffected() throws Exception {
        Ship ca = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/federation/ca.json")));

        ScenarioLoader.applyWeaponStatus(ca, 3);   // must not throw on admin shuttles

        assertTrue("a plain CA carries no fighters", fighterBoxes(ca).isEmpty());
    }
}
