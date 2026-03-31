package com.sfb.ui;

import java.util.List;

import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.systemgroups.HullBoxes;
import com.sfb.systemgroups.PowerSystems;
import com.sfb.utilities.MapUtils;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * A side panel showing full status for the currently selected ship.
 * Call update(ship) whenever the selection changes or game state changes.
 */
public class ShipInfoPanel extends VBox {

    private static final String PANEL_BG = "-fx-background-color: #0c0c20;";
    private static final String SECTION_BG = "-fx-background-color: #111128; -fx-background-radius: 4;";

    private static final Font TITLE_FONT = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 10);
    private static final Font VALUE_FONT = Font.font("Monospaced", 10);

    private static final Color FED_COLOR = Color.rgb(70, 190, 80);
    private static final Color KLIN_COLOR = Color.rgb(210, 50, 50);
    private static final Color DIM_COLOR = Color.rgb(100, 100, 130);

    // --- Title row ---
    private final Label nameLabel = styledLabel("---", TITLE_FONT, Color.WHITE);
    private final Label hullLabel = styledLabel("", VALUE_FONT, DIM_COLOR);
    private final Label factionLabel = styledLabel("", VALUE_FONT, DIM_COLOR);

    // --- Location / movement ---
    private final Label hexLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label facingLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label speedLabel = styledLabel("---", VALUE_FONT, Color.WHITE);

    // --- Shields (6 bars) ---
    private final Label[] shieldLabels = new Label[6];

    // --- Hull boxes ---
    private final Label fhullLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label ahullLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label chullLabel = styledLabel("---", VALUE_FONT, Color.WHITE);

    // --- Power ---
    private final Label lwarpLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label rwarpLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label impulseLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label aprLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label batteryLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label totalPwrLabel = styledLabel("---", VALUE_FONT, Color.rgb(180, 210, 255));

    // --- BPV / condition ---
    private final Label bpvLabel = styledLabel("---", VALUE_FONT, Color.WHITE);
    private final Label condLabel = styledLabel("---", VALUE_FONT, Color.WHITE);

    // --- Weapons (rebuilt dynamically per ship) ---
    private final VBox weaponsContent = new VBox(2);

    public ShipInfoPanel() {
        setPrefWidth(200);
        setMinWidth(200);
        setStyle(PANEL_BG);
        setPadding(new Insets(10, 8, 10, 8));
        setSpacing(8);

        for (int i = 0; i < 6; i++) {
            shieldLabels[i] = styledLabel("---", VALUE_FONT, Color.WHITE);
        }

        getChildren().addAll(
                buildTitleSection(),
                buildSection("LOCATION", buildLocationGrid()),
                buildSection("SHIELDS", buildShieldGrid()),
                buildSection("HULL", buildHullGrid()),
                buildSection("POWER", buildPowerGrid()),
                buildSection("STATUS", buildStatusGrid()),
                buildWeaponsSection());

        showEmpty();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Populate the panel with the given ship's current data. */
    public void update(Ship ship) {
        if (ship == null) {
            showEmpty();
            return;
        }

        Color fc = factionColor(ship.getFaction());
        nameLabel.setText(ship.getName() != null ? ship.getName() : "Unknown");
        nameLabel.setTextFill(fc);
        hullLabel.setText(ship.getHullType() != null ? ship.getHullType() : "");
        factionLabel.setText(ship.getFaction() != null ? ship.getFaction().name() : "");

        // Location / movement
        hexLabel.setText(ship.getLocation().getX() + " , " + ship.getLocation().getY());
        facingLabel.setText(String.valueOf(MapUtils.getFacingLetter(ship.getFacing()))); // Show facing letter.
        speedLabel.setText(String.valueOf(ship.getSpeed()));

        // Shields
        for (int i = 0; i < 6; i++) {
            int cur = ship.getShields().getShieldStrength(i + 1);
            int max = ship.getShields().getMaxShieldStrength(i + 1);
            if (max == 0) {
                shieldLabels[i].setText("n/a");
                shieldLabels[i].setTextFill(DIM_COLOR);
            } else {
                shieldLabels[i].setText(cur + " / " + max + "  " + bar(cur, max, 8));
                shieldLabels[i].setTextFill(shieldColor(cur, max));
            }
        }

        // Hull
        HullBoxes hull = ship.getHullBoxes();
        fhullLabel.setText("Fwd  " + hull.getAvailableFhull());
        ahullLabel.setText("Aft  " + hull.getAvailableAhull());
        chullLabel.setText("Ctr  " + hull.getAvailableChull());

        // Power
        PowerSystems ps = ship.getPowerSysetems();
        lwarpLabel.setText(ps.getAvailableLWarp() + " + " + ps.getAvailableRWarp());
        rwarpLabel.setText(String.valueOf(ps.getAvailableCWarp()));
        impulseLabel.setText(String.valueOf(ps.getAvailableImpulse()));
        aprLabel.setText(ps.getAvailableApr() + " / " + ps.getAvailableAwr());
        batteryLabel.setText(String.valueOf(ps.getAvailableBattery()));
        totalPwrLabel.setText(String.valueOf(ps.getTotalAvailablePower()));

        // Status
        bpvLabel.setText("BPV  " + ship.getBattlePointValue());
        condLabel.setText(ship.isCrippled() ? "CRIPPLED" : "Operational");
        condLabel.setTextFill(ship.isCrippled() ? Color.rgb(220, 60, 40) : Color.rgb(80, 200, 100));

        // Weapons — rebuild from scratch each update
        updateWeaponsContent(ship);
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    private VBox buildTitleSection() {
        HBox subRow = row(hullLabel, factionLabel);
        VBox box = new VBox(2, nameLabel, subRow);
        box.setPadding(new Insets(4, 6, 6, 6));
        box.setStyle(SECTION_BG);
        return box;
    }

    private VBox buildSection(String title, GridPane grid) {
        Label header = styledLabel(title, HEADER_FONT, DIM_COLOR);
        VBox box = new VBox(4, header, grid);
        box.setPadding(new Insets(5, 6, 6, 6));
        box.setStyle(SECTION_BG);
        return box;
    }

    private GridPane buildLocationGrid() {
        GridPane g = grid();
        addRow(g, 0, "Hex", hexLabel);
        addRow(g, 1, "Facing", facingLabel);
        addRow(g, 2, "Speed", speedLabel);
        return g;
    }

    private GridPane buildShieldGrid() {
        GridPane g = grid();
        String[] names = { "#1 Fore", "#2 FR", "#3 Aft R", "#4 Aft", "#5 Aft L", "#6 FL" };
        for (int i = 0; i < 6; i++) {
            addRow(g, i, names[i], shieldLabels[i]);
        }
        return g;
    }

    private GridPane buildHullGrid() {
        GridPane g = grid();
        addRow(g, 0, "Fwd", fhullLabel);
        addRow(g, 1, "Aft", ahullLabel);
        addRow(g, 2, "Ctr", chullLabel);
        return g;
    }

    private GridPane buildPowerGrid() {
        GridPane g = grid();
        addRow(g, 0, "Warp L+R", lwarpLabel);
        addRow(g, 1, "Warp C", rwarpLabel);
        addRow(g, 2, "Impulse", impulseLabel);
        addRow(g, 3, "APR/AWR", aprLabel);
        addRow(g, 4, "Battery", batteryLabel);
        addRow(g, 5, "TOTAL", totalPwrLabel);
        return g;
    }

    private GridPane buildStatusGrid() {
        GridPane g = grid();
        addRow(g, 0, "", bpvLabel);
        addRow(g, 1, "", condLabel);
        return g;
    }

    private VBox buildWeaponsSection() {
        Label header = styledLabel("WEAPONS", HEADER_FONT, DIM_COLOR);
        VBox box = new VBox(4, header, weaponsContent);
        box.setPadding(new Insets(5, 6, 6, 6));
        box.setStyle(SECTION_BG);
        return box;
    }

    private void updateWeaponsContent(Ship ship) {
        weaponsContent.getChildren().clear();

        List<Weapon> phasers = ship.getWeapons().getPhaserList();
        List<Weapon> torps = ship.getWeapons().getTorpList();

        if (!phasers.isEmpty()) {
            int capEnergy = (int) ship.getWeapons().getPhaserCapacitorEnergy();
            int capMax = (int) ship.getWeapons().getAvailablePhaserCapacitor();
            String capBar = bar(capEnergy, capMax, 8);
            Color capColor = capMax > 0 ? shieldColor(capEnergy, capMax) : DIM_COLOR;
            Label capLabel = styledLabel("-- Phasers  cap " + capEnergy + "/" + capMax + " " + capBar + " --",
                    VALUE_FONT, capColor);
            weaponsContent.getChildren().add(capLabel);
            for (Weapon w : phasers) {
                weaponsContent.getChildren().add(weaponRow(w));
            }
        }

        if (!torps.isEmpty()) {
            weaponsContent.getChildren().add(styledLabel("-- Heavy --", VALUE_FONT, DIM_COLOR));
            for (Weapon w : torps) {
                weaponsContent.getChildren().add(weaponRow(w));
            }
        }

        List<Weapon> drones = ship.getWeapons().getDroneList();
        if (!drones.isEmpty()) {
            weaponsContent.getChildren().add(styledLabel("-- Drones --", VALUE_FONT, DIM_COLOR));
            for (Weapon w : drones) {
                weaponsContent.getChildren().add(droneRackRow((DroneRack) w));
            }
        }

        if (phasers.isEmpty() && torps.isEmpty() && drones.isEmpty()) {
            weaponsContent.getChildren().add(styledLabel("none", VALUE_FONT, DIM_COLOR));
        }
    }

    private HBox weaponRow(Weapon w) {
        String name = w.getName() != null ? w.getName() : "?";

        String statusText;
        Color statusColor;

        if (!w.isFunctional()) {
            statusText = "DESTROYED";
            statusColor = Color.rgb(150, 40, 40);
        } else if (w instanceof HeavyWeapon) {
            HeavyWeapon hw = (HeavyWeapon) w;
            if (hw.isArmed()) {
                statusText = "ARMED";
                statusColor = Color.rgb(40, 210, 90);
            } else {
                int turn = hw.getArmingTurn();
                statusText = turn > 0 ? "arming t" + turn : "unarmed";
                statusColor = Color.rgb(220, 185, 30);
            }
        } else {
            statusText = "ready";
            statusColor = Color.rgb(100, 180, 100);
        }

        Label nameLabel = styledLabel(padRight(name, 10), VALUE_FONT, Color.WHITE);
        Label statusLabel = styledLabel(statusText, VALUE_FONT, statusColor);
        return new HBox(6, nameLabel, statusLabel);
    }

    private HBox droneRackRow(DroneRack rack) {
        String name = rack.getName() != null ? rack.getName() : "DroneRack";
        String status;
        Color statusColor;

        if (!rack.isFunctional()) {
            status = "DESTROYED";
            statusColor = Color.rgb(150, 40, 40);
        } else {
            int loaded = rack.getAmmo().size();
            int spaces = rack.getSpaces();
            int reloads = rack.getNumberOfReloads();
            status = loaded + "/" + spaces + " +" + reloads + "R";
            statusColor = loaded > 0 ? Color.rgb(100, 180, 100) : Color.rgb(180, 130, 30);
        }

        Label nameLabel = styledLabel(padRight(name, 10), VALUE_FONT, Color.WHITE);
        Label statusLabel = styledLabel(status, VALUE_FONT, statusColor);
        return new HBox(6, nameLabel, statusLabel);
    }

    private static String padRight(String s, int width) {
        if (s.length() >= width)
            return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width)
            sb.append(' ');
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void showEmpty() {
        nameLabel.setText("No ship selected");
        nameLabel.setTextFill(DIM_COLOR);
        hullLabel.setText("");
        factionLabel.setText("");
        hexLabel.setText("---");
        facingLabel.setText("---");
        speedLabel.setText("---");
        for (Label l : shieldLabels) {
            l.setText("---");
            l.setTextFill(DIM_COLOR);
        }
        fhullLabel.setText("---");
        ahullLabel.setText("---");
        chullLabel.setText("---");
        lwarpLabel.setText("---");
        rwarpLabel.setText("---");
        impulseLabel.setText("---");
        aprLabel.setText("---");
        batteryLabel.setText("---");
        totalPwrLabel.setText("---");
        bpvLabel.setText("---");
        condLabel.setText("---");
        condLabel.setTextFill(DIM_COLOR);
        weaponsContent.getChildren().clear();
    }

    private GridPane grid() {
        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(2);
        return g;
    }

    private void addRow(GridPane g, int row, String key, Label value) {
        Label keyLabel = styledLabel(key, VALUE_FONT, DIM_COLOR);
        g.add(keyLabel, 0, row);
        g.add(value, 1, row);
    }

    private HBox row(Label... labels) {
        HBox hb = new HBox(6);
        for (Label l : labels)
            hb.getChildren().add(l);
        return hb;
    }

    private static Label styledLabel(String text, Font font, Color color) {
        Label l = new Label(text);
        l.setFont(font);
        l.setTextFill(color);
        return l;
    }

    private static Color factionColor(Faction faction) {
        if (faction == null)
            return DIM_COLOR;
        switch (faction) {
            case Federation:
                return FED_COLOR;
            case Klingon:
                return KLIN_COLOR;
            default:
                return Color.rgb(160, 160, 160);
        }
    }

    private static Color shieldColor(int current, int max) {
        double ratio = (double) current / max;
        if (ratio > 0.66)
            return Color.rgb(40, 210, 90);
        if (ratio > 0.33)
            return Color.rgb(220, 185, 30);
        if (ratio > 0.0)
            return Color.rgb(220, 60, 40);
        return DIM_COLOR;
    }

    /** ASCII mini-bar: e.g. "████░░░░" */
    private static String bar(int current, int max, int width) {
        int filled = max > 0 ? (int) Math.round((double) current / max * width) : 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++)
            sb.append(i < filled ? "\u2588" : "\u2591");
        return sb.toString();
    }
}
