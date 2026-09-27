package com.sfb.scenario;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game;
import com.sfb.objects.Ship;
import com.sfb.objects.shuttles.Fighter;
import com.sfb.objects.shuttles.Shuttle;
import com.sfb.properties.Location;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.utilities.MapUtils;

/**
 * Combat Space Patrol: fighters already flying when the scenario opens (S4.12, S4.13).
 * <p>
 * A carrier may put some of its fighters up before the first impulse, within two hexes of
 * itself. It is a real trade rather than free readiness — a fighter on the board can be shot
 * at, and posting a patrol pins the carrier's own speed, since S4.12 requires that "the speed
 * of the ship on the 'previous turn' cannot exceed the maximum speed of the fighters".
 * <p>
 * Where the numbers come from:
 * <ul>
 *   <li>WS-0 and WS-1: none. S4.10 lets a carrier have two fighters <em>armed and ready to
 *       launch</em>, which is not the same as flying.</li>
 *   <li>WS-2: two (S4.12).</li>
 *   <li>WS-3: four (S4.13).</li>
 * </ul>
 * S4.1 settles what a patrol may do on turn one: "fighters on patrol are considered to have
 * fulfilled all launch (J1.34) requirements and are under no fire/launch restrictions" — so
 * they arrive as ordinary flying craft, owing nothing to the launch cooldown.
 * <p>
 * Not modelled: S4.13's option to place the launched fighters "on the balcony instead".
 */
public final class FighterPatrol {

    private FighterPatrol() {}

    /** S4.12/S4.13: how many fighters this weapon status lets a carrier put up. */
    public static int maxDeployed(int weaponStatus) {
        if (weaponStatus >= 3) return 4;
        if (weaponStatus == 2) return 2;
        return 0;
    }

    /** J1.34/S4.12: a patrol sits within two hexes of the ship that launched it. */
    public static final int PATROL_RANGE = 2;

    /**
     * One fighter of the patrol, and where it is standing.
     *
     * @param fighterName the fighter, which must be in one of the carrier's bays
     * @param hex         where it starts; within {@link #PATROL_RANGE} of the carrier
     * @param speed       its own speed — S4.12 lets a patrol fly faster than its ship;
     *                    0 or less means "as fast as it can"
     */
    public record Posting(String fighterName, Location hex, int speed) {
        public Posting(String fighterName, Location hex) {
            this(fighterName, hex, 0);
        }
    }

    /**
     * What is wrong with this patrol, empty if nothing. Every fault rather than the first, so
     * a player fixing a setup sees the whole picture at once.
     */
    public static List<String> check(Ship carrier, List<Posting> patrol, int weaponStatus,
            int mapCols, int mapRows) {
        List<String> problems = new ArrayList<>();
        if (patrol == null || patrol.isEmpty())
            return problems;

        int allowed = maxDeployed(weaponStatus);
        if (allowed == 0)
            problems.add(carrier.getName() + " may not deploy fighters at WS-" + weaponStatus
                    + " (S4.10/S4.11: they may be armed and ready, not flying)");
        else if (patrol.size() > allowed)
            problems.add(carrier.getName() + " may deploy " + allowed + " fighter(s) at WS-"
                    + weaponStatus + ", not " + patrol.size());

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Posting posting : patrol) {
            Shuttle fighter = findInBays(carrier, posting.fighterName());
            if (fighter == null) {
                problems.add(posting.fighterName() + " is not a fighter in "
                        + carrier.getName() + "'s bays");
                continue;
            }
            if (!seen.add(posting.fighterName()))
                problems.add(posting.fighterName() + " is deployed twice");
            if (posting.hex() == null) {
                problems.add(posting.fighterName() + " has nowhere to stand");
                continue;
            }
            if (posting.hex().getX() < 1 || posting.hex().getX() > mapCols
                    || posting.hex().getY() < 1 || posting.hex().getY() > mapRows) {
                problems.add(posting.fighterName() + " is off the map");
                continue;
            }
            int range = MapUtils.getRange(posting.hex(), carrier.getLocation());
            if (range > PATROL_RANGE)
                problems.add(posting.fighterName() + " is " + range + " hexes from "
                        + carrier.getName() + "; a patrol stays within " + PATROL_RANGE
                        + " (S4.12)");
        }
        return problems;
    }

    /**
     * The speed this carrier is held to by the patrol it posts (S4.12).
     * <p>
     * The rule reads as a restriction on deploying — the ship's previous-turn speed "cannot
     * exceed the maximum speed of the fighters" — but there is no previous turn to have flown,
     * so it lands as a cap instead of a refusal (owner's ruling, 2026-09-27). The cost is real
     * and paid on turn one: C2.2 builds next turn's ceiling out of the speed history, so a
     * carrier that posts a slow patrol accelerates from a lower number.
     *
     * @return the cap, or -1 if this patrol imposes none
     */
    public static int speedCapFor(Ship carrier, List<Posting> patrol) {
        int slowest = -1;
        for (Posting posting : patrol) {
            Shuttle fighter = findInBays(carrier, posting.fighterName());
            if (fighter == null)
                continue;
            slowest = slowest < 0 ? fighter.getMaxSpeed() : Math.min(slowest, fighter.getMaxSpeed());
        }
        return slowest;
    }

    /**
     * Put the patrol on the board.
     * <p>
     * Assumes {@link #check} has passed. The fighters take the carrier's facing, because
     * S4.12 requires they share it, and the carrier's speed is capped at the slowest of them —
     * including the two turns of speed history C2.2 reads, since a ship that was never faster
     * than this cannot claim to have been.
     *
     * @return one line per fighter put up, plus a line if the carrier was slowed
     */
    public static List<String> deploy(Game game, Ship carrier, List<Posting> patrol) {
        List<String> log = new ArrayList<>();
        if (patrol == null || patrol.isEmpty())
            return log;

        // Before anything launches: the cap is read off the fighters' bays, and in a moment
        // they will not be in them.
        int cap = speedCapFor(carrier, patrol);

        for (Posting posting : patrol) {
            Shuttle fighter = findInBays(carrier, posting.fighterName());
            if (fighter == null)
                continue;
            ShuttleBay bay = bayHolding(carrier, fighter);
            if (bay == null)
                continue;

            int speed = posting.speed() > 0
                    ? Math.min(posting.speed(), fighter.getMaxSpeed())
                    : fighter.getMaxSpeed();   // J1.21: a shuttle flies at its best by default

            // Impulse 0: S4.1 puts a patrol beyond the launch requirements of J1.34, so the
            // hatch cooldown a mid-game launch would owe does not apply here.
            bay.launch(fighter, speed, carrier.getFacing(), 0);
            fighter.setLocation(posting.hex());
            fighter.setFacing(carrier.getFacing());
            fighter.setCurrentSpeed(speed);
            fighter.setSpeed(speed);
            fighter.setOwner(carrier.getOwner());
            fighter.setParentShipName(carrier.getName());
            game.getActiveShuttles().add(fighter);
            log.add(fighter.getName() + " on patrol at " + posting.hex()
                    + ", speed " + speed + " (S4.12)");
        }

        if (cap >= 0 && carrier.getSpeed() > cap) {
            log.add(carrier.getName() + " held to speed " + cap + " by its patrol — a ship"
                    + " cannot have outrun the fighters it just put up (S4.12)");
            carrier.setSpeed(cap);
        }
        if (cap >= 0) {
            carrier.setSpeedPreviousTurn(Math.min(carrier.getSpeedPreviousTurn(), cap));
            carrier.setSpeedTwoTurnsAgo(Math.min(carrier.getSpeedTwoTurnsAgo(), cap));
        }
        return log;
    }

    private static Shuttle findInBays(Ship carrier, String name) {
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            for (Shuttle s : bay.getInventory())
                if (s instanceof Fighter && s.getName().equalsIgnoreCase(name))
                    return s;
        return null;
    }

    private static ShuttleBay bayHolding(Ship carrier, Shuttle fighter) {
        for (ShuttleBay bay : carrier.getShuttles().getBays())
            if (bay.getInventory().contains(fighter))
                return bay;
        return null;
    }
}
