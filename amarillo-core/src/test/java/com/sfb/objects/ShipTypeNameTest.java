package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

/**
 * The class a ship file writes out, e.g. "Commando Cruiser" for type "CMC".
 * <p>
 * Every one of the 162 ship files has carried a "typeName" since the data was written, and
 * nothing read it: ShipSpec did not declare it, so Jackson dropped it on the floor and the
 * only name a player ever saw was the terse SSD designation. A field that loads silently
 * into nothing looks exactly like a field that works, which is the whole hazard.
 */
public class ShipTypeNameTest {

    private Ship load(String path) throws Exception {
        ShipSpec spec = ShipSpec.fromJson(new File(path));
        assertNotNull(path + " parsed", spec);
        return ShipLibrary.createShip(spec);
    }

    @Test
    public void aShipKnowsItsClassInWords() throws Exception {
        Ship cmc = load("../data/factions/federation/cmc.json");
        assertEquals("CMC", cmc.getType());
        assertEquals("Commando Cruiser", cmc.getTypeName());
    }

    /** The terse designation is untouched — the long name is an addition, not a replacement. */
    @Test
    public void theSsdDesignationIsUnchanged() throws Exception {
        Ship mec = load("../data/factions/kzinti/mec.json");
        assertEquals("MEC", mec.getType());
        assertEquals("Medium Escort Cruiser", mec.getTypeName());
    }

    /**
     * And it reaches the client, which is where the whole point of the field lies. A DTO
     * field is the only part a player can actually see.
     */
    @Test
    public void itSurvivesTheTripIntoTheDto() throws Exception {
        com.sfb.Game game = new com.sfb.Game();
        Ship cmc = load("../data/factions/federation/cmc.json");
        cmc.setName("USS Carrying");
        cmc.setLocation(new com.sfb.properties.Location(10, 10));
        game.getShips().add(cmc);

        com.sfb.dto.GameStateDto dto = new com.sfb.dto.GameStateDto(game, null);
        com.sfb.dto.GameStateDto.ShipDto sd = dto.mapObjects.stream()
                .filter(o -> o instanceof com.sfb.dto.GameStateDto.ShipDto)
                .map(o -> (com.sfb.dto.GameStateDto.ShipDto) o)
                .findFirst().orElseThrow(() -> new AssertionError("no ship in the snapshot"));

        assertEquals("CMC", sd.shipType);
        assertEquals("Commando Cruiser", sd.typeName);
    }

    /**
     * Every ship file names its class. This is the guard the data never had: a new file that
     * forgets the field, or misspells it, shows a blank where a name belongs.
     */
    @Test
    public void everyShipFileNamesItsClass() throws Exception {
        File dir = new File("../data/factions");
        int checked = 0;
        for (File faction : dir.listFiles(File::isDirectory)) {
            for (File f : faction.listFiles(n -> n.getName().endsWith(".json"))) {
                ShipSpec spec = ShipSpec.fromJson(f);
                if (spec == null)
                    continue;
                assertNotNull(f.getName() + " names no class", spec.typeName);
                assertFalse(f.getName() + " has a blank class", spec.typeName.isBlank());
                checked++;
            }
        }
        assertTrue("the ship library should not be empty", checked > 100);
    }
}
