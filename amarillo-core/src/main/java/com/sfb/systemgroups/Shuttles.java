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

    /** This carrier's fighter squadrons (J4.46). Empty on anything that carries none. */
    private final List<com.sfb.objects.Squadron> squadrons = new ArrayList<>();

    /**
     * Fighters this ship was DESIGNED to carry, which is not the same as how many it has.
     * <p>
     * J4.463 hangs the EW-fighter allowance on the design: "this ability to operate two
     * EWFs remains even if combat casualties reduce the total number of fighters during
     * the scenario (or even during a campaign)." Counted once at init and never reduced.
     */
    private int designedFighterComplement;

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
                com.sfb.objects.FighterComplement complement = null;
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
                    // J1.53: outside parking on a track, belonging to THIS bay. Not spaces —
                    // a balcony position cannot be destroyed and holds no ready rack, so it is
                    // a capacity on the bay rather than another ShuttleSpace.
                    Object balconyObj = bayObj.get("balconyPositions");
                    if (balconyObj instanceof Number)
                        bay.setBalconyPositions(((Number) balconyObj).intValue());
                    // J4.62/J4.621: boxes in this bay that have a ready rack although no fighter
                    // sits in them. Recorded now and EQUIPPED later, because what a rack serves
                    // depends on the scenario year (J4.621) and nothing here knows it yet - the
                    // same reason a carrier's complement is re-seated rather than built now.
                    Object racksObj = bayObj.get("readyRacks");
                    if (racksObj instanceof Number)
                        bay.setReadyRackBoxes(((Number) racksObj).intValue());
                    // J4.4: fighters declared by role, filled in by the year below.
                    complement = com.sfb.objects.FighterComplement.fromBayMap(bayObj.get("fighters"));
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
                if (complement != null) {
                    // The bay keeps the declaration so the scenario can re-seat it for ITS
                    // year; this first seating uses the ship's own service year, so a ship
                    // built outside any scenario still has the fighters it entered service
                    // with rather than an empty bay.
                    bay.setFighterComplement(complement);
                    for (int i = 0; i < complement.total(); i++)
                        bay.addEmptySpace();
                    // "serviceyear", all lower case — that is the key ShipSpec.toValuesMap writes
                    // and Ship.init reads. Spelling it serviceYear here returned null, every
                    // carrier fell back to its earliest era, and a Hydran RN+ lost the Stinger-E
                    // it is defined by.
                    Object sy = values.get("serviceyear");
                    // typeCount is the SHIP's counter, shared with the literal list above, so
                    // fighters spread over several bays are numbered once through rather than
                    // restarting in each — three bays of Stinger-1s must not all begin at one.
                    complement.applyTo(bay, sy instanceof Number ? ((Number) sy).intValue() : 0,
                            "", typeCount);
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
        organiseSquadrons();
    }

    // -------------------------------------------------------------------------
    // Squadrons (J4.46)
    // -------------------------------------------------------------------------

    public List<com.sfb.objects.Squadron> getSquadrons() { return squadrons; }

    /** Fighters this ship was designed to carry (J4.463), however many survive. */
    public int getDesignedFighterComplement() { return designedFighterComplement; }

    /**
     * J4.463: how many EW fighters this carrier may field, from the complement it was
     * DESIGNED for — under eight, none at all; under sixteen, one; sixteen to
     * twenty-four, two; twenty-five or more, three.
     */
    public int allowedEwFighters() {
        int designed = designedFighterComplement;
        if (designed < 8)
            return 0;
        if (designed < 16)
            return 1;
        return designed <= 24 ? 2 : 3;
    }

    /**
     * Put this turn's carrier-generated EW into each squadron's pool (J4.93, J4.931).
     * <p>
     * Called from the energy allocation, which is where the rule puts it: J4.931 has the points
     * "generated in a special manner (under an equal limit) separately from those points
     * generated by the carrier for its own use". Every squadron's pool is cleared first, so a
     * turn in which nothing is declared lends nothing rather than repeating last turn.
     *
     * @param declared   squadron name to {ecm, eccm}, as the allocation carried it
     * @param capable    J4.931/J4.6: only an actual carrier may use this procedure, never a
     *                   casual one — a false here zeroes every pool
     * @param pointLimit J4.931's "equal limit", which is the ship's own generation limit;
     *                   J4.942's separate sensor-rating cap is always the looser of the two and
     *                   so never bites
     * @return a line per squadron that was given something, for the allocation log
     */
    public java.util.List<String> applyCarrierEw(
            java.util.Map<String, int[]> declared, boolean capable, int pointLimit) {
        java.util.List<String> log = new java.util.ArrayList<>();
        for (com.sfb.objects.Squadron squadron : squadrons)
            squadron.resetCarrierEw();
        if (!capable || declared == null || declared.isEmpty() || pointLimit <= 0)
            return log;

        for (com.sfb.objects.Squadron squadron : squadrons) {
            int[] want = declared.get(squadron.getName());
            if (want == null || want.length < 2)
                continue;
            int ecm = Math.max(0, want[0]);
            int eccm = Math.max(0, want[1]);
            if (ecm + eccm > pointLimit) {
                log.add(squadron.getName() + ": " + (ecm + eccm) + " points of lent EW exceeds"
                        + " the " + pointLimit + "-point limit (J4.931); trimmed");
                // Trim the ECCM half first, arbitrarily but predictably — a player who has
                // overspent gets a legal allocation rather than a refused turn.
                eccm = Math.max(0, pointLimit - ecm);
                ecm = Math.min(ecm, pointLimit);
            }
            squadron.setCarrierEw(ecm, eccm);
            if (ecm > 0 || eccm > 0)
                log.add(squadron.getName() + ": " + ecm + " ECM / " + eccm
                        + " ECCM generated for lending (J4.93)");
        }
        return log;
    }

    /** EW and two-seat fighters this carrier currently has, across every squadron. */
    public int ewFightersAboard() {
        int n = 0;
        for (com.sfb.objects.Squadron sq : squadrons)
            n += sq.ewFighterCount();
        return n;
    }

    /**
     * Sort this carrier's fighters into squadrons (J4.461: "The carrier must organize its
     * fighters into the minimum number of squadrons").
     * <p>
     * The minimum, so twelve fighters make ONE squadron of twelve rather than two of six,
     * and eighteen make two — of twelve and six, which is the very case J4.463 describes
     * when it allows the smaller one an EW fighter anyway.
     * <p>
     * Done at init so a carrier always has a legal organisation without anyone declaring
     * one; J4.46 lets the player reorganise before the scenario, and J4.465 during it.
     */
    private void organiseSquadrons() {
        squadrons.clear();
        List<com.sfb.objects.shuttles.Fighter> fighters = new ArrayList<>();
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces())
                if (box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter f)
                    fighters.add(f);
        designedFighterComplement = fighters.size();
        if (fighters.isEmpty())
            return;

        // EW fighters first, so each lands in a squadron of its own rather than piling
        // into the first one and tripping J4.463's one-per-squadron limit.
        fighters.sort((a, b) -> Boolean.compare(b.isTwoSeater(), a.isTwoSeater()));

        int slots = 0;
        for (com.sfb.objects.shuttles.Fighter f : fighters)
            slots += f.squadronSlots();
        int needed = Math.max(1,
                (slots + com.sfb.objects.Squadron.MAX_SLOTS - 1)
                        / com.sfb.objects.Squadron.MAX_SLOTS);
        for (int i = 0; i < needed; i++)
            squadrons.add(new com.sfb.objects.Squadron(
                    squadronName(owningUnit == null ? null : owningUnit.getName(), i),
                    owningUnit instanceof com.sfb.objects.Ship sh ? sh : null));

        for (com.sfb.objects.shuttles.Fighter f : fighters)
            for (com.sfb.objects.Squadron sq : squadrons)
                if (sq.add(f) == null)
                    break;
    }

    /** "KHS Ascendant Squadron 1", or just "Squadron 1" on a ship with no name yet. */
    private static String squadronName(String shipName, int index) {
        String prefix = shipName == null || shipName.isBlank() ? "" : shipName + " ";
        return prefix + "Squadron " + (index + 1);
    }

    /**
     * Re-stamp the squadron names this carrier generated, after the ship has been renamed.
     * <p>
     * Called from {@code Ship.setName}, because the order is unavoidable: squadrons are organised
     * while the ship's systems are built, and a scenario names its ships afterwards. Without this
     * every carrier's squadrons wore the placeholder name out of its JSON file — a CVA fielded as
     * "KHS Ascendant" had squadrons called "KHS Olympus Squadron 1", which reached the client.
     * <p>
     * Only the auto-generated names are affected, which is every squadron this class makes; a
     * squadron built directly by a test or a future pre-game choice keeps the name it was given,
     * since it never came through here.
     */
    public void renameSquadronsFor(String shipName) {
        for (int i = 0; i < squadrons.size(); i++)
            squadrons.get(i).setName(squadronName(shipName, i));
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
        double room = droneStore.capacitySpaces() - spacesCommittedForward();
        List<com.sfb.objects.DroneType> pattern = droneLoadoutPattern();
        if (!pattern.isEmpty())
            droneStore.stock(pattern, room);

        // And type-D plasma torpedoes, which share the hold (J4.825). A ship whose fighters
        // carry them has nothing in the drone pattern at all - a plasma-D rail has no design
        // DRONE - so without this the Romulan KRV declared sixty spaces of stores and stocked
        // an empty hold, leaving five Gladiator-Fs that could never be armed.
        // ROOM, not capacity: J4.72 counts what is already forward in the ready racks and on
        // the fighters against the declared total rather than on top of it. Stocking to
        // capacity here put sixty torpedoes in the hold beside the ten already racked, which
        // is seventy spaces of a sixty-space hold.
        if (plasmaDRailCount() > 0)
            droneStore.stockPlasmaDs(room - droneStore.spacesHeld());
    }

    /**
     * Re-stock the hold for the fighters the ship is NOW carrying. SETUP ONLY, after a
     * {@code FighterComplement.reseat}.
     * <p>
     * {@link #stockDroneStore} runs once, while the ship is built, and J4.72 makes the figure
     * it computes depend on what is already forward in the ready racks. Re-seating a bay for a
     * later era changes that, so the hold has to be worked out again — in BOTH directions:
     * <ul>
     *   <li>a Romulan KRV reseated to Y183 flies G-Ds with four rails apiece where the G-F had
     *       two, so twenty spaces sit forward against the ten the hold was sized for: seventy
     *       spaces of torpedoes in a sixty-space ship,</li>
     *   <li>and a Warhawk, whose Y165 Gladiator-1s carry a plasma-F and no rails at all, held
     *       NOTHING — there were no rails to stock for when it was built. Reseated to Y183 its
     *       G-3Ks have two rails each and fifty declared spaces of empty hold, so they fired
     *       the two torpedoes in the box and could never reload.</li>
     * </ul>
     * Rebuilt rather than adjusted, because the hold's contents are derived from the racks'
     * designs and the proportions change with the squadron.
     */
    public void restockDroneStore() {
        if (droneStore == null)
            return;
        stockDroneStore(droneStore.capacitySpaces());
    }

    /**
     * What the ship's drone fighters are built around — one entry per rail, of what that rail
     * is designed to carry. The quartermaster's stocking list, and the proportions a mixed
     * squadron actually needs.
     */
    /** Plasma-D rails across every fighter aboard - whether this ship stocks torpedoes. */
    private int plasmaDRailCount() {
        int rails = 0;
        for (ShuttleBay bay : bays)
            for (ShuttleSpace box : bay.getSpaces()) {
                com.sfb.objects.shuttles.Shuttle occupant = box.getShuttle();
                if (occupant == null)
                    continue;
                for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
                    if (w instanceof com.sfb.weapons.DroneRail rail && rail.isPlasmaD())
                        rails++;
            }
        return rails;
    }

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
     * Energy this ship's squadron still needs to activate its type-D plasma torpedoes
     * (FP9.22), at half a point apiece.
     * <p>
     * The Energy Allocation line's ceiling, as {@link #capacitorPowerWanted} is for box
     * capacitors - and a separate line because it is a separate thing: a capacitor belongs to
     * the BOX and survives the fighter leaving, while an activation belongs to a torpedo on a
     * particular mount and dies with it (FP10.33).
     * <p>
     * Counts the craft parked on a balcony as well as those in boxes. A parked fighter is one
     * J1.53 lets a carrier launch at once, and FP10.32 allows activation "at any point after
     * loading and before firing" - so a torpedo out on the track is precisely one worth paying
     * for. Nothing in J1.531's list of what cannot be done out there touches it: that bars
     * rearming and repair by deck crews, and this is the ship's own energy.
     */
    public double plasmaDActivationWanted() {
        double wanted = 0;
        for (ShuttleBay bay : bays) {
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getInventory())
                wanted += activationWantedBy(craft);
            for (com.sfb.objects.shuttles.Shuttle craft : bay.getBalcony())
                wanted += activationWantedBy(craft);
        }
        return wanted;
    }

    private static double activationWantedBy(com.sfb.objects.shuttles.Shuttle craft) {
        double wanted = 0;
        for (com.sfb.weapons.Weapon w : craft.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.DroneRail rail)
                wanted += rail.activationEnergyWanted();
        return wanted;
    }

    /**
     * Spend energy activating type-D torpedoes (FP9.22), half a point each.
     * <p>
     * Rails are activated in order and each is all-or-nothing: half a point buys one torpedo
     * or none, so an odd quarter point left at the end buys nothing rather than half-arming
     * something. FP10.32 lets this energy be allocated OR drawn from reserve at any time
     * before firing, so the same method serves both - the caller knows which pocket it came
     * from, and the rule does not care.
     *
     * @return energy actually spent, which a short offer leaves below what was handed in
     */
    public double activatePlasmaDs(double energy) {
        double spent = 0;
        for (ShuttleBay bay : bays) {
            java.util.List<com.sfb.objects.shuttles.Shuttle> craft =
                    new java.util.ArrayList<>(bay.getInventory());
            craft.addAll(bay.getBalcony());
            for (com.sfb.objects.shuttles.Shuttle c : craft)
                for (com.sfb.weapons.Weapon w : c.getWeapons().fetchAllWeapons())
                    if (w instanceof com.sfb.weapons.DroneRail rail
                            && rail.activationEnergyWanted() > 0
                            && energy - spent + 1e-9 >= com.sfb.weapons.DroneRail.ACTIVATION_ENERGY
                            && rail.activateTorpedo(com.sfb.weapons.DroneRail.ACTIVATION_ENERGY))
                        spent += com.sfb.weapons.DroneRail.ACTIVATION_ENERGY;
        }
        return spent;
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
        // A plasma-D rack counts torpedoes, each one space (FP9.21), and has no DroneType
        // slots to measure - so slotsMissing would report nothing to do beside an empty rack.
        if (box.getReadyRack().isPlasmaD()) {
            double torpedoes = Math.min(box.getReadyRack().plasmaDMissing(),
                    store.plasmaDCount());
            return (int) Math.round(torpedoes * FighterArming.HALF_ACTIONS_PER_ACTION);
        }
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
        return com.sfb.objects.ShuttleCatalog.displayNameOf(type);
    }
}
