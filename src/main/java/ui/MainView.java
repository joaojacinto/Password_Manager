package ui;

import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class MainView extends BorderPane {

    private static final String HIDDEN_PASSWORD = "••••••••••••";
    private static final double IDLE_LOCK_MINUTES = 1.0;
    private static final double CLIPBOARD_CLEAR_SECONDS = 15;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());

    private static final String CARD_STYLE =
            "-fx-background-color: white; -fx-background-radius: 12; "
                    + "-fx-border-color: #dddddd; -fx-border-radius: 12; -fx-padding: 12 16 12 16;";
    private static final String CAPTION_STYLE = "-fx-text-fill: gray; -fx-font-size: 12px;";

    private final Vault vault;
    private final VaultStorage storage;
    private final HostServices hostServices;
    private final Runnable onLock;

    private final ObservableList<VaultEntry> all;
    private final FilteredList<VaultEntry> filtered;
    private final ListView<VaultEntry> listView = new ListView<>();

    // detail view
    private final Label placeholder = new Label("Select an entry");
    private final BorderPane detailPane = new BorderPane();
    private final Label nameLabel = new Label();
    private final Label usernameLabel = new Label();
    private final Label passwordLabel = new Label();
    private final Button toggleButton = new Button("Show");
    private final VBox websitesBox = new VBox(4);
    private final Label notesLabel = new Label();
    private final Label modifiedLabel = new Label();
    private final Label createdLabel = new Label();
    private VBox usernameCard;
    private VBox websitesCard;
    private VBox notesCard;

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

    public MainView(Vault vault, VaultStorage storage, HostServices hostServices, Runnable onLock) {
        this.vault = vault;
        this.storage = storage;
        this.hostServices = hostServices;
        this.onLock = onLock;

        this.all = FXCollections.observableArrayList(vault.getEntries());
        this.filtered = new FilteredList<>(all, e -> true);
        SortedList<VaultEntry> sorted =
                new SortedList<>(filtered, Comparator.comparing(VaultEntry::name, String.CASE_INSENSITIVE_ORDER));
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
                Label name = new Label(entry.name());
                name.setStyle("-fx-font-weight: bold;");
                Label user = new Label(entry.username().isEmpty() ? "(no username)" : entry.username());
                user.setStyle("-fx-text-fill: gray;");
                setGraphic(new VBox(2, name, user));
            }
        });
        listView.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldEntry, entry) -> showDetails(entry));
        return listView;
    }

    private StackPane buildDetails() {
        nameLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        nameLabel.setWrapText(true);
        passwordLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 15px;");
        passwordLabel.setWrapText(true);   // a long revealed password wraps instead of being cut off
        usernameLabel.setWrapText(true);
        notesLabel.setWrapText(true);

        Button copyUsername = new Button("Copy");
        copyUsername.setMinWidth(60);
        copyUsername.setOnAction(e -> copy(selected.username(), copyUsername, false));

        toggleButton.setMinWidth(60);
        toggleButton.setOnAction(e -> {
            revealed = !revealed;
            refreshPassword();
        });

        Button copyPassword = new Button("Copy");
        copyPassword.setMinWidth(60);
        copyPassword.setOnAction(e -> copy(selected.password(), copyPassword, true));

        usernameCard = card("Email / username", row(usernameLabel, copyUsername));
        VBox passwordCard = card("Password", row(passwordLabel, toggleButton, copyPassword));
        websitesCard = card("Websites", websitesBox);
        notesCard = card("Notes", notesLabel);

        VBox datesCard = new VBox(10,
                captioned("Last modified", modifiedLabel),
                captioned("Created", createdLabel));
        datesCard.setStyle(CARD_STYLE);

        VBox content = new VBox(12, nameLabel, usernameCard, passwordCard, websitesCard, notesCard, datesCard);
        content.setPadding(new Insets(24, 24, 12, 24));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");

        Button editButton = new Button("Edit");
        editButton.setMinWidth(70);
        editButton.setOnAction(e -> editSelected());

        Button deleteButton = new Button("Delete");
        deleteButton.setMinWidth(70);
        deleteButton.setOnAction(e -> deleteSelected());

        HBox actions = new HBox(10, editButton, deleteButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(12, 24, 16, 24));

        detailPane.setCenter(scroll);
        detailPane.setBottom(actions);

        return new StackPane(placeholder, detailPane);
    }

    private VBox card(String caption, Node content) {
        VBox card = new VBox(4, captionLabel(caption), content);
        card.setStyle(CARD_STYLE);
        return card;
    }

    private VBox captioned(String caption, Node value) {
        return new VBox(2, captionLabel(caption), value);
    }

    private Label captionLabel(String text) {
        Label label = new Label(text);
        label.setStyle(CAPTION_STYLE);
        return label;
    }

    private HBox row(Label value, Button... buttons) {
        value.setMinWidth(0);
        value.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(value, Priority.ALWAYS);

        HBox row = new HBox(8, value);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(buttons);
        return row;
    }

    private void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // ---------- showing the selected entry ----------

    private void showDetails(VaultEntry entry) {
        selected = entry;
        revealed = false;

        boolean hasEntry = entry != null;
        placeholder.setVisible(!hasEntry);
        detailPane.setVisible(hasEntry);
        if (!hasEntry) {
            return;
        }

        nameLabel.setText(entry.name());

        usernameLabel.setText(entry.username());
        setShown(usernameCard, !entry.username().isEmpty());

        refreshPassword();
        showWebsites(entry.websites());

        notesLabel.setText(entry.notes());
        setShown(notesCard, !entry.notes().isBlank());

        modifiedLabel.setText(DATE_FORMAT.format(entry.modifiedAt()));
        createdLabel.setText(DATE_FORMAT.format(entry.createdAt()));
    }

    private void refreshPassword() {
        passwordLabel.setText(revealed ? selected.password() : HIDDEN_PASSWORD);
        toggleButton.setText(revealed ? "Hide" : "Show");
    }

    private void showWebsites(List<String> websites) {
        websitesBox.getChildren().clear();
        for (String site : websites) {
            URI uri = toWebUri(site);
            if (uri == null) {
                websitesBox.getChildren().add(new Label(site));   // not a web address: plain text
            } else {
                Hyperlink link = new Hyperlink(site);
                link.setPadding(Insets.EMPTY);
                link.setOnAction(e -> hostServices.showDocument(uri.toString()));
                websitesBox.getChildren().add(link);
            }
        }
        setShown(websitesCard, !websites.isEmpty());
    }

    // only http and https addresses become clickable; "pronto.me" is opened as "https://pronto.me"
    private static URI toWebUri(String text) {
        String candidate = text.contains("://") ? text : "https://" + text;
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return web && uri.getHost() != null ? uri : null;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    // ---------- actions ----------

    private boolean matches(VaultEntry entry, String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String query = text.trim().toLowerCase();
        return entry.name().toLowerCase().contains(query)
                || entry.username().toLowerCase().contains(query)
                || entry.websites().stream().anyMatch(w -> w.toLowerCase().contains(query));
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
            vault.add(entry);   // throws if the same name and username already exist
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return;
        }
        try {
            storage.save(vault);
            all.add(entry);
            listView.getSelectionModel().select(entry);
        } catch (IOException e) {
            vault.remove(entry.name(), entry.username());   // undo
            showError("Could not save the vault: " + e.getMessage());
        }
    }

    private void editSelected() {
        VaultEntry old = selected;
        if (old == null) {
            return;
        }
        showDialog(new EntryDialog(old)).ifPresent(updated -> {
            if (updated != old) {   // nothing changed: no need to save
                updateEntry(old, updated);
            }
        });
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

        String label = entry.username().isEmpty()
                ? entry.name()
                : entry.name() + " (" + entry.username() + ")";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete " + label + "?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (showDialog(confirm).orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        vault.remove(entry.name(), entry.username());
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