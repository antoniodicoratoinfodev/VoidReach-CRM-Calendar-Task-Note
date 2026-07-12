package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.repository.LocalUserRepository;
import com.crm.repository.UserRepository;
import com.crm.service.CrmWorkspaceService;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FXML composition root for the main window.
 *
 * <p>Feature behavior is delegated to focused controllers; this class only wires
 * injected controls, coordinates the shared workspace snapshot, and exposes the
 * event methods referenced by MainView.fxml.</p>
 */
public final class MainController {
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
    @FXML private MenuButton contactsSortMenu;

    @FXML private ScrollPane homeView;
    @FXML private ScrollPane dashboardView;
    @FXML private VBox contactsView;
    @FXML private VBox calendarView;
    @FXML private VBox tasksView;
    @FXML private VBox genericView;
    @FXML private Label genericTitle;
    @FXML private FontIcon genericIcon;
    @FXML private VBox sidebarContainer;
    @FXML private VBox rightPanel;
    @FXML private Button themeToggleBtn;
    @FXML private FontIcon themeToggleIcon;
    @FXML private Label currentUserLabel;
    @FXML private Label saveStatusLabel;
    @FXML private Button accountMenuButton;
    @FXML private FontIcon defaultAvatarIcon;
    @FXML private ImageView avatarImage;

    @FXML private AnchorPane timeLabelsContainer;
    @FXML private AnchorPane calendarTimelineArea;
    @FXML private ScrollPane calendarScrollPane;
    @FXML private DatePicker calendarDatePicker;
    @FXML private ComboBox<String> viewModeCombo;
    @FXML private Label selectedPeriodLabel;
    @FXML private Label miniMonthYearLabel;
    @FXML private GridPane miniCalendarGrid;
    @FXML private Label activitiesTitle;
    @FXML private VBox upcomingActivitiesList;

    @FXML private TextField taskSearchField;
    @FXML private ComboBox<String> taskFilterCombo;
    @FXML private VBox taskListContainer;
    @FXML private Label tasksEmptyLabel;
    @FXML private Label tasksTotalCountLabel;
    @FXML private Label tasksTodayCountLabel;
    @FXML private Label tasksUpcomingCountLabel;
    @FXML private Label tasksCompletedCountLabel;

    @FXML private Label homeGreetingLabel;
    @FXML private Label homeDateLabel;
    @FXML private Label homeContactsCountLabel;
    @FXML private Label homeTodayCountLabel;
    @FXML private Label homeWeekCountLabel;
    @FXML private Label homeNextTitleLabel;
    @FXML private Label homeNextTimeLabel;
    @FXML private VBox homeTodayList;
    @FXML private VBox homeUpcomingList;
    @FXML private VBox homeContactsList;
    @FXML private Label dashboardContactsCountLabel;
    @FXML private Label dashboardActivitiesCountLabel;
    @FXML private Label dashboardWeekCountLabel;
    @FXML private Label dashboardHoursCountLabel;
    @FXML private PieChart dashboardTagsChart;
    @FXML private BarChart<String, Number> dashboardActivityChart;
    @FXML private VBox dashboardInteractionsList;

    private final CrmWorkspaceService workspaceService = new CrmWorkspaceService();
    private final UserRepository userRepository = new LocalUserRepository();
    private ThemeService themeService;
    private DialogService dialogService;
    private ContactsController contactsController;
    private CalendarController calendarController;
    private TasksController tasksController;
    private AccountController accountController;
    private OverviewController overviewController;
    private NavigationController navigationController;
    private Runnable logoutAction;
    private UserAccount currentUser;
    private boolean loadingWorkspace;

    @FXML
    public void initialize() {
        themeService = new ThemeService(this::ownerWindow);
        dialogService = new DialogService(themeService);
        navigationController = new NavigationController(homeView, dashboardView,
                contactsView, calendarView, tasksView, genericView,
                genericTitle, genericIcon, sidebarContainer);
        overviewController = new OverviewController(
                homeGreetingLabel, homeDateLabel,
                homeContactsCountLabel, homeTodayCountLabel, homeWeekCountLabel,
                homeNextTitleLabel, homeNextTimeLabel,
                homeTodayList, homeUpcomingList, homeContactsList,
                dashboardContactsCountLabel, dashboardActivitiesCountLabel,
                dashboardWeekCountLabel, dashboardHoursCountLabel,
                dashboardTagsChart, dashboardActivityChart, dashboardInteractionsList);
        contactsController = new ContactsController(contactsTable, nameColumn, companyColumn,
                titleColumn, emailColumn, phoneColumn, lastInteractionColumn, tagsColumn,
                selectColumn, searchField, rowsPerPageCombo, contactsPagination,
                paginationInfoLabel, selectContactsBtn, contactsSortMenu, themeService,
                this::handleDataChanged);
        calendarController = new CalendarController(calendarView, timeLabelsContainer,
                calendarTimelineArea, calendarScrollPane, calendarDatePicker, viewModeCombo, selectedPeriodLabel,
                miniMonthYearLabel, miniCalendarGrid, activitiesTitle, upcomingActivitiesList, themeService,
                dialogService, this::handleDataChanged, navigationController::showCalendar);
        tasksController = new TasksController(taskSearchField, taskFilterCombo, taskListContainer,
                tasksEmptyLabel, tasksTotalCountLabel, tasksTodayCountLabel,
                tasksUpcomingCountLabel, tasksCompletedCountLabel, themeService,
                new TasksController.TaskActions() {
                    @Override public void edit(LocalDate date, com.crm.model.Task task) {
                        calendarController.editTask(date, task);
                    }
                    @Override public void delete(LocalDate date, com.crm.model.Task task) {
                        calendarController.deleteTask(date, task);
                    }
                    @Override public void setCompleted(LocalDate date, com.crm.model.Task task, boolean completed) {
                        calendarController.setTaskCompleted(date, task, completed);
                    }
                    @Override public void openCalendar(LocalDate date) {
                        calendarController.showTaskInCalendar(date);
                    }
                });
        accountController = new AccountController(currentUserLabel, accountMenuButton,
                defaultAvatarIcon, avatarImage, themeService, dialogService);

        navigationController.initialize();
        contactsController.initialize();
        calendarController.initialize();
        tasksController.initialize();
        overviewController.refresh(List.of(), Map.of());
        updateThemeButton();
        themeToggleBtn.sceneProperty().addListener((observable, oldScene, newScene) ->
                themeService.applyTo(newScene));
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction) {
        setCurrentUser(user, logoutAction, null, 0);
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction,
                               BufferedImage preloadedAvatar, int preloadedPixelSize) {
        this.currentUser = user;
        this.logoutAction = logoutAction;
        themeService.restore(user.getPreferredTheme());
        themeService.applyTo(themeToggleBtn.getScene());
        updateThemeButton();
        calendarController.refreshTheme();
        overviewController.setUser(user);
        refreshOverview();
        accountController.setCurrentUser(
                user, this::logout, preloadedAvatar, preloadedPixelSize);
        loadingWorkspace = true;
        setSaveStatus("Loading data…");
        workspaceService.openAsync(user).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            try {
                if (failure == null) applyUserData(snapshot);
                else {
                    LocalDate today = LocalDate.now();
                    applyUserData(new CrmDataSnapshot(new ArrayList<>(), new HashMap<>(), today, "Day", 1.0));
                    dialogService.showError("Local data cannot be read",
                            "Neither the data file nor its backup could be recovered. "
                                    + "The workspace will remain open without overwriting the file until you make a change.");
                }
            } finally {
                loadingWorkspace = false;
                setSaveStatus(failure == null ? "Data loaded" : "Data not loaded");
            }
        }));
    }

    public void requestInitialFocus() {
        Platform.runLater(homeView::requestFocus);
    }

    private void applyUserData(CrmDataSnapshot snapshot) {
        contactsController.setContacts(snapshot.contacts());
        calendarController.applyState(snapshot.tasksByDate(), snapshot.selectedDate(),
                snapshot.calendarViewMode(), snapshot.calendarZoom());
        tasksController.refresh(calendarController.tasksSnapshot());
        refreshOverview();
    }

    private void handleDataChanged() {
        refreshOverview();
        saveCurrentData();
    }

    private void refreshOverview() {
        Map<LocalDate, List<com.crm.model.Task>> tasks = calendarController.tasksSnapshot();
        overviewController.refresh(contactsController.snapshot(), tasks);
        tasksController.refresh(tasks);
    }

    private void saveCurrentData() {
        if (loadingWorkspace || calendarController.selectedDate() == null) return;
        CrmDataSnapshot snapshot = CrmDataSnapshot.detachedCopyOf(contactsController.snapshot(),
                calendarController.tasksSnapshot(), calendarController.selectedDate(),
                calendarController.viewMode(), calendarController.zoom());
        workspaceService.requestSave(snapshot, state -> Platform.runLater(() -> handleSaveState(state)));
    }

    private Window ownerWindow() {
        Scene scene = themeToggleBtn == null ? null : themeToggleBtn.getScene();
        return scene == null ? null : scene.getWindow();
    }

    private void logout() {
        loadingWorkspace = true;
        setSaveStatus("Final save…");
        workspaceService.closeAsync().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            loadingWorkspace = false;
            if (failure != null) dialogService.showError("Data not saved",
                    "The save could not be completed before signing out.");
            if (logoutAction != null) logoutAction.run();
        }));
    }

    private void handleSaveState(CrmWorkspaceService.SaveState state) {
        if (state == CrmWorkspaceService.SaveState.SAVING) setSaveStatus("Saving…");
        else if (state == CrmWorkspaceService.SaveState.SAVED) setSaveStatus("Saved");
        else {
            setSaveStatus("Save failed");
            dialogService.showError("Data not saved",
                    "The data could not be saved to disk. Your work remains open in this session so you can try again.");
        }
    }

    private void setSaveStatus(String status) {
        if (saveStatusLabel != null) saveStatusLabel.setText(status);
    }

    @FXML private void handleAccountMenu() { accountController.showMenu(); }
    @FXML private void handleNavigation(ActionEvent event) { navigationController.navigate(event); }
    @FXML private void handleAddContact() { contactsController.addContact(); }
    @FXML private void handleAddTask() { calendarController.createTask(LocalDate.now()); }
    @FXML private void handleToggleContactSelection() { contactsController.toggleSelection(); }
    @FXML private void handleDeleteContact() { contactsController.deleteSelectedContacts(); }
    @FXML private void handleMiniPrevMonth() { calendarController.previousMiniMonth(); }
    @FXML private void handleMiniNextMonth() { calendarController.nextMiniMonth(); }
    @FXML private void handleToday() { calendarController.today(); }
    @FXML private void handlePrevDay() { calendarController.previousPeriod(); }
    @FXML private void handleNextDay() { calendarController.nextPeriod(); }
    @FXML private void handleOpenToday() {
        calendarController.today();
        navigationController.showCalendar();
    }
    @FXML private void handleOpenContacts() { navigationController.showContacts(); }
    @FXML private void handleToggleAgenda() {
        boolean show = !rightPanel.isVisible();
        rightPanel.setVisible(show);
        rightPanel.setManaged(show);
    }

    @FXML
    private void handleThemeToggle() {
        themeService.toggle();
        themeService.applyTo(themeToggleBtn.getScene());
        updateThemeButton();
        calendarController.refreshTheme();
        if (currentUser == null) return;
        currentUser.setPreferredTheme(themeService.activeTheme().name());
        try {
            userRepository.save(currentUser);
        } catch (IllegalStateException failure) {
            dialogService.showError("Theme not saved",
                    "The theme was applied, but it could not be remembered for the next launch.");
        }
    }

    private void updateThemeButton() {
        themeToggleIcon.setIconLiteral(themeService.isBlueGrayTheme()
                ? "fas-palette"
                : themeService.isDarkMode() ? "fas-sun" : "fas-moon");
        themeToggleBtn.setText("Theme: " + themeService.activeTheme().displayName());
    }
}
