package com.sfb.ui;

import java.util.List;

import com.sfb.objects.AdminShuttle;
import com.sfb.objects.GASShuttle;
import com.sfb.objects.HTSShuttle;
import com.sfb.objects.Shuttle;
import com.sfb.objects.Ship;
import com.sfb.systemgroups.ShuttleBay;
import com.sfb.systemgroups.Shuttles;

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
 * Dialog for selecting which shuttle bay and shuttle to launch.
 * Only shows admin-type shuttles (not suicide shuttles or scatter packs,
 * which have their own launch flows).
 */
public class ShuttleSelectDialog extends Dialog<ButtonType> {

    private ShuttleBay  selectedBay     = null;
    private Shuttle     selectedShuttle = null;

    public ShuttleSelectDialog(Window owner, Ship ship, int currentImpulse) {
        initOwner(owner);
        setTitle("Launch Shuttle");

        Font sectionFont = Font.font("Monospaced", FontWeight.BOLD, 11);
        Font labelFont   = Font.font("Monospaced", FontWeight.NORMAL, 10);
        String sectionBg = "-fx-background-color: #1a2233; -fx-padding: 8;";

        VBox root = new VBox(10);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #0d1520;");

        Shuttles shuttles = ship.getShuttles();
        List<ShuttleBay> bays = shuttles.getBays();

        ToggleGroup group = new ToggleGroup();
        boolean anyAvailable = false;

        for (int b = 0; b < bays.size(); b++) {
            ShuttleBay bay = bays.get(b);
            VBox bayBox = new VBox(4);
            bayBox.setStyle(sectionBg);

            String cooldownNote = bay.canLaunch(currentImpulse) ? "" : "  (on cooldown)";
            Label bayLabel = new Label("Bay " + (b + 1) + cooldownNote);
            bayLabel.setFont(sectionFont);
            bayLabel.setTextFill(Color.rgb(100, 180, 255));
            bayBox.getChildren().add(bayLabel);

            List<Shuttle> inventory = bay.getInventory();
            if (inventory.isEmpty()) {
                Label empty = new Label("  (empty)");
                empty.setFont(labelFont);
                empty.setTextFill(Color.rgb(100, 100, 100));
                bayBox.getChildren().add(empty);
            } else {
                for (Shuttle shuttle : inventory) {
                    // Only show standard shuttles here
                    if (!(shuttle instanceof AdminShuttle)
                            && !(shuttle instanceof GASShuttle)
                            && !(shuttle instanceof HTSShuttle))
                        continue;

                    String profile = shuttleProfile(shuttle);
                    RadioButton rb = new RadioButton(shuttle.getName() + "  " + profile);
                    rb.setFont(labelFont);
                    rb.setTextFill(Color.WHITE);
                    rb.setToggleGroup(group);
                    rb.setDisable(!bay.canLaunch(currentImpulse));
                    rb.setUserData(new Object[]{ bay, shuttle });
                    bayBox.getChildren().add(rb);
                    anyAvailable = true;
                }
            }
            root.getChildren().add(bayBox);
        }

        if (!anyAvailable) {
            Label none = new Label("No shuttles available to launch.");
            none.setFont(labelFont);
            none.setTextFill(Color.rgb(180, 80, 80));
            root.getChildren().add(none);
        }

        getDialogPane().setContent(root);
        getDialogPane().getStylesheets().add(
                getClass().getResource("/dark-theme.css") != null
                        ? getClass().getResource("/dark-theme.css").toExternalForm() : "");
        getDialogPane().setStyle("-fx-background-color: #0d1520;");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        setResultConverter(bt -> {
            if (bt == ButtonType.OK && group.getSelectedToggle() != null) {
                Object[] data = (Object[]) group.getSelectedToggle().getUserData();
                selectedBay     = (ShuttleBay) data[0];
                selectedShuttle = (Shuttle)    data[1];
            }
            return bt;
        });
    }

    public ShuttleBay  getSelectedBay()     { return selectedBay; }
    public Shuttle     getSelectedShuttle() { return selectedShuttle; }

    private String shuttleProfile(Shuttle s) {
        return String.format("hull %d  spd %d", s.getHull(), s.getMaxSpeed());
    }
}
