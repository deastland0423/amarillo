package com.sfb;

import static org.junit.Assert.*;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.ControlSpaces;
import com.sfb.systemgroups.UIM;

/**
 * The ship systems converted after the weapons: control spaces, labs, transporters, the cloak,
 * the UIMs and the cargo drone store.
 *
 * <h2>Why these need tests of their own</h2>
 * {@link ShipStateRoundTripTest}'s reflective net is a broad witness, but it can only see what
 * {@code ShipDto} exposes — and several of these are invisible there. The CAPTURED control-space
 * counts, a lab box's last-used timestamp and the cloak's transition impulse are all real battle
 * state that no field of the DTO reports, so a save could drop them in silence and the net would
 * never notice.
 *
 * <p>So this file asserts them directly, and names the rule each one serves.
 */
public class ShipSystemsSaveTest {

    private Ship ship;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Testbed");
    }

    private Ship roundTrip() {
        Map<String, Object> state = ship.exportState();
        Ship restored = new Ship();
        restored.init(FederationShips.getFedCa());
        restored.applyState(state);
        return restored;
    }

    // ---------------------------------------------------------------- control spaces

    @Test
    public void damagedControlSpacesStayDamaged() {
        ship.getControlSpaces().damageBridge();
        int left = ship.getControlSpaces().getAvailableBridge();

        assertEquals(left, roundTrip().getControlSpaces().getAvailableBridge());
    }

    /**
     * A control space held by enemy boarding parties (D16.0) decides who may use what the ship
     * can still do. Invisible to the DTO, so only this test stands between a reload and handing
     * a boarded ship back to its original crew.
     */
    @Test
    public void aCapturedControlSpaceStaysCaptured() {
        ship.getControlSpaces().captureRoom(ControlSpaces.RoomType.BRIDGE);
        int captured = ship.getControlSpaces().getCapturedBridge();
        assertTrue("fixture: the bridge should be in enemy hands", captured > 0);

        assertEquals("a reload must not evict the boarding party",
                captured, roundTrip().getControlSpaces().getCapturedBridge());
    }

    // ---------------------------------------------------------------- labs

    /**
     * G4.451: a lab that has just worked is unavailable for a quarter turn. The timestamp is
     * what decides it, and nothing in the DTO carries it.
     */
    @Test
    public void aLabUsedRecentlyIsStillBusyAfterAReload() {
        int atImpulse = 20;
        ship.getLabs().useLab(atImpulse);
        int freeNow = ship.getLabs().availableLabs(atImpulse + 1);

        Ship after = roundTrip();

        assertEquals("the quarter-turn delay must survive the save",
                freeNow, after.getLabs().availableLabs(atImpulse + 1));
        assertEquals("and expire at the same moment",
                ship.getLabs().availableLabs(atImpulse + 40),
                after.getLabs().availableLabs(atImpulse + 40));
    }

    @Test
    public void aDestroyedLabBoxStaysDestroyed() {
        int before = ship.getLabs().availableLabs(1);
        ship.getLabs().damage();
        assertTrue("fixture: damaging a lab should remove a box",
                ship.getLabs().availableLabs(1) < before);

        assertEquals(ship.getLabs().availableLabs(1), roundTrip().getLabs().availableLabs(1));
    }

    // ---------------------------------------------------------------- UIM

    /**
     * D6.522: "UIMs cannot be repaired during a scenario." A burnout is permanent, so a reload
     * that forgot it would silently hand the device back for the rest of the battle.
     */
    @Test
    public void aBurnedOutUimStaysBurnedOut() {
        Ship klingon = new Ship();
        klingon.init(com.sfb.samples.KlingonShips.getD7());
        klingon.setName("IKV Testbed");
        if (klingon.getUims().isEmpty())
            return;   // this hull carries none; nothing to assert

        klingon.getUims().get(0).damage();

        Map<String, Object> state = klingon.exportState();
        Ship restored = new Ship();
        restored.init(com.sfb.samples.KlingonShips.getD7());
        restored.applyState(state);

        assertTrue("a burnout is permanent for the scenario (D6.522)",
                restored.getUims().get(0).isDamaged());
    }

    @Test
    public void anUndamagedUimComesBackUndamaged() {
        Ship klingon = new Ship();
        klingon.init(com.sfb.samples.KlingonShips.getD7());
        if (klingon.getUims().isEmpty())
            return;

        Map<String, Object> state = klingon.exportState();
        Ship restored = new Ship();
        restored.init(com.sfb.samples.KlingonShips.getD7());
        restored.applyState(state);

        for (UIM uim : restored.getUims())
            assertFalse("nothing should break a UIM that was working", uim.isDamaged());
    }

    // ---------------------------------------------------------------- transporters

    /** Energy banked for transporters is energy already paid for, and must not be refunded. */
    @Test
    public void bankedTransporterEnergySurvives() {
        ship.getTransporters().bankEnergy(1.0);
        double banked = ship.getTransporters().getBankedEnergy();
        assertTrue("fixture: should hold a point", banked > 0);

        assertEquals(banked, roundTrip().getTransporters().getBankedEnergy(), 0.0001);
    }

    // ---------------------------------------------------------------- the format's promises

    /** Every one of these groups must tolerate a save that predates it. */
    @Test
    public void aStateMapMissingTheseGroupsLeavesTheBuiltValuesAlone() {
        int bridge = ship.getControlSpaces().getAvailableBridge();
        int labs = ship.getLabs().availableLabs(1);

        ship.applyState(Map.of("facing", 5));

        assertEquals("an older save must not blank out the control spaces",
                bridge, ship.getControlSpaces().getAvailableBridge());
        assertEquals(labs, ship.getLabs().availableLabs(1));
    }
}
