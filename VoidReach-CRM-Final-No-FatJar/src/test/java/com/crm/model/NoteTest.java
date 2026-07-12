package com.crm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NoteTest {
    @Test void supportsMultipleUniqueTaskLinks() {
        Note note = new Note("note-1", "Plan", "", NoteFormat.TEXT, "task-1");

        note.linkTask("task-2");
        note.linkTask("task-1");

        assertEquals(List.of("task-1", "task-2"), note.getLinkedTaskIds());
        assertTrue(note.isLinkedToTask("task-2"));

        note.unlinkTask("task-1");

        assertEquals(List.of("task-2"), note.getLinkedTaskIds());
        assertFalse(note.isLinkedToTask("task-1"));
    }
}
