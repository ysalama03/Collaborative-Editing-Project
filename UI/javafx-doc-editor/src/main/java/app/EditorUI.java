package app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.springframework.web.client.RestTemplate;

public class EditorUI extends Application {

    private String initialContent = ""; // Field to store the initial content

    /**
     * Sets the initial content of the editor.
     *
     * @param content the content to display in the editor
     */
    public void setInitialContent(String content) {
        this.initialContent = content;
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

        // Main Layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setLeft(leftPanel);
        mainLayout.setCenter(textArea);

        Scene scene = new Scene(mainLayout, 800, 600);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.show();

        // Fetch Viewer and Editor Codes from the Server
        fetchDocumentCodes(viewerCodeLabel, editorCodeLabel);
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

            /////////////////// test connection /////////////////
            // Test the connection to the server
            System.out.println("Testing connection to the server...");
            String testUrl = "http://localhost:8080/test";
            String testResponse = restTemplate.getForObject(testUrl, String.class);
            System.out.println("Test Response: " + testResponse);
            /////////////////////////////////////////////////////
            
            // Define the server endpoint for creating a new document
            String serverUrl = "http://localhost:8080/createDocument";

            // Send a POST request to the server and receive the response as a Map
            String response = restTemplate.postForObject(serverUrl, null, String.class);

            // Extract the viewer and editor codes from the response
            String[] codes = response.split(" ");
            String viewerCode = codes[0];
            String editorCode = codes[1];

            // Update the labels with the received codes
            viewerCodeLabel.setText("Viewer Code: " + viewerCode);
            editorCodeLabel.setText("Editor Code: " + editorCode);
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