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
    public void twoChannels_promptsAChoice_andBlindsTheChosenOne() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 1);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);

        assertEquals(Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertEquals(1, game.getPendingBlindChoices().size());
        assertEquals(Arrays.asList("1", "2"), game.getPendingBlindChoices().get(0).options);

        Game.ActionResult r = game.submitBlindChoice("2");
        assertTrue(r.getMessage(), r.isSuccess());
        assertFalse("channel 1 spared", scout.getScoutChannels().get(0).isBlinded(0));
        assertTrue("channel 2 blinded", scout.getScoutChannels().get(1).isBlinded(0));
        assertEquals("resumes the interrupted phase", Game.ImpulsePhase.END_OF_IMPULSE, game.getCurrentPhase());
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
    public void twoBlindingWeapons_queueTwoChoices() {
        Game game = new Game();
        Ship scout = scoutWith(game, 3);
        game.queueScoutBlinds(scout, 2);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        assertEquals(2, game.getPendingBlindChoices().size());

        assertTrue(game.submitBlindChoice("1").isSuccess());
        assertEquals("still one blind to assign", Game.ImpulsePhase.BLIND_CHOICE, game.getCurrentPhase());
        assertTrue(game.submitBlindChoice("2").isSuccess());
        assertEquals(Game.ImpulsePhase.END_OF_IMPULSE, game.getCurrentPhase());
    }

    @Test
    public void invalidChannel_isRejected() {
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        game.queueScoutBlinds(scout, 1);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        Game.ActionResult r = game.submitBlindChoice("9");
        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("Invalid"));
    }

    @Test
    public void canSacrificeAnAlreadyBlindedChannel_extendingIt() {
        // The tactical wrinkle: a channel already spent/blinded stays a valid target, so you
        // can extend it and protect a still-useful one (G24.131).
        Game game = new Game();
        Ship scout = scoutWith(game, 2);
        scout.getScoutChannels().get(0).blind(0); // channel 1 already blinded (until 32)
        int firstRecovery = scout.getScoutChannels().get(0).getBlindedUntilImpulse();

        game.queueScoutBlinds(scout, 1);
        game.settleDamagePhase(Game.ImpulsePhase.END_OF_IMPULSE);
        assertTrue("already-blinded channel is still offered", game.getPendingBlindChoices().get(0).options.contains("1"));

        assertTrue(game.submitBlindChoice("1").isSuccess());
        assertTrue("its blinding is extended", scout.getScoutChannels().get(0).getBlindedUntilImpulse() > firstRecovery);
        assertFalse("channel 2 stays clear", scout.getScoutChannels().get(1).isBlinded(0));
    }
}
