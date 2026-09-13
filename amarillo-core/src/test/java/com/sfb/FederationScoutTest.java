package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShipSpec;
import com.sfb.properties.Faction;
import com.sfb.weapons.ScoutChannel;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Federation Scout (SC) — the first scout ship. Verifies the JSON loads and its eight
 * special-sensor channels build correctly, split between "torp" and "phaser" DAC hit
 * locations (G24.11/.17) — a mixed-replacement ship.
 */
public class FederationScoutTest {

    @Test
    public void sc_loadsWithEightScoutChannels_splitTorpAndPhaser() throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File("../data/factions/federation/sc.json"));
        assertNotNull("sc.json parsed", spec);
        Ship sc = ShipLibrary.createShip(spec);

        assertEquals(Faction.Federation, sc.getFaction());
        assertEquals("SC", sc.getType());

        var channels = sc.getScoutChannels();
        assertEquals("eight scout channels (G24.11)", 8, channels.size());

        long torp   = channels.stream().filter(c -> "torp".equals(c.getDacHitLocaiton())).count();
        long phaser = channels.stream().filter(c -> "phaser".equals(c.getDacHitLocaiton())).count();
        assertEquals("four channels replaced heavy weapons (torp hits, G24.17)", 4, torp);
        assertEquals("four channels replaced phasers (phaser hits, G24.17)", 4, phaser);

        for (ScoutChannel c : channels)
            assertFalse("channels start unpowered until allocated (G24.14)", c.isPowered());

        // Split BPV: combat 100 / economic 120 (A/B scout value, G24.35 / S2.12)
        assertEquals(100, sc.getBattlePointValue());
        assertEquals(120, sc.getEconomicBpv());
    }

    @Test
    public void aPhaserHit_canDestroyEitherAPhaserOrAPhaserChannel() throws Exception {
        // Owner's-choice DAC: on a "phaser" hit the player picks which phaser-location
        // system dies — a real Phaser-1 or one of the four phaser channels. (G24.17's
        // "special sensors take hits with high priority" is a deferred refinement.)
        ShipSpec spec = ShipSpec.fromJson(new File("../data/factions/federation/sc.json"));
        Ship sc = ShipLibrary.createShip(spec);

        ScoutChannel phaserChannel = sc.getScoutChannels().stream()
                .filter(c -> "phaser".equals(c.getDacHitLocaiton())).findFirst().orElseThrow();
        sc.applyDacChoiceHit("phaser", phaserChannel.getName(), null);
        assertFalse("a phaser hit can be directed at a phaser channel", phaserChannel.isFunctional());
    }
}
