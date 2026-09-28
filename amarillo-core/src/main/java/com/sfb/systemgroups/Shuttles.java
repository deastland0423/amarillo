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

    /** The carrier's supply of spare drones for its fighters (J4.7); null if it carries none. */
    private DroneStore droneStore;

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
                    // J1.58: a tunnel deck has a door at each end of the bay, and each
                    // works independently at the full J1.50 rate. Counted as hatches
                    // rather than tubes because a hatch can also RECOVER (J1.541) and will
                    // take an admin shuttle, which a tube will not (J1.542).
                    if ("tunnel".equalsIgnoreCase(String.valueOf(bayObj.get("type"))))
                        bay.setHatchCount(ShuttleBay.TUNNEL_DECK_HATCHES);
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

        stockDroneStore(values.get("dronestoragespaces"));
    }

    /**
     * Build the carrier's drone supply (J4.7) from the spaces its ship file declares.
     * <p>
     * The number is per ship, out of Annex #7G — the rulebook prints only the Kzinti CV's 150
     * — so it is declared in the ship file rather than derived. A ship that declares none has
     * no supply: its racks hold what they hold and no more, which is the honest answer for
     * every hull whose Annex #7G line we have not read.
     * <p>
     * J4.72 decides how much goes in the hold: drones in the ready racks and on the fighters
     * "count as part of the ship's storage", so the declared figure is the TOTAL and what is
     * already forward is deducted. Racks start full (J4.886), so a twelve-AAS carrier has
     * around two dozen of its spaces committed before the scenario opens.
     */
    private void stockDroneStore(Object declared) {
        if (!(declared instanceof Number n) || n.doubleValue() <= 0)
            return;
        droneStore = new DroneStore(n.doubleValue());
        List<com.sfb.objects.DroneType> pattern = droneLoadoutPattern();
        if (pattern.isEmpty())
            return;   // nothing aboard that takes drones; the hold stays empty
        droneStore.stock(pattern, droneStore.capacitySpaces() - spacesCommittedForward());
    }

    /**
     * What the ship's drone fighters are built around — one entry per rail, of what that rail
     * is designed to carry. The quartermaster's stocking list, and the proportions a mixed
     * squadron actually needs.
     */
    private List<com.sfb.objects.DroneType> droneLoadoutPattern() {
        List<com.sfb.objects.DroneType> pattern = new ArrayList<>();
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getReadyRack() != null)
                    pattern.addAll(box.getReadyRack().design());
        return pattern;
    }

    /**
     * Spaces of the ship's storage already moved forward — in the ready racks and on the
     * fighters themselves (J4.72). Deducted from the declared total, never added to it.
     */
    public double spacesCommittedForward() {
        double committed = 0;
        for (ShuttleBay bay : bays) {
            for (ShuttleSpace box : bay.getSpaces()) {
                if (box.isDestroyed())
                    continue;
                if (box.getReadyRack() != null)
                    committed += box.getReadyRack().spaces();
                Shuttle occupant = box.getShuttle();
                if (occupant == null)
                    continue;
                for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
                    if (w instanceof com.sfb.weapons.DroneRail rail && rail.getDrone() != null)
                        committed += rail.getDrone().getRackSize();
            }
        }
        return committed;
    }

    /** The carrier's drone supply (J4.7), or null if this ship declares none. */
    public DroneStore getDroneStore() { return droneStore; }

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
                box.clearCrews();
                if (left <= 0 || box.isDestroyed())
                    continue;
                // An empty box is not an idle one: its rack can still be refilled while its
                // fighter is away (J4.8223), which is the only job that does not need an
                // occupant. Everything else wants a fighter to work on.
                boolean hasFighter =
                        box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter;
                if (!hasFighter && !(ordered && box.getReadyRack() != null))
                    continue;
                com.sfb.objects.shuttles.Shuttle fighter = box.getShuttle();

                if (!ordered) {
                    int wanted = crewsWantedBy(fighter);
                    int posted = Math.min(wanted, left);
                    if (posted <= 0)
                        continue;   // no work here, so nobody stands in this box to be shot
                    box.setDeckCrews(posted);   // the obvious job, first come
                    left -= posted;
                    continue;
                }

                // An order names jobs, not boxes: "1-3:LOAD" and "1-3:REPAIR" can both be on
                // the same fighter, held together to J4.8172's two crews. Each job is checked
                // on its own — a box with nothing to load is still somewhere to unload.
                //
                // Refilling the rack is the exception J4.8172 spells out: "two MORE deck crews
                // can load the ready rack in that box", so it carries its own allowance of two
                // rather than competing with the pair working the fighter.
                int onFighter = 0;
                int onRack = 0;
                for (CrewTask task : CrewTask.values()) {
                    Integer order = requested.get(task.keyFor(boxId(b, i)));
                    if (order == null || order <= 0)
                        continue;
                    if (crewsWantedFor(task, box, fighter, droneStore) <= 0)
                        continue;
                    int used = task.isFighterWork() ? onFighter : onRack;
                    int room = Math.min(2 - used, left);
                    int posted = Math.min(order, room);
                    if (posted <= 0)
                        continue;
                    if (!box.postCrews(task, posted))
                        continue;   // J4.8172 will not have this one beside what is posted
                    if (task.isFighterWork())
                        onFighter += posted;
                    else
                        onRack += posted;
                    left -= posted;
                }
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

    /**
     * Crews this one job could use, at most the two J4.8172 allows on a fighter — and zero
     * when there is no such job here, which keeps people out of boxes where nothing is
     * happening, and so out of the way of J4.811.
     */
    public static int crewsWantedFor(CrewTask task, ShuttleSpace box,
            com.sfb.objects.shuttles.Shuttle fighter) {
        return crewsWantedFor(task, box, fighter, null);
    }

    /**
     * As above, with the ship's drone supply, which only {@link CrewTask#REFILL} needs: with
     * no store there is nothing to fetch, so the job is not offered at all.
     */
    public static int crewsWantedFor(CrewTask task, ShuttleSpace box,
            com.sfb.objects.shuttles.Shuttle fighter, DroneStore store) {
        // J4.8172: a rack cannot be filled and drawn from in the same turn, so a job that
        // conflicts with one already posted here is not on offer either.
        if (!box.canPost(task))
            return 0;
        // An empty box has no fighter to load, unload or mend; only its rack can be worked on.
        if (fighter == null && task != CrewTask.REFILL)
            return 0;
        int half = switch (task) {
            case LOAD -> FighterArming.halfActionsOutstanding(fighter);
            // Worth a crew only if there is something to take off AND somewhere to put it:
            // the drones belong in this box's own ready rack (J4.822).
            case UNLOAD -> box.getReadyRack() == null || box.getReadyRack().isFull()
                    ? 0 : FighterArming.dronesCarriedBy(fighter) * 2;
            // J4.818: one damage point an action. A single point can carry a fighter back
            // under its crippling threshold, so this competes with loading for a reason.
            case REPAIR -> (fighter.getHull() - fighter.getCurrentHull())
                    * FighterArming.HALF_ACTIONS_PER_ACTION;
            // J4.821 prices the trip from the hold at one action a space, and there has to be
            // both a gap in the rack and something in the hold to put in it.
            case REFILL -> store == null || store.isEmpty() || box.getReadyRack() == null
                    ? 0 : refillHalfActionsWanted(box, store);
        };
        if (half <= 0)
            return 0;
        int actions = (half + FighterArming.HALF_ACTIONS_PER_ACTION - 1)
                / FighterArming.HALF_ACTIONS_PER_ACTION;
        return Math.min(2, actions);
    }

    /**
     * Every job a deck crew could be posted to right now, keyed as the wire names them, with
     * the crews each could use. The hangar panel draws its rows from this and the server holds
     * an order to it — so a job the panel cannot show is a job nobody can order.
     */
    public java.util.Map<String, Integer> crewJobsAvailable() {
        java.util.Map<String, Integer> jobs = new java.util.LinkedHashMap<>();
        for (int b = 0; b < bays.size(); b++) {
            java.util.List<ShuttleSpace> spaces = bays.get(b).getSpaces();
            for (int i = 0; i < spaces.size(); i++) {
                ShuttleSpace box = spaces.get(i);
                if (box.isDestroyed())
                    continue;
                for (CrewTask task : CrewTask.values()) {
                    // An EMPTY box still has work: J4.8223 has the crews refill the racks
                    // "while the fighters are on their mission so that the fighters can be
                    // reloaded quickly when they return". That is the whole point of the job,
                    // so it is the one task an empty box still offers.
                    if (box.isEmpty() && task != CrewTask.REFILL)
                        continue;
                    int n = crewsWantedFor(task, box, box.getShuttle(), droneStore);
                    if (n > 0)
                        jobs.put(task.keyFor(boxId(b, i)), n);
                }
            }
        }
        return jobs;
    }

    /**
     * Half-actions the gap in this rack would take to close, at J4.821's action a space,
     * bounded by what the hold can actually supply.
     */
    private static int refillHalfActionsWanted(ShuttleSpace box, DroneStore store) {
        double spaces = 0;
        for (com.sfb.objects.DroneType want : box.getReadyRack().slotsMissing())
            spaces += want.rack;
        spaces = Math.min(spaces, store.spacesHeld());
        return (int) Math.round(spaces * FighterArming.HALF_ACTIONS_PER_ACTION);
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
            ShuttleBay.RearmResult result = bay.rearmFighters(turn, droneStore);
            lastRearmLog.addAll(result.log());
        }
        // The posting lasted the turn; the crews stand down with it. J4.817 actions that span
        // turns are the part of the rule we do not model — see ShuttleBay.rearmFighters.
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces())
                box.clearCrews();
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
    /**
     * What to call a craft of this type in the name a bay gives it.
     * <p>
     * The catalogue answers this, because the catalogue is where the craft's own designation
     * lives (`data/shuttles/shuttles.json`). It used to be a switch here, and the switch was
     * written when the only fighters were Hydran Stingers: every Kzinti type fell through to
     * a default that capitalised the first letter and nothing else, so a bay called its
     * fighters "Aas-1" while the same craft launched onto the map as "AAS-7" — LaunchCoordinator
     * having read the catalogue all along. Two names for one fighter, a case apart.
     * <p>
     * The fallback survives for the types the catalogue deliberately does NOT carry: suicide
     * shuttles and scatter packs are ROLES an admin shuttle takes on, not stock anyone holds.
     */
    private static String displayName(String type) {
        com.sfb.objects.ShuttleCatalog.Entry e = com.sfb.objects.ShuttleCatalog.get(type);
        if (e != null)
            return e.designation;
        switch (type.toLowerCase()) {
            case "suicide":     return "Suicide";
            case "scatterpack": return "ScatterPack";
            default:
                // Capitalize first letter for unknown types
                return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }
}
