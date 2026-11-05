package com.sfb.scenario;

import com.sfb.exceptions.CapacitorException;
import com.sfb.objects.Drone;
import com.sfb.objects.DroneType;
import com.sfb.objects.shuttles.ScatterPack;
import com.sfb.objects.Ship;
import com.sfb.objects.ShipLibrary;
import com.sfb.properties.Faction;
import com.sfb.objects.ShipSpec;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.objects.shuttles.SuicideShuttle;
import com.sfb.objects.Terrain;
import com.sfb.properties.Location;
import com.sfb.properties.TerrainType;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.weapons.ADD;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts a ScenarioSpec into configured Ship objects ready to be added to a Game.
 *
 * Heading → facing mapping (SFB standard, 24-step internal system):
 *   A=1, B=5, C=9, D=13, E=17, F=21
 *
 * Weapon status effects on initial ship state (S4.10–S4.13):
 *   WS-0: capacitors uncharged (cannot hold energy until 1 pt spent to energize)
 *   WS-1: capacitors charged, phaser cap empty
 *   WS-2: capacitors charged, phaser cap full
 *   WS-3: capacitors charged, phaser cap full (multi-turn weapon pre-arming deferred)
 */
public class ScenarioLoader {

    /**
     * Build all ships for every side in the scenario.
     * Returns a list of ship lists, one per side, in the same order as spec.sides.
     * Ships that cannot be resolved (missing spec) are skipped with a warning.
     */
    public static List<List<Ship>> loadShips(ScenarioSpec spec) {
        List<List<Ship>> result = new ArrayList<>();
        if (spec.sides == null)
            return result;
        for (ScenarioSpec.SideSpec side : spec.sides) {
            List<Ship> ships = new ArrayList<>();
            // A side may list no ships at all: one marked bringYourOwn is waiting for a fleet,
            // and has none until somebody hands it one.
            if (side.ships == null) {
                result.add(ships);
                continue;
            }
            for (ScenarioSpec.ShipSetup setup : side.ships) {
                String faction = (setup.faction != null && !setup.faction.isBlank()) ? setup.faction : side.faction;
                Ship ship = buildShip(faction, setup, spec.year,
                        spec.commanderOptions != null
                                ? spec.commanderOptions.maxDroneSpeed : null);
                if (ship != null) ships.add(ship);
            }
            result.add(ships);
        }
        return result;
    }

    private static Ship buildShip(String faction, ScenarioSpec.ShipSetup setup, int year,
            Integer maxDroneSpeed) {
        ShipSpec shipSpec = ShipLibrary.get(faction, setup.type);
        if (shipSpec == null) {
            System.err.println("ScenarioLoader: no spec found for "
                    + faction + "/" + setup.type + " — ship skipped");
            return null;
        }
        // Refits the scenario asked for that nobody named a hull after (S3.24). This field has
        // been written by FleetsToScenario since it was added and read by nothing, so a fleet's
        // refits reached a battle only when they happened to name a variant type.
        if (setup.refits != null && !setup.refits.isEmpty())
            shipSpec = com.sfb.objects.RefitResolver.apply(shipSpec, setup.refits);
        Ship ship = ShipLibrary.createShip(shipSpec);
        ship.setName(setup.shipName);
        ship.getShuttles().prefixShuttleNames(setup.shipName);
        // A ground base states a PLANET and a face instead of a hex: its position, heading and
        // speed are all consequences of where it was built (R1.14, P2.62), and Game.setupFromScenario
        // sites it once the terrain is on the map. Reading startHex here would throw on the null
        // it is entitled to have.
        if (setup.planet == null || setup.planet.isBlank()) {
            ship.setLocation(parseHex(setup.startHex));
            ship.setFacing(parseHeading(setup.startHeading));
            ship.setSpeed(setup.startSpeed);
            // Seed C2.2 speed history — assume ship has been at startSpeed for at least 2 turns
            ship.setSpeedPreviousTurn(setup.startSpeed);
            ship.setSpeedTwoTurnsAgo(setup.startSpeed);
        }
        // Every year-gated rule, in one call, so the three other places that build a ship cannot
        // drift from this one again. applyWeaponStatus stays here: it is scenario setup, not a
        // fact about the hull in that year, and it must follow the fighter re-seat so it arms the
        // craft the carrier ends up with.
        outfitForYear(ship, faction, year, shipSpec, maxDroneSpeed);
        // The loadout the SHIPYARD fixed (G15.4), before weapon status so a mounted weapon is
        // armed with the rest, and after outfitForYear so the year rules are already in place -
        // equip reapplies the ones a later-built weapon would otherwise miss.
        equipDeclaredMounts(ship, setup.optionMounts, setup.optionMountArcs, year);
        applyWeaponStatus(ship, setup.weaponStatus);
        return ship;
    }

    /**
     * Fill the option mounts somebody has already paid for (G15.4).
     *
     * <h2>Why this is not the COI step</h2>
     * G15.415: "Optional weapons cannot be changed without a shipyard overhaul taking several
     * weeks or months." A loadout is part of what the ship IS, so it arrives with the ship —
     * from a saved fleet, or from the scenario that listed it. What the COI step still does is
     * fill mounts NOBODY has bought, which is a scenario delegating the shipyard to its players.
     *
     * <p>Nothing arbitrates between the two: {@code validate} refuses a mount that is already
     * filled, so a COI submission simply cannot overwrite what is here.
     *
     * <p>An illegal or unknown choice is NOTED on the ship and skipped rather than thrown. A
     * scenario or a fleet saved under one year's rules may be played under another, and losing
     * the whole battle to one unavailable weapon helps nobody.
     */
    static void equipDeclaredMounts(Ship ship, java.util.Map<String, String> choices, int year) {
        equipDeclaredMounts(ship, choices, null, year);
    }

    /**
     * @param arcs R8.R3's wing arc choice per mount, designator -> "FORWARD"/"OUTBOARD". Bought
     *             with the weapon, because the rule leaves a wing disruptor or fusion beam's arc
     *             to "the taste of the Orion player" and nothing derives it.
     */
    static void equipDeclaredMounts(Ship ship, java.util.Map<String, String> choices,
            java.util.Map<String, String> arcs, int year) {
        if (choices == null || choices.isEmpty())
            return;
        com.sfb.objects.OptionMountCatalog catalog =
                com.sfb.objects.OptionMountCatalog.loadDefault();
        for (java.util.Map.Entry<String, String> choice : choices.entrySet()) {
            try {
                com.sfb.objects.OptionMountLoadout.equip(
                        ship, catalog, choice.getKey(), choice.getValue(), year,
                        com.sfb.objects.OptionMountArcs.parse(
                                arcs == null ? null : arcs.get(choice.getKey())));
            } catch (IllegalArgumentException e) {
                note(ship, "option mount " + choice.getKey() + " (" + choice.getValue()
                        + ") — " + e.getMessage());
            }
        }
    }

    /**
     * J4.4: re-seat each bay's declared fighter complement for the SCENARIO's year.
     * <p>
     * A ship is built from its own file, which knows only its service year, so a Kzinti CVS
     * arrives flying the AAS it entered service with in Y170. This is where it re-equips: HAAS
     * from Y173, TAAS from Y177, TADS from Y180, TADSC from Y183. A bay whose contents were
     * listed literally has no complement and is left exactly as the file wrote it.
     * <p>
     * Before {@code applyWeaponStatus}, which arms whatever is aboard — arming the outgoing
     * fighters and then swapping them would throw the work away.
     */
    private static void applyFighterComplement(Ship ship, int year) {
        // The work moved to FighterComplement.reseat: the fleet resolver and the ship-catalogue
        // endpoint need the same re-seating, and while this was private to the scenario loader
        // they both quietly sold carriers at their hull's service year.
        for (String n : com.sfb.objects.FighterComplement.reseat(ship, year))
            note(ship, n);
    }

    /**
     * Bring an already-built hull up to the year it is being fielded in.
     *
     * <h2>Why this is public, and why everything that builds a ship must call it</h2>
     * A hull is loaded from its file as it was when it entered service. Five separate rules then
     * move it forward to the scenario's year, and until this existed only the scenario loader ran
     * all five. The three other places that build a ship — the shelf catalogue, the ship-detail
     * endpoint behind the SSD viewer, and {@link FleetLoader}, which is what the fleet validator
     * prices — each ran the fighter re-seat alone.
     *
     * <p>So a Federation NCD inspected at Y176 showed the type-A drone racks it was built with
     * instead of the type-B racks it fights with, and more quietly, the builder charged for it at
     * its as-built BPV: {@link #applyYearUpgrades} adds a Y175 block's {@code refitCost}, and
     * 46 hulls and refits declare one. You bought a ship the battle then handed you a different
     * version of.
     *
     * <p>This is the second time this shape has bitten. {@code applyFighterComplement}'s own
     * comment records the first: the re-seating was private here, so the resolver and the
     * catalogue "both quietly sold carriers at their hull's service year", and the fix was to
     * extract {@link com.sfb.objects.FighterComplement#reseat}. The other four appliers were left
     * behind. Hence one entry point rather than four more extractions.
     *
     * <p><b>Mutates the ship, including its BPV, so it must run exactly once.</b> Calling it twice
     * charges a Y175 refit twice over.
     *
     * @param faction the ship's empire; the Y175 rack defaults are keyed on it. Tolerates null,
     *                which simply means no faction default applies.
     */
    public static void outfitForYear(Ship ship, String faction, int year, ShipSpec spec) {
        outfitForYear(ship, faction, year, spec, null);
    }

    /**
     * @param maxDroneSpeed the scenario's drone speed cap, or null for the year's own answer.
     *                      Only {@link #applyDroneSpeed} reads it; every other year rule here is
     *                      a fact about the hull in that year and no scenario overrides those.
     */
    public static void outfitForYear(Ship ship, String faction, int year, ShipSpec spec,
            Integer maxDroneSpeed) {
        applyFighterComplement(ship, year);
        if (spec != null)
            applyYearUpgrades(ship, faction == null ? "" : faction, year, spec);
        applyEsgCapacitors(ship, year);
        applyFusionHolding(ship, year);
        applyDroneSpeed(ship, year, maxDroneSpeed);
        // Annex #8B: "Orion disruptors with ranges more than 22 include DERFACS from Y168."
        // A year rule like the two above, not a refit. It finds nothing on an Orion built
        // today, because every Orion disruptor arrives through an option mount and mounts are
        // filled later, in applyCoi - OptionMountLoadout.equip runs the same rule there. It is
        // here for a hull that declares a disruptor in its own weapons, which is where a reader
        // will look for it.
        ship.applyOrionDerfacs(year);
        applyUimAvailability(ship, year);
    }

    /** BPV a single ESG capacitor is worth (G23.245). */
    private static final int ESG_CAPACITOR_BPV = 1;

    /**
     * Fit ESG capacitors by scenario year (G23.24), and price them. The Lyrans fielded them
     * Y167–169; a ship in a Y167+ battle carries them regardless of when its hull was introduced.
     * The rare G17.5 "repaired without a capacitor" case is not modelled.
     *
     * <p>G23.245, in full: "The BPV of Lyran ships assumes the presence of the capacitors on all
     * ESGs. If the scenario is set before the capacitors were installed, reduce the BPV by one
     * point per ESG. A ship will have capacitors on all of its ESGs or on none of them." So the
     * stated BPV is the Y167-and-later figure on all 26 ESG hulls, and an earlier battle refunds
     * per generator — two points on the two-ESG ships, one on the Scout.
     *
     * <p>Fitting the capacitors and refunding them belong together because the rule ties them
     * together: the refund is owed exactly when the fitting is withheld. They were split for a
     * while, and the symptom was the sort that never surfaces on its own — a Y160 Lyran CC fought
     * without capacitors and was charged for them anyway. What turned it up was the Lyran Base
     * Station's SSD, whose own data table prices the pre-Y167 base two points lower; the owner
     * confirmed the reading (2026-10-09). Nothing in the ship data says any of this, and nothing
     * should: 26 files would each have to repeat a rule that is the same for all of them.
     *
     * <p>Charged per ESG, not per ship, and NOT keyed on the Lyrans. The rule names them because
     * they are who flies ESGs, but what it prices is the generator — so a WYN or LDR hull that
     * ever mounts one pays and is refunded on the same terms, exactly as the fitting above
     * already works for anyone.
     */
    static void applyEsgCapacitors(Ship ship, int year) {
        boolean hasCapacitors = year >= 167;
        int esgs = 0;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.ESG) {
                ((com.sfb.weapons.ESG) w).setHasCapacitor(hasCapacitors);
                esgs++;
            }
        }
        // A year of 0 is "nobody has picked a date yet", which the catalogue endpoint passes when
        // the shelf is browsed undated. It is not a date before Y167, so it buys no refund — the
        // same guard applyUimAvailability keeps, and for the same reason: an undated browse must
        // quote the hull's own price rather than a discount it did not ask for.
        if (year > 0 && !hasCapacitors && esgs > 0)
            ship.setBattlePointValue(ship.getBattlePointValue() - esgs * ESG_CAPACITOR_BPV);
    }

    /**
     * E7.5: fit the fusion holding system by scenario year. The Hydrans developed it in Y168 and
     * "had installed it on virtually all fusion-armed ships by the time the Hydrans entered the
     * General War in Y169", and "there is no cost for this refit".
     * <p>
     * Universal rather than faction-keyed, like the ADD and plasma-rack upgrades in
     * {@code applyYearUpgrades} and for the same reason: the rule is about the WEAPON, so anyone
     * who ever mounts a fusion gets it on the same date. Its own method rather than a branch
     * inside {@code applyYearUpgrades}, because that one returns early below Y175.
     * <p>
     * Set in BOTH directions, exactly as {@code applyEsgCapacitors} does, so the loader is the
     * single authority and a Y134 hull cannot inherit the modern default. Before this existed
     * the four Y134 Hydran hulls - Hunter, Lancer, Ranger and Small Q-Ship - could hold their
     * fusions in a pre-refit scenario, which E7.23 forbids outright.
     */
    static void applyFusionHolding(Ship ship, int year) {
        boolean hasHolding = year >= 168;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.Fusion fusion) {
                fusion.setHoldingSystem(hasHolding);
            }
        }
    }

    /**
     * Fit the drones the scenario's year flies, and charge the ship for them (S2.11).
     *
     * <h2>The rule</h2>
     * <blockquote>S2.11: "The BPV of all ships (unless otherwise noted) does not include the
     * drone speed upgrade charges; ships are required to pay these charges based on the date of
     * the scenario. Players may use slower drones in special cases (asteroids, webs, bases)
     * where faster drones are not appropriate."</blockquote>
     *
     * <p>FD2.454 fills in what that means in practice, including the part that reads like a
     * contradiction and is not: "Ships are not required to pay for the drone speed that is
     * general availability, and might for tactical reasons have some slower drones on board...
     * A drone-using ship used during a period when medium- or high-speed drones are used is not
     * forced to purchase those faster drones (which in effect increases their BPV)." The year's
     * speed is the NORMAL fitting, charged to the ship rather than to its option budget; a
     * player may decline it and sail with something slower, which they do by naming a loadout in
     * the Commander's Options step. FD2.454 again: "Normally, all drones on a given ship are of
     * the same speed."
     *
     * <h2>What this used to do instead</h2>
     * Nothing. {@code WeaponFactory} filled every rack with type-I-slows and no year rule ever
     * looked at them again, so a Y185 Kzinti CVA sailed with speed-8 drones against ships moving
     * 31 -- drones this file's own {@code DroneType} header calls "obsolete once medium-speed
     * drones arrive in Y165". The default is the loadout most players will use, because it is
     * what they get by not opening the drone dialog at all, and it was the worst one available.
     *
     * <h2>The carrier's half of it</h2>
     * FD2.45 charges "a single loading of each launch rack <b>and/or fighter</b>", so a drone
     * fighter's loading is upgraded and paid for exactly as a rack's is. For a carrier that
     * loading lives in the fighter's ready rack (J4.8222 stocks each box with "the same drones
     * that the fighter it is designed to service carries"), on the rails of whichever fighters
     * have already been armed from their boxes, or split between the two — so
     * {@link #refitFighterDrones} walks both, and {@link #chargeDroneSpeed} counts both.
     * <p>
     * The carrier's hold (J4.7, {@link com.sfb.systemgroups.DroneStore}) is refitted with them
     * and charged for none of it, which is the rest of FD2.45's sentence: "the reloads (and
     * drones stored on a carrier for use by its fighters) are presumed to be of the same type
     * without additional cost." A squadron whose reloads flew slower than its first sortie would
     * be a worse bug than the free fast drones this closes.
     *
     * <h2>What is still NOT upgraded</h2>
     * Scatter-packs and MRS shuttles, and deliberately: FD3.45 and FD3.85 say a unit "may be
     * presumed to have unloaded enough drones for use in the allowed number of scatter-packs",
     * so a scatter-pack's drones come out of a loading this method has already refitted and
     * already billed. Charging them again would charge the same drones twice.
     */
    static void applyDroneSpeed(Ship ship, int year) {
        applyDroneSpeed(ship, year, null);
    }

    /**
     * @param maxDroneSpeed a scenario's cap on what may fly, or null for the year's own answer.
     *                      A ship must not be fitted with drones its own scenario forbids: the
     *                      COI step refuses a loadout over the cap and the dialog will not offer
     *                      one, so a rack filled past it is a ship carrying drones nobody can
     *                      name. That is what shipped: nivram_assault is Y180 with a cap of 20,
     *                      and its ships sailed with speed-32 drones the picker had no row for.
     */
    static void applyDroneSpeed(Ship ship, int year, Integer maxDroneSpeed) {
        // No undated-year guard, unlike applyEsgCapacitors and applyUimAvailability, and that is
        // deliberate: a year of 0 is "nobody has picked a date yet", and Propulsion.generalIn
        // answers an undated year with the frame's own baseline -- so nothing is upgraded and
        // nothing is owed, by arithmetic rather than by a branch. The two methods that DO guard
        // have to, because they subtract BPV for a date that has not arrived. This one does not,
        // and a guard here would be a line no test could tell from its absence.
        // anUndatedShipIsLeftExactlyAsItsDataStatesIt pins the behaviour either way.
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack rack)) continue;
            upgradeLoad(rack.getAmmo(), year, maxDroneSpeed);
            // FD2.45: "the reloads... are presumed to be of the same type without additional
            // cost." So they follow the rack and are charged for nothing, which is also why
            // chargeDroneSpeed below counts the loaded drones only.
            for (List<Drone> set : rack.getReloads())
                upgradeLoad(set, year, maxDroneSpeed);
        }
        refitFighterDrones(ship, year, maxDroneSpeed);
        chargeDroneSpeed(ship, year, maxDroneSpeed);
    }

    /**
     * The same refit for the drones a carrier keeps for its fighters (FD2.45, J4.7, J4.8222).
     *
     * <p>Three places, because a drone bound for a fighter rests in three: the box's ready rack,
     * the rail of a fighter already armed from it, and the hold behind both. A refit that reached
     * only the racks would put the year's fast drones in the box and leave slow ones on the
     * fighter that had been loaded first, which is the one state J4.8223 guarantees a carrier
     * at weapon status 2 or 3 will actually be in.
     *
     * <p>Note that a rail is a {@link com.sfb.weapons.DroneRail}, which extends
     * {@code DroneRack} — so it would have been caught by the loop above if it were among the
     * SHIP's weapons. It is not; it belongs to the fighter.
     */
    private static void refitFighterDrones(Ship ship, int year, Integer maxDroneSpeed) {
        java.util.function.UnaryOperator<Drone> refit =
                d -> upgradeOne(d, year, maxDroneSpeed);
        // The DESIGN half of the same refit. Now that a design slot IS a drone, it is the very
        // same operator the contents go through -- a rack holding type-I-FASTs against a design
        // of type-Is is a multiset difference between two vocabularies, and slotsMissing
        // reports every slot of a full rack missing. See ReadyRack.refitDesign.
        java.util.function.UnaryOperator<Drone> redesign = refit;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces()) {
                if (box.getReadyRack() != null) {
                    box.getReadyRack().refit(refit);
                    // Unconditionally, occupant or none: J4.62's casual carrier has ready racks
                    // and no fighters, and its racks go stale exactly as a carrier's would.
                    box.getReadyRack().refitDesign(redesign);
                }
                com.sfb.objects.shuttles.Shuttle occupant = box.getShuttle();
                if (occupant == null)
                    continue;
                for (com.sfb.weapons.Weapon w : occupant.getWeapons().fetchAllWeapons())
                    if (w instanceof com.sfb.weapons.DroneRail rail) {
                        upgradeLoad(rail.getAmmo(), year, maxDroneSpeed);
                        // A podded rail is refitted too: J4.962 takes it out of the RACK, not
                        // out of the year, so losing the pod leaves a rail the year can arm.
                        DroneType now = upgradeDesign(rail.getDesignDrone(), year,
                                maxDroneSpeed);
                        if (now != null)
                            rail.setDesignDrone(now);
                    }
            }
        if (ship.getShuttles().getDroneStore() != null)
            ship.getShuttles().getDroneStore().refit(refit);
    }

    /** Refit one rack-load to the year's speed, in place, within the scenario's cap. */
    private static void upgradeLoad(List<Drone> load, int year, Integer maxDroneSpeed) {
        if (load == null) return;
        for (int i = 0; i < load.size(); i++) {
            Drone now = upgradeOne(load.get(i), year, maxDroneSpeed);
            if (now != null)
                load.set(i, now);
        }
    }

    /**
     * One drone refitted to the year's speed, or null if it is already there or cannot move.
     * <p>
     * Split out of {@link #upgradeLoad} so the three places a drone sits OUTSIDE a list this
     * loader can walk — a fighter's ready rack, a carrier's hold, a rail — can be refitted
     * through their owners' own {@code refit} doors with the same arithmetic. One copy, so a
     * carrier's fighters cannot fly a different drone from the ship that launched them.
     */
    /**
     * The engine this drone should be flying in this scenario (S2.11), cap and all.
     *
     * <p>Its own method because two things ask it: the DRONE in a rack or on a rail, and the
     * rail's DESIGN. They have to give the same answer or the rack's design and its contents
     * part company — see {@link com.sfb.systemgroups.ReadyRack#redesignFrom}.
     *
     * <p>The scenario's cap is not a rule of the game but a restriction on what flies, so it
     * narrows the fitting rather than changing what the year makes standard. Capping DOWN only:
     * a cap below the frame's own slowest engine cannot invent a drone the chart has no row
     * for, and FD2.227's type-VI has no speed-8 version to fall to.
     */
    private static com.sfb.objects.Propulsion wantedPropulsion(
            com.sfb.objects.DroneSpec spec, int year, Integer maxDroneSpeed) {
        com.sfb.objects.Propulsion want =
                com.sfb.objects.Propulsion.generalIn(year, spec.effectiveFrame());
        if (maxDroneSpeed == null || want.speed <= maxDroneSpeed)
            return want;
        com.sfb.objects.Propulsion capped =
                com.sfb.objects.Propulsion.baselineFor(spec.effectiveFrame());
        for (com.sfb.objects.Propulsion p : com.sfb.objects.Propulsion.values())
            if (p.fits(spec.effectiveFrame()) && p.speed <= maxDroneSpeed
                    && p.speed > capped.speed && p.speed <= want.speed
                    && p.isImprovement())
                capped = p;
        return capped;
    }

    /**
     * The same upgrade applied to a rail's DESIGN rather than to a drone (S2.11).
     *
     * <p>A design is a {@link DroneType} and carries no spec of its own to refit, so it is
     * resolved through {@code DroneType.atSpeed} exactly as {@link #upgradeOne} resolves the
     * drone — a medium-speed standard drone is a type-IM with three turns of fuel, not a
     * type-II with two.
     *
     * @return the type this rail should now be designed around, or null if nothing changed
     */
    private static DroneType upgradeDesign(DroneType was, int year, Integer maxDroneSpeed) {
        if (was == null || was.spec == null)
            return null;
        com.sfb.objects.Propulsion want = wantedPropulsion(was.spec, year, maxDroneSpeed);
        if (want.speed <= was.spec.propulsion().speed)
            return null;
        DroneType sibling = was.atSpeed(want);
        return sibling == was ? null : sibling;
    }

    private static Drone upgradeOne(Drone was, int year, Integer maxDroneSpeed) {
        if (was == null || was.getDroneType() == null || was.getSpec() == null) return null;
        com.sfb.objects.DroneSpec spec = was.getSpec();
        com.sfb.objects.Propulsion want = wantedPropulsion(spec, year, maxDroneSpeed);
        if (want.speed <= spec.propulsion().speed) return null;
        // Through the catalogue where there is an entry, because the sibling carries the
        // endurance with it (DroneType.atSpeed): a medium-speed standard drone is a type-IM
        // with three turns of fuel, not a type-II with two. A drone built in the workshop
        // (FD10.2) keeps its payload and swaps only the engine.
        DroneType basis = was.getDroneType();
        DroneType sibling = basis.atSpeed(want);
        return new Drone(sibling != null ? sibling : basis, spec.withPropulsion(want));
    }

    /**
     * Price the drones the ship is actually carrying, and put the figure on the ship (S2.11).
     *
     * <p>Idempotent, and deliberately so: it SETS the charge from what is in the racks rather
     * than adding to it, so the Commander's Options step can call it again after swapping the
     * year's fast drones for something slower and get the right answer rather than a double one.
     *
     * <p>Loaded drones only. FD2.45: "The cost of improved drones (FD2.22) is paid only for a
     * single loading of each launch rack and/or fighter; the reloads (and drones stored on a
     * carrier for use by its fighters) are presumed to be of the same type without additional
     * cost." FD2.454's worked example counts the same way -- a Kzinti CS's sixteen points for
     * fast drones is its four racks of four, not the reloads behind them.
     *
     * <h2>"and/or fighter": the ready racks are charged too</h2>
     * A drone-armed fighter is a launch rack for this rule's purposes -- FD2.45 says so in as
     * many words, "fighters are equivalent to drone racks for the purpose of loading ready
     * racks" -- so a carrier pays for one loading of each of them. It did not, and a Kzinti CVS
     * therefore flew a squadron of fast drones for nothing while the frigate beside it paid for
     * sixteen.
     *
     * <p>Charged at the READY RACK, which holds exactly that one loading (J4.8222: "each ready
     * rack holds the same drones that the fighter it is designed to service carries"), and
     * <b>the fighters are not counted at all</b>. Owner's ruling, 2026-11-03: "any drones on
     * fighters at the start of a scenario came out of the ready racks, which would be empty."
     * The rack is the whole of the loading whether it is holding it or the squadron is.
     *
     * <p>From the rack's {@link com.sfb.systemgroups.ReadyRack#design()} rather than its
     * contents, and that is the load-bearing part. This first walked the rack's contents PLUS
     * the fighters' rails, on the argument that a drone leaving the rack arrives on a rail so the
     * union is invariant. It is not: a Y183 CVS priced 80 at WS-0, 90 at WS-2 and <b>32</b> at
     * WS-3, because {@code FighterArming.armOccupantFully} loads a rail with
     * {@code new Drone(rail.getDesignDrone())} -- a fresh drone at the frame's baseline -- rather
     * than with what the refit put in the box. Reading the design instead makes the bill depend
     * on the hull and the date alone, which is what S2.11 asks for, and leaves that defect to be
     * fixed where it lives instead of quietly discounting the ship for it.
     *
     * <p>The hold behind the racks is free, by the same sentence of FD2.45 that frees a rack's
     * reloads, so {@link com.sfb.systemgroups.DroneStore} is not read here. J4.621 settles the
     * casual carrier -- an escort with ready racks and no fighter aboard -- the same way, and
     * the per-box walk below gets it for nothing: "pay the cost of speed upgrades for one loading
     * of the ready racks (FD2.45); the rest are held in fighter storage."
     *
     * <p>Rounded half-up at the SHIP, not per rack or per drone: FD10.641 is the only rounding
     * convention the drone rules give ("Round fractions of 0.49- down and 0.50+ up"), and a rack
     * of three medium drones owes a point and a half whatever is in the rack beside it.
     */
    static void chargeDroneSpeed(Ship ship, int year) {
        chargeDroneSpeed(ship, year, null);
    }

    /**
     * @param maxDroneSpeed the scenario's cap, or null for the year's own answer. The ship's own
     *                      racks hold real drones and need no cap -- they were fitted under one
     *                      already -- but a ready rack is priced from its DESIGN, so the speed
     *                      has to be worked out here and a scenario that caps drones at 20 must
     *                      not be billed for 32.
     */
    static void chargeDroneSpeed(Ship ship, int year, Integer maxDroneSpeed) {
        double owed = 0;
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack rack)) continue;
            owed += rackSpeedCost(rack, year);
        }
        owed += readyRackDroneSpeedCost(ship, year, maxDroneSpeed);
        ship.setDroneSpeedBpv((int) Math.round(owed));
    }

    /**
     * One rack's share of the bill: FD2.45's "single loading", which for most racks is simply
     * everything in them.
     *
     * <p>A MAGAZINE launcher is the exception the rule names — "there is a partial exception in
     * the case of H-racks (FD3.84). D-racks are another exception." Its reloads are not behind
     * it in {@link DroneRack#getReloads()} but inside it, as the spare magazines (FD3.43), so
     * {@link DroneRack#getAmmo()} hands back the free ones along with the paid ones and this is
     * where they have to be told apart. FD3.44 pays for one magazine of a type-D's three; a
     * Kzinti battle station was being billed 24 points where the rule asks 8, and a Kzinti TGT
     * the same 24 on a 92-point hull.
     *
     * <h2>Costliest first</h2>
     * FD2.452 settles which magazine is the paid one: "The reload drones are presumed to be of
     * the same cost (or lower) as the drones in the rack." A free reload may be no dearer than
     * what was paid for, so the paid magazine has to be the dearest load in the launcher —
     * otherwise a player fills one magazine with cheap drones, pays for that, and flies three
     * magazines of expensive ones.
     *
     * <p>Filled by SPACES rather than by count, because a magazine is four spaces and FD2.11
     * trades two type-Is for one type-IV at par. Which drones those spaces hold is
     * {@link PaidLoading}'s problem, and the same one the Commander's Option surcharge has
     * ({@link CoiLoadout#rackUpgradeCost(DroneRack, java.util.List, int)}).
     */
    private static double rackSpeedCost(DroneRack rack, int year) {
        double paidSpaces = rack.paidLoadingSpaces();
        java.util.List<Drone> loaded = new ArrayList<>();
        for (Drone d : rack.getAmmo())
            if (d != null && d.getSpec() != null)
                loaded.add(d);

        // The ordinary rack, and every rack but two types: the whole load is the loading, and
        // asking which part of it is dearest would only be a slower way of saying so.
        if (paidSpaces >= rack.getSpaces()) {
            double all = 0;
            for (Drone d : loaded)
                all += d.getSpec().speedCostIn(year);
            return all;
        }
        return PaidLoading.dearestLoading(loaded, paidSpaces,
                Drone::getRackSize, d -> d.getSpec().speedCostIn(year));
    }

    /**
     * FD2.45's "and/or fighter": one full loading per drone ready rack, and nothing per fighter.
     *
     * <p>A destroyed box is skipped. It cannot happen during setup, which is the only time this
     * runs, but J4.831's rule for a box's stores is that they die with it -- so if this ever
     * were re-priced mid-battle, the right answer is that the carrier is not asked to pay for
     * drones that are no longer aboard.
     *
     * <p>A plasma-D or type-K rack costs nothing here and has no design slots to walk, which is
     * right rather than a gap: FP9.18 says type-Ds "do not have guidance options, different
     * speeds, or warhead modules as drones do", so there is no speed to upgrade. A rail carrying
     * an EW pod has no slot either, because {@link com.sfb.systemgroups.ReadyRack#forFighter}
     * already left it out (J4.962).
     */
    private static double readyRackDroneSpeedCost(Ship ship, int year, Integer maxDroneSpeed) {
        double owed = 0;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces()) {
                if (box.isDestroyed() || box.getReadyRack() == null)
                    continue;
                for (Drone slot : box.getReadyRack().design())
                    owed += slotSpeedCost(slot, year, maxDroneSpeed);
            }
        return owed;
    }

    /**
     * What one ready-rack slot's drone costs in speed upgrades, at the year the ship is fielded.
     *
     * <p>Priced through {@link #upgradeOne} rather than by reading the chart again, so the slot
     * is billed for precisely the drone {@link #refitFighterDrones} puts in it -- including the
     * sibling swap that makes a medium type-I a type-IM, and including the scenario cap. A slot
     * the year cannot improve costs whatever its own baseline does, which for a type-I-slow is
     * nothing (S8.133: "the only drones that are included in the BPV of a unit are type-I
     * slow") -- but NOT for a type-III, whose "slow" is speed 12 by FD2.227 and therefore
     * already an upgrade over the type-I the BPV covers.
     */
    private static double slotSpeedCost(Drone slot, int year, Integer maxDroneSpeed) {
        if (slot == null)
            return 0;
        // The slot as it stands, which after a refit is already the year's drone -- upgradeOne
        // answers null and the design is priced where it is. It used to build a baseline drone
        // from the slot's TYPE and upgrade that, which came to the same number only because
        // the design was never refitted.
        Drone fitted = upgradeOne(slot, year, maxDroneSpeed);
        Drone priced = fitted != null ? fitted : slot;
        return priced.getSpec() == null ? 0 : priced.getSpec().speedCostIn(year);
    }

    /** BPV a single UIM is worth (D6.5), read off the "u" refit variants. */
    private static final int UIM_BPV = 5;

    /**
     * D6.56: the year the UIM became available, <b>which is per empire</b>.
     *
     * <p>The prose reads "The UIM became available (to the Klingons) about Y165", and the
     * INSTALLATION list dates each adopter separately: Klingon "See (R3.R3); available Y165 and
     * later", Lyran "See (R11.R4); available <b>Y166</b> and later. Also see (R14.R2) for
     * availability to the <b>LDR in Y170</b>."
     *
     * <p>One constant of 165 handed the Lyran CC a UIM in a Y165 battle, where its own SSD says "No
     * UIM prior to Y166. Reduce BPV -5" — the owner's reading of the SSD is what turned this up. It
     * was exactly one hull in exactly one year, because the CC is the only Lyran UIM hull that can
     * be fielded that early (the CC+ enters service in Y166 and the BCH in Y180), but it was five
     * BPV and a free shift on the range 16-22 band.
     *
     * <p>D6.5 also names the WYN as an early adopter without dating them, and we have no WYN hulls;
     * a faction not listed here falls back to the Klingon year, and if one ever declares a UIM its
     * R-section should be read rather than this default trusted.
     */
    private static final java.util.Map<Faction, Integer> UIM_YEAR_BY_EMPIRE = java.util.Map.of(
            Faction.Klingon, 165,
            Faction.Lyran, 166);

    /** D6.5's general case, and the fallback for an empire D6.56 does not date. */
    private static final int UIM_YEAR = 165;

    /** The year {@code ship}'s empire could first fit a UIM (D6.56). */
    private static int uimYearFor(Ship ship) {
        Faction faction = ship.getFaction();
        return faction == null ? UIM_YEAR : UIM_YEAR_BY_EMPIRE.getOrDefault(faction, UIM_YEAR);
    }

    /**
     * D6.5: strip the UIM from a ship in a scenario earlier than Y165, and refund its BPV.
     * <p>
     * "The UIM became available (to the Klingons) about Y165"; the rule restates it as "available
     * Y165 and later", and YD6.5 adds that it does not exist in the Early Years (Y80-Y120) at all.
     * <p>
     * Two Klingon hulls predate it and still carry one, which is correct data rather than an error:
     * the <b>D7N (Y137)</b> and <b>D7C (Y143)</b> are long-lived hulls whose SSDs print the UIM,
     * because they have one in any scenario from Y165 on. The owner read the D7N's sheet
     * (2026-10-04): it "was shown with 1x UIM in the SSD, but there's a note stating that prior to
     * Y165 it had no UIM and the BPV is 5 less".
     * <p>
     * So the module is a property of the YEAR, not of the hull, exactly like E7.5 fusion holding
     * in {@code applyFusionHolding} above - and like that one, the ship file declares the modern
     * state and the loader takes it away. The five points come straight from the refit variants,
     * where fitting a UIM costs exactly that: D6K 126 / D6Ku 131, D7B 128 / D7Bu 133, D7K 131 /
     * D7Ku 136, D7D 148 / D7Du 153. Removing it has to refund the same, or an early D7N is priced
     * for equipment it is not carrying.
     * <p>
     * Charged per module. Only one-UIM hulls are exercised today - the two-UIM C8 and C9 are Y167
     * and later, so they never reach this - and if a multi-UIM pre-Y165 hull ever appears the SSD
     * should be checked rather than this assumption trusted.
     * <p>
     * DERFACS needs no equivalent: YE3.6 bars it from the Early Years too, but the earliest hull
     * declaring it is the Kzinti TGT at Y130 and the Early Years end at Y120 (Y0.0).
     */
    static void applyUimAvailability(Ship ship, int year) {
        if (year <= 0 || year >= uimYearFor(ship))
            return;
        int removed = ship.removeUims();
        if (removed > 0)
            ship.setBattlePointValue(ship.getBattlePointValue() - removed * UIM_BPV);
    }

    /**
     * Build all terrain objects defined in the scenario.
     * Each entry in spec.terrain becomes one Terrain hex on the map.
     */
    public static List<Terrain> loadTerrain(ScenarioSpec spec) {
        expandTerrainPlans(spec);
        List<Terrain> result = new ArrayList<>();
        if (spec.terrain == null) return result;
        for (ScenarioSpec.TerrainSetup setup : spec.terrain) {
            TerrainType type;
            try {
                type = TerrainType.valueOf(setup.type.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("ScenarioLoader: unknown terrain type '" + setup.type + "' — skipped");
                continue;
            }
            Location loc = parseHex(setup.hex);
            int radius = setup.radius;
            if (radius > 0 && type != TerrainType.GAS_GIANT) {
                System.err.println("ScenarioLoader: radius only applies to GAS_GIANT — ignored for "
                        + setup.type + " at " + setup.hex + " (class-M fills exactly one hex, P2.211)");
                radius = 0;
            }
            Terrain t = new Terrain(type, loc.getX(), loc.getY(), radius);
            t.setName(setup.name != null ? setup.name : setup.type + "-" + setup.hex);
            t.setTokenArt(setup.tokenArt);
            if (setup.rings != null && !setup.rings.isEmpty()) {
                if (type != TerrainType.GAS_GIANT) {
                    System.err.println("ScenarioLoader: rings only apply to GAS_GIANT — ignored for "
                            + setup.type + " at " + setup.hex);
                } else {
                    List<int[]> bands = new ArrayList<>();
                    for (ScenarioSpec.RingBand rb : setup.rings) {
                        int inner = Math.min(rb.inner, rb.outer);
                        int outer = Math.max(rb.inner, rb.outer);
                        if (inner <= radius)
                            System.err.println("ScenarioLoader: ring band inner=" + rb.inner
                                    + " overlaps the giant body (radius " + radius + ") at " + setup.hex
                                    + " — body hexes stay no-entry, ring hexes only where inner > radius");
                        bands.add(new int[] { inner, outer });
                    }
                    t.setRingBands(bands);
                }
            }
            result.add(t);
        }
        return result;
    }

    /**
     * Turn any terrain plans into real hexes, once, writing them into the spec itself.
     * <p>
     * Into the spec rather than straight into Terrain objects, because the lobby broadcasts the
     * spec and the deployment screen has to show a player what they are setting up around — a
     * field that only existed inside a loaded Game would be invisible until the battle began.
     * <p>
     * Ships that already have a starting hex keep it clear: a scenario that places a cruiser at
     * 1216 should not drop an asteroid on top of it.
     */
    public static void expandTerrainPlans(ScenarioSpec spec) {
        if (spec.terrainPlan == null || spec.terrainPlan.isEmpty())
            return;

        List<MapRegion> keepClear = new ArrayList<>();
        if (spec.sides != null)
            for (ScenarioSpec.SideSpec side : spec.sides)
                if (side.ships != null)
                    for (ScenarioSpec.ShipSetup ship : side.ships)
                        if (ship.startHex != null && !ship.startHex.isBlank())
                            keepClear.add(MapRegion.circle(ship.startHex, 0));

        List<ScenarioSpec.TerrainSetup> generated = TerrainGenerator.generate(
                spec.terrainPlan, keepClear, spec.mapCols, spec.mapRows);

        if (spec.terrain == null)
            spec.terrain = new ArrayList<>();
        spec.terrain.addAll(generated);
        // Spent: the hexes are in spec.terrain now, and running again would double them.
        spec.terrainPlan = null;
    }

    public static List<com.sfb.objects.Objective> loadObjectives(ScenarioSpec spec) {
        List<com.sfb.objects.Objective> result = new ArrayList<>();
        if (spec.objectives == null)
            return result;
        for (ScenarioSpec.ObjectiveSetup setup : spec.objectives) {
            Location loc = parseHex(setup.hex);
            com.sfb.objects.Objective obj = new com.sfb.objects.Objective(
                    setup.name != null ? setup.name : "Objective-" + setup.hex, loc.getX(), loc.getY());
            obj.setSurvivesCarrierDestruction(setup.survivesDestruction);
            obj.setSide(setup.side);
            obj.setPoints(setup.points);
            List<String> methods = setup.retrieval != null && !setup.retrieval.isEmpty()
                    ? setup.retrieval
                    : List.of("TRANSPORTER"); // sensible default
            for (String m : methods) {
                try {
                    obj.getAllowedRetrieval().add(
                            com.sfb.properties.RetrievalMethod.valueOf(m.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    System.err.println("ScenarioLoader: unknown retrieval method '" + m
                            + "' on objective " + setup.name + " — skipped");
                }
            }
            result.add(obj);
        }
        return result;
    }

    /**
     * Parse SFB CCRR hex notation to a Location(column, row).
     * "0515" → Location(5, 15).
     */
    /**
     * The face of a planet a ground base is built on: "A"-"F" as the scenario writes headings,
     * or 1-6. Returns 0 when it is neither, which the caller reports rather than guessing at.
     */
    public static int parsePlanetFace(String face) {
        if (face == null || face.isBlank())
            return 0;
        String f = face.trim().toUpperCase();
        if (f.length() == 1 && f.charAt(0) >= 'A' && f.charAt(0) <= 'F')
            return f.charAt(0) - 'A' + 1;
        try {
            int n = Integer.parseInt(f);
            return (n >= 1 && n <= 6) ? n : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static Location parseHex(String hex) {
        int col = Integer.parseInt(hex.substring(0, 2));
        int row = Integer.parseInt(hex.substring(2, 4));
        return new Location(col, row);
    }

    /**
     * Map SFB heading letter to internal 24-step facing.
     * A=1, B=5, C=9, D=13, E=17, F=21.
     */
    static int parseHeading(String heading) {
        char ch = heading.toUpperCase().charAt(0);
        return (ch - 'A') * 4 + 1;
    }

    /**
     * Apply Commander's Option Items to a ship.
     *
     * Call this after buildShip() and before the game starts. Each item is
     * validated (within budget, within limits); violations are logged and
     * that item is skipped rather than throwing.
     *
     * @param ship    the fully constructed ship
     * @param loadout the COI selections for this ship
     * @param spec    the scenario spec (used to read commanderOptions and year)
     */
    public static void applyCoi(Ship ship, CoiLoadout loadout, ScenarioSpec spec) {
        if (loadout == null) return;

        // J4.621: which fighter model a casual carrier's facilities serve. Done FIRST and
        // separately from the budget below, because it costs nothing - the drones and chaff come
        // with the ship ("enough... to re-arm those fighters three times") and only special
        // drones and speed upgrades are bought.
        //
        // A re-fit rather than a first fitting: the facilities were already built with the ship,
        // from the line's standard fighter, because nothing at construction time knows what the
        // player will choose. An unavailable or absent choice falls back to that same standard
        // fighter, which refitFacilities validates.
        if (loadout.fighterFacilityType != null && !loadout.fighterFacilityType.isBlank()) {
            ship.setFighterFacilityType(loadout.fighterFacilityType);
            com.sfb.objects.FighterComplement.refitFacilities(ship, spec.year);
            // The new racks are stocked from the fighter they now serve (J4.8222), and
            // ReadyRack builds that stock at the frame's own baseline speed - so they arrive
            // full of type-I-slows however late the scenario is. applyDroneSpeed has already
            // run and will not run again, which is the whole reason this line is here: without
            // it a casual carrier picks its facilities and the drones in them quietly become
            // obsolete, charged for nothing because there is nothing to charge for. The bill
            // is settled at the end of this method, after the racks hold their final loads.
            refitFighterDrones(ship, spec.year, spec.commanderOptions == null
                    ? null : spec.commanderOptions.maxDroneSpeed);
        }

        int budgetPercent = spec.commanderOptions != null
                ? spec.commanderOptions.budgetPercent
                : CoiBudget.DEFAULT_PERCENT;
        // S3.211's basis, which includes the fighters the hull BPV leaves out. The endpoint now
        // refuses an over-budget loadout outright, so the per-item checks below are the last line
        // of defence for a loadout written into a scenario file, which never passes through it.
        double budget = CoiBudget.allowanceFor(ship, budgetPercent);
        double spent  = 0;

        // --- Extra boarding parties ---
        int extraBPs = Math.min(loadout.extraBoardingParties, CoiLoadout.MAX_EXTRA_BP);
        double bpCost = extraBPs * CoiLoadout.COST_EXTRA_BP;
        if (spent + bpCost <= budget) {
            ship.getCrew().getFriendlyTroops().normal += extraBPs;
            spent += bpCost;
        } else {
            note(ship, "COI: skipping " + extraBPs + " extra BPs — over budget");
        }

        // --- Convert normal BPs to commandos ---
        int conversions = Math.min(loadout.convertBpToCommando, CoiLoadout.MAX_CONVERT_TO_COMMANDO);
        conversions = Math.min(conversions, ship.getCrew().getFriendlyTroops().normal);
        double convCost = conversions * CoiLoadout.COST_CONVERT_TO_COMMANDO;
        if (spent + convCost <= budget) {
            ship.getCrew().getFriendlyTroops().normal    -= conversions;
            ship.getCrew().getFriendlyTroops().commandos += conversions;
            spent += convCost;
        } else {
            note(ship, "COI: skipping " + conversions + " BP→commando conversions — over budget");
        }

        // --- What each fighter's rails carry (FD10.646, J4.23) ---
        // Before readiness, because arming a fighter takes the drones out of its ready rack
        // (J4.8224): choose first, then arm, or the chosen fighter is armed from the rack it
        // had before the player changed it.
        {
            Integer figSpeedCap = spec.commanderOptions != null
                    ? spec.commanderOptions.maxDroneSpeed : null;
            spent += applyFighterLoadouts(ship, loadout.fighterLoadouts, spec.year,
                    figSpeedCap, budget - spent);
        }

        // --- Which fighters are ready (S4.10-S4.12) ---
        // Before the crews are hired, because it costs nothing and cannot fail: it only
        // re-arranges what the weapon status already granted.
        applyFighterReadiness(ship, loadout.armedFighters);

        // --- Extra deck crews (S3.2/J4.816) ---
        // Only a fully capable carrier may hire them. A casual carrier, or a ship with no
        // fighters at all, has nowhere to put them — so the request is refused rather than
        // charged for, and the player is told while there is still time to spend it elsewhere.
        int extraCrews = Math.min(loadout.extraDeckCrews, CoiLoadout.MAX_EXTRA_DECK_CREWS);
        if (extraCrews > 0 && !ship.getCarrierClass().isCarrier()) {
            note(ship, "COI: only a fully capable carrier may hire deck crews (J4.816)");
        } else if (extraCrews > 0) {
            double crewCost = extraCrews * CoiLoadout.COST_EXTRA_DECK_CREW;
            if (spent + crewCost <= budget) {
                ship.getCrew().addDeckCrews(extraCrews);
                spent += crewCost;
            } else {
                note(ship, "COI: skipping " + extraCrews + " extra deck crews — over budget");
            }
        }

        // --- Extra commando squads ---
        int extraCommandos = Math.min(loadout.extraCommandoSquads, CoiLoadout.MAX_EXTRA_COMMANDOS);
        double cmdCost = extraCommandos * CoiLoadout.COST_EXTRA_COMMANDO;
        if (spent + cmdCost <= budget) {
            ship.getCrew().getFriendlyTroops().commandos += extraCommandos;
            spent += cmdCost;
        } else {
            note(ship, "COI: skipping " + extraCommandos + " extra commando squads — over budget");
        }

        // --- T-bombs (4 BPV each; each purchased T-bomb includes 1 free dummy) ---
        boolean allowTBombs = spec.commanderOptions == null || spec.commanderOptions.allowTBombs;
        if (allowTBombs && loadout.extraTBombs > 0) {
            int maxTBombs = com.sfb.constants.Constants.MAX_TBOMBS[ship.getSizeClass()];
            int requested = Math.min(loadout.extraTBombs, maxTBombs);
            if (requested < loadout.extraTBombs) {
                note(ship, "COI: capping T-bombs at " + maxTBombs
                        + " for size class " + ship.getSizeClass());
            }
            double tbCost = requested * CoiLoadout.COST_TBOMB;
            if (spent + tbCost <= budget) {
                ship.setTBombs(ship.getTBombs() + requested);
                ship.setDummyTBombs(ship.getDummyTBombs() + requested); // 1 free dummy per purchased
                spent += tbCost;
            } else {
                note(ship, "COI: skipping " + requested + " T-bombs — over budget");
            }
        }

        // --- Drone rack loadouts (FD2.22's surcharge; year/speed limits enforced) ---
        if (!loadout.droneRackLoadouts.isEmpty() || !loadout.antiDroneLoadouts.isEmpty()) {
            Integer maxSpeed = spec.commanderOptions != null ? spec.commanderOptions.maxDroneSpeed : null;
            int year = spec.year;

            List<DroneRack> racks = new ArrayList<>();
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (w instanceof DroneRack) racks.add((DroneRack) w);
            }

            // Every rack either side of the two maps mentions, so a rack given only
            // anti-drones is still visited.
            java.util.Set<Integer> rackIndexes = new java.util.LinkedHashSet<>();
            rackIndexes.addAll(loadout.droneRackLoadouts.keySet());
            rackIndexes.addAll(loadout.antiDroneLoadouts.keySet());

            for (Integer rackIndexBoxed : rackIndexes) {
                int rackIndex = rackIndexBoxed;
                if (rackIndex < 0 || rackIndex >= racks.size()) {
                    note(ship, "COI: drone rack index " + rackIndex + " out of range — skipped");
                    continue;
                }
                DroneRack rack = racks.get(rackIndex);
                List<com.sfb.objects.DroneLoad> requestedTypes =
                        loadout.droneRackLoadouts.getOrDefault(rackIndex, java.util.List.of());
                int antiDrones = loadout.antiDroneLoadouts.getOrDefault(rackIndex, 0);

                if (antiDrones > 0 && !rack.acceptsAntiDrones()) {
                    note(ship, "COI: rack " + rackIndex + " is a " + rack.getRackType()
                            + " and carries no anti-drones (FD3.70) — anti-drones dropped");
                    antiDrones = 0;
                }
                if (antiDrones > 0 && year > 0 && year < DroneRack.ANTI_DRONE_FIRST_YEAR) {
                    note(ship, "COI: anti-drones are not available before Y"
                            + DroneRack.ANTI_DRONE_FIRST_YEAR + " (FD3.72) — rack "
                            + rackIndex + " anti-drones dropped");
                    antiDrones = 0;
                }

                // Validate each type against year, speed cap, and rack capability
                List<Drone> drones = new ArrayList<>();
                double totalRackSize = 0;
                boolean valid = true;
                for (com.sfb.objects.DroneLoad dt : requestedTypes) {
                    if (!dt.availableIn(year)) {
                        note(ship, "COI: " + dt + " not available in year " + year
                                + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    // The drone's SPEED, not the engine bought for it — which are the same
                    // number on every catalogue drone and part company once FD12.132's
                    // external armor is bolted on. The scenario's cap is not a rule of the
                    // game but a restriction on what flies, so the armored figure is the one
                    // it should be reading.
                    if (maxSpeed != null && dt.speed() > maxSpeed) {
                        note(ship, "COI: " + dt + " speed " + dt.speed()
                                + " exceeds cap " + maxSpeed + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    if (!rack.accepts(dt.basis())) {
                        note(ship, "COI: " + dt + " cannot be loaded in rack type "
                                + rack.getRackType() + " — rack " + rackIndex + " skipped");
                        valid = false; break;
                    }
                    totalRackSize += dt.rackSpaces();
                }
                if (!valid) continue;

                // Every rack reached here was named in one map or the other, which means
                // the player built a loadout for it — so what they built is what it
                // carries, drones and anti-drones alike. A rack they never touched is not
                // in either map and keeps the ammunition it arrived with.
                //
                // FD3.70: the two share one magazine, so they are budgeted together rather
                // than each against the whole rack.
                double totalWithAntiDrones =
                        totalRackSize + antiDrones * DroneRack.ANTI_DRONE_SPACE;
                if (totalWithAntiDrones > rack.getSpaces()) {
                    note(ship, "COI: loadout for rack " + rackIndex + " exceeds rack size ("
                            + totalWithAntiDrones + " > " + rack.getSpaces() + ") — skipped");
                    continue;
                }
                // FD2.22's surcharge over the type-I-slows the hull already paid for, billed
                // here rather than given away. Two things read it, and the second is the one
                // that was wrong: the gate below is the last line of defence for a loadout
                // written into a scenario file, which never passes the endpoint's refusal
                // (CoiBudget.refusalFor already counts this) — but `spent` also becomes
                // ship.setCoiSpend at the end of this method, and S2.20 step B hands that to
                // the ENEMY as victory points. Uncharged, a player could arm every rack with
                // type-IVs and be scored as though the ship had sailed with type-I-slows.
                //
                // Per rack, because this loop skips racks: see CoiLoadout.rackUpgradeCost.
                // The rack itself goes in because FD3.44 charges a magazine launcher for one
                // magazine of its three, and only the rack knows it is one.
                double rackCost = CoiLoadout.rackUpgradeCost(rack, requestedTypes, year);
                if (spent + rackCost > budget) {
                    note(ship, "COI: skipping the loadout for rack " + rackIndex
                            + " — it costs " + rackCost + " and only " + (budget - spent)
                            + " of the budget is left");
                    continue;
                }
                spent += rackCost;

                for (com.sfb.objects.DroneLoad dt : requestedTypes) drones.add(dt.build());
                rack.setAmmo(drones);
                rack.setAddAmmo(0);
                if (antiDrones > 0)
                    rack.loadAntiDrones(antiDrones, year);
            }
        }

        // --- The hold follows the loading (FD2.45) ---
        // Stocked once already, during outfitForYear, from the drones the hull was ISSUED --
        // which is before a player has bought anything. FD2.45 makes the stores "proportional
        // to those on the racks/fighters", so a squadron that now flies ECM drones is owed ECM
        // drones in its hold, free; without this the purchase reached the rails and the first
        // reload put a plain drone back in its place.
        //
        // After readiness rather than before: arming takes drones OUT of the rack (J4.8224),
        // and ReadyRack.loading reads the rail as readily as the rack, so the pattern is the
        // same either way -- but the spaces already committed forward are only right once the
        // fighters have stopped moving.
        if (ship.getShuttles() != null)
            ship.getShuttles().restockDroneStore();

        // --- Special shuttle conversions (WS-2: max 1, WS-3: max 2) ---
        // Admin shuttles are converted in-bay to the requested special type.
        if (!loadout.specialShuttlePrep.isEmpty()) {
            int ws = spec.sides.stream()
                    .flatMap(side -> side.ships.stream())
                    .filter(ss -> ship.getName().equals(ss.shipName))
                    .mapToInt(ss -> ss.weaponStatus)
                    .findFirst().orElse(0);
            // S4.12 (Weapon Status II): one shuttle may be prepared for a special role
            // (scatter pack, suicide shuttle, wild weasel). S4.13 (Weapon Status III): two.
            // Below WS-II, none — there has been no time to prepare anything.
            int maxPrep = ws >= 3 ? 2 : ws == 2 ? 1 : 0;

            int applied = 0;
            for (CoiLoadout.SpecialShuttlePrep prep : loadout.specialShuttlePrep) {
                if (applied >= maxPrep) {
                    note(ship, "COI: special shuttle limit (" + maxPrep + ") reached — skipping "
                            + prep.shuttleName);
                    continue;
                }
                // Find the admin shuttle in the ship's bays
                ShuttleBay foundBay = null;
                Shuttle foundShuttle = null;
                for (ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (Shuttle s : bay.getInventory()) {
                        if (s.getName().equalsIgnoreCase(prep.shuttleName)) {
                            foundBay    = bay;
                            foundShuttle = s;
                            break;
                        }
                    }
                    if (foundBay != null) break;
                }
                if (foundBay == null || foundShuttle == null) {
                    note(ship, "COI: shuttle not found: " + prep.shuttleName + " — skipped");
                    continue;
                }
                if ("suicide".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeSuicide()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a suicide shuttle — skipped");
                        continue;
                    }
                    SuicideShuttle ss = new SuicideShuttle(foundShuttle);
                    int energy = Math.max(1, Math.min(3, prep.energyPerTurn));
                    for (int t = 0; t < 3; t++) ss.arm(energy);
                    foundBay.replaceShuttle(foundShuttle, ss);
                    applied++;

                } else if ("scatterpack".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeScatterPack()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a scatterpack — skipped");
                        continue;
                    }
                    ScatterPack sp = new ScatterPack(foundShuttle);
                    for (DroneType dt : prep.drones) {
                        // Pull one drone of this type from any rack's ammo, then reloads
                        if (!pullDroneFromRacks(ship, dt)) {
                            note(ship, "COI: no " + dt + " available in racks for scatterpack "
                                    + prep.shuttleName + " — drone skipped");
                            continue;
                        }
                        if (!sp.addDrone(new Drone(dt))) {
                            note(ship, "COI: scatterpack " + prep.shuttleName
                                    + " payload full — remaining drones skipped");
                            break;
                        }
                    }
                    if (sp.getPayload().isEmpty()) {
                        // Leave it a plain shuttle. An empty pack can never launch (the
                        // launch action wants a payload) and, being prepared, can no longer
                        // launch as an ordinary shuttle either — dead weight all battle.
                        note(ship, "COI: scatterpack " + prep.shuttleName
                                + " got no drones from the racks — left as a plain shuttle");
                        continue;
                    }
                    foundBay.replaceShuttle(foundShuttle, sp);
                    applied++;

                } else if ("wildweasel".equalsIgnoreCase(prep.type)) {
                    if (!foundShuttle.canBecomeWildWeasel()) {
                        note(ship, "COI: " + prep.shuttleName + " cannot become a Wild Weasel — skipped");
                        continue;
                    }
                    // No AdminShuttle check: the canBecomeWildWeasel() test just above is
                    // the rule (J3.18), and this used to refuse a GAS or HTS that had
                    // already passed it.
                    // Charge to full (2 turns) so it's ready to launch on turn 1
                    foundShuttle.incrementWwCharge();
                    foundShuttle.incrementWwCharge();
                    applied++;

                } else {
                    note(ship, "COI: unknown conversion type '" + prep.type
                            + "' for shuttle " + prep.shuttleName + " — skipped");
                }
            }
        }

        // --- Heavy weapon arming mode overrides (WS-3 only; weapon must already be armed) ---
        if (!loadout.weaponArmingModes.isEmpty()) {
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof HeavyWeapon)) continue;
                if (w instanceof com.sfb.weapons.Fusion) continue;
                if (w instanceof com.sfb.weapons.PlasmaLauncher
                        && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;

                com.sfb.properties.WeaponArmingType mode =
                        loadout.weaponArmingModes.get(w.getDesignator());
                if (mode == null || mode == com.sfb.properties.WeaponArmingType.STANDARD) continue;

                HeavyWeapon hw = (HeavyWeapon) w;
                if (!hw.isArmed()) {
                    // S4.32: the arming turns a ship gets for free before the scenario cannot
                    // include overload energy. A partly armed photon may still be fused for
                    // proximity, which costs nothing (E4.31).
                    if (w instanceof com.sfb.weapons.Photon && hw.getArmingTurn() > 0
                            && mode == com.sfb.properties.WeaponArmingType.SPECIAL) {
                        ((com.sfb.weapons.Photon) w).setSpecial();
                    } else if (mode == com.sfb.properties.WeaponArmingType.OVERLOAD) {
                        note(ship, "COI: weapon " + w.getLabel()
                                + " cannot start overloaded — prior-turn arming carries no"
                                + " overload energy (S4.32)");
                    } else {
                        note(ship, "COI: weapon " + w.getLabel()
                                + " is not armed — arming mode override skipped");
                    }
                    continue;
                }
                // Photons take their overload from the S4.32 pool below, not from a mode flag.
                if (w instanceof com.sfb.weapons.Photon
                        && mode == com.sfb.properties.WeaponArmingType.OVERLOAD) {
                    note(ship, "COI: photon " + w.getLabel()
                            + " — set its free overload energy in photonOverload (S4.32)");
                    continue;
                }

                // Reset and re-arm in the requested mode
                hw.reset();
                switch (mode) {
                    case OVERLOAD: hw.setOverload(); break;
                    case SPECIAL:  hw.setSpecial();  break;
                    case ROLLING:
                        if (hw instanceof com.sfb.weapons.PlasmaLauncher)
                            ((com.sfb.weapons.PlasmaLauncher) hw).setRollingMode();
                        break;
                    default: break;
                }
                hw.setArmed(true);
                hw.setArmingTurn(hw.totalArmingTurns());
                if (w instanceof com.sfb.weapons.Photon) {
                    ((com.sfb.weapons.Photon) w).setArmingEnergy(
                        (double) hw.energyToArm() * hw.totalArmingTurns());
                }
            }
        }

        // --- Free photon overload energy at WS-III (S4.32) ---
        // Two points per tube, poolable across the ship's tubes: a Federation CA with four
        // photons has eight points, enough to take two tubes to a full 100% overload and
        // leave two standard, or to give every tube half an overload. The energy can only
        // overload, never arm, and taking any commits that tube (E4.414) — which caps it at
        // range 8 and doubles its holding cost (E4.413).
        if (!loadout.photonOverload.isEmpty()) {
            int ws = spec.sides.stream()
                    .flatMap(side -> side.ships.stream())
                    .filter(ss -> ship.getName().equals(ss.shipName))
                    .mapToInt(ss -> ss.weaponStatus)
                    .findFirst().orElse(0);
            List<com.sfb.weapons.Photon> tubes = new ArrayList<>();
            for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
                if (w instanceof com.sfb.weapons.Photon)
                    tubes.add((com.sfb.weapons.Photon) w);

            if (ws < 3) {
                note(ship, "COI: free photon overload energy is a WS-3 allowance only"
                        + " — ignored for " + ship.getName() + " at WS-" + ws + " (S4.32)");
            } else if (tubes.isEmpty()) {
                note(ship, "COI: " + ship.getName() + " has no photon tubes — free"
                        + " overload energy ignored (S4.32)");
            } else {
                double pool = CoiLoadout.FREE_OVERLOAD_PER_TUBE * tubes.size();
                double spentPool = 0;
                for (com.sfb.weapons.Photon p : tubes) {
                    Double want = loadout.photonOverload.get(p.getDesignator());
                    if (want == null || want <= 0)
                        continue;
                    double amount = Math.floor(want * 2) / 2.0;          // half points (E4.414)
                    if (amount > com.sfb.weapons.Photon.MAX_OVERLOAD) {  // 100% and no more (E4.41)
                        note(ship, "COI: photon " + p.getDesignator() + " capped at "
                                + com.sfb.weapons.Photon.MAX_OVERLOAD + " overload points (E4.41)");
                        amount = com.sfb.weapons.Photon.MAX_OVERLOAD;
                    }
                    if (spentPool + amount > pool) {
                        amount = pool - spentPool;
                        note(ship, "COI: " + ship.getName() + " has only " + pool
                                + " free overload points — photon " + p.getDesignator()
                                + " reduced to " + amount + " (S4.32)");
                    }
                    if (amount <= 0)
                        continue;
                    p.setOverload();
                    p.setArmingEnergy(p.getArmingEnergy() + amount);
                    spentPool += amount;
                }
            }
        }

        // --- Orion option-mount weapons (G15.4) ---
        // The ship's inherent loadout, not a commander's-option budget item: the
        // BPV delta flows into effective BPV, not the COI budget. Illegal picks
        // (wrong position/size/year, unavailable) are skipped with a reason.
        if (!loadout.optionMounts.isEmpty()) {
            com.sfb.objects.OptionMountCatalog catalog = com.sfb.objects.OptionMountCatalog.loadDefault();
            for (Map.Entry<String, String> entry : loadout.optionMounts.entrySet()) {
                try {
                    com.sfb.objects.OptionMountLoadout.equip(ship, catalog, entry.getKey(),
                            entry.getValue(), spec.year,
                            com.sfb.objects.OptionMountArcs.parse(
                                    loadout.optionMountArcs.get(entry.getKey())));
                } catch (IllegalArgumentException e) {
                    note(ship, "COI: option mount " + entry.getKey() + " ("
                            + entry.getValue() + ") — " + e.getMessage());
                }
            }
        }

        // FD10.622/FD10.632 LAST, over the finished ship.
        //
        // The caps are a proportion of everything aboard, so they cannot be judged rack by rack
        // the way the year, speed and compatibility checks above are — a loadout that is legal
        // in each rack can still put a quarter of the ship's drones over the line. Hence a pass
        // at the end, once every rack holds what it is going to hold.
        //
        // NOTED rather than refused, like every other illegality in this method: a scenario or
        // fleet written under one year's rules may be played under another, and losing the
        // battle to one over-specialised drone load helps nobody. See DroneAvailabilityLimit
        // for what is counted and the two things that are not.
        for (String violation : DroneAvailabilityLimit.violations(ship, spec.year))
            note(ship, "COI: " + violation);

        // S2.11's charge follows what the ship is ACTUALLY carrying, so re-derive it now that
        // the racks hold their final loads. A player who swapped the year's fast drones for
        // something slower -- which FD2.454 lets them do, "might for tactical reasons have some
        // slower drones on board" -- stops paying for speed they no longer have. Idempotent by
        // construction: it sets the figure rather than adding to it, so running after
        // applyDroneSpeed already ran cannot double-charge.
        chargeDroneSpeed(ship, spec.year, spec.commanderOptions == null
                ? null : spec.commanderOptions.maxDroneSpeed);

        // Record the COI spend so the enemy is awarded these points at scenario end
        // (S2.20 step B). Option-mount weapon costs are excluded — they ride the ship's
        // GABPV and are already scored under step C.
        ship.setCoiSpend(spent);
    }

    /**
     * Remove one drone of the given type from the ship's rack ammo or reloads.
     * Searches ammo lists first, then reload sets across all racks.
     * Returns true if a drone was found and removed, false if none available.
     */
    /**
     * Tell the player, not just the console. A COI selection that cannot be applied used to
     * print to System.err and stop there, so a setup silently came out different from what
     * was chosen and the first sign of it was a missing option mid-battle.
     */
    private static void note(Ship ship, String message) {
        System.err.println(message);
        ship.addSetupNote(message);
    }

    private static boolean pullDroneFromRacks(Ship ship, DroneType type) {
        List<DroneRack> racks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack) racks.add((DroneRack) w);
        }
        // Check the ship's reload stockpile first — preserve ammo so the racks are ready to fire on
        // Turn 1. One pile for the ship, per FD2.422, so this no longer has to walk the racks: it
        // used to, and a drone sitting in rack 2's set was only findable after rack 1's was empty,
        // which is the same confusion the stockpile exists to end.
        if (!ship.reloadStockpile().take(type, 1).isEmpty())
            return true;
        // Fall back to ammo only if no matching drone exists in the stockpile
        for (DroneRack rack : racks) {
            Iterator<Drone> it = rack.getAmmo().iterator();
            while (it.hasNext()) {
                if (it.next().getDroneType() == type) { it.remove(); return true; }
            }
        }
        return false;
    }

    /**
     * Apply Y175 universal and faction-specific ship upgrades.
     * Universal: all ADD_6 → ADD_12.
     * Faction defaults apply unless shipSpec.y175Upgrades is non-null (explicit override).
     */
    static void applyYearUpgrades(Ship ship, String faction, int year, ShipSpec shipSpec) {
        if (year < 175) return;

        // Universal: ADD_6 → ADD_12
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof ADD) {
                ADD add = (ADD) w;
                if (add.getAddType() == ADD.AddType.ADD_6) {
                    add.upgradeTo(ADD.AddType.ADD_12);
                }
            }
        }

        // FP10.312: "Along with the Y175 drone rack refits, each plasma rack has two sets of
        // reloads; there is no extra cost for this." Universal like the ADD upgrade above and
        // for the same reason - the rule is about the weapon, not the navy that mounts it - so
        // it sits before the per-ship override and the faction defaults rather than inside
        // them. A Gorn, ISC, Romulan or Orion rack (FP10.15) all get it.
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof com.sfb.weapons.PlasmaRack pr)
                pr.applyY175Refit();
        }

        // Explicit per-ship override list takes precedence over faction defaults
        // The per-ship block NARROWS the faction default; it does not replace it.
        //
        // It used to replace it: the block was applied and the method RETURNED, so the defaults
        // below never ran. That cost three hulls. The Federation NCD and NCD+ named their four
        // type-A racks and not their two type-G ones, so the Federation default that gives a type-G
        // its third reload set (FD3.72) never happened and both fought every Y175 battle two reload
        // sets short. The Klingon G2 declared a refit COST and nothing else, so it paid for an
        // upgrade it never received. Both shapes look obviously fine in the file, which is what made
        // them expensive.
        //
        // So a block now says what is DIFFERENT about this ship, and the default fills in the rest.
        // The named racks are done first and then excluded from the default, which is what makes the
        // override an override — otherwise a Kzinti hull naming a rack TYPE_B would have the default
        // turn it into a TYPE_C afterwards.
        //
        // Every block that existed before this change restated what the default would have done, so
        // none of them behaves differently: a restated rack is named, and a named rack is skipped
        // below. What changes is that the restatement is no longer REQUIRED.
        Set<String> handledRacks = new java.util.HashSet<>();
        if (shipSpec.y175Upgrades != null) {
            if (shipSpec.y175Upgrades.refitCost != 0) {
                // Both values: a refit's cost is added to the economic rating as well as the
                // combat one. See RefitResolver, which does the same for a named refit.
                ship.setBattlePointValue(ship.getBattlePointValue() + shipSpec.y175Upgrades.refitCost);
                ship.setEconomicBpv(ship.getEconomicBpv() + shipSpec.y175Upgrades.refitCost);
            }
            for (ShipSpec.Y175RackUpgrade ru : shipSpec.y175Upgrades.racks) {
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof DroneRack)) continue;
                    if (!ru.designator.equals(w.getDesignator())) continue;
                    DroneRack rack = (DroneRack) w;
                    rack.upgradeRackType(DroneRack.DroneRackType.valueOf(ru.upgradeTo));
                    if (ru.extraReloads > 0) rack.addReloadSets(ru.extraReloads);
                    handledRacks.add(ru.designator);
                }
            }
            for (ShipSpec.Y175AddUpgrade au : shipSpec.y175Upgrades.adds) {
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (!(w instanceof ADD)) continue;
                    if (!au.designator.equals(w.getDesignator())) continue;
                    ((ADD) w).upgradeTo(ADD.AddType.valueOf(au.upgradeTo));
                }
            }
        }

        // Faction defaults, over every rack the block did not speak for.
        List<DroneRack> typeARacks = new ArrayList<>();
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack) {
                DroneRack rack = (DroneRack) w;
                if (handledRacks.contains(rack.getDesignator())) continue;
                if (rack.getRackType() == DroneRack.DroneRackType.TYPE_A) {
                    typeARacks.add(rack);
                }
            }
        }

        switch (faction.toUpperCase()) {
            case "FEDERATION":
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (w instanceof DroneRack) {
                        DroneRack rack = (DroneRack) w;
                        if (handledRacks.contains(rack.getDesignator())) continue;
                        if (rack.getRackType() == DroneRack.DroneRackType.TYPE_G) {
                            rack.addReloadSets(1);
                        }
                    }
                }
                break;

            case "KLINGON":
                for (DroneRack rack : typeARacks) {
                    rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                }
                break;

            case "KZINTI":
                if (typeARacks.size() >= 4) {
                    // First two → TYPE_C, remainder → TYPE_B
                    for (int i = 0; i < typeARacks.size(); i++) {
                        if (i < 2) typeARacks.get(i).upgradeRackType(DroneRack.DroneRackType.TYPE_C);
                        else       typeARacks.get(i).upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                    }
                } else {
                    for (DroneRack rack : typeARacks) {
                        rack.upgradeRackType(DroneRack.DroneRackType.TYPE_B);
                    }
                }
                break;

            case "ORION":
                // Every Orion drone rack gains a reload set in Y175. Both the BR's and the CR's
                // sheets print it, in different decades' words: the BR "GAIN ONE ADDITIONAL
                // RELOAD", the CR "ARE DOUBLED". Those are the same rule — when the CR's sheet
                // was written the Basic Set had only type-A racks, which carry a single set, so
                // +1 WAS doubling (owner, 2026-10-20). The operation is +1, and reading the CR
                // literally would have given a type-C rack twice what it is owed.
                //
                // Unlike every branch above, this one deliberately does NOT consult
                // handledRacks. That filter exists so a hull's declared rack TYPE is not
                // overwritten by a faction default; a reload count is a different axis. The
                // Orion CA is the proof: it is the one Orion hull with a y175Upgrades block, it
                // names both racks for a TYPE_C upgrade, and filtering here would silently cost
                // it the reload set this rule gives it — the same shape of bug the comment
                // above records costing the Federation NCD two sets.
                //
                // Order matters and is already right: the per-ship block ran above, and
                // upgradeRackType resets the count to the new type's base, so a +1 applied here
                // lands on top of the upgraded type rather than on a count about to be reset.
                //
                // Plasma racks are NOT touched here. They get the same +1 from the universal
                // FP10.312 rule at the top of this method, one set becoming two; adding Orion's
                // on top would make three, which is more than either sheet grants.
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
                    if (w instanceof DroneRack rack)
                        rack.addReloadSets(1);
                break;

            default:
                break;
        }
    }

    /**
     * Apply weapon status initial conditions to a ship (S4.10–S4.13).
     */
    /**
     * FP10.25: how many of each plasma rack's torpedoes are active at this weapon status.
     * <p>
     * "Status 0: torpedoes inactive. Status 1: torpedoes inactive. Status II: one torpedo per
     * rack is active. Status III: all torpedoes on racks are active."
     * <p>
     * Called on every branch including 0 and 1, where it happens to set nothing: a rack arrives
     * full of torpedoes (J4.886) but with none ACTIVATED, so the constructor already agrees with
     * the rule at those statuses. The call is there so the ladder is stated in one place and is
     * total, rather than relying on a default matching a rule it does not cite - but it is not
     * load-bearing today, and a mutation test removing it correctly fails nothing.
     */
    private static void applyPlasmaRackStatus(Ship ship, int weaponStatus) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons())
            if (w instanceof com.sfb.weapons.PlasmaRack rack)
                rack.applyWeaponStatus(weaponStatus);
    }

    static void applyWeaponStatus(Ship ship, int weaponStatus) {
        ship.setWeaponStatus(weaponStatus);
        switch (weaponStatus) {
            case 0:
                // WS-0: phasers not energized — caps cannot hold energy yet
                ship.setCapacitorsCharged(false);
                applyPlasmaRackStatus(ship, weaponStatus);
                break;
            case 1:
                // WS-1: phasers energized, caps empty, fire control active
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
                applyPlasmaRackStatus(ship, weaponStatus);
                break;
            case 2:
            case 3:
                // WS-2/3: caps fully charged, fire control active
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
                double capSize = ship.getWeapons().getAvailablePhaserCapacitor();
                if (capSize > 0) {
                    try {
                        ship.chargeCapacitor(capSize);
                    } catch (CapacitorException e) {
                        // Already full — safe to ignore
                    }
                }
                // ESG generators start charged by weapon status (G23.23): WS-2 = 2, WS-3 = 5.
                int esgInitial = weaponStatus == 3 ? 5 : 2;
                for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                    if (w instanceof com.sfb.weapons.ESG) {
                        ((com.sfb.weapons.ESG) w).setStoredEnergy(esgInitial);
                    }
                }
                // FP10.25: a plasma rack's torpedoes are ACTIVATED by weapon status, not armed -
                // "Status II: one torpedo per rack is active. Status III: all torpedoes on racks
                // are active." So this is not the all-but-final-arming-turn treatment the heavy
                // weapons get below; the rack has no arming schedule to be partway through.
                applyPlasmaRackStatus(ship, weaponStatus);
                // WS-2: all-but-final arming turn completed (S4.12).
                // armingTurn = totalArmingTurns - 1 for all eligible heavy weapons.
                // Excludes Disruptors (always ready) and Fusion beams (not multi-turn).
                if (weaponStatus == 2) {
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (!(w instanceof HeavyWeapon)) continue;
                        if (w instanceof com.sfb.weapons.Fusion) continue;
                        if (w instanceof com.sfb.weapons.Disruptor) continue;
                        if (w instanceof com.sfb.weapons.PlasmaLauncher
                                && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;
                        HeavyWeapon hw = (HeavyWeapon) w;
                        hw.setArmingTurn(hw.totalArmingTurns() - 1);
                        // A completed arming turn means its energy is in the tube (E4.21), and
                        // for photons that stored energy is what the warhead is made of. The
                        // pre-game turns are plain standard charges: nothing in S4.12 lets a
                        // ship start with overload energy already committed (E4.411).
                        if (w instanceof com.sfb.weapons.Photon) {
                            ((com.sfb.weapons.Photon) w).setArmingEnergy(
                                com.sfb.weapons.Photon.STANDARD_PER_TURN * (hw.totalArmingTurns() - 1));
                        }
                    }
                }
                // WS-3: all heavy weapons start fully armed (S4.13).
                // Excludes Plasma-R (cannot hold/launch as seeker from armed state).
                // Excludes Disruptors: single-turn arming, reset every turn — pre-arming is
                // meaningless since they re-arm in turn 1 EA anyway.
                // Fusion beams are single-turn arming but still start armed at WS-3.
                // Hellbores start armed but cannot hold — rolling delay applies in turn 1 EA.
                if (weaponStatus == 3) {
                    for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
                        if (!(w instanceof HeavyWeapon)) continue;
                        if (w instanceof com.sfb.weapons.Disruptor) continue;
                        if (w instanceof com.sfb.weapons.PlasmaLauncher
                                && !((com.sfb.weapons.PlasmaLauncher) w).canHold()) continue;
                        HeavyWeapon hw = (HeavyWeapon) w;
                        hw.setArmed(true);
                        hw.setArmingTurn(hw.totalArmingTurns());
                        if (w instanceof com.sfb.weapons.Photon) {
                            ((com.sfb.weapons.Photon) w).setArmingEnergy(
                                (double) hw.energyToArm() * hw.totalArmingTurns());
                        }
                        if (hw instanceof com.sfb.weapons.PlasmaLauncher)
                            ((com.sfb.weapons.PlasmaLauncher) hw).setArmedState();
                    }
                }
                break;
            default:
                System.err.println("ScenarioLoader: unknown weaponStatus " + weaponStatus + " — treating as WS-2");
                ship.setCapacitorsCharged(true);
                ship.setActiveFireControl(true);
        }

        // FP1.23 LAST, so it overrides the ladder above rather than being overridden by it.
        // WS-2 sets every holdable heavy weapon to its all-but-final arming turn, which would
        // leave a plasma-F two-thirds armed; this puts it back to ready.
        armPlasmaFTorpedoes(ship);

        applyFighterWeaponStatus(ship, weaponStatus);
    }

    /**
     * FP1.23: a plasma-F is ready at the start of EVERY weapon status, WS-0 included.
     *
     * <p>The rule is an exception to the whole of (S4.0). Plasma torpedoes are expensive to arm
     * and hold, so a ship "would not normally travel for months with loaded torpedoes" — but:
     *
     * <blockquote>EXCEPTION: Plasma-F torpedoes, held in stasis boxes, can be held almost
     * indefinitely and are <b>always ready at the start of a scenario</b>, except when the ship
     * is "surprised" (D18.12).</blockquote>
     *
     * <p>FP1.132 states the WS-0 half outright, and draws the line this method is keyed on:
     * "Launchers larger than type-F can have a type-F downloaded at the start of the scenario if
     * the weapon status allows the launcher to have a standard torpedo loaded, but cannot have a
     * downloaded type-F at WS-0 (whereas <b>an actual type-F launcher could be holding a torpedo
     * at that status</b>)." So this is keyed on the LAUNCHER's own type, never on what it happens
     * to be loading — a plasma-S tube downloading an F has no stasis box and gets nothing here.
     *
     * <p>The stasis box is the mechanism throughout, and it is also why a plasma-F holds for
     * nothing: FP2.51 prints its hold cost as 0 where a G costs 1 and an S costs 2, and XG17.522
     * confirms the link from the other side — a type-M launcher hastily repaired as a type-F
     * "would not have the stasis box and would have to pay holding energy". We already charge 0
     * in {@code PlasmaLauncher.holdEnergyCost}; this is the other half of the same box.
     *
     * <p><b>The D18.12 exception is not implemented, because nothing can be surprised yet.</b>
     * D18.0 inactive ships are unmodelled — there is no flag to read — and when they arrive,
     * D18.12 is explicit that "their plasma-F launchers, ADDs, plasma racks, PPTs, and drone
     * racks are not loaded", so this method is one of the places that will have to ask.
     *
     * <p>Fighter-mounted plasma-F is deliberately untouched: this walks the SHIP's own weapons,
     * and a fighter's arming is J4.8's deck-crew budget in {@link #applyFighterWeaponStatus}.
     */
    private static void armPlasmaFTorpedoes(Ship ship) {
        for (com.sfb.weapons.Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof com.sfb.weapons.PlasmaLauncher launcher))
                continue;
            if (launcher.getLauncherType() != com.sfb.properties.PlasmaType.F)
                continue;
            launcher.setArmedState();
        }
    }

    /**
     * Weapon status for the fighters in the bays (S4.10-S4.13, J4.8224).
     *
     * Fighters are built empty and their boxes full, which is J4.8223's resting state for a
     * carrier: racks loaded, fighters not. This decides how much of that has been moved onto
     * the fighters before the scenario starts, and every charge or drone that moves comes out
     * of that fighter's own box — so a carrier's total ammunition is the same at every weapon
     * status, only its readiness differs.
     *
     * <ul>
     *   <li>WS-0 and WS-1: two fighters armed (identical for fighter operations).</li>
     *   <li>WS-2: two turns of work by every deck crew (S4.12), which is 2 actions each, and
     *       J4.8172 caps any one fighter at 2 crews — so 4 actions is the most one can
     *       receive. A Stinger-1 wants 2 and an AAS 2, so today's fighters are never short;
     *       an advanced fighter with a heavier load would be only partly armed, which is the
     *       point of spending a budget rather than setting a flag.</li>
     *   <li>WS-3: everything loaded, racks and capacitors drawn down to pay for it
     *       (J4.8224).</li>
     * </ul>
     *
     * Which fighters get the work is first-come unless the player said otherwise in the
     * Commander's Options — see {@link #applyFighterReadiness}. On a squadron of one type it
     * makes no difference; on a mixed one it is a real decision, and the Hydran Ranger refit
     * carries Stinger-2s and Stinger-Hs in the same bay.
     */
    static void applyFighterWeaponStatus(Ship ship, int weaponStatus) {
        // S4.1 extends these provisions to "fully capable carriers (J4.61) and most Hydran
        // ships (J4.623)" — which is what CAPABLE means — and pointedly not to a casual
        // carrier with a fighter or two aboard (J4.62).
        if (!ship.getCarrierClass().isCarrier())
            return;

        java.util.List<com.sfb.systemgroups.ShuttleSpace> boxes = new java.util.ArrayList<>();
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces())
                if (!box.isDestroyed()
                        && box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter)
                    boxes.add(box);
        if (boxes.isEmpty())
            return;

        if (weaponStatus >= 3) {
            for (com.sfb.systemgroups.ShuttleSpace box : boxes)
                box.armOccupantFully();
            return;
        }

        if (weaponStatus == 2) {
            // S4.12: two turns of deck crew work before the scenario opens.
            int halfActions = ship.getCrew().getDeckCrews() * WS2_TURNS
                    * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;
            int perFighterCap = WS2_TURNS * MAX_CREWS_PER_FIGHTER
                    * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;
            for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
                if (halfActions <= 0)
                    break;
                com.sfb.systemgroups.FighterArming.Load load =
                        com.sfb.systemgroups.FighterArming.load(box, box.getShuttle(),
                                Math.min(halfActions, perFighterCap));
                halfActions -= load.halfActionsUsed();
            }
            return;
        }

        // WS-0 and WS-1: a couple of fighters ready on the deck, the rest cold.
        //
        // A fighter with nothing to arm does not use up one of the two. S4.10 grants two
        // fighters READY, and a Stinger-E with one Ph-G and two permanent EW pods (R1.F7) is
        // already ready — arming it is a no-op. Spending a slot on it left a Hydran Ranger
        // refit with ONE hot fighter instead of two, because the Stinger-E is the first
        // fighter its bay lists.
        int armed = 0;
        for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
            if (armed >= WS01_ARMED_FIGHTERS)
                break;
            if (!com.sfb.systemgroups.FighterArming.needsArming(box.getShuttle()))
                continue;
            box.armOccupantFully();
            armed++;
        }
    }

    /** S4.12: the carrier has had two turns of deck crew activity before the scenario. */
    private static final int WS2_TURNS = 2;

    /** J4.8172: two deck crews on one fighter, and no more. */
    private static final int MAX_CREWS_PER_FIGHTER = 2;

    /** S4.10/S4.11: two fighters may be armed and ready at the lowest weapon statuses. */
    private static final int WS01_ARMED_FIGHTERS = 2;

    /**
     * Put the player's chosen drones on each named fighter's rails (FD10.646, FD2.45).
     *
     * <h2>Why this exists at all</h2>
     * FD10.646 settles what a fighter is ISSUED with — an ordinary drone, even on a special
     * rail, because special rails "do not form an exception allowing additional limited
     * availability drones to be purchased". The type-III, the ECM drone and the MW drone are
     * therefore purchases, and this is where they are bought. For a Klingon carrier it is the
     * only place: the conversion strips every drone rack off the hull, so the squadron is the
     * whole of that ship's drone capacity.
     *
     * <h2>The rails and the rack are one loading, not two</h2>
     * The rails get the DESIGN and the ready rack is rebuilt around it (J4.8222: a rack holds
     * "the same drones that the fighter it is designed to service carries"), after which the
     * rack is stocked with what the player actually asked for rather than the frame's
     * baseline. The rack starts full and the fighter starts empty (J4.8223, J4.886), which is
     * why nothing is loaded onto the rails here: {@code ShuttleSpace.armOccupantFully} arms a
     * fighter by taking drones OUT of its rack (J4.8224), and readiness runs next.
     *
     * <p>Billed once per fighter for the same reason — see
     * {@link CoiLoadout#fighterUpgradeCost}. Charged rather than given away because {@code
     * spent} becomes {@code ship.setCoiSpend}, which S2.20 step B hands to the ENEMY as
     * victory points: a free loadout would score the carrier as though its squadron flew
     * type-I-slows.
     *
     * <h2>All or nothing, per fighter</h2>
     * As the rack block does per rack. A fighter whose list names one drone the rules refuse
     * keeps every rail it had, because a half-applied squadron is the state hardest for a
     * player to see: they would be told the ECM drone was dropped and have no way to know
     * which of their twelve fighters ended up carrying what.
     *
     * <p>Boxed fighters only. A balcony fighter (J1.53) has no box and therefore no ready
     * rack, and a loading with nowhere to come from is not one this step can give it.
     *
     * @param budgetLeft what remains of the Commander's Option allowance
     * @return what this spent of it
     */
    private static double applyFighterLoadouts(Ship ship,
            java.util.Map<String, java.util.List<com.sfb.objects.DroneLoad>> loadouts,
            int year, Integer maxSpeed, double budgetLeft) {
        if (loadouts == null || loadouts.isEmpty() || ship.getShuttles() == null)
            return 0;
        double spent = 0;
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces()) {
                com.sfb.objects.shuttles.Shuttle occupant = box.getShuttle();
                if (occupant == null || box.isDestroyed())
                    continue;
                java.util.List<com.sfb.objects.DroneLoad> wanted =
                        loadouts.get(occupant.getName());
                if (wanted == null)
                    continue;       // a fighter nobody named flies what it was issued

                java.util.List<com.sfb.weapons.DroneRail> rails =
                        com.sfb.systemgroups.ReadyRack.stockedRails(occupant);
                if (wanted.size() > rails.size()) {
                    note(ship, "COI: " + occupant.getName() + " has " + rails.size()
                            + " drone rails and the loadout names " + wanted.size()
                            + " — skipped");
                    continue;
                }

                // Validated before anything is changed, so a refusal leaves the fighter as it
                // was rather than part-way between two loadouts.
                java.util.List<com.sfb.objects.DroneLoad> billed = new ArrayList<>();
                boolean valid = true;
                for (int i = 0; i < wanted.size() && valid; i++) {
                    com.sfb.objects.DroneLoad load = wanted.get(i);
                    if (load == null)
                        continue;   // a rail left as issued, and already paid for (FD2.31)
                    com.sfb.weapons.DroneRail rail = rails.get(i);
                    if (!load.availableIn(year)) {
                        note(ship, "COI: " + load + " is not available in year " + year
                                + " — " + occupant.getName() + " skipped");
                        valid = false;
                    } else if (maxSpeed != null && load.speed() > maxSpeed) {
                        note(ship, "COI: " + load + " speed " + load.speed()
                                + " exceeds cap " + maxSpeed + " — "
                                + occupant.getName() + " skipped");
                        valid = false;
                    } else if (!rail.accepts(load.build())) {
                        // J4.23 and FD9.35, asked of the built drone rather than its basis:
                        // "TypeI+MW_SMALL" is a type-I until you look at what it is carrying.
                        note(ship, "COI: a " + rail.getRailType() + " rail will not carry "
                                + load + " (J4.23) — " + occupant.getName() + " skipped");
                        valid = false;
                    } else {
                        billed.add(load);
                    }
                }
                if (!valid || billed.isEmpty())
                    continue;

                double cost = CoiLoadout.rackUpgradeCost(null, billed, year);
                if (spent + cost > budgetLeft) {
                    note(ship, "COI: skipping the loadout for " + occupant.getName()
                            + " — it costs " + cost + " and only "
                            + (budgetLeft - spent) + " of the budget is left");
                    continue;
                }
                spent += cost;

                for (int i = 0; i < wanted.size(); i++)
                    if (wanted.get(i) != null)
                        rails.get(i).setDesignDrone(wanted.get(i).basis());

                // The rack is rebuilt rather than re-stocked, because a slot's SHAPE can
                // change: a special rail going from a type-I to a type-III changes what that
                // slot is short of when the rack runs dry (ReadyRack.slotsMissing), and
                // design() is final for exactly that reason. reequipFor is the sanctioned
                // setup-time door — the same one FighterComplement.reseat uses.
                box.reequipFor(occupant);
                com.sfb.systemgroups.ReadyRack rack = box.getReadyRack();
                if (rack == null)
                    continue;
                // The rack is fresh and full, so its drones stand in the rails' order one for
                // one; this swaps the frame's baseline for what was actually bought.
                int[] slot = { 0 };
                rack.refit(held -> {
                    int i = slot[0]++;
                    com.sfb.objects.DroneLoad load = i < wanted.size() ? wanted.get(i) : null;
                    return load == null ? held : load.build();
                });
                // And the DESIGN, or the rack holds an ECM drone in a slot that says plain --
                // which is what it asks the hold to replace it with when it runs dry.
                int[] designSlot = { 0 };
                rack.refitDesign(was -> {
                    int i = designSlot[0]++;
                    com.sfb.objects.DroneLoad load = i < wanted.size() ? wanted.get(i) : null;
                    return load == null ? was : load.build();
                });
            }
        return spent;
    }

    /**
     * Re-do the fighter readiness around the player's choice (Commander's Options).
     * <p>
     * Runs after the weapon status has already armed whichever fighters came first, because
     * the options are chosen later — so it strips the squadron back and arms the named ones
     * instead. Stripping is not a loss: J4.886's charges go back into the box they came from,
     * which is the same accounting every other route uses.
     * <p>
     * Named fighters beyond what the status allows are ignored rather than refused; the
     * server holds the list to the allowance before it ever gets here.
     */
    static void applyFighterReadiness(Ship ship, java.util.List<String> armedFighters) {
        if (armedFighters == null || armedFighters.isEmpty())
            return;
        int ws = ship.getWeaponStatus();
        if (ws >= 3)
            return;   // S4.13 arms everything; there is nothing to choose between

        java.util.List<com.sfb.systemgroups.ShuttleSpace> boxes = new java.util.ArrayList<>();
        for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays())
            for (com.sfb.systemgroups.ShuttleSpace box : bay.getSpaces())
                if (!box.isDestroyed()
                        && box.getShuttle() instanceof com.sfb.objects.shuttles.Fighter)
                    boxes.add(box);
        if (boxes.isEmpty())
            return;

        for (com.sfb.systemgroups.ShuttleSpace box : boxes)
            com.sfb.systemgroups.FighterArming.disarm(box, box.getShuttle());

        int allowed = ws == 2
                ? boxes.size()   // WS-2 is bounded by the crews' budget, not by a count
                : WS01_ARMED_FIGHTERS;
        int halfActions = ws == 2
                ? ship.getCrew().getDeckCrews() * WS2_TURNS
                        * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION
                : Integer.MAX_VALUE;
        int perFighterCap = WS2_TURNS * MAX_CREWS_PER_FIGHTER
                * com.sfb.systemgroups.FighterArming.HALF_ACTIONS_PER_ACTION;

        int armed = 0;
        for (String name : armedFighters) {
            if (armed >= allowed)
                break;
            for (com.sfb.systemgroups.ShuttleSpace box : boxes) {
                if (!box.getShuttle().getName().equalsIgnoreCase(name))
                    continue;
                if (ws == 2) {
                    if (halfActions <= 0)
                        break;
                    com.sfb.systemgroups.FighterArming.Load load =
                            com.sfb.systemgroups.FighterArming.load(box, box.getShuttle(),
                                    Math.min(halfActions, perFighterCap));
                    halfActions -= load.halfActionsUsed();
                } else {
                    // Naming a fighter that has nothing to arm spends none of the allowance
                    // (S4.10) — the same reasoning as the automatic pass above.
                    if (!com.sfb.systemgroups.FighterArming.needsArming(box.getShuttle()))
                        break;
                    box.armOccupantFully();
                }
                armed++;
                break;
            }
        }
    }
}
