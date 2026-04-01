package com.sfb.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sfb.objects.Drone;
import com.sfb.objects.Seeker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.properties.Faction;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * A JavaFX Canvas that renders the SFB hex map with ship counters.
 *
 * Hex layout: flat-top hexes, odd game-x columns shifted down by half a hex.
 * This matches the movement rules in MapUtils.getAdjacentHex():
 * - Direction 1 (N): same x, y-1
 * - Direction 5 (NE): x+1, same y (if even x) or y-1 (if odd x)
 * - etc.
 *
 * Facing: 24-direction system, facing 1 = north, increases clockwise.
 * screen angle (degrees from east, clockwise) = 270 + (facing-1)*15
 */
public class HexMapCanvas extends Canvas {

    // --- Hex geometry constants ---
    private static final double HEX_SIZE = 28.0; // circumradius (center to vertex)
    private static final double HEX_H = HEX_SIZE * Math.sqrt(3); // flat-top height
    private static final double COL_SPACING = HEX_SIZE * 1.5; // horizontal center-to-center
    private static final double MARGIN = 44.0;

    // --- Counter size ---
    private static final double COUNTER_SIZE = HEX_SIZE * 0.58; // inner counter hex radius
    private static final double SHIELD_RADIUS = HEX_SIZE * 0.87; // shield arc radius

    private final int cols;
    private final int rows;
    private final List<Ship> ships;

    private Ship selectedShip = null;
    private List<Ship> movableShips = new ArrayList<>();
    private List<Seeker> seekers = new ArrayList<>();
    private boolean firingMode = false;

    public HexMapCanvas(int cols, int rows, List<Ship> ships) {
        super(canvasWidth(cols), canvasHeight(rows));
        this.cols = cols;
        this.rows = rows;
        this.ships = ships;
        render();
    }

    private static double canvasWidth(int cols) {
        return MARGIN * 2 + cols * COL_SPACING + HEX_SIZE;
    }

    private static double canvasHeight(int rows) {
        return MARGIN * 2 + rows * HEX_H + HEX_H;
    }

    // -------------------------------------------------------------------------
    // Public state setters
    // -------------------------------------------------------------------------

    public void setSelectedShip(Ship ship) {
        this.selectedShip = ship;
    }

    public Ship getSelectedShip() {
        return selectedShip;
    }

    public void setMovableShips(List<Ship> movable) {
        this.movableShips = new ArrayList<>(movable);
    }

    public void setFiringMode(boolean firing) {
        this.firingMode = firing;
    }

    public void setSeekers(List<Seeker> seekers) {
        this.seekers = seekers;
    }

    /**
     * Returns the ship whose counter contains the given pixel, or null.
     */
    public Ship hitTestShip(double pixelX, double pixelY) {
        Ship closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Ship ship : ships) {
            double[] c = hexCenter(ship.getLocation().getX(), ship.getLocation().getY());
            double dist = Math.hypot(pixelX - c[0], pixelY - c[1]);
            if (dist < COUNTER_SIZE && dist < closestDist) {
                closest = ship;
                closestDist = dist;
            }
        }
        return closest;
    }

    /**
     * Returns all drones whose hex contains the given pixel.
     * Multiple drones can share a hex, so all are returned.
     */
    public List<Drone> hitTestDrones(double pixelX, double pixelY) {
        List<Drone> hits = new ArrayList<>();
        for (Seeker seeker : seekers) {
            if (!(seeker instanceof Drone)) continue;
            Drone drone = (Drone) seeker;
            if (drone.getLocation() == null) continue;
            double[] c = hexCenter(drone.getLocation().getX(), drone.getLocation().getY());
            double dist = Math.hypot(pixelX - c[0], pixelY - c[1]);
            if (dist < COUNTER_SIZE) hits.add(drone);
        }
        return hits;
    }

    // -------------------------------------------------------------------------
    // Public render entry point
    // -------------------------------------------------------------------------

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        drawBackground(gc);
        drawStars(gc);
        drawGrid(gc);
        drawCoordinateLabels(gc);
        for (Ship ship : ships) {
            drawShip(gc, ship);
        }
        for (Seeker seeker : seekers) {
            if (seeker instanceof Drone) drawDrone(gc, (Drone) seeker);
        }
    }

    // -------------------------------------------------------------------------
    // Background and star field
    // -------------------------------------------------------------------------

    private void drawBackground(GraphicsContext gc) {
        gc.setFill(Color.rgb(8, 8, 22));
        gc.fillRect(0, 0, getWidth(), getHeight());
    }

    private void drawStars(GraphicsContext gc) {
        Random rng = new Random(0xDEADBEEFL); // fixed seed = consistent layout
        int starCount = (int) (getWidth() * getHeight() / 1800);
        for (int i = 0; i < starCount; i++) {
            double x = rng.nextDouble() * getWidth();
            double y = rng.nextDouble() * getHeight();
            double brightness = 0.3 + rng.nextDouble() * 0.7;
            double size = rng.nextDouble() < 0.92 ? 1.0 : 1.8;
            gc.setFill(Color.gray(brightness, brightness));
            gc.fillOval(x, y, size, size);
        }
    }

    // -------------------------------------------------------------------------
    // Hex grid
    // -------------------------------------------------------------------------

    private void drawGrid(GraphicsContext gc) {
        gc.setStroke(Color.rgb(30, 50, 90, 0.65));
        gc.setLineWidth(0.8);
        for (int x = 1; x <= cols; x++) {
            for (int y = 1; y <= rows; y++) {
                double[] c = hexCenter(x, y);
                double[][] v = hexVertices(c[0], c[1], HEX_SIZE);
                gc.strokePolygon(v[0], v[1], 6);
            }
        }
    }

    private void drawCoordinateLabels(GraphicsContext gc) {
        gc.setFont(Font.font("Monospaced", 7.5));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int x = 1; x <= cols; x++) {
            for (int y = 1; y <= rows; y++) {
                if (x % 5 == 0 && y % 5 == 0) {
                    double[] c = hexCenter(x, y);
                    gc.setFill(Color.rgb(55, 80, 130, 0.8));
                    gc.fillText(x + "," + y, c[0], c[1] + 3.0);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ship counter
    // -------------------------------------------------------------------------

    private void drawShip(GraphicsContext gc, Ship ship) {
        int gx = ship.getLocation().getX();
        int gy = ship.getLocation().getY();
        double[] c = hexCenter(gx, gy);
        double cx = c[0];
        double cy = c[1];

        Color factionColor = factionColor(ship.getFaction());

        drawShieldArcs(gc, ship, cx, cy, factionColor);
        drawCounter(gc, ship, cx, cy, factionColor);
        drawFacingArrow(gc, cx, cy, ship.getFacing(), factionColor);
        drawCounterLabel(gc, ship, cx, cy);
    }

    /** Six colored arcs around the counter, one per shield. */
    private void drawShieldArcs(GraphicsContext gc, Ship ship, double cx, double cy, Color factionColor) {
        int facing = ship.getFacing();
        double baseDeg = 270.0 + (facing - 1) * 15.0; // screen degrees for shield 1 center

        for (int s = 1; s <= 6; s++) {
            int current = ship.getShields().getShieldStrength(s);
            int max = ship.getShields().getMaxShieldStrength(s);

            if (max == 0)
                continue;

            double ratio = (double) Math.max(0, current) / max;
            Color arcColor = shieldColor(ratio);

            double centerDeg = (baseDeg + (s - 1) * 60.0) % 360.0;
            double startDeg = centerDeg - 28.0; // slightly less than 30° for visible gap

            gc.setStroke(arcColor);
            gc.setLineWidth(3.5);
            drawArcSegment(gc, cx, cy, SHIELD_RADIUS, startDeg, 56.0);

            // Strength number just outside the arc
            double labelAngle = Math.toRadians(centerDeg);
            double labelX = cx + (SHIELD_RADIUS + 10) * Math.cos(labelAngle);
            double labelY = cy + (SHIELD_RADIUS + 10) * Math.sin(labelAngle);
            gc.setFill(arcColor.deriveColor(0, 1, 1.2, 1.0));
            gc.setFont(Font.font("Monospaced", 7.5));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(current), labelX, labelY + 3.0);
        }
    }

    /** Small filled hex as the ship counter body. */
    private void drawCounter(GraphicsContext gc, Ship ship, double cx, double cy, Color factionColor) {
        double[][] v = hexVertices(cx, cy, COUNTER_SIZE);

        // Dark body
        gc.setFill(factionColor.deriveColor(0, 0.8, 0.18, 1.0));
        gc.fillPolygon(v[0], v[1], 6);

        // Colored border
        gc.setStroke(factionColor);
        gc.setLineWidth(1.8);
        gc.strokePolygon(v[0], v[1], 6);

        // Movable-this-impulse: bright yellow outer ring
        if (movableShips.contains(ship) && ship != selectedShip) {
            double[][] outer = hexVertices(cx, cy, COUNTER_SIZE + 3.0);
            gc.setStroke(Color.rgb(240, 210, 40, 0.7));
            gc.setLineWidth(1.5);
            gc.strokePolygon(outer[0], outer[1], 6);
        }

        // Selected: white ring normally, red ring in firing mode
        if (ship == selectedShip) {
            double[][] outer = hexVertices(cx, cy, COUNTER_SIZE + 3.5);
            gc.setStroke(firingMode ? Color.rgb(255, 80, 80) : Color.WHITE);
            gc.setLineWidth(2.2);
            gc.strokePolygon(outer[0], outer[1], 6);
        }
    }

    /** Arrow pointing from counter center toward the facing direction. */
    private void drawFacingArrow(GraphicsContext gc, double cx, double cy, int facing, Color color) {
        double angleDeg = 270.0 + (facing - 1) * 15.0;
        double angleRad = Math.toRadians(angleDeg);
        double len = COUNTER_SIZE * 0.78;

        double tipX = cx + len * Math.cos(angleRad);
        double tipY = cy + len * Math.sin(angleRad);

        gc.setStroke(Color.WHITE.deriveColor(0, 1, 1, 0.9));
        gc.setLineWidth(1.4);
        gc.strokeLine(cx, cy, tipX, tipY);

        // Arrowhead
        double headLen = 5.5;
        double spread = 0.45;
        gc.strokeLine(tipX, tipY,
                tipX - headLen * Math.cos(angleRad - spread),
                tipY - headLen * Math.sin(angleRad - spread));
        gc.strokeLine(tipX, tipY,
                tipX - headLen * Math.cos(angleRad + spread),
                tipY - headLen * Math.sin(angleRad + spread));
    }

    /** Hull type label centered on the counter. */
    private void drawCounterLabel(GraphicsContext gc, Ship ship, double cx, double cy) {
        String label = ship.getHullType() != null ? ship.getHullType()
                : ship.getName() != null ? ship.getName()
                        : "?";
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 8.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, cx, cy + 3.5);
    }

    // -------------------------------------------------------------------------
    // Drone counter
    // -------------------------------------------------------------------------

    private void drawDrone(GraphicsContext gc, Drone drone) {
        if (drone.getLocation() == null) return;
        double[] c = hexCenter(drone.getLocation().getX(), drone.getLocation().getY());
        double cx = c[0];
        double cy = c[1];

        // Color from controller's faction (white if no controller)
        Color color = Color.WHITE;
        if (drone.getController() instanceof Ship) {
            color = factionColor(((Ship) drone.getController()).getFaction());
        }

        // Diamond shape (rotated square)
        double r = COUNTER_SIZE * 0.45;
        double[] dx = { cx,     cx + r, cx,     cx - r };
        double[] dy = { cy - r, cy,     cy + r, cy     };
        gc.setFill(color.deriveColor(0, 0.7, 0.15, 1.0));
        gc.fillPolygon(dx, dy, 4);
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.strokePolygon(dx, dy, 4);

        // Facing arrow
        drawFacingArrow(gc, cx, cy, drone.getFacing(), color);

        // Type label ("I", "II", etc.)
        String label = drone.getDroneType() != null ? drone.getDroneType().toString().replace("Type ", "") : "?";
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Monospaced", FontWeight.BOLD, 7.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(label, cx, cy + 3.0);
    }

    // -------------------------------------------------------------------------
    // Hex geometry helpers
    // -------------------------------------------------------------------------

    /**
     * Pixel center of game hex (x, y).
     * x and y are 1-based (matching Location in the game engine).
     *
     * Even game-x columns are shifted DOWN by HEX_H/2 relative to odd columns.
     * This matches MapUtils: from an even-x hex, the NE neighbor (x+1, y) is in
     * an odd-x column and sits higher on screen.
     */
    private double[] hexCenter(int x, int y) {
        int col = x - 1;
        int row = y - 1;
        double cx = MARGIN + col * COL_SPACING + HEX_SIZE;
        double cy = (x % 2 == 0)
                ? MARGIN + row * HEX_H + HEX_H / 2.0 // even x: shifted down
                : MARGIN + row * HEX_H; // odd x: reference
        return new double[] { cx, cy };
    }

    /** Flat-top hex vertices at (cx, cy) with circumradius size. */
    private double[][] hexVertices(double cx, double cy, double size) {
        double[] xs = new double[6];
        double[] ys = new double[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i);
            xs[i] = cx + size * Math.cos(a);
            ys[i] = cy + size * Math.sin(a);
        }
        return new double[][] { xs, ys };
    }

    /**
     * Draw an arc as a polyline.
     * startDeg and extentDeg are in screen-space degrees (0=east, 90=south,
     * clockwise).
     */
    private void drawArcSegment(GraphicsContext gc, double cx, double cy,
            double radius, double startDeg, double extentDeg) {
        int segments = 14;
        double step = extentDeg / segments;
        gc.beginPath();
        for (int i = 0; i <= segments; i++) {
            double rad = Math.toRadians(startDeg + i * step);
            double px = cx + radius * Math.cos(rad);
            double py = cy + radius * Math.sin(rad);
            if (i == 0)
                gc.moveTo(px, py);
            else
                gc.lineTo(px, py);
        }
        gc.stroke();
    }

    // -------------------------------------------------------------------------
    // Color helpers
    // -------------------------------------------------------------------------

    private Color factionColor(Faction faction) {
        if (faction == null)
            return Color.GRAY;
        switch (faction) {
            case Federation:
                return Color.rgb(4, 193, 240);
            case Klingon:
                return Color.rgb(210, 50, 50);
            case Romulan:
                return Color.rgb(190, 50, 50);
            case Gorn:
                return Color.rgb(50, 170, 130);
            case Kzinti:
                return Color.rgb(210, 110, 40);
            case Hydran:
                return Color.rgb(160, 60, 200);
            case Tholian:
                return Color.rgb(220, 180, 40);
            default:
                return Color.rgb(160, 160, 160);
        }
    }

    /** Green → yellow → red based on shield ratio (0.0 = depleted, 1.0 = full). */
    private Color shieldColor(double ratio) {
        if (ratio > 0.66)
            return Color.rgb(40, 210, 90, 0.85);
        if (ratio > 0.33)
            return Color.rgb(220, 185, 30, 0.85);
        if (ratio > 0.0)
            return Color.rgb(220, 60, 40, 0.85);
        return Color.rgb(70, 70, 70, 0.4); // depleted
    }
}
