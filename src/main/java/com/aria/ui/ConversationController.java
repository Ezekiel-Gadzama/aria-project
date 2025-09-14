// ConversationController.java

package com.aria.ui;

import com.aria.core.AriaOrchestrator;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ConversationController {
    @FXML private Label targetLabel;
    @FXML private TextArea conversationArea;
    @FXML private TextArea messageInput;
    @FXML private ProgressIndicator progressIndicator;

    private AriaOrchestrator orchestrator;

    public void initialize() {
        orchestrator = new AriaOrchestrator();
    }

    public void setTarget(String targetName) {
        targetLabel.setText("Conversation with " + targetName);
    }

    @FXML
    private void onSendMessage() {
        String message = messageInput.getText().trim();
        if (!message.isEmpty()) {
            conversationArea.appendText("You: " + message + "\n\n");
            messageInput.clear();
            // Here you would integrate with the orchestrator
        }
    }

    @FXML
    private void onAISuggest() {
        progressIndicator.setVisible(true);
        // Simulate AI thinking
        new Thread(() -> {
            try {
                Thread.sleep(1000); // Simulate processing
                String suggestion = "This would be an AI-generated suggestion";
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
        alert.setContentText("This action cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Close conversation window
            }
        });
    }
}