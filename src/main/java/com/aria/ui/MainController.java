// MainController.java
package com.aria.ui;

import com.aria.core.AriaOrchestrator;
import com.aria.core.model.ConversationGoal;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {
    @FXML private TextField targetNameField;
    @FXML private ComboBox<String> platformComboBox;
    @FXML private TextArea contextArea;
    @FXML private TextArea outcomeArea;

    private AriaOrchestrator orchestrator;
    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void initialize() {
        platformComboBox.getItems().addAll("Telegram", "WhatsApp", "Instagram");
        platformComboBox.getSelectionModel().selectFirst();
        orchestrator = new AriaOrchestrator();
    }

    @FXML
    private void onStartConversation() {
        String targetAlias_Number = targetNameField.getText().trim();
        String platform = platformComboBox.getValue();
        String context = contextArea.getText().trim();
        String outcome = outcomeArea.getText().trim();

        if (targetAlias_Number.isEmpty() || outcome.isEmpty()) {
            showAlert("Missing Fields", "Target name and desired outcome are required before starting a conversation.");
            return;
        }

        ConversationGoal goal = new ConversationGoal();
        goal.setTargetAlias_Number(targetAlias_Number);
        goal.setPlatform(platform);
        goal.setMeetingContext(context);
        goal.setDesiredOutcome(outcome);

        orchestrator.initializeConversation(goal);

        // Switch to conversation view
        switchToConversationView(targetAlias_Number);

        // Start chat ingestion in background
        startChatIngestion();
    }

    private void switchToConversationView(String targetName) {
        try {
            // Load the conversation view FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/conversationView.fxml"));
            Parent conversationRoot = loader.load();

            // Get the conversation controller
            ConversationController conversationController = loader.getController();
            conversationController.setTarget(targetName);

            // Create new scene and replace the current one
            Scene conversationScene = new Scene(conversationRoot);
            primaryStage.setScene(conversationScene);
            primaryStage.setTitle("ARIA - Conversation with " + targetName);

        } catch (IOException e) {
            showAlert("Error", "Failed to load conversation view: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startChatIngestion() {
        new Thread(() -> {
            try {
                orchestrator.startChatIngestion();
                // Chat ingestion completed successfully
                System.out.println("Chat history ingestion completed!");
            } catch (Exception e) {
                // Show error alert on JavaFX thread
                javafx.application.Platform.runLater(() ->
                        showAlert("Error", "Failed to ingest chat history: " + e.getMessage()));
                e.printStackTrace();
            }
        }).start();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}