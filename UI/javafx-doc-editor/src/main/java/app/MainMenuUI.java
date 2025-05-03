package app;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import app.CRDTfiles.CRDT;

public class MainMenuUI {

    // Method to check server availability
    private boolean isServerAvailable() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            // Use a simple health check endpoint or any existing endpoint
            restTemplate.getForObject("http://localhost:8080/test", String.class);
            return true;
        } catch (ResourceAccessException e) {
            // Server is not available
            return false;
        } catch (Exception e) {
            // Other exceptions might indicate different issues, but we'll treat them as server unavailable
            return false;
        }
    }

    // Method to show server unavailable alert
    private void showServerUnavailableAlert() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Server Error");
        alert.setHeaderText("Server Unavailable");
        alert.setContentText("The server is currently unavailable. Please ensure the server is running and try again.");
        alert.showAndWait();
    }

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

        // Open EditorUI when clicked, but first check if server is available
        newDocButton.setOnAction(e -> {
            // Check server availability before proceeding
            if (!isServerAvailable()) {
                showServerUnavailableAlert();
                return;
            }

            // Server is available, proceed to open EditorUI
            try {
                EditorUI editor = new EditorUI();
                editor.start(primaryStage);
            } catch (Exception ex) {
                ex.printStackTrace();
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Error");
                errorAlert.setHeaderText("Failed to Open Editor");
                errorAlert.setContentText("An error occurred while opening the editor. Please try again.");
                errorAlert.showAndWait();
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

        browseButton.setOnAction(e -> {
            // First check if the server is available
            if (!isServerAvailable()) {
                showServerUnavailableAlert();
                return;
            }
            
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open Text File");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));

            // Open the file chooser dialog
            File selectedFile = fileChooser.showOpenDialog(primaryStage);
            if (selectedFile != null) {
                try {
                    // Read the content of the file
                    String fileContent = Files.readString(selectedFile.toPath());

                    // Open the EditorUI and pass the file content
                    EditorUI editor = new EditorUI();
                    editor.setInitialContent(fileContent); // Pass the file content to EditorUI
                    editor.setImported();
                    editor.start(primaryStage);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                    errorAlert.setTitle("Error");
                    errorAlert.setHeaderText("Failed to Open File");
                    errorAlert.setContentText("An error occurred while reading the file. Please try again.");
                    errorAlert.showAndWait();
                }
            }
        });

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

        joinButton.setOnAction(e -> {
            // First check if the server is available
            if (!isServerAvailable()) {
                showServerUnavailableAlert();
                return;
            }

            String sessionCode = sessionField.getText();
            if (sessionCode.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Warning");
                alert.setHeaderText("Session Code Required");
                alert.setContentText("Please enter a session code to join.");
                alert.showAndWait();
            } else {
                try {
                    // Create a RestTemplate instance
                    RestTemplate restTemplate = new RestTemplate();

                    // Define the server endpoint for creating a new document
                    String serverUrl = "http://localhost:8080/JoinDocument";

                    // Send a GET request to the server and receive the response as a Map
                    HashMap<String, String> response = restTemplate.getForObject(serverUrl + "/" + sessionCode, HashMap.class);

                    if ("error".equals(response.keySet().iterator().next())) {
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Failed to Join Session");
                        alert.setContentText("An error occurred while joining the session. Please check the code and try again.");
                        alert.showAndWait();
                        return;
                    }

                    // Extract userId and role from the response
                    String key = response.keySet().iterator().next();
                    char role = key.charAt(0); // First character indicates role (V for viewer, E for editor)
                    int userId = Integer.parseInt(key.substring(1)); // Remaining part is the userId

                    String text = response.values().iterator().next();

                    System.out.println("User ID: " + userId);
                    System.out.println("CRDT: " + text);

                    EditorUI editor = new EditorUI();
                    editor.setExistingCRDT(role, userId, text, sessionCode); // Replace null with actual CRDT conversion if needed
                    try {
                        editor.start(primaryStage);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Failed to Open Editor");
                        alert.setContentText("An error occurred while opening the editor. Please try again.");
                        alert.showAndWait();
                    }
                } catch (ResourceAccessException ex) {
                    // Handle server down or connection issues
                    showServerUnavailableAlert();
                    ex.printStackTrace();
                } catch (Exception ex) {
                    // Handle other exceptions
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("An Unexpected Error Occurred");
                    alert.setContentText("An error occurred while processing your request. Please try again later.");
                    alert.showAndWait();
                    ex.printStackTrace();
                }
            }
        });

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