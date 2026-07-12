package com.crm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        task.setCompleted(true);
        List<Contact> contacts = new ArrayList<>(List.of(contact));
        Map<LocalDate, List<Task>> tasks = Map.of(LocalDate.of(2026, 7, 12), new ArrayList<>(List.of(task)));
        Note note = new Note("note-1", "Plan", "# Original", NoteFormat.MARKDOWN, "task-1",
                "Georgia", 20, 700, true, "Arial", 24, "#123456", "folder-1");
        note.linkTask("task-2");
        NoteFolder folder = new NoteFolder("folder-1", "Projects", "folder-parent");

        CrmDataSnapshot snapshot = CrmDataSnapshot.detachedCopyOf(contacts, tasks, List.of(note), List.of(folder),
                LocalDate.of(2026, 7, 12), "Day", 1.0);
        contact.setName("Changed in UI");
        task.setStartMin(600);
        contacts.clear();
        note.setContent("Changed in UI");
        note.setFontSize(12);
        note.setFolderId("");
        folder.setName("Changed in UI");

        assertEquals("Ada", snapshot.contacts().getFirst().nameProperty().get());
        assertEquals(540, snapshot.tasksByDate().get(LocalDate.of(2026, 7, 12)).getFirst().getStartMin());
        assertTrue(snapshot.tasksByDate().get(LocalDate.of(2026, 7, 12)).getFirst().isCompleted());
        assertEquals("# Original", snapshot.notes().getFirst().getContent());
        assertEquals(List.of("task-1", "task-2"), snapshot.notes().getFirst().getLinkedTaskIds());
        assertEquals("Georgia", snapshot.notes().getFirst().getFontFamily());
        assertEquals(20, snapshot.notes().getFirst().getFontSize());
        assertEquals(700, snapshot.notes().getFirst().getFontWeight());
        assertTrue(snapshot.notes().getFirst().isItalic());
        assertEquals("Arial", snapshot.notes().getFirst().getPreviewFontFamily());
        assertEquals(24, snapshot.notes().getFirst().getPreviewFontSize());
        assertEquals("#123456", snapshot.notes().getFirst().getPreviewTextColor());
        assertEquals("folder-1", snapshot.notes().getFirst().getFolderId());
        assertEquals("Projects", snapshot.noteFolders().getFirst().getName());
        assertEquals("folder-parent", snapshot.noteFolders().getFirst().getParentFolderId());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.contacts().add(contact));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.noteFolders().add(folder));
    }
}
