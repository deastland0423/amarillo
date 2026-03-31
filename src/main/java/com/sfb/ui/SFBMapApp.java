package com.sfb.ui;

import java.util.List;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.objects.Ship;
import com.sfb.weapons.Weapon;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Pure view layer for the Star Fleet Battles map.
 * Reads state from Game and sends actions to Game — does not mutate ship
 * state directly. This keeps the door open for multiplayer: actions will
 * eventually be sent over a network rather than applied in-process.
 *
 * Run with: mvn exec:java
 */
public class SFBMapApp extends Application {

    private static final int MAP_COLS = 32;
    private static final int MAP_ROWS = 40;

    private final Game game = new Game();

    private HexMapCanvas  mapCanvas;
    private ShipInfoPanel infoPanel;
    private Label         turnLabel;
    private Label         selectedLabel;
    private Label         statusLabel;
    private TextArea      combatLog;
    private boolean       firingMode = false;

    @Override
    public void start(Stage primaryStage) {
        game.setup();

        mapCanvas = new HexMapCanvas(MAP_COLS, MAP_ROWS, game.getShips());
        infoPanel = new ShipInfoPanel();

        ScrollPane scrollPane = new ScrollPane(mapCanvas);
        scrollPane.setPannable(true);
        scrollPane.setStyle("-fx-background-color: #0a0a1e; -fx-background: #0a0a1e;");

        // --- Toolbar ---
        turnLabel = new Label(turnText());
        turnLabel.setStyle("-fx-text-fill: #aaddff; -fx-font-size: 13; -fx-font-weight: bold; -fx-min-width: 180;");

        selectedLabel = new Label("No ship selected");
        selectedLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12; -fx-min-width: 220;");

        statusLabel = new Label("Click a ship to select it");
        statusLabel.setStyle("-fx-text-fill: #889966; -fx-font-size: 11;");

        Button nextImpulseBtn = new Button("Next Impulse  ▶");
        nextImpulseBtn.setStyle(
            "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
            "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-cursor: hand;");
        nextImpulseBtn.setOnAction(e -> advanceImpulse());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label keyHelp = new Label("W=fwd  A/D=turn  Q/E=slip  F=fire");
        keyHelp.setStyle("-fx-text-fill: #445566; -fx-font-size: 10;");

        HBox toolbar = new HBox(16, turnLabel, selectedLabel, statusLabel, spacer, keyHelp, nextImpulseBtn);
        toolbar.setStyle("-fx-padding: 8 14; -fx-background-color: #0d0d22; -fx-alignment: center-left;");

        ScrollPane infoScroll = new ScrollPane(infoPanel);
        infoScroll.setFitToWidth(true);
        infoScroll.setStyle("-fx-background-color: #0c0c20; -fx-background: #0c0c20;");

        combatLog = new TextArea();
        combatLog.setEditable(false);
        combatLog.setWrapText(true);
        combatLog.setPrefHeight(110);
        combatLog.setFont(javafx.scene.text.Font.font("Monospaced", 10));
        combatLog.setStyle(
            "-fx-control-inner-background: #060614; -fx-text-fill: #88cc88; " +
            "-fx-border-color: #1a2a3a;");
        Label logLabel = new Label("COMBAT LOG");
        logLabel.setStyle("-fx-text-fill: #445566; -fx-font-size: 10; -fx-font-family: Monospaced; -fx-padding: 2 0 0 4;");
        VBox logBox = new VBox(2, logLabel, combatLog);
        logBox.setStyle("-fx-background-color: #080818; -fx-padding: 4 6 4 6;");

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);
        root.setRight(infoScroll);
        root.setBottom(logBox);

        Scene scene = new Scene(root, 1200, 820);
        scene.setFill(Color.rgb(10, 10, 30));

        // --- Mouse: click to select or target ---
        mapCanvas.setOnMouseClicked(event -> {
            Ship hit = mapCanvas.hitTestShip(event.getX(), event.getY());
            if (firingMode) {
                Ship attacker = mapCanvas.getSelectedShip();
                if (hit == null) {
                    setStatus("No target — click a ship or press Escape to cancel");
                } else if (hit == attacker) {
                    setStatus("Can't target yourself — click an enemy or press Escape");
                } else {
                    resolveWeaponsFire(attacker, hit);
                    exitFiringMode();
                }
            } else {
                mapCanvas.setSelectedShip(hit);
                if (hit != null) {
                    selectedLabel.setText(hit.getName() + " (" + hit.getHullType() + ")  spd " + hit.getSpeed());
                    statusLabel.setText("Selected  —  press F to fire");
                } else {
                    selectedLabel.setText("No ship selected");
                    statusLabel.setText("");
                }
                infoPanel.update(hit);
            }
            mapCanvas.render();
            scene.getRoot().requestFocus();
        });

        // --- Keyboard ---
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (firingMode) exitFiringMode();
                return;
            }
            if (event.getCode() == KeyCode.F) {
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                firingMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("FIRE MODE — click a target  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (firingMode) return;

            Ship ship = mapCanvas.getSelectedShip();
            if (ship == null) { setStatus("No ship selected"); return; }

            handleMovementKey(event.getCode(), ship);
        });

        refreshMovableShips();

        primaryStage.setTitle("Star Fleet Battles");
        primaryStage.setScene(scene);
        primaryStage.show();
        scene.getRoot().requestFocus();

        scrollPane.setHvalue(0.3);
        scrollPane.setVvalue(0.0);
    }

    // -------------------------------------------------------------------------
    // Impulse management
    // -------------------------------------------------------------------------

    private void advanceImpulse() {
        game.advanceImpulse();
        refreshMovableShips();
        turnLabel.setText(turnText());
        setStatus("Impulse " + game.getCurrentImpulse() + " — ships with yellow ring may move");
        mapCanvas.render();
    }

    private void refreshMovableShips() {
        mapCanvas.setMovableShips(game.getMovableShips());
    }

    private String turnText() {
        return "Turn " + game.getCurrentTurn() + "  |  Impulse " + game.getCurrentImpulse() + " / 32";
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    private void handleMovementKey(KeyCode code, Ship ship) {
        ActionResult result;

        switch (code) {
            case W: result = game.moveForward(ship);   break;
            case A: result = game.turnLeft(ship);      break;
            case D: result = game.turnRight(ship);     break;
            case Q: result = game.sideslipLeft(ship);  break;
            case E: result = game.sideslipRight(ship); break;
            default: return;
        }

        setStatus(result.getMessage());
        if (result.isSuccess()) {
            selectedLabel.setText(ship.getName() + " (" + ship.getHullType() + ")  spd " + ship.getSpeed()
                    + "  @ " + ship.getLocation());
            refreshMovableShips();
        }
        infoPanel.update(ship);
        mapCanvas.render();
    }

    // -------------------------------------------------------------------------
    // Weapons fire
    // -------------------------------------------------------------------------

    private void exitFiringMode() {
        firingMode = false;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  press F to fire" : "");
        mapCanvas.render();
    }

    private void resolveWeaponsFire(Ship attacker, Ship target) {
        int range = game.getRange(attacker, target);
        List<Weapon> bearing = game.getBearingWeapons(attacker, target);

        if (bearing.isEmpty()) {
            setStatus("No weapons bear on " + target.getName() + " at range " + range);
            infoPanel.update(attacker);
            return;
        }

        int shieldNumber = game.getShieldNumber(attacker, target);

        WeaponSelectDialog dialog = new WeaponSelectDialog(
                (Stage) mapCanvas.getScene().getWindow(),
                attacker, target, bearing, range, shieldNumber);
        dialog.showAndWait();

        String entry = dialog.getCombatLogEntry();
        if (entry != null) {
            combatLog.appendText(entry + "\n");
            setStatus("Fired — see combat log");
            selectedLabel.setText(target.getName() + " (" + target.getHullType() + ")  — damage taken");
            infoPanel.update(target);
        } else {
            setStatus("Fire cancelled");
            infoPanel.update(attacker);
        }
        mapCanvas.render();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
