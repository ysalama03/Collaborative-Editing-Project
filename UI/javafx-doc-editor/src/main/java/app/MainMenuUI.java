package app;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainMenuUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Main Menu");

        // Create sections
        HBox newDocSection = createNewDocSection();
        HBox browseSection = createBrowseSection();
        HBox sessionCodeSection = createSessionCodeSection();

        // Layout
        VBox mainLayout = new VBox(20, newDocSection, browseSection, sessionCodeSection);
        mainLayout.setPadding(new Insets(20));
        
        Scene scene = new Scene(mainLayout, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private HBox createNewDocSection() {
        Button newDocButton = new Button("New Doc.");
        newDocButton.setGraphic(new DocumentPlusIcon().getIcon());
        newDocButton.getStyleClass().add("flat-button");

        HBox newDocHBox = new HBox(10, newDocButton);
        newDocHBox.setPadding(new Insets(10));
        return newDocHBox;
    }

    private HBox createBrowseSection() {
        Button browseButton = new Button("Browse..");
        browseButton.setGraphic(new DocumentIcon().getIcon());
        browseButton.getStyleClass().add("flat-button");

        HBox browseHBox = new HBox(10, browseButton);
        browseHBox.setPadding(new Insets(10));
        return browseHBox;
    }

    private HBox createSessionCodeSection() {
        TextField sessionCodeField = new TextField();
        sessionCodeField.setPromptText("Session Code");
        Button joinButton = new Button("Join");
        joinButton.getStyleClass().add("flat-button");

        HBox sessionCodeHBox = new HBox(10, sessionCodeField, joinButton);
        sessionCodeHBox.setPadding(new Insets(10));
        return sessionCodeHBox;
    }

    public static void main(String[] args) {
        launch(args);
    }
}