package com.sfb.systemgroups;

import static org.junit.Assert.*;

import org.junit.Test;

import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.PhaserG;
import com.sfb.weapons.Weapon;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * A deck crew mending a fighter, one damage point an action (J4.818).
 * <p>
 * The point of it is the threshold. J1.33 cripples a fighter by accumulated damage — half
 * speed, its Ph-G cut to a Ph-3, its other weapons offline — so a single point of repair can
 * carry it back under the line and hand all of that back. That is what makes a crew spent
 * here sometimes worth more than a crew spent loading, and it is what these tests are about.
 */
public class FighterRepairTest {

    /** A Stinger-2 damaged to within one point of its crippling threshold, and past it. */
    private static Fighter hurt(int damage) {
        Fighter s = CataloguedFighter.of("stinger2");
        s.setName("Fighter-1");
        s.setCurrentHull(s.getHull() - damage);
        if (s.shouldCripple())
            s.applyCripplingEffects();
        return s;
    }

    private static PhaserG phaserOf(Shuttle f) {
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof PhaserG pg)
                return pg;
        return null;
    }

    private static int fusionCharges(Shuttle f) {
        int total = 0;
        for (Weapon w : f.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                total += ff.getChargesRemaining();
        return total;
    }

    @Test
    public void anActionMendsOnePoint() {
        Fighter s = hurt(3);
        ShuttleSpace box = new ShuttleSpace(s);
        assertFalse("three points is not yet crippling", s.isCrippled());

        FighterArming.Load one = FighterArming.repair(box, s, 2);   // one whole action

        assertEquals(1, one.chargesLoaded());
        assertEquals("hull 10, three points taken, one mended", 8, s.getCurrentHull());
        assertEquals(2, one.halfActionsUsed());
    }

    @Test
    public void halfAnActionMendsNothing() {
        Fighter s = hurt(3);
        ShuttleSpace box = new ShuttleSpace(s);

        FighterArming.Load half = FighterArming.repair(box, s, 1);

        // J4.8174 again: an action that does not finish earns nothing.
        assertNull(half.note());
        assertEquals(7, s.getCurrentHull());
    }

    @Test
    public void twoCrewsMendTwoPoints() {
        Fighter s = hurt(4);
        ShuttleSpace box = new ShuttleSpace(s);

        FighterArming.Load both = FighterArming.repair(box, s, 4);   // two crews, two actions

        assertEquals(2, both.chargesLoaded());
        assertEquals(8, s.getCurrentHull());
    }

    @Test
    public void onePointOfRepairCanUncrippleAFighter() {
        Fighter s = hurt(7);   // a Stinger-2 is crippled at 7 damage
        assertTrue("crippled", s.isCrippled());
        assertTrue("its gatling phaser is down to one shot (J1.3321)",
                phaserOf(s).isReducedToPhaserThree());
        assertEquals("and J1.3324 discharged its fusions", 0, fusionCharges(s));

        ShuttleSpace box = new ShuttleSpace(s);
        FighterArming.Load one = FighterArming.repair(box, s, 2);

        assertFalse("one point back under the line, and it flies properly again (J1.33)",
                s.isCrippled());
        assertEquals("four shots restored", PhaserG.SHOTS_PER_TURN,
                phaserOf(s).getMaxShotsPerTurn());
        assertTrue("and it says so: " + one.note(),
                one.note().contains("fully operational"));
    }

    @Test
    public void repairGivesBackTheWeaponsButNotTheChargesTheCripplingSpent() {
        Fighter s = hurt(7);
        ShuttleSpace box = new ShuttleSpace(s);

        FighterArming.repair(box, s, 2);

        assertFalse(s.isCrippled());
        for (Weapon w : s.getWeapons().fetchAllWeapons())
            assertTrue(w.getName() + " should be back online (J1.332)", w.isFunctional());
        assertEquals("but the charges J1.3324 discharged are spent — a crew must reload them",
                0, fusionCharges(s));
    }

    @Test
    public void aFighterAtFullHullHasNothingToRepair() {
        Fighter s = CataloguedFighter.of("stinger2");
        s.setName("Fighter-1");
        ShuttleSpace box = new ShuttleSpace(s);

        assertNull(FighterArming.repair(box, s, 4).note());
        assertEquals("and the job is not offered", 0,
                Shuttles.crewsWantedFor(CrewTask.REPAIR, box, s));
    }

    @Test
    public void theJobIsOfferedInProportionToTheDamage() {
        ShuttleSpace oneShort = new ShuttleSpace(hurt(1));
        ShuttleSpace badly = new ShuttleSpace(hurt(5));

        assertEquals("a point of damage is one crew's action", 1,
                Shuttles.crewsWantedFor(CrewTask.REPAIR, oneShort, oneShort.getShuttle()));
        assertEquals("more than two points still only fits two crews (J4.8172)", 2,
                Shuttles.crewsWantedFor(CrewTask.REPAIR, badly, badly.getShuttle()));
    }

    @Test
    public void anOrdinaryShuttleCanBeMendedToo() {
        // J4.818 says shuttle damage, not fighter damage.
        AdminShuttle admin = new AdminShuttle();
        admin.setName("Shuttle1");
        admin.setCurrentHull(admin.getHull() - 2);
        ShuttleSpace box = new ShuttleSpace(admin);

        FighterArming.Load one = FighterArming.repair(box, admin, 2);

        assertEquals(1, one.chargesLoaded());
        assertEquals(admin.getHull() - 1, admin.getCurrentHull());
    }
}
