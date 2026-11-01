package com.sfb;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import com.sfb.systemgroups.Energy;

/**
 * J3.221: a voided wild weasel is a shuttle, not a hole in the map.
 *
 * <blockquote>"A voided WW is <b>not destroyed</b> merely as a result of this voiding. In such
 * case, the shuttle continues to move as per its original instructions (J3.111) until it is
 * destroyed or recovered by any means. The shuttle can be recovered by tractor (J1.62), or a
 * pilot could be transported aboard; see (J1.86)."</blockquote>
 *
 * <p>It used to be deleted, which read as the same thing from most angles and is not. A voided
 * weasel is a live craft that has stopped being a decoy, and three different rules want it
 * still there: J3.22's cross-reference calls firing on one a way "to prevent its capture",
 * J3.222 makes it worth ten victory points to an enemy that recovers it, and J3.203 leaves
 * weapons aimed at it in its own right with something to keep chasing.
 *
 * <p>J1.534's bar on holding a weasel — any weasel — on the balcony is not re-tested
 * here. Recovery asks the same {@code barredFromBalcony} the bay transfer asks, and
 * {@code BalconyRestrictionsTest} already proves it from both directions; voiding changes
 * nothing about it, since J1.534 draws no such distinction.
 *
 * <h2>Destroyed is the other case</h2>
 * A weasel killed by a seeking weapon or direct fire is NOT voided by that (J3.21); it explodes
 * for four impulses and then sits as a spent radiation pocket until nothing is chasing it any
 * more (J3.212). That lifecycle is unchanged, and {@link #aDestroyedWeaselStillLeavesWhenVoided}
 * pins the join between the two.
 */
public class VoidedWeaselTest {

    private Game game;
    private Ship fed;
    private Ship klingon;

    @Before
    public void setUp() {
        game = new Game();
        buildShips();
        game.startTurn();
        for (Ship s : game.getShips()) {
            Energy e = new Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            e.setPhaserCapacitor(6.0);      // or every shot reports "no capacitor energy"
            game.submitAllocation(s, e);
        }
        for (int guard = 0; guard < 400 && !game.canLaunchThisPhase(); guard++)
            game.advancePhase();
        assertTrue("fixture: a phase that allows launching", game.canLaunchThisPhase());
    }

    /**
     * The two hulls, with owners: J1.6214 lets only a FRIENDLY craft use the special recovery
     * procedure, and it asks the team rather than the faction.
     */
    private void buildShips() {
        com.sfb.Player fedPlayer = new com.sfb.Player();
        fedPlayer.setName("Knosset");
        fedPlayer.setTeamName("Star Fleet");

        com.sfb.Player klinPlayer = new com.sfb.Player();
        klinPlayer.setName("Aardak");
        klinPlayer.setTeamName("Empire");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(fedPlayer);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 16));
        klingon.setFacing(1);
        klingon.setOwner(klinPlayer);

        game.getShips().add(fed);
        game.getShips().add(klingon);
    }

    /** J3.11: two impulses of deck work, then it is a weasel. */
    private WildWeaselShuttle putUpWeasel() {
        Shuttle sh = fed.getShuttles().getBays().get(0).launchableInventory().get(0);
        sh.incrementWwCharge();
        sh.incrementWwCharge();
        Game.ActionResult r = game.launchWildWeasel(fed, sh.getName(), 1, 3);
        assertTrue(r.getMessage(), r.isSuccess());
        WildWeaselShuttle ww = fed.getActiveWildWeasel();
        assertNotNull("fixture: the decoy is up", ww);
        return ww;
    }

    private boolean onMap(Shuttle craft) {
        return game.getActiveShuttles().contains(craft);
    }

    // ------------------------------------------------------------------- it does not leave

    /** The whole of J3.221's first sentence: voiding is not destruction. */
    @Test
    public void aVoidedWeaselStaysOnTheMap() {
        WildWeaselShuttle ww = putUpWeasel();
        Location where = ww.getLocation();

        game.voidWildWeasel(fed);

        assertTrue("J3.221: not destroyed, so still a craft in flight", onMap(ww));
        assertTrue(ww.isVoided());
        assertEquals("and it has not been moved by the voiding", where, ww.getLocation());
    }

    /** But it has stopped being a decoy, which is what "cease to function" means (J3.22). */
    @Test
    public void aVoidedWeaselProtectsNothing() {
        putUpWeasel();
        assertTrue("fixture: protected", fed.hasActiveWildWeasel());
        assertEquals("fixture: J3.23's six points", 6, fed.getWwEcmBonus());

        game.voidWildWeasel(fed);

        assertFalse("J3.22: it protects the ship no longer", fed.hasActiveWildWeasel());
        assertEquals("J3.23: and the free ECM goes with it", 0, fed.getWwEcmBonus());
    }

    /**
     * J3.221: "the shuttle continues to move as per its original instructions (J3.111)". It was
     * launched on a course at speed three and it holds both.
     */
    @Test
    public void aVoidedWeaselKeepsFlyingItsCourse() {
        WildWeaselShuttle ww = putUpWeasel();
        int facing = ww.getFacing();
        Location start = ww.getLocation();
        game.voidWildWeasel(fed);

        for (int i = 0; i < 40; i++)
            game.advancePhase();

        assertTrue("still out there", onMap(ww));
        assertNotEquals("J3.111: and still running its pre-set course", start, ww.getLocation());
        assertEquals("which it cannot turn off (J3.11)", facing, ww.getFacing());
    }

    /**
     * J3.42 voids a weasel more than 35 hexes from its ship. Once voided there is nothing left
     * to void, and the check has to know that — it runs every impulse, and the craft it is
     * looking at is still there now.
     */
    @Test
    public void theRangeCheckDoesNotKeepVoidingTheSameWeasel() {
        WildWeaselShuttle ww = putUpWeasel();
        game.voidWildWeasel(fed);
        ww.setLocation(new Location(10, 60));       // far outside J3.42's 35 hexes

        int voidings = 0;
        for (int i = 0; i < 40; i++) {
            game.advancePhase();
            for (String line : game.getLastSeekerLog())
                if (line.contains(ww.getName()) && line.contains("voided"))
                    voidings++;
        }
        assertEquals("J3.42 has nothing left to say about it", 0, voidings);
    }

    // --------------------------------------------------------------- and it can be had back

    /**
     * J3.221 names the way home: "The shuttle can be recovered by tractor (J1.62)". J1.611 says
     * the same from the other side — a weasel cannot land under its own power, but "they can be
     * landed by tractor beam (J1.62)".
     */
    @Test
    public void aVoidedWeaselCanBeRecoveredByTractor() {
        WildWeaselShuttle ww = putUpWeasel();
        game.voidWildWeasel(fed);
        ww.setLocation(fed.getLocation());
        assertTrue("fixture: in the tractor beam",
                fed.getTractors().linkUnit(ww, game.getAbsoluteImpulse()));

        Game.ActionResult r = game.beginShuttleRecovery(fed, ww.getName());

        assertTrue(r.getMessage(), r.isSuccess());
    }

    /** And an ACTIVE one cannot: J3.25's pull-in is a different outcome, and is not built. */
    @Test
    public void anActiveWeaselCannotBePulledIn() {
        WildWeaselShuttle ww = putUpWeasel();
        ww.setLocation(fed.getLocation());
        assertTrue("fixture: in the tractor beam",
                fed.getTractors().linkUnit(ww, game.getAbsoluteImpulse()));

        Game.ActionResult r = game.beginShuttleRecovery(fed, ww.getName());

        assertFalse(r.isSuccess());
        assertTrue(r.getMessage(), r.getMessage().contains("J3.25"));
    }

    // ---------------------------------------------------- nothing left to detonate a weapon

    /**
     * J3.30 explains what the explosion period is made of: seeking weapons reaching a weasel
     * "do not actually hit it but are <b>detonated by the electronic field</b>". A voided
     * weasel has ceased to function (J3.22), so there is no field, and a drone that reaches it
     * simply hits a shuttle.
     */
    @Test
    public void aSeekerReachingAVoidedWeaselDoesNotSetOffAnExplosionPeriod() {
        WildWeaselShuttle ww = putUpWeasel();
        game.voidWildWeasel(fed);

        com.sfb.objects.Drone d = new com.sfb.objects.Drone(com.sfb.objects.DroneType.TypeI);
        d.setName("Drone-1");
        d.setSeekerType(com.sfb.objects.Seeker.SeekerType.DRONE);
        d.setLocation(ww.getLocation());
        d.setTarget(ww);
        game.getSeekers().add(d);

        for (int i = 0; i < 80; i++)
            game.advancePhase();

        assertFalse("J3.22: no electronic field, so no explosion period", ww.isExploding());
        assertFalse(ww.isPostExplosion());
        assertFalse("it was hit as the shuttle it now is", onMap(ww));
    }

    /**
     * And the same under direct fire, which is the case J3.22's own cross-reference is about:
     * a voided weasel "may be fired on to prevent its capture". Firing on one must kill it,
     * not hand the shooter a four-impulse decoy that pulls seekers in.
     */
    @Test
    public void directFireOnAVoidedWeaselKillsItOutright() {
        WildWeaselShuttle ww = putUpWeasel();
        game.voidWildWeasel(fed);
        ww.setLocation(new Location(10, 15));       // one hex off the Klingon's bow
        ww.setCurrentHull(1);                       // so no die roll decides the outcome

        java.util.List<com.sfb.weapons.Weapon> gun = klingon.getWeapons().fetchAllWeapons()
                .stream()
                .filter(w -> w instanceof com.sfb.weapons.PhaserWeapon)
                .limit(1)
                .collect(java.util.stream.Collectors.toList());
        assertEquals("fixture: one phaser", 1, gun.size());

        String log = game.fireWeapons(klingon, ww, gun, 1, 1, 0);

        assertFalse(log, ww.isExploding());
        assertFalse("J3.22: it dies as a shuttle", onMap(ww));
    }

    // ------------------------------------------------------------- the destroyed one differs

    /**
     * The join between the two lifecycles. A weasel that has been destroyed is already on its
     * way out — J3.212 keeps its counter only "until all seeking weapons targeted on the WW
     * have reached the explosion hex, accepted other targets, or reached the limit of their
     * endurance" — so voiding one, which sends the last of them home, takes the counter with
     * it. Only an undestroyed weasel stays.
     */
    @Test
    public void aDestroyedWeaselStillLeavesWhenVoided() {
        WildWeaselShuttle ww = putUpWeasel();
        ww.startExplosion(game.getAbsoluteImpulse());
        assertTrue("fixture: destroyed, not voided (J3.21)", ww.isExploding());

        game.voidWildWeasel(fed);

        assertFalse("J3.212: nothing left to hold the counter here", onMap(ww));
    }

    // ------------------------------------------------------------------------- the save

    /**
     * The trap in the save: a weasel is rebuilt from its parent's name and handed back to the
     * ship as its active decoy. A voided one must come back as a craft and nothing more, or the
     * load returns six points of ECM (J3.23) the ship had already lost.
     */
    @Test
    public void aLoadedVoidedWeaselIsNotHandedBackToItsShip() {
        putUpWeasel();
        game.voidWildWeasel(fed);
        assertFalse("fixture: not protected any more", fed.hasActiveWildWeasel());

        java.util.Map<String, Object> saved = game.exportState();

        // A load overlays a save onto ships that already exist -- a seeker or a craft in
        // flight is CREATED by the load, a ship is not (see Seeker.exportState).
        Game reloaded = new Game();
        VoidedWeaselTest fresh = new VoidedWeaselTest();
        fresh.game = reloaded;
        fresh.buildShips();
        reloaded.applyState(saved);

        Ship restoredFed = reloaded.getShips().stream()
                .filter(s -> "USS Enterprise".equals(s.getName()))
                .findFirst().orElseThrow(() -> new AssertionError("the Enterprise came back"));
        assertFalse("J3.221: a voided weasel is not an active decoy",
                restoredFed.hasActiveWildWeasel());
        assertEquals(0, restoredFed.getWwEcmBonus());

        WildWeaselShuttle restoredWw = reloaded.getActiveShuttles().stream()
                .filter(c -> c instanceof WildWeaselShuttle)
                .map(c -> (WildWeaselShuttle) c)
                .findFirst().orElseThrow(() -> new AssertionError("the weasel came back"));
        assertTrue("and it is still out there, voided", restoredWw.isVoided());
    }
}
