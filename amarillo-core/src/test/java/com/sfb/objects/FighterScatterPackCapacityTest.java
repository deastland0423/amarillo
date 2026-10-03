package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.sfb.weapons.DroneRail;
import com.sfb.weapons.Weapon;
import com.sfb.objects.shuttles.CataloguedFighter;
import com.sfb.objects.shuttles.Fighter;

/**
 * A fighter's scatter-pack capacity is the capacity of its drone rails (FD7.11, FD7.44).
 * <p>
 * The owner's rule, 2026-10-02: <b>each STANDARD rail is worth 1, each SPECIAL rail 1, and each
 * LIGHT rail 0.5.</b> A light rail takes a single type-VI drone (J4.232) and a type-VI is half a
 * drone space, which is where the half comes from.
 * <p>
 * This is a GUARD on data that had drifted eight times in thirty rows before it existed, always
 * the same way: the rails changed across an era or a refit and the figure stayed behind. The
 * Kzinti TADS and TADSC kept the TAAS's 3 after gaining SPECIAL rails; the DASC and the Klingon
 * Z-DC kept the DAS's 1 after the C-refit (J4.232) turned their light rails standard. Nothing
 * noticed, because a scatter-pack capacity is only wrong when somebody loads one.
 * <p>
 * <b>EW fighters are exempt, and that is the rule rather than an excuse.</b> An EW pod rides on a
 * standard drone rail and can ride on no other kind (J4.2312), so an EW variant's standard rails
 * are spoken for and its declared capacity already accounts for them. Deriving it would overstate
 * every EW fighter in the game.
 */
public class FighterScatterPackCapacityTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** FD7.44 as the owner states it: standard 1, special 1, light a half. */
    private static double capacityOf(DroneRail.DroneRailType type) {
        switch (type) {
            case LIGHT:    return 0.5;
            case STANDARD: return 1.0;
            case SPECIAL:  return 1.0;
            case HEAVY:    return 1.0;
            // J4.825: "No fighter in the game can use both type-D plasmas and drones, so you
            // cannot load drones on a plasma-D-armed fighter (nor vice versa)." A scatter pack
            // IS drones, so a plasma-D rail contributes nothing to the capacity for one. Stated
            // rather than left to the default below, which would read as an oversight.
            case PLASMA_D: return 0.0;
            default:       return 0.0;
        }
    }

    /**
     * J4.825's exclusivity, as a guard on the data: "No fighter in the game can use both
     * type-D plasmas and drones."
     * <p>
     * Worth pinning because the two mounts are now the same class, told apart only by rail
     * type - so a fighter with one of each is a single keystroke away and would be refused
     * only at load time, deep in a deck crew pass, if at all.
     */
    @Test
    public void noFighterMixesPlasmaDRailsWithDroneRails() {
        List<String> mixed = new ArrayList<>();
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;           // of() refuses a shuttle outright
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            boolean plasmaD = false, drones = false;
            for (Weapon w : f.getWeapons().fetchAllWeapons())
                if (w instanceof DroneRail rail) {
                    if (rail.isPlasmaD())
                        plasmaD = true;
                    else
                        drones = true;
                }
            if (plasmaD && drones)
                mixed.add("  " + entry.type + " has both plasma-D rails and drone rails");
        }
        assertTrue("fighters mixing type-D plasmas and drones (J4.825):\n"
                + String.join("\n", mixed), mixed.isEmpty());
    }

    /**
     * A fighter armed with type-D plasmas can NEVER be a scatter pack (owner's ruling,
     * 2026-10-03: "Since Romulan fighters can never use drones, they shouldn't be able to be
     * scatter packs").
     * <p>
     * It follows from J4.825 - "No fighter in the game can use both type-D plasmas and drones,
     * so you cannot load drones on a plasma-D-armed fighter" - because a scatter pack IS
     * drones (FD7.11), and FD7.211 measures a fighter's capacity for one as "its NORMAL load
     * of drones", which for these fighters is none. FP9.31 says the same of the Gorns: "No
     * Gorn fighter carries drones, even those fighters built by the Federation."
     * <p>
     * Asserted directly rather than left to the capacity arithmetic below. That arithmetic
     * does reach the same answer - a PLASMA_D rail is worth zero - but it reads as a rail
     * being SMALL rather than as drones being forbidden, and the next person to wonder why a
     * two-rail fighter carries no pack deserves the actual reason.
     *
     * <h2>What this is NOT about</h2>
     * FD7.443 says "A fighter or MRS shuttle armed with a plasma-F or plasma-Ds can be used as
     * an SP, which at least has the advantage of launching the plasma torp(s) eight impulses
     * sooner than a manned fighter can." That is a DIFFERENT thing and does not contradict the
     * ruling: FD7.44 prepares the fighter ITSELF as a scatter pack so it releases its OWN
     * seeking weapons early, where {@code scatterPackSize} is FD7.11's question of how many
     * drones a craft can carry as a pack. The first is not modelled at all; the second is
     * zero for these fighters. Recorded here because the two were conflated once already.
     */
    @Test
    public void noPlasmaDFighterCanBeAScatterPack() {
        List<String> wrong = new ArrayList<>();
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            boolean plasmaD = false;
            for (Weapon w : f.getWeapons().fetchAllWeapons())
                if (w instanceof DroneRail rail && rail.isPlasmaD())
                    plasmaD = true;
            if (!plasmaD)
                continue;
            if (entry.scatterPackSize != 0 || entry.canScatterPack())
                wrong.add("  " + entry.type + " carries type-D plasmas but declares"
                        + " scatterPackSize " + entry.scatterPackSize);
        }
        assertTrue("plasma-D fighters cannot load drones (J4.825), so none may be a scatter"
                + " pack:" + "\n" + String.join("\n", wrong), wrong.isEmpty());
    }

    /**
     * And the Romulans specifically, which is where the ruling came from: not one of their
     * fighters may be a pack, whatever it is armed with.
     */
    @Test
    public void noRomulanFighterCanBeAScatterPack() {
        int checked = 0;
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind) || !entry.factions.contains("romulan"))
                continue;
            checked++;
            assertEquals(entry.type + " is Romulan, so it carries no drones", 0,
                    entry.scatterPackSize);
            assertFalse(entry.type + " must not qualify as a scatter pack",
                    entry.canScatterPack());
        }
        assertTrue("fixture: the Romulan fighters should be in the catalogue", checked >= 4);
    }

    @Test
    public void everyNonEwFightersScatterPackCapacityMatchesItsRails() {
        List<String> problems = new ArrayList<>();

        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            // J4.2312: an EW pod rides on a standard rail and can ride on no other kind, so
            // an EW variant's standard rails are spoken for. fixedEwPods counts too: those are
            // built in rather than racked, but either way the rail is not free for a drone.
            if (entry.loadout.ewPods() + entry.loadout.fixedEwPods() > 0)
                continue;

            double derived = 0;
            int rails = 0;
            for (Weapon w : f.getWeapons().fetchAllWeapons())
                if (w instanceof DroneRail rail) {
                    derived += capacityOf(rail.getRailType());
                    rails++;
                }

            int expected = (int) derived;
            if (entry.scatterPackSize != expected)
                problems.add(entry.type + ": " + rails + " rails worth " + derived
                        + " but scatterPackSize is " + entry.scatterPackSize);
        }

        assertTrue("scatter-pack capacity must equal the rails' capacity"
                + " (STANDARD 1, SPECIAL 1, LIGHT 0.5):\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }

    /**
     * A fighter with no drone rails at all cannot be a scatter pack, which the rule gives for
     * free: all four Hydran Stingers carry fusions and no rails, so they derive to zero.
     */
    @Test
    public void aFighterWithNoRailsCannotBeAScatterPack() {
        int checked = 0;
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all()) {
            if (!"fighter".equals(entry.kind))
                continue;
            Fighter f = CataloguedFighter.of(entry.type);
            if (f == null)
                continue;
            boolean anyRail = f.getWeapons().fetchAllWeapons().stream()
                    .anyMatch(w -> w instanceof DroneRail);
            if (anyRail)
                continue;
            assertEquals(entry.type + " has no drone rails, so it holds no drones",
                    0, entry.scatterPackSize);
            assertFalse(entry.type + " must not qualify as a scatter pack (FD7.11)",
                    entry.canScatterPack());
            checked++;
        }
        assertTrue("fixture: the Hydran Stingers should be railless", checked >= 4);
    }
}
