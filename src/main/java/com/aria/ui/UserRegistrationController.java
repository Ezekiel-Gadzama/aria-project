package com.aria.ui;

import com.aria.core.model.User;
import com.aria.service.UserService;
import com.aria.storage.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;

public class UserRegistrationController {
    @FXML private TextField phoneField;
    @FXML private TextField usernameField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextArea appGoalArea;
    @FXML private ProgressIndicator progressIndicator;

    private Stage primaryStage;
    private UserService userService;
    private DatabaseManager dbManager;

    public UserRegistrationController() {
    }
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @FXML
    private void onRegister() {
        String phone = phoneField.getText().trim();
        String username = usernameField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String appGoal = appGoalArea.getText().trim();

        if (phone.isEmpty() || username.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            showAlert("Missing Fields", "Please fill in all required fields.");
            return;
        }

        progressIndicator.setVisible(true);

        new Thread(() -> {
            try {
                // Create and register user
                User user = new User(phone, username, firstName, lastName, appGoal);
                this.userService = new UserService(new DatabaseManager(), user); // Create new DatabaseManager

                // Register user in database
                boolean success = userService.registerUser();

                javafx.application.Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    if (success) {
                        switchToTargetManagement();
                    } else {
                        showAlert("Registration Failed", "Failed to register user. Please try again.");
                    }
                });
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    progressIndicator.setVisible(false);
                    showAlert("Error", "Registration error: " + e.getMessage());
                    e.printStackTrace(); // Add this to see detailed error
                });
            }
        }).start();
    }

    private void switchToTargetManagement() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/TargetManagement.fxml"));
            Parent root = loader.load();

            TargetManagementController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            controller.setUserService(userService);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("ARIA - Target Management");

        } catch (IOException e) {
            showAlert("Error", "Failed to load target management: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}