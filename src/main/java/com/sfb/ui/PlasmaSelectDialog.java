package com.sfb.ui;

import com.sfb.objects.Ship;
import com.sfb.weapons.PlasmaLauncher;
import com.sfb.weapons.Weapon;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Modal dialog for selecting which armed plasma launcher to use.
 * Shows all armed launchers on the ship. Player clicks one to select it;
 * the dialog closes and the selection is available via getSelectedLauncher().
 */
public class PlasmaSelectDialog extends Stage {

    private static final String DARK_BG   = "-fx-background-color: #0d0d22;";
    private static final String BTN_STYLE =
            "-fx-background-color: #2a1a0a; -fx-text-fill: #ffaa66; " +
            "-fx-border-color: #664422; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 11; -fx-cursor: hand;";
    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font LABEL_FONT  = Font.font("Monospaced", 10);

    private PlasmaLauncher selectedLauncher = null;
    private boolean        selectedPseudo   = false;

    public PlasmaSelectDialog(Window owner, Ship ship) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Select Plasma Launcher — " + ship.getName());

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle(DARK_BG);

        Label heading = new Label("Select a plasma launcher to fire:");
        heading.setFont(HEADER_FONT);
        heading.setTextFill(Color.rgb(255, 180, 100));
        root.getChildren().add(heading);

        boolean anyReal   = false;
        boolean anyPseudo = false;

        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof PlasmaLauncher)) continue;
            PlasmaLauncher launcher = (PlasmaLauncher) w;
            if (!launcher.isFunctional()) continue;

            if (launcher.isArmed()) {
                anyReal = true;
                String label = String.format("  %-14s  Plasma-%-2s  strength %d",
                        launcher.getName() != null ? launcher.getName() : "Plasma",
                        launcher.getPlasmaType() != null ? launcher.getPlasmaType().toString() : "?",
                        launcher.getArmedStrength());
                Button btn = new Button(label);
                btn.setFont(LABEL_FONT);
                btn.setStyle(BTN_STYLE);
                btn.setMaxWidth(Double.MAX_VALUE);
                final PlasmaLauncher chosen = launcher;
                btn.setOnAction(e -> { selectedLauncher = chosen; selectedPseudo = false; close(); });
                root.getChildren().add(btn);
            }

            if (launcher.canLaunchPseudo()) {
                anyPseudo = true;
            }
        }

        if (!anyReal) {
            Label none = new Label("No armed plasma launchers available.");
            none.setFont(LABEL_FONT);
            none.setTextFill(Color.rgb(180, 80, 80));
            root.getChildren().add(none);
        }

        if (anyPseudo) {
            Label pseudoHeading = new Label("Pseudo plasma:");
            pseudoHeading.setFont(HEADER_FONT);
            pseudoHeading.setTextFill(Color.rgb(150, 200, 255));
            root.getChildren().add(pseudoHeading);

            for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
                if (!(w instanceof PlasmaLauncher)) continue;
                PlasmaLauncher launcher = (PlasmaLauncher) w;
                if (!launcher.isFunctional() || !launcher.canLaunchPseudo()) continue;

                String pseudoStyle = BTN_STYLE.replace("#2a1a0a", "#0a1a2a").replace("#ffaa66", "#88ccff")
                        .replace("#664422", "#224466");
                String label = String.format("  %-14s  Pseudo Plasma-%-2s  [DECOY]",
                        launcher.getName() != null ? launcher.getName() : "Plasma",
                        launcher.getLauncherType() != null ? launcher.getLauncherType().toString() : "?");
                Button btn = new Button(label);
                btn.setFont(LABEL_FONT);
                btn.setStyle(pseudoStyle);
                btn.setMaxWidth(Double.MAX_VALUE);
                final PlasmaLauncher chosen = launcher;
                btn.setOnAction(e -> { selectedLauncher = chosen; selectedPseudo = true; close(); });
                root.getChildren().add(btn);
            }
        }

        Button cancel = new Button("Cancel");
        cancel.setStyle(BTN_STYLE);
        cancel.setOnAction(e -> close());
        HBox footer = new HBox(cancel);
        footer.setPadding(new Insets(6, 0, 0, 0));
        root.getChildren().add(footer);

        Scene scene = new Scene(root);
        scene.setFill(Color.rgb(13, 13, 34));
        setScene(scene);
        setResizable(false);
    }

    public PlasmaLauncher getSelectedLauncher() {
        return selectedLauncher;
    }

    public boolean isSelectedPseudo() {
        return selectedPseudo;
    }
}
