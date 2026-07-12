package com.crm.controller;

import com.crm.model.Contact;
import com.crm.model.Task;
import com.crm.model.UserAccount;
import javafx.collections.FXCollections;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Renders read-only operational and analytical summaries from the existing CRM data. */
public final class OverviewController {
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private final Label homeGreeting;
    private final Label homeDate;
    private final Label homeContactsCount;
    private final Label homeTodayCount;
    private final Label homeWeekCount;
    private final Label homeNextTitle;
    private final Label homeNextTime;
    private final VBox homeTodayList;
    private final VBox homeUpcomingList;
    private final VBox homeContactsList;
    private final Label dashboardContactsCount;
    private final Label dashboardActivitiesCount;
    private final Label dashboardWeekCount;
    private final Label dashboardHoursCount;
    private final PieChart dashboardTagsChart;
    private final BarChart<String, Number> dashboardActivityChart;
    private final VBox dashboardInteractionsList;

    private UserAccount user;

    public OverviewController(Label homeGreeting, Label homeDate,
                              Label homeContactsCount, Label homeTodayCount, Label homeWeekCount,
                              Label homeNextTitle, Label homeNextTime,
                              VBox homeTodayList, VBox homeUpcomingList, VBox homeContactsList,
                              Label dashboardContactsCount, Label dashboardActivitiesCount,
                              Label dashboardWeekCount, Label dashboardHoursCount,
                              PieChart dashboardTagsChart,
                              BarChart<String, Number> dashboardActivityChart,
                              VBox dashboardInteractionsList) {
        this.homeGreeting = Objects.requireNonNull(homeGreeting);
        this.homeDate = Objects.requireNonNull(homeDate);
        this.homeContactsCount = Objects.requireNonNull(homeContactsCount);
        this.homeTodayCount = Objects.requireNonNull(homeTodayCount);
        this.homeWeekCount = Objects.requireNonNull(homeWeekCount);
        this.homeNextTitle = Objects.requireNonNull(homeNextTitle);
        this.homeNextTime = Objects.requireNonNull(homeNextTime);
        this.homeTodayList = Objects.requireNonNull(homeTodayList);
        this.homeUpcomingList = Objects.requireNonNull(homeUpcomingList);
        this.homeContactsList = Objects.requireNonNull(homeContactsList);
        this.dashboardContactsCount = Objects.requireNonNull(dashboardContactsCount);
        this.dashboardActivitiesCount = Objects.requireNonNull(dashboardActivitiesCount);
        this.dashboardWeekCount = Objects.requireNonNull(dashboardWeekCount);
        this.dashboardHoursCount = Objects.requireNonNull(dashboardHoursCount);
        this.dashboardTagsChart = Objects.requireNonNull(dashboardTagsChart);
        this.dashboardActivityChart = Objects.requireNonNull(dashboardActivityChart);
        this.dashboardInteractionsList = Objects.requireNonNull(dashboardInteractionsList);
        dashboardTagsChart.setAnimated(false);
        dashboardActivityChart.setAnimated(false);
    }

    public void setUser(UserAccount user) {
        this.user = user;
    }

    public void refresh(List<Contact> contacts, Map<LocalDate, List<Task>> tasksByDate) {
        List<Contact> safeContacts = contacts == null ? List.of() : contacts;
        Map<LocalDate, List<Task>> safeTasks = tasksByDate == null ? Map.of() : tasksByDate;
        LocalDate today = LocalDate.now();

        String firstName = user == null ? "" : firstName(user.getFullName());
        homeGreeting.setText(firstName.isBlank() ? "Welcome back" : "Welcome back, " + firstName);
        homeDate.setText(capitalize(today.format(FULL_DATE)));

        List<Task> todayTasks = sortedTasks(safeTasks.getOrDefault(today, List.of()));
        int weekCount = countBetween(safeTasks, today, today.plusDays(6));
        homeContactsCount.setText(String.valueOf(safeContacts.size()));
        homeTodayCount.setText(String.valueOf(todayTasks.size()));
        homeWeekCount.setText(String.valueOf(weekCount));
        updateNextActivity(todayTasks);
        renderTaskList(homeTodayList, todayTasks, false, "No tasks scheduled for today.");
        renderUpcoming(homeUpcomingList, safeTasks, today);
        renderContacts(homeContactsList, safeContacts);

        List<Task> allTasks = safeTasks.values().stream().flatMap(List::stream).toList();
        long totalMinutes = allTasks.stream().mapToLong(Task::getDuration).sum();
        dashboardContactsCount.setText(String.valueOf(safeContacts.size()));
        dashboardActivitiesCount.setText(String.valueOf(allTasks.size()));
        dashboardWeekCount.setText(String.valueOf(weekCount));
        dashboardHoursCount.setText(formatHours(totalMinutes));
        updateTagsChart(safeContacts);
        updateActivityChart(safeTasks, today);
        renderInteractions(dashboardInteractionsList, safeContacts);
    }

    private void updateNextActivity(List<Task> todayTasks) {
        int now = LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        Task next = todayTasks.stream()
                .filter(task -> task.getStartMin() + task.getDuration() >= now)
                .findFirst()
                .orElse(null);
        if (next == null) {
            homeNextTitle.setText("No more tasks today");
            homeNextTime.setText("Your day is clear");
            return;
        }
        homeNextTitle.setText(nonBlank(next.getTitle(), "Untitled task"));
        homeNextTime.setText(timeRange(next));
    }

    private void renderTaskList(VBox target, List<Task> tasks, boolean showDate, String emptyText) {
        target.getChildren().clear();
        if (tasks.isEmpty()) {
            target.getChildren().add(emptyState(emptyText));
            return;
        }
        tasks.stream().limit(5).forEach(task -> target.getChildren().add(taskRow(null, task, showDate)));
    }

    private void renderUpcoming(VBox target, Map<LocalDate, List<Task>> tasksByDate, LocalDate today) {
        target.getChildren().clear();
        List<DatedTask> upcoming = new ArrayList<>();
        tasksByDate.forEach((date, tasks) -> {
            if (date.isAfter(today) && !date.isAfter(today.plusDays(7))) {
                tasks.forEach(task -> upcoming.add(new DatedTask(date, task)));
            }
        });
        upcoming.sort(Comparator.comparing(DatedTask::date).thenComparing(entry -> entry.task().getStartMin()));
        if (upcoming.isEmpty()) {
            target.getChildren().add(emptyState("No tasks in the next 7 days."));
            return;
        }
        upcoming.stream().limit(5).forEach(entry ->
                target.getChildren().add(taskRow(entry.date(), entry.task(), true)));
    }

    private HBox taskRow(LocalDate date, Task task, boolean showDate) {
        Label marker = new Label();
        marker.getStyleClass().addAll("overview-marker", "marker-" + safeColor(task.getColor()));
        VBox text = new VBox(3);
        Label title = new Label(nonBlank(task.getTitle(), "Untitled task"));
        title.getStyleClass().add("overview-item-title");
        String detail = showDate && date != null
                ? capitalize(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ENGLISH)) + " " + date.getDayOfMonth()
                    + " · " + timeRange(task)
                : timeRange(task);
        Label subtitle = new Label(detail);
        subtitle.getStyleClass().add("overview-item-subtitle");
        text.getChildren().addAll(title, subtitle);
        HBox row = new HBox(11, marker, text);
        row.getStyleClass().add("overview-list-row");
        return row;
    }

    private void renderContacts(VBox target, List<Contact> contacts) {
        target.getChildren().clear();
        List<Contact> sorted = contacts.stream()
                .sorted(Comparator.comparing(contact -> value(contact.nameProperty().get()), String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .toList();
        if (sorted.isEmpty()) {
            target.getChildren().add(emptyState("Your address book is still empty."));
            return;
        }
        sorted.forEach(contact -> target.getChildren().add(contactRow(contact, false)));
    }

    private void renderInteractions(VBox target, List<Contact> contacts) {
        target.getChildren().clear();
        List<Contact> interactions = contacts.stream()
                .filter(contact -> !value(contact.lastInteractionProperty().get()).isBlank())
                .limit(6)
                .toList();
        if (interactions.isEmpty()) {
            target.getChildren().add(emptyState("No interactions recorded."));
            return;
        }
        interactions.forEach(contact -> target.getChildren().add(contactRow(contact, true)));
    }

    private HBox contactRow(Contact contact, boolean showInteraction) {
        String name = nonBlank(contact.nameProperty().get(), "Unnamed contact");
        Label initial = new Label(name.substring(0, 1).toUpperCase(ENGLISH));
        initial.getStyleClass().add("contact-initial");
        VBox text = new VBox(3);
        Label title = new Label(name);
        title.getStyleClass().add("overview-item-title");
        String secondary = showInteraction
                ? nonBlank(contact.lastInteractionProperty().get(), "No interaction")
                : nonBlank(contact.companyProperty().get(), nonBlank(contact.emailProperty().get(), "No details"));
        Label subtitle = new Label(secondary);
        subtitle.getStyleClass().add("overview-item-subtitle");
        text.getChildren().addAll(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label tag = new Label(nonBlank(contact.tagsProperty().get(), "No tag"));
        tag.getStyleClass().add("overview-tag");
        HBox row = new HBox(11, initial, text, spacer, tag);
        row.getStyleClass().add("overview-list-row");
        return row;
    }

    private void updateTagsChart(List<Contact> contacts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        contacts.forEach(contact -> {
            String tag = nonBlank(contact.tagsProperty().get(), "No tag");
            counts.merge(tag, 1, Integer::sum);
        });
        dashboardTagsChart.setData(FXCollections.observableArrayList(
                counts.entrySet().stream()
                        .map(entry -> new PieChart.Data(entry.getKey(), entry.getValue()))
                        .toList()));
    }

    private void updateActivityChart(Map<LocalDate, List<Task>> tasksByDate, LocalDate today) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Tasks");
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = today.plusDays(offset);
            String day = capitalize(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, ENGLISH));
            series.getData().add(new XYChart.Data<>(day, tasksByDate.getOrDefault(date, List.of()).size()));
        }
        dashboardActivityChart.getData().setAll(series);
    }

    static int countBetween(Map<LocalDate, List<Task>> tasks, LocalDate from, LocalDate to) {
        return tasks.entrySet().stream()
                .filter(entry -> !entry.getKey().isBefore(from) && !entry.getKey().isAfter(to))
                .mapToInt(entry -> entry.getValue().size())
                .sum();
    }

    private static List<Task> sortedTasks(List<Task> tasks) {
        return tasks.stream().sorted(Comparator.comparingInt(Task::getStartMin)).toList();
    }

    private static Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("overview-empty");
        label.setWrapText(true);
        return label;
    }

    private static String timeRange(Task task) {
        int end = task.getStartMin() + task.getDuration();
        return String.format("%02d:%02d – %02d:%02d",
                task.getStartMin() / 60, task.getStartMin() % 60, end / 60, end % 60);
    }

    static String formatHours(long minutes) {
        long hours = minutes / 60;
        long remainder = minutes % 60;
        return remainder == 0 ? hours + " h" : hours + " h " + remainder + " m";
    }

    private static String firstName(String fullName) {
        String value = value(fullName).trim();
        int separator = value.indexOf(' ');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static String safeColor(String color) {
        String value = value(color).toLowerCase(ENGLISH);
        return List.of("blue", "red", "green", "yellow", "orange", "purple").contains(value)
                ? value : "blue";
    }

    private static String nonBlank(String value, String fallback) {
        String normalized = value(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String capitalize(String value) {
        return value == null || value.isBlank() ? "" : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record DatedTask(LocalDate date, Task task) {}
}
