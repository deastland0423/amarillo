package com.sfb.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sfb.objects.Ship;
import com.sfb.properties.SystemTarget;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Modal dialog for conducting a Hit &amp; Run boarding raid.
 *
 * <p>
 * The player chooses how many boarding parties to send (up to the
 * transporter/party/energy limit) and assigns each to a different system
 * on the target ship.
 *
 * <p>
 * After the dialog closes, call {@link #getTargetSystems()} to retrieve
 * the assignments. A null return means the player cancelled.
 */
public class HitAndRunDialog extends Stage {

    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 13);
    private static final Font SECTION_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font LABEL_FONT = Font.font("Monospaced", 11);
    private static final Font SMALL_FONT = Font.font("Monospaced", 10);
    private static final String DARK_BG = "-fx-background-color: #0d0d22;";
    private static final String SECTION_BG = "-fx-background-color: #111130; -fx-background-radius: 4; -fx-padding: 8;";
    private static final String BTN_STYLE = "-fx-background-color: #1a3a1a; -fx-text-fill: #88ff88; " +
            "-fx-border-color: #336633; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String CANCEL_STYLE = "-fx-background-color: #3a1a1a; -fx-text-fill: #ff8888; " +
            "-fx-border-color: #663333; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 12; -fx-font-weight: bold; -fx-cursor: hand;";

    // null = cancelled, empty = confirmed with 0 parties (shouldn't happen)
    private List<SystemTarget> targetSystems = null;

    /**
     * @param owner      The owning stage.
     * @param actingShip The ship sending boarding parties.
     * @param target     The ship being raided.
     * @param available  Pre-computed list of targetable systems on the target ship.
     */
    public HitAndRunDialog(Stage owner, Ship actingShip, Ship target,
            List<SystemTarget> available) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.DECORATED);
        setTitle("Hit & Run Raid");
        setResizable(false);

        int maxParties = Math.min(
                actingShip.getCrew().getAvailableBoardingParties(),
                Math.min(
                        actingShip.getTransporters().getAvailableTrans(),
                        Math.min(
                                actingShip.getTransporters().availableUses(),
                                available.size()))); // can't target same system twice

        // --- Header ---
        Label title = styledLabel(
                actingShip.getName() + "  →  " + target.getName(),
                HEADER_FONT, Color.WHITE);

        Label stats = styledLabel(
                String.format("Boarding parties: %d    Transporters: %d    Energy uses available: %d",
                        actingShip.getCrew().getAvailableBoardingParties(),
                        actingShip.getTransporters().getAvailableTrans(),
                        actingShip.getTransporters().availableUses()),
                SMALL_FONT, Color.rgb(150, 200, 150));

        VBox header = new VBox(4, title, stats);
        header.setStyle(SECTION_BG);

        // --- Party builder ---
        // partyRows holds one ChoiceBox per boarding party currently added
        List<ChoiceBox<SystemTarget>> partyChoices = new ArrayList<>();
        GridPane partyGrid = new GridPane();
        partyGrid.setHgap(12);
        partyGrid.setVgap(6);

        Label validationLabel = styledLabel("", SMALL_FONT, Color.rgb(255, 80, 80));
        Button confirmBtn = new Button("Confirm Raid");
        confirmBtn.setStyle(BTN_STYLE);
        confirmBtn.setDisable(true);

        // Rebuild the grid whenever a party is added/removed or a selection changes
        Runnable refresh = () -> {
            partyGrid.getChildren().clear();
            Set<SystemTarget> taken = new HashSet<>();
            for (int i = 0; i < partyChoices.size(); i++) {
                SystemTarget sel = partyChoices.get(i).getValue();
                if (sel != null)
                    taken.add(sel);
            }

            // Check for duplicates
            Set<SystemTarget> seen = new HashSet<>();
            boolean hasDuplicate = false;
            for (ChoiceBox<SystemTarget> cb : partyChoices) {
                if (cb.getValue() != null) {
                    if (!seen.add(cb.getValue())) {
                        hasDuplicate = true;
                        break;
                    }
                }
            }

            boolean anyUnset = partyChoices.stream().anyMatch(cb -> cb.getValue() == null);
            boolean canConfirm = !partyChoices.isEmpty() && !hasDuplicate && !anyUnset;

            validationLabel.setText(hasDuplicate
                    ? "Each boarding party must target a different system."
                    : "");
            confirmBtn.setDisable(!canConfirm);

            for (int i = 0; i < partyChoices.size(); i++) {
                Label num = styledLabel("Party " + (i + 1) + ":", LABEL_FONT, Color.rgb(200, 200, 100));
                partyGrid.add(num, 0, i);
                partyGrid.add(partyChoices.get(i), 1, i);
            }
        };

        // Add/Remove party buttons
        Button addParty = arrowButton("+  Add boarding party");
        Button removeParty = arrowButton("−  Remove boarding party");
        removeParty.setDisable(true);

        addParty.setOnAction(e -> {
            if (partyChoices.size() >= maxParties)
                return;
            ChoiceBox<SystemTarget> cb = new ChoiceBox<>();
            cb.getItems().addAll(available);
            cb.setStyle("-fx-background-color: #1a1a3a; -fx-mark-color: #f3f4f7;");
            // Original colros, but the blue mark color is hard to see against the dark
            // background
            // cb.setStyle("-fx-background-color: #1a1a3a; -fx-mark-color: #88aaff;"); //
            cb.valueProperty().addListener((obs, old, val) -> refresh.run());
            partyChoices.add(cb);
            removeParty.setDisable(false);
            if (partyChoices.size() >= maxParties)
                addParty.setDisable(true);
            refresh.run();
        });

        removeParty.setOnAction(e -> {
            if (partyChoices.isEmpty())
                return;
            partyChoices.remove(partyChoices.size() - 1);
            removeParty.setDisable(partyChoices.isEmpty());
            addParty.setDisable(false);
            refresh.run();
        });

        HBox addRemoveRow = new HBox(8, addParty, removeParty);

        Label maxLabel = styledLabel(
                "Max parties this raid: " + maxParties + "    (one transporter use each, cost "
                        + String.format("%.1f", maxParties * 0.2) + " energy)",
                SMALL_FONT, Color.rgb(120, 120, 120));

        VBox partyBox = new VBox(8,
                styledLabel("BOARDING PARTIES", SECTION_FONT, Color.rgb(255, 160, 80)),
                maxLabel,
                addRemoveRow,
                partyGrid,
                validationLabel);
        partyBox.setStyle(SECTION_BG);

        if (maxParties == 0) {
            addParty.setDisable(true);
            partyBox.getChildren().add(styledLabel(
                    "No raid possible — check boarding parties, transporters, and energy.",
                    LABEL_FONT, Color.rgb(255, 80, 80)));
        }

        // --- Button row ---
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(CANCEL_STYLE);
        cancelBtn.setOnAction(e -> close()); // targetSystems stays null

        confirmBtn.setOnAction(e -> {
            targetSystems = new ArrayList<>();
            for (ChoiceBox<SystemTarget> cb : partyChoices) {
                targetSystems.add(cb.getValue());
            }
            close();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonRow = new HBox(8, spacer, cancelBtn, confirmBtn);
        buttonRow.setPadding(new Insets(4, 0, 0, 0));

        // --- Layout ---
        VBox root = new VBox(10, header, partyBox, buttonRow);
        root.setPadding(new Insets(12));
        root.setStyle(DARK_BG);

        Scene scene = new Scene(root, 520, 320);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
    }

    /**
     * Returns the list of system targets the player confirmed, one per boarding
     * party. Returns null if the player cancelled.
     */
    public List<SystemTarget> getTargetSystems() {
        return targetSystems;
    }

    // -------------------------------------------------------------------------

    private static Label styledLabel(String text, Font font, Color color) {
        Label l = new Label(text);
        l.setFont(font);
        l.setTextFill(color);
        return l;
    }

    private static Button arrowButton(String label) {
        Button b = new Button(label);
        b.setFont(Font.font("Monospaced", FontWeight.BOLD, 11));
        b.setStyle(
                "-fx-background-color: #1a2a3a; -fx-text-fill: #88bbff; " +
                        "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
                        "-fx-min-height: 22; -fx-cursor: hand;");
        return b;
    }
}
