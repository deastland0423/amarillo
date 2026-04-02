package com.sfb.ui;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game;
import com.sfb.Game.FireResult;
import com.sfb.exceptions.CapacitorException;
import com.sfb.exceptions.TargetOutOfRangeException;
import com.sfb.exceptions.WeaponUnarmedException;
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
import javafx.scene.control.TextArea;
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
    private static final Font RESULT_FONT = Font.font("Monospaced", 10);

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

    private String combatLogEntry = null;  // set after firing

    public WeaponSelectDialog(Stage owner, Game game, Ship attacker, Unit target,
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
                : rangeStr + "   Drone hull " + ((Drone) target).getHull();
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

        // --- Results area (hidden until fired) ---
        TextArea resultsArea = new TextArea();
        resultsArea.setFont(RESULT_FONT);
        resultsArea.setEditable(false);
        resultsArea.setWrapText(true);
        resultsArea.setPrefHeight(100);
        resultsArea.setStyle(
            "-fx-control-inner-background: #080818; -fx-text-fill: #aaffaa; " +
            "-fx-border-color: #223344;");
        resultsArea.setVisible(false);
        resultsArea.setManaged(false);

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
                resultsArea.setText("No weapons selected.");
                showResults(resultsArea);
                return;
            }

            StringBuilder log = new StringBuilder();
            log.append(attacker.getName()).append("  →  ").append(target.getName())
               .append("   range ").append(range)
               .append("   shield #").append(shieldNumber).append("\n");

            int totalDamage = 0;
            boolean addHit = false;
            for (Weapon w : selected) {
                try {
                    int dmg = ((DirectFire) w).fire(range, adjustedRange);
                    if (dmg == com.sfb.weapons.ADD.HIT) {
                        addHit = true;
                        log.append("  ").append(w.getName()).append("  HIT\n");
                    } else {
                        totalDamage += dmg;
                        log.append("  ").append(w.getName())
                           .append(dmg > 0 ? "  HIT  " + dmg : "  MISS").append("\n");
                    }
                } catch (WeaponUnarmedException ex) {
                    log.append("  ").append(w.getName()).append("  unarmed\n");
                } catch (TargetOutOfRangeException ex) {
                    log.append("  ").append(w.getName()).append("  out of range\n");
                } catch (CapacitorException ex) {
                    log.append("  ").append(w.getName()).append("  no capacitor energy\n");
                }
            }

            if (addHit) {
                String dmgLog = game.applyDamageToUnit(com.sfb.weapons.ADD.HIT, target, shieldNumber);
                log.append("  ADD result: ").append(dmgLog).append("\n");
            }
            log.append("  Total damage: ").append(totalDamage);
            if (target instanceof Ship) {
                FireResult result = game.markShieldDamage((Ship) target, shieldNumber, totalDamage);
                if (result.getBleed() > 0) {
                    log.append("   BLEED-THROUGH: ").append(result.getBleed())
                       .append(" (internal damage resolves at end of Direct-Fire segment)\n");
                }
            } else if (totalDamage > 0) {
                String dmgLog = game.applyDamageToUnit(totalDamage, target, shieldNumber);
                log.append("   ").append(dmgLog).append("\n");
            }

            combatLogEntry = log.toString();
            resultsArea.setText(log.toString());
            showResults(resultsArea);
            fireBtn.setDisable(true);
        });

        cancelBtn.setOnAction(e -> close());

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, btnSpacer, cancelBtn, fireBtn);
        buttons.setPadding(new Insets(6, 10, 8, 10));

        // --- Root layout ---
        VBox root = new VBox(8, header, capLabel, weaponScroll, resultsArea, buttons);
        root.setPadding(new Insets(10));
        root.setStyle(DARK_BG);
        capLabel.setPadding(new Insets(0, 10, 0, 10));

        Scene scene = new Scene(root, 400, 380);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
    }

    /** Returns the combat log text after firing, or null if cancelled. */
    public String getCombatLogEntry() {
        return combatLogEntry;
    }

    private void showResults(TextArea area) {
        area.setVisible(true);
        area.setManaged(true);
        sizeToScene();
    }

    private static String weaponStatus(Weapon w) {
        if (!w.isFunctional()) return "[DESTROYED]";
        if (w instanceof com.sfb.weapons.ADD) {
            com.sfb.weapons.ADD add = (com.sfb.weapons.ADD) w;
            return "[" + add.getShots() + " shots]";
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
