package ui;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.InputEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import vault.Vault;
import vault.VaultEntry;
import vault.VaultStorage;

import java.io.IOException;
import java.util.Comparator;
import java.util.Optional;

public class MainView extends BorderPane {

    private static final String HIDDEN_PASSWORD = "••••••••••••";
    private static final double IDLE_LOCK_MINUTES = 1.0;
    private static final double CLIPBOARD_CLEAR_SECONDS = 10;

    private final Vault vault;
    private final VaultStorage storage;
    private final Runnable onLock;

    private final ObservableList<VaultEntry> all;
    private final FilteredList<VaultEntry> filtered;
    private final ListView<VaultEntry> listView = new ListView<>();

    private final Label placeholder = new Label("Select an entry");
    private final VBox detailBox = new VBox(16);
    private final Label siteLabel = new Label();
    private final Label usernameLabel = new Label();
    private final Label passwordLabel = new Label();
    private final Button toggleButton = new Button("Show");

    // session safety (declaration order matters: activityFilter uses the two fields above it)
    private boolean closed;
    private final PauseTransition idleTimer = new PauseTransition(Duration.minutes(IDLE_LOCK_MINUTES));
    private final PauseTransition clipboardTimer = new PauseTransition(Duration.seconds(CLIPBOARD_CLEAR_SECONDS));
    private final EventHandler<InputEvent> activityFilter = e -> {
        if (!closed) {
            idleTimer.playFromStart();
        }
    };
    private String copiedSecret;
    private Dialog<?> activeDialog;

    private VaultEntry selected;
    private boolean revealed;

    public MainView(Vault vault, VaultStorage storage, Runnable onLock) {
        this.vault = vault;
        this.storage = storage;
        this.onLock = onLock;

        this.all = FXCollections.observableArrayList(vault.getEntries());
        this.filtered = new FilteredList<>(all, e -> true);
        SortedList<VaultEntry> sorted =
                new SortedList<>(filtered, Comparator.comparing(VaultEntry::site, String.CASE_INSENSITIVE_ORDER));
        listView.setItems(sorted);

        setTop(buildTopBar());
        setLeft(buildList());
        setCenter(buildDetails());

        showDetails(null);

        idleTimer.setOnFinished(e -> lock());
        clipboardTimer.setOnFinished(e -> clearClipboardIfOurs());

        // watch all activity in the scene this view is shown in
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(InputEvent.ANY, activityFilter);
            }
            if (newScene != null) {
                newScene.addEventFilter(InputEvent.ANY, activityFilter);
                idleTimer.playFromStart();
            }
        });
    }

    // ---------- locking and cleanup ----------

    public void lock() {
        if (closed) {
            return;
        }
        if (activeDialog != null) {
            activeDialog.close();   // never leave a half-filled form on top of the lock screen
        }
        close();
        storage.lock();
        onLock.run();
    }

    public void close() {
        closed = true;
        idleTimer.stop();
        clearClipboardIfOurs();
    }

    private void clearClipboardIfOurs() {
        clipboardTimer.stop();
        if (copiedSecret == null) {
            return;
        }
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (copiedSecret.equals(clipboard.getString())) {
            clipboard.clear();
        }
        copiedSecret = null;
    }

    // ---------- building the screen ----------

    private HBox buildTopBar() {
        TextField search = new TextField();
        search.setPromptText("Search");
        search.textProperty().addListener((obs, oldText, text) ->
                filtered.setPredicate(entry -> matches(entry, text)));
        HBox.setHgrow(search, Priority.ALWAYS);

        Button addButton = new Button("+ Add");
        addButton.setOnAction(e -> openAddDialog());

        Button lockButton = new Button("Lock");
        lockButton.setOnAction(e -> lock());

        HBox bar = new HBox(8, search, addButton, lockButton);
        bar.setPadding(new Insets(10));
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private ListView<VaultEntry> buildList() {
        listView.setPrefWidth(300);
        listView.setPlaceholder(new Label("No entries"));
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(VaultEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setGraphic(null);
                    return;
                }
                Label site = new Label(entry.site());
                site.setStyle("-fx-font-weight: bold;");
                Label user = new Label(entry.username().isEmpty() ? "(no username)" : entry.username());
                user.setStyle("-fx-text-fill: gray;");
                setGraphic(new VBox(2, site, user));
            }
        });
        listView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldEntry, entry) -> showDetails(entry));
        return listView;
    }

    private StackPane buildDetails() {
        siteLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        passwordLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 16px;");

        Button copyUsername = new Button("Copy");
        copyUsername.setOnAction(e -> copy(selected.username(), copyUsername, false));

        toggleButton.setOnAction(e -> {
            revealed = !revealed;
            refreshPassword();
        });

        Button copyPassword = new Button("Copy");
        copyPassword.setOnAction(e -> copy(selected.password(), copyPassword, true));

        Button editButton = new Button("Edit");
        editButton.setOnAction(e -> editSelected());

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelected());

        detailBox.getChildren().addAll(
                siteLabel,
                field("Username", usernameLabel, copyUsername),
                field("Password", passwordLabel, toggleButton, copyPassword),
                new HBox(10, editButton, deleteButton));
        detailBox.setPadding(new Insets(24));

        StackPane pane = new StackPane(placeholder, detailBox);
        StackPane.setAlignment(detailBox, Pos.TOP_LEFT);
        return pane;
    }

    private VBox field(String name, Label value, Button... buttons) {
        Label caption = new Label(name);
        caption.setStyle("-fx-text-fill: gray;");

        HBox row = new HBox(10, value);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(buttons);

        return new VBox(4, caption, row);
    }

    // ---------- showing the selected entry ----------

    private void showDetails(VaultEntry entry) {
        selected = entry;
        revealed = false;

        boolean hasEntry = entry != null;
        placeholder.setVisible(!hasEntry);
        detailBox.setVisible(hasEntry);

        if (hasEntry) {
            siteLabel.setText(entry.site());
            usernameLabel.setText(entry.username().isEmpty() ? "(none)" : entry.username());
            refreshPassword();
        }
    }

    private void refreshPassword() {
        passwordLabel.setText(revealed ? selected.password() : HIDDEN_PASSWORD);
        toggleButton.setText(revealed ? "Hide" : "Show");
    }

    // ---------- actions ----------

    private boolean matches(VaultEntry entry, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String query = text.trim().toLowerCase();
        return entry.site().toLowerCase().contains(query)
                || entry.username().toLowerCase().contains(query);
    }

    private void copy(String text, Button button, boolean secret) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);

        if (secret) {
            copiedSecret = text;
            clipboardTimer.playFromStart();
        }

        button.setText("Copied!");
        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> button.setText("Copy"));
        pause.play();
    }

    private void openAddDialog() {
        showDialog(new EntryDialog()).ifPresent(this::addEntry);
    }

    private void addEntry(VaultEntry entry) {
        try {
            vault.add(entry);   // throws if the same site and username already exist
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        try {
            storage.save(vault);
            all.add(entry);
            listView.getSelectionModel().select(entry);
        } catch (IOException e) {
            vault.remove(entry.site(), entry.username());   // undo
            showError("Could not save the vault: " + e.getMessage());
        }
    }

    private void editSelected() {
        VaultEntry old = selected;
        if (old == null) {
            return;
        }
        showDialog(new EntryDialog(old)).ifPresent(updated -> updateEntry(old, updated));
    }

    private void updateEntry(VaultEntry old, VaultEntry updated) {
        try {
            vault.replace(old, updated);   // throws if it collides with another entry
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        try {
            storage.save(vault);
            all.set(all.indexOf(old), updated);
            listView.getSelectionModel().select(updated);
        } catch (IOException e) {
            vault.replace(updated, old);   // undo
            showError("Could not save the vault: " + e.getMessage());
        }
    }

    private void deleteSelected() {
        VaultEntry entry = selected;
        if (entry == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + entry.site() + " (" + entry.username() + ")?",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (showDialog(confirm).orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        vault.remove(entry.site(), entry.username());
        try {
            storage.save(vault);
            all.remove(entry);
        } catch (IOException e) {
            vault.add(entry);   // undo
            showError("Could not save the vault: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        showDialog(alert);
    }

    // every dialog goes through here, so it counts as activity and can be closed by the auto-lock
    private <T> Optional<T> showDialog(Dialog<T> dialog) {
        if (getScene() != null) {
            dialog.initOwner(getScene().getWindow());
        }
        dialog.getDialogPane().addEventFilter(InputEvent.ANY, activityFilter);
        activeDialog = dialog;
        try {
            return dialog.showAndWait();
        } finally {
            activeDialog = null;
            if (!closed) {
                idleTimer.playFromStart();
            }
        }
    }
}