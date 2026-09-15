package com.sfb.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sfb.properties.Location;
import com.sfb.utilities.MapUtils;

/**
 * A named piece of the map, described rather than enumerated.
 * <p>
 * Two things need to point at an area of the map without listing its hexes: a fleet's
 * deployment zone, which the players agree along with the terrain and the map (S8.15, S8.135),
 * and a terrain feature, which needs somewhere to scatter and somewhere to keep clear. Both
 * want the same four shapes, so both use this.
 * <p>
 * A circle around a hex, a band along one edge, an arbitrary box, or the whole map. A band is
 * a box in disguise, but it is how people describe it — "set up within six hexes of your own
 * edge" — so it is worth being able to say directly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapRegion {

    public enum Shape {
        /** Within {@code radius} hexes of {@code hex}. */
        CIRCLE,
        /** Within {@code depth} hexes of one map edge. */
        BAND,
        /** Anywhere between two opposite corners. */
        BOX,
        /** The whole map. */
        ANYWHERE
    }

    public Shape shape = Shape.ANYWHERE;

    /** CIRCLE: the centre, in CCRR notation. */
    public String hex;
    /** CIRCLE: hexes from the centre. Zero means the centre hex alone. */
    public int radius;

    /** BAND: which edge — "TOP", "BOTTOM", "LEFT" or "RIGHT". */
    public String edge;
    /** BAND: how far in from that edge, counting the edge itself as one. */
    public int depth;

    /** BOX: two opposite corners, in CCRR notation. */
    public String from;
    public String to;

    public static MapRegion anywhere() {
        return new MapRegion();
    }

    public static MapRegion circle(String hex, int radius) {
        MapRegion z = new MapRegion();
        z.shape = Shape.CIRCLE;
        z.hex = hex;
        z.radius = radius;
        return z;
    }

    public static MapRegion band(String edge, int depth) {
        MapRegion z = new MapRegion();
        z.shape = Shape.BAND;
        z.edge = edge;
        z.depth = depth;
        return z;
    }

    public static MapRegion box(String from, String to) {
        MapRegion z = new MapRegion();
        z.shape = Shape.BOX;
        z.from = from;
        z.to = to;
        return z;
    }

    /**
     * True if a ship may set up on this hex. Off-map is never allowed, whatever the shape.
     *
     * @param mapCols the map's width, needed to place a RIGHT band
     * @param mapRows the map's height, needed to place a BOTTOM band
     */
    public boolean contains(int col, int row, int mapCols, int mapRows) {
        if (col < 1 || col > mapCols || row < 1 || row > mapRows)
            return false;

        switch (shape == null ? Shape.ANYWHERE : shape) {
            case ANYWHERE:
                return true;

            case CIRCLE: {
                Location centre = parse(hex);
                if (centre == null)
                    return false;
                // Hex range, not a square: MapUtils knows the column-parity geometry.
                return MapUtils.getRange(centre, new Location(col, row)) <= Math.max(0, radius);
            }

            case BAND: {
                int d = Math.max(1, depth);
                if (edge == null)
                    return false;
                switch (edge.toUpperCase()) {
                    case "LEFT":   return col <= d;
                    case "RIGHT":  return col > mapCols - d;
                    case "TOP":    return row <= d;
                    case "BOTTOM": return row > mapRows - d;
                    default:       return false;
                }
            }

            case BOX: {
                Location a = parse(from);
                Location b = parse(to);
                if (a == null || b == null)
                    return false;
                // Corners given either way round.
                int loCol = Math.min(a.getX(), b.getX()), hiCol = Math.max(a.getX(), b.getX());
                int loRow = Math.min(a.getY(), b.getY()), hiRow = Math.max(a.getY(), b.getY());
                return col >= loCol && col <= hiCol && row >= loRow && row <= hiRow;
            }

            default:
                return false;
        }
    }

    public boolean contains(Location loc, int mapCols, int mapRows) {
        return loc != null && contains(loc.getX(), loc.getY(), mapCols, mapRows);
    }

    /** How this zone reads in a message, so a refusal can say what was expected. */
    public String describe() {
        switch (shape == null ? Shape.ANYWHERE : shape) {
            case CIRCLE:   return "within " + radius + " hexes of " + hex;
            case BAND:     return "within " + Math.max(1, depth) + " hexes of the "
                                  + (edge == null ? "?" : edge.toLowerCase()) + " edge";
            case BOX:      return "between " + from + " and " + to;
            case ANYWHERE: return "anywhere on the map";
            default:       return "nowhere";
        }
    }

    /** SFB's CCRR notation: two digits of column, two of row. */
    static Location parse(String ccrr) {
        if (ccrr == null || ccrr.length() != 4)
            return null;
        try {
            return new Location(Integer.parseInt(ccrr.substring(0, 2)),
                                Integer.parseInt(ccrr.substring(2, 4)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
