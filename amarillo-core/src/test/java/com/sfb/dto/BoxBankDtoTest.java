package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * The cycling banks on the wire: laboratories, transporters and tractor beams, box by box.
 * <p>
 * The flat counts beside them cannot carry this. {@code availableTransporters} is the INTACT
 * count, so a ship whose every box is cooling off reports them all available — which is what
 * a bar drawn from those numbers would show, and it would be a lie to the ship's own player
 * as much as to an enemy. A box per box is the only readout with room for the third state.
 */
public class BoxBankDtoTest {

	private Game game;
	private Ship fed;
	private Ship klingon;

	@Before
	public void setUp() {
		game = new Game();

		Player federation = new Player();
		federation.setTeamName("Federation");
		Player empire = new Player();
		empire.setTeamName("Klingon");

		fed = new Ship();
		fed.init(FederationShips.getFedCa());     // 8 labs, 3 transporters, 3 tractors
		fed.setName("Enterprise");
		fed.setLocation(new Location(10, 10));
		fed.setFacing(1);
		fed.setOwner(federation);

		klingon = new Ship();
		klingon.init(KlingonShips.getD7());
		klingon.setName("IKV Saber");
		klingon.setLocation(new Location(11, 10));
		klingon.setFacing(4);
		klingon.setOwner(empire);

		game.getShips().add(fed);
		game.getShips().add(klingon);
		fed.attachClock(game.getClock());
		klingon.attachClock(game.getClock());
	}

	private GameStateDto.ShipDto mine() {
		return (GameStateDto.ShipDto) new GameStateDto(game, "Federation").mapObjects.stream()
				.filter(o -> "Enterprise".equals(o.name))
				.findFirst().orElseThrow(() -> new AssertionError("ship missing from view"));
	}

	private GameStateDto.BoxBankDto bank(String system) {
		return mine().boxBanks.stream().filter(b -> system.equals(b.system)).findFirst()
				.orElseThrow(() -> new AssertionError("no bank named " + system));
	}

	// ---------------------------------------------------------------- which banks appear

	@Test
	public void aHullSendsOneBankPerCyclingSystemItActuallyHas() {
		List<String> names = mine().boxBanks.stream().map(b -> b.system).collect(Collectors.toList());

		assertEquals("in SSD order, and only the ones the hull prints",
				List.of("Laboratories", "Transporters", "Tractor Beams"), names);
	}

	@Test
	public void aSystemTheHullDoesNotHaveIsLeftOutRatherThanSentEmpty() {
		// An absent bank and an empty one are the same thing to a player, and the client should
		// not have to know the difference to avoid drawing a heading over nothing.
		fed.getLabs().init(java.util.Map.of("lab", 0));

		assertFalse("no labs printed, so no lab row",
				mine().boxBanks.stream().anyMatch(b -> "Laboratories".equals(b.system)));
		assertEquals("the others are untouched", 2, mine().boxBanks.size());
	}

	@Test
	public void eachBankItemisesEveryPrintedBox() {
		GameStateDto.BoxBankDto labs = bank("Laboratories");

		assertEquals(8, labs.printed);
		assertEquals(8, labs.boxes.size());
		assertEquals("numbered 1..n in printed order", List.of(1, 2, 3, 4, 5, 6, 7, 8),
				labs.boxes.stream().map(b -> b.number).collect(Collectors.toList()));
	}

	// ---------------------------------------------------------------- the three states

	@Test
	public void aBankWithEveryBoxCoolingStillReportsThemAllIntact() {
		// The case the flat counts get wrong, stated as plainly as it can be: nothing is
		// damaged, and nothing can be used.
		fed.getTransporters().bankEnergy(5.0);   // a use spends a box AND 0.2 energy
		for (int i = 0; i < 3; i++)
			assertTrue("box " + (i + 1) + " should have been free",
					fed.getTransporters().useTransporter(game.getAbsoluteImpulse()));

		GameStateDto.BoxBankDto t = bank("Transporters");
		assertEquals(3, t.printed);
		assertEquals("undamaged", 3, t.intact);
		assertEquals("and unusable", 0, t.ready);
		assertTrue("every box says so individually too",
				t.boxes.stream().allMatch(b -> "USED_THIS_TURN".equals(b.state)));
		assertEquals("which the old field still cannot say", 3, mine().availableTransporters);
	}

	@Test
	public void aDestroyedBoxKeepsItsNumberAndIsMarkedRatherThanRemoved() {
		fed.getTransporters().damage();

		GameStateDto.BoxBankDto t = bank("Transporters");
		assertEquals("still three printed", 3, t.printed);
		assertEquals(2, t.intact);
		assertEquals("and three boxes drawn, one of them struck through", 3, t.boxes.size());
		assertEquals(1, t.boxes.stream().filter(b -> "DESTROYED".equals(b.state)).count());
	}

	// ---------------------------------------------------------------- the fourth state

	@Test
	public void aTractorBankReportsWhatItIsHoldingAndTheOthersReportNull() {
		fed.getTractors().initForTurn(4, game.getAbsoluteImpulse());
		assertTrue(fed.getTractors().linkUnit(klingon, game.getAbsoluteImpulse()));

		GameStateDto.BoxBankDto tractors = bank("Tractor Beams");
		assertEquals(Integer.valueOf(1), tractors.holding);
		GameStateDto.BoxDto held = tractors.boxes.stream()
				.filter(b -> "HOLDING".equals(b.state)).findFirst().orElseThrow();
		assertEquals("IKV Saber", held.holding);

		assertNull("a lab bank cannot hold anything, and says so with null not zero",
				bank("Laboratories").holding);
		assertNull(bank("Transporters").holding);
	}

	@Test
	public void theHoldingCountIsNullNotZeroForASystemThatCannotHold() {
		// The Jackson trap, and the reason the field is an Integer: a primitive would be
		// serialized as 0 on every bank, and a client reading "holding: 0" cannot tell a
		// tractor bank with nothing in tow from a lab bank that could never tow anything.
		assertNull(bank("Laboratories").holding);
		assertEquals("a tractor with nothing held DOES say zero, and means it",
				Integer.valueOf(0), bank("Tractor Beams").holding);
	}
}
