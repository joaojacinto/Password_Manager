package ui;

import crypto.CryptoService;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import vault.Vault;
import vault.VaultStorage;

import java.nio.file.Path;

// PasswordManagerApp extends Application, and start(Stage stage) is where JavaFX hands the window.
public class PasswordManagerApp extends Application {

    private static final Path VAULT_FILE =
            Path.of(System.getProperty("user.home"), "PasswordManager", "vault.dat");
    private static final boolean LOCK_ON_MINIMIZE = true;

    private final VaultStorage storage = new VaultStorage(new CryptoService());
    private Scene scene;
    private MainView currentMain;

    @Override
    public void start(Stage stage) {
        scene = new Scene(new Region(), 900, 560);
        showUnlockScreen();

        stage.iconifiedProperty().addListener((obs, wasMinimized, isMinimized) -> {
            if (LOCK_ON_MINIMIZE && isMinimized && currentMain != null) {
                currentMain.lock();
            }
        });

        stage.setTitle("Password Manager");
        stage.setScene(scene);
        stage.show();
    }

    private void showUnlockScreen() {
        currentMain = null;
        scene.setRoot(new UnlockView(storage, VAULT_FILE, this::showMainScreen));
    }

    private void showMainScreen(Vault vault) {
        currentMain = new MainView(vault, storage, this::showUnlockScreen);
        scene.setRoot(currentMain);
    }

    @Override
    public void stop() {
        if (currentMain != null) {
            currentMain.close();   // clears the clipboard if it still holds a copied password
        }
        storage.lock();            // forget the key when the window closes
    }
}