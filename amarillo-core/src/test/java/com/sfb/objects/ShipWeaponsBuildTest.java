package com.sfb.objects;

import com.sfb.weapons.Weapon;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Every weapon every ship file names must actually build.
 *
 * <h2>The hole this fills</h2>
 * {@link WeaponFactory#build} answers an unrecognised type with a line on stderr and a <b>null</b>,
 * and {@code ShipSpec.buildWeapons()} then does:
 *
 * <pre>    Weapon w = WeaponFactory.build(ws, ws.arcs);
 *    if (w != null)
 *        list.add(w);</pre>
 *
 * So a ship whose weapon type is misspelt — or whose weapon class has not been written yet — is
 * built perfectly happily <b>with that weapon silently absent</b>. It flies, it allocates power, it
 * takes damage, and the gun is simply not there. Nothing fails; the stderr line is one of thousands
 * in a build log.
 *
 * <p>{@code CatalogueWeaponsBuildTest} closes exactly this hole for the <b>shuttle</b> catalogue,
 * after the Romulan G-1 flew unarmed because {@code FighterPlasmaF} was in the data before the class
 * existed. Its comment claimed the ship half was "caught by {@code ShipJsonKeyGuardTest}'s sibling
 * checks" — it is not. That guard checks which <i>keys</i> a ship file may use, and a weapon's
 * {@code type} is a <i>value</i>; "Disruptor" and "Disrupter" are equally valid keys.
 *
 * <h2>Why the ship half matters more, not less</h2>
 * Ship files are where the volume is — 270-odd hulls against the catalogue's few dozen craft — and
 * they are the ones entered in long batches by copying a neighbour, which is how a weapon type gets
 * carried to a hull that should not have it or mistyped on the way. The data also runs ahead of the
 * engine deliberately: Lyran hulls have declared ESGs since long before G23.0's ring mechanics were
 * started. That is fine, and is precisely why it must be a class that exists and builds rather than
 * a name that silently evaporates.
 */
public class ShipWeaponsBuildTest {

    @BeforeClass
    public static void loadData() {
        ShipLibrary.loadAllSpecs("../data/factions");
        assertTrue("fixture: the library should have loaded the hulls",
                ShipLibrary.all().size() > 100);
    }

    @Test
    public void everyWeaponOnEveryHullBuilds() {
        List<String> broken = new ArrayList<>();
        int built = 0;

        for (ShipSpec spec : ShipLibrary.all()) {
            if (spec.weapons == null)
                continue;
            for (ShipSpec.WeaponSpec ws : spec.weapons) {
                Weapon w = WeaponFactory.build(ws, ws.arcs);
                if (w == null)
                    broken.add("  " + spec.faction + "/" + spec.type + " weapon '" + ws.designator
                            + "' of type '" + ws.type + "' did not build");
                else
                    built++;
            }
        }

        assertTrue("fixture: the hulls should carry plenty of weapons, got " + built, built >= 1000);
        assertTrue("weapon types the factory does not know, so the SHIP is built without that gun"
                + " and nothing complains:\n" + String.join("\n", broken), broken.isEmpty());
    }

    /**
     * Every hull carries at least one weapon.
     *
     * <p>Not true of shuttles — an EW pod platform is a real design — but a warship with nothing to
     * shoot is a file that lost its weapons block, which is the failure mode a whole-array typo
     * produces rather than a single-gun one. Bases and civilian hulls are the honest exceptions, so
     * they are named rather than filtered by a flag: a freighter that quietly stopped declaring its
     * defensive phasers should still fail.
     */
    @Test
    public void everyHullHasAtLeastOneWeaponThatBuilt() {
        List<String> unarmed = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            int built = 0;
            if (spec.weapons != null)
                for (ShipSpec.WeaponSpec ws : spec.weapons)
                    if (WeaponFactory.build(ws, ws.arcs) != null)
                        built++;
            if (built == 0)
                unarmed.add(spec.faction + "/" + spec.type + " (" + spec.typeName + ")");
        }

        assertEquals("hulls that built with no weapons at all: " + unarmed, List.of(), unarmed);
    }

    /**
     * A roll-call of the weapon type names the ship data actually uses.
     *
     * <p>Printed rather than asserted against a fixed list, which would have to be edited every time
     * a hull is entered. Its value is in the failure above: when a type does not build, the operator
     * wants to know whether it is a typo of something here or a class nobody has written.
     */
    @Test
    public void theWeaponTypesInUseAreAllKnownToTheFactory() {
        Set<String> types = new LinkedHashSet<>();
        for (ShipSpec spec : ShipLibrary.all())
            if (spec.weapons != null)
                for (ShipSpec.WeaponSpec ws : spec.weapons)
                    if (ws.type != null)
                        types.add(ws.type);

        List<String> unknown = new ArrayList<>();
        for (String type : types) {
            ShipSpec.WeaponSpec probe = new ShipSpec.WeaponSpec();
            probe.type = type;
            probe.designator = "probe";
            if (WeaponFactory.build(probe, null) == null)
                unknown.add(type);
        }

        assertFalse("fixture: the ship data should name some weapon types", types.isEmpty());
        assertEquals("weapon type names the ship data uses that the factory cannot build"
                + " (in use: " + types + "): " + unknown, List.of(), unknown);
    }
}
