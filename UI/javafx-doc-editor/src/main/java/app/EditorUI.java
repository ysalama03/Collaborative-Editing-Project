package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

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

    /**
     * Sets the initial content of the editor.
     *
     * @param content the content to display in the editor
     */
    public void setInitialContent(String content) {
        this.initialContent = content;
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
        activeUsersList.getItems().addAll("User1", "User2", "(you)", "User3");

        leftPanel.getChildren().addAll(undoButton, redoButton, exportButton, viewerCodeLabel, copyViewerCodeButton, editorCodeLabel, copyEditorCodeButton, activeUsersList);

        // Right Panel
        TextArea textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setStyle("-fx-border-color: transparent; -fx-font-family: 'Consolas';");

        // Set the initial content in the TextArea
        textArea.setText(initialContent);

        // Track previous text for diffing
        final String[] previousText = {initialContent};

        // Add listener for text changes
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isEditor) {
                handleTextChange(oldValue, newValue);
                previousText[0] = newValue;
            } else {
                // Revert text if a viewer tries to change it
                Platform.runLater(() -> textArea.setText(previousText[0]));
            }
        });
        

        // Main Layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(textArea);

        Scene scene = new Scene(mainLayout, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        // Connect to websocket
        websocket = new ClientWebsocket();
        websocket.connectToWebSocket(this);

        if (isNewSession) {
            // Fetch Viewer and Editor Codes from the Server
            fetchDocumentCodes(viewerCodeLabel, editorCodeLabel);
            //websocket.subscribeToDocument(viewerCode, crdtManager);
            websocket.subscribeToDocument(editorCode, crdtManager);
        } else {
            crdtManager = new CRDTManager(userID, websocket, initialContent);
            setInitialContent(crdtManager.getDocumentText());
            websocket.subscribeToDocument(sessionCode, crdtManager);
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

        Operation op = null;
        String documentCode = viewerCode;

        LocalTime now = LocalTime.now();
        long timeAsLong = now.getHour() * 10000000L + now.getMinute() * 100000 + now.getSecond() * 1000 + now.getNano() / 1_000_000;
        if (newText.length() > oldText.length()) {
            // Insert operation
            String inserted = newText.substring(diffIndex, diffIndex + (newText.length() - oldText.length()));
            op = new Operation("insert", userID, timeAsLong, inserted, -1, -1);
            crdtManager.insertLocalAtPosition(inserted.charAt(0), diffIndex, editorCode);
            Platform.runLater(() -> crdtManager.printCRDT());
            System.out.println(op.getID() + " " + op.getOp() + " " + op.getValue() + " " + op.getTimestamp() + " " + op.getParentID() + " " + op.getParentTimestamp());
        } else if (newText.length() < oldText.length()) {
            // Delete operation
            int caretPos = diffIndex; // this is where the deletion occurred
            CRDT.CharacterId idToDelete = crdtManager.getCRDT().getCharacterIdAtPosition(caretPos);
            
            if (idToDelete != null) {
                op = new Operation("delete", userID, timeAsLong, oldText.substring(caretPos, caretPos + 1), -1, -1);
                crdtManager.deleteLocalAtPosition(diffIndex, documentCode);
                Platform.runLater(() -> crdtManager.printCRDT());
            } else {
                System.out.println("No character found at position " + caretPos);
                return;
            }

            Platform.runLater(() -> crdtManager.printCRDT());
            System.out.println(op.getID() + " " + op.getOp() + " " + op.getValue() + " " + op.getTimestamp() + " " + op.getParentID() + " " + op.getParentTimestamp());
        } else {
            // No change or replacement (not handled here)
            return;
        }

    }

    /**
     * Updates the document UI and CRDT with the given string.
     * @param content The new document content to display.
     */
    public void updateDocumentWithString(String content) {
        this.initialContent = content;
        // Update the TextArea if it exists
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
                    textArea.setText(content);
                }
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

            crdtManager = new CRDTManager(userID, websocket);

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