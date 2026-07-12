package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.CrmDataSnapshot;
import com.crm.model.UserAccount;
import com.crm.service.CrmWorkspaceService;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;

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

    @FXML private VBox contactsView;
    @FXML private VBox calendarView;
    @FXML private VBox genericView;
    @FXML private Label genericTitle;
    @FXML private FontIcon genericIcon;
    @FXML private VBox sidebarContainer;
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

    private final CrmWorkspaceService workspaceService = new CrmWorkspaceService();
    private ThemeService themeService;
    private DialogService dialogService;
    private ContactsController contactsController;
    private CalendarController calendarController;
    private AccountController accountController;
    private NavigationController navigationController;
    private Runnable logoutAction;
    private boolean loadingWorkspace;

    @FXML
    public void initialize() {
        themeService = new ThemeService(this::ownerWindow);
        dialogService = new DialogService(themeService);
        navigationController = new NavigationController(contactsView, calendarView, genericView,
                genericTitle, genericIcon, sidebarContainer);
        contactsController = new ContactsController(contactsTable, nameColumn, companyColumn,
                titleColumn, emailColumn, phoneColumn, lastInteractionColumn, tagsColumn,
                selectColumn, searchField, rowsPerPageCombo, contactsPagination,
                paginationInfoLabel, selectContactsBtn, themeService,
                this::saveCurrentData);
        calendarController = new CalendarController(calendarView, timeLabelsContainer,
                calendarTimelineArea, calendarScrollPane, calendarDatePicker, viewModeCombo, selectedPeriodLabel,
                miniMonthYearLabel, miniCalendarGrid, activitiesTitle, upcomingActivitiesList, themeService,
                dialogService, this::saveCurrentData, navigationController::showCalendar);
        accountController = new AccountController(currentUserLabel, accountMenuButton,
                defaultAvatarIcon, avatarImage, themeService, dialogService);

        navigationController.initialize();
        contactsController.initialize();
        calendarController.initialize();
        updateThemeButton();
    }

    public void setCurrentUser(UserAccount user, Runnable logoutAction) {
        this.logoutAction = logoutAction;
        accountController.setCurrentUser(user, this::logout);
        loadingWorkspace = true;
        setSaveStatus("Caricamento dati…");
        workspaceService.openAsync(user).whenComplete((snapshot, failure) -> Platform.runLater(() -> {
            try {
                if (failure == null) applyUserData(snapshot);
                else {
                    LocalDate today = LocalDate.now();
                    applyUserData(new CrmDataSnapshot(new ArrayList<>(), new HashMap<>(), today, "Day", 1.0));
                    dialogService.showError("Dati locali non leggibili",
                            "Non è stato possibile recuperare il file dati né il suo backup. "
                                    + "L'area di lavoro resta aperta senza sovrascrivere il file finché non effettui una modifica.");
                }
            } finally {
                loadingWorkspace = false;
                setSaveStatus(failure == null ? "Dati caricati" : "Dati non caricati");
            }
        }));
    }

    public void requestInitialFocus() {
        contactsController.requestInitialFocus();
    }

    private void applyUserData(CrmDataSnapshot snapshot) {
        contactsController.setContacts(snapshot.contacts());
        calendarController.applyState(snapshot.tasksByDate(), snapshot.selectedDate(),
                snapshot.calendarViewMode(), snapshot.calendarZoom());
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
        setSaveStatus("Salvataggio finale…");
        workspaceService.closeAsync().whenComplete((ignored, failure) -> Platform.runLater(() -> {
            loadingWorkspace = false;
            if (failure != null) dialogService.showError("Dati non salvati",
                    "Non è stato possibile completare il salvataggio prima dell'uscita.");
            if (logoutAction != null) logoutAction.run();
        }));
    }

    private void handleSaveState(CrmWorkspaceService.SaveState state) {
        if (state == CrmWorkspaceService.SaveState.SAVING) setSaveStatus("Salvataggio…");
        else if (state == CrmWorkspaceService.SaveState.SAVED) setSaveStatus("Salvato");
        else {
            setSaveStatus("Salvataggio non riuscito");
            dialogService.showError("Dati non salvati",
                    "Non è stato possibile salvare i dati sul disco. Il lavoro resta aperto in questa sessione e potrai riprovare.");
        }
    }

    private void setSaveStatus(String status) {
        if (saveStatusLabel != null) saveStatusLabel.setText(status);
    }

    @FXML private void handleAccountMenu() { accountController.showMenu(); }
    @FXML private void handleNavigation(ActionEvent event) { navigationController.navigate(event); }
    @FXML private void handleAddContact() { contactsController.addContact(); }
    @FXML private void handleToggleContactSelection() { contactsController.toggleSelection(); }
    @FXML private void handleDeleteContact() { contactsController.deleteSelectedContacts(); }
    @FXML private void handleShowAllContacts() { contactsController.showAll(); }
    @FXML private void handleMiniPrevMonth() { calendarController.previousMiniMonth(); }
    @FXML private void handleMiniNextMonth() { calendarController.nextMiniMonth(); }
    @FXML private void handleToday() { calendarController.today(); }
    @FXML private void handlePrevDay() { calendarController.previousPeriod(); }
    @FXML private void handleNextDay() { calendarController.nextPeriod(); }

    @FXML
    private void handleThemeToggle() {
        themeService.toggle();
        themeService.applyTo(themeToggleBtn.getScene());
        updateThemeButton();
        calendarController.refreshTheme();
    }

    private void updateThemeButton() {
        themeToggleIcon.setIconLiteral(themeService.isDarkMode() ? "fas-sun" : "fas-moon");
    }
}
