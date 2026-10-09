package com.sfb.scenario;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;

/**
 * S8.131: the scenario date "will define ... what ships, FIGHTERS, and other units will be
 * available", and S8.11 charges for the fighters a carrier brings. Put together, a carrier's
 * price moves with the date — a Kzinti CVS flies 6-point AAS in Y170 and 12-point TADSC in
 * Y183, which is 243 points against 315 for the same hull.
 * <p>
 * It had been wrong everywhere except scenarios. {@code Shuttles} seats each bay at the SHIP's
 * own service year when it is built, and only {@code ScenarioLoader} re-seated it afterwards —
 * so {@code FleetLoader} and the ship-catalogue endpoint both sold every carrier at its hull's
 * introduction price. A Y183 fleet bought a CVS 72 points cheap, and because S3.211 strikes the
 * Commander's Options allowance off the same basis, that was understated to match.
 * <p>
 * The fix was to move the re-seating out of {@code ScenarioLoader} into
 * {@code FighterComplement.reseat} and call it from all three. These tests pin the price rather
 * than the plumbing, so they would fail again for any fourth path that forgets.
 * <p>
 * Note what is deliberately NOT offered: a way to buy an earlier, cheaper complement. The date
 * decides (S8.131), so electing obsolete fighters to save points is not a choice the rules
 * give a buyer. S8.132's five-year refit lag is the nearest thing, and it is a presumption
 * agreed for a whole scenario rather than a discount picked per ship.
 */
public class CarrierPriceByYearTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    /** One Kzinti CVS bought into a fleet dated {@code year}. */
    private Ship cvsInFleetOf(int year) {
        FleetSpec spec = new FleetSpec();
        spec.year = year;
        spec.budget = 1000;
        spec.factions.add("Kzinti");
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = "Kzinti";
        entry.type = "CVS";
        spec.ships.add(entry);

        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("fixture: the CVS should resolve — " + r.unknown(), r.isComplete());
        assertEquals(1, r.ships().size());
        return r.ships().get(0);
    }

    // ---------------------------------------------------------------- the price

    @Test
    public void aCarrierCostsMoreInALaterYear() {
        int early = FleetValidator.costOf(cvsInFleetOf(170));
        int late  = FleetValidator.costOf(cvsInFleetOf(183));

        assertTrue("a TADSC group must cost more than an AAS group: Y170=" + early
                + " Y183=" + late, late > early);
    }

    /**
     * The exact figures, so a change to fighter BPV or complement is noticed rather than drifting.
     * <p>
     * From Y175 the hull also carries its own drone-rack refit, which the CVS prices at 6. That
     * is not part of the fighter arithmetic — it applies to a hull with no bay at all — so the
     * three later figures are each the fighter era's price plus the same 6.
     */
    @Test
    public void theKzintiCvsIsPricedAcrossItsFighterEras() {
        assertEquals("Y170, eleven AAS at 6 and an AAS-E at 8, on a 169 hull", 243,
                FleetValidator.costOf(cvsInFleetOf(170)));
        assertEquals("Y173, HAAS", 267, FleetValidator.costOf(cvsInFleetOf(173)));
        assertEquals("Y177, TAAS, plus the Y175 rack refit at 6", 285,
                FleetValidator.costOf(cvsInFleetOf(177)));
        assertEquals("Y180, TADS, plus the refit", 309, FleetValidator.costOf(cvsInFleetOf(180)));
        assertEquals("Y183, TADSC, plus the refit", 321, FleetValidator.costOf(cvsInFleetOf(183)));
    }

    /**
     * S3.211 takes the Commander's Options allowance off a basis that includes the fighters, so
     * it has to move with them. This is the half that was wrong twice over: once because the
     * basis ignored fighters at all (fixed earlier), and once because the fighters were the
     * wrong era.
     */
    @Test
    public void theOptionsAllowanceFollowsThePriceUp() {
        double early = FleetValidator.coiAllowance(cvsInFleetOf(170));
        double late  = FleetValidator.coiAllowance(cvsInFleetOf(183));

        assertTrue("20% of a bigger basis is bigger: Y170=" + early + " Y183=" + late,
                late > early);
    }

    // ---------------------------------------------------------------- no year

    /**
     * A fleet with no date yet quotes each ship as its own service year built it. That is the
     * honest thing to show while browsing — not a house default year, and not an empty bay.
     */
    @Test
    public void withNoDateTheShipKeepsItsServiceYearComplement() {
        Ship undated = cvsInFleetOf(0);
        Ship atService = cvsInFleetOf(170);   // the CVS entered service in Y170

        assertEquals("undated must match the ship's own service year",
                FleetValidator.costOf(atService), FleetValidator.costOf(undated));
        assertTrue("and that is not an empty bay",
                FleetValidator.carriedFighterBpv(undated) > 0);
    }

    /**
     * A ship with no fighters is unmoved by the date — until the date crosses Y175.
     *
     * <p>This was the control for the tests above: nothing to re-seat, so nothing to re-price.
     * The premise stopped being true when the fleet loader started running every year-gated rule
     * rather than the fighter re-seat alone. The Y175 drone-rack refit charges its own cost, and
     * the Kzinti BC declares 4 of it, so Y170 and Y183 are legitimately 4 apart on a hull that
     * has never carried a fighter.
     *
     * <p>Kept as a control by comparing two dates on the SAME side of the refit, and the crossing
     * is now asserted rather than denied.
     */
    @Test
    public void aShipWithoutFightersMovesOnlyWhenTheDateCrossesARefit() {
        FleetSpec spec = new FleetSpec();
        spec.budget = 1000;
        spec.factions.add("Kzinti");
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = "Kzinti";
        entry.type = "BC";
        spec.ships.add(entry);

        spec.year = 170;
        int early = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));
        spec.year = 174;
        int stillEarly = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));
        spec.year = 183;
        int late = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));

        assertEquals("no fighters and no refit crossed, so nothing for the date to change",
                early, stillEarly);
        assertEquals("the bare hull", 135, early);
        assertEquals("plus the Y175 rack refit the BC declares", 139, late);
    }
}
