package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.Task;
import com.crm.model.UserAccount;
import com.crm.model.CrmDataSnapshot;
import com.crm.repository.LocalUserRepository;
import com.crm.repository.CrmDataRepository;
import com.crm.repository.LocalCrmDataRepository;
import com.crm.service.AvatarService;
import com.crm.service.AuthService;
import javafx.geometry.Side;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

public class MainController {

    private static final long MAX_AVATAR_SIZE_BYTES = 20L * 1024 * 1024;

    @FXML private TableView<Contact> contactsTable;
    @FXML private TableColumn<Contact, String> nameColumn;
    @FXML private TableColumn<Contact, String> companyColumn;
    @FXML private TableColumn<Contact, String> titleColumn;
    @FXML private TableColumn<Contact, String> emailColumn;
    @FXML private TableColumn<Contact, String> phoneColumn;
    @FXML private TableColumn<Contact, String> lastInteractionColumn;
    @FXML private TableColumn<Contact, String> tagsColumn;
    @FXML private TableColumn<Contact, Boolean> selectColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> rowsPerPageCombo;
    @FXML private Pagination contactsPagination;
    @FXML private Label paginationInfoLabel;
    @FXML private Button selectContactsBtn;

    @FXML private VBox contactsView;
    @FXML private VBox calendarView;
    @FXML private VBox genericView;
    @FXML private Label genericTitle;
    @FXML private FontIcon genericIcon;
    @FXML private VBox sidebarContainer;
    @FXML private Button themeToggleBtn;
    @FXML private FontIcon themeToggleIcon;
    @FXML private Label currentUserLabel;
    @FXML private Button accountMenuButton;
    @FXML private FontIcon defaultAvatarIcon;
    @FXML private ImageView avatarImage;
    
    @FXML private AnchorPane timeLabelsContainer;
    @FXML private AnchorPane calendarTimelineArea;
    @FXML private ScrollPane calendarScrollPane;
    @FXML private DatePicker calendarDatePicker;
    @FXML private ComboBox<String> viewModeCombo;

    // Right Sidebar elements
    @FXML private Label miniMonthYearLabel;
    @FXML private GridPane miniCalendarGrid;
    @FXML private VBox upcomingActivitiesList;

    private ObservableList<Contact> contactData = FXCollections.observableArrayList();
    private FilteredList<Contact> filteredData;

    private static final double MINUTE_HEIGHT = 1.0;
    private static final double HOUR_HEIGHT = 60.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double MIN_ZOOM = 0.75;
    private static final double MAX_ZOOM = 3.0;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double TIME_COLUMN_WIDTH = 65.0;
    private static final double MIN_TIMELINE_WIDTH = 320.0;

    private double dragAnchorY;
    private double dragInitialTop;
    private double currentZoom = DEFAULT_ZOOM;
    
    private boolean isDarkMode = true;
    private String currentViewMode = "Day"; 
    private LocalDate weekStartDate; 
    
    private Map<LocalDate, List<Task>> taskDatabase = new HashMap<>();
    private YearMonth currentMiniMonth;
    private Runnable logoutAction;
    private UserAccount currentUser;
    private final AuthService authService = new AuthService(new LocalUserRepository());
    private final AvatarService avatarService = new AvatarService(new LocalUserRepository());
    private final CrmDataRepository crmDataRepository = new LocalCrmDataRepository();
    private boolean loadingUserData;
    private boolean contactSelectionMode;
    private final Set<Contact> checkedContacts = new HashSet<>();
    private final Set<Contact> cutContacts = new HashSet<>();
    private static final PseudoClass CUT_CONTACT = PseudoClass.getPseudoClass("cut-contact");

    public void setCurrentUser(UserAccount user, Runnable logoutAction) {
        this.currentUser = user;
        this.logoutAction = logoutAction;
        if (currentUserLabel != null) currentUserLabel.setText(user.getFullName());
        refreshAvatar();
        loadUserData();
    }

    /** Lets the user type in contact search immediately after the application opens. */
    public void requestInitialFocus() {
        javafx.application.Platform.runLater(searchField::requestFocus);
    }

    @FXML private void handleLogout() {
        if (logoutAction != null) logoutAction.run();
    }

    @FXML private void handleAccountMenu() {
        if (currentUser == null) return;
        MenuItem profile = new MenuItem("Profilo e dati account");
        profile.setOnAction(e -> showProfileDialog());
        MenuItem security = new MenuItem("Sicurezza: cambia password");
        security.setOnAction(e -> showChangePasswordDialog());
        MenuItem avatar = new MenuItem("Aggiorna icona profilo");
        avatar.setOnAction(e -> chooseAvatar());
        MenuItem logout = new MenuItem("Esci dall'account");
        logout.setOnAction(e -> handleLogout());
        ContextMenu menu = new ContextMenu(profile, security, avatar, new SeparatorMenuItem(), logout);
        menu.show(accountMenuButton, Side.BOTTOM, 0, 6);
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Scegli un'immagine profilo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Immagini PNG e JPG", "*.png", "*.jpg", "*.jpeg"));
        java.io.File selected = chooser.showOpenDialog(accountMenuButton.getScene().getWindow());
        if (selected == null) return;
        if (selected.length() > MAX_AVATAR_SIZE_BYTES) {
            showError("Image too large", "Profile image must not exceed 20 MB.");
            return;
        }
        Image image = new Image(selected.toURI().toString());
        if (image.isError()) { showError("Immagine non valida", "Non è stato possibile aprire l'immagine selezionata."); return; }
        showAvatarCropDialog(image);
    }

    private void showAvatarCropDialog(Image image) {
        final double previewSize = 380;
        final double cropSize = 250;
        final double outputSize = 400;
        Canvas canvas = new Canvas(previewSize, previewSize);

        class CropRenderer {
            double zoom = 1.0, offsetX, offsetY, dragX, dragY;
            double baseScale() { return Math.max(cropSize / image.getWidth(), cropSize / image.getHeight()); }
            void clampOffset() {
                double width = image.getWidth() * baseScale() * zoom, height = image.getHeight() * baseScale() * zoom;
                double maxX = Math.max(0, (width - cropSize) / 2), maxY = Math.max(0, (height - cropSize) / 2);
                offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
                offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
            }
            void drawCrop(GraphicsContext gc, double size, double diameter) {
                double scale = Math.max(diameter / image.getWidth(), diameter / image.getHeight()) * zoom;
                double x = (size - image.getWidth() * scale) / 2 + offsetX * (diameter / cropSize);
                double y = (size - image.getHeight() * scale) / 2 + offsetY * (diameter / cropSize);
                gc.drawImage(image, x, y, image.getWidth() * scale, image.getHeight() * scale);
            }
            void draw() {
                GraphicsContext gc = canvas.getGraphicsContext2D(); gc.clearRect(0, 0, previewSize, previewSize);
                gc.setFill(Color.web("#0f172a")); gc.fillRect(0, 0, previewSize, previewSize);
                double backgroundScale = Math.min(previewSize / image.getWidth(), previewSize / image.getHeight());
                double bgW = image.getWidth() * backgroundScale, bgH = image.getHeight() * backgroundScale;
                gc.setGlobalAlpha(0.24); gc.drawImage(image, (previewSize - bgW) / 2, (previewSize - bgH) / 2, bgW, bgH); gc.setGlobalAlpha(1);
                gc.setFill(Color.rgb(15, 23, 42, 0.55)); gc.fillRect(0, 0, previewSize, previewSize);
                gc.save(); gc.beginPath(); gc.arc(previewSize / 2, previewSize / 2, cropSize / 2, cropSize / 2, 0, 360); gc.closePath(); gc.clip();
                drawCrop(gc, previewSize, cropSize); gc.restore();
                gc.setStroke(Color.web("#60a5fa")); gc.setLineWidth(3); gc.strokeOval((previewSize - cropSize) / 2, (previewSize - cropSize) / 2, cropSize, cropSize);
            }
            WritableImage createResult() {
                Canvas result = new Canvas(outputSize, outputSize); GraphicsContext gc = result.getGraphicsContext2D();
                gc.save(); gc.beginPath(); gc.arc(outputSize / 2, outputSize / 2, outputSize / 2, outputSize / 2, 0, 360); gc.closePath(); gc.clip();
                drawCrop(gc, outputSize, outputSize); gc.restore();
                return result.snapshot(null, null);
            }
        }
        CropRenderer renderer = new CropRenderer(); renderer.draw();
        canvas.setOnMousePressed(event -> { renderer.dragX = event.getX(); renderer.dragY = event.getY(); });
        canvas.setOnMouseDragged(event -> { renderer.offsetX += event.getX() - renderer.dragX; renderer.offsetY += event.getY() - renderer.dragY; renderer.dragX = event.getX(); renderer.dragY = event.getY(); renderer.clampOffset(); renderer.draw(); });
        Slider zoom = new Slider(1, 3, 1); zoom.setShowTickLabels(true); zoom.setMajorTickUnit(1); zoom.valueProperty().addListener((obs, old, value) -> { renderer.zoom = value.doubleValue(); renderer.clampOffset(); renderer.draw(); });

        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Ritaglia immagine profilo"); applyThemeToDialog(dialog);
        ButtonType save = new ButtonType("Usa questa immagine", ButtonBar.ButtonData.OK_DONE); dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        Label help = new Label("Trascina l'immagine per scegliere l'inquadratura. Usa il cursore per ingrandirla."); help.setWrapText(true); help.getStyleClass().add("auth-subtitle");
        VBox content = new VBox(14, help, canvas, new Label("Zoom"), zoom); content.setAlignment(javafx.geometry.Pos.CENTER); content.setPrefWidth(previewSize); dialog.getDialogPane().setContent(content);
        if (dialog.showAndWait().filter(result -> result == save).isPresent()) {
            try { avatarService.updateAvatar(currentUser, renderer.createResult()); refreshAvatar(); }
            catch (IllegalStateException ex) { showError("Immagine non aggiornata", ex.getMessage()); }
        }
    }

    private void refreshAvatar() {
        if (currentUser == null || avatarImage == null) return;
        java.nio.file.Path path = avatarService.getAvatarPath(currentUser);
        boolean available = path != null && java.nio.file.Files.isRegularFile(path);
        if (available) {
            avatarImage.setImage(new Image(path.toUri().toString(), 27, 27, true, true));
            avatarImage.setClip(new Circle(13.5, 13.5, 13.5));
        }
        avatarImage.setVisible(available);
        defaultAvatarIcon.setVisible(!available);
    }

    private void showProfileDialog() {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Profilo e account");
        applyThemeToDialog(dialog);
        ButtonType save = new ButtonType("Salva", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(12);
        TextField name = new TextField(currentUser.getFullName());
        TextField email = new TextField(currentUser.getEmail()); email.setEditable(false);
        grid.addRow(0, new Label("Nome:"), name);
        grid.addRow(1, new Label("Email:"), email);
        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().filter(result -> result == save).isPresent()) {
            try { authService.updateProfile(currentUser, name.getText()); currentUserLabel.setText(currentUser.getFullName()); }
            catch (IllegalArgumentException ex) { showError("Profilo non aggiornato", ex.getMessage()); }
        }
    }

    private void showChangePasswordDialog() {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Cambia password");
        applyThemeToDialog(dialog);
        ButtonType save = new ButtonType("Aggiorna password", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(12);
        PasswordField current = new PasswordField(); current.setPromptText("Password attuale");
        PasswordField next = new PasswordField(); next.setPromptText("Minimo 8 caratteri");
        PasswordField confirm = new PasswordField(); confirm.setPromptText("Ripeti la nuova password");
        grid.addRow(0, new Label("Password attuale:"), current);
        grid.addRow(1, new Label("Nuova password:"), next);
        grid.addRow(2, new Label("Conferma password:"), confirm);
        dialog.getDialogPane().setContent(grid);
        if (dialog.showAndWait().filter(result -> result == save).isPresent()) {
            try {
                if (!next.getText().equals(confirm.getText())) throw new IllegalArgumentException("Le password non coincidono.");
                authService.changePassword(currentUser, current.getText(), next.getText());
                showInfo("Password aggiornata", "La password del tuo account è stata aggiornata.");
            } catch (IllegalArgumentException ex) { showError("Password non aggiornata", ex.getMessage()); }
        }
    }

    @FXML
    public void initialize() {
        // A hidden view must not take part in StackPane layout calculations.
        contactsView.managedProperty().bind(contactsView.visibleProperty());
        calendarView.managedProperty().bind(calendarView.visibleProperty());
        genericView.managedProperty().bind(genericView.visibleProperty());

        filteredData = new FilteredList<>(contactData, p -> true);
        searchField.textProperty().addListener((obs, old, newValue) -> {
            filterContacts(newValue);
            updatePagination();
        });
        
        setupColumns();
        setupContactRowFactory();
        setupPagination();
        updatePagination();
        
        LocalDate today = LocalDate.now();
        calendarDatePicker.setValue(today);
        currentMiniMonth = YearMonth.from(today);
        weekStartDate = getWeekStart(today);
        
        calendarDatePicker.valueProperty().addListener((obs, old, newValue) -> {
            if (newValue != null) {
                refreshCalendarForSelectedDate(newValue);
            }
        });

        setupViewModeCombo();
        setupMainCalendar();
        updateRightSidebar();
        updateThemeButton();
        
        contactsTable.setOnKeyPressed(event -> {
            if (isShortcut(event, KeyCode.C)) {
                copySelectedContacts(false); event.consume();
            } else if (isShortcut(event, KeyCode.X)) {
                copySelectedContacts(true); event.consume();
            } else if (event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE) {
                handleDeleteContact();
            }
        });
        
        setupCalendarZoomControls();
    }

    private void setupCalendarZoomControls() {
        calendarScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        calendarScrollPane.setFitToWidth(true);
        calendarScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (Math.abs(oldBounds.getWidth() - newBounds.getWidth()) > 1) {
                setupMainCalendar();
                calendarScrollPane.setHvalue(0);
            }
        });

        calendarTimelineArea.setOnScroll(event -> {
            if (event.isControlDown() || event.isMetaDown()) {
                event.consume();
                double scrollDelta = event.getDeltaY();
                if (scrollDelta > 0) {
                    handleZoom(true, event.getY());
                } else if (scrollDelta < 0) {
                    handleZoom(false, event.getY());
                }
            }
        });
        
        calendarTimelineArea.setOnKeyPressed(event -> {
            if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.DIGIT0) {
                event.consume();
                handleResetZoom();
            }
        });
        
        calendarTimelineArea.setFocusTraversable(true);
        
        calendarView.visibleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                calendarTimelineArea.requestFocus();
                calendarScrollPane.setHvalue(0);
            }
        });
    }
    
    private void handleZoom(boolean zoomIn, double pivotContentY) {
        double targetZoom = zoomIn
                ? Math.min(currentZoom + ZOOM_STEP, MAX_ZOOM)
                : Math.max(currentZoom - ZOOM_STEP, MIN_ZOOM);
        applyCalendarZoom(targetZoom, pivotContentY);
    }

    private void applyCalendarZoom(double targetZoom, double pivotContentY) {
        double oldZoom = currentZoom;
        if (oldZoom == targetZoom) return;

        double contentHeight = getCalendarContentHeight();
        double viewportHeight = calendarScrollPane.getViewportBounds().getHeight();
        double oldScrollY = getScrollY(contentHeight, viewportHeight);
        double pivotViewportY = clamp(pivotContentY - oldScrollY, 0, Math.max(0, viewportHeight));
        double stablePivotContentY = oldScrollY + pivotViewportY;
        double scaleFactor = targetZoom / oldZoom;

        currentZoom = targetZoom;
        setupMainCalendar();
        calendarScrollPane.setHvalue(0);
        saveCurrentData();

        javafx.application.Platform.runLater(() -> {
            double newContentHeight = getCalendarContentHeight();
            double newScrollableHeight = Math.max(0, newContentHeight - viewportHeight);
            if (newScrollableHeight > 0) {
                double newScrollY = (stablePivotContentY * scaleFactor) - pivotViewportY;
                calendarScrollPane.setVvalue(clamp(newScrollY / newScrollableHeight, 0, 1));
            } else {
                calendarScrollPane.setVvalue(0);
            }
            calendarScrollPane.setHvalue(0);
        });
    }

    private double getViewportCenterContentY() {
        double contentHeight = getCalendarContentHeight();
        double viewportHeight = calendarScrollPane.getViewportBounds().getHeight();
        return getScrollY(contentHeight, viewportHeight) + (viewportHeight / 2);
    }

    private double getScrollY(double contentHeight, double viewportHeight) {
        return calendarScrollPane.getVvalue() * Math.max(0, contentHeight - viewportHeight);
    }

    private double getCalendarContentHeight() {
        return Math.max(calendarTimelineArea.getHeight(), calendarTimelineArea.getPrefHeight());
    }

    private double getTimelineWidth() {
        double viewportWidth = calendarScrollPane.getViewportBounds().getWidth();
        if (viewportWidth <= 0) viewportWidth = calendarScrollPane.getWidth();
        if (viewportWidth <= 0) return 1000;
        return Math.max(MIN_TIMELINE_WIDTH, viewportWidth - TIME_COLUMN_WIDTH);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void handleResetZoom() {
        applyCalendarZoom(DEFAULT_ZOOM, getViewportCenterContentY());
        calendarTimelineArea.requestFocus();
    }
    
    private void setupViewModeCombo() {
        viewModeCombo.setItems(FXCollections.observableArrayList("Day", "Week"));
        viewModeCombo.setValue("Day");
        viewModeCombo.valueProperty().addListener((obs, old, newValue) -> {
            currentViewMode = newValue;
            if (newValue.equals("Week")) {
                weekStartDate = getWeekStart(calendarDatePicker.getValue());
            }
            setupMainCalendar();
            saveCurrentData();
        });
    }

    private LocalDate getWeekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() % 7);
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

    private void loadUserData() {
        loadingUserData = true;
        try {
            CrmDataSnapshot data = crmDataRepository.loadForUser(currentUser.getId());
            contactData.setAll(data.contacts());
            checkedContacts.clear();
            cutContacts.clear();
            taskDatabase.clear();
            data.tasksByDate().forEach((date, tasks) -> taskDatabase.put(date, new ArrayList<>(tasks)));
            currentZoom = clamp(data.calendarZoom(), MIN_ZOOM, MAX_ZOOM);
            currentViewMode = "Week".equals(data.calendarViewMode()) ? "Week" : "Day";
            viewModeCombo.setValue(currentViewMode);
            calendarDatePicker.setValue(data.selectedDate());
            weekStartDate = getWeekStart(data.selectedDate());
            currentMiniMonth = YearMonth.from(data.selectedDate());
            updatePagination();
            setupMainCalendar();
            updateRightSidebar();
        } finally {
            loadingUserData = false;
        }
    }

    private void saveCurrentData() {
        if (currentUser == null || loadingUserData || calendarDatePicker.getValue() == null) return;
        Map<LocalDate, List<Task>> tasksCopy = new HashMap<>();
        taskDatabase.forEach((date, tasks) -> tasksCopy.put(date, new ArrayList<>(tasks)));
        crmDataRepository.saveForUser(currentUser.getId(), new CrmDataSnapshot(
                new ArrayList<>(contactData), tasksCopy, calendarDatePicker.getValue(), currentViewMode, currentZoom));
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
                    if (checkBox.isSelected()) checkedContacts.add(contact); else checkedContacts.remove(contact);
                    contactsTable.refresh();
                });
            }
            @Override protected void updateItem(Boolean selected, boolean empty) {
                super.updateItem(selected, empty);
                if (empty || !contactSelectionMode) setGraphic(null);
                else { checkBox.setSelected(Boolean.TRUE.equals(selected)); setGraphic(checkBox); }
            }
        });
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
            TableRow<Contact> row = new TableRow<>() {
                @Override protected void updateItem(Contact item, boolean empty) {
                    super.updateItem(item, empty);
                    pseudoClassStateChanged(CUT_CONTACT, !empty && cutContacts.contains(item));
                }
            };
            row.setOnMouseClicked(event -> { if (!contactSelectionMode && event.getClickCount() == 2 && (!row.isEmpty())) showContactDialog(row.getItem()); });
            return row;
        });
    }

    private void setupMainCalendar() {
        calendarTimelineArea.getChildren().clear();
        timeLabelsContainer.getChildren().clear();
        
        double zoomedHourHeight = HOUR_HEIGHT * currentZoom;
        double zoomedMinuteHeight = MINUTE_HEIGHT * currentZoom;
        double timelineWidth = getTimelineWidth();
        
        timeLabelsContainer.setPrefHeight(24 * zoomedHourHeight + 20);
        calendarTimelineArea.setPrefHeight(24 * zoomedHourHeight + 20);
        calendarTimelineArea.setMinWidth(timelineWidth);
        calendarTimelineArea.setPrefWidth(timelineWidth);
        calendarTimelineArea.setMaxWidth(timelineWidth);

        // Common Time Grid
        for (int i = 0; i <= 24; i++) {
            double hourY = i * zoomedHourHeight;
            Label hourLabel = new Label(String.format("%02d:00", i));
            hourLabel.getStyleClass().add("hour-label");
            AnchorPane.setRightAnchor(hourLabel, 10.0);
            AnchorPane.setTopAnchor(hourLabel, hourY - 10);
            timeLabelsContainer.getChildren().add(hourLabel);

            Line line = new Line(0, hourY, timelineWidth, hourY);
            line.getStyleClass().add("timeline-line-hour");
            calendarTimelineArea.getChildren().add(line);

            if (i < 24) {
                int subdivisions = currentZoom > 1.5 ? 60 : 4;
                int interval = 60 / subdivisions;
                for (int s = 1; s < subdivisions; s++) {
                    double sy = hourY + (s * interval * zoomedMinuteHeight);
                    if (s * interval % 15 == 0 || currentZoom > 1.5 && s % 5 == 0) {
                        Label subLabel = new Label(String.format("%02d:%02d", i, s * interval));
                        subLabel.getStyleClass().add("sub-hour-label");
                        AnchorPane.setRightAnchor(subLabel, 10.0);
                        AnchorPane.setTopAnchor(subLabel, sy - 7);
                        timeLabelsContainer.getChildren().add(subLabel);
                    }
                    Line sLine = new Line(0, sy, timelineWidth, sy);
                    sLine.getStyleClass().add("timeline-line");
                    if (currentZoom > 1.5 && s % 5 != 0) sLine.setOpacity(0.3);
                    calendarTimelineArea.getChildren().add(sLine);
                }
            }
        }

        if (currentViewMode.equals("Day")) {
            renderDayView(zoomedMinuteHeight);
        } else {
            renderWeekView(timelineWidth, zoomedHourHeight, zoomedMinuteHeight);
        }
    }

    private void renderDayView(double zoomedMinuteHeight) {
        calendarTimelineArea.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getTarget() == calendarTimelineArea && e.getButton() == MouseButton.PRIMARY) {
                showTaskEditDialog(null, (int)(e.getY() / zoomedMinuteHeight), 60, "");
            }
        });
        List<Task> tasks = taskDatabase.getOrDefault(calendarDatePicker.getValue(), new ArrayList<>());
        for (Task t : tasks) renderTaskOnTimeline(t, zoomedMinuteHeight, 1.0, 0, 10.0);
    }

    private void renderWeekView(double timelineWidth, double zoomedHourHeight, double zoomedMinuteHeight) {
        double dayWidth = timelineWidth / 7.0;
        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStartDate.plusDays(d);
            Label dayHeader = new Label(date.format(DateTimeFormatter.ofPattern("EEE dd/MM")));
            dayHeader.getStyleClass().add("week-day-header");
            dayHeader.setPrefWidth(dayWidth);
            dayHeader.setLayoutX(d * dayWidth);
            dayHeader.setLayoutY(0);
            calendarTimelineArea.getChildren().add(dayHeader);

            if (d > 0) {
                Line vLine = new Line(d * dayWidth, 0, d * dayWidth, 24 * zoomedHourHeight);
                vLine.getStyleClass().add("week-day-column");
                calendarTimelineArea.getChildren().add(vLine);
            }

            final int dayOffset = d;
            List<Task> tasks = taskDatabase.getOrDefault(date, new ArrayList<>());
            for (Task t : tasks) renderTaskOnTimeline(t, zoomedMinuteHeight, 1.0/7.0, dayOffset, 5.0);
        }
        
        calendarTimelineArea.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && e.getTarget() == calendarTimelineArea && e.getButton() == MouseButton.PRIMARY) {
                int dayOffset = (int)(e.getX() / dayWidth);
                if (dayOffset >= 0 && dayOffset < 7) {
                    calendarDatePicker.setValue(weekStartDate.plusDays(dayOffset));
                    showTaskEditDialog(null, (int)(e.getY() / zoomedMinuteHeight), 60, "");
                }
            }
        });
    }

    private void renderTaskOnTimeline(Task task, double zoomedMinuteHeight, double widthPercent, int dayOffset, double margin) {
        VBox taskBox = new VBox();
        taskBox.getStyleClass().addAll("task-entry", "task-" + task.getColor().toLowerCase());
        
        double timelineWidth = getTimelineWidth();
        double boxWidth = (timelineWidth * widthPercent) - (margin * 2);
        double leftPos = (dayOffset * (timelineWidth * widthPercent)) + margin;
        
        AnchorPane.setLeftAnchor(taskBox, leftPos);
        taskBox.setPrefWidth(boxWidth);
        taskBox.setPrefHeight(task.getDuration() * zoomedMinuteHeight);
        AnchorPane.setTopAnchor(taskBox, task.getStartMin() * zoomedMinuteHeight);
        
        Label tLabel = new Label(task.getTitle()); tLabel.getStyleClass().add("task-title");
        Label timeLabel = new Label(); timeLabel.getStyleClass().add("task-time");
        updateTimeLabel(timeLabel, task.getStartMin(), task.getDuration());
        Label dLabel = new Label(task.getDescription()); dLabel.getStyleClass().add("task-desc");
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        Region resizer = new Region(); resizer.getStyleClass().add("task-resizer"); resizer.setPrefHeight(10);
        
        resizer.setOnMousePressed(e -> { dragAnchorY = e.getScreenY(); e.consume(); });
        resizer.setOnMouseDragged(e -> {
            double deltaY = e.getScreenY() - dragAnchorY;
            task.setDuration(Math.max(5, task.getDuration() + (int)(deltaY / zoomedMinuteHeight)));
            taskBox.setPrefHeight(task.getDuration() * zoomedMinuteHeight);
            updateTimeLabel(timeLabel, task.getStartMin(), task.getDuration());
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
                task.setStartMin(Math.max(0, (int)((dragInitialTop + deltaY) / zoomedMinuteHeight)));
                AnchorPane.setTopAnchor(taskBox, task.getStartMin() * zoomedMinuteHeight);
                updateTimeLabel(timeLabel, task.getStartMin(), task.getDuration());
            }
        });
        taskBox.setOnMouseReleased(e -> { taskBox.getStyleClass().remove("task-entry-dragging"); updateRightSidebar(); saveCurrentData(); });
        taskBox.setFocusTraversable(true);
        taskBox.setOnMouseClicked(e -> {
            taskBox.requestFocus();
            if ((e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) || e.getButton() == MouseButton.SECONDARY) {
                if (currentViewMode.equals("Week")) calendarDatePicker.setValue(weekStartDate.plusDays(dayOffset));
                showTaskEditDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
            }
        });
        taskBox.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE) {
                LocalDate date = currentViewMode.equals("Day") ? calendarDatePicker.getValue() : weekStartDate.plusDays(dayOffset);
                taskDatabase.getOrDefault(date, new ArrayList<>()).remove(task);
                setupMainCalendar(); updateRightSidebar(); saveCurrentData();
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
                dBtn.setStyle("-fx-border-color: #3b82f6; -fx-border-radius: 50%;" + (isDarkMode ? " -fx-text-fill: #cbd5e1;" : ""));
            }
            dBtn.setOnAction(e -> { calendarDatePicker.setValue(date); handleNavigationToCalendar(); });
            miniCalendarGrid.add(dBtn, (i + dayOffset) % 7, (i + dayOffset) / 7 + 1);
        }
        upcomingActivitiesList.getChildren().clear();
        List<Task> tasks = taskDatabase.getOrDefault(calendarDatePicker.getValue(), new ArrayList<>());
        if (tasks.isEmpty()) {
            Label noTasks = new Label("No activities for this day.");
            noTasks.setStyle("-fx-font-style: italic; -fx-padding: 10; -fx-text-fill: " + (isDarkMode ? "#64748b;" : "#94a3b8;"));
            upcomingActivitiesList.getChildren().add(noTasks);
        } else {
            for (Task t : tasks) {
                VBox item = new VBox(5); item.getStyleClass().add("activity-item");
                Label time = new Label(String.format("%02d:%02d - %02d:%02d", t.getStartMin() / 60, t.getStartMin() % 60, (t.getStartMin() + t.getDuration()) / 60, (t.getStartMin() + t.getDuration()) % 60));
                time.getStyleClass().add("activity-time");
                Label title = new Label(t.getTitle()); title.getStyleClass().add("activity-title");
                item.getChildren().addAll(time, title); item.setOnMouseClicked(e -> handleNavigationToCalendar());
                upcomingActivitiesList.getChildren().add(item);
            }
        }
    }

    private void handleNavigationToCalendar() { 
        contactsView.setVisible(false); calendarView.setVisible(true); genericView.setVisible(false); 
        updateActiveStyles(null); // Optional: reset active styles if needed
    }

    @FXML private void handleMiniPrevMonth() { currentMiniMonth = currentMiniMonth.minusMonths(1); updateRightSidebar(); }
    @FXML private void handleMiniNextMonth() { currentMiniMonth = currentMiniMonth.plusMonths(1); updateRightSidebar(); }
    @FXML private void handleToday() { selectCalendarDate(LocalDate.now()); }
    
    @FXML private void handlePrevDay() {
        LocalDate targetDate = currentViewMode.equals("Day")
                ? calendarDatePicker.getValue().minusDays(1)
                : weekStartDate.minusWeeks(1);
        selectCalendarDate(targetDate);
    }
    
    @FXML private void handleNextDay() {
        LocalDate targetDate = currentViewMode.equals("Day")
                ? calendarDatePicker.getValue().plusDays(1)
                : weekStartDate.plusWeeks(1);
        selectCalendarDate(targetDate);
    }

    private void selectCalendarDate(LocalDate date) {
        if (date.equals(calendarDatePicker.getValue())) refreshCalendarForSelectedDate(date);
        else calendarDatePicker.setValue(date);
    }

    private void refreshCalendarForSelectedDate(LocalDate date) {
        currentMiniMonth = YearMonth.from(date);
        if (currentViewMode.equals("Week")) weekStartDate = getWeekStart(date);
        setupMainCalendar();
        updateRightSidebar();
        saveCurrentData();
    }

    private void showTaskEditDialog(Task existingTask, int startMin, int duration, String desc) {
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle(existingTask == null ? "New Task" : "Edit Task");
        applyThemeToDialog(dialog);
        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);
        if (existingTask != null) dialog.getDialogPane().getButtonTypes().add(1, deleteButton);

        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new javafx.geometry.Insets(20, 100, 10, 10));
        TextField titleField = new TextField(existingTask != null ? existingTask.getTitle() : "");
        TextArea descField = new TextArea(existingTask != null ? existingTask.getDescription() : desc); descField.setPrefRowCount(3);
        TextField sH = new TextField(String.format("%02d", startMin / 60)); sH.setPrefWidth(50);
        TextField sM = new TextField(String.format("%02d", startMin % 60)); sM.setPrefWidth(50);
        TextField eH = new TextField(String.format("%02d", (startMin + duration) / 60)); eH.setPrefWidth(50);
        TextField eM = new TextField(String.format("%02d", (startMin + duration) % 60)); eM.setPrefWidth(50);
        ComboBox<String> colorPicker = new ComboBox<>(FXCollections.observableArrayList("Blue", "Red", "Green", "Yellow", "Orange", "Purple"));
        colorPicker.setValue(existingTask != null ? existingTask.getColor() : "Blue");

        grid.add(new Label("Title:"), 0, 0); grid.add(titleField, 1, 0);
        grid.add(new Label("Start (H:M):"), 0, 1); grid.add(new HBox(5, sH, new Label(":"), sM), 1, 1);
        grid.add(new Label("End (H:M):"), 0, 2); grid.add(new HBox(5, eH, new Label(":"), eM), 1, 2);
        grid.add(new Label("Color:"), 0, 3); grid.add(colorPicker, 1, 3);
        grid.add(new Label("Description:"), 0, 4); grid.add(descField, 1, 4);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent()) {
            LocalDate date = calendarDatePicker.getValue();
            if (result.get() == deleteButton && existingTask != null) {
                taskDatabase.get(date).remove(existingTask);
            } else if (result.get() == saveButton) {
                try {
                    int sh = Integer.parseInt(sH.getText().trim());
                    int sm = Integer.parseInt(sM.getText().trim());
                    int eh = Integer.parseInt(eH.getText().trim());
                    int em = Integer.parseInt(eM.getText().trim());
                    if (sh < 0 || sh > 23 || sm < 0 || sm > 59 || eh < 0 || eh > 23 || em < 0 || em > 59) throw new NumberFormatException();
                    
                    if (existingTask != null) taskDatabase.get(date).remove(existingTask);
                    int ns = sh * 60 + sm; int ne = eh * 60 + em;
                    addTaskToDatabase(date, new Task(titleField.getText(), descField.getText(), ns, Math.max(5, ne - ns), colorPicker.getValue()));
                } catch (NumberFormatException ex) {
                    showError("Invalid Time", "Please enter valid hours (0-23) and minutes (0-59).");
                    return;
                }
            }
            setupMainCalendar(); updateRightSidebar();
            saveCurrentData();
        }
    }

    private void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        applyThemeToDialog(alert);
        Node contentLabel = alert.getDialogPane().lookup(".content.label");
        if (contentLabel != null) contentLabel.setStyle("-fx-text-fill: #fca5a5;");
        alert.showAndWait();
    }

    private void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        applyThemeToDialog(alert);
        alert.showAndWait();
    }

    @FXML private void handleNavigation(ActionEvent event) {
        Button clickedBtn = (Button) event.getSource();
        updateActiveStyles(clickedBtn);
        String btnId = clickedBtn.getId();
        
        contactsView.setVisible(false); calendarView.setVisible(false); genericView.setVisible(false);
        
        if (btnId.contains("Contacts")) contactsView.setVisible(true);
        else if (btnId.contains("Calendar")) calendarView.setVisible(true);
        else {
            genericView.setVisible(true);
            if (btnId.contains("Home")) { genericTitle.setText("Home"); genericIcon.setIconLiteral("fas-home"); }
            else if (btnId.contains("Dashboard")) { genericTitle.setText("Dashboard"); genericIcon.setIconLiteral("fas-th-large"); }
            else if (btnId.contains("Leads")) { genericTitle.setText("Leads"); genericIcon.setIconLiteral("fas-bullseye"); }
            else if (btnId.contains("Deals") || btnId.contains("Opportunities")) { genericTitle.setText("Opportunities"); genericIcon.setIconLiteral("fas-handshake"); }
            else if (btnId.contains("Accounts")) { genericTitle.setText("Accounts"); genericIcon.setIconLiteral("fas-building"); }
            else if (btnId.contains("Tasks")) { genericTitle.setText("Tasks"); genericIcon.setIconLiteral("fas-tasks"); }
            else if (btnId.contains("Settings")) { genericTitle.setText("Settings"); genericIcon.setIconLiteral("fas-cog"); }
        }
    }

    private void updateActiveStyles(Button clickedBtn) {
        for (Node node : sidebarContainer.getChildren()) if (node instanceof Button) node.getStyleClass().remove("sidebar-button-active");
        if (clickedBtn != null && clickedBtn.getStyleClass().contains("sidebar-button")) {
            clickedBtn.getStyleClass().add("sidebar-button-active");
        }
    }

    @FXML private void handleAddContact() { showContactDialog(null); }

    @FXML private void handleToggleContactSelection() {
        contactSelectionMode = !contactSelectionMode;
        selectColumn.setVisible(contactSelectionMode);
        selectContactsBtn.setText(contactSelectionMode ? "Done" : "Select");
        contactsTable.getSelectionModel().setSelectionMode(contactSelectionMode ? SelectionMode.MULTIPLE : SelectionMode.SINGLE);
        if (!contactSelectionMode) {
            checkedContacts.clear(); cutContacts.clear(); contactsTable.getSelectionModel().clearSelection();
        }
        contactsTable.refresh();
    }

    private boolean isShortcut(KeyEvent event, KeyCode key) {
        return event.getCode() == key && (event.isControlDown() || event.isMetaDown());
    }

    private List<Contact> getSelectedContacts() {
        Set<Contact> selected = new HashSet<>(checkedContacts);
        selected.addAll(contactsTable.getSelectionModel().getSelectedItems());
        return new ArrayList<>(selected);
    }

    private void copySelectedContacts(boolean cut) {
        List<Contact> selected = getSelectedContacts();
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
        ClipboardContent content = new ClipboardContent(); content.putString(text.toString()); Clipboard.getSystemClipboard().setContent(content);
        if (cut) { cutContacts.clear(); cutContacts.addAll(selected); }
        contactsTable.refresh();
    }

    private String clipboardValue(String value) { return value == null ? "" : value.replace('\t', ' ').replace('\n', ' '); }

    @FXML private void handleDeleteContact() {
        List<Contact> selected = getSelectedContacts();
        if (!selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION); alert.setTitle("Delete Contact");
            applyThemeToDialog(alert);
            alert.setHeaderText(selected.size() == 1 ? "Delete " + selected.get(0).nameProperty().get() + "?" : "Delete " + selected.size() + " contacts?");
            alert.setContentText("This action cannot be undone.");
            if (alert.showAndWait().filter(r -> r == ButtonType.OK).isPresent()) {
                contactData.removeAll(selected); checkedContacts.removeAll(selected); cutContacts.removeAll(selected);
                contactsTable.getSelectionModel().clearSelection(); updatePagination(); saveCurrentData();
            }
        }
    }

    @FXML private void handleShowAllContacts() { rowsPerPageCombo.setValue("All"); }

    private void showContactDialog(Contact contact) {
        Dialog<Contact> dialog = new Dialog<>(); dialog.setTitle(contact == null ? "Add New Contact" : "Edit Contact");
        applyThemeToDialog(dialog);
        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        TextField name = new TextField(); TextField company = new TextField(); TextField title = new TextField();
        TextField email = new TextField(); TextField phone = new TextField();
        TextArea description = new TextArea(); description.setPrefRowCount(3);
        ComboBox<String> tags = new ComboBox<>(FXCollections.observableArrayList("Client", "Tech", "Follow-up"));
        
        if (contact != null) {
            name.setText(contact.nameProperty().get()); company.setText(contact.companyProperty().get());
            title.setText(contact.titleProperty().get()); email.setText(contact.emailProperty().get());
            phone.setText(contact.phoneProperty().get()); tags.setValue(contact.tagsProperty().get());
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
        
        dialog.setResultConverter(db -> {
            if (db == saveButtonType) {
                if (contact == null) return new Contact(name.getText(), company.getText(), title.getText(), email.getText(), phone.getText(), "Just now", tags.getValue(), description.getText());
                else {
                    contact.setName(name.getText()); contact.setCompany(company.getText()); contact.setTitle(title.getText());
                    contact.setEmail(email.getText()); contact.setPhone(phone.getText()); contact.setTags(tags.getValue());
                    contact.setDescription(description.getText());
                    return contact;
                }
            }
            return null;
        });
        dialog.showAndWait().ifPresent(c -> { if (contact == null) contactData.add(0, c); updatePagination(); contactsTable.refresh(); saveCurrentData(); });
    }

    private void filterContacts(String searchText) {
        if (searchText == null || searchText.isEmpty()) filteredData.setPredicate(p -> true);
        else {
            String lcf = searchText.toLowerCase();
            filteredData.setPredicate(c -> 
                c.nameProperty().get().toLowerCase().contains(lcf) ||
                c.companyProperty().get().toLowerCase().contains(lcf) ||
                c.emailProperty().get().toLowerCase().contains(lcf) ||
                (c.descriptionProperty().get() != null && c.descriptionProperty().get().toLowerCase().contains(lcf))
            );
        }
    }

    @FXML private void handleThemeToggle() { isDarkMode = !isDarkMode; applyTheme(); updateThemeButton(); updateRightSidebar(); }

    private void applyTheme() {
        try {
            Scene scene = themeToggleBtn.getScene();
            scene.getStylesheets().clear();
            scene.getStylesheets().add(getClass().getResource(isDarkMode ? "/css/style-dark.css" : "/css/style.css").toExternalForm());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateThemeButton() { themeToggleIcon.setIconLiteral(isDarkMode ? "fas-sun" : "fas-moon"); }

    private void applyThemeToDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        if (themeToggleBtn != null && themeToggleBtn.getScene() != null) {
            dialog.initOwner(themeToggleBtn.getScene().getWindow());
        }
        DialogPane dp = dialog.getDialogPane();
        dp.getStylesheets().clear();
        dp.getStylesheets().add(getClass().getResource(isDarkMode ? "/css/style-dark.css" : "/css/style.css").toExternalForm());
        dp.getStyleClass().add("dialog-pane");
    }
}
