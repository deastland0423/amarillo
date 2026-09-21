package com.sfb.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.properties.Location;
import com.sfb.samples.FederationShips;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The shared hex-geometry fixture: bearings and ranges for a grid of hex pairs, checked in
 * and asserted by BOTH tiers — this test against {@link MapUtils}, and
 * amarillo-web/src/hex/geometry.test.ts against the TypeScript mirror.
 * <p>
 * The client carries a hand port of the zone-based bearing algorithm so it can preview
 * which weapons bear without a round trip. A mirror that has drifted is worse than no
 * mirror, because it looks authoritative — and this one has drifted before, which is why
 * it carries a standing warning against geometric atan2. Neither copy can now move without
 * one of the two test suites going red.
 * <p>
 * The grid runs from an EVEN and an ODD source column, because the offset layout makes
 * neighbours parity-sensitive and half the ways to get this wrong only show up in one
 * parity. It includes same-column and same-row pairs (the due-north and the vertex cases),
 * short range where zones are narrow, and sampled long range where the spine lines have
 * had room to diverge.
 * <p>
 * To regenerate after an intended change to the algorithm:
 * {@code mvn test -pl amarillo-core -Dtest=HexGeometryFixtureTest -Dhex.fixture.regenerate=true}
 * Then run the web suite too, and expect to explain why both moved.
 */
public class HexGeometryFixtureTest {

    /** Repo-root data directory, whether run from the module or the root. */
    private static final String[] PATHS = {
        "data/fixtures/hex-geometry.json",
        "../data/fixtures/hex-geometry.json",
    };

    /** Even and odd source columns: parity changes the answers. */
    private static final int[][] SOURCES = { { 10, 10 }, { 11, 10 } };

    /** Every offset in this box, from each source. */
    private static final int NEAR = 6;

    /** Sampled offsets beyond the box, where the spine lines have diverged. */
    private static final int[] FAR = { -15, -12, -9, 9, 12, 15 };

    private static File fixture() {
        for (String p : PATHS) {
            File f = new File(p);
            if (f.exists())
                return f;
        }
        // Not found: fall back to the path that exists as a directory, for regeneration.
        return new File(new File("data").isDirectory() ? PATHS[0] : PATHS[1]);
    }

    private record Pair(int fromCol, int fromRow, int toCol, int toRow) { }

    private static List<Pair> grid() {
        List<Pair> pairs = new ArrayList<>();
        for (int[] src : SOURCES) {
            for (int dc = -NEAR; dc <= NEAR; dc++)
                for (int dr = -NEAR; dr <= NEAR; dr++)
                    pairs.add(new Pair(src[0], src[1], src[0] + dc, src[1] + dr));
            for (int dc : FAR)
                for (int dr : FAR)
                    pairs.add(new Pair(src[0], src[1], src[0] + dc, src[1] + dr));
            // Straight lines out, so due north/south and the east/west vertices are covered
            // at every distance rather than only inside the box.
            for (int d = 1; d <= 20; d++) {
                pairs.add(new Pair(src[0], src[1], src[0], src[1] - d));
                pairs.add(new Pair(src[0], src[1], src[0], src[1] + d));
                pairs.add(new Pair(src[0], src[1], src[0] - d, src[1]));
                pairs.add(new Pair(src[0], src[1], src[0] + d, src[1]));
            }
        }
        return pairs;
    }

    @Test
    public void theFixtureMatchesMapUtils() throws IOException {
        if (Boolean.getBoolean("hex.fixture.regenerate")) {
            regenerate();
            return;
        }

        File f = fixture();
        assertTrue("fixture missing at " + f.getAbsolutePath()
                + " — regenerate with -Dhex.fixture.regenerate=true", f.exists());

        JsonNode root = new ObjectMapper().readTree(f);
        JsonNode cases = root.path("cases");
        assertTrue("fixture should carry a decent grid", cases.size() > 500);

        for (JsonNode c : cases) {
            Location from = new Location(c.path("fromCol").asInt(), c.path("fromRow").asInt());
            Location to   = new Location(c.path("toCol").asInt(), c.path("toRow").asInt());
            String where = "(" + from.getX() + "|" + from.getY() + ") -> ("
                    + to.getX() + "|" + to.getY() + ")";

            assertEquals("bearing " + where, c.path("bearing").asInt(),
                    MapUtils.getBearing(from, to));
            assertEquals("range " + where, c.path("range").asInt(),
                    MapUtils.getRange(from, to));
        }

        // Shield numbering: the six hexes touching a ship are its six shield facings, and
        // the SSD panel writes the strengths onto them. Core reaches that number through a
        // twelve-point scheme of its own, so agreement is worth asserting rather than
        // assuming — a mislabelled ring means reinforcing the wrong shield.
        JsonNode shields = root.path("shieldRing");
        assertEquals("six facings times six adjacent hexes, from two parities",
                6 * 6 * 2, shields.size());
        for (JsonNode sh : shields) {
            Location shipAt = new Location(sh.path("shipCol").asInt(), sh.path("shipRow").asInt());
            Location from = new Location(sh.path("fromCol").asInt(), sh.path("fromRow").asInt());
            assertEquals("shield facing " + sh.path("facing").asInt() + " attacked from ("
                            + from.getX() + "|" + from.getY() + ")",
                    sh.path("shieldNum").asInt(), shieldNumberFor(shipAt, sh.path("facing").asInt(), from));
        }

        // Relative bearing: the conversion that decides which way an arc points. A mirror
        // could get every true bearing right and still draw every arc rotated.
        JsonNode relatives = root.path("relativeBearings");
        assertEquals("all 24 bearings against all 6 facings", 24 * 6, relatives.size());
        for (JsonNode r : relatives)
            assertEquals("relative bearing of " + r.path("trueBearing").asInt()
                            + " seen from facing " + r.path("facing").asInt(),
                    r.path("relative").asInt(),
                    MapUtils.getRelativeBearing(r.path("trueBearing").asInt(),
                            r.path("facing").asInt()));
    }

    /**
     * Every one of the 24 directions should appear, or the fixture is guarding less than it
     * looks like it is.
     */
    @Test
    public void theFixtureCoversAllTwentyFourDirections() throws IOException {
        File f = fixture();
        assertTrue("fixture missing — regenerate with -Dhex.fixture.regenerate=true", f.exists());

        boolean[] seen = new boolean[25];
        for (JsonNode c : new ObjectMapper().readTree(f).path("cases"))
            seen[c.path("bearing").asInt()] = true;

        for (int d = 1; d <= 24; d++)
            assertTrue("no case in the fixture bears on direction " + d, seen[d]);
    }

    /** What damage allocation would call the facing shield, through the real path. */
    private static int shieldNumberFor(Location shipAt, int facing, Location attackerAt) {
        Game game = new Game();
        Ship target = new Ship();
        target.init(FederationShips.getFedCa());
        target.setName("Target");
        target.setLocation(shipAt);
        target.setFacing(facing);

        Ship attacker = new Ship();
        attacker.init(FederationShips.getFedCa());
        attacker.setName("Attacker");
        attacker.setLocation(attackerAt);
        attacker.setFacing(1);

        game.getShips().add(target);
        game.getShips().add(attacker);
        return game.getShieldNumber(attacker, target);
    }

    /** The six hexes touching a hex, in offset coordinates. */
    private static List<Location> neighbours(Location of) {
        int c = of.getX(), r = of.getY();
        boolean even = c % 2 == 0;
        int up = even ? r : r - 1;          // row of the diagonal neighbours
        int down = even ? r + 1 : r;
        return List.of(
            new Location(c, r - 1),          // dead ahead when facing 1
            new Location(c + 1, up),
            new Location(c + 1, down),
            new Location(c, r + 1),
            new Location(c - 1, down),
            new Location(c - 1, up));
    }

    private void regenerate() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("note", "Generated by HexGeometryFixtureTest with -Dhex.fixture.regenerate=true. "
                + "Asserted by that test against MapUtils and by amarillo-web/src/hex/geometry.test.ts "
                + "against the TypeScript mirror. Do not hand-edit.");
        ArrayNode cases = root.putArray("cases");

        for (Pair p : grid()) {
            Location from = new Location(p.fromCol(), p.fromRow());
            Location to   = new Location(p.toCol(), p.toRow());
            ObjectNode c = cases.addObject();
            c.put("fromCol", p.fromCol());
            c.put("fromRow", p.fromRow());
            c.put("toCol", p.toCol());
            c.put("toRow", p.toRow());
            c.put("bearing", MapUtils.getBearing(from, to));
            c.put("range", MapUtils.getRange(from, to));
        }

        ArrayNode relatives = root.putArray("relativeBearings");
        for (int facing : new int[] { 1, 5, 9, 13, 17, 21 })
            for (int trueBearing = 1; trueBearing <= 24; trueBearing++) {
                ObjectNode r = relatives.addObject();
                r.put("trueBearing", trueBearing);
                r.put("facing", facing);
                r.put("relative", MapUtils.getRelativeBearing(trueBearing, facing));
            }

        ArrayNode shields = root.putArray("shieldRing");
        for (int[] src : SOURCES) {
            Location shipAt = new Location(src[0], src[1]);
            for (int facing : new int[] { 1, 5, 9, 13, 17, 21 })
                for (Location n : neighbours(shipAt)) {
                    ObjectNode sh = shields.addObject();
                    sh.put("shipCol", shipAt.getX());
                    sh.put("shipRow", shipAt.getY());
                    sh.put("facing", facing);
                    sh.put("fromCol", n.getX());
                    sh.put("fromRow", n.getY());
                    sh.put("shieldNum", shieldNumberFor(shipAt, facing, n));
                }
        }

        File f = fixture();
        f.getParentFile().mkdirs();
        mapper.writerWithDefaultPrettyPrinter().writeValue(f, root);
        System.out.println("Wrote " + cases.size() + " cases to " + f.getAbsolutePath());
    }
}
