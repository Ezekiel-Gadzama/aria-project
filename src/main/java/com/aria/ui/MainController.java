// MainController.java
package com.aria.ui;

import com.aria.core.AriaOrchestrator;
import com.aria.core.model.ConversationGoal;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class MainController {
    @FXML private TextField targetNameField;
    @FXML private ComboBox<String> platformComboBox;
    @FXML private TextArea contextArea;
    @FXML private TextArea outcomeArea;

    private AriaOrchestrator orchestrator;

    public void initialize() {
        platformComboBox.getItems().addAll("Telegram", "WhatsApp", "Instagram");
        platformComboBox.getSelectionModel().selectFirst();
        orchestrator = new AriaOrchestrator();
    }

    @FXML
    private void onIngestHistory() {
        try {
            orchestrator.startChatIngestion();
            showAlert("Success", "Chat history ingestion started successfully!");
        } catch (Exception e) {
            showAlert("Error", "Failed to ingest chat history: " + e.getMessage());
        }
    }

    @FXML
    private void onStartConversation() {
        ConversationGoal goal = new ConversationGoal();
        goal.setTargetName(targetNameField.getText());
        goal.setPlatform(platformComboBox.getValue());
        goal.setMeetingContext(contextArea.getText());
        goal.setDesiredOutcome(outcomeArea.getText());

        orchestrator.initializeConversation(goal);
        showAlert("Started", "Conversation with " + goal.getTargetName() + " initialized!");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}