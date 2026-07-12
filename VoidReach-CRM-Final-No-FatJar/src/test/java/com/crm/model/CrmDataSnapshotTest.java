package com.crm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrmDataSnapshotTest {
    @Test void detachedCopyDoesNotShareMutableUiModels() {
        Contact contact = new Contact("contact-1", "Ada", "VoidReach", "CTO", "ada@example.com",
                "1", "today", "vip", "notes");
        Task task = new Task("task-1", "Call", "", 540, 30, "Blue");
        List<Contact> contacts = new ArrayList<>(List.of(contact));
        Map<LocalDate, List<Task>> tasks = Map.of(LocalDate.of(2026, 7, 12), new ArrayList<>(List.of(task)));

        CrmDataSnapshot snapshot = CrmDataSnapshot.detachedCopyOf(contacts, tasks,
                LocalDate.of(2026, 7, 12), "Day", 1.0);
        contact.setName("Changed in UI");
        task.setStartMin(600);
        contacts.clear();

        assertEquals("Ada", snapshot.contacts().getFirst().nameProperty().get());
        assertEquals(540, snapshot.tasksByDate().get(LocalDate.of(2026, 7, 12)).getFirst().getStartMin());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.contacts().add(contact));
    }
}
