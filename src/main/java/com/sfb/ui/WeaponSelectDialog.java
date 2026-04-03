package com.sfb.ui;

import java.util.ArrayList;
import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.DirectFire;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog for selecting which bearing weapons to fire at a target.
 * Returns the combat log text so the caller can append it to the main log.
 */
public class WeaponSelectDialog extends Stage {

    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 12);
    private static final Font LABEL_FONT  = Font.font("Monospaced", 11);
    private static final String DARK_BG    = "-fx-background-color: #0d0d22;";
    private static final String SECTION_BG = "-fx-background-color: #111130; -fx-background-radius: 4;";
    private static final String BTN_STYLE  =
        "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
        "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
        "-fx-font-size: 11; -fx-cursor: hand;";
    private static final String FIRE_STYLE =
        "-fx-background-color: #4a1a1a; -fx-text-fill: #ff8888; " +
        "-fx-border-color: #883333; -fx-border-radius: 3; -fx-background-radius: 3; " +
        "-fx-font-size: 11; -fx-font-weight: bold; -fx-cursor: hand;";

    private List<Weapon> selectedWeapons = null;  // set when player confirms fire

    public WeaponSelectDialog(Stage owner, Ship attacker, Unit target,
                              List<Weapon> bearingWeapons, int range, int shieldNumber) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Weapons Fire");
        setResizable(false);

        int scannerValue = attacker.getScanner();
        int adjustedRange = range + scannerValue;

        // --- Header ---
        Label title = label(attacker.getName() + "  →  " + target.getName(), HEADER_FONT, Color.WHITE);
        String rangeStr = scannerValue > 0
                ? "Range " + range + " (scanner +" + scannerValue + " = " + adjustedRange + ")"
                : "Range " + range;
        String targetInfo = (target instanceof Ship)
                ? rangeStr + "   Target shield #" + shieldNumber
                : (target instanceof Drone)
                    ? rangeStr + "   Drone hull " + ((Drone) target).getHull()
                    : rangeStr + "   " + target.getName();
        Label sub = label(targetInfo, LABEL_FONT, Color.rgb(150, 150, 180));

        VBox header = new VBox(3, title, sub);
        header.setPadding(new Insets(8, 10, 8, 10));
        header.setStyle(SECTION_BG);

        // --- Capacitor info ---
        double capNow = attacker.getWeapons().getPhaserCapacitorEnergy();
        double capMax = attacker.getWeapons().getAvailablePhaserCapacitor();
        Label capLabel = label(
            String.format("Phaser capacitor:  %.0f / %.0f", capNow, capMax),
            LABEL_FONT, Color.rgb(130, 200, 130));

        // --- Weapon checkboxes ---
        List<CheckBox> checkBoxes = new ArrayList<>();
        VBox weaponList = new VBox(4);
        weaponList.setPadding(new Insets(6, 8, 6, 8));
        weaponList.setStyle(SECTION_BG);

        for (Weapon w : bearingWeapons) {
            if (!(w instanceof DirectFire)) continue;

            String statusText = weaponStatus(w);
            boolean fireable  = isFireable(w, attacker);

            String cbText = String.format("%-14s  %s", w.getName(), statusText);
            CheckBox cb = new CheckBox(cbText);
            cb.setFont(LABEL_FONT);
            cb.setTextFill(fireable ? Color.WHITE : Color.rgb(120, 120, 120));
            cb.setSelected(fireable);
            cb.setDisable(!fireable);
            cb.setUserData(w);
            checkBoxes.add(cb);
            weaponList.getChildren().add(cb);
        }

        if (weaponList.getChildren().isEmpty()) {
            weaponList.getChildren().add(label("No fireable weapons bear on this target.",
                    LABEL_FONT, Color.rgb(180, 120, 120)));
        }

        ScrollPane weaponScroll = new ScrollPane(weaponList);
        weaponScroll.setFitToWidth(true);
        weaponScroll.setPrefHeight(Math.min(checkBoxes.size() * 28 + 20, 220));
        weaponScroll.setStyle("-fx-background-color: #111130; -fx-background: #111130;");

        // --- No-selection warning ---
        Label warningLabel = label("No weapons selected.", LABEL_FONT, Color.rgb(220, 120, 120));
        warningLabel.setVisible(false);
        warningLabel.setManaged(false);
        warningLabel.setPadding(new Insets(0, 10, 0, 10));

        // --- Buttons ---
        Button fireBtn   = new Button("Fire Selected");
        Button cancelBtn = new Button("Cancel");
        fireBtn.setStyle(FIRE_STYLE);
        cancelBtn.setStyle(BTN_STYLE);

        fireBtn.setOnAction(e -> {
            List<Weapon> selected = new ArrayList<>();
            for (CheckBox cb : checkBoxes) {
                if (cb.isSelected()) selected.add((Weapon) cb.getUserData());
            }
            if (selected.isEmpty()) {
                warningLabel.setVisible(true);
                warningLabel.setManaged(true);
                sizeToScene();
                return;
            }
            selectedWeapons = selected;
            close();
        });

        cancelBtn.setOnAction(e -> close());

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, btnSpacer, cancelBtn, fireBtn);
        buttons.setPadding(new Insets(6, 10, 8, 10));

        // --- Root layout ---
        VBox root = new VBox(8, header, capLabel, weaponScroll, warningLabel, buttons);
        root.setPadding(new Insets(10));
        root.setStyle(DARK_BG);
        capLabel.setPadding(new Insets(0, 10, 0, 10));

        Scene scene = new Scene(root, 400, 380);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
    }

    /** Returns the weapons the player confirmed firing, or null if cancelled. */
    public List<Weapon> getSelectedWeapons() {
        return selectedWeapons;
    }

    private static String weaponStatus(Weapon w) {
        if (!w.isFunctional()) return "[DESTROYED]";
        if (w instanceof com.sfb.weapons.ADD) {
            com.sfb.weapons.ADD add = (com.sfb.weapons.ADD) w;
            return "[" + add.getShots() + " shots]";
        }
        if (w instanceof com.sfb.weapons.PhaserG) {
            int fired = w.getShotsThisTurn();
            int remaining = w.getMaxShotsPerTurn() - fired;
            return "[" + remaining + "/" + w.getMaxShotsPerTurn() + " shots]";
        }
        if (w instanceof HeavyWeapon) {
            HeavyWeapon hw = (HeavyWeapon) w;
            if (hw.isArmed()) return "[ARMED]";
            return "[unarmed t" + hw.getArmingTurn() + "]";
        }
        if (!w.canFire()) {
            int remaining = w.getMinImpulseGap()
                    - (com.sfb.TurnTracker.getImpulse() - w.getLastImpulseFired());
            return "[cooldown " + remaining + "]";
        }
        return "[ready]";
    }

    private static boolean isFireable(Weapon w, Ship attacker) {
        if (!w.isFunctional()) return false;
        if (!w.canFire()) return false;
        if (w instanceof HeavyWeapon && !((HeavyWeapon) w).isArmed()) return false;
        if (!(w instanceof com.sfb.weapons.ADD) && !(w instanceof HeavyWeapon)) {
            if (attacker.getWeapons().getPhaserCapacitorEnergy() < w.energyToFire()) return false;
        }
        return true;
    }

    private static Label label(String text, Font font, Color color) {
        Label l = new Label(text);
        l.setFont(font);
        l.setTextFill(color);
        return l;
    }
}
