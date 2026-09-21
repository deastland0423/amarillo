package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.systemgroups.Energy;
import com.sfb.weapons.Photon;
import com.sfb.weapons.Weapon;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Photon arming discipline (E4.21/E4.22). The two arming turns must be consecutive, and a
 * torpedo that has completed arming must be paid a point every turn to stay in the tube. A tube
 * that is allocated nothing is discharged (E1.24) — so a half-armed photon cannot sit and wait
 * for a turn when its owner has energy to spare.
 */
public class PhotonArmingDisciplineTest {

    private Ship cruiser() {
        Ship s = new Ship();
        s.init(FederationShips.getFedCa());
        s.setName("USS Enterprise");
        s.setLocation(new Location(10, 10));
        return s;
    }

    private Photon firstPhoton(Ship ship) {
        for (Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof Photon)
                return (Photon) w;
        throw new IllegalStateException("FedCA has no photon");
    }

    /** An allocation that funds the ship but not the tube. */
    private Energy bareAllocation(Ship ship) {
        Energy e = new Energy();
        e.setLifeSupport(ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());
        return e;
    }

    @Test
    public void partiallyArmedPhoton_isDischargedWhenNothingIsAllocated() {
        Ship ship = cruiser();
        Photon p = firstPhoton(ship);
        p.armWithEnergy(2);                       // one turn in
        assertEquals(1, p.getArmingTurn());

        ship.allocateEnergy(bareAllocation(ship));
        ship.startTurn();   // the allocation is applied here

        assertEquals("arming begins again (E4.21)", 0, p.getArmingTurn());
        assertEquals(0.0, p.getArmingEnergy(), 0.001);
        assertFalse(p.isArmed());
    }

    /** E4.22: a loaded torpedo needs a point every turn or it is discharged. */
    @Test
    public void armedPhoton_isDischargedWhenNotPaidItsHoldingEnergy() {
        Ship ship = cruiser();
        Photon p = firstPhoton(ship);
        p.armWithEnergy(2);
        p.armWithEnergy(2);
        assertTrue(p.isArmed());

        ship.allocateEnergy(bareAllocation(ship));
        ship.startTurn();   // the allocation is applied here

        assertFalse("no holding energy — the tube is emptied (E4.22)", p.isArmed());
        assertEquals(0.0, p.getArmingEnergy(), 0.001);
    }

    /** An empty tube has nothing to lose and reports nothing. */
    @Test
    public void emptyTube_isUntouchedAndSilent() {
        Ship ship = cruiser();

        ship.allocateEnergy(bareAllocation(ship));
        ship.startTurn();   // the allocation is applied here

        assertTrue("nothing to report", ship.getAllocationNotes().isEmpty());
        assertEquals(0, firstPhoton(ship).getArmingTurn());
    }

    /** The owning player is told what lapsed — allocation is secret, so only they are. */
    @Test
    public void theAllocatingPlayerIsToldWhatLapsed() {
        Game game = new Game();
        Ship ship = cruiser();
        Photon p = firstPhoton(ship);
        p.armWithEnergy(2);
        game.getShips().add(ship);
        game.startTurn();

        assertTrue(game.submitAllocation(ship, bareAllocation(ship)).isSuccess());

        // The note is raised when the allocation is applied, at the start of the turn, and is
        // carried to the ship's own player in the state snapshot.
        String notes = String.join("; ", ship.getAllocationNotes());
        assertTrue("names the tube: " + notes, notes.contains(p.getName()));
        assertTrue("and says what happened: " + notes, notes.contains("arming must begin again"));
    }

    /** Paying the tube keeps it, of course — the lapse only fires when nothing is allocated. */
    @Test
    public void aFundedTubeIsNotDisturbed() {
        Ship ship = cruiser();
        Photon p = firstPhoton(ship);
        p.armWithEnergy(2);

        Energy e = bareAllocation(ship);
        e.getArmingEnergy().put(p, 2.0);
        e.getArmingType().put(p, com.sfb.properties.WeaponArmingType.STANDARD);
        ship.allocateEnergy(e);
        ship.startTurn();

        assertTrue("second consecutive turn completes it (E4.21)", p.isArmed());
        assertEquals(4.0, p.getArmingEnergy(), 0.001);
        assertTrue(ship.getAllocationNotes().isEmpty());
    }
}
