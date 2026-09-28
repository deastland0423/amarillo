package com.sfb.objects.shuttles;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * What a newly launched craft may not do yet (J1.34).
 * <p>
 * A shuttle leaves the bay with its systems still coming up, and the rule gives two different
 * waits measured from its MOST RECENT launch: a quarter turn before direct-fire weapons
 * (J1.342) and half a turn before seeking weapons (J1.341). The gap between them matters and
 * is easy to collapse — a fighter that may fire its phasers on impulse 13 still may not
 * release a drone until 21.
 * <p>
 * "Most recent" rather than "first": a fighter recovered and sent out again serves the wait
 * afresh, which is why this is an impulse stamp and not a flag.
 */
public class LaunchFireDelayTest {

    private static Aas launchedAt(int impulse) {
        Aas aas = new Aas();
        aas.setName("AAS-1");
        aas.setLaunchImpulse(impulse);
        return aas;
    }

    @Test
    public void directFireOpensAQuarterTurnAfterLaunch() {
        Aas aas = launchedAt(5);

        assertFalse("the impulse it launched on", aas.canFireDirect(5));
        assertFalse("seven impulses later is still short", aas.canFireDirect(12));
        assertTrue("eight impulses after launch (J1.342)", aas.canFireDirect(13));
    }

    @Test
    public void seekingWeaponsWaitTwiceAsLong() {
        Aas aas = launchedAt(5);

        assertFalse(aas.canLaunchSeeker(13));
        assertFalse("fifteen is still short", aas.canLaunchSeeker(20));
        assertTrue("sixteen impulses after launch (J1.341)", aas.canLaunchSeeker(21));
    }

    /**
     * The window between the two waits is the point of having both. Collapsing them — in
     * either direction — is the mistake this pins.
     */
    @Test
    public void thereIsAWindowWhenItMayShootButNotRelease() {
        Aas aas = launchedAt(5);

        for (int impulse = 13; impulse <= 20; impulse++) {
            assertTrue("phasers free at " + impulse, aas.canFireDirect(impulse));
            assertFalse("drones still shut at " + impulse, aas.canLaunchSeeker(impulse));
        }
    }

    @Test
    public void aSecondLaunchStartsTheWaitAgain() {
        Aas aas = launchedAt(5);
        assertTrue(aas.canFireDirect(13));

        aas.setLaunchImpulse(40);   // recovered and sent out again

        assertFalse("J1.34 measures from the MOST RECENT launch", aas.canFireDirect(44));
        assertTrue(aas.canFireDirect(48));
    }

    /**
     * ScatterPack keeps its own launchImpulse and overrides the accessor, so a check that
     * read the FIELD saw the parent's -999 and let every pack straight through. It reads
     * through the getter now.
     */
    @Test
    public void aScatterPackServesTheSameDirectFireWaitAsAnythingElse() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("Pack-1");
        pack.setLaunchImpulse(5);

        assertEquals("the override is what holds the value", 5, pack.getLaunchImpulse());
        assertFalse("a shadowed field made this true for every pack", pack.canFireDirect(6));
        assertTrue(pack.canFireDirect(13));
    }

    /**
     * FD7.33 gives a pack a quarter turn to release rather than J1.341's half, and
     * isReadyToRelease is what holds it to that — so canLaunchSeeker defers rather than
     * inventing a second answer.
     */
    @Test
    public void aScatterPackReleasesOnItsOwnQuarterTurn() {
        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("Pack-2");
        pack.setLaunchImpulse(5);

        assertFalse("not yet", pack.isReadyToRelease(12));
        assertTrue("eight impulses, not sixteen (FD7.33)", pack.isReadyToRelease(13));
    }

    /** A craft sitting in a bay is not serving a launch delay it has never begun. */
    @Test
    public void aCraftStillInItsBayIsNotWaitingOnAnything() {
        Aas aas = new Aas();

        assertTrue(aas.canFireDirect(1));
        assertTrue(aas.canLaunchSeeker(1));
    }

    // -------------------------------------------------------------------------
    // The rule at the place it is actually enforced
    // -------------------------------------------------------------------------

    /**
     * The hole this closed. A DroneRail reports itself DIRECT FIRE — it is fired through the
     * same path as a phaser — so the eight-impulse check let a fighter release drones from
     * impulse 13, eight impulses before J1.341 allows it. The seeker wait has to be applied
     * per WEAPON, not per attack.
     */
    @Test
    public void aFighterPastItsPhaserWaitStillCannotReleaseDrones() {
        com.sfb.Game game = new com.sfb.Game();
        game.startTurn();
        int now = game.getAbsoluteImpulse();

        com.sfb.objects.Ship enemy = new com.sfb.objects.Ship();
        enemy.init(com.sfb.samples.FederationShips.getFedCa());
        enemy.setName("USS Target");
        enemy.setLocation(new com.sfb.properties.Location(11, 10));
        enemy.setFacing(13);
        game.getShips().add(enemy);

        Aas aas = new Aas();
        aas.setName("AAS-1");
        aas.setLocation(new com.sfb.properties.Location(10, 10));
        aas.setFacing(1);
        // Exactly at the direct-fire boundary and half way to the seeker one.
        aas.setLaunchImpulse(now - Shuttle.DIRECT_FIRE_DELAY);
        assertTrue(aas.canFireDirect(now));
        assertFalse(aas.canLaunchSeeker(now));

        com.sfb.weapons.Weapon rail = null;
        com.sfb.weapons.Weapon phaser = null;
        for (com.sfb.weapons.Weapon w : aas.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.DroneRail && rail == null)
                rail = w;
            if (w instanceof com.sfb.weapons.PhaserWeapon && phaser == null)
                phaser = w;
        }
        assertNotNull("fixture needs a drone rail", rail);
        assertNotNull("fixture needs a phaser", phaser);

        String railResult = game.fireWeapons(aas, enemy, java.util.List.of(rail),
                1, 1, 1);
        assertTrue("a drone rail is a seeking weapon however it is fired: " + railResult,
                railResult.contains("J1.341"));

        String phaserResult = game.fireWeapons(aas, enemy, java.util.List.of(phaser),
                1, 1, 1);
        assertFalse("the phaser is past its own wait and must not be caught by J1.341: "
                + phaserResult, phaserResult.contains("J1.341"));
    }
}
