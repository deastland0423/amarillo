package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * (D3.21) Internal damage points, counted per side.
 *
 * <h2>What an "internal hit" is, in the rulebook's own words</h2>
 * D3.21: "Damage points scored on a shield that is 'down' penetrate to the interior and destroy
 * systems within the ship. These are called 'internal hits' or 'internal damage points.'" With
 * an exception that decides the awkward case: "in the case of ships with armor (D4.12), damage
 * which penetrates the shields must ALSO PENETRATE THE ARMOR before it is considered 'internal'
 * damage."
 *
 * <h2>Why it is tracked at all</h2>
 * SH47.5 ends a scenario on it — "if no boxes exist, the player who scored the most internal
 * hits on the other LOSES the scenario and ruins his career by provoking an incident" — but it
 * is also simply the number players argue about after a pass, so it is on the scoreboard in
 * every game rather than wired to one scenario.
 */
public class InternalsScoredTest {

    @Test
    public void internalsAreCreditedToTheSideThatScoredThem() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");

        applyAll(kli, 5, fed);

        assertEquals("the Federation put five into the Klingon",
                5, game.internalsScoredBy("Federation"));
        assertEquals("and the Klingon has scored nothing", 0,
                game.internalsScoredBy("Klingons"));
    }

    /** Both sides at once, so a shared counter would show up as a wrong total. */
    @Test
    public void eachSideKeepsItsOwnTally() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");

        applyAll(kli, 7, fed);
        applyAll(fed, 3, kli);

        assertEquals(7, game.internalsScoredBy("Federation"));
        assertEquals(3, game.internalsScoredBy("Klingons"));
    }

    /**
     * (D3.21's exception) Armor is not an internal. Damage has to get through it first, so a
     * ship with armor absorbs the first points without anybody scoring an internal for them.
     */
    @Test
    public void armorAbsorptionIsNotAnInternal() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        // D4.12 names the hulls that carry armor; the Federation OCL is the sample with it.
        Ship armored = ship(FederationShips.getFedOcl(), "USS Reliant", 12, 12,
                shipNamed(game, "IKV Saber").getOwner());
        game.getShips().add(armored);
        int armor = armored.getHullBoxes().getAvailableArmor();
        assumeArmor(armor);

        applyAll(armored, armor + 4, fed);

        assertEquals("only what got past the armor counts",
                4, game.internalsScoredBy("Federation"));
    }

    /** With no armor the whole penetration is internal, which pins the test above. */
    @Test
    public void withoutArmorEveryPenetratingPointIsAnInternal() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");
        assertEquals("fixture: the D7 carries no armor",
                0, kli.getHullBoxes().getAvailableArmor());

        applyAll(kli, 6, fed);
        assertEquals(6, game.internalsScoredBy("Federation"));
    }

    /**
     * Unattributed damage scores for nobody. A mine, an asteroid or an exploding wreck has no
     * team behind it, and SH47.5 asks who SCORED hits on the other — so these must not drift
     * into either total.
     */
    @Test
    public void damageWithNoAttackerScoresForNobody() {
        Game game = twoShips();
        Ship kli = shipNamed(game, "IKV Saber");

        applyAll(kli, 9, null);

        assertEquals(0, game.internalsScoredBy("Federation"));
        assertEquals(0, game.internalsScoredBy("Klingons"));
        assertTrue("the ship still took them, though", kli.isDamaged());
    }

    /**
     * "On the OTHER" (SH47.5). A side's hits on its own ships — a blast catching a friend, or a
     * captured hull turning its guns around — are not scored against anybody.
     */
    @Test
    public void hitsOnYourOwnShipsDoNotCount() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship second = ship(FederationShips.getFedCa(), "USS Kongo", 11, 11,
                fed.getOwner());
        game.getShips().add(second);

        applyAll(second, 8, fed);

        assertEquals("eight points into its own side is not scoring on the other",
                0, game.internalsScoredBy("Federation"));
        assertEquals("but the ship records them, so the question can still be asked",
                Integer.valueOf(8),
                second.getInternalsTakenByTeam().get("Federation"));
    }

    /**
     * A destroyed hull keeps its history, which is the whole reason the tally lives on the
     * victim: the hits that killed a ship are the ones most worth counting, and a counter on
     * the shooter would lose them when its own ship went.
     */
    @Test
    public void internalsScoredOnAShipThatDiedStillCount() {
        Game game = twoShips();
        Ship fed = shipNamed(game, "USS Enterprise");
        Ship kli = shipNamed(game, "IKV Saber");

        applyAll(kli, 12, fed);
        game.getShips().remove(kli);
        game.getDestroyedShips().add(kli);

        assertEquals("the kill still counts for the side that made it",
                12, game.internalsScoredBy("Federation"));
    }

    /** It is on the scoreboard every broadcast, not only at game end. */
    @Test
    public void theScoreboardCarriesIt() {
        Game game = twoShips();
        applyAll(shipNamed(game, "IKV Saber"), 4, shipNamed(game, "USS Enterprise"));

        Game.TeamScore fed = game.calculateVictoryPoints().teams().stream()
                .filter(t -> "Federation".equals(t.teamName())).findFirst().orElseThrow();
        assertEquals(4, fed.internalsScored());

        com.sfb.dto.GameStateDto dto = new com.sfb.dto.GameStateDto(game, "Klingons");
        com.sfb.dto.GameStateDto.TeamScoreDto row = dto.scoreboard.teams.stream()
                .filter(t -> "Federation".equals(t.teamName)).findFirst().orElseThrow();
        assertEquals("both players see it — the opposing captain counts the holes",
                4, row.internalsScored);
    }

    // ---------------------------------------------------------------- across a save

    /**
     * The tally survives a reload, and so do the two counters beside it that were NOT saved
     * before this work.
     *
     * <h2>The G9.21 odometer</h2>
     * {@code internalDamagePointsTotal} is not a statistic: G9.21 kills a crew unit on every
     * TENTH point and D7.21 takes a boarding party the same way, both measured against the
     * cumulative figure. It was not saved, so a reload reset the odometer and a ship that had
     * taken nine internals needed ten more for its next casualty instead of one. Neither number
     * reaches the DTO, so the whole-graph round-trip comparison could never have seen it.
     */
    @Test
    public void theTallyAndTheOdometerSurviveASave() {
        Game before = twoShips();
        Ship kli = shipNamed(before, "IKV Saber");
        applyAll(kli, 9, shipNamed(before, "USS Enterprise"));

        Map<String, Object> saved = kli.exportState();
        Ship restored = ship(KlingonShips.getD7(), "IKV Saber", 20, 20, null);
        restored.applyState(saved);

        assertEquals("the per-team tally travels", Integer.valueOf(9),
                restored.getInternalsTakenByTeam().get("Federation"));
        assertTrue("and so does the running total", restored.isDamaged());

        // The tenth point must kill a crew unit immediately, not after another ten.
        int crewBefore = restored.getCrew().getAvailableCrewUnits();
        applyAll(restored, 1, null);
        assertEquals("G9.21's tenth point still lands after a reload",
                crewBefore - 1, restored.getCrew().getAvailableCrewUnits());
    }

    /** An older save carries none of it, and must not invent a history. */
    @Test
    public void aSaveWithoutTheKeysLeavesAFreshHullAlone() {
        Game game = twoShips();
        Ship kli = shipNamed(game, "IKV Saber");
        Map<String, Object> saved = kli.exportState();
        saved.remove("internalDamagePointsTotal");
        saved.remove("crewKilledByInternalDamage");
        saved.remove("internalsTakenByTeam");

        Ship restored = ship(KlingonShips.getD7(), "IKV Saber", 20, 20, null);
        restored.applyState(saved);

        assertFalse("a fresh hull has taken nothing", restored.isDamaged());
        assertTrue(restored.getInternalsTakenByTeam().isEmpty());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Apply damage to completion, answering DAC choices as the resolver does.
     *
     * <p>{@code applyInternalDamage} is INTERRUPTIBLE: a DAC result needing a player choice
     * stops the loop and hands back {@code remainingBleed}. The first draft of this test called
     * it once and expected every point to land, which is why five points scored one — the
     * numbers were right and the expectations were wrong. {@code DamageResolver} resumes through
     * the pending-choice queue; this does the same thing by always taking the first option.
     */
    private static void applyAll(Ship target, int points, Ship attacker) {
        Ship.DamageResult result = target.applyInternalDamage(points, attacker);
        while (result.choiceRequired) {
            String chosen = result.options == null || result.options.isEmpty()
                    ? null : result.options.get(0);
            target.applyDacChoiceHit(result.choiceType, chosen, attacker);
            if (result.remainingBleed <= 0)
                return;
            result = target.applyInternalDamage(result.remainingBleed, attacker);
        }
    }

    private static void assumeArmor(int armor) {
        assertTrue("fixture: this test needs a hull with armor, got " + armor, armor > 0);
    }

    private static Game twoShips() {
        Game game = new Game();

        Player fedPlayer = new Player();
        fedPlayer.setName("Captain Dan");
        fedPlayer.setTeamName("Federation");
        Player kliPlayer = new Player();
        kliPlayer.setName("Captain Dave");
        kliPlayer.setTeamName("Klingons");
        game.getPlayers().add(fedPlayer);
        game.getPlayers().add(kliPlayer);

        game.getShips().add(ship(FederationShips.getFedCa(), "USS Enterprise", 10, 10, fedPlayer));
        game.getShips().add(ship(KlingonShips.getD7(), "IKV Saber", 20, 20, kliPlayer));
        return game;
    }

    private static Ship ship(Map<String, Object> spec, String name, int x, int y, Player owner) {
        Ship s = new Ship();
        s.init(spec);
        s.setName(name);
        s.setLocation(new Location(x, y));
        s.setFacing(1);
        if (owner != null) {
            s.setOwner(owner);
            owner.getPlayerUnits().add(s);
        }
        return s;
    }

    private static Ship shipNamed(Game game, String name) {
        for (Ship s : game.getShips())
            if (name.equals(s.getName()))
                return s;
        throw new AssertionError("no ship called " + name);
    }
}
