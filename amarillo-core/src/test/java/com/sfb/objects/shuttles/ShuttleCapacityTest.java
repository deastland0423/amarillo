package com.sfb.objects.shuttles;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Shuttle personnel-hold capacity (J2.211): a standard administrative shuttle
 * carries one crew unit OR two boarding parties — modelled as a 2-space hold
 * where a crew unit costs 2 spaces and a boarding party/commando costs 1.
 */
public class ShuttleCapacityTest {

    @Test
    public void adminShuttle_hasTwoPersonnelSpaces() {
        AdminShuttle s = new AdminShuttle();
        assertEquals(2, s.getPersonnelCapacity());
        assertEquals(15, s.getCargoCapacity()); // scaffolded per G25.131
        assertEquals(0, s.personnelSpacesUsed());
        assertEquals(2, s.personnelSpacesFree());
    }

    @Test
    public void oneCrewUnitFillsAStandardAdminShuttle() {
        AdminShuttle s = new AdminShuttle();
        s.getHold().addCrew(1);
        assertEquals("a crew unit is 2 spaces", 2, s.personnelSpacesUsed());
        assertEquals(0, s.personnelSpacesFree());
    }

    @Test
    public void twoBoardingPartiesAlsoFillIt() {
        AdminShuttle s = new AdminShuttle();
        s.getHold().addBoardingParties(2);
        assertEquals("two boarding parties are 2 spaces (J2.211)", 2, s.personnelSpacesUsed());
        assertEquals(0, s.personnelSpacesFree());
    }

    @Test
    public void mixedLoad_countsSpacesCorrectly() {
        AdminShuttle s = new AdminShuttle();
        s.getHold().addBoardingParties(1);
        s.getHold().addCommandos(1);
        assertEquals("1 BP + 1 commando = 2 spaces", 2, s.personnelSpacesUsed());
        assertEquals(0, s.personnelSpacesFree());
    }
}
