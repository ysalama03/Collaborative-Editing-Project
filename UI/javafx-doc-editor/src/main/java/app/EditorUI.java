package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class EditorUI extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Document Editor");

        // Left Panel
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(200);

        Button undoButton = new Button("Undo");
        Button redoButton = new Button("Redo");
        Button exportButton = new Button("Export");

        Label viewerCodeLabel = new Label("Viewer Code: ABC123");
        Button copyViewerCodeButton = new Button("Copy");

        Label editorCodeLabel = new Label("Editor Code: XYZ789");
        Button copyEditorCodeButton = new Button("Copy");

        ListView<String> activeUsersList = new ListView<>();
        activeUsersList.getItems().addAll("User1", "User2", "(you)", "User3");
        
        leftPanel.getChildren().addAll(undoButton, redoButton, exportButton, viewerCodeLabel, copyViewerCodeButton, editorCodeLabel, copyEditorCodeButton, activeUsersList);

        // Right Panel
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setStyle("-fx-border-color: transparent; -fx-font-family: 'Consolas';");
        
        // Main Layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(textArea);

        Scene scene = new Scene(mainLayout, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}