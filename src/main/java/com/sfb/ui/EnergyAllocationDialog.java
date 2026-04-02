package com.sfb.ui;

import java.util.ArrayList;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

import com.sfb.Game;
import com.sfb.constants.Constants;
import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.properties.WeaponArmingType;
import com.sfb.systems.Energy;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.HeavyWeapon;
import com.sfb.weapons.Weapon;
import javafx.scene.control.CheckBox;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
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
 * Modal dialog for energy allocation at the start of each turn.
 * Shows one ship at a time. Player sets speed, shields, capacitor top-off,
 * and heavy weapon arming. Life support and fire control default to full cost.
 */
public class EnergyAllocationDialog extends Stage {

    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font SECTION_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font LABEL_FONT = Font.font("Monospaced", 11);
    private static final Font SMALL_FONT = Font.font("Monospaced", 10);

    private static final String DARK_BG = "-fx-background-color: #0d0d22;";
    private static final String SECTION_BG = "-fx-background-color: #111130; -fx-background-radius: 4; -fx-padding: 8;";
    private static final String BTN_STYLE = "-fx-background-color: #1a3a1a; -fx-text-fill: #88ff88; " +
            "-fx-border-color: #336633; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-font-weight: bold; -fx-cursor: hand;";

    // Tracks the submitted allocation — null if cancelled (shouldn't happen, no
    // cancel button)
    private Energy submittedAllocation = null;

    public EnergyAllocationDialog(Stage owner, Game game, Ship ship) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Energy Allocation — Turn " + game.getCurrentTurn());
        setResizable(false);

        double totalPower = ship.getPowerSysetems().getTotalAvailablePower();
        double moveCost = ship.getPerformanceData().getMovementCost();
        int warpAvail = ship.getPowerSysetems().getAvailableWarpPower();
        int impAvail = ship.getPowerSysetems().getAvailableImpulse();
        int batCurrent = ship.getPowerSysetems().getBatteryPower();
        int batMax = ship.getPowerSysetems().getAvailableBattery();

        // --- Header ---
        Label shipName = styledLabel(ship.getName() + "  (" + ship.getHullType() + ")", HEADER_FONT, Color.WHITE);
        Label powerSummary = styledLabel(
                String.format("Total power: %.0f    Warp: %d    Impulse: %d",
                        totalPower, warpAvail, impAvail),
                LABEL_FONT, Color.rgb(150, 200, 150));
        Label budgetLabel = styledLabel("Spent: 0 / " + (int) totalPower, LABEL_FONT, Color.rgb(200, 200, 100));

        VBox header = new VBox(4, shipName, powerSummary, budgetLabel);
        header.setStyle(SECTION_BG);

        // --- Batteries ---
        // draw[0]: energy drawn from batteries (adds to budget)
        // recharge[0]: energy put into batteries (costs from budget)
        int[] batDraw = { 0 };
        int[] batRecharge = { 0 };

        RadioButton batDrawBtn = new RadioButton("Draw from batteries");
        RadioButton batRechargeBtn = new RadioButton("Recharge batteries");
        RadioButton batNeitherBtn = new RadioButton("Neither");
        for (RadioButton rb : new RadioButton[] { batDrawBtn, batRechargeBtn, batNeitherBtn }) {
            rb.setFont(LABEL_FONT);
            rb.setTextFill(Color.WHITE);
        }
        ToggleGroup batGroup = new ToggleGroup();
        batDrawBtn.setToggleGroup(batGroup);
        batRechargeBtn.setToggleGroup(batGroup);
        batNeitherBtn.setToggleGroup(batGroup);
        batNeitherBtn.setSelected(true);
        if (batCurrent == 0)
            batDrawBtn.setDisable(true);
        if (batCurrent >= batMax)
            batRechargeBtn.setDisable(true);

        Label batAmountLabel = styledLabel("0", LABEL_FONT, Color.rgb(200, 200, 100));
        Button batMinus = arrowButton("−");
        Button batPlus = arrowButton("+");
        batMinus.setDisable(true);
        batPlus.setDisable(true); // enabled when draw or recharge is selected

        Label batStatusLabel = styledLabel(
                String.format("Charge: %d / %d", batCurrent, batMax),
                SMALL_FONT, Color.rgb(150, 150, 150));

        VBox batteryBox = new VBox(6,
                styledLabel("BATTERIES", SECTION_FONT, Color.rgb(220, 200, 80)),
                batStatusLabel,
                new HBox(12, batDrawBtn, batRechargeBtn, batNeitherBtn),
                new HBox(6, batMinus, batPlus, batAmountLabel));
        batteryBox.setStyle(SECTION_BG);

        // --- Movement ---
        double maxWarpEnergy = Math.min(warpAvail, 30.0 * moveCost);
        int maxWarpSpeed = (int) (maxWarpEnergy / moveCost);

        Slider speedSlider = new Slider(0, maxWarpSpeed, maxWarpSpeed);
        speedSlider.setMajorTickUnit(5);
        speedSlider.setMinorTickCount(4);
        speedSlider.setSnapToTicks(true);
        speedSlider.setShowTickLabels(true);
        speedSlider.setShowTickMarks(true);
        speedSlider.setStyle("-fx-control-inner-background: #0d0d22;");

        RadioButton impYes = new RadioButton("+1 impulse (speed +1)");
        RadioButton impNo = new RadioButton("No impulse");
        impYes.setFont(LABEL_FONT);
        impYes.setTextFill(Color.WHITE);
        impNo.setFont(LABEL_FONT);
        impNo.setTextFill(Color.rgb(150, 150, 150));
        ToggleGroup impGroup = new ToggleGroup();
        impYes.setToggleGroup(impGroup);
        impNo.setToggleGroup(impGroup);
        if (impAvail >= 1) {
            impNo.setSelected(true);
        } else {
            impNo.setSelected(true);
            impYes.setDisable(true);
        }

        Label speedLabel = styledLabel("Warp speed: " + maxWarpSpeed, LABEL_FONT, Color.rgb(100, 180, 255));

        VBox movementBox = new VBox(6,
                styledLabel("MOVEMENT", SECTION_FONT, Color.rgb(100, 180, 255)),
                speedLabel, speedSlider, new HBox(12, impYes, impNo));
        movementBox.setStyle(SECTION_BG);

        // --- Shields ---
        int activeCost = ship.getActiveShieldCost();
        double minCost = ship.getMinimumShieldCost();

        RadioButton shActive = new RadioButton("Active  (cost " + activeCost + ")");
        RadioButton shMinimum = new RadioButton("Minimum  (cost " + minCost + ")");
        RadioButton shOff = new RadioButton("Off  (cost 0)");
        for (RadioButton rb : new RadioButton[] { shActive, shMinimum, shOff }) {
            rb.setFont(LABEL_FONT);
            rb.setTextFill(Color.WHITE);
        }
        ToggleGroup shGroup = new ToggleGroup();
        shActive.setToggleGroup(shGroup);
        shMinimum.setToggleGroup(shGroup);
        shOff.setToggleGroup(shGroup);
        shActive.setSelected(true);

        VBox shieldsBox = new VBox(6,
                styledLabel("SHIELDS", SECTION_FONT, Color.rgb(100, 200, 200)),
                new HBox(16, shActive, shMinimum, shOff));
        shieldsBox.setStyle(SECTION_BG);

        // --- Phaser capacitor ---
        double capCurrent = ship.getWeapons().getPhaserCapacitorEnergy();
        double capMax = ship.getWeapons().getAvailablePhaserCapacitor();
        double capNeeded = Math.max(0, capMax - capCurrent);

        RadioButton capTopOff = new RadioButton(String.format("Top off  (cost %.0f)", capNeeded));
        RadioButton capSkip = new RadioButton("Skip  (cost 0)");
        capTopOff.setFont(LABEL_FONT);
        capTopOff.setTextFill(Color.WHITE);
        capSkip.setFont(LABEL_FONT);
        capSkip.setTextFill(Color.rgb(150, 150, 150));
        ToggleGroup capGroup = new ToggleGroup();
        capTopOff.setToggleGroup(capGroup);
        capSkip.setToggleGroup(capGroup);
        capTopOff.setSelected(capNeeded > 0);
        capSkip.setSelected(capNeeded == 0);
        if (capNeeded == 0)
            capTopOff.setDisable(true);

        Label capStatus = styledLabel(
                String.format("Current: %.0f / %.0f", capCurrent, capMax), SMALL_FONT, Color.rgb(150, 150, 150));

        VBox capBox = new VBox(6,
                styledLabel("PHASER CAPACITOR", SECTION_FONT, Color.rgb(200, 180, 100)),
                capStatus, new HBox(16, capTopOff, capSkip));
        capBox.setStyle(SECTION_BG);

        // --- Heavy weapons ---
        List<HeavyWeapon> heavyWeapons = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof HeavyWeapon && w.isFunctional())
                heavyWeapons.add((HeavyWeapon) w);
        }

        List<ToggleGroup> weaponGroups = new ArrayList<>();
        VBox weaponsBox = new VBox(6);
        weaponsBox.setStyle(SECTION_BG);
        weaponsBox.getChildren().add(styledLabel("HEAVY WEAPONS", SECTION_FONT, Color.rgb(255, 160, 80)));

        if (heavyWeapons.isEmpty()) {
            weaponsBox.getChildren().add(styledLabel("none", LABEL_FONT, Color.rgb(100, 100, 100)));
        } else {
            GridPane grid = new GridPane();
            grid.setHgap(16);
            grid.setVgap(6);
            int row = 0;
            for (HeavyWeapon hw : heavyWeapons) {
                int stdCost = hw.energyToArm(); // weapon reports its own standard cost
                int overCost = stdCost * 2; // overload always costs double standard
                Label wName = styledLabel(((Weapon) hw).getName(), LABEL_FONT, Color.WHITE);
                RadioButton arm = new RadioButton("Arm standard  (cost " + stdCost + ")");
                RadioButton over = new RadioButton("Overload  (cost " + overCost + ")");
                RadioButton skip = new RadioButton("Don't arm");
                arm.setFont(LABEL_FONT);
                arm.setTextFill(Color.rgb(100, 220, 100));
                over.setFont(LABEL_FONT);
                over.setTextFill(Color.rgb(255, 160, 80));
                skip.setFont(LABEL_FONT);
                skip.setTextFill(Color.rgb(150, 150, 150));
                ToggleGroup wg = new ToggleGroup();
                arm.setToggleGroup(wg);
                over.setToggleGroup(wg);
                skip.setToggleGroup(wg);
                arm.setSelected(true);
                weaponGroups.add(wg);
                grid.add(wName, 0, row);
                grid.add(new HBox(12, arm, over, skip), 1, row);
                row++;
            }
            weaponsBox.getChildren().add(grid);
        }

        // --- Shield reinforcement (variables declared here; actions wired after
        // refresh) ---
        int[] shieldMax = new int[6];
        for (int i = 0; i < 6; i++)
            shieldMax[i] = ship.getShields().getShieldStrength(i + 1);

        int[] generalReinf = { 0 };
        Label genValueLabel = styledLabel("0 energy  (+0 pts to all shields)", LABEL_FONT, Color.rgb(100, 200, 200));
        Button genMinus = arrowButton("−");
        Button genPlus = arrowButton("+");
        genMinus.setDisable(true);

        int[] specificReinf = new int[6];
        Label[] specLabels = new Label[6];
        Button[] specMinus = new Button[6];
        Button[] specPlus = new Button[6];
        GridPane specGrid = new GridPane();
        specGrid.setHgap(8);
        specGrid.setVgap(4);
        String[] shieldNames = { "#1 Fore", "#2 RF", "#3 RR", "#4 Aft", "#5 LR", "#6 LF" };
        for (int i = 0; i < 6; i++) {
            specLabels[i] = styledLabel(
                    String.format("%-8s  %2d pts  (max %d)", shieldNames[i], 0, shieldMax[i]),
                    SMALL_FONT, Color.rgb(150, 200, 200));
            specMinus[i] = arrowButton("−");
            specPlus[i] = arrowButton("+");
            specMinus[i].setDisable(true);
            specGrid.add(new HBox(4, specMinus[i], specPlus[i], specLabels[i]), 0, i);
        }

        VBox reinforceBox = new VBox(6,
                styledLabel("SHIELD REINFORCEMENT", SECTION_FONT, Color.rgb(100, 220, 220)),
                styledLabel("General (2 energy = +1 pt to all shields):", SMALL_FONT, Color.rgb(150, 150, 150)),
                new HBox(6, genMinus, genPlus, genValueLabel),
                styledLabel("Specific (1 energy = +1 pt to one shield):", SMALL_FONT, Color.rgb(150, 150, 150)),
                specGrid);
        reinforceBox.setStyle(SECTION_BG);

        // --- Drone reloads ---
        int deckCrews = ship.getCrew().getDeckCrews();
        List<DroneRack> reloadableRacks = new ArrayList<>();
        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (w instanceof DroneRack) {
                DroneRack rack = (DroneRack) w;
                if (rack.isFunctional() && !rack.getReloads().isEmpty())
                    reloadableRacks.add(rack);
            }
        }

        // One checkbox per rack — selecting it assigns the first available reload set
        Map<DroneRack, CheckBox> reloadChecks = new HashMap<>();
        VBox reloadBox = new VBox(6);
        reloadBox.setStyle(SECTION_BG);
        reloadBox.getChildren().add(styledLabel("DRONE RELOADS", SECTION_FONT, Color.rgb(180, 140, 255)));
        Label deckCrewLabel = styledLabel("Deck crews: " + deckCrews + " available", SMALL_FONT,
                Color.rgb(150, 150, 150));
        reloadBox.getChildren().add(deckCrewLabel);

        if (reloadableRacks.isEmpty()) {
            reloadBox.getChildren().add(styledLabel("No reloads available", LABEL_FONT, Color.rgb(100, 100, 100)));
        } else {
            for (DroneRack rack : reloadableRacks) {
                List<Drone> nextSet = rack.getReloads().get(0);
                double cost = DroneRack.reloadCost(nextSet);
                String setDesc = describeReloadSet(nextSet);
                String label = rack.getName() + "  →  " + setDesc
                        + "  (deck crew: " + (int) cost + ")"
                        + "  [" + rack.getReloads().size() + " reload(s) left]";
                CheckBox cb = new CheckBox(label);
                cb.setFont(LABEL_FONT);
                cb.setTextFill(Color.WHITE);
                reloadChecks.put(rack, cb);
                reloadBox.getChildren().add(cb);
            }
        }

        // --- Submit button (declared here so refresh lambda can reference it) ---
        Button submitBtn = new Button("Confirm Allocation");

        // --- Attach all listeners now that every variable is in scope ---
        Runnable refresh = () -> updateBudget(budgetLabel, ship, speedSlider, impYes,
                shGroup, shActive, shMinimum, capGroup, capTopOff, capNeeded,
                heavyWeapons, weaponGroups, reloadableRacks, reloadChecks, deckCrews,
                generalReinf, specificReinf, batDraw, batRecharge, submitBtn);

        // Wire battery button actions now that refresh is in scope
        batGroup.selectedToggleProperty().addListener((obs, old, val) -> {
            batDraw[0] = 0;
            batRecharge[0] = 0;
            batAmountLabel.setText("0");
            batMinus.setDisable(true);
            boolean drawSelected = batGroup.getSelectedToggle() == batDrawBtn;
            boolean rechargeSelected = batGroup.getSelectedToggle() == batRechargeBtn;
            batPlus.setDisable(!drawSelected && !rechargeSelected);
            refresh.run();
        });
        batPlus.setOnAction(e2 -> {
            boolean drawing = batGroup.getSelectedToggle() == batDrawBtn;
            if (drawing && batDraw[0] < batCurrent) {
                batDraw[0]++;
                batAmountLabel.setText(batDraw[0] + " (budget +" + batDraw[0] + ")");
                batMinus.setDisable(false);
            } else if (!drawing && batRecharge[0] < (batMax - batCurrent)) {
                batRecharge[0]++;
                batAmountLabel.setText(batRecharge[0] + " (cost " + batRecharge[0] + ")");
                batMinus.setDisable(false);
            }
            refresh.run();
        });
        batMinus.setOnAction(e2 -> {
            boolean drawing = batGroup.getSelectedToggle() == batDrawBtn;
            if (drawing && batDraw[0] > 0) {
                batDraw[0]--;
                batAmountLabel.setText(batDraw[0] == 0 ? "0" : batDraw[0] + " (budget +" + batDraw[0] + ")");
                batMinus.setDisable(batDraw[0] == 0);
            } else if (!drawing && batRecharge[0] > 0) {
                batRecharge[0]--;
                batAmountLabel.setText(batRecharge[0] == 0 ? "0" : batRecharge[0] + " (cost " + batRecharge[0] + ")");
                batMinus.setDisable(batRecharge[0] == 0);
            }
            refresh.run();
        });

        // Wire reinforcement button actions now that refresh is in scope
        genMinus.setOnAction(e2 -> {
            if (generalReinf[0] >= 2) {
                generalReinf[0] -= 2;
                genValueLabel.setText(generalReinf[0] + " energy  (+" + (generalReinf[0] / 2) + " pts to all shields)");
                genMinus.setDisable(generalReinf[0] == 0);
                refresh.run();
            }
        });
        genPlus.setOnAction(e2 -> {
            generalReinf[0] += 2;
            genValueLabel.setText(generalReinf[0] + " energy  (+" + (generalReinf[0] / 2) + " pts to all shields)");
            genMinus.setDisable(false);
            refresh.run();
        });
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            specMinus[idx].setOnAction(e2 -> {
                if (specificReinf[idx] > 0) {
                    specificReinf[idx]--;
                    specLabels[idx].setText(String.format("%-8s  %2d pts  (max %d)",
                            shieldNames[idx], specificReinf[idx], shieldMax[idx]));
                    specMinus[idx].setDisable(specificReinf[idx] == 0);
                    refresh.run();
                }
            });
            specPlus[idx].setOnAction(e2 -> {
                if (specificReinf[idx] < shieldMax[idx]) {
                    specificReinf[idx]++;
                    specLabels[idx].setText(String.format("%-8s  %2d pts  (max %d)",
                            shieldNames[idx], specificReinf[idx], shieldMax[idx]));
                    specMinus[idx].setDisable(false);
                    refresh.run();
                }
            });
        }

        for (CheckBox cb : reloadChecks.values()) {
            cb.selectedProperty().addListener((obs, old, val) -> refresh.run());
        }

        speedSlider.valueProperty().addListener((obs, old, val) -> {
            speedLabel.setText("Warp speed: " + (int) Math.round(val.doubleValue()));
            refresh.run();
        });
        impGroup.selectedToggleProperty().addListener((obs, old, val) -> refresh.run());
        shGroup.selectedToggleProperty().addListener((obs, old, val) -> refresh.run());
        capGroup.selectedToggleProperty().addListener((obs, old, val) -> refresh.run());
        for (ToggleGroup wg : weaponGroups) {
            wg.selectedToggleProperty().addListener((obs, old, val) -> refresh.run());
        }
        submitBtn.setStyle(BTN_STYLE);
        submitBtn.setOnAction(e -> {
            submittedAllocation = buildAllocation(
                    ship, speedSlider, impYes, impGroup,
                    shGroup, shActive, shMinimum,
                    capGroup, capTopOff, capNeeded,
                    heavyWeapons, weaponGroups,
                    reloadableRacks, reloadChecks,
                    generalReinf, specificReinf,
                    batDraw, batRecharge);
            game.submitAllocation(ship, submittedAllocation);
            close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(spacer, submitBtn);
        buttonRow.setPadding(new Insets(4, 0, 0, 0));

        // Initial budget display
        refresh.run();

        VBox root = new VBox(10, header, batteryBox, movementBox, shieldsBox, reinforceBox, capBox, weaponsBox,
                reloadBox, buttonRow);
        root.setPadding(new Insets(12));
        root.setStyle(DARK_BG);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #0d0d22; -fx-background: #0d0d22;");

        Scene scene = new Scene(scroll, 560, 580);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
    }

    // -------------------------------------------------------------------------

    private Energy buildAllocation(Ship ship, Slider speedSlider, RadioButton impYes,
            ToggleGroup impGroup, ToggleGroup shGroup, RadioButton shActive,
            RadioButton shMinimum, ToggleGroup capGroup, RadioButton capTopOff,
            double capNeeded, List<HeavyWeapon> heavyWeapons, List<ToggleGroup> weaponGroups,
            List<DroneRack> reloadableRacks, Map<DroneRack, CheckBox> reloadChecks,
            int[] generalReinf, int[] specificReinf, int[] batDraw, int[] batRecharge) {

        Energy e = new Energy();
        double moveCost = ship.getPerformanceData().getMovementCost();

        // Movement
        int warpSpeed = (int) Math.round(speedSlider.getValue());
        e.setWarpMovement(warpSpeed * moveCost);
        e.setImpulseMovement(impYes.isSelected() ? 1 : 0);

        // Crippled ships get life support for free; others must pay
        e.setLifeSupport(ship.isCrippled() ? 0 : ship.getLifeSupportCost());
        e.setFireControl(ship.getFireControlCost());

        // Shields
        if (shGroup.getSelectedToggle() == shActive) {
            e.setActivateShields(ship.getActiveShieldCost());
        } else if (shGroup.getSelectedToggle() == shMinimum) {
            e.setActivateShields(ship.getMinimumShieldCost());
        } else {
            e.setActivateShields(0);
        }

        // Phaser capacitor
        if (capGroup.getSelectedToggle() == capTopOff) {
            e.setPhaserCapacitor(capNeeded);
        }

        // Heavy weapons
        for (int i = 0; i < heavyWeapons.size(); i++) {
            HeavyWeapon hw = heavyWeapons.get(i);
            RadioButton selected = (RadioButton) weaponGroups.get(i).getSelectedToggle();
            String txt = selected.getText();
            if (txt.startsWith("Arm standard")) {
                e.getArmingEnergy().put((Weapon) hw, (double) Constants.gArmingCost[0]);
                e.getArmingType().put((Weapon) hw, WeaponArmingType.STANDARD);
            } else if (txt.startsWith("Overload")) {
                e.getArmingEnergy().put((Weapon) hw, (double) (Constants.gArmingCost[0] + 1));
                e.getArmingType().put((Weapon) hw, WeaponArmingType.OVERLOAD);
            }
            // "Don't arm" — weapon not added to maps, applyAllocationEnergy won't be called
        }

        // Drone reloads
        for (DroneRack rack : reloadableRacks) {
            CheckBox cb = reloadChecks.get(rack);
            if (cb != null && cb.isSelected()) {
                e.getReloadAssignments().put(rack, rack.getReloads().get(0));
            }
        }

        // Shield reinforcement
        e.setGeneralReinforcement(generalReinf[0]);
        e.setSpecificReinforcement(specificReinf.clone());

        // Batteries
        e.setBatteryDraw(batDraw[0]);
        e.setBatteryRecharge(batRecharge[0]);

        return e;
    }

    private void updateBudget(Label budgetLabel, Ship ship, Slider speedSlider,
            RadioButton impYes,
            ToggleGroup shGroup, RadioButton shActive, RadioButton shMinimum,
            ToggleGroup capGroup, RadioButton capTopOff, double capNeeded,
            List<HeavyWeapon> heavyWeapons, List<ToggleGroup> weaponGroups,
            List<DroneRack> reloadableRacks, Map<DroneRack, CheckBox> reloadChecks,
            int totalDeckCrews, int[] generalReinf, int[] specificReinf,
            int[] batDraw, int[] batRecharge, Button submitBtn) {

        double moveCost = ship.getPerformanceData().getMovementCost();
        int warpSpeed = (int) Math.round(speedSlider.getValue());
        double spent = warpSpeed * moveCost;

        spent += ship.isCrippled() ? 0 : ship.getLifeSupportCost();
        spent += ship.getFireControlCost();
        if (impYes.isSelected())
            spent += 1;

        // Shields
        if (shGroup.getSelectedToggle() == shActive) {
            spent += ship.getActiveShieldCost();
        } else if (shGroup.getSelectedToggle() == shMinimum) {
            spent += ship.getMinimumShieldCost();
        }

        // Phaser capacitor
        if (capGroup.getSelectedToggle() == capTopOff) {
            spent += capNeeded;
        }

        // Heavy weapons
        for (int i = 0; i < heavyWeapons.size(); i++) {
            RadioButton sel = (RadioButton) weaponGroups.get(i).getSelectedToggle();
            if (sel != null) {
                String txt = sel.getText();
                if (txt.startsWith("Arm standard")) {
                    spent += heavyWeapons.get(i).energyToArm();
                } else if (txt.startsWith("Overload")) {
                    spent += heavyWeapons.get(i).energyToArm() * 2;
                }
            }
        }

        // Drone reloads — track deck crew usage
        double deckCrewUsed = 0;
        for (DroneRack rack : reloadableRacks) {
            CheckBox cb = reloadChecks.get(rack);
            if (cb != null && cb.isSelected()) {
                deckCrewUsed += DroneRack.reloadCost(rack.getReloads().get(0));
            }
        }

        // Reinforcement
        spent += generalReinf[0];
        for (int sr : specificReinf)
            spent += sr;

        // Batteries: draw expands the budget, recharge costs from it
        spent += batRecharge[0];
        double total = ship.getPowerSysetems().getTotalAvailablePower() + batDraw[0];
        boolean overEnergy = spent > total;
        boolean overDeckCrews = deckCrewUsed > totalDeckCrews;
        budgetLabel.setText(String.format("Spent: %.1f / %.0f    Deck crews: %.0f / %d",
                spent, total, deckCrewUsed, totalDeckCrews));
        budgetLabel.setTextFill((overEnergy || overDeckCrews) ? Color.rgb(255, 80, 80) : Color.rgb(200, 200, 100));
        submitBtn.setDisable(overEnergy || overDeckCrews);
    }

    /** Produces a compact description of a reload set, e.g. "2×TypeI 1×TypeIV". */
    private static String describeReloadSet(List<Drone> set) {
        Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Drone d : set) {
            String key = d.getDroneType().toString();
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(entry.getValue()).append("×").append(entry.getKey());
        }
        return sb.toString();
    }

    private static Label styledLabel(String text, Font font, Color color) {
        Label l = new Label(text);
        l.setFont(font);
        l.setTextFill(color);
        return l;
    }

    private static Button arrowButton(String symbol) {
        Button b = new Button(symbol);
        b.setFont(Font.font("Monospaced", FontWeight.BOLD, 11));
        b.setStyle(
                "-fx-background-color: #1a2a3a; -fx-text-fill: #88bbff; " +
                        "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
                        "-fx-min-width: 24; -fx-min-height: 22; -fx-cursor: hand;");
        return b;
    }
}
