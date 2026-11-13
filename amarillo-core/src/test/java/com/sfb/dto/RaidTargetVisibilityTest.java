package com.sfb.dto;

import com.sfb.Game;
import com.sfb.Player;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.properties.SystemTarget;
import com.sfb.samples.KlingonShips;
import com.sfb.samples.RomulanShips;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A hit-and-run raid can only name a system the raider can SEE.
 *
 * <p>The owner's ruling of 2026-11-13, given about DERFACS and worth far more than the field it
 * settled: a DERFACS is a valid raid target (D7.835), so an opponent has to be able to see one
 * to name it. That makes {@code BoardingResolver.getTargetableSystems} a LOWER BOUND on
 * {@link ShipDtoPrivacyTest#SHIP_PUBLIC} — every system the picker offers must be disclosed by
 * some public field, or the picker is offering a choice its owner was never told about.
 *
 * <p>DERFACS was the only system in the list that failed it, and it failed for weeks: it reached
 * no DTO at all, so the ship simply rolled better than any panel said it would. Nothing warned
 * anyone, because each half looked right on its own — core enumerated it, the DTO never
 * mentioned it, and no test knew the two were related. That is what this file is for.
 *
 * <p>It is the enumeration that matters more than today's answers. A new raidable system has to
 * be ruled here before it compiles green, the same bargain {@link ShipDtoPrivacyTest} strikes
 * for DTO fields.
 */
public class RaidTargetVisibilityTest {

    /**
     * Each raidable system, and a {@code ShipDto} field that discloses it to an opponent.
     *
     * <p>The field named is the one that gives a raid its target: it does not have to say
     * everything about the system, only that there is one to shoot at. {@code sensorRating}
     * discloses sensors, {@code weapons} discloses every weapon individually, and
     * {@code availableLWarp} says there is a left warp engine still worth raiding.
     */
    private static final Map<SystemTarget.Type, String> DISCLOSED_BY =
            new EnumMap<>(SystemTarget.Type.class);
    static {
        DISCLOSED_BY.put(SystemTarget.Type.WEAPON,          "weapons");
        DISCLOSED_BY.put(SystemTarget.Type.TRACTOR,         "totalTractors");
        DISCLOSED_BY.put(SystemTarget.Type.WARP_L,          "availableLWarp");
        DISCLOSED_BY.put(SystemTarget.Type.WARP_R,          "availableRWarp");
        DISCLOSED_BY.put(SystemTarget.Type.WARP_C,          "availableCWarp");
        DISCLOSED_BY.put(SystemTarget.Type.IMPULSE,         "availableImpulse");
        DISCLOSED_BY.put(SystemTarget.Type.SENSORS,         "sensorRating");
        DISCLOSED_BY.put(SystemTarget.Type.SCANNERS,        "scannerBonus");
        DISCLOSED_BY.put(SystemTarget.Type.TRANSPORTERS,    "availableTransporters");
        DISCLOSED_BY.put(SystemTarget.Type.BATTERY,         "availableBattery");
        DISCLOSED_BY.put(SystemTarget.Type.FHULL,           "availableFhull");
        DISCLOSED_BY.put(SystemTarget.Type.AHULL,           "availableAhull");
        DISCLOSED_BY.put(SystemTarget.Type.CHULL,           "availableChull");
        DISCLOSED_BY.put(SystemTarget.Type.CLOAKING_DEVICE, "cloakState");
        DISCLOSED_BY.put(SystemTarget.Type.DERFACS,         "derfacsFunctional");
        DISCLOSED_BY.put(SystemTarget.Type.UIM,             "uimFunctional");
        DISCLOSED_BY.put(SystemTarget.Type.BRIDGE,          "availableBridge");
        DISCLOSED_BY.put(SystemTarget.Type.FLAG,            "availableFlag");
        DISCLOSED_BY.put(SystemTarget.Type.EMER,            "availableEmer");
        DISCLOSED_BY.put(SystemTarget.Type.AUXCON,          "availableAuxcon");
        DISCLOSED_BY.put(SystemTarget.Type.SECURITY,        "availableSecurity");
    }

    /**
     * Raidable types the picker never enumerates off a ship, each safe for its own reason.
     *
     * <p>OBJECTIVE is a carried thing — a stasis box, a crate, a VIP — and
     * {@code getTargetableSystems} does not list them. A raid names one by its NAME, through the
     * {@code "OBJECTIVE:<name>"} form, which the raider can only use for something it already
     * knows is aboard. That is what keeps the hidden-objective model intact: an enumerated list
     * would disclose the box by offering it, which is exactly the leak SH47's counters exist to
     * avoid. If objectives are ever added to the picker, this entry must move above and bring a
     * public field with it.
     */
    private static final Set<SystemTarget.Type> NOT_ENUMERATED =
            EnumSet.of(SystemTarget.Type.OBJECTIVE);

    private Game game;
    private Ship klingon;   // D7: carries both a DERFACS and a UIM
    private Ship romulan;   // KR: carries a cloaking device

    @Before
    public void setUp() {
        game = new Game();

        Player kp = new Player();
        kp.setTeamName("Klingons");
        klingon = new Ship();
        klingon.init(KlingonShips.getD7());
        klingon.setName("IKV Saber");
        klingon.setLocation(new Location(10, 10));
        klingon.setOwner(kp);

        Player rp = new Player();
        rp.setTeamName("Romulans");
        romulan = new Ship();
        romulan.init(RomulanShips.getRomKr());
        romulan.setName("Raptor");
        romulan.setLocation(new Location(20, 10));
        romulan.setOwner(rp);

        game.getShips().add(klingon);
        game.getShips().add(romulan);
    }

    /** Every value of the enum is ruled one way or the other. This is the point of the file. */
    @Test
    public void everyRaidTargetTypeIsRuled() {
        Set<String> unruled = new TreeSet<>();
        for (SystemTarget.Type type : SystemTarget.Type.values())
            if (!DISCLOSED_BY.containsKey(type) && !NOT_ENUMERATED.contains(type))
                unruled.add(type.name());

        assertTrue("New SystemTarget.Type value(s) with no ruling on whether a raider can SEE"
                + " them: " + unruled + ". A raid may only name a system its owner can observe"
                + " (the owner's ruling of 2026-11-13, from D7.835). Add each to DISCLOSED_BY"
                + " with the ShipDto field that discloses it, or to NOT_ENUMERATED with the"
                + " reason the picker never offers it.",
                unruled.isEmpty());
    }

    /** No type may be in both lists, which would make the ruling meaningless. */
    @Test
    public void noTypeIsRuledTwice() {
        for (SystemTarget.Type type : NOT_ENUMERATED)
            assertTrue(type + " is ruled both enumerated and not",
                    !DISCLOSED_BY.containsKey(type));
    }

    /** Each named field exists on ShipDto and is PUBLIC. A typo here would pass silently. */
    @Test
    public void everyDisclosingFieldExistsAndIsPublic() {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<SystemTarget.Type, String> e : DISCLOSED_BY.entrySet()) {
            String field = e.getValue();
            try {
                Field f = GameStateDto.ShipDto.class.getField(field);
                if (f == null) wrong.add(e.getKey() + " -> " + field + " (missing)");
            } catch (NoSuchFieldException ex) {
                wrong.add(e.getKey() + " -> " + field + " (no such ShipDto field)");
                continue;
            }
            if (!ShipDtoPrivacyTest.SHIP_PUBLIC.contains(field))
                wrong.add(e.getKey() + " -> " + field + " (not in SHIP_PUBLIC)");
        }
        assertEquals("a raid may only name a system the raider can see", "[]", wrong.toString());
    }

    /**
     * The live check: everything the picker actually offers is ruled.
     *
     * <p>The other tests compare two lists written by hand, which agree right up until someone
     * adds a target without touching either. This one asks the resolver.
     */
    @Test
    public void thePickerOnlyOffersSystemsTheRaiderCanSee() {
        Set<String> offered = new TreeSet<>();
        for (Ship ship : new Ship[] { klingon, romulan })
            for (SystemTarget st : game.getTargetableSystems(ship)) {
                if (NOT_ENUMERATED.contains(st.getType()))
                    fail(st.getType() + " is ruled as never enumerated, but the picker offered"
                            + " it for " + ship.getName() + ". Move it to DISCLOSED_BY with the"
                            + " field that discloses it — or it leaks by being offered.");
                if (!DISCLOSED_BY.containsKey(st.getType()))
                    offered.add(st.getType().name());
            }

        assertTrue("the hit-and-run picker offers system(s) nobody has ruled visible: " + offered,
                offered.isEmpty());
    }

    /**
     * Guard on the test above: it asserts nothing if the fixture produces nothing.
     *
     * <p>Named rather than counted, so a hull that quietly stops reporting its DERFACS fails
     * here instead of silently weakening the check.
     */
    @Test
    public void theFixtureReachesTheSystemsWorthChecking() {
        Set<SystemTarget.Type> seen = new LinkedHashSet<>();
        for (Ship ship : new Ship[] { klingon, romulan })
            for (SystemTarget st : game.getTargetableSystems(ship))
                seen.add(st.getType());

        for (SystemTarget.Type needed : new SystemTarget.Type[] {
                SystemTarget.Type.DERFACS, SystemTarget.Type.UIM,
                SystemTarget.Type.CLOAKING_DEVICE, SystemTarget.Type.WEAPON,
                SystemTarget.Type.TRANSPORTERS, SystemTarget.Type.BRIDGE })
            assertTrue("broken fixture, not a pass: nothing offered " + needed
                    + ", so the live check never looked at it", seen.contains(needed));
    }
}
