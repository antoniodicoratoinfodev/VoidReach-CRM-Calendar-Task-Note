package com.crm.controller;

import com.crm.model.Contact;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MainController {

    @FXML private TableView<Contact> contactsTable;
    @FXML private TableColumn<Contact, String> nameColumn;
    @FXML private TableColumn<Contact, String> companyColumn;
    @FXML private TableColumn<Contact, String> titleColumn;
    @FXML private TableColumn<Contact, String> emailColumn;
    @FXML private TableColumn<Contact, String> phoneColumn;
    @FXML private TableColumn<Contact, String> lastInteractionColumn;
    @FXML private TableColumn<Contact, String> tagsColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> rowsPerPageCombo;
    @FXML private Pagination contactsPagination;
    @FXML private Label paginationInfoLabel;

    @FXML private VBox contactsView;
    @FXML private VBox calendarView;
    @FXML private VBox genericView;
    @FXML private Label genericTitle;
    @FXML private FontIcon genericIcon;
    @FXML private VBox sidebarContainer;
    @FXML private HBox navbarContainer;
    @FXML private Button themeToggleBtn;
    @FXML private FontIcon themeToggleIcon;
    
    @FXML private VBox timeLabelsContainer;
    @FXML private AnchorPane calendarTimelineArea;
    @FXML private DatePicker calendarDatePicker;

    // Right Sidebar elements
    @FXML private Label miniMonthYearLabel;
    @FXML private GridPane miniCalendarGrid;
    @FXML private VBox upcomingActivitiesList;

    private ObservableList<Contact> contactData = FXCollections.observableArrayList();
    private FilteredList<Contact> filteredData;

    private static final double MINUTE_HEIGHT = 1.0;
    private static final double HOUR_HEIGHT = 60.0;

    private double dragAnchorY;
    private double dragInitialTop;
    
    private boolean isDarkMode = true;
    
    private static class Task {
        String title; String description; int startMin; int duration; String color;
        Task(String t, String d, int s, int dur, String c) { title = t; description = d; startMin = s; duration = dur; color = c; }
    }
    private Map<LocalDate, List<Task>> taskDatabase = new HashMap<>();
    private YearMonth currentMiniMonth;

    @FXML
    public void initialize() {
        filteredData = new FilteredList<>(contactData, p -> true);
        searchField.textProperty().addListener((obs, old, newValue) -> {
            filterContacts(newValue);
            updatePagination();
        });
        
        setupColumns();
        setupContactRowFactory();
        setupPagination();
        loadDummyContacts();
        
        LocalDate today = LocalDate.now();
        calendarDatePicker.setValue(today);
        currentMiniMonth = YearMonth.from(today);
        
        addTaskToDatabase(today, new Task("Precision Meeting", "Discuss precision requirements.", 542, 182, "Blue"));
        addTaskToDatabase(today, new Task("Quick Sync", "Weekly internal sync.", 840, 45, "Red"));
        
        calendarDatePicker.valueProperty().addListener((obs, old, newValue) -> {
            setupMainCalendar();
            updateRightSidebar();
        });

        setupMainCalendar();
        updateRightSidebar();
        updateThemeButton();
        
        // Setup delete on key press
        contactsTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                handleDeleteContact();
            }
        });
    }

    private void setupPagination() {
        rowsPerPageCombo.setItems(FXCollections.observableArrayList("15", "25", "50", "100", "All"));
        rowsPerPageCombo.setValue("15");
        rowsPerPageCombo.valueProperty().addListener((obs, old, newValue) -> updatePagination());
        
        contactsPagination.currentPageIndexProperty().addListener((obs, old, newValue) -> updateTablePage());
    }

    private void updatePagination() {
        String selected = rowsPerPageCombo.getValue();
        int totalItems = filteredData.size();
        
        if (selected.equals("All")) {
            contactsPagination.setPageCount(1);
            contactsPagination.setCurrentPageIndex(0);
            contactsPagination.setVisible(false);
            contactsPagination.setManaged(false);
        } else {
            int rowsPerPage = Integer.parseInt(selected);
            int pageCount = (totalItems + rowsPerPage - 1) / rowsPerPage;
            if (pageCount == 0) pageCount = 1;
            contactsPagination.setPageCount(pageCount);
            contactsPagination.setVisible(true);
            contactsPagination.setManaged(true);
        }
        updateTablePage();
    }

    private void updateTablePage() {
        String selected = rowsPerPageCombo.getValue();
        int totalItems = filteredData.size();
        
        if (selected.equals("All")) {
            contactsTable.setItems(FXCollections.observableArrayList(filteredData));
            paginationInfoLabel.setText(String.format("Showing all %d Contacts", totalItems));
        } else {
            int rowsPerPage = Integer.parseInt(selected);
            int pageIndex = contactsPagination.getCurrentPageIndex();
            int fromIndex = pageIndex * rowsPerPage;
            int toIndex = Math.min(fromIndex + rowsPerPage, totalItems);
            
            if (fromIndex > totalItems) {
                contactsTable.setItems(FXCollections.observableArrayList());
                paginationInfoLabel.setText("Showing 0-0 of " + totalItems + " Contacts");
            } else {
                contactsTable.setItems(FXCollections.observableArrayList(filteredData.subList(fromIndex, toIndex)));
                paginationInfoLabel.setText(String.format("Showing %d-%d of %d Contacts", fromIndex + 1, toIndex, totalItems));
            }
        }
    }

    private void addTaskToDatabase(LocalDate date, Task task) {
        taskDatabase.computeIfAbsent(date, k -> new ArrayList<>()).add(task);
    }

    private void setupColumns() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        companyColumn.setCellValueFactory(new PropertyValueFactory<>("company"));
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        lastInteractionColumn.setCellValueFactory(new PropertyValueFactory<>("lastInteraction"));
        tagsColumn.setCellValueFactory(new PropertyValueFactory<>("tags"));
        tagsColumn.setCellFactory(column -> new TableCell<Contact, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); }
                else {
                    Label tagLabel = new Label(item); tagLabel.getStyleClass().add("tag");
                    if (item.equalsIgnoreCase("Client")) tagLabel.getStyleClass().add("tag-client");
                    else if (item.equalsIgnoreCase("Tech")) tagLabel.getStyleClass().add("tag-tech");
                    else if (item.equalsIgnoreCase("Follow-up")) tagLabel.getStyleClass().add("tag-followup");
                    setGraphic(tagLabel);
                }
            }
        });
    }

    private void setupContactRowFactory() {
        contactsTable.setRowFactory(tv -> {
            TableRow<Contact> row = new TableRow<>();
            row.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && (!row.isEmpty())) showContactDialog(row.getItem()); });
            return row;
        });
    }

    private void loadDummyContacts() {
        contactData.add(new Contact("Sarah Jensen", "Delta Dynamics", "Director of Sales", "s.jensen@deltadynamics.com", "+1 555-1234", "Today, 9:15 AM", "Client"));
        contactData.add(new Contact("Michael Lee", "TechSolutions Inc", "Project Manager", "m.lee@techsolutions.com", "+1 555-5678", "Today, 10:30 AM", "Tech"));
        contactData.add(new Contact("David Chen", "Nova Corp", "Marketing Lead", "d.chen@novacorp.io", "+1 555-9012", "Yesterday, 2:15 PM", "Follow-up"));
        
        for (int i = 1; i <= 100; i++) {
            contactData.add(new Contact("Contact " + i, "Company " + i, "Title " + i, "email" + i + "@example.com", "+1 555-00" + i, "Yesterday", "Client"));
        }
        
        updatePagination();
    }

    private void setupMainCalendar() {
        calendarTimelineArea.getChildren().clear();
        timeLabelsContainer.getChildren().clear();
        for (int i = 0; i <= 24; i++) {
            double y = i * HOUR_HEIGHT;
            if (i < 24) {
                Label hourLabel = new Label(String.format("%02d:00", i));
                hourLabel.getStyleClass().add("hour-label"); hourLabel.setPrefHeight(HOUR_HEIGHT);
                timeLabelsContainer.getChildren().add(hourLabel);
            }
            Line line = new Line(0, y, 2000, y); line.getStyleClass().add("timeline-line-hour");
            calendarTimelineArea.getChildren().add(line);
            if (i < 24) {
                for (int q = 1; q < 4; q++) {
                    double qy = y + (q * 15 * MINUTE_HEIGHT);
                    Line qLine = new Line(0, qy, 2000, qy); qLine.getStyleClass().add("timeline-line");
                    calendarTimelineArea.getChildren().add(qLine);
                }
            }
        }
        calendarTimelineArea.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getTarget() == calendarTimelineArea && e.getButton() == MouseButton.PRIMARY) {
                showTaskEditDialog(null, (int)(e.getY() / MINUTE_HEIGHT), 60, "");
            }
        });

        List<Task> tasks = taskDatabase.getOrDefault(calendarDatePicker.getValue(), new ArrayList<>());
        for (Task t : tasks) renderTaskOnTimeline(t);
    }

    private void renderTaskOnTimeline(Task task) {
        VBox taskBox = new VBox();
        taskBox.getStyleClass().add("task-entry");
        taskBox.getStyleClass().add("task-" + task.color.toLowerCase());
        AnchorPane.setLeftAnchor(taskBox, 10.0);
        AnchorPane.setRightAnchor(taskBox, 10.0);
        taskBox.setPrefHeight(task.duration * MINUTE_HEIGHT);
        AnchorPane.setTopAnchor(taskBox, task.startMin * MINUTE_HEIGHT);
        Label tLabel = new Label(task.title); tLabel.getStyleClass().add("task-title");
        Label timeLabel = new Label(); timeLabel.getStyleClass().add("task-time");
        updateTimeLabel(timeLabel, task.startMin, task.duration);
        Label dLabel = new Label(task.description); dLabel.getStyleClass().add("task-desc");
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Region resizer = new Region(); resizer.getStyleClass().add("task-resizer"); resizer.setPrefHeight(10);
        resizer.setOnMousePressed(e -> { dragAnchorY = e.getScreenY(); e.consume(); });
        resizer.setOnMouseDragged(e -> {
            double deltaY = e.getScreenY() - dragAnchorY;
            task.duration = Math.max(5, task.duration + (int)(deltaY / MINUTE_HEIGHT));
            taskBox.setPrefHeight(task.duration * MINUTE_HEIGHT);
            updateTimeLabel(timeLabel, task.startMin, task.duration);
            dragAnchorY = e.getScreenY(); updateRightSidebar(); e.consume();
        });
        taskBox.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragAnchorY = e.getSceneY(); dragInitialTop = AnchorPane.getTopAnchor(taskBox);
                taskBox.getStyleClass().add("task-entry-dragging"); taskBox.toFront();
            }
        });
        taskBox.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double deltaY = e.getSceneY() - dragAnchorY;
                task.startMin = Math.max(0, (int)((dragInitialTop + deltaY) / MINUTE_HEIGHT));
                AnchorPane.setTopAnchor(taskBox, task.startMin * MINUTE_HEIGHT);
                updateTimeLabel(timeLabel, task.startMin, task.duration);
            }
        });
        taskBox.setOnMouseReleased(e -> { taskBox.getStyleClass().remove("task-entry-dragging"); updateRightSidebar(); });
        taskBox.setFocusTraversable(true);
        taskBox.setOnMouseClicked(e -> {
            taskBox.requestFocus();
            if ((e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) || e.getButton() == MouseButton.SECONDARY) {
                showTaskEditDialog(task, task.startMin, task.duration, task.description);
            }
        });
        taskBox.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
                taskDatabase.get(calendarDatePicker.getValue()).remove(task);
                setupMainCalendar(); updateRightSidebar();
            }
        });
        taskBox.getChildren().addAll(tLabel, timeLabel, dLabel, spacer, resizer);
        calendarTimelineArea.getChildren().add(taskBox);
    }

    private void updateTimeLabel(Label label, int start, int duration) {
        int sh = start / 60; int sm = start % 60; int end = start + duration; int eh = end / 60; int em = end % 60;
        label.setText(String.format("%02d:%02d - %02d:%02d", sh, sm, eh, em));
    }

    private void updateRightSidebar() {
        miniMonthYearLabel.setText(currentMiniMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        miniCalendarGrid.getChildren().clear();
        String[] days = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        for (int i = 0; i < 7; i++) {
            Label h = new Label(days[i]); h.getStyleClass().addAll("calendar-day", "calendar-day-header");
            miniCalendarGrid.add(h, i, 0);
        }
        LocalDate first = currentMiniMonth.atDay(1);
        int dayOffset = first.getDayOfWeek().getValue() % 7;
        int daysInMonth = currentMiniMonth.lengthOfMonth();
        for (int i = 0; i < daysInMonth; i++) {
            LocalDate date = first.plusDays(i);
            Button dBtn = new Button(String.valueOf(i + 1)); dBtn.getStyleClass().add("calendar-day");
            if (date.equals(calendarDatePicker.getValue())) {
                dBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 50%;");
            } else if (date.equals(LocalDate.now())) {
                if (isDarkMode) {
                    dBtn.setStyle("-fx-border-color: #3b82f6; -fx-border-radius: 50%; -fx-text-fill: #cbd5e1;");
                } else {
                    dBtn.setStyle("-fx-border-color: #3b82f6; -fx-border-radius: 50%;");
                }
            }
            dBtn.setOnAction(e -> { calendarDatePicker.setValue(date); handleNavigationToCalendar(); });
            miniCalendarGrid.add(dBtn, (i + dayOffset) % 7, (i + dayOffset) / 7 + 1);
        }
        upcomingActivitiesList.getChildren().clear();
        List<Task> tasks = taskDatabase.getOrDefault(calendarDatePicker.getValue(), new ArrayList<>());
        if (tasks.isEmpty()) {
            Label noTasks = new Label("No activities for this day.");
            if (isDarkMode) { noTasks.setStyle("-fx-text-fill: #64748b; -fx-font-style: italic; -fx-padding: 10;"); }
            else { noTasks.setStyle("-fx-text-fill: #94a3b8; -fx-font-style: italic; -fx-padding: 10;"); }
            upcomingActivitiesList.getChildren().add(noTasks);
        } else {
            for (Task t : tasks) {
                VBox item = new VBox(5); item.getStyleClass().add("activity-item");
                Label time = new Label(String.format("%02d:%02d - %02d:%02d", t.startMin / 60, t.startMin % 60, (t.startMin + t.duration) / 60, (t.startMin + t.duration) % 60));
                time.getStyleClass().add("activity-time");
                Label title = new Label(t.title); title.getStyleClass().add("activity-title");
                item.getChildren().addAll(time, title); item.setOnMouseClicked(e -> handleNavigationToCalendar());
                upcomingActivitiesList.getChildren().add(item);
            }
        }
    }

    private void handleNavigationToCalendar() { contactsView.setVisible(false); calendarView.setVisible(true); genericView.setVisible(false); }

    @FXML private void handleMiniPrevMonth() { currentMiniMonth = currentMiniMonth.minusMonths(1); updateRightSidebar(); }
    @FXML private void handleMiniNextMonth() { currentMiniMonth = currentMiniMonth.plusMonths(1); updateRightSidebar(); }
    @FXML private void handleToday() { calendarDatePicker.setValue(LocalDate.now()); currentMiniMonth = YearMonth.from(LocalDate.now()); updateRightSidebar(); }
    @FXML private void handlePrevDay() { calendarDatePicker.setValue(calendarDatePicker.getValue().minusDays(1)); currentMiniMonth = YearMonth.from(calendarDatePicker.getValue()); updateRightSidebar(); }
    @FXML private void handleNextDay() { calendarDatePicker.setValue(calendarDatePicker.getValue().plusDays(1)); currentMiniMonth = YearMonth.from(calendarDatePicker.getValue()); updateRightSidebar(); }

    private void showTaskEditDialog(Task existingTask, int startMin, int duration, String desc) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle(existingTask == null ? "New Task" : "Edit Task");
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);
        if (existingTask != null) { dialog.getDialogPane().getButtonTypes().addAll(saveButton, deleteButton, ButtonType.CANCEL); }
        else { dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL); }
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new javafx.geometry.Insets(20, 100, 10, 10));
        TextField titleField = new TextField(existingTask != null ? existingTask.title : "");
        TextArea descField = new TextArea(existingTask != null ? existingTask.description : desc); descField.setPrefRowCount(3);
        TextField sH = new TextField(String.format("%02d", startMin / 60)); sH.setPrefWidth(50);
        TextField sM = new TextField(String.format("%02d", startMin % 60)); sM.setPrefWidth(50);
        TextField eH = new TextField(String.format("%02d", (startMin + duration) / 60)); eH.setPrefWidth(50);
        TextField eM = new TextField(String.format("%02d", (startMin + duration) % 60)); eM.setPrefWidth(50);
        ComboBox<String> colorPicker = new ComboBox<>(FXCollections.observableArrayList("Blue", "Red", "Green", "Yellow", "Orange", "Purple"));
        colorPicker.setValue(existingTask != null ? existingTask.color : "Blue");
        grid.add(new Label("Title:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("Start (H:M):"), 0, 1); grid.add(new HBox(5, sH, new Label(":"), sM), 1, 1);
        grid.add(new Label("End (H:M):"), 0, 2); grid.add(new HBox(5, eH, new Label(":"), eM), 1, 2);
        grid.add(new Label("Color:"), 0, 3); grid.add(colorPicker, 1, 3);
        grid.add(new Label("Description:"), 0, 4); grid.add(descField, 1, 4);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent()) {
            LocalDate date = calendarDatePicker.getValue();
            if (result.get() == deleteButton && existingTask != null) { taskDatabase.get(date).remove(existingTask); setupMainCalendar(); updateRightSidebar(); return; }
            if (result.get() == saveButton) {
                try {
                    if (existingTask != null) taskDatabase.get(date).remove(existingTask);
                    int sh = Math.max(0, Math.min(23, Integer.parseInt(sH.getText().trim())));
                    int sm = Math.max(0, Math.min(59, Integer.parseInt(sM.getText().trim())));
                    int eh = Math.max(0, Math.min(23, Integer.parseInt(eH.getText().trim())));
                    int em = Math.max(0, Math.min(59, Integer.parseInt(eM.getText().trim())));
                    int ns = sh * 60 + sm; int ne = eh * 60 + em;
                    addTaskToDatabase(date, new Task(titleField.getText(), descField.getText(), ns, Math.max(5, ne - ns), colorPicker.getValue()));
                    setupMainCalendar(); updateRightSidebar();
                } catch (Exception ex) {}
            }
        }
    }

    @FXML private void handleNavigation(ActionEvent event) {
        Button clickedBtn = (Button) event.getSource(); updateActiveStyles(clickedBtn); String btnId = clickedBtn.getId();
        if (btnId.contains("Contacts")) { contactsView.setVisible(true); calendarView.setVisible(false); genericView.setVisible(false); }
        else if (btnId.contains("Calendar")) { contactsView.setVisible(false); calendarView.setVisible(true); genericView.setVisible(false); }
        else {
            String sn = "Section"; String il = "fas-folder-open";
            if (btnId.contains("Dashboard")) { sn = "Dashboard"; il = "fas-th-large"; }
            else if (btnId.contains("Leads")) { sn = "Leads"; il = "fas-bullseye"; }
            else if (btnId.contains("Opportunities")) { sn = "Opportunities"; il = "fas-handshake"; }
            else if (btnId.contains("Accounts")) { sn = "Accounts"; il = "fas-building"; }
            else if (btnId.contains("Tasks")) { sn = "Tasks"; il = "fas-tasks"; }
            else if (btnId.contains("Settings")) { sn = "Settings"; il = "fas-cog"; }
            contactsView.setVisible(false); calendarView.setVisible(false); genericView.setVisible(true);
            genericTitle.setText(sn); genericIcon.setIconLiteral(il);
        }
    }

    private void updateActiveStyles(Button clickedBtn) {
        for (Node node : sidebarContainer.getChildren()) if (node instanceof Button) node.getStyleClass().remove("sidebar-button-active");
        for (Node node : navbarContainer.getChildren()) if (node instanceof Button) node.getStyleClass().remove("nav-link-active");
        if (clickedBtn != null) {
            if (clickedBtn.getStyleClass().contains("sidebar-button")) clickedBtn.getStyleClass().add("sidebar-button-active");
            else if (clickedBtn.getStyleClass().contains("nav-link")) clickedBtn.getStyleClass().add("nav-link-active");
        }
    }

    @FXML private void handleAddContact() { showContactDialog(null); }
    
    @FXML private void handleDeleteContact() {
        Contact selected = contactsTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle("Delete Contact");
            alert.setHeaderText("Are you sure you want to delete " + selected.nameProperty().get() + "?");
            alert.setContentText("This action cannot be undone.");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) { contactData.remove(selected); updatePagination(); }
        }
    }

    @FXML private void handleShowAllContacts() {
        rowsPerPageCombo.setValue("All");
    }

    private void showContactDialog(Contact contact) {
        Dialog<Contact> dialog = new Dialog<>(); dialog.setTitle(contact == null ? "Add New Contact" : "Edit Contact");
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        TextField name = new TextField(); TextField company = new TextField(); TextField title = new TextField();
        TextField email = new TextField(); TextField phone = new TextField();
        ComboBox<String> tags = new ComboBox<>(FXCollections.observableArrayList("Client", "Tech", "Follow-up"));
        if (contact != null) {
            name.setText(contact.nameProperty().get()); company.setText(contact.companyProperty().get());
            title.setText(contact.titleProperty().get()); email.setText(contact.emailProperty().get());
            phone.setText(contact.phoneProperty().get()); tags.setValue(contact.tagsProperty().get());
        }
        grid.add(new Label("Name:"), 0, 0); grid.add(name, 1, 0);
        grid.add(new Label("Company:"), 0, 1); grid.add(company, 1, 1);
        grid.add(new Label("Title:"), 0, 2); grid.add(title, 1, 2);
        grid.add(new Label("Email:"), 0, 3); grid.add(email, 1, 3);
        grid.add(new Label("Phone:"), 0, 4); grid.add(phone, 1, 4);
        grid.add(new Label("Tags:"), 0, 5); grid.add(tags, 1, 5);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(db -> {
            if (db == saveButtonType) {
                if (contact == null) return new Contact(name.getText(), company.getText(), title.getText(), email.getText(), phone.getText(), "Just now", tags.getValue());
                else {
                    contact.setName(name.getText()); contact.setCompany(company.getText()); contact.setTitle(title.getText());
                    contact.setEmail(email.getText()); contact.setPhone(phone.getText()); contact.setTags(tags.getValue());
                    return contact;
                }
            }
            return null;
        });
        Optional<Contact> result = dialog.showAndWait();
        result.ifPresent(c -> { if (contact == null) { contactData.add(0, c); } updatePagination(); contactsTable.refresh(); });
    }

    private void filterContacts(String searchText) {
        if (searchText == null || searchText.isEmpty()) { filteredData.setPredicate(p -> true); }
        else {
            String lcf = searchText.toLowerCase();
            filteredData.setPredicate(c -> 
                c.nameProperty().get().toLowerCase().contains(lcf) ||
                c.companyProperty().get().toLowerCase().contains(lcf) ||
                c.emailProperty().get().toLowerCase().contains(lcf)
            );
        }
    }

    @FXML
    private void handleThemeToggle() { isDarkMode = !isDarkMode; applyTheme(); updateThemeButton(); updateRightSidebar(); }

    private void applyTheme() {
        try {
            Stage stage = (Stage) themeToggleBtn.getScene().getWindow();
            Scene scene = stage.getScene();
            scene.getStylesheets().clear();
            String cssResource = isDarkMode ? "/css/style-dark.css" : "/css/style.css";
            scene.getStylesheets().add(getClass().getResource(cssResource).toExternalForm());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateThemeButton() {
        if (isDarkMode) { themeToggleIcon.setIconLiteral("fas-sun"); }
        else { themeToggleIcon.setIconLiteral("fas-moon"); }
    }
}
