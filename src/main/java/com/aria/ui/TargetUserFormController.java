package com.aria.ui;

import com.aria.core.model.TargetUser;
import com.aria.platform.UserPlatform; // Changed import
import com.aria.platform.Platform;
import com.aria.service.TargetUserService;
import com.aria.service.UserService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TargetUserFormController {
    @FXML private TextField targetNameField;
    @FXML private VBox platformsContainer;

    private Stage primaryStage;
    private UserService userService;
    private TargetUserService targetUserService;
    private TargetUser editingTarget;
    private List<UserPlatform> platforms;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public void setTargetUserService(TargetUserService targetUserService) {
        this.targetUserService = targetUserService;
    }

    public void setEditingTarget(TargetUser targetUser) {
        this.editingTarget = targetUser;
        this.platforms = new ArrayList<>(targetUser.getPlatforms());
        populateForm();
    }

    public void initialize() {
        if (platforms == null) {
            platforms = new ArrayList<>();
        }
    }

    private void populateForm() {
        if (editingTarget != null) {
            targetNameField.setText(editingTarget.getName());
            refreshPlatformsDisplay();
        }
    }

    @FXML
    private void onAddPlatform() {
        PlatformSelectionDialog dialog = new PlatformSelectionDialog();
        dialog.showAndWait().ifPresent(platform -> {
            if (platform != null) {
                UserPlatform userPlatform = new UserPlatform("", "", 0, platform);
                platforms.add(userPlatform);
                refreshPlatformsDisplay();
            }
        });
    }

    private void refreshPlatformsDisplay() {
        platformsContainer.getChildren().clear();

        for (int i = 0; i < platforms.size(); i++) {
            UserPlatform platform = platforms.get(i);
            platformsContainer.getChildren().add(createPlatformRow(platform, i));
        }
    }

    private HBox createPlatformRow(UserPlatform userPlatform, int index) {
        HBox row = new HBox(10);

        Label platformLabel = new Label(userPlatform.getPlatform().name());
        platformLabel.setStyle("-fx-font-weight: bold; -fx-pref-width: 100;");

        // Username field
        Label usernameLabel = new Label("Username:");
        TextField usernameField = new TextField(userPlatform.getUsername());
        usernameField.setPromptText("Username");
        usernameField.setPrefWidth(120);
        usernameField.textProperty().addListener((obs, oldVal, newVal) ->
                userPlatform.setUsername(newVal)
        );

        // Number field
        Label numberLabel = new Label("Number:");
        TextField numberField = new TextField(userPlatform.getNumber());
        numberField.setPromptText("Phone number");
        numberField.setPrefWidth(120);
        numberField.textProperty().addListener((obs, oldVal, newVal) ->
                userPlatform.setNumber(newVal)
        );

        Button removeButton = new Button("Remove");
        removeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        removeButton.setOnAction(e -> {
            platforms.remove(index);
            refreshPlatformsDisplay();
        });

        row.getChildren().addAll(platformLabel, usernameLabel, usernameField, numberLabel, numberField, removeButton);
        return row;
    }

    @FXML
    private void onSave() {
        String targetName = targetNameField.getText().trim();

        if (targetName.isEmpty()) {
            showAlert("Missing Information", "Please enter a target name.");
            return;
        }

        if (platforms.isEmpty()) {
            showAlert("Missing Information", "Please add at least one platform.");
            return;
        }

        TargetUser targetUser = editingTarget != null ? editingTarget : new TargetUser();
        targetUser.setName(targetName);
        targetUser.setPlatforms(platforms);

        boolean success = targetUserService.saveTargetUser(userService.getUser().getUserAppId(), targetUser);

        if (success) {
            returnToTargetManagement();
        } else {
            showAlert("Error", "Failed to save target user.");
        }
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
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Platform selection dialog
    private static class PlatformSelectionDialog extends Dialog<Platform> {
        public PlatformSelectionDialog() {
            setTitle("Select Platform");
            setHeaderText("Choose a communication platform");

            ButtonType addButton = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

            ComboBox<Platform> platformCombo = new ComboBox<>();
            platformCombo.getItems().addAll(Platform.values());

            // Set a default selected value to avoid null
            if (!platformCombo.getItems().isEmpty()) {
                platformCombo.getSelectionModel().selectFirst();
            }

            platformCombo.setConverter(new StringConverter<Platform>() {
                @Override
                public String toString(Platform platform) {
                    // Handle null platform gracefully
                    if (platform == null) {
                        return "Select a platform";
                    }
                    return platform.name().charAt(0) + platform.name().substring(1).toLowerCase();
                }

                @Override
                public Platform fromString(String string) {
                    try {
                        return Platform.valueOf(string.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
            });

            VBox content = new VBox(10);
            content.getChildren().addAll(new Label("Platform:"), platformCombo);
            getDialogPane().setContent(content);

            // Set the result converter
            setResultConverter(dialogButton -> {
                if (dialogButton == addButton) {
                    return platformCombo.getValue();
                }
                return null;
            });

            // Add validation to prevent adding without selection
            javafx.application.Platform.runLater(() -> {
                Button addButtonNode = (Button) getDialogPane().lookupButton(addButton);
                addButtonNode.setDisable(platformCombo.getValue() == null);

                platformCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    addButtonNode.setDisable(newVal == null);
                });
            });
        }
    }
}