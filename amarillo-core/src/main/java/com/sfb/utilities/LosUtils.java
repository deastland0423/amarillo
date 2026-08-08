package com.sfb.utilities;

import java.util.Set;

import com.sfb.properties.Location;

/**
 * Exact line-of-sight geometry (P2.321/P2.322).
 *
 * A line drawn from the center of hex A to the center of hex B is blocked if
 * it passes through ANY PART of a blocking hex — but NOT if it only runs
 * along the hex's edge or touches a corner (fire along the edge is legal,
 * P2.321). So the test is: does the open segment intersect the OPEN INTERIOR
 * of a blocking hexagon?
 *
 * All arithmetic is exact. On this flat-top grid, scaling x by SIZE/2 and y
 * by (√3/2)·SIZE puts every center and every vertex on integer coordinates:
 *   center(col,row) = (3·col, 2·row + (col even ? 1 : 0))
 *   vertices at offsets (±2, 0), (±1, ±1)
 * Positive axis scaling preserves incidence (lines, interiors, intersections),
 * so orientation tests reduce to integer cross products and the clip
 * parameters to exact fractions — no epsilon ever decides legality.
 * (Same discipline as the zone-based bearing algorithm: never approximate
 * grid geometry.)
 */
public final class LosUtils {

    private LosUtils() {}

    /** Scaled-x of a hex center. */
    private static long cx(Location h) { return 3L * h.getX(); }

    /** Scaled-y of a hex center (even columns shift down a half-row). */
    private static long cy(Location h) { return 2L * h.getY() + (h.getX() % 2 == 0 ? 1 : 0); }

    // Vertex offsets of a flat-top hexagon in scaled coordinates, in order.
    private static final long[][] VERTS = {
            { -2, 0 }, { -1, -1 }, { 1, -1 }, { 2, 0 }, { 1, 1 }, { -1, 1 } };

    /**
     * True when the center-to-center segment from {@code a} to {@code b}
     * crosses the strict interior of any hex in {@code blockers}. The hexes
     * containing the endpoints never block (they are not "between" the units,
     * P2.322). Edge-collinear and corner-touching lines do not block.
     */
    public static boolean blocked(Location a, Location b, Set<Location> blockers) {
        if (a == null || b == null || a.equals(b) || blockers == null || blockers.isEmpty())
            return false;
        long px = cx(a), py = cy(a);
        long dx = cx(b) - px, dy = cy(b) - py;
        for (Location hex : blockers) {
            if (hex.equals(a) || hex.equals(b))
                continue;
            if (segmentCrossesInterior(px, py, dx, dy, hex))
                return true;
        }
        return false;
    }

    /**
     * Cyrus–Beck clip of segment P + t·D, t ∈ [0,1], against the closed
     * hexagon, with exact fraction endpoints. The interior is crossed iff the
     * clipped interval has positive length AND the line is not collinear with
     * one of the hexagon's edge lines (in which case the overlap lies wholly
     * on that edge — boundary, not interior).
     */
    private static boolean segmentCrossesInterior(long px, long py, long dx, long dy, Location hex) {
        long hx = cx(hex), hy = cy(hex);

        // t-interval as exact fractions lo = loN/loD, hi = hiN/hiD (D > 0)
        long loN = 0, loD = 1, hiN = 1, hiD = 1;
        boolean collinearWithAnEdge = false;

        for (int i = 0; i < 6; i++) {
            long v1x = hx + VERTS[i][0],           v1y = hy + VERTS[i][1];
            long v2x = hx + VERTS[(i + 1) % 6][0], v2y = hy + VERTS[(i + 1) % 6][1];
            long ex = v2x - v1x, ey = v2y - v1y;

            // side(Q) = cross(E, Q − V1); the hex center is strictly on the
            // interior side, giving the sign convention with no orientation
            // assumptions
            long sideCenter = ex * (hy - v1y) - ey * (hx - v1x);
            long s = Long.signum(sideCenter);

            // f(t) = side(P + tD) = f0 + t·fd  — require s·f(t) ≥ 0
            long f0 = ex * (py - v1y) - ey * (px - v1x);
            long fd = ex * dy - ey * dx;
            long a0 = s * f0, ad = s * fd;

            if (ad == 0) {
                if (a0 < 0)
                    return false;      // parallel, entirely outside this edge
                if (a0 == 0)
                    collinearWithAnEdge = true; // riding this edge's line
                continue;              // parallel on the interior side: no constraint
            }
            // Constraint boundary at t = −a0/ad
            long n = -a0, d = ad;
            if (d < 0) { n = -n; d = -d; } // normalize denominator positive
            if (ad > 0) {
                // f increases with t → constraint is t ≥ n/d → raise lo
                if (n * loD > loN * d) { loN = n; loD = d; }
            } else {
                // f decreases with t → constraint is t ≤ n/d → lower hi
                if (n * hiD < hiN * d) { hiN = n; hiD = d; }
            }
            if (loN * hiD > hiN * loD)
                return false;          // interval empty — no intersection at all
        }

        // Positive-length overlap with the closed hexagon?
        boolean positiveLength = loN * hiD < hiN * loD;
        return positiveLength && !collinearWithAnEdge;
    }
}
