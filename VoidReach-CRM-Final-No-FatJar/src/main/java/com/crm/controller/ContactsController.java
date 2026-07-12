package com.crm.controller;

import com.crm.model.Contact;
import com.crm.service.ThemeService;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns contact presentation, filtering, pagination, editing, selection, and clipboard behavior. */
public final class ContactsController {
    private final TableView<Contact> table;
    private final TableColumn<Contact, String> nameColumn;
    private final TableColumn<Contact, String> companyColumn;
    private final TableColumn<Contact, String> titleColumn;
    private final TableColumn<Contact, String> emailColumn;
    private final TableColumn<Contact, String> phoneColumn;
    private final TableColumn<Contact, String> lastInteractionColumn;
    private final TableColumn<Contact, String> tagsColumn;
    private final TableColumn<Contact, Boolean> selectColumn;
    private final TextField searchField;
    private final ComboBox<String> rowsPerPageCombo;
    private final Pagination pagination;
    private final Label paginationInfoLabel;
    private final Button selectContactsButton;
    private final MenuButton sortMenu;
    private final ThemeService themeService;
    private final Runnable dataChanged;
    private final ObservableList<Contact> contacts = FXCollections.observableArrayList();
    private final FilteredList<Contact> filteredContacts = new FilteredList<>(contacts, contact -> true);
    private final SortedList<Contact> sortedContacts = new SortedList<>(filteredContacts);
    private final Set<Contact> checkedContacts = new HashSet<>();
    private final Map<TableColumn<Contact, String>, StringProperty> columnTitles = new LinkedHashMap<>();
    private boolean selectionMode;

    public ContactsController(TableView<Contact> table,
                              TableColumn<Contact, String> nameColumn,
                              TableColumn<Contact, String> companyColumn,
                              TableColumn<Contact, String> titleColumn,
                              TableColumn<Contact, String> emailColumn,
                              TableColumn<Contact, String> phoneColumn,
                              TableColumn<Contact, String> lastInteractionColumn,
                              TableColumn<Contact, String> tagsColumn,
                              TableColumn<Contact, Boolean> selectColumn,
                              TextField searchField, ComboBox<String> rowsPerPageCombo,
                              Pagination pagination, Label paginationInfoLabel,
                              Button selectContactsButton, MenuButton sortMenu, ThemeService themeService,
                              Runnable dataChanged) {
        this.table = Objects.requireNonNull(table);
        this.nameColumn = Objects.requireNonNull(nameColumn);
        this.companyColumn = Objects.requireNonNull(companyColumn);
        this.titleColumn = Objects.requireNonNull(titleColumn);
        this.emailColumn = Objects.requireNonNull(emailColumn);
        this.phoneColumn = Objects.requireNonNull(phoneColumn);
        this.lastInteractionColumn = Objects.requireNonNull(lastInteractionColumn);
        this.tagsColumn = Objects.requireNonNull(tagsColumn);
        this.selectColumn = Objects.requireNonNull(selectColumn);
        this.searchField = Objects.requireNonNull(searchField);
        this.rowsPerPageCombo = Objects.requireNonNull(rowsPerPageCombo);
        this.pagination = Objects.requireNonNull(pagination);
        this.paginationInfoLabel = Objects.requireNonNull(paginationInfoLabel);
        this.selectContactsButton = Objects.requireNonNull(selectContactsButton);
        this.sortMenu = Objects.requireNonNull(sortMenu);
        this.themeService = Objects.requireNonNull(themeService);
        this.dataChanged = Objects.requireNonNull(dataChanged);
    }

    public void initialize() {
        setupColumns();
        setupEditableHeaders();
        setupSortMenu();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setRowFactory(ignored -> {
            TableRow<Contact> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                boolean openRequest = event.getButton() == MouseButton.SECONDARY
                        || event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 1;
                if (!selectionMode && openRequest && !row.isEmpty()) {
                    showContactDialog(row.getItem());
                    event.consume();
                }
            });
            return row;
        });
        rowsPerPageCombo.setItems(FXCollections.observableArrayList("15", "25", "50", "100", "All"));
        rowsPerPageCombo.setValue("15");
        rowsPerPageCombo.valueProperty().addListener((observable, oldValue, newValue) -> updatePagination());
        pagination.currentPageIndexProperty().addListener((observable, oldValue, newValue) -> updateTablePage());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterContacts(newValue);
            updatePagination();
        });
        table.setOnKeyPressed(this::handleTableKeyPressed);
        updatePagination();
    }

    public void setContacts(List<Contact> values) {
        contacts.setAll(values == null ? List.of() : values);
        checkedContacts.clear();
        updatePagination();
    }

    public List<Contact> snapshot() {
        return new ArrayList<>(contacts);
    }

    public void requestInitialFocus() {
        javafx.application.Platform.runLater(searchField::requestFocus);
    }

    public void addContact() { showContactDialog(null); }

    public void toggleSelection() {
        selectionMode = !selectionMode;
        selectColumn.setVisible(selectionMode);
        selectContactsButton.setText(selectionMode ? "Done" : "Select");
        table.getSelectionModel().setSelectionMode(selectionMode ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        if (!selectionMode) {
            checkedContacts.clear();
            table.getSelectionModel().clearSelection();
        }
        table.refresh();
    }

    public void deleteSelectedContacts() {
        List<Contact> selected = selectedContacts();
        if (selected.isEmpty()) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Contact");
        themeService.applyTo(alert);
        alert.setHeaderText(selected.size() == 1
                ? "Delete " + selected.getFirst().nameProperty().get() + "?"
                : "Delete " + selected.size() + " contacts?");
        alert.setContentText("This action cannot be undone.");
        if (alert.showAndWait().filter(result -> result == ButtonType.OK).isPresent()) {
            contacts.removeAll(selected);
            checkedContacts.removeAll(selected);
            table.getSelectionModel().clearSelection();
            updatePagination();
            dataChanged.run();
        }
    }

    private void setupColumns() {
        selectColumn.setCellValueFactory(data -> new SimpleBooleanProperty(checkedContacts.contains(data.getValue())));
        selectColumn.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            {
                checkBox.getStyleClass().add("contact-select-check");
                checkBox.setOnAction(event -> {
                    Contact contact = getTableRow() == null ? null : getTableRow().getItem();
                    if (contact == null) return;
                    if (checkBox.isSelected()) checkedContacts.add(contact);
                    else checkedContacts.remove(contact);
                    table.refresh();
                });
            }
            @Override protected void updateItem(Boolean selected, boolean empty) {
                super.updateItem(selected, empty);
                if (empty || !selectionMode) setGraphic(null);
                else {
                    checkBox.setSelected(Boolean.TRUE.equals(selected));
                    setGraphic(checkBox);
                }
            }
        });
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        companyColumn.setCellValueFactory(new PropertyValueFactory<>("company"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        lastInteractionColumn.setCellValueFactory(new PropertyValueFactory<>("lastInteraction"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
        Comparator<String> textComparator = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        nameColumn.setComparator(textComparator);
        companyColumn.setComparator(textComparator);
        titleColumn.setComparator(textComparator);
        emailColumn.setComparator(textComparator);
        phoneColumn.setComparator(textComparator);
        lastInteractionColumn.setComparator(textComparator);
        tagsColumn.setComparator(textComparator);
        tagsColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                Label tag = new Label(item);
                tag.getStyleClass().add("tag");
                if (item.equalsIgnoreCase("Client")) tag.getStyleClass().add("tag-client");
                else if (item.equalsIgnoreCase("Tech")) tag.getStyleClass().add("tag-tech");
                else if (item.equalsIgnoreCase("Follow-up")) tag.getStyleClass().add("tag-followup");
                setGraphic(tag);
            }
        });
    }

    private void setupEditableHeaders() {
        configureEditableHeader(nameColumn, "Name");
        configureEditableHeader(companyColumn, "Company");
        configureEditableHeader(titleColumn, "Job title");
        configureEditableHeader(emailColumn, "Email");
        configureEditableHeader(phoneColumn, "Phone");
        configureEditableHeader(lastInteractionColumn, "Last interaction");
        configureEditableHeader(tagsColumn, "Tag");
    }

    private void configureEditableHeader(TableColumn<Contact, String> column, String initialTitle) {
        StringProperty title = new SimpleStringProperty(initialTitle);
        columnTitles.put(column, title);

        Label label = new Label();
        label.textProperty().bind(title);
        label.getStyleClass().add("editable-column-header-label");

        TextField editor = new TextField(initialTitle);
        editor.getStyleClass().add("editable-column-header");
        editor.textProperty().bindBidirectional(title);
        editor.setPrefWidth(Math.max(45, column.getPrefWidth() - 32));
        column.widthProperty().addListener((observable, oldWidth, newWidth) ->
                editor.setPrefWidth(Math.max(45, newWidth.doubleValue() - 32)));
        editor.setOnAction(event -> table.requestFocus());
        editor.focusedProperty().addListener((observable, wasFocused, isFocused) -> {
            if (!isFocused && column.getGraphic() == editor) column.setGraphic(label);
        });

        label.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) return;
            column.setGraphic(editor);
            javafx.application.Platform.runLater(() -> {
                editor.requestFocus();
                editor.selectAll();
            });
            event.consume();
        });

        column.setText("");
        column.setGraphic(label);
        column.setSortable(false);
    }

    private void setupSortMenu() {
        MenuItem clearSort = new MenuItem("No sorting");
        clearSort.setOnAction(event -> clearSort());
        sortMenu.getItems().addAll(clearSort, new SeparatorMenuItem());

        columnTitles.forEach((column, title) -> {
            Menu columnMenu = new Menu();
            columnMenu.textProperty().bind(title);
            MenuItem ascending = new MenuItem();
            ascending.textProperty().bind(Bindings.concat(title, " — ascending"));
            ascending.setOnAction(event -> applySort(column, TableColumn.SortType.ASCENDING, title.get()));
            MenuItem descending = new MenuItem();
            descending.textProperty().bind(Bindings.concat(title, " — descending"));
            descending.setOnAction(event -> applySort(column, TableColumn.SortType.DESCENDING, title.get()));
            columnMenu.getItems().addAll(ascending, descending);
            sortMenu.getItems().add(columnMenu);
        });
    }

    private void applySort(TableColumn<Contact, String> column, TableColumn.SortType type, String title) {
        Comparator<Contact> comparator = (left, right) -> column.getComparator().compare(
                column.getCellObservableValue(left).getValue(),
                column.getCellObservableValue(right).getValue());
        sortedContacts.setComparator(type == TableColumn.SortType.ASCENDING
                ? comparator : comparator.reversed());
        pagination.setCurrentPageIndex(0);
        updateTablePage();
        sortMenu.setText("Sort: " + title + (type == TableColumn.SortType.ASCENDING ? " ↑" : " ↓"));
    }

    private void clearSort() {
        sortedContacts.setComparator(null);
        pagination.setCurrentPageIndex(0);
        updateTablePage();
        sortMenu.setText("Sort");
    }

    private void handleTableKeyPressed(KeyEvent event) {
        if (isShortcut(event, KeyCode.C)) {
            copySelectedContacts();
            event.consume();
        } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
            deleteSelectedContacts();
        }
    }

    private boolean isShortcut(KeyEvent event, KeyCode key) {
        return event.getCode() == key && (event.isControlDown() || event.isMetaDown());
    }

    private List<Contact> selectedContacts() {
        Set<Contact> selected = new HashSet<>(checkedContacts);
        selected.addAll(table.getSelectionModel().getSelectedItems());
        return new ArrayList<>(selected);
    }

    private void copySelectedContacts() {
        List<Contact> selected = selectedContacts();
        if (selected.isEmpty()) return;
        StringBuilder text = new StringBuilder("Name\tCompany\tTitle\tEmail\tPhone\tTags\tDescription\n");
        for (Contact contact : selected) {
            text.append(clipboardValue(contact.nameProperty().get())).append('\t')
                    .append(clipboardValue(contact.companyProperty().get())).append('\t')
                    .append(clipboardValue(contact.titleProperty().get())).append('\t')
                    .append(clipboardValue(contact.emailProperty().get())).append('\t')
                    .append(clipboardValue(contact.phoneProperty().get())).append('\t')
                    .append(clipboardValue(contact.tagsProperty().get())).append('\t')
                    .append(clipboardValue(contact.descriptionProperty().get())).append('\n');
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text.toString());
        Clipboard.getSystemClipboard().setContent(content);
    }

    static String clipboardValue(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ');
    }

    private void showContactDialog(Contact contact) {
        Dialog<Contact> dialog = new Dialog<>();
        dialog.setTitle(contact == null ? "Add New Contact" : "Edit Contact");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        TextField name = new TextField();
        TextField company = new TextField();
        TextField title = new TextField();
        TextField email = new TextField();
        TextField phone = new TextField();
        TextArea description = new TextArea();
        description.setPrefRowCount(3);
        ComboBox<String> tags = new ComboBox<>(FXCollections.observableArrayList("Client", "Tech", "Follow-up"));
        if (contact != null) {
            name.setText(contact.nameProperty().get());
            company.setText(contact.companyProperty().get());
            title.setText(contact.titleProperty().get());
            email.setText(contact.emailProperty().get());
            phone.setText(contact.phoneProperty().get());
            tags.setValue(contact.tagsProperty().get());
            description.setText(contact.descriptionProperty().get());
        }
        grid.add(new Label("Name:"), 0, 0); grid.add(name, 1, 0);
        grid.add(new Label("Company:"), 0, 1); grid.add(company, 1, 1);
        grid.add(new Label("Title:"), 0, 2); grid.add(title, 1, 2);
        grid.add(new Label("Email:"), 0, 3); grid.add(email, 1, 3);
        grid.add(new Label("Phone:"), 0, 4); grid.add(phone, 1, 4);
        grid.add(new Label("Tags:"), 0, 5); grid.add(tags, 1, 5);
        grid.add(new Label("Description:"), 0, 6); grid.add(description, 1, 6);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(result -> {
            if (result != save) return null;
            if (contact == null) return new Contact(name.getText(), company.getText(), title.getText(),
                    email.getText(), phone.getText(), "Just now", tags.getValue(), description.getText());
            contact.setName(name.getText());
            contact.setCompany(company.getText());
            contact.setTitle(title.getText());
            contact.setEmail(email.getText());
            contact.setPhone(phone.getText());
            contact.setTags(tags.getValue());
            contact.setDescription(description.getText());
            return contact;
        });
        dialog.showAndWait().ifPresent(saved -> {
            if (contact == null) contacts.add(0, saved);
            updatePagination();
            table.refresh();
            dataChanged.run();
        });
    }

    private void filterContacts(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            filteredContacts.setPredicate(contact -> true);
            return;
        }
        String query = searchText.toLowerCase();
        filteredContacts.setPredicate(contact ->
                safe(contact.nameProperty().get()).contains(query)
                        || safe(contact.companyProperty().get()).contains(query)
                        || safe(contact.emailProperty().get()).contains(query)
                        || safe(contact.descriptionProperty().get()).contains(query));
    }

    private String safe(String value) { return value == null ? "" : value.toLowerCase(); }

    private void updatePagination() {
        String selected = rowsPerPageCombo.getValue();
        if (selected == null) return;
        int total = sortedContacts.size();
        if (selected.equals("All")) {
            pagination.setPageCount(1);
            pagination.setCurrentPageIndex(0);
            pagination.setVisible(false);
            pagination.setManaged(false);
        } else {
            int rowsPerPage = Integer.parseInt(selected);
            pagination.setPageCount(Math.max(1, (total + rowsPerPage - 1) / rowsPerPage));
            pagination.setVisible(true);
            pagination.setManaged(true);
        }
        updateTablePage();
    }

    private void updateTablePage() {
        String selected = rowsPerPageCombo.getValue();
        if (selected == null) return;
        int total = sortedContacts.size();
        if (selected.equals("All")) {
            table.setItems(FXCollections.observableArrayList(sortedContacts));
            paginationInfoLabel.setText(String.format("Showing all %d Contacts", total));
            return;
        }
        int rowsPerPage = Integer.parseInt(selected);
        int from = pagination.getCurrentPageIndex() * rowsPerPage;
        int to = Math.min(from + rowsPerPage, total);
        if (from >= total) {
            table.setItems(FXCollections.observableArrayList());
            paginationInfoLabel.setText("Showing 0-0 of " + total + " Contacts");
        } else {
            table.setItems(FXCollections.observableArrayList(sortedContacts.subList(from, to)));
            paginationInfoLabel.setText(String.format("Showing %d-%d of %d Contacts", from + 1, to, total));
        }
    }
}
