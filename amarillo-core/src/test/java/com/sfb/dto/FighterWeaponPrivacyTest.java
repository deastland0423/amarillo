package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.TurnTracker;
import com.sfb.objects.Ship;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.FighterDisruptor;
import com.sfb.weapons.FighterFusion;
import com.sfb.weapons.FighterPhoton;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A fighter's charges are its arming state, and an opponent does not see arming.
 *
 * <h2>The builder with no viewer</h2>
 * {@code fromShip} redacts an enemy's copy in two places — the weapon loop and
 * {@code redactForEnemy}. A SHUTTLE goes through {@code buildWeaponDtos}, which until now was
 * handed a weapons group and nothing else, so it could not tell owner from opponent and did not
 * try. Every field it set went to everyone.
 *
 * <p>That mattered most for {@code chargesRemaining}. All three of a fighter's heavy weapons
 * gate firing on it — {@code FighterPhoton}, {@code FighterDisruptor} and {@code FighterFusion}
 * are each {@code chargesRemaining > 0 && super.canFire()} — and for a fighter the charges ARE
 * the arming: a craft is built empty and armed from its own box by J4.83 rearming in the bay.
 * So an opponent could read the count straight off the wire, and read {@code readyToFire} and
 * {@code shotsAvailableNow} computed from it as well. A Stinger could be watched running dry.
 *
 * <h2>The rulings this was already against</h2>
 * {@code fromShuttle}'s own javadoc: "What a shuttle carries is not public: G4.233 gives up
 * whether it is manned and whether it is seeking, and stops there." And
 * {@link MapObjectPrivacyTest} has ruled {@code rails} private since it was written, with the
 * reason spelled out — "What a fighter still has on its rails is never on offer." A fighter's
 * remaining drones were therefore secret while its remaining fusion charges were published.
 *
 * <p>The owner's ruling, 2026-11-02: "You know when a weapon might be available to fire based
 * on its last firing impulse. But other than that, you don't have a view into an opponent's
 * arming status."
 *
 * @see com.sfb.weapons.Weapon#apparentReadyToFireAtTarget()
 */
public class FighterWeaponPrivacyTest {

    private Game game;
    private Shuttle stinger;

    @Before
    public void setUp() throws Exception {
        ShuttleCatalog.loadDefault("../data");
        game = new Game();

        Player hydran = new Player();
        hydran.setTeamName("Hydran");
        Player federation = new Player();
        federation.setTeamName("Federation");

        Ship observer = new Ship();
        observer.init(FederationShips.getFedCa());
        observer.setName("USS Enterprise");
        observer.setLocation(new Location(10, 14));
        observer.setOwner(federation);
        game.getShips().add(observer);

        // A Stinger-1 carries a phaser-3 and a fusion, which is the weapon this is about.
        stinger = CataloguedFighter.of("stinger1");
        stinger.setName("Stinger-1");
        stinger.setOwner(hydran);
        stinger.setParentShipName("HMS Fury");
        stinger.setLocation(new Location(10, 10));
        game.getActiveShuttles().add(stinger);
    }

    // ---------------------------------------------------------------- helpers

    private GameStateDto.ShuttleDto ownerView() {
        return craft(new GameStateDto(game, "Hydran"));
    }

    private GameStateDto.ShuttleDto enemyView() {
        return craft(new GameStateDto(game, "Federation"));
    }

    private static GameStateDto.ShuttleDto craft(GameStateDto dto) {
        return (GameStateDto.ShuttleDto) dto.mapObjects.stream()
            .filter(o -> "Stinger-1".equals(o.name))
            .findFirst().orElseThrow(() -> new AssertionError("the fighter is not on the map"));
    }

    private FighterFusion fusion() {
        for (Weapon w : stinger.getWeapons().fetchAllWeapons())
            if (w instanceof FighterFusion ff)
                return ff;
        throw new AssertionError("fixture: a Stinger-1 should carry a fusion");
    }

    private static GameStateDto.WeaponDto fusionDto(GameStateDto.ShuttleDto dto) {
        return dto.weapons.stream()
            .filter(w -> w.chargesRemaining != null || "FighterFusion".equals(w.type))
            .findFirst().orElseThrow(() -> new AssertionError("no fusion in " + dto.weapons));
    }

    /** Arm the fusion the way a deck crew would (J4.83), so there is something to hide. */
    private FighterFusion armedFusion() {
        FighterFusion ff = fusion();
        while (ff.loadCharge()) { /* to full */ }
        assertTrue("fixture: the fusion should now hold charges", ff.getChargesRemaining() > 0);
        return ff;
    }

    // ---------------------------------------------------------------- the count

    /**
     * The count itself, and NULL rather than 0 — "not disclosed" instead of the plausible lie
     * "none left". The Fire Orders pad also tells a fusion from a phaser by whether the field
     * is absent, so a 0 here would put the 1x/2x buttons on every weapon an enemy owns.
     */
    @Test
    public void anEnemyIsNotToldHowManyChargesAFusionHolds() {
        armedFusion();

        assertNotNull("its owner must see the count to fire it",
            fusionDto(ownerView()).chargesRemaining);
        assertNull("an opponent is told nothing", fusionDto(enemyView()).chargesRemaining);
    }

    /** And the readiness computed from it, which is the half that survived the first fix. */
    @Test
    public void anEnemyIsNotToldAFightersFusionIsEmpty() {
        FighterFusion ff = fusion();
        assertEquals("fixture: a fighter is built empty and armed from its box (J4.83)",
            0, ff.getChargesRemaining());

        assertFalse("its owner knows there is nothing to fire",
            fusionDto(ownerView()).readyToFire);
        assertTrue("an opponent is shown an armed fighter", fusionDto(enemyView()).readyToFire);
        assertTrue("with a shot to offer", fusionDto(enemyView()).shotsAvailableNow > 0);
    }

    /**
     * {@code canFireDouble} leaks "two or more charges" on its own, and it is a primitive
     * boolean on the wire so it cannot say "not disclosed". It answers as the rest do — as
     * though charged — because publishing false would trade the leak for a lie.
     */
    @Test
    public void anEnemyIsNotToldAFighterCannotDoubleFire() {
        FighterFusion ff = fusion();
        assertFalse("fixture: an empty fusion cannot double-fire", ff.canFireDouble());

        assertFalse("its owner is told the truth", fusionDto(ownerView()).canFireDouble);
        assertTrue("an opponent is told it could", fusionDto(enemyView()).canFireDouble);
    }

    // ---------------------------------------------------------------- what stays visible

    /**
     * The limit the ruling itself draws. A fighter that has just fired is on a cooldown
     * everyone watched it earn, and hiding that would be over-redaction.
     */
    @Test
    public void aFighterThatJustFiredReadsUnreadyToAnEnemy() throws Exception {
        FighterFusion ff = armedFusion();
        TurnTracker clock = new TurnTracker();
        ff.setClock(clock);
        clock.nextImpulse();
        ff.fire(1);

        assertFalse("it fired this impulse and the shot was seen",
            ff.apparentReadyToFireAtTarget());
        assertEquals("so it offers nothing", 0, ff.apparentShotsAvailableNow());
    }

    /** Damage is public whatever else is not. */
    @Test
    public void aDestroyedFighterWeaponReadsDestroyedToAnEnemy() {
        armedFusion();
        fusion().damage();

        GameStateDto.WeaponDto theirs = fusionDto(enemyView());
        assertFalse("a destroyed weapon is destroyed to everyone", theirs.functional);
        assertFalse("and a destroyed weapon is not ready", theirs.readyToFire);
    }

    // ---------------------------------------------------------------- the other two weapons

    /**
     * The photon and the disruptor, which no Stinger carries but which fail the same way. Both
     * gate on {@code chargesRemaining > 0}, so both are asserted at the weapon rather than
     * through a hull chosen to carry one.
     */
    @Test
    public void aFighterPhotonAndDisruptorHideTheirChargesToo() {
        FighterPhoton photon = new FighterPhoton();
        FighterDisruptor disruptor = new FighterDisruptor();

        assertFalse("an unarmed fighter photon cannot fire", photon.canFire());
        assertTrue("but an opponent is told it can", photon.apparentReadyToFireAtTarget());
        assertTrue("and is offered a shot", photon.apparentShotsAvailableNow() > 0);

        assertFalse("nor can an unarmed fighter disruptor", disruptor.canFire());
        assertTrue("same answer", disruptor.apparentReadyToFireAtTarget());
        assertTrue("and the same shot", disruptor.apparentShotsAvailableNow() > 0);
    }
}
