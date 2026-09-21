package com.sfb.server;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * right and covered by ScenarioLoaderTest; CoiRequest just had no {@code photonOverload}
 * field, so Jackson dropped the key on arrival. Nothing failed — an empty map is exactly
 * what "no overload was asked for" looks like.
 * <p>
 * The guard against a repeat is the strict parse below: the payload here mirrors what
 * CoiDialog.tsx builds in its submit function, and a key with no field to land in fails the
 * build instead of vanishing. When the dialog learns to send something new, this fixture and
 * CoiRequest have to learn it together.
 */
class CoiRequestWireTest {

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
              "droneRackLoadouts": { "0": ["TypeI", "TypeIV"] },
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
    private static CoiRequest parseStrictly(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper.readValue(json, CoiRequest.class);
    }

    @Test
    void everyKeyTheDialogSendsHasAFieldToLandIn() throws Exception {
        assertNotNull(parseStrictly(PAYLOAD),
                "a key with no field on CoiRequest is silently dropped in production");
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
        assertEquals(List.of(DroneType.TypeI, DroneType.TypeIV),
                loadout.droneRackLoadouts.get(0));
        assertEquals(WeaponArmingType.OVERLOAD, loadout.weaponArmingModes.get("A"));
        assertEquals(WeaponArmingType.STANDARD, loadout.weaponArmingModes.get("C"));
        assertEquals("Ph-1", loadout.optionMounts.get("M1"));
        assertEquals(1, loadout.specialShuttlePrep.size());
        assertEquals("suicide", loadout.specialShuttlePrep.get(0).type);
        assertEquals(3, loadout.specialShuttlePrep.get(0).energyPerTurn);
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
