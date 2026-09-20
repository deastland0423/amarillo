/**
 * Hex geometry, mirrored from `com.sfb.utilities.MapUtils`.
 *
 * The map uses offset coordinates with column-parity-sensitive neighbours, so none of this
 * can be done with naive (dx, dy) arithmetic — the answers differ between odd and even
 * columns. Bearings in particular use the zone-based spine-line algorithm, never geometric
 * atan2 and rounding, which gives subtly wrong directions near the spines.
 *
 * This is a MIRROR, not a second authority. The server decides every rules outcome; these
 * exist so the client can preview one without a round trip, the same way
 * weaponDamageTables.ts mirrors damage. When the two disagree the server is right.
 *
 * Gathered here because it was previously scattered: `hexRange` existed twice with
 * different signatures (GameBoard and HexGrid, identical algorithms), and the bearing port
 * sat in the middle of a five-thousand-line component. One copy of each is the point — a
 * mirror that has drifted is worse than no mirror, because it looks authoritative.
 */

/** The six facings a unit can hold, as SFB directions (A–F). */
export const FACING_DIRS = [1, 5, 9, 13, 17, 21] as const;

export interface Hex {
  col: number;
  row: number;
}

// ---------------------------------------------------------------------------- range

/** Range in hexes between two positions. Mirrors MapUtils.getRange(Location, Location). */
export function hexRange(c1: number, r1: number, c2: number, r2: number): number {
  const xDiff = Math.abs(c2 - c1);
  if (xDiff === 0) return Math.abs(r2 - r1);
  const even    = c1 % 2 === 0;
  const topY    = even ? r1 - Math.floor(xDiff / 2) : r1 - Math.floor((xDiff + 1) / 2);
  const bottomY = even ? r1 + Math.floor((xDiff + 1) / 2) : r1 + Math.floor(xDiff / 2);
  if (r2 >= topY && r2 <= bottomY) return xDiff;
  return r2 < topY ? xDiff + (topY - r2) : xDiff + (r2 - bottomY);
}

/** Range between two hexes given as objects. */
export function hexRangeBetween(a: Hex, b: Hex): number {
  return hexRange(a.col, a.row, b.col, b.row);
}

// ---------------------------------------------------------------------------- bearing

function largeOdd(x: number): number { let y = 2; for (let i = 1; i < x; i += 2) y += 3; return y; }
function smallOdd(x: number): number { let y = 1; for (let i = 1; i < x; i += 2) y += 3; return y; }

/**
 * Zone-based bearing, matching MapUtils.getBearing(Marker, Marker).
 * Returns an SFB direction 1–24, or 0 when both hexes are the same.
 */
export function hexGetBearing(srcCol: number, srcRow: number, tgtCol: number, tgtRow: number): number {
  if (srcCol === tgtCol && srcRow === tgtRow) return 0;
  const xOffset = tgtCol - srcCol;
  if (xOffset === 0) return tgtRow < srcRow ? 1 : 13;
  const absX = Math.abs(xOffset);
  // Due west / due east land on the vertex between two directions: 19 between 17 and 21,
  // 7 between 5 and 9. (4 and 10 belong to the twelve-point shield scheme, not here.)
  if (absX % 2 === 0 && srcRow === tgtRow) return xOffset < 0 ? 19 : 7;

  const srcEven = srcCol % 2 === 0;
  const above   = srcEven ? tgtRow <= srcRow : tgtRow < srcRow;

  const topArcY = srcEven ? srcRow - Math.floor(absX / 2)       : srcRow - Math.floor((absX + 1) / 2);
  const botArcY = srcEven ? srcRow + Math.floor((absX + 1) / 2) : srcRow + Math.floor(absX / 2);

  let spineOffset: number;
  if (absX % 2 === 0) {
    spineOffset = Math.floor(absX / 2) + absX;
  } else {
    const lg = largeOdd(absX), sm = smallOdd(absX);
    spineOffset = above ? (srcEven ? sm : lg) : (srcEven ? lg : sm);
  }
  const spineY = above ? srcRow - spineOffset : srcRow + spineOffset;

  if (xOffset < 0 && above) {
    if (tgtRow === spineY)  return 23;
    if (tgtRow === topArcY) return 21;
    if (tgtRow < spineY)    return 24;
    if (tgtRow > topArcY)   return 20;
    return 22;
  }
  if (xOffset > 0 && above) {
    if (tgtRow === spineY)  return 3;
    if (tgtRow === topArcY) return 5;
    if (tgtRow < spineY)    return 2;
    if (tgtRow > topArcY)   return 6;
    return 4;
  }
  if (xOffset < 0) {
    if (tgtRow === spineY)  return 15;
    if (tgtRow === botArcY) return 17;
    if (tgtRow > spineY)    return 14;
    if (tgtRow < botArcY)   return 18;
    return 16;
  }
  // xOffset > 0, below
  if (tgtRow === spineY)  return 11;
  if (tgtRow === botArcY) return 9;
  if (tgtRow > spineY)    return 12;
  if (tgtRow < botArcY)   return 8;
  return 10;
}

/** Bearing between two hexes given as objects. */
export function hexGetBearingBetween(from: Hex, to: Hex): number {
  return hexGetBearing(from.col, from.row, to.col, to.row);
}

/** Port of MapUtils.getRelativeBearing: a true bearing seen from a unit's facing. */
export function hexGetRelativeBearing(trueBearing: number, facing: number): number {
  if (facing === 1) return trueBearing;
  return trueBearing >= facing ? trueBearing - (facing - 1) : trueBearing + (24 - (facing - 1));
}

/**
 * Turn a unit `steps` facings to starboard (negative for port), wrapping the ring of six.
 *
 * A facing is one of the six directions A-F, so turning is a step around FACING_DIRS and
 * not arithmetic on the 24-point scale — adding 4 to direction 21 gives 25, which is not a
 * direction at all. Anything off the ring snaps to the nearest facing rather than throwing,
 * since a wrong-but-sane diagram beats a blank one.
 */
export function turnFacing(facing: number, steps: number): number {
  const ring = FACING_DIRS.length;                       // six
  const from = Math.round((((facing - 1) % 24) + 24) % 24 / 4) % ring;
  const to = (((from + steps) % ring) + ring) % ring;
  return FACING_DIRS[to];
}

// ---------------------------------------------------------------------------- arcs

/**
 * An arc is a 24-bit mask: bit N-1 set means direction N is covered, expressed RELATIVE to
 * the unit's facing. So reading an arc always means converting a true bearing into a
 * relative one first.
 */
export function directionsInArc(arcMask: number): number[] {
  const dirs: number[] = [];
  for (let d = 1; d <= 24; d++)
    if ((arcMask >> (d - 1)) & 1)
      dirs.push(d);
  return dirs;
}

/** The facings a unit may hold given a mask — used for seeker launch directions. */
export function allowedFacingsFromMask(arcMask: number): Set<number> {
  return new Set(FACING_DIRS.filter(d => (arcMask >> (d - 1)) & 1));
}

/**
 * Which shield faces an ADJACENT hex: 1 dead ahead, numbering clockwise, as on the SSD.
 *
 * Defined for the six hexes touching a unit, where the bearing is always the centre of a
 * shield. Bearings on a seam between two shields (relative 3, 7, 11, 15, 19, 23) have
 * split-shield rules that nothing in this project models yet, and this would answer for
 * them with false confidence.
 *
 * Core reaches the same number through a twelve-point scheme of its own; the shared
 * fixture asserts the two agree, because a mislabelled ring means reinforcing the wrong
 * shield.
 */
export function ringShieldNumber(from: Hex, facing: number, adjacent: Hex): number {
  const relative = hexGetRelativeBearing(hexGetBearingBetween(from, adjacent), facing);
  return (Math.round((relative - 1) / 4) % 6) + 1;
}

/** True if a weapon with this arc, on a unit at `from` facing `facing`, bears on `to`. */
export function bearsOn(from: Hex, facing: number, arcMask: number, to: Hex): boolean {
  const trueBearing = hexGetBearingBetween(from, to);
  if (trueBearing === 0) return false;          // same hex: no bearing exists
  const relative = hexGetRelativeBearing(trueBearing, facing);
  return ((arcMask >> (relative - 1)) & 1) === 1;
}

/**
 * Every hex within `radius` that a weapon with this arc bears on, for drawing an arc
 * diagram. The centre hex is never included: a unit has no bearing on itself.
 *
 * Deliberately unbounded by weapon range — an arc and a range are different rules, and a
 * diagram that silently stops at maximum range cannot be told from one that stops at the
 * edge of the arc.
 */
export function hexesInArc(centre: Hex, facing: number, arcMask: number, radius: number): Hex[] {
  const hexes: Hex[] = [];
  for (let col = centre.col - radius; col <= centre.col + radius; col++)
    for (let row = centre.row - radius; row <= centre.row + radius; row++) {
      const hex = { col, row };
      if (hexRangeBetween(centre, hex) > radius) continue;
      if (bearsOn(centre, facing, arcMask, hex))
        hexes.push(hex);
    }
  return hexes;
}
