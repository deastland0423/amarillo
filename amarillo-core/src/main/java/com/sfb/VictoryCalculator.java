package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;

import java.util.List;

/**
 * Calculates victory points and victory level at the end of a scenario (S2.20).
 *
 * Scoring (S2.21) — highest applicable percentage of Economic BPV per ship:
 *   Internal damage scored = 10%
 *   Forced to disengage    = 25%
 *   Crippled               = 50%
 *   Destroyed              = 100%
 *   Captured               = 200%
 *
 * Victory level (S2.3) = (myScore / opponentScore) as a percentage.
 */
public class VictoryCalculator {

    // S2.21 percentages
    public static final double PCT_INTERNAL_DAMAGE = 0.10;
    public static final double PCT_DISENGAGED      = 0.25;
    public static final double PCT_CRIPPLED        = 0.50;
    public static final double PCT_DESTROYED       = 1.00;
    public static final double PCT_CAPTURED        = 2.00;

    public enum VictoryLevel {
        ASTOUNDING_VICTORY,   // 500%+
        DECISIVE_VICTORY,     // 300–499%
        SUBSTANTIVE_VICTORY,  // 200–299%
        TACTICAL_VICTORY,     // 150–199%
        MARGINAL_VICTORY,     // 110–149%
        DRAW,                 // 91–109%
        MARGINAL_DEFEAT,      // 67–90%
        TACTICAL_DEFEAT,      // 50–66%
        BRUTAL_DEFEAT,        // 33–49%
        CRUSHING_DEFEAT,      // 20–32%
        DEVASTATING_DEFEAT    // 19%-
    }

    /**
     * Score points earned by one player for damage done to a list of enemy ships.
     * Only the highest applicable category is counted per ship (S2.21).
     * Fractions of 0.500+ round up; 0.499 round down (S2.24).
     */
    public static int scorePoints(List<Ship> enemyShips) {
        int total = 0;
        for (Ship ship : enemyShips) {
            total += pointsForShip(ship);
        }
        return total;
    }

    /**
     * Points scored for a single enemy ship — highest applicable category only.
     */
    public static int pointsForShip(Ship ship) {
        int epv = ship.getEconomicBpv();
        double raw;

        if (ship.isCaptured()) {
            raw = epv * PCT_CAPTURED;
        } else if (ship.getBattleStatus() == BattleStatus.DESTROYED) {
            raw = epv * PCT_DESTROYED;
        } else if (ship.isCrippled()) {
            raw = epv * PCT_CRIPPLED;
        } else if (ship.getBattleStatus() == BattleStatus.DISENGAGED) {
            raw = epv * PCT_DISENGAGED;
        } else {
            raw = 0;
        }

        return round(raw);
    }

    /**
     * Determine the victory level for a player (S2.3).
     * Divide myScore by opponentScore and consult the table.
     * Division by zero (opponent scored nothing) is treated as 500%+.
     */
    public static VictoryLevel victoryLevel(int myScore, int opponentScore) {
        if (opponentScore <= 0) return VictoryLevel.ASTOUNDING_VICTORY;
        double pct = (myScore * 100.0) / opponentScore;
        if (pct >= 500) return VictoryLevel.ASTOUNDING_VICTORY;
        if (pct >= 300) return VictoryLevel.DECISIVE_VICTORY;
        if (pct >= 200) return VictoryLevel.SUBSTANTIVE_VICTORY;
        if (pct >= 150) return VictoryLevel.TACTICAL_VICTORY;
        if (pct >= 110) return VictoryLevel.MARGINAL_VICTORY;
        if (pct >=  91) return VictoryLevel.DRAW;
        if (pct >=  67) return VictoryLevel.MARGINAL_DEFEAT;
        if (pct >=  50) return VictoryLevel.TACTICAL_DEFEAT;
        if (pct >=  33) return VictoryLevel.BRUTAL_DEFEAT;
        if (pct >=  20) return VictoryLevel.CRUSHING_DEFEAT;
        return VictoryLevel.DEVASTATING_DEFEAT;
    }

    /** S2.24: round 0.500+ up, 0.499 down. */
    static int round(double value) {
        return (int) Math.floor(value + 0.5);
    }
}
