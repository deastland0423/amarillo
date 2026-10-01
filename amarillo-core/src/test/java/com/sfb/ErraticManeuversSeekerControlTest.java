package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.KzintiShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.DroneRack;

/**
 * C10.512: "A unit using EM cannot guide seeking weapons."
 * <p>
 * This is the one EM clause that is not a guard. A refusal would be no use on its own: the
 * interesting case is a ship ALREADY guiding seekers that decides to start manoeuvring, and the
 * rules say what must then happen. F3.532 calls it an involuntary transfer, triggered by "any
 * event which disqualifies the controlling unit under (F3.3)". F3.5 then lets the player hand
 * each weapon to another friendly unit, and F3.41 settles the rest: "if no other unit assumes
 * control, the seeking weapon will become inert (FD1.7) unless it is capable of controlling
 * itself" — and an inert drone destroys itself without causing damage (FD1.71).
 * <p>
 * So the rule is expressed as a CONTROL CAPACITY OF ZERO rather than as a release routine of
 * its own, and the existing overflow machinery supplies the whole procedure: a ship over its
 * capacity is queued into the CONTROL_OVERFLOW phase and the player resolves one seeker at a
 * time, transferring or releasing. The timing is free as well — EM comes into force in Stage 6E
 * (C10.311) and the overflow sweep runs on the next phase transition, where it already catches
 * a cloak or a blinded scout channel costing a ship its capacity (G24.242).
 * <p>
 * NOT covered, deliberately: a FIGHTER that starts EM while guiding its own drones. The sweep
 * iterates ships only. See the note on {@code Fighter.acquireControl}.
 */
public class ErraticManeuversSeekerControlTest {

    private Game game;
    private Ship kzinti;
    private Ship consort;
    private Ship target;

    @Before
    public void setUp() {
        game = new Game();

        Player kzin = new Player();
        kzin.setTeamName("Kzinti");
        Player klingon = new Player();
        klingon.setTeamName("Klingon");

        kzinti = place(KzintiShips.getKzinBC(), "KHS Quasar", 10, 10, kzin);
        consort = place(KzintiShips.getKzinBC(), "KHS Pulsar", 11, 10, kzin);
        target = place(KlingonShips.getD7(), "IKV Saber", 10, 6, klingon);
        target.setFacing(13);

        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setErraticManuvers(s.getPerformanceData().getErraticCost());
            game.submitAllocation(s, e);
        }

        kzinti.addLockOn(target);
        consort.addLockOn(target);
    }

    private Ship place(java.util.Map<String, Object> data, String name, int x, int y, Player p) {
        Ship s = new Ship();
        s.init(data);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        s.setOwner(p);
        s.setActiveFireControl(true);
        game.getShips().add(s);
        return s;
    }

    private void toActivity() {
        for (int guard = 0; guard < 400; guard++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    /** Put one drone in the air under the Kzinti's control, and return it. */
    private Drone launchOne() {
        toActivity();
        DroneRack rack = kzinti.getWeapons().fetchAllWeapons().stream()
                .filter(w -> w instanceof DroneRack).map(w -> (DroneRack) w)
                .findFirst().orElseThrow(() -> new AssertionError("the BC should carry racks"));
        Drone d = rack.getAmmo().get(0);
        assertTrue(game.launchDrone(kzinti, target, rack, d, 0).isSuccess());
        assertEquals("fixture: the launcher should be guiding it", kzinti, d.getController());
        return d;
    }

    /** Announce EM and run to the end of the impulse, where C10.311 brings it into force. */
    private void beginEm(Ship s) {
        assertTrue(game.announceErraticManeuvers(s, true).isSuccess());
        for (int i = 0; i < 8 && game.getCurrentPhase() != Game.ImpulsePhase.END_OF_IMPULSE; i++)
            game.advancePhase();
        game.advancePhase();
        assertTrue("fixture: EM should be in force", s.isEmEffective());
    }

    // ---------------------------------------------------------------- the capacity

    @Test
    public void aShipConductingEmHasNoControlChannelsAtAll() {
        assertTrue("fixture: it has channels to begin with", kzinti.getControlCapacity() > 0);

        beginEm(kzinti);

        assertEquals("C10.512: it cannot guide seeking weapons, so it has no usable channels",
                0, kzinti.getControlCapacity());
    }

    /** C10.24: held by a tractor it is not conducting EM, so its channels come back. */
    @Test
    public void aTractoredShipKeepsItsChannels() {
        int rated = kzinti.getControlCapacity();
        beginEm(kzinti);
        kzinti.applyTractor(target);

        assertEquals("not manoeuvring while held, so still guiding", rated,
                kzinti.getControlCapacity());

        kzinti.releaseTractor();
        assertEquals("and loses them again on release", 0, kzinti.getControlCapacity());
    }

    /** F3.52: a unit assuming control must itself qualify, so EM blocks a handoff IN too. */
    @Test
    public void aShipConductingEmCannotBeHandedASeeker() {
        Drone d = launchOne();
        beginEm(consort);

        assertFalse("C10.512 bars it from taking the weapon on",
                consort.acquireControl(d));
    }

    // ---------------------------------------------------------------- the consequence

    /**
     * The point of the whole exercise: starting EM while guiding puts the player in the
     * CONTROL_OVERFLOW phase, which is F3.532's involuntary transfer by another name.
     */
    @Test
    public void startingEmWhileGuidingForcesTheControlChoice() {
        launchOne();
        assertTrue("fixture: it is guiding something", kzinti.getControlUsed() > 0);
        assertTrue("fixture: and nothing is pending yet",
                game.getPendingControlOverflows().isEmpty());

        beginEm(kzinti);

        assertFalse("the ship must be made to deal with its seekers",
                game.getPendingControlOverflows().isEmpty());
        assertEquals(kzinti, game.getPendingControlOverflows().get(0).ship);
        assertEquals("and the game stops to ask", Game.ImpulsePhase.CONTROL_OVERFLOW,
                game.getCurrentPhase());
    }

    /** F3.5: handing it to a friendly unit that still qualifies is the way to keep it alive. */
    @Test
    public void theSeekerCanBeHandedToAConsort() {
        Drone d = launchOne();
        beginEm(kzinti);

        Game.ActionResult r = game.submitControlOverflowChoice(d.getName(), consort.getName());

        assertTrue(r.getMessage(), r.isSuccess());
        assertEquals("the consort is guiding it now", consort, d.getController());
        assertTrue("and the manoeuvring ship is clear", kzinti.getControlUsed() == 0);
        assertTrue("so nothing is pending", game.getPendingControlOverflows().isEmpty());
    }

    /**
     * F3.41 and FD1.71: with nobody to take it and no guidance of its own, a released drone
     * goes inert — "the drone destroys itself (without causing any damage)".
     */
    @Test
    public void aReleasedDroneWithNoGuidanceOfItsOwnIsLost() {
        Drone d = launchOne();
        assertFalse("fixture: a plain drone guides nothing itself", d.isSelfGuiding());
        beginEm(kzinti);

        Game.ActionResult r = game.submitControlOverflowChoice(d.getName(), null);

        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("FD1.71: an inert drone destroys itself",
                game.getSeekers().contains((Seeker) d));
    }

    /** F3.42: a self-guiding drone carries on instead, which is the whole point of the type. */
    @Test
    public void aSelfGuidingDroneCarriesOnAlone() {
        Drone d = launchOne();
        d.setSelfGuiding(true);
        beginEm(kzinti);

        Game.ActionResult r = game.submitControlOverflowChoice(d.getName(), null);

        assertTrue(r.getMessage(), r.isSuccess());
        assertTrue("F3.42: released, it guides itself", game.getSeekers().contains((Seeker) d));
    }

    // ---------------------------------------------------------------- not a blanket

    @Test
    public void aShipNotManoeuvringKeepsGuidingNormally() {
        Drone d = launchOne();

        assertTrue("nothing forced on a ship flying straight",
                game.getPendingControlOverflows().isEmpty());
        assertEquals(kzinti, d.getController());
    }
}
