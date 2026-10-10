package com.sfb.objects;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Every piece of token art that exists is claimed by a ship file.
 *
 * <h2>Why this direction and not the other</h2>
 * Two things can go wrong between the ship data and {@code amarillo-web/public/tokens}. A ship can
 * name art that does not exist, or art can exist that no ship names. They are not equally
 * interesting.
 *
 * <p>The first is a KNOWN, DELIBERATE state: about seventy hulls name art that has not been drawn
 * yet, including whole families pointing at one shared image. The owner's Federation and Klingon
 * tokens were commissioned from an artist who is no longer available, so those references are
 * waiting on a decision about how to produce more (2026-10-10). {@code loadTokenImage} falls back
 * to a faction-coloured circle on error by design, so the game plays correctly meanwhile. A guard
 * on that direction would be seventy lines of parked entries restating something already known,
 * and would have to be edited every time a picture arrives.
 *
 * <p>The second is the silent one, and the reason this file exists. The art is finished — drawn,
 * paid for, sitting in the repository — and one missing line in a JSON file means nobody ever sees
 * it. Nothing anywhere complains: the hull simply renders as a circle, exactly as a hull with no
 * art does. The owner found the Hydran Lancer this way, by noticing in play that a ship he had art
 * for was not showing it, and four more were in the same state behind it.
 *
 * <p>So: a picture in the tree is a commitment that something uses it — a hull's own
 * {@code tokenArt}, or a craft's row in the shuttle catalogue, which names its counter
 * the same way since a fighter is a row rather than a class (J4.4).
 */
public class TokenArtClaimedTest {

    private static final File SHIP_DIR  = new File("../data/factions");
    private static final File TOKEN_DIR = new File("../amarillo-web/public/tokens");

    private static Set<String> claimed;

    @BeforeClass
    public static void collectClaims() throws Exception {
        ShipLibrary.loadAllSpecs(SHIP_DIR.getPath());
        ShuttleCatalog.loadDefault("../data");
        claimed = new HashSet<>();
        for (ShipSpec spec : ShipLibrary.all())
            claim(spec.tokenArt);
        // Craft name their own counters too (J4.4: a fighter is a catalogue ROW, not a class).
        // The three Stinger pictures were parked in this file while nothing outside ShipSpec
        // could name one; that stopped being true on 2026-10-10 and the parking came out the
        // same day, which is the only way a parked list stays worth reading.
        for (ShuttleCatalog.Entry entry : ShuttleCatalog.all())
            claim(entry.tokenArt);
    }

    private static void claim(String art) {
        if (art != null && !art.isBlank())
            claimed.add(art.replace('\\', '/').toLowerCase());
    }

    /**
     * Art that something is expected to CLAIM. The shared pieces — shuttle, drone, plasma, and
     * everything under terrain — are named in the client by faction rather than by any row, so
     * nothing claims them and this test has nothing to say about them.
     */
    private static boolean isShipArt(String faction, String file) {
        if (faction.equalsIgnoreCase("terrain"))
            return false;
        String stem = file.toLowerCase();
        // shuttle/drone/plasma are the GENERIC per-faction counters, named in HexGrid rather
        // than by any row — a craft with no art of its own falls back to its faction's shuttle.
        return !(stem.equals("shuttle.png") || stem.equals("drone.png")
                || stem.equals("plasma.png"));
    }


    @Test
    public void everyTokenOnDiskIsNamedBySomething() {
        assertTrue("fixture: the token directory should be where this test thinks ("
                + TOKEN_DIR.getAbsolutePath() + ")", TOKEN_DIR.isDirectory());

        List<String> orphans = new ArrayList<>();
        int checked = 0;
        File[] factions = TOKEN_DIR.listFiles(File::isDirectory);
        for (File faction : factions == null ? new File[0] : factions) {
            File[] art = faction.listFiles(f -> f.getName().toLowerCase().endsWith(".png"));
            for (File piece : art == null ? new File[0] : art) {
                if (!isShipArt(faction.getName(), piece.getName()))
                    continue;
                checked++;
                String path = (faction.getName() + "/" + piece.getName()).toLowerCase();
                if (!claimed.contains(path))
                    orphans.add(path);
            }
        }

        assertTrue("fixture: there should be ship art to check", checked > 20);
        assertTrue("token art exists that no ship file names, so nothing will ever draw it."
                + " Add \"tokenArt\" to the hull it belongs to, or delete the picture:\n  "
                + String.join("\n  ", orphans), orphans.isEmpty());
    }
}
