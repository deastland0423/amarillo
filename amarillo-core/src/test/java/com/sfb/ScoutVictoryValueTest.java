package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * A scout's chart entry is economic/combat, and the two numbers do different jobs. G24.352:
 * once a scout is with other ships on its side, "the reduced combat BPV is ignored and the
 * economic BPV is used for both purposes" — so a scout costs its economic value to buy and is
 * worth its economic value when killed. Scoring it at combat BPV made it cheaper to lose than
 * to buy, which is backwards: scouts are expensive to build and losing one hurts.
 */
public class ScoutVictoryValueTest {

    private Player player(String team) {
        Player p = new Player();
        p.setTeamName(team);
        return p;
    }

    private Ship scout(String name, int combatBpv, int economicBpv, Player owner) {
        Map<String, Object> m = new HashMap<>(FederationShips.getFedCa());
        m.put("bpv", combatBpv);
        m.put("epv", economicBpv);
        Ship s = new Ship();
        s.init(m);
        s.setName(name);
        s.setLocation(new Location(10, 10));
        s.setOwner(owner);
        ScoutChannel c = new ScoutChannel();
        c.setDesignator("1");
        c.setDacHitLocaiton("torp");
        s.getWeapons().addWeapon(c);
        return s;
    }

    private Ship plain(String name, Player owner) {
        Ship s = new Ship();
        s.init(KlingonShips.getD7());
        s.setName(name);
        s.setLocation(new Location(11, 10));
        s.setOwner(owner);
        return s;
    }

    private Game.ShipVpRow rowFor(Game game, String name) {
        return game.calculateVictoryPoints().rows().stream()
                .filter(r -> r.shipName().equals(name))
                .findFirst().orElseThrow();
    }

    @Test
    public void aShipWithScoutChannelsIsAScout() {
        Player fed = player("Federation");
        assertTrue(scout("Scout", 100, 120, fed).isScout());
        assertFalse(plain("Plain", fed).isScout());
    }

    /** G24.352: in company, the scout scores on its economic value. */
    @Test
    public void aScoutInCompanyScoresItsEconomicValue() {
        Game game = new Game();
        Player fed = player("Federation");
        Player kli = player("Klingons");

        game.getShips().add(scout("De Gama", 100, 120, fed));
        game.getShips().add(plain("Friend", fed));
        game.getShips().add(plain("Enemy", kli));

        assertEquals("scored on economic BPV, not the 100 it fights at",
                120, rowFor(game, "De Gama").gabpv());
    }

    /** Two scouts count as a scout and a non-scout, so both score economic (G24.352). */
    @Test
    public void twoScoutsAreCompanyForEachOther() {
        Game game = new Game();
        Player fed = player("Federation");
        Player kli = player("Klingons");

        game.getShips().add(scout("De Gama", 100, 120, fed));
        game.getShips().add(scout("Balboa", 100, 120, fed));
        game.getShips().add(plain("Enemy", kli));

        assertEquals(120, rowFor(game, "De Gama").gabpv());
        assertEquals(120, rowFor(game, "Balboa").gabpv());
    }

    /** G24.351: a scout with no company of its own keeps the stated combat value. */
    @Test
    public void aScoutAloneKeepsItsCombatValue() {
        Game game = new Game();
        Player fed = player("Federation");
        Player kli = player("Klingons");

        game.getShips().add(scout("De Gama", 100, 120, fed));
        game.getShips().add(plain("Enemy", kli));

        assertEquals("alone on its side, so the stated combat value stands",
                100, rowFor(game, "De Gama").gabpv());
    }

    /** Nothing changes for a ship whose chart entry is a single number. */
    @Test
    public void anOrdinaryShipIsUnaffected() {
        Game game = new Game();
        Player fed = player("Federation");
        Player kli = player("Klingons");

        Ship p = plain("Plain", fed);
        game.getShips().add(p);
        game.getShips().add(plain("Enemy", kli));

        assertEquals(p.getBattlePointValue(), rowFor(game, "Plain").gabpv());
    }
}
