package com.crm.controller;

import com.crm.model.Note;
import com.crm.model.Task;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Owns calendar state, task editing, timeline rendering, zoom, and the calendar sidebar. */
public final class CalendarController {
    private static final double MINUTE_HEIGHT = 1.0;
    private static final double HOUR_HEIGHT = 60.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double MIN_ZOOM = 0.75;
    private static final double MAX_ZOOM = 3.0;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double TIME_COLUMN_WIDTH = 65.0;
    private static final double MIN_TIMELINE_WIDTH = 320.0;

    private final VBox calendarView;
    private final AnchorPane timeLabelsContainer;
    private final AnchorPane timelineArea;
    private final ScrollPane scrollPane;
    private final DatePicker datePicker;
    private final ComboBox<String> viewModeCombo;
    private final Label selectedPeriodLabel;
    private final Label miniMonthYearLabel;
    private final GridPane miniCalendarGrid;
    private final Label activitiesTitle;
    private final VBox upcomingActivitiesList;
    private final ThemeService themeService;
    private final DialogService dialogService;
    private final Runnable dataChanged;
    private final Runnable showCalendar;
    private final Map<LocalDate, List<Task>> tasksByDate = new HashMap<>();
    private NoteIntegration noteIntegration = NoteIntegration.EMPTY;

    private double dragAnchorY;
    private double dragAnchorX;
    private double dragInitialTop;
    private int dragTargetDayOffset;
    private boolean draggingTask;
    private double zoom = DEFAULT_ZOOM;
    private PauseTransition resizeDebounce;
    private boolean calendarOpening;
    private boolean applyingState;
    private Scene shortcutScene;
    private String viewMode = "Day";
    private LocalDate weekStartDate;
    private YearMonth currentMiniMonth;

    public CalendarController(VBox calendarView, AnchorPane timeLabelsContainer,
                              AnchorPane timelineArea, ScrollPane scrollPane,
                              DatePicker datePicker, ComboBox<String> viewModeCombo,
                              Label selectedPeriodLabel, Label miniMonthYearLabel, GridPane miniCalendarGrid,
                              Label activitiesTitle, VBox upcomingActivitiesList, ThemeService themeService,
                              DialogService dialogService, Runnable dataChanged,
                              Runnable showCalendar) {
        this.calendarView = Objects.requireNonNull(calendarView);
        this.timeLabelsContainer = Objects.requireNonNull(timeLabelsContainer);
        this.timelineArea = Objects.requireNonNull(timelineArea);
        this.scrollPane = Objects.requireNonNull(scrollPane);
        this.datePicker = Objects.requireNonNull(datePicker);
        this.viewModeCombo = Objects.requireNonNull(viewModeCombo);
        this.selectedPeriodLabel = Objects.requireNonNull(selectedPeriodLabel);
        this.miniMonthYearLabel = Objects.requireNonNull(miniMonthYearLabel);
        this.miniCalendarGrid = Objects.requireNonNull(miniCalendarGrid);
        this.activitiesTitle = Objects.requireNonNull(activitiesTitle);
        this.upcomingActivitiesList = Objects.requireNonNull(upcomingActivitiesList);
        this.themeService = Objects.requireNonNull(themeService);
        this.dialogService = Objects.requireNonNull(dialogService);
        this.dataChanged = Objects.requireNonNull(dataChanged);
        this.showCalendar = Objects.requireNonNull(showCalendar);
    }

    public void initialize() {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        currentMiniMonth = YearMonth.from(today);
        weekStartDate = weekStart(today);
        datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) refreshForSelectedDate(newValue);
        });
        setupViewModeCombo();
        setupZoomControls();
        render();
        updateSidebar();
    }

    public void setNoteIntegration(NoteIntegration noteIntegration) {
        this.noteIntegration = noteIntegration == null ? NoteIntegration.EMPTY : noteIntegration;
        if (currentMiniMonth != null) {
            render();
            updateSidebar();
        }
    }

    public void applyState(Map<LocalDate, List<Task>> source, LocalDate selectedDate,
                           String selectedViewMode, double selectedZoom) {
        applyingState = true;
        try {
            tasksByDate.clear();
            if (source != null) source.forEach((date, tasks) -> tasksByDate.put(date, new ArrayList<>(tasks)));
            zoom = clamp(selectedZoom, MIN_ZOOM, MAX_ZOOM);
            viewMode = "Week".equals(selectedViewMode) ? "Week" : "Day";
            LocalDate date = selectedDate == null ? LocalDate.now() : selectedDate;
            viewModeCombo.setValue(viewMode);
            datePicker.setValue(date);
            weekStartDate = weekStart(date);
            currentMiniMonth = YearMonth.from(date);
            render();
            updateSidebar();
        } finally {
            applyingState = false;
        }
    }

    public Map<LocalDate, List<Task>> tasksSnapshot() {
        Map<LocalDate, List<Task>> copy = new HashMap<>();
        tasksByDate.forEach((date, tasks) -> copy.put(date, new ArrayList<>(tasks)));
        return copy;
    }

    public LocalDate selectedDate() { return datePicker.getValue(); }
    public String viewMode() { return viewMode; }
    public double zoom() { return zoom; }

    public void createTask(LocalDate date) {
        selectDate(date == null ? LocalDate.now() : date);
        int start = Math.min(23 * 60, Math.max(0, (java.time.LocalTime.now().getHour() + 1) * 60));
        showTaskDialog(null, start, Math.min(60, Task.MINUTES_PER_DAY - start), "");
    }

    public void editTask(LocalDate date, Task task) {
        if (date == null || task == null) return;
        selectDate(date);
        showTaskDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
    }

    public void deleteTask(LocalDate date, Task task) {
        if (date == null || task == null) return;
        unlinkNotes(task.getId());
        removeTask(date, task);
        render();
        updateSidebar();
        notifyDataChanged();
    }

    public void setTaskCompleted(LocalDate date, Task task, boolean completed) {
        if (date == null || task == null || task.isCompleted() == completed) return;
        task.setCompleted(completed);
        render();
        updateSidebar();
        notifyDataChanged();
    }

    public void showTaskInCalendar(LocalDate date) {
        if (date == null) return;
        selectDate(date);
        showCalendar.run();
    }

    public void refreshTheme() {
        render();
        updateSidebar();
    }

    public void previousMiniMonth() {
        currentMiniMonth = currentMiniMonth.minusMonths(1);
        updateSidebar();
    }

    public void nextMiniMonth() {
        currentMiniMonth = currentMiniMonth.plusMonths(1);
        updateSidebar();
    }

    public void today() { selectDate(LocalDate.now()); }

    public void previousPeriod() {
        LocalDate target = viewMode.equals("Day")
                ? datePicker.getValue().minusDays(1) : weekStartDate.minusWeeks(1);
        selectDate(target);
    }

    public void nextPeriod() {
        LocalDate target = viewMode.equals("Day")
                ? datePicker.getValue().plusDays(1) : weekStartDate.plusWeeks(1);
        selectDate(target);
    }

    private void setupZoomControls() {
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        resizeDebounce = new PauseTransition(Duration.millis(120));
        resizeDebounce.setOnFinished(event -> {
            render();
            scrollPane.setHvalue(0);
        });
        scrollPane.viewportBoundsProperty().addListener((observable, oldBounds, newBounds) -> {
            if (Math.abs(oldBounds.getWidth() - newBounds.getWidth()) <= 1 || !calendarView.isVisible()) return;
            if (calendarOpening) {
                calendarOpening = false;
                resizeDebounce.stop();
                render();
                scrollPane.setHvalue(0);
            } else {
                resizeDebounce.playFromStart();
            }
        });
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!calendarView.isVisible()) return;
            if (!event.isControlDown() && !event.isMetaDown()) return;
            event.consume();
            Point2D pivot = timelineArea.sceneToLocal(event.getSceneX(), event.getSceneY());
            if (event.getDeltaY() > 0) handleZoom(true, pivot.getY());
            else if (event.getDeltaY() < 0) handleZoom(false, pivot.getY());
        });
        timelineArea.setFocusTraversable(true);
        installShortcutFilterWhenSceneReady();
        calendarView.visibleProperty().addListener((observable, oldValue, visible) -> {
            if (visible) {
                calendarOpening = true;
                resizeDebounce.stop();
                timelineArea.requestFocus();
                scrollPane.setHvalue(0);
            } else {
                calendarOpening = false;
                resizeDebounce.stop();
            }
        });
    }

    private void installShortcutFilterWhenSceneReady() {
        calendarView.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null) oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
            shortcutScene = newScene;
            if (newScene != null) newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
        });
        if (calendarView.getScene() != null) {
            shortcutScene = calendarView.getScene();
            shortcutScene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleCalendarShortcut);
        }
    }

    private void handleCalendarShortcut(KeyEvent event) {
        if (!calendarView.isVisible() || !calendarView.isHover()) return;
        if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.DIGIT0) {
            event.consume();
            applyZoom(DEFAULT_ZOOM, viewportCenterContentY());
        }
    }

    private void handleZoom(boolean zoomIn, double pivotContentY) {
        double target = zoomIn ? Math.min(zoom + ZOOM_STEP, MAX_ZOOM) : Math.max(zoom - ZOOM_STEP, MIN_ZOOM);
        applyZoom(target, pivotContentY);
    }

    private void applyZoom(double target, double pivotContentY) {
        double oldZoom = zoom;
        if (oldZoom == target) return;
        double contentHeight = calendarContentHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double oldScrollY = scrollY(contentHeight, viewportHeight);
        double pivotViewportY = clamp(pivotContentY - oldScrollY, 0, Math.max(0, viewportHeight));
        double stablePivotContentY = oldScrollY + pivotViewportY;
        double scaleFactor = target / oldZoom;
        zoom = target;
        render();
        scrollPane.setHvalue(0);
        notifyDataChanged();
        Platform.runLater(() -> {
            double newScrollableHeight = Math.max(0, calendarContentHeight() - viewportHeight);
            if (newScrollableHeight > 0) {
                double newScrollY = stablePivotContentY * scaleFactor - pivotViewportY;
                scrollPane.setVvalue(clamp(newScrollY / newScrollableHeight, 0, 1));
            } else scrollPane.setVvalue(0);
            scrollPane.setHvalue(0);
        });
    }

    private double viewportCenterContentY() {
        double contentHeight = calendarContentHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        return scrollY(contentHeight, viewportHeight) + viewportHeight / 2;
    }

    private double scrollY(double contentHeight, double viewportHeight) {
        return scrollPane.getVvalue() * Math.max(0, contentHeight - viewportHeight);
    }

    private double calendarContentHeight() {
        return Math.max(timelineArea.getHeight(), timelineArea.getPrefHeight());
    }

    private double timelineWidth() {
        double viewportWidth = scrollPane.getViewportBounds().getWidth();
        if (viewportWidth <= 0) viewportWidth = scrollPane.getWidth();
        if (viewportWidth <= 0) return 1000;
        return Math.max(MIN_TIMELINE_WIDTH, viewportWidth - TIME_COLUMN_WIDTH);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private void setupViewModeCombo() {
        viewModeCombo.setItems(FXCollections.observableArrayList("Day", "Week"));
        viewModeCombo.setValue("Day");
        viewModeCombo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
            viewMode = newValue;
            if (newValue.equals("Week")) weekStartDate = weekStart(datePicker.getValue());
            render();
            updateSidebar();
            notifyDataChanged();
        });
    }

    private LocalDate weekStart(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() % 7);
    }

    private void addTask(LocalDate date, Task task) {
        tasksByDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(task);
    }

    private void removeTask(LocalDate date, Task task) {
        List<Task> tasks = tasksByDate.get(date);
        if (tasks == null) return;
        tasks.remove(task);
        if (tasks.isEmpty()) tasksByDate.remove(date);
    }

    private void render() {
        timelineArea.getChildren().clear();
        timeLabelsContainer.getChildren().clear();
        double zoomedHourHeight = HOUR_HEIGHT * zoom;
        double zoomedMinuteHeight = MINUTE_HEIGHT * zoom;
        double width = timelineWidth();
        double height = 24 * zoomedHourHeight + 20;
        timeLabelsContainer.setPrefHeight(height);
        timelineArea.setPrefHeight(height);
        timelineArea.setMinWidth(width);
        timelineArea.setPrefWidth(width);
        timelineArea.setMaxWidth(width);
        Canvas grid = new Canvas(width, height);
        grid.setMouseTransparent(true);
        drawTimelineGrid(grid, width, zoomedHourHeight, zoomedMinuteHeight);
        timelineArea.getChildren().add(grid);
        for (int hour = 0; hour <= 24; hour++) {
            double hourY = hour * zoomedHourHeight;
            Label hourLabel = new Label(String.format("%02d:00", hour));
            hourLabel.getStyleClass().add("hour-label");
            AnchorPane.setRightAnchor(hourLabel, 10.0);
            AnchorPane.setTopAnchor(hourLabel, hourY - 10);
            timeLabelsContainer.getChildren().add(hourLabel);
            if (hour < 24) addSubHourLabels(hour, hourY, zoomedMinuteHeight);
        }
        if (viewMode.equals("Day")) renderDayView(zoomedMinuteHeight);
        else renderWeekView(width, zoomedMinuteHeight);
    }

    private void addSubHourLabels(int hour, double hourY, double zoomedMinuteHeight) {
        int subdivisions = zoom > 1.5 ? 60 : 4;
        int interval = 60 / subdivisions;
        for (int subdivision = 1; subdivision < subdivisions; subdivision++) {
            double y = hourY + subdivision * interval * zoomedMinuteHeight;
            if (subdivision * interval % 15 == 0 || zoom > 1.5 && subdivision % 5 == 0) {
                Label label = new Label(String.format("%02d:%02d", hour, subdivision * interval));
                label.getStyleClass().add("sub-hour-label");
                AnchorPane.setRightAnchor(label, 10.0);
                AnchorPane.setTopAnchor(label, y - 7);
                timeLabelsContainer.getChildren().add(label);
            }
        }
    }

    private void drawTimelineGrid(Canvas canvas, double width, double hourHeight, double minuteHeight) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Color hourColor = Color.web(themeService.isBlueGrayTheme() ? "#43516a"
                : themeService.isDarkMode() ? "#2a3a52" : "#dbe2ea");
        Color intervalColor = Color.web(themeService.isBlueGrayTheme() ? "#2e3b52"
                : themeService.isDarkMode() ? "#18243a" : "#edf1f5");
        Color weekDivider = Color.web(themeService.isBlueGrayTheme() ? "#3a4961"
                : themeService.isDarkMode() ? "#263449" : "#e5eaf1");
        graphics.setLineWidth(1);
        for (int hour = 0; hour <= 24; hour++) {
            double hourY = hour * hourHeight;
            graphics.setGlobalAlpha(1);
            graphics.setStroke(hourColor);
            graphics.strokeLine(0, hourY, width, hourY);
            if (hour >= 24) continue;
            int subdivisions = zoom > 1.5 ? 60 : 4;
            int interval = 60 / subdivisions;
            graphics.setStroke(intervalColor);
            for (int subdivision = 1; subdivision < subdivisions; subdivision++) {
                double y = hourY + subdivision * interval * minuteHeight;
                graphics.setGlobalAlpha(zoom > 1.5 && subdivision % 5 != 0 ? 0.3 : 1);
                graphics.strokeLine(0, y, width, y);
            }
        }
        if (viewMode.equals("Week")) {
            double dayWidth = width / 7.0;
            graphics.setGlobalAlpha(1);
            graphics.setStroke(weekDivider);
            for (int day = 1; day < 7; day++) graphics.strokeLine(day * dayWidth, 0, day * dayWidth, 24 * hourHeight);
        }
        graphics.setGlobalAlpha(1);
    }

    private void renderDayView(double minuteHeight) {
        timelineArea.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1 && event.getTarget() == timelineArea
                    && event.getButton() == MouseButton.PRIMARY) {
                showNewTaskDialogAt((int) (event.getY() / minuteHeight));
            }
        });
        for (Task task : tasksByDate.getOrDefault(datePicker.getValue(), List.of())) {
            renderTask(task, minuteHeight, 1.0, 0, 10.0);
        }
    }

    private void renderWeekView(double width, double minuteHeight) {
        double dayWidth = width / 7.0;
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStartDate.plusDays(day);
            Label header = new Label(date.format(DateTimeFormatter.ofPattern("EEE dd/MM")));
            header.getStyleClass().add("week-day-header");
            header.setPrefWidth(dayWidth);
            header.setLayoutX(day * dayWidth);
            header.setLayoutY(0);
            timelineArea.getChildren().add(header);
            for (Task task : tasksByDate.getOrDefault(date, List.of())) renderTask(task, minuteHeight, 1.0 / 7.0, day, 5.0);
        }
        timelineArea.setOnMouseClicked(event -> {
            if (event.getClickCount() != 1 || event.getTarget() != timelineArea
                    || event.getButton() != MouseButton.PRIMARY) return;
            int dayOffset = (int) (event.getX() / dayWidth);
            if (dayOffset >= 0 && dayOffset < 7) {
                datePicker.setValue(weekStartDate.plusDays(dayOffset));
                showNewTaskDialogAt((int) (event.getY() / minuteHeight));
            }
        });
    }

    private void showNewTaskDialogAt(int requestedStartMinute) {
        int start = Math.max(0, Math.min(Task.MINUTES_PER_DAY - Task.MIN_DURATION_MINUTES, requestedStartMinute));
        showTaskDialog(null, start, Math.min(60, Task.MINUTES_PER_DAY - start), "");
    }

    private void renderTask(Task task, double minuteHeight, double widthPercent, int dayOffset, double margin) {
        VBox box = new VBox();
        box.getStyleClass().addAll("task-entry", "task-" + task.getColor().toLowerCase());
        if (task.isCompleted()) box.getStyleClass().add("task-entry-completed");
        double width = timelineWidth();
        double dayWidth = width * widthPercent;
        AnchorPane.setLeftAnchor(box, dayOffset * dayWidth + margin);
        AnchorPane.setTopAnchor(box, task.getStartMin() * minuteHeight);
        box.setPrefWidth(dayWidth - margin * 2);
        box.setPrefHeight(task.getDuration() * minuteHeight);
        Label title = new Label(task.getTitle());
        title.getStyleClass().add("task-title");
        Label time = new Label();
        time.getStyleClass().add("task-time");
        updateTimeLabel(time, task.getStartMin(), task.getDuration());
        Label description = new Label(task.getDescription());
        description.getStyleClass().add("task-desc");
        List<Note> linkedNotes = noteIntegration.notesForTask(task.getId());
        FlowPane noteLinks = null;
        if (!linkedNotes.isEmpty()) {
            noteLinks = new FlowPane(4, 2);
            noteLinks.getStyleClass().add("calendar-note-links");
            double maximumLinkWidth = Math.max(48, dayWidth - margin * 4);
            for (Note linked : linkedNotes) {
                String noteTitle = linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle();
                Button openNote = new Button(noteTitle);
                openNote.getStyleClass().add("calendar-note-link");
                openNote.setFocusTraversable(false);
                openNote.setMaxWidth(maximumLinkWidth);
                openNote.setTextOverrun(OverrunStyle.ELLIPSIS);
                openNote.setTooltip(new Tooltip("Open note: " + noteTitle));
                openNote.setOnAction(event -> noteIntegration.openNote(linked.getId()));
                openNote.setOnMousePressed(javafx.event.Event::consume);
                openNote.setOnMouseReleased(javafx.event.Event::consume);
                noteLinks.getChildren().add(openNote);
            }
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Region resizer = new Region();
        resizer.getStyleClass().add("task-resizer");
        resizer.setPrefHeight(10);
        resizer.setOnMousePressed(event -> { dragAnchorY = event.getScreenY(); event.consume(); });
        resizer.setOnMouseDragged(event -> {
            double delta = event.getScreenY() - dragAnchorY;
            int proposed = task.getDuration() + (int) (delta / minuteHeight);
            int maximum = Task.MINUTES_PER_DAY - task.getStartMin();
            task.setDuration(Math.max(Task.MIN_DURATION_MINUTES, Math.min(maximum, proposed)));
            box.setPrefHeight(task.getDuration() * minuteHeight);
            updateTimeLabel(time, task.getStartMin(), task.getDuration());
            dragAnchorY = event.getScreenY();
            updateSidebar();
            event.consume();
        });
        resizer.setOnMouseReleased(event -> { notifyDataChanged(); event.consume(); });
        box.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            dragAnchorY = event.getSceneY();
            dragAnchorX = event.getSceneX();
            dragInitialTop = AnchorPane.getTopAnchor(box);
            dragTargetDayOffset = dayOffset;
            draggingTask = true;
            box.getStyleClass().add("task-entry-dragging");
            box.toFront();
        });
        box.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            int proposed = (int) ((dragInitialTop + event.getSceneY() - dragAnchorY) / minuteHeight);
            task.setStartMin(Math.max(0, Math.min(Task.MINUTES_PER_DAY - task.getDuration(), proposed)));
            AnchorPane.setTopAnchor(box, task.getStartMin() * minuteHeight);
            if (viewMode.equals("Week")) {
                double weekDayWidth = timelineWidth() / 7.0;
                double x = dayOffset * weekDayWidth + event.getSceneX() - dragAnchorX;
                dragTargetDayOffset = Math.max(0, Math.min(6, (int) Math.floor((x + weekDayWidth / 2) / weekDayWidth)));
                AnchorPane.setLeftAnchor(box, dragTargetDayOffset * weekDayWidth + margin);
            }
            updateTimeLabel(time, task.getStartMin(), task.getDuration());
        });
        box.setOnMouseReleased(event -> {
            if (!draggingTask) return;
            draggingTask = false;
            box.getStyleClass().remove("task-entry-dragging");
            if (viewMode.equals("Week")) {
                LocalDate source = weekStartDate.plusDays(dayOffset);
                LocalDate target = weekStartDate.plusDays(dragTargetDayOffset);
                if (!source.equals(target)) {
                    removeTask(source, task);
                    addTask(target, task);
                    selectDate(target);
                    return;
                }
            }
            updateSidebar();
            notifyDataChanged();
        });
        box.setFocusTraversable(true);
        box.setOnMouseClicked(event -> {
            if (isButtonTarget(event.getTarget(), box)) {
                event.consume();
                return;
            }
            box.requestFocus();
            if (event.getButton() == MouseButton.PRIMARY || event.getButton() == MouseButton.SECONDARY) {
                if (viewMode.equals("Week")) datePicker.setValue(weekStartDate.plusDays(dayOffset));
                showTaskDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
            }
        });
        box.setOnKeyPressed(event -> {
            if (event.getCode() != KeyCode.BACK_SPACE && event.getCode() != KeyCode.DELETE) return;
            LocalDate date = viewMode.equals("Day") ? datePicker.getValue() : weekStartDate.plusDays(dayOffset);
            unlinkNotes(task.getId());
            removeTask(date, task);
            render();
            updateSidebar();
            notifyDataChanged();
        });
        box.getChildren().addAll(title, time, description);
        if (noteLinks != null) box.getChildren().add(noteLinks);
        box.getChildren().addAll(spacer, resizer);
        timelineArea.getChildren().add(box);
    }

    private void updateTimeLabel(Label label, int start, int duration) {
        int end = start + duration;
        label.setText(String.format("%02d:%02d - %02d:%02d", start / 60, start % 60, end / 60, end % 60));
    }

    private void updateSidebar() {
        updateSelectedPeriodLabel();
        miniMonthYearLabel.setText(currentMiniMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)));
        miniCalendarGrid.getChildren().clear();
        String[] days = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
        for (int index = 0; index < days.length; index++) {
            Label header = new Label(days[index]);
            header.getStyleClass().addAll("calendar-day", "calendar-day-header");
            miniCalendarGrid.add(header, index, 0);
        }
        LocalDate first = currentMiniMonth.atDay(1);
        int dayOffset = first.getDayOfWeek().getValue() % 7;
        for (int index = 0; index < currentMiniMonth.lengthOfMonth(); index++) {
            LocalDate date = first.plusDays(index);
            Button day = new Button(String.valueOf(index + 1));
            day.getStyleClass().add("calendar-day");
            if (date.equals(datePicker.getValue())) {
                day.getStyleClass().add("calendar-day-selected");
            } else if (date.equals(LocalDate.now())) {
                day.getStyleClass().add("calendar-day-today");
            }
            day.setOnAction(event -> { datePicker.setValue(date); showCalendar.run(); });
            miniCalendarGrid.add(day, (index + dayOffset) % 7, (index + dayOffset) / 7 + 1);
        }
        upcomingActivitiesList.getChildren().clear();
        boolean weekView = "Week".equals(viewMode);
        activitiesTitle.setText(weekView ? "This week's tasks" : "Today's tasks");
        List<SidebarTask> tasks = sidebarTasks(weekView);
        if (tasks.isEmpty()) {
            Label empty = new Label(weekView ? "No tasks this week." : "No tasks for this day.");
            empty.getStyleClass().add("empty-activities");
            upcomingActivitiesList.getChildren().add(empty);
            return;
        }
        for (SidebarTask sidebarTask : tasks) {
            Task task = sidebarTask.task();
            VBox item = new VBox(5);
            item.getStyleClass().add("activity-item");
            Label time = new Label(String.format("%02d:%02d - %02d:%02d", task.getStartMin() / 60,
                    task.getStartMin() % 60, (task.getStartMin() + task.getDuration()) / 60,
                    (task.getStartMin() + task.getDuration()) % 60));
            time.getStyleClass().add("activity-time");
            String titleText = weekView
                    ? sidebarTask.date().format(DateTimeFormatter.ofPattern("EEEE d", Locale.ENGLISH)) + " " + task.getTitle()
                    : task.getTitle();
            Label title = new Label(titleText);
            title.getStyleClass().add("activity-title");
            item.getChildren().addAll(time, title);
            List<Note> linkedNotes = noteIntegration.notesForTask(task.getId());
            if (!linkedNotes.isEmpty()) {
                if (linkedNotes.size() == 1) {
                    Note linked = linkedNotes.getFirst();
                    Button openNote = new Button("Open note");
                    openNote.getStyleClass().add("activity-note-link");
                    openNote.setOnAction(event -> noteIntegration.openNote(linked.getId()));
                    item.getChildren().add(openNote);
                } else {
                    MenuButton note = new MenuButton("Open " + linkedNotes.size() + " notes");
                    note.getStyleClass().add("activity-note-link");
                    linkedNotes.forEach(linked -> {
                        MenuItem menuItem = new MenuItem(linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle());
                        menuItem.setOnAction(event -> noteIntegration.openNote(linked.getId()));
                        note.getItems().add(menuItem);
                    });
                    item.getChildren().add(note);
                }
            }
            item.setOnMouseClicked(event -> {
                if (isButtonTarget(event.getTarget(), item)) return;
                boolean openRequest = event.getButton() == MouseButton.SECONDARY
                        || event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 1;
                if (!openRequest) return;
                selectDate(sidebarTask.date());
                showCalendar.run();
                showTaskDialog(task, task.getStartMin(), task.getDuration(), task.getDescription());
                event.consume();
            });
            upcomingActivitiesList.getChildren().add(item);
        }
    }

    private void updateSelectedPeriodLabel() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if ("Week".equals(viewMode)) {
            LocalDate start = weekStartDate;
            selectedPeriodLabel.setText("Selected week: " + formatter.format(start)
                    + " - " + formatter.format(start.plusDays(6)));
        } else {
            selectedPeriodLabel.setText("Selected day: " + formatter.format(datePicker.getValue()));
        }
    }

    private List<SidebarTask> sidebarTasks(boolean weekView) {
        if (!weekView) return tasksByDate.getOrDefault(datePicker.getValue(), List.of()).stream()
                .sorted(Comparator.comparingInt(Task::getStartMin))
                .map(task -> new SidebarTask(datePicker.getValue(), task))
                .toList();

        LocalDate start = weekStartDate;
        return tasksByDate.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(start) && entry.getKey().isBefore(start.plusDays(7)))
                .flatMap(entry -> entry.getValue().stream().map(task -> new SidebarTask(entry.getKey(), task)))
                .sorted(Comparator.comparing(SidebarTask::date)
                        .thenComparing(entry -> entry.task().getStartMin()))
                .toList();
    }

    private record SidebarTask(LocalDate date, Task task) { }

    private void selectDate(LocalDate date) {
        if (date.equals(datePicker.getValue())) refreshForSelectedDate(date);
        else datePicker.setValue(date);
    }

    private void refreshForSelectedDate(LocalDate date) {
        currentMiniMonth = YearMonth.from(date);
        if (viewMode.equals("Week")) weekStartDate = weekStart(date);
        render();
        updateSidebar();
        notifyDataChanged();
    }

    private void showTaskDialog(Task existingTask, int startMin, int duration, String initialDescription) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existingTask == null ? "New Task" : "Edit Task");
        themeService.applyTo(dialog);
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        ButtonType delete = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        if (existingTask != null) dialog.getDialogPane().getButtonTypes().add(1, delete);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 100, 10, 10));
        TextField title = new TextField(existingTask == null ? "" : existingTask.getTitle());
        TextArea description = new TextArea(existingTask == null ? initialDescription : existingTask.getDescription());
        description.setPrefRowCount(3);
        TextField startHour = timeField(startMin / 60);
        TextField startMinute = timeField(startMin % 60);
        TextField endHour = timeField((startMin + duration) / 60);
        TextField endMinute = timeField((startMin + duration) % 60);
        LocalDate sourceDate = datePicker.getValue();
        DatePicker taskDate = new DatePicker(sourceDate);
        ComboBox<String> color = new ComboBox<>(FXCollections.observableArrayList(
                "Blue", "Red", "Green", "Yellow", "Orange", "Purple"));
        color.setValue(existingTask == null ? "Blue" : existingTask.getColor());
        MenuButton noteMenu = new MenuButton("No linked notes");
        noteMenu.getStyleClass().add("task-note-selector");
        noteMenu.setMaxWidth(Double.MAX_VALUE);
        Map<Note, CheckMenuItem> noteSelections = new java.util.LinkedHashMap<>();
        String existingTaskId = existingTask == null ? "" : existingTask.getId();
        Runnable updateNoteMenuText = () -> {
            long selected = noteSelections.values().stream().filter(CheckMenuItem::isSelected).count();
            noteMenu.setText(selected == 0 ? "No linked notes"
                    : selected + (selected == 1 ? " linked note" : " linked notes"));
        };
        noteIntegration.notes().forEach(candidate -> {
            String noteTitle = candidate.getTitle().isBlank() ? "Untitled note" : candidate.getTitle();
            CheckMenuItem choice = new CheckMenuItem(noteTitle + " " + candidate.getFormat().extension());
            choice.setSelected(!existingTaskId.isBlank() && candidate.isLinkedToTask(existingTaskId));
            choice.setOnAction(event -> updateNoteMenuText.run());
            noteSelections.put(candidate, choice);
            noteMenu.getItems().add(choice);
        });
        if (noteSelections.isEmpty()) {
            MenuItem empty = new MenuItem("No notes available");
            empty.setDisable(true);
            noteMenu.getItems().add(empty);
            noteMenu.setDisable(true);
        }
        updateNoteMenuText.run();
        grid.add(new Label("Title:"), 0, 0); grid.add(title, 1, 0);
        grid.add(new Label("Date:"), 0, 1); grid.add(taskDate, 1, 1);
        grid.add(new Label("Start (H:M):"), 0, 2); grid.add(new HBox(5, startHour, new Label(":"), startMinute), 1, 2);
        grid.add(new Label("End (H:M):"), 0, 3); grid.add(new HBox(5, endHour, new Label(":"), endMinute), 1, 3);
        grid.add(new Label("Color:"), 0, 4); grid.add(color, 1, 4);
        grid.add(new Label("Description:"), 0, 5); grid.add(description, 1, 5);
        grid.add(new Label("Linked notes:"), 0, 6); grid.add(noteMenu, 1, 6);
        if (existingTask != null && !noteIntegration.notesForTask(existingTask.getId()).isEmpty()) {
            FlowPane links = new FlowPane(6, 6);
            links.setPrefWrapLength(340);
            for (Note linked : noteIntegration.notesForTask(existingTask.getId())) {
                Button open = new Button(linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle());
                open.getStyleClass().add("dialog-note-link");
                open.setOnAction(event -> {
                    dialog.close();
                    noteIntegration.openNote(linked.getId());
                });
                links.getChildren().add(open);
            }
            grid.add(new Label("Open linked:"), 0, 7); grid.add(links, 1, 7);
        }
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty()) return;
        LocalDate dateToDisplay = sourceDate;
        if (result.get() == delete && existingTask != null) {
            unlinkNotes(existingTask.getId());
            removeTask(sourceDate, existingTask);
        } else if (result.get() == save) {
            try {
                int sh = Integer.parseInt(startHour.getText().trim());
                int sm = Integer.parseInt(startMinute.getText().trim());
                int eh = Integer.parseInt(endHour.getText().trim());
                int em = Integer.parseInt(endMinute.getText().trim());
                boolean invalidStart = sh < 0 || sh > 23 || sm < 0 || sm > 59;
                boolean invalidEnd = eh < 0 || eh > 24 || em < 0 || em > 59 || eh == 24 && em != 0;
                if (invalidStart || invalidEnd) throw new NumberFormatException();
                LocalDate targetDate = taskDate.getValue();
                if (targetDate == null) {
                    dialogService.showError("Invalid Date", "Please choose a date for the activity.");
                    return;
                }
                int newStart = sh * 60 + sm;
                int newEnd = eh * 60 + em;
                if (newEnd <= newStart) {
                    dialogService.showError("Invalid Time", "The end time must be after the start time.");
                    return;
                }
                if (newEnd - newStart < Task.MIN_DURATION_MINUTES) {
                    dialogService.showError("Invalid Time", "An activity must last at least 5 minutes.");
                    return;
                }
                Task replacement = existingTask == null
                        ? new Task(title.getText(), description.getText(), newStart, newEnd - newStart, color.getValue())
                        : new Task(existingTask.getId(), title.getText(), description.getText(), newStart,
                                newEnd - newStart, color.getValue(), existingTask.isCompleted());
                if (existingTask != null) removeTask(sourceDate, existingTask);
                addTask(targetDate, replacement);
                noteSelections.forEach((candidate, selection) -> {
                    if (selection.isSelected()) candidate.linkTask(replacement.getId());
                    else candidate.unlinkTask(replacement.getId());
                });
                dateToDisplay = targetDate;
            } catch (NumberFormatException exception) {
                dialogService.showError("Invalid Time", "Use 00:00–23:59 for the start and up to 24:00 for the end.");
                return;
            } catch (IllegalArgumentException exception) {
                dialogService.showError("Invalid Time", exception.getMessage());
                return;
            }
        }
        selectDate(dateToDisplay);
    }

    private TextField timeField(int value) {
        TextField field = new TextField(String.format("%02d", value));
        field.setPrefWidth(50);
        return field;
    }

    private void notifyDataChanged() {
        if (!applyingState) dataChanged.run();
    }

    private void unlinkNotes(String taskId) {
        noteIntegration.notesForTask(taskId).forEach(note -> note.unlinkTask(taskId));
    }

    private boolean isButtonTarget(Object target, javafx.scene.Node boundary) {
        if (!(target instanceof javafx.scene.Node node)) return false;
        for (javafx.scene.Node current = node; current != null && current != boundary; current = current.getParent()) {
            if (current instanceof ButtonBase) return true;
        }
        return false;
    }

    public interface NoteIntegration {
        NoteIntegration EMPTY = new NoteIntegration() {
            @Override public List<Note> notes() { return List.of(); }
            @Override public List<Note> notesForTask(String taskId) { return List.of(); }
            @Override public void openNote(String noteId) { }
        };

        List<Note> notes();
        List<Note> notesForTask(String taskId);
        void openNote(String noteId);
    }
}
