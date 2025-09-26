// MainApp.java
package com.aria.ui;

import com.aria.ui.MainController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/GoalInputForm.fxml"));
        Parent root = loader.load();

        // Get the controller and set the primary stage
        MainController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        primaryStage.setTitle("ARIA - Automated Relationship & Interaction Assistant");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}