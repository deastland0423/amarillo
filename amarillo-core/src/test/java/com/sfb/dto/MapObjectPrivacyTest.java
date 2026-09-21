package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.PlasmaTorpedo;
import com.sfb.objects.Ship;
import com.sfb.objects.SpaceMine;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.shuttles.WildWeaselShuttle;
import com.sfb.properties.Location;
import com.sfb.properties.PlasmaType;
import com.sfb.properties.WeaponArmingType;
import com.sfb.samples.FederationShips;
import com.sfb.samples.KlingonShips;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/**
 * What an opponent may know about everything on the map that is not a ship.
 *
 * ShipDtoPrivacyTest does this for ships; this is the rest, and it needs a category ships
 * did not: REVEALED, for what a lab or a scout channel buys (G4.231-G4.233). A drone's
 * type, warhead and endurance, a plasma's target, a shuttle's manning and seeking course —
 * all hidden, then disclosed. A two-way split cannot say that.
 * <p>
 * As with ships, the point is less the assertions than the completeness check: a field in
 * none of the three lists fails the build, so whoever adds one has to decide who may see
 * it. A field nobody has ruled on is sent to everyone.
 * <p>
 * Field reflection cannot see the other two ways this codebase hides things, so they are
 * asserted by hand in DtoRedactionTest instead: TYPE SUBSTITUTION, where an enemy's
 * suicide shuttle arrives as a plain ShuttleDto rather than a redacted SuicideShuttleDto,
 * and OMISSION, where an object is not sent at all (nothing today — pre-battle minefields
 * will be the first).
 * <p>
 * The rulings are the ship owner's (2026-09-20).
 */
public class MapObjectPrivacyTest {

    /** Who may see a field. */
    private enum Visibility { PUBLIC, PRIVATE, REVEALED }

    /** Fields every map object carries. Where a thing IS, is never secret. */
    private static final Set<String> BASE_PUBLIC =
        new HashSet<>(Arrays.asList("name", "location", "tractoredBy"));

    private static final Map<Class<?>, Map<String, Visibility>> RULINGS = new HashMap<>();

    private static void rule(Class<?> type, Visibility v, String... fields) {
        RULINGS.computeIfAbsent(type, k -> new HashMap<>());
        for (String f : fields)
            RULINGS.get(type).put(f, v);
    }

    static {
        // ---- Shuttles. An enemy's suicide shuttle and unreleased scatter pack arrive as
        // one of these too, which is the whole point of them (G4.233).
        rule(GameStateDto.ShuttleDto.class, Visibility.PUBLIC,
            "facing", "speed", "maxSpeed", "effectiveMaxSpeed", "usingEm", "emSpeedCommitted",
            "isFighter", "shuttleTypeName", "parentPlayer", "parentShipName", "weapons",
            "crippled", "hetUsed", "beingRecovered", "landingPhase", "landedHexSide",
            "personnelCapacity", "isIdentified",
            // Damage is visible, and so is the hull behind it: unlike a drone, the craft
            // type is public ("Admin Shuttle"), so its hull was never secret.
            "hull", "maxHull", "damageTaken", "launchImpulse");
        rule(GameStateDto.ShuttleDto.class, Visibility.PRIVATE,
            // What it is CARRYING: G4.233 gives the seeking course and stops there. How
            // much it could carry is a property of the craft and is public, below.
            "holdCrew", "holdSpacesUsed");
        rule(GameStateDto.ShuttleDto.class, Visibility.REVEALED,
            "manned", "seekingCourse", "seekingTargetName");

        // ---- Drones (G4.231). Damage taken is public; the hull behind it is not, since
        // maxHull = hull + damage would name the type.
        rule(GameStateDto.DroneDto.class, Visibility.PUBLIC,
            "facing", "speed", "damageTaken", "controllerFaction", "controllerName",
            "launcherName", "launchImpulse", "isIdentified");
        rule(GameStateDto.DroneDto.class, Visibility.REVEALED,
            "droneType", "warheadDamage", "hull", "maxHull", "endurance", "targetName");

        // ---- Plasma (G4.232). Strength is always known (FP1.32); a lab buys the target
        // and nothing else, so type and pseudo stay hidden even after identification.
        rule(GameStateDto.PlasmaTorpedoDto.class, Visibility.PUBLIC,
            "facing", "speed", "currentStrength", "controllerFaction", "controllerName",
            "distanceTraveled", "damageTaken", "launchImpulse", "isIdentified");
        rule(GameStateDto.PlasmaTorpedoDto.class, Visibility.PRIVATE,
            "plasmaType", "pseudo");
        rule(GameStateDto.PlasmaTorpedoDto.class, Visibility.REVEALED, "targetName");

        // ---- Wild weasel: public from launch, its interference announcing it (J3.0).
        rule(GameStateDto.WildWeaselDto.class, Visibility.PUBLIC,
            "facing", "speed", "parentShipName", "parentPlayer", "exploding", "postExplosion");

        // ---- Mines placed during a battle are seen by everyone; whether one is real is
        // never sent at all. Pre-battle minefields will be owner-only and are not built.
        rule(GameStateDto.MineDto.class, Visibility.PUBLIC, "active", "revealed");

        // ---- Terrain is plain to everyone.
        rule(GameStateDto.TerrainDto.class, Visibility.PUBLIC,
            "terrainType", "radius", "tokenArt", "rings");

        // ---- Objectives. Hidden objectives (a ship carrying an admiral, found by some
        // specified means) are a scenario feature nobody has built; today these are public.
        rule(GameStateDto.ObjectiveDto.class, Visibility.PUBLIC,
            "carrierName", "retrieval", "ownerTeam", "secured", "beingRecovered", "side");

        // ---- Only ever sent to someone entitled to see it: an enemy gets a ShuttleDto
        // instead (DtoRedactionTest covers that substitution). Ruled anyway, so a new
        // field cannot slip in unexamined if that ever changes.
        rule(GameStateDto.ScatterPackDto.class, Visibility.PUBLIC,
            "facing", "speed", "controllerFaction", "controllerName", "targetName",
            "parentShipName", "parentPlayer", "hull", "maxHull", "damageTaken",
            "payload", "released", "isIdentified");
        rule(GameStateDto.SuicideShuttleDto.class, Visibility.PUBLIC,
            "facing", "speed", "controllerFaction", "controllerName", "targetName",
            "parentShipName", "parentPlayer", "hull", "maxHull", "damageTaken",
            "warheadDamage", "armingTurnsComplete", "isIdentified");
    }

    private Game game;
    private Ship klingon;
    private Drone drone;
    private PlasmaTorpedo plasma;
    private com.sfb.objects.shuttles.Shuttle shuttle;

    @Before
    public void setUp() {
        game = new Game();

        Player federation = new Player();
        federation.setTeamName("Federation");
        Player empire = new Player();
        empire.setTeamName("Klingon");

        Ship fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setOwner(federation);

        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKS Fury");
        klingon.setLocation(new Location(14, 10));
        klingon.setOwner(empire);

        game.getShips().add(fed);
        game.getShips().add(klingon);

        // One of everything the Klingons own, each carrying something worth hiding.
        shuttle = new AdminShuttle();
        shuttle.setName("IKS Fury-Admin-1");
        shuttle.setOwner(empire);
        shuttle.setParentShipName(klingon.getName());
        shuttle.setLocation(new Location(14, 11));
        shuttle.getHold().setCrew(2);
        game.getActiveShuttles().add(shuttle);

        WildWeaselShuttle ww = new WildWeaselShuttle(klingon);
        ww.setName("IKS Fury-Admin-2");
        ww.setOwner(empire);
        ww.setLocation(new Location(14, 12));
        game.getActiveShuttles().add(ww);

        drone = new Drone(DroneType.TypeI);
        drone.setName("IKS Fury-Drone-1");
        drone.setLocation(new Location(13, 10));
        drone.setController(klingon);
        drone.setTarget(fed);
        drone.setHull(drone.getHull() - 1);
        game.getSeekers().add(drone);

        plasma = new PlasmaTorpedo(PlasmaType.G, WeaponArmingType.STANDARD);
        plasma.setName("IKS Fury-Plasma-1");
        plasma.setLocation(new Location(13, 11));
        plasma.setController(klingon);
        plasma.setTarget(fed);
        plasma.setPseudoPlasma(true);
        game.getSeekers().add(plasma);

        SuicideShuttle suicide = new SuicideShuttle(new AdminShuttle());
        suicide.setName("IKS Fury-Admin-3");
        suicide.setOwner(empire);
        suicide.setController(klingon);
        suicide.setTarget(fed);
        game.getSeekers().add(suicide);

        ScatterPack pack = new ScatterPack(new AdminShuttle());
        pack.setName("IKS Fury-Admin-4");
        pack.setOwner(empire);
        pack.setController(klingon);
        pack.setTarget(fed);
        pack.addDrone(new Drone(DroneType.TypeI));
        game.getSeekers().add(pack);

        SpaceMine mine = SpaceMine.createDroppedTBomb(klingon, 1, true);
        mine.setLocation(new Location(13, 12));
        game.getMines().add(mine);
    }

    private GameStateDto enemyView() {
        return new GameStateDto(game, "Federation");
    }

    private GameStateDto ownerView() {
        return new GameStateDto(game, "Klingons");
    }

    private GameStateDto.MapObjectDto find(GameStateDto dto, String name) {
        return dto.mapObjects.stream().filter(o -> name.equals(o.name)).findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- the guard that lasts

    @Test
    public void everyFieldOfEveryMapObjectIsRuled() {
        Set<String> unruled = new TreeSet<>();
        Set<String> ghosts = new TreeSet<>();

        for (Map.Entry<Class<?>, Map<String, Visibility>> e : RULINGS.entrySet()) {
            Set<String> actual = new LinkedHashSet<>();
            for (Field f : e.getKey().getFields())
                if (!Modifier.isStatic(f.getModifiers()))
                    actual.add(f.getName());

            for (String name : actual)
                if (!BASE_PUBLIC.contains(name) && !e.getValue().containsKey(name))
                    unruled.add(e.getKey().getSimpleName() + "." + name);

            for (String ruled : e.getValue().keySet())
                if (!actual.contains(ruled))
                    ghosts.add(e.getKey().getSimpleName() + "." + ruled);
        }

        assertTrue("Map-object field(s) with no ruling on who may see them: " + unruled
            + ". Add each to RULINGS as PUBLIC, PRIVATE or REVEALED after deciding. A field"
            + " nobody has ruled on is sent to everyone.", unruled.isEmpty());
        assertTrue("Ruled on field(s) that no longer exist: " + ghosts, ghosts.isEmpty());
    }

    /** A registry that covers none of what a viewer actually gets would pass vacuously. */
    @Test
    public void theFixturePutsOneOfEachOnTheMap() {
        Set<Class<?>> seen = new LinkedHashSet<>();
        for (GameStateDto.MapObjectDto o : enemyView().mapObjects)
            seen.add(o.getClass());

        for (Class<?> type : Arrays.asList(GameStateDto.ShuttleDto.class,
                GameStateDto.DroneDto.class, GameStateDto.PlasmaTorpedoDto.class,
                GameStateDto.WildWeaselDto.class, GameStateDto.MineDto.class))
            assertTrue("an enemy should receive a " + type.getSimpleName()
                + " from this fixture, or the assertions below test nothing",
                seen.contains(type));
    }

    // ---------------------------------------------------------------- today's answer

    @Test
    public void privateFieldsAreBlankToAnEnemyEvenAfterIdentification() throws Exception {
        identifyEverything();

        for (GameStateDto.MapObjectDto o : enemyView().mapObjects) {
            Map<String, Visibility> rules = RULINGS.get(o.getClass());
            if (rules == null)
                continue;   // ships have their own test
            for (Map.Entry<String, Visibility> e : rules.entrySet())
                if (e.getValue() == Visibility.PRIVATE) {
                    Field f = o.getClass().getField(e.getKey());
                    assertTrue(o.getClass().getSimpleName() + "." + e.getKey() + " on "
                        + o.name + " reaches an enemy with a value in it: " + f.get(o),
                        isBlank(f.get(o)));
                }
        }
    }

    @Test
    public void revealedFieldsAreBlankUntilIdentified() throws Exception {
        int checked = 0;
        for (GameStateDto.MapObjectDto o : enemyView().mapObjects) {
            Map<String, Visibility> rules = RULINGS.get(o.getClass());
            if (rules == null)
                continue;
            for (Map.Entry<String, Visibility> e : rules.entrySet())
                if (e.getValue() == Visibility.REVEALED) {
                    Field f = o.getClass().getField(e.getKey());
                    assertTrue(o.getClass().getSimpleName() + "." + e.getKey() + " on "
                        + o.name + " is disclosed without anyone identifying it: " + f.get(o),
                        isBlank(f.get(o)));
                    checked++;
                }
        }
        assertTrue("no REVEALED field was actually checked", checked > 0);
    }

    @Test
    public void identificationDisclosesTheRevealedFieldsAndNothingElse() throws Exception {
        identifyEverything();

        GameStateDto enemy = enemyView();
        GameStateDto owner = ownerView();
        int disclosed = 0;

        for (GameStateDto.MapObjectDto theirs : enemy.mapObjects) {
            Map<String, Visibility> rules = RULINGS.get(theirs.getClass());
            if (rules == null)
                continue;
            GameStateDto.MapObjectDto mine = find(owner, theirs.name);
            if (mine == null || mine.getClass() != theirs.getClass())
                continue;   // substitution: DtoRedactionTest covers those
            for (Map.Entry<String, Visibility> e : rules.entrySet()) {
                Field f = theirs.getClass().getField(e.getKey());
                if (e.getValue() == Visibility.PUBLIC)
                    assertEquals(theirs.getClass().getSimpleName() + "." + e.getKey()
                        + " differs between the views though it is public",
                        describe(f.get(mine)), describe(f.get(theirs)));
                else if (e.getValue() == Visibility.REVEALED) {
                    assertEquals(theirs.getClass().getSimpleName() + "." + e.getKey()
                        + " should be disclosed once identified",
                        String.valueOf(f.get(mine)), String.valueOf(f.get(theirs)));
                    disclosed++;
                }
            }
        }
        assertTrue("nothing was disclosed, so this proved nothing", disclosed > 0);
    }

    /** Identification never turns a hidden thing public: a pseudo stays a pseudo (G4.232). */
    @Test
    public void identifyingAPlasmaStillDoesNotSayWhetherItIsReal() {
        identifyEverything();

        GameStateDto.PlasmaTorpedoDto dto =
            (GameStateDto.PlasmaTorpedoDto) find(enemyView(), "IKS Fury-Plasma-1");

        assertNotNull(dto);
        assertEquals("the target is what a lab buys", "USS Enterprise", dto.targetName);
        assertFalse("and a pseudo is still indistinguishable", dto.pseudo);
        assertEquals("nor is the type ever disclosed", "?", dto.plasmaType);
    }

    private void identifyEverything() {
        drone.identify();
        plasma.identify();
        shuttle.identify();
        for (com.sfb.objects.Seeker s : game.getSeekers())
            s.identify();
    }

    /**
     * A value in a form two views can be compared by. DTOs carry no equals(), so a list of
     * them would differ by object identity alone and say nothing about what was disclosed;
     * its size is the honest comparison.
     */
    private static String describe(Object value) {
        if (value instanceof Collection)
            return "collection of " + ((Collection<?>) value).size();
        return String.valueOf(value);
    }

    private static boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof Boolean) return !((Boolean) value);
        if (value instanceof Number) return ((Number) value).doubleValue() == 0.0;
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof String) return ((String) value).isEmpty() || "?".equals(value);
        return false;
    }
}
