package com.sfb.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.Weapon;

/**
 * FD3.44 and FD3.84: a magazine launcher is charged for one magazine, not for all of them.
 *
 * <blockquote>FD3.44: "There are no reloads for type-D drone racks; the reload drones are loaded
 * in the spare magazines. <b>The unit pays the drone upgrade surcharges for one magazine per
 * launcher</b>; the other two magazines per launcher are considered to have proportional free
 * reloads."</blockquote>
 *
 * <p>FD2.45 flags both by name — "there is a partial exception in the case of H-racks (FD3.84).
 * D-racks are another exception."
 *
 * <h2>Why these two need saying and no other rack does</h2>
 * "One loading paid, the reloads free" normally falls out of where the drones are kept: a rack's
 * reloads sit in {@code getReloads()} and nothing prices that list. A magazine launcher has no
 * such list. FD3.43: "While type-D (and type-H) drone racks do not have formal reloads..." — the
 * spare magazines ARE the reloads, already loaded and launch-ready, so all twelve spaces come
 * back from {@code getAmmo()} and the charge billed the free ones along with the paid one.
 *
 * <h2>What it cost</h2>
 * At Y183, with fast drones at a point each: a Kzinti battle station was charged 24 where the
 * rule asks 8, a Klingon one 12 against 4, and a Kzinti TGT 24 against 8 — on a 92-point hull.
 * Five hulls declare a type-D; no hull declares a type-H.
 *
 * @see DroneRack#paidLoadingSpaces()
 */
public class MagazineRackSpeedChargeTest {

    @Before
    public void loadData() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
    }

    private static Ship bought(String faction, String type, int year) {
        FleetSpec spec = new FleetSpec();
        spec.year = year;
        spec.budget = 4000;
        spec.factions.add(faction);
        FleetSpec.ShipEntry entry = new FleetSpec.ShipEntry();
        entry.faction = faction;
        entry.type = type;
        spec.ships.add(entry);

        FleetLoader.Resolution r = FleetLoader.resolve(spec);
        assertTrue("fixture: " + type + " should resolve — " + r.unknown(), r.isComplete());
        return r.ships().get(0);
    }

    private static List<DroneRack> racksOf(Ship ship) {
        List<DroneRack> racks = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack)
                racks.add(rack);
        return racks;
    }

    /** A bare hull carrying one rack and nothing else, so the figure is that rack's alone. */
    private static Ship shipWith(DroneRack rack) {
        Ship ship = new Ship();
        ship.getWeapons().addWeapon(rack);
        return ship;
    }

    private static List<Drone> many(DroneType type, int n) {
        List<Drone> load = new ArrayList<>();
        for (int i = 0; i < n; i++)
            load.add(new Drone(type));
        return load;
    }

    private static int chargeFor(DroneRack rack, List<Drone> load, int year) {
        rack.setAmmo(new ArrayList<>(load));
        Ship ship = shipWith(rack);
        ScenarioLoader.chargeDroneSpeed(ship, year);
        return ship.getDroneSpeedBpv();
    }

    // ---------------------------------------------------------------- the hulls that carry one

    /**
     * FD3.4 gives a type-D "three separate magazines of four spaces each" behind one launcher.
     * The Kzinti battle station mounts two of them: twelve drones apiece, and at Y183 every one
     * of them is a fast type-I worth a point.
     *
     * <p>Twenty-four points was being charged. FD3.44 asks for one magazine per launcher — four
     * spaces each, so eight.
     */
    @Test
    public void aTypeDLauncherPaysForOneMagazineOfItsThree() {
        Ship bs = bought("Kzinti", "BS", 183);

        List<DroneRack> racks = racksOf(bs);
        assertEquals("fixture: two launchers", 2, racks.size());
        for (DroneRack rack : racks) {
            assertEquals("fixture: twelve spaces of magazine", 12, rack.getSpaces());
            assertEquals("fixture: and twelve drones in them", 12, rack.getAmmo().size());
            assertEquals("FD3.44: one magazine of the three is paid for",
                    4.0, rack.paidLoadingSpaces(), 1e-9);
        }
        assertEquals("two launchers, one magazine each, a point a drone", 8,
                bs.getDroneSpeedBpv());
    }

    /** The Klingon base mounts one launcher, so it pays for one magazine: four, not twelve. */
    @Test
    public void aSingleLauncherBasePaysForFourSpaces() {
        Ship bs = bought("Klingon", "BS", 183);

        assertEquals("fixture: one launcher", 1, racksOf(bs).size());
        assertEquals(4, bs.getDroneSpeedBpv());
    }

    /**
     * The Kzinti TGT, which is where this bit hardest: a 92-point hull was carrying a 24-point
     * drone surcharge, a quarter of its own value, for magazines FD3.44 gives it free.
     */
    @Test
    public void aTugPaysForItsMagazinesTheSameWay() {
        Ship tgt = bought("Kzinti", "TGT", 183);

        assertEquals("fixture: two type-D launchers", 2, racksOf(tgt).size());
        assertEquals("fixture: a hull the overcharge was a quarter of", 92, tgt.getBpv());
        assertEquals(8, tgt.getDroneSpeedBpv());
    }

    /** And it halves with the speed: medium drones are half a point (FD2.223). */
    @Test
    public void theChargeStillFollowsTheYearsSpeed() {
        assertEquals("Y170, medium: two magazines of four at a half", 4,
                bought("Kzinti", "TGT", 170).getDroneSpeedBpv());
        assertEquals("Y160, before medium is General: nothing owed at all", 0,
                bought("Kzinti", "TGT", 160).getDroneSpeedBpv());
    }

    // ---------------------------------------------------------------- which magazine is paid

    /**
     * FD2.452 decides: "The reload drones are presumed to be of the same cost (or lower) as the
     * drones in the rack." A free reload may be no dearer than what was paid for, so the paid
     * magazine has to be the dearest load in the launcher.
     *
     * <p>Otherwise the rule is an exploit: fill one magazine with slow drones, declare that the
     * paid one, and fly three magazines of fast ones for nothing. Eight cheap and four dear
     * here, with the cheap ones FIRST in the list so the answer cannot come from the ordering.
     *
     * <p>MEDIUM for the cheap eight rather than slow, and that is the point of the test rather
     * than a detail: a type-I-slow costs nothing at any date, so a rack of eight slow and four
     * fast totals four whether the cheap ones are billed or not, and the assertion would hold
     * against a charge that had never heard of FD3.44. At medium they are worth half a point
     * each, so billing them would read eight.
     */
    @Test
    public void theCostliestMagazineIsTheOneThatIsPaidFor() {
        List<Drone> load = new ArrayList<>(many(DroneType.TypeIM, 8));
        load.addAll(many(DroneType.TypeIF, 4));

        assertEquals("the four fast drones are the paid magazine, at a point each",
                4, chargeFor(new DroneRack(DroneRack.DroneRackType.TYPE_D), load, 183));
    }

    /**
     * A magazine is four SPACES, not four drones — FD2.11 trades two type-Is for one type-IV at
     * par, so a magazine of type-IVs holds two of them.
     */
    @Test
    public void aMagazineIsFilledBySpacesRatherThanByCount() {
        assertEquals("two two-space type-IVs fill one magazine, at a point each",
                2, chargeFor(new DroneRack(DroneRack.DroneRackType.TYPE_D),
                        many(DroneType.TypeIVF, 6), 183));
    }

    /**
     * And a drone too big for the space left does not end the fill — something smaller may
     * still take it. One two-space type-IV and then half-space type-VIs: the type-IV is dearest
     * so it goes in first, and four type-VIs take the two spaces behind it.
     *
     * <p>1.00 for the type-IV plus four at 0.50 is three. A fill that stopped at the first
     * drone that would not fit would charge one.
     */
    @Test
    public void asmallerDroneCanStillTakeTheSpaceABiggerOneCouldNot() {
        List<Drone> load = new ArrayList<>();
        load.add(new Drone(DroneType.TypeIVF));
        load.addAll(many(DroneType.TypeVIF, 20));

        assertEquals("the magazine is filled, not abandoned", 3,
                chargeFor(new DroneRack(DroneRack.DroneRackType.TYPE_D), load, 183));
    }

    // ---------------------------------------------------------------- type-H

    /**
     * FD3.84 is the same rule for a starbase, with the quantities moved: five magazines, and
     * "the starbase pays the drone upgrade surcharges for two magazines per launcher".
     *
     * <p>Asserted on a rack built here rather than on a hull, because no hull in the data
     * declares a type-H — {@code DroneRack}'s own TYPE_H note says the fifth, type-VI magazine
     * is unmodelled and wanted before any starbase is entered. This pins the half of FD3.84
     * that IS modelled so the other half has something to be added to.
     */
    @Test
    public void aTypeHLauncherPaysForTwoMagazinesOfItsFive() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_H);
        assertEquals("fixture: five magazines of four", 20, rack.getSpaces());
        assertEquals("FD3.84: two of the five", 8.0, rack.paidLoadingSpaces(), 1e-9);

        assertEquals("eight spaces of fast drones at a point each",
                8, chargeFor(rack, many(DroneType.TypeIF, 20), 183));
    }

    // ---------------------------------------------------------------- everyone else

    /**
     * No other rack changes, and the reason is worth keeping: an ordinary rack's reloads live in
     * {@code getReloads()}, which nothing prices, so FD2.45 is already satisfied by where the
     * drones are kept. {@link DroneRack#paidLoadingSpaces()} returns the whole rack for them.
     */
    @Test
    public void anOrdinaryRackIsStillChargedForEverythingInIt() {
        Ship cs = bought("Kzinti", "CS", 183);

        for (DroneRack rack : racksOf(cs))
            assertEquals("a type-B or type-C pays for all of itself",
                    rack.getSpaces(), rack.paidLoadingSpaces(), 1e-9);
        assertEquals("FD2.454's ship, refitted: twenty drones at a point", 20,
                cs.getDroneSpeedBpv());
    }

    /**
     * The type-E is the one that would break a naive spaces-based fill: eight dogfight drones in
     * four spaces, because a type-VI is half a space each (FD3.5). It is not a magazine rack, so
     * it never reaches that code at all — and all eight are charged.
     */
    @Test
    public void aTypeERackChargesAllEightOfItsDogfightDrones() {
        DroneRack rack = new DroneRack(DroneRack.DroneRackType.TYPE_E);
        assertEquals("fixture: four spaces", 4, rack.getSpaces());
        assertEquals("not a magazine rack", 4.0, rack.paidLoadingSpaces(), 1e-9);

        assertEquals("eight type-VIs at FD2.226's half rate", 4,
                chargeFor(rack, many(DroneType.TypeVIF, 8), 183));
    }

    /** A carrier's bill is untouched — the ready-rack half of FD2.45 is a separate walk. */
    @Test
    public void aCarriersSquadronIsUnaffected() {
        assertEquals("the Kzinti CVS still owes ninety", 90,
                bought("Kzinti", "CVS", 183).getDroneSpeedBpv());
    }
}
