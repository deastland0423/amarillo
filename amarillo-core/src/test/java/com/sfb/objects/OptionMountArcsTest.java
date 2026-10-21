package com.sfb.objects;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.Weapon;

/**
 * R8.R3: what a weapon in an Orion option mount actually fires through.
 *
 * <h2>Why this is not the arc on the sheet</h2>
 * Every other hull prints a weapon's arc beside it, because the weapon never moves. An option
 * mount cannot: what sits in it is bought, so the sheet draws the typical case and R8.R3 states
 * the rule. Until this landed, `equip` handed the weapon the mount's printed arc — so a drone
 * rack in the CA's wing mount fired LS where the rule says FULL, and a Plasma-F fired LS where
 * the rule says LP. The CA's printed arcs happen to match the PHASER row, which is why nothing
 * looked wrong.
 */
public class OptionMountArcsTest {

    private OptionMountCatalog catalog;

    @Before
    public void setUp() {
        ShipLibrary.loadAllSpecs("../data/factions");
        catalog = OptionMountCatalog.loadDefault();
    }

    private Ship ca() {
        return ShipLibrary.createShip(ShipLibrary.get("Orion", "CA"));
    }

    /** The CA's mounts: A and B centerline, C the left wing, D the right. */
    private String arcOf(Ship ship, String designator, String option) {
        Weapon w = OptionMountLoadout.equip(ship, catalog, designator, option, 180);
        return w.getArcLabel();
    }

    // ------------------------------------------------------------------ centerline

    /** "All forward centerline option mounts are FA." */
    @Test
    public void aCenterlineWeaponBearsForward() {
        assertEquals("FA", arcOf(ca(), "A", "Disruptor-30"));
        assertEquals("FA", arcOf(ca(), "A", "Phaser-1"));
    }

    /** "...except drones and ADDs, which are FULL." A rack throws a seeker; it needs no arc. */
    @Test
    public void aCenterlineRackBearsFull() {
        assertEquals("FULL", arcOf(ca(), "A", "Drone Rack A"));
        assertEquals("FULL", arcOf(ca(), "A", "ADD (6 round)"));
    }

    /** "...(FP for swivel mount plasma torpedoes)" — the arc IS what the +1 buys. */
    @Test
    public void aSwivelPlasmaBearsTheWiderPlasmaArc() {
        assertEquals("FA", arcOf(ca(), "A", "Plasma-F Torp (No Swivel)"));
        assertEquals("FP", arcOf(ca(), "A", "Plasma-F Torp (Swivel)"));
    }

    // ------------------------------------------------------------------ wing

    /** THE BUG THIS FOUND. A drone rack in a wing mount is FULL, not the mount's printed LS. */
    @Test
    public void aWingRackBearsFullAndNotTheMountsPrintedArc() {
        Ship ship = ca();
        assertEquals("fixture: the CA prints LS beside its left wing mount",
                "[LS]", ship.getOptionMounts().get(2).getArcs().toString());

        assertEquals("FULL", arcOf(ship, "C", "Drone Rack A"));
    }

    /** "Phaser, Plasma-D: LS/RS" — the one row the printed arc already agreed with. */
    @Test
    public void aWingPhaserBearsItsOwnSide() {
        assertEquals("LS", arcOf(ca(), "C", "Phaser-1"));
        assertEquals("RS", arcOf(ca(), "D", "Phaser-1"));
    }

    /** "Plasma-F: LP/RP" — the plasma arc, which is wider than the side arc. */
    @Test
    public void aWingPlasmaFBearsThePlasmaArc() {
        assertEquals("LP", arcOf(ca(), "C", "Plasma-F Torp (No Swivel)"));
        assertEquals("RP", arcOf(ca(), "D", "Plasma-F Torp (No Swivel)"));
    }

    /** "Photon ... FA" even in a wing mount, where it is not the player's to choose. */
    @Test
    public void aWingPhotonBearsForward() {
        assertEquals("FA", arcOf(ca(), "C", "Photon Torpedo"));
    }

    // ------------------------------------------------------------------ the player's choice

    /**
     * R8.R3: a wing disruptor or fusion beam may be assigned EITHER arc, "depending on the taste
     * of the Orion player" — so a ship can carry one of each, which is the case that proves it
     * is a per-mount choice and not a per-ship setting.
     */
    @Test
    public void aWingDisruptorTakesTheArcThePlayerChose() {
        Ship ship = ca();

        Weapon forward = OptionMountLoadout.equip(ship, catalog, "C", "Disruptor-30", 180,
                OptionMountArcs.WingArc.FORWARD);
        Weapon outboard = OptionMountLoadout.equip(ship, catalog, "D", "Fusion Beam", 180,
                OptionMountArcs.WingArc.OUTBOARD);

        assertEquals("FA", forward.getArcLabel());
        assertEquals("the right wing's outboard arc is RF + R", "RF + R", outboard.getArcLabel());
    }

    /** The left wing's outboard arc is the mirror: L + LF. */
    @Test
    public void theOutboardArcMirrorsPerWing() {
        Weapon left = OptionMountLoadout.equip(ca(), catalog, "C", "Disruptor-30", 180,
                OptionMountArcs.WingArc.OUTBOARD);

        assertEquals("L + LF", left.getArcLabel());
    }

    /** Said nothing, got FA — the arc R8.R3 lists first and the rest of the battery bears. */
    @Test
    public void anUnstatedChoiceIsForward() {
        assertEquals("FA", arcOf(ca(), "C", "Disruptor-30"));
    }
}
