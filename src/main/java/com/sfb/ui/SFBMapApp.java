package com.sfb.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sfb.Player;
import com.sfb.TurnTracker;
import com.sfb.constants.Constants;
import com.sfb.objects.Ship;
import com.sfb.properties.Faction;
import com.sfb.properties.Location;
import com.sfb.samples.SampleShips;
import com.sfb.utilities.MapUtils;
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
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * JavaFX entry point for the Star Fleet Battles map viewer.
 *
 * Keyboard controls (with a ship selected):
 *   W / Up    - Move forward
 *   S / Down  - Move backward
 *   Q / Left  - Turn left
 *   E / Right - Turn right
 *   A         - Sideslip left
 *   D         - Sideslip right
 *
 * Run with: mvn exec:java
 */
public class SFBMapApp extends Application {

    private static final int MAP_COLS = 32;
    private static final int MAP_ROWS = 40;

    private final List<Ship> ships = new ArrayList<>();
    private final Set<Ship> movedThisImpulse = new HashSet<>();
    private HexMapCanvas mapCanvas;
    private ShipInfoPanel infoPanel;
    private Label turnLabel;
    private Label selectedLabel;
    private Label statusLabel;
    private boolean firingMode = false;
    private TextArea combatLog;

    @Override
    public void start(Stage primaryStage) {
        setupShips();

        // Advance to impulse 1 so the game is in a valid starting state
        TurnTracker.nextImpulse();

        mapCanvas = new HexMapCanvas(MAP_COLS, MAP_ROWS, ships);
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
        javafx.scene.layout.VBox logBox = new javafx.scene.layout.VBox(2, logLabel, combatLog);
        logBox.setStyle("-fx-background-color: #080818; -fx-padding: 4 6 4 6;");

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);
        root.setRight(infoScroll);
        root.setBottom(logBox);

        Scene scene = new Scene(root, 1200, 820);
        scene.setFill(Color.rgb(10, 10, 30));

        // --- Mouse: click to select or target a ship ---
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
            if (firingMode) return;  // ignore movement keys while targeting

            Ship ship = mapCanvas.getSelectedShip();
            if (ship == null) {
                setStatus("No ship selected");
                return;
            }
            int localImpulse = TurnTracker.getLocalImpulse();
            if (!ship.movesThisImpulse(localImpulse)) {
                setStatus(ship.getName() + " (spd " + ship.getSpeed() + ") does not move on impulse " + localImpulse);
                return;
            }
            if (movedThisImpulse.contains(ship)) {
                setStatus(ship.getName() + " has already moved this impulse");
                return;
            }
            handleMovementKey(event.getCode(), ship);
        });

        updateMovableShips();

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
        TurnTracker.nextImpulse();
        movedThisImpulse.clear();
        updateMovableShips();
        turnLabel.setText(turnText());
        setStatus("Impulse " + TurnTracker.getLocalImpulse() + " — ships with yellow ring may move");
        mapCanvas.render();
    }

    private void updateMovableShips() {
        int localImpulse = TurnTracker.getLocalImpulse();
        List<Ship> movable = new ArrayList<>();
        for (Ship ship : ships) {
            if (ship.movesThisImpulse(localImpulse)) {
                movable.add(ship);
            }
        }
        mapCanvas.setMovableShips(movable);
    }

    private String turnText() {
        int turn    = (TurnTracker.getImpulse() - 1) / Constants.IMPULSES_PER_TURN + 1;
        int impulse = TurnTracker.getLocalImpulse();
        return "Turn " + turn + "  |  Impulse " + impulse + " / 32";
    }

    // -------------------------------------------------------------------------
    // Movement key handling
    // -------------------------------------------------------------------------

    private void handleMovementKey(KeyCode code, Ship ship) {
        boolean moved = false;
        String action = "";

        switch (code) {
            case W:
                moved  = ship.goForward();
                action = "forward";
                break;
            case A:
                moved  = ship.turnLeft();
                action = moved ? "turned left" : "cannot turn left yet (turn mode)";
                break;
            case D:
                moved  = ship.turnRight();
                action = moved ? "turned right" : "cannot turn right yet (turn mode)";
                break;
            case Q:
                moved  = ship.sideslipLeft();
                action = moved ? "sideslipped left" : "cannot sideslip (must move first)";
                break;
            case E:
                moved  = ship.sideslipRight();
                action = moved ? "sideslipped right" : "cannot sideslip (must move first)";
                break;
            default:
                return;  // unrecognised key — do nothing
        }

        if (moved) {
            movedThisImpulse.add(ship);
        }
        selectedLabel.setText(ship.getName() + " (" + ship.getHullType() + ")  spd " + ship.getSpeed()
                + "  @ " + ship.getLocation());
        setStatus(ship.getName() + ": " + action);
        infoPanel.update(ship);
        mapCanvas.render();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
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
        int range = MapUtils.getRange(attacker, target);
        List<Weapon> bearing = attacker.fetchAllBearingWeapons(target);

        if (bearing.isEmpty()) {
            setStatus("No weapons bear on " + target.getName() + " at range " + range);
            infoPanel.update(attacker);
            return;
        }

        int shieldFacing = target.getRelativeShieldFacing(attacker);
        int shieldNumber = (shieldFacing % 2 == 0) ? shieldFacing / 2 : (shieldFacing + 1) / 2;
        shieldNumber = Math.max(1, Math.min(6, shieldNumber));

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

    // -------------------------------------------------------------------------
    // Ship setup
    // -------------------------------------------------------------------------

    private void setupShips() {
        Player player1 = new Player();
        player1.setName("Knosset");
        player1.setFaction(Faction.Federation);

        Player player2 = new Player();
        player2.setName("Kumerian");
        player2.setFaction(Faction.Klingon);

        Ship fedCa = new Ship();
        fedCa.init(SampleShips.getFedCa());
        fedCa.setLocation(new Location(12, 1));
        fedCa.setFacing(13);
        fedCa.setSpeed(16);
        fedCa.setOwner(player1);
        fedCa.autoAllocate();
        ships.add(fedCa);

        Ship fedFfg = new Ship();
        fedFfg.init(SampleShips.getFedFfg());
        fedFfg.setLocation(new Location(16, 1));
        fedFfg.setFacing(13);
        fedFfg.setSpeed(20);
        fedFfg.setOwner(player1);
        fedFfg.autoAllocate();
        ships.add(fedFfg);

        Ship klnD7 = new Ship();
        klnD7.init(SampleShips.getD7());
        klnD7.setLocation(new Location(12, 30));
        klnD7.setFacing(1);
        klnD7.setSpeed(16);
        klnD7.setOwner(player2);
        klnD7.autoAllocate();
        ships.add(klnD7);

        Ship klnF5 = new Ship();
        klnF5.init(SampleShips.getF5());
        klnF5.setLocation(new Location(16, 30));
        klnF5.setFacing(1);
        klnF5.setSpeed(20);
        klnF5.setOwner(player2);
        klnF5.autoAllocate();
        ships.add(klnF5);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
