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
 * The two caps on the ORDINARY drone load, Restricted first.
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

/**
 * FD10.644's pool as its own rows, or none at all on a hull with no dogfight rack.
 *
 * "Fighter squadrons count type-VI drones loaded on type-VI rails separately from all other
 * drones" — a separate basis with its own ceilings at the same percentages, which is why these
 * are rows beside the first two rather than numbers added into them. A ship at its dogfight
 * ceiling has spent none of its ordinary allowance, and a gauge that pooled them would show room
 * that the fleet validator does not agree exists.
 */
export function dogfightCapRows(figures: DroneAvailability): CapRow[] {
  // `?? 0` on every one of these, because a server from before FD10.644 was counted sends no
  // such keys and `undefined > undefined` is false -- which would read as "within the cap"
  // rather than as "no reading", the same way round as the `!= null` trap on a nullable DTO
  // field. A hull with no dogfight rack reports zero spaces and gets no rows by the same test.
  if ((figures.dogfightSpaces ?? 0) <= 0)
    return [];
  const restrictedUsed = figures.dogfightRestrictedUsed ?? 0;
  const restrictedAllowed = figures.dogfightRestrictedAllowed ?? 0;
  const limitedUsed = figures.dogfightLimitedUsed ?? 0;
  const limitedAllowed = figures.dogfightLimitedAllowed ?? 0;
  return [
    {
      label:   'Restricted',
      rule:    'FD10.622',
      used:    restrictedUsed,
      allowed: restrictedAllowed,
      percent: figures.restrictedPercent,
      over:    restrictedUsed > restrictedAllowed,
    },
    {
      label:   'Limited',
      rule:    'FD10.632',
      used:    limitedUsed,
      allowed: limitedAllowed,
      percent: figures.limitedPercent,
      over:    limitedUsed > limitedAllowed,
    },
  ];
}

/** Whether this ship is over any cap, which is what the fleet validator will refuse. */
export function overCaps(figures: DroneAvailability): boolean {
  return capRows(figures).some(r => r.over) || dogfightCapRows(figures).some(r => r.over);
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
