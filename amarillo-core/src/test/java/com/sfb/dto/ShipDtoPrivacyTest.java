package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.*;

/**
 * What an opponent may know about a ship, field by field.
 *
 * This exists because the model was the other way up: everything was public except a
 * handful of things withheld by not being built, and an opponent could read whether your
 * disruptors were armed straight out of the DTO. The rule now is that the CLIENT never
 * decides what to hide — it renders what it is given, and a missing value means unknown —
 * which only works if the server is thorough.
 * <p>
 * Being thorough by hand does not last. So every field of ShipDto and WeaponDto is listed
 * below as PUBLIC or PRIVATE, and a field in neither list fails the build: whoever adds one
 * has to decide which it is. That is the point of this test far more than the assertions
 * are — those check today's answer, this checks tomorrow's.
 * <p>
 * The rulings are the ship owner's (2026-09-19):
 * <ul>
 *   <li>Known: shield box strength, all damage taken, ECM/ECCM generated and lent, whether
 *       a weapon has fired and is on cooldown, and that a weapon is destroyed.</li>
 *   <li>Not known: whether or how a weapon is armed, energy in batteries, the state of
 *       shuttles and fighters aboard, guards, how many T-bombs (real or dummy), shield
 *       REINFORCEMENT, lock-on targets, tactical budget, remaining scout lending.</li>
 *   <li>Command rating is public but inert: it decides fleet legality, not battles.</li>
 * </ul>
 */
public class ShipDtoPrivacyTest {

    // ---------------------------------------------------------------- the rulings

    private static final Set<String> SHIP_PRIVATE = new HashSet<>(Arrays.asList(
        // The energy held, NOT the boxes holding it: availableBattery is a box count and
        // is public, like every other box on the SSD.
        "batteryCharge", "batteryPower", "reserveWarp",
        "phaserCapacitor", "capacitorsCharged",
        // Mines carried, and which are bluffs
        "tBombs", "dummyTBombs", "nuclearSpaceMines",
        // Intentions: paying for Erratic Maneuvers is one, USING them is a visible
        // manoeuvre (C10.11 vs C10.0).
        "lockOnTargets", "tacBudget", "tacAvailable", "sublightTacAvailable", "paidForEm",
        "scoutEwPool", "scoutEwRemaining",
        // Built empty for an enemy rather than blanked
        "droneRacks", "shuttleBays", "allocationNotes"
    ));

    private static final Set<String> SHIP_PUBLIC = new HashSet<>(Arrays.asList(
        // Identity and position
        "name", "location", "shipType", "faction", "teamName", "ownerName",
        "tokenArt", "leader", "escort", "trueCarrier", "bch", "commandRating", "skeleton",
        "captured", "disengaged", "tractoredBy",
        // Movement, which is watched
        "facing", "speed", "tractorTrueSpeed", "turnMode", "turnHexes", "hexesUntilTurn",
        "moveCost", "maxSpeedNextTurn", "decelerating", "decelerationEndsAtImpulse",
        "immobileUntilImpulse", "canDisengageBySeparation", "destructionDirections",
        "hetsThisTurn", "lastHetImpulse", "hetCost", "usingEm", "emPending",
        "erraticCost",
        // Damage: every box, remaining and maximum
        "shields", "minimumShieldCost", "activeShieldCost",
        "availableAhull", "availableChull", "availableFhull", "maxAhull", "maxChull",
        "maxFhull", "availableApr", "maxApr", "availableImpulse", "maxImpulse",
        "availableLWarp", "availableRWarp", "availableCWarp", "maxLWarp", "maxRWarp",
        "maxCWarp", "availableAwr", "maxAwr", "availableBridge", "maxBridge",
        "availableAuxcon", "maxAuxcon", "availableFlag", "maxFlag", "availableEmer",
        "maxEmer", "availableSecurity", "maxSecurity", "availableLab", "functioningLab",
        "availableTractors", "totalTractors", "availableTransporters", "totalTransporters",
        "availableBattery",   // boxes surviving damage; the charge in them is not public
        "availableDeckCrews", "availableCrewUnits", "capturedCrew", "minimumCrew",
        "boardingParties", "commandos", "crewQuality", "totalPower", "phaserCapacitorMax",
        "uimFunctional", "sensorRating", "scannerBonus", "canDoubleEngines",
        // Electronic warfare: generated and lent are both public by ruling
        "ecmAllocated", "eccmAllocated", "ecmTotal", "eccmTotal", "ecmSources",
        "lentEcm", "lentEccm", "scoutEwLent", "offensiveEw", "wildWeaselActive",
        "wwEcmBonus",
        // Visible states and published costs
        "weapons", "activeFireControl", "fireControlActivating", "fcActivatingUntil",
        "fcPaidThisTurn", "fireControlCost", "lifeSupportCost", "cloakCost", "cloakState",
        "cloakFadeStep", "cloakTransitionImpulse", "tractored", "tractoredByName",
        "tractoredTargetNames", "tractorEnergy", "tractorEnergyRemaining",
        "negativeTractorAccumulated", "transporterEnergyCost", "transporterUses"
    ));

    /**
     * Public, but not the same number both sides see, so excluded from the
     * reads-the-same check and asserted on its own terms below.
     * <p>
     * Uses remaining are public — every use of a transporter is seen — but our figure is
     * limited by energy INCLUDING batteries, which are not. Sent as-is, the transporter
     * count would have been a window onto the battery state.
     */
    private static final Set<String> SHIP_PUBLIC_RECOMPUTED = new HashSet<>(Arrays.asList(
        "transporterUses"
    ));

    private static final Set<String> WEAPON_PRIVATE = new HashSet<>(Arrays.asList(
        "armed", "armingType", "armingTurn", "totalArmingTurns", "armingEnergy",
        "readyToFire", "plasmaType", "pseudoPlasmaReady", "isRolling", "chargesRemaining",
        "esgStoredEnergy",
        // Ammunition remaining, hidden like the drones in a rack
        "addShots", "addReloads"
    ));

    private static final Set<String> WEAPON_PUBLIC = new HashSet<>(Arrays.asList(
        // What it IS, from the SSD, and what it has visibly done
        "name", "designator", "arcLabel", "arcMask", "launchDirectionsMask", "functional",
        "isHeavy", "maxShotsPerTurn", "shotsThisTurn", "minImpulseGap", "lastImpulseFired",
        "cooldown", "launcherType", "photonTube", "canOverload", "canSuicide", "canEpt",
        "canProximity", "canFastLoad", "overloadFinalTurnOnly", "armingCost", "holdCost",
        "eptCost", "rollingCost", "canFireDouble",
        // What a full ADD load holds is on the SSD; what is left in it is not
        "addCapacity",
        // Scout channels: what a channel is doing and lending is public by ruling
        "scoutChannel", "channelPowered", "channelBlinded", "channelFunction",
        "channelLendTarget", "channelLentEcm", "channelLentEccm", "channelBreakAttempts",
        "channelIdentifyAttempts", "channelAttractedDrone",
        // ESG: the field it projects is plainly visible
        "esg", "esgActive", "esgAnnounced", "esgRadius", "esgStrength", "esgReleaseIn",
        "esgHasCapacitor", "esgMaxEnergy"
    ));

    private Game game;
    private Ship fed;
    private GameStateDto ownerView;
    private GameStateDto enemyView;

    @Before
    public void setUp() {
        game = new Game();

        Player federation = new Player();
        federation.setTeamName("Federation");
        Player klingon = new Player();
        klingon.setTeamName("Klingon");

        fed = new Ship();
        fed.init(FederationShips.getFedCa());
        fed.setName("USS Enterprise");
        fed.setLocation(new Location(10, 10));
        fed.setFacing(1);
        fed.setOwner(federation);

        Ship enemy = new Ship();
        enemy.init(FederationShips.getFedCa());
        enemy.setName("IKS Fury");
        enemy.setLocation(new Location(10, 14));
        enemy.setFacing(13);
        enemy.setOwner(klingon);

        game.getShips().add(fed);
        game.getShips().add(enemy);

        // Put REAL values into the private fields. Without this the redaction would be
        // indistinguishable from a ship that happened to have nothing to hide, and every
        // assertion below would pass on an empty DTO.
        fed.setTBombs(3);
        fed.setDummyTBombs(2);
        fed.setNuclearSpaceMines(1);
        fed.getPowerSystems().setBatteryPower(3);   // a CA carries three boxes
        fed.getShields().reinforceShield(1, 5);
        fed.addLockOn(enemy);
        for (Weapon w : fed.getWeapons().fetchAllWeapons())
            if (w instanceof HeavyWeapon)
                ((HeavyWeapon) w).arm(2);

        ownerView = new GameStateDto(game, "Federation");
        enemyView = new GameStateDto(game, "Klingon");
    }

    private GameStateDto.ShipDto ship(GameStateDto dto) {
        return (GameStateDto.ShipDto) dto.mapObjects.stream()
            .filter(o -> "USS Enterprise".equals(o.name))
            .findFirst().orElseThrow(() -> new AssertionError("ship missing from view"));
    }

    // ---------------------------------------------------------------- the guard that lasts

    @Test
    public void everyShipFieldIsRuledPublicOrPrivate() {
        assertUnruled(GameStateDto.ShipDto.class, SHIP_PUBLIC, SHIP_PRIVATE, "ShipDto");
    }

    @Test
    public void everyWeaponFieldIsRuledPublicOrPrivate() {
        assertUnruled(GameStateDto.WeaponDto.class, WEAPON_PUBLIC, WEAPON_PRIVATE, "WeaponDto");
    }

    private void assertUnruled(Class<?> type, Set<String> pub, Set<String> priv, String what) {
        Set<String> unruled = new TreeSet<>();
        for (Field f : type.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (!pub.contains(f.getName()) && !priv.contains(f.getName()))
                unruled.add(f.getName());
        }
        assertTrue("New " + what + " field(s) with no ruling on who may see them: " + unruled
            + ". Add each to SHIP_PUBLIC/SHIP_PRIVATE (or the WEAPON_ lists) after deciding."
            + " A field nobody has ruled on defaults to being sent to everyone, which is how"
            + " an opponent came to see whether your disruptors were armed.",
            unruled.isEmpty());

        Set<String> both = new LinkedHashSet<>(pub);
        both.retainAll(priv);
        assertTrue("ruled both public and private: " + both, both.isEmpty());

        // A ruling on a field that no longer exists guards nothing, and hides the fact
        // that it guards nothing.
        Set<String> actual = new HashSet<>();
        for (Field f : type.getFields())
            actual.add(f.getName());
        Set<String> ghosts = new TreeSet<>();
        ghosts.addAll(pub);
        ghosts.addAll(priv);
        ghosts.removeAll(actual);
        assertTrue("ruled on " + what + " field(s) that do not exist: " + ghosts,
            ghosts.isEmpty());
    }

    // ---------------------------------------------------------------- today's answer

    @Test
    public void theFixtureActuallyHasSecretsToKeep() {
        // Guards every assertion below: they would all pass on a ship with nothing to hide.
        GameStateDto.ShipDto mine = ship(ownerView);
        assertEquals("t-bombs", 3, mine.tBombs);
        assertEquals("dummy t-bombs", 2, mine.dummyTBombs);
        assertTrue("batteries", mine.batteryCharge > 0);
        assertFalse("lock-on", mine.lockOnTargets.isEmpty());
        assertTrue("shield reinforcement",
            mine.shields.get(0).current > mine.shields.get(0).baseStrength);
        assertTrue("a heavy weapon part-way through arming — the arming fields are"
                + " what redaction blanks, so at least one must carry something first",
            mine.weapons.stream().anyMatch(w ->
                Boolean.TRUE.equals(w.armed) || w.armingTurn > 0 || w.armingEnergy > 0));
    }

    @Test
    public void everyPrivateShipFieldIsBlankToAnEnemy() throws Exception {
        GameStateDto.ShipDto theirs = ship(enemyView);
        for (String name : SHIP_PRIVATE) {
            Field f = GameStateDto.ShipDto.class.getField(name);
            assertTrue(name + " reaches an enemy with a value in it: " + f.get(theirs),
                isBlank(f.get(theirs)));
        }
    }

    @Test
    public void everyPrivateWeaponFieldIsBlankToAnEnemy() throws Exception {
        GameStateDto.ShipDto theirs = ship(enemyView);
        assertFalse("fixture needs weapons", theirs.weapons.isEmpty());
        for (GameStateDto.WeaponDto w : theirs.weapons)
            for (String name : WEAPON_PRIVATE) {
                Field f = GameStateDto.WeaponDto.class.getField(name);
                assertTrue(w.name + "." + name + " reaches an enemy with a value in it: "
                    + f.get(w), isBlank(f.get(w)));
            }
    }

    /**
     * The other direction, and the reason the public list is worth maintaining: a public
     * field must read the SAME to both sides. Over-redaction is a bug too — it would hide
     * damage a player is entitled to see and leave them guessing about a wreck.
     */
    @Test
    public void everyPublicShipFieldReadsTheSameToBothSides() throws Exception {
        GameStateDto.ShipDto mine = ship(ownerView);
        GameStateDto.ShipDto theirs = ship(enemyView);
        for (String name : SHIP_PUBLIC) {
            if ("shields".equals(name) || "weapons".equals(name)) continue;  // checked below
            if (SHIP_PUBLIC_RECOMPUTED.contains(name)) continue;             // asserted apart
            Field f = GameStateDto.ShipDto.class.getField(name);
            assertEquals(name + " differs between the two views though it is public",
                String.valueOf(f.get(mine)), String.valueOf(f.get(theirs)));
        }
    }

    @Test
    public void shieldBoxesAndDamageAreTheSameToBothSides() {
        GameStateDto.ShipDto mine = ship(ownerView);
        GameStateDto.ShipDto theirs = ship(enemyView);
        for (int i = 0; i < 6; i++) {
            assertEquals("shield " + (i + 1) + " box strength",
                mine.shields.get(i).baseStrength, theirs.shields.get(i).baseStrength);
            assertEquals("shield " + (i + 1) + " maximum",
                mine.shields.get(i).max, theirs.shields.get(i).max);
        }
        // ...but the reinforcement on shield 1 is not passed on.
        assertEquals("an enemy sees the box count, never the reinforcement",
            theirs.shields.get(0).baseStrength, theirs.shields.get(0).current);
    }

    /**
     * The awkward one: public by ruling, but the figure we compute for its owner is capped
     * by hidden energy, so an enemy gets the count the transporter boxes alone support.
     */
    @Test
    public void transporterUsesTellAnEnemyNothingAboutBatteries() {
        GameStateDto.ShipDto theirs = ship(enemyView);
        assertEquals("nothing used yet, so every undamaged box is still to be used",
            theirs.availableTransporters, theirs.transporterUses);

        // The discriminating half: a ship with no energy to power its transporters must
        // still report every unused box, or the shortfall would itself be the tell.
        fed.getPowerSystems().setBatteryPower(0);
        fed.getTransporters().init(java.util.Map.of("trans", 4));
        assertEquals("four boxes, no energy to speak of, still four to an enemy",
            4, ship(new GameStateDto(game, "Klingon")).transporterUses);
        assertTrue("while its owner sees what it can actually power",
            ship(new GameStateDto(game, "Federation")).transporterUses < 4);
    }

    /** Uses MADE are public, because everyone watches them happen. */
    @Test
    public void usingATransporterIsVisibleToAnEnemy() {
        fed.getTransporters().init(java.util.Map.of("trans", 4));
        fed.getTransporters().bankEnergy(1.0);
        assertTrue(fed.getTransporters().useTransporter());
        assertTrue(fed.getTransporters().useTransporter());

        assertEquals("two of four used, so two left to an enemy's eye",
            2, ship(new GameStateDto(game, "Klingon")).transporterUses);
    }

    /** Battery boxes are damage; the charge in them is not. */
    @Test
    public void anEnemyCountsBatteryBoxesButNotTheirCharge() {
        GameStateDto.ShipDto mine = ship(ownerView);
        GameStateDto.ShipDto theirs = ship(enemyView);

        assertTrue("fixture needs battery boxes", mine.availableBattery > 0);
        assertEquals("the boxes are as public as any other damage",
            mine.availableBattery, theirs.availableBattery);
        assertTrue("fixture needs charge in them", mine.batteryCharge > 0);
        assertEquals("but what is in them is not", 0, theirs.batteryCharge);
    }

    @Test
    public void aDestroyedWeaponIsVisibleToEveryone() {
        // Damage is public, whatever else is not: a weapon shot off the SSD is gone for all
        // to see, and it is checked before anything about arming.
        List<Weapon> weapons = fed.getWeapons().fetchAllWeapons();
        assertFalse(weapons.isEmpty());
        weapons.get(0).damage();
        String broken = weapons.get(0).getName();

        GameStateDto.ShipDto theirs = ship(new GameStateDto(game, "Klingon"));
        assertTrue("a destroyed weapon must show as destroyed to an enemy",
            theirs.weapons.stream().anyMatch(w -> broken.equals(w.name) && !w.functional));
    }

    private static boolean isBlank(Object value) {
        if (value == null) return true;
        if (value instanceof Boolean) return !((Boolean) value);
        if (value instanceof Number) return ((Number) value).doubleValue() == 0.0;
        if (value instanceof Collection) return ((Collection<?>) value).isEmpty();
        if (value instanceof String) return ((String) value).isEmpty();
        return false;
    }
}
