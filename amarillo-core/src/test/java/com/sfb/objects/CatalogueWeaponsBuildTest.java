package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.Weapon;

/**
 * Every weapon the shuttle catalogue names must actually build.
 * <p>
 * The gap this closes is the worst kind. {@code WeaponFactory} answers an unrecognised type by
 * printing a line to stderr and returning NULL, so a fighter whose weapon name is misspelt — or
 * whose weapon has not been written yet — is built perfectly happily with no weapons at all. It
 * flies, it launches, it lands, and it cannot shoot. Nothing failed, no test noticed, and the
 * stderr line is one of thousands in a build log.
 * <p>
 * That is exactly what happened to the Romulan G-1: its {@code FighterPlasmaF} was in the data
 * before the class existed, and every fighter guard passed while the Gladiator carried nothing.
 * <p>
 * This checks the catalogue rather than the factory, because the catalogue is the hand-authored
 * side: the factory's job is to know a fixed set of names, and the data's job is to use them.
 * A new weapon in a ship file is caught by {@code ShipJsonKeyGuardTest}'s sibling checks; this
 * is the shuttle half, which had none.
 */
public class CatalogueWeaponsBuildTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    @Test
    public void everyWeaponNamedByEveryCatalogueEntryBuilds() {
        List<String> broken = new ArrayList<>();
        int built = 0;

        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            for (ShipSpec.WeaponSpec ws : entry.loadout.weapons()) {
                Weapon w = WeaponFactory.build(ws, ws.arcs);
                if (w == null)
                    broken.add("  " + entry.type + " weapon '" + ws.designator
                            + "' of type '" + ws.type + "' did not build");
                else
                    built++;
            }
        }

        assertTrue("fixture: the catalogue should carry plenty of weapons", built >= 40);
        assertTrue("weapon types the factory does not know, so the craft is built UNARMED and"
                + " nothing complains:\n" + String.join("\n", broken), broken.isEmpty());
    }

    /**
     * A craft the catalogue gives weapons to must end up holding them. The check above proves
     * the factory can build each recipe; this proves the built weapon reaches the craft, which
     * is a different step and the one a caller actually depends on.
     */
    @Test
    public void everyArmedCraftActuallyCarriesItsWeapons() {
        List<String> empty = new ArrayList<>();

        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            int declared = entry.loadout.weapons().size();
            if (declared == 0)
                continue;
            com.sfb.objects.shuttles.Shuttle craft =
                    com.sfb.objects.shuttles.CataloguedFighter.of(entry.type);
            if (craft == null)
                continue;   // not a fighter; another test's business
            int carried = craft.getWeapons().fetchAllWeapons().size();
            if (carried < declared)
                empty.add("  " + entry.type + " declares " + declared
                        + " weapon(s) but carries " + carried);
        }

        assertTrue("craft that lost weapons between the catalogue and the hull:\n"
                + String.join("\n", empty), empty.isEmpty());
    }
}
