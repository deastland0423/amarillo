package com.sfb.systemgroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * Movement cost is held in TWELFTHS, and that is what makes a speed cost what the rules say.
 *
 * Every cost in the game is a whole twelfth, but the JSON cannot say two-thirds: it says
 * {@code 0.6666667} on 77 hulls and {@code 0.3333333} on 72. Multiplying those gives a number
 * a hair off every real total — speed 3 cost 2.0000001 and speed 30 cost 20.000001 — and the
 * old answer was a tolerance wherever anything compared or divided. That works until somebody
 * writes a new one of those places without knowing to, which is what happened to the energy
 * dialog's speed slider.
 */
public class MovementTwelfthsTest {

	private PerformanceData costing(double declared) {
		PerformanceData p = new PerformanceData();
		Map<String, Object> m = new HashMap<>();
		m.put("movecost", declared);
		p.init(m);
		return p;
	}

	// ---------------------------------------------------------------- reading the cost

	@Test
	public void everySpellingOfAThirdMeansFourTwelfths() {
		// The ship files, a hand-written variant, and the exact double a test might pass.
		for (double spelling : new double[] { 0.3333333, 0.33333333, 0.333333333, 1.0 / 3.0 })
			assertEquals("spelled " + spelling, 4, costing(spelling).getMovementTwelfths());
		for (double spelling : new double[] { 0.6666667, 0.66666667, 2.0 / 3.0 })
			assertEquals("spelled " + spelling, 8, costing(spelling).getMovementTwelfths());
	}

	@Test
	public void theExactCostsReadAsThemselves() {
		assertEquals(3,  costing(0.25).getMovementTwelfths());
		assertEquals(6,  costing(0.5).getMovementTwelfths());
		assertEquals(9,  costing(0.75).getMovementTwelfths());
		assertEquals(12, costing(1.0).getMovementTwelfths());
		assertEquals(18, costing(1.5).getMovementTwelfths());
		assertEquals(24, costing(2.0).getMovementTwelfths());
	}

	@Test
	public void acostThatIsNotAWholeTwelfthIsRefusedRatherThanRounded() {
		// Deliberately strict. Rounding quietly would re-cost a hull on a typo — 0.7 is nearer
		// two-thirds than anything else — and a ship moving cheaper than its SSD says is a
		// fault nobody finds by playing.
		for (double bad : new double[] { 0.7, 0.4, 1.1, 0.123 }) {
			try {
				costing(bad);
				fail("movecost " + bad + " should have been refused");
			} catch (IllegalArgumentException expected) {
				assertTrue(expected.getMessage(), expected.getMessage().contains("whole twelfth"));
			}
		}
	}

	@Test
	public void theDecimalAndTheTwelfthsNeverDisagree() {
		// Two representations of one fact, so they have to be derived from each other rather
		// than both read from the JSON. Found by mutation: nothing else noticed when
		// getMovementCost() went back to reporting the file's 0.6666667 while the twelfths
		// said 8. On its own that is only a cosmetic drift — but it is what keeps multiplying
		// by the decimal honest everywhere this class still does, so it is load-bearing for
		// getHetCost, getErraticCost and the moveCost the DTO sends for display.
		for (double declared : new double[] { 0.25, 0.3333333, 0.5, 0.6666667, 0.75, 1.0, 1.5, 2.0 }) {
			PerformanceData p = costing(declared);
			assertEquals("declared " + declared,
					p.getMovementTwelfths() / 12.0, p.getMovementCost(), 0.0);
		}
	}

	// ---------------------------------------------------------------- the exactness itself

	@Test
	public void aTwoThirdsHullZeroesOutOnEveryThirdSpeed() {
		// The claim in plain form, asserted with an exact comparison and no delta: 2.0000001
		// would satisfy any tolerance and is the number this exists to stop.
		PerformanceData p = costing(0.6666667);
		assertEquals(2.0,  p.movementEnergyFor(3),  0.0);
		assertEquals(4.0,  p.movementEnergyFor(6),  0.0);
		assertEquals(6.0,  p.movementEnergyFor(9),  0.0);
		assertEquals(20.0, p.movementEnergyFor(30), 0.0);
	}

	@Test
	public void aOneThirdHullZeroesOutToo() {
		PerformanceData p = costing(0.3333333);
		assertEquals(1.0,  p.movementEnergyFor(3),  0.0);
		assertEquals(10.0, p.movementEnergyFor(30), 0.0);
	}

	@Test
	public void offTheMultiplesItStillGivesTheHonestRepeatingValue() {
		// No claim that every speed is exact: two-thirds of one hex does not terminate, and
		// pretending it did would be a different lie.
		assertEquals(0.6667, costing(2.0 / 3.0).movementEnergyFor(1), 0.0001);
		assertEquals(1.3333, costing(2.0 / 3.0).movementEnergyFor(2), 0.0001);
	}

	@Test
	public void everySpeedRoundTripsAtEveryCostTheDataUses() {
		for (double cost : new double[] { 0.25, 0.3333333, 0.5, 0.6666667, 0.75, 1.0, 1.5, 2.0 }) {
			PerformanceData p = costing(cost);
			for (int speed = 1; speed <= 30; speed++)
				assertEquals("cost " + cost + " speed " + speed,
						speed, p.speedAffordable(p.movementEnergyFor(speed)));
		}
	}

	@Test
	public void energyShortOfAHexBuysNothingExtra() {
		// The counterpart risk: a fix that rounds up turns a rounding bug into a cheating one.
		for (double cost : new double[] { 0.3333333, 0.6666667, 0.75, 1.0 }) {
			PerformanceData p = costing(cost);
			assertEquals("cost " + cost, 3, p.speedAffordable(p.movementEnergyFor(4) - 0.01));
		}
	}

	@Test
	public void theOldInexactEnergyStillBuysTheSpeedItPaidFor() {
		// A saved game, or any caller that multiplies the cost itself, still arrives with
		// 2.0000001 and 2.9999997. The tolerance in speedAffordable is for them, and is why
		// removing it alongside this change would have broken loading an older save.
		assertEquals(3, costing(0.6666667).speedAffordable(3 * 0.6666667));
		assertEquals(9, costing(0.3333333).speedAffordable(9 * 0.3333333));
		assertEquals(30, costing(0.6666667).speedAffordable(30 * 0.6666667));
	}

	@Test
	public void hetAndErraticCostsAreDerivedFromTheTwelfthsToo() {
		// C6: HET is five times the move cost, EM three or six (C10.x). They were computed off
		// the truncated decimal, so a two-thirds hull's HET cost was 3.3333335.
		assertEquals(10.0 / 3.0, costing(0.6666667).getHetCost(), 0.0);
		assertEquals(5.0, costing(1.0).getHetCost(), 0.0);
		assertEquals(4.0, costing(2.0 / 3.0).getErraticCost(), 0.0);   // 8/12 * 6 = 4 exactly
	}

	// ---------------------------------------------------------------- the data guard

	@Test
	public void everyMoveCostInTheShipDataIsAWholeTwelfth() throws Exception {
		// The claim the representation rests on. If a hull ever declares something else, the
		// constructor throws at load — which is the right failure, but this names the file.
		ObjectMapper mapper = new ObjectMapper();
		List<String> offenders = new ArrayList<>();
		TreeSet<String> seen = new TreeSet<>();

		try (Stream<Path> files = Files.walk(Paths.get("../data/factions"))) {
			for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
				JsonNode n = mapper.readTree(p.toFile());
				if (!n.has("moveCost"))
					continue;
				double declared = n.get("moveCost").asDouble();
				double twelfths = declared * 12;
				seen.add(String.valueOf(declared));
				if (Math.abs(twelfths - Math.round(twelfths)) > 0.001)
					offenders.add(p.getFileName() + " = " + declared);
			}
		}

		assertEquals("a hull declares a movement cost that is not a whole twelfth: " + offenders,
				List.of(), offenders);
		assertEquals("the set of movement costs in the data has changed — check the new one is"
				+ " a real cost and not a typo",
				new TreeSet<>(List.of("0.25", "0.3333333", "0.5", "0.6666667", "0.75", "1.0",
						"1.5", "2.0")),
				seen);
	}
}
