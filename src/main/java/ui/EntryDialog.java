package ui;

import generator.PasswordGenerator;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import vault.VaultEntry;

public class EntryDialog extends Dialog<VaultEntry> {

    private static final int MIN_LENGTH = 16;
    private static final int MAX_LENGTH = 128;
    private static final int DEFAULT_LENGTH = 20;

    private final PasswordGenerator generator = new PasswordGenerator();

    private final PasswordField hiddenPassword = new PasswordField();
    private final TextField visiblePassword = new TextField();
    private final Button toggleButton = new Button("Show");

    public EntryDialog() {
        this(null);
    }

    public EntryDialog(VaultEntry existing) {
        boolean editing = existing != null;
        setTitle(editing ? "Edit entry" : "Add entry");
        setHeaderText(null);

        ButtonType saveType = new ButtonType(editing ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField siteField = new TextField();
        siteField.setPromptText("example.com");
        siteField.setPrefWidth(340);
        TextField usernameField = new TextField();
        usernameField.setPrefWidth(340);

        // two fields sharing the same text, only one visible at a time
        visiblePassword.textProperty().bindBidirectional(hiddenPassword.textProperty());
        setRevealed(false);
        toggleButton.setOnAction(e -> setRevealed(!visiblePassword.isVisible()));

        StackPane passwordStack = new StackPane(hiddenPassword, visiblePassword);
        HBox.setHgrow(passwordStack, Priority.ALWAYS);
        HBox passwordRow = new HBox(8, passwordStack, toggleButton);
        passwordRow.setAlignment(Pos.CENTER_LEFT);

        if (editing) {
            siteField.setText(existing.site());
            usernameField.setText(existing.username());
            hiddenPassword.setText(existing.password());
        }

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: #c0392b;");
        errorLabel.setWrapText(true);

        VBox generatorBox = buildGeneratorBox(errorLabel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Site"), siteField);
        grid.addRow(1, new Label("Username"), usernameField);
        grid.addRow(2, new Label("Password"), passwordRow);
        grid.add(generatorBox, 0, 3, 2, 1);
        grid.add(errorLabel, 0, 4, 2, 1);
        getDialogPane().setContent(grid);

        // validate before closing: if the entry is invalid, show why and keep the dialog open
        Button saveButton = (Button) getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                buildEntry(siteField, usernameField);
            } catch (IllegalArgumentException e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        setResultConverter(button -> button == saveType ? buildEntry(siteField, usernameField) : null);

        setOnShown(e -> siteField.requestFocus());
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

        VBox box = new VBox(8, new Label("Generator"), sliderRow, optionsRow, generateButton);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 6;");
        return box;
    }

    private void setRevealed(boolean revealed) {
        visiblePassword.setVisible(revealed);
        visiblePassword.setManaged(revealed);
        hiddenPassword.setVisible(!revealed);
        hiddenPassword.setManaged(!revealed);
        toggleButton.setText(revealed ? "Hide" : "Show");
    }

    private VaultEntry buildEntry(TextField site, TextField username) {
        return new VaultEntry(site.getText().trim(), username.getText().trim(), hiddenPassword.getText());
    }
}