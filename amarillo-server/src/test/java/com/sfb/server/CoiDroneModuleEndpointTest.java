package com.sfb.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * What the Commander's Options offer as a drone's PAYLOAD (FD10.4) — the third axis.
 *
 * <p>The picker has always had two: the numeral carries the family and the suffix carries speed.
 * Armor, ECM, probe, swordfish and multi-warhead modules were built, priced and capped, and
 * could not be offered because a loadout had no way to name one. {@code DroneLoad} gave them a
 * name; this gives the dialog the catalogue to render.
 *
 * <p>Asked of the server because every number in it is a rules number. FD10.6's availability
 * ladder is resolved for the scenario's year here, and a client holding that table would be the
 * view deciding when a swordfish module becomes obtainable.
 */
class CoiDroneModuleEndpointTest {

    @BeforeEach
    void loadShips() throws Exception {
        com.sfb.objects.ShipLibrary.loadAllSpecs("../data/factions");
        com.sfb.objects.ShipLineCatalog.loadDefault("../data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coiFor(int year, Integer maxDroneSpeed) {
        com.sfb.scenario.ScenarioSpec spec = new com.sfb.scenario.ScenarioSpec();
        spec.mapCols = 42;
        spec.mapRows = 32;
        spec.year = year;
        spec.commanderOptions = new com.sfb.scenario.ScenarioSpec.CommanderOptions();
        spec.commanderOptions.maxDroneSpeed = maxDroneSpeed;
        com.sfb.scenario.ScenarioSpec.SideSpec side = new com.sfb.scenario.ScenarioSpec.SideSpec();
        side.faction = "Federation";
        side.name = "Federation";
        side.ships = new java.util.ArrayList<>();
        com.sfb.scenario.ScenarioSpec.ShipSetup ship = new com.sfb.scenario.ScenarioSpec.ShipSetup();
        ship.type = "FFG";
        ship.shipName = "USS Perry";
        ship.startHex = "1016";
        ship.startHeading = "C";
        side.ships.add(ship);
        spec.sides = new java.util.ArrayList<>(List.of(side));

        List<Map<String, Object>> sides = new GameController(null, null).coiDataFor(spec);
        return sides.stream()
                .flatMap(sd -> ((List<Map<String, Object>>) sd.get("ships")).stream())
                .filter(s -> "USS Perry".equals(s.get("shipName")))
                .findFirst().orElseThrow(() -> new AssertionError("no FFG: " + sides));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> coi, String key) {
        Object value = coi.get(key);
        assertNotNull(value, key + " is missing, so the dialog has nothing to render");
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> named(List<Map<String, Object>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("name"))).findFirst()
                .orElseThrow(() -> new AssertionError(name + " is not offered: " + rows));
    }

    // ------------------------------------------------------------------ the modules

    @Test
    void theModuleCatalogueCarriesWhatAPickerNeeds() {
        Map<String, Object> ecm = named(listOf(coiFor(180, null), "availableDroneModules"), "ECM");

        assertEquals("ECM", ecm.get("label"));
        assertEquals(1.0, (Double) ecm.get("spaces"), 0.0001,
                "FD10.442: one payload space, and only one per drone");
        assertEquals(0.50, (Double) ecm.get("cost"), 0.0001, "FD10.51's chart");
        assertEquals("RESTRICTED", ecm.get("availability"));
        assertEquals(0, ecm.get("strength"), "FD10.44: a jammer that happens to fly");
        assertEquals(false, ecm.get("requiresForward"),
                "FD10.442: \"it need not be forward\"");
        assertEquals(false, ecm.get("armor"));
        assertEquals(0, ecm.get("submunitions"));
    }

    /** FD10.443's prohibition is the one a picker most wants to see coming. */
    @Test
    void armorSaysThatItIsArmor() {
        List<Map<String, Object>> modules =
                listOf(coiFor(180, null), "availableDroneModules");

        assertEquals(true, named(modules, "ARMOR_HALF").get("armor"));
        assertEquals(true, named(modules, "ARMOR_1").get("armor"));
        assertEquals(2, named(modules, "ARMOR_HALF").get("hullBonus"),
                "FD12.21: the 1/2-PS module adds two damage points");
        assertEquals(false, named(modules, "EXPLOSIVE_1").get("armor"));
    }

    /** FD10.402/FD10.432: a forward-only module is a claim on the one forward space. */
    @Test
    void theForwardOnlyModulesSaySo() {
        List<Map<String, Object>> modules =
                listOf(coiFor(180, null), "availableDroneModules");

        assertEquals(true, named(modules, "MW_SMALL").get("requiresForward"));
        assertEquals(true, named(modules, "SWORDFISH_PHASER3").get("requiresForward"));
        assertEquals(3, named(modules, "MW_SMALL").get("submunitions"),
                "FD10.431: three type-VI drones instead of a warhead");
        assertEquals(5, named(modules, "MW_LARGE").get("submunitions"));
    }

    /**
     * FD2.51: "There are no variants (FD2.22) of type-VI drones except for speed upgrades." The
     * dogfight warhead is what the half-space frame carries, not something a player buys, and
     * offering it would be offering to build a drone {@code DroneSpec} refuses.
     */
    @Test
    void theDogfightWarheadIsNotOnOffer() {
        List<Map<String, Object>> modules =
                listOf(coiFor(180, null), "availableDroneModules");

        assertFalse(modules.stream().anyMatch(m -> "DOGFIGHT_WARHEAD".equals(m.get("name"))),
                "the type-VI's fixed warhead is not a choice (FD10.21)");
    }

    // ------------------------------------------------------------------ the frame additions

    @Test
    void theFrameAdditionsAreOfferedToo() {
        List<Map<String, Object>> additions =
                listOf(coiFor(180, null), "availableDroneAdditions");

        Map<String, Object> armor = named(additions, "EXTERNAL_ARMOR_HALF");
        assertEquals(0.25, (Double) armor.get("cost"), 0.0001, "FD10.53's chart");
        assertEquals("LIMITED", armor.get("availability"),
                "FD10.631: external armor is harder to come by than internal (FD10.621)");
        assertEquals(true, armor.get("externalArmor"));
        assertEquals(2, armor.get("hullBonus"));

        assertEquals(false, named(additions, "EXTENDED_RANGE").get("externalArmor"));
    }

    /**
     * The type-III's frame surcharge is not an option: it IS a type-III. FD10.22 charges it
     * "for standard 25-turn range and special guidance", and the endurance that comes with it
     * is stated by the drone rather than derived from the addition — so buying it onto a type-I
     * would get neither, and {@code DroneLoad} refuses a type-III built without it.
     */
    @Test
    void theTypeIiiSurchargeIsNotOnOffer() {
        List<Map<String, Object>> additions =
                listOf(coiFor(180, null), "availableDroneAdditions");

        assertFalse(additions.stream().anyMatch(a -> "TYPE_III_GUIDANCE".equals(a.get("name"))),
                "a type-III's guidance is what makes it one, not a purchase");
    }

    // ------------------------------------------------------------------ submunition speeds

    /**
     * FD8.11 buys submunitions at "any speed available for the year", FD8.24 buys them
     * separately from the bus's own engine, and FD8.44 makes them all share one speed.
     */
    @Test
    void submunitionSpeedsAreOfferedForTheYear() {
        List<Map<String, Object>> speeds =
                listOf(coiFor(180, null), "availableSubmunitionSpeeds");

        assertEquals(List.of("SPEED_12", "SPEED_20", "SPEED_32"),
                speeds.stream().map(sp -> (String) sp.get("name")).toList(),
                "FD10.52 prints a dash in the half-space speed-8 column, and FD2.227 puts a"
                        + " type-VI's slow at 12");
        assertEquals("GENERAL", named(speeds, "SPEED_32").get("availability"));
    }

    @Test
    void anUninventedSubmunitionEngineIsNotOffered() {
        List<Map<String, Object>> speeds =
                listOf(coiFor(166, null), "availableSubmunitionSpeeds");

        assertEquals(List.of("SPEED_12", "SPEED_20"),
                speeds.stream().map(sp -> (String) sp.get("name")).toList(),
                "FD10.651: speed 32 was invented in Y178");
        assertEquals("RESTRICTED", named(speeds, "SPEED_20").get("availability"),
                "FD10.651: medium speed is Restricted in Y166 and General from Y167");
    }

    /** And the scenario's own cap applies to them, as it does to the bus. */
    @Test
    void theScenarioSpeedCapReachesTheSubmunitions() {
        List<Map<String, Object>> speeds =
                listOf(coiFor(180, 20), "availableSubmunitionSpeeds");

        assertEquals(List.of("SPEED_12", "SPEED_20"),
                speeds.stream().map(sp -> (String) sp.get("name")).toList());
    }

    // ------------------------------------------------------------------ per-drone legality

    /** A type-I has one payload space, so a two-space module will not go on it (FD10.4). */
    @Test
    @SuppressWarnings("unchecked")
    void aOneSpaceDroneIsOfferedOnlyWhatFits() {
        Map<String, Object> typeI =
                named(listOf(coiFor(180, null), "availableDroneTypes"), "TypeI");

        assertEquals(1, typeI.get("payloadSpaces"));
        assertEquals(true, typeI.get("modular"));
        List<String> modules = (List<String>) typeI.get("legalModules");
        assertTrue(modules.contains("ECM"));
        assertTrue(modules.contains("SWORDFISH_PHASER3"));
        assertFalse(modules.contains("EXPLOSIVE_2"), "two spaces into one: " + modules);
        assertFalse(modules.contains("MW_LARGE"), "FD10.431's large module is two spaces");
        assertTrue(modules.contains("MW_SMALL"));
    }

    /** And a type-IV takes both sizes. */
    @Test
    @SuppressWarnings("unchecked")
    void aTwoSpaceDroneIsOfferedTheBigModules() {
        Map<String, Object> typeIv =
                named(listOf(coiFor(180, null), "availableDroneTypes"), "TypeIV");

        assertEquals(2, typeIv.get("payloadSpaces"));
        List<String> modules = (List<String>) typeIv.get("legalModules");
        assertTrue(modules.contains("EXPLOSIVE_2"));
        assertTrue(modules.contains("MW_LARGE"));
    }

    /**
     * FD10.21: the half-space frame "cannot be modified", and FD2.51 says the same from the
     * other end. A dogfight drone is offered nothing at all.
     */
    @Test
    @SuppressWarnings("unchecked")
    void aDogfightDroneTakesNothing() {
        Map<String, Object> typeVi =
                named(listOf(coiFor(180, null), "availableDroneTypes"), "TypeVI");

        assertEquals(false, typeVi.get("modular"));
        assertTrue(((List<String>) typeVi.get("legalModules")).isEmpty());
        assertTrue(((List<String>) typeVi.get("legalAdditions")).isEmpty(),
                "FD2.222 bars extended range and FD5.22 bars ATG");
        assertFalse(typeVi.containsKey("rackWithExtendedRange"),
                "a dogfight drone cannot be given extended range (FD2.222)");
    }

    /** FD10.23: "ATG cannot be added to type-III drone, which already has" guidance. */
    @Test
    @SuppressWarnings("unchecked")
    void aTypeIiiIsNotOfferedAtg() {
        List<Map<String, Object>> types = listOf(coiFor(180, null), "availableDroneTypes");

        assertFalse(((List<String>) named(types, "TypeIII").get("legalAdditions"))
                        .contains("ACTIVE_TERMINAL_GUIDANCE"),
                "a type-III already guides itself (FD2.221)");
        assertTrue(((List<String>) named(types, "TypeI").get("legalAdditions"))
                        .contains("ACTIVE_TERMINAL_GUIDANCE"),
                "FD5.22: any drone other than a type-VI may be equipped with ATG");
    }

    /**
     * FD10.24: extended range turns a type-III into a type-III-XX, "which are two-space drones
     * with one payload space". The only modification that changes how much rack a drone fills,
     * which is why the figure is sent rather than derived.
     */
    @Test
    void extendedRangeOnATypeIiiFillsTwoRackSpaces() {
        List<Map<String, Object>> types = listOf(coiFor(180, null), "availableDroneTypes");

        assertEquals(2.0, (Double) named(types, "TypeIII").get("rackWithExtendedRange"), 0.0001);
        assertEquals(1.0, (Double) named(types, "TypeI").get("rackWithExtendedRange"), 0.0001,
                "every other drone keeps its footprint");
        assertEquals(1, named(types, "TypeIII").get("payloadSpaces"),
                "and it is still a one-payload-space drone");
    }

    // ------------------------------------------------------------------ nothing was lost

    /** The two existing axes are untouched: the catalogue is an addition, not a replacement. */
    @Test
    void theDroneTypeListIsStillThere() {
        List<Map<String, Object>> types = listOf(coiFor(180, null), "availableDroneTypes");

        assertTrue(types.size() >= 12, "every drone of Y180 should still be offered: " + types);
        assertEquals("Standard", named(types, "TypeIF").get("familyLabel"));
    }
}
