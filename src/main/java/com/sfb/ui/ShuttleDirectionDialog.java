package com.sfb.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Window;

/**
 * Simple dialog for picking one of the 6 cardinal launch directions.
 */
public class ShuttleDirectionDialog extends Dialog<ButtonType> {

    private static final int[] CARDINALS = { 1, 5, 9, 13, 17, 21 };
    private static final String[] LABELS  = {
        " 1  (forward)",
        " 5  (right-forward)",
        " 9  (right-rear)",
        "13  (aft)",
        "17  (left-rear)",
        "21  (left-forward)"
    };

    private int selectedDirection = 0;

    public ShuttleDirectionDialog(Window owner) {
        initOwner(owner);
        setTitle("Launch Direction");

        Font labelFont = Font.font("Monospaced", FontWeight.NORMAL, 10);

        VBox root = new VBox(8);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #0d1520;");

        Label heading = new Label("Select launch direction:");
        heading.setFont(Font.font("Monospaced", FontWeight.BOLD, 11));
        heading.setTextFill(Color.rgb(100, 200, 200));
        root.getChildren().add(heading);

        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < CARDINALS.length; i++) {
            RadioButton rb = new RadioButton(LABELS[i]);
            rb.setFont(labelFont);
            rb.setTextFill(Color.WHITE);
            rb.setToggleGroup(group);
            rb.setUserData(CARDINALS[i]);
            if (i == 0) rb.setSelected(true); // default to forward
            root.getChildren().add(rb);
        }

        getDialogPane().setContent(root);
        getDialogPane().setStyle("-fx-background-color: #0d1520;");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK && group.getSelectedToggle() != null)
                selectedDirection = (int) group.getSelectedToggle().getUserData();
            return bt;
        });
    }

    /** Returns the chosen cardinal direction (1–21), or 0 if cancelled. */
    public int getSelectedDirection() { return selectedDirection; }
}
