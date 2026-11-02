package com.sfb.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfb.objects.DroneLoad;
import com.sfb.objects.DroneType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.scenario.CoiLoadout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The COI wire layer: everything the Commander's Options dialog sends must reach a
 * CoiLoadout.
 * <p>
 * Written after a playtest found free WS-III photon overload energy (S4.32) selected in the
 * dialog and simply gone by the time the energy allocation form opened. The rule itself was
 * right and covered by ScenarioLoaderTest; CoiChoices just had no {@code photonOverload}
 * field, so Jackson dropped the key on arrival. Nothing failed — an empty map is exactly
 * what "no overload was asked for" looks like.
 * <p>
 * The guard against a repeat is the strict parse below: the payload here mirrors what
 * CoiDialog.tsx builds in its submit function, and a key with no field to land in fails the
 * build instead of vanishing. When the dialog learns to send something new, this fixture and
 * CoiChoices have to learn it together.
 */
class CoiChoicesWireTest {

    /**
     * One ship's entry exactly as the dialog sends it, with every optional key present.
     * Mirrors the object built in CoiDialog.tsx — keep the two in step.
     */
    private static final String PAYLOAD = """
            {
              "extraBoardingParties": 2,
              "convertBpToCommando": 1,
              "extraCommandoSquads": 1,
              "extraTBombs": 3,
              "droneRackLoadouts": {
                "0": ["TypeI", "TypeIV"],
                "2": ["TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF"]
              },
              "antiDroneLoadouts": { "1": 4 },
              "weaponArmingModes": { "A": "OVERLOAD", "C": "STANDARD" },
              "photonOverload": { "A": 4.0, "B": 2.5 },
              "specialShuttlePrep": [
                { "shuttleName": "USS Enterprise-Admin-1", "type": "suicide", "energyPerTurn": 3 }
              ],
              "optionMounts": { "M1": "Ph-1" },
              "cartel": "Beast Raiders"
            }
            """;

    /** Unknown keys are an error here, which is the whole point of this test. */
    private static com.sfb.scenario.CoiChoices parseStrictly(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper.readValue(json, com.sfb.scenario.CoiChoices.class);
    }

    @Test
    void everyKeyTheDialogSendsHasAFieldToLandIn() throws Exception {
        assertNotNull(parseStrictly(PAYLOAD),
                "a key with no field on CoiChoices is silently dropped in production");
    }

    /**
     * The bug itself: free overload energy must survive the wire and reach the loadout that
     * ScenarioLoader.applyCoi reads. Half points are legal (E4.414), so it must not be
     * rounded to an int on the way through either.
     */
    @Test
    void photonOverloadReachesTheLoadout() throws Exception {
        CoiLoadout loadout = parseStrictly(PAYLOAD).toLoadout();

        assertEquals(4.0, loadout.photonOverload.get("A"), 0.001);
        assertEquals(2.5, loadout.photonOverload.get("B"), 0.001,
                "half points are legal and must not be rounded away (E4.414)");
    }

    @Test
    void theRestOfTheLoadoutSurvivesToo() throws Exception {
        CoiLoadout loadout = parseStrictly(PAYLOAD).toLoadout();

        assertEquals(2, loadout.extraBoardingParties);
        assertEquals(1, loadout.convertBpToCommando);
        assertEquals(1, loadout.extraCommandoSquads);
        assertEquals(3, loadout.extraTBombs);
        assertEquals(List.of(DroneLoad.of(DroneType.TypeI), DroneLoad.of(DroneType.TypeIV)),
                loadout.droneRackLoadouts.get(0));
        assertEquals(DroneLoad.parse("TypeIV+ARMOR_HALF+ARMOR_HALF+ARMOR_HALF+EXPLOSIVE_HALF"),
                loadout.droneRackLoadouts.get(2).get(0),
                "FD10.4's payload modules travel as text on the name they modify");
        assertEquals(Integer.valueOf(4), loadout.antiDroneLoadouts.get(1),
                "anti-drones travel on their own map (FD3.70) and must survive the wire");
        assertEquals(WeaponArmingType.OVERLOAD, loadout.weaponArmingModes.get("A"));
        assertEquals(WeaponArmingType.STANDARD, loadout.weaponArmingModes.get("C"));
        assertEquals("Ph-1", loadout.optionMounts.get("M1"));
        assertEquals(1, loadout.specialShuttlePrep.size());
        assertEquals("suicide", loadout.specialShuttlePrep.get(0).type);
        assertEquals(3, loadout.specialShuttlePrep.get(0).energyPerTurn);
    }

    /**
     * A drone the rules refuse is REPORTED, not dropped.
     *
     * <p>Silence was survivable while a name was one of fourteen enum constants the dialog
     * itself supplied: the only way to send an unknown one was a bug in our own code. A
     * composed drone (FD10.2) is assembled from a payload, so asking for one that cannot exist
     * is now something a player can do, and "rack 0 came back empty" is not an answer.
     */
    @Test
    void aDroneTheRulesRefuseIsReportedRatherThanDropped() throws Exception {
        com.sfb.scenario.CoiChoices request = parseStrictly(
                "{\"droneRackLoadouts\": {\"0\": [\"TypeI\", \"TypeI+ECM+ARMOR_1\"]}}");
        CoiLoadout loadout = request.toLoadout();

        assertEquals(List.of(DroneLoad.of(DroneType.TypeI)), loadout.droneRackLoadouts.get(0),
                "the legal drone is still loaded");
        assertEquals(1, request.droneRefusals.size(),
                "and the illegal one is accounted for: " + request.droneRefusals);
        assertTrue(request.droneRefusals.get(0).contains("TypeI+ECM+ARMOR_1"),
                "the warning names the drone: " + request.droneRefusals.get(0));
        assertTrue(request.droneRefusals.get(0).contains("FD10.443"),
                "and the clause that refuses it: " + request.droneRefusals.get(0));
    }

    /** Nothing to report when everything reads, including a composed drone. */
    @Test
    void aLegalLoadoutRefusesNothing() throws Exception {
        com.sfb.scenario.CoiChoices request = parseStrictly(PAYLOAD);
        request.toLoadout();

        assertTrue(request.droneRefusals.isEmpty(), request.droneRefusals.toString());
    }

    /** An omitted key is not an error — the dialog leaves out what the player did not use. */
    @Test
    void anEmptySelectionIsStillValid() throws Exception {
        CoiLoadout loadout = parseStrictly("{\"extraTBombs\": 1}").toLoadout();

        assertEquals(1, loadout.extraTBombs);
        assertTrue(loadout.photonOverload.isEmpty());
        assertTrue(loadout.weaponArmingModes.isEmpty());
    }
}
