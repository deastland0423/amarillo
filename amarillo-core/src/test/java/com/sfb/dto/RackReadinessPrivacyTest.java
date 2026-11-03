package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.TurnTracker;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaRack;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A launcher looks ready to an opponent whether it is loaded or not.
 *
 * <h2>The ruling</h2>
 * The ship owner's, 2026-11-02: "your opponents can't tell when a rack is empty or busy
 * loading. To them it should look as if it were still ready to fire (barring the delay)."
 *
 * <h2>Why the DTO leaked it anyway</h2>
 * {@link GameStateDto} already withheld all the obvious things. {@code droneRacks} is sent as
 * an empty list to an enemy, so what is loaded never crosses the wire, and {@code addShots}
 * and {@code addReloads} are nulled with the comment "ammunition remaining, hidden like the
 * drones in a rack".
 *
 * <p>Readiness went out anyway, because it is computed rather than copied. Both
 * {@code readyToFire} and {@code shotsAvailableNow} run through {@code canFire()}, and for
 * these two weapons {@code canFire()} consults exactly the state being withheld:
 * {@code ADD.canFire()} is {@code shots > 0 && super.canFire()}, and
 * {@code DroneRack.canFire()} opens with {@code !reloadingThisTurn}. So the DTO declined to
 * say how many rounds were left and then published that the number was zero.
 *
 * <p>The reload half is the one that costs a game. FD2.421 takes the rack "out of service for
 * that turn" and the decision "cannot be reversed", so an opponent who read {@code
 * shotsAvailableNow == 0} off a rack on impulse 1 knew that launcher could not touch them for
 * the remaining thirty-one. The same reasoning is already written down one field over, where
 * {@code plasmaRackReloading} was ruled private: "an opponent who knew it was reloading would
 * know that rack cannot fire at them all turn — a free read on this turn's defence."
 *
 * <h2>What is NOT hidden</h2>
 * Everything an opponent watched happen. The box shot off the SSD, the shots already fired,
 * and the cooldown that follows them all stay, in both directions — a launcher concealing its
 * cooldown would be a lie the other way, and the owner's ruling says "barring the delay" for
 * exactly that reason.
 *
 * @see Weapon#apparentReadyToFireAtTarget()
 */
public class RackReadinessPrivacyTest {

    private Game game;
    private Ship klingon;      // a D5: two type-F drone racks and two ADD-12s
    private DroneRack typeG;   // added by hand; no sample hull carries one

    @Before
    public void setUp() {
        game = new Game();

        Player klingonPlayer = new Player();
        klingonPlayer.setTeamName("Klingon");
        Player fedPlayer = new Player();
        fedPlayer.setTeamName("Federation");

        klingon = new Ship();
        klingon.init(KlingonShips.getD5());
        klingon.setName("IKS Devastator");
        klingon.setLocation(new Location(10, 10));
        klingon.setFacing(1);
        klingon.setOwner(klingonPlayer);

        // A type-G, because it is the rack whose READINESS can go false from ammunition
        // alone: it is the only drone rack that can be fired at a target at all (FD3.70),
        // and canFireAntiDrone() asks addAmmo > 0.
        typeG = new DroneRack(DroneRack.DroneRackType.TYPE_G);
        typeG.setDesignator("G");
        klingon.getWeapons().addWeapon(typeG);

        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 14));
        fed.setFacing(13);
        fed.setOwner(fedPlayer);

        game.getShips().add(klingon);
        game.getShips().add(fed);
        klingon.attachClock(game.getClock());
        fed.attachClock(game.getClock());
    }

    // ---------------------------------------------------------------- helpers

    private GameStateDto.ShipDto ownerView() {
        return ship(new GameStateDto(game, "Klingon"));
    }

    private GameStateDto.ShipDto enemyView() {
        return ship(new GameStateDto(game, "Federation"));
    }

    private static GameStateDto.ShipDto ship(GameStateDto dto) {
        return (GameStateDto.ShipDto) dto.mapObjects.stream()
            .filter(o -> "IKS Devastator".equals(o.name))
            .findFirst().orElseThrow(() -> new AssertionError("ship missing from view"));
    }

    private static GameStateDto.WeaponDto weapon(GameStateDto.ShipDto dto, String name) {
        return dto.weapons.stream()
            .filter(w -> name.equals(w.name))
            .findFirst().orElseThrow(() -> new AssertionError("no weapon named " + name
                + " in " + dto.weapons.stream().map(w -> w.name).toList()));
    }

    private DroneRack firstTypeF() {
        for (Weapon w : klingon.getWeapons().fetchAllWeapons())
            if (w instanceof DroneRack rack && rack.getRackType() == DroneRack.DroneRackType.TYPE_F)
                return rack;
        throw new AssertionError("fixture: the D5 should carry type-F racks");
    }

    private ADD firstAdd() {
        for (Weapon w : klingon.getWeapons().fetchAllWeapons())
            if (w instanceof ADD add)
                return add;
        throw new AssertionError("fixture: the D5 should carry ADDs");
    }

    // ---------------------------------------------------------------- busy loading

    /**
     * The headline case, and note it is a type-F — NOT a type-G. The leak was never confined
     * to the rack that can shoot: {@code shotsAvailableNow} runs through {@code canFire()} for
     * every weapon there is, so a plotted reload zeroed the figure on an ordinary launcher too.
     */
    @Test
    public void anEnemyIsNotToldAPlainRackIsReloading() {
        DroneRack rack = firstTypeF();
        String name = rack.getName();
        int before = weapon(enemyView(), name).shotsAvailableNow;
        assertTrue("fixture: the rack should have a shot to offer before anything happens",
            before > 0);

        // FD2.421's plot. The drones in it do not matter here; the flag is what used to cross
        // the wire.
        rack.stagePendingReload(List.of());

        assertEquals("its owner must see the truth — the rack is out for the turn",
            0, weapon(ownerView(), name).shotsAvailableNow);
        assertEquals("an enemy sees what they saw before the plot was made",
            before, weapon(enemyView(), name).shotsAvailableNow);
    }

    /** And the same for the readiness flag on the one rack that can be fired at a target. */
    @Test
    public void anEnemyIsNotToldATypeGIsReloading() {
        typeG.setAddAmmo(4);
        String name = typeG.getName();
        assertTrue("fixture: a loaded type-G reads ready to its owner",
            weapon(ownerView(), name).readyToFire);

        typeG.stagePendingReload(List.of());

        assertFalse("its owner knows it cannot fire this turn",
            weapon(ownerView(), name).readyToFire);
        assertTrue("an enemy is told nothing changed",
            weapon(enemyView(), name).readyToFire);
    }

    // ---------------------------------------------------------------- empty

    /**
     * An empty magazine is the other half of the ruling, and the DTO's own comments already
     * committed to it: the round counts are withheld as "ammunition remaining". Publishing
     * readiness computed from them announced the withheld number.
     */
    @Test
    public void anEnemyIsNotToldATypeGIsOutOfRounds() {
        typeG.setAddAmmo(0);
        String name = typeG.getName();

        assertFalse("its owner must know the magazine is empty",
            weapon(ownerView(), name).readyToFire);
        assertTrue("an enemy is shown a rack that looks loaded",
            weapon(enemyView(), name).readyToFire);
    }

    /** The ADD, which fails the same way through {@code shots > 0}. */
    @Test
    public void anEnemyIsNotToldAnAddIsEmpty() throws Exception {
        ADD add = firstAdd();
        TurnTracker clock = new TurnTracker();
        add.setClock(clock);

        // An ADD has minImpulseGap 1 and no per-turn cap, so it empties one impulse at a time.
        int fired = 0;
        while (add.getShots() > 0) {
            clock.nextImpulse();
            add.fire(1);
            fired++;
        }
        assertEquals("fixture: an ADD-12 should have taken twelve shots to empty", 12, fired);
        clock.nextImpulse();   // clear the cooldown from the last one

        assertFalse("core still knows it is empty", add.canFire());
        assertEquals("and offers its owner no shots", 0, add.shotsAvailableNow());
        assertTrue("but an opponent is told it is ready", add.apparentReadyToFireAtTarget());
        assertTrue("with shots to offer", add.apparentShotsAvailableNow() > 0);
    }

    // ---------------------------------------------------------------- the plasma rack

    /** A fresh type-D rack on its own clock: loaded, and nothing done to it yet. */
    private static PlasmaRack plasmaRack(TurnTracker clock) {
        PlasmaRack rack = new PlasmaRack();
        rack.setClock(clock);
        rack.setDesignator("1");
        clock.nextImpulse();
        return rack;
    }

    /**
     * The clearest case of the owner's wider ruling (2026-11-02): "you don't have a view into
     * an opponent's arming status."
     *
     * <p>FP9.22 charges half an energy point to activate a torpedo and one "cannot be launched
     * without it", so an unactivated rack cannot fire — and that is arming, in the plainest
     * sense of the word. The ship's {@code plasmaActivationRoom} is already withheld as "an
     * arming state, and those are private"; readiness computed from the same thing was not.
     */
    @Test
    public void anEnemyIsNotToldAPlasmaRacksTorpedoesAreUnactivated() {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);

        assertFalse("fixture: nothing has been paid to activate anything",
            rack.getActiveTorpedoes() > 0);
        assertFalse("so its owner cannot fire it (FP9.22)", rack.readyToFireAtTarget());
        assertTrue("but an opponent is told nothing of the sort",
            rack.apparentReadyToFireAtTarget());
        assertTrue("nor through the shot count", rack.apparentShotsAvailableNow() > 0);
    }

    /** FP10.23's reload plot, which reaches the plasma rack through FD2.421. */
    @Test
    public void anEnemyIsNotToldAPlasmaRackIsReloading() {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);
        rack.activate(rack.activationEnergyWanted());
        assertTrue("fixture: activated and loaded, so it is ready to its owner",
            rack.readyToFireAtTarget());

        rack.setReloadingThisTurn(true);

        assertFalse("its owner knows it is out for the turn", rack.readyToFireAtTarget());
        assertTrue("an opponent is told nothing changed", rack.apparentReadyToFireAtTarget());
        assertTrue("nor through the shot count", rack.apparentShotsAvailableNow() > 0);
    }

    /** And an empty one, which is ammunition like any other. */
    @Test
    public void anEnemyIsNotToldAPlasmaRackIsEmpty() throws Exception {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);
        rack.activate(rack.activationEnergyWanted());

        int spent = 0;
        while (!rack.isEmpty()) {
            rack.launch(PlasmaRack.RackMode.DEFENSIVE);
            clock.nextImpulse();
            spent++;
        }
        assertEquals("fixture: a type-D rack holds four (FP10.0)", 4, spent);

        assertFalse("its owner sees an empty rack", rack.readyToFireAtTarget());
        assertTrue("an opponent sees a rack that looks loaded",
            rack.apparentReadyToFireAtTarget());
    }

    /**
     * The mode, which is the one that needed a decision rather than a rule.
     *
     * <p>A rack committed to OFFENSIVE mode has fired its one torpedo for the turn (FP10.211)
     * and is done until the next. An opponent cannot know that, because {@code plasmaRackMode}
     * is private under an existing ruling — the same seeking shot is legal in either mode — so
     * they can only assume the laxer DEFENSIVE reading, which allows another shot next impulse.
     */
    @Test
    public void anEnemyIsNotToldWhichModeAPlasmaRackCommittedTo() {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);
        rack.activate(rack.activationEnergyWanted());
        rack.launch(PlasmaRack.RackMode.OFFENSIVE);
        clock.nextImpulse();

        assertFalse("offensive mode fires once a turn, and its owner knows which mode it is in",
            rack.readyToFireAtTarget());
        assertTrue("an opponent, who cannot, must assume it may fire again",
            rack.apparentReadyToFireAtTarget());
    }

    /**
     * The limit, on the rack whose private clauses are longest: having fired THIS IMPULSE is
     * public, because the shot was seen. FP10.212 allows one per impulse even in the laxest
     * mode, so this is the floor below which an opponent learns nothing more.
     */
    @Test
    public void aPlasmaRackThatJustFiredReadsUnreadyToAnEnemy() {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);
        rack.activate(rack.activationEnergyWanted());
        rack.launch(PlasmaRack.RackMode.DEFENSIVE);

        assertFalse("it fired this impulse and everyone saw it",
            rack.apparentReadyToFireAtTarget());
        assertEquals("and it offers no shot", 0, rack.apparentShotsAvailableNow());
    }

    /** FP10.221's one bolt a turn, kept for the same reason: a bolt is direct fire. */
    @Test
    public void aPlasmaRackThatSpentItsBoltReadsUnreadyToAnEnemy() throws Exception {
        TurnTracker clock = new TurnTracker();
        PlasmaRack rack = plasmaRack(clock);
        rack.activate(rack.activationEnergyWanted());
        rack.declareBoltMode(PlasmaRack.RackMode.DEFENSIVE);
        rack.bolt(PlasmaRack.RackMode.DEFENSIVE, 3, 3);
        clock.nextImpulse();

        assertEquals("fixture: the turn's one bolt is spent", 1, rack.getBoltsThisTurn());
        assertFalse("which an opponent watched happen", rack.apparentReadyToFireAtTarget());
        // And the shot count is zero too, but for a reason that is equally public: a plasma
        // rack never raises maxShotsPerTurn off the default of one, so the bolt spent the
        // weapon's turn as far as the inherited rate cap is concerned. Both halves trace back
        // to the same seen shot, which is the property this file is about — not the figure.
        assertEquals("the inherited one-shot-a-turn cap, spent by a shot that was watched",
            0, rack.apparentShotsAvailableNow());
    }

    // ---------------------------------------------------------------- what stays visible

    /**
     * The limit the owner's ruling itself draws — "barring the delay". A launcher that has
     * just fired is plainly on a cooldown, and hiding that would be the same bug pointing the
     * other way.
     */
    @Test
    public void aCooldownIsStillVisibleToAnEnemy() throws Exception {
        ADD add = firstAdd();
        TurnTracker clock = new TurnTracker();
        add.setClock(clock);
        clock.nextImpulse();
        add.fire(1);

        assertFalse("fired this impulse, so it is not ready — and everyone saw the shot",
            add.apparentReadyToFireAtTarget());
        assertEquals("nor does it offer a shot", 0, add.apparentShotsAvailableNow());
    }

    /**
     * Damage is public whatever else is not. A rack shot off the SSD must read destroyed to
     * both sides, or the concealment would be hiding a kill the opponent earned.
     */
    @Test
    public void aDestroyedRackReadsDestroyedToAnEnemy() {
        typeG.setAddAmmo(4);
        String name = typeG.getName();
        assertTrue("fixture: ready before the hit", weapon(enemyView(), name).readyToFire);

        typeG.damage();

        GameStateDto.WeaponDto theirs = weapon(enemyView(), name);
        assertFalse("a destroyed rack is destroyed to everyone", theirs.functional);
        assertFalse("and a destroyed rack is not ready", theirs.readyToFire);
    }

    /**
     * The primary protection, asserted so it cannot quietly go away: the rack LIST itself
     * never reaches an enemy, which is what keeps the loaded drones, the spaces free and the
     * reload flag off the wire in the first place. The readiness work above exists only
     * because readiness is computed somewhere else.
     */
    @Test
    public void anEnemyGetsNoRackListAtAll() {
        typeG.setAddAmmo(4);

        assertFalse("fixture: its owner sees their racks", ownerView().droneRacks.isEmpty());
        assertTrue("an enemy sees none of them", enemyView().droneRacks.isEmpty());
    }

    /**
     * A plain type-F reads not-ready to BOTH sides, and that is correct rather than a leak: a
     * rack that is not a type-G cannot be fired at a target at all (FD3.70). That is what the
     * rack IS, it is printed on the SSD, and {@code rackType} is public already.
     */
    @Test
    public void aRackThatCannotShootReadsThatWayToBothSides() {
        String name = firstTypeF().getName();

        assertFalse("a type-F is a launcher, not a gun — to its owner",
            weapon(ownerView(), name).readyToFire);
        assertFalse("and to an enemy", weapon(enemyView(), name).readyToFire);
    }
}
