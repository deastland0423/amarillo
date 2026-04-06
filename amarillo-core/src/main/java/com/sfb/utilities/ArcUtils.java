package com.sfb.utilities;

import java.util.List;

public class ArcUtils {
  // Arcs are represented as bitmasks of the 24 directions (1-24),
  // matching the bearing system used throughout the codebase.
  // Direction 1 = straight ahead (north), increasing clockwise.

  // Helper to create a bitmask from a list of 1-based directions.
  // Also public as of() for use in ship data and tests.
  public static int of(int... dirs) {
    return mask(dirs);
  }

  private static int mask(int... dirs) {
    int m = 0;
    for (int d : dirs)
      m |= (1 << (d - 1));
    return m;
  }

  // Basic Arcs (5 directions each, matching SFB arc definitions)
  public static final int LF = mask(21, 22, 23, 24, 1); // left-forward
  public static final int RF = mask(1, 2, 3, 4, 5); // right-forward
  public static final int R = mask(5, 6, 7, 8, 9); // right
  public static final int L = mask(17, 18, 19, 20, 21); // left
  public static final int RR = mask(9, 10, 11, 12, 13); // right-rear
  public static final int LR = mask(13, 14, 15, 16, 17); // left-rear

  // Combined Arcs (derived from base arcs)
  public static final int FA = LF | RF; // forward arc (9 dirs: 21-5)
  public static final int RA = RR | LR; // rear arc (9 dirs: 9-17)
  public static final int LS = L | LR | LF; // left side
  public static final int RS = R | RR | RF; // right side
  public static final int FH = LF | RF | mask(19, 20, 6, 7); // front half
  public static final int RH = LR | RR | mask(18, 19, 7, 8); // rear half
  public static final int RX = R | RR | LR | L;; // Rear Extended
  public static final int FX = L | LF | RF | R; // Front Extended
  public static final int RP = RF | R | mask(10, 11, 23, 24); // Right Plasma arc
  public static final int LP = LF | L | mask(15, 16, 2, 3); // Left Plasma arc
  public static final int FP = FH; // Forward Plasma arc (same as FH)
  public static final int FULL = LF | RF | R | L | RR | LR; // all directions

  // Check whether a 1-based bearing falls within an arc bitmask.
  public static boolean inArc(int targetDir, int arcMask) {
    return (arcMask & (1 << (targetDir - 1))) != 0;
  }

  // Aliases for common arc combinations. To be used for JSON weapon arcs in
  // stored ships.
  public static int calculateMask(List<String> arcComponents) {
    int finalMask = 0;
    for (String component : arcComponents) {
      // Check if it's a named alias
      Integer aliasMask = ArcUtils.getAlias(component.toUpperCase());
      if (aliasMask != null) {
        finalMask |= aliasMask;
      } else {
        // If not an alias, try to parse it as a raw direction (0-23)
        try {
          int dir = Integer.parseInt(component);
          finalMask |= ArcUtils.of(dir);
        } catch (NumberFormatException e) {
          System.err.println("Unknown Arc Component: " + component);
        }
      }
    }
    return finalMask;
  }

  private static Integer getAlias(String name) {
    switch (name) {
      case "LF":
        return LF;
      case "RF":
        return RF;
      case "R":
        return R;
      case "L":
        return L;
      case "RR":
        return RR;
      case "LR":
        return LR;
      case "FA":
        return FA;
      case "RA":
        return RA;
      case "LS":
        return LS;
      case "RS":
        return RS;
      case "FH":
        return FH;
      case "RH":
        return RH;
      case "FX":
        return FX;
      case "RX":
        return RX;
      case "RP":
        return RP;
      case "LP":
        return LP;
      case "FP":
        return FP;
      case "FULL":
        return FULL;
      default:
        return null; // Not an alias
    }
  }
}