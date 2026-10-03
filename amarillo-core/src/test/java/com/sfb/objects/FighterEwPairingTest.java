package com.sfb.objects;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

/**
 * An EW fighter has to be able to KEEP UP with the squadron it flies with.
 * <p>
 * The guard the fighter-line data was missing. The existing ones check that a type exists
 * (everyTypeNamedByEveryLineIsACataloguedFighter), that a role name is one the engine knows,
 * and that no era names a fighter before its year — all of which a stale EW seat passes
 * without complaint, because the older variant is a perfectly real fighter that existed
 * perfectly long ago.
 * <p>
 * The owner's rule, 2026-10-02, and it is about speed rather than naming:
 * <blockquote>
 * "The EW fighter for those fighters doesn't ever change again. Once you get the faster F18-B
 * and its accompanying F18-B-E, no other F18 gets faster. And so no further EW variants are
 * needed."
 * </blockquote>
 * So an EW variant is not minted per airframe variant — it is minted when the airframe gets
 * FASTER, because that is when the old one can no longer stay with the squadron. The whole
 * Federation line falls out of that: the F-18's EW fighter changed exactly once, at Y177,
 * when the airframe went 13 to 15; the F-14 and F-15 were already 15, so theirs never changed
 * at all, and `f18b_e` goes on serving the F-18B+ and the F-18C.
 * <p>
 * Which makes SPEED the invariant worth pinning, not the naming convention. A guard saying
 * "use {@code <type>_e} if the catalogue has one" would encode the symptom and would have to
 * be suppressed on every deliberate case; this one states the reason and is silent on all of
 * them.
 */
public class FighterEwPairingTest {

    @Before
    public void loadCatalogue() throws Exception {
        ShuttleCatalog.load(new File("../data/shuttles/shuttles.json"));
    }

    /** The combat role an EW key accompanies: {@code elite_ew} to {@code elite}. */
    private static String combatRoleOf(String ewKey) {
        if (FighterComplement.EW.equals(ewKey))
            return ShuttleCatalog.LineEra.STANDARD;   // the bare key means the standard line
        return ewKey.substring(0, ewKey.length() - ("_" + FighterComplement.EW).length());
    }

    /**
     * Every (combat type, EW type) pair the lines actually put in the same bay, as
     * "line Yyear role" for the failure message.
     */
    private static List<String[]> pairs() {
        List<String[]> out = new ArrayList<>();
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                for (Map.Entry<String, String> role : era.roles().entrySet()) {
                    if (!FighterComplement.isEwRole(role.getKey()))
                        continue;
                    String combat = era.roles().get(combatRoleOf(role.getKey()));
                    if (combat == null)
                        continue;   // no combat seat of that programme this era; nothing to pair
                    out.add(new String[] {
                            line + " from Y" + era.from + " " + role.getKey(),
                            combat, role.getValue() });
                }
        return out;
    }

    // ---------------------------------------------------------------- the invariant

    /**
     * The one that matters. A squadron flies together (J4.46), so an EW fighter slower than
     * the fighters it supports is left behind — and the failure is silent, because the stale
     * type is a real catalogue entry that existed years earlier.
     */
    @Test
    public void everyEwFighterIsAsFastAsTheFightersItFliesWith() {
        List<String> wrong = new ArrayList<>();
        for (String[] pair : pairs()) {
            ShuttleCatalog.Entry combat = ShuttleCatalog.get(pair[1]);
            ShuttleCatalog.Entry ew = ShuttleCatalog.get(pair[2]);
            if (combat == null || ew == null)
                continue;           // existence is another guard's job
            if (ew.speed < combat.speed)
                wrong.add("  " + pair[0] + ": " + pair[2] + " (speed " + ew.speed
                        + ") cannot keep up with " + pair[1] + " (speed " + combat.speed + ")");
        }
        assertTrue("EW fighters too slow for the squadron they fly with. The EW variant is"
                + " minted when the airframe gets FASTER, so a faster combat fighter with the"
                + " old EW fighter beside it means a variant is missing from the catalogue or"
                + " the era was copied forward:\n" + String.join("\n", wrong),
                wrong.isEmpty());
    }

    /**
     * And not the other way either. A faster EW fighter is not a safety margin, it is a sign
     * the combat seat was left behind when the EW one advanced — the same copy-forward
     * mistake, mirrored.
     */
    @Test
    public void noEwFighterIsFasterThanItsSquadron() {
        List<String> odd = new ArrayList<>();
        for (String[] pair : pairs()) {
            ShuttleCatalog.Entry combat = ShuttleCatalog.get(pair[1]);
            ShuttleCatalog.Entry ew = ShuttleCatalog.get(pair[2]);
            if (combat == null || ew == null)
                continue;
            if (ew.speed > combat.speed)
                odd.add("  " + pair[0] + ": " + pair[2] + " (speed " + ew.speed
                        + ") outruns " + pair[1] + " (speed " + combat.speed + ")");
        }
        assertTrue("EW fighters faster than the fighters they accompany, which usually means"
                + " the COMBAT seat is the stale one:\n" + String.join("\n", odd),
                odd.isEmpty());
    }

    // ---------------------------------------------------------------- it is the right kind

    /**
     * A type in an EW seat must actually carry pods. Naming the plain fighter there seats a
     * craft that is an "EW fighter" in the complement and generates nothing (J4.96), which no
     * other guard would notice: the type exists, the role is known, the year is right.
     */
    @Test
    public void everyEwSeatHoldsACraftWithPods() {
        List<String> podless = new ArrayList<>();
        for (String[] pair : pairs()) {
            ShuttleCatalog.Entry ew = ShuttleCatalog.get(pair[2]);
            if (ew == null)
                continue;
            if (ew.loadout.ewPods() + ew.loadout.fixedEwPods() <= 0)
                podless.add("  " + pair[0] + ": " + pair[2] + " carries no EW pods");
        }
        assertTrue("EW seats filled by craft with no EW pods — they would generate nothing"
                + " (J4.96):\n" + String.join("\n", podless), podless.isEmpty());
    }

    /**
     * And the reverse: a COMBAT seat must not hold a pod-carrying craft. An EW fighter seated
     * as an ordinary one spends its rails on pods (J4.962) and shows up as a fighter that
     * cannot carry a full drone load, for no visible reason.
     */
    @Test
    public void noCombatSeatHoldsAnEwCraft() {
        List<String> misplaced = new ArrayList<>();
        for (String line : ShuttleCatalog.lineNames())
            for (ShuttleCatalog.LineEra era : ShuttleCatalog.lineEras(line))
                for (Map.Entry<String, String> role : era.roles().entrySet()) {
                    if (FighterComplement.isEwRole(role.getKey()))
                        continue;
                    ShuttleCatalog.Entry e = ShuttleCatalog.get(role.getValue());
                    if (e == null)
                        continue;
                    if (e.loadout.ewPods() + e.loadout.fixedEwPods() > 0)
                        misplaced.add("  " + line + " from Y" + era.from + " "
                                + role.getKey() + " = " + role.getValue() + ", which has pods");
                }
        assertTrue("combat seats filled by EW craft:\n" + String.join("\n", misplaced),
                misplaced.isEmpty());
    }

    // ---------------------------------------------------------------- the fixture itself

    /** The guard is worthless if it never looks at anything. */
    @Test
    public void thePairingsAreActuallyBeingChecked() {
        List<String[]> pairs = pairs();
        assertTrue("no EW seats found at all — the lines or the role keys have moved",
                pairs.size() >= 10);

        boolean federation = pairs.stream()
                .anyMatch(p -> p[0].startsWith("federation-fighter") && p[0].endsWith("elite_ew"));
        assertTrue("the Federation elite programme should be among them: "
                + pairs.stream().map(p -> p[0]).toList(), federation);
    }
}
