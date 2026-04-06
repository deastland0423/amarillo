package com.sfb.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Startup dialog: choose local play or connect to a server.
 *
 * After showAndWait(), call getFacade() to get the chosen GameFacade.
 * Returns null if the user closed the dialog without choosing.
 */
public class ConnectDialog {

    private GameFacade facade = null;

    private final Stage dialog;

    public ConnectDialog(Stage owner) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setResizable(false);

        // ── Title ──────────────────────────────────────────────
        Label title = new Label("AMARILLO");
        title.setFont(Font.font("Monospaced", 26));
        title.setTextFill(Color.rgb(140, 190, 255));

        Label sub = new Label("Star Fleet Battles");
        sub.setFont(Font.font("Monospaced", 12));
        sub.setTextFill(Color.rgb(80, 100, 140));

        // ── Local play ─────────────────────────────────────────
        Button localBtn = styledButton("Play Locally", "#2a4a2a", "#70d070");
        Label localDesc = new Label("Single machine — no server required");
        localDesc.setTextFill(Color.rgb(80, 120, 80));
        localDesc.setFont(Font.font("Monospaced", 10));

        localBtn.setOnAction(e -> {
            facade = new LocalGameFacade();
            dialog.close();
        });

        // ── Connect to server ──────────────────────────────────
        Label serverLabel = styledLabel("Connect to Server");

        Label urlLabel    = smallLabel("Server URL");
        TextField urlField = textField("http://localhost:8080");
        urlField.setText("http://localhost:8080");

        Label idLabel     = smallLabel("Game ID");
        TextField idField  = textField("e.g. A3BF");

        Label tokenLabel  = smallLabel("Player Token");
        TextField tokenField = textField("paste token here");

        Button connectBtn = styledButton("Connect", "#1a2a4a", "#70a0e0");
        Label  errorLabel = new Label("");
        errorLabel.setTextFill(Color.SALMON);
        errorLabel.setFont(Font.font("Monospaced", 10));

        connectBtn.setOnAction(e -> {
            String url   = urlField.getText().trim();
            String id    = idField.getText().trim().toUpperCase();
            String token = tokenField.getText().trim();
            if (url.isEmpty() || id.isEmpty() || token.isEmpty()) {
                errorLabel.setText("URL, Game ID and Token are all required.");
                return;
            }
            try {
                facade = new ServerGameClient(url, id, token);
                dialog.close();
            } catch (Exception ex) {
                errorLabel.setText("Connection failed: " + ex.getMessage());
            }
        });

        // ── Layout ─────────────────────────────────────────────
        VBox localBox = new VBox(6, localBtn, localDesc);
        localBox.setAlignment(Pos.CENTER_LEFT);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #2a2a4a;");

        VBox serverBox = new VBox(6,
                serverLabel,
                urlLabel, urlField,
                idLabel,  idField,
                tokenLabel, tokenField,
                connectBtn, errorLabel
        );
        serverBox.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(18,
                new VBox(4, title, sub),
                localBox,
                sep,
                serverBox
        );
        root.setPadding(new Insets(30));
        root.setStyle("-fx-background-color: #0d0d22; -fx-border-color: #2a2a4a; -fx-border-width: 1;");
        root.setPrefWidth(380);

        dialog.setScene(new Scene(root));
    }

    public void showAndWait() {
        dialog.showAndWait();
    }

    /** The chosen facade, or null if the dialog was dismissed. */
    public GameFacade getFacade() {
        return facade;
    }

    // ── Helpers ───────────────────────────────────────────────

    private static Button styledButton(String text, String bg, String fg) {
        Button b = new Button(text);
        b.setFont(Font.font("Monospaced", 13));
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; " +
                "-fx-border-color: %s; -fx-border-radius: 3; -fx-background-radius: 3; " +
                "-fx-cursor: hand; -fx-padding: 8 16;", bg, fg, fg));
        return b;
    }

    private static Label styledLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Monospaced", 12));
        l.setTextFill(Color.rgb(100, 140, 200));
        return l;
    }

    private static Label smallLabel(String text) {
        Label l = new Label(text);
        l.setFont(Font.font("Monospaced", 10));
        l.setTextFill(Color.rgb(80, 100, 140));
        return l;
    }

    private static TextField textField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setFont(Font.font("Monospaced", 11));
        f.setStyle("-fx-background-color: #1a1a30; -fx-text-fill: #c0d0e0; " +
                   "-fx-border-color: #334; -fx-border-radius: 3; -fx-background-radius: 3;");
        return f;
    }
}
