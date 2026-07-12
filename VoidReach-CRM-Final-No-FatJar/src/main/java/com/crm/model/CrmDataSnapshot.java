package com.crm.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregate for one owner. SQL version maps to contacts, calendar_tasks and user_preferences tables. */
public record CrmDataSnapshot(
        List<Contact> contacts,
        Map<LocalDate, List<Task>> tasksByDate,
        LocalDate selectedDate,
        String calendarViewMode,
        double calendarZoom) {

    /**
     * Creates a detached state copy on the JavaFX thread before it is handed to I/O.
     * The copied contacts and tasks do not share mutable state with the UI models.
     */
    public static CrmDataSnapshot detachedCopyOf(List<Contact> contacts,
                                                   Map<LocalDate, List<Task>> tasksByDate,
                                                   LocalDate selectedDate,
                                                   String calendarViewMode,
                                                   double calendarZoom) {
        List<Contact> copiedContacts = contacts.stream()
                .map(contact -> new Contact(contact.getId(), contact.nameProperty().get(),
                        contact.companyProperty().get(), contact.titleProperty().get(),
                        contact.emailProperty().get(), contact.phoneProperty().get(),
                        contact.lastInteractionProperty().get(), contact.tagsProperty().get(),
                        contact.descriptionProperty().get()))
                .toList();

        Map<LocalDate, List<Task>> copiedTasks = new LinkedHashMap<>();
        tasksByDate.forEach((date, tasks) -> copiedTasks.put(date, tasks.stream()
                .map(task -> new Task(task.getId(), task.getTitle(), task.getDescription(),
                        task.getStartMin(), task.getDuration(), task.getColor(), task.isCompleted()))
                .toList()));

        return new CrmDataSnapshot(List.copyOf(copiedContacts), Map.copyOf(copiedTasks),
                selectedDate, calendarViewMode, calendarZoom);
    }
}
