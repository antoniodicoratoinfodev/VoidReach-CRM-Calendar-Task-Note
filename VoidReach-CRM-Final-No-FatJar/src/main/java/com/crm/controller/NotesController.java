package com.crm.controller;

import com.crm.model.Note;
import com.crm.model.NoteFormat;
import com.crm.model.NoteFolder;
import com.crm.model.Task;
import com.crm.service.CodeSyntaxHighlighter;
import com.crm.service.ThemeService;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Owns the notes library, full-size editor, ordering, Markdown preview, and task links. */
public final class NotesController {
    private static final DataFormat NOTE_ID = new DataFormat("application/x-voidreach-note-id");
    private static final TaskOption NO_TASK = new TaskOption(null, null);
    private static final FolderOption ROOT_FOLDER = new FolderOption(null);
    private static final List<Double> FONT_SIZES = List.of(
            12.0, 14.0, 16.0, 18.0, 20.0, 22.0, 24.0, 28.0, 32.0, 36.0, 40.0, 48.0);
    private static final List<String> FONT_WEIGHTS = List.of("Regular", "Medium", "Semibold", "Bold");
    private static final Pattern INLINE_MARKDOWN = Pattern.compile(
            "\\[\\[([^]\\n]+)]]|\\*\\*(.+?)\\*\\*|__(.+?)__|`([^`\\n]+)`|"
                    + "\\[([^]\\n]+)]\\(([^)\\n]+)\\)|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|"
                    + "(?<!_)_([^_\\n]+)_(?!_)|~~(.+?)~~");

    private final VBox libraryPane;
    private final VBox editorPane;
    private final TextField searchField;
    private final TilePane grid;
    private final Label emptyLabel;
    private final Label countLabel;
    private final TextField titleField;
    private final Label formatLabel;
    private final Button backButton;
    private final Label locationLabel;
    private final ComboBox<FolderOption> folderCombo;
    private final ComboBox<TaskOption> taskCombo;
    private final Button openTaskButton;
    private final HBox markdownToolbar;
    private final ToggleButton previewToggle;
    private final ComboBox<String> fontFamilyCombo;
    private final ComboBox<Double> fontSizeCombo;
    private final ComboBox<String> fontWeightCombo;
    private final ToggleButton boldToggle;
    private final ToggleButton italicToggle;
    private final HBox previewSettingsBar;
    private final ComboBox<String> previewFontFamilyCombo;
    private final ComboBox<Double> previewFontSizeCombo;
    private final ColorPicker previewColorPicker;
    private final CodeArea contentArea;
    private final ScrollPane previewScroll;
    private final VBox previewContent;
    private final Label editorStatus;
    private final ThemeService themeService;
    private final NoteActions actions;
    private final List<Note> notes = new ArrayList<>();
    private final List<NoteFolder> folders = new ArrayList<>();
    private Map<LocalDate, List<Task>> tasksByDate = Map.of();
    private Note currentNote;
    private String currentFolderId = "";
    private boolean updatingEditor;
    private final PauseTransition highlightDebounce = new PauseTransition(Duration.millis(45));

    public NotesController(VBox libraryPane, VBox editorPane, TextField searchField, TilePane grid,
                           Label emptyLabel, Label countLabel, TextField titleField, Label formatLabel,
                           Button backButton, Label locationLabel, ComboBox<FolderOption> folderCombo,
                           ComboBox<TaskOption> taskCombo, Button openTaskButton, HBox markdownToolbar,
                           ToggleButton previewToggle, ComboBox<String> fontFamilyCombo,
                           ComboBox<Double> fontSizeCombo, ComboBox<String> fontWeightCombo,
                           ToggleButton boldToggle, ToggleButton italicToggle,
                           HBox previewSettingsBar, ComboBox<String> previewFontFamilyCombo,
                           ComboBox<Double> previewFontSizeCombo, ColorPicker previewColorPicker,
                           CodeArea contentArea, ScrollPane previewScroll,
                           VBox previewContent, Label editorStatus, ThemeService themeService,
                           NoteActions actions) {
        this.libraryPane = Objects.requireNonNull(libraryPane);
        this.editorPane = Objects.requireNonNull(editorPane);
        this.searchField = Objects.requireNonNull(searchField);
        this.grid = Objects.requireNonNull(grid);
        this.emptyLabel = Objects.requireNonNull(emptyLabel);
        this.countLabel = Objects.requireNonNull(countLabel);
        this.titleField = Objects.requireNonNull(titleField);
        this.formatLabel = Objects.requireNonNull(formatLabel);
        this.backButton = Objects.requireNonNull(backButton);
        this.locationLabel = Objects.requireNonNull(locationLabel);
        this.folderCombo = Objects.requireNonNull(folderCombo);
        this.taskCombo = Objects.requireNonNull(taskCombo);
        this.openTaskButton = Objects.requireNonNull(openTaskButton);
        this.markdownToolbar = Objects.requireNonNull(markdownToolbar);
        this.previewToggle = Objects.requireNonNull(previewToggle);
        this.fontFamilyCombo = Objects.requireNonNull(fontFamilyCombo);
        this.fontSizeCombo = Objects.requireNonNull(fontSizeCombo);
        this.fontWeightCombo = Objects.requireNonNull(fontWeightCombo);
        this.boldToggle = Objects.requireNonNull(boldToggle);
        this.italicToggle = Objects.requireNonNull(italicToggle);
        this.previewSettingsBar = Objects.requireNonNull(previewSettingsBar);
        this.previewFontFamilyCombo = Objects.requireNonNull(previewFontFamilyCombo);
        this.previewFontSizeCombo = Objects.requireNonNull(previewFontSizeCombo);
        this.previewColorPicker = Objects.requireNonNull(previewColorPicker);
        this.contentArea = Objects.requireNonNull(contentArea);
        this.previewScroll = Objects.requireNonNull(previewScroll);
        this.previewContent = Objects.requireNonNull(previewContent);
        this.editorStatus = Objects.requireNonNull(editorStatus);
        this.themeService = Objects.requireNonNull(themeService);
        this.actions = Objects.requireNonNull(actions);
    }

    public void initialize() {
        contentArea.setPlaceholder(new Label("Start writing…"));
        List<String> families = new ArrayList<>(Font.getFamilies());
        families.remove("System");
        families.addFirst("System");
        fontFamilyCombo.setItems(FXCollections.observableArrayList(families));
        previewFontFamilyCombo.setItems(FXCollections.observableArrayList(families));
        fontSizeCombo.setItems(FXCollections.observableArrayList(FONT_SIZES));
        previewFontSizeCombo.setItems(FXCollections.observableArrayList(FONT_SIZES));
        fontWeightCombo.setItems(FXCollections.observableArrayList(FONT_WEIGHTS));
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> renderLibrary());
        titleField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setTitle(newValue);
            changed();
        });
        contentArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setContent(newValue);
            scheduleLiveHighlight();
            if (previewToggle.isSelected()) renderPreview();
            changed();
        });
        highlightDebounce.setOnFinished(event -> applySyntaxHighlighting());
        taskCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setLinkedTaskId(newValue == null || newValue.task() == null ? "" : newValue.task().getId());
            updateOpenTaskButton();
            changed();
        });
        folderCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (updatingEditor || currentNote == null) return;
            currentNote.setFolderId(newValue == null || newValue.folder() == null
                    ? "" : newValue.folder().getId());
            changed();
        });
        fontFamilyCombo.valueProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        fontSizeCombo.valueProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        fontWeightCombo.valueProperty().addListener((observable, oldValue, newValue) -> weightSelectionChanged());
        boldToggle.selectedProperty().addListener((observable, oldValue, selected) -> boldSelectionChanged(selected));
        italicToggle.selectedProperty().addListener((observable, oldValue, newValue) -> typographyChanged());
        previewFontFamilyCombo.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged());
        previewFontSizeCombo.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged());
        previewColorPicker.valueProperty().addListener((observable, oldValue, newValue) -> previewTypographyChanged());
        contentArea.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.TAB && isMarkdown()) {
                contentArea.replaceSelection("    ");
                event.consume();
                return;
            }
            if (!(event.isControlDown() || event.isMetaDown())) return;
            if (event.getCode() == KeyCode.B && isMarkdown()) { wrapSelection("**", "**", "bold text"); event.consume(); }
            else if (event.getCode() == KeyCode.I && isMarkdown()) { wrapSelection("*", "*", "italic text"); event.consume(); }
        });
        showLibrary();
    }

    public void applyState(List<Note> source, List<NoteFolder> sourceFolders,
                           Map<LocalDate, List<Task>> tasks) {
        currentNote = null;
        currentFolderId = "";
        notes.clear();
        if (source != null) notes.addAll(source);
        folders.clear();
        if (sourceFolders != null) folders.addAll(sourceFolders);
        refreshTasks(tasks);
        showLibrary();
    }

    public void refreshTasks(Map<LocalDate, List<Task>> tasks) {
        tasksByDate = tasks == null ? Map.of() : tasks;
        if (currentNote != null) {
            boolean wasUpdating = updatingEditor;
            updatingEditor = true;
            refreshTaskChoices();
            updatingEditor = wasUpdating;
        }
        renderLibrary();
    }

    public List<Note> snapshot() { return new ArrayList<>(notes); }
    public List<NoteFolder> foldersSnapshot() { return new ArrayList<>(folders); }

    public void refreshTheme() {
        if (currentNote == null) return;
        if (currentNote.getPreviewTextColor().isBlank()) {
            updatingEditor = true;
            previewColorPicker.setValue(Color.web(defaultPreviewColor()));
            updatingEditor = false;
            if (previewToggle.isSelected()) renderPreview();
        }
        applySyntaxHighlighting();
    }

    public List<Note> notesForTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return List.of();
        return notes.stream().filter(note -> taskId.equals(note.getLinkedTaskId())).toList();
    }

    public void createNote() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New note");
        themeService.applyTo(dialog);
        ButtonType create = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(create, ButtonType.CANCEL);
        TextField title = new TextField();
        title.setPromptText("Note title");
        ComboBox<NoteFormat> format = new ComboBox<>(FXCollections.observableArrayList(NoteFormat.values()));
        format.setValue(NoteFormat.MARKDOWN);
        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.setPadding(new javafx.geometry.Insets(18));
        form.add(new Label("Title:"), 0, 0);
        form.add(title, 1, 0);
        form.add(new Label("Format:"), 0, 1);
        form.add(format, 1, 1);
        dialog.getDialogPane().setContent(form);
        if (dialog.showAndWait().filter(create::equals).isEmpty()) return;
        Note note = new Note(title.getText().isBlank() ? "Untitled note" : title.getText().trim(), format.getValue());
        note.setFolderId(currentFolderId);
        notes.add(note);
        changed();
        open(note);
    }

    public void createFolder() {
        Optional<String> name = requestFolderName("New folder", "Create a folder", "Folder name", "Create", "");
        if (name.isEmpty()) return;
        if (folderNameExists(name.get(), null)) {
            showFolderNameConflict();
            return;
        }
        folders.add(new NoteFolder(name.get()));
        changed();
    }

    public void openRoot() {
        currentFolderId = "";
        searchField.clear();
        renderLibrary();
    }

    public void openById(String noteId) {
        notes.stream().filter(note -> note.getId().equals(noteId)).findFirst().ifPresent(note -> {
            actions.showNotes();
            open(note);
        });
    }

    public void closeEditor() {
        currentNote = null;
        showLibrary();
    }

    public void deleteCurrent() {
        if (currentNote == null) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete note");
        confirmation.setHeaderText("Delete “" + displayTitle(currentNote) + "”?");
        confirmation.setContentText("This cannot be undone. The link from its task will also disappear.");
        themeService.applyTo(confirmation);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        notes.remove(currentNote);
        currentNote = null;
        changed();
        showLibrary();
    }

    public void openLinkedTask() {
        TaskOption selected = taskCombo.getValue();
        if (selected != null && selected.task() != null) actions.openTask(selected.date(), selected.task());
    }

    public void togglePreview() {
        if (!isMarkdown()) return;
        boolean preview = previewToggle.isSelected();
        contentArea.setVisible(!preview);
        contentArea.setManaged(!preview);
        previewScroll.setVisible(preview);
        previewScroll.setManaged(preview);
        previewSettingsBar.setVisible(preview);
        previewSettingsBar.setManaged(preview);
        if (preview) renderPreview();
    }

    public void markdownHeading() { prefixCurrentLine("## "); }
    public void markdownBold() { wrapSelection("**", "**", "bold text"); }
    public void markdownItalic() { wrapSelection("*", "*", "italic text"); }
    public void markdownLink() { wrapSelection("[", "](https://)", "link text"); }
    public void markdownCode() {
        String selection = contentArea.getSelectedText();
        if (selection.contains("\n") || selection.contains("\r")) {
            wrapSelection("```\n", "\n```", "code");
        } else {
            wrapSelection("`", "`", "code");
        }
    }
    public void markdownChecklist() { prefixCurrentLine("- [ ] "); }

    public void resetTypography() {
        if (currentNote == null) return;
        updatingEditor = true;
        fontFamilyCombo.setValue(Note.DEFAULT_FONT_FAMILY);
        fontSizeCombo.setValue(Note.DEFAULT_FONT_SIZE);
        fontWeightCombo.setValue(weightName(Note.DEFAULT_FONT_WEIGHT));
        boldToggle.setSelected(false);
        italicToggle.setSelected(false);
        updatingEditor = false;
        typographyChanged();
    }

    public void resetPreviewTypography() {
        if (currentNote == null) return;
        updatingEditor = true;
        previewFontFamilyCombo.setValue(Note.DEFAULT_PREVIEW_FONT_FAMILY);
        previewFontSizeCombo.setValue(Note.DEFAULT_PREVIEW_FONT_SIZE);
        previewColorPicker.setValue(Color.web(defaultPreviewColor()));
        currentNote.setPreviewFontFamily(Note.DEFAULT_PREVIEW_FONT_FAMILY);
        currentNote.setPreviewFontSize(Note.DEFAULT_PREVIEW_FONT_SIZE);
        currentNote.setPreviewTextColor("");
        updatingEditor = false;
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void open(Note note) {
        currentNote = note;
        updatingEditor = true;
        titleField.setText(note.getTitle());
        contentArea.replaceText(note.getContent());
        formatLabel.setText(note.getFormat().extension());
        ensureFontFamilyAvailable(note.getFontFamily());
        ensureFontSizeAvailable(note.getFontSize());
        fontFamilyCombo.setValue(note.getFontFamily());
        fontSizeCombo.setValue(note.getFontSize());
        fontWeightCombo.setValue(weightName(note.getFontWeight()));
        boldToggle.setSelected(note.getFontWeight() >= 700);
        italicToggle.setSelected(note.isItalic());
        ensurePreviewFontFamilyAvailable(note.getPreviewFontFamily());
        ensurePreviewFontSizeAvailable(note.getPreviewFontSize());
        previewFontFamilyCombo.setValue(note.getPreviewFontFamily());
        previewFontSizeCombo.setValue(note.getPreviewFontSize());
        previewColorPicker.setValue(Color.web(note.getPreviewTextColor().isBlank()
                ? defaultPreviewColor() : note.getPreviewTextColor()));
        markdownToolbar.setVisible(isMarkdown());
        markdownToolbar.setManaged(isMarkdown());
        previewToggle.setSelected(false);
        contentArea.setVisible(true);
        contentArea.setManaged(true);
        previewScroll.setVisible(false);
        previewScroll.setManaged(false);
        previewSettingsBar.setVisible(false);
        previewSettingsBar.setManaged(false);
        refreshFolderChoices();
        refreshTaskChoices();
        applyTypography();
        applySyntaxHighlighting();
        updatingEditor = false;
        libraryPane.setVisible(false);
        libraryPane.setManaged(false);
        editorPane.setVisible(true);
        editorPane.setManaged(true);
        titleField.requestFocus();
    }

    private void showLibrary() {
        editorPane.setVisible(false);
        editorPane.setManaged(false);
        libraryPane.setVisible(true);
        libraryPane.setManaged(true);
        updateFolderNavigation();
        renderLibrary();
    }

    private void renderLibrary() {
        if (grid == null) return;
        grid.getChildren().clear();
        String query = safe(searchField.getText()).trim().toLowerCase(Locale.ROOT);
        List<NoteFolder> visibleFolders = query.isEmpty() && currentFolderId.isBlank()
                ? List.copyOf(folders)
                : List.of();
        visibleFolders.forEach(folder -> grid.getChildren().add(folderCard(folder)));
        List<Note> visible = notes.stream().filter(note -> {
            boolean matchesQuery = query.isEmpty()
                    || note.getTitle().toLowerCase(Locale.ROOT).contains(query)
                    || note.getContent().toLowerCase(Locale.ROOT).contains(query);
            if (!matchesQuery) return false;
            if (!query.isEmpty()) return true;
            return currentFolderId.isBlank() ? isRootNote(note) : currentFolderId.equals(note.getFolderId());
        }).toList();
        visible.forEach(note -> grid.getChildren().add(noteCard(note)));
        if (query.isEmpty() && currentFolderId.isBlank()) {
            countLabel.setText(notes.size() + (notes.size() == 1 ? " note" : " notes") + " · "
                    + folders.size() + (folders.size() == 1 ? " folder" : " folders"));
        } else {
            countLabel.setText(visible.size() + (visible.size() == 1 ? " note" : " notes"));
        }
        emptyLabel.setText(!query.isEmpty() ? "No notes match your search."
                : currentFolderId.isBlank() ? "There are no notes or folders yet. Create one to get started."
                : "This folder is empty. Create a note here or drag one into it.");
        emptyLabel.setVisible(visible.isEmpty() && visibleFolders.isEmpty());
    }

    private VBox folderCard(NoteFolder folder) {
        FontIcon icon = new FontIcon("fas-folder");
        icon.setIconSize(28);
        icon.getStyleClass().add("note-folder-icon");
        Label title = new Label(folder.getName());
        title.getStyleClass().add("note-folder-title");
        long noteCount = notes.stream().filter(note -> folder.getId().equals(note.getFolderId())).count();
        Label count = new Label(noteCount + (noteCount == 1 ? " note" : " notes"));
        count.getStyleClass().add("note-folder-count");

        MenuItem rename = new MenuItem("Rename");
        rename.setOnAction(event -> renameFolder(folder));
        MenuItem delete = new MenuItem("Delete");
        delete.setOnAction(event -> deleteFolder(folder));
        MenuButton menu = new MenuButton("", null, rename, delete);
        menu.getStyleClass().addAll("icon-button", "note-folder-menu");
        menu.setGraphic(new FontIcon("fas-ellipsis-v"));
        menu.setOnMouseClicked(event -> event.consume());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, icon, spacer, menu);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, header, title, count);
        card.getStyleClass().add("note-folder-card");
        card.setPrefWidth(250);
        card.setOnMouseClicked(event -> openFolder(folder));
        card.setOnDragEntered(event -> {
            if (event.getDragboard().hasContent(NOTE_ID)) card.getStyleClass().add("note-folder-drop-target");
        });
        card.setOnDragExited(event -> card.getStyleClass().remove("note-folder-drop-target"));
        card.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(NOTE_ID)) event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        card.setOnDragDropped(event -> {
            card.getStyleClass().remove("note-folder-drop-target");
            Object draggedId = event.getDragboard().getContent(NOTE_ID);
            Note dragged = notes.stream().filter(note -> note.getId().equals(draggedId)).findFirst().orElse(null);
            if (dragged == null) {
                event.setDropCompleted(false);
                return;
            }
            dragged.setFolderId(folder.getId());
            changed();
            event.setDropCompleted(true);
            event.consume();
        });
        return card;
    }

    private VBox noteCard(Note note) {
        FontIcon icon = new FontIcon(note.getFormat() == NoteFormat.MARKDOWN ? "fas-file-code" : "fas-file-alt");
        icon.setIconSize(15);
        icon.getStyleClass().add("note-card-icon");
        Label extension = new Label(note.getFormat().extension());
        extension.getStyleClass().add("note-card-format");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        FontIcon drag = new FontIcon("fas-grip-vertical");
        drag.setIconSize(12);
        drag.getStyleClass().add("note-drag-handle");
        HBox top = new HBox(8, icon, extension, spacer, drag);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(displayTitle(note));
        title.getStyleClass().add("note-card-title");
        Label excerpt = new Label(excerpt(note.getContent()));
        excerpt.setWrapText(true);
        excerpt.setMaxHeight(58);
        excerpt.getStyleClass().add("note-card-excerpt");
        Label link = new Label(linkedTaskCaption(note));
        link.getStyleClass().add("note-card-link");
        link.setVisible(!note.getLinkedTaskId().isBlank());
        link.setManaged(link.isVisible());
        VBox card = new VBox(10, top, title, excerpt, link);
        card.getStyleClass().add("note-card");
        card.setPrefWidth(250);
        card.setOnMouseClicked(event -> open(note));
        card.setOnDragDetected(event -> {
            SnapshotParameters snapshotParameters = new SnapshotParameters();
            snapshotParameters.setFill(Color.TRANSPARENT);
            var dragImage = card.snapshot(snapshotParameters, null);
            Dragboard board = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.put(NOTE_ID, note.getId());
            board.setContent(content);
            board.setDragView(dragImage,
                    Math.max(0, Math.min(event.getX(), dragImage.getWidth())),
                    Math.max(0, Math.min(event.getY(), dragImage.getHeight())));
            card.getStyleClass().add("note-card-dragging");
            event.consume();
        });
        card.setOnDragDone(event -> card.getStyleClass().remove("note-card-dragging"));
        card.setOnDragEntered(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasContent(NOTE_ID)) {
                card.getStyleClass().add("note-card-drop-target");
            }
        });
        card.setOnDragExited(event -> card.getStyleClass().remove("note-card-drop-target"));
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasContent(NOTE_ID)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        card.setOnDragDropped(event -> {
            card.getStyleClass().remove("note-card-drop-target");
            Object draggedId = event.getDragboard().getContent(NOTE_ID);
            Note dragged = notes.stream().filter(candidate -> candidate.getId().equals(draggedId)).findFirst().orElse(null);
            if (dragged == null || dragged == note) { event.setDropCompleted(false); return; }
            dragged.setFolderId(note.getFolderId());
            int targetIndex = notes.indexOf(note);
            notes.remove(dragged);
            notes.add(Math.min(targetIndex, notes.size()), dragged);
            changed();
            event.setDropCompleted(true);
            event.consume();
        });
        return card;
    }

    private void openFolder(NoteFolder folder) {
        currentFolderId = folder.getId();
        searchField.clear();
        updateFolderNavigation();
        renderLibrary();
    }

    private void updateFolderNavigation() {
        NoteFolder current = findFolder(currentFolderId).orElse(null);
        if (!currentFolderId.isBlank() && current == null) currentFolderId = "";
        boolean insideFolder = current != null;
        backButton.setVisible(insideFolder);
        backButton.setManaged(insideFolder);
        locationLabel.setText(insideFolder ? current.getName() : "All notes");
    }

    private void refreshFolderChoices() {
        List<FolderOption> options = new ArrayList<>();
        options.add(ROOT_FOLDER);
        folders.forEach(folder -> options.add(new FolderOption(folder)));
        folderCombo.setItems(FXCollections.observableArrayList(options));
        FolderOption selected = options.stream()
                .filter(option -> option.folder() != null
                        && option.folder().getId().equals(currentNote.getFolderId()))
                .findFirst().orElse(ROOT_FOLDER);
        folderCombo.setValue(selected);
    }

    private void renameFolder(NoteFolder folder) {
        Optional<String> name = requestFolderName("Rename folder", "Rename folder", "Folder name", "Save",
                folder.getName());
        if (name.isEmpty() || name.get().equals(folder.getName())) return;
        if (folderNameExists(name.get(), folder)) {
            showFolderNameConflict();
            return;
        }
        folder.setName(name.get());
        updateFolderNavigation();
        changed();
    }

    private void deleteFolder(NoteFolder folder) {
        long noteCount = notes.stream().filter(note -> folder.getId().equals(note.getFolderId())).count();
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete folder");
        confirmation.setHeaderText("Delete “" + folder.getName() + "”?");
        confirmation.setContentText(noteCount == 0
                ? "The empty folder will be deleted."
                : noteCount + (noteCount == 1 ? " note will" : " notes will") + " be moved to All notes.");
        themeService.applyTo(confirmation);
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) return;
        notes.stream().filter(note -> folder.getId().equals(note.getFolderId()))
                .forEach(note -> note.setFolderId(""));
        folders.remove(folder);
        if (folder.getId().equals(currentFolderId)) currentFolderId = "";
        updateFolderNavigation();
        changed();
    }

    private Optional<String> requestFolderName(String title, String header, String prompt,
                                               String actionText, String initialValue) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().setHeaderText(header);
        themeService.applyTo(dialog);
        ButtonType action = new ButtonType(actionText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(action, ButtonType.CANCEL);
        TextField name = new TextField(initialValue);
        name.setPromptText(prompt);
        name.setPrefColumnCount(28);
        dialog.getDialogPane().setContent(name);
        Node actionButton = dialog.getDialogPane().lookupButton(action);
        actionButton.setDisable(name.getText().trim().isEmpty());
        name.textProperty().addListener((observable, oldValue, newValue) ->
                actionButton.setDisable(newValue == null || newValue.trim().isEmpty()));
        dialog.setOnShown(event -> {
            name.requestFocus();
            name.selectAll();
        });
        return dialog.showAndWait().filter(action::equals).map(ignored -> name.getText().trim());
    }

    private boolean folderNameExists(String name, NoteFolder excluded) {
        return folders.stream().anyMatch(folder -> folder != excluded && folder.getName().equalsIgnoreCase(name));
    }

    private void showFolderNameConflict() {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Folder already exists");
        alert.setHeaderText("Choose a different folder name");
        alert.setContentText("Folder names must be unique.");
        themeService.applyTo(alert);
        alert.showAndWait();
    }

    private Optional<NoteFolder> findFolder(String folderId) {
        return folders.stream().filter(folder -> folder.getId().equals(folderId)).findFirst();
    }

    private boolean isRootNote(Note note) {
        return note.getFolderId().isBlank() || findFolder(note.getFolderId()).isEmpty();
    }

    private void refreshTaskChoices() {
        List<TaskOption> options = new ArrayList<>();
        options.add(NO_TASK);
        tasksByDate.forEach((date, tasks) -> tasks.forEach(task -> options.add(new TaskOption(date, task))));
        options.subList(1, options.size()).sort(Comparator.comparing(TaskOption::date)
                .thenComparingInt(option -> option.task().getStartMin()));
        taskCombo.setItems(FXCollections.observableArrayList(options));
        TaskOption selected = options.stream().filter(option -> option.task() != null
                && option.task().getId().equals(currentNote.getLinkedTaskId())).findFirst().orElse(NO_TASK);
        taskCombo.setValue(selected);
        updateOpenTaskButton();
    }

    private void updateOpenTaskButton() {
        TaskOption selected = taskCombo.getValue();
        boolean available = selected != null && selected.task() != null;
        openTaskButton.setVisible(available);
        openTaskButton.setManaged(available);
    }

    private void scheduleLiveHighlight() {
        highlightDebounce.playFromStart();
    }

    private void applySyntaxHighlighting() {
        if (currentNote == null) return;
        String text = contentArea.getText();
        if (text.isEmpty()) return;
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        if (!isMarkdown()) {
            spans.add(Collections.emptyList(), text.length());
            contentArea.setStyleSpans(0, spans.create());
            return;
        }
        String[] lines = text.split("\\n", -1);
        boolean fencedCode = false;
        boolean multilineInlineCode = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            boolean fence = line.stripLeading().startsWith("```");
            if (fence) {
                addSpan(spans, "syntax-delimiter", line.length());
                fencedCode = !fencedCode;
            } else if (fencedCode) {
                addCodeSpans(spans, line);
            } else {
                multilineInlineCode = addMarkdownSpans(spans, line, multilineInlineCode);
            }
            if (index < lines.length - 1) addSpan(spans, "markdown-source-text", 1);
        }
        contentArea.setStyleSpans(0, spans.create());
    }

    private boolean addMarkdownSpans(StyleSpansBuilder<Collection<String>> spans,
                                     String line, boolean insideCode) {
        int cursor = 0;
        while (cursor < line.length()) {
            if (insideCode) {
                int end = line.indexOf('`', cursor);
                if (end < 0) {
                    addCodeSpans(spans, line.substring(cursor));
                    return true;
                }
                addCodeSpans(spans, line.substring(cursor, end));
                addSpan(spans, "syntax-delimiter", 1);
                insideCode = false;
                cursor = end + 1;
                continue;
            }
            int start = line.indexOf('`', cursor);
            if (start < 0) {
                addSpan(spans, "markdown-source-text", line.length() - cursor);
                return false;
            }
            if (start > cursor) addSpan(spans, "markdown-source-text", start - cursor);
            addSpan(spans, "syntax-delimiter", 1);
            insideCode = true;
            cursor = start + 1;
        }
        return insideCode;
    }

    private void addCodeSpans(StyleSpansBuilder<Collection<String>> spans, String code) {
        for (CodeSyntaxHighlighter.Token token : CodeSyntaxHighlighter.highlight(code)) {
            addSpan(spans, "syntax-" + token.kind().name().toLowerCase(Locale.ROOT), token.text().length());
        }
    }

    private void addSpan(StyleSpansBuilder<Collection<String>> spans, String styleClass, int length) {
        if (length > 0) spans.add(List.of(styleClass), length);
    }

    private void addCodeTokens(TextFlow target, String code, String typography) {
        for (CodeSyntaxHighlighter.Token token : CodeSyntaxHighlighter.highlight(code)) {
            Text text = new Text(token.text());
            text.getStyleClass().add("syntax-" + token.kind().name().toLowerCase(Locale.ROOT));
            text.setStyle(typography);
            setInlineFill(text, codeTokenColor(token.kind()));
            target.getChildren().add(text);
        }
    }

    private String codeTokenColor(CodeSyntaxHighlighter.TokenKind kind) {
        boolean light = themeService.activeTheme() == ThemeService.Theme.LIGHT;
        if (light) {
            return switch (kind) {
                case KEYWORD -> "#7C3AED";
                case TYPE -> "#047857";
                case VARIABLE -> "#0369A1";
                case FUNCTION -> "#92400E";
                case STRING -> "#B45309";
                case NUMBER -> "#15803D";
                case COMMENT -> "#6B7280";
                case ANNOTATION -> "#A16207";
                case OPERATOR -> "#64748B";
                case PLAIN -> "#334155";
            };
        }
        return switch (kind) {
            case KEYWORD -> "#C586C0";
            case TYPE -> "#4EC9B0";
            case VARIABLE -> "#9CDCFE";
            case FUNCTION -> "#DCDCAA";
            case STRING -> "#CE9178";
            case NUMBER -> "#B5CEA8";
            case COMMENT -> "#6A9955";
            case ANNOTATION -> "#DCDCAA";
            case OPERATOR -> "#D4D4D4";
            case PLAIN -> "#D4D4D4";
        };
    }

    private void renderPreview() {
        previewContent.getChildren().clear();
        previewContent.setMaxWidth(980);
        String normalizedMarkdown = CodeSyntaxHighlighter.normalizeMultilineBackticks(contentArea.getText());
        String[] lines = normalizedMarkdown.split("\\R", -1);
        boolean codeBlock = false;
        VBox code = null;
        for (String raw : lines) {
            if (raw.stripLeading().startsWith("```")) {
                codeBlock = !codeBlock;
                if (codeBlock) {
                    code = new VBox(2);
                    code.getStyleClass().add("markdown-code-block");
                    previewContent.getChildren().add(code);
                }
                continue;
            }
            if (codeBlock) {
                TextFlow line = new TextFlow();
                line.getStyleClass().add("markdown-code-line");
                line.setStyle(previewTypographyStyle(0.82, Math.max(400, currentNote.getFontWeight()), false, "Monospaced"));
                addCodeTokens(line, raw.isEmpty() ? " " : raw,
                        previewTypographyStyle(0.82, Math.max(400, currentNote.getFontWeight()), false, "Monospaced"));
                code.getChildren().add(line);
                continue;
            }
            String trimmed = raw.stripLeading();
            if (trimmed.equals("---") || trimmed.equals("***")) {
                previewContent.getChildren().add(new Separator());
            } else if (trimmed.startsWith("### ")) {
                previewContent.getChildren().add(markdownLine(trimmed.substring(4), "markdown-h3", 1.28, true));
            } else if (trimmed.startsWith("## ")) {
                previewContent.getChildren().add(markdownLine(trimmed.substring(3), "markdown-h2", 1.55, true));
            } else if (trimmed.startsWith("# ")) {
                previewContent.getChildren().add(markdownLine(trimmed.substring(2), "markdown-h1", 1.9, true));
            } else if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
                CheckBox item = new CheckBox(trimmed.substring(6));
                item.setSelected(!trimmed.startsWith("- [ ]"));
                item.setMouseTransparent(true);
                item.setFocusTraversable(false);
                item.getStyleClass().add("markdown-check");
                String itemStyle = previewTypographyStyle(1.0, currentNote.getFontWeight(),
                        currentNote.isItalic(), currentNote.getPreviewFontFamily());
                itemStyle += " -fx-text-fill: " + effectivePreviewColor() + ";";
                item.setStyle(itemStyle);
                previewContent.getChildren().add(item);
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                previewContent.getChildren().add(markdownLine("•  " + trimmed.substring(2), "markdown-list-item", 1.0, false));
            } else if (trimmed.startsWith("> ")) {
                previewContent.getChildren().add(markdownLine(trimmed.substring(2), "markdown-quote", 1.0, false));
            } else {
                previewContent.getChildren().add(markdownLine(raw.isEmpty() ? " " : raw, "markdown-paragraph", 1.0, false));
            }
        }
    }

    private Node markdownLine(String value, String styleClass, double sizeMultiplier, boolean heading) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add(styleClass);
        int baseWeight = heading ? Math.max(700, currentNote.getFontWeight()) : currentNote.getFontWeight();
        flow.setLineSpacing(Math.max(3, currentNote.getFontSize() * 0.28));
        flow.setStyle(previewTypographyStyle(sizeMultiplier, baseWeight,
                currentNote.isItalic(), currentNote.getPreviewFontFamily()));
        Matcher matcher = INLINE_MARKDOWN.matcher(value);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() > cursor) flow.getChildren().add(previewText(
                    value.substring(cursor, matcher.start()), sizeMultiplier, baseWeight, currentNote.isItalic()));
            if (matcher.group(1) != null) {
                String target = matcher.group(1).trim();
                Hyperlink link = new Hyperlink(target);
                link.getStyleClass().add("markdown-wiki-link");
                link.setStyle(previewTypographyStyle(sizeMultiplier, Math.max(600, baseWeight),
                        currentNote.isItalic(), currentNote.getPreviewFontFamily()));
                appendInlineStyle(link, "-fx-text-fill: " + linkColor() + ";");
                link.setOnAction(event -> notes.stream()
                        .filter(note -> note.getTitle().equalsIgnoreCase(target))
                        .findFirst().ifPresent(this::open));
                flow.getChildren().add(link);
            } else if (matcher.group(2) != null || matcher.group(3) != null) {
                Text strong = new Text(matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
                strong.getStyleClass().add("markdown-strong");
                strong.setStyle(previewTypographyStyle(sizeMultiplier, Math.max(700, baseWeight),
                        currentNote.isItalic(), currentNote.getPreviewFontFamily()));
                applyPreviewColor(strong);
                flow.getChildren().add(strong);
            } else if (matcher.group(4) != null) {
                TextFlow code = new TextFlow();
                code.getStyleClass().add("markdown-inline-code");
                code.setStyle(previewTypographyStyle(sizeMultiplier * 0.86, 500, false, "Monospaced"));
                addCodeTokens(code, matcher.group(4),
                        previewTypographyStyle(sizeMultiplier * 0.86, 500, false, "Monospaced"));
                flow.getChildren().add(code);
            } else if (matcher.group(5) != null) {
                Hyperlink link = new Hyperlink(matcher.group(5));
                link.getStyleClass().add("markdown-external-link");
                String url = matcher.group(6);
                link.setTooltip(new Tooltip(url + "\nClick to copy the address"));
                link.setStyle(previewTypographyStyle(sizeMultiplier, Math.max(500, baseWeight),
                        currentNote.isItalic(), currentNote.getPreviewFontFamily()));
                appendInlineStyle(link, "-fx-text-fill: " + linkColor() + ";");
                link.setOnAction(event -> {
                    ClipboardContent clipboard = new ClipboardContent();
                    clipboard.putString(url);
                    Clipboard.getSystemClipboard().setContent(clipboard);
                    editorStatus.setText("Link copied");
                });
                flow.getChildren().add(link);
            } else if (matcher.group(7) != null || matcher.group(8) != null) {
                Text emphasis = new Text(matcher.group(7) != null ? matcher.group(7) : matcher.group(8));
                emphasis.getStyleClass().add("markdown-emphasis");
                emphasis.setStyle(previewTypographyStyle(sizeMultiplier, baseWeight,
                        true, currentNote.getPreviewFontFamily()));
                applyPreviewColor(emphasis);
                flow.getChildren().add(emphasis);
            } else if (matcher.group(9) != null) {
                Text deleted = new Text(matcher.group(9));
                deleted.setStrikethrough(true);
                deleted.getStyleClass().add("markdown-strikethrough");
                deleted.setStyle(previewTypographyStyle(sizeMultiplier, baseWeight,
                        currentNote.isItalic(), currentNote.getPreviewFontFamily()));
                applyPreviewColor(deleted);
                flow.getChildren().add(deleted);
            }
            cursor = matcher.end();
        }
        if (cursor < value.length()) flow.getChildren().add(previewText(
                value.substring(cursor), sizeMultiplier, baseWeight, currentNote.isItalic()));
        if (value.isEmpty()) flow.getChildren().add(previewText(
                " ", sizeMultiplier, baseWeight, currentNote.isItalic()));
        return flow;
    }

    private void wrapSelection(String before, String after, String placeholder) {
        if (!isMarkdown()) return;
        int start = contentArea.getSelection().getStart();
        int end = contentArea.getSelection().getEnd();
        String selected = contentArea.getSelectedText();
        String body = selected.isEmpty() ? placeholder : selected;
        contentArea.replaceText(start, end, before + body + after);
        contentArea.selectRange(start + before.length(), start + before.length() + body.length());
        contentArea.requestFocus();
    }

    private void prefixCurrentLine(String prefix) {
        if (!isMarkdown()) return;
        int caret = contentArea.getCaretPosition();
        int lineStart = contentArea.getText().lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        contentArea.insertText(lineStart, prefix);
        contentArea.moveTo(caret + prefix.length());
        contentArea.requestFocus();
    }

    private void typographyChanged() {
        if (updatingEditor || currentNote == null || fontFamilyCombo.getValue() == null
                || fontSizeCombo.getValue() == null || fontWeightCombo.getValue() == null) return;
        currentNote.setFontFamily(fontFamilyCombo.getValue());
        currentNote.setFontSize(fontSizeCombo.getValue());
        currentNote.setFontWeight(weightValue(fontWeightCombo.getValue()));
        currentNote.setItalic(italicToggle.isSelected());
        applyTypography();
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void previewTypographyChanged() {
        if (updatingEditor || currentNote == null || previewFontFamilyCombo.getValue() == null
                || previewFontSizeCombo.getValue() == null || previewColorPicker.getValue() == null) return;
        currentNote.setPreviewFontFamily(previewFontFamilyCombo.getValue());
        currentNote.setPreviewFontSize(previewFontSizeCombo.getValue());
        currentNote.setPreviewTextColor(toHex(previewColorPicker.getValue()));
        if (previewToggle.isSelected()) renderPreview();
        changed();
    }

    private void weightSelectionChanged() {
        if (updatingEditor || fontWeightCombo.getValue() == null) return;
        updatingEditor = true;
        boldToggle.setSelected(weightValue(fontWeightCombo.getValue()) >= 700);
        updatingEditor = false;
        typographyChanged();
    }

    private void boldSelectionChanged(boolean selected) {
        if (updatingEditor || currentNote == null) return;
        updatingEditor = true;
        fontWeightCombo.setValue(selected ? "Bold" : "Regular");
        updatingEditor = false;
        typographyChanged();
    }

    private void applyTypography() {
        if (currentNote == null) return;
        contentArea.setStyle(typographyStyle(1.0, currentNote.getFontWeight(),
                currentNote.isItalic(), currentNote.getFontFamily()));
        previewContent.setStyle("-fx-font-family: \"" + cssFont(currentNote.getFontFamily()) + "\";");
        applySyntaxHighlighting();
    }

    private String typographyStyle(double sizeMultiplier, int weight, boolean italic, String family) {
        return fontStyle(currentNote.getFontSize(), sizeMultiplier, weight, italic, family);
    }

    private String previewTypographyStyle(double sizeMultiplier, int weight, boolean italic, String family) {
        return fontStyle(currentNote.getPreviewFontSize(), sizeMultiplier, weight, italic, family);
    }

    private String fontStyle(double baseSize, double sizeMultiplier, int weight, boolean italic, String family) {
        double size = Math.round(baseSize * sizeMultiplier * 10.0) / 10.0;
        return "-fx-font-family: \"" + cssFont(family) + "\";"
                + " -fx-font-size: " + size + "px;"
                + " -fx-font-weight: " + weight + ";"
                + " -fx-font-style: " + (italic ? "italic" : "normal") + ";";
    }

    private String cssFont(String family) { return safe(family).replace("\\", "").replace("\"", ""); }

    private void ensureFontFamilyAvailable(String family) {
        if (!fontFamilyCombo.getItems().contains(family)) fontFamilyCombo.getItems().add(family);
    }

    private void ensureFontSizeAvailable(double size) {
        if (!fontSizeCombo.getItems().contains(size)) {
            fontSizeCombo.getItems().add(size);
            fontSizeCombo.getItems().sort(Double::compareTo);
        }
    }

    private void ensurePreviewFontFamilyAvailable(String family) {
        if (!previewFontFamilyCombo.getItems().contains(family)) previewFontFamilyCombo.getItems().add(family);
    }

    private void ensurePreviewFontSizeAvailable(double size) {
        if (!previewFontSizeCombo.getItems().contains(size)) {
            previewFontSizeCombo.getItems().add(size);
            previewFontSizeCombo.getItems().sort(Double::compareTo);
        }
    }

    private Text previewText(String value, double sizeMultiplier, int weight, boolean italic) {
        Text text = new Text(value);
        text.setStyle(previewTypographyStyle(sizeMultiplier, weight,
                italic, currentNote.getPreviewFontFamily()));
        applyPreviewColor(text);
        return text;
    }

    private void applyPreviewColor(Text text) {
        setInlineFill(text, effectivePreviewColor());
    }

    private boolean hasCustomPreviewColor() {
        return currentNote != null && !currentNote.getPreviewTextColor().isBlank();
    }

    private String effectivePreviewColor() {
        return hasCustomPreviewColor() ? currentNote.getPreviewTextColor() : defaultPreviewColor();
    }

    private String linkColor() {
        return themeService.activeTheme() == ThemeService.Theme.LIGHT ? "#2563EB" : "#60A5FA";
    }

    private void setInlineFill(Text text, String color) {
        appendInlineStyle(text, "-fx-fill: " + color + ";");
    }

    private void appendInlineStyle(Node node, String css) {
        String existing = node.getStyle();
        node.setStyle((existing == null ? "" : existing) + " " + css);
    }

    private String defaultPreviewColor() {
        return themeService.activeTheme() == ThemeService.Theme.LIGHT ? "#334155" : "#CBD5E1";
    }

    private String toHex(Color color) {
        return String.format("#%02X%02X%02X", Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255), Math.round(color.getBlue() * 255));
    }

    private int weightValue(String name) {
        return switch (name) {
            case "Medium" -> 500;
            case "Semibold" -> 600;
            case "Bold" -> 700;
            default -> 400;
        };
    }

    private String weightName(int weight) {
        if (weight >= 700) return "Bold";
        if (weight >= 600) return "Semibold";
        if (weight >= 500) return "Medium";
        return "Regular";
    }

    private void changed() {
        editorStatus.setText("Saved automatically");
        actions.dataChanged();
        renderLibrary();
    }

    private boolean isMarkdown() { return currentNote != null && currentNote.getFormat() == NoteFormat.MARKDOWN; }
    private String displayTitle(Note note) { return note.getTitle().isBlank() ? "Untitled note" : note.getTitle(); }
    private String excerpt(String content) {
        String plain = safe(content).replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("[`*_>\\[\\]]", "").replaceAll("\\s+", " ").trim();
        if (plain.isEmpty()) return "Empty note";
        return plain.length() > 135 ? plain.substring(0, 132) + "…" : plain;
    }

    private String linkedTaskCaption(Note note) {
        return findTask(note.getLinkedTaskId()).map(option -> "Linked to: " + option.task().getTitle()).orElse("Linked task unavailable");
    }

    private Optional<TaskOption> findTask(String taskId) {
        if (taskId == null || taskId.isBlank()) return Optional.empty();
        return tasksByDate.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(task -> new TaskOption(entry.getKey(), task)))
                .filter(option -> taskId.equals(option.task().getId())).findFirst();
    }

    private String safe(String value) { return value == null ? "" : value; }

    public record TaskOption(LocalDate date, Task task) {
        @Override public String toString() {
            if (task == null) return "No linked task";
            return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)) + " · " + task.getTitle();
        }
    }

    public record FolderOption(NoteFolder folder) {
        @Override public String toString() {
            return folder == null ? "All notes" : folder.getName();
        }
    }

    public interface NoteActions {
        void dataChanged();
        void showNotes();
        void openTask(LocalDate date, Task task);
    }
}
