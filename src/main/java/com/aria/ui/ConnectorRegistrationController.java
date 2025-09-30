package com.aria.ui;

import com.aria.platform.Platform;
import com.aria.platform.telegram.TelegramConnector;
import com.aria.platform.whatsapp.WhatsappConnector;
import com.aria.platform.instagram.InstagramConnector;
import com.aria.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;

public class ConnectorRegistrationController {
    @FXML private ComboBox<Platform> platformComboBox;
    @FXML private VBox connectorFormContainer;
    @FXML private Button registerButton;

    private Stage primaryStage;
    private UserService userService;
    private Platform selectedPlatform;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void initialize() {
        // Setup platform combo box
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

        // Listen for platform selection changes
        platformComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            selectedPlatform = newVal;
            loadConnectorForm(newVal);
            registerButton.setDisable(newVal == null);
        });
    }

    private void loadConnectorForm(Platform platform) {
        connectorFormContainer.getChildren().clear();

        if (platform == null) return;

        switch (platform) {
            case TELEGRAM -> loadTelegramForm();
            case WHATSAPP -> loadWhatsAppForm();
            case INSTAGRAM -> loadInstagramForm();
        }
    }

    private void loadTelegramForm() {
        Label apiIdLabel = new Label("API ID:");
        TextField apiIdField = new TextField();
        apiIdField.setPromptText("Your Telegram API ID");

        Label apiHashLabel = new Label("API Hash:");
        TextField apiHashField = new TextField();
        apiHashField.setPromptText("Your Telegram API Hash");

        Label phoneLabel = new Label("Phone Number:");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Your phone number with country code");

        connectorFormContainer.getChildren().addAll(
                apiIdLabel, apiIdField, apiHashLabel, apiHashField,
                phoneLabel, phoneField
        );

        // Store references for later use
        apiIdField.setUserData("apiId");
        apiHashField.setUserData("apiHash");
        phoneField.setUserData("phone");
    }

    private void loadWhatsAppForm() {
        Label phoneLabel = new Label("Phone Number:");
        TextField phoneField = new TextField();
        phoneField.setPromptText("Your WhatsApp phone number");

        Label sessionLabel = new Label("Session Data (Optional):");
        TextArea sessionArea = new TextArea();
        sessionArea.setPromptText("Paste existing session data if available");
        sessionArea.setPrefRowCount(3);

        connectorFormContainer.getChildren().addAll(
                phoneLabel, phoneField, sessionLabel, sessionArea
        );

        phoneField.setUserData("phone");
        sessionArea.setUserData("session");
    }

    private void loadInstagramForm() {
        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Your Instagram username");

        Label passwordLabel = new Label("Password:");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Your Instagram password");

        connectorFormContainer.getChildren().addAll(
                usernameLabel, usernameField, passwordLabel, passwordField
        );

        usernameField.setUserData("username");
        passwordField.setUserData("password");
    }

    @FXML
    private void onRegister() {
        if (selectedPlatform == null) {
            showAlert("Error", "Please select a platform first.");
            return;
        }

        try {
            switch (selectedPlatform) {
                case TELEGRAM -> registerTelegramConnector();
                case WHATSAPP -> registerWhatsAppConnector();
                case INSTAGRAM -> registerInstagramConnector();
            }

            showAlert("Success", selectedPlatform.name() + " connector registered successfully!");
            returnToTargetManagement();

        } catch (Exception e) {
            showAlert("Error", "Failed to register connector: " + e.getMessage());
        }
    }

    private void registerTelegramConnector() {
        String apiId = getFieldValue("apiId");
        String apiHash = getFieldValue("apiHash");
        String phone = getFieldValue("phone");

        if (apiId.isEmpty() || apiHash.isEmpty() || phone.isEmpty()) {
            throw new IllegalArgumentException("All fields are required for Telegram");
        }

        TelegramConnector connector = new TelegramConnector(apiId, apiHash, phone);
        userService.getUser().setTelegramConnector(connector);
    }

    private void registerWhatsAppConnector() {
        String phone = getFieldValue("phone");
        String session = getFieldValue("session");

        if (phone.isEmpty()) {
            throw new IllegalArgumentException("Phone number is required for WhatsApp");
        }

        WhatsappConnector connector = new WhatsappConnector(phone, session);
        userService.getUser().setWhatsappConnector(connector);
    }

    private void registerInstagramConnector() {
        String username = getFieldValue("username");
        String password = getFieldValue("password");

        if (username.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Username and password are required for Instagram");
        }

        InstagramConnector connector = new InstagramConnector(username, password);
        userService.getUser().setInstagramConnector(connector);
    }

    private String getFieldValue(String fieldName) {
        return connectorFormContainer.getChildren().stream()
                .filter(node -> node instanceof TextInputControl && fieldName.equals(node.getUserData()))
                .map(node -> ((TextInputControl) node).getText().trim())
                .findFirst()
                .orElse("");
    }

    @FXML
    private void onCancel() {
        returnToTargetManagement();
    }

    private void returnToTargetManagement() {
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}