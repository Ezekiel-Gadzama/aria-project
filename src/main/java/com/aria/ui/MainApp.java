package com.aria.ui;

import com.aria.core.ApplicationInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    @Override
    public void init() throws Exception {
        super.init();
        // Initialize application before JavaFX starts
        ApplicationInitializer.initialize();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/UserRegistration.fxml"));
        Parent root = loader.load();

        UserRegistrationController controller = loader.getController();
        controller.setPrimaryStage(primaryStage);

        primaryStage.setTitle("ARIA - User Registration");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}