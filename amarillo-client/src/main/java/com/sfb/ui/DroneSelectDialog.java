package com.sfb.ui;

import java.util.List;

import com.sfb.objects.Drone;
import com.sfb.objects.Ship;
import com.sfb.weapons.DroneRack;
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
 * Modal dialog for selecting which drone to launch from which rack.
 * Shows all launchable racks and their loaded drones. Player clicks
 * a drone to select it; the dialog closes and the selection is available
 * via getSelectedRack() / getSelectedDrone().
 */
public class DroneSelectDialog extends Stage {

    private static final String DARK_BG  = "-fx-background-color: #0d0d22;";
    private static final String BTN_STYLE =
            "-fx-background-color: #1a2a4a; -fx-text-fill: #88bbff; " +
            "-fx-border-color: #334466; -fx-border-radius: 3; -fx-background-radius: 3; " +
            "-fx-font-size: 11; -fx-cursor: hand;";
    private static final Font HEADER_FONT = Font.font("Monospaced", FontWeight.BOLD, 11);
    private static final Font LABEL_FONT  = Font.font("Monospaced", 10);

    private DroneRack selectedRack  = null;
    private Drone     selectedDrone = null;

    public DroneSelectDialog(Window owner, Ship ship) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setTitle("Select Drone — " + ship.getName());

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle(DARK_BG);

        Label heading = new Label("Select a drone to launch:");
        heading.setFont(HEADER_FONT);
        heading.setTextFill(Color.rgb(180, 200, 255));
        root.getChildren().add(heading);

        boolean anyAvailable = false;

        for (Weapon w : ship.getWeapons().fetchAllWeapons()) {
            if (!(w instanceof DroneRack)) continue;
            DroneRack rack = (DroneRack) w;
            if (!rack.isFunctional() || rack.isEmpty() || !rack.canFire()) continue;

            anyAvailable = true;

            Label rackLabel = new Label(rack.getName() != null ? rack.getName() : "Drone Rack");
            rackLabel.setFont(HEADER_FONT);
            rackLabel.setTextFill(Color.rgb(140, 200, 140));
            root.getChildren().add(rackLabel);

            List<Drone> ammo = rack.getAmmo();
            for (int i = 0; i < ammo.size(); i++) {
                Drone drone = ammo.get(i);
                String label = "  [" + (i + 1) + "]  " + drone.getDroneType()
                        + "  dmg " + drone.getWarheadDamage()
                        + "  spd " + drone.getSpeed()
                        + "  end " + drone.getEndurance();
                Button btn = new Button(label);
                btn.setFont(LABEL_FONT);
                btn.setStyle(BTN_STYLE);
                btn.setMaxWidth(Double.MAX_VALUE);

                final DroneRack chosenRack  = rack;
                final Drone     chosenDrone = drone;
                btn.setOnAction(e -> {
                    selectedRack  = chosenRack;
                    selectedDrone = chosenDrone;
                    close();
                });
                root.getChildren().add(btn);
            }
        }

        if (!anyAvailable) {
            Label none = new Label("No drones available to launch.");
            none.setFont(LABEL_FONT);
            none.setTextFill(Color.rgb(180, 80, 80));
            root.getChildren().add(none);
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

    public DroneRack getSelectedRack() {
        return selectedRack;
    }

    public Drone getSelectedDrone() {
        return selectedDrone;
    }
}
