package com.sfb.utilities;

public class ArcUtils {
  // Arcs are represented as bitmasks of the 24 directions (1-24),
  // matching the bearing system used throughout the codebase.
  // Direction 1 = straight ahead (north), increasing clockwise.

  // Helper to create a bitmask from a list of 1-based directions.
  // Also public as of() for use in ship data and tests.
  public static int of(int... dirs) { return mask(dirs); }

  private static int mask(int... dirs) {
    int m = 0;
    for (int d : dirs)
      m |= (1 << (d - 1));
    return m;
  }

  // Basic Arcs (5 directions each, matching SFB arc definitions)
  public static final int LF = mask(21, 22, 23, 24, 1);  // left-forward
  public static final int RF = mask(1,  2,  3,  4,  5);  // right-forward
  public static final int R  = mask(5,  6,  7,  8,  9);  // right
  public static final int L  = mask(17, 18, 19, 20, 21); // left
  public static final int RR = mask(9,  10, 11, 12, 13); // right-rear
  public static final int LR = mask(13, 14, 15, 16, 17); // left-rear

  // Combined Arcs (derived from base arcs)
  public static final int FA   = LF | RF;               // forward arc (9 dirs: 21-5)
  public static final int RA   = RR | LR;               // rear arc    (9 dirs: 9-17)
  public static final int LS   = L  | LR | LF;          // left side
  public static final int RS   = R  | RR | RF;          // right side
  public static final int FH   = LF | RF | mask(19, 20, 6, 7);  // front half
  public static final int RH   = LR | RR | mask(18, 19, 7, 8);  // rear half
  public static final int FULL = LF | RF | R | L | RR | LR;     // all directions

  // Check whether a 1-based bearing falls within an arc bitmask.
  public static boolean inArc(int targetDir, int arcMask) {
    return (arcMask & (1 << (targetDir - 1))) != 0;
  }
}