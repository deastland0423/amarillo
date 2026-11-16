package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.ScoutChannel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * G24.135: a base never blinds its own sensor channels by firing.
 *
 * <p>"Bases do not have exhaust ports for propulsive gases and can mount their sensors in
 * areas where they are shielded from their own weapons fire. No base will blind any of its
 * sensor channels as a result of firing its own weapons. The blinding of explosions
 * (G24.133), pulsars (G24.1813), and gravity waves (G24.1851) remain in effect as do all
 * other (G24.0) rules."
 *
 * <p>This is not a corner case: all nine Base Stations in the data carry two channels each,
 * so every shot a starbase takes runs through this path. {@code queueScoutBlinds} has always
 * honoured the rule; what did not was the combat log, which counted and announced the blinds
 * before handing them over to be discarded. The state was right and the player was told
 * otherwise — which is the worse half to get wrong, because he acts on what he reads.
 *
 * <p>It also explains why firing a phaser-4 as a phaser-3 (E2.25) is pointless. G24.34 says
 * so in a bracket: "a base could fire its phaser-4s as phaser-3s in this manner as well,
 * although there is no reason to, see (G24.135)." Ph-4s are base-only (E2.14), and a base is
 * exempt anyway, so the only thing downfiring saves it is half a point of power.
 */
public class BaseBlindingTest {

	private Ship withChannels(Game game, boolean asBase, int channels) {
		Ship s = new Ship();
		s.init(FederationShips.getFedCa());
		s.setName(asBase ? "Starbase" : "Scout");
		s.setLocation(new Location(10, 10));
		s.setBase(asBase);
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
	public void aBasesChannelsSurviveItsOwnFire() {
		Game game = new Game();
		Ship base = withChannels(game, true, 2);

		game.queueScoutBlinds(base, 2);

		assertTrue("no choice should be queued", game.getPendingBlindChoices().isEmpty());
		assertFalse("channel 1 unblinded", base.getScoutChannels().get(0).isBlinded(0));
		assertFalse("channel 2 unblinded", base.getScoutChannels().get(1).isBlinded(0));
	}

	@Test
	public void aShipWithTheSameChannelsDoesBlind() {
		// The control: identical hull and channels, differing only in isBase. Without this the
		// test above would pass on a fixture that simply never blinds anything.
		Game game = new Game();
		Ship ship = withChannels(game, false, 2);

		game.queueScoutBlinds(ship, 2);

		assertTrue("channel 1 blinded", ship.getScoutChannels().get(0).isBlinded(0));
		assertTrue("channel 2 blinded", ship.getScoutChannels().get(1).isBlinded(0));
	}

	@Test
	public void aBaseIsSparedEvenWhenMoreWeaponsFireThanItHasChannels() {
		// G24.131's overflow rule extends existing blinds by 32 impulses. A base must not
		// reach that path either, however much it fires.
		Game game = new Game();
		Ship base = withChannels(game, true, 2);

		game.queueScoutBlinds(base, 6);

		assertTrue(game.getPendingBlindChoices().isEmpty());
		for (int i = 0; i < 2; i++)
			assertFalse("channel " + (i + 1), base.getScoutChannels().get(i).isBlinded(0));
	}

	// ---------------------------------------------------------------- the log, which lied

	private Ship targetAt(Game game, int x, int y) {
		Ship t = new Ship();
		t.init(FederationShips.getFedCa());
		t.setName("Target");
		t.setLocation(new Location(x, y));
		game.getShips().add(t);
		return t;
	}

	private List<com.sfb.weapons.Weapon> onePhaser() {
		com.sfb.weapons.Phaser1 p = new com.sfb.weapons.Phaser1();
		p.setArcs(com.sfb.utilities.ArcUtils.FH);
		p.setDesignator("1");
		return java.util.Collections.singletonList(p);
	}

	@Test
	public void aBasesCombatLogDoesNotClaimItBlindedItself() throws Exception {
		// The actual fault. queueScoutBlinds always honoured G24.135, so the STATE was right
		// and every test of it passed; the tally and the announcement sat upstream of that
		// call and ran regardless. A starbase fired and was told it had just blinded two of
		// its own channels, while the channels stayed lit.
		Game game = new Game();
		Ship base = withChannels(game, true, 2);
		base.setFacing(1);
		base.chargeCapacitor(base.getWeapons().getAvailablePhaserCapacitor());
		Ship target = targetAt(game, 10, 11);

		String log = game.fireWeapons(base, target, onePhaser(), 1, 1, 1);

		assertFalse("the log must not announce a blind the rule forbids: " + log,
				log.contains("to be blinded"));
		assertFalse("nor name the weapons that would have done it: " + log,
				log.contains("blinding weapon"));
		assertFalse("and nothing actually blinded", base.getScoutChannels().get(0).isBlinded(0));
	}

	@Test
	public void aShipsCombatLogStillAnnouncesTheBlind() throws Exception {
		// The control again, and the thing the guard must not break: a real scout firing a
		// real phaser still reports what it cost him.
		Game game = new Game();
		Ship scout = withChannels(game, false, 2);
		scout.setFacing(1);
		scout.chargeCapacitor(scout.getWeapons().getAvailablePhaserCapacitor());
		Ship target = targetAt(game, 10, 11);

		String log = game.fireWeapons(scout, target, onePhaser(), 1, 1, 1);

		assertTrue("a scout is told what firing cost it: " + log,
				log.contains("to be blinded"));
	}

	@Test
	public void everyBaseStationInTheDataHasChannelsToProtect() throws Exception {
		// The reachability claim this test rests on, measured rather than assumed. If bases
		// ever stop carrying channels, the rule above is still right but stops mattering, and
		// whoever notices should learn it here rather than wonder why this file exists.
		com.fasterxml.jackson.databind.ObjectMapper mapper =
				new com.fasterxml.jackson.databind.ObjectMapper();
		List<String> withChannels = new java.util.ArrayList<>();

		try (java.util.stream.Stream<java.nio.file.Path> files =
				java.nio.file.Files.walk(java.nio.file.Paths.get("../data/factions"))) {
			for (java.nio.file.Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				com.fasterxml.jackson.databind.JsonNode n = mapper.readTree(p.toFile());
				if (!n.path("isBase").asBoolean(false))
					continue;
				if (n.path("weapons").toString().contains("ScoutChannel"))
					withChannels.add(p.getFileName().toString());
			}
		}

		assertEquals("all nine Base Stations carry scout channels: " + withChannels,
				9, withChannels.size());
	}
}
