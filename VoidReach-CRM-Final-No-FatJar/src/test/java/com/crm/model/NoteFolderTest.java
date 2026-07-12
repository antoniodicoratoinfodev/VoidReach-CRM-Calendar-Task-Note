package com.crm.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NoteFolderTest {
    @Test void trimsNamesAndKeepsStableIdWhenRenamed() {
        NoteFolder folder = new NoteFolder("folder-1", "  Projects  ");

        folder.setName(" Archive ");

        assertEquals("folder-1", folder.getId());
        assertEquals("Archive", folder.getName());
    }

    @Test void rejectsMissingIdentityAndBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> new NoteFolder("", "Projects"));
        assertThrows(IllegalArgumentException.class, () -> new NoteFolder("folder-1", "  "));
    }
}
