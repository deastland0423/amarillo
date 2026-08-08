package com.sfb.objects;

import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.properties.PersonnelType;
import com.sfb.properties.TerrainType;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The capacity-bounded manifest transfer primitive (the heart of cargo
 * pickup/dropoff/recovery) and the planet per-side holding it moves to/from.
 * Personnel sizing per J2.211: a crew unit is 2 spaces, a boarding party 1.
 */
public class ManifestTransferTest {

    @Test
    public void personnelSpaces_sizesPerJ2211() {
        Manifest m = new Manifest();
        m.addCrew(1);            // 2 spaces
        m.addBoardingParties(1); // 1 space
        assertEquals(3, m.personnelSpaces());
    }

    @Test
    public void transfer_limitedBySourceHoldings() {
        Manifest from = new Manifest();
        Manifest to   = new Manifest();
        from.addCrew(1);
        int moved = from.transferTo(to, PersonnelType.CREW_UNIT, 5, Integer.MAX_VALUE);
        assertEquals(1, moved);
        assertEquals(0, from.getCrew());
        assertEquals(1, to.getCrew());
    }

    @Test
    public void transfer_limitedByDestinationCapacity() {
        Manifest from = new Manifest();
        Manifest to   = new Manifest();
        from.addCrew(5);
        // 2 free spaces holds exactly one crew unit (2 spaces each).
        int moved = from.transferTo(to, PersonnelType.CREW_UNIT, 5, 2);
        assertEquals(1, moved);
        assertEquals(4, from.getCrew());
        assertEquals(1, to.getCrew());
    }

    @Test
    public void transfer_capacityTooSmallForACrewUnit_movesNothing() {
        Manifest from = new Manifest();
        from.addCrew(3);
        assertEquals(0, from.transferTo(new Manifest(), PersonnelType.CREW_UNIT, 3, 1));
        assertEquals(3, from.getCrew());
    }

    @Test
    public void transfer_boardingPartiesUseOneSpaceEach() {
        Manifest from = new Manifest();
        Manifest to   = new Manifest();
        from.addBoardingParties(5);
        assertEquals(2, from.transferTo(to, PersonnelType.BOARDING_PARTY, 5, 2));
        assertEquals(2, to.getBoardingParties());
    }

    @Test
    public void terrain_holdsPersonnelPerSideIndependently() {
        Terrain planet = new Terrain(TerrainType.PLANET, 5, 5);
        planet.getSideManifest(4).addCrew(1); // side D
        assertEquals(1, planet.getSideManifest(4).getCrew());
        assertEquals("side C untouched", 0, planet.getSideManifest(3).getCrew());
        assertNull(planet.getSideManifest(0));
        assertNull(planet.getSideManifest(7));
    }

    @Test
    public void integration_planetSideToShuttleHold_boundedByShuttleCapacity() {
        AdminShuttle shuttle = new AdminShuttle();      // personnel capacity 2
        Terrain planet = new Terrain(TerrainType.PLANET, 5, 5);
        planet.getSideManifest(4).addCrew(3);           // three survey teams on side D

        int moved = planet.getSideManifest(4).transferTo(
                shuttle.getHold(), PersonnelType.CREW_UNIT, 3, shuttle.personnelSpacesFree());

        assertEquals("one crew unit fills the admin shuttle (J2.211)", 1, moved);
        assertEquals(1, shuttle.getHold().getCrew());
        assertEquals(0, shuttle.personnelSpacesFree());
        assertEquals("two teams left on the surface", 2, planet.getSideManifest(4).getCrew());
    }
}
