package com.sfb.systemgroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.Unit;

public class Shuttles implements Systems {

    private final List<ShuttleBay> bays = new ArrayList<>();
    private Unit owningUnit;

    /** The owning game's clock, injected by Ship.attachClock(); rearming needs the turn. */
    private com.sfb.TurnTracker clock;

    /** What the end-of-turn rearm pass did, for the owner's own readout (J4.8175). */
    private final List<String> lastRearmLog = new ArrayList<>();

    public Shuttles(Unit owner) {
        this.owningUnit = owner;
    }

    // -------------------------------------------------------------------------
    // Systems interface
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public void init(Map<String, Object> values) {
        List<Object> rawBays = (List<Object>) values.get("shuttlebays");
        if (rawBays != null) {
            // Supports two formats per element:
            //   Old: ["stinger1", "stinger1"]          — list of type strings
            //   New: {"shuttles":["stinger1"],"launchTubes":2} — object with optional tube count
            Map<String, Integer> typeCount = new HashMap<>();
            for (Object rawBay : rawBays) {
                ShuttleBay bay = new ShuttleBay(owningUnit);
                List<String> shuttleTypes;
                if (rawBay instanceof Map) {
                    Map<String, Object> bayObj = (Map<String, Object>) rawBay;
                    shuttleTypes = (List<String>) bayObj.get("shuttles");
                    Object tubesObj = bayObj.get("launchTubes");
                    if (tubesObj instanceof Number)
                        bay.setLaunchTubeCount(((Number) tubesObj).intValue());
                } else {
                    shuttleTypes = (List<String>) rawBay;
                }
                if (shuttleTypes != null) {
                    for (String type : shuttleTypes) {
                        int count = typeCount.merge(type, 1, Integer::sum);
                        String name = displayName(type) + "-" + count;
                        Shuttle shuttle = ShuttleBay.buildShuttle(type, name);
                        bay.addSpace(new ShuttleSpace(shuttle));
                    }
                }
                bays.add(bay);
            }
        } else {
            // Legacy format: single integer count, all admin shuttles in one bay
            int count = values.get("shuttle") == null ? 0 : (Integer) values.get("shuttle");
            if (count > 0) {
                ShuttleBay bay = new ShuttleBay(owningUnit);
                for (int i = 0; i < count; i++) {
                    Shuttle shuttle = ShuttleBay.buildShuttle("admin", "Shuttle" + (i + 1));
                    bay.addSpace(new ShuttleSpace(shuttle));
                }
                bays.add(bay);
            }
        }
    }

    @Override
    public int fetchOriginalTotalBoxes() {
        return bays.stream().mapToInt(ShuttleBay::getTotalSpaces).sum();
    }

    @Override
    public int fetchRemainingTotalBoxes() {
        return bays.stream().mapToInt(ShuttleBay::getRemainingSpaces).sum();
    }

    @Override
    public void cleanUp() {
        for (ShuttleBay bay : bays) {
            for (com.sfb.objects.shuttles.Shuttle s : bay.getInventory()) {
                if (s instanceof com.sfb.objects.shuttles.ScatterPack) {
                    ((com.sfb.objects.shuttles.ScatterPack) s).applyPendingPayload();
                } else if (s instanceof com.sfb.objects.shuttles.SuicideShuttle) {
                    com.sfb.objects.shuttles.SuicideShuttle ss = (com.sfb.objects.shuttles.SuicideShuttle) s;
                    // Nothing paid for this turn, by either route: all the arming is lost and
                    // it is a plain admin shuttle again. While arming, the arming energy is
                    // the upkeep; once fully armed, the 1-point hold is.
                    if (ss.isArmed() && !ss.isUpkeepPaid()) {
                        com.sfb.objects.shuttles.AdminShuttle admin = new com.sfb.objects.shuttles.AdminShuttle();
                        admin.setName(ss.getName());
                        admin.setMaxSpeed(ss.getMaxSpeed());
                        admin.setHull(ss.getHull());
                        admin.setCurrentHull(ss.getCurrentHull());
                        // Through the BAY. getInventory() builds a fresh list on every call,
                        // so the old inv.set(i, admin) rewrote a throwaway copy and the
                        // shuttle was never actually reverted - the whole lapse was a no-op.
                        bay.replaceShuttle(ss, admin);
                    } else {
                        ss.resetUpkeep();
                    }
                }
            }
        }
        rearmFighters();
    }

    @Override
    public Unit fetchOwningUnit() { return owningUnit; }

    public void setClock(com.sfb.TurnTracker clock) { this.clock = clock; }

    /**
     * Lines from the most recent end-of-turn rearm pass.
     * <p>
     * Deliberately NOT fed to the shared combat log: how many charges an opponent's fighters
     * are carrying is not something they get to announce to the enemy.
     */
    public List<String> getLastRearmLog() { return lastRearmLog; }

    /**
     * Power the ship's fighter box capacitors could still absorb this turn (J4.832).
     * <p>
     * The ceiling on the Energy Allocation line: a fusion box takes a point per charge and a
     * hellbore box two, and neither takes anything once full.
     */
    public int capacitorPowerWanted() {
        int wanted = 0;
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces())
                wanted += box.capacitorPowerWanted();
        return wanted;
    }

    /**
     * Spend allocated power refilling fighter box capacitors (J4.832).
     * <p>
     * Boxes are filled in order, each to the top before the next is touched, because a player
     * paying for charges wants a fighter that can fly a full sortie rather than a squadron of
     * half-loaded ones. J4.881 keeps this indirect: the ship fills the box, and only the box
     * can arm the fighter.
     *
     * @return points actually taken (a short allocation leaves the rest unspent)
     */
    public int rechargeCapacitors(java.util.Map<String, Integer> byBox) {
        if (byBox == null || byBox.isEmpty())
            return 0;
        int used = 0;
        for (int b = 0; b < bays.size(); b++) {
            java.util.List<ShuttleSpace> spaces = bays.get(b).getSpaces();
            for (int i = 0; i < spaces.size(); i++) {
                Integer points = byBox.get(boxId(b, i));
                if (points != null && points > 0)
                    used += spaces.get(i).addCapacitorEnergy(points);
            }
        }
        return used;
    }

    /**
     * Power each box could still take this turn, by box id — what the hangar panel offers.
     * <p>
     * Per box and never summed: these are separate capacitors wired to separate boxes, and a
     * total across them would name a quantity nobody can spend. Eight points of room in one
     * box is a fighter's full sortie; one point in each of eight is nothing at all.
     */
    public java.util.Map<String, Integer> capacitorPowerWantedByBox() {
        java.util.Map<String, Integer> wanted = new java.util.LinkedHashMap<>();
        for (int b = 0; b < bays.size(); b++) {
            java.util.List<ShuttleSpace> spaces = bays.get(b).getSpaces();
            for (int i = 0; i < spaces.size(); i++) {
                int n = spaces.get(i).capacitorPowerWanted();
                if (n > 0)
                    wanted.put(boxId(b, i), n);
            }
        }
        return wanted;
    }

    public int rechargeCapacitors(int power) {
        int left = Math.max(0, power);
        int used = 0;
        for (ShuttleBay bay : bays) {
            for (ShuttleSpace box : bay.getSpaces()) {
                if (left <= 0)
                    return used;
                int took = box.addCapacitorEnergy(left);
                left -= took;
                used += took;
            }
        }
        return used;
    }

    /**
     * Post the ship's deck crews to the fighter boxes they will work in this turn (J4.817).
     * <p>
     * Done at the start of the turn, not the end, because that is when the rules have them
     * start: an action is 32 consecutive impulses, so a crew is IN a box for the whole turn.
     * Two consequences follow that could not before. A box shot off mid-turn kills the crews
     * posted to it (J4.811), and their work simply does not happen (J4.8174: interrupted is
     * cancelled, no partial credit).
     * <p>
     * It also makes the posting a bet, twice over. Launching a fighter its crews are working
     * on throws that work away (J4.8174), and the crews stay in the box regardless: a crew is
     * standing in that bay for the turn whether or not its job is still there, so a hit that
     * destroys the box kills them either way. Losing the job is not an escape from the bay.
     * <p>
     * Who gets them is first-come down the bays, the same order everything else uses, and a
     * real captain's choice that belongs to the player once the hangar panel can take it.
     *
     * @param available crews free after Energy Allocation took its share (scatter packs)
     * @return crews left unposted
     */
    public int postDeckCrews(int available) {
        return postDeckCrews(available, null);
    }

    /**
     * As above, but the player said where.
     * <p>
     * An ORDER, not a hint: when a posting map is given it is the whole instruction, and a box
     * not named in it gets nobody — the same rule the COI drone loadouts follow, so a player
     * who deliberately leaves crews idle is not overruled by the automatic pass. An absent or
     * empty map means nobody gave an order, and the first-come pass runs as before, so a
     * player who never opens the hangar panel loses nothing.
     *
     * @param requested box id ({@link #boxId}) to crews, as ordered at Energy Allocation
     */
    public int postDeckCrews(int available, java.util.Map<String, Integer> requested) {
        int left = Math.max(0, available);
        boolean ordered = requested != null && !requested.isEmpty();

        for (int b = 0; b < bays.size(); b++) {
            ShuttleBay bay = bays.get(b);
            java.util.List<ShuttleSpace> spaces = bay.getSpaces();
            for (int i = 0; i < spaces.size(); i++) {
                ShuttleSpace box = spaces.get(i);
                box.setDeckCrews(0);
                if (left <= 0 || box.isDestroyed() || box.isEmpty())
                    continue;
                if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter fighter))
                    continue;
                int wanted = crewsWantedBy(fighter);
                if (wanted == 0)
                    continue;   // no work here, so nobody stands in this box to be shot

                int asked = wanted;
                if (ordered) {
                    Integer order = requested.get(boxId(b, i));
                    asked = order == null ? 0 : Math.max(0, Math.min(order, wanted));
                }
                int posted = Math.min(asked, left);
                if (posted <= 0)
                    continue;
                box.setDeckCrews(posted);
                left -= posted;
            }
        }
        return left;
    }

    /** How the wire names a box: its bay's index and its own, as the DTO sends them. */
    public static String boxId(int bayIndex, int spaceIndex) {
        return bayIndex + "-" + spaceIndex;
    }

    /**
     * Crews each box could use this turn, by box id — what the hangar panel offers, and the
     * ceiling the server holds an order to. Zero-work boxes are absent rather than zero.
     */
    public java.util.Map<String, Integer> crewsWantedByBox() {
        java.util.Map<String, Integer> wanted = new java.util.LinkedHashMap<>();
        for (int b = 0; b < bays.size(); b++) {
            java.util.List<ShuttleSpace> spaces = bays.get(b).getSpaces();
            for (int i = 0; i < spaces.size(); i++) {
                ShuttleSpace box = spaces.get(i);
                if (box.isDestroyed() || box.isEmpty())
                    continue;
                if (!(box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter fighter))
                    continue;
                int n = crewsWantedBy(fighter);
                if (n > 0)
                    wanted.put(boxId(b, i), n);
            }
        }
        return wanted;
    }

    /** Crews this fighter's outstanding work could use, at most the two J4.8172 allows. */
    private static int crewsWantedBy(com.sfb.objects.shuttles.Shuttle fighter) {
        int half = FighterArming.halfActionsOutstanding(fighter);
        if (half <= 0)
            return 0;
        int actions = (half + FighterArming.HALF_ACTIONS_PER_ACTION - 1)
                / FighterArming.HALF_ACTIONS_PER_ACTION;
        return Math.min(2, actions);
    }

    /**
     * End-of-turn fighter rearming (J4.83), one pass over every bay.
     * <p>
     * The ship's deck crews are a single pool shared by the bays: J4.813 assigns each crew to
     * one named bay, which we do not model, so the pool is spent first-come across the bays in
     * order rather than pretending every bay has its own full complement.
     */
    private void rearmFighters() {
        lastRearmLog.clear();
        if (clock == null)
            return;
        int turn = clock.getTurn();
        for (ShuttleBay bay : bays) {
            ShuttleBay.RearmResult result = bay.rearmFighters(turn);
            lastRearmLog.addAll(result.log());
        }
        // The posting lasted the turn; the crews stand down with it. J4.817 actions that span
        // turns are the part of the rule we do not model — see ShuttleBay.rearmFighters.
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces())
                box.setDeckCrews(0);
    }

    // -------------------------------------------------------------------------
    // Access
    // -------------------------------------------------------------------------

    public List<ShuttleBay> getBays() { return bays; }

    /** Prepend the ship name to every bayed shuttle's name. Called after the ship gets its scenario name. */
    public void prefixShuttleNames(String shipName) {
        for (ShuttleBay bay : bays) {
            for (Shuttle s : bay.getInventory()) {
                s.setName(shipName + "-" + s.getName());
            }
        }
    }

    /** All shuttles across all bays that are currently in inventory. */
    public List<Shuttle> getAllShuttles() {
        List<Shuttle> all = new ArrayList<>();
        for (ShuttleBay bay : bays) all.addAll(bay.getInventory());
        return all;
    }

    /** Map shuttle type strings to display-friendly names. */
    private static String displayName(String type) {
        switch (type.toLowerCase()) {
            case "admin":       return "Admin";
            case "gas":         return "GAS";
            case "hts":         return "HTS";
            case "suicide":     return "Suicide";
            case "scatterpack": return "ScatterPack";
            case "stinger1":    return "Stinger1";
            case "stinger2":    return "Stinger2";
            case "stingerh":    return "StingerH";
            default:
                // Capitalize first letter for unknown types
                return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }
}
