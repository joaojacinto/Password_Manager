package ui;

import crypto.CryptoService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import vault.Vault;
import vault.VaultStorage;

import java.nio.file.Path;

// PasswordManagerApp extends Application, and start(Stage stage) is where JavaFX hands the window.
public class PasswordManagerApp extends Application {

    private static final Path VAULT_FILE =
            Path.of(System.getProperty("user.home"), "PasswordManager", "vault.dat");

    private final VaultStorage storage = new VaultStorage(new CryptoService());
    private Scene scene;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new UnlockView(storage, VAULT_FILE, this::showMainScreen), 900, 560);

        stage.setTitle("Password Manager");
        stage.setScene(scene);
        stage.show();
    }

    // placeholder: the real main screen comes in the next step
    private void showMainScreen(Vault vault) {
        Label label = new Label("Vault unlocked: " + vault.getEntries().size() + " entries");
        scene.setRoot(new StackPane(label));
    }

    @Override
    public void stop() {
        storage.lock();   // forget the key when the window closes
    }
}