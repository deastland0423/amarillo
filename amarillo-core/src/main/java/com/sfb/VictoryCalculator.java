package com.sfb;

import com.sfb.objects.Ship;
import com.sfb.properties.BattleStatus;

/**
 * Victory point scoring (S2.21/S2.24) and victory level (S2.3). This is the
 * single home for the scoring math; Game.calculateVictoryPoints() assembles
 * the scoreboard (rows, team attribution) and delegates every number here.
 *
 * Scoring — one category per ship, evaluated in this order:
 *   Destroyed  = 100%  (a captured ship that is subsequently destroyed gives
 *                       up only 100% — destruction truncates the capture,
 *                       per user ruling 2026-07-09)
 *   Captured   = 200%
 *   Crippled   =  50%
 *   Disengaged =  25%
 *   Internal damage scored = 10%
 */
public class VictoryCalculator {

    /** Per-ship scoring category with its S2.21 percentage. */
    public enum ShipStatus {
        DESTROYED(1.00),
        CAPTURED(2.00),
        CRIPPLED(0.50),
        DISENGAGED(0.25),
        DAMAGED(0.10),
        INTACT(0.0);

        public final double pct;

        ShipStatus(double pct) {
            this.pct = pct;
        }
    }

    public enum VictoryLevel {
        ASTOUNDING_VICTORY("Astounding Victory"),   // 500%+
        DECISIVE_VICTORY("Decisive Victory"),       // 300–499%
        SUBSTANTIVE_VICTORY("Substantive Victory"), // 200–299%
        TACTICAL_VICTORY("Tactical Victory"),       // 150–199%
        MARGINAL_VICTORY("Marginal Victory"),       // 110–149%
        DRAW("Draw"),                               // 91–109%
        MARGINAL_DEFEAT("Marginal Defeat"),         // 67–90%
        TACTICAL_DEFEAT("Tactical Defeat"),         // 50–66%
        BRUTAL_DEFEAT("Brutal Defeat"),             // 33–49%
        CRUSHING_DEFEAT("Crushing Defeat"),         // 20–32%
        DEVASTATING_DEFEAT("Devastating Defeat");   // 19%-

        private final String label;

        VictoryLevel(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * The single scoring category that applies to this ship (S2.21).
     * Disengagement is recognized from either representation (the boolean flag
     * set by DisengagementResolver, or BattleStatus.DISENGAGED).
     */
    public static ShipStatus status(Ship ship) {
        if (ship.isDestroyed())
            return ShipStatus.DESTROYED; // truncates a capture at 100%
        if (ship.isCaptured())
            return ShipStatus.CAPTURED;
        if (ship.isCrippled())
            return ShipStatus.CRIPPLED;
        if (ship.isDisengaged() || ship.getBattleStatus() == BattleStatus.DISENGAGED)
            return ShipStatus.DISENGAGED;
        if (ship.isDamaged())
            return ShipStatus.DAMAGED;
        return ShipStatus.INTACT;
    }

    /** Points scored against this ship, using the supplied BPV basis (e.g. GABPV). */
    public static int pointsFor(Ship ship, int bpv) {
        return round(bpv * status(ship).pct);
    }

    /** Sum of pointsForShip across a list of enemy ships. */
    public static int scorePoints(java.util.List<Ship> enemyShips) {
        int total = 0;
        for (Ship ship : enemyShips)
            total += pointsForShip(ship);
        return total;
    }

    /** Convenience overload scoring against the ship's economic BPV. */
    public static int pointsForShip(Ship ship) {
        return pointsFor(ship, ship.getEconomicBpv());
    }

    /**
     * Determine the victory level for a side (S2.3): myScore / opponentScore
     * as a percentage, consulted against the table. If the opponent scored
     * nothing: any points at all is an Astounding Victory; zero-zero is a Draw.
     */
    public static VictoryLevel victoryLevel(int myScore, int opponentScore) {
        if (opponentScore <= 0)
            return myScore > 0 ? VictoryLevel.ASTOUNDING_VICTORY : VictoryLevel.DRAW;
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
