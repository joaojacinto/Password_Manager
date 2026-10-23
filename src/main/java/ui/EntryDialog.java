package ui;

import generator.PasswordGenerator;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vault.VaultEntry;

import java.util.List;

public class EntryDialog extends Dialog<VaultEntry> {

    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;
    private static final int DEFAULT_LENGTH = 20;

    private final PasswordGenerator generator = new PasswordGenerator();
    private final VaultEntry existing;

    private final TextField nameField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField hiddenPassword = new PasswordField();
    private final TextField visiblePassword = new TextField();
    private final Button toggleButton = new Button("Show");
    private final TextArea websitesArea = new TextArea();
    private final TextArea notesArea = new TextArea();

    public EntryDialog() {
        this(null);
    }

    public EntryDialog(VaultEntry existing) {
        this.existing = existing;
        boolean editing = existing != null;
        setTitle(editing ? "Edit entry" : "Add entry");
        setHeaderText(null);
        setResizable(true);

        ButtonType saveType = new ButtonType(editing ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        nameField.setPromptText("Example: Reddit");
        usernameField.setPromptText("Example: Email or username");

        websitesArea.setPromptText("One website per line");
        websitesArea.setPrefRowCount(3);

        notesArea.setPrefRowCount(4);
        notesArea.setWrapText(true);

        // two fields sharing the same text, only one visible at a time
        visiblePassword.textProperty().bindBidirectional(hiddenPassword.textProperty());
        setRevealed(false);
        toggleButton.setMinWidth(60);
        toggleButton.setOnAction(e -> setRevealed(!visiblePassword.isVisible()));

        StackPane passwordStack = new StackPane(hiddenPassword, visiblePassword);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);
        HBox passwordRow = new HBox(8, passwordStack, toggleButton);
        passwordRow.setAlignment(Pos.CENTER_LEFT);

        if (editing) {
            nameField.setText(existing.name());
            usernameField.setText(existing.username());
            hiddenPassword.setText(existing.password());
            websitesArea.setText(String.join("\n", existing.websites()));
            notesArea.setText(existing.notes());
        }

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("error");         // replaces errorLabel.setStyle(...)
        errorLabel.setWrapText(true);

        TitledPane generatorPane = new TitledPane("Password generator", buildGeneratorBox(errorLabel));
        generatorPane.setExpanded(false);
        // the dialog doesn't resize by itself when the section opens or closes
        generatorPane.expandedProperty().addListener((obs, wasOpen, isOpen) ->
                Platform.runLater(() -> getDialogPane().getScene().getWindow().sizeToScene()));

        Label websitesLabel = new Label("Websites");
        GridPane.setValignment(websitesLabel, VPos.TOP);
        Label notesLabel = new Label("Notes");
        GridPane.setValignment(notesLabel, VPos.TOP);

        ColumnConstraints labelColumn = new ColumnConstraints();
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setPrefWidth(900);


        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.getColumnConstraints().addAll(labelColumn, fieldColumn);
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Email / username"), usernameField);
        grid.addRow(2, new Label("Password"), passwordRow);
        grid.add(generatorPane, 1, 3);
        grid.addRow(4, websitesLabel, websitesArea);
        grid.addRow(5, notesLabel, notesArea);
        grid.add(errorLabel, 1, 6);
        getDialogPane().setContent(grid);

        // validate before closing: if the entry is invalid, show why and keep the dialog open
        Button saveButton = (Button) getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                buildEntry();
            } catch (IllegalArgumentException e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> button == saveType ? buildEntry() : null);

        setOnShown(e -> nameField.requestFocus());
        setOnHidden(e -> hiddenPassword.clear());
    }

    private VBox buildGeneratorBox(Label errorLabel) {
        Slider lengthSlider = new Slider(MIN_LENGTH, MAX_LENGTH, DEFAULT_LENGTH);
        lengthSlider.setMajorTickUnit(1);
        lengthSlider.setMinorTickCount(0);
        lengthSlider.setSnapToTicks(true);
        HBox.setHgrow(lengthSlider, Priority.ALWAYS);

        Label lengthLabel = new Label();
        lengthLabel.textProperty().bind(Bindings.format("Length: %.0f", lengthSlider.valueProperty()));
        lengthLabel.setMinWidth(80);

        CheckBox lower = new CheckBox("a-z");
        CheckBox upper = new CheckBox("A-Z");
        CheckBox digits = new CheckBox("0-9");
        CheckBox symbols = new CheckBox("!@#");
        lower.setSelected(true);
        upper.setSelected(true);
        digits.setSelected(true);
        symbols.setSelected(true);

        Button generateButton = new Button("Generate");
        generateButton.getStyleClass().add("primary");
        generateButton.setOnAction(e -> {
            errorLabel.setText("");
            try {
                String password = generator.generate((int) lengthSlider.getValue(),
                        lower.isSelected(), upper.isSelected(), digits.isSelected(), symbols.isSelected());
                hiddenPassword.setText(password);
                setRevealed(true);   // show it, so the user can see what was generated
            } catch (IllegalArgumentException ex) {
                errorLabel.setText(ex.getMessage());
            }
        });

        HBox sliderRow = new HBox(10, lengthLabel, lengthSlider);
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        HBox optionsRow = new HBox(12, lower, upper, digits, symbols);

        VBox box = new VBox(8, sliderRow, optionsRow, generateButton);
        box.setPadding(new Insets(10));
        return box;
    }

    private void setRevealed(boolean revealed) {
        visiblePassword.setVisible(revealed);
        visiblePassword.setManaged(revealed);
        hiddenPassword.setVisible(!revealed);
        hiddenPassword.setManaged(!revealed);
        toggleButton.setText(revealed ? "Hide" : "Show");
    }

    // VaultEntry trims everything and drops blank website lines
    private VaultEntry buildEntry() {
        List<String> websites = websitesArea.getText().lines().toList();
        if (existing == null) {
            return VaultEntry.create(nameField.getText(), usernameField.getText(),
                    hiddenPassword.getText(), websites, notesArea.getText());
        }
        return existing.withChanges(nameField.getText(), usernameField.getText(),
                hiddenPassword.getText(), websites, notesArea.getText());
    }
}