package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.OrionShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Orion Stealth Bonus (G15.8): the SSD ECM points ride the general net-ECM
 * calc (D6.394), making the ship harder to hit / lock / seek — exactly like
 * allocated or terrain ECM. Verified through {@code d637Shift}, the shared net
 * shift used by fire, tractors/transporters, and seekers.
 */
public class OrionStealthTest {

    private Game game;
    private Ship fed;    // attacker
    private Ship orion;  // +2 stealth
    private Ship klingon; // no stealth (control)

    private Player player(String team, Faction f) {
        Player p = new Player();
        p.setTeamName(team);
        p.setFaction(f);
        return p;
    }

    @Before
    public void setUp() {
        game = new Game();

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setOwner(player("Federation", Faction.Federation));

        orion = new Ship();
        orion.init(OrionShips.getLr());
        orion.setLocation(new Location(11, 10));
        orion.setOwner(player("Orion", Faction.Orion));

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(12, 10));
        klingon.setOwner(player("Klingon", Faction.Klingon));

        game.getShips().add(fed);
        game.getShips().add(orion);
        game.getShips().add(klingon);
    }

    @Test
    public void stealthBonus_raisesNetEcm() {
        // +2 stealth, nothing else allocated → floor(sqrt(2)) = 1.
        assertEquals("Orion stealth +2 adds to net ECM (G15.8)", 1, game.d637Shift(fed, orion));
    }

    @Test
    public void nonStealthShip_hasNoBonus() {
        assertEquals(0, game.d637Shift(fed, klingon));
    }

    @Test
    public void eccmCancelsStealth() {
        fed.setActiveFireControl(true);
        fed.allocateEw(0, 2, 1); // 2 ECCM cancels the 2-point stealth
        assertEquals("2 ECCM burns through the stealth → no shift", 0, game.d637Shift(fed, orion));
    }
}
