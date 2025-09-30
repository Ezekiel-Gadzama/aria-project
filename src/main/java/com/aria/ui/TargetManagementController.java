package com.aria.ui;

import com.aria.core.model.TargetUser;
import com.aria.platform.Platform;
import com.aria.platform.UserPlatform; // Fixed import
import com.aria.service.TargetUserService;
import com.aria.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ButtonBar;

import java.io.IOException;
import java.util.List;

public class TargetManagementController {
    @FXML private VBox targetsContainer;

    private Stage primaryStage;
    private UserService userService;
    private TargetUserService targetUserService;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
        this.targetUserService = new TargetUserService(userService.getDatabaseManager());
        loadTargetUsers();
    }

    private void loadTargetUsers() {
        targetsContainer.getChildren().clear();

        System.out.println("Loading target users for user ID: " + userService.getUser().getUserAppId());

        List<TargetUser> targetUsers = targetUserService.getTargetUsersByUserId(
                userService.getUser().getUserAppId()
        );

        System.out.println("Found " + targetUsers.size() + " target users");

        for (TargetUser targetUser : targetUsers) {
            System.out.println("Target: " + targetUser.getName() + " with " +
                    targetUser.getPlatforms().size() + " platforms");
            targetsContainer.getChildren().add(createTargetUserCard(targetUser));
        }

        if (targetUsers.isEmpty()) {
            Label noTargetsLabel = new Label("No target users found. Click 'Add New Target' to get started.");
            noTargetsLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");
            targetsContainer.getChildren().add(noTargetsLabel);
        }
    }

    private HBox createTargetUserCard(TargetUser targetUser) {
        HBox card = new HBox(15);
        card.setStyle("-fx-background-color: #ecf0f1; -fx-padding: 15; -fx-border-radius: 5;");
        card.setPrefWidth(600);

        Label nameLabel = new Label(targetUser.getName());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-pref-width: 150;");

        ComboBox<String> platformComboBox = new ComboBox<>();
        platformComboBox.setPromptText("Select Platform");

        // Add platforms with connector status indicators
        for (UserPlatform platform : targetUser.getPlatforms()) {
            String platformName = platform.getPlatform().name();
            boolean connectorRegistered = isConnectorRegistered(platformName);
            String displayName = platformName + (connectorRegistered ? " ✓" : " ✗");
            platformComboBox.getItems().add(displayName);
        }

        platformComboBox.setPrefWidth(150);

        Button chatButton = new Button("Chat");
        chatButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        chatButton.setOnAction(e -> {
            String selectedValue = platformComboBox.getValue();
            if (selectedValue != null) {
                String platformName = selectedValue.replace(" ✓", "").replace(" ✗", "");
                onStartChat(targetUser, platformName);
            }
        });

        Button editButton = new Button("Edit");
        editButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        editButton.setOnAction(e -> onEditTarget(targetUser));

        platformComboBox.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> chatButton.setDisable(newVal == null)
        );

        chatButton.setDisable(true);

        card.getChildren().addAll(nameLabel, platformComboBox, chatButton, editButton);
        return card;
    }

    @FXML
    private void onAddNewTarget() {
        switchToTargetUserForm(null);
    }

    @FXML
    private void onManageConnectors() {
        switchToConnectorRegistration();
    }

    private void onEditTarget(TargetUser targetUser) {
        switchToTargetUserForm(targetUser);
    }


    private void onStartChat(TargetUser targetUser, String platform) {
        if (platform == null) {
            showAlert("Selection Required", "Please select a platform to start chatting.");
            return;
        }

        // Check if the required connector is registered
        if (!isConnectorRegistered(platform)) {
            showConnectorRequiredAlert(platform);
            return;
        }

        switchToConversationView(targetUser, platform);
    }

    private boolean isConnectorRegistered(String platformName) {
        try {
            Platform platform = Platform.valueOf(platformName.toUpperCase());
            return switch (platform) {
                case TELEGRAM -> userService.getUser().getTelegramConnector() != null &&
                        userService.getUser().getTelegramConnector().testConnection();
                case WHATSAPP -> userService.getUser().getWhatsappConnector() != null &&
                        userService.getUser().getWhatsappConnector().testConnection();
                case INSTAGRAM -> userService.getUser().getInstagramConnector() != null &&
                        userService.getUser().getInstagramConnector().testConnection();
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void showConnectorRequiredAlert(String platformName) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Connector Required");
        alert.setHeaderText(platformName + " Connector Not Registered");
        alert.setContentText("You need to register a " + platformName + " connector before you can chat on this platform.");

        ButtonType registerButton = new ButtonType("Register Connector");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(registerButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == registerButton) {
                switchToConnectorRegistration();
            }
        });
    }

    private void switchToConnectorRegistration() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/ConnectorRegistration.fxml"));
            Parent root = loader.load();

            ConnectorRegistrationController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            controller.setUserService(userService);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("ARIA - Register Platform Connector");

        } catch (IOException e) {
            showAlert("Error", "Failed to load connector registration: " + e.getMessage());
        }
    }

    private void switchToTargetUserForm(TargetUser targetUser) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/TargetUserForm.fxml"));
            Parent root = loader.load();

            TargetUserFormController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            controller.setUserService(userService);
            controller.setTargetUserService(targetUserService);
            if (targetUser != null) {
                controller.setEditingTarget(targetUser);
            }

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle(targetUser == null ? "ARIA - Add Target User" : "ARIA - Edit Target User");

        } catch (IOException e) {
            showAlert("Error", "Failed to load target form: " + e.getMessage());
        }
    }

    private void switchToConversationView(TargetUser targetUser, String platform) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/aria/ui/GoalInputForm.fxml"));
            Parent root = loader.load();

            MainController controller = loader.getController();
            controller.setPrimaryStage(primaryStage);
            controller.setUserService(userService);
            controller.setPreSelectedTarget(targetUser, platform);

            Scene scene = new Scene(root);
            primaryStage.setScene(scene);
            primaryStage.setTitle("ARIA - Conversation Setup");

        } catch (IOException e) {
            showAlert("Error", "Failed to load conversation setup: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}