package com.aria.ui;

import com.aria.core.AriaOrchestrator;
import com.aria.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class ConversationController {
    @FXML private Label targetLabel;
    @FXML private TextArea conversationArea;
    @FXML private TextArea messageInput;
    @FXML private ProgressIndicator progressIndicator;

    private AriaOrchestrator orchestrator;
    private Stage primaryStage;
    private UserService userService; // Add this field

    public void setOrchestrator(AriaOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setTarget(String targetName) {
        targetLabel.setText("Conversation with " + targetName);
        // Load initial chat history or welcome message
        conversationArea.setText("Starting conversation with " + targetName + "...\n\n");
    }

    @FXML
    private void onSendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            conversationArea.appendText("You: " + message + "\n\n");
            messageInput.clear();

            // Here you would integrate with the orchestrator to send actual messages
            // For now, just simulate a response
            simulateResponse();
        }
    }

    private void simulateResponse() {
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate response time
                String response = "This is a simulated response from " + targetLabel.getText().replace("Conversation with ", "");

                javafx.application.Platform.runLater(() -> {
                    conversationArea.appendText(targetLabel.getText().replace("Conversation with ", "") + ": " + response + "\n\n");
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void onAISuggest() {
        progressIndicator.setVisible(true);
        // Simulate AI thinking
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate processing
                String suggestion = "This would be an AI-generated suggestion based on conversation context";
                javafx.application.Platform.runLater(() -> {
                    messageInput.setText(suggestion);
                    progressIndicator.setVisible(false);
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void onEndConversation() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("End Conversation");
        alert.setHeaderText("Are you sure you want to end this conversation?");
        alert.setContentText("This will return to the target management screen.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                returnToTargetManagement(); // Changed method name
            }
        });
    }

    private void returnToTargetManagement() { // Renamed method
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/TargetManagement.fxml"));
            Parent root = loader.load();

            TargetManagementController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            if (userService != null) {
                controller.setUserService(userService);
            }

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("ARIA - Target Management");

        } catch (IOException e) {
            showAlert("Error", "Failed to return to target management: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}