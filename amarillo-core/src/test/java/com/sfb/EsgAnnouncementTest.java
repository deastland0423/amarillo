package com.sfb;

import com.sfb.weapons.ESG;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ESG announcement window and lockouts (G23.31/.311/.33/.323/.47) at the weapon
 * level: a release must be announced 4 impulses ahead, the radius stays secret
 * until the field forms, cancellation and post-drop reactivation are locked out.
 */
public class EsgAnnouncementTest {

    private ESG loaded(int energy) {
        ESG esg = new ESG();
        esg.setStoredEnergy(energy);
        return esg;
    }

    @Test
    public void announce_isPending_andCountsDownFourImpulses() {
        ESG esg = loaded(3);
        esg.announce(2, 10); // announce on impulse 10

        assertTrue(esg.isAnnounced());
        assertFalse("field is not up during the announcement window", esg.isActive());
        assertEquals("forms 4 impulses later (G23.31)", 14, esg.getReleaseImpulse());
        assertEquals(4, esg.announceCountdown(10));
        assertEquals(1, esg.announceCountdown(13));
        assertFalse(esg.readyToRelease(13));
        assertTrue(esg.readyToRelease(14));
    }

    @Test
    public void release_formsFieldAndDumpsEnergy() {
        ESG esg = loaded(3);
        esg.announce(2, 10);
        esg.release(14);

        assertTrue(esg.isActive());
        assertFalse(esg.isAnnounced());
        assertEquals(2, esg.getRadius());
        assertEquals("chart[radius 2][energy 3] (G23.42)", 10, esg.getStrength());
        assertEquals("all energy released on formation (G23.222)", 0, esg.getStoredEnergy());
    }

    @Test
    public void release_withNoEnergy_formsNoField_andArmsReactivationLockout() {
        ESG esg = loaded(0);
        esg.announce(1, 10);
        esg.release(14); // G23.3121: activation with no energy still counts as a drop

        assertFalse(esg.isActive());
        assertFalse("no field can be re-announced immediately after a no-field drop",
                esg.canAnnounce(15));
    }

    @Test
    public void cancel_retainsEnergy_andLocksReannounceForEightImpulses() {
        ESG esg = loaded(3);
        esg.announce(2, 10);
        esg.cancelAnnouncement(12);

        assertFalse(esg.isAnnounced());
        assertEquals("energy is kept on cancellation (G23.33)", 3, esg.getStoredEnergy());
        assertFalse("re-announce blocked for 8 impulses", esg.canAnnounce(19)); // 12 + 8 = 20
        assertTrue(esg.canAnnounce(20));
    }

    @Test
    public void reactivationLockout_matchesG23323Example() {
        // Example (G23.323): activated Impulse #10 of Turn #2 = absolute 42;
        // dropped by end of Turn #2 (absolute 64) → cannot reactivate before
        // Impulse #1 of Turn #4 (absolute 97), announced Impulse #29 of Turn #3 (93).
        ESG esg = loaded(3);
        esg.announce(2, 38); // announce impulse 38 → release 42
        esg.release(42);
        assertTrue(esg.isActive());
        esg.deactivate();
        esg.recordDrop(64);
        esg.addEnergy(3); // re-charge the generator before attempting to re-announce

        assertEquals("earliest re-announce is release-97 minus the 4-impulse notice",
                93, esg.earliestAnnounceImpulse());
        assertFalse(esg.canAnnounce(92));
        assertTrue(esg.canAnnounce(93));
    }

    @Test
    public void radiusIsRecordedButHeldSecretUntilRelease() {
        ESG esg = loaded(5);
        esg.announce(3, 10);
        // The chosen radius is recorded (owner can see it) though the active radius
        // is not yet set — the DTO gates opponent visibility separately (G23.311).
        assertEquals(3, esg.getAnnouncedRadius());
        assertEquals("no active radius while only announced", 0, esg.getRadius());
    }
}
