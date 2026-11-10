package com.sfb.objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.sfb.samples.FederationShips;
import com.sfb.samples.RomulanShips;
import com.sfb.systemgroups.MineRack;

/**
 * (Annex #7E, M9.13, D4.351) The cargo row of the DAMAGE CONVERSION CHART, which is not one box
 * type but three.
 *
 * <blockquote>Annex #7E: "Cargo ..... Repair, Mine Rack, ship in Starbase docking module (R1.1D),
 * Works."</blockquote>
 *
 * <blockquote>M9.13: "Cargo hits may be scored on mine rack boxes and <b>must</b> be scored on
 * them if no other cargo boxes are available."</blockquote>
 *
 * <h2>Why the row is a choice and not a fall-through</h2>
 * (D4.351), writing about the hull rows of the same chart, settles the question for all of them:
 * "Barracks (G28.0) and Repair (G17.0) are also destroyed by hull hits. If a ship has either of
 * the acceptable types of hull and one of the systems listed on Annex #7E, <b>the hit must be
 * scored on one of them</b>." The owner picks; the picking is compulsory while anything qualifies.
 *
 * <h2>The two cases that look alike and are not</h2>
 * A hull with cargo boxes and nothing else has one candidate, so there is nothing to decide and no
 * dialog is raised. A hull with cargo AND repair boxes has two, and the owner chooses. Both of
 * those are "mandatory"; only the second is a choice. These tests keep the pair apart, because the
 * tempting simplification — prompt whenever the row can be scored — would put a modal in front of
 * the player on every cargo hit in the game.
 */
public class CargoRowConversionTest {

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
    }

    // ---------------------------------------------------------------- one candidate: no dialog

    /**
     * A Federation CMC has cargo boxes and neither repair boxes nor mine racks, so the row has
     * exactly one candidate and resolves silently.
     *
     * <p>The CMC also carries three barracks boxes, which makes it the fixture that proves
     * barracks are NOT on this row. Annex #7E puts them on the two hull rows and nowhere else,
     * and the easy mistake when wiring a list of "other boxes a hit can land on" is to pour all
     * of them into one bucket.
     */
    @Test
    public void aHullWithOnlyCargoBoxesIsNotAskedToChoose() throws Exception {
        Ship cmc = ShipLibrary.createShip(ShipLibrary.get("Federation", "CMC"));
        cmc.setName("USS Commando");
        int cargo = cmc.getHullBoxes().getAvailableCargo();
        assertEquals("cmc.json declares cargo: 2", 2, cargo);
        assertEquals("and barracks: 3, which belong to the HULL rows",
                3, cmc.getHullBoxes().getAvailableBarracks());

        assertEquals("the row can be scored, in exactly one place",
                List.of("cargo"), cmc.dacChoiceOptionsForTest("cargo"));
        assertFalse("so no dialog: there is nothing for the owner to decide",
                cmc.requiresPlayerChoiceForTest("cargo"));

        assertEquals("cargo HIT", cmc.applySystemHitForTest("cargo"));
        assertEquals(cargo - 1, cmc.getHullBoxes().getAvailableCargo());
    }

    /**
     * And a hull with nothing on the row at all scores nowhere, which advances the DAC.
     *
     * <p>Worth its own test because it is the COMMON case, not an edge: the Federation CA, like
     * most warships, declares no cargo boxes whatever. "Cargo" sits on three DAC columns and on
     * most hulls in the game it has always fallen straight through.
     */
    @Test
    public void aWarshipWithNoCargoBoxesScoresNowhereOnTheRow() {
        Ship ca = plainHull();

        assertEquals("the Federation CA has no cargo boxes",
                0, ca.getHullBoxes().getAvailableCargo());
        assertTrue(ca.dacChoiceOptionsForTest("cargo").isEmpty());
        assertFalse(ca.requiresPlayerChoiceForTest("cargo"));
        assertNull("so the DAC moves to the next column", ca.applySystemHitForTest("cargo"));
    }

    // ---------------------------------------------------------------- two candidates: a dialog

    /**
     * A Base Station has ten cargo boxes and thirty repair boxes, so the owner chooses.
     *
     * <p>Thirty is not a rounding error. Before repair boxes were modelled a Base Station had ten
     * boxes on this row where its SSD prints forty, and three quarters of its absorption on the
     * row did not exist.
     */
    @Test
    public void aHullWithCargoAndRepairBoxesIsAskedWhichToLose() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");

        assertEquals("R1.3: ten cargo boxes", 10, bs.getHullBoxes().getAvailableCargo());
        assertEquals("R1.3: and thirty repair boxes", 30, bs.getHullBoxes().getAvailableRepair());

        assertEquals(List.of("cargo", "repair"), bs.dacChoiceOptionsForTest("cargo"));
        assertTrue("two candidates is a real decision (D4.351)",
                bs.requiresPlayerChoiceForTest("cargo"));
    }

    /** And the owner's pick lands where they said, not on the first candidate. */
    @Test
    public void theOwnersPickLandsOnTheBoxTheyChose() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");

        String label = bs.applyDacChoiceHit("cargo", "repair", null);

        assertEquals("repair HIT (G17.0) [chosen]", label);
        assertEquals("the repair box went", 29, bs.getHullBoxes().getAvailableRepair());
        assertEquals("and the cargo boxes are untouched",
                10, bs.getHullBoxes().getAvailableCargo());
    }

    // ------------------------------------------------- no cargo boxes at all: the "must" case

    /**
     * (M9.13, D4.351) The Romulan ROC has repair boxes and NO cargo boxes, so a cargo hit is
     * compulsory on a repair box — it may not advance the DAC to the next column.
     *
     * <p>This is the regression that mattered most. With repair boxes unmodelled the ROC had
     * nothing on the cargo row at all: {@code tryApplySystemHit("cargo")} returned null, the DAC
     * fell through to the next entry, and the ship was effectively immune to cargo-row damage.
     */
    @Test
    public void theRocHasNoCargoBoxesSoTheHitMustLandOnARepairBox() throws Exception {
        Ship roc = ShipLibrary.createShip(ShipLibrary.get("Romulan", "ROC"));
        roc.setName("RIS Consul");

        assertEquals("roc.json: no cargo boxes", 0, roc.getHullBoxes().getAvailableCargo());
        assertEquals("roc.json: four repair boxes", 4, roc.getHullBoxes().getAvailableRepair());

        assertEquals("the row has one candidate and it is not cargo",
                List.of("repair"), roc.dacChoiceOptionsForTest("cargo"));
        assertFalse("one candidate, so still no dialog", roc.requiresPlayerChoiceForTest("cargo"));

        assertEquals("repair HIT (G17.0)", roc.applySystemHitForTest("cargo"));
        assertEquals(3, roc.getHullBoxes().getAvailableRepair());
    }

    // ---------------------------------------------------------------- mine racks (M9.13)

    /** (M9.13) A mine rack is a candidate on the cargo row, addressed by index. */
    @Test
    public void aMineRackIsACargoRowCandidate() {
        Ship layer = withMineRacks(2);

        assertEquals(List.of("minerack:0", "minerack:1"), layer.dacChoiceOptionsForTest("cargo"));
    }

    /** With cargo boxes beside them, all three kinds are offered together. */
    @Test
    public void cargoRepairAndRacksAreOfferedTogether() {
        Ship ship = withMineRacks(1);
        ship.getHullBoxes().init(hullWith(4, 2));

        assertEquals(List.of("cargo", "repair", "minerack:0"),
                ship.dacChoiceOptionsForTest("cargo"));
        assertTrue(ship.requiresPlayerChoiceForTest("cargo"));
    }

    /**
     * (M9.13) "The mines on a destroyed rack are also destroyed (but will not detonate)."
     *
     * <p>The parenthesis is the clause worth a test: four NSMs going up inside the hull would be
     * the end of the ship, and the rule says plainly that it is not what happens. This is also
     * {@link MineRack#destroy()}'s first real caller — until now nothing in the game could destroy
     * a rack, so the no-detonation path was theoretical.
     */
    @Test
    public void destroyingALoadedRackLosesItsMinesWithoutDetonating() {
        Ship layer = withMineRacks(2);
        layer.getMineRacks().get(0).loadLarge(4);

        String label = layer.applyDacChoiceHit("cargo", "minerack:0", null);

        assertNotNull("the hit landed", label);
        assertTrue("the log says what was lost and why it did not explode: " + label,
                label.contains("4 mine(s) destroyed") && label.contains("M9.13"));
        assertTrue(layer.getMineRacks().get(0).isDestroyed());
        assertEquals("the mines are gone", 0, layer.getMineRacks().get(0).getTotalMines());
        assertFalse("and the ship is not destroyed with them", layer.isDestroyed());
        assertEquals("the other rack is untouched",
                false, layer.getMineRacks().get(1).isDestroyed());
    }

    /** A destroyed rack leaves the candidate list; it cannot absorb a second point. */
    @Test
    public void aDestroyedRackIsNoLongerACandidate() {
        Ship layer = withMineRacks(2);
        layer.getMineRacks().get(0).destroy();

        assertEquals(List.of("minerack:1"), layer.dacChoiceOptionsForTest("cargo"));
        assertNull("and naming it again does nothing",
                layer.applyDacChoiceHit("cargo", "minerack:0", null));
    }

    /**
     * An EMPTY rack is still a box, and still absorbs the point.
     *
     * <p>The trap this pins: {@link MineRack#isUsable(int)} refuses an empty rack, because it is
     * the LAYING check — a rack with no mines in it has none to lay. Damage asks a different
     * question, and reusing that method would have made an empty rack indestructible.
     */
    @Test
    public void anEmptyRackIsStillABoxThatTakesTheHit() {
        Ship layer = withMineRacks(1);
        MineRack rack = layer.getMineRacks().get(0);
        assertTrue("fixture: empty", rack.isEmpty());
        assertFalse("and so not usable for laying", rack.isUsable(1));

        assertEquals(List.of("minerack:0"), layer.dacChoiceOptionsForTest("cargo"));
        String label = layer.applySystemHitForTest("cargo");
        assertEquals("mine rack #1 HIT (empty)", label);
        assertTrue(rack.isDestroyed());
    }

    // ---------------------------------------------------------------- the row runs out

    /**
     * Once every box on the row is gone the row is scored nowhere, which is the signal that
     * advances the DAC to the next column.
     */
    @Test
    public void whenNothingIsLeftOnTheRowTheDacAdvances() {
        Ship ship = withMineRacks(1);
        ship.getHullBoxes().init(hullWith(1, 1));

        assertEquals(3, ship.dacChoiceOptionsForTest("cargo").size());
        assertNotNull(ship.applySystemHitForTest("cargo")); // cargo
        assertNotNull(ship.applySystemHitForTest("cargo")); // repair
        assertNotNull(ship.applySystemHitForTest("cargo")); // the rack

        assertTrue("nothing left on the row", ship.dacChoiceOptionsForTest("cargo").isEmpty());
        assertNull("so the hit is refused and the DAC moves on (C3.14)",
                ship.applySystemHitForTest("cargo"));
    }

    /**
     * The forced path and the chosen path destroy the same box, because both go through one
     * method. Asserted so the auto-pick cannot drift away from the first offered option — the
     * non-interactive wrapper {@code applyInternalDamage(int)} takes exactly that option.
     */
    @Test
    public void theForcedPathTakesTheFirstOfferedCandidate() throws Exception {
        Ship forced = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        Ship chosen = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        forced.setName("A");
        chosen.setName("B");

        String first = forced.dacChoiceOptionsForTest("cargo").get(0);
        forced.applySystemHitForTest("cargo");
        chosen.applyDacChoiceHit("cargo", first, null);

        assertEquals(chosen.getHullBoxes().getAvailableCargo(),
                forced.getHullBoxes().getAvailableCargo());
        assertEquals(chosen.getHullBoxes().getAvailableRepair(),
                forced.getHullBoxes().getAvailableRepair());
    }

    // ---------------------------------------------------------------- what moved, still works

    /**
     * (FD2.445) "These drones are lost when the cargo boxes are destroyed." The cargo branch moved
     * out of {@code tryApplySystemHit} into the shared conversion method, so the drone loss it
     * reports is re-pinned here rather than taken on trust.
     */
    @Test
    public void cargoBoxDronesStillDieWithTheirBox() {
        Map<String, Object> spec = new HashMap<>(FederationShips.getFedCa());
        spec.put("cargo", 2);
        spec.put("cargodronespacesperbox", 6);
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("USS Hauler");
        assertNotNull("fixture: the hull declares cargo-box drone space",
                ship.getCargoDroneStore());
        ship.getCargoDroneStore().restore(6);

        String label = ship.applySystemHitForTest("cargo");

        assertTrue("the loss is reported rather than silent: " + label,
                label.contains("spaces of drones lost"));
    }

    // ---------------------------------------------------------------- the data and the hull

    /**
     * (G17.0) The ten hulls that declare repair boxes, read from disk.
     *
     * <p>Read from the files on purpose. The parked note this work replaced said "Romulan, 1 file,
     * the ROC" — written before the nine Base Stations were entered, and wrong by nine hulls and
     * 270 boxes by the time anyone looked. A roster assertion is how that stops happening twice.
     */
    @Test
    public void tenHullsDeclareRepairBoxes() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.Map<String, Integer> found = new java.util.TreeMap<>();

        java.nio.file.Path root = java.nio.file.Paths.get("../data/factions");
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path p : files.filter(f -> f.toString().endsWith(".json"))
                    .collect(java.util.stream.Collectors.toList())) {
                com.fasterxml.jackson.databind.JsonNode n = mapper.readTree(p.toFile());
                int repair = n.path("hullBoxes").path("repair").asInt(0);
                if (repair > 0)
                    found.put(p.getParent().getFileName() + "/" + p.getFileName(), repair);
            }
        }

        assertEquals("the roster of hulls with repair boxes has changed — if a hull was added,"
                + " say so here: " + found, 10, found.size());
        assertEquals("the ROC is the only one that is not a Base Station",
                Integer.valueOf(4), found.get("romulan/roc.json"));
        for (Map.Entry<String, Integer> e : found.entrySet())
            if (!e.getKey().equals("romulan/roc.json"))
                assertEquals(e.getKey() + " is a Base Station and should have 30",
                        Integer.valueOf(30), e.getValue());
    }

    /**
     * (S2.41 B) Repair boxes are interior boxes, so they raise the cripple threshold.
     *
     * <p>S2.41's exclusion list is exhaustive — "does not include shields, armor, sensor, scanner,
     * DamCon, or excess damage" — and repair is not on it. A Base Station used to be crippled by
     * 30 boxes of damage it should have shrugged off, because the denominator was missing them.
     */
    @Test
    public void repairBoxesCountAsInteriorBoxesForCrippling() throws Exception {
        Ship bs = ShipLibrary.createShip(ShipLibrary.get("Federation", "BS"));
        bs.setName("Starbase Echo");
        assertFalse("fixture: undamaged", bs.isCrippled());

        assertEquals("chull 10 + cargo 10 + armor 5 + repair 30",
                55, bs.getHullBoxes().fetchOriginalTotalBoxes());
        int before = bs.getHullBoxes().fetchRemainingTotalBoxes();

        // Spend every repair box and nothing else. 30 points of damage, all on one kind of box.
        for (int i = 0; i < 30; i++)
            assertTrue("repair box " + i, bs.getHullBoxes().damageRepair());

        assertEquals(0, bs.getHullBoxes().getAvailableRepair());
        // The assertion that moves. Leave repair out of the box totals and this reads
        // `before - 0`: thirty boxes would be destroyed and the cripple arithmetic would never
        // hear about it.
        assertEquals("S2.41 B: thirty interior boxes destroyed, not none",
                before - 30, bs.getHullBoxes().fetchRemainingTotalBoxes());
        assertFalse("but thirty is not yet half of a Base Station's interior", bs.isCrippled());
    }

    /**
     * (G28.0) The Federation CMC's three barracks boxes survive the trip through {@code ShipSpec}.
     *
     * <p>They did not before: {@code HullBoxes.init} read the {@code barracks} key and
     * {@code ShipSpec.toMap} never wrote it, so the one hull in the game with barracks lost all
     * three on every load. Nothing noticed, because nothing damaged a barracks box either.
     */
    @Test
    public void theCmcKeepsItsBarracksBoxes() throws Exception {
        Ship cmc = ShipLibrary.createShip(ShipLibrary.get("Federation", "CMC"));
        cmc.setName("USS Commando");

        assertEquals("cmc.json declares barracks: 3", 3, cmc.getHullBoxes().getMaxBarracks());
        assertEquals(3, cmc.getHullBoxes().getAvailableBarracks());
    }

    // ---------------------------------------------------------------- fixtures

    /** A Federation CA: cargo boxes, no repair boxes, no racks. */
    private static Ship plainHull() {
        Ship ship = new Ship();
        ship.init(FederationShips.getFedCa());
        ship.setName("USS Enterprise");
        return ship;
    }

    /** A hull-box map with the given cargo and repair counts and nothing else on the row. */
    private static Map<String, Object> hullWith(int cargo, int repair) {
        Map<String, Object> m = new HashMap<>();
        m.put("cargo", cargo);
        m.put("repair", repair);
        return m;
    }

    /**
     * A hull carrying mine racks, assembled by hand because no sample builder has any — the one
     * hull in the data with racks is the Romulan large Q-ship, and {@code RomulanShips} carries
     * the KR and the WE. {@code MineRackTest} reads {@code lq.json} to cover the real file.
     */
    private static Ship withMineRacks(int boxes) {
        Map<String, Object> spec = new HashMap<>(RomulanShips.getRomKr());
        spec.put("mineracks", boxes);
        spec.remove("cargo");
        Ship ship = new Ship();
        ship.init(spec);
        ship.setName("Romulan minelayer");
        return ship;
    }
}
