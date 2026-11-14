package com.sfb;

import com.sfb.Game.ActionResult;
import com.sfb.Game.ImpulsePhase;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * What a player is told when a transporter will not fire, and why it is three answers.
 * <p>
 * A transporter box is spent and then cools off for the longer of the next turn or eight
 * impulses — the cycle laboratories follow (G4.451). Every gate asked two questions of it,
 * "undamaged?" and "can I pay?", and so blamed the ENERGY for a box that was merely cooling.
 * That is the worst kind of wrong message: it names a resource the player is holding plenty
 * of, and suggests a fix — allocate more power — that cannot work however often it is tried.
 * <p>
 * The three questions came from {@code CyclingBoxes}. Finding this was the point of writing
 * the vocabulary down: once the gates were spelled intact / ready / powered, the missing one
 * was a hole you could see rather than a subtlety you had to notice.
 */
public class TransporterCoolingMessageTest {

	private Game game;
	private Ship fed;
	private Ship klingon;

	@Before
	public void setUp() {
		game = new Game();

		fed = new Ship();
		fed.init(FederationShips.getFedCa());
		fed.setName("Enterprise");
		fed.setLocation(new Location(10, 10));
		fed.setFacing(1);
		fed.setTBombs(3);

		klingon = new Ship();
		klingon.init(KlingonShips.getD7());
		klingon.setName("IKV Saber");
		klingon.setLocation(new Location(11, 10));   // adjacent — range 1
		klingon.setFacing(4);

		game.getShips().add(fed);
		game.getShips().add(klingon);
		fed.attachClock(game.getClock());
		klingon.attachClock(game.getClock());

		game.advancePhase();
		assertEquals(ImpulsePhase.ACTIVITY, game.getCurrentPhase());

		fed.addLockOn(klingon);
		klingon.getShields().lowerShield(game.getShieldNumber(fed, klingon));
	}

	/**
	 * Spend every transporter box, leaving the bank deliberately overflowing. This is the
	 * state the old messages described as having no energy.
	 */
	private void coolEveryBoxWithEnergyToSpare() {
		fed.getTransporters().bankEnergy(20.0);      // a hundred uses' worth
		int intact = fed.getTransporters().intactBoxes();
		assertTrue("fixture needs a ship with transporters", intact > 0);
		for (int i = 0; i < intact; i++)
			assertTrue("box " + (i + 1) + " should have been free",
					fed.getTransporters().useTransporter(game.getAbsoluteImpulse()));

		assertEquals("every box is now cooling", 0,
				fed.getTransporters().readyBoxes(game.getAbsoluteImpulse()));
		assertEquals("none of them was damaged", intact, fed.getTransporters().intactBoxes());
		assertTrue("and the energy is untouched", fed.getTransporters().getBankedEnergy() > 1.0);
	}

	private void assertBlamesCoolingNotEnergy(ActionResult r) {
		assertFalse("the action must still be refused", r.isSuccess());
		assertTrue("should say the boxes are cooling off, said: " + r.getMessage(),
				r.getMessage().contains("cooling"));
		assertFalse("must NOT blame the energy, said: " + r.getMessage(),
				r.getMessage().contains("energy"));
	}

	// ---------------------------------------------------------------- the three gates

	@Test
	public void layingATBombWithEveryBoxCoolingDoesNotBlameTheEnergy() {
		coolEveryBoxWithEnergyToSpare();

		assertBlamesCoolingNotEnergy(game.placeTBomb(fed, new Location(10, 11), true));
	}

	@Test
	public void beamingBoardingPartiesWithEveryBoxCoolingDoesNotBlameTheEnergy() {
		coolEveryBoxWithEnergyToSpare();

		assertBlamesCoolingNotEnergy(game.performBoardingAction(fed, klingon, 1, 0));
	}

	@Test
	public void transferringCrewWithEveryBoxCoolingDoesNotBlameTheEnergy() {
		coolEveryBoxWithEnergyToSpare();

		assertBlamesCoolingNotEnergy(game.transportCrew(fed, klingon, 2));
	}

	// ---------------------------------------------------------------- the controls

	// ---------------------------------------------------------------- reserve power pays

	/**
	 * Owner's ruling, 2026-11-14: "A powered transporter may be used, of course. If there is
	 * remaining battery/reserve power, it may be used to immediately power an available
	 * transporter."
	 * <p>
	 * {@code Game.spendTransporterEnergy} has always done this — it buys a point of battery
	 * when the bank runs short (H7.x). The GATES in front of it asked a figure that counted
	 * only what was banked at allocation, so every one of them refused a ship the layer behind
	 * them would have charged without complaint. The capability existed and nothing could
	 * reach it.
	 */
	private void nothingBankedButBatteriesAboard() {
		assertEquals("fixture assumes an empty bank", 0.0,
				fed.getTransporters().getBankedEnergy(), 0.0001);
		fed.getPowerSystems().setBatteryPower(3);
		assertTrue("boxes are free; only the energy is in question",
				fed.getTransporters().readyBoxes(game.getAbsoluteImpulse()) > 0);
	}

	@Test
	public void aShipThatAllocatedNothingMayBeamOnReservePower() {
		nothingBankedButBatteriesAboard();

		ActionResult r = game.performBoardingAction(fed, klingon, 1, 0);

		assertTrue("reserve power may power an available transporter: " + r.getMessage(),
				r.isSuccess());
	}

	@Test
	public void aShipThatAllocatedNothingMayLayATBombOnReservePower() {
		nothingBankedButBatteriesAboard();

		ActionResult r = game.placeTBomb(fed, new Location(10, 11), true);

		assertTrue("the T-bomb gate refused this outright: " + r.getMessage(), r.isSuccess());
	}

	@Test
	public void aShipThatAllocatedNothingMayTransferCrewOnReservePower() {
		nothingBankedButBatteriesAboard();

		ActionResult r = game.transportCrew(fed, klingon, 2);

		assertTrue("the crew-transfer gate refused this outright: " + r.getMessage(),
				r.isSuccess());
	}

	@Test
	public void theGateAgreesWithWhatTheSpendWillActuallyCharge() {
		// The invariant underneath all three: a gate that asks a different question from the
		// payment either refuses a ship that can pay, or waves through one that cannot. Both
		// are wrong and only the first was visible.
		nothingBankedButBatteriesAboard();
		int affordable = game.transporterUsesAvailable(fed);
		assertTrue("fixture should afford several uses", affordable >= 2);

		assertTrue("the spend charges what the gate promised",
				game.spendTransporterEnergy(fed, affordable));
	}

	@Test
	public void aGenuineEnergyShortfallStillBlamesTheEnergy() {
		// The control that stops this being one wrong message swapped for another. Boxes free,
		// nothing to power them with: the energy really is the reason and must be named.
		fed.getPowerSystems().setBatteryPower(0);
		assertEquals("fixture assumes an empty bank", 0.0,
				fed.getTransporters().getBankedEnergy(), 0.0001);
		assertTrue("boxes are free, so cooling is not the reason",
				fed.getTransporters().readyBoxes(game.getAbsoluteImpulse()) > 0);

		ActionResult r = game.placeTBomb(fed, new Location(10, 11), true);

		assertFalse(r.isSuccess());
		assertTrue("should blame the energy, said: " + r.getMessage(),
				r.getMessage().contains("energy"));
	}

	@Test
	public void adestroyedBankStillReportsBrokenTransportersRatherThanCooling() {
		// The other control: damage and cooling are different words for a reason, and a ship
		// with no transporters left must not be told to wait for them to cool down.
		fed.getTransporters().bankEnergy(20.0);
		while (fed.getTransporters().intactBoxes() > 0)
			assertTrue(fed.getTransporters().damage());

		ActionResult r = game.placeTBomb(fed, new Location(10, 11), true);

		assertFalse(r.isSuccess());
		assertTrue("should say the transporters are gone, said: " + r.getMessage(),
				r.getMessage().contains("working transporters"));
		assertFalse("they are destroyed, not cooling: " + r.getMessage(),
				r.getMessage().contains("cooling"));
	}

	@Test
	public void theCoolingMessageSaysHowManyOfHowMany() {
		// A count the player can act on: with one box free of three, waiting is the fix and
		// the message should make the arithmetic visible rather than just refusing.
		fed.getTransporters().bankEnergy(20.0);
		int intact = fed.getTransporters().intactBoxes();
		assertTrue("fixture needs at least two boxes to leave one free", intact >= 2);
		for (int i = 0; i < intact - 1; i++)
			fed.getTransporters().useTransporter(game.getAbsoluteImpulse());

		// Ask for two parties when only the last box is free.
		ActionResult r = game.performBoardingAction(fed, klingon, 2, 0);

		assertFalse(r.isSuccess());
		assertTrue("should report free-of-intact, said: " + r.getMessage(),
				r.getMessage().contains("1 of " + intact));
		assertTrue(r.getMessage().contains("cooling"));
	}
}
