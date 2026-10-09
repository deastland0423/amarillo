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
  public static final int RP = mask(23, 24, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11); // Right Plasma arc
  public static final int LP = mask(15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 1, 2, 3); // Left Plasma arc
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

  /**
   * D2.34: where a plasma torpedo on this firing arc may be launched, as a direction bitmask.
   *
   * <p>A swivel mount "is able to track targets in a 180 degree firing arc and to fire its
   * weapons in any of three specified directions" — so the ARC is what the launcher can aim at
   * and the DIRECTION is the facing the torpedo leaves on, and the arc fixes the directions
   * completely. A hull has no say in it.
   *
   * <h2>Why this is derived rather than stored</h2>
   * It used to be stated, once per launcher, in every ship file: 159 hand-typed lists of numbers
   * where only one answer was ever possible. A mistyped list is invisible there, because a wrong
   * set of directions is still a perfectly ordinary set of directions. Three were found in a
   * single day — the Gorn L-Q's rear launchers given the left/right split that LP and RP use, and
   * sixteen side-arc launchers across eight hulls each carrying a spurious 13, which D2.34's
   * "three specified directions" rules out outright.
   *
   * <p>That last one is the cautionary tale: the sixteen were the MAJORITY, eight to one against
   * the single hull that had it right. Deriving makes the error unrepresentable instead of merely
   * detectable.
   *
   * @param arcs the launcher's firing arcs as a ship file spells them, in any order
   * @return the launch-direction mask, or 0 for an arc the rule does not cover — which the
   *         callers treat as "unrestricted", the same as a launcher that states nothing
   */
  public static int plasmaLaunchDirections(List<String> arcs) {
    if (arcs == null || arcs.isEmpty())
      return 0;
    List<String> key = new java.util.ArrayList<>();
    for (String a : arcs)
      key.add(a.toUpperCase(java.util.Locale.ROOT));
    // Sorted, so "L,LF" and "LF,L" — both of which appear in the data — are one case.
    java.util.Collections.sort(key);
    switch (String.join(",", key)) {
      // A 360-degree mount is not a swivel: it fires in any of the six cardinal directions
      // rather than D2.34's three. The Romulan Base Station's plasma-S is the first.
      case "FULL":
        return of(1, 5, 9, 13, 17, 21);
      case "FA":
        return of(1);
      case "RA":
        return of(13);
      case "FP":
        return of(21, 1, 5);
      case "LP":
      case "LS":
        return of(17, 21, 1);
      case "RP":
      case "RS":
        return of(1, 5, 9);
      case "L,LF":
        return of(21);
      case "R,RF":
        return of(5);
      case "L,LR":
        return of(17);
      case "R,RR":
        return of(9);
      // D2.36, the Gorn battle pod's REVERSE swivel mounts, and the aft plasma arc. No hull in
      // the data carries one yet; they are here so the first that does needs no code change.
      case "LPR":
        return of(13, 17, 21);
      case "RPR":
        return of(5, 9, 13);
      case "AP":
        return of(9, 13, 17);
      default:
        return 0;
    }
  }
}