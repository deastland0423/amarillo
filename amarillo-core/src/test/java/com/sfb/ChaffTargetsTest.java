package com.sfb;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.objects.ShuttleCatalog;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;

/**
 * WHICH seekers chaff can distract (D11.32), which is a shorter list than every seeker.
 * <blockquote>
 * "When a fighter (or other shuttle) uses a chaff pack, the owning player rolls a single die.
 * If the die roll is a 1-4, <b>all drones (including dogfight drones and seeking shuttles) and
 * type-D plasma torpedoes (FP9.18) and type-K plasma torpedoes (FP13.51)</b> targeted on that
 * fighter lose their tracking and become inert (FD1.7) or are otherwise treated as if their
 * target had disappeared."
 * </blockquote>
 * FP9.18 says why the small torpedoes are on that list at all, and how narrow the exception is:
 * "Type-D torpedoes, having relatively unsophisticated warheads, can be distracted by chaff
 * (D11.0). <b>No other plasma torpedoes can be distracted by chaff. This is the only way (in
 * combat) that a Pl-D is like a drone.</b>"
 * <p>
 * Until this test existed, {@code dropChaff} removed EVERY seeker tracking the fighter, so one
 * pack could shrug off a plasma-G, -S, -R or -F. It never showed up in play only because the
 * chaff rule is old and plasma-armed fighters are new - the first Romulan Gladiator arrived
 * days ago.
 *
 * <h2>Two simplifications this does NOT fix</h2>
 * D11.32 has the distracted seekers "become inert (FD1.7)"; we remove them from play, as every
 * other loss-of-tracking path does (G24.223's scout lock-break included). And its EXCEPTION -
 * scatter packs and multi-warhead drones pursuing the chaff's HEX and releasing submunitions
 * there - needs seeker hex targeting, which is a separate open item.
 */
public class ChaffTargetsTest {

    private Game game;
    private Ship enemy;
    private Player romulan;
    private Player federation;

    @Before
    public void setUp() throws Exception {
        ShipLibrary.loadAllSpecs("../data/factions");
        ShuttleCatalog.loadDefault("../data");
        game = new Game();

        romulan = new Player();
        romulan.setTeamName("Romulan");
        federation = new Player();
        federation.setTeamName("Federation");

        enemy = ShipLibrary.createShip(ShipLibrary.get("Romulan", "KRV"));
        enemy.setName("RIS Firehawk");
        enemy.setLocation(new Location(10, 14));
        enemy.setFacing(13);
        enemy.setOwner(romulan);
        game.getShips().add(enemy);
    }

    /** A Federation fighter in space with a chaff pack, and the seekers chasing it. */
    private Fighter fighterUnderFire() {
        Fighter f18 = CataloguedFighter.of("f18");
        f18.setName("F18-1");
        f18.setOwner(federation);
        f18.setLocation(new Location(10, 10));
        f18.setFacing(1);
        game.getActiveShuttles().add(f18);
        // Set explicitly because NO fighter type in the catalogue declares chaffPacks, so
        // every one is built with none and dropChaff always refuses. D11.1 says "While all
        // fighters carry chaff", so that is a data gap rather than a rule - the machinery
        // from the catalogue down to Shuttle is already there and waiting for a number.
        f18.setChaffPacks(1);
        return f18;
    }

    private Drone drone(String name, Fighter at) {
        Drone d = new Drone();
        d.setDroneType(DroneType.TypeI);
        d.setName(name);
        d.setSeekerType(Seeker.SeekerType.DRONE);
        d.setTarget(at);
        d.setLocation(enemy.getLocation());
        game.getSeekers().add(d);
        return d;
    }

    private PlasmaTorpedo plasma(PlasmaType type, String name, Fighter at) {
        PlasmaTorpedo t = new PlasmaTorpedo(type, WeaponArmingType.STANDARD);
        t.setName(name);
        t.setSeekerType(Seeker.SeekerType.PLASMA);
        t.setTarget(at);
        t.setLocation(enemy.getLocation());
        game.getSeekers().add(t);
        return t;
    }

    private void toActivity() {
        for (int i = 0; i < 400; i++) {
            if (game.getCurrentPhase() == Game.ImpulsePhase.ACTIVITY)
                return;
            game.advancePhase();
        }
        fail("never reached an Activity phase");
    }

    private void readyToDropChaff() {
        game.startTurn();
        for (Ship s : game.getShips()) {
            com.sfb.systemgroups.Energy e = new com.sfb.systemgroups.Energy();
            e.setLifeSupport(s.getLifeSupportCost());
            e.setFireControl(s.getFireControlCost());
            game.submitAllocation(s, e);
        }
        toActivity();
    }

    /**
     * Drop chaff until the die cooperates. D11.32 only distracts on a 1-4, so a 5 or 6 means
     * "no effect" and the whole fixture has to be rebuilt - the pack is spent and D11.41's
     * lockout has started.
     *
     * @return the result of a drop that actually distracted something
     */
    private Game.ActionResult chaffUntilItTakes(java.util.function.Supplier<Fighter> setUp) {
        for (int attempt = 0; attempt < 60; attempt++) {
            setUp.get();
            readyToDropChaff();
            Fighter f = (Fighter) game.getActiveShuttles().get(game.getActiveShuttles().size() - 1);
            Game.ActionResult r = game.dropChaff(f);
            assertTrue(r.getMessage(), r.isSuccess());
            if (!r.getMessage().contains("no effect"))
                return r;
            // 5 or 6: start over with a fresh fighter and fresh seekers
            game = new Game();
            try {
                setUpAgain();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("60 chaff drops never rolled 1-4");
    }

    private void setUpAgain() throws Exception {
        enemy = ShipLibrary.createShip(ShipLibrary.get("Romulan", "KRV"));
        enemy.setName("RIS Firehawk");
        enemy.setLocation(new Location(10, 14));
        enemy.setFacing(13);
        enemy.setOwner(romulan);
        game.getShips().add(enemy);
    }

    // ---------------------------------------------------------------- the list

    /**
     * The heart of it: a drone and a type-D go, a plasma-G stays. One drop, three seekers, so
     * the inclusion and the exclusion are proven by the same roll rather than separately.
     */
    @Test
    public void chaffTakesTheDroneAndTheTypeDButNotThePlasmaG() {
        final Drone[] theDrone = new Drone[1];
        final PlasmaTorpedo[] small = new PlasmaTorpedo[1];
        final PlasmaTorpedo[] heavy = new PlasmaTorpedo[1];

        chaffUntilItTakes(() -> {
            Fighter f = fighterUnderFire();
            theDrone[0] = drone("Drone-1", f);
            small[0] = plasma(PlasmaType.D, "PlD-1", f);
            heavy[0] = plasma(PlasmaType.G, "PlG-1", f);
            return f;
        });

        assertFalse("a drone is distracted (D11.32)",
                game.getSeekers().contains(theDrone[0]));
        assertFalse("and a type-D (FP9.18)", game.getSeekers().contains(small[0]));
        assertTrue("but a plasma-G is NOT — \"No other plasma torpedoes can be distracted"
                + " by chaff\" (FP9.18)", game.getSeekers().contains(heavy[0]));
    }

    /** D11.32 names the type-K alongside the type-D (FP13.51). */
    @Test
    public void chaffTakesATypeKToo() {
        final PlasmaTorpedo[] k = new PlasmaTorpedo[1];

        chaffUntilItTakes(() -> {
            Fighter f = fighterUnderFire();
            k[0] = plasma(PlasmaType.K, "PlK-1", f);
            drone("Drone-1", f);        // so the drop always has something to report
            return f;
        });

        assertFalse("a type-K is on D11.32's list", game.getSeekers().contains(k[0]));
    }

    /** And the heavy torpedoes, each by name, since the rule excludes them as a class. */
    @Test
    public void noHeavyPlasmaIsEverDistracted() {
        final java.util.List<PlasmaTorpedo> heavies = new java.util.ArrayList<>();

        chaffUntilItTakes(() -> {
            Fighter f = fighterUnderFire();
            heavies.clear();
            for (PlasmaType type : new PlasmaType[] {
                    PlasmaType.F, PlasmaType.G, PlasmaType.S, PlasmaType.R })
                heavies.add(plasma(type, "Pl" + type + "-1", f));
            drone("Drone-1", f);        // the drop needs a legitimate victim to report
            return f;
        });

        for (PlasmaTorpedo heavy : heavies)
            assertTrue(heavy.getPlasmaType() + " must survive chaff (FP9.18)",
                    game.getSeekers().contains(heavy));
    }

    /**
     * A seeking SHUTTLE goes, which D11.32 names in the same breath as the drones - a suicide
     * shuttle or a scatter pack running at the fighter.
     */
    @Test
    public void chaffTakesASeekingShuttle() {
        final com.sfb.objects.shuttles.Shuttle[] seeker = new com.sfb.objects.shuttles.Shuttle[1];

        chaffUntilItTakes(() -> {
            Fighter f = fighterUnderFire();
            com.sfb.objects.shuttles.AdminShuttle base = new com.sfb.objects.shuttles.AdminShuttle();
            base.setName("SS-1");
            com.sfb.objects.shuttles.SuicideShuttle ss =
                    new com.sfb.objects.shuttles.SuicideShuttle(base);
            ss.setName("SS-1");
            ss.setSeekerType(Seeker.SeekerType.SHUTTLE);
            ss.setTarget(f);
            ss.setLocation(enemy.getLocation());
            game.getSeekers().add(ss);
            seeker[0] = ss;
            return f;
        });

        assertFalse("D11.32's \"seeking shuttles\"", game.getSeekers().contains(seeker[0]));
    }

    // ---------------------------------------------------------------- the count reported

    /**
     * The log must count only what it actually distracted. It used to count every seeker in
     * the hex, which told the player a plasma-G had been shaken off when it had not.
     */
    @Test
    public void theReportCountsOnlyWhatWentAway() {
        Game.ActionResult r = chaffUntilItTakes(() -> {
            Fighter f = fighterUnderFire();
            drone("Drone-1", f);
            plasma(PlasmaType.G, "PlG-1", f);
            plasma(PlasmaType.R, "PlR-1", f);
            return f;
        });

        assertTrue(r.getMessage(), r.getMessage().contains("1 seeker(s) distracted"));
        assertEquals("the two heavy torpedoes are still coming", 2, game.getSeekers().size());
    }
}
