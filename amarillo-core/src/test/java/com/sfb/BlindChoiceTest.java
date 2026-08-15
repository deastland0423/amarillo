package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Blinding a scout's own channels by its weapons fire (G24.13) is the firing player's choice
 * (G24.131): with 2+ powered channels the game pauses in BLIND_CHOICE and the player picks
 * which channel takes each blind — including sacrificing a channel that's already spent or
 * blinded. With 0–1 powered channels there is no choice and it resolves silently. Bases never
 * blind their own channels (G24.135).
 */
public class BlindChoiceTest {

    private Ship scoutWith(Game game, int channels) {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("Scout");
        s.setLocation(new Location(10, 10));
        for (int i = 1; i <= channels; i++) {
            ScoutChannel c = new ScoutChannel();
            c.setDesignator(String.valueOf(i));
            c.setDacHitLocaiton("torp");
            c.setPowered(true);
            s.getWeapons().addWeapon(c);
        }
        game.getShips().add(s);
        return s;
    }

    @Test
    public void oneBlindAmongTwoChannels_promptsAChoice_andSparesTheOther() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 1); // 1 blind, 2 unblinded → pick which one
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);

        assertEquals(Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertEquals(2, game.unblindedPoweredChannels(scout).size()); // both offered

        Game.ActionResult r = game.submitBlindChoice("2");
        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("channel 1 spared", scout.getScoutChannels().get(0).isBlinded(0));
        assertTrue("channel 2 blinded", scout.getScoutChannels().get(1).isBlinded(0));
        assertEquals("resumes the interrupted phase", Game.ImpulsePhase.END_OF_IMPULSE, game.getCurrentPhase());
    }

    @Test
    public void blindsEqualToUnblinded_allBlinded_noChoice() {
        // 2 blinding weapons, 2 unblinded channels → both blind, nothing to pick.
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 2);
        assertTrue(game.getPendingBlindChoices().isEmpty());
        assertTrue(scout.getScoutChannels().get(0).isBlinded(0));
        assertTrue(scout.getScoutChannels().get(1).isBlinded(0));
    }

    @Test
    public void alreadyBlindedChannel_isNotOffered() {
        // Channel 1 already blinded; a new blind must fall on an unblinded channel (2 or 3).
        Game game = new Game();
        Ship scout = scoutWith(game, 3);
        scout.getScoutChannels().get(0).blind(0); // channel 1 blinded
        game.queueScoutBlinds(scout, 1); // 1 blind, 2 unblinded (2 & 3) → choice among those
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);

        java.util.List<String> offered = game.unblindedPoweredChannels(scout).stream()
                .map(ScoutChannel::getDesignator).collect(java.util.stream.Collectors.toList());
        assertEquals(Arrays.asList("2", "3"), offered);
        assertFalse("can't double-blind an already-blinded channel", game.submitBlindChoice("1").isSuccess());
        assertTrue(game.submitBlindChoice("2").isSuccess());
    }

    @Test
    public void allChannelsBlinded_extendsEarliest_noMenu() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        scout.getScoutChannels().get(0).blind(0);  // recovers at 32
        scout.getScoutChannels().get(1).blind(10);  // recovers at 42
        int earliestBefore = scout.getScoutChannels().get(0).getBlindedUntilImpulse();

        game.queueScoutBlinds(scout, 1); // no unblinded → forced extend, no choice
        assertTrue(game.getPendingBlindChoices().isEmpty());
        assertTrue("earliest-recovering channel extended",
                scout.getScoutChannels().get(0).getBlindedUntilImpulse() > earliestBefore);
    }

    @Test
    public void singlePoweredChannel_autoResolves_noPrompt() {
        Game game = new Game();
        Ship scout = scoutWith(game, 1);
        game.queueScoutBlinds(scout, 1);
        assertTrue("no choice with one channel", game.getPendingBlindChoices().isEmpty());
        assertTrue("the only channel is blinded", scout.getScoutChannels().get(0).isBlinded(0));
    }

    @Test
    public void noPoweredChannels_nothingHappens() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        scout.getScoutChannels().forEach(c -> c.setPowered(false));
        game.queueScoutBlinds(scout, 1);
        assertTrue(game.getPendingBlindChoices().isEmpty());
    }

    @Test
    public void baseDoesNotBlindItsOwnChannels() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        scout.setBase(true);
        game.queueScoutBlinds(scout, 1);
        assertTrue("G24.135", game.getPendingBlindChoices().isEmpty());
    }

    @Test
    public void twoBlindsAmongThreeChannels_choiceThenForced() {
        // 2 blinds, 3 unblinded → pick which two; after the first pick the second remains a
        // choice (2 unblinded left), then it's done and the third is spared.
        Game game = new Game();
        Ship scout = scoutWith(game, 3);
        game.queueScoutBlinds(scout, 2);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        assertEquals(1, game.getPendingBlindChoices().size());
        assertEquals(2, game.getPendingBlindChoices().get(0).remaining);

        assertTrue(game.submitBlindChoice("1").isSuccess());
        assertEquals("still one blind to assign", Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertTrue(game.submitBlindChoice("2").isSuccess());
        assertEquals(Game.ImpulsePhase.END_OF_IMPULSE, game.getCurrentPhase());
        assertFalse("channel 3 spared", scout.getScoutChannels().get(2).isBlinded(0));
    }

    @Test
    public void enterBlindChoiceIfPending_promptsForABlindQueuedOutsideCombat() {
        // Models the plasma-launch path (G24.1342): a blind is queued during the Activity phase,
        // outside the damage-settling flow, so the launcher enters BLIND_CHOICE directly.
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 1); // launch queued 1 blind, 2 unblinded → a choice
        game.enterBlindChoiceIfPending();
        assertEquals(Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertTrue(game.submitBlindChoice("1").isSuccess());
    }

    @Test
    public void enterBlindChoiceIfPending_noPromptWhenForcedOrNothingPending() {
        Game game = new Game();
        Ship scout = scoutWith(game, 1); // one channel → the launch blind auto-resolves
        game.queueScoutBlinds(scout, 1);
        game.enterBlindChoiceIfPending();
        assertNotEquals(Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertTrue(scout.getScoutChannels().get(0).isBlinded(0));
    }

    @Test
    public void aChannelNotAmongTheUnblindedOptions_isRejected() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 1);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        Game.ActionResult r = game.submitBlindChoice("9");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("unblinded"));
    }
}
