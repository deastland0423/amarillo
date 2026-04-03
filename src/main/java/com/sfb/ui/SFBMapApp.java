package com.sfb.ui;

import java.util.List;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.commands.FireCommand;
import com.sfb.commands.LaunchDroneCommand;
import com.sfb.commands.LaunchPlasmaCommand;
import com.sfb.commands.MoveCommand;
import com.sfb.objects.Drone;
import com.sfb.objects.Marker;
import com.sfb.objects.Ship;
import com.sfb.objects.Unit;
import com.sfb.weapons.DroneRack;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
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
    private Button        nextPhaseBtn;
    private boolean       firingMode       = false;
    private boolean       droneMode        = false;
    private boolean       plasmaMode       = false;
    private DroneRack     pendingRack      = null;
    private Drone         pendingDrone     = null;
    private PlasmaLauncher pendingLauncher = null;
    private boolean        pendingPseudo   = false;
    private final Tooltip droneTooltip   = new Tooltip();

    @Override
    public void start(Stage primaryStage) {
        game.setup();

        mapCanvas = new HexMapCanvas(MAP_COLS, MAP_ROWS, game.getShips());
        mapCanvas.setSeekers(game.getSeekers());
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

        nextPhaseBtn = new Button(nextPhaseLabel());
        nextPhaseBtn.setStyle(
            "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
            "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-cursor: hand;");
        nextPhaseBtn.setOnAction(e -> advancePhase());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label keyHelp = new Label("W=fwd  A/D=turn  Q/E=slip  F=fire  L=drone");
        keyHelp.setStyle("-fx-text-fill: #445566; -fx-font-size: 10;");

        HBox toolbar = new HBox(16, turnLabel, selectedLabel, statusLabel, spacer, keyHelp, nextPhaseBtn);
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
            List<Marker> hits = mapCanvas.hitTestAll(event.getX(), event.getY());
            if (hits.isEmpty()) {
                handleHitMarker(null, scene);
            } else if (hits.size() == 1) {
                handleHitMarker(hits.get(0), scene);
            } else {
                ContextMenu menu = new ContextMenu();
                for (Marker m : hits) {
                    MenuItem item = new MenuItem(m.getName());
                    item.setOnAction(e -> handleHitMarker(m, scene));
                    menu.getItems().add(item);
                }
                menu.show(mapCanvas, event.getScreenX(), event.getScreenY());
            }
        });

        // --- Drone tooltip on hover ---
        mapCanvas.setOnMouseMoved(event -> {
            List<Drone> hovered = mapCanvas.hitTestDrones(event.getX(), event.getY());
            if (hovered.isEmpty()) {
                droneTooltip.hide();
            } else {
                droneTooltip.setText(buildDroneTooltip(hovered));
                droneTooltip.show(mapCanvas,
                        event.getScreenX() + 12,
                        event.getScreenY() + 12);
            }
        });
        mapCanvas.setOnMouseExited(event -> droneTooltip.hide());

        // --- Keyboard ---
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (firingMode)  exitFiringMode();
                if (droneMode)   exitDroneMode();
                if (plasmaMode)  exitPlasmaMode();
                return;
            }
            if (event.getCode() == KeyCode.F) {
                if (!game.canFireThisPhase()) {
                    setStatus("Can't fire during " + game.getCurrentPhase().getLabel() + " phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                firingMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("FIRE MODE — click a target  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (event.getCode() == KeyCode.L) {
                if (!game.canLaunchThisPhase()) {
                    setStatus("Can't launch drones during " + game.getCurrentPhase().getLabel() + " phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                DroneSelectDialog dlg = new DroneSelectDialog(
                        mapCanvas.getScene().getWindow(), ship);
                dlg.showAndWait();
                pendingRack  = dlg.getSelectedRack();
                pendingDrone = dlg.getSelectedDrone();
                if (pendingRack == null || pendingDrone == null) return;
                droneMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("DRONE MODE — click a target  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (event.getCode() == KeyCode.P) {
                if (!game.canLaunchThisPhase()) {
                    setStatus("Can't launch plasma during " + game.getCurrentPhase().getLabel() + " phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                PlasmaSelectDialog dlg = new PlasmaSelectDialog(
                        mapCanvas.getScene().getWindow(), ship);
                dlg.showAndWait();
                pendingLauncher = dlg.getSelectedLauncher();
                pendingPseudo   = dlg.isSelectedPseudo();
                if (pendingLauncher == null) return;
                plasmaMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("PLASMA MODE — click a target  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (firingMode || droneMode || plasmaMode) return;

            Ship ship = mapCanvas.getSelectedShip();
            if (ship == null) { setStatus("No ship selected"); return; }

            handleMovementKey(event.getCode(), ship);
        });

        runAllocationPhase(primaryStage);
        refreshMovableShips();
        setStatus(phaseStatus());

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

    /**
     * Show the energy allocation dialog for each ship that needs allocation.
     * Blocks until all ships have submitted. Called at game start and each
     * turn rollover.
     */
    private void runAllocationPhase(Stage stage) {
        while (game.isAwaitingAllocation()) {
            Ship ship = game.nextShipNeedingAllocation();
            if (ship == null) break;
            EnergyAllocationDialog dialog = new EnergyAllocationDialog(stage, game, ship);
            dialog.showAndWait();
        }
        turnLabel.setText(turnText());
        setStatus(phaseStatus());
    }

    private void advancePhase() {
        if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                && !game.getMovableShips().isEmpty()) {
            setStatus("All scheduled ships must move before leaving the movement phase");
            return;
        }
        boolean leavingMovementPhase = game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT;
        boolean leavingFirePhase     = game.getCurrentPhase() == Game.ImpulsePhase.DIRECT_FIRE;
        game.advancePhase();
        if (leavingMovementPhase) {
            List<String> seekerLog = game.getLastSeekerLog();
            if (!seekerLog.isEmpty()) {
                for (String entry : seekerLog) {
                    combatLog.appendText(entry + "\n");
                }
                infoPanel.update(null);
            }
            List<String> droneInternalLog = game.getLastInternalDamageLog();
            if (!droneInternalLog.isEmpty()) {
                for (String entry : droneInternalLog) {
                    combatLog.appendText(entry + "\n");
                }
                infoPanel.update(null);
            }
        }
        if (leavingFirePhase) {
            List<String> internalLog = game.getLastInternalDamageLog();
            if (!internalLog.isEmpty()) {
                for (String entry : internalLog) {
                    combatLog.appendText(entry + "\n");
                }
                infoPanel.update(null);
            }
        }
        if (game.isAwaitingAllocation()) {
            runAllocationPhase((Stage) mapCanvas.getScene().getWindow());
        }
        refreshMovableShips();
        turnLabel.setText(turnText());
        nextPhaseBtn.setText(nextPhaseLabel());
        setStatus(phaseStatus());
        mapCanvas.render();
    }

    private String nextPhaseLabel() {
        switch (game.getCurrentPhase()) {
            case MOVEMENT:       return "End Movement  ▶";
            case ACTIVITY:       return "End Activity  ▶";
            case DIRECT_FIRE:    return "Resolve Fire  ▶";
            case END_OF_IMPULSE:
                return game.getCurrentImpulse() >= 32 ? "Next Turn  ▶" : "Next Impulse  ▶";
            default:             return "Next Phase  ▶";
        }
    }

    private String phaseStatus() {
        switch (game.getCurrentPhase()) {
            case MOVEMENT:    return "Impulse " + game.getCurrentImpulse() + " — Movement  (W/A/D/Q/E to move)";
            case ACTIVITY:    return "Impulse " + game.getCurrentImpulse() + " — Activity";
            case DIRECT_FIRE: return "Impulse " + game.getCurrentImpulse() + " — Direct Fire  (select a ship, press F)";
            case END_OF_IMPULSE: return "Impulse " + game.getCurrentImpulse() + " — End of Impulse";
            default: return "";
        }
    }

    private void refreshMovableShips() {
        mapCanvas.setMovableShips(game.getMovableShips());
    }

    private String turnText() {
        return "Turn " + game.getCurrentTurn() + "  |  Impulse " + game.getCurrentImpulse()
                + " / 32  |  " + game.getCurrentPhase().getLabel();
    }

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------

    private void handleMovementKey(KeyCode code, Ship ship) {
        ActionResult result;

        switch (code) {
            case W: result = game.execute(new MoveCommand(ship, MoveCommand.Action.FORWARD));        break;
            case A: result = game.execute(new MoveCommand(ship, MoveCommand.Action.TURN_LEFT));     break;
            case D: result = game.execute(new MoveCommand(ship, MoveCommand.Action.TURN_RIGHT));    break;
            case Q: result = game.execute(new MoveCommand(ship, MoveCommand.Action.SIDESLIP_LEFT)); break;
            case E: result = game.execute(new MoveCommand(ship, MoveCommand.Action.SIDESLIP_RIGHT));break;
            default: return;
        }

        if (result.isSuccess()) {
            selectedLabel.setText(ship.getName() + " (" + ship.getHullType() + ")  spd " + ship.getSpeed()
                    + "  @ " + ship.getLocation());
            refreshMovableShips();
            int remaining = game.getMovableShips().size();
            if (remaining == 0) {
                setStatus("All ships moved — ready to advance");
            } else {
                setStatus(remaining + " ship(s) still need to move");
            }
        } else {
            setStatus(result.getMessage());
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
        setStatus(sel != null ? "Selected  —  F: fire  L: launch drone  P: launch plasma" : "");
        mapCanvas.render();
    }

    private void exitDroneMode() {
        droneMode    = false;
        pendingRack  = null;
        pendingDrone = null;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  F: fire  L: launch drone  P: launch plasma" : "");
        mapCanvas.render();
    }

    private void exitPlasmaMode() {
        plasmaMode      = false;
        pendingLauncher = null;
        pendingPseudo   = false;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  press P to launch plasma" : "");
        mapCanvas.render();
    }


    private void handleHitMarker(Marker hit, Scene scene) {
        Unit hitUnit = (hit instanceof Unit) ? (Unit) hit : null;
        Ship hitShip = (hit instanceof Ship) ? (Ship) hit : null;

        if (droneMode) {
            Ship launcher = mapCanvas.getSelectedShip();
            if (hitUnit == null) {
                setStatus("No target — click a ship or drone or press Escape to cancel");
            } else if (hitUnit == launcher) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else {
                if (pendingRack != null && pendingDrone != null) {
                    Game.ActionResult result = game.execute(new LaunchDroneCommand(launcher, hitUnit, pendingRack, pendingDrone));
                    combatLog.appendText(result.getMessage() + "\n");
                    setStatus(result.getMessage());
                    mapCanvas.setSeekers(game.getSeekers());
                    mapCanvas.render();
                }
                exitDroneMode();
            }
        } else if (plasmaMode) {
            Ship launcher = mapCanvas.getSelectedShip();
            if (hitUnit == null) {
                setStatus("No target — click a ship or press Escape to cancel");
            } else if (hitUnit == launcher) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else {
                if (pendingLauncher != null) {
                    Game.ActionResult result = game.execute(new LaunchPlasmaCommand(launcher, hitUnit, pendingLauncher, pendingPseudo));
                    combatLog.appendText(result.getMessage() + "\n");
                    setStatus(result.getMessage());
                    mapCanvas.setSeekers(game.getSeekers());
                    mapCanvas.render();
                }
                exitPlasmaMode();
            }
        } else if (firingMode) {
            Ship attacker = mapCanvas.getSelectedShip();
            if (hitUnit == null) {
                setStatus("No target — click a ship or drone or press Escape to cancel");
            } else if (hitUnit == attacker) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else {
                resolveWeaponsFire(attacker, hitUnit);
                exitFiringMode();
            }
        } else {
            mapCanvas.setSelectedShip(hitShip);
            if (hitShip != null) {
                selectedLabel.setText(hitShip.getName() + " (" + hitShip.getHullType() + ")  spd " + hitShip.getSpeed());
                statusLabel.setText("Selected  —  F: fire  L: launch drone  P: launch plasma");
            } else {
                selectedLabel.setText(hit != null ? hit.getName() : "No ship selected");
                statusLabel.setText("");
            }
            infoPanel.update(hitShip);
        }
        mapCanvas.render();
        scene.getRoot().requestFocus();
    }

    private void resolveWeaponsFire(Ship attacker, Unit target) {
        int range = game.getRange(attacker, target);
        List<Weapon> bearing = game.getBearingWeapons(attacker, target);

        // Only phasers can fire at plasma torpedoes
        if (target instanceof com.sfb.objects.PlasmaTorpedo) {
            bearing = bearing.stream()
                    .filter(w -> "phaser".equals(w.getDacHitLocaiton()))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (bearing.isEmpty()) {
            setStatus("No weapons bear on " + target.getName() + " at range " + range);
            infoPanel.update(attacker);
            return;
        }

        int shieldNumber = (target instanceof Ship)
                ? game.getShieldNumber(attacker, (Ship) target)
                : 0;

        WeaponSelectDialog dialog = new WeaponSelectDialog(
                (Stage) mapCanvas.getScene().getWindow(),
                attacker, target, bearing, range, shieldNumber);
        dialog.showAndWait();

        List<Weapon> selected = dialog.getSelectedWeapons();
        if (selected != null) {
            int adjustedRange = range + attacker.getScanner();
            Game.ActionResult result = game.execute(
                    new FireCommand(attacker, target, selected, range, adjustedRange, shieldNumber));
            combatLog.appendText(result.getMessage() + "\n");
            setStatus("Fired — see combat log");
            if (target instanceof Ship) {
                selectedLabel.setText(target.getName() + " (" + ((Ship) target).getHullType() + ")  — damage taken");
                infoPanel.update((Ship) target);
            } else {
                selectedLabel.setText(target.getName() + "  — drone hit");
            }
        } else {
            setStatus("Fire cancelled");
            infoPanel.update(attacker);
        }
        mapCanvas.render();
    }

    private String buildDroneTooltip(List<Drone> drones) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < drones.size(); i++) {
            if (i > 0) sb.append("\n----------\n");
            Drone d = drones.get(i);
            sb.append("Type:    ").append(d.getDroneType() != null ? d.getDroneType() : "?").append("\n");
            sb.append("Warhead: ").append(d.getWarheadDamage()).append("\n");
            sb.append("Hull:    ").append(d.getHull()).append("\n");
            sb.append("Speed:   ").append(d.getSpeed()).append("\n");
            String targetName = d.getTarget() != null ? d.getTarget().getName() : "none";
            sb.append("Target:  ").append(targetName);
        }
        return sb.toString();
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
