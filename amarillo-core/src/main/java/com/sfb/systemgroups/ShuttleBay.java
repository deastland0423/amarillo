package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.sfb.objects.shuttles.Aas;
import com.sfb.objects.shuttles.AdminShuttle;
import com.sfb.objects.shuttles.Haas;
import com.sfb.objects.shuttles.Taas;
import com.sfb.objects.shuttles.Haas_E;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.GASShuttle;
import com.sfb.objects.shuttles.HTSShuttle;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.Stinger1;
import com.sfb.objects.shuttles.Stinger_E;
import com.sfb.objects.shuttles.Stinger2;
import com.sfb.objects.shuttles.StingerH;
import com.sfb.objects.Unit;

/**
 * A single shuttle bay on a ship.
 *
 * Each bay has a fixed number of spaces (slots). Spaces can hold a shuttle,
 * a bay-mounted drone rack (D12.3), or nothing. Spaces can be permanently
 * destroyed by DAC hits. Chain reactions are confined to a single bay
 * (D12.112).
 *
 * Each bay has a standard hatch (one launch per 2 impulses) and optionally
 * one or more launch tubes (J1.54). Each tube has its own 2-impulse cooldown
 * and can only launch fighters. Recovery always uses the standard hatch
 * (J1.541).
 */
public class ShuttleBay {

    private static final int LAUNCH_COOLDOWN = 2;

    /**
     * J1.58: a tunnel deck has doors at both ends of the bay, and "each hatch operates
     * independently at the full rate in (J1.50)". The Kzinti CV, CVS, CVL, MCV and CVE are
     * built this way, as is the Federation CVS.
     */
    public static final int TUNNEL_DECK_HATCHES = 2;

    private final Unit owner;
    private final List<ShuttleSpace> spaces = new ArrayList<>();

    /**
     * When each hatch was last used, one entry per hatch (J1.50, J1.58).
     * <p>
     * An array rather than a single impulse because a hatch is the thing the rule limits,
     * not the bay: a tunnel deck has two and they are independent, so a carrier can put two
     * fighters out on the same impulse and neither hatch is free again for two.
     * <p>
     * Distinct from a launch TUBE, which is not a hatch: J1.541 lets tubes launch fighters
     * but never recover them, and J1.542 bars administrative shuttles and heavy fighters
     * from them entirely. A hatch has none of those restrictions and is what a recovery
     * always uses (J1.543).
     */
    private int[] lastHatchImpulse = { -LAUNCH_COOLDOWN };

    // Launch tubes (J1.54) — each has its own cooldown
    private int launchTubeCount = 0;
    private int[] lastTubeImpulse = new int[0];

    public ShuttleBay(Unit owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------
    // Space management
    // -------------------------------------------------------------------------

    public void addSpace(ShuttleSpace space) {
        spaces.add(space);
    }

    /** Add an empty space (no shuttle). */
    public void addEmptySpace() {
        spaces.add(new ShuttleSpace());
    }

    public List<ShuttleSpace> getSpaces() {
        return spaces;
    }

    /** Total spaces in the bay (fixed at construction; never changes). */
    public int getTotalSpaces() {
        return spaces.size();
    }

    /** Spaces permanently destroyed by DAC hits. */
    public int getDestroyedSpaces() {
        return (int) spaces.stream().filter(ShuttleSpace::isDestroyed).count();
    }

    /** Spaces that are empty (not destroyed, not occupied). */
    public int getEmptySpaceCount() {
        return (int) spaces.stream().filter(ShuttleSpace::isEmpty).count();
    }

    /** Shuttles currently in inventory (launched shuttles are absent). */
    public List<Shuttle> getInventory() {
        List<Shuttle> inv = new ArrayList<>();
        for (ShuttleSpace s : spaces)
            if (s.getShuttle() != null)
                inv.add(s.getShuttle());
        return inv;
    }

    /** Original total spaces — for DAC box tracking. */
    public int getCapacity() {
        return spaces.size();
    }

    /** Remaining undestroyed spaces — for DAC remaining-box tracking. */
    public int getRemainingSpaces() {
        return (int) spaces.stream().filter(s -> !s.isDestroyed()).count();
    }

    // -------------------------------------------------------------------------
    // Shuttle placement (used during init and landing)
    // -------------------------------------------------------------------------

    /**
     * Seat a shuttle in the first empty space (or a new space if none).
     *
     * The turn is recorded on the space because rearming asks how long the occupant
     * has been
     * sitting there (J4.8174): a fighter that arrived this turn has not been idle
     * for a whole
     * one, so no deck crew action on it could have finished.
     *
     * @param turn the turn the shuttle arrives in
     */
    public void addShuttle(Shuttle shuttle, int turn) {
        for (ShuttleSpace space : spaces) {
            if (space.isEmpty()) {
                space.setShuttle(shuttle);
                space.setOccupiedSinceTurn(turn);
                return;
            }
        }
        // No empty space — add a new one (should only happen during init)
        ShuttleSpace fresh = new ShuttleSpace(shuttle);
        fresh.setOccupiedSinceTurn(turn);
        spaces.add(fresh);
    }

    /**
     * Replace one shuttle in-bay with another (e.g. admin → ScatterPack).
     * Uses identity comparison so the correct space is updated even if two
     * shuttles have the same name. Returns true if the old shuttle was found.
     */
    public boolean replaceShuttle(Shuttle oldShuttle, Shuttle newShuttle) {
        for (ShuttleSpace space : spaces) {
            if (space.getShuttle() == oldShuttle) {
                space.setShuttle(newShuttle);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Launch tubes
    // -------------------------------------------------------------------------

    public void setLaunchTubeCount(int n) {
        launchTubeCount = n;
        lastTubeImpulse = new int[n];
        Arrays.fill(lastTubeImpulse, -LAUNCH_COOLDOWN);
    }

    public int getLaunchTubeCount() {
        return launchTubeCount;
    }

    public int getAvailableTubeCount(int currentImpulse) {
        int count = 0;
        for (int last : lastTubeImpulse)
            if (currentImpulse - last >= LAUNCH_COOLDOWN)
                count++;
        return count;
    }

    // -------------------------------------------------------------------------
    // Launch
    // -------------------------------------------------------------------------

    /** How many hatches this bay has: one ordinarily, two for a tunnel deck (J1.58). */
    public int getHatchCount() {
        return lastHatchImpulse.length;
    }

    /**
     * Set the number of hatches, keeping whatever cooldowns are already running. Called
     * when a bay is built from a ship file that declares a tunnel deck.
     */
    public void setHatchCount(int hatches) {
        int n = Math.max(1, hatches);
        int[] next = new int[n];
        java.util.Arrays.fill(next, -LAUNCH_COOLDOWN);
        System.arraycopy(lastHatchImpulse, 0, next, 0,
                Math.min(lastHatchImpulse.length, n));
        lastHatchImpulse = next;
    }

    /** Hatches free to be used this impulse (J1.50). */
    public int getAvailableHatchCount(int currentImpulse) {
        int free = 0;
        for (int used : lastHatchImpulse)
            if (currentImpulse - used >= LAUNCH_COOLDOWN)
                free++;
        return free;
    }

    public boolean canLaunch(int currentImpulse) {
        return getAvailableHatchCount(currentImpulse) > 0;
    }

    /**
     * Claim a free hatch, or return false if every one of them is still cooling. Recovery
     * uses this too: J1.50 limits a hatch to one launch OR one recovery per two impulses.
     */
    public boolean claimHatch(int currentImpulse) {
        for (int i = 0; i < lastHatchImpulse.length; i++) {
            if (currentImpulse - lastHatchImpulse[i] >= LAUNCH_COOLDOWN) {
                lastHatchImpulse[i] = currentImpulse;
                return true;
            }
        }
        return false;
    }

    public boolean canLaunch(Shuttle shuttle, int currentImpulse) {
        if (isLaunchTubeEligible(shuttle) && getAvailableTubeCount(currentImpulse) > 0)
            return true;
        return canLaunch(currentImpulse);
    }

    /** Book a hatch as used — a launch or a recovery, which J1.50 treats alike. */
    public void markUsed(int currentImpulse) {
        claimHatch(currentImpulse);
    }

    /**
     * Launch the given shuttle. Removes it from its space (space stays, now empty).
     * Returns the shuttle, or null if not found in any space.
     */
    public Shuttle launch(Shuttle shuttle, int speed, int facing, int currentImpulse) {
        ShuttleSpace space = findSpace(shuttle);
        if (space == null)
            return null;

        space.setShuttle(null);
        shuttle.setSpeed(Math.min(speed, shuttle.getMaxSpeed()));
        shuttle.setFacing(facing);

        // A tube first where one will serve, so the hatches stay free for the recoveries and
        // the admin shuttles that J1.541/J1.542 will not let through a tube.
        if (isLaunchTubeEligible(shuttle)) {
            for (int i = 0; i < launchTubeCount; i++) {
                if (currentImpulse - lastTubeImpulse[i] >= LAUNCH_COOLDOWN) {
                    lastTubeImpulse[i] = currentImpulse;
                    return shuttle;
                }
            }
        }
        claimHatch(currentImpulse);
        return shuttle;
    }

    // -------------------------------------------------------------------------
    // DAC damage
    // -------------------------------------------------------------------------

    /**
     * Destroy a specific space by index. Returns the shuttle that was in the
     * space (null if empty), so the caller can check isArmed() for chain reaction.
     */
    public Shuttle destroySpace(int spaceIndex) {
        if (spaceIndex < 0 || spaceIndex >= spaces.size())
            return null;
        return spaces.get(spaceIndex).destroy();
    }

    /**
     * Find the space containing the given shuttle. Returns null if not found.
     */
    public ShuttleSpace findSpace(Shuttle shuttle) {
        for (ShuttleSpace space : spaces)
            if (space.getShuttle() == shuttle)
                return space;
        return null;
    }

    /**
     * Index of the given space, or -1 if not in this bay.
     */
    public int indexOf(ShuttleSpace space) {
        return spaces.indexOf(space);
    }

    // -------------------------------------------------------------------------
    // Factory: build shuttle from type string
    // -------------------------------------------------------------------------

    public static Shuttle buildShuttle(String type, String name) {
        Shuttle s;
        switch (type.toLowerCase()) {
            case "gas":
                s = new GASShuttle();
                break;
            case "hts":
                s = new HTSShuttle();
                break;
            case "stinger1":
                s = new Stinger1();
                break;
            case "stinger2":
                s = new Stinger2();
                break;
            case "stingerh":
                s = new StingerH();
                break;
            case "stinger_e":
                s = new Stinger_E();
                break;
            case "aas":
                s = new Aas();
                break;
            case "haas":
                s = new Haas();
                break;
            case "taas":
                s = new Taas();
                break;
            case "haas_e":
                s = new Haas_E();
                break;
            case "admin":
                s = new AdminShuttle();
                break;
            default:
                // An unknown key used to fall through to an admin shuttle, so a typo or a
                // fighter whose case was never added launched as a shuttle and nobody noticed.
                // Say so, loudly, and still return something rather than killing the load.
                System.err.println("ShuttleBay: unknown shuttle type '" + type
                        + "' — no case in buildShuttle; falling back to an admin shuttle."
                        + " Add the case when adding a new type.");
                s = new AdminShuttle();
                break;
        }
        s.setName(name);
        return s;
    }

    public Unit getOwner() {
        return owner;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Whether this craft may go out through a launch tube (J1.542).
     * <p>
     * Fighters only: "administrative shuttles (including their variants such as MRS, HTS,
     * GAS, MSS, etc.) and heavy fighters cannot be launched through launch tubes." The admin
     * half falls out of the type test — none of those are Fighters — so a Hydran bay of
     * three tubes and a door puts three Stingers and ONE admin shuttle out in an impulse,
     * the shuttle going through the door because nothing else will take it.
     * <p>
     * The heavy-fighter half is NOT enforced, because no heavy fighter exists in the game
     * data yet (J10.0; the Federation A-20 and F-111 are the usual ones). A flag for a craft
     * nobody can build would be untestable furniture — but a heavy fighter IS a Fighter, so
     * whoever adds the first one must exclude it here or it will wrongly take a tube.
     */
    private static boolean isLaunchTubeEligible(Shuttle shuttle) {
        return shuttle instanceof Fighter;
    }

    // -------------------------------------------------------------------------
    // Rearming (J4.83, Hydran subset)
    // -------------------------------------------------------------------------

    /**
     * What one bay's rearm pass did: a line per fighter worked on, and the crews it
     * spent.
     */
    public record RearmResult(List<String> log, int crewsUsed) {
        static final RearmResult NOTHING = new RearmResult(List.of(), 0);
    }

    /**
     * Reload the fighters that spent a whole turn in their boxes (J4.83).
     *
     * The rules run on deck crew ACTIONS of 32 consecutive impulses (J4.8171) which
     * may start
     * on any impulse and span turns. We run the ordinary case instead, and the
     * arithmetic says
     * the ordinary case is a turn: a Stinger-1 wants four fusion charges, at half
     * an action
     * each (J4.833) that is two actions, and the two deck crews J4.8172 allows on
     * one fighter
     * finish two actions in one turn. A hellbore charge is one full action (J4.834)
     * - one crew,
     * one turn.
     *
     * So: a fighter that was in its box at the start of the turn and is still there
     * at the end
     * is reloaded from that box's own capacitor (J4.881 - never straight from the
     * ship), as far
     * as the crews reach. A fighter that launched has nothing done to it: J4.8174
     * makes an
     * interrupted action cancelled with no partial credit, and leaving early is the
     * commonest
     * interruption there is.
     *
     * Not modelled yet: crews assigned to a named bay (J4.813), a second crew
     * joining a job at
     * end of turn (J4.823), and the launch lockout on the impulse after a reload
     * (J4.8172).
     *
     * Each box works the crews POSTED to it at the start of the turn, rather than
     * drawing on
     * a pool now: J4.817's action runs for 32 consecutive impulses, so the crews
     * were in that
     * box the whole time. Which is what lets J4.811 kill them when the box is shot
     * off, and
     * what makes a destroyed box's work simply not happen (J4.8174).
     *
     * @param turn the turn that is ending
     */
    public RearmResult rearmFighters(int turn) {
        return rearmFighters(turn, null);
    }

    /**
     * As above, with the ship's drone supply so {@link CrewTask#REFILL} has somewhere to
     * fetch from (J4.7). A bay given no store simply finds no refilling to do.
     */
    public RearmResult rearmFighters(int turn, DroneStore store) {
        List<String> log = new ArrayList<>();
        int crewsUsed = 0;

        for (ShuttleSpace space : spaces) {
            if (space.isDestroyed())
                continue;
            if (space.getDeckCrews() <= 0)
                continue; // nobody was posted here, or they died with an earlier hit
            // Any occupant, not just a fighter: J4.818 mends shuttle damage, and an admin
            // shuttle with a hole in it is a job a deck crew can be posted to. Loading and
            // unloading simply find nothing to do on one.
            Shuttle occupant = space.getShuttle();
            // It has to have sat the whole turn. A fighter recovered DURING this turn has
            // not,
            // and neither has one that launched and came back.
            //
            // An EMPTY box is exempt, and deliberately: J4.8223 has the crews refill the
            // racks while the fighters are away, so the box whose fighter left this turn is
            // exactly the one that wants the work. There is no occupant to have sat still.
            if (occupant != null && space.getOccupiedSinceTurn() >= turn)
                continue;

            for (java.util.Map.Entry<CrewTask, Integer> job : space.getCrewTasks().entrySet()) {
                if (occupant == null && job.getKey() != CrewTask.REFILL)
                    continue;
                RearmResult one = work(job.getKey(), space, occupant, job.getValue(), store);
                log.addAll(one.log());
                crewsUsed += one.crewsUsed();
            }
        }
        return new RearmResult(log, crewsUsed);
    }

    /**
     * Rearm the fighter in one box as far as its capacitor and the loose crews
     * allow.
     *
     * The costs and the loading itself live in {@link FighterArming}, which the
     * pre-game
     * weapon status setup uses too — it differs only in the budget it brings. A
     * crew working
     * one turn completes one action, so the budget here is the crews it may put on
     * this
     * fighter, and the crews it SPENT are the actions that came back.
     */
    private RearmResult work(CrewTask task, ShuttleSpace space, Shuttle fighter, int crews,
            DroneStore store) {
        int budget = Math.min(2, crews) * FighterArming.HALF_ACTIONS_PER_ACTION;
        FighterArming.Load load = switch (task) {
            case LOAD -> FighterArming.load(space, fighter, budget);
            case UNLOAD -> FighterArming.unload(space, fighter, budget);
            case REPAIR -> FighterArming.repair(space, fighter, budget);
            case REFILL -> FighterArming.refill(space, store, budget);
        };
        if (load.note() == null)
            return RearmResult.NOTHING;

        int crewsUsed = (load.halfActionsUsed() + FighterArming.HALF_ACTIONS_PER_ACTION - 1)
                / FighterArming.HALF_ACTIONS_PER_ACTION;
        return new RearmResult(List.of(load.note()), crewsUsed);
    }
}
