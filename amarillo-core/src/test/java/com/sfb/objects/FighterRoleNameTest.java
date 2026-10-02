package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Every role name in the data must be one the engine knows.
 * <p>
 * This exists because the failure is SILENT in both directions, and in a way no other test
 * catches:
 * <ul>
 * <li>A role in a LINE era that {@code FighterComplement.ROLES} does not list is simply never
 *     asked for — {@code typesFor} iterates ROLES, so the era's entry is inert.</li>
 * <li>A role in a SHIP file that ROLES does not list is worse. {@code fromBayMap} reads counts
 *     only for known keys, so the count is dropped, the bay seats fewer fighters than its SSD
 *     says, and {@code total()} agrees with the mistake. No error anywhere.</li>
 * </ul>
 * Both halves matter for the same reason: adding a role takes three separate edits
 * ({@code ROLES}, the field on {@code ShipSpec.FighterComplementSpec}, and the put in
 * {@code toInitMap}), and nothing else notices when one is missed. Written BEFORE the
 * superiority/assault rename for exactly that reason — a half-finished rename would otherwise
 * have emptied bays quietly.
 */
public class FighterRoleNameTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** Keys a bay's {@code fighters} block may carry besides its role counts. */
    private static final List<String> NOT_ROLES = List.of("line");

    @Test
    public void everyRoleNamedByALineIsOneTheEngineKnows() {
        List<String> problems = new ArrayList<>();
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                for (Map.Entry<String, String> role : era.roles().entrySet())
                    if (!FighterComplement.ROLES.contains(role.getKey()))
                        problems.add("fighterLines." + line + " era from Y" + era.from
                                + " names role '" + role.getKey() + "'");

        assertTrue("role names the engine does not know, so nothing will ever ask for them:\n  "
                + String.join("\n  ", problems)
                + "\nKnown roles: " + FighterComplement.ROLES, problems.isEmpty());
    }

    @Test
    public void everyRoleNamedByAShipFileIsOneTheEngineKnows() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> problems = new ArrayList<>();

        File root = new File("../data/factions");
        File[] factions = root.listFiles(File::isDirectory);
        assertNotNull("no faction directories under " + root.getAbsolutePath(), factions);

        for (File faction : factions) {
            File[] files = faction.listFiles(f -> f.getName().endsWith(".json"));
            if (files == null)
                continue;
            for (File file : files) {
                JsonNode ship = mapper.readTree(file);
                for (JsonNode bay : ship.path("shuttleBays")) {
                    JsonNode fighters = bay.path("fighters");
                    if (!fighters.isObject())
                        continue;
                    fighters.fieldNames().forEachRemaining(name -> {
                        if (!NOT_ROLES.contains(name) && !FighterComplement.ROLES.contains(name))
                            problems.add(faction.getName() + "/" + file.getName()
                                    + " declares '" + name + "'");
                    });
                }
            }
        }

        assertTrue("ship files declaring role counts the engine will silently drop:\n  "
                + String.join("\n  ", problems)
                + "\nKnown roles: " + FighterComplement.ROLES, problems.isEmpty());
    }

    /**
     * The fallback target has to be a role in its own right, or every era that omits a role
     * would fall back to nothing. {@code LineEra.typeFor} returns {@code byRole.get(STANDARD)}
     * when a role is absent, so this is the one role name that is load-bearing in CODE as well
     * as data — and the one a rename is most likely to half-finish.
     */
    @Test
    public void theFallbackRoleIsItselfANamedRole() {
        assertTrue("LineEra.STANDARD ('" + ShuttleCatalog.LineEra.STANDARD
                        + "') must be one of " + FighterComplement.ROLES,
                FighterComplement.ROLES.contains(ShuttleCatalog.LineEra.STANDARD));
    }

    /** Every line must offer the fallback role in every era, or omissions resolve to nothing. */
    @Test
    public void everyEraOffersTheFallbackRole() {
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                assertTrue(line + " era from Y" + era.from + " has no '"
                                + ShuttleCatalog.LineEra.STANDARD
                                + "' entry, so any role it omits resolves to nothing: " + era,
                        era.hasOwnTypeFor(ShuttleCatalog.LineEra.STANDARD));
    }

    // ------------------------------------------------- the assault role, end to end

    /**
     * Adding a role takes THREE separate edits and nothing else notices a missed one, so these
     * walk the whole chain for {@code assault} rather than trusting that it was added.
     * <p>
     * First edit: {@code FighterComplement.ROLES}, which is what {@code fromBayMap} reads
     * counts for. Miss it and the count is dropped with no error.
     */
    @Test
    public void anAssaultCountSurvivesTheBayMap() {
        Map<String, Object> bay = new java.util.LinkedHashMap<>();
        bay.put("line", "kzinti-attack");
        bay.put("superiority", 4);
        bay.put("assault", 3);
        bay.put("ew", 1);

        FighterComplement c = FighterComplement.fromBayMap(bay);

        assertNotNull(c);
        assertEquals("the assault count must survive", 3, c.countOf("assault"));
        assertEquals("and be counted in the total", 8, c.total());
    }

    /** Second edit: the typed field on the spec, and its own total(). */
    @Test
    public void theSpecCountsAssaultInItsTotal() {
        ShipSpec.FighterComplementSpec spec = new ShipSpec.FighterComplementSpec();
        spec.line = "kzinti-attack";
        spec.superiority = 4;
        spec.attack = 2;
        spec.assault = 3;
        spec.ew = 1;

        assertEquals(10, spec.total());
    }

    /**
     * Third edit: the put in {@code ShipSpec.toInitMap}, which is the bridge between the two
     * above. A ship file's counts reach {@code fromBayMap} only through it, so a miss here is
     * the one that empties bays while every other test still passes.
     */
    @Test
    public void aShipFilesAssaultCountReachesTheComplement() throws Exception {
        // turnMode is required: toInitMap passes it to TurnMode.valueOf, which throws on null.
        String json = "{\"faction\":\"Kzinti\",\"type\":\"TEST\",\"turnMode\":\"A\","
                + "\"shuttleBays\":"
                + "[{\"shuttles\":[\"admin\"],\"fighters\":{\"line\":\"kzinti-attack\","
                + "\"superiority\":2,\"assault\":3,\"ew\":1}}]}";
        ShipSpec spec = new ObjectMapper().readValue(json, ShipSpec.class);

        assertEquals("parsed off the file", 3, spec.shuttleBays.get(0).fighters.assault);

        // "shuttlebays", all lower case — toInitMap writes the keys Ship.init reads, and that
        // casing convention has bitten before (serviceYear vs serviceyear cost every carrier its
        // era once). Asserted rather than assumed.
        Object bays = spec.toInitMap().get("shuttlebays");
        assertNotNull("toInitMap writes lower-case keys", bays);
        Object bayFighters = ((Map<?, ?>) ((List<?>) bays).get(0)).get("fighters");
        FighterComplement c = FighterComplement.fromBayMap(bayFighters);

        assertNotNull("the complement must survive toInitMap", c);
        assertEquals("carried through the values map", 3, c.countOf("assault"));
        assertEquals(6, c.total());
    }

    /**
     * The annex's own fallback for this role: "If the ship is not operating assault fighters, it
     * will [be the] same as the other spare fighter." No line has an assault fighter yet, so an
     * assault slot takes the superiority type.
     */
    @Test
    public void assaultSlotsTakeTheSuperiorityFighterWhileNoLineHasOne() {
        Map<String, Object> bay = new java.util.LinkedHashMap<>();
        bay.put("line", "kzinti-attack");
        bay.put("superiority", 1);
        bay.put("assault", 2);

        List<String> types = FighterComplement.fromBayMap(bay).typesFor(170);

        assertEquals("three seats filled", 3, types.size());
        assertEquals("all of them the Y170 superiority fighter",
                1, new java.util.HashSet<>(types).size());
    }

    /**
     * A ship must not declare a role its LINE never offers in any era.
     * <p>
     * This is the gap the superiority/assault rename exposed, and the name guards above cannot
     * see it: once {@code attack} and {@code assault} are BOTH valid names, a line that moved its
     * heavy-weapon fighter to {@code assault} while a carrier still declares {@code attack}
     * passes every other check — and those slots silently fall back to the superiority fighter.
     * The Kzinti CVA would have fielded six TADS instead of six DAS, with nothing to say so.
     * <p>
     * Deliberately tolerant of the legitimate case: a role no era offers YET is fine, which is
     * how a Hydran RN declares EW fighters in Y134 before any existed. Only a role NO era ever
     * offers is a mismatch, and only when the count is above zero — a declared zero is just a
     * carrier saying it carries none.
     */
    @Test
    public void noShipDeclaresARoleItsLineNeverOffers() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> problems = new ArrayList<>();

        File root = new File("../data/factions");
        for (File faction : root.listFiles(File::isDirectory)) {
            File[] files = faction.listFiles(f -> f.getName().endsWith(".json"));
            if (files == null)
                continue;
            for (File file : files) {
                JsonNode ship = mapper.readTree(file);
                for (JsonNode bay : ship.path("shuttleBays")) {
                    JsonNode fighters = bay.path("fighters");
                    if (!fighters.isObject())
                        continue;
                    String line = fighters.path("line").asText("");
                    if (line.isEmpty())
                        continue;

                    java.util.Set<String> everOffered = new java.util.LinkedHashSet<>();
                    for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                        everOffered.addAll(era.roles().keySet());
                    if (everOffered.isEmpty())
                        continue;             // unknown line is another test's business

                    fighters.fieldNames().forEachRemaining(role -> {
                        if (NOT_ROLES.contains(role) || fighters.path(role).asInt(0) <= 0)
                            return;
                        if (!everOffered.contains(role))
                            problems.add(faction.getName() + "/" + file.getName()
                                    + " declares " + fighters.path(role).asInt() + " '" + role
                                    + "' but line '" + line + "' never offers that role"
                                    + " (it offers " + everOffered + ")");
                    });
                }
            }
        }

        assertTrue("a ship's slots would fall back to superiority fighters with no error:\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }
}
