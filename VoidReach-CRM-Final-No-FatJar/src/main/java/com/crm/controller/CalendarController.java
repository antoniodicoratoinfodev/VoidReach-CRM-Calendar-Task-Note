package com.crm.controller;

import com.crm.model.Note;
import com.crm.model.NoteFolder;
import com.crm.model.Task;
import com.crm.service.DialogService;
import com.crm.service.ThemeService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns calendar state, task editing, timeline rendering, zoom, and the calendar sidebar. */
public final class CalendarController {
    private static final int MINI_CALENDAR_COLUMNS = 7;
    private static final double MINI_CALENDAR_MAX_CELL_SIZE = 40;
    private static final double MINUTE_HEIGHT = 1.0;
    private static final double HOUR_HEIGHT = 60.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double MIN_ZOOM = 0.75;
    private static final double MAX_ZOOM = 3.0;
    private static final double DEFAULT_ZOOM = 1.0;
    private static final double DEFAULT_TIME_COLUMN_WIDTH = 72.0;
    private static final double MIN_TIMELINE_WIDTH = 320.0;
    private static final double TIMELINE_TOP_SPACER_HEIGHT = 12.0;
    private static final double WEEK_HEADER_HEIGHT = 36.0;
    private static final double TASK_RESIZE_HIT_HEIGHT = 10.0;
    private static final double TASK_RESIZE_OVERLAP = 3.0;
    private static final double TASK_TITLE_MAX_FONT_SIZE = 12.0;
    private static final double TASK_TITLE_MIN_FONT_SIZE = 4.0;
    private static final double TASK_TITLE_FULL_SIZE_HEIGHT = 24.0;

    private final VBox calendarView;
    private final AnchorPane timeLabelsContainer;
    private final AnchorPane timelineArea;
    private final HBox calendarContentRow;
    private final ScrollPane scrollPane;
    private final DatePicker datePicker;
    private final ComboBox<String> viewModeCombo;
    private final Label selectedPeriodLabel;
    private final Label zoomLabel;
    private final Label miniMonthYearLabel;
    private final GridPane miniCalendarGrid;
    private final Label activitiesTitle;
    private final VBox upcomingActivitiesList;
    private final ThemeService themeService;
    private final DialogService dialogService;
    private final Runnable dataChanged;
    private final Runnable showCalendar;
    private final Map<LocalDate, List<Task>> tasksByDate = new HashMap<>();
    private final List<TaskResizeTarget> taskResizeTargets = new ArrayList<>();
    private NoteIntegration noteIntegration = NoteIntegration.EMPTY;

    private double dragAnchorY;
    private double dragAnchorX;
    private double dragInitialTop;
    private int dragTargetDayOffset;
    private boolean draggingTask;
    private TaskResizeTarget activeResizeTarget;
    private double resizeStartScreenY;
    private int resizeStartDuration;
    private boolean suppressClickAfterResize;
    private double zoom = DEFAULT_ZOOM;
    private PauseTransition resizeDebounce;
    private boolean calendarOpening;
    private boolean applyingState;
    private Scene shortcutScene;
    private String viewMode = "Day";
    private LocalDate weekStartDate;
    private YearMonth currentMiniMonth;

    public CalendarController(VBox calendarView, AnchorPane timeLabelsContainer,
                              AnchorPane timelineArea, HBox calendarContentRow, ScrollPane scrollPane,
                              DatePicker datePicker, ComboBox<String> viewModeCombo,
                              Label selectedPeriodLabel, Label zoomLabel,
                              Label miniMonthYearLabel, GridPane miniCalendarGrid,
                              Label activitiesTitle, VBox upcomingActivitiesList, ThemeService themeService,
                              DialogService dialogService, Runnable dataChanged,
                              Runnable showCalendar) {
        this.calendarView = Objects.requireNonNull(calendarView);
        this.timeLabelsContainer = Objects.requireNonNull(timeLabelsContainer);
        this.timelineArea = Objects.requireNonNull(timelineArea);
        this.calendarContentRow = Objects.requireNonNull(calendarContentRow);
        this.scrollPane = Objects.requireNonNull(scrollPane);
        this.datePicker = Objects.requireNonNull(datePicker);
        this.viewModeCombo = Objects.requireNonNull(viewModeCombo);
        this.selectedPeriodLabel = Objects.requireNonNull(selectedPeriodLabel);
        this.zoomLabel = Objects.requireNonNull(zoomLabel);
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
        installCalendarContentClip();
        setupMiniCalendarLayout();
        miniCalendarGrid.widthProperty().addListener((observable, oldWidth, newWidth) ->
                resizeMiniCalendarCells());
        setupViewModeCombo();
        setupZoomControls();
        setupTaskResizeGestures();
        updateZoomLabel();
        render();
        updateSidebar();
    }

    private void installCalendarContentClip() {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(26);
        clip.setArcHeight(26);
        clip.widthProperty().bind(scrollPane.widthProperty());
        clip.heightProperty().bind(scrollPane.heightProperty());
        scrollPane.setClip(clip);
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
            updateZoomLabel();
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

    public void zoomIn() { handleZoom(true, viewportCenterContentY()); }
    public void zoomOut() { handleZoom(false, viewportCenterContentY()); }
    public void resetZoom() { applyZoom(DEFAULT_ZOOM, viewportCenterContentY()); }

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

    /** Refreshes note titles and link controls without changing calendar state or saving data. */
    public void refreshNoteLinks() {
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

    /** Gives weekdays and dates the same responsive column at every agenda width. */
    private void setupMiniCalendarLayout() {
        miniCalendarGrid.setMinWidth(0);
        miniCalendarGrid.setMaxWidth(Double.MAX_VALUE);
        miniCalendarGrid.getColumnConstraints().clear();
        for (int column = 0; column < MINI_CALENDAR_COLUMNS; column++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / MINI_CALENDAR_COLUMNS);
            constraints.setHalignment(HPos.CENTER);
            constraints.setHgrow(Priority.ALWAYS);
            constraints.setFillWidth(true);
            miniCalendarGrid.getColumnConstraints().add(constraints);
        }
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
        scrollPane.addEventFilter(ZoomEvent.ZOOM, event -> {
            if (!calendarView.isVisible() || event.getZoomFactor() <= 0) return;
            event.consume();
            Point2D pivot = timelineArea.sceneToLocal(event.getSceneX(), event.getSceneY());
            applyZoom(clamp(zoom * event.getZoomFactor(), MIN_ZOOM, MAX_ZOOM), pivot.getY());
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
        if (!event.isControlDown() && !event.isMetaDown()) return;
        switch (event.getCode()) {
            case DIGIT0, NUMPAD0 -> {
                event.consume();
                resetZoom();
            }
            case PLUS, ADD, EQUALS -> {
                event.consume();
                zoomIn();
            }
            case MINUS, SUBTRACT -> {
                event.consume();
                zoomOut();
            }
            default -> { }
        }
    }

    private void handleZoom(boolean zoomIn, double pivotContentY) {
        double target = zoomIn ? Math.min(zoom + ZOOM_STEP, MAX_ZOOM) : Math.max(zoom - ZOOM_STEP, MIN_ZOOM);
        applyZoom(target, pivotContentY);
    }

    private void applyZoom(double target, double pivotContentY) {
        target = clamp(target, MIN_ZOOM, MAX_ZOOM);
        double oldZoom = zoom;
        if (Math.abs(oldZoom - target) < 0.0001) return;
        double contentHeight = calendarContentHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        double oldScrollY = scrollY(contentHeight, viewportHeight);
        double pivotViewportY = clamp(pivotContentY - oldScrollY, 0, Math.max(0, viewportHeight));
        double stablePivotContentY = oldScrollY + pivotViewportY;
        double scaleFactor = target / oldZoom;
        zoom = target;
        updateZoomLabel();
        render();
        scrollPane.setHvalue(0);
        notifyDataChanged();
        Platform.runLater(() -> {
            calendarContentRow.applyCss();
            calendarContentRow.layout();
            scrollPane.applyCss();
            scrollPane.layout();
            double newScrollableHeight = Math.max(0, calendarContentHeight() - viewportHeight);
            if (newScrollableHeight > 0) {
                double topInset = timelineTopInset();
                double newScrollY = topInset + (stablePivotContentY - topInset) * scaleFactor - pivotViewportY;
                scrollPane.setVvalue(clamp(newScrollY / newScrollableHeight, 0, 1));
            } else scrollPane.setVvalue(0);
            scrollPane.setHvalue(0);
        });
    }

    private void updateZoomLabel() {
        zoomLabel.setText(Math.round(zoom * 100) + "%");
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
        double timeColumnWidth = timeLabelsContainer.getWidth();
        if (timeColumnWidth <= 0) timeColumnWidth = timeLabelsContainer.getPrefWidth();
        if (timeColumnWidth <= 0) timeColumnWidth = DEFAULT_TIME_COLUMN_WIDTH;
        return timelineWidthFor(viewportWidth, timeColumnWidth);
    }

    static double timelineWidthFor(double viewportWidth, double timeColumnWidth) {
        return Math.max(MIN_TIMELINE_WIDTH, viewportWidth - timeColumnWidth);
    }

    private double timelineTopInset() {
        return TIMELINE_TOP_SPACER_HEIGHT + (viewMode.equals("Week") ? WEEK_HEADER_HEIGHT : 0);
    }

    private static double clamp(double value, double min, double max) {
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
        taskResizeTargets.clear();
        activeResizeTarget = null;
        timelineArea.getChildren().clear();
        timeLabelsContainer.getChildren().clear();
        double zoomedHourHeight = HOUR_HEIGHT * zoom;
        double zoomedMinuteHeight = MINUTE_HEIGHT * zoom;
        double width = timelineWidth();
        double topInset = timelineTopInset();
        double height = topInset + 24 * zoomedHourHeight + 12;
        setFixedHeight(timeLabelsContainer, height);
        setFixedHeight(timelineArea, height);
        setFixedHeight(calendarContentRow, height);
        timelineArea.setMinWidth(width);
        timelineArea.setPrefWidth(width);
        timelineArea.setMaxWidth(width);
        Canvas grid = new Canvas(width, height);
        grid.setMouseTransparent(true);
        drawTimelineGrid(grid, width, zoomedHourHeight, zoomedMinuteHeight, topInset);
        timelineArea.getChildren().add(grid);
        if (viewMode.equals("Week")) {
            Region cornerHeader = new Region();
            cornerHeader.getStyleClass().add("week-time-header");
            cornerHeader.setPrefHeight(WEEK_HEADER_HEIGHT);
            AnchorPane.setTopAnchor(cornerHeader, 0.0);
            AnchorPane.setLeftAnchor(cornerHeader, 0.0);
            AnchorPane.setRightAnchor(cornerHeader, 0.0);
            timeLabelsContainer.getChildren().add(cornerHeader);
        }
        for (int hour = 0; hour <= 24; hour++) {
            double hourY = hour * zoomedHourHeight;
            Label hourLabel = new Label(String.format("%02d:00", hour));
            hourLabel.getStyleClass().add("hour-label");
            AnchorPane.setRightAnchor(hourLabel, 10.0);
            AnchorPane.setTopAnchor(hourLabel, topInset + hourY - 7);
            timeLabelsContainer.getChildren().add(hourLabel);
            if (hour < 24) addSubHourLabels(hour, topInset + hourY, zoomedMinuteHeight);
        }
        if (viewMode.equals("Day")) renderDayView(zoomedMinuteHeight, topInset);
        else renderWeekView(width, zoomedMinuteHeight, topInset);
        calendarContentRow.requestLayout();
        scrollPane.requestLayout();
    }

    private void setFixedHeight(Region region, double height) {
        region.setMinHeight(height);
        region.setPrefHeight(height);
        region.setMaxHeight(height);
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

    private void drawTimelineGrid(Canvas canvas, double width, double hourHeight, double minuteHeight,
                                  double topInset) {
        GraphicsContext graphics = canvas.getGraphicsContext2D();
        Color hourColor = Color.web(themeService.isBlueGrayTheme() ? "#43516a"
                : themeService.isGrayBlueTheme() ? "#56616d"
                : themeService.isDarkMode() ? "#2a3a52" : "#68727d");
        Color intervalColor = Color.web(themeService.isBlueGrayTheme() ? "#2e3b52"
                : themeService.isGrayBlueTheme() ? "#3d454e"
                : themeService.isDarkMode() ? "#18243a" : "#87919b");
        Color weekDivider = Color.web(themeService.isBlueGrayTheme() ? "#3a4961"
                : themeService.isGrayBlueTheme() ? "#4d5864"
                : themeService.isDarkMode() ? "#263449" : "#68727d");
        graphics.setLineWidth(1);
        for (int hour = 0; hour <= 24; hour++) {
            double hourY = topInset + hour * hourHeight;
            graphics.setGlobalAlpha(1);
            graphics.setStroke(hourColor);
            graphics.strokeLine(0, hourY, width, hourY);
            if (hour >= 24) continue;
            int subdivisions = zoom > 1.5 ? 60 : 4;
            int interval = 60 / subdivisions;
            graphics.setStroke(intervalColor);
            for (int subdivision = 1; subdivision < subdivisions; subdivision++) {
                double y = hourY + subdivision * interval * minuteHeight;
                boolean minorZoomLine = zoom > 1.5 && subdivision % 5 != 0;
                double alpha = themeService.isDarkMode() ? (minorZoomLine ? 0.3 : 1)
                        : (minorZoomLine ? 0.2 : 0.55);
                graphics.setGlobalAlpha(alpha);
                graphics.strokeLine(0, y, width, y);
            }
        }
        if (viewMode.equals("Week")) {
            double dayWidth = width / 7.0;
            graphics.setGlobalAlpha(1);
            graphics.setStroke(weekDivider);
            for (int day = 1; day < 7; day++) {
                graphics.strokeLine(day * dayWidth, topInset, day * dayWidth, topInset + 24 * hourHeight);
            }
        }
        graphics.setGlobalAlpha(1);
    }

    private void renderDayView(double minuteHeight, double topInset) {
        timelineArea.setOnMouseClicked(event -> {
            if (event.getClickCount() == 1 && event.getTarget() == timelineArea
                    && event.getButton() == MouseButton.PRIMARY && event.getY() >= topInset) {
                showNewTaskDialogAt((int) ((event.getY() - topInset) / minuteHeight));
            }
        });
        for (Task task : tasksByDate.getOrDefault(datePicker.getValue(), List.of())) {
            renderTask(task, minuteHeight, 1.0, 0, 10.0, topInset);
        }
    }

    private void renderWeekView(double width, double minuteHeight, double topInset) {
        double dayWidth = width / 7.0;
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStartDate.plusDays(day);
            Label header = new Label(date.format(DateTimeFormatter.ofPattern("EEE dd/MM")));
            header.getStyleClass().add("week-day-header");
            header.setPrefWidth(dayWidth);
            header.setPrefHeight(WEEK_HEADER_HEIGHT);
            header.setLayoutX(day * dayWidth);
            header.setLayoutY(0);
            timelineArea.getChildren().add(header);
            for (Task task : tasksByDate.getOrDefault(date, List.of())) {
                renderTask(task, minuteHeight, 1.0 / 7.0, day, 5.0, topInset);
            }
        }
        timelineArea.setOnMouseClicked(event -> {
            if (event.getClickCount() != 1 || event.getTarget() != timelineArea
                    || event.getButton() != MouseButton.PRIMARY) return;
            if (event.getY() < topInset) return;
            int dayOffset = (int) (event.getX() / dayWidth);
            if (dayOffset >= 0 && dayOffset < 7) {
                datePicker.setValue(weekStartDate.plusDays(dayOffset));
                showNewTaskDialogAt((int) ((event.getY() - topInset) / minuteHeight));
            }
        });
    }

    private void showNewTaskDialogAt(int requestedStartMinute) {
        int start = Math.max(0, Math.min(Task.MINUTES_PER_DAY - Task.MIN_DURATION_MINUTES, requestedStartMinute));
        showTaskDialog(null, start, Math.min(60, Task.MINUTES_PER_DAY - start), "");
    }

    private void renderTask(Task task, double minuteHeight, double widthPercent, int dayOffset, double margin,
                            double topInset) {
        VBox box = new VBox();
        box.getStyleClass().addAll("task-entry", "task-" + task.getColor().toLowerCase());
        if (task.isCompleted()) box.getStyleClass().add("task-entry-completed");
        double width = timelineWidth();
        double dayWidth = width * widthPercent;
        AnchorPane.setLeftAnchor(box, dayOffset * dayWidth + margin);
        AnchorPane.setTopAnchor(box, topInset + task.getStartMin() * minuteHeight);
        setTaskWidth(box, taskEntryWidth(dayWidth, margin));
        Rectangle taskClip = new Rectangle();
        taskClip.widthProperty().bind(box.widthProperty());
        taskClip.heightProperty().bind(box.heightProperty());
        taskClip.setArcWidth(8);
        taskClip.setArcHeight(8);
        box.setClip(taskClip);
        Label title = new Label(task.getTitle());
        title.getStyleClass().add("task-title");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        double timedHeight = taskEntryHeight(task.getDuration(), minuteHeight);
        double titleFontSize = taskTitleFontSize(timedHeight);
        boolean compact = titleFontSize < TASK_TITLE_MAX_FONT_SIZE;
        title.setWrapText(!compact);
        title.setTextOverrun(compact ? OverrunStyle.ELLIPSIS : OverrunStyle.CLIP);
        if (compact) {
            box.getStyleClass().add("task-entry-compact");
            box.setStyle("-fx-padding: 0 4;");
            title.setStyle(String.format(Locale.ROOT, "-fx-font-size: %.2fpx;", titleFontSize));
        }
        Tooltip fullTitle = new Tooltip(task.getTitle());
        fullTitle.setWrapText(true);
        fullTitle.setMaxWidth(420);
        fullTitle.setStyle("-fx-font-size: 12px;");
        fullTitle.setShowDelay(Duration.millis(250));
        fullTitle.setShowDuration(Duration.INDEFINITE);
        title.setTooltip(fullTitle);
        Tooltip.install(box, fullTitle);
        setTaskHeight(box, timedHeight);
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
        resizer.getStyleClass().add("task-resize-hit-area");
        resizer.setPickOnBounds(true);
        resizer.setMouseTransparent(false);
        String idleResizeStyle = "-fx-background-color: transparent; -fx-cursor: s-resize;";
        resizer.setStyle(idleResizeStyle);
        resizer.setOnMouseEntered(event -> resizer.setStyle(
                "-fx-background-color: rgba(248,250,252,0.38);"
                        + " -fx-background-insets: 0 0 8 0; -fx-cursor: s-resize;"));
        resizer.setOnMouseExited(event -> resizer.setStyle(idleResizeStyle));
        box.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            dragAnchorY = event.getSceneY();
            dragAnchorX = event.getSceneX();
            dragInitialTop = AnchorPane.getTopAnchor(box);
            dragTargetDayOffset = dayOffset;
            draggingTask = true;
            box.getStyleClass().add("task-entry-dragging");
            box.toFront();
            resizer.toFront();
        });
        box.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) return;
            int proposed = (int) ((dragInitialTop + event.getSceneY() - dragAnchorY - topInset) / minuteHeight);
            task.setStartMin(Math.max(0, Math.min(Task.MINUTES_PER_DAY - task.getDuration(), proposed)));
            AnchorPane.setTopAnchor(box, topInset + task.getStartMin() * minuteHeight);
            if (viewMode.equals("Week")) {
                double weekDayWidth = timelineWidth() / 7.0;
                double x = dayOffset * weekDayWidth + event.getSceneX() - dragAnchorX;
                dragTargetDayOffset = Math.max(0, Math.min(6, (int) Math.floor((x + weekDayWidth / 2) / weekDayWidth)));
                AnchorPane.setLeftAnchor(box, dragTargetDayOffset * weekDayWidth + margin);
            }
            positionTaskResizeHandle(resizer, box);
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
        if (compact) {
            StackPane compactContent = new StackPane(title);
            compactContent.setMinHeight(0);
            compactContent.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(compactContent, Priority.ALWAYS);
            StackPane.setAlignment(title, javafx.geometry.Pos.CENTER_LEFT);
            Tooltip.install(compactContent, fullTitle);
            box.getChildren().add(compactContent);
        } else {
            box.getChildren().add(title);
            box.getChildren().addAll(time, description);
            if (noteLinks != null) box.getChildren().add(noteLinks);
            box.getChildren().add(spacer);
        }
        timelineArea.getChildren().add(box);
        setTaskWidth(resizer, taskEntryWidth(dayWidth, margin));
        setTaskHeight(resizer, TASK_RESIZE_HIT_HEIGHT);
        Tooltip.install(resizer, fullTitle);
        timelineArea.getChildren().add(resizer);
        positionTaskResizeHandle(resizer, box);
        taskResizeTargets.add(new TaskResizeTarget(task, box, resizer, time, minuteHeight));
    }

    private void setupTaskResizeGestures() {
        timelineArea.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            TaskResizeTarget target = findTaskResizeTarget(event.getX(), event.getY());
            if (target == null) return;
            activeResizeTarget = target;
            suppressClickAfterResize = true;
            resizeStartScreenY = event.getScreenY();
            resizeStartDuration = target.task().getDuration();
            target.taskBox().toFront();
            target.resizeHandle().toFront();
            event.consume();
        });
        timelineArea.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (activeResizeTarget == null || !event.isPrimaryButtonDown()) return;
            Task task = activeResizeTarget.task();
            int maximum = Task.MINUTES_PER_DAY - task.getStartMin();
            task.setDuration(taskDurationAfterResize(resizeStartDuration,
                    event.getScreenY() - resizeStartScreenY, activeResizeTarget.minuteHeight(), maximum));
            setTaskHeight(activeResizeTarget.taskBox(),
                    taskEntryHeight(task.getDuration(), activeResizeTarget.minuteHeight()));
            positionTaskResizeHandle(activeResizeTarget.resizeHandle(), activeResizeTarget.taskBox());
            updateTimeLabel(activeResizeTarget.timeLabel(), task.getStartMin(), task.getDuration());
            updateSidebar();
            event.consume();
        });
        timelineArea.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (activeResizeTarget == null) return;
            activeResizeTarget = null;
            notifyDataChanged();
            Platform.runLater(() -> {
                render();
                updateSidebar();
                suppressClickAfterResize = false;
            });
            event.consume();
        });
        timelineArea.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (!suppressClickAfterResize) return;
            suppressClickAfterResize = false;
            event.consume();
        });
    }

    private TaskResizeTarget findTaskResizeTarget(double x, double y) {
        for (int index = taskResizeTargets.size() - 1; index >= 0; index--) {
            TaskResizeTarget target = taskResizeTargets.get(index);
            double left = AnchorPane.getLeftAnchor(target.taskBox());
            double top = AnchorPane.getTopAnchor(target.taskBox());
            if (taskResizeHit(x, y, left, top, target.taskBox().getPrefWidth(),
                    target.taskBox().getPrefHeight())) return target;
        }
        return null;
    }

    private void positionTaskResizeHandle(Region resizeHandle, Region taskBox) {
        AnchorPane.setLeftAnchor(resizeHandle, AnchorPane.getLeftAnchor(taskBox));
        AnchorPane.setTopAnchor(resizeHandle, AnchorPane.getTopAnchor(taskBox)
                + taskBox.getPrefHeight() - TASK_RESIZE_OVERLAP);
    }

    /**
     * A task must stay inside its day column even when a title, time, or linked-note button has a
     * larger computed minimum width. A preferred width alone is not a constraint in JavaFX.
     */
    private void setTaskWidth(Region taskBox, double width) {
        double exactWidth = Math.max(0, width);
        taskBox.setMinWidth(exactWidth);
        taskBox.setPrefWidth(exactWidth);
        taskBox.setMaxWidth(exactWidth);
    }

    static double taskEntryWidth(double dayWidth, double margin) {
        return Math.max(0, dayWidth - margin * 2);
    }

    /** Keeps a task's colored bounds exactly aligned with its scheduled time range. */
    private void setTaskHeight(Region taskBox, double height) {
        double exactHeight = Math.max(0, height);
        taskBox.setMinHeight(exactHeight);
        taskBox.setPrefHeight(exactHeight);
        taskBox.setMaxHeight(exactHeight);
    }

    static double taskEntryHeight(int durationMinutes, double minuteHeight) {
        return Math.max(0, durationMinutes * minuteHeight);
    }

    static double taskTitleFontSize(double taskHeight) {
        double proportionalSize = TASK_TITLE_MAX_FONT_SIZE * Math.max(0, taskHeight)
                / TASK_TITLE_FULL_SIZE_HEIGHT;
        return clamp(proportionalSize, TASK_TITLE_MIN_FONT_SIZE, TASK_TITLE_MAX_FONT_SIZE);
    }

    static int taskDurationAfterResize(int initialDuration, double dragPixels,
                                       double minuteHeight, int maximumDuration) {
        if (minuteHeight <= 0) return Math.max(Task.MIN_DURATION_MINUTES,
                Math.min(maximumDuration, initialDuration));
        int minuteDelta = (int) Math.round(dragPixels / minuteHeight);
        return Math.max(Task.MIN_DURATION_MINUTES,
                Math.min(maximumDuration, initialDuration + minuteDelta));
    }

    static boolean taskResizeHit(double x, double y, double left, double top,
                                 double width, double height) {
        double end = top + height;
        return x >= left && x <= left + width
                && y >= end - TASK_RESIZE_OVERLAP
                && y <= end + TASK_RESIZE_HIT_HEIGHT - TASK_RESIZE_OVERLAP;
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
        resizeMiniCalendarCells();
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
                    String noteTitle = linked.getTitle().isBlank() ? "Untitled note" : linked.getTitle();
                    Button openNote = new Button(noteTitle);
                    openNote.getStyleClass().add("activity-note-link");
                    openNote.setTooltip(new Tooltip("Open note: " + noteTitle));
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

    /** Keeps date buttons square while weekday headers expand across the seven equal columns. */
    private void resizeMiniCalendarCells() {
        double gridWidth = miniCalendarGrid.getWidth();
        if (gridWidth <= 0) return;
        double cellSize = miniCalendarCellSize(gridWidth, miniCalendarGrid.getHgap());
        miniCalendarGrid.getChildren().stream()
                .filter(node -> node.getStyleClass().contains("calendar-day"))
                .filter(Region.class::isInstance)
                .map(Region.class::cast)
                .forEach(cell -> {
                    GridPane.setHalignment(cell, HPos.CENTER);
                    boolean weekdayHeader = cell.getStyleClass().contains("calendar-day-header");
                    if (weekdayHeader) {
                        cell.setMinSize(0, cellSize);
                        cell.setPrefSize(cellSize, cellSize);
                        cell.setMaxSize(Double.MAX_VALUE, cellSize);
                    } else {
                        cell.setMinSize(cellSize, cellSize);
                        cell.setPrefSize(cellSize, cellSize);
                        cell.setMaxSize(cellSize, cellSize);
                    }
                });
    }

    static double miniCalendarCellSize(double gridWidth, double horizontalGap) {
        if (!Double.isFinite(gridWidth) || gridWidth <= 0) return 0;
        double safeGap = Double.isFinite(horizontalGap) ? Math.max(0, horizontalGap) : 0;
        double totalGaps = safeGap * (MINI_CALENDAR_COLUMNS - 1);
        double availableCellWidth = Math.max(0, gridWidth - totalGaps) / MINI_CALENDAR_COLUMNS;
        return Math.min(MINI_CALENDAR_MAX_CELL_SIZE, availableCellWidth);
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

    private record TaskResizeTarget(Task task, VBox taskBox, Region resizeHandle,
                                    Label timeLabel, double minuteHeight) { }

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
        Button noteMenu = new Button("No linked notes ▾");
        noteMenu.getStyleClass().add("task-note-selector");
        noteMenu.setMaxWidth(Double.MAX_VALUE);
        Popup notePickerPopup = new Popup();
        notePickerPopup.setAutoFix(true);
        notePickerPopup.setAutoHide(true);
        notePickerPopup.setHideOnEscape(true);
        notePickerPopup.setConsumeAutoHidingEvents(false);
        Map<Note, BooleanProperty> noteSelections = new java.util.LinkedHashMap<>();
        String existingTaskId = existingTask == null ? "" : existingTask.getId();
        Runnable updateNoteMenuText = () -> {
            long selected = noteSelections.values().stream().filter(BooleanProperty::get).count();
            noteMenu.setText((selected == 0 ? "No linked notes"
                    : selected + (selected == 1 ? " linked note" : " linked notes")) + "  ▾");
        };
        noteIntegration.notes().forEach(candidate -> {
            BooleanProperty choice = new SimpleBooleanProperty(
                    !existingTaskId.isBlank() && candidate.isLinkedToTask(existingTaskId));
            choice.addListener((observable, oldValue, newValue) -> updateNoteMenuText.run());
            noteSelections.put(candidate, choice);
        });
        if (noteSelections.isEmpty()) {
            noteMenu.setText("No notes available");
            noteMenu.setDisable(true);
        } else {
            TextField noteSearch = new TextField();
            noteSearch.setPromptText("Search notes by title…");
            noteSearch.getStyleClass().add("note-picker-search");
            TreeView<NotePickerItem> noteTree = new TreeView<>();
            noteTree.getStyleClass().add("note-picker-tree");
            noteTree.setPrefSize(420, 330);
            noteTree.setCellFactory(ignored -> new TreeCell<>() {
                @Override protected void updateItem(NotePickerItem item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(null);
                    setGraphic(null);
                    if (empty || item == null) return;
                    if (item.note() == null) {
                        setText(item.label());
                        getStyleClass().remove("note-picker-file");
                        return;
                    }
                    if (!getStyleClass().contains("note-picker-file")) getStyleClass().add("note-picker-file");
                    CheckBox choice = new CheckBox(item.label() + " " + item.note().getFormat().extension());
                    BooleanProperty selected = noteSelections.get(item.note());
                    choice.setSelected(selected.get());
                    choice.selectedProperty().addListener((observable, oldValue, newValue) -> selected.set(newValue));
                    choice.setMaxWidth(Double.MAX_VALUE);
                    setGraphic(choice);
                }
            });
            Runnable refreshNoteTree = () -> noteTree.setRoot(buildNotePickerTree(
                    noteIntegration.notes(), noteIntegration.folders(), noteSearch.getText()));
            noteSearch.textProperty().addListener((observable, oldValue, newValue) -> refreshNoteTree.run());
            refreshNoteTree.run();
            Label hint = new Label("Expand folders and select one or more notes");
            hint.getStyleClass().add("note-picker-hint");
            VBox picker = new VBox(8, noteSearch, noteTree, hint);
            picker.getStyleClass().add("note-picker");
            notePickerPopup.getContent().add(picker);
            noteMenu.setOnAction(event -> {
                if (notePickerPopup.isShowing()) {
                    notePickerPopup.hide();
                    return;
                }
                Bounds anchor = noteMenu.localToScreen(noteMenu.getBoundsInLocal());
                if (anchor == null || noteMenu.getScene() == null) return;
                picker.getStylesheets().setAll(noteMenu.getScene().getStylesheets());
                notePickerPopup.show(noteMenu, anchor.getMinX(), anchor.getMaxY() + 4);
                Platform.runLater(noteSearch::requestFocus);
            });
        }
        updateNoteMenuText.run();
        grid.add(new Label("Title:"), 0, 0); grid.add(title, 1, 0);
        grid.add(new Label("Date:"), 0, 1); grid.add(taskDate, 1, 1);
        grid.add(new Label("Start (H:M):"), 0, 2); grid.add(new HBox(5, startHour, new Label(":"), startMinute), 1, 2);
        grid.add(new Label("End (H:M):"), 0, 3); grid.add(new HBox(5, endHour, new Label(":"), endMinute), 1, 3);
        grid.add(new Label("Color:"), 0, 4); grid.add(color, 1, 4);
        grid.add(new Label("Description:"), 0, 5); grid.add(DialogService.withResizeGrip(description), 1, 5);
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
        dialog.setOnHidden(event -> notePickerPopup.hide());
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
                    if (selection.get()) candidate.linkTask(replacement.getId());
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

    private TreeItem<NotePickerItem> buildNotePickerTree(List<Note> notes, List<NoteFolder> folders,
                                                          String searchText) {
        String query = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        TreeItem<NotePickerItem> root = new TreeItem<>(new NotePickerItem("Home", null));
        root.setExpanded(true);
        Map<String, TreeItem<NotePickerItem>> folderItems = new HashMap<>();
        List<NoteFolder> sortedFolders = folders.stream()
                .sorted(Comparator.comparing(NoteFolder::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        sortedFolders.forEach(folder -> folderItems.put(folder.getId(),
                new TreeItem<>(new NotePickerItem(folder.getName(), null))));

        sortedFolders.forEach(folder -> {
            TreeItem<NotePickerItem> item = folderItems.get(folder.getId());
            TreeItem<NotePickerItem> parent = folderItems.get(folder.getParentFolderId());
            if (parent == null || createsTreeCycle(item, parent)) root.getChildren().add(item);
            else parent.getChildren().add(item);
        });

        notes.stream()
                .filter(note -> query.isEmpty() || displayNoteTitle(note).toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparing(this::displayNoteTitle, String.CASE_INSENSITIVE_ORDER))
                .forEach(note -> {
                    TreeItem<NotePickerItem> parent = folderItems.get(note.getFolderId());
                    if (parent == null) parent = root;
                    parent.getChildren().add(new TreeItem<>(new NotePickerItem(displayNoteTitle(note), note)));
                });

        if (!query.isEmpty()) {
            pruneEmptyNoteFolders(root);
            expandTree(root);
        }
        return root;
    }

    private boolean createsTreeCycle(TreeItem<NotePickerItem> item, TreeItem<NotePickerItem> parent) {
        Set<TreeItem<NotePickerItem>> visited = new HashSet<>();
        for (TreeItem<NotePickerItem> cursor = parent; cursor != null && visited.add(cursor); cursor = cursor.getParent()) {
            if (cursor == item) return true;
        }
        return false;
    }

    private boolean pruneEmptyNoteFolders(TreeItem<NotePickerItem> item) {
        item.getChildren().removeIf(this::pruneEmptyNoteFolders);
        return item.getValue().note() == null && item.getParent() != null && item.getChildren().isEmpty();
    }

    private void expandTree(TreeItem<NotePickerItem> item) {
        item.setExpanded(true);
        item.getChildren().forEach(this::expandTree);
    }

    private String displayNoteTitle(Note note) {
        return note.getTitle().isBlank() ? "Untitled note" : note.getTitle();
    }

    private record NotePickerItem(String label, Note note) {
        @Override public String toString() { return label; }
    }

    public interface NoteIntegration {
        NoteIntegration EMPTY = new NoteIntegration() {
            @Override public List<Note> notes() { return List.of(); }
            @Override public List<NoteFolder> folders() { return List.of(); }
            @Override public List<Note> notesForTask(String taskId) { return List.of(); }
            @Override public void openNote(String noteId) { }
        };

        List<Note> notes();
        List<NoteFolder> folders();
        List<Note> notesForTask(String taskId);
        void openNote(String noteId);
    }
}
