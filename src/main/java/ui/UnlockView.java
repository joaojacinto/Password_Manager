package ui;

import crypto.DecryptionException;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import vault.Vault;
import vault.VaultStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Consumer;

public class UnlockView extends VBox {

    private static final int MIN_MASTER_LENGTH = 12;
    private static final double FIELD_WIDTH = 320;

    private final VaultStorage storage;
    private final Path vaultFile;
    private final Consumer<Vault> onUnlocked;
    private final boolean creating;

    private final PasswordField passwordField = new PasswordField();
    private final PasswordField confirmField = new PasswordField();
    private final Button actionButton = new Button();
    private final Label messageLabel = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();

    public UnlockView(VaultStorage storage, Path vaultFile, Consumer<Vault> onUnlocked) {
        this.storage = storage;
        this.vaultFile = vaultFile;
        this.onUnlocked = onUnlocked;
        this.creating = !Files.exists(vaultFile);

        buildLayout();
    }

    private void buildLayout() {
        setAlignment(Pos.CENTER);
        setSpacing(12);
        setPadding(new Insets(40));

        Label title = new Label("Password Manager");
        title.getStyleClass().add("title");

        Label subtitle = new Label(creating ? "Create your vault" : "Unlock your vault");
        subtitle.getStyleClass().add("muted");

        actionButton.getStyleClass().add("primary");
        messageLabel.getStyleClass().add("error");

        passwordField.setPromptText("Master password");
        passwordField.setMaxWidth(FIELD_WIDTH);
        passwordField.setOnAction(e -> submit());   // Enter key

        getChildren().addAll(title, subtitle, passwordField);

        if (creating) {
            confirmField.setPromptText("Repeat master password");
            confirmField.setMaxWidth(FIELD_WIDTH);
            confirmField.setOnAction(e -> submit());

            Label warning = new Label("At least " + MIN_MASTER_LENGTH
                    + " characters. If you forget it, the vault cannot be recovered.");
            warning.setWrapText(true);
            warning.setMaxWidth(FIELD_WIDTH);

            getChildren().addAll(confirmField, warning);
        }

        actionButton.setText(creating ? "Create vault" : "Unlock");
        actionButton.setOnAction(e -> submit());

        spinner.setPrefSize(24, 24);
        spinner.setVisible(false);

        //messageLabel.setStyle("-fx-text-fill: #c0392b;");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(FIELD_WIDTH);

        getChildren().addAll(actionButton, spinner, messageLabel);
    }

    private void submit() {
        if (actionButton.isDisabled()) {
            return;   // already working
        }
        messageLabel.setText("");

        char[] password = passwordField.getText().toCharArray();

        if (creating) {
            char[] confirm = confirmField.getText().toCharArray();
            String problem = validateNewPassword(password, confirm);
            Arrays.fill(confirm, '\0');
            if (problem != null) {
                Arrays.fill(password, '\0');
                messageLabel.setText(problem);
                return;
            }
        }

        setBusy(true);

        Task<Vault> task = new Task<>() {
            @Override
            protected Vault call() throws Exception {
                try {
                    if (creating) {
                        Files.createDirectories(vaultFile.getParent());
                        storage.create(vaultFile, password);
                        return new Vault();
                    }
                    return storage.unlock(vaultFile, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };

        task.setOnSucceeded(e -> {
            passwordField.clear();
            confirmField.clear();
            onUnlocked.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            setBusy(false);
            passwordField.clear();
            passwordField.requestFocus();
            messageLabel.setText(describe(task.getException()));
        });

        Thread worker = new Thread(task, "vault-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private String validateNewPassword(char[] password, char[] confirm) {
        if (password.length < MIN_MASTER_LENGTH) {
            return "Use at least " + MIN_MASTER_LENGTH + " characters.";
        }
        if (!Arrays.equals(password, confirm)) {
            return "The passwords don't match.";
        }
        return null;
    }

    private String describe(Throwable error) {
        if (error instanceof DecryptionException) {
            return "Wrong password, or the vault file is corrupted.";
        }
        if (error instanceof IOException) {
            return "File error: " + error.getMessage();
        }
        return "Unexpected error: " + error.getMessage();
    }

    private void setBusy(boolean busy) {
        passwordField.setDisable(busy);
        confirmField.setDisable(busy);
        actionButton.setDisable(busy);
        spinner.setVisible(busy);
    }
}