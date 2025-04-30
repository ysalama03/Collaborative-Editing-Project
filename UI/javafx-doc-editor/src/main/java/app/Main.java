package app;
import app.MainMenuUI;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("JavaFX Document Editor");

        // Initialize the Main Menu UI
        MainMenuUI mainMenu = new MainMenuUI();
        Scene scene = new Scene(mainMenu.createMainMenu(primaryStage), 800, 600);
        
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}