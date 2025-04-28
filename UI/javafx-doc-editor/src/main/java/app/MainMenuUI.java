package app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class MainMenuUI {

    public Parent createMainMenu(Stage primaryStage) {
        // Section 1: New Doc
        VBox newDocSection = new VBox(10);
        newDocSection.setAlignment(Pos.CENTER);

        Rectangle newDocIcon = new Rectangle(48, 48, Color.TRANSPARENT);
        newDocIcon.setStroke(Color.BLACK);
        newDocIcon.setArcWidth(8);
        newDocIcon.setArcHeight(8);
        // Draw a plus sign
        Pane plus = new Pane();
        plus.setPrefSize(48, 48);
        plus.getChildren().addAll(
            line(24, 12, 24, 36),
            line(12, 24, 36, 24)
        );
        StackPane newDocIconStack = new StackPane(newDocIcon, plus);

        Button newDocButton = new Button("New Doc.");
        newDocButton.setMaxWidth(Double.MAX_VALUE);
        newDocButton.getStyleClass().add("main-menu-btn");

        // Open EditorUI when clicked
        newDocButton.setOnAction(e -> {
            EditorUI editor = new EditorUI();
            try {
                editor.start(primaryStage);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        newDocSection.getChildren().addAll(newDocIconStack, newDocButton);

        // Section 2: Browse
        VBox browseSection = new VBox(10);
        browseSection.setAlignment(Pos.CENTER);

        Rectangle browseIcon = new Rectangle(48, 48, Color.TRANSPARENT);
        browseIcon.setStroke(Color.BLACK);
        browseIcon.setArcWidth(8);
        browseIcon.setArcHeight(8);
        StackPane browseIconStack = new StackPane(browseIcon);

        Button browseButton = new Button("Browse..");
        browseButton.setMaxWidth(Double.MAX_VALUE);
        browseButton.getStyleClass().add("main-menu-btn");

        browseSection.getChildren().addAll(browseIconStack, browseButton);

        // Section 3: Session Code + Join
        VBox sessionSection = new VBox(10);
        sessionSection.setAlignment(Pos.CENTER);

        Label sessionLabel = new Label("Session Code");
        TextField sessionField = new TextField();
        sessionField.setPromptText("Enter code");
        sessionField.setPrefWidth(120);

        Button joinButton = new Button("Join");
        joinButton.getStyleClass().add("main-menu-btn");

        HBox joinBox = new HBox(5, sessionField, joinButton);
        joinBox.setAlignment(Pos.CENTER);

        sessionSection.getChildren().addAll(sessionLabel, joinBox);

        // Main layout
        HBox mainBox = new HBox(60, newDocSection, browseSection, sessionSection);
        mainBox.setAlignment(Pos.CENTER);

        VBox root = new VBox(mainBox);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40));
        root.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));

        return root;
    }

    // Helper to draw a line for the plus sign
    private javafx.scene.shape.Line line(double startX, double startY, double endX, double endY) {
        javafx.scene.shape.Line l = new javafx.scene.shape.Line(startX, startY, endX, endY);
        l.setStroke(Color.BLACK);
        l.setStrokeWidth(3);
        return l;
    }
}