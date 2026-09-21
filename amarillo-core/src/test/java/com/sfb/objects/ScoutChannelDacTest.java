package com.sfb.objects;

import com.sfb.samples.FederationShips;
import com.sfb.utilities.DacPriority;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * G24.17: a special sensor is destroyed on the DAC hits for the weapon it replaced, with
 * "relatively high priority" (D4.3221) — so it is offered as a target on that location's
 * hit and is forced first under the rule of 3. DacPriority reserves priority 0 for it.
 */
public class ScoutChannelDacTest {

    private Ship scoutish() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa()); // has phasers and photons (torp)
        ScoutChannel ph = new ScoutChannel();
        ph.setDesignator("P");
        ph.setDacHitLocaiton("phaser");
        ship.getWeapons().addWeapon(ph);
        ScoutChannel tp = new ScoutChannel();
        tp.setDesignator("T");
        tp.setDacHitLocaiton("torp");
        ship.getWeapons().addWeapon(tp);
        return ship;
    }

    @Test
    public void channelsTakeTopPriorityInTheirCategory() {
        ScoutChannel phaserCh = new ScoutChannel(); phaserCh.setDacHitLocaiton("phaser");
        ScoutChannel torpCh   = new ScoutChannel(); torpCh.setDacHitLocaiton("torp");
        ScoutChannel droneCh  = new ScoutChannel(); droneCh.setDacHitLocaiton("drone");

        assertEquals("phaser channel is highest phaser priority (G24.17)", 0, DacPriority.phaserPriority(phaserCh));
        assertEquals("torp channel is highest torp priority", 0, DacPriority.torpPriority(torpCh));
        assertEquals("drone channel is highest drone priority", 0, DacPriority.dronePriority(droneCh));
        // A channel only takes top priority in ITS category, not others.
        assertTrue("a torp channel is not a phaser candidate",
                DacPriority.phaserPriority(torpCh) > DacPriority.phaserPriority(new com.sfb.weapons.Phaser1()));
    }

    @Test
    public void phaserChannel_isOfferedOnAPhaserHit() {
        Ship ship = scoutish();
        assertTrue("a phaser hit can destroy a phaser channel (G24.17)",
                ship.dacChoiceOptionsForTest("phaser").contains("ScoutChannel-P"));
    }

    @Test
    public void torpChannel_isOfferedOnATorpHit() {
        Ship ship = scoutish();
        assertTrue("a torp hit can destroy a torp channel (G24.17)",
                ship.dacChoiceOptionsForTest("torp").contains("ScoutChannel-T"));
    }
}
