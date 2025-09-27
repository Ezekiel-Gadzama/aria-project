package com.aria.ui;

import com.aria.core.AriaOrchestrator;
import com.aria.core.model.ConversationGoal;
import com.aria.core.model.TargetUser;
import com.aria.platform.Platform;
import com.aria.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;

public class MainController {
    @FXML private TextField targetNameField;
    @FXML private ComboBox<Platform> platformComboBox;
    @FXML private TextArea contextArea;
    @FXML private TextArea outcomeArea;

    private AriaOrchestrator orchestrator;
    private UserService userService;
    private Stage primaryStage;
    private TargetUser preSelectedTarget;
    private String preSelectedPlatform;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
        this.orchestrator = new AriaOrchestrator(userService);
    }

    public void setPreSelectedTarget(TargetUser targetUser, String platform) {
        this.preSelectedTarget = targetUser;
        this.preSelectedPlatform = platform;
        populatePreSelectedFields();
    }

    public void initialize() {
        platformComboBox.getItems().addAll(Platform.values());
        platformComboBox.setConverter(new StringConverter<Platform>() {
            @Override
            public String toString(Platform platform) {
                return platform.name().charAt(0) + platform.name().substring(1).toLowerCase();
            }

            @Override
            public Platform fromString(String string) {
                return Platform.valueOf(string.toUpperCase());
            }
        });

        if (preSelectedTarget == null) {
            platformComboBox.getSelectionModel().selectFirst();
        } else {
            populatePreSelectedFields();
        }
    }

    private void populatePreSelectedFields() {
        if (preSelectedTarget != null) {
            targetNameField.setText(preSelectedTarget.getName());
            targetNameField.setDisable(true);

            // Populate from the target's conversation goal if available
            ConversationGoal goal = preSelectedTarget.getConversationGoal();
            if (goal != null) {
                if (goal.getMeetingContext() != null) {
                    contextArea.setText(goal.getMeetingContext());
                }
                if (goal.getDesiredOutcome() != null) {
                    outcomeArea.setText(goal.getDesiredOutcome());
                }
            }

            if (preSelectedPlatform != null) {
                try {
                    Platform platform = Platform.valueOf(preSelectedPlatform.toUpperCase());
                    platformComboBox.getSelectionModel().select(platform);
                    platformComboBox.setDisable(true);

                    // Set the selected platform index on the target user
                    preSelectedTarget.findPlatformIndex(platform).ifPresent(preSelectedTarget::setSelectedPlatformIndex);
                } catch (IllegalArgumentException e) {
                    // Platform not found, leave combo box enabled
                }
            }
        }
    }

    @FXML
    private void onStartConversation() {
        String context = contextArea.getText().trim();
        String outcome = outcomeArea.getText().trim();

        if (outcome.isEmpty()) {
            showAlert("Missing Fields", "Desired outcome is required before starting a conversation.");
            return;
        }

        // If we don't have a pre-selected target, create a new one
        TargetUser targetUser;
        if (preSelectedTarget == null) {
            // Create a new target user for ad-hoc conversation
            targetUser = new TargetUser();
            targetUser.setName(targetNameField.getText().trim());

            // Create a basic platform entry for the selected platform
            Platform selectedPlatform = platformComboBox.getValue();
            if (selectedPlatform != null) {
                com.aria.platform.UserPlatform userPlatform = new com.aria.platform.UserPlatform();
                userPlatform.setPlatform(selectedPlatform);
                userPlatform.setUsername(targetNameField.getText().trim()); // Use name as username for ad-hoc
                targetUser.getPlatforms().add(userPlatform);
                targetUser.setSelectedPlatformIndex(0); // Select the first (and only) platform
            }
        } else {
            targetUser = preSelectedTarget;

            // Update the selected platform if the user changed it
            Platform selectedPlatform = platformComboBox.getValue();
            if (selectedPlatform != null && !platformComboBox.isDisable()) {
                targetUser.findPlatformIndex(selectedPlatform).ifPresent(targetUser::setSelectedPlatformIndex);
            }
        }

        // Use or create conversation goal
        ConversationGoal goal;
        if (targetUser.getConversationGoal() != null) {
            goal = targetUser.getConversationGoal();
            goal.setMeetingContext(context);
            goal.setDesiredOutcome(outcome);
        } else {
            goal = new ConversationGoal();
            goal.setMeetingContext(context);
            goal.setDesiredOutcome(outcome);
            targetUser.setConversationGoal(goal);
        }

        // FIXED: Use the new method signature with TargetUser
        orchestrator.initializeConversation(goal, targetUser);
        switchToConversationView(targetUser.getName());
        startChatIngestion();
    }

    @FXML
    private void onBackToTargets() {
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
            showAlert("Error", "Failed to return to target management: " + e.getMessage());
        }
    }

    private void switchToConversationView(String targetName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/conversationView.fxml"));
            Parent conversationRoot = loader.load();

            ConversationController conversationController = loader.getController();
            conversationController.setTarget(targetName);
            conversationController.setPrimaryStage(primaryStage);
            conversationController.setOrchestrator(orchestrator);
            conversationController.setUserService(userService); // Add this line

            Scene conversationScene = new Scene(conversationRoot);
            primaryStage.setScene(conversationScene);
            primaryStage.setTitle("ARIA - Conversation with " + targetName);

        } catch (IOException e) {
            showAlert("Error", "Failed to load conversation view: " + e.getMessage());
        }
    }

    private void startChatIngestion() {
        new Thread(() -> {
            try {
                orchestrator.startChatIngestion();
                System.out.println("Chat history ingestion completed!");
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showAlert("Error", "Failed to ingest chat history: " + e.getMessage()));
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