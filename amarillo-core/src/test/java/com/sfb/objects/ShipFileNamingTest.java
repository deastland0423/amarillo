package com.sfb.objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * A ship file must name the type it declares.
 *
 * <h2>The error class this exists for, which no other guard can see</h2>
 * Hulls are entered by hand from the SSDs, and a new one starts life as a copy of its neighbour.
 * Forget to change {@code type} and one of three things happens:
 * <ul>
 *   <li>The copy collides with its source, and {@code ShipLibraryIntegrityTest} catches it — because
 *       faction+type is the registry key and one of the two hulls stops existing.</li>
 *   <li>The copy collides with a THIRD file, same thing.</li>
 *   <li><b>The copy collides with nothing, and every guard passes.</b> {@code dwb.json} declared
 *       {@code DWb} and no test in the build could tell: it is unique, it is a real string, nothing
 *       is overwritten. It is simply the wrong hull under the wrong name, and it would have gone
 *       into play that way.</li>
 * </ul>
 * This closes the third case by checking the file against its own name, which is the one piece of
 * information a copy-paste cannot silently carry over.
 *
 * <h2>Walking the FILES, not the library</h2>
 * Deliberate, and the same reason {@code ShipLibraryIntegrityTest} does it: a guard that walks
 * {@code ShipLibrary} cannot see a hull that never registered. The discarded half of a duplicate is
 * exactly what this is looking for.
 */
public class ShipFileNamingTest {

    private static final File FACTIONS = new File("../data/factions");

    /**
     * Files whose name describes the class rather than spelling the type code.
     * <p>
     * The Condors are the only two, and they are listed rather than pattern-matched on purpose: an
     * exception should cost a deliberate edit here, which is the whole value of a drift guard. If
     * this list starts growing, the convention has changed and should be written down instead.
     */
    private static final Set<String> NAMED_FOR_THE_CLASS = Set.of(
            "romulan/condor.json", "romulan/condor+.json");

    /**
     * How a filename and a type are compared.
     * <p>
     * Case is ignored, and the hyphen is dropped because a type may carry one where a filename does
     * not — the Hydran {@code GEN-F} lives in {@code genf.json}. The PLUS is kept, because it is the
     * refit marker and dropping it would make {@code dws+.json} match a bare {@code DWS}, which is
     * one of the very mistakes this is here to catch.
     */
    private static String normalise(String s) {
        return s.toLowerCase().replace("-", "");
    }

    private List<File> shipFiles() {
        List<File> files = new ArrayList<>();
        File[] factions = FACTIONS.listFiles(File::isDirectory);
        if (factions == null)
            return files;
        for (File faction : factions) {
            File[] ships = faction.listFiles(n -> n.getName().endsWith(".json"));
            if (ships != null)
                for (File f : ships)
                    files.add(f);
        }
        return files;
    }

    private static String relative(File f) {
        return f.getParentFile().getName() + "/" + f.getName();
    }

    @Test
    public void everyShipFileNamesTheTypeItDeclares() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (File f : shipFiles()) {
            String rel = relative(f);
            if (NAMED_FOR_THE_CLASS.contains(rel))
                continue;
            String type = new ObjectMapper().readTree(f).path("type").asText("");
            if (type.isBlank())
                continue;       // a file with no type is a different guard's business
            checked++;
            String base = f.getName().substring(0, f.getName().length() - ".json".length());
            if (!normalise(type).equals(normalise(base)))
                wrong.add(rel + " declares type '" + type + "'");
        }

        assertTrue("there should be ship files to check", checked > 50);
        String indent = System.lineSeparator() + "  ";
        assertTrue("a ship file whose name and type disagree — usually a copy whose type was not"
                + " changed:" + indent + String.join(indent, wrong), wrong.isEmpty());
    }

    /**
     * A lowercase letter in a type is only ever a trailing modifier, and only one of three.
     *
     * <p>The data says so unanimously: twenty-one hulls carry a lowercase letter, every one of them
     * is the last character, and every one is {@code a} (AWR refit), {@code p} (phaser refit) or
     * {@code u} (UIM refit) — {@code DNa}, {@code FFAp}, {@code D7Ku}, {@code CC+a}, {@code FFE+p}.
     * A primary code is uppercase without exception: the power-pack refit is {@code B} in all
     * fourteen of its hulls.
     *
     * <p>Which is how {@code DWb} was wrong and invisible. It breaks no key, so nothing else
     * notices; it is simply a secondary modifier where a primary code belongs, and in any list of
     * types it reads as a different kind of ship from its thirteen siblings.
     */
    @Test
    public void aLowercaseLetterIsOnlyEverATrailingModifier() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        List<String> wrong = new ArrayList<>();
        int modifiers = 0;
        for (File f : shipFiles()) {
            String type = new ObjectMapper().readTree(f).path("type").asText("");
            if (type.isBlank())
                continue;
            for (int i = 0; i < type.length(); i++) {
                char c = type.charAt(i);
                if (!Character.isLowerCase(c))
                    continue;
                if (i != type.length() - 1)
                    wrong.add(relative(f) + " has '" + c + "' inside type '" + type + "'");
                else if ("apu".indexOf(c) < 0)
                    wrong.add(relative(f) + " ends type '" + type + "' in '" + c
                            + "'; the refit modifiers are a, p and u, and a primary code is upper case");
                else
                    modifiers++;
            }
        }

        assertTrue("the data should still hold some a/p/u refits", modifiers > 10);
        String indent = System.lineSeparator() + "  ";
        assertTrue("unexpected lower case in a ship type:" + indent + String.join(indent, wrong),
                wrong.isEmpty());
    }

    /**
     * Every exception above is still earning its place.
     * <p>
     * A stale exemption is worse than none: it silently un-guards a file that has since been
     * renamed to match, and nobody finds out. So the list is checked against the data too.
     */
    @Test
    public void everyNamedForTheClassFileStillNeedsItsExemption() throws Exception {
        assumeTrue("data/factions must exist", FACTIONS.isDirectory());

        for (String rel : NAMED_FOR_THE_CLASS) {
            File f = new File(FACTIONS, rel);
            assertTrue(rel + " is exempted but does not exist", f.isFile());
            String type = new ObjectMapper().readTree(f).path("type").asText("");
            String base = f.getName().substring(0, f.getName().length() - ".json".length());
            assertNotEquals(rel + " now matches its own name, so the exemption can go",
                    normalise(type), normalise(base));
        }
    }
}
