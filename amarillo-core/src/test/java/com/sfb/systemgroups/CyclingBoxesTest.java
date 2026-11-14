package com.sfb.systemgroups;

import com.sfb.objects.Ship;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * The shared vocabulary for banks of SSD boxes that cycle: laboratories, transporters and
 * tractor beams.
 * <p>
 * These tests exist because the three systems used to answer the same questions under
 * different names, and "available" meant a different thing in each: boxes that SURVIVED
 * DAMAGE on transporters and tractors, boxes FREE RIGHT NOW on laboratories, and on
 * {@code Transporters.availableUses} the free ones capped by the energy to power them —
 * which is owner-only, because an opponent could read the battery state back out of it.
 * Nothing caught the confusion, because picking the wrong one of three same-named methods
 * compiles perfectly.
 * <p>
 * So what is pinned here is the DISTINCTION between the questions, system by system, plus a
 * guard that the next cycling system to arrive answers them too.
 */
public class CyclingBoxesTest {

	private static final int T1       = 5;    // turn 1, impulse 5
	private static final int T1_LATE  = 30;   // turn 1, late enough to cool into turn 2
	private static final int T2_EARLY = 33;   // turn 2, impulse 1

	private Labs labs(int count) {
		Labs l = new Labs(new Ship());
		l.init(Map.of("lab", count));
		return l;
	}

	private Transporters transporters(int count) {
		Transporters t = new Transporters(new Ship());
		t.init(Map.of("trans", count));
		t.bankEnergy(count);              // energy enough that the boxes are the only limit
		return t;
	}

	private Tractors tractors(int count) {
		Tractors t = new Tractors(new Ship());
		t.init(Map.of("tractor", count));
		t.initForTurn(count, T1);
		return t;
	}

	private List<CyclingBoxes> allThree() {
		return List.of(labs(4), transporters(4), tractors(4));
	}

	// ------------------------------------------------- the three counts, and their order

	@Test
	public void allThreeSystemsStartWithEveryBoxPrintedIntactAndReady() {
		for (CyclingBoxes c : allThree()) {
			String who = c.getClass().getSimpleName();
			assertEquals(who, 4, c.printedBoxes());
			assertEquals(who, 4, c.intactBoxes());
			assertEquals(who, 4, c.readyBoxes(T1));
			assertEquals(who + " holds nothing yet", 0, c.holdingBoxes());
		}
	}

	@Test
	public void theCountsNestPrintedThenIntactThenReady() {
		// The ordering is the whole point of having three words. It has to hold in every
		// state, so this walks one system through damage AND use at the same time.
		Transporters t = transporters(4);
		t.damage();                       // three intact
		t.useTransporter(T1);             // two ready

		assertEquals(4, t.printedBoxes());
		assertEquals(3, t.intactBoxes());
		assertEquals(2, t.readyBoxes(T1));
		assertTrue(t.printedBoxes() >= t.intactBoxes());
		assertTrue(t.intactBoxes() >= t.readyBoxes(T1));
	}

	// ------------------------------------------------- what each question is NOT

	@Test
	public void aDestroyedBoxLeavesThePrintedCountAlone() {
		// printedBoxes is the SSD, which damage does not rewrite. It is the denominator of a
		// damage gauge, so a system that shrank it would draw a crippled ship as a healthy one.
		Labs l = labs(4);
		assertTrue(l.damage());
		assertEquals("the SSD still prints four", 4, l.printedBoxes());
		assertEquals(3, l.intactBoxes());
	}

	@Test
	public void aSpentBoxIsStillIntact() {
		// The distinction the old names lost: spending a lab is not the same as losing it, and
		// counting a busy box as missing reported a working ship as a crippled one.
		Labs l = labs(4);
		assertTrue("a box was free to use", l.useLab(T1) > 0);

		assertEquals("still four undamaged boxes", 4, l.intactBoxes());
		assertEquals("but only three can take a job", 3, l.readyBoxes(T1));
	}

	@Test
	public void aBoxUsedLateIsStillCoolingEarlyNextTurn() {
		// G4.22 and G4.451 together: the next turn AND eight impulses, whichever is longer.
		// Without this, readyBoxes would equal intactBoxes at every turn boundary and the
		// third question would collapse into the second.
		Labs l = labs(4);
		l.useLab(T1_LATE);

		assertEquals("new turn, but inside the quarter-turn delay", 3, l.readyBoxes(T2_EARLY));
		assertEquals("free again eight impulses on", 4, l.readyBoxes(T1_LATE + 8));
		assertEquals("and it was never damaged", 4, l.intactBoxes());
	}

	// ------------------------------------------------- the fourth state, tractors only

	@Test
	public void aBeamHoldingSomethingIsIntactNotReadyAndCountedAsHolding() {
		// The state only a tractor has (G7.0). It is neither "spent this turn" nor "free", and
		// a readout that folded it into either would misdescribe every beam on a tug.
		Tractors t = tractors(4);
		assertTrue(t.linkUnit(new Ship(), T1));

		assertEquals(4, t.printedBoxes());
		assertEquals("holding is not damage", 4, t.intactBoxes());
		assertEquals("but it cannot start a second link", 3, t.readyBoxes(T1));
		assertEquals(1, t.holdingBoxes());
	}

	@Test
	public void aHeldLinkSurvivesIntoTheNextTurnWhereASpentBoxWouldHaveFreedUp() {
		// G7.42: the link persists across the boundary. So holdingBoxes cannot be derived from
		// the use timestamp the way readyBoxes is — the beam is busy for a reason that has no
		// expiry, which is exactly why it needs a question of its own.
		Tractors t = tractors(4);
		assertTrue(t.linkUnit(new Ship(), T1));
		t.initForTurn(4, T2_EARLY);

		assertEquals("still holding a turn later", 1, t.holdingBoxes());
		assertEquals(3, t.readyBoxes(T2_EARLY));
	}

	@Test
	public void releasingALinkFreesTheHoldWithoutFreeingTheBoxForTheRestOfTheTurn() {
		// G7.13: one link per beam per turn, and releasing does not give the use back. So the
		// two questions move independently — holdingBoxes drops, readyBoxes does not rise.
		Tractors t = tractors(4);
		Ship held = new Ship();
		assertTrue(t.linkUnit(held, T1));
		t.releaseTractor(held);

		assertEquals("nothing held now", 0, t.holdingBoxes());
		assertEquals("but that beam is still spent this turn", 3, t.readyBoxes(T1));
		assertEquals(4, t.intactBoxes());
	}

	@Test
	public void aDestroyedBeamStopsCountingAsHolding() {
		Tractors t = tractors(4);
		assertTrue(t.linkUnit(new Ship(), T1));
		assertNotNull("beam 1 was the one that took the link", t.destroyBeam(1));

		assertEquals(0, t.holdingBoxes());
		assertEquals(3, t.intactBoxes());
		assertEquals("the SSD still prints four", 4, t.printedBoxes());
	}

	@Test
	public void labsAndTransportersNeverHold() {
		// Not a stub awaiting work — the honest answer. A lab or a transporter box finishes
		// inside the impulse it was used in, so there is nothing for it to be holding.
		Labs l = labs(4);
		l.useLab(T1);
		Transporters t = transporters(4);
		t.useTransporter(T1);

		assertEquals(0, l.holdingBoxes());
		assertEquals(0, t.holdingBoxes());
	}

	// ------------------------------------------------- the itemised readout

	private List<CyclingBoxes.BoxState> states(CyclingBoxes c, int impulse) {
		List<CyclingBoxes.BoxState> out = new ArrayList<>();
		for (CyclingBoxes.BoxReadout r : c.boxReadout(impulse))
			out.add(r.state());
		return out;
	}

	@Test
	public void theReadoutHasOneEntryPerPrintedBoxAndAgreesWithTheCounts() {
		for (CyclingBoxes c : allThree()) {
			String who = c.getClass().getSimpleName();
			List<CyclingBoxes.BoxReadout> r = c.boxReadout(T1);

			assertEquals(who + " itemises every printed box", c.printedBoxes(), r.size());
			assertEquals(who + " readout must agree with intactBoxes", c.intactBoxes(),
					r.stream().filter(b -> b.state() != CyclingBoxes.BoxState.DESTROYED).count());
			assertEquals(who + " readout must agree with readyBoxes", c.readyBoxes(T1),
					r.stream().filter(b -> b.state() == CyclingBoxes.BoxState.UNUSED).count());
		}
	}

	@Test
	public void aDestroyedBoxLeavesANUMBEREDGapRatherThanShufflingItsNeighbours() {
		// The reason BoxCycle keeps the number as the identity: a raid names a box (D7.835)
		// and a scout channel holds one across four attempts (G24.251). If the readout
		// renumbered after a loss, the box a player pointed at would not be the box hit.
		Transporters t = transporters(4);
		t.useTransporter(T1);             // box 1 is now the least valuable...
		assertTrue(t.damage());           // ...so damage() takes it

		List<CyclingBoxes.BoxReadout> r = t.boxReadout(T1);
		assertEquals("all four still printed", 4, r.size());
		assertEquals(1, r.get(0).number());
		assertEquals(CyclingBoxes.BoxState.DESTROYED, r.get(0).state());
		assertEquals("box 2 is still called box 2", 2, r.get(1).number());
		assertEquals(CyclingBoxes.BoxState.UNUSED, r.get(1).state());
	}

	@Test
	public void theReadoutTellsUsedThisTurnApartFromCoolingDown() {
		// Three visual states need three names. Spent-this-turn and still-cooling-from-last
		// look the same to a count and are different to a player: one clears at the turn
		// boundary, the other does not.
		Labs l = labs(2);
		l.useLab(T1_LATE);

		assertEquals(List.of(CyclingBoxes.BoxState.USED_THIS_TURN, CyclingBoxes.BoxState.UNUSED),
				states(l, T1_LATE));
		assertEquals("same box, next turn, still inside the quarter-turn delay",
				List.of(CyclingBoxes.BoxState.COOLING_DOWN, CyclingBoxes.BoxState.UNUSED),
				states(l, T2_EARLY));
		assertEquals("and free once the delay runs out",
				List.of(CyclingBoxes.BoxState.UNUSED, CyclingBoxes.BoxState.UNUSED),
				states(l, T1_LATE + 8));
	}

	@Test
	public void aHoldingBeamReportsHoldingAndNamesWhatItHolds() {
		Tractors t = tractors(3);
		Ship prey = new Ship();
		prey.setName("IKV Saber");
		assertTrue(t.linkUnit(prey, T1));

		CyclingBoxes.BoxReadout first = t.boxReadout(T1).get(0);
		assertEquals(CyclingBoxes.BoxState.HOLDING, first.state());
		assertEquals("IKV Saber", first.holding());
		assertNull("an idle beam holds nothing", t.boxReadout(T1).get(1).holding());
	}

	@Test
	public void holdingIsReportedAheadOfTheCycleBecauseItIsThePresentTense() {
		// A holding beam was also spent this turn, so both are true of it. Saying "used" of a
		// beam that is visibly towing something describes the past; the readout says HOLDING.
		Tractors t = tractors(2);
		Ship prey = new Ship();
		prey.setName("IKV Saber");
		assertTrue(t.linkUnit(prey, T1));

		assertEquals(CyclingBoxes.BoxState.HOLDING, states(t, T1).get(0));
		assertTrue("...even though the beam is indeed spent for the turn",
				t.getBeams().get(0).isUsedThisTurn(T1));
	}

	@Test
	public void onlyATractorEverReportsHolding() {
		// The counterpart to holdingBoxes() == 0: nothing in a lab or transporter readout may
		// claim a state its system cannot be in, or the panel will draw a legend nobody can
		// reach.
		Labs l = labs(3);
		l.useLab(T1);
		Transporters t = transporters(3);
		t.useTransporter(T1);

		for (CyclingBoxes c : List.of(l, t))
			for (CyclingBoxes.BoxReadout r : c.boxReadout(T1)) {
				assertNotEquals(c.getClass().getSimpleName() + " cannot hold anything",
						CyclingBoxes.BoxState.HOLDING, r.state());
				assertNull(r.holding());
			}
	}

	// ------------------------------------------------- the guards against drift

	@Test
	public void everySystemThatOwnsABoxCycleAnswersTheseQuestions() throws Exception {
		// The drift guard. A new cycling system that quietly invents its own words for these
		// three counts is how the confusion arose the first time, and it compiles.
		Pattern field = Pattern.compile(
				"^\\s*(private|protected|public)\\s+(final\\s+)?BoxCycle\\s+\\w+",
				Pattern.MULTILINE);
		Path dir = Paths.get("src/main/java/com/sfb/systemgroups");
		List<String> missing = new ArrayList<>();
		Set<String> holders = new TreeSet<>();

		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				String name = p.getFileName().toString().replace(".java", "");
				if (!field.matcher(Files.readString(p)).find())
					continue;                 // a mention in a COMMENT is not ownership
				holders.add(name);
				if (!CyclingBoxes.class.isAssignableFrom(
						Class.forName("com.sfb.systemgroups." + name)))
					missing.add(name);
			}
		}

		assertEquals("a system owns a BoxCycle but does not implement CyclingBoxes: " + missing,
				List.of(), missing);
		assertEquals("the BoxCycle holders have changed — add the new one here deliberately",
				new TreeSet<>(List.of("Labs", "Transporters")), holders);
	}

	@Test
	public void tractorsImplementTheVocabularyWithoutOwningABoxCycle() {
		// Deliberately outside the scan above, and worth stating so nobody "tidies" it in. A
		// beam carries a held unit as well as a timestamp, so Tractors keeps its own
		// TractorBeam objects and borrows only BoxCycle.cycleFree, the rule itself.
		assertTrue(CyclingBoxes.class.isAssignableFrom(Tractors.class));
	}

	@Test
	public void theInterfaceAsksNothingThatDependsOnEnergy() throws Exception {
		// The privacy pin. Every count on CyclingBoxes is public — a box on the SSD is public
		// and so is what it is doing, which is why a hit-and-run raid can name one (D7.835).
		// An energy-aware figure published beside them would leak the battery state, so the
		// one that exists lives on Transporters under a name that says so.
		Set<String> declared = new TreeSet<>();
		for (Method m : CyclingBoxes.class.getDeclaredMethods())
			declared.add(m.getName());

		assertEquals("adding a question here publishes it to every client — think first",
				new TreeSet<>(List.of("printedBoxes", "intactBoxes", "readyBoxes", "holdingBoxes",
						"boxReadout")),
				declared);
		assertNotNull("the energy-capped figure still exists, just not here",
				Transporters.class.getMethod("poweredUses", int.class));
	}
}
