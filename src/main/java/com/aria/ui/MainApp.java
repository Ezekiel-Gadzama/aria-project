// MainApp.java
package com.aria.ui;

import com.aria.core.model.User;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
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
        DatabaseManager dbManager = new DatabaseManager();
        User user = new User("+79869078536", "Goldenpriest", "Ezekiel", "Gadzama", "");
        UserService userService = new UserService(dbManager, user);

        // Get the controller and set the primary stage
        MainController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);
        controller.setUserService(userService);

        primaryStage.setTitle("ARIA - Automated Relationship & Interaction Assistant");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}