package com.sfb.ui;

import java.util.ArrayList;
import java.util.List;

import com.sfb.Game;
import com.sfb.Game.ActionResult;
import com.sfb.commands.MoveCommand;
import com.sfb.commands.ShuttleMoveCommand;
import com.sfb.properties.SystemTarget;
import com.sfb.systems.Energy;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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
 * All game state and actions go through GameFacade — either LocalGameFacade
 * (single machine) or ServerGameClient (networked multiplayer).
 *
 * Run with: mvn exec:java
 */
public class SFBMapApp extends Application {

    private static final int MAP_COLS = 32;
    private static final int MAP_ROWS = 40;

    private GameFacade game; // assigned in start() after ConnectDialog

    private HexMapCanvas  mapCanvas;
    private ShipInfoPanel infoPanel;
    private Label         turnLabel;
    private Label         selectedLabel;
    private Label         statusLabel;
    private Label         keyHelp;
    private TextArea      combatLog;
    private Button        nextPhaseBtn;
    private boolean       waitingForReady  = false;
    private boolean       firingMode       = false;
    private boolean       droneMode        = false;
    private boolean       plasmaMode       = false;
    private boolean       hitAndRunMode    = false;
    private boolean       shuttleMode      = false;
    private boolean       suicideShuttleMode  = false;
    private boolean       scatterPackMode     = false;
    private com.sfb.objects.ScatterPack pendingScatterPack = null;
    private com.sfb.objects.Shuttle         selectedShuttle = null;
    private com.sfb.systemgroups.ShuttleBay pendingBay     = null;
    private com.sfb.objects.Shuttle         pendingShuttle = null;
    private DroneRack     pendingRack      = null;
    private Drone         pendingDrone     = null;
    private PlasmaLauncher pendingLauncher = null;
    private boolean        pendingPseudo   = false;
    private final Tooltip droneTooltip   = new Tooltip();

    @Override
    public void start(Stage primaryStage) {
        ConnectDialog connectDialog = new ConnectDialog(primaryStage);
        connectDialog.showAndWait();
        game = connectDialog.getFacade();
        if (game == null) {
            primaryStage.close();
            return;
        }

        game.setup();

        mapCanvas = new HexMapCanvas(MAP_COLS, MAP_ROWS, game.getShips());
        mapCanvas.setSeekers(game.getSeekers());
        mapCanvas.setMines(game.getMines());
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
        nextPhaseBtn.setOnAction(e -> { if (waitingForReady) cancelReady(); else advancePhase(); });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        keyHelp = new Label();
        keyHelp.setStyle("-fx-text-fill: #8899aa; -fx-font-size: 10;");
        updateKeyHelp();

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
            if (mapCanvas.isHexSelectionMode()) {
                mapCanvas.handleHexClick(event.getX(), event.getY());
                return;
            }
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

        // --- Drone tooltip on hover / hex highlight in selection mode ---
        mapCanvas.setOnMouseMoved(event -> {
            if (mapCanvas.isHexSelectionMode()) {
                mapCanvas.setHoveredHex(event.getX(), event.getY());
                return;
            }
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
                if (firingMode)            exitFiringMode();
                if (droneMode)             exitDroneMode();
                if (plasmaMode)            exitPlasmaMode();
                if (shuttleMode)           exitShuttleMode();
                if (hitAndRunMode)         exitHitAndRunMode();
                if (suicideShuttleMode)  { suicideShuttleMode = false; pendingShuttle = null; mapCanvas.setFiringMode(false); mapCanvas.render(); }
                if (scatterPackMode)     { scatterPackMode = false; pendingScatterPack = null; mapCanvas.setFiringMode(false); mapCanvas.render(); }
                if (selectedShuttle != null) { selectedShuttle = null; selectedLabel.setText(""); }
                if (mapCanvas.isHexSelectionMode()) {
                    mapCanvas.exitHexSelectionMode();
                    mapCanvas.render();
                    setStatus("Hex selection cancelled");
                }
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
            if (event.getCode() == KeyCode.S) {
                if (!game.canLaunchThisPhase()) {
                    setStatus("Can't launch shuttles during " + game.getCurrentPhase().getLabel() + " phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                ShuttleSelectDialog dlg = new ShuttleSelectDialog(
                        mapCanvas.getScene().getWindow(), ship, game.getAbsoluteImpulse());
                dlg.showAndWait();
                pendingBay    = dlg.getSelectedBay();
                pendingShuttle = dlg.getSelectedShuttle();
                if (pendingBay == null || pendingShuttle == null) return;
                ShuttleDirectionDialog dirDlg = new ShuttleDirectionDialog(
                        mapCanvas.getScene().getWindow());
                dirDlg.showAndWait();
                int relDir = dirDlg.getSelectedDirection();
                if (relDir == 0) return; // cancelled
                int facing = ((relDir - 1 + ship.getFacing() - 1) % 24) + 1;
                Game.ActionResult result = game.launchShuttle(
                        ship, pendingBay, pendingShuttle,
                        pendingShuttle.getMaxSpeed(), facing);
                appendLog(result.getMessage());
                if (result.isSuccess()) {
                    // Pre-select the shuttle so it auto-moves when its impulse comes
                    selectedShuttle = pendingShuttle;
                    selectedLabel.setText(pendingShuttle.getName() + "  (shuttle — moves in MOVEMENT phase)");
                    setStatus(result.getMessage() + " — move it with W/A/D in MOVEMENT phase");
                } else {
                    setStatus(result.getMessage());
                }
                mapCanvas.setActiveShuttles(game.getActiveShuttles());
                mapCanvas.render();
                exitShuttleMode();
                return;
            }
            if (event.getCode() == KeyCode.H) {
                if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY) {
                    setStatus("Hit & Run raids can only be performed during the Activity phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                if (ship.getCrew().getAvailableBoardingParties() == 0) {
                    setStatus("No boarding parties available");
                    return;
                }
                if (ship.getTransporters().availableUses() == 0) {
                    setStatus("No transporter energy available");
                    return;
                }
                hitAndRunMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("HIT & RUN MODE — click an enemy ship  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (event.getCode() == KeyCode.U) {
                if (!game.canLaunchThisPhase()) {
                    setStatus("Suicide shuttles can only be launched during the Activity phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                // Find armed suicide shuttles in any bay
                List<com.sfb.objects.SuicideShuttle> armedList = new ArrayList<>();
                for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (com.sfb.objects.Shuttle s : bay.getInventory()) {
                        if (s instanceof com.sfb.objects.SuicideShuttle
                                && ((com.sfb.objects.SuicideShuttle) s).isArmed())
                            armedList.add((com.sfb.objects.SuicideShuttle) s);
                    }
                }
                if (armedList.isEmpty()) {
                    setStatus(ship.getName() + " has no fully-armed suicide shuttles");
                    return;
                }
                // Pick shuttle (auto-select if only one)
                com.sfb.objects.SuicideShuttle chosen;
                if (armedList.size() == 1) {
                    chosen = armedList.get(0);
                } else {
                    javafx.scene.control.ChoiceDialog<com.sfb.objects.SuicideShuttle> pick =
                            new javafx.scene.control.ChoiceDialog<>(armedList.get(0), armedList);
                    pick.setTitle("Launch Suicide Shuttle");
                    pick.setHeaderText("Select suicide shuttle to launch:");
                    pick.initOwner(mapCanvas.getScene().getWindow());
                    chosen = pick.showAndWait().orElse(null);
                    if (chosen == null) { setStatus("Cancelled"); return; }
                }
                // Enter target-selection mode
                pendingShuttle      = chosen;
                suicideShuttleMode = true;
                mapCanvas.setFiringMode(true);
                setStatus("SUICIDE SHUTTLE — click target ship  (Escape to cancel)");
                mapCanvas.render();
                return;
            }
            if (event.getCode() == KeyCode.K) {
                if (!game.canLaunchThisPhase()) {
                    setStatus("Scatter packs can only be launched during the Activity phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                List<com.sfb.objects.ScatterPack> packList = new ArrayList<>();
                for (com.sfb.systemgroups.ShuttleBay bay : ship.getShuttles().getBays()) {
                    for (com.sfb.objects.Shuttle s : bay.getInventory()) {
                        if (s instanceof com.sfb.objects.ScatterPack
                                && !((com.sfb.objects.ScatterPack) s).getPayload().isEmpty())
                            packList.add((com.sfb.objects.ScatterPack) s);
                    }
                }
                if (packList.isEmpty()) {
                    setStatus(ship.getName() + " has no loaded scatter packs");
                    return;
                }
                com.sfb.objects.ScatterPack chosen;
                if (packList.size() == 1) {
                    chosen = packList.get(0);
                } else {
                    javafx.scene.control.ChoiceDialog<com.sfb.objects.ScatterPack> pick =
                            new javafx.scene.control.ChoiceDialog<>(packList.get(0), packList);
                    pick.setTitle("Launch Scatter Pack");
                    pick.setHeaderText("Select scatter pack to launch:");
                    pick.initOwner(mapCanvas.getScene().getWindow());
                    chosen = pick.showAndWait().orElse(null);
                    if (chosen == null) { setStatus("Cancelled"); return; }
                }
                pendingScatterPack = chosen;
                scatterPackMode    = true;
                mapCanvas.setFiringMode(true);
                setStatus("SCATTER PACK — click target ship  (Escape to cancel)  ["
                        + chosen.getPayload().size() + " drones]");
                mapCanvas.render();
                return;
            }
            if (event.getCode() == KeyCode.B) {
                if (game.getCurrentPhase() != Game.ImpulsePhase.ACTIVITY) {
                    setStatus("tBombs can only be placed during the Activity phase");
                    return;
                }
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                if (ship.getTransporters().availableUses() < 1) {
                    setStatus("No transporter energy available");
                    return;
                }
                if (ship.getTBombs() < 1 && ship.getDummyTBombs() < 1) {
                    setStatus("No tBombs available");
                    return;
                }
                enterHexSelectionMode("Select hex to place tBomb", loc -> {
                    // Ask real or dummy
                    boolean hasReal  = ship.getTBombs() > 0;
                    boolean hasDummy = ship.getDummyTBombs() > 0;
                    boolean isReal;
                    if (hasReal && hasDummy) {
                        ButtonType realBtn  = new ButtonType("Real tBomb");
                        ButtonType dummyBtn = new ButtonType("Dummy tBomb");
                        ButtonType cancelBtn = new ButtonType("Cancel");
                        Alert alert = new Alert(Alert.AlertType.NONE,
                                "Place a real tBomb or a dummy?",
                                realBtn, dummyBtn, cancelBtn);
                        alert.setTitle("tBomb Type");
                        alert.initOwner(mapCanvas.getScene().getWindow());
                        ButtonType choice = alert.showAndWait().orElse(cancelBtn);
                        if (choice == cancelBtn) { setStatus("Cancelled"); return; }
                        isReal = (choice == realBtn);
                    } else {
                        isReal = hasReal;
                    }
                    Game.ActionResult result = game.placeTBomb(ship, loc, isReal);
                    appendLog(result.getMessage());
                    setStatus(result.isSuccess() ? "tBomb placed" : result.getMessage());
                    mapCanvas.render();
                    infoPanel.update(ship);
                });
                return;
            }
            if (event.getCode() == KeyCode.C) {
                Ship ship = mapCanvas.getSelectedShip();
                if (ship == null) { setStatus("Select a ship first"); return; }
                com.sfb.systemgroups.CloakingDevice cloak = ship.getCloakingDevice();
                if (cloak == null) { setStatus(ship.getName() + " has no cloaking device"); return; }
                Game.ActionResult result;
                com.sfb.systemgroups.CloakingDevice.CloakState state = cloak.getState();
                if (state == com.sfb.systemgroups.CloakingDevice.CloakState.INACTIVE
                        || state == com.sfb.systemgroups.CloakingDevice.CloakState.FADING_IN) {
                    result = game.cloak(ship);
                } else {
                    result = game.uncloak(ship);
                }
                appendLog(result.getMessage());
                setStatus(result.getMessage());
                infoPanel.update(ship);
                mapCanvas.render();
                return;
            }
            if (firingMode || droneMode || plasmaMode || hitAndRunMode || suicideShuttleMode || scatterPackMode) return;

            // Shuttle movement: only in MOVEMENT phase when all ships have moved
            if (selectedShuttle != null
                    && game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT
                    && game.getMovableShips().isEmpty()) {
                handleShuttleMovementKey(event.getCode(), selectedShuttle);
                return;
            }

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

        // If connected to a server, re-render whenever state is pushed
        if (game instanceof ServerGameClient) {
            int[] lastAllocatedTurn = { -1 }; // tracks which turn we've already shown the dialog
            ((ServerGameClient) game).setOnStateChanged(v -> {
                refreshMovableShips();
                turnLabel.setText(turnText());
                waitingForReady = false;
                nextPhaseBtn.setText(nextPhaseLabel());
                nextPhaseBtn.setStyle(
                    "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
                    "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
                    "-fx-font-size: 12; -fx-cursor: hand;");
                ServerGameClient sgc = (ServerGameClient) game;

                // Allocation phase triggered by server state — only show once per turn
                if (game.isAwaitingAllocation()
                        && game.nextShipNeedingAllocation() != null
                        && lastAllocatedTurn[0] != game.getCurrentTurn()) {
                    lastAllocatedTurn[0] = game.getCurrentTurn();
                    runAllocationPhase((Stage) mapCanvas.getScene().getWindow());
                    return;
                }

                if (sgc.isWaitingForOtherPlayer()) {
                    Ship next = sgc.getNextInQueue();
                    String shipName = next != null ? next.getName() : "another ship";
                    setStatus("Waiting for " + shipName + " to move...");
                    mapCanvas.setSelectedShip(null);
                } else {
                    setStatus(phaseStatus());
                }
                updateKeyHelp();
                mapCanvas.render();
            });
            // Shut down the poller when the window closes
            primaryStage.setOnCloseRequest(e -> ((ServerGameClient) game).shutdown());
        }
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
        if (game instanceof ServerGameClient) {
            if (!game.isAwaitingAllocation()) return;
            // Show dialog for each of our ships that still needs allocation
            Ship ship;
            while ((ship = game.nextShipNeedingAllocation()) != null) {
                EnergyAllocationDialog dialog = new EnergyAllocationDialog(stage, game.getCurrentTurn(), ship);
                dialog.showAndWait();
                Energy allocation = dialog.getSubmittedAllocation();
                if (allocation != null) {
                    ActionResult result = game.allocateEnergy(ship, allocation);
                    if (!result.isSuccess()) break;
                }
            }
            setStatus("Waiting for other players to allocate energy...");
        } else {
            while (game.isAwaitingAllocation()) {
                Ship ship = game.nextShipNeedingAllocation();
                if (ship == null) break;
                EnergyAllocationDialog dialog = new EnergyAllocationDialog(stage, game.getCurrentTurn(), ship);
                dialog.showAndWait();
                Energy allocation = dialog.getSubmittedAllocation();
                if (allocation != null) {
                    game.allocateEnergy(ship, allocation);
                }
            }
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
        Game.ActionResult result = game.advancePhase();
        if (result.isWaiting()) {
            // Server acknowledged ready signal but other players haven't confirmed yet
            waitingForReady = true;
            nextPhaseBtn.setText(cancelReadyLabel());
            nextPhaseBtn.setStyle(
                "-fx-background-color: #2a1a1a; -fx-text-fill: #ff8888; " +
                "-fx-border-color: #663333; -fx-border-radius: 3; -fx-background-radius: 3; " +
                "-fx-font-size: 12; -fx-cursor: hand;");
            setStatus("Waiting for other players... (click to cancel)");
            return;
        }
        waitingForReady = false;
        if (!result.getMessage().isEmpty()) {
            appendLog(result.getMessage());
        }
        if (game.isAwaitingAllocation()) {
            runAllocationPhase((Stage) mapCanvas.getScene().getWindow());
        }
        refreshMovableShips();
        turnLabel.setText(turnText());
        nextPhaseBtn.setText(nextPhaseLabel());
        setStatus(phaseStatus());
        updateKeyHelp();
        // Auto-select the first ship that needs to move
        if (game.getCurrentPhase() == Game.ImpulsePhase.MOVEMENT) {
            List<Ship> movable = game.getMovableShips();
            if (!movable.isEmpty()) {
                Ship first = movable.get(0);
                mapCanvas.setSelectedShip(first);
                infoPanel.update(first);
                selectedLabel.setText(first.getName() + " (" + first.getHullType() + ")");
            }
        }
        mapCanvas.render();
    }

    private String waitingLabel() {
        return "Waiting " + game.getReadyCount() + "/" + game.getPlayerCount() + "  ⏳";
    }

    private String cancelReadyLabel() {
        return "Cancel Ready  ✕";
    }

    private void cancelReady() {
        game.unready();
        waitingForReady = false;
        nextPhaseBtn.setText(nextPhaseLabel());
        nextPhaseBtn.setStyle(
            "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
            "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-cursor: hand;");
        setStatus(phaseStatus());
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
            case MOVEMENT: {
                List<Ship> movable = game.getMovableShips();
                if (movable.isEmpty()) return "Impulse " + game.getCurrentImpulse() + " — Movement  (all ships moved)";
                String names = movable.stream().map(Ship::getName).collect(java.util.stream.Collectors.joining(", "));
                return "Impulse " + game.getCurrentImpulse() + " — Move: " + names;
            }
            case ACTIVITY:    return "Impulse " + game.getCurrentImpulse() + " — Activity";
            case DIRECT_FIRE: return "Impulse " + game.getCurrentImpulse() + " — Direct Fire  (select a ship, press F)";
            case END_OF_IMPULSE: return "Impulse " + game.getCurrentImpulse() + " — End of Impulse";
            default: return "";
        }
    }

    private void appendLog(String text) {
        combatLog.appendText(text + "\n");
        combatLog.setScrollTop(Double.MAX_VALUE);
    }

    private void updateKeyHelp() {
        switch (game.getCurrentPhase()) {
            case MOVEMENT:
                keyHelp.setText("W=fwd  A/D=turn  Q/E=sideslip  (shuttles: W/A/D after ships)  ESC=cancel");
                break;
            case ACTIVITY:
                keyHelp.setText("L=launch drone  P=plasma  S=shuttle  U=suicide shuttle  K=scatter pack  B=tBomb  C=cloak/uncloak  ESC=cancel");
                break;
            case DIRECT_FIRE:
                keyHelp.setText("F=fire  H=hit&run  ESC=cancel");
                break;
            default:
                keyHelp.setText("");
                break;
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
            case W: result = game.moveShip(ship, MoveCommand.Action.FORWARD);        break;
            case A: result = game.moveShip(ship, MoveCommand.Action.TURN_LEFT);      break;
            case D: result = game.moveShip(ship, MoveCommand.Action.TURN_RIGHT);     break;
            case Q: result = game.moveShip(ship, MoveCommand.Action.SIDESLIP_LEFT);  break;
            case E: result = game.moveShip(ship, MoveCommand.Action.SIDESLIP_RIGHT); break;
            default: return;
        }

        if (result.isSuccess()) {
            refreshMovableShips();
            List<Ship> movable = game.getMovableShips();
            if (movable.isEmpty()) {
                // Auto-select first movable shuttle if any
                List<com.sfb.objects.Shuttle> movableShuttles = game.getMovableShuttles();
                if (!movableShuttles.isEmpty()) {
                    selectedShuttle = movableShuttles.get(0);
                    selectedLabel.setText(selectedShuttle.getName() + "  (shuttle)");
                    setStatus("Move shuttle: " + selectedShuttle.getName() + "  W=fwd  A/D=turn  ESC=deselect");
                    mapCanvas.render();
                    return;
                }
                setStatus("All ships moved — ready to advance");
                selectedLabel.setText(ship.getName() + " (" + ship.getHullType() + ")  spd " + ship.getSpeed()
                        + "  @ " + ship.getLocation());
                infoPanel.update(ship);
            } else {
                // Auto-advance to next ship that needs to move this impulse
                Ship next = movable.get(0);
                mapCanvas.setSelectedShip(next);
                infoPanel.update(next);
                selectedLabel.setText(next.getName() + " (" + next.getHullType() + ")");
                String names = movable.stream().map(Ship::getName).collect(java.util.stream.Collectors.joining(", "));
                setStatus("Move: " + names);
            }
        } else {
            setStatus(result.getMessage());
            infoPanel.update(ship);
        }
        mapCanvas.render();
    }

    private void handleShuttleMovementKey(KeyCode code, com.sfb.objects.Shuttle shuttle) {
        ActionResult result;
        switch (code) {
            case W: result = game.moveShuttle(shuttle, ShuttleMoveCommand.Action.FORWARD);    break;
            case A: result = game.moveShuttle(shuttle, ShuttleMoveCommand.Action.TURN_LEFT);  break;
            case D: result = game.moveShuttle(shuttle, ShuttleMoveCommand.Action.TURN_RIGHT); break;
            default: return;
        }

        if (result.isSuccess()) {
            mapCanvas.setActiveShuttles(game.getActiveShuttles());
            List<com.sfb.objects.Shuttle> movable = game.getMovableShuttles();
            if (movable.isEmpty()) {
                selectedShuttle = null;
                setStatus("All shuttles moved — ready to advance");
                selectedLabel.setText("");
            } else {
                selectedShuttle = movable.get(0);
                selectedLabel.setText(selectedShuttle.getName() + "  (shuttle)");
                setStatus("Move shuttle: " + selectedShuttle.getName() + "  W=fwd  A/D=turn  ESC=deselect");
            }
        } else {
            setStatus(result.getMessage());
        }
        mapCanvas.render();
    }

    // -------------------------------------------------------------------------
    // Weapons fire
    // -------------------------------------------------------------------------

    /**
     * Enter hex selection mode. The status bar prompts the player; when they
     * click a hex the callback receives the Location (null if outside the map).
     * Any future feature that needs a hex target calls this instead of wiring
     * its own click handler.
     */
    private void enterHexSelectionMode(String prompt, java.util.function.Consumer<com.sfb.properties.Location> callback) {
        if (firingMode) exitFiringMode();
        if (droneMode)  exitDroneMode();
        if (plasmaMode) exitPlasmaMode();
        mapCanvas.enterHexSelectionMode(loc -> {
            mapCanvas.render();
            if (loc != null) {
                callback.accept(loc);
            } else {
                setStatus("No hex selected");
            }
        });
        setStatus(prompt + "  (Escape to cancel)");
    }

    private void exitFiringMode() {
        firingMode = false;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  F: fire  L: drone  P: plasma  H: hit & run" : "");
        mapCanvas.render();
    }

    private void exitDroneMode() {
        droneMode    = false;
        pendingRack  = null;
        pendingDrone = null;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  F: fire  L: drone  P: plasma  H: hit & run" : "");
        mapCanvas.render();
    }

    private void exitPlasmaMode() {
        plasmaMode      = false;
        pendingLauncher = null;
        pendingPseudo   = false;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  F: fire  L: drone  P: plasma  H: hit & run" : "");
        mapCanvas.render();
    }

    private void exitShuttleMode() {
        shuttleMode    = false;
        pendingBay     = null;
        pendingShuttle = null;
        mapCanvas.render();
    }

    private void exitHitAndRunMode() {
        hitAndRunMode = false;
        mapCanvas.setFiringMode(false);
        Ship sel = mapCanvas.getSelectedShip();
        setStatus(sel != null ? "Selected  —  F: fire  L: drone  P: plasma  H: hit & run" : "");
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
                    Game.ActionResult result = game.launchDrone(launcher, hitUnit, pendingRack, pendingDrone);
                    appendLog(result.getMessage());
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
                    Game.ActionResult result = game.launchPlasma(launcher, hitUnit, pendingLauncher, pendingPseudo);
                    appendLog(result.getMessage());
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
        } else if (scatterPackMode) {
            Ship launcher = mapCanvas.getSelectedShip();
            if (hitShip == null) {
                setStatus("Click an enemy ship — or press Escape to cancel");
            } else if (hitShip == launcher) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else {
                Game.ActionResult result = game.launchScatterPack(launcher, pendingScatterPack, hitShip);
                appendLog(result.getMessage());
                setStatus(result.getMessage());
                mapCanvas.setSeekers(game.getSeekers());
                mapCanvas.render();
                scatterPackMode    = false;
                pendingScatterPack = null;
                mapCanvas.setFiringMode(false);
            }
        } else if (suicideShuttleMode) {
            Ship launcher = mapCanvas.getSelectedShip();
            if (hitShip == null) {
                setStatus("Click an enemy ship — or press Escape to cancel");
            } else if (hitShip == launcher) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else if (pendingShuttle instanceof com.sfb.objects.SuicideShuttle) {
                Game.ActionResult result = game.launchSuicideShuttle(
                        launcher, (com.sfb.objects.SuicideShuttle) pendingShuttle, hitShip);
                appendLog(result.getMessage());
                setStatus(result.getMessage());
                mapCanvas.setSeekers(game.getSeekers());
                mapCanvas.render();
                suicideShuttleMode = false;
                pendingShuttle = null;
                mapCanvas.setFiringMode(false);
            }
        } else if (hitAndRunMode) {
            Ship actingShip = mapCanvas.getSelectedShip();
            if (hitShip == null) {
                setStatus("Click an enemy ship — or press Escape to cancel");
            } else if (hitShip == actingShip) {
                setStatus("Can't target yourself — click an enemy or press Escape");
            } else {
                List<SystemTarget> available = game.getTargetableSystems(hitShip);
                if (available.isEmpty()) {
                    setStatus("No targetable systems on " + hitShip.getName());
                } else {
                    HitAndRunDialog dlg = new HitAndRunDialog(
                            (Stage) mapCanvas.getScene().getWindow(),
                            actingShip, hitShip, available);
                    dlg.showAndWait();
                    List<SystemTarget> targets = dlg.getTargetSystems();
                    if (targets != null && !targets.isEmpty()) {
                        Game.ActionResult result = game.hitAndRun(actingShip, hitShip, targets);
                        appendLog(result.getMessage());
                        setStatus(result.isSuccess() ? "Raid complete — see combat log" : result.getMessage());
                        infoPanel.update(hitShip);
                    } else {
                        setStatus("Raid cancelled");
                    }
                }
                exitHitAndRunMode();
            }
        } else {
            // Check if a shuttle was clicked
            com.sfb.objects.Shuttle clickedShuttle = null;
            if (hit instanceof com.sfb.objects.Shuttle)
                clickedShuttle = (com.sfb.objects.Shuttle) hit;

            if (clickedShuttle != null) {
                selectedShuttle = clickedShuttle;
                mapCanvas.setSelectedShip(null);
                selectedLabel.setText(clickedShuttle.getName() + "  (shuttle)");
                statusLabel.setText("Shuttle selected  —  W=fwd  A/D=turn  ESC=deselect");
            } else {
                selectedShuttle = null;
                mapCanvas.setSelectedShip(hitShip);
                if (hitShip != null) {
                    selectedLabel.setText(hitShip.getName() + " (" + hitShip.getHullType() + ")  spd " + hitShip.getSpeed());
                    statusLabel.setText("Selected  —  F: fire  L: drone  P: plasma  H: hit & run");
                } else {
                    selectedLabel.setText(hit != null ? hit.getName() : "No ship selected");
                    statusLabel.setText("");
                }
                infoPanel.update(hitShip);
            }
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
            int adjustedRange = game.getEffectiveRange(attacker, target);
            Game.ActionResult result = game.fire(attacker, target, selected, range, adjustedRange, shieldNumber);
            appendLog(result.getMessage());
            setStatus("Fired — see combat log");
            if (target instanceof Ship) {
                selectedLabel.setText(target.getName() + " (" + ((Ship) target).getHullType() + ")  — damage taken");
                infoPanel.update((Ship) target);
            } else {
                selectedLabel.setText(target.getName() + "  — hit");
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
