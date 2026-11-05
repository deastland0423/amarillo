import type { DroneAvailability } from '../api/gameApi';

/**
 * Reading the server's drone-availability figures (FD10.622, FD10.632).
 *
 * <h2>Why this is a module and not four lines inside the dialog</h2>
 * It decides whether the Save button locks, which makes it the browser half of a refusal. The
 * other COI mirrors — `coiDroneCost`, `cartelQuota` — are modules for the same reason: the dialog
 * has no test of its own, so anything that can say no has to live somewhere a test can reach.
 *
 * Nothing here computes a tier or a percentage. Every number arrives from core, which has already
 * applied the draft loadout to the real ship; this only compares what is used against what is
 * allowed and labels the two rows.
 */

/** One cap, as a row of the gauge. */
export interface CapRow {
  /** What a player calls this tier. */
  label:   string;
  /** The clause that sets it, which is also the row's key. */
  rule:    string;
  used:    number;
  allowed: number;
  percent: number;
  over:    boolean;
}

/**
 * The two caps, Restricted first.
 *
 * Both are always returned, breach or not. FD10.64 nests them — "the allowed percentage of
 * Restricted Availability drones includes the limit on Limited Availability drones" — so one
 * drone moves both numbers, and showing only the one that happens to be breached would leave a
 * player unable to see why a legal-looking swap tripped the other.
 */
export function capRows(figures: DroneAvailability): CapRow[] {
  return [
    {
      label:   'Restricted',
      rule:    'FD10.622',
      used:    figures.restrictedUsed,
      allowed: figures.restrictedAllowed,
      percent: figures.restrictedPercent,
      over:    figures.restrictedUsed > figures.restrictedAllowed,
    },
    {
      label:   'Limited',
      rule:    'FD10.632',
      used:    figures.limitedUsed,
      allowed: figures.limitedAllowed,
      percent: figures.limitedPercent,
      over:    figures.limitedUsed > figures.limitedAllowed,
    },
  ];
}

/** Whether this ship is over either cap, which is what the fleet validator will refuse. */
export function overCaps(figures: DroneAvailability): boolean {
  return capRows(figures).some(r => r.over);
}

/**
 * The ships of a draft that are over a cap, in the order the server reported them.
 *
 * Names rather than a count, because the gauge lives inside each ship's own panel and a fleet of
 * six puts five of them off the top of the screen from the message at the bottom.
 *
 * An EMPTY reading means nothing is known, not that everything is legal: the figures arrive from
 * a request that may not have answered yet, or at all. The caller may only lock the Save button
 * on a ship that is named here — never unlock a refusal on the strength of a missing reply.
 */
export function shipsOverCaps(
  figures: Record<string, DroneAvailability> | undefined,
): string[] {
  return Object.values(figures ?? {}).filter(overCaps).map(a => a.shipName);
}
