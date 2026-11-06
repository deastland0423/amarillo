package com.sfb.scenario;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;

/**
 * S8.131: the scenario date "will define ... what ships, FIGHTERS, and other
 * units will be
 * available", and S8.11 charges for the fighters a carrier brings. Put
 * together, a carrier's
 * price moves with the date — a Kzinti CVS flies 6-point AAS in Y170 and
 * 12-point TADSC in
 * Y183, which is 243 points against 315 for the same hull.
 * <p>
 * It had been wrong everywhere except scenarios. {@code Shuttles} seats each
 * bay at the SHIP's
 * own service year when it is built, and only {@code ScenarioLoader} re-seated
 * it afterwards —
 * so {@code FleetLoader} and the ship-catalogue endpoint both sold every
 * carrier at its hull's
 * introduction price. A Y183 fleet bought a CVS 72 points cheap, and because
 * S3.211 strikes the
 * Commander's Options allowance off the same basis, that was understated to
 * match.
 * <p>
 * The fix was to move the re-seating out of {@code ScenarioLoader} into
 * {@code FighterComplement.reseat} and call it from all three. These tests pin
 * the price rather
 * than the plumbing, so they would fail again for any fourth path that forgets.
 * <p>
 * Note what is deliberately NOT offered: a way to buy an earlier, cheaper
 * complement. The date
 * decides (S8.131), so electing obsolete fighters to save points is not a
 * choice the rules
 * give a buyer. S8.132's five-year refit lag is the nearest thing, and it is a
 * presumption
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
                double early = FleetValidator.costOf(cvsInFleetOf(170));
                double late = FleetValidator.costOf(cvsInFleetOf(183));

                assertTrue("a TADSC group must cost more than an AAS group: Y170=" + early
                                + " Y183=" + late, late > early);
        }

        /**
         * The exact figures, so a change to fighter BPV or complement is noticed rather
         * than drifting.
         * <p>
         * From Y175 the hull also carries its own drone-rack refit, which the CVS
         * prices at 6. That
         * is not part of the fighter arithmetic — it applies to a hull with no bay at
         * all — so the
         * three later figures are each the fighter era's price plus the same 6.
         *
         * <p>
         * And each carries S2.11's drone speed charge, which S8.133 keeps out of the
         * hull's BPV:
         * "the only drones that are included in the BPV of a unit are type-I slow".
         * <p>
         * That charge is in TWO parts, and the second one is the squadron's. FD2.45
         * pays "for a
         * single loading of each launch rack <b>and/or fighter</b>", so a carrier owes
         * for its
         * fighters' drones as well as its own racks' — see
         * {@link FighterDroneSpeedCostTest},
         * which is where that half is pinned. The fighter half is the one that moves
         * most here,
         * because the complement gains rails as it modernises:
         * <ul>
         * <li><b>Y170, Y173</b> — 16 ship mediums at a half, 8. Eleven AAS (then HAAS)
         * of two
         * standard rails: 22 type-I mediums at a half, 11. The EW craft's two pods
         * replace both
         * its rails (J4.962), so it has no ready rack at all. Total 19.</li>
         * <li><b>Y177</b> — the Y175 refit has widened the magazines to twenty, so 10
         * for the
         * ship. A TAAS adds two LIGHT rails: 11 x (2 x 0.50 + 2 x 0.25) = 16.5, and the
         * TAAS-E's
         * two surviving light rails 0.50. Total 27.</li>
         * <li><b>Y180</b> — fast drones are standard issue at a point each, so 20 for
         * the ship.
         * A TADS adds two SPECIAL rails: 11 x (2 x 1 + 2 x 0.50 + 2 x 1) = 55, plus the
         * TADS-E's
         * four at 3. Total 78.</li>
         * <li><b>Y183</b> — the TADSC trades its light rails for standard ones, so
         * every one of
         * the squadron's 70 drones is a whole point. Total 90.</li>
         * </ul>
         * Ninety points of drones on a 175-point hull is not a rounding error, and it
         * was being
         * handed over free: before FD2.45's fighter clause was implemented these five
         * figures
         * read 251, 275, 295, 329 and 341.
         */
        @Test
        public void theKzintiCvsIsPricedAcrossItsFighterEras() {
                assertEquals("Y170, eleven AAS at 6 and an AAS-E at 8, on a 169 hull, and 19 of drones",
                                262, FleetValidator.costOf(cvsInFleetOf(170)), 1e-9);
                assertEquals("Y173, HAAS", 286, FleetValidator.costOf(cvsInFleetOf(173)), 1e-9);
                assertEquals("Y177, TAAS, plus the Y175 rack refit at 6, and 27 of drones",
                                312, FleetValidator.costOf(cvsInFleetOf(177)), 1e-9);
                assertEquals("Y180, TADS, plus the refit, and 78 of FAST drones",
                                387, FleetValidator.costOf(cvsInFleetOf(180)), 1e-9);
                assertEquals("Y183, TADSC, plus the refit, and 90 now that every rail is standard",
                                411, FleetValidator.costOf(cvsInFleetOf(183)), 1e-9);
        }

        /**
         * S3.211 takes the Commander's Options allowance off a basis that includes the
         * fighters, so
         * it has to move with them. This is the half that was wrong twice over: once
         * because the
         * basis ignored fighters at all (fixed earlier), and once because the fighters
         * were the
         * wrong era.
         */
        @Test
        public void theOptionsAllowanceFollowsThePriceUp() {
                double early = FleetValidator.coiAllowance(cvsInFleetOf(170));
                double late = FleetValidator.coiAllowance(cvsInFleetOf(183));

                assertTrue("20% of a bigger basis is bigger: Y170=" + early + " Y183=" + late,
                                late > early);
        }

        // ---------------------------------------------------------------- no year

        /**
         * A fleet with no date yet quotes each ship as its own service year built it.
         * That is the
         * honest thing to show while browsing — not a house default year, and not an
         * empty bay.
         *
         * <h2>Asked of the COMPLEMENT rather than of the price</h2>
         * This compared the two purchase prices, which worked until the price learned
         * about drones.
         * An undated ship is carrying type-I-slows, because
         * {@code Propulsion.generalIn} answers an
         * undated year with the frame's baseline — and FD2.31 has those in the BPV
         * already, so it
         * owes nothing. The same hull at Y170 is carrying mediums and owes 8 (S8.133).
         * The two
         * prices are legitimately eight apart, and always will be; it is the FIGHTERS
         * this test is
         * about, so it asks about those.
         */
        @Test
        public void withNoDateTheShipKeepsItsServiceYearComplement() {
                Ship undated = cvsInFleetOf(0);
                Ship atService = cvsInFleetOf(170); // the CVS entered service in Y170

                assertEquals("undated must carry the complement of its own service year",
                                FleetValidator.carriedFighterBpv(atService),
                                FleetValidator.carriedFighterBpv(undated), 1e-9);
                assertTrue("and that is not an empty bay",
                                FleetValidator.carriedFighterBpv(undated) > 0);
                assertEquals("the only thing the date changes here is S2.11's drone charge",
                                FleetValidator.costOf(atService) - atService.getDroneSpeedBpv(),
                                FleetValidator.costOf(undated) - undated.getDroneSpeedBpv(), 1e-9);
        }

        /**
         * A ship with no fighters is unmoved by the date — until the date crosses Y175.
         *
         * <p>
         * This was the control for the tests above: nothing to re-seat, so nothing to
         * re-price.
         * The premise stopped being true when the fleet loader started running every
         * year-gated rule
         * rather than the fighter re-seat alone. The Y175 drone-rack refit charges its
         * own cost, and
         * the Kzinti BC declares 4 of it, so Y170 and Y183 are legitimately 4 apart on
         * a hull that
         * has never carried a fighter.
         *
         * <p>
         * Kept as a control by comparing two dates on the SAME side of the refit, and
         * the crossing
         * is now asserted rather than denied.
         *
         * <p>
         * Amended a second time for the same kind of reason: a hull with no fighters
         * still carries
         * DRONES, and S8.133 keeps improved ones out of its BPV, so the date moves the
         * price through
         * them too. Y170 and Y174 agree because both fly mediums out of the same racks;
         * Y183 is
         * dearer by the refit AND by twenty fast drones at a point where twenty mediums
         * cost a half.
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
                double early = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));
                spec.year = 174;
                double stillEarly = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));
                spec.year = 183;
                double late = FleetValidator.costOf(FleetLoader.resolve(spec).ships().get(0));

                assertEquals("no fighters and no refit crossed, so nothing for the date to change",
                                early, stillEarly, 1e-9);
                // 128, not the 135 this read until 2026-11-06: the owner corrected the hull
                // after Annex #1 and the sample builder both gave 128 against the file's 135.
                assertEquals("a 128 hull and sixteen mediums at a half (S2.11)", 136, early, 1e-9);
                assertEquals("plus the Y175 rack refit the BC declares, and twenty fast drones at a point",
                                152, late, 1e-9);
        }
}
