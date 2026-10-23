package com.sfb.objects;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Structural integrity of the ship library itself, as distinct from the contents of any one file.
 *
 * <h2>Why this exists</h2>
 * {@code ShipLibrary} keys specs on faction + type and loaded them with a bare
 * {@code registry.put}, so a second file declaring an existing type silently replaced the first.
 * The ship did not become wrong — it ceased to exist, and <b>every other guard kept passing,
 * because every other guard walks the registry.</b> A hull that was never registered cannot fail
 * a test about registered hulls.
 * <p>
 * It happened on 2026-10-03: a Hydran file named {@code ah.json} still carried
 * {@code "type": "EH"} from the file it was copied out of, so {@code eh.json} overwrote it and
 * the Y175 aegis-FULL Escort Hunter was absent from the game. Nothing reported it; it was found
 * only by sweeping the files by hand. These guards are what should have found it.
 */
public class ShipLibraryIntegrityTest {

    private static final String ROOT = "../data/factions";

    /**
     * No two files may claim the same faction + type.
     * <p>
     * Walks the FILES rather than asking {@code ShipLibrary.duplicateKeys()}, deliberately: the
     * registry is the thing that loses information here, so a guard that trusted the library's own
     * account of what it dropped would share the blind spot it is meant to cover.
     */
    @Test
    public void noTwoFilesClaimTheSameFactionAndType() throws Exception {
        Map<String, String> firstSeen = new LinkedHashMap<>();
        List<String> clashes = new ArrayList<>();
        walk(new File(ROOT), firstSeen, clashes);

        assertEquals("Two ship files declare the same faction and type, so ShipLibrary keeps only"
                        + " one and the other hull does not exist in the game:\n  "
                        + String.join("\n  ", clashes),
                List.of(), clashes);
    }

    /** And the library agrees it saw none, which is what the runtime warning keys off. */
    @Test
    public void theLibraryReportsNoDuplicatesAfterLoading() {
        ShipLibrary.loadAllSpecs(ROOT);
        assertEquals("ShipLibrary recorded faction/type collisions during load",
                Map.of(), ShipLibrary.duplicateKeys());
    }

    /**
     * Loading twice must not report the whole library as colliding with itself.
     * <p>
     * The registry is deliberately never cleared — several test classes load the tree per JVM and
     * re-putting identical specs is harmless — so collision tracking has to reset per load. This
     * pins that, because getting it wrong would turn the guard above into a permanent red that
     * everyone learns to ignore.
     */
    @Test
    public void loadingTwiceReportsNoCollisions() {
        ShipLibrary.loadAllSpecs(ROOT);
        ShipLibrary.loadAllSpecs(ROOT);
        assertEquals(Map.of(), ShipLibrary.duplicateKeys());
    }

    /** Every registered spec can actually be looked up by the key its own file declares. */
    @Test
    public void everySpecIsReachableByItsOwnFactionAndType() {
        ShipLibrary.loadAllSpecs(ROOT);
        List<String> unreachable = new ArrayList<>();
        for (ShipSpec spec : ShipLibrary.all()) {
            ShipSpec found = ShipLibrary.get(spec.faction, spec.type);
            if (found == null)
                unreachable.add(spec.faction + "/" + spec.type + " is registered but get() misses it");
            else if (found != spec)
                unreachable.add(spec.faction + "/" + spec.type + " resolves to a different spec ("
                        + found.name + " vs " + spec.name + ")");
        }
        assertEquals(List.of(), unreachable);
    }

    private void walk(File dir, Map<String, String> firstSeen, List<String> clashes)
            throws Exception {
        File[] entries = dir.listFiles();
        if (entries == null)
            return;
        for (File e : entries) {
            if (e.isDirectory()) {
                walk(e, firstSeen, clashes);
                continue;
            }
            if (!e.getName().endsWith(".json"))
                continue;
            ShipSpec spec;
            try {
                spec = ShipSpec.fromJson(e);
            } catch (Exception ignored) {
                continue;   // not every json under data/factions is a ship
            }
            if (spec.type == null || spec.type.isBlank())
                continue;   // same filter ShipLibrary applies
            String key = spec.faction + "/" + spec.type;
            String prior = firstSeen.put(key, e.getName());
            if (prior != null)
                clashes.add(key + " : " + prior + " and " + e.getName());
        }
    }

    /**
     * E11.16: a PPD cannot be installed on a ship smaller than size class 3.
     *
     * <h2>Why this is a DATA guard and not a load-time refusal</h2>
     * A hull either has a legal PPD or the file is wrong; there is no runtime decision to make
     * and nothing a player can do about it. Refusing to load the hull would take a working game
     * away over a typo, so the build fails instead, naming the file.
     *
     * <h2>Why it is not generalised to the other weapons that carry a size bar</h2>
     * {@code OptionCatalogEntry.maxSizeClass} puts the same bar on five more options — Plasma-S
     * (both swivel variants), Disruptor-30, Disruptor-40 and Hellbore — and the obvious move is
     * to walk all six here. It would be WRONG, and the data says so plainly: seven size-class-4
     * hulls carry a Hellbore, four carry a long disruptor, and one size-class-5 hull carries a
     * plasma-S. Every one of them is correct.
     * <p>
     * The bar in Annex #8B is a rule about what an Orion may buy INTO AN OPTION MOUNT on a small
     * hull. It is not a property of the weapon, and a native hull mounting the same gun at size
     * class 4 breaks nothing. E11.16 is different in kind: it is stated about the device itself,
     * in the PPD's own rules, and applies wherever a PPD is installed.
     * <p>
     * Recorded at this length because "the catalogue already knows about five more of these"
     * is a genuinely attractive thought, and acting on it turns a green suite red twelve times
     * over against data that is right.
     */
    @Test
    public void noHullSmallerThanSizeClass3CarriesAPpd() throws Exception {
        Map<String, String> offenders = new LinkedHashMap<>();
        walkForPpds(new File(ROOT), offenders);
        assertTrue("E11.16 bars a PPD below size class 3, but: " + offenders,
                offenders.isEmpty());
    }

    private void walkForPpds(File dir, Map<String, String> offenders) {
        File[] entries = dir.listFiles();
        if (entries == null)
            return;
        for (File e : entries) {
            if (e.isDirectory()) {
                walkForPpds(e, offenders);
                continue;
            }
            if (!e.getName().endsWith(".json"))
                continue;
            ShipSpec spec;
            try {
                spec = ShipSpec.fromJson(e);
            } catch (Exception ignored) {
                continue;
            }
            if (spec.type == null || spec.type.isBlank())
                continue;

            // Refits count: a refit that adds a PPD installs one just as the base hull does.
            List<ShipSpec.WeaponSpec> all = new ArrayList<>();
            if (spec.weapons != null)
                all.addAll(spec.weapons);
            if (spec.refits != null)
                for (ShipSpec.RefitSpec r : spec.refits)
                    if (r.addWeapons != null)
                        all.addAll(r.addWeapons);

            for (ShipSpec.WeaponSpec w : all)
                if ("PPD".equals(w.type) && spec.sizeClass > 3)
                    offenders.put(e.getName(),
                            spec.faction + " " + spec.type + " is size class " + spec.sizeClass);
        }
    }
}
