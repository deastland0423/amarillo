package com.sfb.objects;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Art drawn for a ship the game does not have yet, with the hull it is waiting for.
     *
     * <p>A different thing from art nobody noticed: nothing CAN claim these, because there is no
     * ship file to put a {@code tokenArt} line in. The generator ran ahead of the data, which is
     * a good problem — the picture is ready the day the hull is entered from its SSD.
     *
     * <p>{@link #noParkedEntryOutlivesItsReason} is what stops this list rotting. The moment a
     * hull arrives and claims its picture, or the picture is deleted, the line here has to go —
     * the same rule {@code ShipJsonKeyGuardTest} keeps for its own parked paths, and the reason
     * the Stinger parking in this file lasted exactly one day.
     */
    private static final Map<String, String> AWAITING_A_HULL = new LinkedHashMap<>();
    static {
        AWAITING_A_HULL.put("klingon/d6m.png", "D6M mauler");
        // R13.19 ISC Minesweeper, Captain's Module C2 SSD p.45 (PDF p.46). Identified from the
        // book's own contents page rather than guessed: the ISC list there runs R13.2 through
        // R13.22 plus the two Q-ships, and .19 is the only minesweeper in it. Unblocked — it
        // needs no tug or pod work, only the sheet reading.
        AWAITING_A_HULL.put("isc/ms.png", "MS minesweeper");

        // Romulan hulls drawn 2026-10-24 ahead of their sheets. The owner identified every one
        // of these; none was guessed from the filename, because the stems are ambiguous in both
        // directions here — SNS is not the SNP, and KHK is not the KR.
        //
        // Module R4, read off that book's own contents page:
        AWAITING_A_HULL.put("romulan/phx.png", "R4.49 PHX Phoenix space control ship");
        AWAITING_A_HULL.put("romulan/th.png",  "R4.50 TH ThunderHawk battle control ship");
        AWAITING_A_HULL.put("romulan/spj.png", "R4.51 SPJ assault cruiser");
        AWAITING_A_HULL.put("romulan/spr.png", "R4.52 SPR repair cruiser");
        AWAITING_A_HULL.put("romulan/fhf.png", "R4.75 FHF FlameHawk mauler cruiser");

        // Advanced Missions, per the owner 2026-10-24:
        AWAITING_A_HULL.put("romulan/khk.png", "KHK KillerHawk-K");
        AWAITING_A_HULL.put("romulan/skd.png", "SKD SkyHawk-D minesweeper");
        AWAITING_A_HULL.put("romulan/skh.png", "SKH SkyHawk-H cargo transport");
        AWAITING_A_HULL.put("romulan/sns.png", "SNS Snipe-S+ improved sublight frigate");
        AWAITING_A_HULL.put("romulan/spd.png", "SPD SparrowHawk-D minesweeper");
        AWAITING_A_HULL.put("romulan/spf.png", "SPF SparrowHawk-F mauler");
        AWAITING_A_HULL.put("romulan/sph.png", "SPH SparrowHawk-H cargo transport");

        // The one that is not waiting on a sheet but on a DECISION. The Klingons sold the C9 to
        // the Romulans only after its B refit was done, so the Romulan hull in the game is the
        // K9RB and there is no K9R — the owner's words, 2026-10-24. This picture exists in case
        // a pre-B-refit Romulan version is ever wanted, and if that decision goes the other way
        // the right move is to delete the file, which this list will then demand.
        AWAITING_A_HULL.put("romulan/k9r.png", "K9R, a pre-B-refit Romulan C9 that may never exist");
    }

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
                if (!claimed.contains(path) && !AWAITING_A_HULL.containsKey(path))
                    orphans.add(path);
            }
        }

        assertTrue("fixture: there should be ship art to check", checked > 20);
        assertTrue("token art exists that no ship file names, so nothing will ever draw it."
                + " Add \"tokenArt\" to the hull it belongs to, or delete the picture:\n  "
                + String.join("\n  ", orphans), orphans.isEmpty());
    }

    /**
     * MS-DOS device names are still reserved on Windows, and a tokenArt path that uses one names
     * a file that cannot exist there.
     *
     * <h2>How this was found, which is the best argument for keeping it</h2>
     * The Romulan Condor's type code is CON. Its picture was called {@code romulan/con.png} and
     * had never been drawn — the owner went to draw it on 2026-10-24 and the operating system
     * refused to create the file. CON, PRN, AUX, NUL, COM1-9 and LPT1-9 are reserved with ANY
     * extension, so {@code con.png} is not a filename on Windows; it is the console.
     *
     * <p>This is not cosmetic and it is not only about the person drawing. A contributor on Linux
     * CAN create {@code con.png} and commit it, and then {@code git checkout} fails on Windows —
     * the repository stops being usable by its owner. The same reason {@code condor.json} is the
     * hull's filename and is exempted in {@code ShipFileNamingTest}: {@code con.json} cannot exist
     * either. That exemption reads as a naming preference; it is actually a hard constraint.
     *
     * <p>It bites tools too. A script here opened {@code data/factions/romulan/con.json} to make
     * the fix and hung: Python had opened the console device and was waiting on the keyboard.
     *
     * <p>Checked over the basename only, because a DIRECTORY may legally be called {@code aux}
     * on neither — but no faction is named for a device, and widening this to path segments
     * would be guessing at a problem nobody has.
     */
    @Test
    public void noTokenArtNamesAWindowsReservedDevice() {
        Set<String> reserved = new HashSet<>(List.of(
                "con", "prn", "aux", "nul",
                "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
                "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"));

        List<String> bad = new ArrayList<>();
        for (String art : claimed) {
            String base = art.substring(art.lastIndexOf('/') + 1);
            int dot = base.lastIndexOf('.');
            String stem = (dot < 0 ? base : base.substring(0, dot)).toLowerCase();
            if (reserved.contains(stem))
                bad.add(art + " — '" + stem + "' is a reserved device name; this file cannot be"
                        + " created on Windows, and a checkout that contains it fails there");
        }

        String indent = System.lineSeparator() + "  ";
        assertTrue("tokenArt naming a Windows device:" + indent + String.join(indent, bad),
                bad.isEmpty());
    }

    /**
     * A parked picture whose hull has arrived, or which has been deleted, is a line claiming
     * something untrue. The list is only worth reading while every entry is live.
     */
    @Test
    public void noParkedEntryOutlivesItsReason() {
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> e : AWAITING_A_HULL.entrySet()) {
            String path = e.getKey();
            if (claimed.contains(path))
                stale.add(path + " — " + e.getValue() + " is in the game now and claims it;"
                        + " remove this line");
            else if (!new File(TOKEN_DIR, path).isFile())
                stale.add(path + " — the picture is gone; remove this line");
        }
        assertTrue("AWAITING_A_HULL has entries that are no longer true:\n  "
                + String.join("\n  ", stale), stale.isEmpty());
    }
}
