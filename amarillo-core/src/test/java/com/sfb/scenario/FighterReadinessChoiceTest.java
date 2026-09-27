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
 * Choosing WHICH fighters start ready (S4.10–S4.12).
 * <p>
 * On a squadron of one type it makes no difference, which is why it went unnoticed: the plain
 * Ranger's nine Stinger-1s are interchangeable and the first two are as good as any. The
 * Ranger REFIT is not — it carries Stinger-2s and Stinger-Hs in the same bay, and whether the
 * two hot fighters are fusion or hellbore is a decision about what you can do on turn one.
 * <p>
 * The choice is free. Readiness is what the weapon status already granted; this only says who
 * gets it. Stripping the squadron back to re-arrange it loses nothing either — J4.886's
 * charges go home to the box they came from.
 */
public class FighterReadinessChoiceTest {

    private static Ship rangerRefit() throws Exception {
        Ship rn = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/hydran/rnplus.json")));
        rn.setName("HMS Tenacity");
        return rn;
    }

    private static ScenarioSpec scenario() {
        ScenarioSpec spec = new ScenarioSpec();
        spec.commanderOptions = new ScenarioSpec.CommanderOptions();
        spec.commanderOptions.budgetPercent = 20;
        return spec;
    }

    private static List<Shuttle> fighters(Ship ship) {
        List<Shuttle> out = new ArrayList<>();
        for (ShuttleBay bay : ship.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof Fighter)
                    out.add(box.getShuttle());
        return out;
    }

    private static List<String> armedNames(Ship ship) {
        List<String> out = new ArrayList<>();
        for (Shuttle f : fighters(ship))
            if (FighterArming.chargesCarriedBy(f) > 0)
                out.add(f.getName());
        return out;
    }

    private static String firstOfType(Ship ship, String type) {
        for (Shuttle f : fighters(ship))
            if (f.getClass().getSimpleName().equalsIgnoreCase(type))
                return f.getName();
        throw new IllegalStateException("no " + type + " aboard");
    }

    @Test
    public void theRefitCarriesTwoKindsOfFighterSoTheChoiceIsReal() throws Exception {
        Ship rn = rangerRefit();

        long stinger2 = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("Stinger2")).count();
        long stingerH = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("StingerH")).count();

        assertEquals("seven fusion fighters", 7, stinger2);
        assertEquals("and two hellbore ones", 2, stingerH);
    }

    @Test
    public void withNoChoiceTheFirstTwoAreArmedAsBefore() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 1);

        List<String> armed = armedNames(rn);

        assertEquals(2, armed.size());
        assertEquals("first come, as the file lists them",
                fighters(rn).get(0).getName(), armed.get(0));
    }

    @Test
    public void theCaptainCanSayWhichTwoAreHot() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 1);
        String hellbore = firstOfType(rn, "StingerH");
        String lastFusion = fighters(rn).get(fighters(rn).size() - 1).getName();

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(hellbore, lastFusion);
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        List<String> armed = armedNames(rn);
        assertEquals(2, armed.size());
        assertTrue("the hellbore the captain wanted: " + armed, armed.contains(hellbore));
        assertTrue("and the fusion fighter at the back: " + armed, armed.contains(lastFusion));
    }

    @Test
    public void theFightersPassedOverGiveTheirChargesBack() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 1);
        Shuttle firstComer = fighters(rn).get(0);
        assertTrue("armed by the status", FighterArming.chargesCarriedBy(firstComer) > 0);

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(firstOfType(rn, "StingerH"));
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("no longer one of the chosen", 0,
                FighterArming.chargesCarriedBy(firstComer));
        for (ShuttleBay bay : rn.getShuttles().getBays())
            for (ShuttleSpace box : bay.getSpaces()) {
                if (box.capacitorCapacity() == 0)
                    continue;
                assertEquals(box.getShuttle().getName() + ": what it holds plus what is in its"
                        + " box is still one capacitor", box.capacitorCapacity(),
                        box.getCapacitorCharges()
                                + FighterArming.chargesCarriedBy(box.getShuttle()));
            }
    }

    @Test
    public void namingMoreFightersThanTheStatusAllowsArmsOnlyWhatItAllows() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 0);

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = fighters(rn).stream().map(Shuttle::getName).toList();
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("S4.10 gives two, however many the player names",
                2, armedNames(rn).size());
    }

    @Test
    public void atWsTwoTheChoiceIsTheOrderTheBudgetIsSpentIn() throws Exception {
        Ship rn = rangerRefit();
        rn.getCrew().killDeckCrews(rn.getCrew().getDeckCrews() - 2);   // two crews, two actions
        ScenarioLoader.applyWeaponStatus(rn, 2);
        String hellbore = firstOfType(rn, "StingerH");

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(hellbore);
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        // A hellbore charge is one whole action (J4.834), so two crews' two turns cover it
        // easily — and the fusion fighters the automatic pass had picked are cold again.
        assertTrue("the named fighter was served first: " + armedNames(rn),
                armedNames(rn).contains(hellbore));
    }

    @Test
    public void atWsThreeThereIsNothingToChoose() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 3);
        assertEquals("S4.13 arms the lot", 9, armedNames(rn).size());

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(firstOfType(rn, "StingerH"));
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("and a preference cannot disarm the rest of them",
                9, armedNames(rn).size());
    }
}
