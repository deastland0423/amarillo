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
 * Ranger REFIT is not — it carries Stinger-2s, Stinger-Hs and a Stinger-E in the same bay, and
 * whether the two hot fighters are fusion or hellbore is a decision about what you can do on
 * turn one.
 * <p>
 * The Stinger-E (R1.F7) added a third case that neither pass handled: a fighter with NOTHING
 * to arm. It was the first fighter the RN+ listed, and both the automatic pass and the
 * Commander's Options pass counted it against S4.10's two — so the ship went into the scenario
 * with one hot fighter instead of two.
 * <p>
 * Bay order no longer puts it first: the complement is resolved by role from the year (J4.4) and
 * the EW fighter is seated last, precisely because it has nothing a deck crew can load. So the
 * tests below reach it by naming it in the readiness preference rather than by position.
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

    /**
     * The first fighter the weapon status will actually arm.
     * <p>
     * Not the same as the first fighter in the bay since the RN+ gained its Stinger-E: that
     * craft carries a Ph-G and two permanent EW pods (R1.F7) and so has nothing a deck crew
     * can load, which means S4.10's two slots skip over it.
     */
    private static Shuttle firstArmable(Ship ship) {
        for (Shuttle f : fighters(ship))
            if (FighterArming.needsArming(f))
                return f;
        throw new IllegalStateException("nothing aboard needs arming");
    }

    private static String firstOfType(Ship ship, String type) {
        for (Shuttle f : fighters(ship))
            if (f.getClass().getSimpleName().equalsIgnoreCase(type))
                return f.getName();
        throw new IllegalStateException("no " + type + " aboard");
    }

    @Test
    public void theRefitCarriesThreeKindsOfFighterSoTheChoiceIsReal() throws Exception {
        Ship rn = rangerRefit();

        long stinger2 = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("Stinger2")).count();
        long stingerH = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("StingerH")).count();
        long stingerE = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("Stinger_E")).count();

        assertEquals("six fusion fighters", 6, stinger2);
        assertEquals("two hellbore ones", 2, stingerH);
        assertEquals("and the EW fighter of its standard complement (R1.F7)", 1, stingerE);
        assertEquals("nine in all", 9, fighters(rn).size());
    }

    /**
     * The Stinger-E is not part of the choice, because there is nothing to choose about it.
     * One Ph-G and two permanent EW pods (R1.F7) means no fusion charge, no hellbore charge
     * and no drone — nothing its box can hand it, so it flies as it sits.
     */
    @Test
    public void theEwFighterNeedsNoArmingAndSoIsNeverAChoice() throws Exception {
        Ship rn = rangerRefit();
        Shuttle ewf = fighters(rn).stream()
                .filter(f -> f.getClass().getSimpleName().equals("Stinger_E"))
                .findFirst().orElseThrow();

        assertFalse("nothing for a deck crew to do", FighterArming.needsArming(ewf));
        assertEquals(0, FighterArming.chargesCarriedBy(ewf));

        // Asked for FIRST and still not armed. This used to lean on the EW fighter happening to
        // be first in the bay, which was true only of the hand-written Hydran data; now the
        // complement is seated by role (J4.4) and the EW fighter comes last. Naming it in the
        // preference puts it at the front of the queue regardless, which is the rule this is
        // about: it is skipped because it needs no arming, not because of where it sits.
        ScenarioLoader.applyWeaponStatus(rn, 1);
        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(ewf.getName());
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("still nothing aboard it", 0, FighterArming.chargesCarriedBy(ewf));
        assertFalse("and it is not counted among the ready: " + armedNames(rn),
                armedNames(rn).contains(ewf.getName()));
    }

    /**
     * And it must not eat one of S4.10's two slots. A pass that armed boxes rather than
     * fighters-that-need-arming left the ship with ONE hot fighter instead of two — which is
     * what happened when the EW fighter was the first craft the RN+ listed.
     * <p>
     * It is now seated last, since a complement resolved by role puts the EW fighter at the end
     * (J4.4) — so the two slots would reach the armable fighters even if the skip were broken.
     * The test above is the one that still exercises the skip, by naming the EW fighter first.
     */
    @Test
    public void aFighterWithNothingToArmDoesNotSpendAReadinessSlot() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 1);

        assertEquals("S4.10 still delivers two ready fighters: " + armedNames(rn),
                2, armedNames(rn).size());
    }

    @Test
    public void withNoChoiceTheFirstTwoAreArmedAsBefore() throws Exception {
        Ship rn = rangerRefit();
        ScenarioLoader.applyWeaponStatus(rn, 1);

        List<String> armed = armedNames(rn);

        assertEquals(2, armed.size());
        assertEquals("first come, as the file lists them — skipping any that need no arming",
                firstArmable(rn).getName(), armed.get(0));
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
        // The fighter to be passed over has to be one the status armed AND one the captain
        // does not then name. Since the Stinger-E is skipped, the status arms the two
        // Stinger-Hs — so naming the first leaves the second as the one that gives its
        // charges back.
        String chosen = firstOfType(rn, "StingerH");
        Shuttle passedOver = fighters(rn).stream()
                .filter(FighterArming::needsArming)
                .filter(f -> !f.getName().equals(chosen))
                .findFirst().orElseThrow();
        assertTrue("armed by the status", FighterArming.chargesCarriedBy(passedOver) > 0);

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(chosen);
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("no longer one of the chosen", 0,
                FighterArming.chargesCarriedBy(passedOver));
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
        // Eight, not nine: S4.13 arms the lot, and the Stinger-E is not something that can
        // be armed. It needs nothing, so it is ready at every weapon status including this one.
        assertEquals("S4.13 arms everything that can be armed", 8, armedNames(rn).size());

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(firstOfType(rn, "StingerH"));
        ScenarioLoader.applyCoi(rn, loadout, scenario());

        assertEquals("and a preference cannot disarm the rest of them",
                8, armedNames(rn).size());
    }

    /**
     * The stack trace from playtest, as a test. Naming a Kzinti HAAS-E in the Commander's
     * Options threw IllegalStateException out of the submit endpoint — armFully asked a rail
     * carrying an EW pod to take a drone, and DroneRail.loadDrone refuses rather than quietly
     * drop the pod (J4.962).
     * <p>
     * The endpoint no longer offers such a fighter, but this is the layer below that: core must
     * not throw even when a name reaches it, because the list is a client's suggestion and
     * nothing stops an old client or a replayed request from sending one.
     */
    @Test
    public void namingAFighterWhosePodsFillItsRailsDoesNotThrow() throws Exception {
        Ship cvs = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cvs.json")));
        cvs.setName("KHS Watchful");
        ScenarioLoader.applyWeaponStatus(cvs, 1);

        // By capability, not by class: which EW fighter a CVS carries is decided by the year
        // (J4.4), so it is an AAS-E at its Y170 service year and a HAAS-E only from Y173.
        Shuttle ewFighter = fighters(cvs).stream()
                .filter(f -> f instanceof com.sfb.objects.shuttles.Fighter ftr
                        && ftr.getEwPods() > 0)
                .findFirst().orElseThrow();

        CoiLoadout loadout = new CoiLoadout();
        loadout.armedFighters = List.of(ewFighter.getName());
        ScenarioLoader.applyCoi(cvs, loadout, scenario());   // must not throw

        assertEquals("and it is still carrying its pods, not a drone", 0,
                FighterArming.dronesCarriedBy(ewFighter));
    }

    /**
     * And naming it must not eat a readiness slot either, for the same reason a Stinger-E does
     * not: there is no work to do on it. Its drone-armed sisters get the two.
     */
    @Test
    public void aPoddedFighterDoesNotSpendASlotOnTheKzintiCarrierEither() throws Exception {
        Ship cvs = ShipLibrary.createShip(
                ShipSpec.fromJson(new File("../data/factions/kzinti/cvs.json")));
        cvs.setName("KHS Watchful");
        ScenarioLoader.applyWeaponStatus(cvs, 1);

        // Not armedNames here: that counts CHARGES, which only a Hydran fighter has. A HAAS is
        // armed with drones, so this is the same question asked in the currency it uses.
        List<String> loaded = fighters(cvs).stream()
                .filter(f -> FighterArming.dronesCarriedBy(f) > 0)
                .map(Shuttle::getName).toList();

        assertEquals("S4.10's two, none of it wasted on the EW fighter: " + loaded,
                2, loaded.size());
        assertFalse("and the EW fighter is not one of them: " + loaded,
                loaded.stream().anyMatch(n -> n.contains("HAAS-E")));
    }
}
