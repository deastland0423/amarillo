package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Transporters;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Paying for transporter use, from banked energy and from reserve power (H7.x).
 * <p>
 * Two faults sat here, the first hiding the second. {@code useTransporter(game.getAbsoluteImpulse())} returns false
 * when the bank will not cover a use, and all four call sites threw that answer away — so
 * the energy limit was never enforced at all. Underneath it, nothing ever topped the bank
 * up from batteries: {@code bankEnergy()} was called once, at allocation, despite its own
 * javadoc promising "or drawn mid-turn from batteries". A ship that allocated nothing
 * could not legitimately transport, and one that allocated nothing transported anyway.
 * <p>
 * A use costs 0.2 and batteries are whole points, so a shortfall buys a point of reserve
 * power — five uses' worth — and what is left over stays banked for the rest of the turn
 * rather than evaporating. Tractors have always worked this way (G7.15).
 */
public class TransporterEnergyTest {

    private Game game;
    private Ship fed;

    @Before
    public void setUp() {
        game = new Game();
        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setSpeedPreviousTurn(31);
        fed.setSpeedTwoTurnsAgo(31);
        game.getShips().add(fed);
    }

    /** No transporter energy banked at all — the state after allocating none. */
    private void bankNothing() {
        assertEquals("fixture assumes an empty bank", 0.0,
                fed.getTransporters().getBankedEnergy(), 0.0001);
    }

    // ---------------------------------------------------------------- reserve power

    @Test
    public void aShipThatAllocatedNothingCanStillBeamOnBatteries() {
        bankNothing();
        fed.getPowerSystems().setBatteryPower(2);

        assertTrue("batteries can pay for it, exactly as they can for a tractor",
                game.spendTransporterEnergy(fed, 1));
    }

    /**
     * A transporter use consumes a BOX, not just energy: a specific box is used on each
     * attempt and is unavailable until the next turn or eight impulses later, whichever is
     * longer. So a ship cannot beam more times in a turn than it has transporters, however
     * much energy it is holding.
     */
    @Test
    public void aShipCannotBeamMoreOftenThanItHasTransporters() {
        bankNothing();
        fed.getTransporters().init(java.util.Map.of("trans", 2));
        fed.getPowerSystems().setBatteryPower(5);   // energy for twenty-five uses

        assertTrue("first box", game.spendTransporterEnergy(fed, 1));
        assertTrue("second box", game.spendTransporterEnergy(fed, 1));
        assertFalse("but there is no third box to use, energy or not",
                game.spendTransporterEnergy(fed, 1));
        assertTrue("and the energy is still there, unspent",
                fed.getPowerSystems().getBatteryPower() > 0);
    }

    @Test
    public void onePointOfReservePowerBuysFiveUses() {
        // 0.2 per use, and a battery point is indivisible — so the change stays banked.
        bankNothing();
        // Six boxes, because this test is about the ENERGY arithmetic and a CA's three
        // transporters would stop it at three uses for an unrelated reason. Each use
        // consumes a box (they cool off until the next turn or eight impulses, whichever
        // is longer), which is its own rule and has its own test below.
        fed.getTransporters().init(java.util.Map.of("trans", 6));
        fed.getPowerSystems().setBatteryPower(1);

        for (int i = 1; i <= 5; i++)
            assertTrue("use " + i + " of 5 should be affordable",
                    game.spendTransporterEnergy(fed, 1));

        assertEquals("the point is spent, not one point per use", 0,
                fed.getPowerSystems().getBatteryPower());
        assertFalse("and a sixth has nothing left to draw on",
                game.spendTransporterEnergy(fed, 1));
    }

    @Test
    public void theChangeFromABatteryPointIsNotThrownAway() {
        bankNothing();
        fed.getPowerSystems().setBatteryPower(1);

        game.spendTransporterEnergy(fed, 1);   // draws the whole point, uses 0.2

        assertEquals("0.8 remains banked for the rest of the turn", 0.8,
                fed.getTransporters().getBankedEnergy(), 0.0001);
    }

    @Test
    public void withNeitherEnergyNorBatteriesItIsRefused() {
        bankNothing();
        fed.getPowerSystems().setBatteryPower(0);

        assertFalse(game.spendTransporterEnergy(fed, 1));
    }

    // ---------------------------------------------------------------- banked energy

    @Test
    public void bankedEnergyIsSpentBeforeBatteries() {
        fed.getTransporters().bankEnergy(1.0);
        fed.getPowerSystems().setBatteryPower(3);

        assertTrue(game.spendTransporterEnergy(fed, 1));

        assertEquals("the batteries are not touched while the bank can pay",
                3, fed.getPowerSystems().getBatteryPower());
    }

    // ---------------------------------------------------------------- the count

    @Test
    public void availableUsesCountsBankAndBatteriesTogether() {
        fed.getTransporters().bankEnergy(0.4);          // two uses
        fed.getPowerSystems().setBatteryPower(1);       // five more

        int transporters = fed.getTransporters().getAvailableTrans();
        assertTrue("fixture needs working transporters", transporters > 0);

        assertEquals("0.4 banked + 1 battery = 7 uses, capped by the transporters aboard",
                Math.min(transporters, 7), game.transporterUsesAvailable(fed));
    }

    @Test
    public void withNoWorkingTransportersNothingIsAffordable() {
        fed.getPowerSystems().setBatteryPower(6);
        while (fed.getTransporters().getAvailableTrans() > 0)
            fed.getTransporters().damage();

        assertEquals(0, game.transporterUsesAvailable(fed));
        assertFalse(game.spendTransporterEnergy(fed, 1));
    }

    @Test
    public void askingForMoreThanIsAffordableSpendsNothing() {
        bankNothing();
        fed.getPowerSystems().setBatteryPower(1);   // five uses

        assertFalse("six is beyond reach", game.spendTransporterEnergy(fed, 6));
        assertEquals("and the attempt must not have nibbled at the batteries",
                1, fed.getPowerSystems().getBatteryPower());
        assertEquals("nor at the bank", 0.0,
                fed.getTransporters().getBankedEnergy(), 0.0001);
    }

    @Test
    public void theCostIsStillTwoTenths() {
        // Pinned because the battery arithmetic above depends on it.
        assertEquals(0.2, Transporters.energyPerUse(), 0.0001);
    }

    // ---------------------------------------------------------------- a plasma is not cargo

    @Test
    public void aTractorBeamCannotHoldAPlasmaTorpedo() {
        // PlasmaTorpedo extends Unit, and Unit is Tractorable, so a plasma turns up in the
        // tractor target search by inheritance. It is energy rather than a physical object
        // and cannot be caught, so the refusal has to be explicit.
        com.sfb.objects.PlasmaTorpedo torp =
                new com.sfb.objects.PlasmaTorpedo(com.sfb.properties.PlasmaType.G,
                        com.sfb.properties.WeaponArmingType.STANDARD);
        torp.setName("Plasma-1");
        torp.setLocation(new Location(11, 10));
        game.getSeekers().add(torp);

        fed.getTractors().initForTurn(5);
        fed.addLockOn(torp);
        fed.setActiveFireControl(true);

        Game.ActionResult r = game.establishTractor(fed, "Plasma-1", 1);

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("plasma"));
    }
}
