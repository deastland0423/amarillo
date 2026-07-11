package com.sfb.objects;

import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.TractorBeam;
import com.sfb.systemgroups.Tractors;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Per-beam tractor state (TractorBeam extraction). Each beam individually
 * tracks G7.13 usage and its held unit, so combat damage and DAC choices can
 * destroy a SPECIFIC beam and break exactly that link. Before this extraction,
 * DAC "tractor" hits were silently skipped — tractors were indestructible.
 */
public class TractorBeamTest {

    private Ship fed;     // FedCA — 3 tractor beams
    private Ship klingon;

    @Before
    public void setUp() {
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
    }

    private Drone drone(String name) {
        Drone d = new Drone(DroneType.TypeI);
        d.setName(name);
        return d;
    }

    // -------------------------------------------------------------------------
    // Beam lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void linkUnit_recordsHeldUnitOnSpecificBeam() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone d = drone("Drone-1");

        assertTrue(t.linkUnit(d));

        List<TractorBeam> beams = t.getBeams();
        assertEquals(3, beams.size());
        assertSame(d, beams.get(0).getHeldUnit());
        assertTrue(beams.get(0).isUsedThisTurn());
        assertNull(beams.get(1).getHeldUnit());
        assertTrue(t.getTractoredUnits().contains(d));
    }

    @Test
    public void release_freesLinkButBeamStaysUsed() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone d = drone("Drone-1");
        t.linkUnit(d);

        t.releaseTractor(d);

        assertNull(t.getBeams().get(0).getHeldUnit());
        assertTrue("G7.13: release does not free the beam this turn",
                t.getBeams().get(0).isUsedThisTurn());
        assertEquals(2, t.getBeamsAvailableThisTurn());
        assertFalse(d.isTractored());
    }

    @Test
    public void newTurn_freesUsedIdleBeams_keepsHoldingBeamsUsed() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone released = drone("Drone-1");
        Drone held = drone("Drone-2");
        t.linkUnit(released);
        t.linkUnit(held);
        t.releaseTractor(released);

        t.initForTurn(3); // next turn

        assertEquals("Released beam is fresh; holding beam still in use (G7.42)",
                2, t.getBeamsAvailableThisTurn());
        assertSame(held, t.getBeams().get(1).getHeldUnit());
    }

    // -------------------------------------------------------------------------
    // Damage auto-pick ladder: used-idle → unused-idle → holding
    // -------------------------------------------------------------------------

    @Test
    public void damage_prefersUsedIdleBeam() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone released = drone("Drone-1");
        Drone held = drone("Drone-2");
        t.linkUnit(released);   // beam 1: used
        t.linkUnit(held);       // beam 2: holding
        t.releaseTractor(released); // beam 1: used-idle; beam 3: unused-idle

        String label = t.damageAutoPick();

        assertTrue("Used-idle beam sacrificed first: " + label, label.contains("Tractor #1"));
        assertFalse(t.getBeams().get(0).isFunctional());
        assertEquals(2, t.getAvailableTractors());
        assertSame("Link untouched", held, t.getBeams().get(1).getHeldUnit());
        assertTrue(t.getBeams().get(2).isFunctional());
    }

    @Test
    public void damage_lastResortBreaksTheOnlyLink() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone held = drone("Drone-1");
        t.linkUnit(held);
        t.destroyBeam(2);
        t.destroyBeam(3); // only beam 1 (holding) remains

        assertFalse("Single option — no player choice", t.needsDamageChoice());
        String label = t.damageAutoPick();

        assertTrue("Link break must be logged: " + label, label.contains("link to Drone-1 broken"));
        assertEquals(0, t.getAvailableTractors());
        assertFalse("Held unit released on beam destruction", held.isTractored());
    }

    @Test
    public void damage_returnsNullWhenNoBeamsRemain() {
        Tractors t = fed.getTractors();
        t.destroyBeam(1);
        t.destroyBeam(2);
        t.destroyBeam(3);
        assertNull("DAC advances past an empty system", t.damageAutoPick());
    }

    // -------------------------------------------------------------------------
    // DAC choice: only when every functional beam is holding
    // -------------------------------------------------------------------------

    @Test
    public void choice_requiredOnlyWhenAllFunctionalBeamsHold() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        t.linkUnit(drone("Drone-1"));
        t.linkUnit(drone("Drone-2"));
        assertFalse("Idle beam exists — auto-pick", t.needsDamageChoice());

        t.linkUnit(drone("Drone-3"));
        assertTrue("All 3 beams holding — owner must pick which link breaks",
                t.needsDamageChoice());
    }

    @Test
    public void choice_optionsDescribeEachHeldBeam() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        t.linkUnit(drone("Drone-1"));
        t.linkUnit(klingon);
        t.linkUnit(drone("Drone-3"));

        List<String> options = fed.dacChoiceOptionsForTest("tractor");

        assertEquals(3, options.size());
        assertEquals("Tractor #2 — holding IKV Saber", options.get(1));
    }

    @Test
    public void choice_appliedHit_destroysChosenBeamAndBreaksLink() {
        Tractors t = fed.getTractors();
        t.initForTurn(5);
        Drone d1 = drone("Drone-1");
        Drone d3 = drone("Drone-3");
        t.linkUnit(d1);
        t.linkUnit(klingon);
        t.linkUnit(d3);

        String label = fed.applyDacChoiceHit("tractor", "Tractor #2 — holding IKV Saber", null);

        assertNotNull(label);
        assertTrue(label.contains("link to IKV Saber broken"));
        assertFalse(t.getBeams().get(1).isFunctional());
        assertFalse(klingon.isTractored());
        assertTrue("Other links untouched", d1.isTractored());
        assertTrue(d3.isTractored());
    }

    @Test
    public void choice_invalidSelection_returnsNull() {
        fed.getTractors().initForTurn(5);
        assertNull(fed.applyDacChoiceHit("tractor", "no beam here", null));
        assertNull("Beam 9 does not exist",
                fed.applyDacChoiceHit("tractor", "Tractor #9 — holding X", null));
    }
}
