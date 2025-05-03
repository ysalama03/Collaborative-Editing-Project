package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalTime;
import java.util.HashMap;

import org.springframework.web.client.RestTemplate;

import app.CRDTfiles.CRDT;
import app.CRDTfiles.CRDTManager;
import app.Client.ClientWebsocket;

public class EditorUI extends Application {

    private String initialContent = ""; // Field to store the initial content
    private ClientWebsocket websocket;
    String viewerCode;
    String editorCode;
    String sessionCode;
    int userID;
    CRDTManager crdtManager;
    Boolean isNewSession = true; // Flag to check if it's a new session
    Boolean isEditor = true; // Flag to check if it's an editor
    Boolean isImported = false; // Flag to check if the document is imported

    // Define the listener as a field
    private ChangeListener<String> textChangeListener;

    /**
     * Sets the initial content of the editor.
     *
     * @param content the content to display in the editor
     */
    public void setInitialContent(String content) {
        this.initialContent = content;
    }

    public void setImported() {
        this.isImported = true; // Set the imported flag
    }

    public void setExistingCRDT(char role, int userID, String text, String documentCode) {
        
        this.isEditor = role == 'E'; // Set the editor role based on the parameter
        this.userID = userID;
        isNewSession = false; // Set to false if an existing CRDT is provided
        sessionCode = documentCode;
        initialContent = text; // Set the initial content to the provided text
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Document Editor");

        // Left Panel
        VBox leftPanel = new VBox(10);
        leftPanel.setPrefWidth(200);

        Button undoButton = new Button("Undo");
        Button redoButton = new Button("Redo");
        Button exportButton = new Button("Export");

        // Labels for Viewer Code and Editor Code
        Label viewerCodeLabel = new Label("Viewer Code: Loading...");
        Label editorCodeLabel = new Label("Editor Code: Loading...");
        Button copyViewerCodeButton = new Button("Copy");
        Button copyEditorCodeButton = new Button("Copy");

        ListView<String> activeUsersList = new ListView<>();

        leftPanel.getChildren().addAll(undoButton, redoButton, exportButton, viewerCodeLabel, copyViewerCodeButton, editorCodeLabel, copyEditorCodeButton, activeUsersList);

        // Right Panel
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setStyle("-fx-border-color: transparent; -fx-font-family: 'Consolas';");

        // Set the initial content in the TextArea
        textArea.setText(initialContent);

        // Define the listener
        textChangeListener = (observable, oldValue, newValue) -> {
            if (isEditor) {
                handleTextChange(oldValue, newValue);
            } else {
                // Revert text if a viewer tries to change it
                Platform.runLater(() -> textArea.setText(initialContent));
            }
        };

        // Add the listener to the TextArea
        textArea.textProperty().addListener(textChangeListener);

        // Add functionality to the Export button
        exportButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Document");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            File file = fileChooser.showSaveDialog(primaryStage);
            if (file != null) {
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write(initialContent);
                    System.out.println("Document saved to: " + file.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                    System.err.println("Error saving the document: " + e.getMessage());
                }
            }
        });

        // Main Layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(textArea);

        Scene scene = new Scene(mainLayout, 800, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Connect to WebSocket
        websocket = new ClientWebsocket();
        websocket.connectToWebSocket(this);

        if (isNewSession) {
            fetchDocumentCodes(viewerCodeLabel, editorCodeLabel);
            websocket.subscribeToDocument(editorCode, crdtManager);
            websocket.subscribeToActiveUsers(userID, editorCode, activeUsersList); // Subscribe to active users
            websocket.subscribeToActiveUsers(userID, viewerCode, activeUsersList); // Subscribe to active users
            websocket.sendUserId(userID, editorCode);
            sessionCode = editorCode;
        } else {
            crdtManager = new CRDTManager(userID, websocket, initialContent, false, sessionCode);
            if (isEditor) {
                editorCodeLabel.setText("Editor Code: " + sessionCode);
            } else {
                viewerCodeLabel.setText("Viewer Code: " + sessionCode);
            }
            setInitialContent(crdtManager.getDocumentText());
            websocket.subscribeToDocument(sessionCode, crdtManager);
            websocket.subscribeToActiveUsers(userID, sessionCode, activeUsersList); // Subscribe to active users
            websocket.sendUserId(userID, sessionCode);
        }

    }

    /**
     * Handles text changes in the editor, forms an Operation, and sends it.
     */
    private void handleTextChange(String oldText, String newText) {
        if (websocket == null) return;

        // Simple diff logic: find first difference
        int minLen = Math.min(oldText.length(), newText.length());
        int diffIndex = 0;
        while (diffIndex < minLen && oldText.charAt(diffIndex) == newText.charAt(diffIndex)) {
            diffIndex++;
        }
        
        if (newText.length() > oldText.length()) {
            // Insert operation - process each character individually
            String inserted = newText.substring(diffIndex, diffIndex + (newText.length() - oldText.length()));
            
            // Process each character in the inserted string
            for (int i = 0; i < inserted.length(); i++) {
                char c = inserted.charAt(i);
                LocalTime now = LocalTime.now();
                // Add a small increment to ensure unique timestamps for each character
                long timeAsLong = now.getHour() * 10000000L + now.getMinute() * 100000 + 
                                 now.getSecond() * 1000 + now.getNano() / 1_000_000 + i;
                
                Operation op = new Operation("insert", userID, timeAsLong, String.valueOf(c), -1, -1);
                
                // Insert at the correct position (diffIndex + i accounts for previously inserted chars)
                crdtManager.insertLocalAtPosition(c, diffIndex + i, sessionCode);
                
                System.out.println(op.getID() + " " + op.getOp() + " " + op.getValue() + 
                                  " " + op.getTimestamp() + " " + op.getParentID() + 
                                  " " + op.getParentTimestamp());
            }
            
            Platform.runLater(() -> crdtManager.printCRDT());
            
        } else if (newText.length() < oldText.length()) {
            // Delete operation - may need to delete multiple characters
            int charsToDelete = oldText.length() - newText.length();
            
            for (int i = 0; i < charsToDelete; i++) {
                // For each character to delete, we need to find the position
                // Note: Always delete at diffIndex since the positions shift after each deletion
                CRDT.CharacterId idToDelete = crdtManager.getCRDT().getCharacterIdAtPosition(diffIndex);
                
                if (idToDelete != null) {
                    LocalTime now = LocalTime.now();
                    long timeAsLong = now.getHour() * 10000000L + now.getMinute() * 100000 + 
                                     now.getSecond() * 1000 + now.getNano() / 1_000_000 + i;
                    
                    Operation op = new Operation("delete", userID, timeAsLong, 
                    String.valueOf(oldText.charAt(diffIndex + i)), -1, -1);
                    
                    crdtManager.deleteLocalAtPosition(diffIndex, sessionCode);
                    
                    System.out.println(op.getID() + " " + op.getOp() + " " + op.getValue() + 
                    " " + op.getTimestamp() + " " + op.getParentID() + 
                    " " + op.getParentTimestamp());
                } else {
                    System.out.println("No character found at position " + diffIndex);
                    break;
                }
            }
            
            Platform.runLater(() -> crdtManager.printCRDT());
        }
    }
    /**
 * Updates the document UI and CRDT with the given string.
 * @param content The new document content to display.
 */
public void updateDocumentWithString(String content) {
    // Store the new content
    this.initialContent = content;

    Platform.runLater(() -> {
        // Find the TextArea in the scene and update it
        Scene scene = Stage.getWindows().stream()
            .filter(Window::isShowing)
            .findFirst()
            .map(Window::getScene)
            .orElse(null);

        if (scene != null) {
            TextArea textArea = (TextArea) scene.lookup(".text-area");
            if (textArea != null) {
                // Temporarily remove the listener to prevent change events
                textArea.textProperty().removeListener(textChangeListener);
                
                // Update the TextArea content with the full document text
                textArea.setText(content);
                
                // Debug log
                System.out.println("UI updated with text: " + content);
                
                // Re-add the listener
                textArea.textProperty().addListener(textChangeListener);
            } else {
                System.err.println("TextArea not found in scene!");
            }
        } else {
            System.err.println("No active window scene found!");
        }
    });
}

    /**
     * Fetches the viewer and editor codes from the server and updates the labels.
     *
     * @param viewerCodeLabel the label to display the viewer code
     * @param editorCodeLabel the label to display the editor code
     */
    private void fetchDocumentCodes(Label viewerCodeLabel, Label editorCodeLabel) {
        try {
            // Create a RestTemplate instance
            RestTemplate restTemplate = new RestTemplate();
            
            // Define the server endpoint for creating a new document
            String serverUrl = "http://localhost:8080/createDocument";

            // Send a POST request to the server and receive the response as a Map
            HashMap<String, Object> response = restTemplate.postForObject(serverUrl, null, HashMap.class);

            // Extract the viewer and editor codes from the response
            userID = (int) response.get("userId");
            viewerCode = (String) response.get("viewerCode");
            editorCode = (String) response.get("editorCode");
            System.out.println("User ID: " + userID);
            System.out.println("Viewer Code: " + viewerCode);
            System.out.println("Editor Code: " + editorCode);

            // Update the labels with the received codes
            viewerCodeLabel.setText("Viewer Code: " + viewerCode);
            editorCodeLabel.setText("Editor Code: " + editorCode);

            if (isImported) {
                crdtManager = new CRDTManager(userID, websocket, initialContent, true, editorCode);   
            }
            else
            {
                crdtManager = new CRDTManager(userID, websocket);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.println("Error: " + ex.getMessage());
            viewerCodeLabel.setText("Viewer Code: Error");
            editorCodeLabel.setText("Editor Code: Error");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}