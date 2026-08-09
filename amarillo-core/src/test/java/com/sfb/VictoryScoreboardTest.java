package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Scoreboard assembly tests (Game.calculateVictoryPoints): team attribution,
 * especially for captured ships. D7.50 transfers a captured ship's owner to
 * the captor, so without capturedFromTeam the scoreboard would award the 200%
 * capture VP to the ORIGINAL owner (the victim scoring its own loss) — the
 * inversion found and fixed 2026-07-09.
 */
public class VictoryScoreboardTest {

    private Game game;
    private Ship fed;
    private Ship klingon;
    private Player fedPlayer;
    private Player klingonPlayer;

    @Before
    public void setUp() {
        game = new Game();

        fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");
        klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingons");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setOwner(fedPlayer);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(11, 10));
        klingon.setOwner(klingonPlayer);

        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    private Game.TeamScore teamScore(Game.Scoreboard board, String team) {
        return board.teams().stream()
                .filter(t -> t.teamName().equals(team))
                .findFirst().orElseThrow();
    }

    @Test
    public void capturedShip_scoresForTheCaptorTeam() {
        // Simulate the D7.50 capture effects: ownership transfers to the captor,
        // capturedFromTeam remembers the side it was taken from
        klingon.setCaptured(true);
        klingon.setCapturedFromTeam(klingonPlayer.getTeamName());
        klingonPlayer.getPlayerUnits().remove(klingon);
        fedPlayer.getPlayerUnits().add(klingon);
        klingon.setOwner(fedPlayer);

        Game.Scoreboard board = game.calculateVictoryPoints();

        Game.ShipVpRow row = board.rows().stream()
                .filter(r -> r.shipName().equals("IKV Saber"))
                .findFirst().orElseThrow();
        assertEquals("Captured ship's row stays attributed to its original side",
                "Klingons", row.teamName());
        assertEquals("CAPTURED", row.status());
        assertTrue(row.vpScored() > 0);

        assertEquals("The CAPTOR scores the 200% capture VP",
                row.vpScored(), teamScore(board, "Federation").vpScored());
        assertEquals("The victim scores nothing for losing its own ship",
                0, teamScore(board, "Klingons").vpScored());
    }

    @Test
    public void boardingCapture_recordsCapturedFromTeam() {
        // The real capture path (D7.53 effects) must remember the original side.
        // Stage a hopeless defense: no defenders, attackers aboard, attacker recorded.
        klingon.getCrew().getFriendlyTroops().removeCasualties(999);
        klingon.addEnemyBoardingParties(20);
        klingon.setBoardingAttacker(fedPlayer);

        // Run boarding combat rounds until captured (control rooms fall over rounds)
        for (int i = 0; i < 30 && !klingon.isCaptured(); i++)
            game.performBoardingCombat(klingon);

        assertTrue("Undefended ship must eventually be captured", klingon.isCaptured());
        assertEquals("Capture must record the original team for VP attribution",
                "Klingons", klingon.getCapturedFromTeam());
        assertEquals("D7.50: ownership transferred to the captor",
                fedPlayer, klingon.getOwner());
    }

    @Test
    public void capturedThenDestroyed_scoresOnly100PctOnScoreboard() {
        klingon.setCaptured(true);
        klingon.setCapturedFromTeam("Klingons");
        klingon.setOwner(fedPlayer);
        klingon.setBattleStatus(com.sfb.properties.BattleStatus.DESTROYED);

        Game.Scoreboard board = game.calculateVictoryPoints();

        Game.ShipVpRow row = board.rows().stream()
                .filter(r -> r.shipName().equals("IKV Saber"))
                .findFirst().orElseThrow();
        assertEquals("Destruction truncates the capture (user ruling 2026-07-09)",
                "DESTROYED", row.status());
    }

    @Test
    public void commanderOptionSpend_isAwardedToTheEnemy() {
        // S2.20 step B: what you buy in the COI, you hand to the enemy as points.
        fed.setCoiSpend(10);    // Fed bought 10 BPV of Commander's Options
        klingon.setCoiSpend(3); // Klingon bought 3

        Game.Scoreboard board = game.calculateVictoryPoints();

        // Both ships intact → no step-C points; only the COI transfer scores.
        assertEquals("Klingons receive the Fed's COI spend",
                10, teamScore(board, "Klingons").vpScored());
        assertEquals("Federation receive the Klingon's COI spend",
                3, teamScore(board, "Federation").vpScored());
        // ...and the scoreboard itemizes it so players can see it.
        assertEquals("Fed team's forfeited COI is shown", 10, teamScore(board, "Federation").coiForfeited());
        assertEquals("Klingon team's forfeited COI is shown", 3, teamScore(board, "Klingons").coiForfeited());
        Game.ShipVpRow fedRow = board.rows().stream()
                .filter(r -> r.shipName().equals("USS Enterprise")).findFirst().orElseThrow();
        assertEquals("per-ship COI spend is shown", 10, fedRow.coiSpend());
    }

    @Test
    public void coiSpend_roundsPerS224() {
        klingon.setCoiSpend(4.5); // rounds up to 5 (S2.24)
        Game.Scoreboard board = game.calculateVictoryPoints();
        assertEquals(5, teamScore(board, "Federation").vpScored());
    }

    @Test
    public void crippledDisengagedShip_scoresAsCrippled() {
        // Highest-applicable: crippled (50%) beats disengaged (25%) — the old
        // inline Game code checked disengaged first and under-scored this case
        klingon.setDisengaged(true);
        klingon.getSpecialFunctions().damageExcessDamage(); // cripples the ship

        Game.Scoreboard board = game.calculateVictoryPoints();

        Game.ShipVpRow row = board.rows().stream()
                .filter(r -> r.shipName().equals("IKV Saber"))
                .findFirst().orElseThrow();
        assertEquals("CRIPPLED", row.status());
    }
}
