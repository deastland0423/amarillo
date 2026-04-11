package com.sfb.ui;

import com.sfb.objects.Ship;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
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
 * Modal dialog for transporting boarding parties onto an enemy ship at combat
 * rate (G8.31): one boarding party per transporter per impulse.
 *
 * <p>The player chooses how many normal boarding parties and commandos to send.
 * The total is capped by available boarding parties, transporters, and
 * transporter energy uses.
 *
 * <p>After the dialog closes, call {@link #getNormal()} and
 * {@link #getCommandos()} to read the selections. Both return -1 if cancelled.
 */
public class BoardingActionDialog extends Stage {

    private static final Font HEADER_FONT  = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font SECTION_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font LABEL_FONT   = Font.font("Monospaced", 11);
    private static final Font SMALL_FONT   = Font.font("Monospaced", 10);

    private static final String DARK_BG      = "-fx-background-color: #0d0d22;";
    private static final String SECTION_BG   = "-fx-background-color: #111130; -fx-background-radius: 4; -fx-padding: 8;";
    private static final String BTN_STYLE    = "-fx-background-color: #1a3a1a; -fx-text-fill: #88ff88; " +
            "-fx-border-color: #336633; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String CANCEL_STYLE = "-fx-background-color: #3a1a1a; -fx-text-fill: #ff8888; " +
            "-fx-border-color: #663333; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String SPINNER_STYLE =
            "-fx-background-color: #1a1a3a; -fx-text-fill: #ccccff; -fx-border-color: #334466;";

    private int resultNormal   = -1; // -1 = cancelled
    private int resultCommandos = -1;

    public BoardingActionDialog(Stage owner, Ship actingShip, Ship target) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Transport Boarding Parties");
        setResizable(false);

        int availableNormal    = actingShip.getCrew().getFriendlyTroops().normal;
        int availableCommandos = actingShip.getCrew().getFriendlyTroops().commandos;
        int availableTrans     = actingShip.getTransporters().getAvailableTrans();
        int availableUses      = actingShip.getTransporters().availableUses();
        int maxTotal           = Math.min(availableNormal + availableCommandos,
                                          Math.min(availableTrans, availableUses));

        // --- Header ---
        Label titleLabel = styledLabel(
                actingShip.getName() + "  →  " + target.getName(),
                HEADER_FONT, Color.WHITE);
        Label statsLabel = styledLabel(
                String.format("Normal BPs: %d    Commandos: %d    Transporters: %d    Energy uses: %d",
                        availableNormal, availableCommandos, availableTrans, availableUses),
                SMALL_FONT, Color.rgb(150, 200, 150));
        Label rateLabel = styledLabel(
                "Combat rate (G8.31): one boarding party per transporter per impulse",
                SMALL_FONT, Color.rgb(120, 120, 120));

        VBox header = new VBox(4, titleLabel, statsLabel, rateLabel);
        header.setStyle(SECTION_BG);

        // --- Spinners ---
        Spinner<Integer> normalSpinner   = makeSpinner(0, Math.min(availableNormal,   maxTotal));
        Spinner<Integer> commandoSpinner = makeSpinner(0, Math.min(availableCommandos, maxTotal));

        Label validationLabel = styledLabel("", SMALL_FONT, Color.rgb(255, 80, 80));
        Button confirmBtn = new Button("Transport");
        confirmBtn.setStyle(BTN_STYLE);
        confirmBtn.setDisable(maxTotal == 0);

        // Keep total ≤ maxTotal and update validation
        Runnable validate = () -> {
            int total = normalSpinner.getValue() + commandoSpinner.getValue();
            if (total == 0) {
                validationLabel.setText("Send at least one boarding party.");
                confirmBtn.setDisable(true);
            } else if (total > maxTotal) {
                validationLabel.setText("Total exceeds transporter capacity (" + maxTotal + " max).");
                confirmBtn.setDisable(true);
            } else {
                validationLabel.setText("");
                confirmBtn.setDisable(false);
            }
        };

        normalSpinner.valueProperty().addListener((obs, o, n) -> validate.run());
        commandoSpinner.valueProperty().addListener((obs, o, n) -> validate.run());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.add(styledLabel("Normal boarding parties:", LABEL_FONT, Color.rgb(200, 200, 100)), 0, 0);
        grid.add(normalSpinner, 1, 0);
        grid.add(styledLabel("Commandos:", LABEL_FONT, Color.rgb(255, 160, 80)), 0, 1);
        grid.add(commandoSpinner, 1, 1);
        grid.add(styledLabel("Max total (transporter limit): " + maxTotal,
                SMALL_FONT, Color.rgb(120, 120, 120)), 0, 2, 2, 1);

        VBox partyBox = new VBox(8,
                styledLabel("BOARDING PARTIES TO SEND", SECTION_FONT, Color.rgb(255, 160, 80)),
                grid,
                validationLabel);
        partyBox.setStyle(SECTION_BG);

        if (maxTotal == 0) {
            partyBox.getChildren().add(styledLabel(
                    "No transport possible — check boarding parties, transporters, and energy.",
                    LABEL_FONT, Color.rgb(255, 80, 80)));
        }

        // --- Enemy status ---
        int enemyOnBoard = target.getEnemyBoardingParties();
        String enemyText = enemyOnBoard == 0
                ? "No friendly troops currently aboard " + target.getName()
                : target.getEnemyTroops() + " already aboard " + target.getName();
        Label enemyLabel = styledLabel(enemyText, SMALL_FONT, Color.rgb(150, 150, 200));
        VBox enemyBox = new VBox(4,
                styledLabel("CURRENT BOARDING STATUS", SECTION_FONT, Color.rgb(160, 160, 255)),
                enemyLabel);
        enemyBox.setStyle(SECTION_BG);

        // --- Buttons ---
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(CANCEL_STYLE);
        cancelBtn.setOnAction(e -> close()); // results stay -1

        confirmBtn.setOnAction(e -> {
            resultNormal    = normalSpinner.getValue();
            resultCommandos = commandoSpinner.getValue();
            close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(8, spacer, cancelBtn, confirmBtn);
        buttonRow.setPadding(new Insets(4, 0, 0, 0));

        // --- Layout ---
        VBox root = new VBox(10, header, partyBox, enemyBox, buttonRow);
        root.setPadding(new Insets(12));
        root.setStyle(DARK_BG);

        Scene scene = new Scene(root, 480, 360);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
    }

    /** Number of normal BPs to send, or -1 if cancelled. */
    public int getNormal()    { return resultNormal; }

    /** Number of commandos to send, or -1 if cancelled. */
    public int getCommandos() { return resultCommandos; }

    /** True if the player confirmed (not cancelled). */
    public boolean isConfirmed() { return resultNormal >= 0; }

    // -------------------------------------------------------------------------

    private static Spinner<Integer> makeSpinner(int min, int max) {
        Spinner<Integer> s = new Spinner<>();
        s.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, 0));
        s.setEditable(false);
        s.setStyle("-fx-pref-width: 80;");
        s.getEditor().setStyle(SPINNER_STYLE);
        return s;
    }

    private static Label styledLabel(String text, Font font, Color color) {
        Label l = new Label(text);
        l.setFont(font);
        l.setTextFill(color);
        return l;
    }
}
